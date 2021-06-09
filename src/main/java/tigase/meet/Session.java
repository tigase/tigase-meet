/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

public class Session {

	private final String id;
	private State state = State.NEW;

	public Session(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public boolean isActive() {
		return state == State.ACTIVE;
	}

	public synchronized void setState(State newState) {
		this.state = newState;
	}

	public enum State {
		NEW, ACTIVE, TERMINATED
	}

}
