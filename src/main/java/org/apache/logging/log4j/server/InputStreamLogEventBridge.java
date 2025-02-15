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
package org.apache.logging.log4j.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LogEventListener;
import org.apache.logging.log4j.core.parser.ParseException;
import org.apache.logging.log4j.core.parser.TextLogEventParser;
import org.apache.logging.log4j.util.Strings;

/**
 * Reads and logs {@link LogEvent}s from an {@link InputStream}.
 */
public abstract class InputStreamLogEventBridge extends AbstractLogEventBridge<InputStream> {

	private final int bufferSize;

	private final Charset charset;

	private final String eventEndMarker;

	private final TextLogEventParser parser;

	public InputStreamLogEventBridge(final TextLogEventParser parser, final int bufferSize, final Charset charset, final String eventEndMarker) {
		this.bufferSize = bufferSize;
		this.charset = charset;
		this.eventEndMarker = eventEndMarker;
		this.parser = parser;
	}

	abstract protected int[] getEventIndices(final String text, int beginIndex);

	@Override
	public int logEvents(final InputStream inputStream, final LogEventListener logEventListener) throws IOException, ParseException {
		int nbEvents = 0;
		String workingText = Strings.EMPTY;
		try {
			final Reader inReader = new InputStreamReader(inputStream, charset);

			// Allocate buffer once
			final char[] buffer = new char[bufferSize];
			String textRemains = workingText = Strings.EMPTY;
			while (true) {
				// Process until the stream is EOF.
				final int streamReadLength = inReader.read(buffer);
				if (streamReadLength == END) {
					// The input stream is EOF
					if (workingText.isEmpty()) {
						throw new EOFException("Socket closed");
					}
					break;
				}
				final String text = workingText = textRemains + new String(buffer, 0, streamReadLength);
				int beginIndex = 0;
				while (true) {
					// Extract and log all XML events in the buffer
					final int[] pair = getEventIndices(text, beginIndex);
					final int eventStartMarkerIndex = pair[0];
					if (eventStartMarkerIndex < 0) {
						// No more events or partial XML only in the buffer.
						// Save the unprocessed string part
						textRemains = text.substring(beginIndex);
						break;
					}
					final int eventEndMarkerIndex = pair[1];
					if (eventEndMarkerIndex > 0) {
						final int eventEndXmlIndex = eventEndMarkerIndex + eventEndMarker.length();
						final String textEvent = workingText = text.substring(eventStartMarkerIndex, eventEndXmlIndex);
						final LogEvent logEvent = unmarshal(textEvent);
						logEventListener.log(logEvent);
						nbEvents++;
						beginIndex = eventEndXmlIndex;
					} else {
						// No more events or partial XML only in the buffer.
						// Save the unprocessed string part
						textRemains = text.substring(beginIndex);
						break;
					}
				}
			}
			return nbEvents;
		} catch (final EOFException ex) {
			//close silently
			throw ex;
		} catch (final IOException ex) {
			logger.warn(ex.getMessage() + " last read: " + workingText);
			throw ex;
		}
	}

	protected LogEvent unmarshal(final String jsonEvent) throws ParseException {
		return parser.parseFrom(jsonEvent);
	}

	@Override
	public String toString() {
		return "InputStreamLogEventBridge [bufferSize=" + bufferSize + ", charset=" + charset + ", eventEndMarker="
				+ eventEndMarker + ", parser=" + parser + "]";
	}

}
