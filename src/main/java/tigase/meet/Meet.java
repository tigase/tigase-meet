/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.meet.janus.JanusConnection;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;

import java.util.concurrent.CompletableFuture;

public class Meet {

	private final Object roomId;
	private final JanusConnection janusConnection;

	public Meet(JanusConnection janusConnection, Object roomId) {
		this.janusConnection = janusConnection;
		this.roomId = roomId;
	}

	public CompletableFuture<Participation> join() {
		return janusConnection.createSession()
				.thenCompose(session -> session.attachPlugin(JanusVideoRoomPlugin.class)
						.thenCompose(plugin -> plugin.createPublisher(roomId))
						.thenCombineAsync(session.attachPlugin(JanusVideoRoomPlugin.class)
												  .thenApply(plugin -> plugin.createSubscriber(roomId)),
										  (publisher, subscriber) -> new Participation(publisher, subscriber)));
	}

}
