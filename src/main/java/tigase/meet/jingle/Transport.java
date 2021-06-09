/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Transport {

	public static Transport from(Element el) {
		if ("transport".equals(el.getName())) {
			Optional<String> ufrag = Optional.ofNullable(el.getAttributeStaticStr("ufrag"));
			Optional<String> pwd = Optional.ofNullable(el.getAttributeStaticStr("pwd"));
			List<Element> children = Optional.ofNullable(el.getChildren()).orElse(Collections.emptyList());
			Optional<Fingerprint> fingerprint = children.stream()
					.map(Fingerprint::from)
					.filter(Objects::nonNull)
					.findFirst();
			List<Candidate> candidates = children.stream()
					.map(Candidate::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			return new Transport(ufrag, pwd, candidates, fingerprint);
		}
		return null;
	}

	private final Optional<String> ufrag;
	private final Optional<String> pwd;
	private final List<Candidate> candidates;
	private final Optional<Fingerprint> fingerprint;

	public Transport(Optional<String> ufrag, Optional<String> pwd, List<Candidate> candidates,
					 Optional<Fingerprint> fingerprint) {
		this.ufrag = ufrag;
		this.pwd = pwd;
		this.candidates = candidates;
		this.fingerprint = fingerprint;
	}

	public Optional<String> getUfrag() {
		return ufrag;
	}

	public Optional<String> getPwd() {
		return pwd;
	}

	public List<Candidate> getCandidates() {
		return candidates;
	}

	public Optional<Fingerprint> getFingerprint() {
		return fingerprint;
	}

	public Element toElement() {
		Element el = new Element("transport");
		el.setXMLNS("urn:xmpp:jingle:transports:ice-udp:1");
		fingerprint.map(Fingerprint::toElement).ifPresent(el::addChild);
		candidates.stream().map(Candidate::toElement).forEach(el::addChild);
		ufrag.ifPresent(ufrag -> el.setAttribute("ufrag", ufrag));
		pwd.ifPresent(pwd -> el.setAttribute("pwd", pwd));
		return el;
	}
}
