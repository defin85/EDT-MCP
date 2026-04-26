/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.junit.Test;

import com.ditrix.edt.mcp.server.tools.debug.RuntimeDebugBreakpointBridge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RuntimeDebugControlToolTest
{
    @Test
    public void testGetDebugStackRequiresThreadId()
    {
        JsonObject payload = parse(new GetDebugStackTool().execute(Map.of()));

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("threadId is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testGetDebugStackReportsStaleThreadId()
    {
        JsonObject payload = parse(new GetDebugStackTool().execute(Map.of("threadId", "thread-stale"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("stale_thread_id", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testGetDebugVariablesRequiresFrameId()
    {
        JsonObject payload = parse(new GetDebugVariablesTool().execute(Map.of()));

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("frameId is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testGetDebugVariablesReportsStaleFrameId()
    {
        JsonObject payload = parse(new GetDebugVariablesTool().execute(Map.of("frameId", "frame-stale"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("stale_frame_id", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testEvaluateDebugExpressionRequiresFrameId()
    {
        JsonObject payload = parse(new EvaluateDebugExpressionTool().execute(Map.of("expression", "1 + 1"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("frameId is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testEvaluateDebugExpressionRequiresExpression()
    {
        JsonObject payload = parse(new EvaluateDebugExpressionTool().execute(Map.of("frameId", "frame-1"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("expression is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testEvaluateDebugExpressionReportsStaleFrameId()
    {
        JsonObject payload = parse(new EvaluateDebugExpressionTool()
                .execute(Map.of("frameId", "frame-stale", "expression", "1 + 1"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("stale_frame_id", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testControlDebugSessionRequiresAction()
    {
        JsonObject payload = parse(new ControlDebugSessionTool().execute(Map.of("threadId", "thread-1"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("action is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testControlDebugSessionRequiresTargetId()
    {
        JsonObject payload = parse(new ControlDebugSessionTool().execute(Map.of("action", "resume"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("sessionId or threadId is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testControlDebugSessionReportsStaleThreadId()
    {
        JsonObject payload = parse(new ControlDebugSessionTool()
                .execute(Map.of("threadId", "thread-stale", "action", "resume"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("stale_thread_id", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testControlDebugSessionRequiresThreadIdForSuspend()
    {
        JsonObject payload = parse(new ControlDebugSessionTool()
                .execute(Map.of("sessionId", "session-stale", "action", "suspend"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("thread_id_required", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSetDebugBreakpointRequiresProjectName()
    {
        JsonObject payload = parse(new SetDebugBreakpointTool().execute(Map.of()));

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("projectName is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSetDebugBreakpointRequiresModulePath()
    {
        JsonObject payload = parse(new SetDebugBreakpointTool()
                .execute(Map.of("projectName", "Demo"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("modulePath is required", payload.get("error").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testSetDebugBreakpointRejectsInvalidLine()
    {
        JsonObject payload = parse(new SetDebugBreakpointTool()
                .execute(Map.of("projectName", "Demo", "modulePath", "CommonModules/A/Module.bsl", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        "lineNumber", "0"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("invalid_line_number", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRunToDebugBreakpointRequiresThreadOrApplication()
    {
        JsonObject payload = parse(new RunToDebugBreakpointTool().execute(Map.of(
                "projectName", "Demo", //$NON-NLS-1$ //$NON-NLS-2$
                "modulePath", "CommonModules/A/Module.bsl", //$NON-NLS-1$ //$NON-NLS-2$
                "lineNumber", "1"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("debug_target_required", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRemoveDebugBreakpointReportsStaleId()
    {
        JsonObject payload = parse(new RemoveDebugBreakpointTool()
                .execute(Map.of("breakpointId", "breakpoint-stale"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals("stale_breakpoint_id", payload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCleanupMcpDebugBreakpointsIsSafeWhenEmpty()
    {
        JsonObject payload = parse(new CleanupMcpDebugBreakpointsTool().execute(Map.of("dryRun", "true"))); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(0, payload.get("removedCount").getAsInt()); //$NON-NLS-1$
        assertEquals(0, payload.get("failedCount").getAsInt()); //$NON-NLS-1$
    }

    @Test
    public void testCleanupMcpDebugBreakpointsRemovesOnlyOwnedBreakpoints()
            throws Exception
    {
        String projectName = "BreakpointCleanupTest"; //$NON-NLS-1$
        String modulePath = "CommonModules/Test/Module.bsl"; //$NON-NLS-1$
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        deleteProjectIfExists(project);

        try
        {
            project.create(null);
            project.open(null);
            IFile file = project.getFile(new Path("src").append(modulePath)); //$NON-NLS-1$
            createFileWithParents(file, "Procedure Test()\n\tMessage(\"owned\");\n\tMessage(\"user\");\nEndProcedure\n"); //$NON-NLS-1$

            JsonObject ownedPayload = parse(new SetDebugBreakpointTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath, //$NON-NLS-1$
                    "lineNumber", "2"))); //$NON-NLS-1$ //$NON-NLS-2$
            if (!ownedPayload.get("success").getAsBoolean()) //$NON-NLS-1$
            {
                assertEquals("unsupported_backend_capability", ownedPayload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("Unsupported backend is only expected when EDT debug core is absent: " + ownedPayload, //$NON-NLS-1$
                        Platform.getBundle("com._1c.g5.v8.dt.debug.core") == null); //$NON-NLS-1$
                return;
            }

            JsonObject userPayload = parse(new SetDebugBreakpointTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath, //$NON-NLS-1$
                    "lineNumber", "3"))); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(userPayload.get("success").getAsBoolean()); //$NON-NLS-1$
            String userBreakpointId = userPayload.getAsJsonObject("breakpoint") //$NON-NLS-1$
                    .get("breakpointId").getAsString(); //$NON-NLS-1$
            clearOwnerAttributeForLine(file, 3);

            JsonObject cleanupPayload = parse(new CleanupMcpDebugBreakpointsTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath))); //$NON-NLS-1$

            assertTrue(cleanupPayload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertEquals(1, cleanupPayload.get("removedCount").getAsInt()); //$NON-NLS-1$
            assertEquals(1, cleanupPayload.get("skippedCount").getAsInt()); //$NON-NLS-1$
            assertEquals(1, cleanupPayload.get("remainingCount").getAsInt()); //$NON-NLS-1$
            JsonArray skipped = cleanupPayload.getAsJsonArray("skipped"); //$NON-NLS-1$
            assertEquals("protected_user_breakpoint", skipped.get(0).getAsJsonObject().get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$

            JsonObject removeUserPayload = parse(new RemoveDebugBreakpointTool().execute(Map.of(
                    "breakpointId", userBreakpointId, //$NON-NLS-1$
                    "removeUserBreakpoint", "true"))); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(removeUserPayload.get("success").getAsBoolean()); //$NON-NLS-1$
        }
        finally
        {
            deleteProjectIfExists(project);
        }
    }

    @Test
    public void testBreakpointSetListDuplicateAndRemoveAgainstWorkspace()
            throws Exception
    {
        String projectName = "BreakpointBridgeTest"; //$NON-NLS-1$
        String modulePath = "CommonModules/Test/Module.bsl"; //$NON-NLS-1$
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        deleteProjectIfExists(project);

        try
        {
            project.create(null);
            project.open(null);
            IFile file = project.getFile(new Path("src").append(modulePath)); //$NON-NLS-1$
            createFileWithParents(file, "Procedure Test()\n\tMessage(\"x\");\nEndProcedure\n"); //$NON-NLS-1$

            JsonObject setPayload = parse(new SetDebugBreakpointTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath, //$NON-NLS-1$
                    "lineNumber", "2"))); //$NON-NLS-1$ //$NON-NLS-2$
            if (!setPayload.get("success").getAsBoolean()) //$NON-NLS-1$
            {
                assertEquals("unsupported_backend_capability", setPayload.get("reason").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
                assertTrue("Unsupported backend is only expected when EDT debug core is absent: " + setPayload, //$NON-NLS-1$
                        Platform.getBundle("com._1c.g5.v8.dt.debug.core") == null); //$NON-NLS-1$
                return;
            }
            assertTrue(setPayload.get("created").getAsBoolean()); //$NON-NLS-1$
            JsonObject breakpoint = setPayload.getAsJsonObject("breakpoint"); //$NON-NLS-1$
            assertEquals(modulePath, breakpoint.get("modulePath").getAsString()); //$NON-NLS-1$
            assertEquals(2, breakpoint.get("lineNumber").getAsInt()); //$NON-NLS-1$
            assertFalse(breakpoint.get("persisted").getAsBoolean()); //$NON-NLS-1$
            assertTrue(breakpoint.get("ownedByMcp").getAsBoolean()); //$NON-NLS-1$

            JsonObject duplicatePayload = parse(new SetDebugBreakpointTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath, //$NON-NLS-1$
                    "lineNumber", "2"))); //$NON-NLS-1$ //$NON-NLS-2$
            assertTrue(duplicatePayload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertFalse(duplicatePayload.get("created").getAsBoolean()); //$NON-NLS-1$
            assertTrue(duplicatePayload.get("preExisting").getAsBoolean()); //$NON-NLS-1$

            JsonObject listPayload = parse(new ListDebugBreakpointsTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath))); //$NON-NLS-1$
            assertTrue(listPayload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertEquals(1, listPayload.get("count").getAsInt()); //$NON-NLS-1$

            String breakpointId = breakpoint.get("breakpointId").getAsString(); //$NON-NLS-1$
            JsonObject removePayload = parse(new RemoveDebugBreakpointTool()
                    .execute(Map.of("breakpointId", breakpointId))); //$NON-NLS-1$
            assertTrue(removePayload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertTrue(removePayload.get("removed").getAsBoolean()); //$NON-NLS-1$

            JsonObject emptyListPayload = parse(new ListDebugBreakpointsTool().execute(Map.of(
                    "projectName", projectName, //$NON-NLS-1$
                    "modulePath", modulePath))); //$NON-NLS-1$
            assertTrue(emptyListPayload.get("success").getAsBoolean()); //$NON-NLS-1$
            assertEquals(0, emptyListPayload.get("count").getAsInt()); //$NON-NLS-1$
        }
        finally
        {
            deleteProjectIfExists(project);
        }
    }

    @Test
    public void testToolSchemasExposeSnapshotAndBoundedExpansionInputs()
    {
        assertTrue(new ListDebugSessionsTool().getInputSchema().contains("applicationId")); //$NON-NLS-1$
        assertTrue(new GetDebugStackTool().getInputSchema().contains("maxFrames")); //$NON-NLS-1$
        assertTrue(new GetDebugVariablesTool().getInputSchema().contains("variablePath")); //$NON-NLS-1$
        assertTrue(new GetDebugVariablesTool().getInputSchema().contains("maxVariables")); //$NON-NLS-1$
        assertTrue(new EvaluateDebugExpressionTool().getInputSchema().contains("timeoutSeconds")); //$NON-NLS-1$
        assertTrue(new EvaluateDebugExpressionTool().getInputSchema().contains("maxValueLength")); //$NON-NLS-1$
        assertTrue(new ControlDebugSessionTool().getInputSchema().contains("step_over")); //$NON-NLS-1$
        assertTrue(new ListDebugBreakpointsTool().getInputSchema().contains("modulePath")); //$NON-NLS-1$
        assertTrue(new SetDebugBreakpointTool().getInputSchema().contains("persisted")); //$NON-NLS-1$
        assertTrue(new RemoveDebugBreakpointTool().getInputSchema().contains("removeUserBreakpoint")); //$NON-NLS-1$
        assertTrue(new CleanupMcpDebugBreakpointsTool().getInputSchema().contains("dryRun")); //$NON-NLS-1$
        assertTrue(new RunToDebugBreakpointTool().getInputSchema().contains("cleanupOnTimeout")); //$NON-NLS-1$
    }

    private JsonObject parse(String json)
    {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void createFileWithParents(IFile file, String content)
            throws Exception
    {
        IContainer parent = file.getParent();
        if (parent instanceof IFolder folder)
        {
            createFolderWithParents(folder);
        }
        file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, null);
    }

    private static void clearOwnerAttributeForLine(IFile file, int lineNumber)
            throws CoreException
    {
        for (IMarker marker : file.findMarkers(null, true, IResource.DEPTH_ZERO))
        {
            if (marker.getAttribute(IMarker.LINE_NUMBER, -1) == lineNumber)
            {
                marker.setAttribute(RuntimeDebugBreakpointBridge.OWNER_ATTRIBUTE, (String)null);
                return;
            }
        }
        throw new AssertionError("No breakpoint marker for line " + lineNumber); //$NON-NLS-1$
    }

    private static void createFolderWithParents(IFolder folder)
            throws CoreException
    {
        IContainer parent = folder.getParent();
        if (parent instanceof IFolder parentFolder && !parentFolder.exists())
        {
            createFolderWithParents(parentFolder);
        }
        if (!folder.exists())
        {
            folder.create(IResource.NONE, true, null);
        }
    }

    private static void deleteProjectIfExists(IProject project)
            throws CoreException
    {
        if (project.exists())
        {
            project.delete(true, true, null);
        }
    }
}
