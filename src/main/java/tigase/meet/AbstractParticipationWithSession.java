/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.xmpp.jid.JID;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public abstract class AbstractParticipationWithSession<P extends AbstractParticipationWithSession<P,M>, M extends AbstractMeet<P>> extends AbstractParticipation<P,M> {

	private static final Logger log = Logger.getLogger(AbstractParticipationWithSession.class.getCanonicalName());

	private final JID jid;
	private Optional<Session> subscriberSession = Optional.empty();
	private Optional<Session> publisherSession = Optional.empty();

	public AbstractParticipationWithSession(M meet, JID jid, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		super(meet, localPublisher, localSubscriber);
		this.jid = jid;
	}

	public JID getJid() {
		return jid;
	}


	public synchronized Optional<Session> getPublisherSession() {
		return publisherSession;
	}

	public Optional<String> getPublisherSessionId() {
		return publisherSession.map(Session::getId);
	}

	public synchronized Optional<Session> getSubscriberSession() {
		return subscriberSession;
	}

	public Optional<String> getSubscriberSessionId() {
		return subscriberSession.map(Session::getId);
	}

	public synchronized void startSubscriberSession(String subscriberSessionId) {
		this.subscriberSession = Optional.of(new Session(subscriberSessionId));
	}

	public synchronized void startPublisherSession(String publisherSessionId) {
		this.publisherSession = Optional.of(new Session(publisherSessionId));
	}

	public synchronized void terminateSubscriberSession() {
		subscriberSession = Optional.empty();
	}

	public synchronized void terminatePublisherSession() {
		publisherSession = Optional.empty();
	}

	@Override
	public void receivedPublisherSDP(JSEP jsep) {
		getPublisherSessionId().ifPresent(sessionId -> receivedPublisherSDP(sessionId, jsep));
	}

	protected abstract void receivedPublisherSDP(String sesionId, JSEP jsep);

	@Override
	public void receivedPublisherCandidate(JanusPlugin.Candidate candidate) {
		getPublisherSessionId().ifPresent(sessionId -> receivedPublisherCandidate(sessionId, candidate));
	}

	protected abstract void receivedPublisherCandidate(String sessionId, JanusPlugin.Candidate candidate);

	@Override
	public synchronized void receivedSubscriberSDP(JSEP jsep) {
		if (getSubscriberSessionId().isEmpty()) {
			startSubscriberSession(UUID.randomUUID().toString());
		}
		getSubscriberSessionId().ifPresent(sessionId -> receivedSubscriberSDP(sessionId, jsep));
	}

	protected abstract void receivedSubscriberSDP(String sessionId, JSEP jsep);

	@Override
	public void receivedSubscriberCandidate(JanusPlugin.Candidate candidate) {
		getSubscriberSessionId().ifPresent(sessionId -> receivedSubscriberCandidate(sessionId, candidate));
	}

	protected abstract void receivedSubscriberCandidate(String sessionId, JanusPlugin.Candidate candidate);

	@Override
	public synchronized CompletableFuture<Void> leave(Throwable ex) {
		terminatePublisherSession();
		terminateSubscriberSession();
		return super.leave(ex);
	}

	@Override
	public String toString() {
		return "AbstractParticipationWithSession{" + "jid=" + jid + ", " + "meet=" + getMeet()  + '}';
	}
}
