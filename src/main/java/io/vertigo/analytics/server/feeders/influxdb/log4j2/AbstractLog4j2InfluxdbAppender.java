package io.vertigo.analytics.server.feeders.influxdb.log4j2;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.LogMessage;
import io.vertigo.analytics.server.TraceSpan;
import io.vertigo.core.lang.json.CoreJsonAdapters;

abstract class AbstractLog4j2InfluxdbAppender<O> extends AbstractAppender {

	private static final Gson GSON = CoreJsonAdapters.addCoreGsonConfig(new GsonBuilder(), false)
			.registerTypeAdapter(TraceSpan.class, new AProcessJsonDeserializer())
			.create();

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
			if (logMessage.getEvent() != null) {
				influxDBClient.getWriteApiBlocking().writePoints(logMessage.getAppName(), org, eventToPoints(logMessage.getEvent(), logMessage.getHost()));
			}
			if (logMessage.getEvents() != null) { //for batch send
				for (final O batchEvent : logMessage.getEvents()) {
					influxDBClient.getWriteApiBlocking().writePoints(logMessage.getAppName(), org, eventToPoints(batchEvent, logMessage.getHost()));
				}
			}
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

	private static class AProcessJsonDeserializer implements JsonDeserializer<TraceSpan> {

		/** {@inheritDoc} */
		@Override
		public TraceSpan deserialize(final JsonElement jsonElement, final Type type, final JsonDeserializationContext context) {
			//need a custom deserializer to support old AProcess format (subProcesses instead of childSpans, and missing field as empty map or list)
			final JsonObject jsonObject = jsonElement.getAsJsonObject();
			final String category = jsonObject.getAsJsonPrimitive("category").getAsString();

			final String name = jsonObject.getAsJsonPrimitive("name").getAsString();

			final long start = jsonObject.getAsJsonPrimitive("start").getAsLong();
			final long end = jsonObject.getAsJsonPrimitive("end").getAsLong();

			final Map<String, Double> measures = jsonObject.has("measures") ? context.deserialize(jsonObject.getAsJsonObject("measures"), TypeToken.getParameterized(Map.class, String.class, Double.class).getType())
					: Collections.emptyMap();
			final Map<String, String> tags = jsonObject.has("tags") ? context.deserialize(jsonObject.getAsJsonObject("tags"), TypeToken.getParameterized(Map.class, String.class, String.class).getType())
					: Collections.emptyMap();
			final Map<String, String> metadatas = jsonObject.has("metadatas") ? context.deserialize(jsonObject.getAsJsonObject("metadatas"), TypeToken.getParameterized(Map.class, String.class, String.class).getType())
					: Collections.emptyMap();
			final JsonArray subProcessArray = jsonObject.has("childSpans") ? jsonObject.getAsJsonArray("childSpans")
					: jsonObject.has("subProcesses") ? jsonObject.getAsJsonArray("subProcesses")
							: null;
			final List<TraceSpan> subProcesses = subProcessArray != null ? context.deserialize(subProcessArray, TypeToken.getParameterized(List.class, TraceSpan.class).getType())
					: Collections.emptyList();

			return new TraceSpan(
					category, name, Instant.ofEpochMilli(start), Instant.ofEpochMilli(end),
					measures, metadatas, tags, subProcesses);
		}
	}

}
