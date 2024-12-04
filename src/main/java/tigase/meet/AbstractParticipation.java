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

import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.meet.janus.videoroom.Publisher;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class AbstractParticipation<P extends AbstractParticipation<P,M>, M extends AbstractMeet<P>> implements LocalPublisher.Listener, LocalSubscriber.Listener {

	private static final Logger log = Logger.getLogger(AbstractParticipation.class.getCanonicalName());

	private final M meet;
	protected final LocalPublisher publisher;
	protected final LocalSubscriber subscriber;

	/**
	 * Constructor of the class which stores publisher and subscriber.
	 *
	 * Remember to call `setListeners()` method to initialize listeners.
	 * @param meet
	 * @param localPublisher
	 * @param localSubscriber
	 */
	public AbstractParticipation(M meet, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		this.meet = meet;
		this.publisher = localPublisher;
		this.subscriber = localSubscriber;
	}

	public M getMeet() {
		return meet;
	}

	public void setListeners() {
		this.subscriber.setListener(this);
		this.publisher.setListener(this);
	}

	@Override
	public void addedPublishers(Collection<Publisher> publishers) {
		subscriber.subscribe(publishers.stream()
									 .flatMap(p -> p.getStreams()
											 .stream()
											 .map(s -> new JanusVideoRoomPlugin.Stream(p.getId(), s.getMid())))
									 .collect(Collectors.toList()));
	}

	@Override
	public void removedPublishers(long publisherId) {
		subscriber.unsubscribe(publisherId);
	}
	
	public CompletableFuture<JSEP> sendPublisherSDP(JSEP offer) {
		return publisher.publish(offer).whenComplete((x, ex) -> {
			if (ex != null) {
				leave(ex);
			}
		});
	}

	public CompletableFuture<Void> sendPublisherCandidate(JanusPlugin.Candidate candidate) {
		return this.publisher.sendCandidate(candidate);
	}

	public CompletableFuture<Void> sendSubscriberCandidate(JanusPlugin.Candidate candidate) {
		return this.subscriber.sendCandidate(candidate);
	}

	public CompletableFuture<Void> sendSubscriberSDP(JSEP answer) {
		return subscriber.start(answer).whenComplete((x, ex) -> {
			if (ex != null) {
				leave(ex);
			}
		});
	}

	public synchronized CompletableFuture<Void> leave(Throwable ex) {
		if (ex != null) {
			log.log(Level.WARNING, ex, () -> "participation " + toString() + " leaving due to error");
		}
		if (meet.left((P) this)) {
			return publisher.unpublish()
					.thenCompose(x -> new CompletableFuture<>().completeOnTimeout(x, 1, TimeUnit.SECONDS))
					.thenCompose(x -> publisher.leave())
					.thenCompose(x -> publisher.getSession().destroy());
		} else {
			return CompletableFuture.completedFuture(null);
		}
	}

	@Override
	public String toString() {
		return "AbstractParticipation{" + "meet=" + meet + '}';
	}
}
