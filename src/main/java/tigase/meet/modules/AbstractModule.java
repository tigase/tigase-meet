/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.modules;

import tigase.component.exceptions.ComponentException;
import tigase.server.Packet;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

public abstract class AbstractModule extends tigase.component.modules.AbstractModule {

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
			ComponentException e = (ex instanceof ComponentException)
								   ? ((ComponentException) ex)
								   : new ComponentException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);

			Packet result = e.makeElement(packet, true);
			if (this.log.isLoggable(Level.FINEST)) {
				this.log.log(Level.FINEST, "Sending back: " + result.toString());
			}

			this.writer.write(result);
		} catch (Exception var5) {
			if (this.log.isLoggable(Level.WARNING)) {
				this.log.log(Level.WARNING, "Problem during generate error response", var5);
			}
		}
	}

	private String serializeException(Throwable ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		return sw.toString();
	}
}
