/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

public class Fingerprint {

	public static Fingerprint from(Element el) {
		if ("fingerprint".equals(el.getName()) && "urn:xmpp:jingle:apps:dtls:0".equals(el.getName())) {
			String hash = el.getAttributeStaticStr("hash");
			String value = el.getCData();
			String setup = el.getAttributeStaticStr("setup");
			if (hash == null || value == null || setup == null) {
				return null;
			}
			return new Fingerprint(hash, value, Setup.valueOf(setup));
		}
		return null;
	}

	public enum Setup {
		actpass, active, passive
	}

	private final String hash;
	private final String value;
	private final Setup setup;

	public Fingerprint(String hash, String value, Setup setup) {
		this.hash = hash;
		this.value = value;
		this.setup = setup;
	}

	public String getHash() {
		return hash;
	}

	public String getValue() {
		return value;
	}

	public Setup getSetup() {
		return setup;
	}

	public Element toElement() {
		Element el = new Element("fingerprint", value);
		el.setXMLNS("urn:xmpp:jingle:apps:dtls:0");
		el.setAttribute("hash", hash);
		el.setAttribute("setup", setup.name());
		return el;
	}
}
