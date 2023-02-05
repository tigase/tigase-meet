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

import tigase.component.exceptions.ComponentException;
import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.meet.janus.videoroom.Publisher;
import tigase.meet.jingle.*;
import tigase.meet.utils.DelayedRunQueue;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Participation extends AbstractParticipationWithSession<Participation,Meet> {

	private static final Logger log = Logger.getLogger(Participation.class.getCanonicalName());

	private final Content.Creator localPublisherRole = Content.Creator.responder;
	private final Content.Creator localSubscriberRole = Content.Creator.initiator;

	private SDPHolder localPublisherSDP;
	private SDPHolder localSubscriberSDP;
	private SDPHolder remotePublisherSDP;
	private SDPHolder remoteSubscriberSDP;

	private Map<String,Content.Creator> publisherContentCreators = new ConcurrentHashMap<>();
	private Map<String,Content.Creator> subscriberContentCreators = new ConcurrentHashMap<>();

	private Listener listener;

	private final DelayedRunQueue cachedLocalPublisherCandidatesQueue = new DelayedRunQueue();
	private final DelayedRunQueue cachedLocalSubscriberCandidatesQueue = new DelayedRunQueue();

	private final CopyOnWriteArrayList<Publisher> publishers;

	public Participation(Meet meet, JID jid, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		super(meet, jid, localPublisher, localSubscriber);
		publishers = new CopyOnWriteArrayList<>();
	}

	@Override
	public void addedPublishers(Collection<Publisher> publishers) {
		this.publishers.addAll(publishers);
		this.listener.publishersJoined(publishers);
		super.addedPublishers(publishers);
	}

	@Override
	public void removedPublishers(long publisherId) {
		this.publishers.stream().filter(publisher -> publisher.getId() == publisherId).findFirst().ifPresent(publisher -> {
			this.listener.publishersLeft(Collections.singletonList(publisher));
			this.publishers.remove(publisher);
		});
		super.removedPublishers(publisherId);
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

		updatePublisherContentCreators(sdpOffer);

		SDP prevSDP = this.remotePublisherSDP == null ? null : remotePublisherSDP.sdp();
		
		if (prevSDP == null) {
			JSEP jsepOffer = new JSEP(JSEP.Type.offer, sdpOffer.toString("0", Content.Creator.responder, SDP.Direction.incoming));
			this.remotePublisherSDP = new SDPHolder(sdpOffer, jsepOffer);
			return this.sendPublisherSDP(jsepOffer)
					.thenApply(jsepAnswer -> new SDPHolder(SDP.from(jsepAnswer.getSdp(), this::getPublisherContentCreatorFor, Content.Creator.responder), jsepAnswer))
					.whenComplete((sdpHolder, ex) -> {
						synchronized (this) {
							updatePublisherContentCreators(sdpHolder.sdp());
							this.localPublisherSDP = sdpHolder;
							cachedLocalPublisherCandidatesQueue.delayFinished();
						}
					}).thenApply(SDPHolder::sdp);
		} else {
			JSEP jsepOffer = new JSEP(JSEP.Type.offer, prevSDP.applyDiff(action, sdpOffer).toString("0", Content.Creator.responder, SDP.Direction.incoming));
			this.remotePublisherSDP = new SDPHolder(sdpOffer, jsepOffer);
			return this.sendPublisherSDP(jsepOffer)
					.thenApply(jsepAnswer -> new SDPHolder(SDP.from(jsepAnswer.getSdp(), this::getPublisherContentCreatorFor, Content.Creator.responder), jsepAnswer))
					.whenComplete((sdpHolder, ex) -> {
						synchronized (this) {
							updatePublisherContentCreators(sdpHolder.sdp());
							this.localPublisherSDP = sdpHolder;
							cachedLocalPublisherCandidatesQueue.delayFinished();
						}
					}).thenApply(SDPHolder::sdp);
		}
	}

	@Override
	protected synchronized void receivedPublisherSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localPublisherSDP == null ? null : this.localPublisherSDP.sdp();
		SDP currentSDP = SDP.from(jsep.getSdp(), this::getPublisherContentCreatorFor, Content.Creator.responder);
		updatePublisherContentCreators(currentSDP);
		this.localPublisherSDP = new SDPHolder(currentSDP, jsep);
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
			Content content = convertCandidateToContent(Content.Creator.initiator, localPublisherSDP.sdp(), candidate);
			if (content != null) {
				listener.receivedPublisherCandidate(sessionId, content);
			} else {
				log.log(Level.WARNING, () ->"ERROR: it was not possible to convert publisher JanusPlugin.Candidate to Candidate, " + candidate);
			}
		});
	}

	public CompletableFuture<Void> sendSubscriberSDP(String sessionId, ContentAction action, SDP sdpAnswer) {
		if (getSubscriberSessionId().filter(sessionId::equals).isEmpty()) {
			return CompletableFuture.failedFuture(new ComponentException(Authorization.CONFLICT));
		}

		updateSubscriberContentCreators(sdpAnswer);

		SDP prevSDP = this.remoteSubscriberSDP == null ? null : this.remoteSubscriberSDP.sdp();

		if (prevSDP == null) {
			JSEP jsepOffer = new JSEP(JSEP.Type.answer, sdpAnswer.toString("0", Content.Creator.initiator, SDP.Direction.incoming));
			this.remoteSubscriberSDP = new SDPHolder(sdpAnswer, jsepOffer);
			return this.sendSubscriberSDP(jsepOffer);
		} else {
			JSEP jsepOffer = new JSEP(JSEP.Type.answer, prevSDP.applyDiff(action, sdpAnswer).toString("0", Content.Creator.initiator, SDP.Direction.incoming));
			this.remoteSubscriberSDP = new SDPHolder(sdpAnswer, jsepOffer);
			return this.sendSubscriberSDP(jsepOffer);
		}
	}

	@Override
	protected void receivedSubscriberSDP(String sessionId, JSEP jsep) {
		SDP prevSDP = this.localSubscriberSDP == null ? null : this.localSubscriberSDP.sdp();
		SDP currentSDP = SDP.from(jsep.getSdp(), this::getSubscriberContentCreatorFor, Content.Creator.initiator);
		updateSubscriberContentCreators(currentSDP);
		this.localSubscriberSDP = new SDPHolder(currentSDP, jsep);
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
						sendSubscriberSDP(remoteSubscriberSDP.jsep);
					}
				}
			}
		}
	}

	@Override
	protected void receivedSubscriberCandidate(String sessionId, JanusPlugin.Candidate candidate) {
		cachedLocalSubscriberCandidatesQueue.offer(() -> {
			Content content = convertCandidateToContent(Content.Creator.initiator, localSubscriberSDP.sdp(), candidate);
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
				new JanusPlugin.Candidate(contentName, findSdpMLineIndex(remotePublisherSDP.jsep(), contentName),
										  candidate.toSDP()));
	}

	public void sendSubscriberCandidate(String contentName, Candidate candidate) {
		sendSubscriberCandidate(
				new JanusPlugin.Candidate(contentName, findSdpMLineIndex(remoteSubscriberSDP.jsep(), contentName),
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
			log.log(Level.WARNING,
					"content '" + contentName + "' was not found in " + this.getMeet().getJid() + " SDP sent to " +
							getJid());
			idx = 0;
		}

		return idx;
	}

	public void setListener(Listener listener) {
		this.listener = listener;
		setListeners();
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

	protected Content.Creator getPublisherContentCreatorFor(String name) {
		// local session is always responder
		return Optional.ofNullable(publisherContentCreators.get(name)).orElse(Content.Creator.responder);
	}

	protected void updatePublisherContentCreators(SDP sdp) {
		for (Content content : sdp.getContents()) {
			publisherContentCreators.put(content.getName(), content.getCreator());
		}
	}

	protected Content.Creator getSubscriberContentCreatorFor(String name) {
		// local session is always initiator
		return Optional.ofNullable(subscriberContentCreators.get(name)).orElse(Content.Creator.initiator);
	}

	protected void updateSubscriberContentCreators(SDP sdp) {
		for (Content content : sdp.getContents()) {
			subscriberContentCreators.put(content.getName(), content.getCreator());
		}
	}

	public interface Listener {

		void publishersJoined(Collection<Publisher> joined);

		void publishersLeft(Collection<Publisher> left);

		void receivedPublisherSDP(String sessionId, ContentAction action, SDP sdp);

		void receivedPublisherCandidate(String sessionId, Content content);

		void terminatedPublisherSession(String sessionId);

		void receivedSubscriberSDP(String sessionId, ContentAction action, SDP sdp);

		void receivedSubscriberCandidate(String sessionId, Content content);

		void terminatedSubscriberSession(String sessionId);
		
	}

	public record SDPHolder(SDP sdp, JSEP jsep) {

	}
}
