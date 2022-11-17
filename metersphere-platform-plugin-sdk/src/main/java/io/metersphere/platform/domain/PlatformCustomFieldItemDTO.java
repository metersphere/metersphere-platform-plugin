package io.metersphere.platform.domain;

import io.metersphere.base.domain.CustomField;
import lombok.Data;

@Data
public class PlatformCustomFieldItemDTO extends CustomField {
    private Object value;
    private String key;
    private String customData;
    private Boolean required;
    private String defaultValue;
}
