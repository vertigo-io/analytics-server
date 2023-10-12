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

import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Command;
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Option;
import org.apache.logging.log4j.server.AbstractSocketServer.ServerConfigurationFactory;
import org.apache.logging.log4j.status.StatusLogger;

/**
 * Listens for Log4j events on a TCP server socket and passes them on to Log4j.
 *
 * @param <T>
 *        The kind of input stream read
 * @see #main(String[])
 */
public class TcpSocketServerBuilder<T extends InputStream> {

	protected static final StatusLogger LOGGER = StatusLogger.getLogger();

	private TcpSocketServerBuilder() {
		//builder onty
	}

	@Command(name = "TcpSocketServer")
	protected static class CommandLineArguments extends AbstractSocketServer.CommandLineArguments {

		@Option(names = { "--backlog", "-b" }, description = "Server socket backlog. Must be a positive integer.")
		// Same default as ServerSocket
		private int backlog = 50;

		int getBacklog() {
			return backlog;
		}

		void setBacklog(final int backlog) {
			this.backlog = backlog;
		}
	}

	/**
	 * Main startup for the server. Run with "--help" for to print command line help on the console.
	 *
	 * @param args
	 *        The command line arguments.
	 * @throws Exception
	 *         if an error occurs.
	 */
	public static void main(final String[] args) throws Exception {
		final CommandLineArguments cla = CommandLine.populateCommand(new CommandLineArguments(), args);
		if (cla.isHelp() || cla.backlog < 0 || cla.getPort() < 0) {
			CommandLine.usage(cla, System.err);
			return;
		}
		if (cla.getConfigLocation() != null) {
			ConfigurationFactory.setConfigurationFactory(new ServerConfigurationFactory(cla.getConfigLocation()));
		}
		final TcpSocketServer<InputStream> socketServer = TcpSocketServerBuilder.createJsonSocketServer(
				cla.getPort(), cla.getBacklog(), cla.getLocalBindAddress());
		final Thread serverThread = socketServer.startNewThread();
		if (cla.isInteractive()) {
			socketServer.awaitTermination(serverThread);
		}
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
	public static TcpSocketServer<InputStream> createJsonSocketServer(final int port) throws IOException {
		LOGGER.entry("createJsonSocketServer", port);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, new JsonInputStreamLogEventBridge());
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
			final InetAddress localBindAddress) throws IOException {
		LOGGER.entry("createJsonSocketServer", port, backlog, localBindAddress);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, backlog, localBindAddress,
				new JsonInputStreamLogEventBridge());
		return LOGGER.exit(socketServer);
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
	public static TcpSocketServer<ObjectInputStream> createSerializedSocketServer(final int port) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<ObjectInputStream> socketServer = new TcpSocketServer<>(port, new CompressibleFilteredObjectInputStreamLogEventBridge(false));
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
			final InetAddress localBindAddress) throws IOException {
		return createSerializedSocketServer(port, backlog, localBindAddress, Collections.<String> emptyList());
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
			final int port, final int backlog, final InetAddress localBindAddress, final List<String> allowedClasses) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<ObjectInputStream> socketServer = new TcpSocketServer<>(port, backlog, localBindAddress,
				new CompressibleFilteredObjectInputStreamLogEventBridge(allowedClasses, false));
		return LOGGER.exit(socketServer);
	}

	/**
	 * Creates a socket server that reads XML log events.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @return a new a socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 */
	public static TcpSocketServer<InputStream> createXmlSocketServer(final int port) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, new XmlInputStreamLogEventBridge());
		return LOGGER.exit(socketServer);
	}

	/**
	 * Creates a socket server that reads XML log events.
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
	public static TcpSocketServer<InputStream> createXmlSocketServer(final int port,
			final int backlog, final InetAddress localBindAddress) throws IOException {
		LOGGER.entry(port);
		final TcpSocketServer<InputStream> socketServer = new TcpSocketServer<>(port, backlog, localBindAddress,
				new XmlInputStreamLogEventBridge());
		return LOGGER.exit(socketServer);
	}
}
