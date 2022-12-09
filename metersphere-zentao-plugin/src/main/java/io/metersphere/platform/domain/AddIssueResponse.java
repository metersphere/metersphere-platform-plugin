package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddIssueResponse extends ZentaoResponse {
    @Getter
    @Setter
    public static class Issue {
        private String status;
        private String id;
    }
}
