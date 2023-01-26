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
package tigase.meet;

import tigase.meet.janus.JSEP;
import tigase.meet.janus.JanusPlugin;
import tigase.meet.janus.videoroom.LocalPublisher;
import tigase.meet.janus.videoroom.LocalSubscriber;
import tigase.meet.janus.videoroom.Publisher;

import java.util.Collection;

public class ParticipationWithListener extends AbstractParticipation<ParticipationWithListener,AbstractMeet<ParticipationWithListener>>
		implements LocalPublisher.Listener, LocalSubscriber.Listener {

	private Listener listener;

	public ParticipationWithListener(AbstractMeet<ParticipationWithListener> meet, LocalPublisher localPublisher, LocalSubscriber localSubscriber) {
		super(meet, localPublisher, localSubscriber);
	}

	@Override
	public void addedPublishers(Collection<Publisher> publishers) {
		new Thread(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			ParticipationWithListener.super.addedPublishers(publishers);
		}).start();
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
		super.setListeners();
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
