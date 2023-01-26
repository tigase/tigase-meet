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
import tigase.xmpp.jid.JID;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

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

		log.log(Level.FINEST, () -> "user " + packet.getStanzaFrom() + " initiated meet " + meetJid + " destruction");
		return meet.destroy().thenApply(x -> packet.okResult((Element) null, 0)).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.FINEST, ex,
						() -> "meet " + meetJid + " destruction by " + packet.getStanzaFrom() + " failed");
			} else {
				log.log(Level.FINEST,
						() -> "meet " + meetJid + " destruction by " + packet.getStanzaFrom() + " succeeded");
			}
		});
	}
}
