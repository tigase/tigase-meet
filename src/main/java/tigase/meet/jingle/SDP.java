/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import java.util.*;
import java.util.stream.Collectors;

public class SDP {

	public static SDP from(String sdp, Content.Creator creator) {
		String[] parts = sdp.substring(0, sdp.length() - 2).split("\r\nm=");
		List<String> media = Arrays.stream(parts).skip(1).map(it -> "m=" + it).collect(Collectors.toList());
		String[] sessionLines = parts[0].split("\r\n");
		Optional<String[]> sessionLine = Arrays.stream(sessionLines)
				.filter(it -> it.startsWith("o="))
				.findFirst()
				.map(it -> it.split(" "));
		if (sessionLine.isEmpty() || sessionLine.get().length <= 3) {
			return null;
		}

		String sid = sessionLine.get()[1];
		String id = sessionLine.get()[2];

		Optional<String[]> groupParts = Arrays.stream(sessionLines)
				.filter(it -> it.startsWith("a=group:BUNDLE "))
				.findFirst()
				.map(it -> it.split(" "));

		List<String> bundle = groupParts.filter(it -> "a=group:BUNDLE ".equals(it[0]))
				.map(it -> Arrays.stream(it).skip(1).collect(Collectors.toList()))
				.orElse(Collections.emptyList());

		List<Content> contents = media.stream()
				.map(m -> Content.from(m, creator))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		return new SDP(id, contents, bundle);
	}

	private final String id;
	private final List<Content> contents;
	private final List<String> bundle;

	public SDP(String id, List<Content> contents, List<String> bundle) {
		this.id = id;
		this.contents = contents;
		this.bundle = bundle;
	}

	public String getId() {
		return id;
	}

	public List<Content> getContents() {
		return contents;
	}

	public List<String> getBundle() {
		return bundle;
	}

	public String toString(String sid) {
		List<String> lines = new ArrayList<>();
		lines.add("v=0");
		lines.add("o=- " + sid + " " + id + " IN IP4 0.0.0.0");
		lines.add("s=-");
		lines.add("t=0 0");

		if (!bundle.isEmpty()) {
			lines.add("a=group:BUNDLE " + bundle.stream().collect(Collectors.joining(" ")));
		}

		contents.stream().map(Content::toSDP).forEach(lines::add);

		return lines.stream().collect(Collectors.joining("\r\n")) + "\r\n";
	}
}
