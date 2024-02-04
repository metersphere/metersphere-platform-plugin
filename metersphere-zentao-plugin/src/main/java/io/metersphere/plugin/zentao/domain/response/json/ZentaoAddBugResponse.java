package io.metersphere.plugin.zentao.domain.response.json;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoAddBugResponse extends ZentaoResponse {

	@Data
	public static class Bug {
		private String status;
		private String id;
	}
}
