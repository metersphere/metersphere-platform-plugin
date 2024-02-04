package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestPlanResponse extends ZentaoRestBaseResponse {

	private List<Plan> plans;

	@Data
	public static class Plan {
		private String id;
		private String title;
		private List<Plan> children;
	}
}
