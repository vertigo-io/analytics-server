package io.vertigo.analytics.server.feeders.influxdb.log4j2;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.LogMessage;

abstract class AbstractLog4j2InfluxdbAppender<O> extends AbstractAppender {

	private static final Gson GSON = new GsonBuilder().create();

	private final InfluxDBClient influxDBClient;
	private final String org;
	private final String orgId;

	@Override
	public void stop() {
		if (influxDBClient != null) {
			influxDBClient.close();
		}

	}

	protected AbstractLog4j2InfluxdbAppender(
			final String name,
			final Filter filter,
			final Configuration config,
			final String serverUrl,
			final String token,
			final String org) {
		super(name, filter, null, true);
		//---
		influxDBClient = InfluxDBClientFactory.create(serverUrl, token.toCharArray(), org);
		this.org = org;
		orgId = influxDBClient.getOrganizationsApi().findOrganizations().stream().filter(organization -> organization.getName().equals(org)).findFirst().get().getId();
	}

	@Override
	public void append(final LogEvent event) {

		try {
			final LogMessage<O> logMessage = GSON.fromJson(event.getMessage().getFormattedMessage(), getLogMessageType());
			if (!influxDBClient.getBucketsApi().findBuckets().stream().anyMatch(bucket -> bucket.getName().equals(logMessage.getAppName()))) {
				influxDBClient.getBucketsApi().createBucket(logMessage.getAppName(), orgId);
			}
			influxDBClient.getWriteApi().writePoints(logMessage.getAppName(), org, eventToPoints(logMessage.getEvent(), logMessage.getHost()));
			//db.write(logMessage.getAppName(), "autogen", eventToPoints(logMessage.getEvent(), logMessage.getHost()));
		} catch (final JsonSyntaxException e) {
			// it wasn't a message for us so we do nothing
		} catch (final Exception e) {
			getHandler().error("error writing log to influxdb", e);//by default the logger evicts some logs on the appender to avoid flooding. (better than nothing)
		}

	}

	protected abstract List<Point> eventToPoints(final O healthCheck, final String host);

	protected abstract Type getEventType();

	private Type getLogMessageType() {
		return new ParameterizedType() {

			@Override
			public Type getRawType() {
				return LogMessage.class;
			}

			@Override
			public Type getOwnerType() {
				return null;
			}

			@Override
			public Type[] getActualTypeArguments() {
				return new Type[] { getEventType() };
			}
		};
	}

}
