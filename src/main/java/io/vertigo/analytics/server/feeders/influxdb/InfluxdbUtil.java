package io.vertigo.analytics.server.feeders.influxdb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;

import io.vertigo.analytics.server.events.health.HealthCheck;
import io.vertigo.analytics.server.events.metric.Metric;
import io.vertigo.analytics.server.events.process.AProcess;

public class InfluxdbUtil {

	private static final String TAG_NAME = "name";
	private static final String TAG_LOCATION = "location";

	private InfluxdbUtil() {
		// Util
	}

	public static List<Point> heathCheckToPoints(final HealthCheck healthCheck, final String host) {

		final String message = healthCheck.getMeasure().getMessage();
		final String messageToStore = message != null ? message : "";

		return Collections.singletonList(Point.measurement("healthcheck")
				.time(healthCheck.getCheckInstant(), WritePrecision.MS)
				.addField("location", host)
				.addField("name", healthCheck.getName())
				.addField("checker", healthCheck.getChecker())
				.addField("module", healthCheck.getModule())
				.addField("feature", healthCheck.getFeature())
				.addField("status", healthCheck.getMeasure().getStatus().getNumericValue())
				.addField("message", messageToStore)
				.addTag("location", host)
				.addTag("name", healthCheck.getName())
				.addTag("checker", healthCheck.getChecker())
				.addTag("module", healthCheck.getModule())
				.addTag("feature", healthCheck.getFeature())
				.addTag("status", String.valueOf(healthCheck.getMeasure().getStatus().getNumericValue())));
	}

	public static List<Point> metricToPoints(final Metric metric, final String host) {

		final String module = metric.getModule();// for now module is null
		final String moduleToStore = module != null ? module : "";

		return Collections.singletonList(Point.measurement("metric")
				.time(metric.getMeasureInstant(), WritePrecision.MS)
				.addField("location", host)
				.addField("name", metric.getName())
				.addField("module", moduleToStore)
				.addField("feature", metric.getFeature())
				.addField("value", metric.getValue())
				.addTag("location", host)
				.addTag("name", metric.getName())
				.addTag("module", moduleToStore)
				.addTag("feature", metric.getFeature())
				.addTag("value", String.valueOf(metric.getValue())));
	}

	public static List<Point> processToPoints(final AProcess process, final String host) {
		final List<Point> points = new ArrayList<>();
		flatProcess(process, new Stack<>(), points, host);
		return points;
	}

	private static Point processToPoint(final AProcess process, final VisitState visitState, final String host) {
		final Map<String, Object> countFields = visitState.getCountsByCategory().entrySet().stream()
				.collect(Collectors.toMap((entry) -> entry.getKey() + "_count", (entry) -> entry.getValue()));
		final Map<String, Object> durationFields = visitState.getDurationsByCategory().entrySet().stream()
				.collect(Collectors.toMap((entry) -> entry.getKey() + "_duration", (entry) -> entry.getValue()));

		// we add a inner duration for convinience
		final Long innerDuration = process.getDurationMillis() - process.getSubProcesses()
				.stream()
				.collect(Collectors.summingLong(AProcess::getDurationMillis));

		final Map<String, String> properedTags = process.getTags().entrySet()
				.stream()
				.collect(Collectors.toMap(
						entry -> properString(entry.getKey()),
						entry -> properString(entry.getValue())));

		return Point.measurement(process.getCategory())
				.time(Instant.ofEpochMilli(process.getStart()), WritePrecision.MS)
				.addTag(TAG_NAME, properString(process.getName()))
				.addTag(TAG_LOCATION, host)
				.addTags(properedTags)
				.addField("duration", process.getDurationMillis())
				.addField("subprocesses", process.getSubProcesses().size())
				.addField("name", properString(process.getName()))
				.addField("inner_duration", innerDuration)
				.addFields(countFields)
				.addFields(durationFields)
				.addFields((Map) process.getMeasures());
	}

	private static VisitState flatProcess(final AProcess process, final Stack<String> upperCategory, final List<Point> points, final String host) {
		final VisitState visitState = new InfluxdbUtil.VisitState(upperCategory);
		process.getSubProcesses().stream()
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

		void push(final AProcess process) {
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

		private void incDurations(final String category, final Long duration) {
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

}
