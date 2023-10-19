package io.metersphere.plugin.jira.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JiraSprint {
    private String name;
    private Integer id;
    private String boardName;
}
