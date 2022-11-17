package io.metersphere.platform.api;

import im.metersphere.plugin.exception.MSPluginException;
import im.metersphere.plugin.utils.JSON;
import im.metersphere.plugin.utils.LogUtil;
import io.metersphere.platform.constants.CustomFieldType;
import io.metersphere.platform.domain.PlatformCustomFieldItemDTO;
import io.metersphere.platform.domain.PlatformIssuesDTO;
import io.metersphere.platform.domain.PlatformRequest;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractPlatform implements Platform {
    protected String key;
    protected PlatformRequest request;
    protected boolean isThirdPartTemplate;
    protected String defaultCustomFields;

    public static final String MD_IMAGE_DIR = "/opt/metersphere/data/image/markdown";

    public <T> T getIntegrationConfig(Class<T> clazz) {
        String config = request.getIntegrationConfig();
        if (StringUtils.isBlank(config)) {
            MSPluginException.throwException("配置为空");
        }
        return JSON.parseObject(config, clazz);
    }

    @Override
    public byte[] getAttachmentContent(String fileKey) {
        return null;
    }

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
            issue.setCustomFields(JSON.toJSONString(customFields));
        }
    }

    protected String getCustomFieldsValuesString(List<PlatformCustomFieldItemDTO> thirdPartCustomField) {
        List fields = new ArrayList();
        thirdPartCustomField.forEach(item -> {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("customData", item.getCustomData());
            field.put("id", item.getId());
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
        List<PlatformCustomFieldItemDTO> customFields = JSON.parseArray(customFieldsStr, PlatformCustomFieldItemDTO.class);
        Set<String> names = issue.keySet();
        customFields.forEach(item -> {
            String fieldName = item.getCustomData();
            Object value = issue.get(fieldName);
            if (value != null) {
                if (value instanceof Map) {
                    item.setValue(getSyncJsonParamValue(value));
                    if (StringUtils.equals(fieldName, "assignee")) {
                        item.setValue(((Map) value).get("displayName"));
                    } else {
                        item.setValue(getSyncJsonParamValue(value));
                    }
                } else if (value instanceof List) {
                    // Sprint 是单选 同步回来是 JSONArray
                    if (StringUtils.equals(item.getType(), "select")) {
                        if (((List) value).size() > 0) {
                            Object o = ((List) value).get(0);
                            if (o instanceof Map) {
                                item.setValue(getSyncJsonParamValue(o));
                            }
                        }
                    } else {
                        List<Object> values = new ArrayList<>();
                        ((List) value).forEach(attr -> {
                            if (attr instanceof Map) {
                                values.add(getSyncJsonParamValue(attr));
                            } else {
                                values.add(attr);
                            }
                        });
                        item.setValue(values);
                    }
                } else {
                    item.setValue(value);
                }
            } else if (names.contains(fieldName)) {
                if (item.getType().equals(CustomFieldType.CHECKBOX.getValue())) {
                    item.setValue(new ArrayList<>());
                } else {
                    item.setValue(null);
                }
            } else {
                try {
                    if (item.getValue() != null) {
                        item.setValue(JSON.parseObject(item.getValue().toString()));
                    }
                } catch (Exception e) {
                    LogUtil.error(e);
                }
            }
        });
        return customFields;
    }

    protected Object getSyncJsonParamValue(Object value) {
        Map valObj = ((Map) value);
        String accountId = Optional.ofNullable(valObj.get("accountId")).orElse("").toString();
        Map child = (Map) valObj.get("child");
        if (child != null) {// 级联框
            List<Object> values = new ArrayList<>();
            String id = Optional.ofNullable(valObj.get("id")).orElse("").toString();
            if (StringUtils.isNotBlank(id)) {
                values.add(valObj.get("id"));
            }
            if (StringUtils.isNotBlank(id)) {
                values.add(child.get("id"));
            }
            return values;
        } else if (StringUtils.isNotBlank(accountId) && isThirdPartTemplate) {
            // 用户选择框
            return accountId;
        } else {
            String id = Optional.ofNullable(valObj.get("id")).orElse("").toString();
            if (StringUtils.isNotBlank(id)) {
                return valObj.get("id");
            } else {
                return valObj.get("key");
            }
        }
    }

    public void validateProjectKey(String projectId) {
        if (StringUtils.isBlank(projectId)) {
            MSPluginException.throwException("请在项目设置配置 " + key + "项目ID");
        }
    }

    public List<File> getImageFiles(String input) {
        List<File> files = new ArrayList<>();
        String regex = "(\\!\\[.*?\\]\\((.*?)\\))";
        Pattern pattern = Pattern.compile(regex);
        if (StringUtils.isBlank(input)) {
            return new ArrayList<>();
        }
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            try {
                String path = matcher.group(2);
                if (!path.contains("/resource/md/get/url")) {
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
}
