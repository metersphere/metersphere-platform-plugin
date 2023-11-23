package io.metersphere.plugin.jira.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class JiraAllowedValueOption {

    private String text;

    private String value;

    private List<JiraAllowedValueOption> children;
}
