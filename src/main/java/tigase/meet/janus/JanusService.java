/*
 * Tigase Meet - Video calls component for Tigase
 * Copyright (C) 2021 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.meet.janus;

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.licence.Licence;
import tigase.licence.LicenceChecker;
import tigase.licence.LicenceCheckerUpdateCallbackImpl;
import tigase.meet.IMeetRepository;
import tigase.meet.MeetComponent;
import tigase.xml.Element;

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

	private LicenceChecker licenceChecker;

	@Inject(nullAllowed = true)
	private IMeetRepository meetRepository;

	@Override
	public void initialize() {
		licenceChecker = LicenceChecker.getLicenceChecker("acs", new LicenceCheckerUpdateCallbackImpl("acs") {

			@Override
			public String getID() {
				return "meet";
			}

			@Override
			public Element getComponentAdditionalData() {
				Element element = super.getComponentAdditionalData();
				element.addChild(new Element("meets-count", String.valueOf(meetRepository.size())));
				element.addChild(new Element("meets-participants-max", String.valueOf(meetRepository.getMaxParticipantsInMeeting())));
				return element;
			}

			@Override
			public String getMissingLicenseWarning() {
				// FIXME: need a proper warning!!
				return "\nThis installation contains Tigase Meet package, not an open source software.\nThe Tigase Meet is only available under a commercial license.\nThe full text of the commercial license agreement is available upon request.\n\nMore information about Meet component and licensing can be found here:\nhttp://www.tigase.com/\nThe Tigase Meet component is provided free of charge for testing and\ndevelopment purposes only. Any use of the component on production systems,\neither commercial or not-for-profit, requires the purchase of a license.\n\nIf the Tigase Meet component is activated without a valid license, it will\ncontinue to work, including its full set of features, but it will send\ncertain statistical information to Tigase's servers on a regular basis.\nIf the Tigase Meet component cannot access our servers to send information,\nit will stop working. Once a valid license is installed, the Tigase Meet\ncomponent will stop sending statistical information to Tigase's servers.\n\nBy activating the Tigase Meet component without a valid license you agree\nand accept that the component will send certain statistical information\n(such as DNS domain names, vhost names, number of online users, number of\ncluster nodes, etc.) which may be considered confidential and proprietary\nby the user. You accept and confirm that such information, which may be\nconsidered confidential or proprietary, will be transferred to Tigase's\nservers and that you will not pursue any remedy at law as a result of the\ninformation transfer.\nIf the Tigase Meet component is installed but not activated, no statistical\ninformation will be sent to Tigase's servers.";
			}

			@Override
			public boolean additionalValidation(Licence lic) {
				if (!super.additionalValidation(lic)) {
					return false;
				}

				// FIXME: we need to retrieve "limits" from license and check them
				if (meetRepository.size() > lic.getPropertyAsInteger("meets-limit")) {
					return false;
				}

				if (meetRepository.getMaxParticipantsInMeeting() > lic.getPropertyAsInteger("meets-participants-max")) {
					return false;
				}
				return true;
			}
		});
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] {
					new X509ExtendedTrustManager() {
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
			}, null);
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
