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

import tigase.eventbus.EventBusEvent;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

public interface IPresenceRepository {

	boolean isAvailable(BareJID meetJid, JID jid);

	static class UserDisappearedEvent implements EventBusEvent {

		private final BareJID meetJid;
		private final JID jid;

		public UserDisappearedEvent(BareJID meetJid, JID jid) {
			this.meetJid = meetJid;
			this.jid = jid;
		}

		public BareJID getMeetJid() {
			return meetJid;
		}

		public JID getJid() {
			return jid;
		}
		
	}
}
