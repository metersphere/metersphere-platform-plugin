package io.metersphere.plugin.jira.constants;

/**
 * 支持高级搜索的Jira字段, 需实现对应的搜索方法
 * 抽象出方法名为常量, 返回给前端, 前端根据方法名调用对应的搜索方法
 */
public class JiraMetadataFieldSearchMethod {

    public static final String GET_USER = "getUserSearchOptions";
    public static final String GET_ASSIGNABLE = "getAssignableOptions";
    public static final String GET_ISSUE_LINK = "getIssueLinkOptions";
    public static final String GET_SPRINT = "getSprintOptions";
}
