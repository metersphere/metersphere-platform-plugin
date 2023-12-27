package io.metersphere.plugin.zentao.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoCreateMetaDataResponse extends ZentaoResponse {


    @Data
    public static class MetaData {
        private String title;
        private Map<String, Object> users;
        private Map<String, Object> customFields;
        private Map<String, Object> builds;
    }
}
