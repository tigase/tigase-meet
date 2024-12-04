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
package tigase.meet.janus;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.StringWriter;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JanusConnection implements WebSocket.Listener {

	private static final Logger log = Logger.getLogger(JanusConnection.class.getCanonicalName());
	private static final JsonFactory jsonFactory = new JsonFactory();

	private final String id = UUID.randomUUID().toString();
	private WebSocket webSocket;
	private StringBuilder sb = new StringBuilder();
	private ConcurrentHashMap<String, CompletableFuture<Void>> sendTransactions = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, CompletableFuture<Map<String,Object>>> executeTransactions = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Long, JanusSession> activeSessions = new ConcurrentHashMap<>();

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final JanusPluginsRegister pluginsRegister;
	private final ScheduledExecutorService executorService;
	private final Duration sessionTimeout;

	public JanusConnection(JanusPluginsRegister pluginsRegister, ScheduledExecutorService executorService, Duration sessionTimeout) {
		this.pluginsRegister = pluginsRegister;
		this.executorService = executorService;
		this.sessionTimeout = sessionTimeout;
	}

	public void close() {
		CompletableFuture.allOf(activeSessions.values().stream().map(session -> session.destroy()).toArray(CompletableFuture[]::new)).handle( (x, ex) -> {
			return withContext(webSocket -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));
		});
	}

	public String getId() {
		return id;
	}

	public String nextTransactionId() {
		return UUID.randomUUID().toString();
	}

	protected void setWebSocket(WebSocket webSocket) {
		this.webSocket = webSocket;
	}

	public String logPrefix(String transaction) {
		return logPrefix() + ", transaction " + transaction;
	}

	public CompletableFuture<JanusSession> createSession() {
		String transaction = nextTransactionId();
		log.log(Level.FINER, () -> this.logPrefix(transaction) + ", creating session..");
		CompletableFuture<JanusSession> future = new CompletableFuture<>();
		execute("create", transaction, generator -> {
		}).thenApply(data -> new JanusSession(this, (Map<String, Object>) data.get("data")))
				.whenComplete((session, ex) -> {
					if (ex != null) {
						log.log(Level.WARNING, ex,
								() -> this.logPrefix(transaction) + ", session creation failed.");
						future.completeExceptionally(ex);
					} else {
						this.activeSessions.put(session.getSessionId(), session);
						log.log(Level.FINER, () -> session.logPrefix(transaction) + " session created.");
						session.scheduleKeepAlive(executorService, sessionTimeout);
						future.complete(session);
					}
				});
		return future;
	}

	public CompletableFuture<Void> destroySession(JanusSession session) {
		String transaction = nextTransactionId();
		log.log(Level.FINER, () -> session.logPrefix(transaction) + " destroying ..");
		if (activeSessions.remove(session.getSessionId()) == null) {
			return CompletableFuture.completedFuture(null);
		}
		return execute("destroy", transaction, generator -> {
			generator.writeNumberField("session_id", session.getSessionId());
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				// do not log JANUS_ERROR_SESSION_NOT_FOUND - it means that session was already destroyed..
				String msg = ex.getMessage();
				if (msg != null && msg.startsWith("458 - ")) {
					return;
				}
				log.log(Level.WARNING, ex,
						() -> session.logPrefix(transaction) +
								" destruction failed!");
			} else {
				log.log(Level.FINER,
						() -> session.logPrefix(transaction) +
								" destroyed");
			}
		}).thenApply(x -> null);
	}

	public String getPluginId(Class<? extends JanusPlugin> plugin) {
		return pluginsRegister.getPluginId(plugin);
	}

	public CompletableFuture<Map<String,Object>> getInfo() {
		return execute("info", nextTransactionId(), generator -> {});
	}

	public CompletableFuture<Map<String,Object>> execute(String janus, String transaction, RequestGenerator requestGenerator) {
		CompletableFuture<Map<String,Object>> future = new CompletableFuture<Map<String,Object>>().whenComplete((result, ex) -> executeTransactions.remove(transaction));
		try {
			executeTransactions.put(transaction, future);
			sendInternal(janus, transaction, requestGenerator);
		} catch (IOException|ExecutionException|InterruptedException ex) {
			executeTransactions.remove(transaction);
			future.completeExceptionally(ex);
		}
		return future;
	}

	public CompletableFuture<Void> send(String janus, String transaction, RequestGenerator requestGenerator) {
		CompletableFuture<Void> future = new CompletableFuture<Void>().whenComplete((result, ex) -> sendTransactions.remove(transaction));
		try {
			sendTransactions.put(transaction, future);
			sendInternal(janus, transaction, requestGenerator);
		} catch (IOException|ExecutionException|InterruptedException ex) {
			future.completeExceptionally(ex);
		}
		return future;
	}

	private void sendInternal(String janus, String transaction, RequestGenerator requestGenerator) throws IOException, ExecutionException, InterruptedException {
		StringWriter w = new StringWriter();
		JsonGenerator generator = jsonFactory.createGenerator(w);
		generator.writeStartObject();
		generator.writeStringField("janus", janus);
		generator.writeStringField("transaction", transaction);
		requestGenerator.accept(generator);
		generator.writeEndObject();
		generator.close();
		String str = w.toString();
		log.log(Level.FINEST, () -> logPrefix() + ", sending request: " + str);
		withContext(webSocket -> {
			return webSocket.sendText(str, true);
		}).get();
	}

	public String logPrefix() {
		return "connection " + getId();
	}

	private <T> CompletableFuture<T> withContext(Function<WebSocket, CompletableFuture<T>> function) {
		CompletableFuture<T> future = new CompletableFuture<>();
		executor.execute(() -> {
			function.apply(webSocket).whenComplete((result, ex) -> {
				if (ex != null) {
					future.completeExceptionally(ex);
				} else {
					future.complete(result);
				}
			});
		});
		return future;
	}

	@FunctionalInterface
	public interface RequestGenerator {

		void accept(JsonGenerator generator) throws IOException;

	}

	@Override
	public void onOpen(WebSocket webSocket) {
		log.log(Level.FINEST, () -> logPrefix() + ", opened connection");
		webSocket.request(1);

	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		log.log(Level.FINEST, () -> logPrefix() + ", closed connection");
		webSocket.request(1);
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		log.log(Level.WARNING, error, () -> logPrefix() + ", exception on connection");
		webSocket.request(1);
	}

	@Override
	public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
		log.log(Level.FINEST, () -> logPrefix() + ", received binary: " + data);
		webSocket.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		sb.append(data);
		webSocket.request(1);
		//log.log(Level.FINEST, () -> logPrefix() + ", received text: " + data);
		if (last) {
			log.log(Level.FINEST, () -> logPrefix() + ", received message: " + sb.toString());
			try {
				Map<String, Object> values = decode(sb.toString());
				String janus = (String) values.get("janus");
				String transaction = (String) values.get("transaction");
				if (janus == null) {
					throw new NullPointerException("Received JSON with 'janus' not set!");
				}
				switch (janus) {
					case "server_info":
					case "success":
						if (transaction == null) {
							throw new NullPointerException("Received JSON with 'transaction' not set!");
						}
						removeExecuteTransaction(transaction).ifPresentOrElse(future -> future.complete(values),
																							  () -> log.log(Level.WARNING, () -> logPrefix(transaction) + ", received success without matching transaction, payload: " + values));
						break;
					case "error":
						if (transaction == null) {
							throw new NullPointerException("Received JSON with 'transaction' not set!");
						}
						JanusException ex = new JanusException((Map<String, Object>) values.get("error"));
						log.log(Level.WARNING, ex, () -> this.logPrefix(transaction) + ", request failed!");
						removeExecuteTransaction(transaction).ifPresentOrElse(
								future -> future.completeExceptionally(ex), () -> {
									removeSendTransaction(transaction).ifPresentOrElse(
											future -> future.completeExceptionally(ex), () -> log.log(Level.WARNING,
																									  () -> logPrefix(
																											  transaction) +
																											  ", received error without matching transaction, payload: " +
																											  values));
								});
						break;
					case "ack":
						if (transaction == null) {
							throw new NullPointerException("Received JSON with 'transaction' not set!");
						}
						removeSendTransaction(transaction).ifPresent(future -> future.complete(null));

						log.log(Level.FINEST, () -> this.logPrefix(transaction) + ", request acknowledged.");
						break;
					case "detached":
						log.log(Level.FINEST, () -> logPrefix() + ", received detached event: " + values);
						break;
					case "event":
						Optional<CompletableFuture<Map<String, Object>>> handler = transaction != null ? removeExecuteTransaction(transaction) : Optional.empty();
						log.log(Level.FINEST, () -> logPrefix() + ", received event: " + values + ", with handler: " + handler.isPresent());
						if (handler.isPresent()) {
							handler.get().complete(values);
						} else {
							Optional.ofNullable((Long) values.get("session_id")).map(this::getSession).ifPresentOrElse(session -> session.handleEvent(values), () -> log.log(Level.WARNING, () -> logPrefix() + ", event for not existing session: " + values));
						}
						break;
					case "trickle":
						log.log(Level.FINEST, () -> logPrefix() + ", received trickle: " + values);
						Optional.ofNullable((Long) values.get("session_id")).map(this::getSession).ifPresentOrElse(session -> session.handleTrickle(values), () -> log.log(Level.WARNING, () -> logPrefix() + ", trickle for not existing session: " + values));
						break;
					default:
						log.log(Level.FINEST, () -> logPrefix() + ", received something: " + values);
						break;
				}
			} catch (Throwable e) {
				log.log(Level.WARNING, e, () -> logPrefix() + ", JSON processing failed!\n" + sb.toString());
			}
			sb = new StringBuilder();
		}
		return null;
	}

	protected JanusSession getSession(long id) {
		return activeSessions.get(id);
	}

	protected Optional<CompletableFuture<Map<String,Object>>> removeExecuteTransaction(String transactionId) {
		return Optional.ofNullable(executeTransactions.get(transactionId));
	}

	protected Optional<CompletableFuture<Void>> removeSendTransaction(String transactionId) {
		return Optional.ofNullable(sendTransactions.get(transactionId));
	}

	protected Map<String,Object> decode(String content) throws IOException {
		JsonParser parser = jsonFactory.createParser(content);
		try {
			parser.nextToken();
			return decode(parser);
		} finally {
			parser.close();
		}
	}

	protected Map<String,Object> decode(JsonParser parser) throws IOException {
		if (parser.getCurrentToken() != JsonToken.START_OBJECT) {
			throw new IllegalStateException("Invalid parser state! state = " + parser.getCurrentToken());
		}
		Map<String,Object> result = new HashMap<>();
		String fieldName = null;
		while (parser.nextToken() != JsonToken.END_OBJECT) {
			JsonToken token = parser.getCurrentToken();
			if (token == JsonToken.FIELD_NAME) {
				fieldName = parser.getCurrentName();
			} else {
				result.put(fieldName, decodeValue(parser));
			}
		}
		return result;
	}

	protected Object decodeValue(JsonParser parser) throws IOException {
		JsonToken token = parser.getCurrentToken();
		if (token == JsonToken.VALUE_STRING) {
			return parser.getText();
		} else if (token == JsonToken.VALUE_NULL) {
			return null;
		}  else if (token == JsonToken.VALUE_TRUE) {
			return true;
		} else if (token == JsonToken.VALUE_FALSE) {
			return false;
		} else if (token == JsonToken.VALUE_NUMBER_INT) {
			return parser.getNumberValue();
			//return parser.getIntValue();
		} else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
			return parser.getFloatValue();
		} else if (token == JsonToken.VALUE_EMBEDDED_OBJECT) {
			return parser.getEmbeddedObject();
		} else if (token == JsonToken.START_OBJECT) {
			return decode(parser);
		} else if (token == JsonToken.START_ARRAY) {
			return decodeArray(parser);
		} else {
			throw new IllegalStateException("Unexpected token");
		}
	}

	protected List decodeArray(JsonParser parser) throws IOException {
		List list = new ArrayList<>();
		while (parser.nextToken() != JsonToken.END_ARRAY) {
			list.add(decodeValue(parser));
		}
		return list;
	}
	
}
