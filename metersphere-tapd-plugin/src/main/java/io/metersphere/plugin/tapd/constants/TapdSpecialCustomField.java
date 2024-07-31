package io.metersphere.plugin.tapd.constants;

import java.util.List;

public class TapdSpecialCustomField {

	public static final String USER_CHOOSER = "user_chooser";
	public static final String MIX_CHOOSER = "mix_chooser";

	public static List<String> getSpecialFields() {
		return List.of(USER_CHOOSER, MIX_CHOOSER);
	}
}
