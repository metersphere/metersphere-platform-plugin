package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraIssueType {
    private String id;
    private String name;
    private String untranslatedName;
}
