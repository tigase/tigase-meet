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
