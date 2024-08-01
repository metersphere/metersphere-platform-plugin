package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestExecutionStoriesResponse extends ZentaoRestBaseResponse {

	private List<Story> stories;

	@Data
	public static class Story {
		private String id;
		private String title;
	}
}
