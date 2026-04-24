/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.testruns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reuse scope for a persistent unit-test session.
 */
public final class UnitTestSessionTarget
{
    private final String provider;
    private final String projectName;
    private final String applicationId;
    private final String applicationName;

    public UnitTestSessionTarget(String provider, String projectName, String applicationId, String applicationName)
    {
        this.provider = requireText(provider, "provider").toLowerCase(); //$NON-NLS-1$
        this.projectName = requireText(projectName, "projectName"); //$NON-NLS-1$
        this.applicationId = requireText(applicationId, "applicationId"); //$NON-NLS-1$
        this.applicationName = trimToNull(applicationName);
    }

    public String getProvider()
    {
        return provider;
    }

    public String getProjectName()
    {
        return projectName;
    }

    public String getApplicationId()
    {
        return applicationId;
    }

    public String getApplicationName()
    {
        return applicationName;
    }

    public boolean matchesReuseScope(UnitTestSessionTarget other)
    {
        return other != null && provider.equals(other.provider) && projectName.equals(other.projectName)
                && applicationId.equals(other.applicationId);
    }

    public Map<String, Object> toPublicMap()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", provider); //$NON-NLS-1$
        result.put("projectName", projectName); //$NON-NLS-1$
        result.put("applicationId", applicationId); //$NON-NLS-1$
        if (applicationName != null)
        {
            result.put("applicationName", applicationName); //$NON-NLS-1$
        }
        return result;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(provider, projectName, applicationId);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof UnitTestSessionTarget other))
        {
            return false;
        }
        return matchesReuseScope(other);
    }

    private static String requireText(String value, String name)
    {
        String trimmed = trimToNull(value);
        if (trimmed == null)
        {
            throw new IllegalArgumentException(name + " is required"); //$NON-NLS-1$
        }
        return trimmed;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
