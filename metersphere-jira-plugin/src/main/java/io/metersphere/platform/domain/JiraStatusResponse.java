package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class JiraStatusResponse {
    private String self;
    private String name;
    private String id;
    private String subtask;
    private List<Statuses> statuses;

    @Getter
    @Setter
    public static class Statuses {
        private String self;
        private String description;
        private String iconUrl;
        private String name;
        private String untranslatedName;
        private String id;
        private StatusCategory statusCategory;
    }

    @Getter
    @Setter
    public static class StatusCategory {
        private String self;
        private int id;
        private String key;
        private String colorName;
        private String name;
    }
}
