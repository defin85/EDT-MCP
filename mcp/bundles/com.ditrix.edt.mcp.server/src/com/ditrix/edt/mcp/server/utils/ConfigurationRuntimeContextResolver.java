package com.ditrix.edt.mcp.server.utils;

import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com._1c.g5.v8.dt.common.Pair;
import com._1c.g5.v8.dt.platform.services.core.infobases.IInfobaseAccessManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ILaunchableRuntimeComponent;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentManager;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IRuntimeComponentTypes;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.IThickClientLauncher;
import com._1c.g5.v8.dt.platform.services.model.InfobaseReference;
import com._1c.g5.v8.dt.platform.services.model.RuntimeInstallation;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.e1c.g5.dt.applications.ApplicationException;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.IApplicationManager;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Shared resolver for configuration-project runtime test execution.
 */
public final class ConfigurationRuntimeContextResolver
{
    public static final class Resolution
    {
        private final ResolvedConfigurationRuntimeContext context;
        private final ToolResult failureResult;

        private Resolution(ResolvedConfigurationRuntimeContext context, ToolResult failureResult)
        {
            this.context = context;
            this.failureResult = failureResult;
        }

        public boolean isResolved()
        {
            return context != null;
        }

        public ResolvedConfigurationRuntimeContext getContext()
        {
            return context;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    public static final class ApplicationResolution
    {
        private final IApplication application;
        private final IInfobaseApplication infobaseApplication;
        private final ToolResult failureResult;

        private ApplicationResolution(IApplication application, IInfobaseApplication infobaseApplication,
                ToolResult failureResult)
        {
            this.application = application;
            this.infobaseApplication = infobaseApplication;
            this.failureResult = failureResult;
        }

        public boolean isResolved()
        {
            return application != null && infobaseApplication != null;
        }

        public IApplication getApplication()
        {
            return application;
        }

        public IInfobaseApplication getInfobaseApplication()
        {
            return infobaseApplication;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    public static final class ThickClientResolution
    {
        private final ILaunchableRuntimeComponent component;
        private final IThickClientLauncher launcher;
        private final ToolResult failureResult;

        private ThickClientResolution(ILaunchableRuntimeComponent component, IThickClientLauncher launcher,
                ToolResult failureResult)
        {
            this.component = component;
            this.launcher = launcher;
            this.failureResult = failureResult;
        }

        public boolean isResolved()
        {
            return component != null && launcher != null;
        }

        public ILaunchableRuntimeComponent getComponent()
        {
            return component;
        }

        public IThickClientLauncher getLauncher()
        {
            return launcher;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    private ConfigurationRuntimeContextResolver()
    {
    }

    public static Resolution resolve(String toolName, String projectName)
    {
        if (projectName == null || projectName.isEmpty())
        {
            return new Resolution(null, ToolResult.error("projectName is required")); //$NON-NLS-1$
        }

        ResolvedProjectContext projectContext = ProjectContextResolver.resolve(projectName);
        if (projectContext == null || projectContext.getProject() == null)
        {
            return new Resolution(null, ToolResult.error("Project not found: " + projectName)); //$NON-NLS-1$
        }

        if (!projectContext.isConfigurationProject())
        {
            return new Resolution(null,
                    ToolResult.error("Tool '" + toolName + "' supports configuration-project targets only. " //$NON-NLS-1$ //$NON-NLS-2$
                            + "Project '" + projectName + "' is not a supported configuration project.")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        return new Resolution(new ResolvedConfigurationRuntimeContext(projectContext, projectContext.getProject()),
                null);
    }

    public static ApplicationResolution resolveApplication(String toolName, ResolvedConfigurationRuntimeContext context,
            String applicationId)
    {
        if (applicationId == null || applicationId.isEmpty())
        {
            return new ApplicationResolution(null, null,
                    ToolResult.error("applicationId is required. Use get_applications first.")); //$NON-NLS-1$
        }
        if (context == null || context.getProject() == null)
        {
            return new ApplicationResolution(null, null, ToolResult.error("Configuration runtime context is required")); //$NON-NLS-1$
        }

        IApplicationManager applicationManager = Activator.getDefault() != null ? Activator.getDefault().getApplicationManager()
                : null;
        if (applicationManager == null)
        {
            return new ApplicationResolution(null, null,
                    ToolResult.error("IApplicationManager service is not available")); //$NON-NLS-1$
        }

        try
        {
            Optional<IApplication> application = applicationManager.getApplication(context.getProject(), applicationId);
            if (!application.isPresent())
            {
                return new ApplicationResolution(null, null,
                        ToolResult.error("Application not found: " + applicationId //$NON-NLS-1$
                                + ". Use get_applications to discover valid application IDs.")); //$NON-NLS-1$
            }
            IInfobaseApplication infobaseApplication = InfobaseSyncUtils.asInfobaseApplication(application.get());
            if (infobaseApplication == null)
            {
                return new ApplicationResolution(null, null,
                        ToolResult.error("Application is not an infobase application: " + applicationId)); //$NON-NLS-1$
            }
            return new ApplicationResolution(application.get(), infobaseApplication, null);
        }
        catch (ApplicationException e)
        {
            Activator.logError("Failed to resolve application for unit-test execution: " + applicationId, e); //$NON-NLS-1$
            return new ApplicationResolution(null, null,
                    ToolResult.error("Failed to resolve application: " + e.getMessage())); //$NON-NLS-1$
        }
    }

    public static ThickClientResolution resolveThickClient(String toolName, ResolvedConfigurationRuntimeContext context,
            IInfobaseApplication infobaseApplication, String applicationId)
    {
        if (context == null || context.getProject() == null || infobaseApplication == null)
        {
            return new ThickClientResolution(null, null, ToolResult.error("Runtime target infobase is required")); //$NON-NLS-1$
        }

        IInfobaseAccessManager infobaseAccessManager = Activator.getDefault() != null
                ? Activator.getDefault().getInfobaseAccessManager()
                : null;
        IRuntimeComponentManager runtimeComponentManager = Activator.getDefault() != null
                ? Activator.getDefault().getRuntimeComponentManager()
                : null;
        if (infobaseAccessManager == null || runtimeComponentManager == null)
        {
            return new ThickClientResolution(null, null,
                    ToolResult.error("Runtime resolution services are not available for unit-test execution")); //$NON-NLS-1$
        }

        try
        {
            Object resolvableInstallation = invokeInstallationLookup(infobaseAccessManager, "getInstallation", //$NON-NLS-1$
                    new Class<?>[] {IProject.class, InfobaseReference.class}, context.getProject(),
                    infobaseApplication.getInfobase());
            if (resolvableInstallation == null)
            {
                resolvableInstallation = invokeInstallationLookup(infobaseAccessManager, "getInstallation", //$NON-NLS-1$
                        new Class<?>[] {InfobaseReference.class}, infobaseApplication.getInfobase());
            }
            if (resolvableInstallation == null)
            {
                return new ThickClientResolution(null, null,
                        ToolResult.error("The selected target does not expose a resolvable runtime installation.")); //$NON-NLS-1$
            }

            Object installationObject = ReflectionUtils.invokeMethod(resolvableInstallation, "get"); //$NON-NLS-1$
            if (!(installationObject instanceof RuntimeInstallation runtimeInstallation))
            {
                return new ThickClientResolution(null, null,
                        ToolResult.error("The runtime installation resolver returned an unsupported value.")); //$NON-NLS-1$
            }

            Pair<?, ?> thickClientPair = runtimeComponentManager.getComponentAndExecutor(runtimeInstallation,
                    IRuntimeComponentTypes.THICK_CLIENT);
            if (thickClientPair == null || !(thickClientPair.first instanceof ILaunchableRuntimeComponent component)
                    || !(thickClientPair.second instanceof IThickClientLauncher launcher))
            {
                return new ThickClientResolution(null, null,
                        ToolResult.error("The runtime component manager did not provide a usable thick-client executor.")); //$NON-NLS-1$
            }
            return new ThickClientResolution(component, launcher, null);
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to resolve runtime installation for application: " + applicationId, e); //$NON-NLS-1$
            return new ThickClientResolution(null, null,
                    ToolResult.error("Failed to resolve runtime installation: " + e.getMessage())); //$NON-NLS-1$
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Activator.logError("Failed to resolve runtime installation bridge for application: " + applicationId, //$NON-NLS-1$
                    cause);
            return new ThickClientResolution(null, null,
                    ToolResult.error("Failed to resolve runtime bridge: " + cause.getMessage())); //$NON-NLS-1$
        }
    }

    private static Object invokeInstallationLookup(IInfobaseAccessManager accessManager, String methodName,
            Class<?>[] parameterTypes, Object... args) throws Exception
    {
        return accessManager.getClass().getMethod(methodName, parameterTypes).invoke(accessManager, args);
    }
}
