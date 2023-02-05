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
package tigase.meet.janus;

import tigase.meet.janus.videoroom.JanusVideoRoomPlugin;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class JanusPluginsRegister {

	private final ConcurrentHashMap<Class<? extends JanusPlugin>, String> pluginToId = new ConcurrentHashMap<>();

	JanusPluginsRegister() {
		register(JanusVideoRoomPlugin.class, JanusVideoRoomPlugin.ID);
	}

	public void register(Class<? extends JanusPlugin> plugin, String id) {
		pluginToId.put(plugin, id);
	}

	public void unregister(Class<? extends JanusPlugin> plugin) {
		pluginToId.remove(plugin);
	}

	public String getPluginId(Class<? extends JanusPlugin> plugin) {
		return Optional.ofNullable(pluginToId.get(plugin))
				.orElseThrow(() -> new NoSuchElementException(
						"Plugin class " + plugin.getCanonicalName() + " was not registered!"));
	}

}
