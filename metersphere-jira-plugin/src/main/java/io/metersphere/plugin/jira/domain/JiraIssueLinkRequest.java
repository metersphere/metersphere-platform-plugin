package io.metersphere.plugin.jira.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraIssueLinkRequest {

    private JiraIssueLinkType type;
    private JiraIssueLinkKey inwardIssue;
    private JiraIssueLinkKey outwardIssue;

    @Data
    @Builder
    public static class JiraIssueLinkType {
        private String id;
    }

    @Data
    @Builder
    public static class JiraIssueLinkKey {
        private String key;
    }
}
