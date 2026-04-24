/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;

import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ConfigurationFilesFormat;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.ConfigurationFilesKind;
import com._1c.g5.v8.dt.platform.services.core.runtimes.execution.RuntimeExecutionArguments;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.ExtensionLifecycleFailure;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ExtensionRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.ditrix.edt.mcp.server.utils.ResolvedExtensionRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;

/**
 * Developer-oriented probe that exports an extension from an infobase target and compares the
 * exported XML tree with the workspace {@code src/} layout.
 */
public class ProbeExtensionXmlContractTool implements IMcpTool
{
    public static final String NAME = "probe_extension_xml_contract"; //$NON-NLS-1$

    private static final int DEFAULT_SAMPLE_LIMIT = 20;
    private static final int MAX_SAMPLE_LIMIT = 100;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Developer-oriented extension lifecycle probe: export the selected extension and compare EDT XML layout with workspace src; diagnostic only, does not apply the extension."; //$NON-NLS-1$
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.builder("Probe extension XML contract") //$NON-NLS-1$
                .readOnlyHint(true)
                .destructiveHint(false)
                .idempotentHint(true)
                .openWorldHint(true)
                .build();
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "Extension project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_extension_runtime_targets (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("sampleLimit", "Maximum number of sample paths to return per diff bucket (default: 20, max: 100)") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("cleanupExport", "Delete the temporary exported XML tree after comparison (default: false)") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        int sampleLimit = Math.max(1, Math.min(MAX_SAMPLE_LIMIT,
                JsonUtils.extractIntArgument(params, "sampleLimit", DEFAULT_SAMPLE_LIMIT))); //$NON-NLS-1$
        boolean cleanupExport = JsonUtils.extractBooleanArgument(params, "cleanupExport", false); //$NON-NLS-1$
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (applicationId == null || applicationId.isEmpty())
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }

        ToolResult notReadyResult = ProjectStateChecker.checkReadyOrErrorResult(projectName);
        if (notReadyResult != null)
        {
            return notReadyResult.toJson();
        }

        ExtensionRuntimeContextResolver.Resolution resolution = ExtensionRuntimeContextResolver.resolve(NAME, projectName);
        if (!resolution.isResolved())
        {
            return resolution.getFailureResult().toJson();
        }

        ResolvedExtensionRuntimeContext context = resolution.getContext();
        IProject parentProject = context.getParentProject();
        if (parentProject == null)
        {
            return ExtensionLifecycleFailure.parentMissing(NAME, context.getProjectContext(),
                    "The extension project has no linked parent configuration project.").toJson(); //$NON-NLS-1$
        }

        ToolResult parentNotReadyResult = ProjectStateChecker.checkReadyOrErrorResult(parentProject);
        if (parentNotReadyResult != null)
        {
            return parentNotReadyResult.toJson();
        }

        Path workspaceRoot = resolveWorkspaceSourceRoot(context.getProjectContext().getProject());
        if (workspaceRoot == null || !Files.isDirectory(workspaceRoot))
        {
            return ToolResult.error("Workspace extension src/ directory is not available for: " + projectName).toJson(); //$NON-NLS-1$
        }

        ExtensionRuntimeContextResolver.ApplicationResolution applicationResolution = ExtensionRuntimeContextResolver
                .resolveApplication(NAME, context, applicationId);
        if (!applicationResolution.isResolved())
        {
            return applicationResolution.getFailureResult().toJson();
        }

        ExtensionRuntimeContextResolver.ThickClientResolution thickClientResolution = ExtensionRuntimeContextResolver
                .resolveThickClient(NAME, context, applicationResolution.getInfobaseApplication(), applicationId);
        if (!thickClientResolution.isResolved())
        {
            return thickClientResolution.getFailureResult().toJson();
        }

        ToolResult accessSettingsFailure = ExtensionRuntimeBridgeSupport.preflightAccessSettings(NAME, context,
                applicationResolution.getInfobaseApplication(), applicationId);
        if (accessSettingsFailure != null)
        {
            return accessSettingsFailure.toJson();
        }

        IApplication application = applicationResolution.getApplication();
        IInfobaseApplication infobaseApplication = applicationResolution.getInfobaseApplication();
        RuntimeExecutionArguments arguments = new RuntimeExecutionArguments();
        arguments.setDisableStartupMessages(true);
        if (context.getRuntimeExtensionName() != null)
        {
            arguments.setExtensionName(context.getRuntimeExtensionName());
        }

        Path exportDestination;
        try
        {
            exportDestination = Files.createTempDirectory("edt-mcp-extension-xml-probe-"); //$NON-NLS-1$
        }
        catch (IOException e)
        {
            return ToolResult.error("Failed to create temporary export directory: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }

        try
        {
            ExtensionRuntimeBridgeSupport.InvocationResult<Path> invocation = ExtensionRuntimeBridgeSupport
                    .invokeWithGuard(NAME, context, applicationId,
                            () -> thickClientResolution.getLauncher().exportConfigurationToXml(
                                    thickClientResolution.getComponent(),
                                    infobaseApplication.getInfobase(),
                                    ConfigurationFilesFormat.HIERARCHICAL,
                                    ConfigurationFilesKind.PLAIN_FILES,
                                    arguments,
                                    exportDestination));
            if (!invocation.isSuccess())
            {
                return invocation.getFailureResult().toJson();
            }

            Path exportRoot = invocation.getValue();
            if (exportRoot == null)
            {
                exportRoot = exportDestination;
            }
            if (!Files.isDirectory(exportRoot))
            {
                return ToolResult.error("The EDT XML export did not produce a readable directory tree.").toJson(); //$NON-NLS-1$
            }

            DirectoryComparison comparison = compareTrees(workspaceRoot, exportRoot, sampleLimit);

            return ToolResult.success()
                    .put("projectName", context.getProjectContext().getProjectName()) //$NON-NLS-1$
                    .put("extensionName", context.getRuntimeExtensionName()) //$NON-NLS-1$
                    .put("parentProjectName", context.getParentProjectName()) //$NON-NLS-1$
                    .put("applicationId", application.getId()) //$NON-NLS-1$
                    .put("applicationName", application.getName()) //$NON-NLS-1$
                    .put("format", ConfigurationFilesFormat.HIERARCHICAL.name()) //$NON-NLS-1$
                    .put("kind", ConfigurationFilesKind.PLAIN_FILES.name()) //$NON-NLS-1$
                    .put("workspaceSourceRoot", workspaceRoot.toAbsolutePath().toString()) //$NON-NLS-1$
                    .put("exportRoot", exportRoot.toAbsolutePath().toString()) //$NON-NLS-1$
                    .put("cleanupExport", cleanupExport) //$NON-NLS-1$
                    .put("compatibleLayout", comparison.isCompatibleLayout()) //$NON-NLS-1$
                    .put("workspaceFileCount", comparison.workspaceFiles.size()) //$NON-NLS-1$
                    .put("exportFileCount", comparison.exportedFiles.size()) //$NON-NLS-1$
                    .put("commonFileCount", comparison.commonFileCount) //$NON-NLS-1$
                    .put("workspaceOnlyCount", comparison.workspaceOnlyCount) //$NON-NLS-1$
                    .put("exportOnlyCount", comparison.exportOnlyCount) //$NON-NLS-1$
                    .put("commonFilesSample", comparison.commonFiles) //$NON-NLS-1$
                    .put("workspaceOnlySample", comparison.workspaceOnly) //$NON-NLS-1$
                    .put("exportOnlySample", comparison.exportOnly) //$NON-NLS-1$
                    .toJson();
        }
        catch (IOException e)
        {
            return ToolResult.error("Failed to compare workspace and exported XML trees: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
        finally
        {
            if (cleanupExport)
            {
                deleteRecursivelyQuietly(exportDestination);
            }
        }
    }

    private static Path resolveWorkspaceSourceRoot(IProject project)
    {
        if (project == null || project.getLocation() == null)
        {
            return null;
        }
        return project.getLocation().append("src").toFile().toPath(); //$NON-NLS-1$
    }

    private static DirectoryComparison compareTrees(Path workspaceRoot, Path exportRoot, int sampleLimit)
            throws IOException
    {
        Set<String> workspaceFiles = collectRelativeFiles(workspaceRoot);
        Set<String> exportedFiles = collectRelativeFiles(exportRoot);

        List<String> workspaceOnly = workspaceFiles.stream()
                .filter(path -> !exportedFiles.contains(path))
                .collect(Collectors.toList());
        List<String> exportOnly = exportedFiles.stream()
                .filter(path -> !workspaceFiles.contains(path))
                .collect(Collectors.toList());
        List<String> commonFiles = workspaceFiles.stream()
                .filter(exportedFiles::contains)
                .collect(Collectors.toList());

        return new DirectoryComparison(workspaceFiles, exportedFiles,
                sample(commonFiles, sampleLimit),
                sample(workspaceOnly, sampleLimit),
                sample(exportOnly, sampleLimit),
                workspaceOnly.size(),
                exportOnly.size(),
                commonFiles.size());
    }

    private static Set<String> collectRelativeFiles(Path root) throws IOException
    {
        try (Stream<Path> stream = Files.walk(root))
        {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> normalizePath(root.relativize(path)))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static String normalizePath(Path path)
    {
        return path.toString().replace('\\', '/');
    }

    private static List<String> sample(List<String> values, int sampleLimit)
    {
        if (values.size() <= sampleLimit)
        {
            return values;
        }
        return new ArrayList<>(values.subList(0, sampleLimit));
    }

    private static void deleteRecursivelyQuietly(Path root)
    {
        if (root == null || !Files.exists(root))
        {
            return;
        }
        try (Stream<Path> stream = Files.walk(root))
        {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try
                {
                    Files.deleteIfExists(path);
                }
                catch (IOException e)
                {
                    // Keep cleanup best-effort for developer probes.
                }
            });
        }
        catch (IOException e)
        {
            // Keep cleanup best-effort for developer probes.
        }
    }

    private static final class DirectoryComparison
    {
        private final Set<String> workspaceFiles;
        private final Set<String> exportedFiles;
        private final List<String> commonFiles;
        private final List<String> workspaceOnly;
        private final List<String> exportOnly;
        private final int workspaceOnlyCount;
        private final int exportOnlyCount;
        private final int commonFileCount;

        private DirectoryComparison(Set<String> workspaceFiles, Set<String> exportedFiles, List<String> commonFiles,
                List<String> workspaceOnly, List<String> exportOnly, int workspaceOnlyCount, int exportOnlyCount,
                int commonFileCount)
        {
            this.workspaceFiles = workspaceFiles;
            this.exportedFiles = exportedFiles;
            this.commonFiles = commonFiles;
            this.workspaceOnly = workspaceOnly;
            this.exportOnly = exportOnly;
            this.workspaceOnlyCount = workspaceOnlyCount;
            this.exportOnlyCount = exportOnlyCount;
            this.commonFileCount = commonFileCount;
        }

        private boolean isCompatibleLayout()
        {
            return workspaceFiles.equals(exportedFiles);
        }
    }
}
