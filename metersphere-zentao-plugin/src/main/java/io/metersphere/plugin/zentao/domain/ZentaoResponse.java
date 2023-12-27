package io.metersphere.plugin.zentao.domain;

import lombok.Data;

@Data
public class ZentaoResponse {
    private String status;
    private String md5;
    private String data;
}
