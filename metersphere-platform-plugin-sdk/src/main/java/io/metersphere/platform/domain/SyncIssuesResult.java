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
public class SyncIssuesResult {
    private List<IssuesWithBLOBs> updateIssues = new ArrayList<>();
    private List<IssuesWithBLOBs> addIssues = new ArrayList<>();
    private  Map<String, List<PlatformAttachment>> attachmentMap = new HashMap<>();
    private List<String> deleteIssuesIds = new ArrayList<>();
    private Map<String, String> customFieldMap = new HashMap<>();

}
