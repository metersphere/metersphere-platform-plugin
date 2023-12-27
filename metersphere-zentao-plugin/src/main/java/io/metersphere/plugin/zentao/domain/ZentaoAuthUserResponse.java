package io.metersphere.plugin.zentao.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoAuthUserResponse {
    private String status;
    private User user;
    private String reason;

    @Data
    public static class User {
        private String id;
        private String account;
    }
}
