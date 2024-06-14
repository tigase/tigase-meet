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
package tigase.meet.janus;

import java.util.Map;

public class JanusException extends RuntimeException {

	private final int code;
	private final String reason;

	public JanusException(int code, String reason) {
		super(String.valueOf(code) + " - " + reason);
		this.code = code;
		this.reason = reason;
	}

	public JanusException() {
		this(-1, "Unknown exception");
	}

	public JanusException(Map<String, Object> error) {
		this((Integer) error.get("code"), (String) error.get("reason"));
	}

	public int getCode() {
		return code;
	}
}
