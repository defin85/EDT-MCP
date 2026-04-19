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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.emf.common.util.EMap;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.core.platform.IConfigurationProvider;
import com._1c.g5.v8.dt.metadata.mdclass.AccumulationRegister;
import com._1c.g5.v8.dt.metadata.mdclass.BusinessProcess;
import com._1c.g5.v8.dt.metadata.mdclass.Catalog;
import com._1c.g5.v8.dt.metadata.mdclass.CommonAttribute;
import com._1c.g5.v8.dt.metadata.mdclass.CommonModule;
import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com._1c.g5.v8.dt.metadata.mdclass.Constant;
import com._1c.g5.v8.dt.metadata.mdclass.DataProcessor;
import com._1c.g5.v8.dt.metadata.mdclass.Document;
import com._1c.g5.v8.dt.metadata.mdclass.EventSubscription;
import com._1c.g5.v8.dt.metadata.mdclass.ExchangePlan;
import com._1c.g5.v8.dt.metadata.mdclass.InformationRegister;
import com._1c.g5.v8.dt.metadata.mdclass.MdObject;
import com._1c.g5.v8.dt.metadata.mdclass.Report;
import com._1c.g5.v8.dt.metadata.mdclass.ScheduledJob;
import com._1c.g5.v8.dt.metadata.mdclass.Task;
import com.ditrix.edt.mcp.server.Activator;
import com.ditrix.edt.mcp.server.protocol.JsonSchemaBuilder;
import com.ditrix.edt.mcp.server.protocol.JsonUtils;
import com.ditrix.edt.mcp.server.tools.IMcpTool;
import com.ditrix.edt.mcp.server.utils.ProjectCapabilityFailure;
import com.ditrix.edt.mcp.server.utils.ProjectContextResolver;
import com.ditrix.edt.mcp.server.utils.ResolvedProjectContext;

/**
 * Tool to get list of metadata objects from 1C configuration.
 * Returns Name, Synonym, Type for each metadata object.
 */
public class GetMetadataObjectsTool implements IMcpTool
{
    public static final String NAME = "get_metadata_objects"; //$NON-NLS-1$
    private static final ThreadLocal<ProjectCapabilityFailure> LAST_FAILURE = new ThreadLocal<>();
    
    /** Metadata type constants (all lowercase for case-insensitive matching) */
    private static final String TYPE_ALL = "all"; //$NON-NLS-1$
    private static final String TYPE_DOCUMENTS = "documents"; //$NON-NLS-1$
    private static final String TYPE_CATALOGS = "catalogs"; //$NON-NLS-1$
    private static final String TYPE_INFORMATION_REGISTERS = "informationregisters"; //$NON-NLS-1$
    private static final String TYPE_ACCUMULATION_REGISTERS = "accumulationregisters"; //$NON-NLS-1$
    private static final String TYPE_COMMON_MODULES = "commonmodules"; //$NON-NLS-1$
    private static final String TYPE_ENUMS = "enums"; //$NON-NLS-1$
    private static final String TYPE_CONSTANTS = "constants"; //$NON-NLS-1$
    private static final String TYPE_REPORTS = "reports"; //$NON-NLS-1$
    private static final String TYPE_DATA_PROCESSORS = "dataprocessors"; //$NON-NLS-1$
    private static final String TYPE_EXCHANGE_PLANS = "exchangeplans"; //$NON-NLS-1$
    private static final String TYPE_BUSINESS_PROCESSES = "businessprocesses"; //$NON-NLS-1$
    private static final String TYPE_TASKS = "tasks"; //$NON-NLS-1$
    private static final String TYPE_COMMON_ATTRIBUTES = "commonattributes"; //$NON-NLS-1$
    private static final String TYPE_EVENT_SUBSCRIPTIONS = "eventsubscriptions"; //$NON-NLS-1$
    private static final String TYPE_SCHEDULED_JOBS = "scheduledjobs"; //$NON-NLS-1$
    
    @Override
    public String getName()
    {
        return NAME;
    }
    
    @Override
    public String getDescription()
    {
        return "Get list of metadata objects from 1C configuration. " + //$NON-NLS-1$
               "Returns Name, Synonym, Comment, Type, ObjectModule, ManagerModule for each object. " + //$NON-NLS-1$
               "Supports filtering by metadata type."; //$NON-NLS-1$
    }
    
    @Override
    public String getInputSchema()
    {
        return JsonSchemaBuilder.object()
            .stringProperty("projectName", //$NON-NLS-1$
                "EDT project name (required)", true) //$NON-NLS-1$
            .stringProperty("metadataType", //$NON-NLS-1$
                "Filter by metadata type: 'all', 'documents', 'catalogs', 'informationRegisters', " + //$NON-NLS-1$
                "'accumulationRegisters', 'commonModules', 'enums', 'constants', 'reports', 'dataProcessors', " + //$NON-NLS-1$
                "'exchangePlans', 'businessProcesses', 'tasks', 'commonAttributes', 'eventSubscriptions', " + //$NON-NLS-1$
                "'scheduledJobs'. Default: 'all'") //$NON-NLS-1$
            .stringProperty("nameFilter", //$NON-NLS-1$
                "Partial name match filter (case-insensitive)") //$NON-NLS-1$
            .integerProperty("limit", //$NON-NLS-1$
                "Maximum number of results. Default: 100") //$NON-NLS-1$
            .stringProperty("language", //$NON-NLS-1$
                "Language code for synonyms (e.g. 'en', 'ru'). If not specified, uses configuration default language.") //$NON-NLS-1$
            .build();
    }
    
    @Override
    public ResponseType getResponseType()
    {
        return ResponseType.MARKDOWN;
    }
    
    @Override
    public String getResultFileName(Map<String, String> params)
    {
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        if (projectName != null && !projectName.isEmpty())
        {
            return "metadata-" + projectName.toLowerCase() + ".md"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return "metadata-objects.md"; //$NON-NLS-1$
    }
    
    @Override
    public String execute(Map<String, String> params)
    {
        LAST_FAILURE.remove();
        String projectName = JsonUtils.extractStringArgument(params, "projectName"); //$NON-NLS-1$
        String metadataType = JsonUtils.extractStringArgument(params, "metadataType"); //$NON-NLS-1$
        String nameFilter = JsonUtils.extractStringArgument(params, "nameFilter"); //$NON-NLS-1$
        String limitStr = JsonUtils.extractStringArgument(params, "limit"); //$NON-NLS-1$
        String language = JsonUtils.extractStringArgument(params, "language"); //$NON-NLS-1$
        
        // Validate required parameter
        if (projectName == null || projectName.isEmpty())
        {
            return "Error: projectName is required"; //$NON-NLS-1$
        }
        
        // Set defaults
        if (metadataType == null || metadataType.isEmpty())
        {
            metadataType = TYPE_ALL;
        }
        // Note: language will be resolved from configuration default if null/empty
        
        int limit = 100;
        if (limitStr != null && !limitStr.isEmpty())
        {
            try
            {
                limit = Math.min((int) Double.parseDouble(limitStr), 1000);
            }
            catch (NumberFormatException e)
            {
                // Use default
            }
        }
        
        // Execute on UI thread
        AtomicReference<String> resultRef = new AtomicReference<>();
        AtomicReference<ProjectCapabilityFailure> failureRef = new AtomicReference<>();
        final String mdType = metadataType;
        final String filter = nameFilter;
        final int maxResults = limit;
        final String lang = language; // null means use config default
        final ResolvedProjectContext context = ProjectContextResolver.resolve(projectName);
        
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> {
            try
            {
                String result = getMetadataObjectsInternal(context, projectName, mdType, filter, maxResults, lang,
                        failureRef);
                resultRef.set(result);
            }
            catch (Exception e)
            {
                Activator.logError("Error getting metadata objects", e); //$NON-NLS-1$
                resultRef.set("Error: " + e.getMessage()); //$NON-NLS-1$
            }
        });

        ProjectCapabilityFailure failure = failureRef.get();
        if (failure != null)
        {
            LAST_FAILURE.set(failure);
        }
        
        return resultRef.get();
    }

    @Override
    public Object getStructuredContent(Map<String, String> params, String result)
    {
        try
        {
            ProjectCapabilityFailure failure = LAST_FAILURE.get();
            return failure != null ? failure.toStructuredContent() : null;
        }
        finally
        {
            LAST_FAILURE.remove();
        }
    }
    
    /**
     * Internal implementation that runs on UI thread.
     */
    private String getMetadataObjectsInternal(ResolvedProjectContext context, String projectName, String metadataType,
                                               String nameFilter, int limit, String language,
                                               AtomicReference<ProjectCapabilityFailure> failureRef)
    {
        IProject project = context != null && context.getProject() != null ? context.getProject()
                : ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (project == null || !project.exists())
        {
            return "Error: Project not found: " + projectName; //$NON-NLS-1$
        }
        
        // Get configuration
        IConfigurationProvider configProvider = Activator.getDefault().getConfigurationProvider();
        if (configProvider == null)
        {
            return "Error: Configuration provider not available"; //$NON-NLS-1$
        }
        
        Configuration config = configProvider.getConfiguration(project);
        if (config == null)
        {
            if (context != null && context.isExtensionProject())
            {
                ProjectCapabilityFailure failure = ProjectCapabilityFailure.extensionModelUnavailable(NAME, context,
                        "EDT did not provide a configuration model for extension metadata reads."); //$NON-NLS-1$
                failureRef.set(failure);
                return failure.toMarkdown();
            }
            return "Error: Could not get configuration for project: " + projectName; //$NON-NLS-1$
        }
        
        // Determine language for synonyms
        String effectiveLanguage = language;
        if (effectiveLanguage == null || effectiveLanguage.isEmpty())
        {
            // Use configuration default language
            if (config.getDefaultLanguage() != null)
            {
                effectiveLanguage = config.getDefaultLanguage().getName();
            }
            else
            {
                effectiveLanguage = "ru"; // Fallback to Russian //$NON-NLS-1$
            }
        }
        
        // Collect metadata objects
        List<MetadataInfo> objects = new ArrayList<>();
        int total;
        
        switch (metadataType.toLowerCase())
        {
            case TYPE_ALL:
                total = 0;
                total += collectDocuments(config, objects, nameFilter, limit);
                total += collectCatalogs(config, objects, nameFilter, limit);
                total += collectInformationRegisters(config, objects, nameFilter, limit);
                total += collectAccumulationRegisters(config, objects, nameFilter, limit);
                total += collectCommonModules(config, objects, nameFilter, limit);
                total += collectEnums(config, objects, nameFilter, limit);
                total += collectConstants(config, objects, nameFilter, limit);
                total += collectReports(config, objects, nameFilter, limit);
                total += collectDataProcessors(config, objects, nameFilter, limit);
                total += collectExchangePlans(config, objects, nameFilter, limit);
                total += collectBusinessProcesses(config, objects, nameFilter, limit);
                total += collectTasks(config, objects, nameFilter, limit);
                total += collectCommonAttributes(config, objects, nameFilter, limit);
                total += collectEventSubscriptions(config, objects, nameFilter, limit);
                total += collectScheduledJobs(config, objects, nameFilter, limit);
                break;
            case TYPE_DOCUMENTS:
                total = collectDocuments(config, objects, nameFilter, limit);
                break;
            case TYPE_CATALOGS:
                total = collectCatalogs(config, objects, nameFilter, limit);
                break;
            case TYPE_INFORMATION_REGISTERS:
                total = collectInformationRegisters(config, objects, nameFilter, limit);
                break;
            case TYPE_ACCUMULATION_REGISTERS:
                total = collectAccumulationRegisters(config, objects, nameFilter, limit);
                break;
            case TYPE_COMMON_MODULES:
                total = collectCommonModules(config, objects, nameFilter, limit);
                break;
            case TYPE_ENUMS:
                total = collectEnums(config, objects, nameFilter, limit);
                break;
            case TYPE_CONSTANTS:
                total = collectConstants(config, objects, nameFilter, limit);
                break;
            case TYPE_REPORTS:
                total = collectReports(config, objects, nameFilter, limit);
                break;
            case TYPE_DATA_PROCESSORS:
                total = collectDataProcessors(config, objects, nameFilter, limit);
                break;
            case TYPE_EXCHANGE_PLANS:
                total = collectExchangePlans(config, objects, nameFilter, limit);
                break;
            case TYPE_BUSINESS_PROCESSES:
                total = collectBusinessProcesses(config, objects, nameFilter, limit);
                break;
            case TYPE_TASKS:
                total = collectTasks(config, objects, nameFilter, limit);
                break;
            case TYPE_COMMON_ATTRIBUTES:
                total = collectCommonAttributes(config, objects, nameFilter, limit);
                break;
            case TYPE_EVENT_SUBSCRIPTIONS:
                total = collectEventSubscriptions(config, objects, nameFilter, limit);
                break;
            case TYPE_SCHEDULED_JOBS:
                total = collectScheduledJobs(config, objects, nameFilter, limit);
                break;
            default:
                return "Error: Unknown metadata type: " + metadataType + ". " + //$NON-NLS-1$ //$NON-NLS-2$
                       "Supported (case-insensitive): all, documents, catalogs, informationRegisters, accumulationRegisters, " + //$NON-NLS-1$
                       "commonModules, enums, constants, reports, dataProcessors, exchangePlans, " + //$NON-NLS-1$
                       "businessProcesses, tasks, commonAttributes, eventSubscriptions, scheduledJobs"; //$NON-NLS-1$
        }
        
        // Format output
        return formatOutput(projectName, objects, total, limit, effectiveLanguage, metadataType);
    }
    
    /**
     * Formats the output as markdown.
     */
    private String formatOutput(String projectName, List<MetadataInfo> objects, int total, int limit,
                                 String language, String metadataType)
    {
        StringBuilder sb = new StringBuilder();
        
        sb.append("## Configuration Metadata: ").append(projectName).append("\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
        
        int shown = Math.min(total, limit);
        
        if (!TYPE_ALL.equals(metadataType))
        {
            sb.append("**Filter:** ").append(metadataType).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("**Total:** ").append(total).append(" objects"); //$NON-NLS-1$ //$NON-NLS-2$
        if (shown < total)
        {
            sb.append(" (showing ").append(shown).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sb.append("\n\n"); //$NON-NLS-1$
        
        if (objects.isEmpty())
        {
            sb.append("No metadata objects found.\n"); //$NON-NLS-1$
            return sb.toString();
        }
        
        // Table header
        sb.append("| Name | Synonym | Comment | Type | ObjectModule | ManagerModule |\n"); //$NON-NLS-1$
        sb.append("|------|---------|---------|------|--------------|---------------|\n"); //$NON-NLS-1$
        
        // Table rows
        int count = 0;
        for (MetadataInfo info : objects)
        {
            if (count >= limit)
            {
                break;
            }
            
            // Get synonym for the specified language
            String displaySynonym = getSynonymForLanguage(info, language);
            String displayComment = info.comment != null ? info.comment : ""; //$NON-NLS-1$
            
            sb.append("| ").append(info.name); //$NON-NLS-1$
            sb.append(" | ").append(displaySynonym); //$NON-NLS-1$
            sb.append(" | ").append(displayComment); //$NON-NLS-1$
            sb.append(" | ").append(info.type); //$NON-NLS-1$
            sb.append(" | ").append(info.hasObjectModule ? "Yes" : "-"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sb.append(" | ").append(info.hasManagerModule ? "Yes" : "-"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            sb.append(" |\n"); //$NON-NLS-1$
            
            count++;
        }
        
        return sb.toString();
    }
    
    // ========== Collection methods ==========
    
    private int collectDocuments(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getDocuments(), config.getDocuments().size(), filter, limit, Document::getName,
                doc -> objects.add(createMetadataInfo(doc, "Document", hasModule(doc.getObjectModule()), //$NON-NLS-1$
                        hasModule(doc.getManagerModule()))));
    }
    
    private int collectCatalogs(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getCatalogs(), config.getCatalogs().size(), filter, limit, Catalog::getName,
                cat -> objects.add(createMetadataInfo(cat, "Catalog", hasModule(cat.getObjectModule()), //$NON-NLS-1$
                        hasModule(cat.getManagerModule()))));
    }
    
    private int collectInformationRegisters(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getInformationRegisters(), config.getInformationRegisters().size(), filter, limit,
                InformationRegister::getName,
                reg -> objects.add(createMetadataInfo(reg, "InformationRegister", //$NON-NLS-1$
                        hasModule(reg.getRecordSetModule()), hasModule(reg.getManagerModule()))));
    }
    
    private int collectAccumulationRegisters(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getAccumulationRegisters(), config.getAccumulationRegisters().size(), filter, limit,
                AccumulationRegister::getName,
                reg -> objects.add(createMetadataInfo(reg, "AccumulationRegister", //$NON-NLS-1$
                        hasModule(reg.getRecordSetModule()), hasModule(reg.getManagerModule()))));
    }
    
    private int collectCommonModules(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getCommonModules(), config.getCommonModules().size(), filter, limit,
                CommonModule::getName,
                mod -> objects.add(createMetadataInfo(mod, "CommonModule", hasModule(mod.getModule()), false))); //$NON-NLS-1$
    }
    
    private int collectEnums(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getEnums(), config.getEnums().size(), filter, limit,
                com._1c.g5.v8.dt.metadata.mdclass.Enum::getName,
                en -> objects.add(createMetadataInfo(en, "Enum", false, hasModule(en.getManagerModule())))); //$NON-NLS-1$
    }
    
    private int collectConstants(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getConstants(), config.getConstants().size(), filter, limit, Constant::getName,
                con -> objects.add(createMetadataInfo(con, "Constant", hasModule(con.getValueManagerModule()), //$NON-NLS-1$
                        hasModule(con.getManagerModule()))));
    }
    
    private int collectReports(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getReports(), config.getReports().size(), filter, limit, Report::getName,
                rep -> objects.add(createMetadataInfo(rep, "Report", hasModule(rep.getObjectModule()), //$NON-NLS-1$
                        hasModule(rep.getManagerModule()))));
    }
    
    private int collectDataProcessors(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getDataProcessors(), config.getDataProcessors().size(), filter, limit,
                DataProcessor::getName,
                dp -> objects.add(createMetadataInfo(dp, "DataProcessor", hasModule(dp.getObjectModule()), //$NON-NLS-1$
                        hasModule(dp.getManagerModule()))));
    }
    
    private int collectExchangePlans(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getExchangePlans(), config.getExchangePlans().size(), filter, limit,
                ExchangePlan::getName,
                ep -> objects.add(createMetadataInfo(ep, "ExchangePlan", hasModule(ep.getObjectModule()), //$NON-NLS-1$
                        hasModule(ep.getManagerModule()))));
    }
    
    private int collectBusinessProcesses(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getBusinessProcesses(), config.getBusinessProcesses().size(), filter, limit,
                BusinessProcess::getName,
                bp -> objects.add(createMetadataInfo(bp, "BusinessProcess", hasModule(bp.getObjectModule()), //$NON-NLS-1$
                        hasModule(bp.getManagerModule()))));
    }
    
    private int collectTasks(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getTasks(), config.getTasks().size(), filter, limit, Task::getName,
                task -> objects.add(createMetadataInfo(task, "Task", hasModule(task.getObjectModule()), //$NON-NLS-1$
                        hasModule(task.getManagerModule()))));
    }
    
    private int collectCommonAttributes(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getCommonAttributes(), config.getCommonAttributes().size(), filter, limit,
                CommonAttribute::getName,
                attr -> objects.add(createMetadataInfo(attr, "CommonAttribute", false, false))); //$NON-NLS-1$
    }
    
    private int collectEventSubscriptions(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getEventSubscriptions(), config.getEventSubscriptions().size(), filter, limit,
                EventSubscription::getName,
                sub -> objects.add(createMetadataInfo(sub, "EventSubscription", false, false))); //$NON-NLS-1$
    }
    
    private int collectScheduledJobs(Configuration config, List<MetadataInfo> objects, String filter, int limit)
    {
        return collectLimited(config.getScheduledJobs(), config.getScheduledJobs().size(), filter, limit,
                ScheduledJob::getName,
                job -> objects.add(createMetadataInfo(job, "ScheduledJob", false, false))); //$NON-NLS-1$
    }
    
    // ========== Helper methods ==========
    
    private MetadataInfo createMetadataInfo(MdObject mdObject, String type, boolean hasObjectModule, boolean hasManagerModule)
    {
        MetadataInfo info = new MetadataInfo();
        info.name = mdObject.getName();
        info.type = type;
        info.comment = mdObject.getComment();
        info.hasObjectModule = hasObjectModule;
        info.hasManagerModule = hasManagerModule;
        
        // Get synonyms - getSynonym() returns EMap<String, String> directly
        EMap<String, String> synonym = mdObject.getSynonym();
        if (synonym != null)
        {
            // Copy all language entries
            for (java.util.Map.Entry<String, String> entry : synonym.entrySet())
            {
                if (entry.getKey() != null && entry.getValue() != null)
                {
                    info.synonyms.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return info;
    }

    static <T> int collectLimited(Iterable<T> source, int sourceSize, String filter, int limit,
            Function<T, String> nameExtractor, Consumer<T> collector)
    {
        int safeLimit = Math.max(limit, 0);
        String normalizedFilter = normalizeFilter(filter);

        if (!hasText(normalizedFilter))
        {
            if (safeLimit > 0)
            {
                int collected = 0;
                for (T item : source)
                {
                    if (collected >= safeLimit)
                    {
                        break;
                    }
                    collector.accept(item);
                    collected++;
                }
            }
            return Math.max(sourceSize, 0);
        }

        int total = 0;
        int collected = 0;
        for (T item : source)
        {
            String name = nameExtractor != null ? nameExtractor.apply(item) : null;
            if (matchesFilter(name, normalizedFilter))
            {
                total++;
                if (collected < safeLimit)
                {
                    collector.accept(item);
                    collected++;
                }
            }
        }
        return total;
    }

    private static boolean matchesFilter(String name, String normalizedFilter)
    {
        if (!hasText(normalizedFilter))
        {
            return true;
        }
        return name != null && name.toLowerCase(Locale.ROOT).contains(normalizedFilter);
    }

    private static String normalizeFilter(String filter)
    {
        return hasText(filter) ? filter.toLowerCase(Locale.ROOT) : null;
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }
    
    private boolean hasModule(Module module)
    {
        return module != null;
    }
    
    /**
     * Gets synonym for the specified language with fallback.
     */
    private String getSynonymForLanguage(MetadataInfo info, String language)
    {
        // Try the requested language first
        String synonym = info.synonyms.get(language);
        if (synonym != null && !synonym.isEmpty())
        {
            return synonym;
        }
        
        // Fallback: try to find any available synonym
        for (String val : info.synonyms.values())
        {
            if (val != null && !val.isEmpty())
            {
                return val;
            }
        }
        
        return ""; //$NON-NLS-1$
    }
    
    /**
     * Holds metadata object information.
     */
    private static class MetadataInfo
    {
        String name;
        java.util.Map<String, String> synonyms = new java.util.HashMap<>();
        String comment;
        String type;
        boolean hasObjectModule;
        boolean hasManagerModule;
    }
}
