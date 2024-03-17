package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class ZentaoRestBuildResponse {

	private Integer total;

	private List<Build> builds;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Build {
		private String id;
		private String name;
	}
}
