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

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.meet.IMeetRepository;
import tigase.meet.MeetComponent;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

@Bean(name = "janus", parent = MeetComponent.class, active = true)
public class JanusService implements Initializable, UnregisterAware {
	@ConfigField(desc = "URI for connect to Janus", alias = "uri")
	private String uri;
	@ConfigField(desc = "Janus session timeout", alias = "session-timeout")
	private Duration sessionTimeout = Duration.of(60, ChronoUnit.SECONDS);

	private HttpClient client;

	private JanusPluginsRegister pluginsRegister = new JanusPluginsRegister();
	private ScheduledExecutorService executorService;


	@Inject(nullAllowed = true)
	private IMeetRepository meetRepository;

	@Override
	public void initialize() {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] {new DummyTrustManager()}, null);
			client = HttpClient.newBuilder().sslContext(sslContext).build();
			executorService = Executors.newScheduledThreadPool(4);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void beforeUnregister() {
		if (executorService != null) {
			executorService.shutdown();
		}
	}
	
	static class DummyTrustManager extends X509ExtendedTrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {

		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
				throws CertificateException {

		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {

		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
				throws CertificateException {

		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {

		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {

		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	private static final Logger log = Logger.getLogger(JanusService.class.getCanonicalName());

	public CompletableFuture<JanusConnection> newConnection() {
		JanusConnection connection = new JanusConnection(pluginsRegister, executorService, sessionTimeout);
		log.log(Level.FINER, () -> connection.logPrefix() + ", creating connection..");
		return client.newWebSocketBuilder()
				.subprotocols("janus-protocol")
				.buildAsync(URI.create(uri), connection)
				.thenApply(webSocket -> {
					connection.setWebSocket(webSocket);
					return connection;
				}).whenComplete((conn, ex) -> {
					if (ex != null) {
						log.log(Level.WARNING, ex, () -> connection.logPrefix() + ", connection creation failed.");
					} else {
						log.log(Level.FINER, () -> connection.logPrefix() + ", connection created.");
					}
				});
	}
}
