/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class JanusPlugin<T extends JanusPlugin.Content> {

	public static <T extends JanusPlugin> T newInstance(Class<T> pluginClass, JanusSession session,
														Map<String, Object> data) {
		try {
			return pluginClass.getDeclaredConstructor(JanusSession.class, Map.class).newInstance(session, data);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
			throw new IllegalArgumentException("Class " + pluginClass + " does not contain required constructor!", ex);
		}
	}

	private final JanusSession session;
	private final long handleId;

	protected JanusPlugin(JanusSession session, Map<String, Object> data) {
		if (data == null) {
			throw new IllegalArgumentException("Missing 'data'!");
		}
		Long id = (Long) data.get("id");
		if (id == null) {
			throw new IllegalArgumentException("Missing handle id!");
		}

		this.session = session;
		this.handleId = id;
	}

	public abstract String getId();

	public abstract void handleEvent(T content);

	public void handleTrickle(Map<String, Object> trickle) {
		List<Map<String, Object>> list = (List<Map<String, Object>>) trickle.get("candidates");
		if (list != null) {
			for (Map<String, Object> map : list) {
				if (map.containsKey("completed")) {
					continue;
				}
				receivedCandidate(Candidate.fromMap(map));
			}
		} else {
			Object map = trickle.get("candidate");
			if (map instanceof Map) {
				if (!((Map)map).containsKey("completed")) {
					receivedCandidate(Candidate.fromMap((Map<String, Object>) map));
				}
			}
		}
	}

	protected abstract void receivedCandidate(Candidate candidate);

	public JanusSession getSession() {
		return session;
	}

	public long getHandleId() {
		return handleId;
	}

	public CompletableFuture<T> execute(String transaction, JanusConnection.RequestGenerator bodyGenerator, JSEP jsep) {
		return session.execute("message", transaction, generator -> {
			generator.writeNumberField("handle_id", handleId);
			if (bodyGenerator != null) {
				generator.writeFieldName("body");
				generator.writeStartObject();
				bodyGenerator.accept(generator);
				generator.writeEndObject();
			}
			if (jsep != null) {
				jsep.write(generator);
			}
		}).thenApply(this::extractData);
	}

	public CompletableFuture<Void> sendMessage(String transaction, JanusConnection.RequestGenerator bodyGenerator, JSEP jsep) {
		return session.send("message", transaction, generator -> {
			generator.writeNumberField("handle_id", handleId);
			if (bodyGenerator != null) {
				generator.writeFieldName("body");
				generator.writeStartObject();
				bodyGenerator.accept(generator);
				generator.writeEndObject();
			}
			if (jsep != null) {
				jsep.write(generator);
			}
		}).thenApply(x -> (Void) null);
	}

	public CompletableFuture<Void> sendTrickle(String transaction, JanusConnection.RequestGenerator bodyGenerator) {
		return session.send("trickle", transaction, generator -> {
			generator.writeNumberField("handle_id", handleId);
			bodyGenerator.accept(generator);
		});
	}

	public CompletableFuture<Void> sendTrickle(String transaction, Candidate candidate) {
		return sendTrickle(transaction, generator -> {
			generator.writeFieldName("candidate");
			candidate.write(generator);
		});
	}

	public CompletableFuture<Void> detach() {
		return session.detachPlugin(this);
	}
	
	protected T extractData(Map<String, Object> response) {
		Map<String, Object> plugindata = (Map<String, Object>) response.get("plugindata");
		if (plugindata == null) {
			throw new NullPointerException("Received JSON with 'plugindata' not set!");
		}
		if (!getId().equals(plugindata.get("plugin"))) {
			throw new IllegalArgumentException("Received data by plugin " + getId() + " sent for " + plugindata.get("plugin"));
		}
		Map<String, Object> data = (Map<String, Object>) plugindata.get("data");
		if (data == null) {
			throw new NullPointerException("Missing 'data' for plugin!");
		}

		assertNotError(data);
		
		return newContent(data, JSEP.fromData(response));
	}

	protected abstract T newContent(Map<String, Object> data, JSEP jsep);
	
	protected void assertNotError(Map<String, Object> data) {
		Integer errorCode = (Integer) data.get("error_code");
		if (errorCode != null) {
			throw new JanusException(errorCode, (String) data.get("error"));
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{" + "session=" + session.getSessionId() + ", handleId=" + handleId + '}';
	}

	public static class Content {
		public final Map<String, Object> data;
		public final JSEP jsep;

		public Content(Map<String, Object> data, JSEP jsep) {
			this.data = data;
			this.jsep = jsep;
		}

	}

	public static class Candidate {

		public static final Candidate fromMap(Map<String, Object> data) {
			return new Candidate((String) data.get("sdpMid"), ((Number) data.get("sdpMLineIndex")).intValue(),
								 (String) data.get("candidate"));
		}

		private final String mid;
		private final int sdpMLineIndex;
		private final String candidate;

		public Candidate(String mid, int sdpMLineIndex, String candidate) {
			this.mid = mid;
			this.sdpMLineIndex = sdpMLineIndex;
			this.candidate = candidate;
		}

		public String getMid() {
			return mid;
		}

		public int getSdpMLineIndex() {
			return sdpMLineIndex;
		}

		public String getCandidate() {
			return candidate;
		}

		public void write(JsonGenerator generator) throws IOException {
			generator.writeStartObject();
			generator.writeStringField("sdpMid", mid);
			generator.writeNumberField("sdpMLineIndex", sdpMLineIndex);
			generator.writeStringField("candidate", candidate);
			generator.writeEndObject();
		}

		@Override
		public String toString() {
			return "Candidate{" + "mid='" + mid + '\'' + ", sdpMLineIndex=" + sdpMLineIndex + ", candidate='" +
					candidate + '\'' + '}';
		}
	}
}
