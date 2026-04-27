/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Source-tied diagnostics for query texts embedded in BSL modules.
 */
public class BslQueryDiagnosticsTool implements IMcpTool
{
    public static final String NAME = "diagnose_bsl_queries"; //$NON-NLS-1$

    private static final int DEFAULT_MAX_QUERIES = 20;
    private static final int MAX_QUERIES_LIMIT = 100;
    private static final int MAX_STATEMENT_LINES = 80;

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Extract and validate supported BSL query text assignments from a module or method, " //$NON-NLS-1$
                + "returning validation diagnostics tied to source locations."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("modulePath", "Path from src/, e.g. 'CommonModules/MyModule/Module.bsl' (required)", //$NON-NLS-1$ //$NON-NLS-2$
                        true)
                .stringProperty("methodName", "Optional procedure/function name to limit extraction scope") //$NON-NLS-1$ //$NON-NLS-2$
                .booleanProperty("dcsMode", //$NON-NLS-1$
                        "Validate extracted queries in DCS mode. Default: false") //$NON-NLS-1$
                .integerProperty("maxQueries", "Maximum extracted assignments to report (default 20, max 100)") //$NON-NLS-1$ //$NON-NLS-2$
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
        return ToolAnnotations.readOnly("Diagnose BSL queries"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String modulePath = JsonUtils.extractStringArgument(params, "modulePath"); //$NON-NLS-1$
        String methodName = JsonUtils.extractStringArgument(params, "methodName"); //$NON-NLS-1$
        boolean dcsMode = JsonUtils.extractBooleanArgument(params, "dcsMode", false); //$NON-NLS-1$
        int maxQueries = clamp(JsonUtils.extractIntArgument(params, "maxQueries", DEFAULT_MAX_QUERIES), //$NON-NLS-1$
                1, MAX_QUERIES_LIMIT);

        if (!hasText(projectName))
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(modulePath))
        {
            return ToolResult.error("modulePath is required").toJson(); //$NON-NLS-1$
        }

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists())
        {
            return ToolResult.error("Project not found: " + projectName).toJson(); //$NON-NLS-1$
        }
        if (!project.isOpen())
        {
            return ToolResult.error("Project is closed: " + projectName).toJson(); //$NON-NLS-1$
        }

        IFile file = project.getFile(new Path("src").append(modulePath)); //$NON-NLS-1$
        if (file == null || !file.exists())
        {
            return ToolResult.error("Module file not found: src/" + modulePath).toJson(); //$NON-NLS-1$
        }

        try
        {
            List<String> lines = BslModuleUtils.readFileLines(file);
            ExtractionResult extraction = extractQueries(lines, modulePath, methodName, maxQueries);
            return buildToolResult(projectName, modulePath, methodName, dcsMode, extraction).toString();
        }
        catch (Exception e)
        {
            Activator.logError("Error diagnosing BSL queries", e); //$NON-NLS-1$
            return ToolResult.error("Query diagnostics failed: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
    }

    private JsonObject buildToolResult(String projectName, String modulePath, String methodName, boolean dcsMode,
            ExtractionResult extraction)
    {
        JsonObject result = new JsonObject();
        result.addProperty("success", true); //$NON-NLS-1$
        result.addProperty("project", projectName); //$NON-NLS-1$
        result.addProperty("modulePath", modulePath); //$NON-NLS-1$
        if (hasText(methodName))
        {
            result.addProperty("methodName", methodName); //$NON-NLS-1$
        }
        result.addProperty("dcsMode", dcsMode); //$NON-NLS-1$
        result.addProperty("queryCount", extraction.queries.size()); //$NON-NLS-1$
        result.addProperty("limitationCount", extraction.limitations.size()); //$NON-NLS-1$

        JsonArray diagnostics = new JsonArray();
        ValidateQueryTool validator = new ValidateQueryTool();
        for (QueryExtraction query : extraction.queries)
        {
            JsonObject item = query.toJson();
            if (query.supportedExtraction)
            {
                item.add("validation", validateExtractedQuery(validator, projectName, query.queryText, dcsMode)); //$NON-NLS-1$
            }
            diagnostics.add(item);
        }
        result.add("diagnostics", diagnostics); //$NON-NLS-1$

        JsonArray limitations = new JsonArray();
        for (QueryLimitation limitation : extraction.limitations)
        {
            limitations.add(limitation.toJson());
        }
        result.add("limitations", limitations); //$NON-NLS-1$
        return result;
    }

    private JsonObject validateExtractedQuery(ValidateQueryTool validator, String projectName, String queryText,
            boolean dcsMode)
    {
        String json = validator.execute(Map.of(
                "projectName", projectName, //$NON-NLS-1$
                "queryText", queryText, //$NON-NLS-1$
                "dcsMode", Boolean.toString(dcsMode))); //$NON-NLS-1$
        try
        {
            return JsonParser.parseString(json).getAsJsonObject();
        }
        catch (Exception e)
        {
            JsonObject failure = new JsonObject();
            failure.addProperty("success", false); //$NON-NLS-1$
            failure.addProperty("error", "Unable to parse validate_query result"); //$NON-NLS-1$ //$NON-NLS-2$
            failure.addProperty("rawResult", json); //$NON-NLS-1$
            return failure;
        }
    }

    static ExtractionResult extractQueries(List<String> lines, String modulePath, String methodName, int maxQueries)
    {
        ExtractionResult result = new ExtractionResult();
        if (lines == null || lines.isEmpty())
        {
            return result;
        }

        MethodRange scope = findScope(lines, methodName);
        if (hasText(methodName) && scope == null)
        {
            result.limitations.add(QueryLimitation.methodNotFound(modulePath, methodName));
            return result;
        }

        List<MethodRange> methodRanges = findMethodRanges(lines);
        int from = scope != null ? scope.startLine : 1;
        int to = scope != null ? scope.endLine : lines.size();
        for (int lineIndex = from - 1; lineIndex < to && result.queries.size() < maxQueries; lineIndex++)
        {
            String line = lines.get(lineIndex);
            int assignmentOffset = findQueryTextAssignmentOffset(line);
            if (assignmentOffset < 0)
            {
                continue;
            }

            Statement statement = collectStatement(lines, lineIndex, to);
            String queryIdentifier = extractQueryIdentifier(statement.text, assignmentOffset);
            LiteralParseResult parsed = parseSupportedStringExpression(statement.text);
            String containingMethod = scope != null ? scope.name : findContainingMethod(methodRanges, lineIndex + 1);

            if (parsed.supported)
            {
                result.queries.add(QueryExtraction.supported(modulePath, containingMethod, queryIdentifier,
                        lineIndex + 1, statement.endLine, parsed.text));
            }
            else
            {
                QueryExtraction unsupported = QueryExtraction.unsupported(modulePath, containingMethod, queryIdentifier,
                        lineIndex + 1, statement.endLine, parsed.reason);
                result.queries.add(unsupported);
                result.limitations.add(QueryLimitation.dynamicText(modulePath, containingMethod, queryIdentifier,
                        lineIndex + 1, statement.endLine, parsed.reason));
            }
            lineIndex = statement.endLine - 1;
        }

        if (result.queries.size() >= maxQueries)
        {
            result.limitations.add(QueryLimitation.maxQueries(modulePath, maxQueries));
        }
        return result;
    }

    private static List<MethodRange> findMethodRanges(List<String> lines)
    {
        List<MethodRange> ranges = new ArrayList<>();
        MethodRange current = null;
        for (int i = 0; i < lines.size(); i++)
        {
            java.util.regex.Matcher startMatcher = BslModuleUtils.METHOD_START_PATTERN.matcher(lines.get(i));
            if (startMatcher.find())
            {
                current = new MethodRange(startMatcher.group(1), i + 1, lines.size());
                ranges.add(current);
                continue;
            }
            if (current != null && BslModuleUtils.METHOD_END_PATTERN.matcher(lines.get(i)).find())
            {
                current.endLine = i + 1;
                current = null;
            }
        }
        return ranges;
    }

    private static MethodRange findScope(List<String> lines, String methodName)
    {
        if (!hasText(methodName))
        {
            return null;
        }
        for (MethodRange range : findMethodRanges(lines))
        {
            if (methodName.equalsIgnoreCase(range.name))
            {
                return range;
            }
        }
        return null;
    }

    private static String findContainingMethod(List<MethodRange> ranges, int line)
    {
        for (MethodRange range : ranges)
        {
            if (line >= range.startLine && line <= range.endLine)
            {
                return range.name;
            }
        }
        return null;
    }

    private static int findQueryTextAssignmentOffset(String line)
    {
        if (line == null)
        {
            return -1;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        int textIndex = lower.indexOf(".текст"); //$NON-NLS-1$
        if (textIndex < 0)
        {
            textIndex = lower.indexOf(".text"); //$NON-NLS-1$
        }
        if (textIndex < 0)
        {
            return -1;
        }
        int equals = findCharOutsideString(line, '=', textIndex);
        return equals >= 0 ? equals : -1;
    }

    private static int findCharOutsideString(String text, char target, int from)
    {
        boolean inString = false;
        for (int i = Math.max(0, from); i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '"')
            {
                if (inString && i + 1 < text.length() && text.charAt(i + 1) == '"')
                {
                    i++;
                    continue;
                }
                inString = !inString;
            }
            if (!inString && c == target)
            {
                return i;
            }
        }
        return -1;
    }

    private static Statement collectStatement(List<String> lines, int startIndex, int maxLine)
    {
        StringBuilder text = new StringBuilder();
        int lastLine = startIndex + 1;
        int limit = Math.min(lines.size(), Math.min(maxLine, startIndex + MAX_STATEMENT_LINES));
        for (int i = startIndex; i < limit; i++)
        {
            if (text.length() > 0)
            {
                text.append('\n');
            }
            text.append(lines.get(i));
            lastLine = i + 1;
            if (hasTerminatorOutsideString(lines.get(i)))
            {
                break;
            }
        }
        return new Statement(text.toString(), lastLine);
    }

    private static boolean hasTerminatorOutsideString(String line)
    {
        return findCharOutsideString(line, ';', 0) >= 0;
    }

    private static String extractQueryIdentifier(String statement, int assignmentOffsetInFirstLine)
    {
        String firstLine = statement.split("\\R", 2)[0]; //$NON-NLS-1$
        int textIndex = firstLine.toLowerCase(Locale.ROOT).indexOf(".текст"); //$NON-NLS-1$
        if (textIndex < 0)
        {
            textIndex = firstLine.toLowerCase(Locale.ROOT).indexOf(".text"); //$NON-NLS-1$
        }
        if (textIndex <= 0)
        {
            return "query"; //$NON-NLS-1$
        }
        String left = firstLine.substring(0, Math.min(textIndex, assignmentOffsetInFirstLine)).trim();
        int whitespace = Math.max(left.lastIndexOf(' '), left.lastIndexOf('\t'));
        return whitespace >= 0 ? left.substring(whitespace + 1) : left;
    }

    private static LiteralParseResult parseSupportedStringExpression(String statement)
    {
        int equals = findCharOutsideString(statement, '=', 0);
        if (equals < 0)
        {
            return LiteralParseResult.unsupported("missing_assignment"); //$NON-NLS-1$
        }

        String rhs = statement.substring(equals + 1);
        StringBuilder query = new StringBuilder();
        boolean inString = false;
        boolean sawString = false;
        for (int i = 0; i < rhs.length(); i++)
        {
            char c = rhs.charAt(i);
            if (inString)
            {
                if (c == '"')
                {
                    if (i + 1 < rhs.length() && rhs.charAt(i + 1) == '"')
                    {
                        query.append('"');
                        i++;
                    }
                    else
                    {
                        inString = false;
                    }
                }
                else
                {
                    query.append(c);
                }
                continue;
            }

            if (c == '"')
            {
                inString = true;
                sawString = true;
                continue;
            }
            if (c == ';')
            {
                break;
            }
            if (c == '/' && i + 1 < rhs.length() && rhs.charAt(i + 1) == '/')
            {
                i = skipToLineEnd(rhs, i);
                continue;
            }
            if (Character.isWhitespace(c) || c == '+')
            {
                continue;
            }
            return LiteralParseResult.unsupported("dynamic_query_text"); //$NON-NLS-1$
        }

        if (inString)
        {
            return LiteralParseResult.unsupported("unterminated_string_literal"); //$NON-NLS-1$
        }
        if (!sawString)
        {
            return LiteralParseResult.unsupported("no_string_literal"); //$NON-NLS-1$
        }
        return LiteralParseResult.supported(normalizeBslQueryText(query.toString()));
    }

    static String normalizeBslQueryText(String queryText)
    {
        if (queryText == null || queryText.isEmpty())
        {
            return queryText;
        }

        String[] lines = queryText.split("\\R", -1); //$NON-NLS-1$
        List<String> normalized = new ArrayList<>(lines.length);
        for (String line : lines)
        {
            int index = 0;
            while (index < line.length() && Character.isWhitespace(line.charAt(index)))
            {
                index++;
            }
            if (index < line.length() && line.charAt(index) == '|')
            {
                normalized.add(line.substring(index + 1));
            }
            else
            {
                normalized.add(line);
            }
        }

        int first = 0;
        while (first < normalized.size() && normalized.get(first).isBlank())
        {
            first++;
        }
        int last = normalized.size() - 1;
        while (last >= first && normalized.get(last).isBlank())
        {
            last--;
        }
        if (first > last)
        {
            return ""; //$NON-NLS-1$
        }
        return String.join("\n", normalized.subList(first, last + 1)); //$NON-NLS-1$
    }

    private static int skipToLineEnd(String text, int index)
    {
        for (int i = index; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r')
            {
                return i;
            }
        }
        return text.length();
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    static final class ExtractionResult
    {
        final List<QueryExtraction> queries = new ArrayList<>();
        final List<QueryLimitation> limitations = new ArrayList<>();
    }

    static final class QueryExtraction
    {
        final String modulePath;
        final String methodName;
        final String queryIdentifier;
        final int startLine;
        final int endLine;
        final boolean supportedExtraction;
        final String queryText;
        final String limitationReason;

        private QueryExtraction(String modulePath, String methodName, String queryIdentifier, int startLine,
                int endLine, boolean supportedExtraction, String queryText, String limitationReason)
        {
            this.modulePath = modulePath;
            this.methodName = methodName;
            this.queryIdentifier = queryIdentifier;
            this.startLine = startLine;
            this.endLine = endLine;
            this.supportedExtraction = supportedExtraction;
            this.queryText = queryText;
            this.limitationReason = limitationReason;
        }

        static QueryExtraction supported(String modulePath, String methodName, String queryIdentifier, int startLine,
                int endLine, String queryText)
        {
            return new QueryExtraction(modulePath, methodName, queryIdentifier, startLine, endLine, true, queryText,
                    null);
        }

        static QueryExtraction unsupported(String modulePath, String methodName, String queryIdentifier, int startLine,
                int endLine, String reason)
        {
            return new QueryExtraction(modulePath, methodName, queryIdentifier, startLine, endLine, false, null,
                    reason);
        }

        JsonObject toJson()
        {
            JsonObject json = new JsonObject();
            json.addProperty("modulePath", modulePath); //$NON-NLS-1$
            if (hasText(methodName))
            {
                json.addProperty("methodName", methodName); //$NON-NLS-1$
            }
            json.addProperty("queryIdentifier", queryIdentifier); //$NON-NLS-1$
            json.addProperty("supportedExtraction", supportedExtraction); //$NON-NLS-1$
            JsonObject source = new JsonObject();
            source.addProperty("line", startLine); //$NON-NLS-1$
            source.addProperty("endLine", endLine); //$NON-NLS-1$
            json.add("source", source); //$NON-NLS-1$
            if (supportedExtraction)
            {
                json.addProperty("queryText", queryText); //$NON-NLS-1$
            }
            else
            {
                json.addProperty("limitationReason", limitationReason); //$NON-NLS-1$
            }
            return json;
        }
    }

    static final class QueryLimitation
    {
        final String id;
        final String modulePath;
        final String methodName;
        final String queryIdentifier;
        final int line;
        final int endLine;
        final String message;

        private QueryLimitation(String id, String modulePath, String methodName, String queryIdentifier, int line,
                int endLine, String message)
        {
            this.id = id;
            this.modulePath = modulePath;
            this.methodName = methodName;
            this.queryIdentifier = queryIdentifier;
            this.line = line;
            this.endLine = endLine;
            this.message = message;
        }

        static QueryLimitation dynamicText(String modulePath, String methodName, String queryIdentifier, int line,
                int endLine, String reason)
        {
            return new QueryLimitation(reason, modulePath, methodName, queryIdentifier, line, endLine,
                    "Query text is not a supported static string-literal assignment"); //$NON-NLS-1$
        }

        static QueryLimitation methodNotFound(String modulePath, String methodName)
        {
            return new QueryLimitation("method_not_found", modulePath, methodName, null, 0, 0, //$NON-NLS-1$
                    "Requested method was not found in the module"); //$NON-NLS-1$
        }

        static QueryLimitation maxQueries(String modulePath, int maxQueries)
        {
            return new QueryLimitation("max_queries_reached", modulePath, null, null, 0, 0, //$NON-NLS-1$
                    "Only the first " + maxQueries + " query assignments were reported"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        JsonObject toJson()
        {
            JsonObject json = new JsonObject();
            json.addProperty("id", id); //$NON-NLS-1$
            json.addProperty("modulePath", modulePath); //$NON-NLS-1$
            if (hasText(methodName))
            {
                json.addProperty("methodName", methodName); //$NON-NLS-1$
            }
            if (hasText(queryIdentifier))
            {
                json.addProperty("queryIdentifier", queryIdentifier); //$NON-NLS-1$
            }
            if (line > 0)
            {
                json.addProperty("line", line); //$NON-NLS-1$
                json.addProperty("endLine", endLine); //$NON-NLS-1$
            }
            json.addProperty("message", message); //$NON-NLS-1$
            return json;
        }
    }

    private static final class MethodRange
    {
        final String name;
        final int startLine;
        int endLine;

        MethodRange(String name, int startLine, int endLine)
        {
            this.name = name;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    private static final class Statement
    {
        final String text;
        final int endLine;

        Statement(String text, int endLine)
        {
            this.text = text;
            this.endLine = endLine;
        }
    }

    private static final class LiteralParseResult
    {
        final boolean supported;
        final String text;
        final String reason;

        private LiteralParseResult(boolean supported, String text, String reason)
        {
            this.supported = supported;
            this.text = text;
            this.reason = reason;
        }

        static LiteralParseResult supported(String text)
        {
            return new LiteralParseResult(true, text, null);
        }

        static LiteralParseResult unsupported(String reason)
        {
            return new LiteralParseResult(false, null, reason);
        }
    }
}
