/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public abstract class AbstractParticipation<P extends AbstractParticipation<P,M>, M extends AbstractMeet<P>> implements LocalPublisher.Listener, LocalSubscriber.Listener {

	private static final Logger log = Logger.getLogger(AbstractParticipation.class.getCanonicalName());

	private final M meet;
	protected final LocalPublisher publisher;
	protected final LocalSubscriber subscriber;

	public AbstractParticipation(M meet, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		this.meet = meet;
		this.publisher = localPublisher;
		this.subscriber = localSubscriber;
		this.subscriber.setListener(this);
		this.publisher.setListener(this);
	}

	public M getMeet() {
		return meet;
	}

	@Override
	public void addedPublishers(Collection<Publisher> publishers) {
		subscriber.subscribe(publishers.stream()
									 .map(p -> new JanusVideoRoomPlugin.Stream(p.getId(), null))
									 .collect(Collectors.toList())).thenAccept(jsep -> {
			receivedSubscriberSDP(jsep);
		});
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
		meet.left((P) this);
		return CompletableFuture.allOf(publisher.leave()).thenCompose(x -> publisher.getSession().destroy());
	}

	@Override
	public String toString() {
		return "AbstractParticipation{" + "meet=" + meet + '}';
	}
}
