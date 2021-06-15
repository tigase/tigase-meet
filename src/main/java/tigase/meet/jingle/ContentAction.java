/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.jingle;

public enum ContentAction {
	init,
	add,
	remove,
	modify,
	accept;

	public static ContentAction fromJingleAction(Action action) {
		switch (action) {
			case sessionInitiate:
				return init;
			case contentAdd:
				return add;
			case contentRemove:
				return remove;
			case contentModify:
				return modify;
			case contentAccept:
				return accept;
			default:
				return null;
		}
	}

	public Action toJingleAction(Action defAction) {
		switch (this) {
			case add:
				return Action.contentAdd;
			case remove:
				return Action.contentRemove;
			case modify:
				return Action.contentModify;
			case accept:
				return Action.contentAccept;
			default:
				return defAction;
		}
	}
}
