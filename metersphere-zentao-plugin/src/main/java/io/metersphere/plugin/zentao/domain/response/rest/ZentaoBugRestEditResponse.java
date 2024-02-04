package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;

import java.util.Date;

@Data
public class ZentaoBugRestEditResponse {

	private String id;
	/**
	 * BUG标题
	 */
	private String title;
	/**
	 * 状态
	 */
	private String status;
	/**
	 * 重现步骤
	 */
	private String steps;
	/**
	 * 所属模块
	 */
	private int module;
	/**
	 * 所属项目
	 */
	private int project;
	/**
	 * 所属执行
	 */
	private int execution;
	/**
	 * 影响版本
	 */
	private String openedBuild;
	/**
	 * 创建人
	 */
	private User openedBy;
	/**
	 * 创建时间 YYYY-MM-DDTHH:mm:ssZ
	 */
	private String openedDate;
	/**
	 * 截止日期
	 */
	private Date deadline;
	/**
	 * 指派给
	 */
	private User assignedTo;
	/**
	 * 指派时间 YYYY-MM-DDTHH:mm:ssZ
	 */
	private String assignedDate;
	/**
	 * 缺陷类型
	 */
	private String type;
	/**
	 * 操作系统
	 */
	private String os;
	/**
	 * 浏览器
	 */
	private String browser;
	/**
	 * 严重程度
	 */
	private Integer severity;
	/**
	 * 优先级
	 */
	private Integer pri;
	/**
	 * 相关任务
	 */
	private Integer task;
	/**
	 * 相关需求
	 */
	private Integer story;

	@Data
	public static class User {
		private String id;
		private String account;
		private String realname;
	}
}
