package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.io.File;

@Getter
@Setter
public class SyncIssuesAttachmentRequest {
    /**
     * 平台 ID
     */
    private String platformId;
    /**
     * 需要同步的附件
     */
    private File file;
    /**
     * 操作类型是更新还是删除
     * 参考 AttachmentSyncType
     */
    private String syncType;
}
