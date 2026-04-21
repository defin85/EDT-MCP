package com.ditrix.edt.mcp.server.utils;

import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com._1c.g5.v8.dt.common.Pair;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com._1c.g5.v8.dt.core.platform.IV8ProjectManager;
import com._1c.g5.v8.dt.core.platform.IExtensionProject;
import com._1c.g5.v8.dt.core.platform.IV8Project;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
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
 * Shared resolver for extension lifecycle tools.
 */
public final class ExtensionRuntimeContextResolver
{
    /**
     * Result wrapper for extension runtime context resolution.
     */
    public static final class Resolution
    {
        private final ResolvedExtensionRuntimeContext context;
        private final ToolResult failureResult;

        private Resolution(ResolvedExtensionRuntimeContext context, ToolResult failureResult)
        {
            this.context = context;
            this.failureResult = failureResult;
        }

        public boolean isResolved()
        {
            return context != null;
        }

        public ResolvedExtensionRuntimeContext getContext()
        {
            return context;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    /**
     * Result wrapper for runtime target application resolution.
     */
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

    /**
     * Result wrapper for thick-client runtime bridge resolution.
     */
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

    private ExtensionRuntimeContextResolver()
    {
    }

    public static Resolution resolve(String toolName, String projectName)
    {
        if (projectName == null || projectName.isEmpty())
        {
            return new Resolution(null, ToolResult.error("projectName is required")); //$NON-NLS-1$
        }

        ResolvedProjectContext projectContext = ProjectContextResolver.resolve(projectName);
        if (projectContext == null)
        {
            return new Resolution(null, ToolResult.error("Project not found: " + projectName)); //$NON-NLS-1$
        }
        if (!projectContext.isExtensionProject())
        {
            return new Resolution(null, ExtensionLifecycleFailure.extensionProjectRequired(toolName, projectContext));
        }

        IDtProjectManager dtProjectManager = Activator.getDefault() != null ? Activator.getDefault().getDtProjectManager()
                : null;
        IV8ProjectManager v8ProjectManager = Activator.getDefault() != null ? Activator.getDefault().getV8ProjectManager()
                : null;
        if (dtProjectManager == null || v8ProjectManager == null)
        {
            return new Resolution(null, ToolResult.error("EDT project managers are not available")); //$NON-NLS-1$
        }

        IProject project = projectContext.getProject();
        IDtProject dtProject = dtProjectManager.getDtProject(project);
        if (dtProject == null)
        {
            return new Resolution(null,
                    ExtensionLifecycleFailure.modelUnavailable(toolName, projectContext,
                            "DT project handle is not available for the extension project.")); //$NON-NLS-1$
        }

        IV8Project v8Project = v8ProjectManager.getProject(dtProject);
        if (!(v8Project instanceof IExtensionProject extensionProject))
        {
            return new Resolution(null,
                    ExtensionLifecycleFailure.modelUnavailable(toolName, projectContext,
                            "V8 project handle is not an IExtensionProject.")); //$NON-NLS-1$
        }

        Configuration configuration = extensionProject.getConfiguration();
        if (configuration == null)
        {
            return new Resolution(null,
                    ExtensionLifecycleFailure.modelUnavailable(toolName, projectContext,
                            "Extension configuration root is not available.")); //$NON-NLS-1$
        }

        String runtimeExtensionName = configuration.getName();
        if (runtimeExtensionName == null || runtimeExtensionName.isBlank())
        {
            runtimeExtensionName = projectContext.getExtensionName();
        }

        ResolvedExtensionRuntimeContext context = new ResolvedExtensionRuntimeContext(projectContext, extensionProject,
                configuration, runtimeExtensionName, extensionProject.getParent(), extensionProject.getParentProject());
        return new Resolution(context, null);
    }

    public static ApplicationResolution resolveApplication(String toolName, ResolvedExtensionRuntimeContext context,
            String applicationId)
    {
        if (applicationId == null || applicationId.isEmpty())
        {
            return new ApplicationResolution(null, null,
                    ToolResult.error("applicationId is required. Use get_extension_runtime_targets first.")); //$NON-NLS-1$
        }
        if (context == null)
        {
            return new ApplicationResolution(null, null, ToolResult.error("Extension runtime context is required")); //$NON-NLS-1$
        }

        IProject parentProject = context.getParentProject();
        if (parentProject == null)
        {
            return new ApplicationResolution(null, null,
                    ExtensionLifecycleFailure.parentMissing(toolName, context.getProjectContext(),
                            "The extension project has no linked parent configuration project.")); //$NON-NLS-1$
        }

        IApplicationManager applicationManager = Activator.getDefault() != null ? Activator.getDefault().getApplicationManager()
                : null;
        if (applicationManager == null)
        {
            return new ApplicationResolution(null, null,
                    ExtensionLifecycleFailure.runtimeServiceUnavailable(toolName, context.getProjectContext(),
                            context.getParentProjectName(),
                            "IApplicationManager service is not available.")); //$NON-NLS-1$
        }

        try
        {
            Optional<IApplication> application = applicationManager.getApplication(parentProject, applicationId);
            if (!application.isPresent())
            {
                return new ApplicationResolution(null, null,
                        ExtensionLifecycleFailure.targetNotFound(toolName, context.getProjectContext(),
                                context.getParentProjectName(), applicationId,
                                "Use get_extension_runtime_targets to discover valid application IDs.")); //$NON-NLS-1$
            }

            IInfobaseApplication infobaseApplication = InfobaseSyncUtils.asInfobaseApplication(application.get());
            if (infobaseApplication == null)
            {
                return new ApplicationResolution(null, null,
                        ExtensionLifecycleFailure.targetNotFound(toolName, context.getProjectContext(),
                                context.getParentProjectName(), applicationId,
                                "The selected application is not an infobase application.")); //$NON-NLS-1$
            }

            return new ApplicationResolution(application.get(), infobaseApplication, null);
        }
        catch (ApplicationException e)
        {
            Activator.logError("Failed to resolve runtime target application: " + applicationId, e); //$NON-NLS-1$
            return new ApplicationResolution(null, null,
                    ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                            context.getParentProjectName(), applicationId, e.getMessage()));
        }
    }

    public static ThickClientResolution resolveThickClient(String toolName, ResolvedExtensionRuntimeContext context,
            IInfobaseApplication infobaseApplication, String applicationId)
    {
        if (context == null || infobaseApplication == null)
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
                    ExtensionLifecycleFailure.runtimeServiceUnavailable(toolName, context.getProjectContext(),
                            context.getParentProjectName(),
                            "Extension runtime resolution services are not available.")); //$NON-NLS-1$
        }

        try
        {
            Object resolvableInstallation = invokeInstallationLookup(infobaseAccessManager, "getInstallation", //$NON-NLS-1$
                    new Class<?>[] {IProject.class, InfobaseReference.class},
                    context.getParentProject(), infobaseApplication.getInfobase());
            if (resolvableInstallation == null)
            {
                resolvableInstallation = invokeInstallationLookup(infobaseAccessManager, "getInstallation", //$NON-NLS-1$
                        new Class<?>[] {InfobaseReference.class}, infobaseApplication.getInfobase());
            }
            if (resolvableInstallation == null)
            {
                return new ThickClientResolution(null, null,
                        ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                                context.getParentProjectName(), applicationId,
                                "The selected target does not expose a resolvable runtime installation.")); //$NON-NLS-1$
            }

            Object installationObject = ReflectionUtils.invokeMethod(resolvableInstallation, "get"); //$NON-NLS-1$
            if (!(installationObject instanceof RuntimeInstallation runtimeInstallation))
            {
                return new ThickClientResolution(null, null,
                        ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                                context.getParentProjectName(), applicationId,
                                "The runtime installation resolver returned an unsupported value.")); //$NON-NLS-1$
            }

            Pair<?, ?> thickClientPair = runtimeComponentManager.getComponentAndExecutor(runtimeInstallation,
                    IRuntimeComponentTypes.THICK_CLIENT);
            if (thickClientPair == null || !(thickClientPair.first instanceof ILaunchableRuntimeComponent component)
                    || !(thickClientPair.second instanceof IThickClientLauncher launcher))
            {
                return new ThickClientResolution(null, null,
                        ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                                context.getParentProjectName(), applicationId,
                                "The runtime component manager did not provide a usable thick-client executor.")); //$NON-NLS-1$
            }
            return new ThickClientResolution(component, launcher, null);
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to resolve runtime installation for application: " + applicationId, e); //$NON-NLS-1$
            return new ThickClientResolution(null, null,
                    ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                            context.getParentProjectName(), applicationId, e.getMessage()));
        }
        catch (Exception e)
        {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Activator.logError("Failed to resolve runtime installation bridge for application: " + applicationId, //$NON-NLS-1$
                    cause);
            return new ThickClientResolution(null, null,
                    ExtensionLifecycleFailure.runtimeCheckFailed(toolName, context.getProjectContext(),
                            context.getParentProjectName(), applicationId, cause.getMessage()));
        }
    }

    private static Object invokeInstallationLookup(IInfobaseAccessManager accessManager, String methodName,
            Class<?>[] parameterTypes, Object... args) throws Exception
    {
        return accessManager.getClass().getMethod(methodName, parameterTypes).invoke(accessManager, args);
    }
}
