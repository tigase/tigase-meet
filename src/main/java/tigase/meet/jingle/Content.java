/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Content {

	public static Content from(Element el) {
		if ("content".equals(el.getName())) {
			String name = el.getAttributeStaticStr("name");
			if (name == null) {
				return null;
			}
			Optional<Creator> creator = Optional.ofNullable(el.getAttributeStaticStr("creator")).map(Creator::valueOf);
			if (creator.isEmpty()) {
				return null;
			}
			Optional<Senders> senders = Optional.ofNullable(el.getAttributeStaticStr("senders")).map(Senders::valueOf);
			List<Element> children = Optional.ofNullable(el.getChildren()).orElse(Collections.emptyList());
			Optional<Description> description = children.stream().map(Description::from).filter(Objects::nonNull).findFirst();
			List<Transport> transports = children.stream().map(Transport::from).filter(Objects::nonNull).collect(
					Collectors.toList());
			return new Content(creator.get(), name, senders, description, transports);
		}
		return null;
	}

	public static Content from(String sdp, Content.Creator creator) {
		String[] lines = sdp.split("\r\n");
		String[] line = lines[0].split(" ");
		String mediaName = line[0].substring(2);
		String name = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=mid:"))
				.findFirst()
				.map(it -> it.substring(6))
				.orElse(mediaName);

		Optional<String> pwd = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=ice-pwd:"))
				.findFirst()
				.map(it -> it.substring("a=ice-pwd:".length()));
		Optional<String> ufrag = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=ice-ufrag:"))
				.findFirst()
				.map(it -> it.substring("a=ice-ufrag:".length()));

		Optional<Senders> senders = StreamType.fromLines(lines).map(it -> it.toSenders(creator));

		List<String> payloadIds = Arrays.stream(line).limit(lines.length-1).skip(3).collect(Collectors.toList());
		List<Payload> payloads = payloadIds.stream().map(id -> {
			String prefix = "a=rtpmap:" + id;
			Optional<String[]> l = Arrays.stream(lines)
					.filter(it -> it.startsWith(prefix))
					.map(it -> it.substring(prefix.length()))
					.findFirst()
					.map(it -> it.split("/"));
			String prefix2 = "a=fmtp:" + id;
			List<Payload.Parameter> parameters = Arrays.stream(lines)
					.filter(it -> it.startsWith(prefix2))
					.map(it -> it.substring(prefix2.length()))
					.map(it -> it.split(";"))
					.flatMap(Arrays::stream)
					.map(Payload.Parameter::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			String prefix3 = "a=rtcp-fb:" + id + " ";
			List<Payload.RtcpFeedback> rtcpFeedbacks = Arrays.stream(lines)
					.filter(it -> it.startsWith(prefix3))
					.map(it -> it.substring(prefix3.length()))
					.map(Payload.RtcpFeedback::from)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			Optional<Integer> clockrate = l.map(x -> x[1]).map(Integer::parseInt);
			int channels = l.filter(x -> x.length > 2).map(x -> x[2]).map(Integer::parseInt).orElse(1);
			return new Payload(Integer.parseInt(id), channels, clockrate, Optional.empty(), l.map(x -> x[0].trim()), Optional.empty(), parameters, rtcpFeedbacks);
		}).collect(Collectors.toList());

		List<Encryption> encryptions = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=crypto:"))
				.map(it -> it.split(" "))
				.filter(it -> it.length > 3)
				.map(it -> {
					return new Encryption(it[0], it[1], Optional.ofNullable(it.length > 3 ? it[3] : null), it[2]);
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		List<HdrExt> hdrExts = HdrExt.from(lines);
		List<SSRC> ssrcs = SSRC.from(lines);
		List<SSRCGroup> ssrcGroups = SSRCGroup.from(lines);
		boolean rtcpMux = Arrays.stream(lines).anyMatch(it -> it.equals("a=rtcp-mux"));

		Description description = new Description(mediaName, Optional.empty(), payloads, null, encryptions, rtcpMux, ssrcs,
												  ssrcGroups, hdrExts);

		List<Candidate> candidates = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=candidate:"))
				.map(Candidate::from)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		Optional<Fingerprint.Setup> setup = Arrays.stream(lines)
				.filter(it -> it.startsWith("a=setup:"))
				.findFirst()
				.map(it -> it.substring("a=setup:".length()))
				.map(Fingerprint.Setup::valueOf);
		Optional<Fingerprint> fingerprint = setup.flatMap(setupValue -> {
			return Arrays.stream(lines)
					.filter(it -> it.startsWith("a=fingerprint:"))
					.map(it -> it.substring("a=fingerprint:".length()).split(" "))
					.filter(it -> it.length >= 2)
					.findFirst()
					.map(it -> {
						return new Fingerprint(it[0], it[1], setupValue);
					});
		});
		Transport transport = new Transport(ufrag, pwd, candidates, fingerprint);

		return new Content(creator,name, senders, Optional.of(description), List.of(transport));
	}

	public enum Creator {
		initiator, responder
	}

	public enum Senders {
		none,
		initiator,
		responder,
		both;

		public StreamType toStreamType(Creator creator) {
			switch (this) {
				case none:
					return StreamType.inactive;
				case both:
					return StreamType.sendrecv;
				case initiator:
					return creator == Creator.initiator ? StreamType.sendonly : StreamType.recvonly;
				case responder:
					return creator == Creator.responder ? StreamType.sendonly : StreamType.recvonly;
			}
			throw new IllegalStateException("Unsupported state: " + this.name() + " - " + creator.name());
		}
	}

	private final Creator creator;
	private final String name;
	private final Optional<Description> description;
	private final List<Transport> transports;
	private final Optional<Senders> senders;

	public Content(Creator creator, String name, Optional<Senders> senders, Optional<Description> description, List<Transport> transports) {
		this.creator = creator;
		this.name = name;
		this.senders = senders;
		this.description = description;
		this.transports = transports;
	}

	public Creator getCreator() {
		return creator;
	}

	public String getName() {
		return name;
	}

	public Optional<Description> getDescription() {
		return description;
	}

	public List<Transport> getTransports() {
		return transports;
	}

	public Senders getSenders() {
		return senders.orElse(Senders.both);
	}

	public Element toElement() {
		Element el = new Element("content");
		el.setAttribute("name", name);
		el.setAttribute("creator", creator.name());
		senders.ifPresent(senders -> el.setAttribute("senders", senders.name()));
		description.map(Description::toElement).ifPresent(el::addChild);
		transports.stream().map(Transport::toElement).forEach(el::addChild);
		return el;
	}

	public Content cloneHeaderOnly() {
		return new Content(creator, name, senders, Optional.empty(), Collections.emptyList());
	}

	public Content withSenders(Senders senders) {
		return new Content(creator, name, Optional.ofNullable(senders), description, transports);
	}

	public enum StreamType {
		inactive,
		sendonly,
		recvonly,
		sendrecv;

		public static final Map<String, StreamType> SDP_LINE = Arrays.stream(StreamType.values())
				.collect(Collectors.toMap(it -> "a=" + it.name(), Function.identity()));

		public static Optional<StreamType> fromLines(String[] lines) {
			return Arrays.stream(lines).map(SDP_LINE::get).filter(Objects::nonNull).findFirst();
		}

		public Senders toSenders(Creator creator) {
			switch (this) {
				case inactive:
					return Senders.none;
				case sendrecv:
					return Senders.both;
				case recvonly:
					return creator == Creator.initiator ? Senders.responder : Senders.initiator;
				case sendonly:
					return creator == Creator.initiator ? Senders.initiator : Senders.responder;
			}
			throw new IllegalStateException("Unsupported state: " + this.name() + " - " + creator.name());
		}
	}

	public String toSDP() {
		List<String> lines = new ArrayList<>();

		description.ifPresent(description -> {
			String proto = (description.getEncryptions().isEmpty() ||
					!transports.stream().filter(it -> it.getFingerprint() == null).findAny().isEmpty())
						   ? "RTP/AVPF"
						   : "RTP/SAVPF";
			lines.add("m=" + description.getMedia() + " 1 " + proto + " " + description.getPayloads()
					.stream()
					.map(it -> it.getId())
					.map(String::valueOf)
					.collect(Collectors.joining(" ")));
		});

		lines.add("c=IN IP4 0.0.0.0");

		if (description.filter(it -> Set.of("audio", "video").contains(it.getMedia())).isPresent()) {
			lines.add("a=rtcp:1 IN IP4 0.0.0.0");
		}

		transports.stream().findFirst().ifPresent(transport -> {
			transport.getUfrag().map(it -> "a=ice-ufrag:" + it).ifPresent(lines::add);
			transport.getPwd().map(it -> "a=ice-pwd:" + it).ifPresent(lines::add);
			transport.getFingerprint().ifPresent(fingerprint -> {
				lines.add("a=fingerprint:" + fingerprint.getHash() + " " + fingerprint.getValue());
				lines.add("a=setup:" + fingerprint.getSetup().name());
			});
		});

		lines.add("a=" + getSenders().toStreamType(creator).name());
//		lines.add("a=sendrecv");
		lines.add("a=mid:" + name);
		lines.add("a=ice-options:trickle");

		description.ifPresent(description -> {
			if (Set.of("audio", "video").contains(description.getMedia())) {
				if (description.isRtcpMux()) {
					lines.add("a=rtcp-mux");
				}
				description.getEncryptions().stream().map(Encryption::toSDP).forEach(lines::add);
			}
			description.getPayloads().stream().map(Payload::toSDP).flatMap(Collection::stream).forEach(lines::add);

			description.getHdrExts().stream().map(HdrExt::toSDP).forEach(lines::add);
			description.getSsrcGroups().stream().map(SSRCGroup::toSDP).forEach(lines::add);
			description.getSsrcs().stream().map(SSRC::toSDP).flatMap(Collection::stream).forEach(lines::add);
		});

		transports.stream().findFirst().ifPresent(transport -> {
			transport.getCandidates().stream().map(Candidate::toSDP).forEach(lines::add);
		});

		return lines.stream().collect(Collectors.joining("\r\n"));
	}
}
