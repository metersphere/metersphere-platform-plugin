package io.metersphere.plugin.zentao.domain.response.rest;

import lombok.Data;

@Data
public class ZentaoRestBaseResponse {

	private int page;

	private int total;

	private int limit;
}
