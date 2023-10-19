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
import java.io.OptionalDataException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.parser.ParseException;
import org.apache.logging.log4j.core.util.Closer;
import org.apache.logging.log4j.core.util.Log4jThread;
import org.apache.logging.log4j.message.EntryMessage;

/**
 * Listens for Log4j events on a TCP server socket and passes them on to Log4j.
 *
 * @param <T>
 *        The kind of input stream read
 * @see #main(String[])
 */
public class TcpSocketServer<T extends InputStream> extends AbstractSocketServer<T> {

	/**
	 * Thread that processes the events.
	 */
	private class SocketHandler extends Log4jThread {
		protected final Logger logger;
		private final Socket socket;

		private volatile boolean shutdown = false;

		public SocketHandler(final Socket socket) {
			//this constructor must be safe : may lock sockets' receiver thread and no more connections could be accepted
			this.socket = socket;
			this.logger = LogManager.getLogger(this.getClass().getSimpleName() + socket.getInetAddress() + ':' + socket.getPort() + "->" + socket.getLocalPort());
			logger.debug("Create SocketHandler");
		}

		@Override
		public void run() {
			final EntryMessage entry = logger.traceEntry();
			long lastReceiveLogTime = 0;
			long deltaPacketsReceived = 0;
			boolean closed = false;
			final T inputStream;
			String socketMode = "UNKNOWN";

			try {
				logger.debug("Init SocketHandler, wrap inputStream");
				try {
					inputStream = logEventInput.wrapStream(socket.getInputStream());
				} catch (final IOException e) {
					logger.error("IOException encountered while initializing socket", e);
					return;
				}
				if (inputStream instanceof DelimitedInputStream) {
					socketMode = logEventInput.getClass().getSimpleName() + " mode:" + ((DelimitedInputStream) inputStream).getCompressionType();
				} else {
					socketMode = logEventInput.getClass().getSimpleName() + " mode: NONE";
				}
				logger.debug("Ready SocketHandler with {}", socketMode);

				try {
					logger.info("Start listening events with {}", socketMode);
					while (!shutdown) {
						logger.debug("Listening events");
						deltaPacketsReceived += logEventInput.logEvents(inputStream, TcpSocketServer.this);
						if (logger.isDebugEnabled()) {
							logger.debug("Received {} batchs events", deltaPacketsReceived);
							deltaPacketsReceived = 0;
						} else if (System.currentTimeMillis() - lastReceiveLogTime > 5 * 60 * 1000) { //log every 5 minutes
							logger.info("Received {} batchs events", deltaPacketsReceived);
							lastReceiveLogTime = System.currentTimeMillis();
							deltaPacketsReceived = 0;
						}
					}
				} catch (final EOFException e) {
					closed = true;
				} catch (final OptionalDataException e) {
					logger.error("OptionalDataException eof=" + e.eof + " length=" + e.length, e);
				} catch (final SocketException e) {
					if (e.getMessage().contains("Connection reset")) {
						closed = true; //juste client close connection
					} else {
						logger.error("IOException encountered while reading from socket", e);
					}
				} catch (final IOException e) {
					logger.error("IOException encountered while reading from socket", e);
				} catch (final ParseException e) {
					logger.error("ParseException encountered while reading from socket", e);
				}
				if (!closed) {
					Closer.closeSilently(inputStream);
				}
			} finally {
				handlers.remove(Long.valueOf(getId()));
				logger.info("Received {} batchs events", deltaPacketsReceived);
				logger.info("Stop listening events with {}", socketMode);
			}
			logger.traceExit(entry);
		}

		public void shutdown() {
			this.shutdown = true;
			if (socket != null) {
				Closer.closeSilently(socket);
			}
			interrupt();
		}
	}

	private final ConcurrentMap<Long, SocketHandler> handlers = new ConcurrentHashMap<>();

	private final ServerSocket serverSocket;

	/**
	 * Constructor.
	 *
	 * @param port
	 *        The port number, or 0 to automatically allocate a port number.
	 * @param backlog
	 *        The server socket backlog.
	 * @param localBindAddress
	 *        The local InetAddress the server will bind to
	 * @param logEventInput
	 *        the log even input
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 * @since 2.7
	 */
	@SuppressWarnings("resource")
	public TcpSocketServer(final int port, final int backlog, final InetAddress localBindAddress, final LogEventBridge<T> logEventInput) throws IOException {
		this(port, logEventInput, createServerSocket(port, backlog, localBindAddress));
	}

	private static ServerSocket createServerSocket(final int port, final int backlog, final InetAddress localBindAddress) throws IOException {
		final ServerSocket serverSocket = new ServerSocket(port, backlog, localBindAddress);
		serverSocket.setSoTimeout(0); //accept socket timeout : infinite : must wait for new connections
		return serverSocket;
	}

	private static ServerSocket createServerSocket(final int port) throws IOException {
		return createServerSocket(port, 50, null);
	}

	/**
	 * Constructor.
	 *
	 * @param port
	 *         The port number, or 0 to automatically allocate a port number.
	 * @param logEventInput
	 *        the log even input
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 */
	@SuppressWarnings("resource")
	public TcpSocketServer(final int port, final LogEventBridge<T> logEventInput) throws IOException {
		this(port, logEventInput, createServerSocket(port));
	}

	/**
	 * Constructor.
	 *
	 * @param port
	 *        to listen.
	 * @param logEventInput
	 *        the log even input
	 * @param serverSocket
	 *        the socket server
	 * @throws IOException
	 *         if an I/O error occurs when opening the socket.
	 */
	public TcpSocketServer(final int port, final LogEventBridge<T> logEventInput, final ServerSocket serverSocket)
			throws IOException {
		super(port, logEventInput);
		this.serverSocket = serverSocket;
	}

	/**
	 * Accept incoming events and processes them.
	 */
	@Override
	public void run() {
		final EntryMessage entry = logger.traceEntry();
		while (isActive()) {
			if (serverSocket.isClosed()) {
				return;
			}
			try {
				// Accept incoming connections.
				logger.info("Listening for a connection {}...", serverSocket);
				@SuppressWarnings("resource") // clientSocket is closed during SocketHandler shutdown
				final Socket clientSocket = serverSocket.accept(); //use soTimeout socket parameter
				logger.debug("Accepted connection on {}...", serverSocket);

				// accept() will block until a client connects to the server.
				// If execution reaches this point, then it means that a client
				// socket has been accepted.

				logger.info("Socket accepted: {}", clientSocket);
				//Must defined socket parameters
				clientSocket.setSoLinger(true, 0); //define that close will be force to close immediatly
				clientSocket.setReceiveBufferSize(clientSocket.getReceiveBufferSize());
				clientSocket.setSoTimeout(10000);

				final SocketHandler handler = new SocketHandler(clientSocket);
				handlers.put(Long.valueOf(handler.getId()), handler);
				handler.start();
			} catch (final IOException e) {
				if (serverSocket.isClosed()) {
					// OK we're done.
					return;
				}
				if (e.getMessage().equals("Connection reset")) {
					//simpler log for standard exceptions
					logger.info("Exception encountered on accept. Ignoring. Message: {}", e.getMessage());
				} else {
					logger.error("Exception encountered on accept. Ignoring. Stack trace :", e);
				}
			}
		}
		for (final Map.Entry<Long, SocketHandler> handlerEntry : handlers.entrySet()) {
			final SocketHandler handler = handlerEntry.getValue();
			handler.shutdown();
			try {
				handler.join();
			} catch (final InterruptedException ignored) {
				// Ignore the exception
			}
		}
		logger.traceExit(entry);
	}

	/**
	 * Shutdown the server.
	 *
	 * @throws IOException if the server socket could not be closed
	 */
	@Override
	public void shutdown() throws IOException {
		final EntryMessage entry = logger.traceEntry();
		setActive(false);
		//Thread.currentThread().interrupt();
		serverSocket.close();
		logger.traceExit(entry);
	}

	@Override
	public String toString() {
		return "TcpSocketServer [serverSocket=" + serverSocket + ", handlers=" + handlers + ", logEventInput="
				+ logEventInput + "]";
	}
}
