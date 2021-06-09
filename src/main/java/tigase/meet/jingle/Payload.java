/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.*;
import java.util.stream.Collectors;

public class Payload {

	public static Payload from(Element el) {
		if ("payload-type".equals(el.getName())) {
			String id = el.getAttributeStaticStr("id");
			if (id == null) {
				return null;
			}
			String channels = el.getAttributeStaticStr("channels");
			Optional<Integer> clockrate = Optional.ofNullable(el.getAttributeStaticStr("clockrate")).map(Integer::parseInt);
			Optional<Integer> ptime = Optional.ofNullable(el.getAttributeStaticStr("ptime")).map(Integer::parseInt);
			Optional<Integer> maxptime = Optional.ofNullable(el.getAttributeStaticStr("maxptime")).map(Integer::parseInt);
			Optional<String> name = Optional.ofNullable(el.getAttributeStaticStr("name"));

			List<Parameter> parameters = Optional.ofNullable(el.getChildren())
					.orElse(Collections.emptyList())
					.stream()
					.map(Parameter::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			List<RtcpFeedback> rtcpFeedbacks = Optional.ofNullable(el.getChildren())
					.orElse(Collections.emptyList())
					.stream()
					.map(RtcpFeedback::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			;

			return new Payload(Integer.parseInt(id), channels == null ? 1 : Integer.parseInt(channels), clockrate, maxptime, name, ptime, parameters, rtcpFeedbacks);
		}
		return null;
	}

	private final int id;
	private final int channels;
	private final Optional<Integer> clockrate;
	private final Optional<Integer> maxptime;
	private final Optional<String> name;
	private final Optional<Integer> ptime;
	private final List<Parameter> parameters;
	private final List<RtcpFeedback> rtcpFeedback;

	public Payload(int id, int channels, Optional<Integer> clockrate, Optional<Integer> maxptime, Optional<String> name, Optional<Integer> ptime, List<Parameter> parameters, List<RtcpFeedback> rtcpFeedbacks) {
		this.id = id;
		this.channels = channels;
		this.clockrate = clockrate;
		this.maxptime = maxptime;
		this.name = name;
		this.ptime = ptime;
		this.parameters = parameters;
		this.rtcpFeedback= rtcpFeedbacks;
	}

	public int getId() {
		return id;
	}

	public int getChannels() {
		return channels;
	}

	public Optional<Integer> getClockrate() {
		return clockrate;
	}

	public Optional<Integer> getMaxptime() {
		return maxptime;
	}

	public Optional<String> getName() {
		return name;
	}

	public Optional<Integer> getPtime() {
		return ptime;
	}

	public List<Parameter> getParameters() {
		return parameters;
	}

	public List<RtcpFeedback> getRtcpFeedback() {
		return rtcpFeedback;
	}

	public Element toElement() {
		Element el = new Element("payload-type");
		el.setAttribute("id", String.valueOf(id));
		if (channels != 1) {
			el.setAttribute("channels", String.valueOf(channels));
		}
		name.ifPresent(name -> el.setAttribute("name", name));
		clockrate.ifPresent(clockrate -> el.setAttribute("clockrate", String.valueOf(clockrate)));
		ptime.ifPresent(ptime -> el.setAttribute("ptime", String.valueOf(ptime)));
		maxptime.ifPresent(maxptime -> el.setAttribute("maxptime", String.valueOf(maxptime)));
		parameters.stream().map(Parameter::toElement).forEach(el::addChild);
		rtcpFeedback.stream().map(RtcpFeedback::toElement).forEach(el::addChild);
		return el;
	}

	public List<String> toSDP() {
		StringBuilder line = new StringBuilder("a=rtpmap:").append(id).append(" ").append(name).append("/").append(clockrate);
		if (channels > 1) {
			line.append("/").append(channels);
		}

		List<String> lines = new ArrayList<>();
		lines.add(line.toString());
		if (!parameters.isEmpty()) {
			lines.add("a=fmtp:" + id + " " + parameters.stream().map(Parameter::toSDP).collect(Collectors.joining(";")));
		}
		rtcpFeedback.stream().map(it -> {
			StringBuilder sb = new StringBuilder("a=rtcp-fb:").append(id).append(" ").append(it.type);
			it.getSubtype().ifPresent(subtype -> sb.append(" ").append(subtype));
			return sb.toString();
		}).forEach(lines::add);

		return lines;
	}

	public static class Parameter {

		public static Parameter from(String sdp) {
			String[] parts = sdp.split("=");
			return new Parameter(parts[0], parts.length > 1 ? parts[1] : "");
		}

		public static Parameter from(Element el) {
			if ("parameter".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:1".equals(el.getXMLNS())) {
				String name = el.getAttributeStaticStr("name");
				String value = el.getAttributeStaticStr("value");
				if (name == null || value == null) {
					return null;
				}
				return new Parameter(name, value);
			}
			return null;
		}

		private final String name;
		private final String value;

		public Parameter(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		public Element toElement() {
			return new Element("parameter", new String[]{"xmlns", "name", "value"},
							   new String[]{"urn:xmpp:jingle:apps:rtp:1", name, value});
		}

		public String toSDP() {
			return name + "="+value;
		}
	}

	public static class RtcpFeedback {

		public static RtcpFeedback from(Element el) {
			if ("rtcp-fb".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:rtcp-fb:0".equals(el.getXMLNS())) {
				String type = el.getAttributeStaticStr("type");
				if (type == null) {
					return null;
				}
				return new RtcpFeedback(type, Optional.ofNullable(el.getAttributeStaticStr("subtype")));
			}
			return null;
		}

		public static RtcpFeedback from(String sdp) {
			String[] parts = sdp.split(" ");
			return new RtcpFeedback(parts[0], Optional.ofNullable(parts.length > 1 ? parts[1] : null));
		}

		private final String type;
		private final Optional<String> subtype;

		public RtcpFeedback(String type, Optional<String> subtype) {
			this.type = type;
			this.subtype = subtype;
		}

		public String getType() {
			return type;
		}

		public Optional<String> getSubtype() {
			return subtype;
		}

		public Element toElement() {
			Element el = new Element("rtcp-fb", new String[]{"xmlns", "type"},
							   new String[]{"urn:xmpp:jingle:apps:rtp:rtcp-fb:01", type});
			subtype.ifPresent(subtype -> el.setAttribute("subtype", subtype));
			return el;
		}
	}
}
