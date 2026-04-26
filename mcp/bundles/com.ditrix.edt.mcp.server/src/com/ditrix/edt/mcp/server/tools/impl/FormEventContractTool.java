/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.tools.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.protocol.ToolResult;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.tools.ToolAnnotations;
import com.ditrix.edt.mcp.server.utils.MetadataPathResolver;
import com.google.gson.JsonObject;

/**
 * Checks that a form event is bound in metadata and implemented in the form module.
 */
public class FormEventContractTool implements IMcpTool
{
    public static final String NAME = "check_form_event_contract"; //$NON-NLS-1$

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Check a managed form event contract by verifying both form metadata binding and " //$NON-NLS-1$
                + "the corresponding form module handler procedure."; //$NON-NLS-1$
    }

    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
                .stringProperty("projectName", "EDT project name (required)", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("formPath", //$NON-NLS-1$
                        "Metadata form path, e.g. 'Catalog.Products.Forms.ItemForm' or 'CommonForm.MyForm'", true) //$NON-NLS-1$
                .stringProperty("eventName", "Form event name to inspect, e.g. BeforeWriteAtServer", true) //$NON-NLS-1$ //$NON-NLS-2$
                .stringProperty("handlerName", //$NON-NLS-1$
                        "Expected handler name. Optional when the metadata binding should determine the handler") //$NON-NLS-1$
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
        return ToolAnnotations.readOnly("Check form event contract"); //$NON-NLS-1$
    }

    @Override
    public String execute(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String formPath = JsonUtils.extractStringArgument(params, "formPath"); //$NON-NLS-1$
        String eventName = JsonUtils.extractStringArgument(params, "eventName"); //$NON-NLS-1$
        String handlerName = JsonUtils.extractStringArgument(params, "handlerName"); //$NON-NLS-1$

        if (!hasText(projectName))
        {
            return ToolResult.error("projectName is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(formPath))
        {
            return ToolResult.error("formPath is required").toJson(); //$NON-NLS-1$
        }
        if (!hasText(eventName))
        {
            return ToolResult.error("eventName is required").toJson(); //$NON-NLS-1$
        }

        String formFilePath = MetadataPathResolver.resolveFormFilePath(formPath);
        if (!hasText(formFilePath))
        {
            return ToolResult.error("Unsupported formPath: " + formPath).toJson(); //$NON-NLS-1$
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

        IFile formFile = project.getFile(new Path(formFilePath));
        if (formFile == null || !formFile.exists())
        {
            return ToolResult.error("Form metadata file not found: " + formFilePath).toJson(); //$NON-NLS-1$
        }

        String moduleFilePath = formFilePath.substring(0, formFilePath.length() - "Form.form".length()) //$NON-NLS-1$
                + "Module.bsl"; //$NON-NLS-1$
        IFile moduleFile = project.getFile(new Path(moduleFilePath));

        try
        {
            String formXml = BslModuleUtils.readFileText(formFile);
            List<String> formLines = BslModuleUtils.readFileLines(formFile);
            List<String> moduleLines = moduleFile != null && moduleFile.exists()
                    ? BslModuleUtils.readFileLines(moduleFile) : List.of();
            ContractResult result = checkContract(formPath, formFilePath, moduleFilePath, formXml, formLines,
                    moduleLines, eventName, handlerName);
            return result.toJson(projectName).toString();
        }
        catch (Exception e)
        {
            Activator.logError("Error checking form event contract", e); //$NON-NLS-1$
            return ToolResult.error("Form event contract check failed: " + e.getMessage()).toJson(); //$NON-NLS-1$
        }
    }

    static ContractResult checkContract(String formPath, String formFilePath, String moduleFilePath, String formXml,
            List<String> formLines, List<String> moduleLines, String eventName, String expectedHandlerName)
    {
        EventBinding binding = extractEventBinding(formXml, formLines, eventName);
        String effectiveHandler = hasText(expectedHandlerName) ? expectedHandlerName
                : binding != null ? binding.handlerName : null;
        MethodLocation procedure = hasText(effectiveHandler) ? findMethod(moduleLines, effectiveHandler) : null;

        String status;
        if (binding == null)
        {
            status = "unbound_event"; //$NON-NLS-1$
        }
        else if (!hasText(binding.handlerName))
        {
            status = "unbound_event"; //$NON-NLS-1$
        }
        else if (hasText(expectedHandlerName) && !expectedHandlerName.equalsIgnoreCase(binding.handlerName))
        {
            status = "mismatched_handler_name"; //$NON-NLS-1$
        }
        else if (procedure == null)
        {
            status = "missing_procedure"; //$NON-NLS-1$
        }
        else
        {
            status = "valid"; //$NON-NLS-1$
        }

        return new ContractResult(formPath, formFilePath, moduleFilePath, eventName, expectedHandlerName, binding,
                procedure, status);
    }

    static EventBinding extractEventBinding(String formXml, List<String> formLines, String eventName)
    {
        if (!hasText(formXml) || !hasText(eventName))
        {
            return null;
        }

        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(formXml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();
            EventBinding structured = findStructuredBinding(root, eventName, formLines);
            if (structured != null)
            {
                return structured;
            }
        }
        catch (Exception e)
        {
            Activator.logWarning("Unable to parse form metadata XML for event binding: " + e.getMessage()); //$NON-NLS-1$
            return null;
        }

        return findTextualBinding(formLines, eventName);
    }

    private static EventBinding findStructuredBinding(Element root, String eventName, List<String> formLines)
    {
        NodeList nodes = root.getElementsByTagName("*"); //$NON-NLS-1$
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);
            if (!(node instanceof Element))
            {
                continue;
            }
            Element element = (Element) node;
            String eventCandidate = firstText(element, "event", "eventName", "name"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (!eventName.equalsIgnoreCase(nullToEmpty(eventCandidate)))
            {
                continue;
            }
            String handler = firstText(element, "handler", "handlerName", "method", "procedure", "action"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            if (!hasText(handler))
            {
                handler = firstAttribute(element, "handler", "handlerName", "method", "procedure", "action"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            }
            int line = findLine(formLines, eventName, handler);
            return new EventBinding(eventName, handler, line);
        }
        return null;
    }

    private static EventBinding findTextualBinding(List<String> formLines, String eventName)
    {
        if (formLines == null)
        {
            return null;
        }
        for (int i = 0; i < formLines.size(); i++)
        {
            String line = formLines.get(i);
            if (!containsIgnoreCase(line, eventName))
            {
                continue;
            }
            String handler = extractNearbyHandler(formLines, i);
            return new EventBinding(eventName, handler, i + 1);
        }
        return null;
    }

    private static String firstText(Element element, String... names)
    {
        for (String name : names)
        {
            String attribute = firstAttribute(element, name);
            if (hasText(attribute))
            {
                return attribute;
            }
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++)
            {
                Node child = children.item(i);
                if (child instanceof Element && name.equalsIgnoreCase(localName(child)))
                {
                    String text = child.getTextContent();
                    if (hasText(text))
                    {
                        return text.trim();
                    }
                }
            }
        }
        return null;
    }

    private static String firstAttribute(Element element, String... names)
    {
        NamedNodeMap attributes = element.getAttributes();
        for (String name : names)
        {
            for (int i = 0; i < attributes.getLength(); i++)
            {
                Node attribute = attributes.item(i);
                if (name.equalsIgnoreCase(localName(attribute)))
                {
                    return attribute.getNodeValue();
                }
            }
        }
        return null;
    }

    private static String localName(Node node)
    {
        if (node.getLocalName() != null)
        {
            return node.getLocalName();
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static String extractNearbyHandler(List<String> lines, int eventLineIndex)
    {
        int from = Math.max(0, eventLineIndex - 5);
        int to = Math.min(lines.size(), eventLineIndex + 6);
        for (int i = from; i < to; i++)
        {
            String line = lines.get(i);
            String tagValue = extractSimpleXmlTagValue(line, "handler"); //$NON-NLS-1$
            if (hasText(tagValue))
            {
                return tagValue;
            }
            tagValue = extractSimpleXmlTagValue(line, "handlerName"); //$NON-NLS-1$
            if (hasText(tagValue))
            {
                return tagValue;
            }
        }
        return null;
    }

    private static String extractSimpleXmlTagValue(String line, String tagName)
    {
        String lower = line.toLowerCase(Locale.ROOT);
        String open = "<" + tagName.toLowerCase(Locale.ROOT) + ">"; //$NON-NLS-1$ //$NON-NLS-2$
        String close = "</" + tagName.toLowerCase(Locale.ROOT) + ">"; //$NON-NLS-1$ //$NON-NLS-2$
        int start = lower.indexOf(open);
        int end = lower.indexOf(close);
        if (start >= 0 && end > start)
        {
            return line.substring(start + open.length(), end).trim();
        }
        return null;
    }

    static MethodLocation findMethod(List<String> lines, String handlerName)
    {
        if (lines == null || !hasText(handlerName))
        {
            return null;
        }
        int startLine = -1;
        String signature = null;
        for (int i = 0; i < lines.size(); i++)
        {
            java.util.regex.Matcher matcher = BslModuleUtils.METHOD_START_PATTERN.matcher(lines.get(i));
            if (matcher.find() && handlerName.equalsIgnoreCase(matcher.group(1)))
            {
                startLine = i + 1;
                signature = lines.get(i).trim();
                break;
            }
        }
        if (startLine < 0)
        {
            return null;
        }
        int endLine = lines.size();
        for (int i = startLine - 1; i < lines.size(); i++)
        {
            if (BslModuleUtils.METHOD_END_PATTERN.matcher(lines.get(i)).find())
            {
                endLine = i + 1;
                break;
            }
        }
        return new MethodLocation(handlerName, startLine, endLine, signature);
    }

    private static int findLine(List<String> lines, String eventName, String handler)
    {
        if (lines == null)
        {
            return 0;
        }
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            if (containsIgnoreCase(line, eventName) || hasText(handler) && containsIgnoreCase(line, handler))
            {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean containsIgnoreCase(String value, String needle)
    {
        return value != null && needle != null
                && value.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String nullToEmpty(String value)
    {
        return value != null ? value.trim() : ""; //$NON-NLS-1$
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    static final class ContractResult
    {
        final String formPath;
        final String formFilePath;
        final String moduleFilePath;
        final String eventName;
        final String expectedHandlerName;
        final EventBinding binding;
        final MethodLocation procedure;
        final String status;

        ContractResult(String formPath, String formFilePath, String moduleFilePath, String eventName,
                String expectedHandlerName, EventBinding binding, MethodLocation procedure, String status)
        {
            this.formPath = formPath;
            this.formFilePath = formFilePath;
            this.moduleFilePath = moduleFilePath;
            this.eventName = eventName;
            this.expectedHandlerName = expectedHandlerName;
            this.binding = binding;
            this.procedure = procedure;
            this.status = status;
        }

        JsonObject toJson(String projectName)
        {
            JsonObject json = new JsonObject();
            json.addProperty("success", true); //$NON-NLS-1$
            json.addProperty("project", projectName); //$NON-NLS-1$
            json.addProperty("formPath", formPath); //$NON-NLS-1$
            json.addProperty("formFilePath", formFilePath); //$NON-NLS-1$
            json.addProperty("moduleFilePath", moduleFilePath); //$NON-NLS-1$
            json.addProperty("eventName", eventName); //$NON-NLS-1$
            if (hasText(expectedHandlerName))
            {
                json.addProperty("expectedHandlerName", expectedHandlerName); //$NON-NLS-1$
            }
            json.addProperty("status", status); //$NON-NLS-1$

            JsonObject metadata = new JsonObject();
            metadata.addProperty("bound", binding != null && hasText(binding.handlerName)); //$NON-NLS-1$
            if (binding != null)
            {
                metadata.addProperty("eventLine", binding.line); //$NON-NLS-1$
                if (hasText(binding.handlerName))
                {
                    metadata.addProperty("handlerName", binding.handlerName); //$NON-NLS-1$
                }
            }
            json.add("metadataBinding", metadata); //$NON-NLS-1$

            JsonObject module = new JsonObject();
            module.addProperty("exists", procedure != null); //$NON-NLS-1$
            if (procedure != null)
            {
                module.addProperty("handlerName", procedure.name); //$NON-NLS-1$
                module.addProperty("line", procedure.startLine); //$NON-NLS-1$
                module.addProperty("endLine", procedure.endLine); //$NON-NLS-1$
                module.addProperty("signature", procedure.signature); //$NON-NLS-1$
                module.addProperty("signatureStatus", "reported"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else
            {
                module.addProperty("signatureStatus", "missing_or_unknown"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            json.add("moduleProcedure", module); //$NON-NLS-1$
            return json;
        }
    }

    static final class EventBinding
    {
        final String eventName;
        final String handlerName;
        final int line;

        EventBinding(String eventName, String handlerName, int line)
        {
            this.eventName = eventName;
            this.handlerName = handlerName;
            this.line = line;
        }
    }

    static final class MethodLocation
    {
        final String name;
        final int startLine;
        final int endLine;
        final String signature;

        MethodLocation(String name, int startLine, int endLine, String signature)
        {
            this.name = name;
            this.startLine = startLine;
            this.endLine = endLine;
            this.signature = signature;
        }
    }
}
