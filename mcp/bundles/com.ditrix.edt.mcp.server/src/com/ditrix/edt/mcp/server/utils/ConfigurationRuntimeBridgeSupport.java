package com.ditrix.edt.mcp.server.utils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;

import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessSettings;
import com._1c.g5.v8.dt.platform.services.core.infobases.InfobaseAccessSettingsValidator;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Shared guardrails for configuration-project runtime test execution.
 */
public final class ConfigurationRuntimeBridgeSupport
{
    @FunctionalInterface
    public interface CheckedSupplier<T>
    {
        T get() throws Exception;
    }

    public static final class InvocationResult<T>
    {
        private final T value;
        private final ToolResult failureResult;

        private InvocationResult(T value, ToolResult failureResult)
        {
            this.value = value;
            this.failureResult = failureResult;
        }

        public boolean isSuccess()
        {
            return failureResult == null;
        }

        public T getValue()
        {
            return value;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    private static final long DEFAULT_RUNTIME_BRIDGE_TIMEOUT_MS = 10_000L;

    private static final ConcurrentHashMap<String, BridgeState> BRIDGE_STATES = new ConcurrentHashMap<>();

    private ConfigurationRuntimeBridgeSupport()
    {
    }

    public static ToolResult preflightAccessSettings(String toolName, ResolvedConfigurationRuntimeContext context,
            IInfobaseApplication infobaseApplication, String applicationId)
    {
        if (context == null || infobaseApplication == null)
        {
            return ToolResult.error("Configuration runtime context and infobase target are required"); //$NON-NLS-1$
        }

        IInfobaseAccessManager accessManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseAccessManager()
                : null;
        if (accessManager == null)
        {
            return ToolResult.error("IInfobaseAccessManager service is not available"); //$NON-NLS-1$
        }

        try
        {
            IInfobaseAccessSettings settings = accessManager.getSettings(infobaseApplication.getInfobase());
            if (settings == null || Objects.equals(settings, IInfobaseAccessSettings.NOT_DEFINED))
            {
                return ToolResult.error("Infobase access settings are not configured for application " + applicationId //$NON-NLS-1$
                        + ". Configure EDT runtime access settings and retry."); //$NON-NLS-1$
            }

            IStatus validationStatus = InfobaseAccessSettingsValidator.validate(settings);
            if (validationStatus != null && !validationStatus.isOK())
            {
                return ToolResult.error("Infobase access settings are invalid for application " + applicationId + ": " //$NON-NLS-1$ //$NON-NLS-2$
                        + validationStatus.getMessage());
            }
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to read infobase access settings for application: " + applicationId, e); //$NON-NLS-1$
            return ToolResult.error("Failed to read infobase access settings: " + e.getMessage()); //$NON-NLS-1$
        }

        return null;
    }

    public static <T> InvocationResult<T> invokeWithGuard(String toolName, ResolvedConfigurationRuntimeContext context,
            String applicationId, CheckedSupplier<T> supplier)
    {
        return invokeWithGuard(toolName, context, applicationId, DEFAULT_RUNTIME_BRIDGE_TIMEOUT_MS, supplier);
    }

    public static <T> InvocationResult<T> invokeWithGuard(String toolName, ResolvedConfigurationRuntimeContext context,
            String applicationId, long timeoutMs, CheckedSupplier<T> supplier)
    {
        if (context == null)
        {
            return failure(ToolResult.error("Configuration runtime context is required")); //$NON-NLS-1$
        }
        if (timeoutMs <= 0)
        {
            timeoutMs = DEFAULT_RUNTIME_BRIDGE_TIMEOUT_MS;
        }

        String bridgeKey = buildBridgeKey(context, applicationId);
        BridgeState state = BRIDGE_STATES.computeIfAbsent(bridgeKey, key -> new BridgeState());
        if (!state.active.compareAndSet(false, true))
        {
            return failure(ToolResult.error("Another runtime test operation is already in progress for application " //$NON-NLS-1$
                    + applicationId + ".")); //$NON-NLS-1$
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> valueRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        Thread worker = new Thread(() -> {
            try
            {
                valueRef.set(supplier.get());
            }
            catch (Throwable t)
            {
                errorRef.set(t);
            }
            finally
            {
                latch.countDown();
            }
        }, "MCP-Configuration-Runtime-Bridge"); //$NON-NLS-1$
        worker.setDaemon(true);
        worker.setContextClassLoader(contextClassLoader);
        worker.start();

        try
        {
            boolean completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!completed)
            {
                worker.interrupt();
                String message = "The EDT runtime bridge did not respond within " + timeoutMs //$NON-NLS-1$
                        + " ms. Restart EDT before retrying run_unit_tests."; //$NON-NLS-1$
                Activator.logError("Configuration runtime bridge timed out for application: " + applicationId, //$NON-NLS-1$
                        new RuntimeException(message));
                return failure(ToolResult.error(message));
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            worker.interrupt();
            return failure(ToolResult.error("Interrupted while waiting for the EDT runtime bridge.")); //$NON-NLS-1$
        }
        finally
        {
            state.active.set(false);
        }

        Throwable error = errorRef.get();
        if (error != null)
        {
            if (error instanceof Exception exception)
            {
                Activator.logError("Configuration runtime execution failed for application: " + applicationId, //$NON-NLS-1$
                        exception);
                return failure(ToolResult.error(exception.getMessage())); //$NON-NLS-1$
            }
            throw new RuntimeException(error);
        }

        return success(valueRef.get());
    }

    private static String buildBridgeKey(ResolvedConfigurationRuntimeContext context, String applicationId)
    {
        String projectName = context.getProjectName() != null ? context.getProjectName() : ""; //$NON-NLS-1$
        String target = applicationId != null ? applicationId : ""; //$NON-NLS-1$
        return projectName + "::" + target; //$NON-NLS-1$
    }

    private static <T> InvocationResult<T> success(T value)
    {
        return new InvocationResult<>(value, null);
    }

    private static <T> InvocationResult<T> failure(ToolResult failureResult)
    {
        return new InvocationResult<>(null, failureResult);
    }

    private static final class BridgeState
    {
        private final AtomicBoolean active = new AtomicBoolean(false);
    }
}
