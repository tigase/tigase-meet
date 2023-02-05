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
