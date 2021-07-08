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
import tigase.meet.MeetComponent;
import tigase.meet.PresenceCollectorRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.StanzaType;

@Bean(name = "presenceCollectorModule", parent = MeetComponent.class, active = true)
public class PresenceCollectorModule extends tigase.component.modules.AbstractModule {

	private static final Criteria CRITERIA = ElementCriteria.name("presence");

	@Inject
	private PresenceCollectorRepository presenceRepository;

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		if (packet.getType() == null || packet.getType() == StanzaType.available) {
			presenceRepository.addJid(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom());
		} else {
			presenceRepository.removeJid(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom());
		}
		Packet response = packet.copyElementOnly().swapStanzaFromTo();
		write(response);
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRITERIA;
	}
}
