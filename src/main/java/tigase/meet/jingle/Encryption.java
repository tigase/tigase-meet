/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
