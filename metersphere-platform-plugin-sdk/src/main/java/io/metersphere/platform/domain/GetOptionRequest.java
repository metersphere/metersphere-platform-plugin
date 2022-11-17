package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetOptionRequest {
    private String projectConfig;
    private String optionMethod;
}
