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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalSubscriber {

	private static final Logger log = Logger.getLogger(LocalSubscriber.class.getCanonicalName());

	private final JanusVideoRoomPlugin videoRoomPlugin;
	private final Object roomId;

	private boolean subscribed = false;
	private Listener listener;

	public LocalSubscriber(JanusVideoRoomPlugin videoRoomPlugin, Object roomId) {
		this.videoRoomPlugin = videoRoomPlugin;
		this.roomId = roomId;
	}

	public JanusSession getSession() {
		return videoRoomPlugin.getSession();
	}

	public CompletableFuture<JSEP> joinAsSubscriber(Long privateId, long publisherId,
													List<JanusVideoRoomPlugin.Stream> streams) {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
				", joining room as subscriber..");
		return videoRoomPlugin.execute("join", transaction, roomId, generator -> {
			generator.writeStringField("ptype", "subscriber");
			generator.writeBooleanField("autoupdate", true);
			if (privateId != null) {
				generator.writeNumberField("private_id", privateId);
			}
			generator.writeFieldName("streams");
			generator.writeStartArray();
			for (JanusVideoRoomPlugin.Stream stream : streams) {
				stream.write(generator);
			}
			generator.writeEndArray();
		}, null).thenApply(content -> {
			subscribed = true;
			log.log(Level.FINER, () -> toString() + " updated subscribed streams: " + content.data.get("streams"));
			return content.jsep;
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction +
						", failed to join room as subscriber!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
						", joined room as subscriber.");
				if (x != null) {
					listener.receivedSubscriberSDP(x);
				}
			}
		});
	}

	public CompletableFuture<JSEP> subscribe(List<JanusVideoRoomPlugin.Stream> streams) {
		if (!subscribed) {
			return joinAsSubscriber(null, streams.stream()
					.mapToLong(JanusVideoRoomPlugin.Stream::getFeed)
					.findFirst()
					.getAsLong(), streams);
		}
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
				", subscribing streams " + streams + "..");
		return videoRoomPlugin.execute("subscribe", transaction, roomId, generator -> {
			generator.writeFieldName("streams");
			generator.writeStartArray();
			for (JanusVideoRoomPlugin.Stream stream : streams) {
				stream.write(generator);
			}
			generator.writeEndArray();
		}, null).thenApply(x -> x.jsep).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction +
						", failed to subscribe streams!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
						", subscribed streams.");
				if (x != null) {
					listener.receivedSubscriberSDP(x);
				}
			}
		});
	}

	public CompletableFuture<JSEP> unsubscribe(long publisherId) {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
				", unsubscribing streams with feed " + publisherId + "..");
		return videoRoomPlugin.execute("unsubscribe", transaction, roomId, generator -> {
			generator.writeFieldName("streams");
			generator.writeStartArray();
			new JanusVideoRoomPlugin.Stream(publisherId, null).write(generator);
			generator.writeEndArray();
		}, null).thenApply(content -> content.jsep).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction +
						", failed to unsubscribed streams!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
						", unsubscribed streams.");
				if (x != null) {
					listener.receivedSubscriberSDP(x);
				}
			}
		});
	}

	public CompletableFuture<Void> start(JSEP jsep) {
		String transaction = videoRoomPlugin.getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
				", starting stream..");
		return videoRoomPlugin.execute("start", transaction, roomId, generator -> {
		}, jsep).thenApply(content -> {
			if ("event".equals(content.getVideoRoom()) && "ok".equals(content.data.get("started"))) {
				return (Void) null;
			} else {
				throw new UnsupportedOperationException("Unexpected response: " + content);
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction +
						", failed to start stream!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction +
						", stream started.");
			}
		});
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

	public void handleEvent(JanusVideoRoomPlugin.Content content) {
		if ("updated".equals(content.getVideoRoom()) && content.data.containsKey("streams")) {
			log.log(Level.FINEST, () -> toString() + " updated subscribed streams: " + content.data.get("streams") + ", jsep: " + content.jsep);
			if (content.jsep != null) {
				listener.receivedSubscriberSDP(content.jsep);
			}
		}
	}

	public void receivedCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedSubscriberCandidate(candidate);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public interface Listener {

		void receivedSubscriberSDP(JSEP jsep);

		void receivedSubscriberCandidate(JanusPlugin.Candidate candidate);

	}
}
