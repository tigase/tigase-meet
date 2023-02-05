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
