/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.debug;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISourceLocator;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.ISuspendResume;
import org.eclipse.debug.core.model.ITerminate;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Snapshot bridge over Eclipse debug model objects for supported EDT runtime launches.
 */
public final class RuntimeDebugModelBridge
{
    private static final String LAUNCH_CONFIG_TYPE_ID =
            "com._1c.g5.v8.dt.launching.core.RuntimeClient"; //$NON-NLS-1$

    private static final String ATTR_PROJECT_NAME =
            "com._1c.g5.v8.dt.debug.core.ATTR_PROJECT_NAME"; //$NON-NLS-1$

    private static final String ATTR_APPLICATION_ID =
            "com._1c.g5.v8.dt.debug.core.ATTR_APPLICATION_ID"; //$NON-NLS-1$

    private static final int DEFAULT_LIMIT = 100;

    private static final int HARD_LIMIT = 200;

    private static final int MAX_DISPLAY_VALUE_LENGTH = 500;

    private static final AtomicLong SNAPSHOT_SEQUENCE = new AtomicLong();

    private static final Map<String, ILaunch> SESSIONS = new ConcurrentHashMap<>();

    private static final Map<String, IThread> THREADS = new ConcurrentHashMap<>();

    private static final Map<String, IStackFrame> FRAMES = new ConcurrentHashMap<>();

    private RuntimeDebugModelBridge()
    {
        // Utility class
    }

    public static String listSessions(String projectFilter, String applicationFilter)
    {
        ILaunchManager launchManager = getLaunchManager();
        if (launchManager == null)
        {
            return ToolResult.error("Eclipse launch manager is not available").toJson(); //$NON-NLS-1$
        }

        long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
        JsonArray sessions = new JsonArray();
        for (ILaunch launch : launchManager.getLaunches())
        {
            SessionInfo info = getSupportedSessionInfo(launch);
            if (info == null || !matchesFilter(info, projectFilter, applicationFilter))
            {
                continue;
            }

            String sessionId = sessionId(launch, info, snapshotId);
            SESSIONS.put(sessionId, launch);
            sessions.add(toSessionJson(launch, info, sessionId, snapshotId));
        }

        return ToolResult.success()
                .put("sessions", sessions) //$NON-NLS-1$
                .put("count", sessions.size()) //$NON-NLS-1$
                .put("snapshotId", Long.toString(snapshotId)) //$NON-NLS-1$
                .toJson();
    }

    public static String getStack(String threadId, int requestedMaxFrames)
    {
        if (threadId == null || threadId.isEmpty())
        {
            return ToolResult.error("threadId is required").toJson(); //$NON-NLS-1$
        }

        IThread thread = THREADS.get(threadId);
        if (thread == null)
        {
            return ToolResult.error("Unknown or stale threadId: " + threadId) //$NON-NLS-1$
                    .put("reason", "stale_thread_id") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        if (thread.isTerminated())
        {
            return ToolResult.error("Thread is terminated: " + threadId) //$NON-NLS-1$
                    .put("reason", "thread_terminated") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        if (!thread.isSuspended())
        {
            return ToolResult.error("Thread is running; stack frames are only available for suspended threads") //$NON-NLS-1$
                    .put("reason", "thread_running") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("threadId", threadId) //$NON-NLS-1$
                    .put("pollHint", "Call list_debug_sessions until the thread is suspended") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        try
        {
            int maxFrames = normalizeLimit(requestedMaxFrames);
            long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
            IStackFrame[] frames = thread.getStackFrames();
            JsonArray frameArray = new JsonArray();
            int count = Math.min(frames.length, maxFrames);
            for (int i = 0; i < count; i++)
            {
                String frameId = frameId(threadId, frames[i], i, snapshotId);
                FRAMES.put(frameId, frames[i]);
                frameArray.add(toFrameJson(frames[i], frameId, i));
            }

            return ToolResult.success()
                    .put("threadId", threadId) //$NON-NLS-1$
                    .put("thread", toThreadJson(thread, threadId, snapshotId)) //$NON-NLS-1$
                    .put("frames", frameArray) //$NON-NLS-1$
                    .put("count", frameArray.size()) //$NON-NLS-1$
                    .put("truncated", frames.length > count) //$NON-NLS-1$
                    .put("snapshotId", Long.toString(snapshotId)) //$NON-NLS-1$
                    .toJson();
        }
        catch (DebugException e)
        {
            Activator.logError("Failed to read debug stack", e); //$NON-NLS-1$
            return ToolResult.error("Failed to read debug stack: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    public static String getVariables(String frameId, List<String> variablePath, int requestedMaxVariables)
    {
        if (frameId == null || frameId.isEmpty())
        {
            return ToolResult.error("frameId is required").toJson(); //$NON-NLS-1$
        }

        IStackFrame frame = FRAMES.get(frameId);
        if (frame == null)
        {
            return ToolResult.error("Unknown or stale frameId: " + frameId) //$NON-NLS-1$
                    .put("reason", "stale_frame_id") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        if (frame.isTerminated())
        {
            return ToolResult.error("Frame is terminated: " + frameId) //$NON-NLS-1$
                    .put("reason", "frame_terminated") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        if (!frame.isSuspended())
        {
            return ToolResult.error("Frame is no longer suspended; refresh the stack snapshot") //$NON-NLS-1$
                    .put("reason", "frame_not_suspended") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("pollHint", "Call get_debug_stack for a fresh suspended snapshot") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        try
        {
            int maxVariables = normalizeLimit(requestedMaxVariables);
            VariableSource source = resolveVariableSource(frame, variablePath);
            if (source.error != null)
            {
                return source.error.toJson();
            }

            JsonArray variables = new JsonArray();
            int count = Math.min(source.variables.length, maxVariables);
            for (int i = 0; i < count; i++)
            {
                variables.add(toVariableJson(source.variables[i], source.path, i));
            }

            return ToolResult.success()
                    .put("frameId", frameId) //$NON-NLS-1$
                    .put("variablePath", toJsonArray(source.path)) //$NON-NLS-1$
                    .put("variables", variables) //$NON-NLS-1$
                    .put("count", variables.size()) //$NON-NLS-1$
                    .put("truncated", source.variables.length > count) //$NON-NLS-1$
                    .toJson();
        }
        catch (DebugException e)
        {
            Activator.logError("Failed to read debug variables", e); //$NON-NLS-1$
            return ToolResult.error("Failed to read debug variables: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    public static String control(String sessionId, String threadId, String action)
    {
        if (action == null || action.isEmpty())
        {
            return ToolResult.error("action is required").toJson(); //$NON-NLS-1$
        }

        try
        {
            if (threadId != null && !threadId.isEmpty())
            {
                IThread thread = THREADS.get(threadId);
                if (thread == null)
                {
                    return ToolResult.error("Unknown or stale threadId: " + threadId) //$NON-NLS-1$
                            .put("reason", "stale_thread_id") //$NON-NLS-1$ //$NON-NLS-2$
                            .toJson();
                }
                return controlThread(thread, threadId, action);
            }

            if (sessionId == null || sessionId.isEmpty())
            {
                return ToolResult.error("sessionId or threadId is required").toJson(); //$NON-NLS-1$
            }

            if (!"terminate".equals(action)) //$NON-NLS-1$
            {
                return ToolResult.error("Action requires threadId: " + action) //$NON-NLS-1$
                        .put("reason", "thread_id_required") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("action", action) //$NON-NLS-1$
                        .toJson();
            }

            ILaunch launch = SESSIONS.get(sessionId);
            if (launch == null)
            {
                return ToolResult.error("Unknown or stale sessionId: " + sessionId) //$NON-NLS-1$
                        .put("reason", "stale_session_id") //$NON-NLS-1$ //$NON-NLS-2$
                        .toJson();
            }
            return controlSession(launch, sessionId, action);
        }
        catch (DebugException e)
        {
            Activator.logError("Failed to control debug session", e); //$NON-NLS-1$
            return ToolResult.error("Failed to control debug session: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    private static String controlThread(IThread thread, String threadId, String action) throws DebugException
    {
        switch (action)
        {
        case "resume": //$NON-NLS-1$
            if (!thread.canResume())
            {
                return unsupportedAction("Thread cannot resume", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.resume();
            break;
        case "suspend": //$NON-NLS-1$
            if (!thread.canSuspend())
            {
                return unsupportedAction("Thread cannot suspend", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.suspend();
            break;
        case "step_over": //$NON-NLS-1$
            if (!thread.canStepOver())
            {
                return unsupportedAction("Thread cannot step over", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.stepOver();
            break;
        case "step_into": //$NON-NLS-1$
            if (!thread.canStepInto())
            {
                return unsupportedAction("Thread cannot step into", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.stepInto();
            break;
        case "step_return": //$NON-NLS-1$
            if (!thread.canStepReturn())
            {
                return unsupportedAction("Thread cannot step return", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.stepReturn();
            break;
        case "terminate": //$NON-NLS-1$
            if (!thread.canTerminate())
            {
                return unsupportedAction("Thread cannot terminate", action, threadId).toJson(); //$NON-NLS-1$
            }
            thread.terminate();
            break;
        default:
            return ToolResult.error("Unsupported action: " + action) //$NON-NLS-1$
                    .put("reason", "unsupported_action") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        return ToolResult.success()
                .put("threadId", threadId) //$NON-NLS-1$
                .put("action", action) //$NON-NLS-1$
                .put("state", stateOf(thread)) //$NON-NLS-1$
                .put("thread", toThreadJson(thread, threadId, SNAPSHOT_SEQUENCE.incrementAndGet())) //$NON-NLS-1$
                .put("pollHint", "Call list_debug_sessions or get_debug_stack for the next state") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();
    }

    private static String controlSession(ILaunch launch, String sessionId, String action) throws DebugException
    {
        switch (action)
        {
        case "terminate": //$NON-NLS-1$
            if (!launch.canTerminate())
            {
                return unsupportedAction("Session cannot terminate", action, sessionId).toJson(); //$NON-NLS-1$
            }
            launch.terminate();
            break;
        default:
            return ToolResult.error("Unsupported action: " + action) //$NON-NLS-1$
                    .put("reason", "unsupported_action") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        return ToolResult.success()
                .put("sessionId", sessionId) //$NON-NLS-1$
                .put("action", action) //$NON-NLS-1$
                .put("state", stateOf(launch)) //$NON-NLS-1$
                .put("pollHint", "Call list_debug_sessions for the next state") //$NON-NLS-1$ //$NON-NLS-2$
                .toJson();
    }

    private static ToolResult unsupportedAction(String message, String action, String id)
    {
        return ToolResult.error(message)
                .put("reason", "unsupported_backend_capability") //$NON-NLS-1$ //$NON-NLS-2$
                .put("action", action) //$NON-NLS-1$
                .put("id", id); //$NON-NLS-1$
    }

    private static JsonObject toSessionJson(ILaunch launch, SessionInfo info, String sessionId, long snapshotId)
    {
        JsonObject object = new JsonObject();
        object.addProperty("sessionId", sessionId); //$NON-NLS-1$
        object.addProperty("snapshotId", Long.toString(snapshotId)); //$NON-NLS-1$
        object.addProperty("project", info.projectName); //$NON-NLS-1$
        object.addProperty("applicationId", info.applicationId); //$NON-NLS-1$
        object.addProperty("launchConfiguration", info.launchConfigurationName); //$NON-NLS-1$
        object.addProperty("launchMode", nullToEmpty(launch.getLaunchMode())); //$NON-NLS-1$
        object.addProperty("state", stateOf(launch)); //$NON-NLS-1$
        object.addProperty("launchClass", launch.getClass().getName()); //$NON-NLS-1$
        object.addProperty("debugTargetCount", launch.getDebugTargets().length); //$NON-NLS-1$
        object.addProperty("processCount", launch.getProcesses().length); //$NON-NLS-1$
        object.add("debugTargets", toTargetsJson(launch)); //$NON-NLS-1$
        object.add("threads", toThreadsJson(launch, sessionId, snapshotId)); //$NON-NLS-1$
        object.add("processes", toProcessesJson(launch)); //$NON-NLS-1$
        return object;
    }

    private static JsonArray toTargetsJson(ILaunch launch)
    {
        JsonArray targets = new JsonArray();
        for (IDebugTarget target : launch.getDebugTargets())
        {
            JsonObject object = new JsonObject();
            object.addProperty("name", safeTargetName(target)); //$NON-NLS-1$
            object.addProperty("className", target.getClass().getName()); //$NON-NLS-1$
            object.addProperty("modelIdentifier", nullToEmpty(target.getModelIdentifier())); //$NON-NLS-1$
            object.addProperty("state", stateOf(target)); //$NON-NLS-1$
            object.addProperty("canResume", target.canResume()); //$NON-NLS-1$
            object.addProperty("canSuspend", target.canSuspend()); //$NON-NLS-1$
            object.addProperty("canTerminate", target.canTerminate()); //$NON-NLS-1$
            targets.add(object);
        }
        return targets;
    }

    private static JsonArray toThreadsJson(ILaunch launch, String sessionId, long snapshotId)
    {
        JsonArray threads = new JsonArray();
        int targetIndex = 0;
        for (IDebugTarget target : launch.getDebugTargets())
        {
            try
            {
                IThread[] targetThreads = target.getThreads();
                for (int i = 0; i < targetThreads.length; i++)
                {
                    String threadId = threadId(sessionId, targetIndex, targetThreads[i], i, snapshotId);
                    THREADS.put(threadId, targetThreads[i]);
                    threads.add(toThreadJson(targetThreads[i], threadId, snapshotId));
                }
            }
            catch (DebugException e)
            {
                Activator.logError("Failed to read debug target threads", e); //$NON-NLS-1$
            }
            targetIndex++;
        }
        return threads;
    }

    private static JsonObject toThreadJson(IThread thread, String threadId, long snapshotId)
    {
        JsonObject object = new JsonObject();
        object.addProperty("threadId", threadId); //$NON-NLS-1$
        object.addProperty("snapshotId", Long.toString(snapshotId)); //$NON-NLS-1$
        object.addProperty("name", safeThreadName(thread)); //$NON-NLS-1$
        object.addProperty("className", thread.getClass().getName()); //$NON-NLS-1$
        object.addProperty("modelIdentifier", safeModelIdentifier(thread)); //$NON-NLS-1$
        object.addProperty("state", stateOf(thread)); //$NON-NLS-1$
        object.addProperty("canResume", thread.canResume()); //$NON-NLS-1$
        object.addProperty("canSuspend", thread.canSuspend()); //$NON-NLS-1$
        object.addProperty("canStepOver", thread.canStepOver()); //$NON-NLS-1$
        object.addProperty("canStepInto", thread.canStepInto()); //$NON-NLS-1$
        object.addProperty("canStepReturn", thread.canStepReturn()); //$NON-NLS-1$
        object.addProperty("canTerminate", thread.canTerminate()); //$NON-NLS-1$
        object.addProperty("hasStackFrames", safeHasStackFrames(thread)); //$NON-NLS-1$
        return object;
    }

    private static JsonArray toProcessesJson(ILaunch launch)
    {
        JsonArray processes = new JsonArray();
        for (IProcess process : launch.getProcesses())
        {
            JsonObject object = new JsonObject();
            object.addProperty("label", nullToEmpty(process.getLabel())); //$NON-NLS-1$
            object.addProperty("className", process.getClass().getName()); //$NON-NLS-1$
            object.addProperty("terminated", process.isTerminated()); //$NON-NLS-1$
            processes.add(object);
        }
        return processes;
    }

    private static JsonObject toFrameJson(IStackFrame frame, String frameId, int index) throws DebugException
    {
        JsonObject object = new JsonObject();
        object.addProperty("frameId", frameId); //$NON-NLS-1$
        object.addProperty("index", index); //$NON-NLS-1$
        object.addProperty("name", nullToEmpty(frame.getName())); //$NON-NLS-1$
        object.addProperty("className", frame.getClass().getName()); //$NON-NLS-1$
        object.addProperty("modelIdentifier", safeModelIdentifier(frame)); //$NON-NLS-1$
        object.addProperty("lineNumber", frame.getLineNumber()); //$NON-NLS-1$
        object.addProperty("charStart", frame.getCharStart()); //$NON-NLS-1$
        object.addProperty("charEnd", frame.getCharEnd()); //$NON-NLS-1$
        object.addProperty("state", stateOf(frame)); //$NON-NLS-1$
        object.addProperty("hasVariables", frame.hasVariables()); //$NON-NLS-1$
        object.addProperty("canResume", frame.canResume()); //$NON-NLS-1$
        object.addProperty("canStepOver", frame.canStepOver()); //$NON-NLS-1$
        object.addProperty("canStepInto", frame.canStepInto()); //$NON-NLS-1$
        object.addProperty("canStepReturn", frame.canStepReturn()); //$NON-NLS-1$
        object.add("source", toSourceJson(frame)); //$NON-NLS-1$
        return object;
    }

    private static JsonObject toSourceJson(IStackFrame frame)
    {
        JsonObject object = new JsonObject();
        ILaunch launch = frame.getLaunch();
        ISourceLocator locator = launch != null ? launch.getSourceLocator() : null;
        if (locator == null)
        {
            object.addProperty("supported", false); //$NON-NLS-1$
            object.addProperty("reason", "source_locator_unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
            return object;
        }

        Object source = locator.getSourceElement(frame);
        if (source == null)
        {
            object.addProperty("supported", false); //$NON-NLS-1$
            object.addProperty("reason", "source_element_unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
            object.addProperty("sourceLocatorClass", locator.getClass().getName()); //$NON-NLS-1$
            return object;
        }

        object.addProperty("supported", true); //$NON-NLS-1$
        object.addProperty("sourceLocatorClass", locator.getClass().getName()); //$NON-NLS-1$
        object.addProperty("sourceClass", source.getClass().getName()); //$NON-NLS-1$
        object.addProperty("display", truncate(source.toString())); //$NON-NLS-1$
        return object;
    }

    private static JsonObject toVariableJson(IVariable variable, List<String> parentPath, int index) throws DebugException
    {
        JsonObject object = new JsonObject();
        String name = nullToEmpty(variable.getName());
        JsonArray path = toJsonArray(parentPath);
        path.add(name);

        object.addProperty("name", name); //$NON-NLS-1$
        object.add("path", path); //$NON-NLS-1$
        object.addProperty("index", index); //$NON-NLS-1$
        object.addProperty("className", variable.getClass().getName()); //$NON-NLS-1$
        object.addProperty("modelIdentifier", safeModelIdentifier(variable)); //$NON-NLS-1$
        object.addProperty("declaredType", nullToEmpty(variable.getReferenceTypeName())); //$NON-NLS-1$
        object.addProperty("valueChanged", variable.hasValueChanged()); //$NON-NLS-1$

        IValue value = variable.getValue();
        if (value == null)
        {
            object.addProperty("valueString", ""); //$NON-NLS-1$ //$NON-NLS-2$
            object.addProperty("valueType", ""); //$NON-NLS-1$ //$NON-NLS-2$
            object.addProperty("hasChildren", false); //$NON-NLS-1$
            object.addProperty("truncated", false); //$NON-NLS-1$
            object.addProperty("expansionUnsupported", true); //$NON-NLS-1$
            return object;
        }

        object.addProperty("valueClassName", value.getClass().getName()); //$NON-NLS-1$
        object.addProperty("valueType", nullToEmpty(value.getReferenceTypeName())); //$NON-NLS-1$
        String valueString = nullToEmpty(value.getValueString());
        object.addProperty("valueString", truncate(valueString)); //$NON-NLS-1$
        object.addProperty("valueTruncated", valueString.length() > MAX_DISPLAY_VALUE_LENGTH); //$NON-NLS-1$
        object.addProperty("allocated", value.isAllocated()); //$NON-NLS-1$
        try
        {
            object.addProperty("hasChildren", value.hasVariables()); //$NON-NLS-1$
            object.addProperty("expansionUnsupported", false); //$NON-NLS-1$
        }
        catch (DebugException e)
        {
            object.addProperty("hasChildren", false); //$NON-NLS-1$
            object.addProperty("expansionUnsupported", true); //$NON-NLS-1$
            object.addProperty("expansionError", truncate(nullToEmpty(e.getMessage()))); //$NON-NLS-1$
        }
        object.addProperty("truncated", false); //$NON-NLS-1$
        return object;
    }

    private static VariableSource resolveVariableSource(IStackFrame frame, List<String> variablePath) throws DebugException
    {
        List<String> path = variablePath != null ? variablePath : List.of();
        IVariable[] variables = frame.getVariables();
        if (path.isEmpty())
        {
            return VariableSource.success(path, variables);
        }

        IVariable selected = null;
        IVariable[] current = variables;
        for (String segment : path)
        {
            selected = findVariable(current, segment);
            if (selected == null)
            {
                return VariableSource.error(ToolResult.error("Variable path segment not found: " + segment) //$NON-NLS-1$
                        .put("reason", "variable_path_not_found") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("segment", segment) //$NON-NLS-1$
                        .put("variablePath", toJsonArray(path))); //$NON-NLS-1$
            }
            IValue value = selected.getValue();
            if (value == null || !value.hasVariables())
            {
                return VariableSource.error(ToolResult.error("Variable path cannot be expanded: " + segment) //$NON-NLS-1$
                        .put("reason", "variable_expansion_unsupported") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("segment", segment) //$NON-NLS-1$
                        .put("variablePath", toJsonArray(path))); //$NON-NLS-1$
            }
            current = value.getVariables();
        }
        return VariableSource.success(path, current);
    }

    private static IVariable findVariable(IVariable[] variables, String name) throws DebugException
    {
        for (IVariable variable : variables)
        {
            if (Objects.equals(variable.getName(), name))
            {
                return variable;
            }
        }
        return null;
    }

    private static SessionInfo getSupportedSessionInfo(ILaunch launch)
    {
        if (launch == null || launch.isTerminated() || !ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode()))
        {
            return null;
        }

        ILaunchConfiguration configuration = launch.getLaunchConfiguration();
        if (configuration == null || launch.getDebugTargets().length == 0)
        {
            return null;
        }

        try
        {
            ILaunchConfigurationType type = configuration.getType();
            if (type == null || !LAUNCH_CONFIG_TYPE_ID.equals(type.getIdentifier()))
            {
                return null;
            }

            String projectName = configuration.getAttribute(ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
            String applicationId = configuration.getAttribute(ATTR_APPLICATION_ID, ""); //$NON-NLS-1$
            if (projectName.isEmpty() || applicationId.isEmpty()
                    || !hasResolvableApplication(projectName, applicationId))
            {
                return null;
            }

            return new SessionInfo(projectName, applicationId, configuration.getName());
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to inspect launch configuration", e); //$NON-NLS-1$
            return null;
        }
    }

    private static boolean hasResolvableApplication(String projectName, String applicationId)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists() || !project.isOpen())
        {
            return false;
        }

        IApplicationManager applicationManager = Activator.getDefault().getApplicationManager();
        if (applicationManager == null)
        {
            return false;
        }

        try
        {
            return applicationManager.getApplication(project, applicationId).isPresent();
        }
        catch (ApplicationException e)
        {
            Activator.logError("Failed to resolve debug launch application", e); //$NON-NLS-1$
            return false;
        }
    }

    private static boolean matchesFilter(SessionInfo info, String projectFilter, String applicationFilter)
    {
        if (projectFilter != null && !projectFilter.isEmpty() && !projectFilter.equals(info.projectName))
        {
            return false;
        }
        return applicationFilter == null || applicationFilter.isEmpty() || applicationFilter.equals(info.applicationId);
    }

    private static ILaunchManager getLaunchManager()
    {
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        return debugPlugin != null ? debugPlugin.getLaunchManager() : null;
    }

    private static int normalizeLimit(int requested)
    {
        if (requested <= 0)
        {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, HARD_LIMIT);
    }

    private static String stateOf(ILaunch launch)
    {
        if (launch.isTerminated())
        {
            return "terminated"; //$NON-NLS-1$
        }
        for (IDebugTarget target : launch.getDebugTargets())
        {
            String state = stateOf(target);
            if ("suspended".equals(state)) //$NON-NLS-1$
            {
                return state;
            }
        }
        return "running"; //$NON-NLS-1$
    }

    private static String stateOf(ISuspendResume element)
    {
        if (element instanceof ITerminate terminate && terminate.isTerminated())
        {
            return "terminated"; //$NON-NLS-1$
        }
        if (element.isSuspended())
        {
            return "suspended"; //$NON-NLS-1$
        }
        return "running"; //$NON-NLS-1$
    }

    private static boolean safeHasStackFrames(IThread thread)
    {
        try
        {
            return thread.hasStackFrames();
        }
        catch (DebugException e)
        {
            return false;
        }
    }

    private static String safeTargetName(IDebugTarget target)
    {
        try
        {
            return nullToEmpty(target.getName());
        }
        catch (DebugException e)
        {
            return ""; //$NON-NLS-1$
        }
    }

    private static String safeThreadName(IThread thread)
    {
        try
        {
            return nullToEmpty(thread.getName());
        }
        catch (DebugException e)
        {
            return ""; //$NON-NLS-1$
        }
    }

    private static String safeModelIdentifier(IDebugElement element)
    {
        return nullToEmpty(element.getModelIdentifier());
    }

    private static String sessionId(ILaunch launch, SessionInfo info, long snapshotId)
    {
        return opaqueId("session", snapshotId, info.projectName, info.applicationId, //$NON-NLS-1$
                info.launchConfigurationName, launch.getLaunchMode(), launch);
    }

    private static String threadId(String sessionId, int targetIndex, IThread thread, int index, long snapshotId)
    {
        return opaqueId("thread", snapshotId, sessionId, Integer.toString(targetIndex), //$NON-NLS-1$
                Integer.toString(index), safeThreadName(thread), thread);
    }

    private static String frameId(String threadId, IStackFrame frame, int index, long snapshotId)
    {
        return opaqueId("frame", snapshotId, threadId, Integer.toString(index), frame); //$NON-NLS-1$
    }

    private static String opaqueId(String prefix, long snapshotId, Object... parts)
    {
        Object[] normalizedParts = Arrays.stream(parts)
                .map(RuntimeDebugModelBridge::stableIdPart)
                .toArray();
        int hash = Arrays.deepHashCode(normalizedParts);
        return prefix + '-' + Long.toString(snapshotId, 36) + '-' + Integer.toUnsignedString(hash, 36);
    }

    private static Object stableIdPart(Object part)
    {
        if (part instanceof ILaunch || part instanceof IDebugElement)
        {
            return part.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(part));
        }
        return part;
    }

    private static JsonArray toJsonArray(List<String> values)
    {
        JsonArray array = new JsonArray();
        if (values != null)
        {
            for (String value : values)
            {
                array.add(value);
            }
        }
        return array;
    }

    private static String truncate(String value)
    {
        if (value == null || value.length() <= MAX_DISPLAY_VALUE_LENGTH)
        {
            return nullToEmpty(value);
        }
        return value.substring(0, MAX_DISPLAY_VALUE_LENGTH) + "..."; //$NON-NLS-1$
    }

    private static String nullToEmpty(String value)
    {
        return value != null ? value : ""; //$NON-NLS-1$
    }

    private record SessionInfo(String projectName, String applicationId, String launchConfigurationName)
    {
    }

    private static final class VariableSource
    {
        private final List<String> path;

        private final IVariable[] variables;

        private final ToolResult error;

        private VariableSource(List<String> path, IVariable[] variables, ToolResult error)
        {
            this.path = path;
            this.variables = variables;
            this.error = error;
        }

        private static VariableSource success(List<String> path, IVariable[] variables)
        {
            return new VariableSource(path, variables, null);
        }

        private static VariableSource error(ToolResult error)
        {
            return new VariableSource(List.of(), new IVariable[0], error);
        }
    }
}
