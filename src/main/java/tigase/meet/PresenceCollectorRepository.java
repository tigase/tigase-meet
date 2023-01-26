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
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "presenceCollectorRepository", parent = MeetComponent.class, active = true)
public class PresenceCollectorRepository implements IPresenceRepository {

	private static final Logger log = Logger.getLogger(PresenceCollectorRepository.class.getCanonicalName());

	private ConcurrentHashMap<BareJID,MeetPresences> meetParticipantPresences = new ConcurrentHashMap<>();

	@Inject
	private EventBus eventBus;

	public void addJid(BareJID meetJid, JID jid) throws ComponentException {
		MeetPresences meetPresences = meetParticipantPresences.get(meetJid);
		if (meetPresences == null) {
			log.log(Level.FINEST, () -> "not marking " + jid + " as available for " + meetJid + " - meet doesn't exist!");
			throw new ComponentException(Authorization.ITEM_NOT_FOUND, "There is no meeting for this JID");
		}

		meetPresences.addJid(jid);
	}

	public void removeJid(BareJID meetJid, JID jid) throws ComponentException {
		MeetPresences meetPresences = meetParticipantPresences.get(meetJid);
		if (meetPresences == null) {
			log.log(Level.FINEST, () ->"not marking " + jid + " as unavailable for " + meetJid + " - meet doesn't exist!");
			throw new ComponentException(Authorization.ITEM_NOT_FOUND, "There is no meeting for this JID");
		}

		meetPresences.removeJid(jid);
		eventBus.fire(new UserDisappearedEvent(meetJid, jid));
	}

	@Override
	public boolean isAvailable(BareJID meetJid, JID jid) {
		MeetPresences meetPresences = meetParticipantPresences.get(meetJid);
		if (meetPresences == null) {
			return false;
		}

		return meetPresences.isAvailable(jid);
	}

	public void meetCreated(BareJID meetJid) {
		log.log(Level.FINEST, () -> "creating presence store for " + meetJid);
		meetParticipantPresences.put(meetJid, new MeetPresences());
	}

	public void meetDestroyed(BareJID meetJid) {
		log.log(Level.FINEST, () -> "destroying presence store for " + meetJid);
		meetParticipantPresences.remove(meetJid);
	}

	private class MeetPresences {

		private final CopyOnWriteArraySet<JID> availableJids = new CopyOnWriteArraySet<>();

		public void addJid(JID jid) {
			availableJids.add(jid);
		}

		public void removeJid(JID jid) {
			availableJids.remove(jid);
		}

		public boolean isAvailable(JID jid) {
			return availableJids.contains(jid);
		}
	}

}
