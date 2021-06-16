/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.concurrent.CompletableFuture;

public interface IMeetLogic {

	void checkCreatePermission(BareJID meetJid, JID senderJID) throws ComponentException;

	void checkPermission(Meet meet, JID senderJID, Action action) throws ComponentException;

	default CompletableFuture<Meet> checkPermissionFuture(Meet meet, JID senderJID, Action action) {
		try {
			checkPermission(meet, senderJID, action);
			return CompletableFuture.completedFuture(meet);
		} catch (ComponentException ex) {
			return CompletableFuture.failedFuture(ex);
		}
	}

	enum Action {
		join,
		destroy
	}
}
