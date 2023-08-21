package io.metersphere.platform.domain;

import lombok.*;

@Getter
@Setter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SelectOption {

    private String text;
    private String value;
}
