package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class JiraIssue {
    private String expand;
    private String id;
    private String self;
    private String key;
    private Map<String, Object> fields;
}
