/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.component.exceptions.ComponentException;
import tigase.meet.janus.JanusConnection;
import tigase.util.common.TimerTask;
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

	private final MeetRepository repository;
	private final BareJID jid;

	private TimerTask timeoutTask;

	public Meet(MeetRepository repository, JanusConnection janusConnection, Object roomId, BareJID jid) {
		super(janusConnection, roomId);
		this.repository = repository;
		this.jid = jid;

		this.timeoutTask = this.repository.scheduleJoinTimeoutTask(this);
	}

	public BareJID getJid() {
		return jid;
	}

	public void allow(BareJID jid) {
		allowed.add(jid);
	}

	public void deny(BareJID jid) {
		allowed.remove(jid);
	}

	public boolean isPublic() {
		return isAllowed(ALLOW_EVERYONE);
	}

	public boolean isAllowed(BareJID jid) {
		return allowed.contains(ALLOW_EVERYONE) || allowed.contains(jid);
	}

	public CompletableFuture<Participation> join(JID jid) {
		if (participationByJid.contains(jid)) {
		    return CompletableFuture.failedFuture(new ComponentException(Authorization.CONFLICT));
		}
		return join((publisher, subscriber) -> new Participation(this, jid, publisher, subscriber)).whenComplete((participation, ex) -> {
			if (ex == null) {
				this.participationByJid.put(participation.getJid(), participation);
				this.cancelTimeoutTask();
			}
		});
	}

	@Override
	public CompletableFuture<Void> destroy() {
		return super.destroy().thenAccept(x -> repository.destroyed(jid)).thenAccept(x -> {
			this.cancelTimeoutTask();
		});
	}

	public Participation getParticipation(JID jid) {
		return participationByJid.get(jid);
	}

	public void left(Participation participation) {
		this.participationByJid.remove(participation.getJid());
		if (this.participationByJid.isEmpty()) {
			destroy();
		}
	}

	public boolean hasParticipants() {
		return !this.participationByJid.isEmpty();
	}

	private synchronized void cancelTimeoutTask() {
		TimerTask timerTask = this.timeoutTask;
		this.timeoutTask = null;
		if (timerTask != null) {
			timerTask.cancel();
		}
	}
}
