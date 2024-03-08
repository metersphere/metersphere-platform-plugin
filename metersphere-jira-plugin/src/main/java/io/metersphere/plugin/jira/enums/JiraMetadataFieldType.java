package io.metersphere.plugin.jira.enums;

import io.metersphere.plugin.jira.constants.JiraMetadataField;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 枚举用来映射JiraField的字段类型到Metersphere的字段类型
 * 多个JiraField的字段类型最终映射唯一类型
 */
@Getter
public enum JiraMetadataFieldType {
	/**
	 * INPUT(特殊自定义字段类型: url)
	 */
	INPUT(Set.of("textfield", "url", JiraMetadataField.SUMMARY_FIELD_NAME), PlatformCustomFieldType.INPUT.name()),
	/**
	 * MULTIPLE_INPUT
	 */
	MULTIPLE_INPUT(Set.of("labels"), PlatformCustomFieldType.MULTIPLE_INPUT.name()),
	/**
	 * 文本框
	 */
	TEXTAREA(Set.of("textarea"), PlatformCustomFieldType.TEXTAREA.name()),
	/**
	 * 单选复选框
	 */
	RADIO_BUTTON(Set.of("radiobuttons"), PlatformCustomFieldType.RADIO.name()),
	/**
	 * 浮点数
	 */
	FLOAT(Set.of("float"), PlatformCustomFieldType.FLOAT.name()),
	/**
	 * 日期(特殊自定义字段类型: datepicker)
	 */
	DATE(Set.of("date", "datepicker"), PlatformCustomFieldType.DATE.name()),
	/**
	 * 日期时间
	 */
	DATETIME(Set.of("datetime"), PlatformCustomFieldType.DATETIME.name()),
	/**
	 * SELECT(特殊自定义字段类型: version)
	 */
	SELECT(Set.of("version", "select", "priority"), PlatformCustomFieldType.SELECT.name()),
	/**
	 * MULTIPLE_SELECT(特殊自定义字段类型: multiversion)
	 */
	MULTIPLE_SELECT(Set.of("multiselect", "multiversion", "components", "fixVersions", "versions"), PlatformCustomFieldType.MULTIPLE_SELECT.name()),
	/**
	 * 级联选择(后续如果需特殊处理选项值, 可移除)
	 */
	CASCADING_SELECT(Set.of("cascadingselect"), PlatformCustomFieldType.CASCADER.name()),
	/**
	 * 富文本
	 */
	RICH_TEXT(Set.of(JiraMetadataField.DESCRIPTION_FIELD_NAME, JiraMetadataField.ENVIRONMENT_FIELD_NAME), PlatformCustomFieldType.RICH_TEXT.name());

	private final Set<String> jiraFieldTypeSet;

	private final String customFieldType;

	JiraMetadataFieldType(Set<String> jiraFieldTypeSet, String customFieldType) {
		this.jiraFieldTypeSet = jiraFieldTypeSet;
		this.customFieldType = customFieldType;
	}

	public static String mappingJiraCustomType(String jiraType) {
		// 这里的类型匹配为正则最佳匹配;
		// 例如jiraType为"fixVersion", 存在枚举A("version", "A"), 枚举B("fixVersion", "B"), 则会匹配到B, 并返回B的类型
		List<Set<String>> typeSetList = Arrays.stream(JiraMetadataFieldType.values()).map(JiraMetadataFieldType::getJiraFieldTypeSet).toList();
		Set<String> keys = typeSetList.stream().flatMap(Set::stream).collect(Collectors.toSet());
		Set<String> matchKeys = keys.stream().filter(jiraType::contains).collect(Collectors.toSet());
		Optional<String> matchOptional = matchKeys.stream().max(Comparator.comparingInt(String::length));
		if (matchOptional.isPresent()) {
			String bestMatchKey = matchOptional.get();
			return getCustomFieldType(bestMatchKey);
		} else {
			return null;
		}
	}

	public static String getCustomFieldType(String key) {
		return Arrays.stream(JiraMetadataFieldType.values())
				.filter(jiraMetadataFieldType -> jiraMetadataFieldType.getJiraFieldTypeSet().contains(key))
				.findFirst()
				.map(JiraMetadataFieldType::getCustomFieldType)
				.orElse(null);
	}
}
