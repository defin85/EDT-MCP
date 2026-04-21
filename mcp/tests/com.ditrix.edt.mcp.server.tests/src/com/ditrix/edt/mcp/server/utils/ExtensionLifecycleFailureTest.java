/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ExtensionLifecycleFailure}.
 */
public class ExtensionLifecycleFailureTest
{
    @Test
    public void testParentMissingProducesStableCategory()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.parentMissing("get_extension_runtime_targets", context, //$NON-NLS-1$
                        "Parent project is required for runtime targets.") //$NON-NLS-1$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_PARENT_MISSING,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("extension", payload.get("projectKind").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("get_extension_runtime_targets", payload.get("tool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRuntimeCheckFailedCarriesTargetIdentifiers()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.runtimeCheckFailed("check_extension_applicability", context, //$NON-NLS-1$
                        "DORolf", "app-1", "Designer runtime check failed") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_RUNTIME_CHECK_FAILED,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("DORolf", payload.get("parentProjectName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", payload.get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRuntimeAccessSettingsRequiredUsesDedicatedCategory()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.runtimeAccessSettingsRequired("list_infobase_extensions", //$NON-NLS-1$
                        context, "DORolf", "app-1", "Configure access settings in EDT first.") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_RUNTIME_ACCESS_SETTINGS_REQUIRED,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("DORolf", payload.get("parentProjectName").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("app-1", payload.get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRuntimeBridgeBusyUsesDedicatedCategory()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.runtimeBridgeBusy("check_extension_applicability", context, //$NON-NLS-1$
                        "DORolf", "app-1", "Restart EDT before retrying.") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_RUNTIME_BRIDGE_BUSY,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("check_extension_applicability", payload.get("tool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRuntimeHeadlessUnsafeUsesDedicatedCategory()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.runtimeHeadlessUnsafe("check_extension_applicability", //$NON-NLS-1$
                        context, "DORolf", "app-1", "Use EDT UI manually.") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_RUNTIME_HEADLESS_UNSAFE,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("app-1", payload.get("applicationId").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testApplyFailedUsesDedicatedCategory()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ), List.of(ProjectContextResolver.EXTENSION_NATURE),
                "EXT_001"); //$NON-NLS-1$

        JsonObject payload = JsonParser
                .parseString(ExtensionLifecycleFailure.applyFailed("apply_extension_to_infobase", context, //$NON-NLS-1$
                        "DORolf", "app-1", "Synchronization failed") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .toJson())
                .getAsJsonObject();

        assertFalse(payload.get("success").getAsBoolean()); //$NON-NLS-1$
        assertEquals(ExtensionLifecycleFailure.CATEGORY_EXTENSION_APPLY_FAILED,
                payload.get("category").getAsString()); //$NON-NLS-1$
        assertEquals("apply_extension_to_infobase", payload.get("tool").getAsString()); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
