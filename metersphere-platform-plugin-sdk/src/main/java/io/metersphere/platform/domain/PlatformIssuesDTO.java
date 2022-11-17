package io.metersphere.platform.domain;

import io.metersphere.base.domain.IssuesWithBLOBs;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PlatformIssuesDTO extends IssuesWithBLOBs {
    private List<PlatformCustomFieldItemDTO> customFieldList;
    private List<PlatformAttachment> attachments = new ArrayList<>();
}
