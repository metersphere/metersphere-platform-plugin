package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class PlatformIssuesUpdateRequest extends PlatformIssuesDTO {
    private String userPlatformInfo;
    private String projectConfig;
    private Set<String> msAttachmentNames;
    private PlatformStatusDTO transitions;
}
