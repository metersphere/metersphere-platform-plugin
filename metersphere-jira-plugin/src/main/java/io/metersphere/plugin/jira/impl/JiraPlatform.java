package io.metersphere.plugin.jira.impl;


import io.metersphere.plugin.jira.client.JiraDefaultClient;
import io.metersphere.plugin.jira.constants.JiraMetadataField;
import io.metersphere.plugin.jira.constants.JiraMetadataFieldSearchMethod;
import io.metersphere.plugin.jira.constants.JiraMetadataSpecialCustomField;
import io.metersphere.plugin.jira.constants.JiraMetadataSpecialSystemField;
import io.metersphere.plugin.jira.domain.*;
import io.metersphere.plugin.jira.enums.JiraMetadataFieldType;
import io.metersphere.plugin.jira.enums.JiraOptionKey;
import io.metersphere.plugin.platform.dto.*;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import io.metersphere.plugin.platform.enums.SyncAttachmentType;
import io.metersphere.plugin.platform.spi.AbstractPlatform;
import io.metersphere.plugin.sdk.util.MSPluginException;
import io.metersphere.plugin.sdk.util.PluginLogUtils;
import io.metersphere.plugin.sdk.util.PluginUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author jianxing
 */
@Extension
public class JiraPlatform extends AbstractPlatform {

    protected JiraDefaultClient jiraClient;

    protected JiraProjectConfig projectConfig;

    public JiraPlatform(PlatformRequest request) {
        super(request);
        JiraIntegrationConfig integrationConfig = getIntegrationConfig(request.getIntegrationConfig(), JiraIntegrationConfig.class);
        jiraClient = new JiraDefaultClient(integrationConfig);
    }

    /**
     * 校验集成配置
     */
    @Override
    public void validateIntegrationConfig() {
        jiraClient.auth();
    }

    /**
     * 校验项目配置
     *
     * @param projectConfigStr 项目配置信息
     */
    @Override
    public void validateProjectConfig(String projectConfigStr) {
        // TODO 平台key校验
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

    public void setUserConfig(String userPlatformConfig) {
        JiraIntegrationConfig config = getIntegrationConfig(userPlatformConfig, JiraIntegrationConfig.class);
        validateAndSetConfig(config);
    }

    private void validateAndSetConfig(JiraIntegrationConfig config) {
        jiraClient.initConfig(config);
    }

    public void validateStoryType() {
        if (StringUtils.isBlank(projectConfig.getJiraDemandTypeId())) {
            throw new MSPluginException("请在项目中配置 Jira 需求类型!");
        }
    }

    public void validateIssueType() {
        if (StringUtils.isBlank(projectConfig.getJiraBugTypeId())) {
            throw new MSPluginException("请在项目中配置 Jira 缺陷类型!");
        }
    }

    public void validateProjectKey(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            throw new MSPluginException("请在项目设置配置 Jira 项目ID");
        }
    }

    /**
     * 是否支持第三方模板
     *
     * @return 支持第三方模板的平台才会在MS平台存在默认模板
     */
    @Override
    public boolean isThirdPartTemplateSupport() {
        // Jira 支持第三方默认模板
        return true;
    }

    /**
     * 获取第三方平台缺陷的自定义字段
     *
     * @param projectConfigStr 项目配置信息
     * @return 自定义字段集合
     */
    @Override
    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfigStr) {
        projectConfig = getProjectConfig(projectConfigStr);
        Map<String, String> optionData = prepareOptionData();
        Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClient.getCreateMetadata(projectConfig.getJiraKey(),
                projectConfig.getJiraBugTypeId());

        List<PlatformCustomFieldItemDTO> fields = new ArrayList<>();
        Character filedKey = 'A';
        for (String name : createMetadata.keySet()) {
            if (StringUtils.equals(JiraMetadataField.ATTACHMENT_NAME, name)) {
                // skip attachment field
                continue;
            }
            JiraCreateMetadataResponse.Field item = createMetadata.get(name);
            JiraCreateMetadataResponse.Schema schema = item.getSchema();
            PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
            setCustomFieldBaseProperty(item, customField, name, filedKey);
            setCustomFieldTypeAndOption(schema, customField, item.getAllowedValues(), optionData);
            setCustomFieldDefaultValue(customField, item);
            fields.add(customField);
            filedKey = handleRelateField(customField, fields, filedKey, optionData);
        }

        // 类型为空的字段不展示
        fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType())).collect(Collectors.toList());
        return sortCustomField(fields);
    }

    @Override
    public String addBug(PlatformBugUpdateRequest request) {
        // 校验服务集成配置
        validateConfig(request.getUserPlatformConfig(), request.getProjectConfig());

        // 过滤出链接事务字段
        List<PlatformCustomFieldItemDTO> issueLinkFields = filterIssueLinksField(request);
        // 处理字段参数, 构建Jira创建缺陷参数
        Map<String, Object> paramMap = buildParamMap(request, projectConfig.getJiraBugTypeId(), projectConfig.getJiraKey());
        JiraAddIssueResponse result = jiraClient.addIssue(PluginUtils.toJSONString(paramMap), getFieldNameMap(request));


        // 上传富文本中的图片作为附件
        // List<File> imageFiles = getImageFiles(request);
        // imageFiles.forEach(img -> jiraClient.uploadAttachment(result.getKey(), img));
        // link issue
        if (!CollectionUtils.isEmpty(issueLinkFields)) {
            linkIssue(issueLinkFields, result.getKey(), jiraClient.getIssueLinkType());
        }

        return result.getKey();
    }

    @Override
    public void updateBug(PlatformBugUpdateRequest request) {

    }

    @Override
    public void deleteBug(String platformBugId) {

    }

    @Override
    public boolean isAttachmentSupport() {
        // Jira 支持附件操作
        return true;
    }

    @Override
    public void syncAttachmentToPlatform(SyncAttachmentToPlatformRequest request) {
        String syncType = request.getSyncType();
        File file = request.getFile();
        if (StringUtils.equals(SyncAttachmentType.UPLOAD.syncOperateType(), syncType)) {
            // 上传附件
            jiraClient.uploadAttachment(request.getPlatformKey(), file);
        } else if (StringUtils.equals(SyncAttachmentType.DELETE.syncOperateType(), syncType)) {
            // 删除附件
            JiraIssue jiraIssue = jiraClient.getIssues(request.getPlatformKey());
            Map<String, Object> fields = jiraIssue.getFields();
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) fields.get(JiraMetadataField.ATTACHMENT_NAME);
            if (!CollectionUtils.isEmpty(attachments)) {
                for (Map<String, Object> attachment : attachments) {
                    String filename = attachment.get("filename").toString();
                    if (StringUtils.equals(filename, file.getName())) {
                        String fileId = attachment.get("id").toString();
                        jiraClient.deleteAttachment(fileId);
                    }
                }
            }
        }
    }

    @Override
    public void syncBugs() {

    }

    @Override
    public void syncAllBugs() {

    }

    /**
     * 获取第三方平台状态列表
     *
     * @param projectConfig 项目配置信息
     * @param issueKey      缺陷ID
     * @return 状态列表
     */
    @Override
    public List<PlatformStatusDTO> getStatusList(String projectConfig, String issueKey) {
        JiraProjectConfig config = getProjectConfig(projectConfig);
        List<PlatformStatusDTO> platformStatusDTOS = new ArrayList<>();
        if (StringUtils.isBlank(issueKey)) {
            // 缺陷ID为空时, 获取所有状态列表
            List<JiraStatusResponse> statusResponseList = jiraClient.getStatus(config.getJiraKey());
            List<List<JiraStatusResponse.Statuses>> issueTypeStatus = statusResponseList.stream().filter(statusResponse -> StringUtils.equals(config.getJiraBugTypeId(), statusResponse.getId()))
                    .map(JiraStatusResponse::getStatuses).toList();
            if (!CollectionUtils.isEmpty(issueTypeStatus)) {
                issueTypeStatus.forEach(item -> {
                    item.forEach(statuses -> {
                        PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
                        platformStatusDTO.setId(statuses.getName());
                        platformStatusDTO.setName(statuses.getName());
                        platformStatusDTOS.add(platformStatusDTO);
                    });

                });
            }
        } else {
            // 缺陷ID不为空时, 获取状态流
            List<JiraTransitionsResponse.Transitions> transitions = jiraClient.getTransitions(issueKey);
            if (!CollectionUtils.isEmpty(transitions)) {
                transitions.forEach(item -> {
                    PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
                    platformStatusDTO.setId(item.getTo().getName());
                    platformStatusDTO.setName(item.getTo().getName());
                    platformStatusDTOS.add(platformStatusDTO);
                });
            }
        }
        return platformStatusDTOS;
    }

    /**
     * 获取指派人选项值
     *
     * @param request 查询参数
     * @return 选项集合
     */
    public List<SelectOption> getAssignableOptions(GetOptionRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return getAssignableOptions(projectConfig.getJiraKey(), request.getQuery());
    }

    /**
     * 获取指派人选项值
     *
     * @param jiraKey 项目KEY
     * @param query   查询参数
     * @return 选项集合
     */
    private List<SelectOption> getAssignableOptions(String jiraKey, String query) {
        List<JiraUser> userOptions = jiraClient.assignableUserSearch(jiraKey, query);
        return handleUserOptions(userOptions);
    }

    /**
     * 获取用户选项值
     *
     * @param request 查询参数
     * @return 选项集合
     */
    public List<SelectOption> getUserSearchOptions(GetOptionRequest request) {
        return getUserSearchOptions(request.getQuery());
    }

    /**
     * 获取用户选项值
     *
     * @param query 查询参数
     * @return 选项集合
     */
    private List<SelectOption> getUserSearchOptions(String query) {
        List<JiraUser> reportOptions = jiraClient.allUserSearch(query);
        return handleUserOptions(reportOptions);
    }

    /**
     * 获取链接事务选项值
     *
     * @param request 请求参数
     * @return 选项集合
     */
    public List<SelectOption> getIssueLinkOptions(GetOptionRequest request) {
        return getIssueLinkOptions("", request.getQuery());
    }

    /**
     * 获取链接事务选项值
     *
     * @param currentIssueKey 当前缺陷ID
     * @param query           查询参数
     * @return 选项集合
     */
    public List<SelectOption> getIssueLinkOptions(String currentIssueKey, String query) {
        return jiraClient.getIssueLinks(currentIssueKey, query)
                .stream()
                .map(item -> new SelectOption(item.getKey() + StringUtils.SPACE + item.getSummary(), item.getKey())).toList();
    }

    /**
     * 获取Issue链接事务类型选项
     *
     * @return 选项集合
     */
    public List<SelectOption> getIssueLinkTypeOptions() {
        List<SelectOption> selectOptions = new ArrayList<>();
        List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes = jiraClient.getIssueLinkType();
        for (JiraIssueLinkTypeResponse.IssueLinkType issueLinkType : issueLinkTypes) {
            if (StringUtils.equals(issueLinkType.getInward(), issueLinkType.getOutward())) {
                selectOptions.add(new SelectOption(issueLinkType.getInward(), issueLinkType.getInward()));
            } else {
                selectOptions.add(new SelectOption(issueLinkType.getInward(), issueLinkType.getInward()));
                selectOptions.add(new SelectOption(issueLinkType.getOutward(), issueLinkType.getOutward()));
            }
        }
        return selectOptions;
    }

    /**
     * 获取sprint选项值
     *
     * @param request 请求参数
     * @return 选项集合
     */
    public List<SelectOption> getSprintOptions(GetOptionRequest request) {
        return jiraClient.getSprint(request.getQuery())
                .stream()
                .map(sprint -> new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString()))
                .collect(Collectors.toList());
    }

    /**
     * 获取Jira缺陷类型
     *
     * @param request
     * @return
     */
    public List<SelectOption> getBugType(PluginOptionsRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return jiraClient.getIssueType(projectConfig.getJiraKey())
                .stream()
                .map(item -> new SelectOption(item.getName(), item.getId()))
                .collect(Collectors.toList());
    }

    /**
     * 获取项目配置
     *
     * @param configStr 项目配置JSON
     * @return 项目配置对象
     */
    private JiraProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            throw new MSPluginException("请在项目中添加项目配置！");
        }
        return PluginUtils.parseObject(configStr, JiraProjectConfig.class);
    }

    private void setCustomFieldBaseProperty(JiraCreateMetadataResponse.Field item, PlatformCustomFieldItemDTO customField, String name, Character filedKey) {
        customField.setId(name);
        customField.setName(item.getName());
        customField.setKey(String.valueOf(filedKey++));
        customField.setCustomData(name);
        if (StringUtils.equals(JiraMetadataField.SUMMARY_FIELD_NAME, item.getKey())) {
            customField.setName(JiraMetadataField.SUMMARY_FIELD_NAME_MS_ZH);
            customField.setRequired(true);
        } else {
            customField.setRequired(item.isRequired());
        }
        if (StringUtils.equals(JiraMetadataField.DESCRIPTION_FIELD_NAME, item.getKey())) {
            customField.setName(JiraMetadataField.DESCRIPTION_FIELD_NAME_MS_ZH);
        }
    }

    /**
     * 设置自定义字段类型和选项
     *
     * @param schema        jira field schema
     * @param customField   自定义字段
     * @param allowedValues 允许的选项值
     */
    private void setCustomFieldTypeAndOption(JiraCreateMetadataResponse.Schema schema, PlatformCustomFieldItemDTO customField,
                                             List<JiraCreateMetadataResponse.AllowedValues> allowedValues, Map<String, String> optionData) {
        Set<String> specialCustomFieldType = new HashSet<>(JiraMetadataSpecialCustomField.getSpecialFields());
        String customType = schema.getCustom();
        if (StringUtils.isNotBlank(customType)) {
            // Jira自定义字段
            customField.setType(JiraMetadataFieldType.mappingJiraCustomType(customType));
            if (mappingSpecialField(specialCustomFieldType, customType)) {
                // 特殊自定义字段类型
                handleSpecialCustomFieldType(schema, customField, optionData);
            }
        } else {
            // Jira系统字段
            customField.setType(JiraMetadataFieldType.mappingJiraCustomType(customField.getId()));
            Set<String> specialSystemFieldType = new HashSet<>(JiraMetadataSpecialSystemField.getSpecialFields());
            if (specialSystemFieldType.contains(schema.getType()) || specialSystemFieldType.contains(schema.getSystem())) {
                // 特殊系统字段类型
                handleSpecialSystemFieldType(schema, customField, optionData);
            }
            if (StringUtils.isNotBlank(schema.getType()) && StringUtils.isBlank(customField.getType())) {
                customField.setType(JiraMetadataFieldType.mappingJiraCustomType(schema.getType()));
            }
        }
        // allowedValues不为空时, 替换options
        if (!CollectionUtils.isEmpty(allowedValues)) {
            customField.setOptions(PluginUtils.toJSONString(handleAllowedValuesOptions(allowedValues)));
        }
    }

    /**
     * 处理特殊自定义字段类型
     *
     * @param schema      jira field schema
     * @param customField 自定义字段
     */
    private void handleSpecialCustomFieldType(JiraCreateMetadataResponse.Schema schema, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
        String customType = schema.getCustom();
        String specialCustomFieldTypesOfSchemaType = "project";
        String arraySchemaType = "array";

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.MULTI_CHECK_BOX)) {
            // 多选复选框, 需设置默认值为空数组
            customField.setType(PlatformCustomFieldType.CHECKBOX.getType());
            customField.setDefaultValue(PluginUtils.toJSONString(new ArrayList<>()));
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.CUSTOM_FIELD_TYPES) && StringUtils.equals(schema.getType(), specialCustomFieldTypesOfSchemaType)) {
            // 插件字段类型为{自定义字段类型获取}
            customField.setType(PlatformCustomFieldType.SELECT.getType());
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.PEOPLE)) {
            // PEOPLE类型特殊字段
            if (StringUtils.isNotBlank(schema.getType()) && StringUtils.equals(schema.getType(), arraySchemaType)) {
                customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.getType());
            } else {
                customField.setType(PlatformCustomFieldType.SELECT.getType());
            }
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
            customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.USER_PICKER)) {
            // 自定义字段类型为用户选择器
            customField.setType(PlatformCustomFieldType.SELECT.getType());
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
            customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.MULTI_USER_PICKER)) {
            // 自定义字段类型为多用户选择器
            customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.getType());
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
            customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.SPRINT_FIELD_NAME)) {
            // 自定义字段类型为sprint
            customField.setOptions(optionData.get(JiraOptionKey.SPRINT.name()));
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_SPRINT);
            customField.setType(PlatformCustomFieldType.SELECT.getType());
        }

        if (StringUtils.contains(customType, JiraMetadataSpecialCustomField.EPIC_LINK)) {
            // 自定义字段类型为epic-link
            customField.setOptions(optionData.get(JiraOptionKey.EPIC.name()));
            customField.setType(PlatformCustomFieldType.SELECT.getType());
        }
    }

    /**
     * 处理特殊系统字段
     *
     * @param schema      jira field schema
     * @param customField 自定义字段
     */
    private void handleSpecialSystemFieldType(JiraCreateMetadataResponse.Schema schema, PlatformCustomFieldItemDTO customField, Map<String, String> optionData) {
        if (StringUtils.equals(schema.getType(), JiraMetadataSpecialSystemField.TIME_TRACKING)) {
            // TIME_TRACKING (特殊系统字段)
            customField.setId(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
            customField.setCustomData(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
            customField.setName(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME);
            customField.setType(PlatformCustomFieldType.INPUT.getType());
        }

        if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.ASSIGNEE)) {
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_ASSIGNABLE);
            customField.setOptions(optionData.get(JiraOptionKey.ASSIGN.name()));
            customField.setType(PlatformCustomFieldType.SELECT.getType());
        }

        if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.REPORTER)) {
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_USER);
            customField.setOptions(optionData.get(JiraOptionKey.USER.name()));
            customField.setType(PlatformCustomFieldType.SELECT.getType());
        }

        if (StringUtils.equals(schema.getSystem(), JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
            customField.setSupportSearch(true);
            customField.setSearchMethod(JiraMetadataFieldSearchMethod.GET_ISSUE_LINK);
            customField.setOptions(optionData.get(JiraOptionKey.ISSUE_LINK.name()));
            customField.setType(PlatformCustomFieldType.MULTIPLE_SELECT.getType());
        }
    }

    /**
     * 设置自定义字段默认值
     *
     * @param customField 自定义字段
     * @param item        Jira字段
     */
    private void setCustomFieldDefaultValue(PlatformCustomFieldItemDTO customField, JiraCreateMetadataResponse.Field item) {
        if (item.isHasDefaultValue()) {
            Object defaultValue = item.getDefaultValue();
            if (defaultValue != null) {
                Object msDefaultValue;
                if (defaultValue instanceof Map) {
                    // noinspection unchecked
                    msDefaultValue = ((Map<String, Object>) defaultValue).get("id");
                } else if (defaultValue instanceof List) {
                    List<Object> defaultList = new ArrayList<>();
                    // noinspection unchecked
                    ((List<Object>) defaultValue).forEach(i -> {
                        if (i instanceof Map) {
                            // noinspection unchecked
                            defaultList.add(((Map<String, Object>) i).get("id"));
                        } else {
                            defaultList.add(i);
                        }
                    });
                    msDefaultValue = defaultList;
                } else {
                    if (customField.getType().equals(PlatformCustomFieldType.DATE.getType())) {
                        if (defaultValue instanceof String) {
                            msDefaultValue = defaultValue;
                        } else {
                            msDefaultValue = Instant.ofEpochMilli((Long) defaultValue).atZone(ZoneId.systemDefault()).toLocalDate().toString();
                        }
                    } else if (customField.getType().equals(PlatformCustomFieldType.DATETIME.getType())) {
                        if (defaultValue instanceof String) {
                            msDefaultValue = defaultValue;
                        } else {
                            msDefaultValue = LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) defaultValue), ZoneId.systemDefault()).toString();
                        }
                    } else {
                        msDefaultValue = defaultValue;
                    }
                }
                customField.setDefaultValue(PluginUtils.toJSONString(msDefaultValue));
            }
        }
    }

    /**
     * 处理自定义关联字段
     *
     * @param item   当前字段
     * @param fields 字段集合
     * @param key    唯一KEY
     * @return 生成后的KEY
     */
    private Character handleRelateField(PlatformCustomFieldItemDTO item, List<PlatformCustomFieldItemDTO> fields, Character key, Map<String, String> optionData) {
        String id = item.getId();
        if (StringUtils.equals(id, JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID)) {
            // add original_estimate relate field
            PlatformCustomFieldItemDTO remainingEstimate = new PlatformCustomFieldItemDTO();
            BeanUtils.copyProperties(item, remainingEstimate);
            remainingEstimate.setId(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
            remainingEstimate.setCustomData(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
            remainingEstimate.setName(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_NAME);
            remainingEstimate.setKey(String.valueOf(key++));
            fields.add(remainingEstimate);
        }

        if (StringUtils.equals(id, JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
            // add issue link relate field
            PlatformCustomFieldItemDTO issueLinkField = new PlatformCustomFieldItemDTO();
            issueLinkField.setId(JiraMetadataField.ISSUE_LINK_TYPE);
            issueLinkField.setName(JiraMetadataField.ISSUE_LINK_TYPE_COLUMN_ZH);
            issueLinkField.setCustomData(JiraMetadataField.ISSUE_LINK_TYPE);
            issueLinkField.setRequired(false);
            issueLinkField.setOptions(optionData.get(JiraOptionKey.ISSUE_LINK_TYPE.name()));
            issueLinkField.setType(PlatformCustomFieldType.SELECT.getType());
            issueLinkField.setKey(String.valueOf(key++));
            fields.add(issueLinkField);
        }

        return key;
    }

    /**
     * 排序自定义字段
     *
     * @param fields 字段集合
     * @return 排序后的字段集合
     */
    private List<PlatformCustomFieldItemDTO> sortCustomField(List<PlatformCustomFieldItemDTO> fields) {
        // 按类型排序 (富文本排最后, 缺陷链接事务其次, INPUT最前面, SUMMARY其次, 其余字段按默认排序)
        fields.sort((a, b) -> {
            if (a.getType().equals(PlatformCustomFieldType.RICH_TEXT.getType())) {
                return 1;
            }
            if (b.getType().equals(PlatformCustomFieldType.RICH_TEXT.getType())) {
                return -1;
            }
            if (a.getId().equals(JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
                return 1;
            }
            if (b.getId().equals(JiraMetadataSpecialSystemField.ISSUE_LINKS)) {
                return -1;
            }
            if (a.getId().equals(JiraMetadataField.ISSUE_LINK_TYPE)) {
                return 1;
            }
            if (b.getId().equals(JiraMetadataField.ISSUE_LINK_TYPE)) {
                return -1;
            }
            if (a.getId().equals(JiraMetadataField.SUMMARY_FIELD_NAME)) {
                return -1;
            }
            if (b.getId().equals(JiraMetadataField.SUMMARY_FIELD_NAME)) {
                return 1;
            }
            if (a.getType().equals(PlatformCustomFieldType.INPUT.getType())) {
                return -1;
            }
            if (b.getType().equals(PlatformCustomFieldType.INPUT.getType())) {
                return 1;
            }
            return a.getType().compareTo(b.getType());
        });
        return fields;
    }

    /**
     * 处理Jira用户选项
     *
     * @param userList 用户列表
     * @return 用户选项集合
     */
    private List<SelectOption> handleUserOptions(List<JiraUser> userList) {
        List<SelectOption> options = new ArrayList<>();
        userList.forEach(user -> {
            SelectOption selectOption = new SelectOption();
            // set option value
            if (StringUtils.isNotBlank(user.getAccountId())) {
                selectOption.setValue(user.getAccountId());
            } else {
                selectOption.setValue(user.getName());
            }
            // set option text
            if (StringUtils.isNotBlank(user.getEmailAddress())) {
                selectOption.setText(user.getDisplayName() + " (" + user.getEmailAddress() + ")");
            } else {
                selectOption.setText(user.getDisplayName());
            }
            options.add(selectOption);
        });
        return options;
    }

    /**
     * 处理Jira字段允许的选项值
     *
     * @param allowedValues 允许的选项值
     * @return 选项集合
     */
    private List<JiraAllowedValueOption> handleAllowedValuesOptions(List<JiraCreateMetadataResponse.AllowedValues> allowedValues) {
        if (CollectionUtils.isEmpty(allowedValues)) {
            return null;
        }
        List<JiraAllowedValueOption> allowedValueOptions = new ArrayList<>();
        allowedValues.forEach(val -> {
            JiraAllowedValueOption allowedValueOption = new JiraAllowedValueOption();
            allowedValueOption.setValue(val.getId());
            if (StringUtils.isNotBlank(val.getName())) {
                allowedValueOption.setText(val.getName());
            } else {
                allowedValueOption.setText(val.getValue());
            }
            allowedValueOption.setChildren(handleAllowedValuesOptions(val.getChildren()));
            allowedValueOptions.add(allowedValueOption);
        });
        return allowedValueOptions;
    }

    private void validateConfig(String userPlatformConfig, String projectConfig) {
        setUserConfig(userPlatformConfig);
        JiraProjectConfig jiraProjectConfig = getProjectConfig(projectConfig);
        validateProjectKey(jiraProjectConfig.getJiraKey());
        validateIssueType();
    }

    private List<PlatformCustomFieldItemDTO> filterIssueLinksField(PlatformBugUpdateRequest request) {
        if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
            // remove and return issue link field
            List<PlatformCustomFieldItemDTO> issueLinkFields = request.getCustomFieldList().stream().filter(item ->
                    StringUtils.equalsAny(item.getCustomData(), JiraMetadataField.ISSUE_LINK_TYPE, JiraMetadataSpecialSystemField.ISSUE_LINKS)).toList();
            request.getCustomFieldList().removeAll(issueLinkFields);
            return issueLinkFields;
        } else {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> buildParamMap(PlatformBugUpdateRequest request, String issueTypeId, String jiraKey) {
        Map<String, Object> issuetype = new LinkedHashMap<>();
        issuetype.put("id", issueTypeId);
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("key", jiraKey);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", project);
        fields.put("issuetype", issuetype);

        Map<String, Object> param = new LinkedHashMap<>();
        param.put("fields", fields);

        parseField(getThirdPartCustomField(request.getProjectConfig()), request, fields);
        setSpecialParam(fields);
        return param;
    }

    private void parseField(List<PlatformCustomFieldItemDTO> allFields, PlatformBugUpdateRequest request, Map<String, Object> fields) {
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();

        if (!CollectionUtils.isEmpty(customFields)) {
            customFields.forEach(item -> {
                String fieldName = item.getCustomData();
                if (StringUtils.isNotBlank(fieldName)) {
                    if (ObjectUtils.isNotEmpty(item.getValue())) {
                        if (StringUtils.isNotBlank(item.getType())) {
                            if (StringUtils.equalsAny(item.getType(), "select", "radio", "member")) {
                                Map param = new LinkedHashMap<>();
                                param.put("id", item.getValue());
                                fields.put(fieldName, param);
                            } else if (StringUtils.equalsAny(item.getType(), "multipleSelect", "checkbox", "multipleMember")) {
                                List attrs = new ArrayList();
                                if (item.getValue() != null) {
                                    List values = PluginUtils.parseArray((String) item.getValue());
                                    values.forEach(v -> {
                                        Map param = new LinkedHashMap<>();
                                        param.put("id", v);
                                        attrs.add(param);
                                    });
                                }
                                fields.put(fieldName, attrs);
                            } else if (StringUtils.equalsAny(item.getType(), "cascadingSelect")) {
                                if (item.getValue() != null) {
                                    Map attr = new LinkedHashMap<>();
                                    List values = PluginUtils.parseArray((String) item.getValue());
                                    if (!CollectionUtils.isEmpty(values)) {
                                        if (values.size() > 0) {
                                            attr.put("id", values.get(0));
                                        }
                                        if (values.size() > 1) {
                                            Map param = new LinkedHashMap<>();
                                            param.put("id", values.get(1));
                                            attr.put("child", param);
                                        }
                                    } else {
                                        attr.put("id", item.getValue());
                                    }
                                    fields.put(fieldName, attr);
                                }
                            } else if (StringUtils.equalsAny(item.getType(), "richText")) {
                                // fields.put(fieldName, parseRichTextImageUrlToJira(item.getValue().toString()));
                                if (fieldName.equals(JiraMetadataField.DESCRIPTION_FIELD_NAME)) {
                                    request.setDescription(item.getValue().toString());
                                }
                            } else if (StringUtils.equals(item.getType(), "datetime")) {
                                if (item.getValue() != null && item.getValue() instanceof String) {
                                    // 2023-07-12 11:12:46 -> 2021-12-10T11:12:46+08:00
                                    fields.put(fieldName, ((String) item.getValue()).trim().replace(" ", "T") + "+08:00");
                                }
                            } else {
                                fields.put(fieldName, item.getValue());
                            }
                        }

                    }
                }
            });
        }
    }

    /**
     * 参数比较特殊，需要特别处理
     *
     * @param fields
     */
    private void setSpecialParam(Map fields) {

        try {
            Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClient.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraBugTypeId());

            for (String key : createMetadata.keySet()) {
                JiraCreateMetadataResponse.Field item = createMetadata.get(key);
                JiraCreateMetadataResponse.Schema schema = item.getSchema();

                if (StringUtils.equals(key, JiraMetadataSpecialSystemField.TIME_TRACKING)) {
                    Map newField = new LinkedHashMap<>();
                    // originalEstimate -> 2d 转成 timetracking : { originalEstimate: 2d}
                    newField.put(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID, fields.get(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID));
                    newField.put(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID, fields.get(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID));
                    fields.put(key, newField);
                    fields.remove(JiraMetadataField.ORIGINAL_ESTIMATE_TRACKING_FIELD_ID);
                    fields.remove(JiraMetadataField.REMAINING_ESTIMATE_TRACKING_FIELD_ID);
                }

                if (schema == null || fields.get(key) == null) {
                    continue;
                }

                if (schema.getCustom() != null) {
                    if (schema.getCustom().endsWith(JiraMetadataSpecialCustomField.SPRINT_FIELD_NAME)) {
                        Map field = (Map) fields.get(key);
                        Object id = field.get("id");
                        if (id != null) {
                            // sprint 传参数比较特殊，需要要传数值
                            fields.put(key, Integer.parseInt(id.toString()));
                        }
                    } else if (schema.getCustom().endsWith("pic-link")) {
                        Map field = (Map) fields.get(key);
                        fields.put(key, field.get("id"));
                    } else if (schema.getCustom().endsWith("multiuserpicker")) { // 多选用户列表
                        List<Map> userItems = (List) fields.get(key);
                        userItems.forEach(i -> {
                            i.put("name", i.get("id"));
                            i.put("id", i.get("id"));
                        });
                    }
                }

                if (schema.getType() != null) {
                    if (schema.getType().endsWith("user")) {
                        Map field = (Map) fields.get(key);
                        // 如果不是用户ID，则是用户的key，参数调整为key
                        Map newField = new LinkedHashMap<>();
                        // name 是私有化部署使用
                        newField.put("name", field.get("id").toString());
                        // id 是SaaS使用
                        newField.put("id", field.get("id").toString());
                        fields.put(key, newField);
                    }
                }
            }
        } catch (Exception e) {
            PluginLogUtils.error(e);
        }
    }

    private static Map<String, String> getFieldNameMap(PlatformBugUpdateRequest request) {
        Map<String, String> filedNameMap = null;
        if (!CollectionUtils.isEmpty(request.getCustomFieldList())) {
            filedNameMap = request.getCustomFieldList().stream()
                    .collect(Collectors.toMap(PlatformCustomFieldItemDTO::getId, PlatformCustomFieldItemDTO::getName));
        }
        return filedNameMap;
    }

    /**
     * 根据字段类型, 匹配具体的字段集合
     *
     * @param specialFields
     * @param type
     * @return true: 匹配到特殊字段, false: 未匹配到特殊字段
     */
    private static boolean mappingSpecialField(Set<String> specialFields, String type) {
        // 匹配特殊字段, 包含即可
        Optional<String> findField = specialFields.stream().filter(field -> StringUtils.contains(type, field)).findAny();
        return findField.isPresent();
    }

    // private List<File> getImageFiles(PlatformBugUpdateRequest request) {
    //     List<File> files = new ArrayList<>();
    //     List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
    //     if (!CollectionUtils.isEmpty(customFields)) {
    //         customFields.forEach(item -> {
    //             String fieldName = item.getCustomData();
    //             if (StringUtils.isNotBlank(fieldName)) {
    //                 if (item.getValue() != null) {
    //                     if (StringUtils.isNotBlank(item.getType())) {
    //                         if (StringUtils.equalsAny(item.getType(), "richText")) {
    //                             files.addAll(getImageFiles(item.getValue().toString()));
    //                         }
    //                     }
    //                 }
    //             }
    //         });
    //     }
    //
    //     return files;
    // }

    private void linkIssue(List<PlatformCustomFieldItemDTO> issueLinkFields, String issueKey, List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes) {
        // 暂时只支持关联一组事务, 前台Form表单对多组事务关联关系的支持麻烦
        PlatformCustomFieldItemDTO issueLinkType = issueLinkFields.get(0);
        PlatformCustomFieldItemDTO issueLink = issueLinkFields.get(1);
        if (issueLinkType.getValue() != null && issueLink.getValue() != null && StringUtils.isNotEmpty(issueLinkType.getValue().toString())) {
            String type = issueLinkType.getValue().toString();
            JiraIssueLinkTypeResponse.IssueLinkType attachType = issueLinkTypes.stream().filter(item -> StringUtils.equalsAny(type, item.getInward(), item.getOutward())).findFirst().get();
            List<String> linkKeys = PluginUtils.parseArray(issueLink.getValue().toString(), String.class);
            if (CollectionUtils.isEmpty(linkKeys)) {
                return;
            }
            linkKeys.forEach(linkKey -> {
                JiraIssueLinkRequest issueLinkRequest = new JiraIssueLinkRequest();
                issueLinkRequest.setType(JiraIssueLinkRequest.JiraIssueLinkType.builder().id(attachType.getId()).build());
                if (StringUtils.equals(type, attachType.getInward())) {
                    issueLinkRequest.setInwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(linkKey).build());
                    issueLinkRequest.setOutwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(issueKey).build());
                } else {
                    issueLinkRequest.setOutwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(linkKey).build());
                    issueLinkRequest.setInwardIssue(JiraIssueLinkRequest.JiraIssueLinkKey.builder().key(issueKey).build());
                }
                jiraClient.linkIssue(issueLinkRequest);
            });
        }
    }

    /**
     * 准备特定的选项数据, 防止多次网络请求
     *
     * @return
     */
    private Map<String, String> prepareOptionData() {
        Map<String, String> optionData = new HashMap<>();
        // Jira用户下拉选项
        optionData.put(JiraOptionKey.USER.name(), PluginUtils.toJSONString(getUserSearchOptions(StringUtils.EMPTY)));
        // Jira自定义Sprint选项
        List<JiraSprint> sprints = jiraClient.getSprint(null);
        List<SelectOption> sprintOptions = new ArrayList<>();
        sprints.forEach(sprint -> sprintOptions.add(new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString())));
        optionData.put(JiraOptionKey.SPRINT.name(), PluginUtils.toJSONString(sprintOptions));
        // Jira自定义Epic选项
        List<JiraEpic> epics = jiraClient.getEpics(projectConfig.getJiraKey());
        List<SelectOption> epicOptions = new ArrayList<>();
        epics.forEach(epic -> epicOptions.add(new SelectOption(epic.getName(), epic.getKey())));
        optionData.put(JiraOptionKey.EPIC.name(), PluginUtils.toJSONString(epicOptions));
        // 获取Jira指派人选项
        optionData.put(JiraOptionKey.ASSIGN.name(), PluginUtils.toJSONString(getAssignableOptions(projectConfig.getJiraKey(), null)));
        // 获取Jira关联事务选项
        optionData.put(JiraOptionKey.ISSUE_LINK.name(), PluginUtils.toJSONString(getIssueLinkOptions(null, null)));
        optionData.put(JiraOptionKey.ISSUE_LINK_TYPE.name(), PluginUtils.toJSONString(getIssueLinkTypeOptions()));
        return optionData;
    }
}
