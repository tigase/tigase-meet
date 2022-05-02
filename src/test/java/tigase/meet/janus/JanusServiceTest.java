/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import tigase.component.DSLBeanConfigurator;
import tigase.conf.ConfigBuilder;
import tigase.kernel.AbstractKernelTestCase;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.util.log.LogFormatter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

@Ignore
public class JanusServiceTest extends AbstractKernelTestCase {

	private static final String TEST_SDP = "v=0\n" + "o=- 7748854561613975422 2 IN IP4 127.0.0.1\n" + "s=-\n" +
			"t=0 0\n" + "a=group:BUNDLE m0\n" + "a=msid-semantic: WMS RTCmS\n" +
			"m=audio 64527 UDP/TLS/RTP/SAVPF 111 103 104 9 102 0 8 106 105 13 110 112 113 126\n" +
			"c=IN IP4 10.28.28.197\n" + "a=rtcp:9 IN IP4 0.0.0.0\n" +
			"a=candidate:3565984846 1 udp 2122260224 10.28.28.197 64527 typ host generation 0 network-id 1 network-cost 50\n" +
			"a=ice-ufrag:AZw+\n" + "a=ice-pwd:Gydud5GVcWgjUFRznV/hh2QY\n" + "a=ice-options:trickle renomination\n" +
			"a=fingerprint:sha-256 D1:3A:E1:0A:05:D1:74:E7:A4:1F:E3:33:3B:46:17:DD:55:15:F5:A1:D6:42:DA:03:07:3F:26:5B:73:8E:E3:8E\n" +
			"a=setup:actpass\n" + "a=mid:m0\n" + "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\n" +
			"a=extmap:9 urn:ietf:params:rtp-hdrext:sdes:mid\n" + "a=sendrecv\n" + "a=msid:RTCmS audio0\n" +
			"a=rtcp-mux\n" + "a=rtpmap:111 opus/48000/2\n" + "a=rtcp-fb:111 transport-cc\n" +
			"a=fmtp:111 minptime=10;useinbandfec=1\n" + "a=rtpmap:103 ISAC/16000\n" + "a=rtpmap:104 ISAC/32000\n" +
			"a=rtpmap:9 G722/8000\n" + "a=rtpmap:102 ILBC/8000\n" + "a=rtpmap:0 PCMU/8000\n" +
			"a=rtpmap:8 PCMA/8000\n" + "a=rtpmap:106 CN/32000\n" + "a=rtpmap:105 CN/16000\n" + "a=rtpmap:13 CN/8000\n" +
			"a=rtpmap:110 telephone-event/48000\n" + "a=rtpmap:112 telephone-event/32000\n" +
			"a=rtpmap:113 telephone-event/16000\n" + "a=rtpmap:126 telephone-event/8000\n" +
			"a=ssrc:4276098296 cname:6NCBp2ajBbDLtr7M\n" + "a=ssrc:4276098296 msid:RTCmS audio0\n" +
			"a=ssrc:4276098296 mslabel:RTCmS\n" + "a=ssrc:4276098296 label:audio0";

	public static void configureLogging(Level level) {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new LogFormatter(true));
		handler.setLevel(level);

		Logger logger = Logger.getLogger("tigase.meet");
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
		logger.setLevel(level);
	}

	JanusService janusService;

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(DefaultTypesConverter.class).setActive(true).exec();
		kernel.registerBean(DSLBeanConfigurator.class).setActive(true).exec();
		kernel.registerBean(JanusService.class).setActive(true).exec();
	}

	@Before
	public void setupJanus() {
		configureLogging(Level.FINEST);
		ConfigBuilder configBuilder = new ConfigBuilder().with(new AbstractBeanConfigurator.BeanDefinition.Builder().name("janus").active(true).with("uri", "ws://127.0.0.1:8188/").build());
		getKernel().getInstance(DSLBeanConfigurator.class).setProperties(configBuilder.build());
		janusService = getKernel().getInstance(JanusService.class);
	}

	@Test
	public void test() throws ExecutionException, InterruptedException, IOException {
		CompletableFuture<JanusConnection> future = janusService.newConnection();
		JanusConnection connection = future.get();
		CompletableFuture<JanusSession> result1 = connection.createSession();
		CompletableFuture<Map<String,Object>> result2 = connection.getInfo();
		CompletableFuture.allOf(result1, result2).get();

		JanusSession session = result1.get();
		CompletableFuture<JanusVideoRoomPlugin> pluginFuture = session.attachPlugin(JanusVideoRoomPlugin.class);
		JanusVideoRoomPlugin plugin1 = pluginFuture.get();

		JanusSession session2 = connection.createSession().get();
		JanusVideoRoomPlugin plugin2 = session2.attachPlugin(JanusVideoRoomPlugin.class).get();

		Object roomId = 1234l;
		try {
			roomId = plugin1.createRoom(1234l, 3).get();
		} catch (ExecutionException ex) {
			//room = new JanusVideoRoom(plugin, 1234l);
		}

		LocalPublisher localPublisher2 = plugin2.createPublisher(roomId, "Local publisher 1").get();
		LocalPublisher localPublisher1 = plugin1.createPublisher(roomId, "Local publisher 2").get();

		Thread.sleep(400);

		Assert.assertNotNull(localPublisher1.publish(new JSEP(JSEP.Type.offer, TEST_SDP)).get());
		//localPublisher.unpublish().get();


		JanusVideoRoomPlugin plugin3 = session.attachPlugin(JanusVideoRoomPlugin.class).get();
		LocalSubscriber localSubscriber3 = plugin3.createSubscriber(roomId);

		localSubscriber3.subscribe(List.of(new JanusVideoRoomPlugin.Stream(4083653061418717l, null))).get();

		//localPublisher2.subscribe(Collections.emptyList()).get();
		Thread.sleep(100);
		localPublisher1.leave().get();

		localPublisher2.leave();
		session2.destroy().get();

		plugin1.destroyRoom(roomId).get();

		session.keepAlive().get();

		CompletableFuture<Void> destroyFuture = result1.get().destroy();
		CompletableFuture.allOf(destroyFuture).get();
	}
	
}
