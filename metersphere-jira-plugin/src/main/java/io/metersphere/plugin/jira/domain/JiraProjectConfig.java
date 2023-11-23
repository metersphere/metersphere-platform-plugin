package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraProjectConfig {
    private String jiraBugTypeId;
    private String jiraDemandTypeId;
    private boolean thirdPartTemplate;
    private String jiraKey;
}
