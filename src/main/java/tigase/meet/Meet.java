/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.meet.janus.JanusConnection;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class Meet extends AbstractMeet<Participation> {

	public static final BareJID ALLOW_EVERYONE = BareJID.bareJIDInstanceNS("*");

	private Set<BareJID> allowed = new CopyOnWriteArraySet();
	// we should have maps for incoming and outgoing sessions!
	private final ConcurrentHashMap<JID, Participation> participationByJid = new ConcurrentHashMap<>();

	public Meet(JanusConnection janusConnection, Object roomId) {
		super(janusConnection, roomId);
	}

	public void allow(BareJID jid) {
		allowed.add(jid);
	}

	public void deny(BareJID jid) {
		allowed.remove(jid);
	}

	public boolean isAllowed(BareJID jid) {
		return allowed.contains(ALLOW_EVERYONE) || allowed.contains(jid);
	}

	public CompletableFuture<Participation> join(JID jid) {
		if (participationByJid.contains(jid)) {
		    return CompletableFuture.failedFuture(new ComponentException(Authorization.CONFLICT));
		}
		if (!isAllowed(jid.getBareJID())) {
			return CompletableFuture.failedFuture(new ComponentException(Authorization.NOT_ALLOWED));
		}

		return join((publisher, subscriber) -> new Participation(this, jid, publisher, subscriber)).whenComplete((participation, ex) -> {
			if (ex == null) {
				this.participationByJid.put(participation.getJid(), participation);
			}
		});
	}

	public Participation getParticipation(JID jid) {
		return participationByJid.get(jid);
	}

	public void left(Participation participation) {
		this.participationByJid.remove(participation.getJid(), participation);
	}
	
}
