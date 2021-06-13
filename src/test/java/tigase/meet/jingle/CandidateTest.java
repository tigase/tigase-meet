/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import org.junit.Test;

import static org.junit.Assert.*;

public class CandidateTest {

	@Test
	public void fromSrflx() {
		Candidate candidate = Candidate.from("candidate:4 1 udp 1679819007 46.171.244.86 64102 typ srflx raddr 172.17.0.2 rport 59706");
		assertNotNull(candidate);
	}
}