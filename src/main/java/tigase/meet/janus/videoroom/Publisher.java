/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus.videoroom;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Publisher {

	public static List<Publisher> fromEvent(List<Map<String, Object>> list) {
		if (list == null) {
			return null;
		}

		return list.stream()
				.map(data -> new Publisher(((Number) data.get("id")).longValue(), (String) data.get("display"),
										   Stream.fromEvent((List<Map<String, Object>>) data.get("streams"))))
				.collect(Collectors.toList());
	}

	private final long id;
	private final String display;
	private final List<Stream> streams;

	public Publisher(long id, String display, List<Stream> streams) {
		this.id = id;
		this.display = display;
		this.streams = streams;
	}

	public long getId() {
		return id;
	}

	public String getDisplay() {
		return display;
	}

	public List<Stream> getStreams() {
		return streams;
	}

	public static class Stream {

		public static List<Stream> fromEvent(List<Map<String, Object>> list) {
			if (list == null || list.isEmpty()) {
				return Collections.emptyList();
			}

			return list.stream()
					.map(data -> new Stream(Stream.Type.valueOf((String) data.get("type")), (String) data.get("mid"),
											(String) data.get("description"), ((Number) data.get("mindex")).intValue()))
					.collect(Collectors.toList());
		}

		public enum Type {
			audio,
			video,
			data,
			unknown
		}

		private final Stream.Type type;
		private final String mid;
		private final String description;
		private final int mindex;

		public Stream(Stream.Type type, String mid, String description, int mindex) {
			this.type = type;
			this.mid = mid;
			this.description = description;
			this.mindex = mindex;
		}

		public Stream.Type getType() {
			return type;
		}

		public String getDescription() {
			return description;
		}

		public String getMid() {
			return mid;
		}

		public int getMindex() {
			return mindex;
		}
	}

	@Override
	public String toString() {
		return "Publisher{" + "id=" + id + ", display='" + display + '\'' + ", streams=" + streams.size() + '}';
	}
}
