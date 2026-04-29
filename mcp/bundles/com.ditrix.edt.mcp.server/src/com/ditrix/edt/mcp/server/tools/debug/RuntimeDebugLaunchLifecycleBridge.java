/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.ISuspendResume;
import org.eclipse.debug.core.model.ITerminate;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.swt.widgets.Display;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Launch-level inventory bridge over Eclipse debug model objects.
 */
public final class RuntimeDebugLaunchLifecycleBridge
{
    public static final String RUNTIME_CLIENT_LAUNCH_CONFIG_TYPE_ID =
            "com._1c.g5.v8.dt.launching.core.RuntimeClient"; //$NON-NLS-1$

    public static final String ATTR_PROJECT_NAME =
            "com._1c.g5.v8.dt.debug.core.ATTR_PROJECT_NAME"; //$NON-NLS-1$

    public static final String ATTR_APPLICATION_ID =
            "com._1c.g5.v8.dt.debug.core.ATTR_APPLICATION_ID"; //$NON-NLS-1$

    private static final AtomicLong SNAPSHOT_SEQUENCE = new AtomicLong();

    private static final Map<String, ILaunch> LAUNCHES = new ConcurrentHashMap<>();

    private static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 30;

    private static final int MAX_WAIT_TIMEOUT_SECONDS = 300;

    private static final int DEFAULT_LAUNCH_TIMEOUT_SECONDS = 15;

    private static final int MAX_LAUNCH_TIMEOUT_SECONDS = 120;

    private static final long POLL_INTERVAL_MILLIS = 250L;

    private static final Pattern DEBUGGER_URL_PATTERN =
            Pattern.compile("(?i)(?:/DEBUGGERURL)(?:\\s+|=)(\"[^\"]+\"|\\S+)"); //$NON-NLS-1$

    private RuntimeDebugLaunchLifecycleBridge()
    {
        // Utility class
    }

    public static String listLaunches(String projectFilter, String applicationFilter)
    {
        ILaunchManager launchManager = getLaunchManager();
        if (launchManager == null)
        {
            return ToolResult.error("Eclipse launch manager is not available").toJson(); //$NON-NLS-1$
        }

        return ToolResult.toJsonStatic(snapshotLaunches(launchManager.getLaunches(), projectFilter, applicationFilter));
    }

    public static JsonObject snapshotLaunches(String projectFilter, String applicationFilter)
    {
        ILaunchManager launchManager = getLaunchManager();
        if (launchManager == null)
        {
            JsonObject object = new JsonObject();
            object.addProperty("success", false); //$NON-NLS-1$
            object.addProperty("reason", "launch_manager_unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
            return object;
        }

        return snapshotLaunches(launchManager.getLaunches(), projectFilter, applicationFilter);
    }

    public static JsonObject duplicateLaunchPreflight(String projectName, String applicationId)
    {
        return duplicateLaunchPreflight(snapshotLaunches(projectName, applicationId));
    }

    static JsonObject duplicateLaunchPreflight(ILaunch[] launches, String projectName, String applicationId)
    {
        return duplicateLaunchPreflight(snapshotLaunches(launches, projectName, applicationId));
    }

    private static JsonObject duplicateLaunchPreflight(JsonObject latestSnapshot)
    {
        JsonArray matchingLaunches = new JsonArray();
        JsonArray launches = latestSnapshot.has("launches") ? latestSnapshot.getAsJsonArray("launches") //$NON-NLS-1$ //$NON-NLS-2$
                : new JsonArray();
        for (JsonElement launchElement : launches)
        {
            JsonObject launch = launchElement.getAsJsonObject();
            if (isActiveLaunch(launch))
            {
                matchingLaunches.add(launch);
            }
        }

        if (matchingLaunches.size() > 0)
        {
            return errorObject("Matching RuntimeClient debug launch is already running", //$NON-NLS-1$
                    "debug_launch_already_running") //$NON-NLS-1$
                            .put("matchingLaunches", matchingLaunches) //$NON-NLS-1$
                            .put("relatedReasons", relatedDuplicateReasons()) //$NON-NLS-1$
                            .put("operatorChoices", operatorChoices()) //$NON-NLS-1$
                            .put("latestLaunchSnapshot", latestSnapshot) //$NON-NLS-1$
                            .build();
        }

        JsonObject object = new JsonObject();
        object.addProperty("success", true); //$NON-NLS-1$
        object.addProperty("duplicate", false); //$NON-NLS-1$
        object.add("latestLaunchSnapshot", latestSnapshot); //$NON-NLS-1$
        return object;
    }

    public static String waitForSupportedSession(String projectName, String applicationId, int requestedTimeoutSeconds)
    {
        return waitForSupportedSession(
                () -> JsonParser.parseString(RuntimeDebugModelBridge.listSessions(projectName, applicationId))
                        .getAsJsonObject(),
                () -> snapshotLaunches(projectName, applicationId), requestedTimeoutSeconds, POLL_INTERVAL_MILLIS);
    }

    static String waitForSupportedSession(SnapshotSupplier sessionSupplier, SnapshotSupplier launchSnapshotSupplier,
            int requestedTimeoutSeconds, long pollIntervalMillis)
    {
        int timeoutSeconds = normalizeTimeoutSeconds(requestedTimeoutSeconds, DEFAULT_WAIT_TIMEOUT_SECONDS,
                MAX_WAIT_TIMEOUT_SECONDS);
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        JsonObject latestLaunchSnapshot = launchSnapshotSupplier.get();

        while (System.currentTimeMillis() <= deadline)
        {
            JsonObject sessions = sessionSupplier.get();
            if (sessions.has("success") && sessions.get("success").getAsBoolean() //$NON-NLS-1$ //$NON-NLS-2$
                    && sessions.has("count") && sessions.get("count").getAsInt() > 0) //$NON-NLS-1$ //$NON-NLS-2$
            {
                latestLaunchSnapshot = launchSnapshotSupplier.get();
                sessions.addProperty("outcome", "supported_session_visible"); //$NON-NLS-1$ //$NON-NLS-2$
                sessions.addProperty("phase", "supported_thread_visible"); //$NON-NLS-1$ //$NON-NLS-2$
                sessions.addProperty("timedOut", false); //$NON-NLS-1$
                sessions.add("latestLaunchSnapshot", latestLaunchSnapshot); //$NON-NLS-1$
                return sessions.toString();
            }

            latestLaunchSnapshot = launchSnapshotSupplier.get();
            try
            {
                Thread.sleep(pollIntervalMillis);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return ToolResult.error("Interrupted while waiting for supported debug session") //$NON-NLS-1$
                        .put("reason", "debug_session_wait_interrupted") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("latestLaunchSnapshot", latestLaunchSnapshot) //$NON-NLS-1$
                        .toJson();
            }
        }

        return ToolResult.error("Timed out waiting for supported debug session") //$NON-NLS-1$
                .put("reason", "debug_session_wait_timeout") //$NON-NLS-1$ //$NON-NLS-2$
                .put("timedOut", true) //$NON-NLS-1$
                .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                .put("latestLaunchSnapshot", latestLaunchSnapshot) //$NON-NLS-1$
                .put("operatorChoices", operatorChoices()) //$NON-NLS-1$
                .toJson();
    }

    public static JsonObject launchDebugConfigurationBounded(ILaunchConfiguration configuration,
            int requestedTimeoutSeconds)
    {
        Display display = Display.getDefault();
        return launchDebugConfigurationBounded(configuration, requestedTimeoutSeconds,
                display != null ? new DisplayUiLaunchExecutor(display) : null);
    }

    static JsonObject launchDebugConfigurationBounded(ILaunchConfiguration configuration, int requestedTimeoutSeconds,
            UiLaunchExecutor uiLaunchExecutor)
    {
        int timeoutSeconds = normalizeTimeoutSeconds(requestedTimeoutSeconds, DEFAULT_LAUNCH_TIMEOUT_SECONDS,
                MAX_LAUNCH_TIMEOUT_SECONDS);
        if (uiLaunchExecutor == null || uiLaunchExecutor.isDisposed())
        {
            return launchDirect(configuration, timeoutSeconds, "direct_launch_no_display"); //$NON-NLS-1$
        }
        if (uiLaunchExecutor.isCurrentThread())
        {
            return errorObject("Cannot bounded-launch from the SWT UI thread", //$NON-NLS-1$
                    "debug_launch_ui_thread_request") //$NON-NLS-1$
                            .put("accepted", false) //$NON-NLS-1$
                            .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                            .build();
        }

        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean cancelledBeforeEntry = new AtomicBoolean(false);
        AtomicBoolean callbackEntered = new AtomicBoolean(false);
        AtomicBoolean callbackCompleted = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();

        uiLaunchExecutor.asyncExec(() -> {
            if (cancelledBeforeEntry.get() || uiLaunchExecutor.isDisposed())
            {
                callbackCompleted.set(true);
                completed.countDown();
                return;
            }
            callbackEntered.set(true);
            try
            {
                DebugUITools.launch(configuration, ILaunchManager.DEBUG_MODE);
            }
            catch (Throwable e)
            {
                error.set(e);
            }
            finally
            {
                callbackCompleted.set(true);
                completed.countDown();
            }
        });

        try
        {
            boolean done = completed.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!done)
            {
                boolean entered = callbackEntered.get();
                if (!entered)
                {
                    cancelledBeforeEntry.set(true);
                }
                return errorObject("Debug launch UI callback did not finish inside the bounded call window", //$NON-NLS-1$
                        "debug_launch_ui_blocked") //$NON-NLS-1$
                                .put("accepted", entered) //$NON-NLS-1$
                                .put("uiCallbackEntered", entered) //$NON-NLS-1$
                                .put("cancelledBeforeLaunch", !entered) //$NON-NLS-1$
                                .put("inFlight", entered && !callbackCompleted.get()) //$NON-NLS-1$
                                .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                                .put("operatorChoices", operatorChoices()) //$NON-NLS-1$
                                .build();
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return errorObject("Interrupted while launching debug configuration", //$NON-NLS-1$
                    "debug_launch_interrupted") //$NON-NLS-1$
                            .put("accepted", callbackEntered.get()) //$NON-NLS-1$
                            .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                            .build();
        }

        Throwable failure = error.get();
        if (failure != null)
        {
            return errorObject("Failed to launch debug configuration: " + failure.getMessage(), //$NON-NLS-1$
                    "debug_launch_failed") //$NON-NLS-1$
                            .put("accepted", false) //$NON-NLS-1$
                            .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                            .build();
        }

        return acceptedLaunchObject("ui_launch_completed", timeoutSeconds); //$NON-NLS-1$
    }

    public static String terminateLaunch(String projectName, String applicationId, String launchId)
    {
        if (launchId != null && !launchId.isEmpty())
        {
            ILaunch launch = LAUNCHES.get(launchId);
            if (launch == null)
            {
                return ToolResult.error("Unknown or stale launchId: " + launchId) //$NON-NLS-1$
                        .put("reason", "stale_launch_id") //$NON-NLS-1$ //$NON-NLS-2$
                        .toJson();
            }
            if (!isLaunchKnownToManager(launch))
            {
                LAUNCHES.remove(launchId);
                return ToolResult.error("Unknown or stale launchId: " + launchId) //$NON-NLS-1$
                        .put("reason", "stale_launch_id") //$NON-NLS-1$ //$NON-NLS-2$
                        .toJson();
            }
            if (!matchesLaunchIdentity(launch, projectName, applicationId))
            {
                return ToolResult.error("launchId does not match requested project/application") //$NON-NLS-1$
                        .put("reason", "launch_target_mismatch") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("launchId", launchId) //$NON-NLS-1$
                        .toJson();
            }
            return terminateMatchedLaunch(projectName, applicationId, launchId, launch);
        }

        JsonObject snapshot = snapshotLaunches(projectName, applicationId);
        return terminateLaunchFromSnapshot(projectName, applicationId, snapshot);
    }

    static String terminateLaunch(ILaunch[] launches, String projectName, String applicationId, String launchId)
    {
        if (launchId != null && !launchId.isEmpty())
        {
            snapshotLaunches(launches, projectName, applicationId);
            ILaunch launch = LAUNCHES.get(launchId);
            if (launch == null)
            {
                return ToolResult.error("Unknown or stale launchId: " + launchId) //$NON-NLS-1$
                        .put("reason", "stale_launch_id") //$NON-NLS-1$ //$NON-NLS-2$
                        .toJson();
            }
            if (!matchesLaunchIdentity(launch, projectName, applicationId))
            {
                return ToolResult.error("launchId does not match requested project/application") //$NON-NLS-1$
                        .put("reason", "launch_target_mismatch") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("launchId", launchId) //$NON-NLS-1$
                        .toJson();
            }
            return terminateMatchedLaunch(projectName, applicationId, launchId, launch);
        }
        return terminateLaunchFromSnapshot(projectName, applicationId, snapshotLaunches(launches, projectName,
                applicationId));
    }

    private static String terminateLaunchFromSnapshot(String projectName, String applicationId, JsonObject snapshot)
    {
        JsonArray launches = snapshot.has("launches") ? snapshot.getAsJsonArray("launches") : new JsonArray(); //$NON-NLS-1$ //$NON-NLS-2$
        if (launches.size() == 0)
        {
            return ToolResult.error("No matching RuntimeClient debug launch found") //$NON-NLS-1$
                    .put("reason", "no_matching_launch") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("latestLaunchSnapshot", snapshot) //$NON-NLS-1$
                    .toJson();
        }
        if (launches.size() > 1)
        {
            JsonArray launchIds = new JsonArray();
            for (JsonElement launchElement : launches)
            {
                JsonObject launch = launchElement.getAsJsonObject();
                launchIds.add(stringProperty(launch, "launchId")); //$NON-NLS-1$
            }
            return ToolResult.error("Multiple matching RuntimeClient debug launches found; pass launchId") //$NON-NLS-1$
                    .put("reason", "multiple_matching_launches") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("launchIds", launchIds) //$NON-NLS-1$
                    .put("latestLaunchSnapshot", snapshot) //$NON-NLS-1$
                    .toJson();
        }

        String selectedLaunchId = stringProperty(launches.get(0).getAsJsonObject(), "launchId"); //$NON-NLS-1$
        ILaunch launch = LAUNCHES.get(selectedLaunchId);
        if (launch == null)
        {
            return ToolResult.error("Selected launch disappeared before termination") //$NON-NLS-1$
                    .put("reason", "stale_launch_id") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("launchId", selectedLaunchId) //$NON-NLS-1$
                    .put("latestLaunchSnapshot", snapshot) //$NON-NLS-1$
                    .toJson();
        }
        return terminateMatchedLaunch(projectName, applicationId, selectedLaunchId, launch);
    }

    static JsonObject snapshotLaunches(ILaunch[] launches, String projectFilter, String applicationFilter)
    {
        long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
        String snapshotIdString = "launch-snapshot-" + Long.toString(snapshotId, 36); //$NON-NLS-1$
        JsonArray launchArray = new JsonArray();
        JsonArray unsupportedLaunches = new JsonArray();
        JsonArray filteredLaunches = new JsonArray();

        for (int i = 0; i < launches.length; i++)
        {
            LaunchClassification classification = classify(launches[i], projectFilter, applicationFilter,
                    snapshotIdString, i);
            if (classification.filtered())
            {
                filteredLaunches.add(classification.snapshot());
            }
            else if (classification.runtimeClientDebugLaunch())
            {
                launchArray.add(classification.snapshot());
                LAUNCHES.put(classification.launchId(), launches[i]);
            }
            else
            {
                unsupportedLaunches.add(classification.snapshot());
            }
        }

        JsonObject object = new JsonObject();
        object.addProperty("success", true); //$NON-NLS-1$
        object.addProperty("snapshotId", snapshotIdString); //$NON-NLS-1$
        object.addProperty("projectName", nullToEmpty(projectFilter)); //$NON-NLS-1$
        object.addProperty("applicationId", nullToEmpty(applicationFilter)); //$NON-NLS-1$
        object.addProperty("count", launchArray.size()); //$NON-NLS-1$
        object.addProperty("unsupportedCount", unsupportedLaunches.size()); //$NON-NLS-1$
        object.addProperty("filteredCount", filteredLaunches.size()); //$NON-NLS-1$
        object.add("launches", launchArray); //$NON-NLS-1$
        object.add("unsupportedLaunches", unsupportedLaunches); //$NON-NLS-1$
        object.add("filteredLaunches", filteredLaunches); //$NON-NLS-1$
        return object;
    }

    private static LaunchClassification classify(ILaunch launch, String projectFilter, String applicationFilter,
            String snapshotId, int index)
    {
        JsonArray unsupportedReasons = new JsonArray();
        JsonArray filterReasons = new JsonArray();
        ILaunchConfiguration configuration = launch.getLaunchConfiguration();
        String configurationName = configuration != null ? configuration.getName() : ""; //$NON-NLS-1$
        String typeId = ""; //$NON-NLS-1$
        String projectName = ""; //$NON-NLS-1$
        String applicationId = ""; //$NON-NLS-1$

        if (configuration == null)
        {
            unsupportedReasons.add("launch_configuration_unavailable"); //$NON-NLS-1$
        }
        else
        {
            try
            {
                ILaunchConfigurationType type = configuration.getType();
                typeId = type != null ? nullToEmpty(type.getIdentifier()) : ""; //$NON-NLS-1$
                projectName = configuration.getAttribute(ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
                applicationId = configuration.getAttribute(ATTR_APPLICATION_ID, ""); //$NON-NLS-1$
            }
            catch (CoreException e)
            {
                Activator.logError("Failed to read launch configuration for lifecycle diagnostics", e); //$NON-NLS-1$
                unsupportedReasons.add("launch_configuration_unreadable"); //$NON-NLS-1$
            }
        }

        boolean runtimeClient = RUNTIME_CLIENT_LAUNCH_CONFIG_TYPE_ID.equals(typeId);
        boolean debugMode = ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode());
        if (!runtimeClient)
        {
            unsupportedReasons.add("unsupported_launch_configuration_type"); //$NON-NLS-1$
        }
        if (!debugMode)
        {
            unsupportedReasons.add("not_debug_mode"); //$NON-NLS-1$
        }
        if (projectName.isEmpty() || applicationId.isEmpty())
        {
            unsupportedReasons.add("project_application_unavailable"); //$NON-NLS-1$
        }

        if (projectFilter != null && !projectFilter.isEmpty() && !projectFilter.equals(projectName))
        {
            filterReasons.add("projectName_mismatch"); //$NON-NLS-1$
        }
        if (applicationFilter != null && !applicationFilter.isEmpty() && !applicationFilter.equals(applicationId))
        {
            filterReasons.add("applicationId_mismatch"); //$NON-NLS-1$
        }

        JsonArray processes = toProcessesJson(launch);
        JsonArray debugTargets = toTargetsJson(launch);
        JsonArray threads = toThreadsJson(launch);
        if (processes.size() == 0)
        {
            unsupportedReasons.add("runtime_process_unavailable"); //$NON-NLS-1$
        }
        if (debugTargets.size() == 0)
        {
            unsupportedReasons.add("debug_target_unavailable"); //$NON-NLS-1$
        }
        if (threads.size() == 0)
        {
            unsupportedReasons.add("supported_thread_unavailable"); //$NON-NLS-1$
        }

        String launchId = launchId(projectName, applicationId, configurationName, launch);
        JsonObject object = new JsonObject();
        object.addProperty("launchId", launchId); //$NON-NLS-1$
        object.addProperty("snapshotId", snapshotId); //$NON-NLS-1$
        object.addProperty("launchMode", nullToEmpty(launch.getLaunchMode())); //$NON-NLS-1$
        object.addProperty("state", stateOf(launch)); //$NON-NLS-1$
        object.addProperty("launchClass", launch.getClass().getName()); //$NON-NLS-1$
        object.addProperty("supported", runtimeClient && debugMode && threads.size() > 0); //$NON-NLS-1$
        object.add("launchConfiguration", launchConfigurationJson(configurationName, typeId, projectName, //$NON-NLS-1$
                applicationId));
        object.add("lifecycle", lifecycleJson(configuration != null, processes.size() > 0, //$NON-NLS-1$
                debugTargets.size() > 0, threads.size() > 0, launch.isTerminated()));
        object.add("processes", processes); //$NON-NLS-1$
        object.add("debugTargets", debugTargets); //$NON-NLS-1$
        object.add("threads", threads); //$NON-NLS-1$
        object.add("unsupportedReasons", unsupportedReasons); //$NON-NLS-1$
        object.add("filterReasons", filterReasons); //$NON-NLS-1$

        return new LaunchClassification(launchId, filterReasons.size() > 0, runtimeClient && debugMode, object);
    }

    private static JsonObject launchConfigurationJson(String name, String typeId, String projectName,
            String applicationId)
    {
        JsonObject object = new JsonObject();
        object.addProperty("name", nullToEmpty(name)); //$NON-NLS-1$
        object.addProperty("typeId", nullToEmpty(typeId)); //$NON-NLS-1$
        object.addProperty("projectName", nullToEmpty(projectName)); //$NON-NLS-1$
        object.addProperty("applicationId", nullToEmpty(applicationId)); //$NON-NLS-1$
        return object;
    }

    private static JsonObject lifecycleJson(boolean launchConfigStarted, boolean runtimeProcessStarted,
            boolean debugTargetAttached, boolean supportedThreadVisible, boolean launchTerminated)
    {
        JsonObject object = new JsonObject();
        object.addProperty("launch_config_started", phase(launchConfigStarted, false, false)); //$NON-NLS-1$
        object.addProperty("runtime_process_started", phase(runtimeProcessStarted, launchConfigStarted, //$NON-NLS-1$
                launchTerminated));
        object.addProperty("debug_target_attached", phase(debugTargetAttached, runtimeProcessStarted, //$NON-NLS-1$
                launchTerminated));
        object.addProperty("supported_thread_visible", phase(supportedThreadVisible, debugTargetAttached, //$NON-NLS-1$
                launchTerminated));
        return object;
    }

    private static String phase(boolean visible, boolean previousVisible, boolean terminated)
    {
        if (visible)
        {
            return "true"; //$NON-NLS-1$
        }
        if (terminated)
        {
            return "false"; //$NON-NLS-1$
        }
        return previousVisible ? "pending" : "false"; //$NON-NLS-1$ //$NON-NLS-2$
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
            object.addProperty("canTerminate", target.canTerminate()); //$NON-NLS-1$
            targets.add(object);
        }
        return targets;
    }

    private static JsonArray toThreadsJson(ILaunch launch)
    {
        JsonArray threads = new JsonArray();
        for (IDebugTarget target : launch.getDebugTargets())
        {
            try
            {
                for (IThread thread : target.getThreads())
                {
                    JsonObject object = new JsonObject();
                    object.addProperty("name", safeThreadName(thread)); //$NON-NLS-1$
                    object.addProperty("className", thread.getClass().getName()); //$NON-NLS-1$
                    object.addProperty("modelIdentifier", nullToEmpty(thread.getModelIdentifier())); //$NON-NLS-1$
                    object.addProperty("state", stateOf(thread)); //$NON-NLS-1$
                    object.addProperty("canTerminate", thread.canTerminate()); //$NON-NLS-1$
                    threads.add(object);
                }
            }
            catch (DebugException e)
            {
                Activator.logError("Failed to read debug target threads for lifecycle diagnostics", e); //$NON-NLS-1$
            }
        }
        return threads;
    }

    private static JsonArray toProcessesJson(ILaunch launch)
    {
        JsonArray processes = new JsonArray();
        for (IProcess process : launch.getProcesses())
        {
            JsonObject object = new JsonObject();
            JsonArray reasons = new JsonArray();
            object.addProperty("label", nullToEmpty(process.getLabel())); //$NON-NLS-1$
            object.addProperty("className", process.getClass().getName()); //$NON-NLS-1$
            object.addProperty("terminated", process.isTerminated()); //$NON-NLS-1$
            object.addProperty("state", process.isTerminated() ? "terminated" : "running"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            object.addProperty("canTerminate", process.canTerminate()); //$NON-NLS-1$
            addAttribute(object, "processType", process, IProcess.ATTR_PROCESS_TYPE); //$NON-NLS-1$
            addAttribute(object, "processLabel", process, IProcess.ATTR_PROCESS_LABEL); //$NON-NLS-1$

            String pid = safeProcessAttribute(process, IProcess.ATTR_PROCESS_ID);
            object.addProperty("pidAvailable", !pid.isEmpty()); //$NON-NLS-1$
            if (!pid.isEmpty())
            {
                object.addProperty("pid", pid); //$NON-NLS-1$
            }

            String commandLine = safeProcessAttribute(process, IProcess.ATTR_CMDLINE);
            object.addProperty("commandLineAvailable", !commandLine.isEmpty()); //$NON-NLS-1$
            if (commandLine.isEmpty())
            {
                reasons.add("process_details_unavailable"); //$NON-NLS-1$
            }
            else
            {
                RedactedCommandLine redactedCommandLine = redactCommandLine(commandLine);
                object.addProperty("sanitizedCommandLine", redactedCommandLine.value()); //$NON-NLS-1$
                object.addProperty("redacted", redactedCommandLine.redacted()); //$NON-NLS-1$
                object.add("redactionReasons", toJsonArray(redactedCommandLine.reasons())); //$NON-NLS-1$
                addCommandParts(object, redactedCommandLine.value());
                object.add("debuggerUrl", debuggerUrlJson(commandLine)); //$NON-NLS-1$
            }

            if (!object.has("debuggerUrl")) //$NON-NLS-1$
            {
                JsonObject debuggerUrl = new JsonObject();
                debuggerUrl.addProperty("present", false); //$NON-NLS-1$
                debuggerUrl.addProperty("source", "unavailable"); //$NON-NLS-1$ //$NON-NLS-2$
                object.add("debuggerUrl", debuggerUrl); //$NON-NLS-1$
            }
            object.add("reasons", reasons); //$NON-NLS-1$
            processes.add(object);
        }
        return processes;
    }

    private static JsonObject launchDirect(ILaunchConfiguration configuration, int timeoutSeconds, String outcome)
    {
        try
        {
            configuration.launch(ILaunchManager.DEBUG_MODE, new NullProgressMonitor());
            return acceptedLaunchObject(outcome, timeoutSeconds);
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to launch debug configuration directly", e); //$NON-NLS-1$
            return errorObject("Failed to launch debug configuration: " + e.getMessage(), //$NON-NLS-1$
                    "debug_launch_failed") //$NON-NLS-1$
                            .put("accepted", false) //$NON-NLS-1$
                            .put("timeoutSeconds", timeoutSeconds) //$NON-NLS-1$
                            .build();
        }
    }

    private static String terminateMatchedLaunch(String projectName, String applicationId, String launchId,
            ILaunch launch)
    {
        JsonArray terminated = new JsonArray();
        JsonArray skipped = new JsonArray();
        JsonArray failed = new JsonArray();
        JsonArray processIds = processIds(launch);

        terminateElement("launch", launchId, launch, terminated, skipped, failed); //$NON-NLS-1$
        int processIndex = 0;
        for (IProcess process : launch.getProcesses())
        {
            terminateElement("process", Integer.toString(processIndex), process, terminated, skipped, failed); //$NON-NLS-1$
            processIndex++;
        }
        int targetIndex = 0;
        for (IDebugTarget target : launch.getDebugTargets())
        {
            terminateElement("debugTarget", Integer.toString(targetIndex), target, terminated, skipped, failed); //$NON-NLS-1$
            targetIndex++;
        }

        boolean hasFailure = failed.size() > 0;
        JsonObject latestLaunchSnapshot = snapshotLaunches(projectName, applicationId);
        return ToolResult.success()
                .put("terminated", !hasFailure && terminated.size() > 0) //$NON-NLS-1$
                .put("launchId", launchId) //$NON-NLS-1$
                .put("projectName", projectName) //$NON-NLS-1$
                .put("applicationId", applicationId) //$NON-NLS-1$
                .put("terminationMethod", "eclipse_ITerminate") //$NON-NLS-1$ //$NON-NLS-2$
                .put("processIds", processIds) //$NON-NLS-1$
                .put("terminatedElements", terminated) //$NON-NLS-1$
                .put("skippedElements", skipped) //$NON-NLS-1$
                .put("failedElements", failed) //$NON-NLS-1$
                .put("finalObservedState", finalObservedState(latestLaunchSnapshot, launch)) //$NON-NLS-1$
                .put("latestLaunchSnapshot", latestLaunchSnapshot) //$NON-NLS-1$
                .toJson();
    }

    private static void terminateElement(String kind, String id, ITerminate terminate, JsonArray terminated,
            JsonArray skipped, JsonArray failed)
    {
        JsonObject object = new JsonObject();
        object.addProperty("kind", kind); //$NON-NLS-1$
        object.addProperty("id", id); //$NON-NLS-1$
        object.addProperty("className", terminate.getClass().getName()); //$NON-NLS-1$
        object.addProperty("terminationMethod", "eclipse_ITerminate"); //$NON-NLS-1$ //$NON-NLS-2$
        if (terminate instanceof IProcess)
        {
            String pid = safeProcessAttribute((IProcess)terminate, IProcess.ATTR_PROCESS_ID);
            if (!pid.isEmpty())
            {
                object.addProperty("pid", pid); //$NON-NLS-1$
            }
        }
        try
        {
            if (terminate.isTerminated())
            {
                object.addProperty("reason", "already_terminated"); //$NON-NLS-1$ //$NON-NLS-2$
                skipped.add(object);
                return;
            }
            if (!terminate.canTerminate())
            {
                object.addProperty("reason", "terminate_not_supported"); //$NON-NLS-1$ //$NON-NLS-2$
                skipped.add(object);
                return;
            }
            terminate.terminate();
            object.addProperty("status", "terminated"); //$NON-NLS-1$ //$NON-NLS-2$
            terminated.add(object);
        }
        catch (DebugException e)
        {
            object.addProperty("error", e.getMessage()); //$NON-NLS-1$
            failed.add(object);
        }
    }

    private static boolean matchesLaunchIdentity(ILaunch launch, String projectName, String applicationId)
    {
        ILaunchConfiguration configuration = launch.getLaunchConfiguration();
        if (configuration == null || !ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode()))
        {
            return false;
        }
        try
        {
            ILaunchConfigurationType type = configuration.getType();
            if (type == null || !RUNTIME_CLIENT_LAUNCH_CONFIG_TYPE_ID.equals(type.getIdentifier()))
            {
                return false;
            }
            return projectName.equals(configuration.getAttribute(ATTR_PROJECT_NAME, "")) //$NON-NLS-1$
                    && applicationId.equals(configuration.getAttribute(ATTR_APPLICATION_ID, "")); //$NON-NLS-1$
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to validate launch identity before termination", e); //$NON-NLS-1$
            return false;
        }
    }

    private static boolean isLaunchKnownToManager(ILaunch launch)
    {
        ILaunchManager launchManager = getLaunchManager();
        if (launchManager == null)
        {
            return false;
        }
        for (ILaunch currentLaunch : launchManager.getLaunches())
        {
            if (currentLaunch == launch)
            {
                return true;
            }
        }
        return false;
    }

    private static JsonObject acceptedLaunchObject(String outcome, int timeoutSeconds)
    {
        JsonObject object = new JsonObject();
        object.addProperty("success", true); //$NON-NLS-1$
        object.addProperty("accepted", true); //$NON-NLS-1$
        object.addProperty("outcome", outcome); //$NON-NLS-1$
        object.addProperty("phase", "launch_config_started"); //$NON-NLS-1$ //$NON-NLS-2$
        object.addProperty("timeoutSeconds", timeoutSeconds); //$NON-NLS-1$
        return object;
    }

    public static void enrichLaunchResult(JsonObject launchResult, JsonObject latestLaunchSnapshot)
    {
        JsonObject launch = firstLaunch(latestLaunchSnapshot);
        JsonObject phases = launch.has("lifecycle") //$NON-NLS-1$
                ? launch.getAsJsonObject("lifecycle").deepCopy() //$NON-NLS-1$
                : defaultLaunchPhases(isAccepted(launchResult));
        launchResult.add("phases", phases); //$NON-NLS-1$
        launchResult.add("launch", launch.deepCopy()); //$NON-NLS-1$
        if (!launchResult.has("operatorChoices")) //$NON-NLS-1$
        {
            launchResult.add("operatorChoices", acceptedLaunchChoices()); //$NON-NLS-1$
        }
        launchResult.add("latestLaunchSnapshot", latestLaunchSnapshot); //$NON-NLS-1$
    }

    static JsonArray unsupportedLaunchesForSessionDiagnostics(JsonObject launchDiagnostics)
    {
        JsonArray diagnostics = new JsonArray();
        if (launchDiagnostics == null)
        {
            return diagnostics;
        }
        JsonArray unsupported = launchDiagnostics.has("unsupportedLaunches") //$NON-NLS-1$
                ? launchDiagnostics.getAsJsonArray("unsupportedLaunches") //$NON-NLS-1$
                : new JsonArray();
        for (JsonElement element : unsupported)
        {
            diagnostics.add(element.deepCopy());
        }

        JsonArray launches = launchDiagnostics.has("launches") //$NON-NLS-1$
                ? launchDiagnostics.getAsJsonArray("launches") //$NON-NLS-1$
                : new JsonArray();
        for (JsonElement element : launches)
        {
            JsonObject launch = element.getAsJsonObject();
            boolean supported = launch.has("supported") && launch.get("supported").getAsBoolean(); //$NON-NLS-1$ //$NON-NLS-2$
            JsonArray reasons = launch.has("unsupportedReasons") //$NON-NLS-1$
                    ? launch.getAsJsonArray("unsupportedReasons") //$NON-NLS-1$
                    : new JsonArray();
            if (!supported || reasons.size() > 0)
            {
                diagnostics.add(launch.deepCopy());
            }
        }
        return diagnostics;
    }

    private static boolean isActiveLaunch(JsonObject launch)
    {
        if (!"terminated".equals(stringProperty(launch, "state"))) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return true;
        }
        if (hasNonTerminatedProcess(launch))
        {
            return true;
        }
        JsonArray targets = launch.has("debugTargets") ? launch.getAsJsonArray("debugTargets") //$NON-NLS-1$ //$NON-NLS-2$
                : new JsonArray();
        for (JsonElement targetElement : targets)
        {
            JsonObject target = targetElement.getAsJsonObject();
            if (!"terminated".equals(stringProperty(target, "state"))) //$NON-NLS-1$ //$NON-NLS-2$
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNonTerminatedProcess(JsonObject launch)
    {
        JsonArray processes = launch.has("processes") ? launch.getAsJsonArray("processes") : new JsonArray(); //$NON-NLS-1$ //$NON-NLS-2$
        for (JsonElement processElement : processes)
        {
            JsonObject process = processElement.getAsJsonObject();
            if (!process.has("terminated") || !process.get("terminated").getAsBoolean()) //$NON-NLS-1$ //$NON-NLS-2$
            {
                return true;
            }
        }
        return false;
    }

    private static JsonObject firstLaunch(JsonObject snapshot)
    {
        if (snapshot != null && snapshot.has("launches")) //$NON-NLS-1$
        {
            JsonArray launches = snapshot.getAsJsonArray("launches"); //$NON-NLS-1$
            if (launches.size() > 0)
            {
                return launches.get(0).getAsJsonObject();
            }
        }
        return new JsonObject();
    }

    private static JsonObject defaultLaunchPhases(boolean accepted)
    {
        JsonObject phases = new JsonObject();
        phases.addProperty("launch_config_started", accepted ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        phases.addProperty("runtime_process_started", accepted ? "pending" : "false"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        phases.addProperty("debug_target_attached", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        phases.addProperty("supported_thread_visible", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        return phases;
    }

    private static boolean isAccepted(JsonObject launchResult)
    {
        return launchResult.has("accepted") && launchResult.get("accepted").getAsBoolean(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JsonObject finalObservedState(JsonObject latestLaunchSnapshot, ILaunch selectedLaunch)
    {
        JsonObject object = new JsonObject();
        JsonObject launch = firstLaunch(latestLaunchSnapshot);
        if (launch.entrySet().isEmpty())
        {
            object.addProperty("visible", false); //$NON-NLS-1$
            object.addProperty("state", stateOf(selectedLaunch)); //$NON-NLS-1$
            object.addProperty("source", "selected_launch_after_termination"); //$NON-NLS-1$ //$NON-NLS-2$
            return object;
        }
        object.addProperty("visible", true); //$NON-NLS-1$
        object.addProperty("state", stringProperty(launch, "state")); //$NON-NLS-1$ //$NON-NLS-2$
        object.addProperty("source", "latest_launch_snapshot"); //$NON-NLS-1$ //$NON-NLS-2$
        if (launch.has("lifecycle")) //$NON-NLS-1$
        {
            object.add("lifecycle", launch.getAsJsonObject("lifecycle").deepCopy()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        object.add("launch", launch.deepCopy()); //$NON-NLS-1$
        return object;
    }

    private static JsonArray processIds(ILaunch launch)
    {
        JsonArray processIds = new JsonArray();
        for (IProcess process : launch.getProcesses())
        {
            String pid = safeProcessAttribute(process, IProcess.ATTR_PROCESS_ID);
            if (!pid.isEmpty())
            {
                processIds.add(pid);
            }
        }
        return processIds;
    }

    private static void addAttribute(JsonObject object, String property, IProcess process, String attribute)
    {
        String value = safeProcessAttribute(process, attribute);
        if (!value.isEmpty())
        {
            object.addProperty(property, value);
        }
    }

    private static String safeProcessAttribute(IProcess process, String attribute)
    {
        try
        {
            return nullToEmpty(process.getAttribute(attribute));
        }
        catch (RuntimeException e)
        {
            return ""; //$NON-NLS-1$
        }
    }

    private static RedactedCommandLine redactCommandLine(String commandLine)
    {
        List<String> tokens = tokenize(commandLine);
        List<String> sanitized = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        boolean redactNext = false;
        for (String token : tokens)
        {
            String stripped = stripQuotes(token);
            if (redactNext)
            {
                sanitized.add("[REDACTED]"); //$NON-NLS-1$
                addReason(reasons, "credential_argument_redacted"); //$NON-NLS-1$
                redactNext = false;
                continue;
            }

            String normalized = stripped.toLowerCase(Locale.ROOT);
            int separator = Math.max(stripped.indexOf('='), stripped.indexOf(':'));
            if (separator > 0 && isSensitiveName(normalized.substring(0, separator)))
            {
                sanitized.add(token.substring(0, Math.min(token.length(), separator + 1)) + "[REDACTED]"); //$NON-NLS-1$
                addReason(reasons, "credential_argument_redacted"); //$NON-NLS-1$
                continue;
            }
            if (isSensitiveStandaloneFlag(normalized))
            {
                sanitized.add(token);
                redactNext = true;
                continue;
            }

            sanitized.add(token);
        }
        if (redactNext)
        {
            addReason(reasons, "dangling_sensitive_flag"); //$NON-NLS-1$
        }
        return new RedactedCommandLine(String.join(" ", sanitized), !reasons.isEmpty(), reasons); //$NON-NLS-1$
    }

    private static boolean isSensitiveStandaloneFlag(String token)
    {
        return "/p".equals(token) || "/password".equals(token) || "/n".equals(token) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || "/uc".equals(token) || "--password".equals(token) || "--token".equals(token) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || "--secret".equals(token); //$NON-NLS-1$
    }

    private static boolean isSensitiveName(String name)
    {
        return name.contains("password") || name.contains("passwd") || name.contains("secret") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || name.contains("token") || "/p".equals(name) || "/n".equals(name) || "/uc".equals(name); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static void addCommandParts(JsonObject object, String sanitizedCommandLine)
    {
        List<String> tokens = tokenize(sanitizedCommandLine);
        if (tokens.isEmpty())
        {
            return;
        }
        object.addProperty("executable", stripQuotes(tokens.get(0))); //$NON-NLS-1$
        JsonArray arguments = new JsonArray();
        for (int i = 1; i < tokens.size(); i++)
        {
            arguments.add(stripQuotes(tokens.get(i)));
        }
        object.add("arguments", arguments); //$NON-NLS-1$
    }

    private static JsonObject debuggerUrlJson(String commandLine)
    {
        JsonObject object = new JsonObject();
        Matcher matcher = DEBUGGER_URL_PATTERN.matcher(commandLine);
        if (matcher.find())
        {
            object.addProperty("present", true); //$NON-NLS-1$
            object.addProperty("source", "command_line"); //$NON-NLS-1$ //$NON-NLS-2$
            object.addProperty("value", sanitizeDebuggerUrl(stripQuotes(matcher.group(1)))); //$NON-NLS-1$
        }
        else
        {
            object.addProperty("present", false); //$NON-NLS-1$
            object.addProperty("source", "command_line"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return object;
    }

    private static String sanitizeDebuggerUrl(String value)
    {
        return value.replaceAll("(?i)(password|passwd|token|secret)=([^&\\s]+)", "$1=[REDACTED]") //$NON-NLS-1$ //$NON-NLS-2$
                .replaceAll("://([^/@]+)@", "://[REDACTED]@"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static List<String> tokenize(String value)
    {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < value.length(); i++)
        {
            char ch = value.charAt(i);
            if (ch == '"')
            {
                inQuotes = !inQuotes;
                current.append(ch);
            }
            else if (Character.isWhitespace(ch) && !inQuotes)
            {
                if (current.length() > 0)
                {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            }
            else
            {
                current.append(ch);
            }
        }
        if (current.length() > 0)
        {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripQuotes(String value)
    {
        if (value != null && value.length() >= 2 && value.charAt(0) == '"'
                && value.charAt(value.length() - 1) == '"')
        {
            return value.substring(1, value.length() - 1);
        }
        return nullToEmpty(value);
    }

    private static void addReason(List<String> reasons, String reason)
    {
        if (!reasons.contains(reason))
        {
            reasons.add(reason);
        }
    }

    private static JsonArray operatorChoices()
    {
        JsonArray choices = new JsonArray();
        choices.add("list_debug_sessions"); //$NON-NLS-1$
        choices.add("wait_debug_session"); //$NON-NLS-1$
        choices.add("terminate_debug_launch"); //$NON-NLS-1$
        choices.add("manual_cleanup_in_edt"); //$NON-NLS-1$
        return choices;
    }

    private static JsonArray acceptedLaunchChoices()
    {
        JsonArray choices = new JsonArray();
        choices.add("list_debug_sessions"); //$NON-NLS-1$
        choices.add("wait_debug_session"); //$NON-NLS-1$
        choices.add("list_debug_launches"); //$NON-NLS-1$
        return choices;
    }

    private static JsonArray relatedDuplicateReasons()
    {
        JsonArray reasons = new JsonArray();
        reasons.add("old_debug_session_detected"); //$NON-NLS-1$
        return reasons;
    }

    private static int normalizeTimeoutSeconds(int requested, int defaultValue, int hardLimit)
    {
        if (requested <= 0)
        {
            return defaultValue;
        }
        return Math.min(requested, hardLimit);
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

    private static String stringProperty(JsonObject object, String property)
    {
        return object.has(property) && !object.get(property).isJsonNull() ? object.get(property).getAsString() : ""; //$NON-NLS-1$
    }

    private static ILaunchManager getLaunchManager()
    {
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        return debugPlugin != null ? debugPlugin.getLaunchManager() : null;
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

    private static String launchId(String projectName, String applicationId, String configurationName, ILaunch launch)
    {
        return opaqueId("launch", projectName, applicationId, configurationName, launch.getLaunchMode(), launch); //$NON-NLS-1$
    }

    private static String opaqueId(String prefix, Object... parts)
    {
        Object[] normalizedParts = Arrays.stream(parts)
                .map(RuntimeDebugLaunchLifecycleBridge::stableIdPart)
                .toArray();
        int hash = Arrays.deepHashCode(normalizedParts);
        return prefix + '-' + Integer.toUnsignedString(hash, 36);
    }

    private static Object stableIdPart(Object part)
    {
        if (part instanceof ILaunch || part instanceof IDebugElement)
        {
            return part.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(part));
        }
        return part;
    }

    private static String nullToEmpty(String value)
    {
        return value != null ? value : ""; //$NON-NLS-1$
    }

    private record LaunchClassification(String launchId, boolean filtered, boolean runtimeClientDebugLaunch,
            JsonObject snapshot)
    {
    }

    private record RedactedCommandLine(String value, boolean redacted, List<String> reasons)
    {
    }

    @FunctionalInterface
    interface SnapshotSupplier
    {
        JsonObject get();
    }

    interface UiLaunchExecutor
    {
        void asyncExec(Runnable runnable);

        boolean isDisposed();

        boolean isCurrentThread();
    }

    private static final class DisplayUiLaunchExecutor implements UiLaunchExecutor
    {
        private final Display display;

        private DisplayUiLaunchExecutor(Display display)
        {
            this.display = display;
        }

        @Override
        public void asyncExec(Runnable runnable)
        {
            display.asyncExec(runnable);
        }

        @Override
        public boolean isDisposed()
        {
            return display.isDisposed();
        }

        @Override
        public boolean isCurrentThread()
        {
            return Display.getCurrent() == display;
        }
    }

    private static ResultObject errorObject(String message, String reason)
    {
        JsonObject object = new JsonObject();
        object.addProperty("success", false); //$NON-NLS-1$
        object.addProperty("error", message); //$NON-NLS-1$
        object.addProperty("reason", reason); //$NON-NLS-1$
        return new ResultObject(object);
    }

    private static final class ResultObject
    {
        private final JsonObject object;

        private ResultObject(JsonObject object)
        {
            this.object = object;
        }

        private ResultObject put(String key, boolean value)
        {
            object.addProperty(key, value);
            return this;
        }

        private ResultObject put(String key, int value)
        {
            object.addProperty(key, value);
            return this;
        }

        private ResultObject put(String key, JsonObject value)
        {
            object.add(key, value);
            return this;
        }

        private ResultObject put(String key, JsonArray value)
        {
            object.add(key, value);
            return this;
        }

        private JsonObject build()
        {
            return object;
        }
    }
}
