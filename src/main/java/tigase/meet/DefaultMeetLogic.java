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
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

@Bean(name = "meetLogic", parent = MeetComponent.class, active = true)
public class DefaultMeetLogic implements IMeetLogic, Initializable, UnregisterAware {

	@Inject(bean = "service")
	private MeetComponent meetComponent;
	@Inject
	private EventBus eventBus;
	@Inject
	private IMeetRepository meetRepository;

	@ConfigField(desc = "Maximal no. of publishers for each meeting")
	private Integer maxNoOfPublishers = 6;

	@Override
	public void checkCreatePermission(BareJID meetJid, JID senderJID) throws ComponentException {
		if (!meetJid.getDomain().equals(meetComponent.getName() + "." + senderJID.getDomain())) {
			throw new ComponentException(Authorization.FORBIDDEN);
		}
	}

	@Override
	public void checkPermission(Meet meet, JID senderJID, Action action) throws ComponentException {
		if (meet.isPublic()) {
			return;
		} else {
			if (!meet.isAllowed(senderJID.getBareJID())) {
				throw new ComponentException(Authorization.FORBIDDEN, "You are not authorized to " + action + " this meeting.");
			}
		}
	}

	@Override
	public int getDefMaxNoOfPublishers() {
		return maxNoOfPublishers;
	}

	@Override
	public void initialize() {
		eventBus.registerAll(this);
	}

	@Override
	public void beforeUnregister() {
		if (eventBus != null) {
			eventBus.unregisterAll(this);
		}
	}

	@HandleEvent
	public void userDisappeared(IPresenceRepository.UserDisappearedEvent event) {
		try {
			Meet meet = meetRepository.getMeet(event.getMeetJid());
			Participation participation = meet.getParticipation(event.getJid());
			if (participation != null) {
				participation.leave(null);
			}
		} catch (ComponentException ex) {
			
		}
	}
}
