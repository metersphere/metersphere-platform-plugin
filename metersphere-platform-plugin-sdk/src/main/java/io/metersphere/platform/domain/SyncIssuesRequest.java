package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class SyncIssuesRequest {
    private String projectConfig;
    private String defaultCustomFields;;
    private List<PlatformIssuesDTO> issues;
}
