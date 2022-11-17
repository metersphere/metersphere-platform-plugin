package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectOption {
    public SelectOption(String text, String value) {
        this.text = text;
        this.value = value;
    }

    private String text;
    private String value;
}
