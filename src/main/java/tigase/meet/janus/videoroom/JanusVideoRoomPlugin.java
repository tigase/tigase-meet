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

import com.fasterxml.jackson.core.JsonGenerator;
import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusConnection;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.JanusSession;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JanusVideoRoomPlugin extends JanusPlugin<JanusVideoRoomPlugin.Content> {

	public static final String ID = "janus.plugin.videoroom";

	private static final Logger log = Logger.getLogger(JanusVideoRoomPlugin.class.getCanonicalName());

	private LocalPublisher localPublisher;
	private LocalSubscriber localSubscriber;

	public JanusVideoRoomPlugin(JanusSession session, Map<String, Object> data) {
		super(session, data);
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public void handleEvent(JanusVideoRoomPlugin.Content content) {
		log.log(Level.FINER, toString() + ", received event with: " + content.data + " and jsep " + content.jsep);
		if (localPublisher != null) {
			localPublisher.handleEvent(content);
		}
		if (localSubscriber != null) {
			localSubscriber.handleEvent(content);
		}
	}

	@Override
	protected void receivedCandidate(Candidate candidate) {
		if (localPublisher != null) {
			localPublisher.receivedCandidate(candidate);
		}
		if (localSubscriber != null) {
			localSubscriber.receivedCandidate(candidate);
		}
	}

	public CompletableFuture<Object> createRoom(Object roomId, int maxNoOfPublishers, String videoCodec) {
		String transaction = getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction + " creating room " + roomId + "...");
		return execute("create", transaction, roomId, generator -> {
			generator.writeNumberField("publishers", maxNoOfPublishers);
			if (videoCodec != null && !videoCodec.isEmpty()) {
				generator.writeStringField("videocodec", videoCodec);
			}
			generator.writeBooleanField("notify_joining", true);
		}, null).thenApply(content -> {
			String videoroom = content.getVideoRoom();
			switch (videoroom) {
				case "created":
					return content.getRoom();
				default:
					throw new UnsupportedOperationException("Unsupported response '" + videoroom + "'");
			}
		}).whenComplete((room, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", room " + roomId + " creation failed!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", room " + roomId + " created.");
			}
		});
	}

	public CompletableFuture<Void> destroyRoom(Object roomId) {
		String transaction = getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", destroying room..");
		return execute("destroy", transaction, roomId, generator -> {}, null).thenAccept(data -> {
			String videoroom = data.getVideoRoom();
			switch (videoroom) {
				case "destroyed":
					break;
				default:
					throw new UnsupportedOperationException("Unsupported response '" + videoroom + "'");
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", failed to destruct room!");
			} else {
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", room destroyed.");
			}
		});
	}

	public CompletableFuture<LocalPublisher> createPublisher(Object roomId, String displayName) {
		String transaction = getSession().nextTransactionId();
		log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", joining as publisher..");
		return execute("join", transaction, roomId, generator -> {
			generator.writeStringField("ptype", "publisher");
			if (displayName != null) {
				generator.writeStringField("display", displayName);
			}
		}, null).thenApply(data -> {
			if ("joined".equals(data.getVideoRoom())) {
				return LocalPublisher.fromData(data.data, this, roomId);
			} else {
				throw new UnsupportedOperationException("Unexpected response: " + data);
			}
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> toString() + ", transaction " + transaction + ", failed to join as publisher!");
			} else {
				synchronized (this) {
					localPublisher = x;
				}
				log.log(Level.FINER, () -> toString() + ", transaction " + transaction + ", joined as publisher.");
			}
		});
	}

	public synchronized LocalSubscriber createSubscriber(Object roomId) {
		localSubscriber = new LocalSubscriber(this, roomId);
		return localSubscriber;
	}
	
	public CompletableFuture<Content> execute(String request, String transaction, Object roomId, JanusConnection.RequestGenerator requestGenerator, JSEP jsep) {
		return execute(transaction, generator -> {
			generator.writeStringField("request", request);
			if (roomId != null) {
				if (roomId instanceof Number) {
					generator.writeNumberField("room", ((Number) roomId).longValue());
				} else {
					generator.writeStringField("room", roomId.toString());
				}
			}
			requestGenerator.accept(generator);
		}, jsep);
	}

	public CompletableFuture<Void> sendMessage(String request, String transaction, Object roomId, JanusConnection.RequestGenerator requestGenerator, JSEP jsep) {
		return sendMessage(transaction, generator -> {
			generator.writeStringField("request", request);
			if (roomId != null) {
				if (roomId instanceof Number) {
					generator.writeNumberField("room", ((Number) roomId).longValue());
				} else {
					generator.writeStringField("room", roomId.toString());
				}
			}
			requestGenerator.accept(generator);
		}, jsep);
	}

	@Override
	protected Content newContent(Map<String, Object> data, JSEP jsep) {
		return new Content(data, jsep);
	}

	public static class Content extends JanusPlugin.Content {

		public Content(Map data, JSEP jsep) {
			super(data, jsep);
		}

		public Object getRoom() {
			Object room = data.get("room");
			if (room instanceof Number) {
				return ((Number) room).longValue();
			}
			return room;
		}

		public String getVideoRoom() {
			return Optional.ofNullable((String) data.get("videoroom")).orElseThrow(() -> new NullPointerException("Missing 'videoroom'!"));
		}

	}

	public static class Stream {
		public final long feedId;
		public final String mid;

		public Stream(long feedId, String mid) {
			this.feedId = feedId;
			this.mid = mid;
		}

		public long getFeed() {
			return feedId;
		}

		public void write(JsonGenerator generator) throws IOException {
			generator.writeStartObject();
			generator.writeNumberField("feed", feedId);
			if (mid != null) {
				generator.writeStringField("mid", mid);
			}
			generator.writeEndObject();
		}
	}
}
