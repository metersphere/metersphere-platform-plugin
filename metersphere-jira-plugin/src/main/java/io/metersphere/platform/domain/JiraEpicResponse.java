package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraEpicResponse {
    private List<EpicLists> epicLists;
    private int total;

    @Getter
    @Setter
    public static class EpicLists {
        private List<JiraEpic> epicNames;
    }
}
