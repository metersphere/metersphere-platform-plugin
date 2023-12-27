package io.metersphere.plugin.zentao.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoAddBugResponse extends ZentaoResponse {

    @Data
    public static class Bug {
        private String status;
        private String id;
    }
}
