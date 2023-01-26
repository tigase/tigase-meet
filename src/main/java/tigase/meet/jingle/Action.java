/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
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
		this.value = name().replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
	}

	public String getValue() {
		return value;
	}

}
