/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.meet.janus.JanusService;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Bean(name = "repository", parent = MeetComponent.class, active = true)
public class MeetRepository implements IMeetRepository {

	private final ConcurrentHashMap<BareJID, CompletableFuture<Meet>> meets = new ConcurrentHashMap<>();

	@Inject
	private JanusService janusService;

	@Override
	public CompletableFuture<Meet> create(BareJID key) {
		CompletableFuture<Meet> future = new CompletableFuture<>();
		if (meets.putIfAbsent(key, future) != null) {
			future.completeExceptionally(new ComponentException(Authorization.CONFLICT));
			return future;
		}

		createMeet(key).whenComplete((room,ex) -> {
			if (ex != null) {
				meets.remove(key, future);
				future.completeExceptionally(ex);
			} else {
				future.complete(room);
			}
		});
				
		return future;
	}

	@Override
	public Meet getMeet(BareJID jid) throws ComponentException {
		CompletableFuture<Meet> future = meets.get(jid);
		if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
			try {
				return future.get();
			} catch (Throwable ex) {
				throw new ComponentException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
			}
		}
		throw new ComponentException(Authorization.ITEM_NOT_FOUND);
	}

	@Override
	public void destroyed(BareJID jid) {
		meets.remove(jid);
	}

	private CompletableFuture<Meet> createMeet(BareJID meetJid) {
		return janusService.newConnection()
				.thenCompose(connection -> connection.createSession()
						.thenCompose(session -> session.attachPlugin(JanusVideoRoomPlugin.class)
								.thenCompose(videoRoomPlugin -> videoRoomPlugin.createRoom(null))
								.exceptionallyCompose(
										ex -> session.destroy().handle((x, ex1) -> CompletableFuture.failedFuture(ex))))
						// we should close this session even it is not connected? or maybe we should store it?
						.thenApply(roomId -> new Meet(this, connection, roomId, meetJid))
						.exceptionallyCompose(ex -> {
							connection.close();
							return CompletableFuture.failedFuture(ex);
						}));
	}
}
