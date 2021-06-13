/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.utils;

import java.util.ArrayDeque;

public class DelayedRunQueue {

	private ArrayDeque<Runnable> queue = new ArrayDeque<>();
	private boolean delay = true;

	public synchronized void offer(Runnable runnable) {
		if (delay) {
			this.queue.offer(runnable);
		} else {
			runnable.run();
		}
	}

	public synchronized void delayFinished() {
		this.delay = false;
		Runnable run;
		while ((run = queue.poll()) != null) {
			run.run();
		}
	}

}
