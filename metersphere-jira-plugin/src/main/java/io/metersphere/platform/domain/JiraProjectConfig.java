package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraProjectConfig {
    private String jiraIssueTypeId;
    private String jiraStoryTypeId;
    private boolean thirdPartTemplate;
    private String jiraKey;
}
