/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
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
