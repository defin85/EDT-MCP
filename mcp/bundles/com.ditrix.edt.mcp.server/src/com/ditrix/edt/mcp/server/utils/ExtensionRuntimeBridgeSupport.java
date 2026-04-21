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
 * Shared fail-fast helpers for extension runtime bridge tools.
 */
public final class ExtensionRuntimeBridgeSupport
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

    private ExtensionRuntimeBridgeSupport()
    {
    }

    public static ToolResult preflightAccessSettings(String toolName, ResolvedExtensionRuntimeContext context,
            IInfobaseApplication infobaseApplication, String applicationId)
    {
        if (context == null || infobaseApplication == null)
        {
            return ToolResult.error("Extension runtime context and infobase target are required"); //$NON-NLS-1$
        }

        IInfobaseAccessManager accessManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseAccessManager()
                : null;
        if (accessManager == null)
        {
            return ExtensionLifecycleFailure.runtimeServiceUnavailable(toolName, context.getProjectContext(),
                    context.getParentProjectName(), "IInfobaseAccessManager service is not available."); //$NON-NLS-1$
        }

        try
        {
            IInfobaseAccessSettings settings = accessManager.getSettings(infobaseApplication.getInfobase());
            if (settings == null || Objects.equals(settings, IInfobaseAccessSettings.NOT_DEFINED))
            {
                return ExtensionLifecycleFailure.runtimeAccessSettingsRequired(toolName, context.getProjectContext(),
                        context.getParentProjectName(), applicationId,
                        "Configure infobase access settings in EDT for the selected target and retry."); //$NON-NLS-1$
            }

            IStatus validationStatus = InfobaseAccessSettingsValidator.validate(settings);
            if (validationStatus != null && !validationStatus.isOK())
            {
                return ExtensionLifecycleFailure.runtimeAccessSettingsRequired(toolName, context.getProjectContext(),
                        context.getParentProjectName(), applicationId,
                        validationStatus.getMessage() != null && !validationStatus.getMessage().isBlank()
                                ? validationStatus.getMessage()
                                : "The stored infobase access settings are incomplete or invalid."); //$NON-NLS-1$
            }
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to read infobase access settings for application: " + applicationId, e); //$NON-NLS-1$
            return ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                    context.getParentProjectName(), applicationId, e.getMessage());
        }

        return null;
    }

    public static <T> InvocationResult<T> invokeWithGuard(String toolName, ResolvedExtensionRuntimeContext context,
            String applicationId, CheckedSupplier<T> supplier)
    {
        return invokeWithGuard(toolName, context, applicationId, DEFAULT_RUNTIME_BRIDGE_TIMEOUT_MS, supplier);
    }

    public static <T> InvocationResult<T> invokeWithGuard(String toolName, ResolvedExtensionRuntimeContext context,
            String applicationId, long timeoutMs, CheckedSupplier<T> supplier)
    {
        if (context == null)
        {
            return failure(ToolResult.error("Extension runtime context is required")); //$NON-NLS-1$
        }
        if (timeoutMs <= 0)
        {
            timeoutMs = DEFAULT_RUNTIME_BRIDGE_TIMEOUT_MS;
        }

        String bridgeKey = buildBridgeKey(context, applicationId);
        BridgeState state = BRIDGE_STATES.computeIfAbsent(bridgeKey, key -> new BridgeState());

        String stickyFailure = state.stickyFailureMessage;
        if (stickyFailure != null && !stickyFailure.isBlank())
        {
            return failure(ExtensionLifecycleFailure.runtimeBridgeBusy(toolName, context.getProjectContext(),
                    context.getParentProjectName(), applicationId, stickyFailure));
        }

        if (!state.active.compareAndSet(false, true))
        {
            return failure(ExtensionLifecycleFailure.runtimeBridgeBusy(toolName, context.getProjectContext(),
                    context.getParentProjectName(), applicationId,
                    "Another extension runtime probe is already in progress for this target.")); //$NON-NLS-1$
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
        }, "MCP-Extension-Runtime-Bridge"); //$NON-NLS-1$
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
                        + " ms. Restart EDT before retrying this tool."; //$NON-NLS-1$
                state.stickyFailureMessage = message;
                Activator.logError("Extension runtime bridge timed out for application: " + applicationId, //$NON-NLS-1$
                        new RuntimeException(message));
                return failure(ExtensionLifecycleFailure.runtimeBridgeBusy(toolName, context.getProjectContext(),
                        context.getParentProjectName(), applicationId, message));
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            worker.interrupt();
            return failure(ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                    context.getParentProjectName(), applicationId,
                    "Interrupted while waiting for the EDT runtime bridge.")); //$NON-NLS-1$
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
                Activator.logError("Extension runtime probe failed for application: " + applicationId, exception); //$NON-NLS-1$
                return failure(ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                        context.getParentProjectName(), applicationId, exception.getMessage()));
            }
            throw new RuntimeException(error);
        }

        state.stickyFailureMessage = null;
        return success(valueRef.get());
    }

    private static String buildBridgeKey(ResolvedExtensionRuntimeContext context, String applicationId)
    {
        String parentProjectName = context.getParentProjectName() != null ? context.getParentProjectName() : ""; //$NON-NLS-1$
        String extensionName = context.getRuntimeExtensionName() != null ? context.getRuntimeExtensionName() : ""; //$NON-NLS-1$
        return parentProjectName + "::" + applicationId + "::" + extensionName; //$NON-NLS-1$ //$NON-NLS-2$
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
        private volatile String stickyFailureMessage;
    }
}
