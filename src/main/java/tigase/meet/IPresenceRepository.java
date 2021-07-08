/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

public interface IPresenceRepository {

	boolean isAvailable(BareJID meetJid, JID jid);

	class UserDisappearedEvent {

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
