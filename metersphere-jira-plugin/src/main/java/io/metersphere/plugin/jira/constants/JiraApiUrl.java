package io.metersphere.plugin.jira.constants;

public class JiraApiUrl {

    public static final String CREATE_META = "/issue/createmeta?projectKeys={1}&issuetypeIds={2}&expand=projects.issuetypes.fields";

    public static final String GET_ISSUE_TYPE = "/issuetype/project?projectId={0}";
}
