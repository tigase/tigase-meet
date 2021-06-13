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

public class Description {

	public static Description from(Element el) {
		if ("description".equals(el.getName()) && "urn:xmpp:jingle:apps:rtp:1".equals(el.getXMLNS())) {
			String media = el.getAttributeStaticStr("media");
			if (media == null) {
				return null;
			}
			Optional<String> ssrc = Optional.ofNullable(el.getAttributeStaticStr("ssrc"));
			List<Element> children = Optional.ofNullable(el.getChildren()).orElse(Collections.emptyList());
			List<Payload> payloads = children.stream()
					.map(Payload::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			Optional<String> bandwidth = Optional.ofNullable(el.getChild("bandwidth"))
					.map(it -> it.getAttributeStaticStr("type"));
			boolean rtcpMux = el.getChild("rtcp-mux") != null;
			List<Encryption> encryptions = Optional.ofNullable(el.getChild("encryption"))
					.map(it -> it.getChildren())
					.orElse(Collections.emptyList())
					.stream()
					.map(Encryption::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			List<SSRC> ssrcs = children.stream().map(SSRC::from).filter(Objects::nonNull).collect(Collectors.toList());
			List<SSRCGroup> ssrcGroups = children.stream()
					.map(SSRCGroup::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			List<HdrExt> hdrExts = children.stream()
					.map(HdrExt::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			return new Description(media, ssrc, payloads, bandwidth, encryptions, rtcpMux, ssrcs, ssrcGroups, hdrExts);
		}
		return null;
	}

	public enum Senders {
		initiator, responder, both
	}

	private final String media;
	private final Optional<String> ssrc;
	private final List<Payload> payloads;
	private final Optional<String> bandwidth;
	private final List<Encryption> encryptions;
	private final boolean rtcpMux;
	private final List<SSRC> ssrcs;
	private final List<SSRCGroup> ssrcGroups;
	private final List<HdrExt> hdrExts;

	public Description(String media, Optional<String> ssrc, List<Payload> payloads, Optional<String> bandwidth,
					   List<Encryption> encryptions, boolean rtcpMux, List<SSRC> ssrcs, List<SSRCGroup> ssrcGroups,
					   List<HdrExt> hdrExts) {
		this.media = media;
		this.ssrc = ssrc;
		this.payloads = payloads;
		this.bandwidth = bandwidth;
		this.encryptions = encryptions;
		this.rtcpMux = rtcpMux;
		this.ssrcs = ssrcs;
		this.ssrcGroups = ssrcGroups;
		this.hdrExts = hdrExts;
	}

	public String getMedia() {
		return media;
	}

	public Optional<String> getSsrc() {
		return ssrc;
	}

	public List<Payload> getPayloads() {
		return payloads;
	}

	public Optional<String> getBandwidth() {
		return bandwidth;
	}

	public List<Encryption> getEncryptions() {
		return encryptions;
	}

	public boolean isRtcpMux() {
		return rtcpMux;
	}

	public List<SSRC> getSsrcs() {
		return ssrcs;
	}

	public List<SSRCGroup> getSsrcGroups() {
		return ssrcGroups;
	}

	public List<HdrExt> getHdrExts() {
		return hdrExts;
	}

	public Element toElement() {
		Element el = new Element("description");
		el.setXMLNS("urn:xmpp:jingle:apps:rtp:1");
		el.setAttribute("media", media);
		if (rtcpMux) {
			el.addChild(new Element("rtcp-mux"));
		}
		ssrc.ifPresent(ssrc -> el.setAttribute("ssrc", ssrc));
		payloads.stream().map(Payload::toElement).forEach(el::addChild);
		if (!encryptions.isEmpty()) {
			Element encryption = new Element("encryption");
			encryptions.stream().map(Encryption::toElement).forEach(encryption::addChild);
			el.addChild(encryption);
		}
		ssrcGroups.stream().map(SSRCGroup::toElement).forEach(el::addChild);
		ssrcs.stream().map(SSRC::toElement).forEach(el::addChild);
		hdrExts.stream().map(HdrExt::toElement).forEach(el::addChild);
		return el;
	}
}