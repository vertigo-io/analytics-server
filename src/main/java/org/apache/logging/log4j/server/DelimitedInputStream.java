package org.apache.logging.log4j.server;

import java.io.ByteArrayInputStream;
// for InputStream and ByteArrayOutputStream
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DelimitedInputStream extends InputStream {
	private static final int END = -1;
	private final InputStream in; // Stream to parse
	private int next[];
	private boolean EOS;
	private boolean startReaded = false;
	private int tokenLengthBytes = 0;

	private final CompressInputStreamHelper.CompressionType modeName;

	private final byte delim[];

	private final byte start[];
	private final byte end[];

	private final ByteArrayOutputStream token = new ByteArrayOutputStream(); //reused

	public DelimitedInputStream(final InputStream underlying, final byte start[], final byte end[], final CompressInputStreamHelper.CompressionType modeName) {
		in = underlying;
		this.start = start;
		this.end = end;
		this.modeName = modeName;
		tokenLengthBytes = 0;
		delim = new byte[start.length + end.length];
		System.arraycopy(end, 0, delim, 0, end.length); //delim : end+start
		System.arraycopy(start, 0, delim, end.length, start.length);

		preProcess(delim); //compute next

		EOS = false;
	}

	public DelimitedInputStream(final InputStream underlying, final byte start[], final int tokenLengthBytes, final CompressInputStreamHelper.CompressionType modeName) {
		in = underlying;
		this.start = start;
		end = new byte[0];
		this.modeName = modeName;
		this.tokenLengthBytes = tokenLengthBytes;
		delim = start;

		EOS = false;
	}

	public CompressInputStreamHelper.CompressionType getCompressionType() {
		return modeName;
	}

	@Override
	public String toString() {
		return "DelimitedInputStream mode:" + modeName + " over " + in;
	}

	private void preProcess(final byte delim[]) {
		next = new int[delim.length];
		int i = 0;
		int j = -1;
		next[i] = j;
		while (i < delim.length - 1) {
			while (j > -1 && delim[i] != delim[j]) {
				j = next[j];
			}
			next[++i] = ++j;
		}
	}

	// Read bytes up to and including delimiter;
	// Return bytes read excluding delimiter.
	public InputStream nextToken() throws IOException {
		if (EOS) {
			throw new EOFException("nextToken called after stream ended");
		}
		if (delim.length == 0) {
			return nullInputStream();
		}
		if (!startReaded) {
			final byte[] startRead = new byte[start.length];
			final int len = in.read(startRead); //read the signature : always check len otherwise : read unset buffer
			if (len == END) {
				throw new EOFException("nextToken called after stream ended");
			} else if (!Arrays.equals(start, startRead)) {
				throw new StreamCorruptedException("invalid header " + byteArrayToHex(ByteBuffer.wrap(startRead, 0, startRead.length).array()));
			}
			startReaded = true;
		}
		// Reuse a buffer to hold the token
		token.reset();

		/** If stream contains length, we just take them */
		if (tokenLengthBytes > 0) {
			//don't add start in this mode
			int length = 0;
			for (int i = 0; i < tokenLengthBytes; i++) {
				length = length << 8;
				length += in.read();
			}
			if (length <= 0 && length >= 16777215) { //not 00 00 00 and not FF FF FF
				throw new StreamCorruptedException("cant read token length " + length);
			}
			token.write(in.readNBytes(length));
			startReaded = false;
		} else {
			token.write(start, 0, start.length); //add start

			/** Look for delimiter */
			int seen = 0; // # bytes of delimiter matched so far
			int nextByte; // Next byte in input stream to process
			while (seen < delim.length && !EOS) {
				nextByte = in.read();
				if (nextByte == -1) {
					EOS = true;
				} else if ((byte) nextByte == delim[seen]) {
					seen += 1;
				} else { // !EOS && nextByte != delim[seen]
					while ((byte) nextByte != delim[seen] && seen > 0) {
						token.write(delim, 0, seen - next[seen]);
						seen = next[seen];
					}
					if ((byte) nextByte != delim[seen]) {
						token.write(nextByte);
					} else {
						seen += 1;
					}
				}
			}
			//add end
			if (seen == delim.length) {
				token.write(end, 0, end.length);
				startReaded = true; //start already readed
			} else if (seen < delim.length) {
				//EOS
				token.write(delim, 0, seen);
				startReaded = false;
			}
		}
		return new ByteArrayInputStream(token.toByteArray());
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException("can't read, use nextToken()");
	}

	@Override
	public void close() throws IOException {
		in.close();
	}

	private static String byteArrayToHex(final byte[] a) {
		final StringBuilder sb = new StringBuilder(a.length * 3);
		for (final byte b : a) {
			sb.append(String.format("%02x ", b));
		}
		return sb.toString();
	}
}
