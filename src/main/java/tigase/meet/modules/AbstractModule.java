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
package tigase.meet.modules;

import tigase.component.exceptions.ComponentException;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public abstract class AbstractModule extends tigase.component.modules.AbstractModule {

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		processPacket(packet).whenComplete((result, ex) -> {
			if (ex != null) {
				sendExeception(packet, ex);
			} else {
				writer.write(result);
			}
		});
	}

	public abstract CompletableFuture<Packet> processPacket(Packet packet) throws ComponentException, TigaseStringprepException;

	public ComponentException convertThrowable(Throwable ex) {
		return (ex instanceof ComponentException)
			   ? ((ComponentException) ex)
			   : new ComponentException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
	}

	public void sendExeception(Packet packet, Throwable ex) {
		try {
			StanzaType t = packet.getType();
			if (t == StanzaType.error) {
				if (this.log.isLoggable(Level.FINER)) {
					this.log.log(Level.FINER, packet.getElemName() + " stanza already with type='error' ignored", ex);
				}

				return;
			}

			this.log.log(Level.FINEST, () -> "Sending back exception for " + packet.toString() + ", exception:\n" + serializeException(ex));

			Packet result = convertThrowable(ex).makeElement(packet, true);
			if (this.log.isLoggable(Level.FINEST)) {
				this.log.log(Level.FINEST, "Sending back: " + result.toString());
			}

			this.writer.write(result);
		} catch (Exception var5) {
		}
	}

	private String serializeException(Throwable ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		return sw.toString();
	}
}
