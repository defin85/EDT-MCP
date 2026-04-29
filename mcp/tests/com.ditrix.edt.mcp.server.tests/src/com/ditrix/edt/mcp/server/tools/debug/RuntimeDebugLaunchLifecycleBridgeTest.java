/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RuntimeDebugLaunchLifecycleBridgeTest
{
    @Test
    public void testSnapshotClassifiesRuntimeLaunchAndRedactsCommandLine()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, //$NON-NLS-1$ //$NON-NLS-2$
                new IProcess[] { process("\"C:\\Program Files\\1cv8\\1cv8c.exe\" /N Admin /P secret " //$NON-NLS-1$
                        + "/DEBUGGERURL tcp://127.0.0.1:1560?token=abc", "4242") }, //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[] { target(thread("RuntimeDebugTargetThread")) }); //$NON-NLS-1$

        JsonObject snapshot = RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(1, snapshot.get("count").getAsInt()); //$NON-NLS-1$
        JsonObject launchJson = snapshot.getAsJsonArray("launches").get(0).getAsJsonObject(); //$NON-NLS-1$
        assertTrue(launchJson.get("supported").getAsBoolean()); //$NON-NLS-1$
        assertEquals("true", launchJson.getAsJsonObject("lifecycle") //$NON-NLS-1$ //$NON-NLS-2$
                .get("supported_thread_visible").getAsString()); //$NON-NLS-1$

        JsonObject process = launchJson.getAsJsonArray("processes").get(0).getAsJsonObject(); //$NON-NLS-1$
        String sanitizedCommandLine = process.get("sanitizedCommandLine").getAsString(); //$NON-NLS-1$
        assertFalse(sanitizedCommandLine.contains("secret")); //$NON-NLS-1$
        assertFalse(sanitizedCommandLine.contains("token=abc")); //$NON-NLS-1$
        assertTrue(sanitizedCommandLine.contains("[REDACTED]")); //$NON-NLS-1$
        assertEquals("4242", process.get("pid").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(process.getAsJsonObject("debuggerUrl").get("present").getAsBoolean()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(process.getAsJsonObject("debuggerUrl").get("value").getAsString().contains("token=abc")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Test
    public void testSnapshotReportsFilterAndUnsupportedReasons()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[0]);

        JsonObject filtered = RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "OtherProject", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(0, filtered.get("count").getAsInt()); //$NON-NLS-1$
        assertEquals("projectName_mismatch", filtered.getAsJsonArray("filteredLaunches") //$NON-NLS-1$ //$NON-NLS-2$
                .get(0).getAsJsonObject().getAsJsonArray("filterReasons").get(0).getAsString()); //$NON-NLS-1$

        JsonObject unready = RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject launchJson = unready.getAsJsonArray("launches").get(0).getAsJsonObject(); //$NON-NLS-1$
        assertFalse(launchJson.get("supported").getAsBoolean()); //$NON-NLS-1$
        assertTrue(launchJson.getAsJsonArray("unsupportedReasons").toString() //$NON-NLS-1$
                .contains("runtime_process_unavailable")); //$NON-NLS-1$
        assertTrue(launchJson.getAsJsonArray("unsupportedReasons").toString() //$NON-NLS-1$
                .contains("debug_target_unavailable")); //$NON-NLS-1$
    }

    @Test
    public void testUnreadyRuntimeLaunchIsPromotedToUnsupportedSessionDiagnostic()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[0]);

        JsonObject snapshot = RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(1, RuntimeDebugLaunchLifecycleBridge.unsupportedLaunchesForSessionDiagnostics(snapshot).size());
        JsonObject sessionDiagnostic = RuntimeDebugLaunchLifecycleBridge
                .unsupportedLaunchesForSessionDiagnostics(snapshot).get(0).getAsJsonObject();
        assertFalse(sessionDiagnostic.get("supported").getAsBoolean()); //$NON-NLS-1$
        assertTrue(sessionDiagnostic.getAsJsonArray("unsupportedReasons").toString() //$NON-NLS-1$
                .contains("supported_thread_unavailable")); //$NON-NLS-1$
    }

    @Test
    public void testAcceptedLaunchResultIncludesPublicPhasePayload()
    {
        JsonObject launchResult = new JsonObject();
        launchResult.addProperty("success", true); //$NON-NLS-1$
        launchResult.addProperty("accepted", true); //$NON-NLS-1$
        launchResult.addProperty("phase", "launch_config_started"); //$NON-NLS-1$ //$NON-NLS-2$
        JsonObject emptySnapshot = new JsonObject();
        emptySnapshot.addProperty("success", true); //$NON-NLS-1$
        emptySnapshot.addProperty("count", 0); //$NON-NLS-1$

        RuntimeDebugLaunchLifecycleBridge.enrichLaunchResult(launchResult, emptySnapshot);

        assertTrue(launchResult.has("phases")); //$NON-NLS-1$
        assertEquals("true", launchResult.getAsJsonObject("phases") //$NON-NLS-1$ //$NON-NLS-2$
                .get("launch_config_started").getAsString()); //$NON-NLS-1$
        assertEquals("pending", launchResult.getAsJsonObject("phases") //$NON-NLS-1$ //$NON-NLS-2$
                .get("runtime_process_started").getAsString()); //$NON-NLS-1$
        assertEquals("false", launchResult.getAsJsonObject("phases") //$NON-NLS-1$ //$NON-NLS-2$
                .get("supported_thread_visible").getAsString()); //$NON-NLS-1$
        assertTrue(launchResult.has("launch")); //$NON-NLS-1$
        assertTrue(launchResult.has("operatorChoices")); //$NON-NLS-1$
        assertTrue(launchResult.getAsJsonArray("operatorChoices").toString() //$NON-NLS-1$
                .contains("wait_debug_session")); //$NON-NLS-1$
    }

    @Test
    public void testDuplicatePreflightFailsClosedForActiveMatchingLaunch()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[0]);

        JsonObject preflight = RuntimeDebugLaunchLifecycleBridge.duplicateLaunchPreflight(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(preflight.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("debug_launch_already_running", preflight.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(preflight.getAsJsonArray("operatorChoices").toString().contains("terminate_debug_launch")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testTerminateRefusesAmbiguousMatchingLaunchesWithoutLaunchId()
    {
        ILaunch first = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], new IDebugTarget[0]); //$NON-NLS-1$ //$NON-NLS-2$
        ILaunch second = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], new IDebugTarget[0]); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject result = JsonParser.parseString(RuntimeDebugLaunchLifecycleBridge.terminateLaunch(
                new ILaunch[] { first, second }, "ProjectA", "ApplicationA", "")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .getAsJsonObject();

        assertFalse(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("multiple_matching_launches", result.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, result.getAsJsonArray("launchIds").size()); //$NON-NLS-1$
    }

    @Test
    public void testWaitForSupportedSessionReturnsTimeoutDiagnostics()
    {
        JsonObject emptySessions = new JsonObject();
        emptySessions.addProperty("success", true); //$NON-NLS-1$
        emptySessions.addProperty("count", 0); //$NON-NLS-1$
        JsonObject latestLaunchSnapshot = new JsonObject();
        latestLaunchSnapshot.addProperty("success", true); //$NON-NLS-1$
        latestLaunchSnapshot.addProperty("count", 0); //$NON-NLS-1$

        JsonObject result = JsonParser.parseString(RuntimeDebugLaunchLifecycleBridge.waitForSupportedSession(
                () -> emptySessions, () -> latestLaunchSnapshot, 1, 1L)).getAsJsonObject();

        assertFalse(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("debug_session_wait_timeout", result.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.get("timedOut").getAsBoolean()); //$NON-NLS-1$
        assertTrue(result.has("latestLaunchSnapshot")); //$NON-NLS-1$
    }

    @Test
    public void testWaitForSupportedSessionReturnsFinalLaunchSnapshotOnSuccess()
    {
        JsonObject supportedSessions = new JsonObject();
        supportedSessions.addProperty("success", true); //$NON-NLS-1$
        supportedSessions.addProperty("count", 1); //$NON-NLS-1$
        AtomicInteger snapshotCounter = new AtomicInteger();

        JsonObject result = JsonParser.parseString(RuntimeDebugLaunchLifecycleBridge.waitForSupportedSession(
                () -> supportedSessions, () -> {
                    JsonObject snapshot = new JsonObject();
                    snapshot.addProperty("success", true); //$NON-NLS-1$
                    snapshot.addProperty("snapshotMarker", snapshotCounter.incrementAndGet()); //$NON-NLS-1$
                    return snapshot;
                }, 1, 1L)).getAsJsonObject();

        assertTrue(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(2, result.getAsJsonObject("latestLaunchSnapshot") //$NON-NLS-1$
                .get("snapshotMarker").getAsInt()); //$NON-NLS-1$
    }

    @Test
    public void testTerminateReportsProcessIdsMethodAndFinalState()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, //$NON-NLS-1$ //$NON-NLS-2$
                new IProcess[] { process("1cv8c /DEBUGGERURL tcp://127.0.0.1:1560", "4242") }, //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[0]);

        JsonObject result = JsonParser.parseString(RuntimeDebugLaunchLifecycleBridge.terminateLaunch(
                new ILaunch[] { launch }, "ProjectA", "ApplicationA", "")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .getAsJsonObject();

        assertTrue(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("eclipse_ITerminate", result.get("terminationMethod").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.getAsJsonArray("processIds").toString().contains("4242")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(result.getAsJsonArray("terminatedElements").toString().contains("4242")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("terminated", result.getAsJsonObject("finalObservedState") //$NON-NLS-1$ //$NON-NLS-2$
                .get("state").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testLaunchIdSurvivesDiagnosticRefreshForSameLaunch()
    {
        ILaunch launch = launch("ProjectA", "ApplicationA", true, false, new IProcess[0], //$NON-NLS-1$ //$NON-NLS-2$
                new IDebugTarget[0]);
        JsonObject firstSnapshot = RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$
        String launchId = firstSnapshot.getAsJsonArray("launches").get(0).getAsJsonObject() //$NON-NLS-1$
                .get("launchId").getAsString(); //$NON-NLS-1$
        RuntimeDebugLaunchLifecycleBridge.snapshotLaunches(new ILaunch[] { launch },
                "ProjectA", "ApplicationA"); //$NON-NLS-1$ //$NON-NLS-2$

        JsonObject result = JsonParser.parseString(RuntimeDebugLaunchLifecycleBridge.terminateLaunch(
                new ILaunch[] { launch }, "ProjectA", "ApplicationA", launchId)) //$NON-NLS-1$ //$NON-NLS-2$
                .getAsJsonObject();

        assertTrue(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(launchId, result.get("launchId").getAsString()); //$NON-NLS-1$
    }

    @Test
    public void testBoundedUiLaunchReportsBlockedBeforeCallbackEntry()
    {
        RuntimeDebugLaunchLifecycleBridge.UiLaunchExecutor executor =
                new RuntimeDebugLaunchLifecycleBridge.UiLaunchExecutor()
                {
                    @Override
                    public void asyncExec(Runnable runnable)
                    {
                        // Simulate a UI queue that never enters the callback inside the timeout.
                    }

                    @Override
                    public boolean isDisposed()
                    {
                        return false;
                    }

                    @Override
                    public boolean isCurrentThread()
                    {
                        return false;
                    }
                };

        JsonObject result = RuntimeDebugLaunchLifecycleBridge.launchDebugConfigurationBounded(
                configuration("ProjectA", "ApplicationA"), 1, executor); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(result.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("debug_launch_ui_blocked", result.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertFalse(result.get("accepted").getAsBoolean()); //$NON-NLS-1$
        assertTrue(result.get("cancelledBeforeLaunch").getAsBoolean()); //$NON-NLS-1$
    }

    private static ILaunch launch(String projectName, String applicationId, boolean debugMode, boolean terminated,
            IProcess[] processes, IDebugTarget[] targets)
    {
        AtomicBoolean terminatedState = new AtomicBoolean(terminated);
        ILaunchConfiguration configuration = configuration(projectName, applicationId);
        return proxy(ILaunch.class, (method, args) -> {
            String name = method.getName();
            if ("getLaunchConfiguration".equals(name)) //$NON-NLS-1$
            {
                return configuration;
            }
            if ("getLaunchMode".equals(name)) //$NON-NLS-1$
            {
                return debugMode ? ILaunchManager.DEBUG_MODE : ILaunchManager.RUN_MODE;
            }
            if ("getProcesses".equals(name)) //$NON-NLS-1$
            {
                return processes;
            }
            if ("getDebugTargets".equals(name)) //$NON-NLS-1$
            {
                return targets;
            }
            if ("isTerminated".equals(name)) //$NON-NLS-1$
            {
                return terminatedState.get();
            }
            if ("canTerminate".equals(name)) //$NON-NLS-1$
            {
                return !terminatedState.get();
            }
            if ("terminate".equals(name)) //$NON-NLS-1$
            {
                terminatedState.set(true);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static ILaunchConfiguration configuration(String projectName, String applicationId)
    {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(RuntimeDebugLaunchLifecycleBridge.ATTR_PROJECT_NAME, projectName);
        attributes.put(RuntimeDebugLaunchLifecycleBridge.ATTR_APPLICATION_ID, applicationId);
        ILaunchConfigurationType type = proxy(ILaunchConfigurationType.class, (method, args) -> {
            if ("getIdentifier".equals(method.getName())) //$NON-NLS-1$
            {
                return RuntimeDebugLaunchLifecycleBridge.RUNTIME_CLIENT_LAUNCH_CONFIG_TYPE_ID;
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(ILaunchConfiguration.class, (method, args) -> {
            String name = method.getName();
            if ("getName".equals(name)) //$NON-NLS-1$
            {
                return "RuntimeClient"; //$NON-NLS-1$
            }
            if ("getType".equals(name)) //$NON-NLS-1$
            {
                return type;
            }
            if ("getAttribute".equals(name) && args != null && args.length == 2) //$NON-NLS-1$
            {
                return attributes.getOrDefault(args[0], (String)args[1]);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static IProcess process(String commandLine, String pid)
    {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(IProcess.ATTR_CMDLINE, commandLine);
        attributes.put(IProcess.ATTR_PROCESS_ID, pid);
        AtomicBoolean terminatedState = new AtomicBoolean(false);
        return proxy(IProcess.class, (method, args) -> {
            String name = method.getName();
            if ("getLabel".equals(name)) //$NON-NLS-1$
            {
                return "1cv8c"; //$NON-NLS-1$
            }
            if ("getAttribute".equals(name)) //$NON-NLS-1$
            {
                return attributes.get(args[0]);
            }
            if ("isTerminated".equals(name)) //$NON-NLS-1$
            {
                return terminatedState.get();
            }
            if ("canTerminate".equals(name)) //$NON-NLS-1$
            {
                return !terminatedState.get();
            }
            if ("terminate".equals(name)) //$NON-NLS-1$
            {
                terminatedState.set(true);
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static IDebugTarget target(IThread thread)
    {
        return proxy(IDebugTarget.class, (method, args) -> {
            String name = method.getName();
            if ("getThreads".equals(name)) //$NON-NLS-1$
            {
                return new IThread[] { thread };
            }
            if ("getName".equals(name)) //$NON-NLS-1$
            {
                return "RuntimeDebugTarget"; //$NON-NLS-1$
            }
            if ("getModelIdentifier".equals(name)) //$NON-NLS-1$
            {
                return "com.e1c.g5.v8.dt.debug"; //$NON-NLS-1$
            }
            if ("isTerminated".equals(name)) //$NON-NLS-1$
            {
                return false;
            }
            if ("canTerminate".equals(name)) //$NON-NLS-1$
            {
                return true;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static IThread thread(String threadName)
    {
        return proxy(IThread.class, (method, args) -> {
            String name = method.getName();
            if ("getName".equals(name)) //$NON-NLS-1$
            {
                return threadName;
            }
            if ("getModelIdentifier".equals(name)) //$NON-NLS-1$
            {
                return "com.e1c.g5.v8.dt.debug"; //$NON-NLS-1$
            }
            if ("isTerminated".equals(name)) //$NON-NLS-1$
            {
                return false;
            }
            if ("canTerminate".equals(name)) //$NON-NLS-1$
            {
                return true;
            }
            return defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation)
    {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] { type }, (proxy, method, args) -> {
                    if ("toString".equals(method.getName())) //$NON-NLS-1$
                    {
                        return type.getSimpleName();
                    }
                    if ("hashCode".equals(method.getName())) //$NON-NLS-1$
                    {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) //$NON-NLS-1$
                    {
                        return proxy == args[0];
                    }
                    return invocation.invoke(method, args);
                });
    }

    private static Object defaultValue(Class<?> returnType)
    {
        if (!returnType.isPrimitive())
        {
            if (returnType.isArray())
            {
                return Array.newInstance(returnType.getComponentType(), 0);
            }
            return null;
        }
        if (returnType == boolean.class)
        {
            return false;
        }
        if (returnType == int.class)
        {
            return 0;
        }
        if (returnType == long.class)
        {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface Invocation
    {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
