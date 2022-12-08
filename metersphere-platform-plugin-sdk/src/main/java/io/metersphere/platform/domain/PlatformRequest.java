package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformRequest {
    private String integrationConfig;
    private String workspaceId;
    private String userPlatformInfo;
}
