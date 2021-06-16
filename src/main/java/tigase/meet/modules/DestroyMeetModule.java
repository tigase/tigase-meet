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
import tigase.xmpp.jid.JID;

import java.util.concurrent.CompletableFuture;

@Bean(name = "destroyMeetModule", parent = MeetComponent.class, active = true)
public class DestroyMeetModule extends AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("destroy", "tigase:meet:0"));

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

		BareJID meetJid = packet.getStanzaTo().getBareJID();
		JID from = packet.getStanzaFrom();

		Meet meet = meetRepository.getMeet(meetJid);

		logic.checkPermission(meet, from, IMeetLogic.Action.destroy);

		return meet.destroy().thenApply(x -> packet.okResult((Element) null, 0));
	}
}
