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

import java.util.Optional;
import java.util.UUID;

public class Candidate {

	public static Candidate from(Element el) {
		if ("candidate".equals(el.getName())) {
			String component = el.getAttributeStaticStr("component");
			String foundation = el.getAttributeStaticStr("foundation");
			String generation = el.getAttributeStaticStr("generation");
			String id = el.getAttributeStaticStr("id");
			if (id == null) {
				id = UUID.randomUUID().toString();
			}
			String ip = el.getAttributeStaticStr("ip");
			String network = el.getAttributeStaticStr("network");
			String port = el.getAttributeStaticStr("port");
			String priority = el.getAttributeStaticStr("priority");
			String protocolType = el.getAttributeStaticStr("protocol");
			if (component == null || foundation == null || generation == null || ip == null || network == null ||
					port == null || priority == null || protocolType == null) {
				return null;
			}

			Optional<String> relAddr = Optional.ofNullable(el.getAttributeStaticStr("rel-addr"));
			Optional<Integer> relPort = Optional.ofNullable(el.getAttributeStaticStr("rel-port"))
					.map(Integer::parseInt);
			Optional<CandidateType> candidateType = Optional.ofNullable(el.getAttributeStaticStr("type"))
					.map(CandidateType::valueOf);
			Optional<String> tcpType = Optional.ofNullable(el.getAttributeStaticStr("tcptype"));

			return new Candidate(component, foundation, Integer.parseInt(generation), id, ip, Integer.parseInt(network),
								 Integer.parseInt(port), Integer.parseInt(priority), ProtocolType.valueOf(protocolType),
								 relAddr, relPort, candidateType, tcpType);
		}
		return null;
	}

	public static Candidate from(String line) {
		int idx = "candidate:".length() + (line.startsWith("a=") ? 2 : 0);
		String[] parts = line.substring(idx).split(" ");
		if (parts.length >= 8) {
			ProtocolType protocolType = ProtocolType.valueOf(parts[2].toLowerCase());
			int priority = Integer.parseInt(parts[3]);
			int port = Integer.parseInt(parts[5]);
			CandidateType type = CandidateType.valueOf(parts[7]);

			Optional<Integer> generation = Optional.empty();
			Optional<String> relAddr = Optional.empty();
			Optional<Integer> relPort = Optional.empty();
			Optional<String> tcpType = Optional.empty();

			int i = 8;
			while (parts.length >= i + 2) {
				switch (parts[i]) {
					case "tcptype":
						tcpType = Optional.ofNullable(parts[i+1]);
						break;
					case "generation":
						generation = Optional.ofNullable(parts[i+1]).map(Integer::parseInt);
						break;
					case "raddr":
						relAddr = Optional.ofNullable(parts[i+1]);
						break;
					case "rport":
						relPort = Optional.ofNullable(parts[i+1]).map(Integer::parseInt);
						break;
					default:
						i = i - 1;
						break;
				}
				i = i + 2;
			}

			return new Candidate(parts[1], parts[0], generation.orElse(0), UUID.randomUUID().toString(), parts[4], 0, port, priority, protocolType, relAddr, relPort, Optional.of(type), tcpType);
		}
		return null;
	}

	public enum ProtocolType {
		udp, tcp
	}

	public enum CandidateType {
		host, prlfx, relay, srflx
	}

	private final String component;
	private final String foundation;
	private final int generation;
	private final String id;
	private final String ip;
	private final int network;
	private final int port;
	private final int priority;
	private final ProtocolType protocolType;
	private final Optional<String> relAddr;
	private final Optional<Integer> relPort;
	private final Optional<CandidateType> type;
	private final Optional<String> tcpType;

	public Candidate(String component, String foundation, int generation, String id, String ip, int network, int port,
					 int priority, ProtocolType protocolType, Optional<String> relAddr, Optional<Integer> relPort,
					 Optional<CandidateType> type, Optional<String> tcpType) {
		this.component = component;
		this.foundation = foundation;
		this.generation = generation;
		this.id = id;
		this.ip = ip;
		this.network = network;
		this.port = port;
		this.priority = priority;
		this.protocolType = protocolType;
		this.relAddr = relAddr;
		this.relPort = relPort;
		this.type = type;
		this.tcpType = tcpType;
	}

	public String getComponent() {
		return component;
	}

	public String getFoundation() {
		return foundation;
	}

	public int getGeneration() {
		return generation;
	}

	public String getId() {
		return id;
	}

	public String getIp() {
		return ip;
	}

	public int getNetwork() {
		return network;
	}

	public int getPort() {
		return port;
	}

	public int getPriority() {
		return priority;
	}

	public ProtocolType getProtocolType() {
		return protocolType;
	}

	public Optional<String> getRelAddr() {
		return relAddr;
	}

	public Optional<Integer> getRelPort() {
		return relPort;
	}

	public Optional<CandidateType> getType() {
		return type;
	}

	public Optional<String> getTcpType() {
		return tcpType;
	}

	public Element toElement() {
		Element el = new Element("candidate");
		el.setAttribute("component", component);
		el.setAttribute("foundation", foundation);
		el.setAttribute("generation", String.valueOf(generation));
		el.setAttribute("id", id);
		el.setAttribute("ip", ip);
		el.setAttribute("network", String.valueOf(network));
		el.setAttribute("port", String.valueOf(port));
		el.setAttribute("protocol", protocolType.name());
		el.setAttribute("priority", String.valueOf(priority));
		relAddr.ifPresent(relAddr -> el.setAttribute("rel-addr", relAddr));
		relPort.map(String::valueOf).ifPresent(relPort -> el.setAttribute("rel-port", relPort));
		type.map(CandidateType::name).ifPresent(type -> el.setAttribute("type", type));
		tcpType.ifPresent(tcpType -> el.setAttribute("tcptype", tcpType));
		return el;
	}

	public String toSDP() {
		CandidateType type = this.type.orElse(CandidateType.host);
		StringBuilder sb = new StringBuilder();
		sb.append("candidate:")
				.append(foundation)
				.append(" ")
				.append(component)
				.append(" ")
				.append(protocolType.name().toLowerCase())
				.append(" ")
				.append(priority)
				.append(" ")
				.append(ip)
				.append(" ")
				.append(port)
				.append(" typ ")
				.append(type.name());

		if (type != CandidateType.host) {
			relAddr.ifPresent(relAddr -> {
				relPort.ifPresent(relPort -> {
					sb.append(" raddr ").append(relAddr).append(" rport ").append(relPort);
				});
			});
		}

		if (protocolType == ProtocolType.tcp) {
			tcpType.ifPresent(tcpType -> sb.append(" tcptype ").append(tcpType));
		}

		sb.append(" generation ").append(generation);
		return sb.toString();
	}
}
