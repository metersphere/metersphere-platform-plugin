package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetSessionResponse extends ZentaoResponse {
    @Getter
    @Setter
    public static class Session {
//        private String title;
//        private String sessionName;
        private String sessionID;
//        private int rand;
//        private String pager;
    }
}
