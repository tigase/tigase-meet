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
package tigase.meet;

import tigase.meet.janus.JanusConnection;
import tigase.meet.janus.JanusSession;
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

	protected CompletableFuture<T> join(String displayName, BiFunction<LocalPublisher, LocalSubscriber, T> participationConstructor) {
		return janusConnection.createSession()
				.thenCompose(session -> this.createParticipation(session, displayName, participationConstructor)
						.whenComplete((x, ex1) -> {
							if (ex1 != null) {
								session.destroy();
							}
						}));
	}

	protected CompletableFuture<T> createParticipation(JanusSession session, String displayName, BiFunction<LocalPublisher, LocalSubscriber, T> participationConstructor) {
		CompletableFuture<LocalPublisher> localPublisherFuture = session.attachPlugin(JanusVideoRoomPlugin.class)
				.thenCompose(plugin -> plugin.createPublisher(roomId, displayName));
		CompletableFuture<LocalSubscriber> localSubscriberFuture = session.attachPlugin(JanusVideoRoomPlugin.class)
				.thenApply(plugin -> plugin.createSubscriber(roomId));
		return localPublisherFuture.thenCombineAsync(localSubscriberFuture, participationConstructor);
	}

	public CompletableFuture<Void> destroy() {
		return janusConnection.createSession()
				.thenCompose(session -> session.attachPlugin(JanusVideoRoomPlugin.class).thenCompose(plugin -> plugin.destroyRoom(roomId)).whenComplete((x,ex) -> {
					if (ex != null) {
						session.destroy();
					}
				}))
				.thenAccept(x -> janusConnection.close());
	}

	public abstract void left(T participation);

	@Override
	public String toString() {
		return "AbstractMeet{" + "roomId=" + roomId + ", janusConnection=" + janusConnection + '}';
	}
}
