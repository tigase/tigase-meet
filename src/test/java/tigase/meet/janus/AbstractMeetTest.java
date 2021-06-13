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
import org.junit.Test;
import tigase.component.DSLBeanConfigurator;
import tigase.conf.ConfigBuilder;
import tigase.kernel.AbstractKernelTestCase;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.meet.AbstractMeet;
import tigase.meet.ParticipationWithListener;
import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;
import tigase.util.log.LogFormatter;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AbstractMeetTest
		extends AbstractKernelTestCase {

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
		ConfigBuilder configBuilder = new ConfigBuilder().with(new AbstractBeanConfigurator.BeanDefinition.Builder().name("janus").active(true).with("uri", "wss://127.0.0.1:8989/").build());
		getKernel().getInstance(DSLBeanConfigurator.class).setProperties(configBuilder.build());
		janusService = getKernel().getInstance(JanusService.class);
	}


	@Test
	public void test() throws ExecutionException, InterruptedException {
		MeetTest2 meet = new MeetTest2(janusService.newConnection().get(), 1234l);

		ParticipationWithListener participation = meet.join().get();
//		CompletableFuture<Meet.Participation> participantFuture2 = meet.join();
//		CompletableFuture<Meet.Participation> participantFuture3 = meet.join();
//		CompletableFuture.allOf(participantFuture1, participantFuture2, participantFuture3).get();

		PeerConnectionFactory factory = new PeerConnectionFactory();
		ReentrantLock lock = new ReentrantLock();
		RTCPeerConnection publisherConnection = factory.createPeerConnection(new RTCConfiguration(), new PeerConnectionObserver() {
			@Override
			public void onIceCandidate(RTCIceCandidate rtcIceCandidate) {
				JanusVideoRoomPlugin.Candidate candidate = new JanusVideoRoomPlugin.Candidate(rtcIceCandidate.sdpMid, rtcIceCandidate.sdpMLineIndex, rtcIceCandidate.sdp);
				participation.sendPublisherCandidate(candidate);
			}
		});

		AudioSource audioSource = factory.createAudioSource(new AudioOptions());

		publisherConnection.addTrack(factory.createAudioTrack("m1", audioSource), Collections.EMPTY_LIST);
		
		RTCPeerConnection subscriberConnection = factory.createPeerConnection(new RTCConfiguration(), new PeerConnectionObserver() {
			@Override
			public void onIceCandidate(RTCIceCandidate rtcIceCandidate) {
				JanusVideoRoomPlugin.Candidate candidate = new JanusVideoRoomPlugin.Candidate(rtcIceCandidate.sdpMid, rtcIceCandidate.sdpMLineIndex, rtcIceCandidate.sdp);
				participation.sendSubscriberCandidate(candidate);
			}
		});
		
		participation.setListener(new ParticipationWithListener.Listener() {

			@Override
			public void receivedPublisherCandidate(JanusPlugin.Candidate candidate) {
				RTCIceCandidate iceCandidate = new RTCIceCandidate(candidate.getMid(), candidate.getSdpMLineIndex(), candidate.getCandidate());
				publisherConnection.addIceCandidate(iceCandidate);
			}
			
			@Override
			public void receivedPublisherSDP(JSEP jsep) {
				RTCSessionDescription answer = new RTCSessionDescription(
						jsep.getType() == JSEP.Type.offer ? RTCSdpType.OFFER : RTCSdpType.ANSWER, jsep.getSdp());
				new Thread(() -> {
					publisherConnection.setRemoteDescription(answer, new SetSessionDescriptionObserver() {
						@Override
						public void onSuccess() {
							System.out.println("remote description set!");
						}

						@Override
						public void onFailure(String s) {
							System.out.println("remote description failed: " + s);
						}
					});
				}).start();
			}

			@Override
			public void receivedSubscriberCandidate(JanusPlugin.Candidate candidate) {
				RTCIceCandidate iceCandidate = new RTCIceCandidate(candidate.getMid(), candidate.getSdpMLineIndex(), candidate.getCandidate());
				subscriberConnection.addIceCandidate(iceCandidate);
			}

			@Override
			public void receivedSubscriberSDP(JSEP jsep) {
				RTCSessionDescription sessionDescription = new RTCSessionDescription(jsep.getType() == JSEP.Type.offer ? RTCSdpType.OFFER : RTCSdpType.ANSWER, jsep.getSdp());
				new Thread(() -> {
					subscriberConnection.setRemoteDescription(sessionDescription, new SetSessionDescriptionObserver() {
						@Override
						public void onSuccess() {
							if (jsep.getType() == JSEP.Type.offer) {
								System.out.println("remote description set!");
								subscriberConnection.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
									@Override
									public void onSuccess(RTCSessionDescription rtcSessionDescription) {
										System.out.println("response prepared!");
										subscriberConnection.setLocalDescription(rtcSessionDescription, new SetSessionDescriptionObserver() {
											@Override
											public void onSuccess() {
												System.out.println("local description set!");
												participation.sendSubscriberSDP(
														new JSEP(JSEP.Type.answer, rtcSessionDescription.sdp));
											}

											@Override
											public void onFailure(String s) {
												System.out.println("local description failed: " + s);
											}
										});
									}

									@Override
									public void onFailure(String s) {
										System.out.println("response creation failed: " + s);
									}
								});
							}
						}

						@Override
						public void onFailure(String s) {
							System.out.println("remote description failed: " + s);
						}
					});
				}).start();
			}
		});

		publisherConnection.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
			@Override
			public void onSuccess(RTCSessionDescription rtcSessionDescription) {
				publisherConnection.setLocalDescription(rtcSessionDescription, new SetSessionDescriptionObserver() {
					@Override
					public void onSuccess() {
						System.out.println("local description set!");
						//try {
							participation.sendPublisherSDP(new JSEP(rtcSessionDescription.sdpType == RTCSdpType.OFFER ? JSEP.Type.offer : JSEP.Type.answer, rtcSessionDescription.sdp));//.get();
//						} catch (InterruptedException|ExecutionException e) {
//							e.printStackTrace();
//						}
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
			System.out.println("peer connection 2 state: " + subscriberConnection.getConnectionState() + ", " + subscriberConnection.getIceConnectionState() + "\n" + subscriberConnection.getCurrentLocalDescription() + "\n" + subscriberConnection.getCurrentRemoteDescription());
			Thread.sleep(1000);
		}
	}

	public class MeetTest2 extends AbstractMeet<ParticipationWithListener> {

		public MeetTest2(JanusConnection janusConnection, Object roomId) {
			super(janusConnection, roomId);
		}

		@Override
		public void left(ParticipationWithListener participation) {

		}

		public CompletableFuture<ParticipationWithListener> join() {
			return join(((publisher, subscriber) -> new ParticipationWithListener(this, publisher, subscriber)));
		}

	}
}
