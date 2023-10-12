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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.status.StatusLogger;

import io.vertigo.analytics.server.json.GsonTemplateLayoutLogEventParser;

/**
 * Listens for Log4j events on a TCP server socket and passes them on to Log4j.
 * @param <T> The kind of input stream read
 * @see #main(String[])
 */
public final class AnalyticsTcpSocketServer<T extends InputStream> {
	protected static final StatusLogger LOGGER = StatusLogger.getLogger();

	private AnalyticsTcpSocketServer() {
		//builder onty
	}

	/**
	 * Creates a socket server that reads serialized log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 */
	public static TcpSocketServer<ObjectInputStream> createSerializedSocketServer(final int port, final boolean compress) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<ObjectInputStream> socketServer = new TcpSocketServer<>(port, new CompressibleFilteredObjectInputStreamLogEventBridge(compress));
		return LOGGER.exit(socketServer);
	}

	/**
	 * Creates a socket server that reads serialized log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @param backlog
	 *        The server socket backlog.
	 * @param localBindAddress
	 *        The local InetAddress the server will bind to
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 * @since 2.7
	 */
	public static TcpSocketServer<ObjectInputStream> createSerializedSocketServer(final int port, final int backlog,
			final InetAddress localBindAddress, final boolean compress) throws IOException {
		return createSerializedSocketServer(port, backlog, localBindAddress, Collections.<String> emptyList(), compress);
	}

	/**
	 * Creates a socket server that reads serialized log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @param backlog
	 *        The server socket backlog.
	 * @param localBindAddress
	 *        The local InetAddress the server will bind to
	 * @param allowedClasses additional class names to allow for deserialization
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 * @since 2.8.2
	 */
	public static TcpSocketServer<ObjectInputStream> createSerializedSocketServer(
			final int port, final int backlog, final InetAddress localBindAddress, final List<String> allowedClasses, final boolean compress) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<ObjectInputStream> socketServer = new TcpSocketServer<>(port, backlog, localBindAddress,
				new CompressibleFilteredObjectInputStreamLogEventBridge(allowedClasses, compress));
		return LOGGER.exit(socketServer);
	}

	/**
	 * Creates a socket server that reads JSON log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 */
	public static TcpSocketServer<InputStream> createJsonSocketServer(final int port, final boolean compress) throws IOException {
		LOGGER.entry("createJsonSocketServer", port);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, new CompressibleJsonInputStreamLogEventBridge(new GsonTemplateLayoutLogEventParser(), compress));
		return LOGGER.exit(socketServer);
	}

	/**
	 * Creates a socket server that reads JSON log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @param backlog
	 *        The server socket backlog.
	 * @param localBindAddress
	 *        The local InetAddress the server will bind to
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 * @since 2.9
	 */
	public static TcpSocketServer<InputStream> createJsonSocketServer(final int port, final int backlog,
			final InetAddress localBindAddress, final boolean compress) throws IOException {
		LOGGER.entry("createJsonSocketServer", port, backlog, localBindAddress);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, backlog, localBindAddress,
				new CompressibleJsonInputStreamLogEventBridge(new GsonTemplateLayoutLogEventParser(), compress));
		return LOGGER.exit(socketServer);
	}

	public static TcpSocketServer<InputStream> createJsonSecuredServer(final int port, final SslConfiguration sslConfiguration, final boolean compress) throws IOException {
		LOGGER.entry("createJsonSecuredServer", port, sslConfiguration, compress);
		final TcpSocketServer<InputStream> securedServer = new TcpSocketServer<>(port,
				new CompressibleJsonInputStreamLogEventBridge(new GsonTemplateLayoutLogEventParser(), compress),
				sslConfiguration.getSslServerSocketFactory().createServerSocket(port));
		return LOGGER.exit(securedServer);
	}
}
