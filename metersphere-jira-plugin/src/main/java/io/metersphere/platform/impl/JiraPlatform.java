package io.metersphere.platform.impl;


import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.platform.api.AbstractPlatform;
import io.metersphere.platform.client.JiraClientV2;
import io.metersphere.platform.constants.AttachmentSyncType;
import io.metersphere.platform.constants.CustomFieldType;
import io.metersphere.platform.domain.*;
import io.metersphere.platform.utils.BeanUtils;
import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JiraPlatform extends AbstractPlatform {

    protected JiraClientV2 jiraClientV2;

    protected SimpleDateFormat sdfWithZone = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    protected JiraProjectConfig projectConfig;

    private Set<String> jiraImageFileNames;

    private static final String ATTACHMENT_NAME = "attachment";
    private static final String SPRINT_FIELD_NAME = "sprint";
    private static final String DESCRIPTION_FIELD_NAME = "description";
    private static final String SUMMARY_FIELD_NAME = "summary";
    private static final String TIME_TRACKING_FIELD_NAME = "timetracking";
    private static final String ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME = "originalEstimate";
    private static final String REMAINING_ESTIMATE_TRACKING_FIELD_NAME = "remainingEstimate";
    private static final String USER_SEARCH_METHOD = "getUserSearchOptions";
    private static final String ASSIGNABLE_SEARCH_METHOD = "getAssignableOptions";
    private static final String ISSUE_LINKS_SEARCH_METHOD = "getIssueLinkOptions";
    private static final String ISSUE_LINK = "issuelinks";
    private static final String ISSUE_LINK_TYPE = "issueLinkTypes";
    private static final String ISSUE_LINK_TYPE_COLUMN_ZH = "链接事务类型";

    public JiraPlatform(PlatformRequest request) {
        super.key = JiraPlatformMetaInfo.KEY;
        super.request = request;
        jiraClientV2 = new JiraClientV2();
        setConfig();
    }

    public JiraConfig setConfig() {
        JiraConfig config = getIntegrationConfig();
        validateConfig(config);
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

    public JiraConfig setUserConfig(String userPlatformInfo) {
        JiraConfig config = getIntegrationConfig();
        JiraUserPlatformInfo userInfo = StringUtils.isBlank(userPlatformInfo) ? new JiraUserPlatformInfo()
                : JSON.parseObject(userPlatformInfo, JiraUserPlatformInfo.class);
        if (StringUtils.isNotBlank(userInfo.getAuthType()) && StringUtils.isNotBlank(userInfo.getToken())) {
            config.setAuthType(userInfo.getAuthType());
            config.setToken(userInfo.getToken());
        }
        if (StringUtils.isNotBlank(userInfo.getJiraAccount())
                && StringUtils.isNotBlank(userInfo.getJiraPassword())) {
            // 历史数据 authType 为空
            config.setAuthType(userInfo.getAuthType());
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
                if (StringUtils.isNotBlank(defaultCustomFields)) {
                    issue.setCustomFieldList(JSON.parseArray(defaultCustomFields, PlatformCustomFieldItemDTO.class));
                } else {
                    issue.setCustomFieldList(new ArrayList<>());
                }
            } else {
                mergeCustomField(issue, defaultCustomFields);
            }

            Map fields = jiraIssue.getFields();
            String status = getStatus(fields);

            Map<String, String> fileContentMap = getContextMap((List) fields.get(ATTACHMENT_NAME));

            // 先转换下desc的图片
            String description = parseJira2MsRichText(Optional.ofNullable(fields.get(DESCRIPTION_FIELD_NAME)).orElse("").toString(), fileContentMap);
            fields.put(DESCRIPTION_FIELD_NAME, description);
            List<PlatformCustomFieldItemDTO> customFieldItems = syncIssueCustomFieldList(issue.getCustomFieldList(), jiraIssue.getFields());

            parseSpecialCustomField(customFieldItems, fields);

            // 其他自定义里有富文本框的也转换下图片
            for (PlatformCustomFieldItemDTO item : customFieldItems) {
                if (!StringUtils.equals(DESCRIPTION_FIELD_NAME, item.getId())) {
                    // desc转过了，跳过
                    if (StringUtils.equals(CustomFieldType.RICH_TEXT.getValue(), item.getType())) {
                        item.setValue(parseJira2MsRichText((String) item.getValue(), fileContentMap));
                    }
                }
            }

            Map assignee = (Map) fields.get("assignee");
            issue.setTitle(fields.get(SUMMARY_FIELD_NAME).toString());
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

    @Override
    protected String getCustomFieldsValuesString(List<PlatformCustomFieldItemDTO> thirdPartCustomField) {
        List fields = new ArrayList();
        thirdPartCustomField.forEach(item -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("customData", item.getCustomData());
            field.put(ID_FIELD_NAME, item.getId());
            field.put("name", item.getName());
            field.put("type", item.getType());
            field.put("inputSearch", item.getInputSearch());
            field.put("optionMethod", item.getOptionMethod());
            String defaultValue = item.getDefaultValue();
            if (StringUtils.isNotBlank(defaultValue)) {
                field.put("value", JSON.parseObject(defaultValue));
            }
            fields.add(field);
        });
        return JSON.toJSONString(fields);
    }

    private void parseSpecialCustomField(List<PlatformCustomFieldItemDTO> customFieldItems, Map fields) {
        for (PlatformCustomFieldItemDTO customFieldItem : customFieldItems) {
            Object value = customFieldItem.getValue();
            try {
                if (customFieldItem.getInputSearch() != null
                        && customFieldItem.getInputSearch()
                        && StringUtils.equalsAny(customFieldItem.getOptionMethod(), USER_SEARCH_METHOD, ASSIGNABLE_SEARCH_METHOD)) {
                    Object itemFields = fields.get(customFieldItem.getCustomData());
                    String displayName;
                    if (itemFields instanceof List) {
                        HashMap<String, String> optionLabelMap = new HashMap<>();
                        for (Object item : ((List) itemFields)) {
                            Map fieldMap = (Map) item;
                            String val;
                            Object accountId = fieldMap.get("accountId");
                            if (accountId != null && StringUtils.isNotBlank(accountId.toString())) {
                                val = accountId.toString();
                            } else {
                                val = fieldMap.get("name").toString();
                            }
                            if (fieldMap.get("emailAddress") != null) {
                                optionLabelMap.put(val, fieldMap.get("displayName") + " (" + fieldMap.get("emailAddress") + ")");
                            } else {
                                optionLabelMap.put(val, fieldMap.get("displayName").toString());
                            }
                        }
                        displayName = JSON.toJSONString(optionLabelMap);
                    } else {
                        if (itemFields == null) {
                            continue;
                        }
                        Map fieldMap = (Map) itemFields;
                        displayName = fieldMap.get("displayName").toString();
                        if (fieldMap.get("emailAddress") != null) {
                            displayName += " (" + fieldMap.get("emailAddress") + ")";
                        }
                    }
                    customFieldItem.setOptionLabel(displayName);
                    continue;
                }

                // 解析 sprint 的 ID 重新设置
                if (value != null && value instanceof List) {
                    List arrayValue = (List) value;
                    if (CollectionUtils.isEmpty(arrayValue)) {
                        continue;
                    }
                    String valueStr = arrayValue.get(0).toString();

                    /*
                     * 参考格式
                     * "customfield_10105": [
                     *    "com.atlassian.greenhopper.service.sprint.Sprint@7394e052[id=6,rapidViewId=4,state=FUTURE,name=AAA Sprint 3]"
                     * ]
                     */
                    if (StringUtils.contains(valueStr, "sprint")) {
                        // 非 SaaS 版本，参数值中带了名称，将名称加入下拉框选项
                        String substring = valueStr.substring(valueStr.indexOf("[") + 1, valueStr.length() - 1);
                        for (String s : substring.split(",")) {
                            String[] param = s.split("=");
                            if (StringUtils.equals(param[0], "id")) {
                                customFieldItem.setValue(param[1]);
                            } else if (StringUtils.equals(param[0], "name")) {
                                customFieldItem.setOptionLabel(param[1]);
                            }
                        }
                    } else if (StringUtils.equals(customFieldItem.getName(), "Sprint")) {
                        // SaaS 版本
                        customFieldItem.setValue(arrayValue.get(0));
                        List<Map> sprintValue = (List) fields.get(customFieldItem.getId());
                        Object valueName = Optional.ofNullable(sprintValue.get(0).get("name")).orElse(StringUtils.EMPTY);
                        customFieldItem.setOptionLabel(valueName.toString());
                    }
                    continue;
                }

                // 设置 时间跟踪
                if (StringUtils.equals(customFieldItem.getId(), ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME)) {
                    Map timeTracking = (Map) fields.get(TIME_TRACKING_FIELD_NAME);
                    if (timeTracking != null) {
                        customFieldItem.setValue(timeTracking.get(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME));
                    }
                }

                if (StringUtils.equals(customFieldItem.getId(), REMAINING_ESTIMATE_TRACKING_FIELD_NAME)) {
                    Map timeTracking = (Map) fields.get(TIME_TRACKING_FIELD_NAME);
                    if (timeTracking != null) {
                        customFieldItem.setValue(timeTracking.get(REMAINING_ESTIMATE_TRACKING_FIELD_NAME));
                    }
                }

                if (StringUtils.equals(customFieldItem.getType(), CustomFieldType.DATETIME.getValue())) {
                    if (customFieldItem.getValue() != null && customFieldItem.getValue() instanceof String) {
                        customFieldItem.setValue(convertToUniversalFormat(customFieldItem.getValue().toString()));
                    }
                }

                // sync issue link type
                // 只同步缺陷其中一组事务类型的数据, 不支持多组事务类型, 与新增编辑保持一致
                if (StringUtils.equals(customFieldItem.getId(), ISSUE_LINK_TYPE)) {
                    List<Map<String, Object>> issueLinks = (List) fields.get(ISSUE_LINK);
                    if (CollectionUtils.isNotEmpty(issueLinks)) {
                        Map<String, Object> firstLink = issueLinks.get(0);
                        Map<String, Object> linkType = (Map) firstLink.get("type");
                        boolean isFirstInward = firstLink.containsKey("inwardIssue");
                        String type = isFirstInward ? linkType.get("inward").toString() : linkType.get("outward").toString();
                        customFieldItem.setValue(type);
                        List<String> issueLinkVars = new ArrayList<>();
                        issueLinks.stream().filter(filterLink -> {
                            Map<String, Object> filterLinkType = (Map) filterLink.get("type");
                            return StringUtils.equals(filterLinkType.get("id").toString(), linkType.get("id").toString());
                        }).filter(filterLink -> isFirstInward ? filterLink.containsKey("inwardIssue") : filterLink.containsKey("outwardIssue"))
                                .forEach(filterLink -> {
                                    Map<String, Object> linkIssue;
                                    if (isFirstInward) {
                                        linkIssue = (Map) filterLink.get("inwardIssue");
                                    } else {
                                        linkIssue = (Map) filterLink.get("outwardIssue");
                                    }
                                    issueLinkVars.add(linkIssue.get("key").toString());
                                });
                        Optional<PlatformCustomFieldItemDTO> any = customFieldItems.stream().filter(field -> ISSUE_LINK.equals(field.getCustomData())).findAny();
                        any.ifPresent(platformCustomFieldItemDTO -> platformCustomFieldItemDTO.setValue(issueLinkVars));
                    }
                }
            } catch (Exception e) {
                LogUtil.error(e);
            }
        }
    }

    public static String convertToUniversalFormat(String input) {
        if (!input.contains("T")) {
            return input;
        }
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        OffsetDateTime offsetDateTime = OffsetDateTime.parse(input, inputFormatter);
        return offsetDateTime.format(outputFormatter);
    }

    private String parseJira2MsRichText(String text, Map<String, String> fileContentMap) {
        if (StringUtils.isBlank(text)) {
            return text;
        }

        text = text.replaceAll("!image", "\n!image");
        String[] splits = text.split("\\n");
        for (int j = 0; j < splits.length; j++) {
            String splitStr = splits[j];
            if (StringUtils.isNotEmpty(splitStr)) {
                List<String> keys = fileContentMap.keySet().stream().filter(key -> splitStr.contains(key)).collect(Collectors.toList());
                if (CollectionUtils.isNotEmpty(keys)) {
                    text = text.replace(splitStr, fileContentMap.get(keys.get(0)));
                    fileContentMap.remove(keys.get(0));
                }
            }
        }

        // 这个 parse 顺序不能调换
        try {
            text = parseJiraLink2MsLink(text);
            text = parseSimpleJiraLink2MsLink(text);
        } catch (Exception e) {
            LogUtil.error(e);
        }
        return text;
    }
    /**
     * 这个格式是 ms 创建后同步到 jira 的
     * [GGG]([http://aa.com|http://aa.com]) -> [GGG](http://aa.com)
     * @param input
     * @return
     */
    private String parseJiraLink2MsLink(String input) {
        String pattern = "(\\(\\[.*?\\]\\))";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(1);
            if (StringUtils.isNotEmpty(group) && group.startsWith("([http")) {
                String[] split = group.split("\\|");
                String msFormat = split[0].replaceFirst("\\[", "") + ")";
                input = input.replace(group, msFormat);
            }
        }
        return input;
    }

    /**
     * 这个格式是 jira 的
     * [http://aa.com|http://aa.com] -> [asd](http://aa.com)
     * @param input
     * @return
     */
    private String parseSimpleJiraLink2MsLink(String input) {
        String pattern = "(\\[.*?\\])";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(1);
            if (StringUtils.isNotEmpty(group) && group.startsWith("[http")) {
                String[] split = group.split("\\|");
                String msFormat = split[0].replaceFirst("\\[", "");
                msFormat = "[" + msFormat + "]" + "(" + msFormat + ")";
                input = input.replace(group, msFormat);
            }
        }
        return input;
    }


    private Map<String, String> getContextMap(List attachments) {
        // 附件处理
        Map<String, String> fileContentMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(attachments)) {
            for (int i = 0; i < attachments.size(); i++) {
                Map attachment = (Map) attachments.get(i);
                String filename = attachment.get("filename").toString();
                String content = attachment.get("content").toString();

                try {
                    content = getProxyPath(new URI(content).getPath());
                    if (StringUtils.contains(attachment.get("mimeType").toString(), "image")) {
                        String contentUrl = "![" + filename + "](" + content + ")";
                        fileContentMap.put(filename, contentUrl);
                    } else {
                        String contentUrl = "附件[" + filename + "]下载地址:" + content;
                        fileContentMap.put(filename, contentUrl);
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
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
                String summary = fields.get(SUMMARY_FIELD_NAME).toString();
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

    @Override
    public List<SelectOption> getFormOptions(GetOptionRequest request) {
        return getFormOptions(this, request);
    }

    @Override
    @Deprecated
    public List<SelectOption> getProjectOptions(GetOptionRequest request) {
        return getFormOptions(this, request);
    }

    public List getAssignableOptions(GetOptionRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return getAssignableOptions(projectConfig.getJiraKey(), request.getQuery());
    }

    private List getAssignableOptions(String jiraKey, String query) {
        List<JiraUser> userOptions = jiraClientV2.assignableUserSearch(jiraKey, query);
        return handleOptions(userOptions);
    }

    public List getUserSearchOptions(GetOptionRequest request) {
        return getUserSearchOptions(request.getQuery());
    }

    private List getUserSearchOptions(String query) {
        List<JiraUser> reportOptions = jiraClientV2.allUserSearch(query);
        return handleOptions(reportOptions);
    }

    public List<SelectOption> getIssueLinkOptions(GetOptionRequest request) {
        return getIssueLinkOptions("", request.getQuery());
    }

    public List<SelectOption> getIssueLinkOptions(String currentIssueKey, String query) {
        return jiraClientV2.getIssueLinks(currentIssueKey, query)
                .stream()
                .map(item -> new SelectOption(item.getKey() + StringUtils.SPACE + item.getSummary(), item.getKey())).toList();
    }

    public List<SelectOption> getIssueLinkTypeOptions() {
        List<SelectOption> selectOptions = new ArrayList<>();
        List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes = jiraClientV2.getIssueLinkType();
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
     * 由 getFormOptions 反射调用
     *
     * @return
     */
    public List<SelectOption> getIssueTypes(GetOptionRequest request) {
        JiraProjectConfig projectConfig = getProjectConfig(request.getProjectConfig());
        return jiraClientV2.getIssueType(projectConfig.getJiraKey())
                .stream()
                .map(item -> new SelectOption(item.getName(), item.getId()))
                .collect(Collectors.toList());
    }

    public List<SelectOption> getSprintOptions(GetOptionRequest request) {
        return jiraClientV2.getSprint(request.getQuery())
                .stream()
                .map(sprint -> new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString()))
                .collect(Collectors.toList());
    }

    @Override
    public IssuesWithBLOBs addIssue(PlatformIssuesUpdateRequest request) {
        jiraImageFileNames = new HashSet<>();
        setUserConfig(request.getUserPlatformUserConfig());
        projectConfig = getProjectConfig(request.getProjectConfig());
        this.isThirdPartTemplate = projectConfig.isThirdPartTemplate();
        validateProjectKey(projectConfig.getJiraKey());
        validateIssueType();

        // 处理Issue Links
        List<PlatformCustomFieldItemDTO> issueLinkFields = filterIssueLinksField(request);
        Map<String, Object> addJiraIssueParam = buildUpdateParam(request, projectConfig.getJiraIssueTypeId(), projectConfig.getJiraKey());

        JiraAddIssueResponse result = jiraClientV2.addIssue(JSON.toJSONString(addJiraIssueParam), getFieldNameMap(request));
        JiraIssue jiraIssue = jiraClientV2.getIssues(result.getId());

        // 上传富文本中的图片作为附件
        List<File> imageFiles = getImageFiles(request);
        imageFiles.forEach(img -> jiraClientV2.uploadAttachment(result.getKey(), img));
        // link issue
        if (CollectionUtils.isNotEmpty(issueLinkFields)) {
            linkIssue(issueLinkFields, result.getKey(), jiraClientV2.getIssueLinkType());
        }

        String status = getStatus(jiraIssue.getFields());
        request.setPlatformStatus(status);
        request.setPlatformId(result.getKey());
        request.setId(UUID.randomUUID().toString());

        return request;
    }

    private List<File> getImageFiles(PlatformIssuesUpdateRequest request) {
        List<File> files = new ArrayList<>();
        List<PlatformCustomFieldItemDTO> customFields = request.getCustomFieldList();
        if (CollectionUtils.isNotEmpty(customFields)) {
            customFields.forEach(item -> {
                String fieldName = item.getCustomData();
                if (StringUtils.isNotBlank(fieldName)) {
                    if (item.getValue() != null) {
                        if (StringUtils.isNotBlank(item.getType())) {
                            if (StringUtils.equalsAny(item.getType(), "richText")) {
                                files.addAll(getImageFiles(item.getValue().toString()));
                            }
                        }
                    }
                }
            });
        }

        return files;
    }

    /**
     * 参数比较特殊，需要特别处理
     *
     * @param fields
     */
    private void setSpecialParam(Map fields) {

        try {
            Map<String, JiraCreateMetadataResponse.Field> createMetadata = jiraClientV2.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraIssueTypeId());

            for (String key : createMetadata.keySet()) {
                JiraCreateMetadataResponse.Field item = createMetadata.get(key);
                JiraCreateMetadataResponse.Schema schema = item.getSchema();

                if (StringUtils.equals(key, TIME_TRACKING_FIELD_NAME)) {
                    Map newField = new LinkedHashMap<>();
                    // originalEstimate -> 2d 转成 timetracking : { originalEstimate: 2d}
                    newField.put(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME, fields.get(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME));
                    newField.put(REMAINING_ESTIMATE_TRACKING_FIELD_NAME, fields.get(REMAINING_ESTIMATE_TRACKING_FIELD_NAME));
                    fields.put(key, newField);
                    fields.remove(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME);
                    fields.remove(REMAINING_ESTIMATE_TRACKING_FIELD_NAME);
                }

                if (schema == null || fields.get(key) == null) {
                    continue;
                }

                if (schema.getCustom() != null) {
                    if (schema.getCustom().endsWith(SPRINT_FIELD_NAME)) {
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
            LogUtil.error(e);
        }
    }

    private Map<String, Object> buildUpdateParam(PlatformIssuesUpdateRequest request, String issueTypeId, String jiraKey) {
        request.setPlatform(key);
        Map fields = new LinkedHashMap<>();
        Map project = new LinkedHashMap<>();

        fields.put("project", project);
        project.put("key", jiraKey);

        Map issuetype = new LinkedHashMap<>();
        issuetype.put("id", issueTypeId);
        fields.put("issuetype", issuetype);

        Map addJiraIssueParam = new LinkedHashMap();
        addJiraIssueParam.put("fields", fields);

        if (isThirdPartTemplate) {
            parseCustomFiled(request, fields);
            request.setTitle(fields.get(SUMMARY_FIELD_NAME).toString());
        } else {
            fields.put(SUMMARY_FIELD_NAME, request.getTitle());
            // 添加后，解析图片会用到
            if (CollectionUtils.isNotEmpty(request.getCustomFieldList())) {
                request.getCustomFieldList().add(getRichTextCustomField(DESCRIPTION_FIELD_NAME, request.getDescription()));
            }
            parseCustomFiled(request, fields);
        }
        setSpecialParam(fields);

        return addJiraIssueParam;
    }

    private List<PlatformCustomFieldItemDTO> filterIssueLinksField(PlatformIssuesUpdateRequest request) {
        if (CollectionUtils.isNotEmpty(request.getCustomFieldList())) {
            // remove and return issue link field
            List<PlatformCustomFieldItemDTO> issueLinkFields = request.getCustomFieldList().stream()
                    .filter(item -> StringUtils.equalsAny(item.getCustomData(), ISSUE_LINK_TYPE, ISSUE_LINK)).toList();
            request.getCustomFieldList().removeAll(issueLinkFields);
            return issueLinkFields;
        } else {
            return new ArrayList<>();
        }
    }

    private void linkIssue(List<PlatformCustomFieldItemDTO> issueLinkFields, String issueKey, List<JiraIssueLinkTypeResponse.IssueLinkType> issueLinkTypes) {
        // 暂时只支持关联一组事务, 前台Form表单对多组事务关联关系的支持麻烦
        PlatformCustomFieldItemDTO issueLinkType = issueLinkFields.get(0);
        PlatformCustomFieldItemDTO issueLink = issueLinkFields.get(1);
        if (issueLinkType.getValue() != null && issueLink.getValue() != null) {
            String type = issueLinkType.getValue().toString();
            JiraIssueLinkTypeResponse.IssueLinkType attachType = issueLinkTypes.stream().filter(item -> StringUtils.equalsAny(type, item.getInward(), item.getOutward())).findFirst().get();
            List<String> linkKeys = JSON.parseArray(issueLink.getValue().toString(), String.class);
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
                jiraClientV2.linkIssue(issueLinkRequest);
            });
        }
    }

    private void deleteIssueLinks(String issueKey) {
        JiraIssue issue = jiraClientV2.getIssues(issueKey);
        Map<String, Object> fields = issue.getFields();
        if (fields.containsKey(ISSUE_LINK)) {
            List<Map<String, Object>> issueLinks = (List) fields.get(ISSUE_LINK);
            issueLinks.stream().map(item -> item.get("id").toString()).forEach(jiraClientV2::deleteIssueLink);
        }
    }

    private PlatformCustomFieldItemDTO getRichTextCustomField(String name, String value) {
        PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
        customField.setId(name);
        customField.setType(CustomFieldType.RICH_TEXT.getValue());
        customField.setCustomData(name);
        customField.setValue(value);
        customField.setName(name);
        return customField;
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

        if (CollectionUtils.isNotEmpty(customFields)) {
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
                                    List values = JSON.parseArray((String) item.getValue());
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
                                if (fieldName.equals(DESCRIPTION_FIELD_NAME)) {
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

    @Override
    public IssuesWithBLOBs updateIssue(PlatformIssuesUpdateRequest request) {
        setUserConfig(request.getUserPlatformUserConfig());

        projectConfig = getProjectConfig(request.getProjectConfig());
        jiraImageFileNames = new HashSet<>();
        this.isThirdPartTemplate = this.projectConfig.isThirdPartTemplate();

        validateProjectKey(projectConfig.getJiraKey());
        validateIssueType();

        // 过滤 issue link
        List<PlatformCustomFieldItemDTO> issueLinksField = filterIssueLinksField(request);
        Map<String, Object> param = buildUpdateParam(request, projectConfig.getJiraIssueTypeId(), projectConfig.getJiraKey());
        jiraClientV2.updateIssue(request.getPlatformId(), JSON.toJSONString(param), getFieldNameMap(request));

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
        if (CollectionUtils.isNotEmpty(issueLinksField)) {
            // 编辑时, 删除上一次所有的 issue link, 重新关联一组 issue link
            deleteIssueLinks(request.getPlatformId());
            linkIssue(issueLinksField, request.getPlatformId(), jiraClientV2.getIssueLinkType());
        }
        return request;
    }

    private static Map<String, String> getFieldNameMap(PlatformIssuesUpdateRequest request) {
        Map<String, String> filedNameMap = null;
        if (CollectionUtils.isNotEmpty(request.getCustomFieldList())) {
            filedNameMap = request.getCustomFieldList().stream()
                    .collect(Collectors.toMap(PlatformCustomFieldItemDTO::getId, PlatformCustomFieldItemDTO::getName));
        }
        return filedNameMap;
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
    public List<PlatformStatusDTO> getStatusList(String projectConfig) {
        List<PlatformStatusDTO> platformStatusDTOS = new ArrayList<>();
        JiraProjectConfig jiraProjectConfig = getProjectConfig(projectConfig);
        List<JiraStatusResponse> statusResponses = jiraClientV2.getStatus(jiraProjectConfig.getJiraKey());
        List<List<JiraStatusResponse.Statuses>> issueTypeStatus = statusResponses.stream().filter(jiraStatusResponse -> jiraProjectConfig.getJiraIssueTypeId().equals(jiraStatusResponse.getId())).map(JiraStatusResponse::getStatuses).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(issueTypeStatus)) {
            issueTypeStatus.forEach(item -> {
                item.forEach(statuses -> {
                    PlatformStatusDTO platformStatusDTO = new PlatformStatusDTO();
                    platformStatusDTO.setLabel(statuses.getName());
                    platformStatusDTO.setValue(statuses.getName());
                    platformStatusDTOS.add(platformStatusDTO);
                });

            });
        }
        return platformStatusDTOS;
    }

    @Override
    public List<PlatformStatusDTO> getTransitions(String projectConfig, String issueKey) {
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

    @Override
    public List<PlatformCustomFieldItemDTO> getThirdPartCustomField(String projectConfigStr) {
        Set<String> ignoreSet = new HashSet() {{
            add(ATTACHMENT_NAME);
        }};
        projectConfig = getProjectConfig(projectConfigStr);

        Map<String, JiraCreateMetadataResponse.Field> createMetadata =
                jiraClientV2.getCreateMetadata(projectConfig.getJiraKey(), projectConfig.getJiraIssueTypeId());

        String assignableOptions = "[]";
        String allUserOptions = "[]";
        String issueLinkOptions = "[]";

        try {
            assignableOptions = JSON.toJSONString(getAssignableOptions(projectConfig.getJiraKey(), null));
            allUserOptions = JSON.toJSONString(getUserSearchOptions(StringUtils.EMPTY));
            issueLinkOptions = JSON.toJSONString(getIssueLinkOptions(null, null));
        } catch (Exception e) {
            LogUtil.error(e);
        }

        List<PlatformCustomFieldItemDTO> fields = new ArrayList<>();
        Character filedKey = 'A';
        for (String name : createMetadata.keySet()) {
            JiraCreateMetadataResponse.Field item = createMetadata.get(name);
            if (ignoreSet.contains(name)) {
                continue;  // attachment todo
            }
            JiraCreateMetadataResponse.Schema schema = item.getSchema();
            PlatformCustomFieldItemDTO customField = new PlatformCustomFieldItemDTO();
            customField.setKey(String.valueOf(filedKey++));
            customField.setId(name);
            customField.setCustomData(name);
            customField.setName(item.getName());

            if (StringUtils.isNotBlank(item.getKey()) && item.getKey().equals("summary")) {
                customField.setRequired(true);
            } else {
                customField.setRequired(item.isRequired());
            }
            setCustomFiledType(schema, customField, assignableOptions, allUserOptions, issueLinkOptions);
            setCustomFiledDefaultValue(customField, item);
            List options = getAllowedValuesOptions(item.getAllowedValues());
            setSpecialFieldOptions(customField, schema);
            if (options != null) {
                customField.setOptions(JSON.toJSONString(options));
            }
            fields.add(customField);
            filedKey = handleSpecialField(customField, fields, filedKey);
            if (ISSUE_LINK.equals(name)) {
                // 如果是Jira的issue link字段，需要同步添加issue link type字段
                PlatformCustomFieldItemDTO issueLinkField = new PlatformCustomFieldItemDTO();
                issueLinkField.setId(ISSUE_LINK_TYPE);
                issueLinkField.setName(ISSUE_LINK_TYPE_COLUMN_ZH);
                issueLinkField.setCustomData(ISSUE_LINK_TYPE);
                issueLinkField.setRequired(false);
                issueLinkField.setOptions(JSON.toJSONString(getIssueLinkTypeOptions()));
                issueLinkField.setType(CustomFieldType.SELECT.getValue());
                issueLinkField.setKey(String.valueOf(filedKey++));
                fields.add(issueLinkField);
            }
        }

        fields = fields.stream().filter(i -> StringUtils.isNotBlank(i.getType()))
                .collect(Collectors.toList());

        // 按类型排序，富文本排最后，input 排最前面，summary 排第一个
        fields.sort((a, b) -> {
            if (a.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return 1;
            if (b.getType().equals(CustomFieldType.RICH_TEXT.getValue())) return -1;
            if (a.getId().equals(ISSUE_LINK)) return 1;
            if (b.getId().equals(ISSUE_LINK)) return -1;
            if (a.getId().equals(ISSUE_LINK_TYPE)) return 1;
            if (b.getId().equals(ISSUE_LINK_TYPE)) return -1;
            if (a.getId().equals(SUMMARY_FIELD_NAME)) return -1;
            if (b.getId().equals(SUMMARY_FIELD_NAME)) return 1;
            if (a.getType().equals(CustomFieldType.INPUT.getValue())) return -1;
            if (b.getType().equals(CustomFieldType.INPUT.getValue())) return 1;
            return a.getType().compareTo(b.getType());
        });
        return fields;
    }

    private Character handleSpecialField(PlatformCustomFieldItemDTO customFieldItem,
                                         List<PlatformCustomFieldItemDTO> fields, Character filedKey) {
        String id = customFieldItem.getId();
        if (StringUtils.equals(id, ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME)) {
            PlatformCustomFieldItemDTO remainingEstimate = new PlatformCustomFieldItemDTO();
            BeanUtils.copyBean(remainingEstimate, customFieldItem);
            remainingEstimate.setId(REMAINING_ESTIMATE_TRACKING_FIELD_NAME);
            remainingEstimate.setCustomData(REMAINING_ESTIMATE_TRACKING_FIELD_NAME);
            remainingEstimate.setName("Remaining Estimate");
            remainingEstimate.setKey(String.valueOf(filedKey++));
            fields.add(remainingEstimate);
        }
        return filedKey;
    }

    private void setSpecialFieldOptions(PlatformCustomFieldItemDTO customField, JiraCreateMetadataResponse.Schema item) {
        try {
            String customType = item.getCustom();
            if (StringUtils.isNotBlank(customType)) {
                if (customType.contains(SPRINT_FIELD_NAME)) {
                    List<JiraSprint> sprints = jiraClientV2.getSprint(null);
                    List<SelectOption> options = new ArrayList<>();
                    sprints.forEach(sprint -> options.add(new SelectOption(StringUtils.join(sprint.getName(), " (", sprint.getBoardName(), ")"), sprint.getId().toString())));
                    customField.setOptions(JSON.toJSONString(options));
                    customField.setInputSearch(true);
                    customField.setOptionMethod("getSprintOptions");
                } else if (StringUtils.contains(customType, "epic-link")) {
                    List<JiraEpic> epics = jiraClientV2.getEpics(projectConfig.getJiraKey());
                    List<SelectOption> options = new ArrayList<>();
                    epics.forEach(sprint -> options.add(new SelectOption(sprint.getName(), sprint.getKey())));
                    customField.setOptions(JSON.toJSONString(options));
                }
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }
    }


    private void setCustomFiledType(JiraCreateMetadataResponse.Schema schema, PlatformCustomFieldItemDTO customField, String assignableOptions, String allUserOptions, String issueLinkOptions) {
        Map<String, String> fieldTypeMap = new HashMap() {{
            put(SUMMARY_FIELD_NAME, CustomFieldType.INPUT.getValue());
            put(DESCRIPTION_FIELD_NAME, CustomFieldType.RICH_TEXT.getValue());
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
                customField.setInputSearch(true);
                customField.setOptionMethod(USER_SEARCH_METHOD);
                customField.setOptions(allUserOptions);
            } else if (customType.contains("userpicker")) {
                value = CustomFieldType.SELECT.getValue();
                customField.setInputSearch(true);
                customField.setOptionMethod(USER_SEARCH_METHOD);
                customField.setOptions(allUserOptions);
            } else if (customType.contains("people")) {
                if (StringUtils.isNotBlank(schema.getType()) && StringUtils.equals(schema.getType(), "array")) {
                    value = CustomFieldType.MULTIPLE_SELECT.getValue();
                } else {
                    value = CustomFieldType.SELECT.getValue();
                }
                customField.setInputSearch(true);
                customField.setOptionMethod(USER_SEARCH_METHOD);
                customField.setOptions(allUserOptions);
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
            } else if (customType.contains("epic-link")) {
                value = CustomFieldType.SELECT.getValue();
            } else if (customType.contains(SPRINT_FIELD_NAME)) {
                value = CustomFieldType.SELECT.getValue();
            }
        } else {
            // 系统字段
            value = fieldTypeMap.get(customField.getId());
            String type = schema.getType();
            if (TIME_TRACKING_FIELD_NAME.equals(type)) {
                customField.setId(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME);
                customField.setCustomData(ORIGINAL_ESTIMATE_TRACKING_FIELD_NAME);
                customField.setName("Original Estimate");
                value = CustomFieldType.INPUT.getValue();
            } else if ("assignee".equals(schema.getSystem())) {
                // 经办人
                value = CustomFieldType.SELECT.getValue();
                customField.setInputSearch(true);
                customField.setOptionMethod(ASSIGNABLE_SEARCH_METHOD);
                customField.setOptions(assignableOptions);
            } else if ("reporter".equals(schema.getSystem())) {
                value = CustomFieldType.SELECT.getValue();
                customField.setInputSearch(true);
                customField.setOptionMethod(USER_SEARCH_METHOD);
                customField.setOptions(allUserOptions);
            } else if ("date".equals(type)) {
                value = CustomFieldType.DATE.getValue();
            } else if ("datetime".equals(type)) {
                value = CustomFieldType.DATETIME.getValue();
            } else if (ISSUE_LINK.equals(schema.getSystem())) {
                // 关联事务
                value = CustomFieldType.MULTIPLE_SELECT.getValue();
                customField.setInputSearch(true);
                customField.setOptionMethod(ISSUE_LINKS_SEARCH_METHOD);
                customField.setOptions(issueLinkOptions);
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

    private List handleOptions(List<JiraUser> userList) {
        List options = new ArrayList();
        userList.forEach(val -> {
            Map jsonObject = new LinkedHashMap<>();
            if (StringUtils.isNotBlank(val.getAccountId())) {
                jsonObject.put("value", val.getAccountId());
            } else {
                jsonObject.put("value", val.getName());
            }
            jsonObject.put("text", val.getDisplayName());
            if (StringUtils.isNotBlank(val.getEmailAddress())) {
                jsonObject.put("text", jsonObject.get("text") + " (" + val.getEmailAddress() + ")");
            }
            options.add(jsonObject);
        });
        return options;
    }

    @Override
    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        return jiraClientV2.proxyForGet(path, responseEntityClazz);
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
            List attachments = (List) fields.get(ATTACHMENT_NAME);
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

            List attachments = (List) jiraIssue.getFields().get(ATTACHMENT_NAME);
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
    public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {
        jiraClientV2.getAttachmentContent(fileKey, inputStreamHandler);
    }

    public void syncJiraRichTextAttachment(PlatformIssuesUpdateRequest request) {
        Set<String> jiraFileNames = new HashSet<>();
        Set<String> msFileNames = request.getMsAttachmentNames();

        // 获取富文本图片附件名称
        List<PlatformCustomFieldItemDTO> richTexts = request.getCustomFieldList()
                .stream()
                .filter(item -> item.getType().equals("richText"))
                .collect(Collectors.toList());

        if (CollectionUtils.isNotEmpty(richTexts)) {
            richTexts.forEach(richText -> {
                if (richText.getValue() != null) {
                    String url = richText.getValue().toString();
                    addJiraImageFileName(msFileNames, url);
                    addMsImageFileName(msFileNames, url);
                }
            });

        }

        // 获得所有Jira附件, 遍历删除MS中不存在的
        JiraIssue jiraIssue = jiraClientV2.getIssues(request.getPlatformId());
        Map fields = jiraIssue.getFields();
        List attachments = (List) fields.get(ATTACHMENT_NAME);
        if (!attachments.isEmpty() && attachments.size() > 0) {
            for (int i = 0; i < attachments.size(); i++) {
                Map attachment = (Map) attachments.get(i);
                String filename = attachment.get("filename").toString();
                jiraFileNames.add(filename);
                if (!msFileNames.contains(filename) && !jiraImageFileNames.contains(filename)) {
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

    /**
     * 获取 Ms 的图片名称 f2aef1f3.png
     * ![Stamp.png](/resource/md/get?fileName=f2aef1f3.png)
     */
    private void addMsImageFileName(Set<String> fileNames, String input) {
        addFileName(fileNames, "\\!\\[.*?\\]\\(/resource/md/get\\?fileName=(.*?)\\)", input);
    }

    /**
     * 获取从 jira 同步后的图片名称 d4cfd42c.png
     * ![d4cfd42c.png](/resource/md/get/path?platform=Jira&workspaceId=xxx&path=xx)
     */
    private void addJiraImageFileName(Set<String> fileNames, String input) {
        addFileName(fileNames,  "\\!\\[(.*?)\\]\\(/resource/md/get/path", input);
    }

    private void addFileName(Set<String> fileNames, String pattern, String input) {
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (StringUtils.isNotEmpty(path)) {
                fileNames.add(matcher.group(1));
            }
        }
    }

    private String parseRichTextImageUrlToJira(String parseRichText) {
        if (StringUtils.isBlank(parseRichText)) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\!?(\\[.*?\\]\\((.*?)\\))").matcher(parseRichText);
        while (matcher.find()) {
            String msRichAttachmentUrl = matcher.group();
            String filename = "";
            if (msRichAttachmentUrl.contains("fileName")) {
                // 本地上传的图片URL
                filename = msRichAttachmentUrl.substring(msRichAttachmentUrl.indexOf("=") + 1, msRichAttachmentUrl.lastIndexOf(")"));
            } else if (msRichAttachmentUrl.contains("platform=Jira")) {
                // Jira同步的图片URL
                filename = msRichAttachmentUrl.substring(msRichAttachmentUrl.indexOf("[") + 1, msRichAttachmentUrl.indexOf("]"));
                jiraImageFileNames.add(filename);
            }
            if (msRichAttachmentUrl.contains("(http")) {
                parseRichText = parseMsLink2JiraLink(parseRichText);
            } else {
                // 非链接
                parseRichText = parseRichText.replace(msRichAttachmentUrl, "\n\n!" + filename + "|width=1360,height=876!\n");
            }
        }
        return parseRichText;
    }

    private String parseMsLink2JiraLink(String input) {
        String pattern = "\\!?\\[.*?\\]\\((.*?)\\)";
        Matcher matcher = Pattern.compile(pattern).matcher(input);
        while (matcher.find()) {
            String group = matcher.group(0);
            String url = matcher.group(1);
            if (url.startsWith("http")) {
                String jiraFormat = "[" + url + "|" + url + "]";
                input = input.replace(group, jiraFormat);
            }
        }
        return input;
    }

    @Override
    public void syncAllIssues(SyncAllIssuesRequest syncRequest) {
        JiraProjectConfig projectConfig = getProjectConfig(syncRequest.getProjectConfig());
        this.isThirdPartTemplate = projectConfig.isThirdPartTemplate();

        int startAt = 0;
        // Jira最大支持100
        int maxResults = 100;
        List<JiraIssue> jiraIssues;
        int currentSize;

        if (this.isThirdPartTemplate) {
            super.defaultCustomFields = getCustomFieldsValuesString(getThirdPartCustomField(syncRequest.getProjectConfig()));
        } else {
            super.defaultCustomFields = syncRequest.getDefaultCustomFields();
        }

        this.projectConfig = projectConfig;

        do {
            SyncAllIssuesResult syncIssuesResult = new SyncAllIssuesResult();

            String jiraKey = projectConfig.getJiraKey();
            validateIssueType();
            validateProjectKey(jiraKey);

            JiraIssueListResponse result = jiraClientV2.getProjectIssues(startAt, maxResults, jiraKey, projectConfig.getJiraIssueTypeId());
            jiraIssues = result.getIssues();

            currentSize = jiraIssues.size();
            List<String> allIds = jiraIssues.stream().map(JiraIssue::getId).collect(Collectors.toList());
            // 创建的时候是 platform_id 存的key，之前全量同步存的是id，统一改成存key，这里做兼容处理
            allIds.addAll(jiraIssues.stream().map(JiraIssue::getKey).collect(Collectors.toList()));

            syncIssuesResult.setAllIds(allIds);

            if (syncRequest != null) {
                jiraIssues = filterSyncJiraIssueByCreated(jiraIssues, syncRequest);
            }
            Map<String, Object> attachmentMap = new HashMap<>();

            if (CollectionUtils.isNotEmpty(jiraIssues)) {
                if (!jiraIssues.get(0).getFields().containsKey(ATTACHMENT_NAME)) {
                    // 如果不包含附件信息，则查询下附件
                    try {
                        JiraIssueListResponse response = jiraClientV2.getProjectIssuesAttachment(startAt, maxResults,
                                jiraKey, projectConfig.getJiraIssueTypeId());
                        List<JiraIssue> jiraIssuesWithAttachment = response.getIssues();
                        attachmentMap = jiraIssuesWithAttachment.stream()
                                .collect(Collectors.toMap(JiraIssue::getKey,
                                        i -> i.getFields().get(ATTACHMENT_NAME)));
                    } catch (Exception e) {
                        LogUtil.error(e);
                    }
                }

                for (JiraIssue jiraIssue : jiraIssues) {
                    if (attachmentMap.containsKey(jiraIssue.getKey())) {
                        // 接口可能缺少附件字段，单独获取
                        jiraIssue.getFields().put(ATTACHMENT_NAME, attachmentMap.get(jiraIssue.getKey()));
                    }
                    PlatformIssuesDTO issue = getUpdateIssue(null, jiraIssue);

                    // 设置临时UUID，同步附件时需要用
                    issue.setId(UUID.randomUUID().toString());

                    issue.setPlatformId(jiraIssue.getKey());
                    syncIssuesResult.getUpdateIssues().add(issue);

                    //同步第三方平台系统附件字段
                    syncJiraIssueAttachments(syncIssuesResult, issue, jiraIssue);
                }
            }

            startAt += maxResults;

            HashMap<Object, Object> syncParam = buildSyncAllParam(syncIssuesResult);

            syncRequest.getHandleSyncFunc().accept(syncParam);
        } while (currentSize >= maxResults);
    }

    private List<JiraIssue> filterSyncJiraIssueByCreated(List<JiraIssue> jiraIssues, SyncAllIssuesRequest syncRequest) {
        if (syncRequest.getCreateTime() == null) {
            return jiraIssues;
        }
        List<JiraIssue> filterIssues = jiraIssues.stream().filter(jiraIssue -> {
            long createTimeMills = 0;
            try {
                createTimeMills = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) jiraIssue.getFields().get("created")).getTime();
                if (syncRequest.isPre()) {
                    return createTimeMills <= syncRequest.getCreateTime().longValue();
                } else {
                    return createTimeMills >= syncRequest.getCreateTime().longValue();
                }
            } catch (ParseException e) {
                e.printStackTrace();
                return false;
            }
        }).collect(Collectors.toList());
        return filterIssues;
    }

    @Override
    protected Object getSyncJsonParamValue(Object value) {
        Map valObj = ((Map) value);
        Map child = (Map) valObj.get("child");

        String idValue = Optional.ofNullable(valObj.get(ID_FIELD_NAME))
                .orElse(StringUtils.EMPTY)
                .toString();

        String accountId = Optional.ofNullable(valObj.get("accountId"))
                .orElse(StringUtils.EMPTY)
                .toString();

        if (child != null) {// 级联框
            return getCascadeValues(idValue, child);
        }  else if (StringUtils.isNotBlank(accountId)) {
            return accountId;
        } else {
            if (valObj.containsKey("emailAddress")) {
                return valObj.get("name");
            }
            if (StringUtils.isNotBlank(idValue)) {
                return idValue;
            }
            return valObj.get("key");
        }
    }
}
