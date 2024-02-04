package io.metersphere.plugin.zentao.domain.response.json;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoSessionResponse extends ZentaoResponse {

	@Data
	public static class Session {
		private String sessionID;
	}
}
