package io.metersphere.plugin.jira.constants;

import java.util.Arrays;
import java.util.List;

public class JiraMetadataSpecialSystemField {

    public static final String TIME_TRACKING = "timetracking";
    public static final String ASSIGNEE = "assignee";
    public static final String REPORTER = "reporter";
    public static final String ISSUE_LINKS = "issuelinks";

    public static List<String> getSpecialFields() {
        return Arrays.asList(TIME_TRACKING, ASSIGNEE, REPORTER, ISSUE_LINKS);
    }
}
