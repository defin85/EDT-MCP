package com.ditrix.edt.mcp.server.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EMap;

import com._1c.g5.v8.dt.metadata.mdclass.Configuration;
import com.ditrix.edt.mcp.server.protocol.ToolResult;

/**
 * Shared helpers for serializing configuration root properties.
 */
public final class ConfigurationPropertiesSupport
{
    private ConfigurationPropertiesSupport()
    {
    }

    /**
     * Populates common configuration-root properties into the provided result.
     *
     * @param result target tool result
     * @param configuration configuration root
     * @return same result instance for chaining
     */
    public static ToolResult putCommonConfigurationProperties(ToolResult result, Configuration configuration)
    {
        if (result == null || configuration == null)
        {
            return result;
        }

        result.put("name", configuration.getName()) //$NON-NLS-1$
            .put("synonym", toLocalizedMap(configuration.getSynonym())) //$NON-NLS-1$
            .put("comment", configuration.getComment()) //$NON-NLS-1$
            .put("briefInformation", toLocalizedMap(configuration.getBriefInformation())) //$NON-NLS-1$
            .put("detailedInformation", toLocalizedMap(configuration.getDetailedInformation())) //$NON-NLS-1$
            .put("vendor", configuration.getVendor()) //$NON-NLS-1$
            .put("version", configuration.getVersion()) //$NON-NLS-1$
            .put("copyright", toLocalizedMap(configuration.getCopyright())) //$NON-NLS-1$
            .put("vendorInformationAddress", toLocalizedMap(configuration.getVendorInformationAddress())) //$NON-NLS-1$
            .put("configurationInformationAddress", toLocalizedMap(configuration.getConfigurationInformationAddress())); //$NON-NLS-1$

        if (configuration.getScriptVariant() != null)
        {
            result.put("scriptVariant", configuration.getScriptVariant().toString()); //$NON-NLS-1$
        }
        if (configuration.getDefaultRunMode() != null)
        {
            result.put("defaultRunMode", configuration.getDefaultRunMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getDataLockControlMode() != null)
        {
            result.put("dataLockControlMode", configuration.getDataLockControlMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getCompatibilityMode() != null)
        {
            result.put("compatibilityMode", configuration.getCompatibilityMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getModalityUseMode() != null)
        {
            result.put("modalityUseMode", configuration.getModalityUseMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getInterfaceCompatibilityMode() != null)
        {
            result.put("interfaceCompatibilityMode", configuration.getInterfaceCompatibilityMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getObjectAutonumerationMode() != null)
        {
            result.put("objectAutonumerationMode", configuration.getObjectAutonumerationMode().toString()); //$NON-NLS-1$
        }
        if (configuration.getDefaultLanguage() != null)
        {
            result.put("defaultLanguage", configuration.getDefaultLanguage().getName()); //$NON-NLS-1$
        }

        List<String> usePurposes = new ArrayList<>();
        if (configuration.getUsePurposes() != null)
        {
            for (Object purpose : configuration.getUsePurposes())
            {
                usePurposes.add(purpose.toString());
            }
        }
        result.put("usePurposes", usePurposes); //$NON-NLS-1$
        return result;
    }

    /**
     * Converts localized EMF maps to plain JSON-friendly maps.
     *
     * @param localizedString localized map from the EDT metadata model
     * @return regular map with string keys and values
     */
    public static Map<String, String> toLocalizedMap(EMap<?, ?> localizedString)
    {
        Map<String, String> map = new HashMap<>();
        if (localizedString == null)
        {
            return map;
        }

        for (Object entry : localizedString)
        {
            if (entry instanceof Map.Entry<?, ?> mapEntry)
            {
                String key = mapEntry.getKey() != null ? mapEntry.getKey().toString() : ""; //$NON-NLS-1$
                String value = mapEntry.getValue() != null ? mapEntry.getValue().toString() : ""; //$NON-NLS-1$
                map.put(key, value);
            }
        }
        return map;
    }
}
