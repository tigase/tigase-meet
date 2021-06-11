/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet;

import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;

public class ParticipationWithListener extends AbstractParticipation<ParticipationWithListener,AbstractMeet<ParticipationWithListener>>
		implements LocalPublisher.Listener, LocalSubscriber.Listener {

	private Listener listener;

	public ParticipationWithListener(AbstractMeet<ParticipationWithListener> meet, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		super(meet, localPublisher, localSubscriber);
	}
	
	@Override
	public void receivedPublisherSDP(JSEP jsep) {
		listener.receivedPublisherSDP(jsep);
	}

	@Override
	public void receivedPublisherCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedPublisherCandidate(candidate);
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public void receivedSubscriberSDP(JSEP jsep) {
		listener.receivedSubscriberSDP(jsep);
	}

	@Override
	public void receivedSubscriberCandidate(JanusPlugin.Candidate candidate) {
		listener.receivedSubscriberCandidate(candidate);
	}
	
	public interface Listener {

		// only for subsequent publications of SDP
		void receivedPublisherSDP(JSEP jsep);

		void receivedPublisherCandidate(JanusPlugin.Candidate candidate);

		void receivedSubscriberSDP(JSEP jsep);

		void receivedSubscriberCandidate(JanusPlugin.Candidate candidate);

	}
}
