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
		participationByJid.entrySet()
				.stream()
				.filter(e -> jid.equals(e.getKey().getBareJID()))
				.map(e -> e.getValue())
				.forEach(participation -> participation.leave(null));
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
		return join(jid.getBareJID().toString(), (publisher, subscriber) -> new Participation(this, jid, publisher, subscriber)).whenComplete((participation, ex) -> {
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

	@Override
	public boolean left(Participation participation) {
		this.participationByJid.remove(participation.getJid());
		if (this.participationByJid.isEmpty()) {
			destroy();
			return false;
		}
		return true;
	}

	public boolean hasParticipants() {
		return !this.participationByJid.isEmpty();
	}

	public int getParticipantsCount() {
		return participationByJid.size();
	}

	private synchronized void cancelTimeoutTask() {
		TimerTask timerTask = this.timeoutTask;
		this.timeoutTask = null;
		if (timerTask != null) {
			timerTask.cancel();
		}
	}
}
