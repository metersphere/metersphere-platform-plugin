package io.metersphere.plugin.zentao.enums;

import io.metersphere.plugin.platform.dto.SelectOption;
import io.metersphere.plugin.platform.enums.PlatformCustomFieldType;
import lombok.Getter;

import java.util.List;

/**
 * 由于禅道官网Restful API暂不支持获取模板自定义字段, 估用枚举字段代替
 * 参看版本: 18.10 <a href="https://www.zentao.net/book/api/721.html"></a>
 * Tips: 部分无法获取选项值的字段 例如: {所属模块, 相关任务} && Restful API不支持的字段 例如: {反馈者, 通知邮箱, 操作系统, 浏览器, 抄送给}; 暂时不在默认模板支持.
 * 注意: 所属项目字段为内置处理字段, 不在默认模板枚举中定义
 */
@Getter
public enum ZentaoBugDefaultTemplateField {
	/**
	 * 所属执行
	 */
	EXECUTION("execution", "所属执行", null, false, PlatformCustomFieldType.SELECT.name(), null, null, 1),
	/**
	 * 影响版本
	 */
	OPENED_BUILD("openedBuild", "影响版本", null, true, PlatformCustomFieldType.MULTIPLE_SELECT.name(), null, null, 2),
	/**
	 * 当前指派
	 */
	ASSIGNED_TO("assignedTo", "当前指派", null, true, PlatformCustomFieldType.SELECT.name(), null, null, 3),
	/**
	 * 截止日期
	 */
	DEADLINE("deadline", "截止日期", null, false, PlatformCustomFieldType.DATE.name(), null, null, 4),
	// /**
	//  * 反馈者
	//  */
	// FEEDBACK_BY("feedbackBy", "反馈者", null, false, PlatformCustomFieldType.INPUT.name(), null, null, 5),
	// /**
	//  * 通知邮箱
	//  */
	// NOTIFY_EMAIL("notifyEmail", "通知邮箱", null, false, PlatformCustomFieldType.INPUT.name(), null, null, 6),
	/**
	 * Bug类型
	 */
	BUG_TYPE("type", "Bug类型", null, false, PlatformCustomFieldType.SELECT.name(),
			List.of(new SelectOption("代码错误", "codeerror"), new SelectOption("配置相关", "config"), new SelectOption("安装部署", "install"),
					new SelectOption("安全相关", "security"), new SelectOption("性能问题", "performance"), new SelectOption("标准规范", "standard"),
					new SelectOption("测试脚本", "automation"), new SelectOption("设计缺陷", "designdefect"), new SelectOption("其他", "others")),
			"codeerror", 7),
	/**
	 * 严重程度
	 */
	SEVERITY("severity", "严重程度", null, false, PlatformCustomFieldType.SELECT.name(),
			List.of(new SelectOption("1", "1"), new SelectOption("2", "2"), new SelectOption("3", "3"), new SelectOption("4", "4")),
			"3", 8),
	/**
	 * 优先级
	 */
	PRI("pri", "优先级", null, false, PlatformCustomFieldType.SELECT.name(),
			List.of(new SelectOption("1", "1"), new SelectOption("2", "2"), new SelectOption("3", "3"), new SelectOption("4", "4")),
			"3", 9),
	/**
	 * Bug标题
	 */
	TITLE("title", "Bug标题", null, true, PlatformCustomFieldType.INPUT.name(), null, null, 10),
	/**
	 * 重现步骤
	 */
	STEPS("steps", "重现步骤", null, false, PlatformCustomFieldType.RICH_TEXT.name(), null, "<p style=\\\"\\\">[步骤]<br> </p><p style=\\\"\\\">[结果]<br> </p><p style=\\\"\\\">[期望]<br></p>", 11),
	/**
	 * 相关需求
	 */
	STORY("story", "相关需求", null, false, PlatformCustomFieldType.SELECT.name(), null, null, 12),
	// /**
	//  * 相关任务
	//  */
	// TASK("task", "相关任务", null, false, PlatformCustomFieldType.SELECT.name(), null, null, 13),
	/**
	 * 关键词
	 */
	KEYWORDS("keywords", "关键词", null, false, PlatformCustomFieldType.INPUT.name(), null, null, 14);

	private final String id;

	private final String name;

	private final String placeHolder;

	private final Boolean required;

	private final String type;

	private final List<SelectOption> options;

	private final String defaultValue;

	private final Integer sort;

	ZentaoBugDefaultTemplateField(String id, String name, String placeHolder, Boolean required, String type, List<SelectOption> options,
								  String defaultValue, Integer sort) {
		this.id = id;
		this.name = name;
		this.placeHolder = placeHolder;
		this.required = required;
		this.type = type;
		this.options = options;
		this.defaultValue = defaultValue;
		this.sort = sort;
	}
}
