/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SDP {

	public static SDP from(Element jingleEl) {
//		JID initiator = Optional.ofNullable(jingleEl.getAttributeStaticStr("initiator"))
//				.map(JID::jidInstanceNS).orElse(from);
//
		List<Content> contents = Optional.ofNullable(jingleEl.getChildren())
				.orElse(Collections.emptyList())
				.stream()
				.map(Content::from)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<Element> groupChildren = Optional.ofNullable(
				jingleEl.getChild("group", "urn:xmpp:jingle:apps:grouping:0"))
				.map(it -> it.getChildren())
				.orElse(Collections.EMPTY_LIST);
		List<String> bundle = groupChildren.stream()
				.filter(it -> "content".equals(it.getName()))
				.map(it -> it.getAttributeStaticStr("name"))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());

		return new SDP(String.valueOf(new Date().getTime()), contents, bundle);
	}

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

		List<String> bundle = groupParts.filter(it -> "a=group:BUNDLE".equals(it[0]))
				.map(it -> Arrays.stream(it).skip(1).collect(Collectors.toList()))
				.orElse(Collections.emptyList());

		List<Content> contents = media.stream()
				.map(m -> Content.from(m, sessionLines, creator))
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

	public Element toElement(Action action, String sessionId, JID from) {
		Element jingleEl = new Element("jingle");
		jingleEl.setAttribute("xmlns", "urn:xmpp:jingle:1");
		jingleEl.setAttribute("action", action.getValue());
		jingleEl.setAttribute("sid", sessionId);
		switch (action) {
			case sessionInitiate:
				jingleEl.setAttribute("initiator", from.toString());
				break;
			case sessionAccept:
				jingleEl.setAttribute("responder", from.toString());
				break;
			default:
				break;
		}
		contents.stream().map(Content::toElement).forEach(jingleEl::addChild);
		if (!bundle.isEmpty()) {
			jingleEl.withElement("group", "urn:xmpp:jingle:apps:grouping:0", groupEl -> {
				groupEl.setAttribute("semantics", "BUNDLE");
				bundle.stream()
						.map(name -> new Element("content", new String[]{"name"}, new String[]{name}))
						.forEach(groupEl::addChild);
			});
		}
		return jingleEl;
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

	public SDP applyDiff(ContentAction action, SDP diff) {
		switch (action) {
			case accept:
			case add:
				return new SDP(this.id, Stream.concat(getContents().stream(), diff.getContents().stream())
						.collect(Collectors.toList()), diff.getBundle());
			case init:
				return diff;
			case modify:
				Content[] contents = this.getContents().toArray(new Content[this.getContents().size()]);
				for (Content diffed : diff.getContents()) {
					for (int i=0; i<contents.length; i++) {
						if (contents[i].getName().equals(diffed.getName())) {
							contents[i] = contents[i].withSenders(diffed.getSenders());
						}
					}
				}
				return new SDP(id, new ArrayList<>(Arrays.asList(contents)), getBundle());
			case remove:
				Set<String> toRemove = diff.getContents().stream().map(Content::getName).collect(Collectors.toSet());
				return new SDP(id, getContents().stream().filter(it -> !toRemove.contains(it.getName())).collect(
						Collectors.toList()), diff.getBundle());
		}
		throw new UnsupportedOperationException("Unsupported content action: " + action.name());
	}

	public Map<ContentAction,SDP> diffFrom(SDP oldSdp) {
		Map<ContentAction,SDP> results = new HashMap<>();
		
		List<String> oldContentNames = oldSdp.getContents().stream().map(Content::getName).collect(Collectors.toList());
		List<String> newContentNames = getContents().stream().map(Content::getName).collect(Collectors.toList());

		List<Content> contentsToRemove = oldSdp.getContents()
				.stream()
				.filter(it -> !newContentNames.contains(it.getName()))
				.collect(Collectors.toList());
		if (!contentsToRemove.isEmpty()) {
			SDP sdp = new SDP(id, contentsToRemove.stream().map(Content::cloneHeaderOnly).collect(Collectors.toList()), this.getBundle());
			results.put(ContentAction.remove, sdp);
		}

		List<Content> contentsToAdd = getContents().stream()
				.filter(it -> !oldContentNames.contains(it.getName()))
				.collect(Collectors.toList());
		if (!contentsToAdd.isEmpty()) {
			results.put(ContentAction.add, new SDP(id, contentsToAdd, this.getBundle()));
		}

		List<Content> contentsToModify = this.getContents()
				.stream()
				.filter(it -> oldSdp.getContents()
						.stream()
						.filter(oldContent -> it.getName().equals(oldContent.getName()))
						.filter(oldContent -> it.getSenders() != oldContent.getSenders())
						.findFirst()
						.isPresent())
				.map(Content::cloneHeaderOnly)
				.collect(Collectors.toList());
		if (!contentsToModify.isEmpty()) {
			results.put(ContentAction.modify, new SDP(id, contentsToModify, Collections.emptyList()));
		}

		return results;
	}
}
