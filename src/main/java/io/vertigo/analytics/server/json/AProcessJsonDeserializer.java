package io.vertigo.analytics.server.json;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import io.vertigo.analytics.server.TraceSpan;

public class AProcessJsonDeserializer implements JsonDeserializer<TraceSpan> {

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
