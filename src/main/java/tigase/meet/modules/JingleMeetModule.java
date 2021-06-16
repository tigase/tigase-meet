/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.modules;

import tigase.component.exceptions.ComponentException;
import tigase.component.responses.AsyncCallback;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.meet.*;
import tigase.meet.jingle.*;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Bean(name = "jingleMeetModule", parent = MeetComponent.class, active = true)
public class JingleMeetModule extends AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.name("iq").add(ElementCriteria.name("jingle", "urn:xmpp:jingle:1"));

	private static final String[] FEATURES = new String[]{"tigase:meet:0", "tigase:meet:0:media:audio",
														  "tigase:meet:0:media:video", "urn:xmpp:jingle:1",
														  "urn:xmpp:jingle:apps:rtp:1",
														  "urn:xmpp:jingle:apps:rtp:audio",
														  "urn:xmpp:jingle:apps:rtp:video",
														  "urn:xmpp:jingle:transports:ice-udp:1",
														  "urn:xmpp:jingle:apps:dtls:0"};

	@Inject
	private IMeetLogic logic;

	@Inject
	private IMeetRepository meetRepository;

	@Override
	public String[] getFeatures() {
		return FEATURES;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRITERIA;
	}

	@Override
	public CompletableFuture<Packet> processPacket(Packet packet) throws ComponentException, TigaseStringprepException {
		if (StanzaType.set != packet.getType()) {
			throw new ComponentException(Authorization.BAD_REQUEST);
		}

		BareJID meetJid = packet.getStanzaTo().getBareJID();
		JID from = packet.getStanzaFrom();

		Element jingleEl = Optional.ofNullable(packet.getElemChild("jingle", "urn:xmpp:jingle:1"))
				.orElseThrow(() -> new ComponentException(Authorization.BAD_REQUEST, "Missing jingle action value"));
		String sessionId = Optional.ofNullable(jingleEl.getAttributeStaticStr("sid"))
				.orElseThrow(() -> new ComponentException(Authorization.BAD_REQUEST, "Missing sid"));
		Action action = Optional.ofNullable(jingleEl.getAttributeStaticStr("action"))
				.map(Action::from)
				.orElseThrow(() -> new ComponentException(Authorization.BAD_REQUEST, "Invalid jingle action value"));

		switch (packet.getType()) {
			case set:
				switch (action) {
					case sessionInitiate: {
						log.log(Level.FINEST, () -> "received session-initiate: " + jingleEl.toString());
						SDP sdp = SDP.from(jingleEl);
						return withMeet(meetJid).thenCompose(meet -> logic.checkPermissionFuture(meet, from, IMeetLogic.Action.join)).thenCompose(meet -> meet.join(from)).thenCompose(participation -> {
							participation.setListener(new ParticipationListener(meetJid, participation));

							participation.startPublisherSession(sessionId);
							log.log(Level.FINEST, () -> "sending SDP to Janus: " + sdp.toString("0"));
							return participation.sendPublisherSDP(sessionId, ContentAction.init, sdp).thenApply(result -> {
								log.log(Level.FINEST, "received publisher SDP in completion handler");
								return packet.okResult((String) null, 0);
							}).whenComplete((response, ex) -> {
								if (ex != null) {
									participation.leave(ex);
								}
							});
						});
						}
					case sessionAccept: {
						return withParticipation(meetJid, from).thenApply(participation -> {
							SDP sdp = SDP.from(jingleEl);
							participation.sendSubscriberSDP(sessionId, ContentAction.init, sdp);
							return packet.okResult((String) null, 0);
						});
						}
					case contentAdd:
					case contentModify:
					case contentRemove:
					case contentAccept:
						return withParticipation(meetJid, from).thenApply(participation -> {
							SDP sdp = SDP.from(jingleEl);
							if (sdp != null) {
								ContentAction contentAction = ContentAction.fromJingleAction(action);
								participation.updateSDP(sessionId, contentAction, sdp).whenComplete((sdpAnswer, ex) -> {
									if (ex != null) {
										participation.leave(ex);
									}
								});
							}
							return packet.okResult((String) null, 0);
						});
					case transportInfo:
						// we may need to delay processing of those requests until session-initiate is task is finished as in other case "participation" is not ready yet!
						return withParticipation(meetJid, from).thenApply(participation -> {
							for (Content content : Optional.ofNullable(jingleEl.getChildren())
									.orElse(Collections.emptyList())
									.stream()
									.map(Content::from)
									.filter(Objects::nonNull)
									.collect(Collectors.toList())) {
								content.getTransports().stream().findFirst().ifPresent(transport -> {
									for (Candidate candidate : transport.getCandidates()) {
										participation.sendCandidate(sessionId, content.getName(), candidate);
									}
								});
							}
							return packet.okResult((String) null, 0);
						});
					case sessionTerminate:
						return withParticipation(meetJid, from).thenApply(participation -> {
							// TODO: how to inform session that it was already closed on the remote end? so that it will not send termination request back?
							participation.leave(null);
							return packet.okResult((String) null, 0);
						});
					default:
						throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
				}
			default:
				throw new IllegalStateException("Unexpected value: " + packet.getType());
		}
	}

	private CompletableFuture<Meet> withMeet(BareJID meetJid) throws ComponentException {
		//consumer.apply(meetRepository.getMeet(meetJid));
		return CompletableFuture.completedFuture(meetRepository.getMeet(meetJid));
	}

	private CompletableFuture<Participation> withParticipation(BareJID meetJid, JID sender)
			throws ComponentException {
		return withMeet(meetJid).thenCompose(meet -> {
			Participation participation = meet.getParticipation(sender);
			if (participation == null)  {
				return CompletableFuture.failedFuture(new ComponentException(Authorization.ITEM_NOT_FOUND));
			} else {
				return CompletableFuture.completedFuture(participation);
			}
		});
	}

	private CompletableFuture<Void> sendJingle(BareJID from, JID to, Action action, String sessionId, SDP sdp, Participation participation) {
		return sendJingle(from, to, action,sessionId, sdp).whenComplete((x, ex) -> {
			if (ex != null) {
				participation.leave(ex);
			} else {
				// everything went well, nothing to do..
			}
		});
	}

	private CompletableFuture<Void> sendJingle(BareJID from, JID to, Action action, String sessionId, SDP sdp) {
		Element iqEl = new Element("iq");
		iqEl.setAttribute("id", UUID.randomUUID().toString());
		iqEl.setAttribute("type", StanzaType.set.name());
		iqEl.addChild(sdp.toElement(action, sessionId, JID.jidInstanceNS(from)));

		CompletableFuture<Void> future = new CompletableFuture<>();
		writer.write(new Iq(iqEl, JID.jidInstanceNS(from), to), new AsyncCallback() {

			@Override
			public void onError(Packet packet, String s) {
				future.completeExceptionally(new ComponentException(Authorization.getByCondition(packet.getErrorCondition())));
			}

			@Override
			public void onSuccess(Packet packet) {
				// nothing to do?
				future.complete((Void) null);
			}

			@Override
			public void onTimeout() {
				future.completeExceptionally(new ComponentException(Authorization.REMOTE_SERVER_TIMEOUT));
			}
		});
		return future;
	}
	
	private class ParticipationListener implements Participation.Listener {

		private final BareJID meetJid;
		private final Participation participation;

		public ParticipationListener(BareJID meetJid, Participation participation) {
			this.meetJid = meetJid;
			this.participation = participation;
		}

		@Override
		public void receivedPublisherSDP(String sessionId, ContentAction contentAction,
										 SDP sdp) {
			log.log(Level.FINEST, "received publisher SDP in listener");
			sendJingle(meetJid, participation.getJid(), contentAction.toJingleAction(Action.sessionAccept), sessionId, sdp, participation);
		}

		@Override
		public void receivedPublisherCandidate(String sessionId, Content content) {
			sendJingle(meetJid, participation.getJid(), Action.transportInfo, sessionId, new SDP("", List.of(content), Collections.emptyList()));
		}

		@Override
		public void terminatedPublisherSession(String sessionId) {
			sendJingle(meetJid, participation.getJid(), Action.sessionTerminate, sessionId, new SDP("", Collections.emptyList(), Collections.emptyList()));
		}

		@Override
		public void receivedSubscriberSDP(String sessionId, ContentAction contentAction,
										  SDP sdp) {
			sendJingle(meetJid, participation.getJid(), contentAction.toJingleAction(Action.sessionInitiate), sessionId, sdp, participation);
		}

		@Override
		public void receivedSubscriberCandidate(String sessionId, Content content) {
			sendJingle(meetJid, participation.getJid(), Action.transportInfo, sessionId, new SDP("", List.of(content),
																			   Collections.emptyList()));
		}

		@Override
		public void terminatedSubscriberSession(String sessionId) {
			sendJingle(meetJid, participation.getJid(), Action.sessionTerminate, sessionId, new SDP("", Collections.emptyList(), Collections.emptyList()));
		}
	}
}
