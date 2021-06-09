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
import tigase.meet.IMeetRepository;
import tigase.meet.Meet;
import tigase.meet.MeetComponent;
import tigase.meet.Participation;
import tigase.meet.jingle.Action;
import tigase.meet.jingle.Candidate;
import tigase.meet.jingle.Content;
import tigase.meet.jingle.SDP;
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
import java.util.stream.Collectors;

@Bean(name = "jingleMeetModule", parent = MeetComponent.class, active = true)
public class JingleMeetModule extends AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.name("iq").add(ElementCriteria.name("jingle", "urn:xmpp:jingle:1"));
	
	@Inject
	private IMeetRepository meetRepository;

	@Override
	public Criteria getModuleCriteria() {
		return CRITERIA;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
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
					case sessionInitiate:
						JID initiator = Optional.ofNullable(jingleEl.getAttributeStaticStr("initiator"))
								.map(JID::jidInstanceNS).orElse(from);

						List<Content> contents = Optional.ofNullable(jingleEl.getChildren())
								.orElse(Collections.emptyList())
								.stream()
								.map(Content::from)
								.filter(Objects::nonNull)
								.collect(Collectors.toList());
						List<Element> groupChildren = Optional.ofNullable(
								jingleEl.getChild("group", "urn:xmpp:jingle:apps:grouping:0"))
								.map(it -> it.getChildren())
								.orElse(Collections.EMPTY_LIST);
						List<String> bundle = groupChildren.stream()
								.filter(it -> "content".equals(it.getName()))
								.map(it -> it.getAttributeStaticStr("name"))
								.filter(Objects::nonNull)
								.collect(Collectors.toList());

						SDP sdp = new SDP(String.valueOf(new Date().getTime()), contents, bundle);

						this.withMeet(meetJid, meet -> {
							meet.join(from).thenAccept(participation -> {
								participation.setListener(new Participation.Listener() {

									@Override
									public void receivedPublisherSDP(String sessionId, SDP sdp) {
										sendJingle(meetJid, from, Action.sessionAccept, sessionId, sdp.getContents(), sdp.getBundle(), participation);
									}

									@Override
									public void receivedPublisherCandidate(String sessionId, Content content) {
										sendJingle(meetJid, from, Action.transportInfo, sessionId, List.of(content), Collections.emptyList());
									}

									@Override
									public void terminatedPublisherSession(String sessionId) {
										sendJingle(meetJid, from, Action.sessionTerminate, sessionId, Collections.emptyList(), Collections.emptyList());
									}

									@Override
									public void receivedSubscriberSDP(String sessionId, SDP sdp) {
										sendJingle(meetJid, from, Action.sessionInitiate, sessionId, sdp.getContents(), sdp.getBundle(), participation);
									}

									@Override
									public void receivedSubscriberCandidate(String sessionId, Content content) {
										sendJingle(meetJid, from, Action.transportInfo, sessionId, List.of(content), Collections.emptyList());
									}

									@Override
									public void terminatedSubscriberSession(String sessionId) {
										sendJingle(meetJid, from, Action.sessionTerminate, sessionId, Collections.emptyList(), Collections.emptyList());
									}
								});

								participation.startPublisherSession(sessionId);
								participation.sendPublisherSDP(sessionId, sdp ).whenComplete((sdpAnswer, ex) -> {
									if (ex != null) {
										participation.leave();
										sendExeception(packet, new ComponentException(Authorization.NOT_ACCEPTABLE, ex.getMessage(), ex));
									} else {
										writer.write(packet.okResult((String) null, 0));
										sendJingle(meetJid, from, Action.sessionInitiate, sessionId,
												   sdpAnswer.getContents(), sdpAnswer.getBundle(), participation);
									}
								});
							});
						});
						break;
					case transportInfo:
						withMeet(meetJid, meet -> {
							Participation participation = Optional.ofNullable(meet.getParticipation(from))
									.orElseThrow(() -> new ComponentException(Authorization.ITEM_NOT_FOUND));
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
						});
					case sessionTerminate:
						withMeet(meetJid, meet -> {
							Participation participation = Optional.ofNullable(meet.getParticipation(from))
									.orElseThrow(() -> new ComponentException(Authorization.ITEM_NOT_FOUND));
							// TODO: how to inform session that it was already closed on the remote end? so that it will not send termination request back?
							participation.leave();
						});
						break;
					default:
						throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
				}
				break;
			default:
				throw new IllegalStateException("Unexpected value: " + packet.getType());
		}
	}

	private void withMeet(BareJID meetJid, ConsumerThrowingComponentException<Meet> consumer) throws ComponentException {
		consumer.apply(meetRepository.getMeet(meetJid));
	}

	private void sendJingle(BareJID from, JID to, Action action, String sessionId, List<Content> contents, List<String> bundle, Participation participation) {
		sendJingle(from, to, action,sessionId, contents, bundle).whenComplete((x, ex) -> {
			if (ex != null) {
				participation.leave();
			} else {
				// everything went well, nothing to do..
			}
		});
	}

//	private void sendJingleTransportCandidate(BareJID from, JID to, String sessionId, Content.Creator role, JSEP jsep, JanusPlugin.Candidate janusCandidate) {
//		Candidate candidate = Candidate.from(janusCandidate.getCandidate());
//		if (candidate == null) {
//			return;
//		}
//		SDP sdp = SDP.from(jsep.getSdp(), role);
//		if (sdp == null) {
//			return;
//		}
//		Optional<Transport> transport = sdp.getContents()
//				.stream()
//				.filter(c -> janusCandidate.getMid().equals(c.getName()))
//				.findFirst()
//				.flatMap(it -> it.getTransports().stream().findFirst());
//
//		if (transport.isEmpty()) {
//			return;
//		}
//
//		Content content = new Content(role, janusCandidate.getMid(), Optional.empty(),
//									  List.of(new Transport(transport.get().getUfrag(), transport.get().getPwd(),
//															List.of(candidate), Optional.empty())));
//		sendJingle(from, to, JingleAction.transportInfo, sessionId, List.of(content), Collections.emptyList());
//	}

	private CompletableFuture<Void> sendJingle(BareJID from, JID to, Action action, String sessionId, List<Content> contents, List<String> bundle) {
		Element iqEl = new Element("iq");
		iqEl.setAttribute("type", StanzaType.set.name());
		iqEl.withElement("jingle", "urn:xmpp:jingle:1", jingleEl -> {
			jingleEl.setAttribute("action", action.getValue());
			jingleEl.setAttribute("sid", sessionId);
			switch (action) {
				case sessionInitiate:
					jingleEl.setAttribute("initiator", from.toString());
					break;
				case sessionAccept:
					jingleEl.setAttribute("responder", from.toString());
					break;
				default:
					break;
			}
			contents.stream().map(Content::toElement).forEach(jingleEl::addChild);
			if (!bundle.isEmpty()) {
				jingleEl.withElement("group", "urn:xmpp:jingle:apps:grouping:0", groupEl -> {
					groupEl.setAttribute("semantics", "BUNDLE");
					bundle.stream()
							.map(name -> new Element("content", new String[]{"name"}, new String[]{name}))
							.forEach(groupEl::addChild);
				});
			}
		});

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

	@FunctionalInterface
	public interface ConsumerThrowingComponentException<T> {

		void apply(T value) throws ComponentException;

	}
}
