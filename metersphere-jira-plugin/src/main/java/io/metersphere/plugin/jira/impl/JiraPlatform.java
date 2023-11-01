package io.metersphere.plugin.jira.impl;


import io.metersphere.plugin.jira.client.JiraDefaultClient;
import io.metersphere.plugin.jira.domain.JiraIntegrationConfig;
import io.metersphere.plugin.jira.domain.JiraIssueProject;
import io.metersphere.plugin.jira.domain.JiraProjectConfig;
import io.metersphere.plugin.platform.dto.*;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jianxing
 */
@Extension
public class JiraPlatform extends AbstractPlatform {

    protected JiraDefaultClient jiraClient;

    public JiraPlatform(PlatformRequest request) {
        super(request);
        JiraIntegrationConfig integrationConfig = getIntegrationConfig(request.getIntegrationConfig(), JiraIntegrationConfig.class);
        jiraClient = new JiraDefaultClient(integrationConfig);
    }

    @Override
    public void validateIntegrationConfig() {
        jiraClient.auth();
    }

    @Override
    public void validateProjectConfig(String projectConfigStr) {
        //TODO 平台key校验
        try {
            JiraProjectConfig projectConfig = getProjectConfig(projectConfigStr);
            JiraIssueProject project = jiraClient.getProject(projectConfig.getJiraKey());
            if (project != null && StringUtils.isBlank(project.getId())) {
                throw new MSPluginException("项目不存在");
            }
        } catch (Exception e) {
            throw new MSPluginException(e.getMessage());
        }
    }

    @Override
    public boolean isThirdPartTemplateSupport() {
        return true;
    }

    @Override
    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfig) {
        return null;
    }

    @Override
    public String addBug(PlatformBugUpdateRequest request) {
        return null;
    }

    @Override
    public void updateBug(PlatformBugUpdateRequest request) {

    }

    @Override
    public void deleteBug(String platformBugId) {

    }

    @Override
    public boolean isAttachmentUploadSupport() {
        return false;
    }

    @Override
    public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {

    }

    @Override
    public void syncPartIssues() {

    }

    @Override
    public void syncAllIssues() {

    }

    @Override
    public List<PlatformStatusDTO> getStatusList() {
        return null;
    }


    /**
     * 由 getFormOptions 反射调用
     *
     * @return
     */
    public List<SelectOption> getBugType(PluginOptionsRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return jiraClient.getIssueType(projectConfig.getJiraKey())
                .stream()
                .map(item -> new SelectOption(item.getName(), item.getId()))
                .collect(Collectors.toList());
    }


    public JiraProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            throw new MSPluginException("请在项目中添加项目配置！");
        }
        JiraProjectConfig projectConfig = PluginUtils.parseObject(configStr, JiraProjectConfig.class);
        return projectConfig;
    }
}
