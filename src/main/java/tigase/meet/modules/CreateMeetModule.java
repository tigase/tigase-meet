/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.modules;

import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.meet.IMeetLogic;
import tigase.meet.IMeetRepository;
import tigase.meet.MediaType;
import tigase.meet.MeetComponent;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Bean(name = "createMeetModule", parent = MeetComponent.class, active = true)
public class CreateMeetModule extends AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("create", "tigase:meet:0"));

	private static final String[] FEATURES = new String[] { "tigase:meet:0" };

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

		logic.checkCreatePermission(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom());

		Element createElem = Optional.ofNullable(packet.getElemChild("create", "tigase:meet:0"))
				.orElseThrow(() -> new ComponentException(Authorization.BAD_REQUEST, "Missing `create` element"));
		List<MediaType> mediaTypes = Optional.ofNullable(createElem.mapChildren(el -> "media".equals(el.getName()),
																				el -> MediaType.valueOf(
																						el.getAttributeStaticStr(
																								"type"))))
				.orElse(Collections.emptyList());

		if (!mediaTypes.contains(MediaType.video)) {
			throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED, "Only audio and video media are supported!");
		}

		List<BareJID> allowed = Optional.ofNullable(createElem.mapChildren(el -> "participant".equals(el.getName()),
																		   el -> BareJID.bareJIDInstanceNS(
																				   el.getCData())))
				.orElse(Collections.emptyList());

		return meetRepository.create(BareJID.bareJIDInstance(UUID.randomUUID().toString(), packet.getStanzaTo().getDomain())).thenApply(meet -> {
			meet.allow(packet.getStanzaFrom().getBareJID());
			for (BareJID jid : allowed) {
				meet.allow(jid);
			}
			Element resultCreateElem = new Element("create", "tigase:meet:0");
			resultCreateElem.setAttribute("id", meet.getJid().getLocalpart());
			return packet.okResult(resultCreateElem, 0);
		});
	}
}
