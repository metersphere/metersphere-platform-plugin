package io.metersphere.platform.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JiraSprintResponse {
    private List<JiraSuggestions> suggestions;
    private List<JiraSuggestions> allMatches;
}
