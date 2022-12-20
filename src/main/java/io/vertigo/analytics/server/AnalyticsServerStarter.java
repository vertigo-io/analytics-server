package io.vertigo.analytics.server;

import java.io.IOException;
import java.util.Optional;

import javax.net.ssl.KeyManagerFactory;

import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.net.ssl.KeyStoreConfiguration;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.net.ssl.StoreConfigurationException;
import org.apache.logging.log4j.core.net.ssl.TrustStoreConfiguration;
import org.apache.logging.log4j.server.SecureTcpSocketServer;
import org.apache.logging.log4j.server.TcpSocketServer;

import io.kinetix.analytics.server.AnalyticsTcpServer;
import io.vertigo.analytics.server.feeders.influxdb.http.HttpProcessInflux;
import io.vertigo.commons.CommonsFeatures;
import io.vertigo.connectors.influxdb.InfluxDbFeatures;
import io.vertigo.connectors.javalin.JavalinFeatures;
import io.vertigo.core.node.AutoCloseableNode;
import io.vertigo.core.node.config.BootConfig;
import io.vertigo.core.node.config.NodeConfig;
import io.vertigo.core.param.Param;
import io.vertigo.core.plugins.param.env.EnvParamPlugin;
import io.vertigo.core.plugins.resource.url.URLResourceResolverPlugin;
import io.vertigo.dashboard.DashboardFeatures;
import io.vertigo.database.DatabaseFeatures;
import io.vertigo.datamodel.DataModelFeatures;
import io.vertigo.vega.VegaFeatures;

/**
 * @author mlaroche
 *
 */
public class AnalyticsServerStarter {

	/**
	 * Args are by group of 3 ( type of server; port ; configUrl)
	 * @param args
	 * @throws IOException
	 * @throws NumberFormatException
	 * @throws StoreConfigurationException
	 */
	public static void main(final String[] args) throws NumberFormatException, IOException, StoreConfigurationException {
		if (args.length == 0 && args.length % 3 != 0) {
			throw new RuntimeException("You must provide three params");
		}
		boolean isLog4jEnabled = false;
		// all good
		for (int i = 0; i < (int) Math.floor(args.length / 3); i++) {
			final String port = args[i * 3 + 1];
			final String configFile = args[i * 3 + 2];
			switch (args[i * 3]) {
				case "log4j2":
					Configurator.initialize("definedLog4jContext", AnalyticsServerStarter.class.getClassLoader(), configFile);
					final TcpSocketServer javaSerializedTcpSocketServer = TcpSocketServer.createSerializedSocketServer(Integer.parseInt(port));
					javaSerializedTcpSocketServer.startNewThread();
					isLog4jEnabled = true;
					break;
				case "log4j2json":
					Configurator.initialize("definedLog4jContext", AnalyticsServerStarter.class.getClassLoader(), configFile);
					final TcpSocketServer jsoncpSocketServer = TcpSocketServer.createJsonSocketServer(Integer.parseInt(port));
					jsoncpSocketServer.startNewThread();
					isLog4jEnabled = true;
					break;
				case "log4j2jsonSsl":
					Configurator.initialize("definedLog4jContext", AnalyticsServerStarter.class.getClassLoader(), configFile);
					final var keyStoreUrl = Optional.ofNullable(System.getenv("KEYSTORE_URL")).orElse("/opt/analytics/ssl/keystore.p12");
					final var trustStoreUrlOpt = Optional.ofNullable(System.getenv("TRUSTSTORE_URL"));
					final TcpSocketServer jsonSSlTcpSocketServer = SecureTcpSocketServer.createJsonServer(Integer.parseInt(port),
							SslConfiguration.createSSLConfiguration(
									"TLSv1.2",
									KeyStoreConfiguration.createKeyStoreConfiguration(keyStoreUrl, null, "KEYSTORE_PASSWORD", null, "PKCS12", KeyManagerFactory
											.getDefaultAlgorithm()),
									trustStoreUrlOpt.isPresent() ? TrustStoreConfiguration.createKeyStoreConfiguration(trustStoreUrlOpt.get(), null, "TRUSTSTORE_PASSWORD", null, "PKCS12", KeyManagerFactory
											.getDefaultAlgorithm()) : null,
									false));
					jsonSSlTcpSocketServer.startNewThread();
					isLog4jEnabled = true;
					break;
				case "log4net":
					Configurator.initialize("definedLog4netContext", AnalyticsServerStarter.class.getClassLoader(), configFile);
					final AnalyticsTcpServer ats = new AnalyticsTcpServer();
					ats.start(Integer.parseInt(port));
					break;
				case "localUi":
					try (AutoCloseableNode node = new AutoCloseableNode(buildNodeConfig(port))) {
						try {
							Thread.currentThread().join();
						} catch (final InterruptedException e) {
							//nothing
						}
					}
					break;
				default:
					break;
			}
		}
		// at least one is started
		if (isLog4jEnabled) {
			HttpProcessInflux.start();
		}

	}

	private static NodeConfig buildNodeConfig(final String port) {
		return NodeConfig.builder()
				.withBoot(BootConfig.builder()
						.withLocales("fr_FR")
						.addPlugin(EnvParamPlugin.class)
						.addPlugin(URLResourceResolverPlugin.class)
						.build())
				.addModule(new JavalinFeatures()
						.withEmbeddedServer(
								Param.of("port", port),
								Param.of("ssl", "true"),
								Param.of("keyStoreUrl", "${KEYSTORE_URL}"),
								Param.of("keyStorePassword", "${KEYSTORE_PASSWORD}"),
								Param.of("sslKeyAlias", "${SSL_KEY_ALIAS}"))
						.build())
				.addModule(new InfluxDbFeatures()
						.withInfluxDb(
								Param.of("host", "${INFLUXDB_URL}"),
								Param.of("token", "${INFLUXDB_TOKEN}"),
								Param.of("org", "${INFLUXDB_ORG}"))
						.build())
				.addModule(new CommonsFeatures()
						.build())
				.addModule(new DatabaseFeatures()
						.withTimeSeriesDataBase()
						.withInfluxDb()
						.build())
				.addModule(new DataModelFeatures()
						.build())
				.addModule(new VegaFeatures()
						.withWebServices()
						.withJavalinWebServerPlugin(Param.of("apiPrefix", "/api"))
						.build())
				.addModule(new DashboardFeatures()
						.withAnalytics(
								Param.of("appName", "${APP_NAME}"))
						.build())
				.build();
	}

}
