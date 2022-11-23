package io.metersphere.platform.impl;


import im.metersphere.plugin.exception.MSPluginException;
import im.metersphere.plugin.utils.JSON;
import im.metersphere.plugin.utils.LogUtil;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.api.AbstractPlatform;
import io.metersphere.platform.constants.AttachmentSyncType;
import io.metersphere.platform.constants.CustomFieldType;
import io.metersphere.platform.domain.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JiraPlatform extends AbstractPlatform {

    protected JiraClientV2 jiraClientV2;

    protected SimpleDateFormat sdfWithZone = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    protected Boolean isSass = false;
    protected JiraProjectConfig projectConfig;

    public JiraPlatform(PlatformRequest request) {
        super.key = JiraPlatformMetaInfo.KEY;
        super.request = request;
        jiraClientV2 = new JiraClientV2();
        setConfig();
    }

    public JiraConfig setConfig() {
        JiraConfig config = getIntegrationConfig();
        validateConfig(config);
        if (config.getUrl().contains(".atlassian.net")) {
            this.isSass = true;
        }
        jiraClientV2.setConfig(config);
        return config;
    }

    private void validateConfig(JiraConfig config) {
        jiraClientV2.setConfig(config);
        if (config == null) {
            MSPluginException.throwException("jira config is null");
        }
    }

    public JiraConfig getIntegrationConfig() {
        return getIntegrationConfig(JiraConfig.class);
    }

    @Override
    public List<SelectOption> getProjectOptions(GetOptionRequest request) {
        String method = request.getOptionMethod();
        try {
            // 这里反射调用 getIssueTypes 获取下拉框选项
            return (List<SelectOption>) this.getClass().getMethod(method, request.getClass()).invoke(this, request);
        } catch (InvocationTargetException e) {
            LogUtil.error(e);
            MSPluginException.throwException(e.getTargetException());
        }  catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
        }
        return null;
    }

    public JiraConfig setUserConfig(String userPlatformInfo) {
        JiraConfig config = getIntegrationConfig();
        JiraUserPlatformInfo userInfo = StringUtils.isBlank(userPlatformInfo) ? new JiraUserPlatformInfo()
                :  JSON.parseObject(userPlatformInfo, JiraUserPlatformInfo.class);
        if (StringUtils.isNotBlank(userInfo.getJiraAccount())
                && StringUtils.isNotBlank(userInfo.getJiraPassword())) {
            config.setAccount(userInfo.getJiraAccount());
            config.setPassword(userInfo.getJiraPassword());
        }
        validateConfig(config);
        jiraClientV2.setConfig(config);
        return config;
    }

    public PlatformIssuesDTO getUpdateIssue(PlatformIssuesDTO issue, JiraIssue jiraIssue) {
        try {
            if (issue == null) {
                issue = new PlatformIssuesDTO();
                issue.setCustomFields(defaultCustomFields);
            } else {
                mergeCustomField(issue, defaultCustomFields);
            }

            Map fields = jiraIssue.getFields();
            String status = getStatus(fields);

            Map<String, String> fileContentMap = getContextMap((List) fields.get("attachment"));

            // 先转换下desc的图片
            String description = dealWithDescription(Optional.ofNullable(fields.get("description")).orElse("").toString(), fileContentMap);
            fields.put("description", description);
            List<PlatformCustomFieldItemDTO> customFieldItems = syncIssueCustomFieldList(issue.getCustomFields(), jiraIssue.getFields());

            // 其他自定义里有富文本框的也转换下图片
            for (PlatformCustomFieldItemDTO item : customFieldItems) {
                if (!StringUtils.equals("description", item.getId())) {
                    // desc转过了，跳过
                    if (StringUtils.equals(CustomFieldType.RICH_TEXT.getValue(), item.getType())) {
                        item.setValue(dealWithDescription((String) item.getValue(), fileContentMap));
                    }
                }
            }

            Map assignee = (Map) fields.get("assignee");
            issue.setTitle(fields.get("summary").toString());
            issue.setLastmodify(assignee == null ? "" : assignee.get("displayName").toString());
            issue.setDescription(description);
            issue.setPlatformStatus(status);
            issue.setPlatform(key);
            issue.setCustomFields(JSON.toJSONString(customFieldItems));
            try {
                issue.setCreateTime(sdfWithZone.parse((String) fields.get("created")).getTime());
                issue.setUpdateTime(sdfWithZone.parse((String) fields.get("updated")).getTime());
            } catch (Exception e) {
                LogUtil.error(e);
            }
            return issue;
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
            return null;
        }
    }

    private String dealWithDescription(String description, Map<String, String> fileContentMap) {
        if (StringUtils.isBlank(description)) {
            return description;
        }

        description = description.replaceAll("!image", "\n!image");
        String[] splitStrs = description.split("\\n");
        for (int j = 0; j < splitStrs.length; j++) {
            String splitStr = splitStrs[j];
            if (StringUtils.isNotEmpty(splitStr)) {
                List<String> keys = fileContentMap.keySet().stream().filter(key -> splitStr.contains(key)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(keys)) {
                    description = description.replace(splitStr, fileContentMap.get(keys.get(0)));
                    fileContentMap.remove(keys.get(0));
                } else {
                    if (splitStr.contains("MS附件：")) {
                        // 解析标签内容
                        String name = getHyperLinkPathForImg("\\!\\[(.*?)\\]", StringEscapeUtils.unescapeJava(splitStr));
                        String path = getHyperLinkPathForImg("\\|(.*?)\\)", splitStr);
                        path = "/resource/md/get/url?platform=Jira&url=" + URLEncoder.encode(path, StandardCharsets.UTF_8);

                        // 解析标签内容为图片超链接格式，进行替换
                        description = description.replace(splitStr, "\n\n![" + name + "](" + path + ")");
                    }
                    description = description.replace(splitStr, StringEscapeUtils.unescapeJava(splitStr.replace("MS附件：", "")));
                }
            }
        }
        return description;
    }

    private Map<String, String> getContextMap(List attachments) {
        // 附件处理
        Map<String, String> fileContentMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(attachments)) {
            for (int i = 0; i < attachments.size(); i++) {
                Map attachment = (Map) attachments.get(i);
                String filename = attachment.get("filename").toString();
                String content = attachment.get("content").toString();
                content = "/resource/md/get/url?platform=Jira&url=" + URLEncoder.encode(content, StandardCharsets.UTF_8);

                if (StringUtils.contains(attachment.get("mimeType").toString(), "image")) {
                    String contentUrl = "![" + filename + "](" + content + ")";
                    fileContentMap.put(filename, contentUrl);
                } else {
                    String contentUrl = "附件[" + filename + "]下载地址:" + content;
                    fileContentMap.put(filename, contentUrl);
                }
            }
        }
        return fileContentMap;
    }

    private String getStatus(Map fields) {
        Map statusObj = (Map) fields.get("status");
        if (statusObj != null) {
            Map statusCategory = (Map) statusObj.get("statusCategory");
            return statusObj.get("name").toString() == null ? statusCategory.get("name").toString() : statusObj.get("name").toString();
        }
        return "";
    }

    @Override
    public List<DemandDTO> getDemands(String projectConfigStr) {
        List<DemandDTO> list = new ArrayList<>();
        projectConfig = getProjectConfig(projectConfigStr);
        validateStoryType();

        int maxResults = 50, startAt = 0;
        List demands;
        do {
            demands = jiraClientV2.getDemands(projectConfig.getJiraKey(), projectConfig.getJiraStoryTypeId(), startAt, maxResults);
            for (int i = 0; i < demands.size(); i++) {
                Map o = (Map) demands.get(i);
                String issueKey = o.get("key").toString();
                Map fields = (Map) o.get("fields");
                String summary = fields.get("summary").toString();
                DemandDTO demandDTO = new DemandDTO();
                demandDTO.setName(summary);
                demandDTO.setId(issueKey);
                demandDTO.setPlatform(key);
                list.add(demandDTO);
            }
            startAt += maxResults;
        } while (demands.size() >= maxResults);
        return list;
    }


    public JiraProjectConfig getProjectConfig(String configStr) {
        if (StringUtils.isBlank(configStr)) {
            MSPluginException.throwException("请在项目中添加项目配置！");
        }
        JiraProjectConfig projectConfig = JSON.parseObject(configStr, JiraProjectConfig.class);
        return projectConfig;
    }

    public void validateStoryType() {
        if (StringUtils.isBlank(projectConfig.getJiraStoryTypeId())) {
            MSPluginException.throwException("请在项目中配置 Jira 需求类型！");
        }
    }

    public void validateIssueType() {
        if (StringUtils.isBlank(projectConfig.getJiraIssueTypeId())) {
            MSPluginException.throwException("请在项目中配置 Jira 缺陷类型！");
        }
    }

    /**
     * 由 getProjectOptions 反射调用
     * @return
     */
    public List<SelectOption> getIssueTypes(GetOptionRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return jiraClientV2.getIssueType(projectConfig.getJiraKey())
                .stream()
                .map(item -> new SelectOption(item.getName(), item.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());
        projectConfig = getProjectConfig(request.getProjectConfig());
        this.isThirdPartTemplate = projectConfig.isThirdPartTemplate();
        validateProjectKey(projectConfig.getJiraKey());
        validateIssueType();

        Map addJiraIssueParam = buildUpdateParam(request, projectConfig.getJiraIssueTypeId(), projectConfig.getJiraKey());
        JiraAddIssueResponse result = jiraClientV2.addIssue(JSON.toJSONString(addJiraIssueParam));
        JiraIssue jiraIssue = jiraClientV2.getIssues(result.getId());

        // 上传富文本中的图片作为附件
        List<File> imageFiles = getImageFiles(request);
        imageFiles.forEach(img -> jiraClientV2.uploadAttachment(result.getKey(), img));

        String status = getStatus(jiraIssue.getFields());
        request.setPlatformStatus(status);
        request.setPlatformId(result.getKey());
        request.setId(UUID.randomUUID().toString());

        return request;
    }

    private List<File> getImageFiles(PlatformIssuesUpdateRequest request) {
        List<File> files = new ArrayList<>();
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            if (StringUtils.isNotBlank(fieldName)) {
                if (item.getValue() != null) {
                    if (StringUtils.isNotBlank(item.getType())) {
                        if (StringUtils.equalsAny(item.getType(),  "richText")) {
                            files.addAll(getImageFiles(item.getValue().toString()));
                        }
                    }
                }
            }
        });
        return files;
    }

    /**
     * 参数比较特殊，需要特别处理
     * @param fields
     */
    private void setSpecialParam(Map fields) {

        try {
            Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClientV2.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraIssueTypeId());
            List<JiraUser> userOptions = jiraClientV2.getAssignableUser(projectConfig.getJiraKey());

            Boolean isUserKey = false;
            if (CollectionUtils.isNotEmpty(userOptions) && StringUtils.isBlank(userOptions.get(0).getAccountId())) {
                isUserKey = true;
            }

            for (String key : createMetadata.keySet()) {
                JiraCreateMetadataResponse.Field item = createMetadata.get(key);
                JiraCreateMetadataResponse.Schema schema = item.getSchema();
                if (schema == null) {
                    continue;
                }
                if (schema.getCustom() != null && schema.getCustom().endsWith("sprint")) {
                    try {
                        Map field = (Map) fields.get(key);
                        // sprint 传参数比较特殊，需要要传数值
                        fields.put(key, (Integer) field.get("id"));
                    } catch (Exception e) {}
                }
                if (isUserKey) {
                    if (schema.getType() != null && schema.getType().endsWith("user")) {
                        Map field = (Map) fields.get(key);
                        // 如果不是用户ID，则是用户的key，参数调整为key
                        Map newField = new LinkedHashMap<>();
                        newField.put("name", field.get("id").toString());
                        fields.put(key, newField);
                    }
                    if (schema.getCustom() != null && schema.getCustom().endsWith("multiuserpicker")) { // 多选用户列表
                        try {
                            List<Map> userItems = (List) fields.get(key);
                            userItems.forEach(i -> i.put("name", i.get("id")));
                        } catch (Exception e) {LogUtil.error(e);}
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }

    private Map<String, Object> buildUpdateParam(PlatformIssuesUpdateRequest request, String issueTypeId, String jiraKey) {
        request.setPlatform(key);
        Map fields = new LinkedHashMap<>();
        Map project = new LinkedHashMap<>();

        String desc = "";
        // 附件描述信息处理
        if (StringUtils.isNotBlank(request.getDescription())) {
            desc = dealWithImage(request.getDescription());
        }

        fields.put("project", project);
        project.put("key", jiraKey);

        Map issuetype = new LinkedHashMap<>();
        issuetype.put("id", issueTypeId);
        fields.put("issuetype", issuetype);

        Map addJiraIssueParam = new LinkedHashMap();
        addJiraIssueParam.put("fields", fields);

        if (isThirdPartTemplate) {
            parseCustomFiled(request, fields);
            request.setTitle(fields.get("summary").toString());
        } else {
            fields.put("summary", request.getTitle());
            fields.put("description", desc);
            // 添加后，解析图片会用到
            request.getCustomFieldList().add(getRichTextCustomField("description", desc));
            parseCustomFiled(request, fields);
        }
        setSpecialParam(fields);

        return addJiraIssueParam;
    }

    private PlatformCustomFieldItemDTO getRichTextCustomField(String name, String value) {
        PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
        customField.setId(name);
        customField.setType(CustomFieldType.RICH_TEXT.getValue());
        customField.setCustomData(name);
        customField.setValue(value);
        return customField;
    }

    private String dealWithImage(String description) {
        String regex = "(\\!\\[.*?\\]\\((.*?)\\))";
        Matcher matcher = Pattern.compile(regex).matcher(description);

        try {
            while (matcher.find()) {
                if (StringUtils.isNotEmpty(matcher.group())) {
                    // img标签内容
                    String imgPath = matcher.group();
                    // 解析标签内容为图片超链接格式，进行替换
                    description = description.replace(imgPath, "\nMS附件：" + imgPath);
                }
            }
        } catch (Exception exception) {
        }

        return description;
    }

    private String getHyperLinkPathForImg(String regex, String targetStr) {
        String result = "";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(targetStr);

        try {
            while (matcher.find()) {
                String url = matcher.group(1);
                result = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
            }
        } catch (Exception exception) {
            return targetStr;
        }

        return result;
    }

    private void parseCustomFiled(PlatformIssuesUpdateRequest request, Map fields) {
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();

        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            String name = item.getName();
            if (StringUtils.isNotBlank(fieldName)) {
                if (ObjectUtils.isNotEmpty(item.getValue())) {
                    if (StringUtils.isNotBlank(item.getType())) {
                        if (StringUtils.equalsAny(item.getType(), "select", "radio", "member")) {
                            Map param = new LinkedHashMap<>();
                            if (fieldName.equals("assignee") || fieldName.equals("reporter")) {
                                if (isThirdPartTemplate || isSass) {
                                    param.put("id", item.getValue());
                                } else {
                                    param.put("accountId", item.getValue());
                                }
                            } else {
                                param.put("id", item.getValue());
                            }
                            fields.put(fieldName, param);
                        } else if (StringUtils.equalsAny(item.getType(),  "multipleSelect", "checkbox", "multipleMember")) {
                            List attrs = new ArrayList();
                            if (item.getValue() != null) {
                                List values = JSON.parseArray((String) item.getValue());
                                values.forEach(v -> {
                                    Map param = new LinkedHashMap<>();
                                    param.put("id", v);
                                    attrs.add(param);
                                });
                            }
                            fields.put(fieldName, attrs);
                        } else if (StringUtils.equalsAny(item.getType(),  "cascadingSelect")) {
                            if (item.getValue() != null) {
                                Map attr = new LinkedHashMap<>();
                                List values = JSON.parseArray((String) item.getValue());
                                if (CollectionUtils.isNotEmpty(values)) {
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
                            fields.put(fieldName, parseRichTextImageUrlToJira(item.getValue().toString()));
                            if (fieldName.equals("description")) {
                                request.setDescription(item.getValue().toString());
                            }
                        } else {
                            fields.put(fieldName, item.getValue());
                        }
                    }

                }
            }
        });
    }

    @Override
    public IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());

        projectConfig = getProjectConfig(request.getProjectConfig());
        validateProjectKey(projectConfig.getJiraKey());
        validateIssueType();

        Map param = buildUpdateParam(request, projectConfig.getJiraIssueTypeId(), projectConfig.getJiraKey());
        jiraClientV2.updateIssue(request.getPlatformId(), JSON.toJSONString(param));

        // 同步Jira富文本有关的附件
        syncJiraRichTextAttachment(request);

        if (request.getTransitions() != null) {
            try {
                List<JiraTransitionsResponse.Transitions> transitions = jiraClientV2.getTransitions(request.getPlatformId());
                transitions.forEach(transition -> {
                    if (Objects.equals(request.getPlatformStatus(), transition.getTo().getName())) {
                        jiraClientV2.setTransitions(request.getPlatformId(), transition);
                    }
                });
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
        return request;
    }

    @Override
    public void deleteIssue(String platformId) {
        jiraClientV2.deleteIssue(platformId);
    }

    @Override
    public void validateIntegrationConfig() {
        jiraClientV2.auth();
    }

    @Override
    public void validateProjectConfig(String projectConfigStr) {
        try {
            JiraProjectConfig projectConfig = getProjectConfig(projectConfigStr);
            JiraIssueProject project = jiraClientV2.getProject(projectConfig.getJiraKey());
            if (project != null && StringUtils.isBlank(project.getId())) {
                MSPluginException.throwException("项目不存在");
            }
        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e.getMessage());
        }
    }

    @Override
    public void validateUserConfig(String userConfig) {
        setUserConfig(userConfig);
        jiraClientV2.auth();
    }

    @Override
    public boolean isAttachmentUploadSupport() {
        return true;
    }

    @Override
    public List<PlatformUser> getPlatformUser() {
        return null;
    }

    @Override
    public SyncIssuesResult syncIssues(SyncIssuesRequest request) {
        projectConfig = getProjectConfig(request.getProjectConfig());
        super.isThirdPartTemplate = projectConfig.isThirdPartTemplate();

        if (projectConfig.isThirdPartTemplate()) {
            super.defaultCustomFields = getCustomFieldsValuesString(getThirdPartCustomField(request.getProjectConfig()));
        } else {
            super.defaultCustomFields = request.getDefaultCustomFields();
        }

        List<PlatformIssuesDTO> issues = request.getIssues();

        SyncIssuesResult syncIssuesResult = new SyncIssuesResult();

        issues.forEach(item -> {
            try {
                JiraIssue jiraIssue = jiraClientV2.getIssues(item.getPlatformId());
                item = getUpdateIssue(item, jiraIssue);
                syncIssuesResult.getUpdateIssues().add(item);
                syncIssuesResult.getCustomFieldMap().put(item.getId(), item.getCustomFields());
                // 同步第三方平台附件
                syncJiraIssueAttachments(syncIssuesResult, item, jiraIssue);
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 404) {
                    syncIssuesResult.getDeleteIssuesIds().add(item.getId());
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        });
        return syncIssuesResult;
    }

    @Override
    public List<PlatformStatusDTO> getStatusList(String issueKey) {
        List<PlatformStatusDTO> platformStatusDTOS = new ArrayList<>();
        List<JiraTransitionsResponse.Transitions> transitions = jiraClientV2.getTransitions(issueKey);
        if (CollectionUtils.isNotEmpty(transitions)) {
            transitions.forEach(item -> {
                PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
                platformStatusDTO.setLabel(item.getTo().getName());
                platformStatusDTO.setValue(item.getTo().getName());
                platformStatusDTOS.add(platformStatusDTO);
            });
        }
        return platformStatusDTOS;
    }

    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfigStr) {
        Set<String> ignoreSet = new HashSet() {{
            add("timetracking");
            add("attachment");
        }};
        projectConfig = getProjectConfig(projectConfigStr);

        Map<String, JiraCreateMetadataResponse.Field> createMetadata =
                jiraClientV2.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraIssueTypeId());

        String userOptions = getUserOptions(projectConfig.getJiraKey());
        List<PlatformCustomFieldItemDTO> fields = new ArrayList<>();
        char filedKey = 'A';
        for (String name : createMetadata.keySet()) {
            JiraCreateMetadataResponse.Field item = createMetadata.get(name);
            if (ignoreSet.contains(name)) {
                continue;  // timetracking, attachment todo
            }
            JiraCreateMetadataResponse.Schema schema = item.getSchema();
            PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
            customField.setKey(String.valueOf(filedKey++));
            customField.setId(name);
            customField.setCustomData(name);
            customField.setName(item.getName());
            customField.setRequired(item.isRequired());
            setCustomFiledType(schema, customField, userOptions);
            setCustomFiledDefaultValue(customField, item);
            List options = getAllowedValuesOptions(item.getAllowedValues());
            if (options != null)
                customField.setOptions(JSON.toJSONString(options));
            fields.add(customField);
        }

        fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType()))
                .collect(Collectors.toList());

        // 按类型排序，富文本排最后，input 排最前面，summary 排第一个
        fields.sort((a, b) -> {
            if (a.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return 1;
            if (b.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return -1;
            if (a.getId().equals("summary")) return -1;
            if (b.getId().equals("summary")) return 1;
            if (a.getType().equals(CustomFieldType.INPUT.getValue())) return -1;
            if (b.getType().equals(CustomFieldType.INPUT.getValue())) return 1;
            return a.getType().compareTo(b.getType());
        });
        return fields;
    }


    private void setCustomFiledType(JiraCreateMetadataResponse.Schema schema, PlatformCustomFieldItemDTO customField, String userOptions) {
        Map<String, String> fieldTypeMap = new HashMap() {{
            put("summary", CustomFieldType.INPUT.getValue());
            put("description", CustomFieldType.RICH_TEXT.getValue());
            put("components", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("fixVersions", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("versions", CustomFieldType.MULTIPLE_SELECT.getValue());
            put("priority", CustomFieldType.SELECT.getValue());
            put("environment", CustomFieldType.RICH_TEXT.getValue());
            put("labels", CustomFieldType.MULTIPLE_INPUT.getValue());
        }};
        String customType = schema.getCustom();
        String value = null;
        if (StringUtils.isNotBlank(customType)) {
            // 自定义字段
            if (customType.contains("multiselect")) {
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
            } else if (customType.contains("cascadingselect")) {
                value = "cascadingSelect";
            } else if (customType.contains("multiuserpicker")) {
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
                customField.setOptions(userOptions);
            } else if (customType.contains("userpicker")) {
                value = CustomFieldType.SELECT.getValue();
                customField.setOptions(userOptions);
            } else if (customType.contains("people")) {
                if (StringUtils.isNotBlank(schema.getType()) && StringUtils.equals(schema.getType(), "array")) {
                    value = CustomFieldType.MULTIPLE_SELECT.getValue();
                } else {
                    value = CustomFieldType.SELECT.getValue();
                }
                customField.setOptions(userOptions);
            } else if (customType.contains("multicheckboxes")) {
                value = CustomFieldType.CHECKBOX.getValue();
                customField.setDefaultValue(JSON.toJSONString(new ArrayList()));
            } else if (customType.contains("radiobuttons")) {
                value = CustomFieldType.RADIO.getValue();
            } else if (customType.contains("textfield")) {
                value = CustomFieldType.INPUT.getValue();
            } else if (customType.contains("datetime")) {
                value = CustomFieldType.DATETIME.getValue();
            } else if (customType.contains("datepicker")) {
                value = CustomFieldType.DATE.getValue();
            } else if (customType.contains("float")) {
                value = CustomFieldType.FLOAT.getValue();
            } else if (customType.contains("select")) {
                value = CustomFieldType.SELECT.getValue();
            } else if (customType.contains("url")) {
                value = CustomFieldType.INPUT.getValue();
            } else if (customType.contains("textarea")) {
                value = CustomFieldType.TEXTAREA.getValue();
            } else if (customType.contains("labels")) {
                value = CustomFieldType.MULTIPLE_INPUT.getValue();
            } else if (customType.contains("multiversion")) {
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
            } else if (customType.contains("version")) {
                value = CustomFieldType.SELECT.getValue();
            } else if (customType.contains("customfieldtypes") && StringUtils.equals(schema.getType(), "project")) {
                value = CustomFieldType.SELECT.getValue();
            }
        } else {
            // 系统字段
            value = fieldTypeMap.get(customField.getId());
            String type = schema.getType();
            if ("user".equals(type)) {
                value = CustomFieldType.SELECT.getValue();
                customField.setOptions(userOptions);
            } else if ("date".equals(type)) {
                value = CustomFieldType.DATE.getValue();
            } else if ("datetime".equals(type)) {
                value = CustomFieldType.DATETIME.getValue();
            }
        }
        customField.setType(value);
    }

    private void setCustomFiledDefaultValue(PlatformCustomFieldItemDTO customField, JiraCreateMetadataResponse.Field item) {
        if (item.isHasDefaultValue()) {
            Object defaultValue = item.getDefaultValue();
            if (defaultValue != null) {
                Object msDefaultValue;
                if (defaultValue instanceof Map) {
                    msDefaultValue = ((Map) defaultValue).get("id");
                } else if (defaultValue instanceof List) {
                    List defaultList = new ArrayList();
                    ((List) defaultValue).forEach(i -> {
                        if (i instanceof Map) {
                            Map obj = (Map) i;
                            defaultList.add(obj.get("id"));
                        } else {
                            defaultList.add(i);
                        }
                    });

                    msDefaultValue = defaultList;
                } else {
                    if (customField.getType().equals(CustomFieldType.DATE.getValue())) {
                        if (defaultValue instanceof String) {
                            msDefaultValue = defaultValue;
                        } else {
                            msDefaultValue = Instant.ofEpochMilli((Long) defaultValue).atZone(ZoneId.systemDefault()).toLocalDate().toString();
                        }
                    } else if (customField.getType().equals(CustomFieldType.DATETIME.getValue())) {
                        if (defaultValue instanceof String) {
                            msDefaultValue = defaultValue;
                        } else {
                            msDefaultValue = LocalDateTime.ofInstant(Instant.ofEpochMilli((Long) defaultValue), ZoneId.systemDefault()).toString();
                        }
                    } else {
                        msDefaultValue = defaultValue;
                    }
                }
                customField.setDefaultValue(JSON.toJSONString(msDefaultValue));
            }
        }
    }

    private List getAllowedValuesOptions(List<JiraCreateMetadataResponse.AllowedValues> allowedValues) {
        if (allowedValues != null) {
            List options = new ArrayList<>();
            allowedValues.forEach(val -> {
                Map jsonObject = new LinkedHashMap();
                jsonObject.put("value", val.getId());
                if (StringUtils.isNotBlank(val.getName())) {
                    jsonObject.put("text", val.getName());
                } else {
                    jsonObject.put("text", val.getValue());
                }
                List children = getAllowedValuesOptions(val.getChildren());
                if (children != null) {
                    jsonObject.put("children", children);
                }
                options.add(jsonObject);
            });
            return options;
        }
        return null;
    }

    private String getUserOptions(String projectKey) {
        List<JiraUser> userOptions = jiraClientV2.getAssignableUser(projectKey);
        List options = new ArrayList();
        userOptions.forEach(val -> {
            Map jsonObject = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(val.getAccountId())) {
                jsonObject.put("value", val.getAccountId());
            } else {
                jsonObject.put("value", val.getName());
            }
            jsonObject.put("text", val.getDisplayName());
            options.add(jsonObject);
        });
        return JSON.toJSONString(options);
    }

    @Override
    public ResponseEntity proxyForGet(String url, Class responseEntityClazz) {
        return jiraClientV2.proxyForGet(url, responseEntityClazz);
    }

    @Override
    public void syncIssuesAttachment(SyncIssuesAttachmentRequest request) {
        String syncType = request.getSyncType();
        File file = request.getFile();
        // 同步缺陷MS附件到Jira
        if (StringUtils.equals(AttachmentSyncType.UPLOAD.syncOperateType(), syncType)) {
            // 上传附件
            jiraClientV2.uploadAttachment(request.getPlatformId(), file);
        } else if (StringUtils.equals(AttachmentSyncType.DELETE.syncOperateType(), syncType)) {
            // 删除附件
            JiraIssue jiraIssue = jiraClientV2.getIssues(request.getPlatformId());
            Map fields = jiraIssue.getFields();
            List attachments = (List) fields.get("attachment");
            if (!attachments.isEmpty() && attachments.size() > 0) {
                for (int i = 0; i < attachments.size(); i++) {
                    Map attachment = (Map) attachments.get(i);
                    String filename = attachment.get("filename").toString();
                    if (filename.equals(file.getName())) {
                        String fileId = attachment.get("id").toString();
                        jiraClientV2.deleteAttachment(fileId);
                    }
                }
            }
        }
    }

    public void syncJiraIssueAttachments(SyncIssuesResult syncIssuesResult, PlatformIssuesDTO issue, JiraIssue jiraIssue) {
        try {

            List attachments = (List) jiraIssue.getFields().get("attachment");
            // 同步Jira中新的附件
            if (CollectionUtils.isNotEmpty(attachments)) {
                Map<String, List<PlatformAttachment>> attachmentMap = syncIssuesResult.getAttachmentMap();
                attachmentMap.put(issue.getId(), new ArrayList<>());
                for (int i = 0; i < attachments.size(); i++) {
                    Map attachment = (Map) attachments.get(i);
                    String filename = attachment.get("filename").toString();
                    if ((issue.getDescription() == null || !issue.getDescription().contains(filename))
                            && (issue.getCustomFields() == null || !issue.getCustomFields().contains(filename))
                    ) {
                        PlatformAttachment syncAttachment = new PlatformAttachment();
                        // name 用于查重
                        syncAttachment.setFileName(filename);
                        // key 用于获取附件内容
                        syncAttachment.setFileKey(attachment.get("content").toString());
                        attachmentMap.get(issue.getId()).add(syncAttachment);
                    }
                }
            }

        } catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
        }
    }

    @Override
    public byte[] getAttachmentContent(String fileKey) {
        return jiraClientV2.getAttachmentContent(fileKey);
    }

    public void syncJiraRichTextAttachment(PlatformIssuesUpdateRequest request) {
        List<String> jiraFileNames = new ArrayList<>();
        Set<String> msFileNames = request.getMsAttachmentNames();

        // 获取富文本图片附件名称
        List<PlatformCustomFieldItemDTO> richTexts = request.getCustomFieldList()
                .stream()
                .filter(item -> item.getType().equals("richText"))
                .collect(Collectors.toList());
        richTexts.forEach(richText -> {
            if (richText.getValue() != null) {
                String url = richText.getValue().toString();
                if (url.contains("fileName")) {
                    // 本地上传的图片URL
                    msFileNames.add(url.substring(url.indexOf("=") + 1, url.lastIndexOf(")")));
                } else if (url.contains("platform=Jira")) {
                    // Jira同步的图片URL
                    msFileNames.add(url.substring(url.indexOf("[") + 1, url.indexOf("]")));
                }
            }
        });

        // 获得所有Jira附件, 遍历删除MS中不存在的
        JiraIssue jiraIssue = jiraClientV2.getIssues(request.getPlatformId());
        Map fields = jiraIssue.getFields();
        List attachments = (List) fields.get("attachment");
        if (!attachments.isEmpty() && attachments.size() > 0) {
            for (int i = 0; i < attachments.size(); i++) {
                Map attachment = (Map) attachments.get(i);
                String filename = attachment.get("filename").toString();
                jiraFileNames.add(filename);
                if (!msFileNames.contains(filename)) {
                    String fileId = attachment.get("id").toString();
                    jiraClientV2.deleteAttachment(fileId);
                }
            }
        }

        // 上传富文本有关的新附件
        List<File> imageFiles = getImageFiles(request);
        imageFiles.forEach(img -> {
            if (!jiraFileNames.contains(img.getName())) {
                jiraClientV2.uploadAttachment(request.getPlatformId(), img);
            }
        });
    }

    private String parseRichTextImageUrlToJira(String parseRichText) {
        String regex = "(\\!\\[.*?\\]\\((.*?)\\))";
        if (StringUtils.isBlank(parseRichText)) {
            return "";
        }
        Matcher matcher = Pattern.compile(regex).matcher(parseRichText);
        while (matcher.find()) {
            String msRichAttachmentUrl = matcher.group();
            String filename = "";
            if (msRichAttachmentUrl.contains("fileName")) {
                // 本地上传的图片URL
                filename = msRichAttachmentUrl.substring(msRichAttachmentUrl.indexOf("=") + 1, msRichAttachmentUrl.lastIndexOf(")"));
            } else if (msRichAttachmentUrl.contains("platform=Jira")) {
                // Jira同步的图片URL
                filename = msRichAttachmentUrl.substring(msRichAttachmentUrl.indexOf("[") + 1, msRichAttachmentUrl.indexOf("]"));
            }
            parseRichText = parseRichText.replace(msRichAttachmentUrl, "\n!" + filename + "|width=1360,height=876!\n");
        }
        return parseRichText;
    }
}
