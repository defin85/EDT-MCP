/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.List;
import java.util.Map;

import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeBridgeSupport;
import com.ditrix.edt.mcp.server.utils.ConfigurationRuntimeContextResolver;
import com.ditrix.edt.mcp.server.utils.ResolvedConfigurationRuntimeContext;
import com.e1c.g5.dt.applications.IApplication;
import com.e1c.g5.dt.applications.infobases.IInfobaseApplication;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Fail-closed document movement evidence probe.
 */
public class ProbeDocumentMovementsTool implements IMcpTool
{
    public static final String NAME = "probe_document_movements"; //$NON-NLS-1$

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 60;
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
        return "Read-only live-evidence probe for document movements by recorder. Fails closed " //$NON-NLS-1$
                + "until a headless-safe runtime register-read transport is proven."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("applicationId", "Application ID from get_applications (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("recorder", "Document recorder reference or identity to inspect (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringArrayProperty("registers", "Optional register FQN/name filters; omitted means all supported movement registers") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("timeoutSeconds", "Bounded probe timeout request (default 10, max 60)") //$NON-NLS-1$ //$NON-NLS-2$
                .integerProperty("sampleLimit", "Maximum sample records per register (default 20, max 100)") //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.JSON;
    }

    @Override
    public ToolAnnotations getAnnotations()
    {
        return ToolAnnotations.readOnly("Probe document movements"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String applicationId = JsonUtils.extractStringArgument(params, "applicationId"); //$NON-NLS-1$
        String recorder = JsonUtils.extractStringArgument(params, "recorder"); //$NON-NLS-1$
        List<String> registers = JsonUtils.extractArrayArgument(params, "registers"); //$NON-NLS-1$
        int timeoutSeconds = clamp(JsonUtils.extractIntArgument(params, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS), //$NON-NLS-1$
                1, MAX_TIMEOUT_SECONDS);
        int sampleLimit = clamp(JsonUtils.extractIntArgument(params, "sampleLimit", DEFAULT_SAMPLE_LIMIT), //$NON-NLS-1$
                0, MAX_SAMPLE_LIMIT);

        if (!hasText(projectName))
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(applicationId))
        {
            return ToolResult.error("applicationId is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(recorder))
        {
            return ToolResult.error("recorder is required").toJson(); //$NON-NLS-1$
        }

        ConfigurationRuntimeContextResolver.Resolution resolution = ConfigurationRuntimeContextResolver.resolve(NAME,
                projectName);
        if (!resolution.isResolved())
        {
            return buildUnsupportedResult(projectName, applicationId, null, recorder, registers, timeoutSeconds,
                    sampleLimit, "target_preflight_failed", "error", errorMessage(resolution.getFailureResult()), //$NON-NLS-1$ //$NON-NLS-2$
                    "Use a configuration project with get_applications-discovered applicationId.").toString(); //$NON-NLS-1$
        }

        ResolvedConfigurationRuntimeContext context = resolution.getContext();
        ConfigurationRuntimeContextResolver.ApplicationResolution applicationResolution =
                ConfigurationRuntimeContextResolver.resolveApplication(NAME, context, applicationId);
        if (!applicationResolution.isResolved())
        {
            return buildUnsupportedResult(projectName, applicationId, null, recorder, registers, timeoutSeconds,
                    sampleLimit, "target_preflight_failed", "error", //$NON-NLS-1$ //$NON-NLS-2$
                    errorMessage(applicationResolution.getFailureResult()),
                    "Use get_applications to select an existing infobase application target.").toString(); //$NON-NLS-1$
        }

        IApplication application = applicationResolution.getApplication();
        IInfobaseApplication infobaseApplication = applicationResolution.getInfobaseApplication();
        ToolResult accessFailure = ConfigurationRuntimeBridgeSupport.preflightAccessSettings(NAME, context,
                infobaseApplication, applicationId);
        if (accessFailure != null)
        {
            return buildUnsupportedResult(projectName, applicationId, application, recorder, registers, timeoutSeconds,
                    sampleLimit, "runtime_access_settings_unavailable", "unsupported", //$NON-NLS-1$ //$NON-NLS-2$
                    errorMessage(accessFailure),
                    "Configure valid EDT infobase access settings before retrying the probe.").toString(); //$NON-NLS-1$
        }

        return buildUnsupportedResult(projectName, applicationId, application, recorder, registers, timeoutSeconds,
                sampleLimit, "document_movement_read_transport_unavailable", "unsupported", //$NON-NLS-1$ //$NON-NLS-2$
                "No headless-safe EDT/runtime API is proven for register-record reads by recorder in this rollout.", //$NON-NLS-1$
                "Use this fail-closed evidence or add a narrow read-only runtime transport before enabling movement reads.") //$NON-NLS-1$
                        .toString();
    }

    static JsonObject buildUnsupportedResult(String projectName, String applicationId, IApplication application,
            String recorder, List<String> registers, int timeoutSeconds, int sampleLimit, String limitationId,
            String status, String message, String recommendation)
    {
        JsonObject result = new JsonObject();
        result.addProperty("success", true); //$NON-NLS-1$
        result.addProperty("status", status); //$NON-NLS-1$
        result.addProperty("readOnly", true); //$NON-NLS-1$
        result.addProperty("performed", false); //$NON-NLS-1$
        result.addProperty("timeoutSeconds", timeoutSeconds); //$NON-NLS-1$
        result.addProperty("timeout", false); //$NON-NLS-1$
        result.addProperty("sampleLimit", sampleLimit); //$NON-NLS-1$

        JsonObject preflight = new JsonObject();
        preflight.addProperty("target", "target_preflight_failed".equals(limitationId) ? "failed" : "resolved"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        preflight.addProperty("accessSettings", accessSettingsStatus(limitationId)); //$NON-NLS-1$
        preflight.addProperty("runtimeReadTransport", "unsupported"); //$NON-NLS-1$ //$NON-NLS-2$
        preflight.addProperty("runtimeBridge", "not_started"); //$NON-NLS-1$ //$NON-NLS-2$
        preflight.addProperty("unsupportedTarget", true); //$NON-NLS-1$
        result.add("preflight", preflight); //$NON-NLS-1$

        JsonObject applicationJson = new JsonObject();
        applicationJson.addProperty("projectName", projectName); //$NON-NLS-1$
        applicationJson.addProperty("applicationId", applicationId); //$NON-NLS-1$
        if (application != null)
        {
            applicationJson.addProperty("applicationName", application.getName()); //$NON-NLS-1$
        }
        result.add("application", applicationJson); //$NON-NLS-1$

        JsonObject target = new JsonObject();
        target.addProperty("type", "document_movements"); //$NON-NLS-1$ //$NON-NLS-2$
        target.addProperty("recorder", recorder); //$NON-NLS-1$
        target.add("registers", toJsonArray(registers)); //$NON-NLS-1$
        result.add("target", target); //$NON-NLS-1$

        JsonObject transport = new JsonObject();
        transport.addProperty("status", "unsupported"); //$NON-NLS-1$ //$NON-NLS-2$
        transport.addProperty("readOnlyProven", false); //$NON-NLS-1$
        transport.addProperty("queryExecution", "not_attempted"); //$NON-NLS-1$ //$NON-NLS-2$
        transport.addProperty("clientSuppliedQueryAccepted", false); //$NON-NLS-1$
        transport.addProperty("bridgeState", "not_started"); //$NON-NLS-1$ //$NON-NLS-2$
        result.add("transport", transport); //$NON-NLS-1$

        JsonObject evidence = new JsonObject();
        evidence.addProperty("recorder", recorder); //$NON-NLS-1$
        evidence.addProperty("rowCountKnown", false); //$NON-NLS-1$
        evidence.addProperty("totalRowCount", 0); //$NON-NLS-1$
        evidence.addProperty("registerCount", 0); //$NON-NLS-1$
        evidence.add("registers", new JsonArray()); //$NON-NLS-1$
        evidence.add("samples", new JsonArray()); //$NON-NLS-1$
        result.add("evidence", evidence); //$NON-NLS-1$

        JsonArray limitations = new JsonArray();
        JsonObject limitation = new JsonObject();
        limitation.addProperty("id", limitationId); //$NON-NLS-1$
        limitation.addProperty("severity", "blocker"); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("message", hasText(message) ? message : "Document movement reads are unsupported."); //$NON-NLS-1$ //$NON-NLS-2$
        limitation.addProperty("recommendation", recommendation); //$NON-NLS-1$
        limitations.add(limitation);
        result.add("limitations", limitations); //$NON-NLS-1$
        return result;
    }

    private static String accessSettingsStatus(String limitationId)
    {
        if ("runtime_access_settings_unavailable".equals(limitationId)) //$NON-NLS-1$
        {
            return "failed"; //$NON-NLS-1$
        }
        if ("document_movement_read_transport_unavailable".equals(limitationId)) //$NON-NLS-1$
        {
            return "valid"; //$NON-NLS-1$
        }
        return "not_checked"; //$NON-NLS-1$
    }

    private static String errorMessage(ToolResult result)
    {
        if (result == null)
        {
            return null;
        }
        try
        {
            JsonObject json = JsonParser.parseString(result.toJson()).getAsJsonObject();
            if (json.has("error")) //$NON-NLS-1$
            {
                return json.get("error").getAsString(); //$NON-NLS-1$
            }
        }
        catch (Exception e)
        {
            return null;
        }
        return null;
    }

    private static JsonArray toJsonArray(List<String> values)
    {
        JsonArray array = new JsonArray();
        if (values != null)
        {
            for (String value : values)
            {
                array.add(value);
            }
        }
        return array;
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
}
