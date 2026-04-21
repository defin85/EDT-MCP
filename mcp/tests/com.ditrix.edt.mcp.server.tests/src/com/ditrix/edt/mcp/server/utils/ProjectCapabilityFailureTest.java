/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.List;

import org.junit.Test;

public class ProjectCapabilityFailureTest
{
    @Test
    public void testRequireConfigurationProjectRejectsExtensionProject()
    {
        ResolvedProjectContext context = extensionContext();

        ProjectCapabilityFailure.ValidationResult result = ProjectCapabilityFailure
                .requireConfigurationProject(context, "get_applications", "Configuration-only flow."); //$NON-NLS-1$ //$NON-NLS-2$

        assertTrue(result.hasFailure());
        assertEquals(ProjectCapabilityFailure.CATEGORY_CONFIGURATION_ONLY,
                errorCategory(result.getFailure())); //$NON-NLS-1$
        assertEquals("runtimeApplication", result.getFailure().toStructuredContent().get("requiredCapability")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRequireVerifiedExtensionSupportRejectsGuardedExtensionTool()
    {
        ResolvedProjectContext context = extensionContext();

        ProjectCapabilityFailure.ValidationResult result = ProjectCapabilityFailure
                .requireVerifiedExtensionSupport(context, "go_to_definition", ProjectCapability.MODULE_READ, //$NON-NLS-1$
                        "Outside verified matrix."); //$NON-NLS-1$

        assertTrue(result.hasFailure());
        assertEquals(ProjectCapabilityFailure.CATEGORY_UNSUPPORTED_EXTENSION_OPERATION,
                errorCategory(result.getFailure())); //$NON-NLS-1$
        assertEquals("moduleRead", result.getFailure().toStructuredContent().get("requiredCapability")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testRequireVerifiedExtensionSupportAllowsConfigurationProject()
    {
        ResolvedProjectContext context = new ResolvedProjectContext(null, ProjectKind.CONFIGURATION, true,
                EnumSet.allOf(ProjectCapability.class), List.of(), null);

        ProjectCapabilityFailure.ValidationResult result = ProjectCapabilityFailure
                .requireVerifiedExtensionSupport(context, "go_to_definition", ProjectCapability.MODULE_READ, //$NON-NLS-1$
                        "Outside verified matrix."); //$NON-NLS-1$

        assertFalse(result.hasFailure());
        assertNull(result.getFailure());
        assertEquals(ProjectKind.CONFIGURATION, result.getContext().getProjectKind());
    }

    private ResolvedProjectContext extensionContext()
    {
        return new ResolvedProjectContext(null, ProjectKind.EXTENSION, true,
                EnumSet.of(ProjectCapability.METADATA_READ, ProjectCapability.MODULE_READ), List.of(), "DemoExt"); //$NON-NLS-1$
    }

    @SuppressWarnings("unchecked")
    private String errorCategory(ProjectCapabilityFailure failure)
    {
        return (String) ((java.util.Map<String, Object>) failure.toStructuredContent().get("error")).get("category"); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
