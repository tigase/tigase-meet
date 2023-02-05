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
import java.util.logging.Level;

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

		BareJID meetJid = BareJID.bareJIDInstance(UUID.randomUUID().toString(), packet.getStanzaTo().getDomain());
		log.log(Level.FINEST,
				() -> "user " + packet.getStanzaFrom() + " initiated meet creation with jid " + meetJid + ", media: " +
						mediaTypes + ", and allowed: " + allowed);

		return meetRepository.create(meetJid, logic.getDefMaxNoOfPublishers()).thenApply(meet -> {
			meet.allow(packet.getStanzaFrom().getBareJID());
			for (BareJID jid : allowed) {
				meet.allow(jid);
			}
			Element resultCreateElem = new Element("create" );
			resultCreateElem.setXMLNS("tigase:meet:0");
			resultCreateElem.setAttribute("id", meet.getJid().getLocalpart());
			return packet.okResult(resultCreateElem, 0);
		}).whenComplete((x, ex) -> {
			if (ex != null) {
				log.log(Level.FINEST, ex,
						() -> "meet " + meetJid + " creation by " + packet.getStanzaFrom() + " failed");
			} else {
				log.log(Level.FINEST,
						() -> "meet " + meetJid + " creation by " + packet.getStanzaFrom() + " succeeded");
			}
		});
	}
}
