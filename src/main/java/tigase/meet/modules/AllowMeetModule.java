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
