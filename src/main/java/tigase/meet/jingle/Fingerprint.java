/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.Arrays;
import java.util.Optional;

public class Fingerprint {

	public static class FingerprintData {
		public static Optional<FingerprintData> from(String[] lines) {
			return Arrays.stream(lines)
					.filter(it -> it.startsWith("a=fingerprint:"))
					.map(it -> it.substring("a=fingerprint:".length()).split(" "))
					.filter(it -> it.length >= 2)
					.findFirst().map(it -> new FingerprintData(it[0], it[1]));
		}

		private final String hash;
		private final String value;

		public FingerprintData(String hash, String value) {
			this.hash = hash;
			this.value = value;
		}

		public String getHash() {
			return hash;
		}

		public String getValue() {
			return value;
		}
	}

	public static Fingerprint from(Element el) {
		if ("fingerprint".equals(el.getName()) && "urn:xmpp:jingle:apps:dtls:0".equals(el.getXMLNS())) {
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
		actpass, active, passive;

		public static Optional<Setup> from(String[] lines) {
			return Arrays.stream(lines)
					.filter(it -> it.startsWith("a=setup:"))
					.findFirst()
					.map(it -> it.substring("a=setup:".length()))
					.map(Fingerprint.Setup::valueOf);
		}
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
