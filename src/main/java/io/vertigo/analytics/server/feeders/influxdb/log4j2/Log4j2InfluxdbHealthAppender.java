package io.vertigo.analytics.server.feeders.influxdb.log4j2;

import java.lang.reflect.Type;
import java.util.List;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.events.health.HealthCheck;
import io.vertigo.analytics.server.feeders.influxdb.InfluxdbUtil;

@Plugin(name = "InfluxdbHealth", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class Log4j2InfluxdbHealthAppender extends AbstractLog4j2InfluxdbAppender<HealthCheck> {

	private Log4j2InfluxdbHealthAppender(
			final String name,
			final Filter filter,
			final Configuration config,
			final String serverUrl,
			final String token,
			final String org) {
		super(name, filter, config, serverUrl, token, org);
	}

	@Override
	protected List<Point> eventToPoints(final HealthCheck healthCheck, final String host) {
		return InfluxdbUtil.heathCheckToPoints(healthCheck, host);
	}

	@Override
	protected Type getEventType() {
		return HealthCheck.class;
	}

	@PluginFactory
	public static Log4j2InfluxdbHealthAppender createAppender(
			@PluginAttribute("name") final String name,
			@PluginConfiguration final Configuration config,
			@PluginElement("Filter") final Filter filter,
			@PluginAttribute("serverUrl") final String serverUrl,
			@PluginAttribute("token") final String token,
			@PluginAttribute("org") final String org) {
		if (name == null) {
			LOGGER.error("A name for the Appender must be specified");
			return null;
		}
		return new Log4j2InfluxdbHealthAppender(name, filter, config, serverUrl, token, org);
	}
}
