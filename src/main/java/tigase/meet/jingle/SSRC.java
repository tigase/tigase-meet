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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SSRC {
	
	public static SSRC from(Element el) {
		if ("source".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:ssma:0".equals(el.getXMLNS())) {
			String ssrc = el.getAttributeStaticStr("ssrc");
			if (ssrc == null) {
				ssrc = el.getAttributeStaticStr("id");
			}
			if (ssrc == null) {
				return null;
			}

			List<Parameter> parameters = Optional.ofNullable(el.getChildren())
					.orElse(Collections.emptyList())
					.stream()
					.map(Parameter::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			return new SSRC(ssrc, parameters);
		}
		return null;
	}

	public static List<SSRC> from(String[] lines) {
		Stream<String> ssrcs = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=ssrc:"))
				.map(it -> it.substring("a=ssrc:".length()))
				.map(it -> it.split(" ")[0])
				.distinct();
		Optional<String> msid = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=msid:"))
				.map(it -> it.substring("a=msid:".length()))
				.findFirst();
		return ssrcs.map(ssrc -> {
			String prefix = "a=ssrc:" + ssrc;
			List<SSRC.Parameter> parameters = Arrays.stream(lines)
					.filter(it -> it.startsWith(prefix))
					.map(it -> it.substring(prefix.length()))
					.map(it -> it.split(":"))
					.filter(it -> !it[0].trim().isEmpty())
					.map(it -> new SSRC.Parameter(it[0].trim(), Optional.ofNullable(
							it.length == 1 ? null : Arrays.stream(it).skip(1).collect(Collectors.joining(":")))))
					.collect(Collectors.toList());
			if (parameters.stream().filter(it -> "msid".equals(it.getName())).findAny().isEmpty()) {
				msid.ifPresent(value -> {
					parameters.add(new SSRC.Parameter("msid", msid));
				});
			}
			return new SSRC(ssrc, parameters);
		}).collect(Collectors.toList());
	}

	private final String ssrc;
	private final List<Parameter> parameters;
	
	public SSRC(String ssrc, List<Parameter> parameters) {
		this.ssrc = ssrc;
		this.parameters = parameters;
	}

	public String getSsrc() {
		return ssrc;
	}

	public List<Parameter> getParameters() {
		return parameters;
	}

	public Element toElement() {
		Element el = new Element("source");
		el.setXMLNS("urn:xmpp:jingle:apps:rtp:ssma:0");
		el.setAttribute("ssrc", ssrc);
		el.setAttribute("id", ssrc);

		parameters.stream().map(Parameter::toElement).forEach(el::addChild);
		
		return el;
	}

	public List<String> toSDP() {
		return parameters.stream().map(it -> "a=ssrc:" + ssrc + " " + it.toSDP()).collect(Collectors.toList());
	}

	public static class Parameter {

		public static Parameter from(Element el) {
			if ("parameter".equals(el.getName())) {
				String name = el.getAttributeStaticStr("name");
				Optional<String> value = Optional.ofNullable(el.getAttributeStaticStr("value"));
				return new Parameter(name, value);
			}
			return null;
		}

		private final String name;
		private final Optional<String> value;

		public Parameter(String name, Optional<String> value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public Optional<String> getValue() {
			return value;
		}

		public Element toElement() {
			Element el = new Element("parameter");
			el.setAttribute("name", name);
			value.ifPresent(v -> el.setAttribute("value", v));
			return el;
		}

		public String toSDP() {
			if (value.isPresent()) {
				return name + ":" + value.get();
			} else {
				return name;
			}
		}
	}
}
