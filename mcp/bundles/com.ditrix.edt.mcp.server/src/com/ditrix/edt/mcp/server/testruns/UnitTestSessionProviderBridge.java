/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.ToolResult;

/**
 * Provider-side persistent session control boundary.
 */
public interface UnitTestSessionProviderBridge
{
    String getProvider();

    PrepareResult prepare(PrepareRequest request) throws Exception;

    ExecuteResult execute(ExecuteRequest request) throws Exception;

    RecycleResult recycle(RecycleRequest request) throws Exception;

    final class PrepareRequest
    {
        private final String toolName;
        private final String ownerSessionId;
        private final UnitTestSessionTarget target;
        private final Map<String, Object> providerParameters;

        public PrepareRequest(String toolName, String ownerSessionId, UnitTestSessionTarget target,
                Map<String, Object> providerParameters)
        {
            this.toolName = toolName;
            this.ownerSessionId = ownerSessionId;
            this.target = target;
            this.providerParameters = providerParameters != null ? Map.copyOf(providerParameters) : Map.of();
        }

        public String getToolName()
        {
            return toolName;
        }

        public String getOwnerSessionId()
        {
            return ownerSessionId;
        }

        public UnitTestSessionTarget getTarget()
        {
            return target;
        }

        public Map<String, Object> getProviderParameters()
        {
            return providerParameters;
        }
    }

    final class PrepareResult
    {
        private final UnitTestSessionSnapshot snapshot;
        private final ToolResult failureResult;

        private PrepareResult(UnitTestSessionSnapshot snapshot, ToolResult failureResult)
        {
            this.snapshot = snapshot;
            this.failureResult = failureResult;
        }

        public static PrepareResult prepared(UnitTestSessionSnapshot snapshot)
        {
            return new PrepareResult(snapshot, null);
        }

        public static PrepareResult failed(ToolResult failureResult)
        {
            return new PrepareResult(null, failureResult);
        }

        public boolean isSuccess()
        {
            return snapshot != null && failureResult == null;
        }

        public UnitTestSessionSnapshot getSnapshot()
        {
            return snapshot;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    final class ExecuteRequest
    {
        private final String toolName;
        private final UnitTestSessionSnapshot currentSnapshot;
        private final YaxUnitRuntimeAdapter.RunRequest runRequest;
        private final Map<String, Object> providerParameters;

        public ExecuteRequest(String toolName, UnitTestSessionSnapshot currentSnapshot,
                YaxUnitRuntimeAdapter.RunRequest runRequest, Map<String, Object> providerParameters)
        {
            this.toolName = toolName;
            this.currentSnapshot = currentSnapshot;
            this.runRequest = runRequest;
            this.providerParameters = providerParameters != null ? Map.copyOf(providerParameters) : Map.of();
        }

        public String getToolName()
        {
            return toolName;
        }

        public UnitTestSessionSnapshot getCurrentSnapshot()
        {
            return currentSnapshot;
        }

        public YaxUnitRuntimeAdapter.RunRequest getRunRequest()
        {
            return runRequest;
        }

        public Map<String, Object> getProviderParameters()
        {
            return providerParameters;
        }
    }

    final class ExecuteResult
    {
        private final UnitTestRunRecord record;
        private final UnitTestSessionSnapshot updatedSnapshot;
        private final ToolResult failureResult;

        private ExecuteResult(UnitTestRunRecord record, UnitTestSessionSnapshot updatedSnapshot, ToolResult failureResult)
        {
            this.record = record;
            this.updatedSnapshot = updatedSnapshot;
            this.failureResult = failureResult;
        }

        public static ExecuteResult executed(UnitTestRunRecord record, UnitTestSessionSnapshot updatedSnapshot)
        {
            return new ExecuteResult(record, updatedSnapshot, null);
        }

        public static ExecuteResult failed(ToolResult failureResult)
        {
            return new ExecuteResult(null, null, failureResult);
        }

        public boolean isSuccess()
        {
            return record != null && failureResult == null;
        }

        public UnitTestRunRecord getRecord()
        {
            return record;
        }

        public UnitTestSessionSnapshot getUpdatedSnapshot()
        {
            return updatedSnapshot;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }

    final class RecycleRequest
    {
        private final String toolName;
        private final String sessionId;
        private final UnitTestSessionSnapshot currentSnapshot;

        public RecycleRequest(String toolName, String sessionId, UnitTestSessionSnapshot currentSnapshot)
        {
            this.toolName = toolName;
            this.sessionId = sessionId;
            this.currentSnapshot = currentSnapshot;
        }

        public String getToolName()
        {
            return toolName;
        }

        public String getSessionId()
        {
            return sessionId;
        }

        public UnitTestSessionSnapshot getCurrentSnapshot()
        {
            return currentSnapshot;
        }
    }

    final class RecycleResult
    {
        private final UnitTestSessionRecycleOutcome outcome;
        private final UnitTestSessionSnapshot oldSnapshot;
        private final UnitTestSessionSnapshot replacementSnapshot;
        private final ToolResult failureResult;

        private RecycleResult(UnitTestSessionRecycleOutcome outcome, UnitTestSessionSnapshot oldSnapshot,
                UnitTestSessionSnapshot replacementSnapshot, ToolResult failureResult)
        {
            this.outcome = outcome;
            this.oldSnapshot = oldSnapshot;
            this.replacementSnapshot = replacementSnapshot;
            this.failureResult = failureResult;
        }

        public static RecycleResult recycled(UnitTestSessionRecycleOutcome outcome, UnitTestSessionSnapshot oldSnapshot,
                UnitTestSessionSnapshot replacementSnapshot)
        {
            return new RecycleResult(outcome, oldSnapshot, replacementSnapshot, null);
        }

        public static RecycleResult failed(ToolResult failureResult)
        {
            return new RecycleResult(null, null, null, failureResult);
        }

        public boolean isSuccess()
        {
            return outcome != null && failureResult == null;
        }

        public UnitTestSessionRecycleOutcome getOutcome()
        {
            return outcome;
        }

        public UnitTestSessionSnapshot getOldSnapshot()
        {
            return oldSnapshot;
        }

        public UnitTestSessionSnapshot getReplacementSnapshot()
        {
            return replacementSnapshot;
        }

        public ToolResult getFailureResult()
        {
            return failureResult;
        }
    }
}
