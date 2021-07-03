/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

@Bean(name = "meetLogic", parent = MeetComponent.class, active = true)
public class DefaultMeetLogic implements IMeetLogic {

	@Inject(bean = "service")
	private MeetComponent meetComponent;

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
}