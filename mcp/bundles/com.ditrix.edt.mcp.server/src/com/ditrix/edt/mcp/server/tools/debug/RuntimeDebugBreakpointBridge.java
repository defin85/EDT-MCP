/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.debug;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.osgi.framework.Bundle;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Bridge over the verified EDT BSL line breakpoint backend.
 */
public final class RuntimeDebugBreakpointBridge
{
    public static final String OWNER_ATTRIBUTE = "com.ditrix.edt.mcp.server.owner"; //$NON-NLS-1$

    private static final String OWNER_VALUE = "runtime-debug-breakpoints"; //$NON-NLS-1$

    private static final String BSL_DEBUG_BUNDLE = "com._1c.g5.v8.dt.debug.core"; //$NON-NLS-1$

    private static final String BSL_LINE_BREAKPOINT_CLASS =
            "com._1c.g5.v8.dt.internal.debug.core.model.breakpoints.BslLineBreakpoint"; //$NON-NLS-1$

    private static final String BSL_MODEL_ID = "com._1c.g5.v8.dt.debug"; //$NON-NLS-1$

    private static final String BSL_LINE_MARKER_TYPE =
            "com._1c.g5.v8.dt.debug.core.bslLineBreakpointMarker"; //$NON-NLS-1$

    private static final String CURRENT_HIT_COUNT_ATTRIBUTE =
            "com._1c.g5.v8.dt.debug.core.currentHitCount"; //$NON-NLS-1$

    private static final String RUNTIME_PLATFORM_ATTRIBUTE =
            "com._1c.g5.v8.dt.debug.core.runtimePlatformVersion"; //$NON-NLS-1$

    private static final AtomicLong SNAPSHOT_SEQUENCE = new AtomicLong();

    private static final Map<String, IBreakpoint> BREAKPOINTS = new ConcurrentHashMap<>();

    private RuntimeDebugBreakpointBridge()
    {
        // Utility class
    }

    public static String listBreakpoints(String projectFilter, String modulePathFilter)
    {
        IBreakpointManager manager = getBreakpointManager();
        if (manager == null)
        {
            return ToolResult.error("Eclipse breakpoint manager is not available").toJson(); //$NON-NLS-1$
        }

        try
        {
            long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
            JsonArray breakpoints = new JsonArray();
            for (IBreakpoint breakpoint : manager.getBreakpoints(BSL_MODEL_ID))
            {
                if (!isSupportedBslLineBreakpoint(breakpoint))
                {
                    continue;
                }

                BreakpointLocation location = locationOf(breakpoint);
                if (!matchesFilter(location, projectFilter, modulePathFilter))
                {
                    continue;
                }

                String breakpointId = breakpointId(breakpoint, location, snapshotId);
                BREAKPOINTS.put(breakpointId, breakpoint);
                breakpoints.add(toBreakpointJson(breakpoint, location, breakpointId, snapshotId, false, false));
            }

            return ToolResult.success()
                    .put("breakpoints", breakpoints) //$NON-NLS-1$
                    .put("count", breakpoints.size()) //$NON-NLS-1$
                    .put("snapshotId", Long.toString(snapshotId)) //$NON-NLS-1$
                    .toJson();
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to list debug breakpoints", e); //$NON-NLS-1$
            return ToolResult.error("Failed to list debug breakpoints: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    public static String setBreakpoint(String projectName, String modulePath, int lineNumber, boolean persisted)
    {
        SourceResolution resolution = resolveSource(projectName, modulePath, lineNumber);
        if (resolution.error != null)
        {
            return resolution.error.toJson();
        }

        IBreakpointManager manager = getBreakpointManager();
        if (manager == null)
        {
            return ToolResult.error("Eclipse breakpoint manager is not available").toJson(); //$NON-NLS-1$
        }

        try
        {
            IBreakpoint existing = findBreakpoint(manager, resolution.file, lineNumber);
            long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
            if (existing != null)
            {
                String breakpointId = breakpointId(existing, locationOf(existing), snapshotId);
                BREAKPOINTS.put(breakpointId, existing);
                return ToolResult.success()
                        .put("created", false) //$NON-NLS-1$
                        .put("preExisting", true) //$NON-NLS-1$
                        .put("breakpoint", toBreakpointJson(existing, locationOf(existing), breakpointId, snapshotId, //$NON-NLS-1$
                                false, true))
                        .toJson();
            }

            IBreakpoint breakpoint = createBreakpoint(resolution.file, lineNumber, persisted);
            String breakpointId = breakpointId(breakpoint, locationOf(breakpoint), snapshotId);
            BREAKPOINTS.put(breakpointId, breakpoint);
            return ToolResult.success()
                    .put("created", true) //$NON-NLS-1$
                    .put("preExisting", false) //$NON-NLS-1$
                    .put("breakpoint", toBreakpointJson(breakpoint, locationOf(breakpoint), breakpointId, snapshotId, //$NON-NLS-1$
                            true, false))
                    .toJson();
        }
        catch (ReflectiveOperationException e)
        {
            Activator.logError("Failed to create EDT BSL breakpoint", e); //$NON-NLS-1$
            return ToolResult.error("Failed to create EDT BSL breakpoint: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "unsupported_backend_capability") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
        catch (CoreException e)
        {
            if (isUnsupportedBackend(e))
            {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                Activator.logError("Failed to create EDT BSL breakpoint", cause); //$NON-NLS-1$
                return ToolResult.error("Failed to create EDT BSL breakpoint: " + cause.getMessage()) //$NON-NLS-1$
                        .put("reason", "unsupported_backend_capability") //$NON-NLS-1$ //$NON-NLS-2$
                        .toJson();
            }
            Activator.logError("Failed to set debug breakpoint", e); //$NON-NLS-1$
            return ToolResult.error("Failed to set debug breakpoint: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    public static String removeBreakpoint(String breakpointId, boolean removeUserBreakpoint)
    {
        if (breakpointId == null || breakpointId.isEmpty())
        {
            return ToolResult.error("breakpointId is required").toJson(); //$NON-NLS-1$
        }

        IBreakpoint breakpoint = BREAKPOINTS.get(breakpointId);
        if (breakpoint == null || !hasExistingMarker(breakpoint))
        {
            BREAKPOINTS.remove(breakpointId);
            return ToolResult.error("Unknown or stale breakpointId: " + breakpointId) //$NON-NLS-1$
                    .put("reason", "stale_breakpoint_id") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }

        try
        {
            if (!isOwnedByMcp(breakpoint) && !removeUserBreakpoint)
            {
                return ToolResult.error("Refusing to remove a pre-existing user breakpoint") //$NON-NLS-1$
                        .put("reason", "protected_user_breakpoint") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("breakpointId", breakpointId) //$NON-NLS-1$
                        .toJson();
            }

            BreakpointLocation location = locationOf(breakpoint);
            IWorkspace workspace = ResourcesPlugin.getWorkspace();
            IResource rule = breakpoint.getMarker().getResource();
            workspace.run(monitor -> breakpoint.delete(), rule, IWorkspace.AVOID_UPDATE, null);
            BREAKPOINTS.remove(breakpointId);

            JsonObject removed = new JsonObject();
            removed.addProperty("breakpointId", breakpointId); //$NON-NLS-1$
            removed.addProperty("projectName", location.projectName); //$NON-NLS-1$
            removed.addProperty("modulePath", location.modulePath); //$NON-NLS-1$
            removed.addProperty("lineNumber", location.lineNumber); //$NON-NLS-1$
            return ToolResult.success()
                    .put("removed", true) //$NON-NLS-1$
                    .put("breakpoint", removed) //$NON-NLS-1$
                    .toJson();
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to remove debug breakpoint", e); //$NON-NLS-1$
            return ToolResult.error("Failed to remove debug breakpoint: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    public static String cleanupMcpBreakpoints(String projectFilter, String modulePathFilter, boolean dryRun)
    {
        IBreakpointManager manager = getBreakpointManager();
        if (manager == null)
        {
            return ToolResult.error("Eclipse breakpoint manager is not available").toJson(); //$NON-NLS-1$
        }

        JsonArray removed = new JsonArray();
        JsonArray skipped = new JsonArray();
        JsonArray failed = new JsonArray();
        int remaining = 0;

        try
        {
            long snapshotId = SNAPSHOT_SEQUENCE.incrementAndGet();
            for (IBreakpoint breakpoint : manager.getBreakpoints(BSL_MODEL_ID))
            {
                if (!isSupportedBslLineBreakpoint(breakpoint))
                {
                    continue;
                }
                BreakpointLocation location = locationOf(breakpoint);
                if (!matchesFilter(location, projectFilter, modulePathFilter))
                {
                    remaining++;
                    continue;
                }

                String breakpointId = breakpointId(breakpoint, location, snapshotId);
                BREAKPOINTS.put(breakpointId, breakpoint);
                boolean ownedByMcp = isOwnedByMcp(breakpoint);
                JsonObject breakpointJson = toBreakpointJson(breakpoint, location, breakpointId, snapshotId, false,
                        false);
                if (!ownedByMcp)
                {
                    breakpointJson.addProperty("reason", "protected_user_breakpoint"); //$NON-NLS-1$ //$NON-NLS-2$
                    skipped.add(breakpointJson);
                    remaining++;
                    continue;
                }

                if (dryRun)
                {
                    breakpointJson.addProperty("dryRun", true); //$NON-NLS-1$
                    breakpointJson.addProperty("reason", "dry_run"); //$NON-NLS-1$ //$NON-NLS-2$
                    skipped.add(breakpointJson);
                    remaining++;
                    continue;
                }

                try
                {
                    IWorkspace workspace = ResourcesPlugin.getWorkspace();
                    IResource rule = breakpoint.getMarker().getResource();
                    workspace.run(monitor -> breakpoint.delete(), rule, IWorkspace.AVOID_UPDATE, null);
                    BREAKPOINTS.entrySet().removeIf(entry -> entry.getValue() == breakpoint);
                    removed.add(breakpointJson);
                }
                catch (CoreException e)
                {
                    JsonObject failure = breakpointJson.deepCopy();
                    failure.addProperty("error", e.getMessage()); //$NON-NLS-1$
                    failed.add(failure);
                    remaining++;
                }
            }

            return ToolResult.success()
                    .put("dryRun", dryRun) //$NON-NLS-1$
                    .put("removed", removed) //$NON-NLS-1$
                    .put("removedCount", removed.size()) //$NON-NLS-1$
                    .put("skipped", skipped) //$NON-NLS-1$
                    .put("skippedCount", skipped.size()) //$NON-NLS-1$
                    .put("failed", failed) //$NON-NLS-1$
                    .put("failedCount", failed.size()) //$NON-NLS-1$
                    .put("remainingCount", remaining) //$NON-NLS-1$
                    .toJson();
        }
        catch (CoreException e)
        {
            Activator.logError("Failed to clean up MCP debug breakpoints", e); //$NON-NLS-1$
            return ToolResult.error("Failed to clean up MCP debug breakpoints: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "debug_backend_error") //$NON-NLS-1$ //$NON-NLS-2$
                    .toJson();
        }
    }

    private static IBreakpoint createBreakpoint(IFile file, int lineNumber, boolean persisted)
            throws CoreException, ReflectiveOperationException
    {
        IBreakpoint[] created = new IBreakpoint[1];
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        workspace.run(monitor -> {
            try
            {
                created[0] = instantiateBslLineBreakpoint(file, lineNumber);
                IMarker marker = created[0].getMarker();
                marker.setAttribute(OWNER_ATTRIBUTE, OWNER_VALUE);
                created[0].setPersisted(persisted);
                getBreakpointManager().addBreakpoint(created[0]);
            }
            catch (ReflectiveOperationException e)
            {
                throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
            }
        }, file, IWorkspace.AVOID_UPDATE, null);
        return created[0];
    }

    private static boolean isUnsupportedBackend(CoreException e)
    {
        return e.getCause() instanceof ReflectiveOperationException;
    }

    private static IBreakpoint instantiateBslLineBreakpoint(IFile file, int lineNumber)
            throws ReflectiveOperationException
    {
        Bundle bundle = Platform.getBundle(BSL_DEBUG_BUNDLE);
        if (bundle == null)
        {
            throw new ClassNotFoundException("Bundle not available: " + BSL_DEBUG_BUNDLE); //$NON-NLS-1$
        }
        Class<?> breakpointClass = bundle.loadClass(BSL_LINE_BREAKPOINT_CLASS);
        Constructor<?> constructor = breakpointClass.getConstructor(IResource.class, int.class);
        Object instance = constructor.newInstance(file, lineNumber);
        if (!(instance instanceof IBreakpoint breakpoint))
        {
            throw new ClassCastException("Created object is not an Eclipse breakpoint: " + instance.getClass()); //$NON-NLS-1$
        }
        return breakpoint;
    }

    private static IBreakpoint findBreakpoint(IBreakpointManager manager, IFile file, int lineNumber)
            throws CoreException
    {
        for (IBreakpoint breakpoint : manager.getBreakpoints(BSL_MODEL_ID))
        {
            if (!isSupportedBslLineBreakpoint(breakpoint))
            {
                continue;
            }
            IMarker marker = breakpoint.getMarker();
            if (marker != null && marker.exists() && Objects.equals(marker.getResource(), file)
                    && breakpoint instanceof ILineBreakpoint lineBreakpoint
                    && lineBreakpoint.getLineNumber() == lineNumber)
            {
                return breakpoint;
            }
        }
        return null;
    }

    private static SourceResolution resolveSource(String projectName, String modulePath, int lineNumber)
    {
        if (projectName == null || projectName.isEmpty())
        {
            return SourceResolution.error(ToolResult.error("projectName is required")); //$NON-NLS-1$
        }
        if (modulePath == null || modulePath.isEmpty())
        {
            return SourceResolution.error(ToolResult.error("modulePath is required")); //$NON-NLS-1$
        }
        if (lineNumber <= 0)
        {
            return SourceResolution.error(ToolResult.error("lineNumber must be positive") //$NON-NLS-1$
                    .put("reason", "invalid_line_number")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        String normalizedModulePath = modulePath.replace('\\', '/');
        if (normalizedModulePath.startsWith("/") || normalizedModulePath.contains("..")) //$NON-NLS-1$ //$NON-NLS-2$
        {
            return SourceResolution.error(ToolResult.error("modulePath must be relative to src and must not contain '..'") //$NON-NLS-1$
                    .put("reason", "invalid_module_path")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!normalizedModulePath.toLowerCase().endsWith(".bsl")) //$NON-NLS-1$
        {
            return SourceResolution.error(ToolResult.error("modulePath must point to a .bsl file") //$NON-NLS-1$
                    .put("reason", "non_bsl_file")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists())
        {
            return SourceResolution.error(ToolResult.error("Project not found: " + projectName) //$NON-NLS-1$
                    .put("reason", "project_not_found")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!project.isOpen())
        {
            return SourceResolution.error(ToolResult.error("Project is closed: " + projectName) //$NON-NLS-1$
                    .put("reason", "project_closed")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        IFile file = project.getFile(new Path("src").append(normalizedModulePath)); //$NON-NLS-1$
        if (!file.exists())
        {
            return SourceResolution.error(ToolResult.error("File not found: src/" + normalizedModulePath) //$NON-NLS-1$
                    .put("reason", "file_not_found")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!"bsl".equalsIgnoreCase(file.getFileExtension())) //$NON-NLS-1$
        {
            return SourceResolution.error(ToolResult.error("File is not a BSL module: src/" + normalizedModulePath) //$NON-NLS-1$
                    .put("reason", "non_bsl_file")); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try
        {
            int lineCount = countLines(file);
            if (lineNumber > lineCount)
            {
                return SourceResolution.error(ToolResult.error("lineNumber is outside the file range") //$NON-NLS-1$
                        .put("reason", "line_out_of_range") //$NON-NLS-1$ //$NON-NLS-2$
                        .put("lineCount", lineCount)); //$NON-NLS-1$
            }
            return SourceResolution.success(file);
        }
        catch (Exception e)
        {
            Activator.logError("Failed to read BSL source for breakpoint resolution", e); //$NON-NLS-1$
            return SourceResolution.error(ToolResult.error("Failed to read source: " + e.getMessage()) //$NON-NLS-1$
                    .put("reason", "source_read_error")); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private static int countLines(IFile file) throws Exception
    {
        int count = 0;
        try (InputStream stream = file.getContents();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
        {
            while (reader.readLine() != null)
            {
                count++;
            }
        }
        return count;
    }

    private static boolean isSupportedBslLineBreakpoint(IBreakpoint breakpoint) throws CoreException
    {
        if (!(breakpoint instanceof ILineBreakpoint) || !BSL_MODEL_ID.equals(breakpoint.getModelIdentifier()))
        {
            return false;
        }
        IMarker marker = breakpoint.getMarker();
        return marker != null && marker.exists() && marker.isSubtypeOf(BSL_LINE_MARKER_TYPE);
    }

    private static boolean hasExistingMarker(IBreakpoint breakpoint)
    {
        IMarker marker = breakpoint.getMarker();
        return marker != null && marker.exists();
    }

    private static boolean isOwnedByMcp(IBreakpoint breakpoint) throws CoreException
    {
        IMarker marker = breakpoint.getMarker();
        return marker != null && marker.exists() && OWNER_VALUE.equals(marker.getAttribute(OWNER_ATTRIBUTE, "")); //$NON-NLS-1$
    }

    private static BreakpointLocation locationOf(IBreakpoint breakpoint) throws CoreException
    {
        IMarker marker = breakpoint.getMarker();
        IResource resource = marker.getResource();
        IProject project = resource.getProject();
        String modulePath = modulePathFrom(resource);
        int lineNumber = breakpoint instanceof ILineBreakpoint lineBreakpoint ? lineBreakpoint.getLineNumber() : -1;
        return new BreakpointLocation(project.getName(), modulePath, lineNumber, resource);
    }

    private static String modulePathFrom(IResource resource)
    {
        IPath projectRelative = resource.getProjectRelativePath();
        if (projectRelative.segmentCount() > 1 && "src".equals(projectRelative.segment(0))) //$NON-NLS-1$
        {
            return projectRelative.removeFirstSegments(1).toPortableString();
        }
        return projectRelative.toPortableString();
    }

    private static boolean matchesFilter(BreakpointLocation location, String projectFilter, String modulePathFilter)
    {
        if (projectFilter != null && !projectFilter.isEmpty() && !projectFilter.equals(location.projectName))
        {
            return false;
        }
        return modulePathFilter == null || modulePathFilter.isEmpty()
                || modulePathFilter.replace('\\', '/').equals(location.modulePath);
    }

    private static JsonObject toBreakpointJson(IBreakpoint breakpoint, BreakpointLocation location, String breakpointId,
            long snapshotId, boolean created, boolean preExisting) throws CoreException
    {
        IMarker marker = breakpoint.getMarker();
        JsonObject object = new JsonObject();
        object.addProperty("breakpointId", breakpointId); //$NON-NLS-1$
        object.addProperty("snapshotId", Long.toString(snapshotId)); //$NON-NLS-1$
        object.addProperty("projectName", location.projectName); //$NON-NLS-1$
        object.addProperty("modulePath", location.modulePath); //$NON-NLS-1$
        object.addProperty("lineNumber", location.lineNumber); //$NON-NLS-1$
        object.addProperty("enabled", breakpoint.isEnabled()); //$NON-NLS-1$
        object.addProperty("registered", breakpoint.isRegistered()); //$NON-NLS-1$
        object.addProperty("persisted", breakpoint.isPersisted()); //$NON-NLS-1$
        object.addProperty("ownedByMcp", isOwnedByMcp(breakpoint)); //$NON-NLS-1$
        object.addProperty("created", created); //$NON-NLS-1$
        object.addProperty("preExisting", preExisting); //$NON-NLS-1$
        object.addProperty("className", breakpoint.getClass().getName()); //$NON-NLS-1$
        object.addProperty("modelIdentifier", breakpoint.getModelIdentifier()); //$NON-NLS-1$
        object.addProperty("markerType", marker.getType()); //$NON-NLS-1$
        object.addProperty("resourceFullPath", location.resource.getFullPath().toPortableString()); //$NON-NLS-1$
        object.addProperty("resourceLocation", location.resource.getLocation() != null //$NON-NLS-1$
                ? location.resource.getLocation().toOSString()
                : ""); //$NON-NLS-1$
        object.addProperty("backendCurrentHitCount", marker.getAttribute(CURRENT_HIT_COUNT_ATTRIBUTE, 0)); //$NON-NLS-1$
        object.addProperty("backendRuntimePlatformVersion", //$NON-NLS-1$
                marker.getAttribute(RUNTIME_PLATFORM_ATTRIBUTE, "")); //$NON-NLS-1$
        object.add("markerAttributes", markerAttributes(marker)); //$NON-NLS-1$
        return object;
    }

    private static JsonObject markerAttributes(IMarker marker) throws CoreException
    {
        JsonObject attributes = new JsonObject();
        for (Map.Entry<String, Object> entry : marker.getAttributes().entrySet())
        {
            Object value = entry.getValue();
            if (value instanceof Number number)
            {
                attributes.addProperty(entry.getKey(), number);
            }
            else if (value instanceof Boolean bool)
            {
                attributes.addProperty(entry.getKey(), bool);
            }
            else
            {
                attributes.addProperty(entry.getKey(), value != null ? value.toString() : ""); //$NON-NLS-1$
            }
        }
        return attributes;
    }

    private static String breakpointId(IBreakpoint breakpoint, BreakpointLocation location, long snapshotId)
    {
        Object[] parts = new Object[] {
                location.projectName,
                location.modulePath,
                location.lineNumber,
                markerIdentity(breakpoint),
                breakpoint.getClass().getName(),
                Integer.toHexString(System.identityHashCode(breakpoint))
        };
        int hash = Arrays.deepHashCode(parts);
        return "breakpoint-" + Long.toString(snapshotId, 36) + '-' + Integer.toUnsignedString(hash, 36); //$NON-NLS-1$
    }

    private static String markerIdentity(IBreakpoint breakpoint)
    {
        IMarker marker = breakpoint.getMarker();
        if (marker == null)
        {
            return ""; //$NON-NLS-1$
        }
        return Long.toString(marker.getId());
    }

    private static IBreakpointManager getBreakpointManager()
    {
        DebugPlugin debugPlugin = DebugPlugin.getDefault();
        return debugPlugin != null ? debugPlugin.getBreakpointManager() : null;
    }

    private record BreakpointLocation(String projectName, String modulePath, int lineNumber, IResource resource)
    {
    }

    private static final class SourceResolution
    {
        private final IFile file;

        private final ToolResult error;

        private SourceResolution(IFile file, ToolResult error)
        {
            this.file = file;
            this.error = error;
        }

        private static SourceResolution success(IFile file)
        {
            return new SourceResolution(file, null);
        }

        private static SourceResolution error(ToolResult error)
        {
            return new SourceResolution(null, error);
        }
    }
}
