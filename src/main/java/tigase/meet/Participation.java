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
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Participation extends AbstractParticipationWithSession<Participation,Meet> {

	private SDP localPublisherSDP;
	private SDP localSubscriberSDP;
	private JSEP remotePublisherSDP;
	private JSEP remoteSubscriberSDP;

	private Listener listener;

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
					.thenApply(jsepAnswer -> SDP.from(jsepAnswer.getSdp(), Content.Creator.responder));
		} else {
			JSEP jsepOffer = new JSEP(JSEP.Type.offer, prevSDP.applyDiff(action, sdpOffer).toString("0"));
			this.remotePublisherSDP = jsepOffer;
			return this.sendPublisherSDP(jsepOffer)
					.thenApply(jsepAnswer -> SDP.from(jsepAnswer.getSdp(), Content.Creator.responder));
		}
	}
	
	@Override
	protected void receivedPublisherSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localPublisherSDP;
		SDP currentSDP = SDP.from(jsep.getSdp(), Content.Creator.responder);
		this.localPublisherSDP = currentSDP;
		if (prevSDP == null) {
			listener.receivedPublisherSDP(sessionId, ContentAction.init, currentSDP);
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
	protected void receivedPublisherCandidate(String sessionId, JanusPlugin.Candidate candidate) {
		Content content = convertCandidateToContent(Content.Creator.initiator, localPublisherSDP, candidate);
		if (content != null) {
			listener.receivedSubscriberCandidate(sessionId, content);
		}
	}

	@Override
	protected void receivedSubscriberSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localSubscriberSDP;
		SDP currentSDP = SDP.from(jsep.getSdp(), Content.Creator.initiator);
		this.localSubscriberSDP = currentSDP;
		if (prevSDP == null) {
			listener.receivedSubscriberSDP(sessionId, ContentAction.init, currentSDP);
		} else {
			// we need to calculate and post notifications
			Map<ContentAction, SDP> results = currentSDP.diffFrom(prevSDP);
			for (ContentAction action : ContentAction.values()) {
				SDP sdp = results.get(action);
				if (sdp != null) {
					listener.receivedSubscriberSDP(sessionId, action, sdp);
				}
			}
		}
	}

	@Override
	protected void receivedSubscriberCandidate(String sessionId, JanusPlugin.Candidate candidate) {
		Content content = convertCandidateToContent(Content.Creator.initiator, localSubscriberSDP, candidate);
		if (content != null) {
			listener.receivedSubscriberCandidate(sessionId, content);
		}
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
		sendPublisherCandidate(
				new JanusPlugin.Candidate(contentName, findSdpMLineIndex(remoteSubscriberSDP, contentName),
										  candidate.toSDP()));
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
		Optional<Transport> transport = sdp.getContents()
				.stream()
				.filter(c -> janusCandidate.getMid().equals(c.getName()))
				.findFirst()
				.flatMap(it -> it.getTransports().stream().findFirst());

		if (transport.isEmpty()) {
			return null;
		}

		return new Content(role, janusCandidate.getMid(), Optional.empty(), Optional.empty(),
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

	public enum ContentAction {
		init,
		add,
		remove,
		modify;

		public static ContentAction fromJingleAction(Action action) {
			switch (action) {
				case sessionInitiate:
					return init;
				case contentAdd:
					return add;
				case contentRemove:
					return remove;
				case contentModify:
					return modify;
				default:
					return null;
			}
		}
	}
}
