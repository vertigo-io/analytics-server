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
import java.util.zip.GZIPInputStream;

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
	protected static final Logger logger = StatusLogger.getLogger();
	protected static final int END = -1;

	private CompressInputStreamHelper() {
		//nothing
	}

	public static <T extends InputStream> T wrapStream(final InputStream inputStream, final boolean compress) throws IOException {
		InputStream usedInputStream = inputStream;
		if (compress) {
			final BufferedInputStream bis = new BufferedInputStream(usedInputStream);
			final byte[] signature = new byte[2];
			bis.mark(2);
			bis.read(signature); //read the signature
			bis.reset();
			if ((0xFF00 & signature[1] << 8 | (byte) (0xFF & signature[0])) == GZIPInputStream.GZIP_MAGIC) { //check if matches standard gzip magic number
				usedInputStream = new GZIPInputStream(bis);
			} else if (signature[0] == LZFChunk.BYTE_Z && signature[1] == LZFChunk.BYTE_V) { //check if matches standard LZF magic number
				usedInputStream = new LZFInputStream(bis);
			} else { //check if matches standard gzip magic number
				usedInputStream = bis;
			}
		}
		return (T) usedInputStream;
	}

}
