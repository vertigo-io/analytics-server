package io.vertigo.analytics.server.feeders.influxdb.http;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import io.javalin.Javalin;
import io.vertigo.analytics.server.AProcess;
import io.vertigo.analytics.server.LogMessage;

public class HttpProcessInflux {

	private static final Gson gson = new Gson();

	private static final Logger logger = LogManager.getLogger(HttpProcessInflux.class);

	public static void start() {
		final Javalin app = Javalin.create().start(7000);
		final Type logMessageType = new ParameterizedType() {

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
				return new Type[] { AProcess.class };
			}
		};

		app.post("/process/_send", (ctx) -> {
			try {
				gson.fromJson(ctx.body(), logMessageType);
			} catch (final Exception e) {
				ctx.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				ctx.result(e.getMessage());
			}
			logger.info(ctx.body()); // re route to existing appender
			ctx.status(HttpServletResponse.SC_NO_CONTENT);
			ctx.result("");
		});

	}

}
