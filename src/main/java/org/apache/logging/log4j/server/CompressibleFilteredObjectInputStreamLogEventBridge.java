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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.SequenceInputStream;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LogEventListener;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.apache.logging.log4j.server.CompressInputStreamHelper.CompressionType;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.FilteredObjectInputStream;

/**
 * Reads and logs serialized {@link LogEvent} objects (created with {@link SerializedLayout}) from an {@link ObjectInputStream}.
 * This is use as a singleton instance.
 */
public class CompressibleFilteredObjectInputStreamLogEventBridge extends AbstractLogEventBridge<InputStream> {

	protected static final StatusLogger LOGGER = StatusLogger.getLogger();

	private static byte[] serializedHeader;

	static {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			new ObjectOutputStream(baos).close();
			serializedHeader = baos.toByteArray();
		} catch (final Exception ex) {
			LOGGER.error("Unable to generate Object stream header", ex);
		}
	}

	private final List<String> allowedClasses;
	private final boolean detectCompression;

	public CompressibleFilteredObjectInputStreamLogEventBridge(final boolean compress) {
		this(Collections.<String> emptyList(), compress);
	}

	/**
	 * Constructs an ObjectInputStreamLogEventBridge with additional allowed classes to deserialize.
	 * @param allowedClasses class names to also allow for deserialization
	 * @since 2.8.2
	 */
	public CompressibleFilteredObjectInputStreamLogEventBridge(final List<String> allowedClasses, final boolean detectCompression) {
		this.allowedClasses = allowedClasses;
		this.detectCompression = detectCompression;
	}

	@Override
	public void logEvents(final InputStream inputStream, final LogEventListener logEventListener)
			throws IOException {
		try {
			final LogEvent event;
			if (inputStream instanceof DelimitedInputStream) {
				try (var nextToken = CompressInputStreamHelper.nextTokenStream((DelimitedInputStream) inputStream, serializedHeader)) {
					try (ObjectInputStream ois = new FilteredObjectInputStream(nextToken, allowedClasses)) {
						event = (LogEvent) ois.readObject();
					}
				} //must close added streams, but not the inner input
			} else {
				event = (LogEvent) ((ObjectInputStream) inputStream).readObject();
			}
			logEventListener.log(event);
		} catch (final ClassNotFoundException e) {
			throw new IOException(e);
		}
	}

	@Override
	public InputStream wrapStream(final InputStream inputStream) throws IOException {
		//call at socket opening
		if (detectCompression) {
			//if compressed : recreate Gzip for each object
			//consum OIS at stream opening
			final BufferedInputStream usedInputStream = new BufferedInputStream(inputStream);
			final byte[] readedHeader = new byte[serializedHeader.length];
			final int len = usedInputStream.read(readedHeader); //read the header
			if (len == END) {
				throw new EOFException("cant read header, stream ended");
			}
			final boolean matched = IntStream.range(0, serializedHeader.length).allMatch(i -> serializedHeader[i] == readedHeader[i]);
			if (!matched) {
				throw new StreamCorruptedException("invalid header " + byteArrayToHex(ByteBuffer.wrap(readedHeader, 0, serializedHeader.length).array()));
			}
			final CompressionType compressionType = CompressInputStreamHelper.detectCompressionPrefix(usedInputStream);
			if (compressionType == CompressionType.NONE) {
				//if not compressed,we need to keep the header
				//we can't reset
				return new FilteredObjectInputStream(new SequenceInputStream(new ByteArrayInputStream(serializedHeader), usedInputStream), allowedClasses);
			}
			//if compressed, we need to skip the header
			return CompressInputStreamHelper.wrapStream(usedInputStream, compressionType);
		}
		//else we could use ObjectInputStream
		return new FilteredObjectInputStream(inputStream, allowedClasses);
	}

	private static String byteArrayToHex(final byte[] a) {
		final StringBuilder sb = new StringBuilder(a.length * 3);
		for (final byte b : a) {
			sb.append(String.format("%02x ", b));
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		return "CompressibleFilteredObjectInputStreamLogEventBridge";
	}
}
