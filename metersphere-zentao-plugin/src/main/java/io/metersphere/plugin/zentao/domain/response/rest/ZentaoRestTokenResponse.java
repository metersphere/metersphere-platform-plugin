package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class ZentaoRestTokenResponse {

	private String token;
}
