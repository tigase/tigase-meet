/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class SDPTest {

	public static final String TEST_STRING = ("v=0\n" + "o=- 1623251477217656 2 IN IP4 0.0.0.0\n" +
			"s=VideoRoom 1234\n" + "t=0 0\n" + "a=group:BUNDLE 0 1 2 3\n" + "a=ice-options:trickle\n" +
			"a=fingerprint:sha-256 89:5D:8D:AA:1D:0B:6F:7F:54:16:D2:61:E7:B7:4C:D7:0E:DF:93:FD:10:34:66:7A:71:24:0D:D8:45:E9:4C:C9\n" +
			"a=msid-semantic: WMS janus\n" + "m=audio 9 UDP/TLS/RTP/SAVPF 111\n" + "c=IN IP4 0.0.0.0\n" +
			"a=sendonly\n" + "a=mid:0\n" + "a=rtcp-mux\n" + "a=ice-ufrag:HTzj\n" +
			"a=ice-pwd:kLUcEX+Rrq4lWvUcJA1hZ/\n" + "a=ice-options:trickle\n" + "a=setup:actpass\n" +
			"a=rtpmap:111 opus/48000/2\n" + "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\n" +
			"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\n" + "a=msid:janus janus0\n" +
			"a=ssrc:4040912716 cname:janus\n" + "m=video 9 UDP/TLS/RTP/SAVPF 96\n" + "c=IN IP4 0.0.0.0\n" +
			"a=sendonly\n" + "a=mid:1\n" + "a=rtcp-mux\n" + "a=ice-ufrag:HTzj\n" +
			"a=ice-pwd:kLUcEX+Rrq4lWvUcJA1hZ/\n" + "a=ice-options:trickle\n" + "a=setup:actpass\n" +
			"a=rtpmap:96 VP8/90000\n" + "a=rtcp-fb:96 ccm fir\n" + "a=rtcp-fb:96 nack\n" + "a=rtcp-fb:96 nack pli\n" +
			"a=rtcp-fb:96 goog-remb\n" +
			"a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\n" +
			"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\n" +
			"a=extmap:12 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\n" + "a=msid:janus janus1\n" +
			"a=ssrc:740108580 cname:janus\n" + "m=audio 9 UDP/TLS/RTP/SAVPF 111\n" + "c=IN IP4 0.0.0.0\n" +
			"a=sendonly\n" + "a=mid:2\n" + "a=rtcp-mux\n" + "a=ice-ufrag:HTzj\n" +
			"a=ice-pwd:kLUcEX+Rrq4lWvUcJA1hZ/\n" + "a=ice-options:trickle\n" + "a=setup:actpass\n" +
			"a=rtpmap:111 opus/48000/2\n" + "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\n" +
			"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\n" + "a=msid:janus janus2\n" +
			"a=ssrc:3154195879 cname:janus\n" + "m=video 9 UDP/TLS/RTP/SAVPF 96\n" + "c=IN IP4 0.0.0.0\n" +
			"a=sendonly\n" + "a=mid:3\n" + "a=rtcp-mux\n" + "a=ice-ufrag:HTzj\n" +
			"a=ice-pwd:kLUcEX+Rrq4lWvUcJA1hZ/\n" + "a=ice-options:trickle\n" + "a=setup:actpass\n" +
			"a=rtpmap:96 VP8/90000\n" + "a=rtcp-fb:96 ccm fir\n" + "a=rtcp-fb:96 nack\n" + "a=rtcp-fb:96 nack pli\n" +
			"a=rtcp-fb:96 goog-remb\n" +
			"a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01\n" +
			"a=extmap:4 urn:ietf:params:rtp-hdrext:sdes:mid\n" +
			"a=extmap:12 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\n" + "a=msid:janus janus3\n" +
			"a=ssrc:3023058024 cname:janus").replaceAll("\n", "\r\n");

	@Test
	public void testConversion() {
		String sid = UUID.randomUUID().toString();
		SDP sdp1 = SDP.from(SDPTest.TEST_STRING, Content.Creator.initiator);
		assertNotNull(sdp1);
		assertFalse(sdp1.getBundle().isEmpty());
		assertFalse(sdp1.getContents().get(1).getDescription().get().getPayloads().get(0).getRtcpFeedback().isEmpty());
		String sdpStr1 = sdp1.toString(sid);

		SDP sdp2 = SDP.from(sdpStr1, Content.Creator.initiator);
		assertNotNull(sdp2);
		String sdpStr2 = sdp2.toString(sid);
		
		assertEquals(sdp2.getContents(), sdp2.getContents());
		assertFalse(sdp2.getBundle().isEmpty());
		assertFalse(sdp2.getContents().get(1).getDescription().get().getPayloads().get(0).getRtcpFeedback().isEmpty());

		assertEquals(sdpStr2, sdpStr1);
	}

}