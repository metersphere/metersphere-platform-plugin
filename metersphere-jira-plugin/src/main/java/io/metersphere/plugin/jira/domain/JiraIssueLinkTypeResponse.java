package io.metersphere.plugin.jira.domain;

import lombok.Data;

import java.util.List;

@Data
public class JiraIssueLinkTypeResponse {

    private List<IssueLinkType> issueLinkTypes;

    @Data
    public static class IssueLinkType {
        private String id;
        private String name;
        private String inward;
        private String outward;
    }
}
