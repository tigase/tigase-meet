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
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.concurrent.CompletableFuture;

public interface IMeetLogic {

	int getDefMaxNoOfPublishers();

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
