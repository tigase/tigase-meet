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
