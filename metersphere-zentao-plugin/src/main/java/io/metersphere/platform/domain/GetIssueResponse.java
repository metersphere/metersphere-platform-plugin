package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetIssueResponse extends ZentaoResponse {
    @Getter
    @Setter
    public static class Issue {
        private String id;
        private String title;
        private String steps;
        private String status;
        private String openedBy;
//        private String openedDate;
        private String deleted;
//        private String product;
//        private String openedBuild;
//        private String assignedTo;
    }
}
