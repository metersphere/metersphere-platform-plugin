package io.metersphere.platform.domain;

import io.metersphere.base.domain.IssuesWithBLOBs;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
public class SyncAllIssuesResult extends SyncIssuesResult {
    private List<IssuesWithBLOBs> updateIssues = new ArrayList<>();
    private Map<String, List<PlatformAttachment>> attachmentMap = new HashMap<>();
    /**
     * 保存当前查询到的缺陷的平台ID
     */
    private List<String> allIds = new ArrayList<>();
}
