package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Getter
@Setter
public class SyncIssuesAttachmentRequest {
    private String platformId;
    private File file;
    private String syncType;
}
