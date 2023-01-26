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
package tigase.meet.janus.videoroom;

import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.JanusSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LocalPublisher {

	public static LocalPublisher fromData(Map<String, Object> data, JanusVideoRoomPlugin videoRoomPlugin,
										  Object roomId) {
		long id = Optional.ofNullable((Number) data.get("id"))
				.map(Number::longValue)
				.orElseThrow(() -> new NullPointerException("Missing 'id'!"));
		long privateId = Optional.ofNullable((Number) data.get("private_id"))
				.map(Number::longValue)
				.orElseThrow(() -> new NullPointerException("Missing 'private_id'!"));
		return new LocalPublisher(videoRoomPlugin, roomId, id, privateId,
								  Publisher.fromEvent((List<Map<String, Object>>) data.get("publishers")));
	}

	private static final Logger log = Logger.getLogger(LocalPublisher.class.getCanonicalName());

	private final JanusVideoRoomPlugin videoRoomPlugin;
	private final Object roomId;
	private final long id;
	private final long privateId;

	private final ConcurrentHashMap<Long, Publisher> publishers = new ConcurrentHashMap<>();
	private Listener listener;

	public LocalPublisher(JanusVideoRoomPlugin videoRoomPlugin, Object roomId, long id, long privateId,
						  List<Publisher> publishers) {
		this.videoRoomPlugin = videoRoomPlugin;
		this.roomId = roomId;
		this.id = id;
		this.privateId = privateId;
		this.publishers.putAll(publishers.stream().collect(Collectors.toMap(Publisher::getId, Function.identity())));
	}

	public long getId() {
		return id;
	}

	public long getPrivateId() {
		return privateId;
	}

	public Object getRoomId() {
		return roomId;
	}

	public JanusSession getSession() {
		return videoRoomPlugin.getSession();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
		if (!publishers.isEmpty()) {
			listener.addedPublishers(publishers.values());
		}
	}

	public CompletableFuture<Void> leave() {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER,
				() -> toString() + ", transaction " + transaction + ", publisher " + id + " leaving room..");
		return videoRoomPlugin.execute("leave", transaction, roomId, generator -> {
		}, null).thenApply(content -> {
			if ("event".equals(content.getVideoRoom()) && "ok".equals(content.data.get("leaving"))) {
				return (Void) null;
			} else {
				throw new UnsupportedOperationException("Unexpected response: " + content);
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", publisher " + id +
						" failed to leave room!");
			} else {
				log.log(Level.FINER,
						() -> toString() + ", transaction " + transaction + ", publisher " + id + " left room.");
			}
		});
	}

	public CompletableFuture<JSEP> publish(JSEP jsep) {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER,
				() -> toString() + ", transaction " + transaction + ", publisher " + id + " publishes stream..");
		return videoRoomPlugin.execute("publish", transaction, roomId, generator -> {
		}, jsep).thenApply(content -> {
			if ("event".equals(content.getVideoRoom()) && "ok".equals(content.data.get("configured"))) {
				return content.jsep;
			} else {
				throw new UnsupportedOperationException("Unexpected response: " + content);
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", publisher " + id +
						" failed to publish stream!");
			} else {
				log.log(Level.FINER,
						() -> toString() + ", transaction " + transaction + ", publisher " + id + " published stream.");
				if(x != null) {
					listener.receivedPublisherSDP(x);
				}
			}
		});
	}

	public CompletableFuture<Void> unpublish() {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER,
				() -> toString() + ", transaction " + transaction + ", publisher " + id + " stops publishing stream..");
		return videoRoomPlugin.execute("unpublish", transaction, roomId, generator -> {
		}, null).thenApply(content -> {
			if ("event".equals(content.getVideoRoom()) && "ok".equals(content.data.get("unpublished"))) {
				return (Void) null;
			} else {
				throw new UnsupportedOperationException("Unexpected response: " + content);
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", publisher " + id +
						" failed to stop publishing stream!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", publisher " + id +
						" stopped publishing stream.");
			}
		});
	}

	public void handleEvent(JanusVideoRoomPlugin.Content content) {
		if ("event".equals(content.getVideoRoom()) && roomId.equals(content.getRoom())) {
			List<Publisher> publishers = Publisher.fromEvent(
					(List<Map<String, Object>>) content.data.get("publishers"));
			if (publishers != null) {
				this.publishers.putAll(
						publishers.stream().collect(Collectors.toMap(Publisher::getId, Function.identity())));
				if (listener != null) {
					listener.addedPublishers(publishers);
				}
			} else if (content.data.containsKey("unpublished")) {
				Optional.ofNullable(content.data.get("unpublished"))
						.filter(Number.class::isInstance)
						.map(Number.class::cast)
						.ifPresent(id -> {
							this.publishers.remove(id.longValue());
							if (listener != null) {
								listener.removedPublishers(id.longValue());
							}
						});
			}
		}
	}

	public Collection<Publisher> getPublishers() {
		return publishers.values();
	}

	public CompletableFuture<Void> sendCandidate(JanusPlugin.Candidate candidate) {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
				", sending candidate " + candidate + "..");
		return videoRoomPlugin.sendTrickle(transaction, candidate).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction +
						", failed to send candidate!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
						", candidate sent.");
			}
		});
	}

	public void receivedCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedPublisherCandidate(candidate);
	}

	public interface Listener {

		void addedPublishers(Collection<Publisher> publishers);

		void removedPublishers(long publisherId);

		void receivedPublisherSDP(JSEP jsep);

		void receivedPublisherCandidate(JanusPlugin.Candidate candidate);

	}
}
