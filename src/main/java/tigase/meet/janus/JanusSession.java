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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JanusSession {

	private static final Logger log = Logger.getLogger(JanusSession.class.getCanonicalName());

	private final JanusConnection connection;
	private final long sessionId;
	private ScheduledFuture<?> keepAliveFuture;

	private final ConcurrentHashMap<Long,JanusPlugin> attachedPlugins = new ConcurrentHashMap<>();

	protected JanusSession(JanusConnection connection, Map<String, Object> sessionData) {
		if (sessionData == null) {
			throw new IllegalArgumentException("Missing 'sessionData'!");
		}
		Long id = (Long) sessionData.get("id");
		if (id == null) {
			throw new IllegalArgumentException("Missing session id!");
		}
		
		this.connection = connection;
		this.sessionId = id;
	}

	public JanusConnection getConnection() {
		return connection;
	}

	public long getSessionId() {
		return sessionId;
	}

	public String nextTransactionId() {
		return connection.nextTransactionId();
	}

	public String logPrefix() {
		return connection.logPrefix() + ", session " + this.getSessionId();
	}

	public String logPrefix(String transaction) {
		return connection.logPrefix(transaction) + ", session " + this.getSessionId();
	}

	public void handleEvent(Map<String, Object> event) {
		Long senderId = (Long) event.get("sender");
		if (senderId != null) {
			JanusPlugin plugin = attachedPlugins.get(senderId);
			if (plugin != null) {
				JanusPlugin.Content content = plugin.extractData(event);
				plugin.handleEvent(content);
			} else {
				log.log(Level.WARNING, () -> logPrefix() + ", received event from unknown sender: " + senderId + ", values: " + event);
			}
		} else {
			log.log(Level.WARNING, () -> logPrefix() + ", received event from without sender, values: " + event);
		}
	}

	public void handleTrickle(Map<String, Object> trickle) {
		Long senderId = (Long) trickle.get("sender");
		if (senderId != null) {
			JanusPlugin plugin = attachedPlugins.get(senderId);
			if (plugin != null) {
				plugin.handleTrickle(trickle);
			} else {
				log.log(Level.WARNING, () -> logPrefix() + ", received trickle from unknown sender: " + senderId + ", values: " + trickle);
			}
		} else {
			log.log(Level.WARNING, () -> logPrefix() + ", received trickle from without sender, values: " + trickle);
		}
	}
	
	public CompletableFuture<Map<String,Object>> execute(String janus, String transaction, JanusConnection.RequestGenerator requestGenerator) {
		return connection.execute(janus, transaction, generator -> {
			generator.writeNumberField("session_id", sessionId);
			requestGenerator.accept(generator);
		});
	}

	public CompletableFuture<Void> send(String janus, String transaction, JanusConnection.RequestGenerator requestGenerator) {
		return connection.send(janus, transaction, generator -> {
			generator.writeNumberField("session_id", sessionId);
			requestGenerator.accept(generator);
		});
	}

	public <T extends JanusPlugin> CompletableFuture<T> attachPlugin(Class<T> pluginClass) {
		String transaction = nextTransactionId();
		String pluginId = connection.getPluginId(pluginClass);
		log.log(Level.FINER, () -> this.logPrefix(transaction) + ", attaching plugin " + pluginId + " with class " +
				pluginClass.getCanonicalName() + "...");
		return execute("attach", transaction, generator -> {
			generator.writeStringField("plugin", pluginId);
		}).thenApply(data -> JanusPlugin.newInstance(pluginClass, this, (Map<String, Object>) data.get("data"))).whenComplete((plugin, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> this.logPrefix(transaction) + ", plugin " + pluginId + " with class " +
						pluginClass.getCanonicalName() + " attachment failed.");
			} else {
				log.log(Level.FINER,
						() -> this.logPrefix(transaction) + ", plugin " + plugin + " attached.");
				attachedPlugins.put(plugin.getHandleId(), plugin);
			}
		});
	}

	public CompletableFuture<Void> detachPlugin(JanusPlugin plugin) {
		String transaction = nextTransactionId();
		log.log(Level.FINER, () -> this.logPrefix(transaction) + ", detaching plugin " + plugin + "...");
		this.attachedPlugins.remove(plugin.getHandleId());
		return execute("detach", transaction, generator -> {
			generator.writeNumberField("handle_id", plugin.getHandleId());
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.WARNING, ex, () -> this.logPrefix(transaction) + ", plugin " + plugin + " detachment failed.");
			} else {
				log.log(Level.FINER,
						() -> this.logPrefix(transaction) + ", plugin " + plugin.toString() + " detached.");
				if (keepAliveFuture != null) {
					keepAliveFuture.cancel(false);
				}
			}
		}).thenApply(x -> null);
	}

	public CompletableFuture<Void> keepAlive() {
		return send("keepalive", nextTransactionId(), generator -> {});
	}

	public void scheduleKeepAlive(ScheduledExecutorService executorService, Duration sessionTimeout) {
		if (keepAliveFuture != null) {
			keepAliveFuture.cancel(false);
		}
		long timeout = sessionTimeout.minusSeconds(5).toMillis();
		keepAliveFuture = executorService.scheduleAtFixedRate(this::keepAlive, timeout, timeout, TimeUnit.MILLISECONDS);
	}

	public CompletableFuture<Void> destroy() {
		CompletableFuture<Void> future = new CompletableFuture<>();
		CompletableFuture.allOf(
				attachedPlugins.values().stream().map(this::detachPlugin).toArray(CompletableFuture[]::new))
				.whenComplete((x, ex) -> {
					if (ex != null) {
						future.completeExceptionally(ex);
					} else {
						connection.destroySession(this).whenComplete((x1, ex1) -> {
							if (ex1 != null) {
								future.completeExceptionally(ex1);
							} else {
								future.complete(null);
							}
						});
					}
				});
		return future;
	}
	
}
