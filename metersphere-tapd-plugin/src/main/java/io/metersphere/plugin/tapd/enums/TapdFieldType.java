package io.metersphere.plugin.tapd.enums;

import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public enum TapdFieldType {

	/**
	 * INPUT
	 */
	INPUT(Set.of("text", "input"), PlatformCustomFieldType.INPUT.name()),
	/**
	 * 文本框
	 */
	TEXTAREA(Set.of("textarea"), PlatformCustomFieldType.TEXTAREA.name()),
	/**
	 * 单选复选框
	 */
	RADIO_BUTTON(Set.of("radio"), PlatformCustomFieldType.RADIO.name()),
	/**
	 * 多选复选框
	 */
	CHECKBOX(Set.of("checkbox"), PlatformCustomFieldType.CHECKBOX.name()),
	/**
	 * 浮点数
	 */
	FLOAT(Set.of("float"), PlatformCustomFieldType.FLOAT.name()),
	/**
	 * 整型
	 */
	INT(Set.of("integer"), PlatformCustomFieldType.INT.name()),
	/**
	 * 日期(特殊自定义字段类型: dateinput)
	 */
	DATE(Set.of("dateinput"), PlatformCustomFieldType.DATE.name()),
	/**
	 * 日期时间
	 */
	DATETIME(Set.of("datetime"), PlatformCustomFieldType.DATETIME.name()),
	/**
	 * SELECT
	 */
	SELECT(Set.of("select"), PlatformCustomFieldType.SELECT.name()),
	/**
	 * MULTIPLE_SELECT
	 */
	MULTIPLE_SELECT(Set.of("multi_select"), PlatformCustomFieldType.MULTIPLE_SELECT.name()),
	/**
	 * 级联选择(后续如果需特殊处理选项值, 可移除)
	 */
	CASCADING_SELECT(Set.of("cascade_checkbox", "cascade_radio"), PlatformCustomFieldType.CASCADER.name()),
	/**
	 * 富文本
	 */
	RICH_TEXT(Set.of("rich_edit"), PlatformCustomFieldType.RICH_TEXT.name());

	private final Set<String> tapdFieldTypeSet;

	private final String customFieldType;

	TapdFieldType(Set<String> tapdFieldTypeSet, String customFieldType) {
		this.tapdFieldTypeSet = tapdFieldTypeSet;
		this.customFieldType = customFieldType;
	}

	public static String mappingTapdHtmlType(String htmlType) {
		// 这里的类型匹配为正则最佳匹配;
		// 例如tapdFieldType为"fixVersion", 存在枚举A("version", "A"), 枚举B("fixVersion", "B"), 则会匹配到B, 并返回B的类型
		List<Set<String>> typeSetList = Arrays.stream(TapdFieldType.values()).map(TapdFieldType::getTapdFieldTypeSet).toList();
		Set<String> keys = typeSetList.stream().flatMap(Set::stream).collect(Collectors.toSet());
		Set<String> matchKeys = keys.stream().filter(htmlType::contains).collect(Collectors.toSet());
		Optional<String> matchOptional = matchKeys.stream().max(Comparator.comparingInt(String::length));
		if (matchOptional.isPresent()) {
			String bestMatchKey = matchOptional.get();
			return getCustomFieldType(bestMatchKey);
		} else {
			return null;
		}
	}

	public static String getCustomFieldType(String key) {
		return Arrays.stream(TapdFieldType.values())
				.filter(fieldType -> fieldType.getTapdFieldTypeSet().contains(key))
				.findFirst()
				.map(TapdFieldType::getCustomFieldType)
				.orElse(null);
	}
}
