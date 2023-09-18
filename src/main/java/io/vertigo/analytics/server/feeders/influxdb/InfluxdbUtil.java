package io.vertigo.analytics.server.feeders.influxdb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.TraceSpan;
import io.vertigo.core.analytics.health.HealthCheck;
import io.vertigo.core.analytics.health.HealthStatus;
import io.vertigo.core.analytics.metric.Metric;

public class InfluxdbUtil {

	private static final String TAG_NAME = "name";
	private static final String TAG_LOCATION = "location";
	private static final AtomicInteger nanoSeq = new AtomicInteger();

	private InfluxdbUtil() {
		// Util
	}

	public static List<Point> heathCheckToPoints(final HealthCheck healthCheck, final String host) {

		final String message = healthCheck.healthMeasure().message();
		final String messageToStore = message != null ? message : "";

		return Collections.singletonList(Point.measurement("healthcheck")
				.time(epochMilliToUniqueInstant(healthCheck.checkInstant()), WritePrecision.NS)
				.addField("location", host)
				.addField("name", healthCheck.name())
				.addField("checker", healthCheck.checker())
				.addField("module", healthCheck.module())
				.addField("feature", healthCheck.feature())
				.addField("status", getNumericValue(healthCheck.healthMeasure().status()))
				.addField("message", messageToStore)
				.addTag("location", host)
				.addTag("name", healthCheck.name())
				.addTag("checker", healthCheck.checker())
				.addTag("module", healthCheck.module())
				.addTag("feature", healthCheck.feature())
				.addTag("status", String.valueOf(getNumericValue(healthCheck.healthMeasure().status()))));
	}

	public static List<Point> metricToPoints(final Metric metric, final String host) {

		final String module = metric.module();// for now module is null
		final String moduleToStore = module != null ? module : "";

		return Collections.singletonList(Point.measurement("metric")
				.time(epochMilliToUniqueInstant(metric.measureInstant()), WritePrecision.NS)
				.addField("location", host)
				.addField("name", metric.name())
				.addField("module", moduleToStore)
				.addField("feature", metric.feature())
				.addField("value", metric.value())
				.addTag("location", host)
				.addTag("name", metric.name())
				.addTag("module", moduleToStore)
				.addTag("feature", metric.feature())
				.addTag("value", String.valueOf(metric.value())));
	}

	public static List<Point> processToPoints(final TraceSpan process, final String host) {
		final List<Point> points = new ArrayList<>();
		flatProcess(process, new Stack<>(), points, host);
		return points;
	}

	private static int getNumericValue(final HealthStatus status) {
		switch (status) {
			case RED:
				return 0;
			case YELLOW:
				return 1;
			case GREEN:
				return 2;
			default:
				throw new RuntimeException("Unkown satus : " + status);
		}
	}

	private static Point processToPoint(final TraceSpan process, final VisitState visitState, final String host) {
		final Map<String, Object> countFields = visitState.getCountsByCategory().entrySet().stream()
				.collect(Collectors.toMap((entry) -> entry.getKey() + "_count", (entry) -> entry.getValue()));
		final Map<String, Object> durationFields = visitState.getDurationsByCategory().entrySet().stream()
				.collect(Collectors.toMap((entry) -> entry.getKey() + "_duration", (entry) -> entry.getValue()));

		// we add a inner duration for convinience
		final long innerDuration = process.getDurationMillis() - process.getChildSpans()
				.stream()
				.collect(Collectors.summingLong(TraceSpan::getDurationMillis));

		final Map<String, String> properedTags = process.getTags().entrySet()
				.stream()
				.collect(Collectors.toMap(
						entry -> properString(entry.getKey()),
						entry -> properString(entry.getValue())));

		final Map<String, String> properedMetadatas = process.getMetadatas().entrySet()
				.stream()
				.collect(Collectors.toMap(
						entry -> properString(entry.getKey()),
						entry -> properString(entry.getValue())));

		return Point.measurement(process.getCategory())
				.time(epochMilliToUniqueInstant(Instant.ofEpochMilli(process.getStart())), WritePrecision.NS)
				.addTag(TAG_NAME, properString(process.getName()))
				.addTag(TAG_LOCATION, host)
				.addTags(properedTags)
				.addField("duration", process.getDurationMillis())
				.addField("subprocesses", process.getChildSpans().size())
				.addField("name", properString(process.getName()))
				.addField("inner_duration", innerDuration)
				.addFields(countFields)
				.addFields(durationFields)
				.addFields((Map) process.getMeasures())
				.addFields((Map) properedMetadatas);
	}

	private static VisitState flatProcess(final TraceSpan process, final Stack<String> upperCategory, final List<Point> points, final String host) {
		final VisitState visitState = new InfluxdbUtil.VisitState(upperCategory);
		process.getChildSpans().stream()
				.forEach(subProcess -> {
					visitState.push(subProcess);
					//on descend => stack.push
					final VisitState childVisiteState = flatProcess(subProcess, upperCategory, points, host);
					visitState.merge(childVisiteState);
					//on remonte => stack.poll
					visitState.pop();
				});
		points.add(processToPoint(process, visitState, host));
		return visitState;

	}

	static class VisitState {
		private final Map<String, Integer> countsByCategory = new HashMap<>();
		private final Map<String, Long> durationsByCategory = new HashMap<>();
		private final Stack<String> stack;

		public VisitState(final Stack<String> upperCategory) {
			stack = upperCategory;
		}

		void push(final TraceSpan process) {
			incDurations(process.getCategory(), process.getDurationMillis());
			incCounts(process.getCategory(), 1);
			stack.push(process.getCategory());
		}

		void merge(final VisitState visitState) {
			visitState.durationsByCategory.entrySet()
					.forEach((entry) -> incDurations(entry.getKey(), entry.getValue()));
			visitState.countsByCategory.entrySet()
					.forEach((entry) -> incCounts(entry.getKey(), entry.getValue()));
		}

		void pop() {
			stack.pop();
		}

		private void incDurations(final String category, final long duration) {
			if (!stack.contains(category)) {
				final Long existing = durationsByCategory.get(category);
				if (existing == null) {
					durationsByCategory.put(category, duration);
				} else {
					durationsByCategory.put(category, existing + duration);
				}
			}
		}

		private void incCounts(final String category, final Integer count) {
			final Integer existing = countsByCategory.get(category);
			if (existing == null) {
				countsByCategory.put(category, count);
			} else {
				countsByCategory.put(category, existing + count);
			}
		}

		Map<String, Integer> getCountsByCategory() {
			return countsByCategory;
		}

		Map<String, Long> getDurationsByCategory() {
			return durationsByCategory;
		}

	}

	private static String properString(final String string) {
		if (string == null) {
			return string;
		}
		return string.replaceAll("\n", " ");
	}

	private static Instant epochMilliToUniqueInstant(final Instant measureTime) {
		final var nano = nanoSeq.updateAndGet(i -> ++i % 99999) + 1;
		return measureTime.plusNanos(nano);
	}

}
