package io.metersphere.plugin.zentao.domain.request.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @since 2024-02-02
 * 基于18.10的V1版本API文档
 * 注意: 18.10的API暂时不支持{os, browser}字段, 需要改代码文件来支持 <a href="https://www.zentao.net/book/api/721.html"></a>
 */
@Setter
@Getter
public class ZentaoRestBugEditRequest {

	/**
	 * 所属分支
	 */
	private String branch;
	/**
	 * 所属模块
	 */
	private String module;
	/**
	 * 所属执行
	 */
	private String execution;
	/**
	 * 缺陷标题
	 */
	private String title;
	/**
	 * 缺陷关键字
	 */
	private String keywords;
	/**
	 * 严重程度
	 */
	private String severity;
	/**
	 * 优先级
	 */
	private String pri;
	/**
	 * 缺陷类型  (codeerror 代码错误 |config 配置相关|install 安装部署|security 安全相关|performance 性能问题|standard 标准规范|automation|测试脚本|designdefect 设计缺陷|others 其他)
	 */
	private String type;
	/**
	 * 重现步骤
	 */
	private String steps;
	/**
	 * 相关任务
	 */
	private String task;
	/**
	 * 相关需求
	 */
	private String story;
	/**
	 * 截止日期
	 */
	private String deadline;
	/**
	 * 影响版本
	 */
	private List<String> openedBuild;
	/**
	 * 当前指派给
	 */
	private String assignedTo;
	/**
	 * 所属项目
	 */
	private String project;

}
