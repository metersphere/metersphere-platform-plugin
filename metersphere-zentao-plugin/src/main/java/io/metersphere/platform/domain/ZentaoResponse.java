package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZentaoResponse {
    private String status;
    private String md5;
    private String data;
}
