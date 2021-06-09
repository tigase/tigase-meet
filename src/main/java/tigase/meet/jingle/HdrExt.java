/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HdrExt {

	public static HdrExt from(Element el) {
		if ("rtp-hdrext".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0".equals(el.getXMLNS())) {
			String id = el.getAttributeStaticStr("id");
			String uri = el.getAttributeStaticStr("uri");
			String senders = el.getAttributeStaticStr("senders");
			if (id == null || uri == null) {
				return null;
			}
			return new HdrExt(id, uri,
							  senders == null ? Description.Senders.both : Description.Senders.valueOf(senders));
		}
		return null;
	}

	public static List<HdrExt> from(String[] lines) {
		return Arrays.stream(lines)
				.filter(it -> it.startsWith("a=extmap:"))
				.map(it -> it.substring("a=extmap:".length()))
				.map(it -> it.split(" "))
				.filter(it -> it.length > 1 && !it[0].contains("/"))
				.map(it -> new HdrExt(it[0], it[1], Description.Senders.both))
				.collect(Collectors.toList());
	}

	private final String id;
	private final String uri;
	private final Description.Senders senders;

	public HdrExt(String id, String uri, Description.Senders senders) {
		this.id = id;
		this.uri = uri;
		this.senders = senders;
	}

	public String getId() {
		return id;
	}

	public String getUri() {
		return uri;
	}

	public Description.Senders getSenders() {
		return senders;
	}

	public Element toElement() {
		Element el = new Element("rtp-hdrext");
		el.setXMLNS("urn:xmpp:jingle:apps:rtp:rtp-hdrext:0");
		el.setAttribute("id", id);
		el.setAttribute("uri", uri);

		if (senders != Description.Senders.both) {
			el.setAttribute("senders", senders.name());
		}

		return el;
	}

	public String toSDP() {
		return "a=extmap:" + id + " " + uri;
	}

}
