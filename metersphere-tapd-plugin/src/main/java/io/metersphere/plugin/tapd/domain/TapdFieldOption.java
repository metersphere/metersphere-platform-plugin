package io.metersphere.plugin.tapd.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TapdFieldOption {

	private String text;

	private String value;

	private List<TapdFieldOption> children;
}
