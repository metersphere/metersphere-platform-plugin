package io.metersphere.plugin.zentao.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoSessionResponse extends ZentaoResponse {

    @Data
    public static class Session {
        private String sessionID;
    }
}
