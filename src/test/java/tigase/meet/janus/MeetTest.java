/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus;

import dev.onvoid.webrtc.*;
import dev.onvoid.webrtc.media.audio.AudioOptions;
import dev.onvoid.webrtc.media.audio.AudioSource;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import tigase.component.DSLBeanConfigurator;
import tigase.component.PacketWriter;
import tigase.component.exceptions.ComponentException;
import tigase.component.responses.AsyncCallback;
import tigase.component.responses.ResponseManager;
import tigase.conf.ConfigBuilder;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.AbstractKernelTestCase;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.meet.AbstractMeet;
import tigase.meet.Meet;
import tigase.meet.MeetRepository;
import tigase.meet.jingle.*;
import tigase.meet.modules.JingleMeetModule;
import tigase.meet.utils.DelayedRunQueue;
import tigase.server.Packet;
import tigase.util.log.LogFormatter;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Ignore
public class MeetTest extends AbstractKernelTestCase {

	private static final Logger log = Logger.getLogger(MeetTest.class.getCanonicalName());

	public static void configureLogging(Level level) {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new LogFormatter(true));
		handler.setLevel(level);

		Logger logger = Logger.getLogger("tigase.meet");
		logger.addHandler(handler);
		logger.setUseParentHandlers(false);
		logger.setLevel(level);
	}

	private final BareJID meetJid = BareJID.bareJIDInstanceNS("1234@meet.example.com");
	private DummyPacketWriter packetWriter;
	private MeetRepository meetRepository;
	private JingleMeetModule jingleMeetModule;

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(DefaultTypesConverter.class).setActive(true).exec();
		kernel.registerBean(DSLBeanConfigurator.class).setActive(true).exec();
		kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).setActive(true).exec();
		kernel.registerBean(JanusService.class).setActive(true).exec();
		kernel.registerBean(DummyPacketWriter.class).setActive(true).exec();
		kernel.registerBean(MeetRepository.class).setActive(true).exec();
		kernel.registerBean(JingleMeetModule.class).setActive(true).exec();
	}

	@Before
	public void setupJanus()
			throws NoSuchFieldException, ExecutionException, InterruptedException, IllegalAccessException {
		configureLogging(Level.FINEST);
		ConfigBuilder configBuilder = new ConfigBuilder().with(new AbstractBeanConfigurator.BeanDefinition.Builder().name("janus").active(true).with("uri", "wss://127.0.0.1:8989/").build());
		getKernel().getInstance(DSLBeanConfigurator.class).setProperties(configBuilder.build());
		packetWriter = getInstance(DummyPacketWriter.class);
		meetRepository = getInstance(MeetRepository.class);
		jingleMeetModule = getKernel().getInstance(JingleMeetModule.class);
		JanusService janusService = getInstance(JanusService.class);
		Meet meet = new Meet(meetRepository, janusService.newConnection().get(), 1234l, meetJid);
		meet.allow(AbstractMeet.ALLOW_EVERYONE);
		Field f = MeetRepository.class.getDeclaredField("meets");
		f.setAccessible(true);
		((Map<BareJID,CompletableFuture<Meet>>) f.get(meetRepository)).put(meetJid, CompletableFuture.completedFuture(meet));
	}

	private RTCPeerConnection publisherConnection;
	private RTCPeerConnection subscriberConnection;

	@Test
	public void test() throws ExecutionException, InterruptedException, ComponentException, TigaseStringprepException {
		JID user1 = JID.jidInstance("user1@example.com/res1");

		AtomicReference<String> publisherSession = new AtomicReference<>();
		AtomicReference<String> subscriberSession = new AtomicReference<>();

		DelayedRunQueue publisherIceCandidateQueue = new DelayedRunQueue();
		ExecutorService executor = Executors.newSingleThreadExecutor();

		PeerConnectionFactory factory = new PeerConnectionFactory();
		publisherConnection = factory.createPeerConnection(new RTCConfiguration(), new PeerConnectionObserver() {
			
			@Override
			public void onIceCandidate(RTCIceCandidate rtcIceCandidate) {
				publisherIceCandidateQueue.offer(() -> {
					Candidate candidate = Candidate.from(rtcIceCandidate.sdp);
					Transport transport = SDP.from(publisherConnection.getLocalDescription().sdp, Content.Creator.initiator)
							.getContents()
							.stream()
							.filter(it -> rtcIceCandidate.sdpMid.equals(it.getName()))
							.findFirst()
							.get()
							.getTransports()
							.stream()
							.findFirst()
							.get();
					Content content = new Content(Content.Creator.initiator, rtcIceCandidate.sdpMid, Optional.empty(),
												  Optional.empty(),
												  List.of(new Transport(transport.getUfrag(), transport.getPwd(),
																		List.of(candidate), Optional.empty())));
					SDP sdp = new SDP("", List.of(content), Collections.emptyList());
					Element iqEl = new Element("iq");
					iqEl.setAttribute("id", UUID.randomUUID().toString());
					iqEl.setAttribute("type", StanzaType.set.name());
					iqEl.addChild(sdp.toElement(Action.transportInfo, publisherSession.get(), user1));
					process(Packet.packetInstance(iqEl, user1, JID.jidInstanceNS(meetJid)));
				});
			}
		});

		AudioSource audioSource = factory.createAudioSource(new AudioOptions());

		publisherConnection.addTrack(factory.createAudioTrack("m1", audioSource), Collections.EMPTY_LIST);
		
		subscriberConnection = factory.createPeerConnection(new RTCConfiguration(), new PeerConnectionObserver() {
			@Override
			public void onSignalingChange(RTCSignalingState state) {
				System.out.println("subscriber signaling state changed to: " + state);
			}

			@Override
			public void onIceCandidate(RTCIceCandidate rtcIceCandidate) {
				executor.execute(() -> {
					Candidate candidate = Candidate.from(rtcIceCandidate.sdp);
					Transport transport = SDP.from(subscriberConnection.getLocalDescription().sdp, Content.Creator.responder)
							.getContents()
							.stream()
							.filter(it -> rtcIceCandidate.sdpMid.equals(it.getName()))
							.findFirst()
							.get()
							.getTransports()
							.stream()
							.findFirst()
							.get();
					Content content = new Content(Content.Creator.initiator, rtcIceCandidate.sdpMid, Optional.empty(),
												  Optional.empty(),
												  List.of(new Transport(transport.getUfrag(), transport.getPwd(),
																		List.of(candidate), Optional.empty())));
					SDP sdp = new SDP("", List.of(content), Collections.emptyList());
					Element iqEl = new Element("iq");
					iqEl.setAttribute("id", UUID.randomUUID().toString());
					iqEl.setAttribute("type", StanzaType.set.name());
					iqEl.addChild(sdp.toElement(Action.transportInfo, subscriberSession.get(), user1));
					process(Packet.packetInstance(iqEl, user1, JID.jidInstanceNS(meetJid)));
				});
			}
		});

		//ReentrantLock lock = new ReentrantLock();
		packetWriter.packetConsumer = packet -> {
			assertNotEquals(packet.getType(), StanzaType.error);

			log.log(Level.FINEST, () -> "xmpp: C << S : " + packet.toString());

			Element jingleEl = packet.getElemChild("jingle", "urn:xmpp:jingle:1");
			if (jingleEl == null && packet.getType() == StanzaType.result) {
				publisherIceCandidateQueue.delayFinished();
				return;
			}
			assertNotNull(jingleEl);

			System.out.println("<< " + jingleEl.getAttributeStaticStr("sid") + " : "+ jingleEl.getAttributeStaticStr("action"));
			System.out.println(jingleEl.toString());
			String sessionId = jingleEl.getAttributeStaticStr("sid");
			Action action = Action.from(jingleEl.getAttributeStaticStr("action"));
			switch (action) {
				case sessionInitiate:
					subscriberSession.set(sessionId);
					executor.execute(() -> {
						try {
							subscriberConnection.setRemoteDescription(new

																			  RTCSessionDescription(RTCSdpType.OFFER,
																									SDP.from(jingleEl).

																											toString("0")),
																	  new

																			  SetSessionDescriptionObserver() {
																				  @Override
																				  public void onSuccess() {
																					  System.out.println(
																							  "remote description set!");
																					  subscriberConnection.createAnswer(
																							  new RTCAnswerOptions(),
																							  new CreateSessionDescriptionObserver() {
																								  @Override
																								  public void onSuccess(
																										  RTCSessionDescription rtcSessionDescription) {
																									  System.out.println(
																											  "response prepared!");
																									  subscriberConnection
																											  .setLocalDescription(
																													  rtcSessionDescription,
																													  new SetSessionDescriptionObserver() {
																														  @Override
																														  public void onSuccess() {
																															  System.out
																																	  .println(
																																			  "local description set!");
																															  Element iqEl = new Element(
																																	  "iq");
																															  iqEl.setAttribute(
																																	  "id",
																																	  UUID.randomUUID()
																																			  .toString());
																															  iqEl.setAttribute(
																																	  "type",
																																	  StanzaType.set
																																			  .name());
																															  iqEl.addChild(
																																	  SDP.from(
																																			  rtcSessionDescription.sdp,
																																			  Content.Creator.responder)
																																			  .toElement(
																																					  Action.sessionAccept,
																																					  sessionId,
																																					  user1));
																															  MeetTest.this
																																	  .process(
																																			  Packet.packetInstance(
																																					  iqEl,
																																					  user1,
																																					  JID.jidInstanceNS(
																																							  meetJid)));
																														  }

																														  @Override
																														  public void onFailure(
																																  String s) {
																															  System.out
																																	  .println(
																																			  "local description failed: " +
																																					  s);
																														  }
																													  });
																								  }

																								  @Override
																								  public void onFailure(
																										  String s) {
																									  System.out.println(
																											  "response creation failed: " +
																													  s);
																								  }
																							  });
																				  }

																				  @Override
																				  public void onFailure(String s) {
																					  System.out.println(
																							  "remote description failed: " +
																									  s);
																					  System.out.println("yy: " + SDP.from(jingleEl).toString("0"));
																				  }
																			  });
						} catch (Throwable ex) {
							ex.printStackTrace();
							System.out.println("xx: " + SDP.from(jingleEl).toString("0"));
						}
					});
					process(packet.okResult((String) null, 0));
					break;
				case sessionAccept:
					assertEquals(publisherSession.get(), sessionId);
					//new Thread(() -> {
						publisherConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.ANSWER, SDP.from(jingleEl).toString("0")),
																 new SetSessionDescriptionObserver() {
																	 @Override
																	 public void onSuccess() {
																		 System.out.println("remote description set!");
																	 }

																	 @Override
																	 public void onFailure(String s) {
																		 System.out.println("remote description failed: " + s);
																	 }
																 });
					//}).start();
					process(packet.okResult((String) null, 0));
					break;
				case transportInfo: {
					SDP sdp = SDP.from(jingleEl);
					if (sessionId.equals(publisherSession.get())) {
						System.out.println("publisherConnection: " + publisherConnection);
						System.out.println("publisherConnection.getRemoteDescription().sdp: " + publisherConnection.getRemoteDescription());
						List<String> mLines = Arrays.stream(publisherConnection.getRemoteDescription().sdp.split("\r\n"))
								.filter(it -> it.startsWith("a=mid:"))
								.collect(Collectors.toList());
						for (Content content : sdp.getContents()) {
							for (Transport transport : content.getTransports()) {
								for (Candidate candidate : transport.getCandidates()) {
									int mLineIndex = mLines.indexOf("a=mid:" + content.getName());
									RTCIceCandidate iceCandidate = new RTCIceCandidate(content.getName(), mLineIndex,
																					   "a=" + candidate.toSDP());
									publisherConnection.addIceCandidate(iceCandidate);
								}
							}
						}
					} else if (sessionId.equals(subscriberSession.get())) {
						executor.execute(() -> {
							System.out.println("subscriberConnection: " + subscriberConnection);
							System.out.println("subscriberConnection.getRemoteDescription().sdp: " + subscriberConnection.getRemoteDescription());
							List<String> mLines = Arrays.stream(subscriberConnection.getRemoteDescription().sdp.split("\r\n"))
									.filter(it -> it.startsWith("a=mid:"))
									.collect(Collectors.toList());
							for (Content content : sdp.getContents()) {
								for (Transport transport : content.getTransports()) {
									for (Candidate candidate : transport.getCandidates()) {
										int mLineIndex = mLines.indexOf("a=mid:" + content.getName());
										RTCIceCandidate iceCandidate = new RTCIceCandidate(content.getName(), mLineIndex,
																						   "a=" + candidate.toSDP());
										subscriberConnection.addIceCandidate(iceCandidate);
									}
								}
							}
						});
					} else {
						assertTrue("Invalid session id: " + sessionId, false);
					}
					packet.okResult((String) null, 0);
				}
				break;
				case contentAdd:
				case contentModify:
				case contentRemove: {
					SDP sdp = SDP.from(jingleEl);
					if (sessionId.equals(publisherSession.get())) {
						SDP oldSdp = SDP.from(publisherConnection.getRemoteDescription().sdp, Content.Creator.responder);
						SDP newSdp = oldSdp.applyDiff(ContentAction.fromJingleAction(action), sdp);
						publisherConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, newSdp.toString("0")),
																 new SetSessionDescriptionObserver() {
																	 @Override
																	 public void onSuccess() {
																		 System.out.println("remote description success!");
																	 }

																	 @Override
																	 public void onFailure(String s) {
																		 System.out.println("remote description failed: " + s);
																	 }
																 });
					} else if (sessionId.equals(subscriberSession.get())) {
						executor.execute(() -> {
							SDP oldSdp = SDP.from(subscriberConnection.getRemoteDescription().sdp, Content.Creator.initiator);
							SDP newSdp = oldSdp.applyDiff(ContentAction.fromJingleAction(action), sdp);
							subscriberConnection.setRemoteDescription(new RTCSessionDescription(RTCSdpType.OFFER, newSdp.toString("0")),
																	  new SetSessionDescriptionObserver() {
																		  @Override
																		  public void onSuccess() {
																			  System.out.println("remote description success: " + subscriberConnection.getConnectionState() + " : " + subscriberConnection.getIceConnectionState() + " : " + subscriberConnection.getSignalingState());
																			  if (subscriberConnection.getSignalingState() == RTCSignalingState.HAVE_REMOTE_OFFER) {
																				  subscriberConnection.createAnswer(new RTCAnswerOptions(),
																													new CreateSessionDescriptionObserver() {
																														@Override
																														public void onSuccess(
																																RTCSessionDescription rtcSessionDescription) {
																															SDP oldSdp = SDP.from(
																																	subscriberConnection
																																			.getCurrentLocalDescription().sdp,
																																	Content.Creator.responder);
																															Map<ContentAction, SDP> changes = SDP
																																	.from(rtcSessionDescription.sdp,
																																		  Content.Creator.responder)
																																	.diffFrom(
																																			oldSdp);
																															subscriberConnection.setLocalDescription(
																																	rtcSessionDescription,
																																	new SetSessionDescriptionObserver() {
																																		@Override
																																		public void onSuccess() {
																																			System.out
																																					.println(
																																							"local description success!");
																																			for (ContentAction contentAction : ContentAction
																																					.values()) {
																																				SDP value = changes
																																						.get(contentAction);
																																				if (value ==
																																						null) {
																																					continue;
																																				}

																																				Element iqEl = new Element(
																																						"iq");
																																				iqEl.setAttribute(
																																						"id",
																																						UUID.randomUUID()
																																								.toString());
																																				iqEl.setAttribute(
																																						"type",
																																						StanzaType.set
																																								.name());
																																				iqEl.addChild(
																																						value.toElement(
																																								Action.contentAccept,
																																								sessionId,
																																								user1));
																																				MeetTest.this
																																						.process(
																																								Packet.packetInstance(
																																										iqEl,
																																										user1,
																																										JID.jidInstanceNS(
																																												meetJid)));
																																			}
																																		}

																																		@Override
																																		public void onFailure(
																																				String s) {
																																			System.out
																																					.println(
																																							"local description failure: " +
																																									s);
																																		}
																																	});
																														}

																														@Override
																														public void onFailure(
																																String s) {

																														}
																													});
																			  }
																		  }

																		  @Override
																		  public void onFailure(String s) {
																			  System.out.println("remote description failed: " + s);
																		  }
																	  });
						});
					}
					break;
				}
				default:
					throw new UnsupportedOperationException("Jingle action " + action + " is not supported!");
			}
			log.log(Level.FINEST, "packet with id " + packet.getAttributeStaticStr("id") + " was processed!");
		};

		publisherSession.set(UUID.randomUUID().toString());
		publisherConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
			@Override
			public void onSuccess(RTCSessionDescription rtcSessionDescription) {
				publisherConnection.setLocalDescription(rtcSessionDescription, new SetSessionDescriptionObserver() {
					@Override
					public void onSuccess() {
						System.out.println("local description set!");
						SDP sdp = SDP.from(rtcSessionDescription.sdp, Content.Creator.initiator);
						Element iqEl = new Element("iq");
						iqEl.setAttribute("id", UUID.randomUUID().toString());
						iqEl.setAttribute("type", StanzaType.set.name());
						iqEl.addChild(sdp.toElement(Action.sessionInitiate, publisherSession.get(), user1));
						process(Packet.packetInstance(iqEl, user1, JID.jidInstanceNS(meetJid)));
					}
					
					@Override
					public void onFailure(String s) {
						System.out.println("local description failed: " + s);
					}
				});
			}

			@Override
			public void onFailure(String s) {

			}
		});

		for (int i = 0; i < 1000; i++) {
			System.out.println("peer connection 1 state: " + publisherConnection.getConnectionState() + ", " + publisherConnection.getIceConnectionState());
			System.out.println("peer connection 2 state: " + subscriberConnection.getConnectionState() + ", " + subscriberConnection.getIceConnectionState() + ", " + subscriberConnection.getSignalingState());
			Thread.sleep(1000);
		}
	}

	protected void process(Packet packet) {
		log.log(Level.FINEST, () -> "xmpp: C >> S " + packet.toString());
		Element jingleEl = packet.getElemChild("jingle", "urn:xmpp:jingle:1");
		if (jingleEl != null) {
			System.out.println(
					">> " + jingleEl.getAttributeStaticStr("sid") + " : " + jingleEl.getAttributeStaticStr("action"));
			System.out.println(jingleEl.toString());
		}
		Runnable run = Optional.ofNullable(packetWriter.getProcessor(packet)).orElse(() -> {
			try {
				jingleMeetModule.process(packet);
			} catch (ComponentException|TigaseStringprepException ex) {
				throw new RuntimeException(ex);
			}
		});
		run.run();
	}

	@Bean(name = "packetWriter", active = true)
	public static class DummyPacketWriter implements PacketWriter {

		protected Consumer<Packet> packetConsumer;
		private ResponseManager responseManager = new ResponseManager();

		public Runnable getProcessor(Packet packet) {
			return responseManager.getResponseHandler(packet);
		}

		@Override
		public void write(Collection<Packet> collection) {
			throw new UnsupportedOperationException("Feature not implemented!");
		}

		@Override
		public void write(Packet packet) {
			packetConsumer.accept(packet);
		}

		@Override
		public void write(Packet packet, AsyncCallback callback) {
			this.responseManager.registerResponseHandler(packet, 60000L, callback);
			packetConsumer.accept(packet);
		}
	}
}
