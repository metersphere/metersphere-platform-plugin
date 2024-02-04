package io.metersphere.plugin.zentao.domain.request.rest;

import java.util.List;

/**
 * @since 2024-02-02
 * 基于18.10的V1版本API文档
 * 注意: 18.10的API暂时不支持{os, browser}字段, 需要改代码文件来支持 <a href="https://www.zentao.net/book/api/721.html"></a>
 */
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

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public String getModule() {
		return module;
	}

	public void setModule(String module) {
		this.module = module;
	}

	public String getExecution() {
		return execution;
	}

	public void setExecution(String execution) {
		this.execution = execution;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getSeverity() {
		return severity;
	}

	public void setSeverity(String severity) {
		this.severity = severity;
	}

	public String getPri() {
		return pri;
	}

	public void setPri(String pri) {
		this.pri = pri;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSteps() {
		return steps;
	}

	public void setSteps(String steps) {
		this.steps = steps;
	}

	public String getTask() {
		return task;
	}

	public void setTask(String task) {
		this.task = task;
	}

	public String getStory() {
		return story;
	}

	public void setStory(String story) {
		this.story = story;
	}

	public String getDeadline() {
		return deadline;
	}

	public void setDeadline(String deadline) {
		this.deadline = deadline;
	}

	public List<String> getOpenedBuild() {
		return openedBuild;
	}

	public void setOpenedBuild(List<String> openedBuild) {
		this.openedBuild = openedBuild;
	}

	public String getAssignedTo() {
		return assignedTo;
	}

	public void setAssignedTo(String assignedTo) {
		this.assignedTo = assignedTo;
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}
}
