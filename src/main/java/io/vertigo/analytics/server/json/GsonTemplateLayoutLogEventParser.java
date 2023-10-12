/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package io.vertigo.analytics.server.json;

import java.io.InputStream;
import java.time.Instant;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.parser.ParseException;
import org.apache.logging.log4j.core.parser.TextLogEventParser;
import org.apache.logging.log4j.message.SimpleMessage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads and logs JSON with a sub part of "ecs.version":"1.2.0", for JsonTemplateLayout {@link LogEvent}s from an {@link InputStream}..
 */
public class GsonTemplateLayoutLogEventParser implements TextLogEventParser {

	@Override
	public LogEvent parseFrom(final String input) throws ParseException {
		final JsonObject jsonObject = JsonParser.parseString(input).getAsJsonObject();
		return Log4jLogEvent.newBuilder()
				.setTimeMillis(Instant.parse(jsonObject.get("@timestamp").getAsString()).toEpochMilli())
				.setLevel(Level.toLevel(jsonObject.get("log.level").getAsString()))
				.setMessage(new SimpleMessage(jsonObject.get("message").getAsString()))
				.setThreadName(jsonObject.get("process.thread.name").getAsString())
				.setLoggerName(jsonObject.get("log.logger").getAsString())
				.build();
	}

	@Override
	public LogEvent parseFrom(final byte[] input) throws ParseException {
		throw new UnsupportedOperationException("Not implemented yet.");
	}

	@Override
	public LogEvent parseFrom(final byte[] input, final int offset, final int length) throws ParseException {
		throw new UnsupportedOperationException("Not implemented yet.");
	}

}
