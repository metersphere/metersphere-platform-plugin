package io.metersphere.plugin.tapd.domain;

import lombok.Data;

@Data
public class TapdTemplateField {

	private String id;
	private String field;
	private String value;
	private String required;
	private Integer sort;
}
