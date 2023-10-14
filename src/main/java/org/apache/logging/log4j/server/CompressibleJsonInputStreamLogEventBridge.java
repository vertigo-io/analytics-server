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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LogEventListener;
import org.apache.logging.log4j.core.parser.ParseException;
import org.apache.logging.log4j.core.parser.TextLogEventParser;
import org.apache.logging.log4j.server.CompressInputStreamHelper.CompressionType;
import org.apache.logging.log4j.util.Chars;

/**
 * Reads and logs Compressible JSON {@link LogEvent}s from an {@link InputStream}..
 */
public class CompressibleJsonInputStreamLogEventBridge extends InputStreamLogEventBridge {

	private static final int[] END_PAIR = new int[] { END, END };
	private static final char EVENT_END_MARKER = '}';
	private static final char EVENT_START_MARKER = '{';
	private static final char JSON_ESC = '\\';
	private static final char JSON_STR_DELIM = Chars.DQUOTE;
	private final boolean detectCompression;
	private CompressionType compressionType;

	public CompressibleJsonInputStreamLogEventBridge(final TextLogEventParser parser, final boolean detectCompression) {
		this(parser, 1024, Charset.defaultCharset(), detectCompression);
	}

	public CompressibleJsonInputStreamLogEventBridge(final TextLogEventParser parser, final int bufferSize, final Charset charset, final boolean detectCompression) {
		super(parser, bufferSize, charset, String.valueOf(EVENT_END_MARKER));
		this.detectCompression = detectCompression;
	}

	@Override
	protected int[] getEventIndices(final String text, final int beginIndex) {
		// Scan the text for the end of the next JSON object.
		final int start = text.indexOf(EVENT_START_MARKER, beginIndex);
		if (start == END) {
			return END_PAIR;
		}
		final char[] charArray = text.toCharArray();
		int stack = 0;
		boolean inStr = false;
		boolean inEsc = false;
		for (int i = start; i < charArray.length; i++) {
			final char c = charArray[i];
			if (inEsc) {
				// Skip this char and continue
				inEsc = false;
			} else {
				switch (c) {
					case EVENT_START_MARKER:
						if (!inStr) {
							stack++;
						}
						break;
					case EVENT_END_MARKER:
						if (!inStr) {
							stack--;
						}
						break;
					case JSON_STR_DELIM:
						inStr = !inStr;
						break;
					case JSON_ESC:
						inEsc = true;
						break;
					default:
						break;
				}
				if (stack == 0) {
					return new int[] { start, i };
				}
			}
		}
		return END_PAIR;
	}

	@Override
	public void logEvents(final InputStream inputStream, final LogEventListener logEventListener) throws IOException, ParseException {
		// The default is to return the same object as given.
		if (compressionType != CompressionType.NONE) {
			try (var usedInputStream = CompressInputStreamHelper.wrapStream(inputStream, compressionType, null)) {
				super.logEvents(usedInputStream, logEventListener);
			} //must close added streams
		} else {
			super.logEvents(inputStream, logEventListener); //mode stream : don't close
		}
	}

	// The default is to return the same object as given.
	@SuppressWarnings("unchecked")
	@Override
	public InputStream wrapStream(final InputStream inputStream) throws IOException {
		//call at socket opening
		compressionType = CompressionType.NONE;
		if (detectCompression) {
			//if compressed : recreate Gzip for each object
			final BufferedInputStream usedInputStream = new BufferedInputStream(inputStream);
			compressionType = CompressInputStreamHelper.detectCompressionPrefix(usedInputStream);
			//if compressed, we need to skip the header
			return usedInputStream;
		}
		return inputStream;
	}

}
