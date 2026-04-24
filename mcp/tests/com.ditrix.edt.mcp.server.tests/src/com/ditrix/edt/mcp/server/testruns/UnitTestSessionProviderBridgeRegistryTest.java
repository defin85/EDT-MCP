/**
 * MCP Server for EDT - Tests
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

import com.ditrix.edt.mcp.server.protocol.ToolResult;

public class UnitTestSessionProviderBridgeRegistryTest
{
    @Test
    public void testRegistryNormalizesProviderLookup()
    {
        UnitTestSessionProviderBridgeRegistry registry = new UnitTestSessionProviderBridgeRegistry();
        UnitTestSessionProviderBridge bridge = new FakeBridge("YAXUNIT"); //$NON-NLS-1$

        registry.register(bridge);

        assertSame(bridge, registry.get("yaxunit")); //$NON-NLS-1$
        assertSame(bridge, registry.get(" YAXUNIT ")); //$NON-NLS-1$
        assertNull(registry.get("vanessa")); //$NON-NLS-1$
    }

    @Test
    public void testPrepareAndRecycleResultsCarryTypedPayloads()
    {
        UnitTestSessionSnapshot snapshot = new UnitTestSessionRegistry(60_000L).createStartingSession(null,
                new UnitTestSessionTarget("yaxunit", "Demo", "app-1", null), Map.of("pid", 1234)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

        UnitTestSessionProviderBridge.PrepareResult prepared = UnitTestSessionProviderBridge.PrepareResult
                .prepared(snapshot);
        UnitTestSessionProviderBridge.RecycleResult recycled = UnitTestSessionProviderBridge.RecycleResult
                .recycled(UnitTestSessionRecycleOutcome.MARKED_STALE, snapshot, null);

        assertTrue(prepared.isSuccess());
        assertEquals(snapshot, prepared.getSnapshot());
        assertTrue(recycled.isSuccess());
        assertEquals(UnitTestSessionRecycleOutcome.MARKED_STALE, recycled.getOutcome());
        assertEquals(snapshot, recycled.getOldSnapshot());
        assertNull(recycled.getReplacementSnapshot());
    }

    @Test
    public void testFailureResultsAreExplicit()
    {
        UnitTestSessionProviderBridge.PrepareResult prepared = UnitTestSessionProviderBridge.PrepareResult
                .failed(ToolResult.error("provider unavailable")); //$NON-NLS-1$

        assertTrue(!prepared.isSuccess());
        assertNotNull(prepared.getFailureResult());
    }

    private static final class FakeBridge implements UnitTestSessionProviderBridge
    {
        private final String provider;

        private FakeBridge(String provider)
        {
            this.provider = provider;
        }

        @Override
        public String getProvider()
        {
            return provider;
        }

        @Override
        public PrepareResult prepare(PrepareRequest request)
        {
            return PrepareResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }

        @Override
        public ExecuteResult execute(ExecuteRequest request)
        {
            return ExecuteResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }

        @Override
        public RecycleResult recycle(RecycleRequest request)
        {
            return RecycleResult.failed(ToolResult.error("not implemented")); //$NON-NLS-1$
        }
    }
}
