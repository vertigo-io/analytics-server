package io.vertigo.analytics.server.feeders.influxdb.log4j;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.LogMessage;

abstract class AbstractInfluxdbAppender<O> extends AppenderSkeleton {

	//Appender params
	private String serverUrl;
	private String token;
	private String org;
	private String orgId;

	private static final Gson GSON = new GsonBuilder().create();

	private InfluxDBClient influxDBClient;

	@Override
	public void close() {
		if (influxDBClient != null) {
			influxDBClient.close();
		}

	}

	@Override
	public boolean requiresLayout() {
		return false;
	}

	@Override
	protected void append(final LoggingEvent event) {

		if (event.getMessage() instanceof String) {
			try {
				final LogMessage<O> logMessage = GSON.fromJson((String) event.getMessage(), getLogMessageType());
				final InfluxDBClient db = getClient();
				if (!db.getBucketsApi().findBuckets().stream().anyMatch(bucket -> bucket.getName().equals(logMessage.getAppName()))) {
					db.getBucketsApi().createBucket(logMessage.getAppName(), orgId);
				}
				db.getWriteApiBlocking().writePoints(logMessage.getAppName(), getOrg(), eventToPoints(logMessage.getEvent(), logMessage.getHost()));
				//db.write(logMessage.getAppName(), "autogen", eventToPoints(logMessage.getEvent(), logMessage.getHost()));
			} catch (final JsonSyntaxException e) {
				// it wasn't a message for us so we do nothing
			} catch (final Exception e) {
				// for now we do nothing
				//LogLog.error("error writing log to influxdb", e); // if we want to log all errors occuring in the appender (might cause flooding of the logs)
				getErrorHandler().error("error writing log to influxdb", e, ErrorCode.WRITE_FAILURE);//by default the logger log only one error on the appender to avoid flooding. (better than nothing)
			}
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

	private InfluxDBClient getClient() {
		if (influxDBClient == null) {
			influxDBClient = InfluxDBClientFactory.create(getServerUrl(), getToken().toCharArray(), getOrg());
			orgId = influxDBClient.getOrganizationsApi().findOrganizations().stream().filter(org -> org.getName().equals(getOrg())).findFirst().get().getId();
		}
		return influxDBClient;
	}

	// getters setters (log4j way)

	public String getServerUrl() {
		return serverUrl;
	}

	public void setServerUrl(final String serverUrl) {
		this.serverUrl = serverUrl;
	}

	public String getToken() {
		return token;
	}

	public void setToken(final String token) {
		this.token = token;
	}

	public String getOrg() {
		return org;
	}

	public void setOrg(final String org) {
		this.org = org;
	}

}
