/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.ToLongBiFunction;

import org.graalvm.nativebridge.BinaryOutput.ByteArrayBinaryOutput;
import org.graalvm.nativebridge.ProcessIsolateThreadSupport.DispatchSupport;
import org.graalvm.nativebridge.ProcessIsolateThreadSupport.Result;
import org.graalvm.nativebridge.ProcessIsolateThreadSupport.ThreadChannel;

/**
 * Represents a heap isolated by a separate operating system process. A {@code ProcessIsolate}
 * encapsulates execution within a distinct OS-level process, fully isolated from the host.
 * <p>
 * All foreign objects associated with a {@link ProcessPeer} are tied to exactly one
 * {@code ProcessIsolate}, which serves as the anchor for foreign execution and resource management.
 * </p>
 *
 * @see ProcessPeer
 * @see ProcessIsolateThread
 * @see Isolate
 */
public final class ProcessIsolate extends AbstractIsolate<ProcessIsolateThread> {

    private static final AtomicLong SOCKET_ID = new AtomicLong();
    private static final Map<Long, ProcessIsolate> isolates = new ConcurrentHashMap<>();

    private final long isolatePid;
    private final ProcessIsolateThreadSupport threadSupport;
    final ToLongBiFunction<ProcessIsolateThread, Long> releaseObjectHandle;
    private final Consumer<? super ProcessIsolate> onIsolateTearDown;

    private ProcessIsolate(long pid,
                    ProcessIsolateThreadSupport threadSupport,
                    ToLongBiFunction<ProcessIsolateThread, Long> releaseObjectHandle,
                    ThreadLocal<ProcessIsolateThread> threadLocal,
                    Consumer<? super ProcessIsolate> onIsolateTearDown) {
        super(threadLocal);
        this.isolatePid = pid;
        this.releaseObjectHandle = Objects.requireNonNull(releaseObjectHandle, "ReleaseObjectHandle must be non-null.");
        this.threadSupport = Objects.requireNonNull(threadSupport, "ThreadSupport must be non-null.");
        this.onIsolateTearDown = onIsolateTearDown;
    }

    @Override
    public long getIsolateId() {
        return isolatePid;
    }

    /**
     * Checks whether the current Java runtime and operating system support UNIX domain sockets.
     * <p>
     * This method attempts to create a socket channel using the {@link StandardProtocolFamily#UNIX}
     * protocol family to verify support for AF_UNIX sockets.
     * <p>
     * <strong>Note:</strong> If an {@link IOException} occurs during socket creation e.g., due to
     * resource exhaustion such as reaching the file descriptor limit, the method still returns
     * {@code true} because such an exception indicates an environmental issue rather than a lack of
     * support for UNIX domain sockets. Also, it is generally better to fail later, when the socket
     * is actually created, so that the failure reports the correct and more informative error
     * message.
     */
    public static boolean isSupported() {
        return ProcessIsolateThreadSupport.isSupported();
    }

    public static ProcessIsolate get(long isolateProcessId) {
        ProcessIsolate res = isolates.get(isolateProcessId);
        if (res == null) {
            throw new IllegalStateException("ProcessIsolate for process " + isolateProcessId + " does not exist.");
        }
        return res;
    }

    public static ProcessIsolate spawnProcessIsolate(ProcessIsolateConfig config,
                    BinaryMarshaller<Throwable> throwableMarshaller,
                    DispatchHandler[] dispatchHandlers,
                    ToLongBiFunction<ProcessIsolateThread, Long> releaseObjectHandle) throws IsolateCreateException {
        if (config.getLauncher() == null) {
            throw new IllegalArgumentException("Config must be an initiator ProcessIsolateConfig.");
        }
        DispatchSupportImpl dispatchSupport = new DispatchSupportImpl(throwableMarshaller, dispatchHandlers);
        ProcessIsolateThreadSupport processIsolateThreadSupport;
        Process process;
        try {
            processIsolateThreadSupport = ProcessIsolateThreadSupport.newBuilder(dispatchSupport).setLocalAddress(config.getInitiatorAddress()).buildInitiator();
            List<String> commandLine = new ArrayList<>();
            commandLine.add(config.getLauncher().toString());
            commandLine.addAll(config.getLauncherArguments());
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.inheritIO();
            process = builder.start();
        } catch (IOException e) {
            throw new IsolateCreateException(e);
        }
        long pid = process.pid();
        ProcessIsolate processIsolate = new ProcessIsolate(pid, processIsolateThreadSupport, releaseObjectHandle,
                        config.getThreadLocalFactory().get(), config.getOnIsolateTearDownHook());
        dispatchSupport.isolate = processIsolate;
        Future<Boolean> connected = processIsolateThreadSupport.connectInBackgroundThread();
        /*
         * Wait for the process isolate handshake. The starting isolate process might abort for some
         * reason before establishing a connection with the initiator. While waiting in accept, we
         * need to periodically check if the process is still alive.
         */
        while (true) {
            boolean alive = process.isAlive();
            try {
                connected.get(1, TimeUnit.SECONDS);
                break;
            } catch (TimeoutException timeout) {
                if (!alive) {
                    connected.cancel(true);
                    throw new IsolateCreateException("Failed to start isolate subprocess: the subprocess exited with a non-zero exit code (" + process.exitValue() + ").");
                }
            } catch (InterruptedException | CancellationException interruptedException) {
                throw new IsolateCreateException(interruptedException);
            } catch (ExecutionException executionException) {
                throw new IsolateCreateException(executionException.getCause());
            }
        }
        ProcessIsolate previous = isolates.put(pid, processIsolate);
        if (previous != null && !previous.isDisposed()) {
            throw new IllegalStateException("ProcessIsolate for process " + pid + " already exists and is not disposed.");
        }
        return processIsolate;
    }

    public static void connectProcessIsolate(ProcessIsolateConfig config,
                    BinaryMarshaller<Throwable> throwableMarshaller,
                    DispatchHandler[] dispatchHandlers,
                    ToLongBiFunction<ProcessIsolateThread, Long> releaseObjectHandle) throws IsolateCreateException {
        if (config.getLauncher() != null) {
            throw new IllegalArgumentException("Config must be a target ProcessIsolateConfig.");
        }
        Path initiatorSocket = config.getInitiatorAddress();
        assert initiatorSocket != null;
        Path localAddress = createUniqueSocketAddress(initiatorSocket.getParent(), "isolate");
        DispatchSupportImpl dispatchSupport = new DispatchSupportImpl(throwableMarshaller, dispatchHandlers);
        long pid = ProcessHandle.current().pid();
        try {
            ProcessIsolateThreadSupport processIsolateThreadSupport = ProcessIsolateThreadSupport.newBuilder(dispatchSupport).setInitiatorAddress(initiatorSocket).setLocalAddress(
                            localAddress).buildTarget();

            dispatchSupport.isolate = new ProcessIsolate(pid, processIsolateThreadSupport, releaseObjectHandle,
                            config.getThreadLocalFactory().get(), config.getOnIsolateTearDownHook());
            processIsolateThreadSupport.connectInCurrentThread();
        } catch (IOException ioe) {
            throw new IsolateCreateException(ioe);
        }
    }

    static Path createUniqueSocketAddress(Path folder, String prefix) {
        Path useFolder = folder != null ? folder : Path.of("");
        Path socketAddress;
        do {
            String fileName = String.format("%s_%d_%d", prefix, ProcessHandle.current().pid(), SOCKET_ID.incrementAndGet());
            socketAddress = useFolder.resolve(fileName);
        } while (Files.exists(socketAddress));
        return socketAddress;
    }

    @Override
    public String toString() {
        return "ProcessIsolate[" + uuid + " for isolate process " + isolatePid + "]";
    }

    @Override
    ProcessIsolateThread attachCurrentThread() {
        try {
            return new ProcessIsolateThread(Thread.currentThread(), this, false, threadSupport.attachThread());
        } catch (IOException e) {
            throw new IsolateDeathException(e);
        }
    }

    @Override
    void detachCurrentThread(ProcessIsolateThread currentThread) {
        try {
            currentThread.threadChannel.close();
        } catch (IOException e) {
            throw new IsolateDeathException(e);
        }
    }

    @Override
    void callTearDownHook() {
        if (onIsolateTearDown != null) {
            try {
                onIsolateTearDown.accept(this);
            } catch (IsolateDeathException id) {
                /*
                 * Ignore isolate death during close. The close operation may be invoked even on the
                 * crashed isolate.
                 */
            }
        }
    }

    @Override
    boolean doIsolateShutdown(ProcessIsolateThread shutdownThread) {
        boolean success = false;
        try {
            threadSupport.close();
            success = true;
        } catch (IOException | InterruptedException e) {
            // success is false
        } finally {
            if (success) {
                isolates.computeIfPresent(isolatePid, (id, processIsolate) -> (processIsolate == ProcessIsolate.this ? null : processIsolate));
            }
        }
        return success;
    }

    boolean isHost() {
        return threadSupport.isInitiator();
    }

    static Collection<? extends ProcessIsolate> getAllProcessIsolates() {
        return isolates.values();
    }

    private static final class DispatchSupportImpl implements DispatchSupport {

        private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

        private final BinaryMarshaller<Throwable> throwableMarshaller;
        private final DispatchHandler[] dispatchHandlers;
        private volatile ProcessIsolate isolate;

        DispatchSupportImpl(BinaryMarshaller<Throwable> throwableMarshaller, DispatchHandler[] dispatchHandlers) {
            this.throwableMarshaller = Objects.requireNonNull(throwableMarshaller, "ThrowableMarshaller must be non-null.");
            this.dispatchHandlers = Objects.requireNonNull(dispatchHandlers, "DispatchHandlers must be non-null.");
        }

        @Override
        public Result dispatch(ByteBuffer byteBuffer) {
            BinaryInput in = BinaryInput.create(byteBuffer.array());
            int messageId = in.readInt();
            int handlerIndex = messageId >>> 16;
            BinaryOutput out;
            boolean success = false;
            try {
                out = dispatchHandlers[handlerIndex].dispatch(messageId, isolate, in);
                success = true;
            } catch (Throwable t) {
                out = ByteArrayBinaryOutput.claimBuffer(in);
                throwableMarshaller.write(out, t);
            }
            if (out == null) {
                return new Result(success, EMPTY);
            } else {
                ByteBuffer payload = ByteBuffer.wrap(((BinaryOutput.ByteArrayBinaryOutput) out).getArray(), 0, out.getPosition());
                return new Result(success, payload);
            }
        }

        @Override
        public void onWorkerThreadStarted(Thread thread, ThreadChannel channel) {
            assert thread == Thread.currentThread();
            ProcessIsolate processIsolate = isolate;
            if (processIsolate.isAttached()) {
                throw new IllegalStateException(String.format("Worker thread %s is already attached to isolate %s.", Thread.currentThread(), this));
            }
            processIsolate.registerForeignThread(new ProcessIsolateThread(thread, processIsolate, true, channel));
        }

        @Override
        public void onWorkerThreadTerminated(Thread thread, ThreadChannel channel) {
            assert thread == Thread.currentThread();
            isolate.detachCurrentThread();
        }
    }
}
