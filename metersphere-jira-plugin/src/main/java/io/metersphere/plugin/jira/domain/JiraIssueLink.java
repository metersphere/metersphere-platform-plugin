package io.metersphere.plugin.jira.domain;

import lombok.Data;

@Data
public class JiraIssueLink {

    private String id;

    private String key;

    private String summary;
}
