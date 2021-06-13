/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.meet.janus.JanusConnection;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.xmpp.jid.BareJID;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public abstract class AbstractMeet<T extends AbstractParticipation> {

	public static final BareJID ALLOW_EVERYONE = BareJID.bareJIDInstanceNS("*");

	private final Object roomId;
	private final JanusConnection janusConnection;

	public AbstractMeet(JanusConnection janusConnection, Object roomId) {
		this.janusConnection = janusConnection;
		this.roomId = roomId;
	}

	public Object getRoomId() {
		return roomId;
	}

	public JanusConnection getJanusConnection() {
		return janusConnection;
	}

	protected CompletableFuture<T> join(BiFunction<LocalPublisher, LocalSubscriber, T> participationConstructor) {
		return janusConnection.createSession()
				.thenCompose(session -> session.attachPlugin(JanusVideoRoomPlugin.class)
						.thenCompose(plugin -> plugin.createPublisher(roomId))
						.thenCombineAsync(session.attachPlugin(JanusVideoRoomPlugin.class)
												  .thenApply(plugin -> plugin.createSubscriber(roomId)),
										  participationConstructor)
						.whenComplete((x, ex1) -> {
							if (ex1 != null) {
								session.destroy();
							}
						}));
	}

	public abstract void left(T participation);

	@Override
	public String toString() {
		return "AbstractMeet{" + "roomId=" + roomId + ", janusConnection=" + janusConnection + '}';
	}
}
