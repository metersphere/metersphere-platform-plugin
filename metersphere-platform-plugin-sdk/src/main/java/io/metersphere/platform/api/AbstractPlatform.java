package io.metersphere.platform.api;

import io.metersphere.plugin.exception.MSPluginException;
import io.metersphere.plugin.utils.JSON;
import io.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.constants.CustomFieldType;
import io.metersphere.platform.domain.*;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractPlatform implements Platform {
    protected String key;
    protected PlatformRequest request;
    protected boolean isThirdPartTemplate;
    protected String defaultCustomFields;

    public static final String MD_IMAGE_DIR = "/opt/metersphere/data/image/markdown";
    public static final String PROXY_PATH = "/resource/md/get/path?platform=%s&workspaceId=%s&path=%s";
    public static final String ID_FIELD_NAME = "id";
    public static final String MARKDOWN_IMAGE_REGULAR = "(\\!\\[.*?\\]\\((.*?)\\))";

    public <T> T getIntegrationConfig(Class<T> clazz) {
        String config = request.getIntegrationConfig();
        if (StringUtils.isBlank(config)) {
            MSPluginException.throwException("配置为空");
        }
        return JSON.parseObject(config, clazz);
    }

    @Override
    public List<SelectOption> getProjectOptions(GetOptionRequest request) {
        return null;
    }

    @Override
    public List<SelectOption> getFormOptions(GetOptionRequest request)  {
        return null;
    }

    public List<SelectOption> getFormOptions(Object subObject, GetOptionRequest request)  {
        String method = request.getOptionMethod();
        try {
            // 这里反射调用 getIssueTypes 等方法，获取下拉框选项
            return (List<SelectOption>) subObject.getClass().getMethod(method, request.getClass()).invoke(subObject, request);
        } catch (InvocationTargetException e) {
            LogUtil.error(e.getTargetException());
            MSPluginException.throwException(e.getTargetException());
        }  catch (Exception e) {
            LogUtil.error(e);
            MSPluginException.throwException(e);
        }
        return null;
    }

    protected HashMap<Object, Object> buildSyncAllParam(SyncAllIssuesResult syncIssuesResult) {
        HashMap<Object, Object> syncParam = new HashMap<>();
        syncParam.put("updateIssues", syncIssuesResult.getUpdateIssues());
        syncParam.put("attachmentMap", syncIssuesResult.getAttachmentMap());
        syncParam.put("allIds", syncIssuesResult.getAllIds());
        return syncParam;
    }

    @Override
    public void getAttachmentContent(String fileKey, Consumer<InputStream> inputStreamHandler) {}

    protected void mergeCustomField(PlatformIssuesDTO issue, String defaultCustomField) {
        if (StringUtils.isNotBlank(defaultCustomField)) {
            List<PlatformCustomFieldItemDTO> customFields = issue.getCustomFieldList();
            Map<String, PlatformCustomFieldItemDTO> fieldMap = customFields.stream()
                    .collect(Collectors.toMap(PlatformCustomFieldItemDTO::getId, i -> i));

            List<PlatformCustomFieldItemDTO> defaultFields = JSON.parseArray(defaultCustomField, PlatformCustomFieldItemDTO.class);
            for (PlatformCustomFieldItemDTO defaultField : defaultFields) {
                String id = defaultField.getId();
                if (StringUtils.isBlank(id)) {
                    defaultField.setId(defaultField.getKey());
                }
                if (fieldMap.keySet().contains(id)) {
                    // 设置第三方平台的属性名称
                    fieldMap.get(id).setCustomData(defaultField.getCustomData());
                } else {
                    // 如果自定义字段里没有模板新加的字段，就把新字段加上
                    customFields.add(defaultField);
                }
            }

            // 过滤没有配置第三方字段名称的字段，不需要更新
            customFields = customFields.stream()
                    .filter(i -> StringUtils.isNotBlank(i.getCustomData()))
                    .collect(Collectors.toList());
            issue.setCustomFieldList(customFields);
        }
    }

    protected String getProxyPath(String path) {
        return String.format(PROXY_PATH, this.key, this.request.getWorkspaceId(), URLEncoder.encode(path, StandardCharsets.UTF_8));
    }

    protected String getCustomFieldsValuesString(List<PlatformCustomFieldItemDTO> thirdPartCustomField) {
        List fields = new ArrayList();
        thirdPartCustomField.forEach(item -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("customData", item.getCustomData());
            field.put(ID_FIELD_NAME, item.getId());
            field.put("name", item.getName());
            field.put("type", item.getType());
            String defaultValue = item.getDefaultValue();
            if (StringUtils.isNotBlank(defaultValue)) {
                field.put("value", JSON.parseObject(defaultValue));
            }
            fields.add(field);
        });
        return JSON.toJSONString(fields);
    }

    protected List<PlatformCustomFieldItemDTO> syncIssueCustomFieldList(String customFieldsStr, Map issue) {
       return syncIssueCustomFieldList(JSON.parseArray(customFieldsStr, PlatformCustomFieldItemDTO.class), issue);
    }

    protected List<PlatformCustomFieldItemDTO> syncIssueCustomFieldList(List<PlatformCustomFieldItemDTO> customFields, Map issue) {
        Set<String> names = issue.keySet();
        Iterator<PlatformCustomFieldItemDTO> iterator = customFields.iterator();
        while (iterator.hasNext()) {
            PlatformCustomFieldItemDTO item = iterator.next();
            String fieldName = item.getCustomData();
            Object value = issue.get(fieldName);
            if (value != null) {
                if (value instanceof Map) {
                    item.setValue(getSyncJsonParamValue(value));
                } else if (value instanceof List) {
                    List<Object> values = new ArrayList<>();
                    ((List) value).forEach(attr -> {
                        if (attr instanceof Map) {
                            values.add(getSyncJsonParamValue(attr));
                        } else {
                            values.add(attr);
                        }
                    });
                    item.setValue(values);
                } else {
                    item.setValue(value);
                }
            } else if (names.contains(fieldName)) {
                if (StringUtils.isNotBlank(item.getType()) && item.getType().equals(CustomFieldType.CHECKBOX.getValue())) {
                    item.setValue(new ArrayList<>());
                } else {
                    item.setValue(null);
                }
            } else if (!this.isThirdPartTemplate) {
                // 如果不是第三方模板，并且不是需要更新的模板字段，则去掉，否则空值会覆盖原字段的值
                iterator.remove();
            } else {
                try {
                    if (item.getValue() != null) {
                        item.setValue(JSON.parseObject(item.getValue().toString()));
                    }
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            }
        }
        return customFields;
    }

    protected Object getSyncJsonParamValue(Object value) {
        Map valObj = ((Map) value);
        Map child = (Map) valObj.get("child");

        String idValue = Optional.ofNullable(valObj.get(ID_FIELD_NAME))
                .orElse(StringUtils.EMPTY)
                .toString();

        if (child != null) {// 级联框
            return getCascadeValues(idValue, child);
        } else {
            if (StringUtils.isNotBlank(idValue)) {
                return idValue;
            }
            return valObj.get("key");
        }
    }

    protected List<Object> getCascadeValues(String idValue, Map child) {
        List<Object> values = new ArrayList<>();
        if (StringUtils.isNotBlank(idValue)) {
            values.add(idValue);
        }
        if (child.get(ID_FIELD_NAME) != null && StringUtils.isNotBlank(child.get(ID_FIELD_NAME).toString())) {
            values.add(child.get(ID_FIELD_NAME));
        }
        return values;
    }

    public void validateProjectKey(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            MSPluginException.throwException("请在项目设置配置 " + key + "项目ID");
        }
    }

    public List<File> getImageFiles(String input) {
        List<File> files = new ArrayList<>();
        Pattern pattern = Pattern.compile(MARKDOWN_IMAGE_REGULAR);
        if (StringUtils.isBlank(input)) {
            return new ArrayList<>();
        }
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            try {
                String path = matcher.group(2);
                if (!path.contains("/resource/md/get/url") && !path.contains("/resource/md/get/path")) {
                    if (path.contains("/resource/md/get/")) { // 兼容旧数据
                        String name = path.substring(path.indexOf("/resource/md/get/") + 17);
                        files.add(new File(MD_IMAGE_DIR + "/" + name));
                    } else if (path.contains("/resource/md/get")) { // 新数据走这里
                        String name = path.substring(path.indexOf("/resource/md/get") + 26);
                        files.add(new File(MD_IMAGE_DIR + "/" + URLDecoder.decode(name, StandardCharsets.UTF_8.name())));
                    }
                }
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
            }
        }
        return files;
    }

    /**
     * 将html格式的缺陷描述转成ms平台的格式
     *
     * @param htmlDesc
     * @return
     */
    protected String htmlDesc2MsDesc(String htmlDesc) {
        String desc = htmlImg2MsImg(htmlDesc);
        Document document = Jsoup.parse(desc);
        document.outputSettings(new Document.OutputSettings().prettyPrint(false));
        document.select("br").append("\\n");
        document.select("p").prepend("\\n\\n");
        desc = document.html().replaceAll("\\\\n", StringUtils.LF);
        desc = Jsoup.clean(desc, "", Safelist.none(), new Document.OutputSettings().prettyPrint(false));
        return desc.replace("&nbsp;", "");
    }

    protected String htmlImg2MsImg(String input) {
        // <img src="xxx/resource/md/get/a0b19136_中心主题.png"/> ->  ![中心主题.png](/resource/md/get/a0b19136_中心主题.png)
        String regex = "(<img\\s*src=\\\"(.*?)\\\".*?>)";
        Pattern pattern = Pattern.compile(regex);
        if (StringUtils.isBlank(input)) {
            return "";
        }
        Matcher matcher = pattern.matcher(input);
        String result = input;
        while (matcher.find()) {
            String url = matcher.group(2);
            if (url.contains("/resource/md/get/")) { // 兼容旧数据
                String path = url.substring(url.indexOf("/resource/md/get/"));
                String name = path.substring(path.indexOf("/resource/md/get/") + 26);
                String mdLink = "![" + name + "](" + path + ")";
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            } else if(url.contains("/resource/md/get")) { //新数据走这里
                String path = url.substring(url.indexOf("/resource/md/get"));
                String name = path.substring(path.indexOf("/resource/md/get") + 35);
                String mdLink = "![" + name + "](" + path + ")";
                result = matcher.replaceFirst(mdLink);
                matcher = pattern.matcher(result);
            }
        }
        return result;
    }

    protected String msImg2HtmlImg(String input, String endpoint) {
        // ![中心主题.png](/resource/md/get/a0b19136_中心主题.png) -> <img src="xxx/resource/md/get/a0b19136_中心主题.png"/>
        Pattern pattern = Pattern.compile(MARKDOWN_IMAGE_REGULAR);
        if (StringUtils.isBlank(input)) {
            return "";
        }
        Matcher matcher = pattern.matcher(input);
        String result = input;
        while (matcher.find()) {
            String path = matcher.group(2);
            if (endpoint.endsWith("/")) {
                endpoint = endpoint.substring(0, endpoint.length() - 1);
            }
            String format = " <img src=\"%s\"/>";
            if (path.trim().startsWith("http")) {
                path = String.format(format, path);
            } else {
                path = String.format(format, endpoint + path);
            }
            result = matcher.replaceFirst(path);
            matcher = pattern.matcher(result);
        }
        return result;
    }

    protected void addCustomFields(PlatformIssuesUpdateRequest issuesRequest, MultiValueMap<String, Object> paramMap) {
        List<PlatformCustomFieldItemDTO> customFields = issuesRequest.getCustomFieldList();
        if (!CollectionUtils.isEmpty(customFields)) {
            customFields.forEach(item -> {
                if (StringUtils.isNotBlank(item.getCustomData())) {
                    if (item.getValue() instanceof String) {
                        paramMap.add(item.getCustomData(), ((String) item.getValue()).trim());
                    } else {
                        paramMap.add(item.getCustomData(), item.getValue());
                    }
                }
            });
        }
    }

    @Override
    public ResponseEntity proxyForGet(String path, Class responseEntityClazz) {
        return null;
    }

    @Override
    public void syncAllIssues(SyncAllIssuesRequest request) {
        return;
    }

    @Override
    public List<PlatformStatusDTO> getTransitions(String projectConfig, String issueKey) {
        return this.getStatusList(projectConfig);
    }

    @Override
    public void handleDemandUpdate(DemandUpdateRequest request) {
    }

    @Override
    public void handleDemandUpdateBatch(DemandUpdateRequest request) {
    }
}
