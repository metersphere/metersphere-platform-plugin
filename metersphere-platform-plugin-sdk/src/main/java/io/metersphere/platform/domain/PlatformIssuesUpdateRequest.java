package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class PlatformIssuesUpdateRequest extends PlatformIssuesDTO {
    private String userPlatformUserConfig;
    private String projectConfig;
    private Set<String> msAttachmentNames;
    private PlatformStatusDTO transitions;
}
