package io.metersphere.platform.domain;

import lombok.Data;

import java.util.List;

@Data
public class JiraIssueLinkResponse {

    private List<IssueLink> sections;

    @Data
    public static class IssueLink{
        private List<JiraIssueLink> issues;
    }
}
