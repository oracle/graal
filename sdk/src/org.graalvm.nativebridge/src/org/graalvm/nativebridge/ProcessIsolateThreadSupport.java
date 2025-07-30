/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativebridge;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides support for managing threads in a process isolated polyglot environment. For each thread
 * that is attached to the local process, a corresponding thread is created in a peer process. These
 * threads communicate via an unnamed {@code AF_UNIX} socket, enabling inter-process communication
 * and method calls between the two processes.
 *
 * <p>
 * This support allows reentrant operations, where the initiating process calls a remote operation
 * that is executed by a peer thread in the isolate subprocess. During this operation, the peer
 * thread may invoke an upcall to the initiator process, which must be completed before the initial
 * call can return.
 * </p>
 *
 * <p>
 * This class is thread-safe and ensures that all communication and operations between threads are
 * properly synchronized. It also includes mechanisms for forcibly closing connections and handling
 * interruptions during thread operations.
 * </p>
 * <p>
 * <b>Usage:</b><br>
 * Initiator process
 *
 * <pre>
 * ProcessIsolateThreadSupport host = ProcessIsolateThreadSupport.newBuilder(dispatchSupport).socketNamePrefix("host").buildInitiator();
 * Path localAddress = host.getLocalAddress();
 * spawnSubprocess("/path/to/isolate/launcher", localAddress.toString());
 * host.connect();
 *
 * ThreadEndPoint endPoint = host.attachThread();
 * ByteBuffer response = endPoint.sendAndReceive(request);
 * host.detachThread();
 * </pre>
 *
 * Target process
 *
 * <pre>
 * Path hostAddress = Path.of(args[0]);
 * ProcessIsolateThreadSupport host = ProcessIsolateThreadSupport.newBuilder(dispatchSupport).socketNamePrefix("target").initiatorAddress(hostAddress).buildTarget();
 * host.connectInCurrentThread();
 * </pre>
 * </p>
 */
final class ProcessIsolateThreadSupport {

    private static final int EOF = -1;
    private static final int ATTACH_HEADER_SIZE = 2 * Byte.BYTES + 2 * Integer.BYTES;
    private static final int CALL_HEADER_SIZE = 2 * Byte.BYTES + Integer.BYTES;

    private static final int INITIAL_REQUEST_CACHE_SIZE = 1 << 10;
    private static final int MAX_REQUEST_CACHE_SIZE = 1 << 20;

    private static final int MAX_INTERRUPTED_ATTACH_RETRIES = 10;

    private final DispatchSupport dispatchSupport;
    private final ServerSocketChannel local;
    private UnixDomainSocketAddress peer;
    private Thread listenThread;
    private final Set<ThreadChannel> workerThreads = ConcurrentHashMap.newKeySet();
    private final Set<ThreadChannel> attachedThreads = ConcurrentHashMap.newKeySet();
    private final boolean initiator;
    private volatile State state;

    private ProcessIsolateThreadSupport(DispatchSupport dispatchSupport,
                    ServerSocketChannel local,
                    UnixDomainSocketAddress peer) {
        this.dispatchSupport = Objects.requireNonNull(dispatchSupport);
        this.local = Objects.requireNonNull(local);
        this.peer = peer;
        this.initiator = peer == null;
        this.state = State.NEW;
    }

    static boolean isSupported() {
        ServerSocketChannel serverSocket;
        try {
            try {
                serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            } catch (UnsupportedOperationException unsupported) {
                return false;
            }
            serverSocket.close();
        } catch (IOException e) {
            /*
             * Handles cases where an {@link IOException} may be thrown due to the operating system
             * being unable to allocate a new socket, such as when the process has exhausted its
             * available file handles. Despite such failures, this method ensures that it still
             * reports that AX_UNIX sockets are supported. The intention is to fail later when the
             * socket is created with the correct error message.
             */
        }
        return true;
    }

    boolean isInitiator() {
        return initiator;
    }

    /**
     * Starts a new background thread to establishes a connection with the peer
     * {@link ProcessIsolateThreadSupport} instance and to process thread attachment requests. The
     * caller needs to wait until the returned {@link Future} is done before performing any requests
     * to this {@link ProcessIsolateThreadSupport}. The {@link Future} has value {@code true} when
     * connection was successful.
     */
    Future<Boolean> connectInBackgroundThread() {
        FutureTask<Boolean> result = new FutureTask<>(this::handleConnect);
        Thread thread = new Thread(() -> {
            result.run();
            try {
                result.get();
                accept();
            } catch (ExecutionException | CancellationException e) {
                // connect failed do not listen
            } catch (InterruptedException e) {
                throw new AssertionError("Should not reach here", e);
            }
        });
        thread.setName(String.format("%s Connection Listen Thread", ProcessIsolateThreadSupport.class.getSimpleName()));
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    /**
     * Establishes a connection with the peer {@link ProcessIsolateThreadSupport} instance. The
     * method uses the current thread to process thread attachment requests and exits when the
     * connection to the isolate is closed.
     * 
     * @throws IOException If an I/O error occurs while attempting to connect.
     */
    void connectInCurrentThread() throws IOException {
        if (handleConnect()) {
            accept();
        }
    }

    private synchronized boolean handleConnect() throws IOException {
        if (state != State.NEW) {
            throw new IllegalStateException("Already connected, current state: " + state);
        }
        if (this.peer == null) {
            // host initiator
            try (SocketChannel s = local.accept()) {
                String peerAddress = readConnectRequest(s);
                peer = UnixDomainSocketAddress.of(peerAddress);
            } catch (CloseException ce) {
                handleClose();
                throw ce;
            }
        } else {
            // isolate subprocess
            try (SocketChannel s = SocketChannel.open(peer)) {
                writeConnectRequest(s, getLocalAddress().toString());
            }
            installParentProcessWatchDog();
        }
        listenThread = Thread.currentThread();
        state = State.CONNECTED;
        return true;
    }

    private void installParentProcessWatchDog() {
        Optional<ProcessHandle> parentOpt = ProcessHandle.current().parent();
        if (parentOpt.isPresent()) {
            ProcessHandle parent = parentOpt.get();
            CompletableFuture<ProcessHandle> onExit = parent.onExit();
            onExit.thenRun(() -> {
                try {
                    handleClose();
                } catch (IOException ioe) {
                    /*
                     * Exiting because the parent process has already terminated. At this point,
                     * exceptions are no longer relevant, we only need to terminate the child
                     * process, which has been re-parented to init (systemd).
                     */
                }
            });
        }
    }

    /**
     * Closes the connection with the peer {@link ProcessIsolateThreadSupport} instance and
     * terminates all worker threads. This method ensures that resources are cleaned up and the
     * connection is terminated.
     *
     * @throws IOException If an I/O error occurs during the closing operation.
     * @throws InterruptedException If the operation is interrupted while waiting for threads to
     *             terminate.
     */
    synchronized void close() throws IOException, InterruptedException {
        if (state == State.CLOSED) {
            return;
        }
        state = State.CLOSED;
        closeAndDeleteLocalSocket();
        try (SocketChannel channel = SocketChannel.open(peer)) {
            writeCloseRequest(channel);
        } catch (IOException e) {
            // Closing may cause IOExceptions if it's called simultaneously from both sides.
        }
        listenThread.join();
        cancelWorkerThreads();
        for (ThreadChannel threadChannel : attachedThreads) {
            try {
                threadChannel.close();
            } catch (IOException ioe) {
                /*
                 * IOException at this point is not a concern, as the isolate subprocess has already
                 * exited. Closing sockets merely frees resources.
                 */
            }
        }
    }

    /**
     * Attaches the current thread to the remote {@link ProcessIsolateThreadSupport} instance. This
     * operation creates a new {@link Thread} in the remote process, corresponding to the calling
     * thread. The threads communicate over an unnamed {@code AF_UNIX} socket for inter-process
     * calls.
     *
     * @throws IOException If an I/O error occurs while attaching the thread.
     */
    ThreadChannel attachThread() throws IOException {
        checkState();
        SocketChannel c = connectPeer();
        c.configureBlocking(false);
        writeAttachRequest(c, ThreadInfo.current());
        ThreadChannel threadChannel = new ThreadChannel(this, c, null);
        attachedThreads.add(threadChannel);
        return threadChannel;
    }

    /**
     * Connects to the peer process using blocking socket channel.
     *
     * <p>
     * Using a non-blocking {@link SocketChannel} for the initial connect can fail on some Linux
     * systems under high load, throwing a {@link java.net.SocketException} with
     * {@code errno = EAGAIN (Resource temporarily unavailable)}. This makes non-blocking connect
     * unreliable in such environments.
     *
     * <p>
     * Although {@link Selector} and {@link SelectionKey#OP_CONNECT} can be used to wait for the
     * completion of a connection, they cannot be used to initiate it. Therefore, this method
     * performs the connect in blocking mode.
     *
     * <p>
     * If the connect attempt is interrupted (e.g., due to {@link Thread#interrupt()}), the
     * resulting {@link ClosedByInterruptException} is caught and ignored, and the method retries.
     * This avoids propagating the exception, which is important for distinguishing between
     * cancellation and interruption in {@code IsolateDeathHandler} as both {@code Context.close()}
     * and {@code Context.interrupt()} interrupt threads.
     */
    private SocketChannel connectPeer() throws IOException {
        int interruptCount = 0;
        try {
            while (true) {
                SocketChannel c = SocketChannel.open(StandardProtocolFamily.UNIX);
                try {
                    c.connect(peer);
                    return c;
                } catch (ClosedByInterruptException closed) {
                    if (interruptCount++ < MAX_INTERRUPTED_ATTACH_RETRIES) {
                        // Clear the thread interrupt status before retry
                        Thread.interrupted();
                        // Retry on interrupt to avoid leaking cancellation semantics into
                        // IsolateDeathHandler. Closing or interrupting contexts may interrupt this
                        // thread.
                    } else {
                        // Fail with IsolateDeathException on repeated interrupts to avoid livelock.
                        throw closed;
                    }
                }
            }
        } finally {
            if (interruptCount > 0 && !Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Retrieves the local address of the {@code AF_UNIX} socket used by this instance.
     *
     * @return The {@link Path} representing the local socket address.
     * @throws IOException If an I/O error occurs while retrieving the address.
     */
    Path getLocalAddress() throws IOException {
        return ((UnixDomainSocketAddress) local.getLocalAddress()).getPath();
    }

    static Builder newBuilder(DispatchSupport dispatchSupport) {
        return new Builder(dispatchSupport);
    }

    record Result(boolean success, ByteBuffer payload) {

        private ResponseType responseType() {
            return success ? ResponseType.SUCCESS : ResponseType.FAILURE;
        }
    }

    interface DispatchSupport {

        void onWorkerThreadStarted(Thread thread, ThreadChannel channel);

        void onWorkerThreadTerminated(Thread thread, ThreadChannel channel);

        Result dispatch(ByteBuffer message);
    }

    static final class Builder {

        private final DispatchSupport dispatchSupport;
        private Path localSocketAddress = Path.of(String.format("%s_%d", ProcessIsolateThreadSupport.class.getSimpleName(), ProcessHandle.current().pid()));
        private Path initiatorAddress;

        private Builder(DispatchSupport dispatchSupport) {
            this.dispatchSupport = Objects.requireNonNull(dispatchSupport, "DispatchSupport must be non-null.");
        }

        Builder setLocalAddress(Path socketAddress) {
            this.localSocketAddress = Objects.requireNonNull(socketAddress, "SocketAddress must be non-null.");
            return this;
        }

        Builder setInitiatorAddress(Path address) {
            this.initiatorAddress = Objects.requireNonNull(address, "Address must be non-null.");
            return this;
        }

        /**
         * Creates a new {@link ProcessIsolateThreadSupport} instance for an initiator process.
         * 
         * @throws IOException If an I/O error occurs while setting up the socket.
         */
        ProcessIsolateThreadSupport buildInitiator() throws IOException {
            ServerSocketChannel serverSocket = openUnixDomainServerSocket(localSocketAddress);
            return new ProcessIsolateThreadSupport(dispatchSupport, serverSocket, null);
        }

        /**
         * Creates a new {@link ProcessIsolateThreadSupport} instance for an isolate subprocess. The
         * subprocess connects to the initiator's {@link ProcessIsolateThreadSupport} instance and
         * handles requests accordingly.
         * 
         * @return A new instance of {@link ProcessIsolateThreadSupport} for the isolate subprocess.
         * @throws IOException If an I/O error occurs while establishing the connection.
         * @throws IllegalStateException if {@link #setInitiatorAddress(Path) initiatorAddress} was
         *             not set.
         */
        ProcessIsolateThreadSupport buildTarget() throws IOException {
            if (initiatorAddress == null) {
                throw new IllegalStateException("InitiatorAddress must be set.");
            }
            ServerSocketChannel serverSocket = openUnixDomainServerSocket(localSocketAddress);
            return new ProcessIsolateThreadSupport(dispatchSupport, serverSocket, UnixDomainSocketAddress.of(initiatorAddress));
        }

        private static ServerSocketChannel openUnixDomainServerSocket(Path socketPath) throws IOException {
            if (Files.exists(socketPath)) {
                throw new IllegalArgumentException(String.format("The socket '%s' already exists.", socketPath));
            }
            socketPath.toFile().deleteOnExit();
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
            ServerSocketChannel serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverSocket.bind(address);
            if (socketPath.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(socketPath, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
            return serverSocket;
        }
    }

    static final class ThreadChannel implements Closeable {

        private final ProcessIsolateThreadSupport owner;
        private final SocketChannel channel;
        private final Selector selector;
        private final SelectionKey readKey;
        private final Thread workerThread;

        private ThreadChannel(ProcessIsolateThreadSupport owner, SocketChannel channel, Thread workerThread) throws IOException {
            this.owner = Objects.requireNonNull(owner, "Owner must be non-null");
            this.channel = Objects.requireNonNull(channel, "Channel must be non-null");
            /*
             * We need to use non-blocking channel to support cancelling. The blocking channel is
             * automatically closed whenever the thread calling read is interrupted.
             */
            this.channel.configureBlocking(false);
            this.selector = Selector.open();
            this.readKey = channel.register(selector, SelectionKey.OP_READ);
            this.workerThread = workerThread;
        }

        /**
         * Detaches the current thread from the remote {@link ProcessIsolateThreadSupport} instance.
         * This results in the termination of the corresponding thread in the remote process and the
         * closure of the communication socket.
         *
         * @throws IOException If an I/O error occurs during the detachment process.
         */
        @Override
        public void close() throws IOException {
            selector.close();
            channel.configureBlocking(true);
            channel.close();
        }

        /**
         * Sends a request and awaits a response from the remote {@link ProcessIsolateThreadSupport}
         * instance. While awaiting the completion of the request, nested requests from the remote
         * process may be processed.
         *
         * @param data The data to be sent as part of the request.
         * @return The response data received from the remote process.
         * @throws IOException If an I/O error occurs during the request-response operation.
         */
        Result sendAndReceive(ByteBuffer data) throws IOException {
            owner.checkState();
            ByteBuffer header = ByteBuffer.allocate(CALL_HEADER_SIZE);
            header.put(RequestType.CALL.tag);
            header.put(ResponseType.UNDEFINED.binaryForm);
            header.putInt(data.limit() - data.position());
            header.flip();
            writeFully(channel, new ByteBuffer[]{header, data});
            while (true) { // TERMINATION ARGUMENT: type
                if (selector.select() == 0 || !selector.selectedKeys().remove(readKey)) {
                    continue;
                }
                header.clear();
                readFully(channel, header);
                header.flip();
                RequestType type = RequestType.fromTag(header.get());
                ResponseType responseType = ResponseType.fromBinaryForm(header.get());
                int contentLength = header.getInt();
                ByteBuffer target;
                if (contentLength <= data.capacity()) {
                    data.clear();
                    data.limit(contentLength);
                    target = data;
                } else {
                    target = ByteBuffer.allocate(contentLength);
                }
                readFully(channel, target);
                switch (type) {
                    case CALL -> {
                        Result result = owner.dispatchSupport.dispatch(target);
                        target = result.payload;
                        header.clear();
                        header.put(RequestType.RESULT.tag);
                        header.put(result.responseType().binaryForm);
                        header.putInt(target.limit() - target.position());
                        header.flip();
                        writeFully(channel, new ByteBuffer[]{header, target});
                    }
                    case RESULT -> {
                        assert responseType != ResponseType.UNDEFINED;
                        boolean success = responseType == ResponseType.SUCCESS;
                        return new Result(success, target);
                    }
                    default -> throw throwIllegalRequest(type, RequestType.CALL, RequestType.RESULT);
                }
            }
        }

        private void dispatch() throws IOException {
            ByteBuffer header = ByteBuffer.allocate(CALL_HEADER_SIZE);
            ByteBuffer payloadCache = ByteBuffer.allocate(INITIAL_REQUEST_CACHE_SIZE);
            while (owner.state == State.CONNECTED) {
                if (selector.select() == 0 || !selector.selectedKeys().remove(readKey)) {
                    continue;
                }
                readFully(channel, header);
                header.flip();
                RequestType type = RequestType.fromTag(header.get());
                if (type != RequestType.CALL) {
                    throw throwIllegalRequest(type, RequestType.CALL);
                }
                // Ignore error flag, used only in response
                header.position(header.position() + 1);
                int len = header.getInt();
                ByteBuffer request;
                if (len <= payloadCache.capacity()) {
                    request = payloadCache;
                    request.clear();
                    request.limit(len);
                } else {
                    request = ByteBuffer.allocate(len);
                    if (len < MAX_REQUEST_CACHE_SIZE) {
                        payloadCache = request;
                    }
                }
                readFully(channel, request);
                Result result = owner.dispatchSupport.dispatch(request);
                ByteBuffer response = result.payload;
                header.clear();
                header.put(RequestType.RESULT.tag);
                header.put(result.responseType().binaryForm);
                header.putInt(response.limit() - response.position());
                header.flip();
                writeFully(channel, new ByteBuffer[]{header, response});
                header.clear();
            }
        }
    }

    private static void writeConnectRequest(SocketChannel c, String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(ATTACH_HEADER_SIZE);
        header.put(RequestType.CONNECT.tag);
        header.putInt(bytes.length);
        /*
         * Align the message size to ATTACH_HEADER_SIZE. For performance reasons, CONNECT, ATTACH,
         * and CLOSE requests must have the same size. It is preferable to send a larger request for
         * CONNECT and CLOSE, which are called only once, rather than performing two read syscalls
         * when handling ATTACH.
         */
        header.position(header.limit());
        header.flip();
        ByteBuffer contentBuffer = ByteBuffer.wrap(bytes);
        writeFully(c, new ByteBuffer[]{header, contentBuffer});
    }

    private static String readConnectRequest(SocketChannel c) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ATTACH_HEADER_SIZE);
        readFully(c, header);
        header.flip();
        RequestType type = RequestType.fromTag(header.get());
        switch (type) {
            case CONNECT -> {
                int len = header.getInt();
                byte[] bytes = new byte[len];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                readFully(c, buffer);
                return new String(bytes, 0, buffer.position(), StandardCharsets.UTF_8);
            }
            case CLOSE -> throw new CloseException();
            default -> throw throwIllegalRequest(type, RequestType.CONNECT, RequestType.CLOSE);
        }
    }

    private static RuntimeException throwIllegalRequest(RequestType type, RequestType expected, RequestType... expectedRest) {
        String expectedNames = Stream.concat(Stream.of(expected), Arrays.stream(expectedRest)).map(Enum::name).collect(Collectors.joining(", "));
        throw new IllegalStateException(String.format("Illegal request %s, expected %s", type, expectedNames));
    }

    private void accept() {
        while (state == State.CONNECTED) {
            SocketChannel peerThreadChannel = null;
            try {
                peerThreadChannel = local.accept();
                ThreadInfo info = readAttachRequest(peerThreadChannel);
                /*
                 * By default, the stack size for new threads is 512KB in native-image, compared to
                 * 2MB on HotSpot. We choose to use the larger size (2MB) for consistency and
                 * safety. Ideally, the host thread's actual stack size should be communicated as
                 * part of the attach request, but the Java API does not expose this information. To
                 * retrieve it, we would need to use a native library that calls
                 * pthread_get_stacksize_np(pthread_self()).
                 */
                Thread workerThread = new Thread(null, new DispatchRunnable(peerThreadChannel), info.name(), 2097152);
                workerThread.setPriority(info.priority);
                workerThread.setDaemon(info.daemon);
                workerThread.start();
            } catch (CloseException ce) {
                try {
                    handleClose();
                    if (peerThreadChannel != null) {
                        peerThreadChannel.close();
                    }
                } catch (IOException e) {
                    // Ignore close exception on exit.
                }
            } catch (IOException ioe) {
                /*
                 * Connection failed, close the peerThreadChannel to notify client opening the
                 * connection.
                 */
                if (peerThreadChannel != null) {
                    try {
                        peerThreadChannel.close();
                    } catch (IOException e) {
                        // Ignore close exception on exit.
                    }
                }
            }
        }
    }

    private void handleClose() throws IOException {
        state = State.CLOSED;
        closeAndDeleteLocalSocket();
        try {
            cancelWorkerThreads();
        } catch (InterruptedException ie) {
            throw new InterruptedIOException();
        }
    }

    private void closeAndDeleteLocalSocket() throws IOException {
        Path localAddress = getLocalAddress();
        local.close();
        try {
            Files.deleteIfExists(localAddress);
        } catch (IOException ioe) {
            /*
             * Failed to eagerly delete the socket file. This is not a critical issue since the file
             * will be deleted automatically on JVM exit.
             */
        }
    }

    private void cancelWorkerThreads() throws InterruptedException {
        for (ThreadChannel worker : workerThreads) {
            worker.selector.wakeup();
            worker.workerThread.interrupt();
            worker.workerThread.join();
        }
    }

    private static void writeCloseRequest(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ATTACH_HEADER_SIZE);
        header.put(RequestType.CLOSE.tag);
        /*
         * Align the message size to ATTACH_HEADER_SIZE. For performance reasons, CONNECT, ATTACH,
         * and CLOSE requests must have the same size. It is preferable to send a larger request for
         * CONNECT and CLOSE, which are called only once, rather than performing two read syscalls
         * when handling ATTACH.
         */
        header.position(header.limit());
        header.flip();
        writeFully(channel, header);
    }

    private static void writeAttachRequest(SocketChannel channel, ThreadInfo info) throws IOException {
        byte[] nameBytes = info.name().getBytes(StandardCharsets.UTF_8);
        ByteBuffer header = ByteBuffer.allocate(ATTACH_HEADER_SIZE);
        header.put(RequestType.ATTACH.tag);
        header.putInt(info.priority);
        header.put((byte) (info.daemon() ? 1 : 0));
        header.putInt(nameBytes.length);
        header.flip();
        ByteBuffer nameBuffer = ByteBuffer.wrap(nameBytes);
        writeFully(channel, new ByteBuffer[]{header, nameBuffer});
    }

    private static ThreadInfo readAttachRequest(SocketChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ATTACH_HEADER_SIZE);
        readFully(channel, header);
        header.flip();
        RequestType type = RequestType.fromTag(header.get());
        switch (type) {
            case ATTACH -> {
                int priority = header.getInt();
                boolean daemon = header.get() != 0;
                int len = header.getInt();
                byte[] nameBytes = new byte[len];
                ByteBuffer buffer = ByteBuffer.wrap(nameBytes);
                readFully(channel, buffer);
                String name = new String(nameBytes, 0, buffer.position(), StandardCharsets.UTF_8);
                return new ThreadInfo(name, priority, daemon);
            }
            case CLOSE -> throw new CloseException();
            default -> throw throwIllegalRequest(type, RequestType.ATTACH, RequestType.CLOSE);
        }
    }

    private void checkState() {
        State currentState = state;
        if (currentState != State.CONNECTED) {
            throw new IllegalStateException("Must be connected, current state " + currentState);
        }
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        do {
            if (channel.write(buffer) == EOF) {
                throw new EOFException();
            }
        } while (buffer.hasRemaining());
    }

    private static void writeFully(SocketChannel channel, ByteBuffer[] buffers) throws IOException {
        ByteBuffer lastBuffer = buffers[buffers.length - 1];
        do {
            if (channel.write(buffers) == EOF) {
                throw new EOFException();
            }
        } while (lastBuffer.hasRemaining());
    }

    private static void readFully(SocketChannel channel, ByteBuffer buffer) throws IOException {
        do {
            if (channel.read(buffer) == EOF) {
                throw new EOFException();
            }
        } while (buffer.hasRemaining());
    }

    private record ThreadInfo(String name, int priority, boolean daemon) {

        static ThreadInfo current() {
            Thread current = Thread.currentThread();
            return new ThreadInfo(current.getName(), current.getPriority(), current.isDaemon());
        }
    }

    private enum State {
        NEW,
        CONNECTED,
        CLOSED
    }

    private enum RequestType {
        CONNECT(0),
        ATTACH(1),
        CLOSE(2),
        CALL(3),
        RESULT(4);

        private static final RequestType[] TYPES;
        static {
            RequestType[] values = values();
            RequestType[] types = new RequestType[values.length];
            for (RequestType rt : values) {
                types[rt.tag] = rt;
            }
            TYPES = types;
        }

        final byte tag;

        RequestType(int tag) {
            this.tag = (byte) tag;
        }

        static RequestType fromTag(int tag) {
            return TYPES[tag];
        }
    }

    private enum ResponseType {
        SUCCESS(1),
        FAILURE(0),
        UNDEFINED(-1);

        final byte binaryForm;

        ResponseType(int binaryForm) {
            this.binaryForm = (byte) binaryForm;
        }

        static ResponseType fromBinaryForm(byte raw) {
            for (ResponseType responseType : values()) {
                if (responseType.binaryForm == raw) {
                    return responseType;
                }
            }
            throw new IllegalArgumentException("Unsupported ResponseType binaryForm " + raw);
        }
    }

    @SuppressWarnings("serial")
    private static final class CloseException extends IOException {
    }

    final class DispatchRunnable implements Runnable {

        private final SocketChannel peerThreadChannel;

        private DispatchRunnable(SocketChannel peerThreadChannel) {
            this.peerThreadChannel = peerThreadChannel;
        }

        @Override
        public void run() {
            Thread currentThread = Thread.currentThread();
            try (ThreadChannel threadChannel = new ThreadChannel(ProcessIsolateThreadSupport.this, peerThreadChannel, currentThread)) {
                workerThreads.add(threadChannel);
                try {
                    dispatchSupport.onWorkerThreadStarted(currentThread, threadChannel);
                    try {
                        threadChannel.dispatch();
                    } finally {
                        dispatchSupport.onWorkerThreadTerminated(currentThread, threadChannel);
                    }
                } finally {
                    workerThreads.remove(threadChannel);
                }
            } catch (IOException ioe) {
                // Closes peerThreadChannel to notify client
            }
        }
    }
}
