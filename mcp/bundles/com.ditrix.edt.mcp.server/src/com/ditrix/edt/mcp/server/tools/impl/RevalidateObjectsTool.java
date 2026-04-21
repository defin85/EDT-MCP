/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import com._1c.g5.v8.bm.core.IBmObject;
import com._1c.g5.v8.bm.core.IBmTransaction;
import com._1c.g5.v8.bm.integration.AbstractBmTask;
import com._1c.g5.v8.bm.integration.IBmModel;
import com._1c.g5.v8.dt.core.platform.IBmModelManager;
import com._1c.g5.v8.dt.core.platform.IDtProject;
import com._1c.g5.v8.dt.core.platform.IDtProjectManager;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.McpServer;
import com.ditrix.edt.mcp.server.progress.CapturingProgressMonitor;
import com.ditrix.edt.mcp.server.progress.DerivedDataDetachedTracker;
import com.ditrix.edt.mcp.server.progress.OperationProgressReporter;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContext;
import com.ditrix.edt.mcp.server.progress.ToolExecutionContextHolder;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tasks.TaskCancellationToken;
import com.ditrix.edt.mcp.server.tasks.TaskSchedulingKey;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.BuildUtils;
import com.ditrix.edt.mcp.server.utils.DiagnosticThreadDumpWatchdog;
import com.ditrix.edt.mcp.server.utils.MetadataTypeUtils;
import com.ditrix.edt.mcp.server.utils.ProjectStateChecker;
import com.e1c.g5.v8.dt.check.ICheckScheduler;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Tool to revalidate EDT project or specific objects by their FQN.
 * This performs:
 * 1. Refresh project from disk (to detect external changes)
 * 2. Find objects by FQN
 * 3. Schedule validation for those objects
 * 4. Wait for validation to complete
 */
public class RevalidateObjectsTool implements IMcpTool
{
    public static final String NAME = "revalidate_objects"; //$NON-NLS-1$

    private static final String STAGE_VALIDATION = "validation"; //$NON-NLS-1$
    private static final String STAGE_REFRESH = "refresh"; //$NON-NLS-1$
    private static final String STAGE_FULL_REVALIDATION = "full_revalidation"; //$NON-NLS-1$
    private static final String STAGE_OBJECT_LOOKUP = "object_lookup"; //$NON-NLS-1$
    private static final String STAGE_OBJECT_VALIDATION = "object_validation"; //$NON-NLS-1$
    private static final String STAGE_WAITING_FOR_BUILD = "waiting_for_build"; //$NON-NLS-1$
    private static final String STAGE_COMPLETION = "completion"; //$NON-NLS-1$
    private static final String STAGE_FAILURE = "failure"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Revalidate EDT project or specific objects. " + //$NON-NLS-1$
               "If objects array is empty or missing, revalidates entire project. " + //$NON-NLS-1$
               "FQN examples: 'Document.SalesOrder', 'Catalog.Products', 'CommonModule.Common'. " + //$NON-NLS-1$
               "Russian type names are also supported (e.g. 'Документ.ПриходнаяНакладная', 'Справочник.Номенклатура'). " + //$NON-NLS-1$
               "Full-project revalidation is async-first at runtime: bare calls auto-promote into task-backed " + //$NON-NLS-1$
               "execution, while partial object revalidation remains synchronous."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
            .stringArrayProperty("objects", "FQNs to revalidate (e.g. ['Document.SalesOrder']). Russian type names supported (e.g. 'Документ.ПродажаТоваров'). Empty array = full project revalidation") //$NON-NLS-1$ //$NON-NLS-2$
            .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public TaskSupport getTaskSupport()
    {
        return TaskSupport.OPTIONAL;
    }

    @Override
    public String validateTaskRequest(Map<String, String> params)
    {
        List<String> objects = parseObjectsList(JsonUtils.extractStringArgument(params, "objects")); //$NON-NLS-1$
        if (!objects.isEmpty())
        {
            return "Task augmentation is supported only for full project revalidation. Omit the objects array or pass an empty array."; //$NON-NLS-1$
        }
        return null;
    }

    @Override
    public TaskSchedulingKey getTaskSchedulingKey(Map<String, String> params)
    {
        List<String> objects = parseObjectsList(JsonUtils.extractStringArgument(params, "objects")); //$NON-NLS-1$
        if (!objects.isEmpty())
        {
            return TaskSchedulingKey.none();
        }
        return TaskSchedulingKey.projectScoped(JsonUtils.extractStringArgument(params, "projectName")); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String objectsJson = JsonUtils.extractStringArgument(params, "objects"); //$NON-NLS-1$

        // Check if project is ready for operations
        if (projectName != null && !projectName.isEmpty())
        {
            ToolResult notReadyResult = ProjectStateChecker.checkReadyOrErrorResult(projectName);
            if (notReadyResult != null)
            {
                return notReadyResult.toJson();
            }
        }

        List<String> objects = parseObjectsList(objectsJson);
        return revalidateObjects(projectName, objects);
    }

    /**
     * Parses the objects array from JSON string using Gson JsonParser.
     *
     * @param objectsJson JSON array string like ["obj1", "obj2"]
     * @return list of object FQNs
     */
    private static List<String> parseObjectsList(String objectsJson)
    {
        List<String> result = new ArrayList<>();
        if (objectsJson == null || objectsJson.isEmpty())
        {
            return result;
        }

        try
        {
            JsonElement element = JsonParser.parseString(objectsJson);
            if (element.isJsonArray())
            {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement item : array)
                {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString())
                    {
                        result.add(item.getAsString());
                    }
                }
            }
        }
        catch (JsonParseException e)
        {
            Activator.logError("Error parsing objects JSON: " + objectsJson, e); //$NON-NLS-1$
        }
        return result;
    }

    /**
     * Revalidates specific objects in a project or full project.
     *
     * @param projectName name of the project
     * @param objectFqns list of object FQNs to revalidate (empty for full project)
     * @return JSON string with result
     */
    private String revalidateObjects(String projectName, List<String> objectFqns)
    {
        if (projectName == null || projectName.isEmpty())
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }

        boolean fullProjectRevalidation = objectFqns == null || objectFqns.isEmpty();
        McpServer server = Activator.getDefault() != null ? Activator.getDefault().getMcpServer() : null;
        ToolExecutionContext context = ToolExecutionContextHolder.get();
        String diagnosticLabel = buildDiagnosticLabel(context, projectName, fullProjectRevalidation, objectFqns);
        OperationProgressReporter reporter = createProgressReporter(context, projectName, fullProjectRevalidation,
                objectFqns);
        CapturingProgressMonitor monitor = new CapturingProgressMonitor(reporter);
        TaskCancellationToken cancellationToken = context != null ? context.getCancellationToken() : null;
        IProject project = null;
        boolean buildTriggered = false;
        boolean detachedHandoff = false;
        registerActiveOperation(server, reporter, context);

        try
        {
            Activator.logInfo("[diag] " + diagnosticLabel + " :: start"); //$NON-NLS-1$ //$NON-NLS-2$
            IWorkspace workspace = ResourcesPlugin.getWorkspace();

            // Find project
            Activator.logInfo("[diag] " + diagnosticLabel + " :: resolving workspace project handle"); //$NON-NLS-1$ //$NON-NLS-2$
            project = workspace.getRoot().getProject(projectName);
            Activator.logInfo("[diag] " + diagnosticLabel + " :: project handle resolved"); //$NON-NLS-1$ //$NON-NLS-2$
            if (project == null || !project.exists())
            {
                return ToolResult.error("Project not found: " + projectName).toJson(); //$NON-NLS-1$
            }
            Activator.logInfo("[diag] " + diagnosticLabel + " :: project exists"); //$NON-NLS-1$ //$NON-NLS-2$

            if (!project.isOpen())
            {
                return ToolResult.error("Project is closed: " + projectName).toJson(); //$NON-NLS-1$
            }
            Activator.logInfo("[diag] " + diagnosticLabel + " :: project is open"); //$NON-NLS-1$ //$NON-NLS-2$

            // Refresh from disk
            long refreshStageStartedAt = System.nanoTime();
            Activator.logInfo("[diag] " + diagnosticLabel + " :: refresh stage start"); //$NON-NLS-1$ //$NON-NLS-2$
            try (DiagnosticThreadDumpWatchdog watchdog = DiagnosticThreadDumpWatchdog.start(diagnosticLabel,
                    STAGE_REFRESH, 30000L))
            {
                long refreshReporterStartedAt = System.nanoTime();
                Activator.logInfo("[diag] " + diagnosticLabel + " :: reporter.stage(refresh) start"); //$NON-NLS-1$ //$NON-NLS-2$
                reporter.stage(STAGE_REFRESH, "Refreshing project from disk"); //$NON-NLS-1$
                Activator.logInfo("[diag] " + diagnosticLabel + " :: reporter.stage(refresh) returned in " //$NON-NLS-1$ //$NON-NLS-2$
                        + elapsedMillis(refreshReporterStartedAt) + "ms"); //$NON-NLS-1$

                long refreshStartedAt = System.nanoTime();
                Activator.logInfo("[diag] " + diagnosticLabel + " :: refreshLocal start"); //$NON-NLS-1$ //$NON-NLS-2$
                project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
                Activator.logInfo("[diag] " + diagnosticLabel + " :: refreshLocal completed in " //$NON-NLS-1$ //$NON-NLS-2$
                        + elapsedMillis(refreshStartedAt) + "ms"); //$NON-NLS-1$
            }
            Activator.logInfo("[diag] " + diagnosticLabel + " :: refresh stage completed in " //$NON-NLS-1$ //$NON-NLS-2$
                    + elapsedMillis(refreshStageStartedAt) + "ms"); //$NON-NLS-1$
            if (isCancellationRequested(cancellationToken, monitor))
            {
                return cancelled(reporter);
            }

            if (fullProjectRevalidation)
            {
                reporter.stage(STAGE_FULL_REVALIDATION, "Triggering full project revalidation"); //$NON-NLS-1$
                Activator.logInfo("Revalidating entire project: " + project.getName()); //$NON-NLS-1$
                Activator.logInfo("[diag] " + diagnosticLabel + " :: project.build start"); //$NON-NLS-1$ //$NON-NLS-2$
                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);
                Activator.logInfo("[diag] " + diagnosticLabel + " :: project.build returned"); //$NON-NLS-1$ //$NON-NLS-2$
                buildTriggered = true;

                reporter.indeterminate(STAGE_WAITING_FOR_BUILD, "Waiting for build and derived data"); //$NON-NLS-1$
                try (DiagnosticThreadDumpWatchdog watchdog = DiagnosticThreadDumpWatchdog.start(diagnosticLabel,
                        STAGE_WAITING_FOR_BUILD, 30000L))
                {
                    BuildUtils.waitForBuildAndDerivedData(project, monitor, diagnosticLabel);
                }
                if (isCancellationRequested(cancellationToken, monitor))
                {
                    return cancelled(reporter);
                }

                String message = "Full project revalidation completed"; //$NON-NLS-1$
                reporter.stage(STAGE_COMPLETION, message);
                reporter.completed(message);
                return ToolResult.success()
                    .put("project", projectName) //$NON-NLS-1$
                    .put("mode", "full") //$NON-NLS-1$ //$NON-NLS-2$
                    .put("message", message) //$NON-NLS-1$
                    .toJson();
            }

            return revalidateSpecificObjects(project, objectFqns, monitor, reporter, cancellationToken,
                    diagnosticLabel);
        }
        catch (Exception e)
        {
            Activator.logError("Error during project revalidation", e); //$NON-NLS-1$
            reporter.stage(STAGE_FAILURE, "Project revalidation failed"); //$NON-NLS-1$
            reporter.failed("Project revalidation failed: " + e.getMessage(), e); //$NON-NLS-1$
            return ToolResult.error(e.getMessage()).toJson();
        }
        finally
        {
            if (!detachedHandoff && fullProjectRevalidation && buildTriggered && isCancellationRequested(cancellationToken, monitor)
                    && project != null)
            {
                detachedHandoff = DerivedDataDetachedTracker.handoff(server, reporter, List.of(project));
            }
            if (!detachedHandoff)
            {
                clearActiveOperation(server, context);
            }
        }
    }

    /**
     * Revalidates specific objects using ICheckScheduler.
     *
     * @param project the IProject to work with
     * @param objectFqns list of object FQNs to revalidate
     * @param monitor progress monitor
     * @param reporter task/sync progress reporter
     * @param cancellationToken cancellation token for task-backed execution
     * @return JSON string with result
     * @throws CoreException on error
     */
    private String revalidateSpecificObjects(IProject project, List<String> objectFqns, IProgressMonitor monitor,
            OperationProgressReporter reporter, TaskCancellationToken cancellationToken, String diagnosticLabel)
            throws CoreException
    {
        String projectName = project.getName();
        reporter.stage(STAGE_OBJECT_LOOKUP, "Resolving requested objects for validation"); //$NON-NLS-1$

        // Get services from Activator
        IBmModelManager bmModelManager = Activator.getDefault().getBmModelManager();
        ICheckScheduler checkScheduler = Activator.getDefault().getCheckScheduler();

        if (bmModelManager == null)
        {
            return ToolResult.error("IBmModelManager service is not available").toJson(); //$NON-NLS-1$
        }

        if (checkScheduler == null)
        {
            return ToolResult.error("ICheckScheduler service is not available").toJson(); //$NON-NLS-1$
        }

        // Get DtProject
        IDtProjectManager dtProjectManager = Activator.getDefault().getDtProjectManager();
        IDtProject dtProject = dtProjectManager != null ? dtProjectManager.getDtProject(project) : null;

        if (dtProject == null)
        {
            return ToolResult.error("Not an EDT project: " + projectName).toJson(); //$NON-NLS-1$
        }

        // Get BM model
        IBmModel bmModel = bmModelManager.getModel(dtProject);
        if (bmModel == null)
        {
            return ToolResult.error("BM model not available for project: " + projectName).toJson(); //$NON-NLS-1$
        }

        if (isCancellationRequested(cancellationToken, monitor))
        {
            return cancelled(reporter);
        }

        // Find objects by FQN using executeReadonlyTask
        List<String> found = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> skippedNullUri = new ArrayList<>();
        Collection<Object> objectsToValidate = new ArrayList<>();

        // Normalize FQNs to English singular form (supports Russian type names),
        // but keep the original user input for result reporting
        List<String> originalFqns = new ArrayList<>(objectFqns);
        List<String> normalizedFqns = new ArrayList<>();
        for (String fqn : objectFqns)
        {
            normalizedFqns.add(MetadataTypeUtils.normalizeFqn(fqn));
        }

        long lookupStartedAt = System.nanoTime();
        bmModel.executeReadonlyTask(new AbstractBmTask<Void>("RevalidateObjectsLookup") //$NON-NLS-1$
        {
            @Override
            public Void execute(IBmTransaction tx, IProgressMonitor pm)
            {
                for (int i = 0; i < normalizedFqns.size(); i++)
                {
                    String normalizedFqn = normalizedFqns.get(i);
                    String originalFqn = originalFqns.get(i);

                    IBmObject obj = tx.getTopObjectByFqn(normalizedFqn);
                    if (obj != null)
                    {
                        long bmId = obj.bmGetId();
                        if (bmId > 0)
                        {
                            Activator.logInfo("Found object: " + originalFqn + " -> bmId: " + bmId); //$NON-NLS-1$ //$NON-NLS-2$
                            objectsToValidate.add(Long.valueOf(bmId));
                            found.add(originalFqn);
                        }
                        else
                        {
                            Activator.logInfo("Object has invalid bmId: " + originalFqn + " -> " + bmId); //$NON-NLS-1$ //$NON-NLS-2$
                            skippedNullUri.add(originalFqn);
                        }
                    }
                    else
                    {
                        Activator.logInfo("Object not found: " + originalFqn + " (normalized: " + normalizedFqn + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        notFound.add(originalFqn);
                    }
                }
                return null;
            }
        });
        Activator.logInfo("[diag] " + diagnosticLabel + " :: object lookup completed in " //$NON-NLS-1$ //$NON-NLS-2$
                + elapsedMillis(lookupStartedAt) + "ms; found=" + found.size() + ", notFound=" + notFound.size() //$NON-NLS-1$ //$NON-NLS-2$
                + ", skipped=" + skippedNullUri.size()); //$NON-NLS-1$

        if (isCancellationRequested(cancellationToken, monitor))
        {
            return cancelled(reporter);
        }

        if (!objectsToValidate.isEmpty())
        {
            reporter.stage(STAGE_OBJECT_VALIDATION, "Scheduling validation for selected objects"); //$NON-NLS-1$
            Collection<Object> validObjects = new ArrayList<>();
            for (Object obj : objectsToValidate)
            {
                if (obj != null)
                {
                    validObjects.add(obj);
                }
            }

            if (!validObjects.isEmpty())
            {
                // Use 4-parameter version without IBmTransaction
                // Use empty set for checkIds = validate with all checks
                Activator.logInfo("[diag] " + diagnosticLabel + " :: scheduleValidation start for " //$NON-NLS-1$ //$NON-NLS-2$
                        + validObjects.size() + " bm ids"); //$NON-NLS-1$
                try (DiagnosticThreadDumpWatchdog watchdog = DiagnosticThreadDumpWatchdog.start(diagnosticLabel,
                        STAGE_OBJECT_VALIDATION, 30000L))
                {
                    checkScheduler.scheduleValidation(project, Collections.emptySet(), validObjects, monitor);
                    Activator.logInfo("[diag] " + diagnosticLabel + " :: scheduleValidation returned"); //$NON-NLS-1$ //$NON-NLS-2$

                    reporter.indeterminate(STAGE_WAITING_FOR_BUILD, "Waiting for validation results"); //$NON-NLS-1$
                    BuildUtils.waitForBuildAndDerivedData(project, monitor, diagnosticLabel);
                }
            }
        }
        else
        {
            Activator.logInfo("[diag] " + diagnosticLabel + " :: no matching objects scheduled for validation"); //$NON-NLS-1$ //$NON-NLS-2$
            reporter.indeterminate(STAGE_WAITING_FOR_BUILD, "Waiting for validation results"); //$NON-NLS-1$
            BuildUtils.waitForBuildAndDerivedData(project, monitor, diagnosticLabel);
        }
        if (isCancellationRequested(cancellationToken, monitor))
        {
            return cancelled(reporter);
        }

        String message = "Revalidation completed"; //$NON-NLS-1$
        reporter.stage(STAGE_COMPLETION, message);
        reporter.completed(message);

        ToolResult result = ToolResult.success()
            .put("project", projectName) //$NON-NLS-1$
            .put("mode", "objects") //$NON-NLS-1$ //$NON-NLS-2$
            .put("objectsRequested", objectFqns.size()) //$NON-NLS-1$
            .put("objectsFound", found.size()) //$NON-NLS-1$
            .put("objectsValidated", found) //$NON-NLS-1$
            .put("message", message); //$NON-NLS-1$

        if (!notFound.isEmpty())
        {
            result.put("objectsNotFound", notFound); //$NON-NLS-1$
        }

        if (!skippedNullUri.isEmpty())
        {
            result.put("objectsSkippedNullUri", skippedNullUri); //$NON-NLS-1$
        }

        return result.toJson();
    }

    private OperationProgressReporter createProgressReporter(ToolExecutionContext context, String projectName,
            boolean fullProjectRevalidation, List<String> objectFqns)
    {
        OperationProgressReporter reporter = new OperationProgressReporter();
        String mode = fullProjectRevalidation ? "full project" : "selected objects"; //$NON-NLS-1$ //$NON-NLS-2$
        String detail = fullProjectRevalidation ? projectName : projectName + " (" + objectFqns.size() + " objects)"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        reporter.start(context != null ? context.getOperationId() : null, NAME, STAGE_VALIDATION,
                "Preparing " + mode + " revalidation for " + detail, context != null ? context.getRequestId() : null, //$NON-NLS-1$ //$NON-NLS-2$
                context != null ? context.getSessionId() : null, context != null ? context.getProgressToken() : null,
                Map.of("projectName", projectName)); //$NON-NLS-1$
        return reporter;
    }

    private void registerActiveOperation(McpServer server, OperationProgressReporter reporter, ToolExecutionContext context)
    {
        if (server == null)
        {
            return;
        }
        if (context != null && context.getOperationId() != null)
        {
            reporter.appendStateListener(state -> server.getTaskRegistry().updateProgress(context.getOperationId(), state));
        }
        server.setActiveOperation(reporter);
    }

    private void clearActiveOperation(McpServer server, ToolExecutionContext context)
    {
        if (server != null)
        {
            server.clearActiveOperation(context != null ? context.getOperationId() : null);
        }
    }

    private boolean isCancellationRequested(TaskCancellationToken cancellationToken, IProgressMonitor monitor)
    {
        boolean cancelled = cancellationToken != null && cancellationToken.isCancellationRequested();
        if (cancelled)
        {
            monitor.setCanceled(true);
        }
        return cancelled || monitor.isCanceled() || Thread.currentThread().isInterrupted();
    }

    private String cancelled(OperationProgressReporter reporter)
    {
        String message = "Project revalidation cancelled"; //$NON-NLS-1$
        reporter.stage(STAGE_FAILURE, message);
        reporter.cancelled(message);
        return ToolResult.error(message).toJson();
    }

    String buildDiagnosticLabel(ToolExecutionContext context, String projectName, boolean fullProjectRevalidation,
            List<String> objectFqns)
    {
        String operationId = context != null ? context.getOperationId() : null;
        String requestId = context != null ? context.getRequestId() : null;
        String objectSummary = fullProjectRevalidation ? "full" : summarizeObjects(objectFqns); //$NON-NLS-1$
        return "tool=" + NAME + ", project=" + projectName + ", requestId=" + safe(requestId) + ", operationId=" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                + safe(operationId) + ", objects=" + objectSummary + ", thread=" //$NON-NLS-1$ //$NON-NLS-2$
                + Thread.currentThread().getName();
    }

    private String summarizeObjects(List<String> objectFqns)
    {
        if (objectFqns == null || objectFqns.isEmpty())
        {
            return "[]"; //$NON-NLS-1$
        }
        int limit = Math.min(objectFqns.size(), 3);
        List<String> sample = objectFqns.subList(0, limit);
        if (objectFqns.size() <= limit)
        {
            return sample.toString();
        }
        return sample + " +" + (objectFqns.size() - limit); //$NON-NLS-1$
    }

    private String safe(String value)
    {
        return value != null && !value.isBlank() ? value : "<none>"; //$NON-NLS-1$
    }

    private long elapsedMillis(long startedAt)
    {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
