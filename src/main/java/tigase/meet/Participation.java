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
import java.util.stream.Collectors;

public class Participation
		implements LocalPublisher.Listener, LocalSubscriber.Listener {

	private final LocalPublisher publisher;
	private final LocalSubscriber subscriber;

	private Listener listener;

	public Participation(LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		this.publisher = localPublisher;
		this.subscriber = localSubscriber;
	}

	public LocalPublisher getPublisher() {
		return publisher;
	}

	public LocalSubscriber getSubscriber() {
		return subscriber;
	}

	@Override
	public void addedPublishers(Collection<Publisher> publishers) {
		subscriber.subscribe(publishers.stream()
									 .map(p -> new JanusVideoRoomPlugin.Stream(p.getId(), null))
									 .collect(Collectors.toList())).thenAccept(jsep -> {
			System.out.println("received new SDP!");
		});
	}

	@Override
	public void removedPublishers(long publisherId) {
		subscriber.unsubscribe(publisherId);
	}

	@Override
	public void receivedPublisherSDP(JSEP jsep) {
		listener.receivedPublisherSDP(jsep);
	}

	@Override
	public void receivedPublisherCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedPublisherCandidate(candidate);
	}

	public CompletableFuture<Void> sendPublisherSDP(JSEP offer) {
		return this.getPublisher().publish(offer).thenAccept(answer -> {
			this.listener.receivedPublisherSDP(answer);
		});
	}

	public CompletableFuture<Void> sendPublisherCandidate(JanusPlugin.Candidate candidate) {
		return this.getPublisher().sendCandidate(candidate);
	}

	public void setListener(Listener listener) {
		this.listener = listener;
		this.subscriber.setListener(this);
		this.publisher.setListener(this);
	}

	public CompletableFuture<Void> sendSubscriberCandidate(JanusPlugin.Candidate candidate) {
		return this.getSubscriber().sendCandidate(candidate);
	}

	@Override
	public void receivedSubscriberSDP(JSEP jsep) {
		listener.receivedSubscriberSDP(jsep);
	}

	@Override
	public void receivedSubscriberCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedSubscriberCandidate(candidate);
	}

	public interface Listener {

		void receivedPublisherSDP(JSEP jsep);

		void receivedPublisherCandidate(JanusPlugin.Candidate candidate);

		void receivedSubscriberSDP(JSEP jsep);

		void receivedSubscriberCandidate(JanusPlugin.Candidate candidate);

	}
}
