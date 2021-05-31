/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
}
