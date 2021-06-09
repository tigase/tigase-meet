/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.xmpp.jid.BareJID;

import java.util.concurrent.CompletableFuture;

public interface IMeetRepository {

	CompletableFuture<Meet> create(BareJID jid) throws ComponentException;

	Meet getMeet(BareJID jid) throws ComponentException;

}
