package io.vertigo.analytics.server.feeders.tempo.log4j2;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.vertigo.analytics.server.LogMessage;
import io.vertigo.analytics.server.TraceSpan;

@Plugin(name = "TempoProcess", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class Log4j2TempoProcessAppender extends AbstractAppender {

	private static final Gson GSON = new GsonBuilder().create();
	private final OpenTelemetrySdk openTelemetry;

	@PluginFactory
	public static Log4j2TempoProcessAppender createAppender(
			@PluginAttribute("name") final String name,
			@PluginConfiguration final Configuration config,
			@PluginElement("Filter") final Filter filter,
			@PluginAttribute("tempoUrl") final String tempoUrl) {
		if (name == null) {
			LOGGER.error("A name for the Appender must be specified");
			return null;
		}
		return new Log4j2TempoProcessAppender(name, filter, config, tempoUrl);
	}

	private Log4j2TempoProcessAppender(
			final String name,
			final Filter filter,
			final Configuration config,
			final String tempoUrlJaeger) {
		super(name, filter, null, true);
		//---

		final Resource resource = Resource.getDefault()
				.merge(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "vertigo")));

		final SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
				.addSpanProcessor(BatchSpanProcessor.builder(JaegerGrpcSpanExporter.builder()
						.setEndpoint(tempoUrlJaeger)
						.build()).build())
				.setResource(resource)
				.build();

		openTelemetry = OpenTelemetrySdk.builder()
				.setTracerProvider(sdkTracerProvider)
				.buildAndRegisterGlobal();
	}

	@Override
	public void append(final LogEvent event) {

		try {
			final LogMessage<TraceSpan> logMessage = GSON.fromJson(event.getMessage().getFormattedMessage(), getLogMessageType());
			sendProcess(logMessage);
		} catch (final JsonSyntaxException e) {
			// it wasn't a message for us so we do nothing
		} catch (final Exception e) {
			getHandler().error("error writing log to tempo", e);//by default the logger evicts some logs on the appender to avoid flooding. (better than nothing)
		}

	}

	private void sendProcess(final LogMessage<TraceSpan> logMessage) {
		if (logMessage.getEvent() != null) {
			sendProcess(logMessage.getEvent(), logMessage.getAppName(), logMessage.getHost());
		}
		if (logMessage.getEvents() != null) {
			for (final var process : logMessage.getEvents()) {
				sendProcess(process, logMessage.getAppName(), logMessage.getHost());
			}
		}
	}

	private void sendProcess(final TraceSpan process, final String appName, final String host) {
		final var openTelemetryTracer = openTelemetry.getTracer("vertigo-analytics", "0.10.0");
		final var rootSpan = openTelemetryTracer
				.spanBuilder(process.getName())
				.setNoParent()
				.setStartTimestamp(process.getStart(), TimeUnit.MILLISECONDS)
				.startSpan();
		rootSpan.makeCurrent();
		final var topVisitState = processToPoints(process, process.getTags().get("location"), openTelemetryTracer);
		processToSpan(rootSpan, process, topVisitState, process.getTags().get("location"));
		rootSpan
				.setAttribute("vertigo.app.name", appName)
				.setAttribute("vertigo.host", host)
				.end(process.getEnd(), TimeUnit.MILLISECONDS);

	}

	public static VisitState processToPoints(final TraceSpan process, final String host, final Tracer tracer) {
		return flatProcess(process, new Stack<>(), host, tracer);
	}

	private static void processToSpan(final Span span, final TraceSpan process, final VisitState visitState, final String host) {
		final AttributesBuilder attributesBuilder = Attributes.builder();
		process.getMeasures().entrySet().stream().forEach(entry -> attributesBuilder.put(entry.getKey(), entry.getValue()));
		process.getMetadatas().entrySet().stream().forEach(entry -> attributesBuilder.put(properString(entry.getKey()), properString(entry.getValue())));
		process.getTags().entrySet().stream().forEach(entry -> attributesBuilder.put(properString(entry.getKey()), properString(entry.getValue())));
		visitState.getCountsByCategory().entrySet().stream()
				.forEach(entry -> attributesBuilder.put(entry.getKey() + "_count", entry.getValue()));
		visitState.getDurationsByCategory().entrySet().stream()
				.forEach(entry -> attributesBuilder.put(entry.getKey() + "_duration", entry.getValue()));
		span.setAllAttributes(attributesBuilder.build());
		span
				.setAttribute("category", process.getCategory())
				.setAttribute("service.namespace", process.getCategory())
				.setAttribute("service.name", process.getName());

	}

	private static VisitState flatProcess(final TraceSpan process, final Stack<String> upperCategory, final String host, final Tracer tracer) {
		final VisitState visitState = new Log4j2TempoProcessAppender.VisitState(upperCategory);
		process.getChildSpans().stream()
				.forEach(subProcess -> {
					visitState.push(subProcess);
					final var subSpan = tracer.spanBuilder(subProcess.getName())
							.setStartTimestamp(subProcess.getStart(), TimeUnit.MILLISECONDS)
							.startSpan();
					final var scope = subSpan.makeCurrent();
					//on descend => stack.push
					final VisitState childVisiteState = flatProcess(subProcess, upperCategory, host, tracer);
					processToSpan(subSpan, subProcess, visitState, host);
					subSpan.end(subProcess.getEnd(), TimeUnit.MILLISECONDS);
					scope.close();
					visitState.merge(childVisiteState);
					//on remonte => stack.poll
					visitState.pop();
				});
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
				return new Type[] { TraceSpan.class };
			}
		};
	}

}
