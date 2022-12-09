package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetUserResponse {
    private String status;
    private User user;
    private String reason;

    @Getter
    @Setter
    public static class User {
        private String id;
        private String account;
    }
}
