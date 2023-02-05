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
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.Optional;

public class Encryption {

	public static Encryption from(Element el) {
		if ("crypto".equals(el.getName())) {
			String cryptoSuite = el.getAttributeStaticStr("crypto-suite");
			String keyParams = el.getAttributeStaticStr("key-params");
			Optional<String> sessionParams = Optional.ofNullable(el.getAttributeStaticStr("session-params"));
			String tag = el.getAttributeStaticStr("tag");
			if (cryptoSuite == null || keyParams == null || tag == null) {
				return null;
			}
			return new Encryption(cryptoSuite, keyParams,sessionParams, tag);
		}
		return null;
	}

	private final String cryptoSuite;
	private final String keyParams;
	private final Optional<String> sessionParams;
	private final String tag;

	public Encryption(String cryptoSuite, String keyParams, Optional<String> sessionParams, String tag) {
		this.cryptoSuite = cryptoSuite;
		this.keyParams = keyParams;
		this.sessionParams = sessionParams;
		this.tag = tag;
	}

	public String getCryptoSuite() {
		return cryptoSuite;
	}

	public String getKeyParams() {
		return keyParams;
	}

	public Optional<String> getSessionParams() {
		return sessionParams;
	}

	public String getTag() {
		return tag;
	}

	public Element toElement() {
		Element el = new Element("crypto");
		el.setAttribute("crypto-suite", cryptoSuite);
		el.setAttribute("key-params", keyParams);
		sessionParams.ifPresent(sessionParams -> el.setAttribute("session-params", sessionParams));
		el.setAttribute("tag", tag);
		return el;
	}

	public String toSDP() {
		StringBuilder sb = new StringBuilder("a=crypto:").append(tag).append(" ").append(cryptoSuite).append(" ").append(keyParams);
		sessionParams.ifPresent(it -> sb.append(" ").append(it));
		return sb.toString();
	}
}
