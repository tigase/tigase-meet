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
import tigase.meet.Meet;
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
import java.util.concurrent.CompletableFuture;

@Bean(name = "allowMeetModule", parent = MeetComponent.class, active = true)
public class AllowMeetModule extends AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.name("iq").add(ElementCriteria.name("allow", "tigase:meet:0"));

	@Inject
	private IMeetLogic logic;

	@Inject
	private IMeetRepository meetRepository;
	
	@Override
	public CompletableFuture<Packet> processPacket(Packet packet) throws ComponentException, TigaseStringprepException {
		if (StanzaType.set != packet.getType()) {
			throw new ComponentException(Authorization.BAD_REQUEST);
		}

		Meet meet = meetRepository.getMeet(packet.getStanzaTo().getBareJID());

		Element allowElem = Optional.ofNullable(packet.getElemChild("allow", "tigase:meet:0"))
				.orElseThrow(() -> new ComponentException(Authorization.BAD_REQUEST, "Missing `allow` element"));

		List<BareJID> allowed = Optional.ofNullable(allowElem.mapChildren(el -> "participant".equals(el.getName()),
																		  el -> BareJID.bareJIDInstanceNS(
																				  el.getCData())))
				.orElse(Collections.emptyList());

		for (BareJID jid : allowed) {
			meet.allow(jid);
		}

		return CompletableFuture.completedFuture(packet.okResult((String) null, 0));
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRITERIA;
	}
}