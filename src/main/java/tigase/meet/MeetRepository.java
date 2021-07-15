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
import tigase.kernel.beans.config.ConfigField;
import tigase.meet.janus.JanusService;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.server.AbstractMessageReceiver;
import tigase.stats.StatisticsList;
import tigase.util.common.TimerTask;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "meetRepository", parent = MeetComponent.class, active = true)
public class MeetRepository implements IMeetRepository {

	private static final Logger log = Logger.getLogger(MeetRepository.class.getCanonicalName());
	private final ConcurrentHashMap<BareJID, CompletableFuture<Meet>> meets = new ConcurrentHashMap<>();

	@ConfigField(
			desc = "Bean name"
	)
	private String name;

	@Inject(bean = "service")
	private AbstractMessageReceiver component;
	@Inject
	private JanusService janusService;
	@Inject(nullAllowed = true)
	private PresenceCollectorRepository presenceCollectorRepository;

	private final CounterWithSupplier meetsCounter = new CounterWithSupplier("active meetings", Level.FINEST,
																			 () -> (long) this.size());

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public CompletableFuture<Meet> create(BareJID key, int maxNoOfPublishers) {
		CompletableFuture<Meet> future = new CompletableFuture<>();
		if (meets.putIfAbsent(key, future) != null) {
			future.completeExceptionally(new ComponentException(Authorization.CONFLICT));
			return future;
		}

		createMeet(key, maxNoOfPublishers).whenComplete((meet,ex) -> {
			if (ex != null) {
				meets.remove(key, future);
				future.completeExceptionally(ex);
			} else {
				if (presenceCollectorRepository != null) {
					presenceCollectorRepository.meetCreated(meet.getJid());
				}
				future.complete(meet);
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
		if (presenceCollectorRepository != null) {
			presenceCollectorRepository.meetDestroyed(jid);
		}
		log.log(Level.FINEST, () -> "meet " + jid  + " was destroyed");
	}

	public int size() {
		return meets.size();
	}
	
	@Override
	public void everyHour() {
		meetsCounter.everyHour();
	}

	@Override
	public void everyMinute() {
		meetsCounter.everyMinute();
	}

	@Override
	public void everySecond() {
		meetsCounter.everySecond();
	}

	@Override
	public void getStatistics(String s, StatisticsList statisticsList) {
		meetsCounter.getStatistics(component.getName() + "/" + getName(), statisticsList);
	}

	private CompletableFuture<Meet> createMeet(BareJID meetJid, int maxNoOfPublishers) {
		return janusService.newConnection()
				.thenCompose(connection -> connection.createSession()
						.thenCompose(session -> session.attachPlugin(JanusVideoRoomPlugin.class)
								.thenCompose(videoRoomPlugin -> videoRoomPlugin.createRoom(null, maxNoOfPublishers))
								.exceptionallyCompose(
										ex -> session.destroy().handle((x, ex1) -> CompletableFuture.failedFuture(ex))))
						// we should close this session even it is not connected? or maybe we should store it?
						.thenApply(roomId -> {
							log.log(Level.FINEST, () -> "meet " + meetJid  + " was created");
							return new Meet(this, connection, roomId, meetJid);
						})
						.exceptionallyCompose(ex -> {
							connection.close();
							return CompletableFuture.failedFuture(ex);
						}));
	}

	protected TimerTask scheduleJoinTimeoutTask(Meet meet) {
		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				if (!meet.hasParticipants()) {
					meet.destroy();
				}
			}
		};
		component.addTimerTask(timerTask, 5 * 60 * 1000);
		return timerTask;
	}

	private class CounterWithSupplier {
		private final Supplier<Long> valueSupplier;

		protected final Level level;
		protected String name;

		private long last_hour_counter = 0L;
		private long last_minute_counter = 0L;
		private long last_second_counter = 0L;
		private long maximum = 0l;

		public CounterWithSupplier(String name, Level level, Supplier<Long> valueSupplier) {
			this.name = name;
			this.level = level;
			this.valueSupplier = valueSupplier;
		}

		public synchronized void everyHour() {
			long counter = this.valueSupplier.get();
			this.last_hour_counter = counter;
		}

		public synchronized void everyMinute() {
			long counter = this.valueSupplier.get();
			this.last_minute_counter = counter;
		}

		public synchronized void everySecond() {
			long counter = this.valueSupplier.get();
			this.last_second_counter = counter;
			this.maximum = Math.max(counter, this.maximum);
		}

		public void getStatistics(String compName, StatisticsList list) {
			if (list.checkLevel(this.level)) {
				list.add(compName, this.name + " total", this.valueSupplier.get(), this.level);
				list.add(compName, this.name + " last hour", this.last_hour_counter, this.level);
				list.add(compName, this.name + " last minute", this.last_minute_counter, this.level);
				list.add(compName, this.name + " last second", this.last_second_counter, this.level);
				list.add(compName, this.name + " maximum", this.maximum, this.level);
			}
		}
	}
}
