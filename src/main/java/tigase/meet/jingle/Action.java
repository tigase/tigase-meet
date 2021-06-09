/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Action {
	sessionInitiate,
	sessionAccept,
	sessionInfo,
	sessionTerminate,

	contentAccept,
	contentAdd,
	contentModify,
	contentReject,
	contentRemove,

	transportInfo;

	public static Map<String, Action> actions = EnumSet.allOf(Action.class)
			.stream()
			.collect(Collectors.toMap(Action::getValue, Function.identity()));

	public static Action from(String value) {
		return actions.get(value);
	}

	private final String value;

	Action() {
		this.value = name().replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
	}

	public String getValue() {
		return value;
	}

}
