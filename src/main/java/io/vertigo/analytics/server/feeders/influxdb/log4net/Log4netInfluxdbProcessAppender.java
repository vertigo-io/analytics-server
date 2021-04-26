package io.vertigo.analytics.server.feeders.influxdb.log4net;

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

import io.vertigo.analytics.server.events.process.AProcess;
import io.vertigo.analytics.server.feeders.influxdb.InfluxdbUtil;

@Plugin(name = "InfluxdbProcess4net", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class Log4netInfluxdbProcessAppender extends AbstractLog4netInfluxdbAppender<AProcess> {

	private Log4netInfluxdbProcessAppender(
			final String name,
			final Filter filter,
			final Configuration config,
			final String serverUrl,
			final String token,
			final String org) {
		super(name, filter, config, serverUrl, token, org);
	}

	@Override
	protected List<Point> eventToPoints(final AProcess process, final String host) {
		return InfluxdbUtil.processToPoints(process, host);
	}

	@Override
	protected Type getEventType() {
		return AProcess.class;
	}

	@PluginFactory
	public static Log4netInfluxdbProcessAppender createAppender(
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
		return new Log4netInfluxdbProcessAppender(name, filter, config, serverUrl, token, org);
	}
}
