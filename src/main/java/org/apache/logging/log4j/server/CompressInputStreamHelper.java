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
import java.io.SequenceInputStream;
import java.io.StreamCorruptedException;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;

import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFInputStream;

/**
 * Support compression of ImputStream GZIP or LZF.
 *
 * @param <T> The kind of input stream read
 */
public final class CompressInputStreamHelper {
	protected static final StatusLogger LOGGER = StatusLogger.getLogger();

	protected static final Logger logger = StatusLogger.getLogger();
	protected static final int END = -1;
	public static byte[] GZIP_HEADER;

	static {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			new GZIPOutputStream(baos).close();
			GZIP_HEADER = Arrays.copyOfRange(baos.toByteArray(), 0, 10);
		} catch (final Exception ex) {
			LOGGER.error("Unable to generate Object stream header", ex);
		}
	}

	private CompressInputStreamHelper() {
		//nothing
	}

	/*public static <T extends InputStream> T wrapStream(final InputStream inputStream, final boolean compress) throws IOException {
		return CompressInputStreamHelper.wrapStream(inputStream, compress, null);
	}*/

	public static <T extends InputStream> T nextTokenStream(final DelimitedInputStream inputStream, final CompressionType compressionType, final byte[] appendHeader) throws IOException {
		InputStream usedInputStream = inputStream.nextToken();
		switch (compressionType) {
			case GZIP_W_LENGTH:
				usedInputStream = new GZIPInputStream(usedInputStream, 2048);
				break;
			case GZIP:
				usedInputStream = new GZIPInputStream(usedInputStream, 2048);
				break;
			case LZF:
				usedInputStream = new LZFInputStream(usedInputStream);
				break;
			case NONE:
			default:
				throw new StreamCorruptedException("invalid compressionType " + compressionType);
		}
		if (appendHeader != null && compressionType != CompressionType.NONE) {
			usedInputStream = new SequenceInputStream(new ByteArrayInputStream(appendHeader), usedInputStream);
		}
		return (T) usedInputStream;
	}

	public static <T extends InputStream> T wrapStream(final InputStream inputStream, final CompressionType compressionType) throws IOException {
		InputStream usedInputStream = inputStream;
		if (compressionType != null) {
			switch (compressionType) {
				case GZIP_W_LENGTH:
					usedInputStream = new DelimitedInputStream(usedInputStream, new byte[] { (byte) 0xf1, (byte) 0xb8 }, 3);
					break;
				case GZIP:
					usedInputStream = new DelimitedInputStream(usedInputStream, CompressInputStreamHelper.GZIP_HEADER, new byte[] { 0x00, 0x00 });
					break;
				case LZF:
					usedInputStream = new LZFInputStream(usedInputStream);
					break;
				case NONE:
				default:
					//already bufferedStream
					break;
			}
		}
		return (T) usedInputStream;
	}

	/*private static String byteArrayToHex(final byte[] a) {
		final StringBuilder sb = new StringBuilder(a.length * 3);
		for (final byte b : a) {
			sb.append(String.format("%02x ", b));
		}
		return sb.toString();
	}*/

	public enum CompressionType {
		GZIP_W_LENGTH, GZIP, LZF, NONE

	}

	public static CompressionType detectCompressionPrefix(final BufferedInputStream usedInputStream) throws IOException {
		final byte[] signature = new byte[2];
		usedInputStream.mark(2);
		final int len = usedInputStream.read(signature); //read the signature
		if (len == END) {
			throw new EOFException("cant read signature, stream ended");
		}
		usedInputStream.reset();
		if ((signature[0] & 0xFF) == 0xf1 && (signature[1] & 0xFF) == 0xb8) { //check if matches standard gzip with length magic number
			return CompressionType.GZIP_W_LENGTH;
		} else if (signature[1] == GZIP_HEADER[1] && signature[0] == GZIP_HEADER[0]) { //check if matches standard gzip magic number
			return CompressionType.GZIP;
		} else if (signature[0] == LZFChunk.BYTE_Z && signature[1] == LZFChunk.BYTE_V) { //check if matches standard LZF magic number
			return CompressionType.LZF;
		} else {
			//already bufferedStream
			return CompressionType.NONE;
		}
	}

}
