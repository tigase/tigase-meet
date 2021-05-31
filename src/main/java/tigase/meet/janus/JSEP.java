/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus;

import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.Map;

public class JSEP {

	public static JSEP fromData(Map<String, Object> data) {
		Map<String, Object> jsep = (Map<String, Object>) data.get("jsep");
		if (jsep == null) {
			return null;
		}
		return new JSEP(Type.valueOf((String) jsep.get("type")), (String) jsep.get("sdp"));
	}

	public enum Type {
		offer,
		answer
	}

	private final Type type;
	private final String sdp;

	public JSEP(Type type, String sdp) {
		this.type = type;
		this.sdp = sdp;
	}

	public String getSdp() {
		return sdp;
	}

	public Type getType() {
		return type;
	}

	public void write(JsonGenerator generator) throws IOException {
		generator.writeFieldName("jsep");
		generator.writeStartObject();
		generator.writeStringField("type", type.name());
		generator.writeStringField("sdp", sdp);
		generator.writeEndObject();
	}

	@Override
	public String toString() {
		return "JSEP{" + "type=" + type + ", sdp='" + sdp + '\'' + '}';
	}
}
