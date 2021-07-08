/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.meet.jingle.*;
import tigase.meet.utils.DelayedRunQueue;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Participation extends AbstractParticipationWithSession<Participation,Meet> {

	private static final Logger log = Logger.getLogger(Participation.class.getCanonicalName());

	private SDP localPublisherSDP;
	private SDP localSubscriberSDP;
	private JSEP remotePublisherSDP;
	private JSEP remoteSubscriberSDP;

	private Listener listener;

	private final DelayedRunQueue cachedLocalPublisherCandidatesQueue = new DelayedRunQueue();
	private final DelayedRunQueue cachedLocalSubscriberCandidatesQueue = new DelayedRunQueue();


	public Participation(Meet meet, JID jid, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		super(meet, jid, localPublisher, localSubscriber);
	}

	public synchronized void terminateSubscriberSession() {
		getSubscriberSessionId().ifPresent(sessionId -> listener.terminatedSubscriberSession(sessionId));
		super.terminateSubscriberSession();
	}

	public synchronized void terminatePublisherSession() {
		getPublisherSessionId().ifPresent(sessionId -> listener.terminatedPublisherSession(sessionId));
		super.terminatePublisherSession();
	}

	public CompletableFuture<SDP> sendPublisherSDP(String sessionId, ContentAction action, SDP sdpOffer) {
		if (getPublisherSessionId().filter(sessionId::equals).isEmpty()) {
			return CompletableFuture.failedFuture(new ComponentException(Authorization.CONFLICT));
		}

		SDP prevSDP = this.remotePublisherSDP == null
					  ? null
					  : SDP.from(this.remotePublisherSDP.getSdp(), Content.Creator.initiator);
		
		if (prevSDP == null) {
			JSEP jsepOffer = new JSEP(JSEP.Type.offer, sdpOffer.toString("0"));
			this.remotePublisherSDP = jsepOffer;
			return this.sendPublisherSDP(jsepOffer)
					.thenApply(jsepAnswer -> SDP.from(jsepAnswer.getSdp(), Content.Creator.responder))
					.whenComplete((answerSDP, ex) -> {
						synchronized (this) {
							this.localPublisherSDP = answerSDP;
							cachedLocalPublisherCandidatesQueue.delayFinished();
						}
					});
		} else {
			JSEP jsepOffer = new JSEP(JSEP.Type.offer, prevSDP.applyDiff(action, sdpOffer).toString("0"));
			this.remotePublisherSDP = jsepOffer;
			return this.sendPublisherSDP(jsepOffer)
					.thenApply(jsepAnswer -> SDP.from(jsepAnswer.getSdp(), Content.Creator.responder))
					.whenComplete((answerSDP, ex) -> {
						synchronized (this) {
							this.localPublisherSDP = answerSDP;
							cachedLocalPublisherCandidatesQueue.delayFinished();
						}
					});
		}
	}

	@Override
	protected synchronized void receivedPublisherSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localPublisherSDP;
		SDP currentSDP = SDP.from(jsep.getSdp(), Content.Creator.responder);
		this.localPublisherSDP = currentSDP;
		if (prevSDP == null) {
			listener.receivedPublisherSDP(sessionId, ContentAction.init, currentSDP);
			cachedLocalPublisherCandidatesQueue.delayFinished();
		} else {
			// we need to calculate and post notifications
			Map<ContentAction, SDP> results = currentSDP.diffFrom(prevSDP);
			for (ContentAction action : ContentAction.values()) {
				SDP sdp = results.get(action);
				if (sdp != null) {
					listener.receivedPublisherSDP(sessionId, action, sdp);
				}
			}
		}
	}

	@Override
	protected synchronized void receivedPublisherCandidate(String sessionId, JanusPlugin.Candidate candidate) {
		cachedLocalPublisherCandidatesQueue.offer(() -> {
			Content content = convertCandidateToContent(Content.Creator.initiator, localPublisherSDP, candidate);
			if (content != null) {
				listener.receivedPublisherCandidate(sessionId, content);
			} else {
				log.log(Level.WARNING, "ERROR: it was not possible to convert publisher JanusPlugin.Candidate to Candidate, " + candidate);
			}
		});
	}

	public CompletableFuture<Void> sendSubscriberSDP(String sessionId, ContentAction action, SDP sdpAnswer) {
		if (getSubscriberSessionId().filter(sessionId::equals).isEmpty()) {
			return CompletableFuture.failedFuture(new ComponentException(Authorization.CONFLICT));
		}

		SDP prevSDP = this.remoteSubscriberSDP == null
					  ? null
					  : SDP.from(this.remoteSubscriberSDP.getSdp(), Content.Creator.initiator);

		if (prevSDP == null) {
			JSEP jsepOffer = new JSEP(JSEP.Type.answer, sdpAnswer.toString("0"));
			this.remoteSubscriberSDP = jsepOffer;
			return this.sendSubscriberSDP(jsepOffer);
		} else {
			JSEP jsepOffer = new JSEP(JSEP.Type.answer, prevSDP.applyDiff(action, sdpAnswer).toString("0"));
			this.remoteSubscriberSDP = jsepOffer;
			return this.sendSubscriberSDP(jsepOffer);
		}
	}

	@Override
	protected void receivedSubscriberSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localSubscriberSDP;
		SDP currentSDP = SDP.from(jsep.getSdp(), Content.Creator.initiator);
		this.localSubscriberSDP = currentSDP;
		if (prevSDP == null) {
			listener.receivedSubscriberSDP(sessionId, ContentAction.init, currentSDP);
			cachedLocalSubscriberCandidatesQueue.delayFinished();
		} else {
			// we need to calculate and post notifications
			Map<ContentAction, SDP> results = currentSDP.diffFrom(prevSDP);
			for (ContentAction action : ContentAction.values()) {
				SDP sdp = results.get(action);
				if (sdp != null) {
					listener.receivedSubscriberSDP(sessionId, action, sdp);
					if (action == ContentAction.modify)  {
						sendSubscriberSDP(remoteSubscriberSDP);
					}
				}
			}
		}
	}

	@Override
	protected void receivedSubscriberCandidate(String sessionId, JanusPlugin.Candidate candidate) {
		cachedLocalSubscriberCandidatesQueue.offer(() -> {
			Content content = convertCandidateToContent(Content.Creator.initiator, localSubscriberSDP, candidate);
			if (content != null) {
				listener.receivedSubscriberCandidate(sessionId, content);
			} else {
				log.log(Level.WARNING, "ERROR: it was not possible to convert subscriber JanusPlugin.Candidate to Candidate, " + candidate);
			}
		});
	}

	public void sendCandidate(String sessionId, String contentName, Candidate candidate) {
		if (getPublisherSessionId().filter(sessionId::equals).isPresent()) {
			sendPublisherCandidate(contentName,candidate);
			return;
		}
		if (getSubscriberSessionId().filter(sessionId::equals).isPresent()) {
			sendSubscriberCandidate(contentName, candidate);
			return;
		}
	}

	public void sendPublisherCandidate(String contentName, Candidate candidate) {
		sendPublisherCandidate(
				new JanusPlugin.Candidate(contentName, findSdpMLineIndex(remotePublisherSDP, contentName),
										  candidate.toSDP()));
	}

	public void sendSubscriberCandidate(String contentName, Candidate candidate) {
		sendSubscriberCandidate(
				new JanusPlugin.Candidate(contentName, findSdpMLineIndex(remoteSubscriberSDP, contentName),
										  candidate.toSDP()));
	}

	public CompletableFuture<Void> updateSDP(String sessionId, ContentAction action, SDP sdp) {
		if (getPublisherSessionId().filter(sessionId::equals).isPresent()) {
			return sendPublisherSDP(sessionId, action, sdp).thenApply(x -> null);
		}
		if (getSubscriberSessionId().filter(sessionId::equals).isPresent()) {
			return sendSubscriberSDP(sessionId, action, sdp);
		}
		return CompletableFuture.failedFuture(new ComponentException(Authorization.ITEM_NOT_FOUND));
	}


	protected int findSdpMLineIndex(JSEP jsep, String contentName) {
		String[] lines = jsep.getSdp().split("\r\n");
		List<String> contents = Arrays.stream(lines).filter(it -> it.startsWith("a=mid:")).collect(Collectors.toList());

		int idx = contents.indexOf("a=mid:" + contentName);
		if (idx == -1) {
			idx = 0;
		}

		return idx;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	protected Content convertCandidateToContent(Content.Creator role, SDP sdp, JanusPlugin.Candidate janusCandidate) {
		if (sdp == null) {
			return null;
		}
		Candidate candidate = Candidate.from(janusCandidate.getCandidate());
		if (candidate == null) {
			return null;
		}

		String mid = Optional.ofNullable(janusCandidate.getMid())
				.or(() -> sdp.getContents().stream().map(Content::getName).findFirst())
				.get();
		Optional<Transport> transport = sdp.getContents()
				.stream()
				.filter(c -> mid.equals(c.getName()))
				.findFirst()
				.flatMap(it -> it.getTransports().stream().findFirst());

		if (transport.isEmpty()) {
			return null;
		}

		return new Content(role, mid, Optional.empty(), Optional.empty(),
						   List.of(new Transport(transport.get().getUfrag(), transport.get().getPwd(),
												 List.of(candidate), Optional.empty())));
	}

	public interface Listener {

		void receivedPublisherSDP(String sessionId, ContentAction action, SDP sdp);

		void receivedPublisherCandidate(String sessionId, Content content);

		void terminatedPublisherSession(String sessionId);

		void receivedSubscriberSDP(String sessionId, ContentAction action, SDP sdp);

		void receivedSubscriberCandidate(String sessionId, Content content);

		void terminatedSubscriberSession(String sessionId);
		
	}

}
