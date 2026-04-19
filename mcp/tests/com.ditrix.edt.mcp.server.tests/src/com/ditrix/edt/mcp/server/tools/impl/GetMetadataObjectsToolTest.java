/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class GetMetadataObjectsToolTest
{
    @Test
    public void testCollectLimitedWithoutFilterUsesKnownSourceSizeAndStopsAtLimit()
    {
        List<String> source = List.of("DocA", "DocB", "DocC", "DocD"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        List<String> collected = new ArrayList<>();

        int total = GetMetadataObjectsTool.collectLimited(source, source.size(), null, 2, value -> value,
                collected::add);

        assertEquals(4, total);
        assertEquals(List.of("DocA", "DocB"), collected); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Test
    public void testCollectLimitedWithFilterCountsAllMatchesButOnlyCollectsUpToLimit()
    {
        List<String> source = List.of("Catalog", "Document", "CatalogItem", "Task"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        List<String> collected = new ArrayList<>();

        int total = GetMetadataObjectsTool.collectLimited(source, source.size(), "cat", 1, value -> value, //$NON-NLS-1$
                collected::add);

        assertEquals(2, total);
        assertEquals(List.of("Catalog"), collected); //$NON-NLS-1$
    }
}
