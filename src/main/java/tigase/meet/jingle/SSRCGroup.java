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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SSRCGroup {

	public static SSRCGroup from(Element el) {
		if ("ssrc-group".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.getXMLNS())) {
			String semantics = el.getAttributeStaticStr("semantics");
			if (semantics == null) {
				return null;
			}

			List<String> sources = Optional.ofNullable(
					el.mapChildren(it -> "source".equals(it.getName()), it -> it.getAttributeStaticStr("ssrc")))
					.orElse(Collections.emptyList());
			return new SSRCGroup(semantics, sources);
		}
		return null;
	}

	public static List<SSRCGroup> from(String[] lines) {
		return Arrays.stream(lines)
				.filter(it -> it.startsWith("a=ssrc-group:"))
				.map(it -> it.substring("a=ssrc-group:".length()))
				.map(it -> it.split(" "))
				.filter(it -> it.length >= 2)
				.map(it -> new SSRCGroup(it[0], Arrays.stream(it).skip(1).collect(Collectors.toList())))
				.collect(Collectors.toList());
	}

	private final String semantics;
	private final List<String> sources;

	public SSRCGroup(String semantics, List<String> sources) {
		this.semantics = semantics;
		this.sources = sources;
	}

	public String getSemantics() {
		return semantics;
	}

	public List<String> getSources() {
		return sources;
	}

	public Element toElement() {
		Element el = new Element("ssrc-group");
		el.setXMLNS("urn:xmpp:jingle:apps:rtp:ssma:0");
		el.setAttribute("semantics", semantics);

		sources.stream()
				.map(source -> new Element("source", new String[]{"ssrc"}, new String[]{source}))
				.forEach(el::addChild);

		return el;
	}

	public String toSDP() {
		return Stream.concat(Stream.of("a=ssrc-group:" + semantics), sources.stream()).collect(Collectors.joining(" "));
	}
}
