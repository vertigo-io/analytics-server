/*
 * Copyright (c) 1994, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.apache.logging.log4j.server;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@code PrefixExtractorInputStream} adds
 * functionality to another input stream, namely
 * the  ability to "mark and reset" firstsfixed bytes of stream.
 * Its usefull to extract and detect prefix of a stream.
 * 
 * Similar to PushBackStream but only firsts bytes, then read directly on innerStream.
 * PushBackStream didn't detect EoF and can't be use with socket (don't detect socket close).
 *
 * @author  Npi2loup
 */
public class PrefixExtractorInputStream extends FilterInputStream {
	protected byte[] buf;
	protected int pos;
	
	public PrefixExtractorInputStream(final InputStream in, final int size) {
		super(in);
		if (size <= 0) {
			throw new IllegalArgumentException("size <= 0");
		}
		buf = new byte[size];
		pos = size;
	}

	/**
	 * Check to make sure that this stream has not been closed
	 */
	private void ensureOpen() throws IOException {
		if (in == null) {
			throw new IOException("Stream closed");
		}
	}

	
	public int read() throws IOException {
		ensureOpen();
		if (pos < buf.length) {
			return buf[pos++] & 0xff;
		}
		return super.read();
	}

	
	public int read(final byte[] b, int off, int len) throws IOException {
		ensureOpen();
		if (b == null) {
			throw new NullPointerException();
		} else if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		} else if (len == 0) {
			return 0;
		}

		int avail = buf.length - pos;
		if (avail > 0) {
			if (len < avail) {
				avail = len;
			}
			System.arraycopy(buf, pos, b, off, avail);
			pos += avail;
			off += avail;
			len -= avail;
		}
		if (len > 0) {
			len = super.read(b, off, len);
			if (len == -1) {
				return avail == 0 ? -1 : avail;
			}
			return avail + len;
		}
		return avail;
	}


	/**
	 * Tests if this input stream supports the {@code mark} and
	 * {@code reset} methods, which it does not.
	 *
	 * @return   {@code false}, since this class does not support the
	 *           {@code mark} and {@code reset} methods.
	 * @see      java.io.InputStream#mark(int)
	 * @see      java.io.InputStream#reset()
	 */
	public boolean markSupported() {
		return false;
	}

	/**
	 * Marks the current position in this input stream.
	 *
	 * <p> The {@code mark} method of {@code PushbackInputStream}
	 * does nothing.
	 *
	 * @param   readlimit   the maximum limit of bytes that can be read before
	 *                      the mark position becomes invalid.
	 * @see     java.io.InputStream#reset()
	 */
	public synchronized void mark(final int readlimit) {
	}

	/**
	 * Repositions this stream to the position at the time the
	 * {@code mark} method was last called on this input stream.
	 *
	 * <p> The method {@code reset} for class
	 * {@code PushbackInputStream} does nothing except throw an
	 * {@code IOException}.
	 *
	 * @throws  IOException  if this method is invoked.
	 * @see     java.io.InputStream#mark(int)
	 * @see     java.io.IOException
	 */
	public synchronized void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	/**
	 * Closes this input stream and releases any system resources
	 * associated with the stream.
	 * Once the stream has been closed, further read(), unread(),
	 * available(), reset(), or skip() invocations will throw an IOException.
	 * Closing a previously closed stream has no effect.
	 *
	 * @throws     IOException  if an I/O error occurs.
	 */
	public synchronized void close() throws IOException {
		if (in == null) {
			return;
		}
		in.close();
		in = null;
		buf = null;
	}
}
