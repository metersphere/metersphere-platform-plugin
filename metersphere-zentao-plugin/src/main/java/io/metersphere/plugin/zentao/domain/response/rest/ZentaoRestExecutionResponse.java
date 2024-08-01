package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestExecutionResponse extends ZentaoRestBaseResponse {

	private List<Execution> executions;

	@Data
	public static class Execution {
		private String id;
		private String name;
	}
}
