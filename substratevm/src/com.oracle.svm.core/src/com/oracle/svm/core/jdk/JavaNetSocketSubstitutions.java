/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnixDomainSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import sun.nio.ch.IOStatus;
import sun.nio.ch.Net;

import com.oracle.svm.core.annotate.Alias;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrTicks;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.events.SocketReadEvent;
import jdk.jfr.events.SocketWriteEvent;
import sun.net.ConnectionResetException;
import com.oracle.svm.core.SubstrateUtil;

/**
 * This class essentially does what jdk.jfr.internal.instrument.SocketInputStreamInstrumentor does
 * to emit jdk.SocketRead events.
 */
@TargetClass(className = "java.net.Socket$SocketInputStream", onlyWith = HasJfrSupport.class)
final class Target_java_net_Socket_SocketInputStream {
    @Alias private InputStream in = null;
    @Alias private Socket parent = null;

    @Substitute
    public int read(byte[] b, int off, int len) throws IOException {
        SocketReadEvent event = new SocketReadEvent();
        if (!event.isEnabled()) {
            return read(b, off, len);
        }
        int bytesRead = 0;
        InetAddress remote = parent.getInetAddress();

        long startTicks = JfrTicks.elapsedTicks();
        event.begin();
        try {
            bytesRead = in.read(b, off, len);
        } finally {
            if (JavaVersionUtil.JAVA_SPEC >= 19) {
                Target_jdk_jfr_events_SocketReadEvent.commit(startTicks, JfrTicks.elapsedTicks() - startTicks, remote.getHostName(), remote.getHostAddress(), parent.getPort(),
                                parent.getSoTimeout(), bytesRead < 0 ? 0L : bytesRead, bytesRead < 0);
            } else {
                event.end();
                event.bytesRead = bytesRead < 0 ? 0L : bytesRead;
                event.endOfStream = bytesRead < 0;
                event.host = remote.getHostName();
                event.address = remote.getHostAddress();
                event.port = parent.getPort();
                event.timeout = parent.getSoTimeout();
                event.commit();
            }
        }
        return bytesRead;
    }
}

/**
 * This class essentially does what jdk.jfr.internal.instrument.SocketOutputStreamInstrumentor does
 * to emit jdk.SocketWrite events.
 */
@TargetClass(className = "java.net.Socket$SocketOutputStream", onlyWith = HasJfrSupport.class)
final class Target_java_net_Socket_SocketOutputStream {
    @Alias private OutputStream out = null;
    @Alias private Socket parent = null;

    @Substitute
    public void write(byte[] b, int off, int len) throws IOException {
        SocketWriteEvent event = new SocketWriteEvent();
        if (!event.isEnabled()) {
            out.write(b, off, len);
            return;
        }
        int bytesWritten = 0;
        long startTicks = JfrTicks.elapsedTicks();
        InetAddress remote = parent.getInetAddress();
        event.begin();
        try {
            out.write(b, off, len);
            bytesWritten = len;
        } finally {

            if (JavaVersionUtil.JAVA_SPEC >= 19) {
                Target_jdk_jfr_events_SocketWriteEvent.commit(startTicks, JfrTicks.elapsedTicks() - startTicks, remote.getHostName(), remote.getHostAddress(), parent.getPort(), bytesWritten);
            } else {
                event.end();
                event.host = remote.getHostName();
                event.address = remote.getHostAddress();
                event.port = parent.getPort();
                event.bytesWritten = bytesWritten;
                event.commit();
            }
        }
    }
}

@TargetClass(className = "sun.nio.ch.SocketChannelImpl", onlyWith = HasJfrSupport.class)
final class Target_sun_nio_ch_SocketChannelImpl {
    @Alias private ReentrantLock readLock;
    @Alias private ReentrantLock writeLock;
    @Alias private boolean connectionReset;
    @Alias private volatile boolean isInputClosed;
    @Alias private volatile boolean isOutputClosed;
    @Alias private FileDescriptor fd;
    @Alias private static Target_sun_nio_ch_NativeDispatcher nd;

    @Alias
    private native void ensureOpenAndConnected();

    @Alias
    private native void beginRead(boolean blocking) throws ClosedChannelException;

    @Alias
    private native void beginWrite(boolean blocking) throws ClosedChannelException;

    @Alias
    private native void throwConnectionReset() throws SocketException;

    @Alias
    private native void endRead(boolean blocking, boolean completed) throws AsynchronousCloseException;

    @Alias
    private native void endWrite(boolean blocking, boolean completed);

    @Alias
    public native SocketAddress getRemoteAddress() throws IOException;

    @Substitute
    public int read(ByteBuffer buf) throws IOException {
        SocketReadEvent event = new SocketReadEvent();
        event.begin();
        int bytesRead = 0;
        long startTicks = 0;
        if (event.isEnabled()) {
            startTicks = JfrTicks.elapsedTicks();
        }

        Objects.requireNonNull(buf);

        readLock.lock();
        try {
            ensureOpenAndConnected();
            boolean blocking = SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractSelectableChannel.class).isBlocking();
            int n = 0;
            try {
                beginRead(blocking);

                // check if connection has been reset
                if (connectionReset) {
                    throwConnectionReset();
                }
                // check if input is shutdown
                if (isInputClosed) {
                    bytesRead = IOStatus.EOF;
                } else {
                    n = Target_sun_nio_ch_IOUtil.read(fd, buf, -1, nd);
                    if (blocking) {
                        while (Target_sun_nio_ch_IOStatus.okayToRetry(n) && SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractInterruptibleChannel.class).isOpen()) {
                            SubstrateUtil.cast(this, Target_sun_nio_ch_SelChImpl.class).park(Net.POLLIN);
                            n = Target_sun_nio_ch_IOUtil.read(fd, buf, -1, nd);
                        }
                    }
                }
            } catch (ConnectionResetException e) {
                connectionReset = true;
                throwConnectionReset();
            } finally {
                endRead(blocking, n > 0);
                if (n <= 0 && isInputClosed) {
                    bytesRead = IOStatus.EOF;
                } else {
                    bytesRead = IOStatus.normalize(n);
                }

            }
            if (event.isEnabled()) {
                JavaNetSocketSubstitutions.commitChannelReadEvent(event, bytesRead, getRemoteAddress(), startTicks);
            }

            return bytesRead;
        } finally {
            readLock.unlock();
        }
    }

    @Substitute
    public long read(ByteBuffer[] dsts, int offset, int length)
                    throws IOException {
        SocketReadEvent event = new SocketReadEvent();
        event.begin();
        long bytesRead = 0;
        long startTicks = 0;
        if (event.isEnabled()) {
            startTicks = JfrTicks.elapsedTicks();
        }
        Objects.checkFromIndexSize(offset, length, dsts.length);

        readLock.lock();
        try {
            ensureOpenAndConnected();
            boolean blocking = SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractSelectableChannel.class).isBlocking();
            long n = 0;
            try {
                beginRead(blocking);

                // check if connection has been reset
                if (connectionReset) {
                    throwConnectionReset();
                }
                // check if input is shutdown
                if (isInputClosed) {
                    bytesRead = IOStatus.EOF;
                } else {
                    n = Target_sun_nio_ch_IOUtil.read(fd, dsts, offset, length, nd);

                    if (blocking) {
                        while (Target_sun_nio_ch_IOStatus.okayToRetry(n) && SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractInterruptibleChannel.class).isOpen()) {
                            SubstrateUtil.cast(this, Target_sun_nio_ch_SelChImpl.class).park(Net.POLLIN);
                            n = Target_sun_nio_ch_IOUtil.read(fd, dsts, offset, length, nd);
                        }
                    }
                }
            } catch (ConnectionResetException e) {
                connectionReset = true;
                throwConnectionReset();
            } finally {
                endRead(blocking, n > 0);
                if (n <= 0 && isInputClosed) {
                    bytesRead = IOStatus.EOF;
                } else {
                    bytesRead = IOStatus.normalize(n);
                }
            }
            if (event.isEnabled()) {
                JavaNetSocketSubstitutions.commitChannelReadEvent(event, bytesRead, getRemoteAddress(), startTicks);
            }

            return bytesRead;

        } finally {
            readLock.unlock();
        }
    }

    @Substitute
    public int write(ByteBuffer buf) throws IOException {
        SocketWriteEvent event = new SocketWriteEvent();
        event.begin();
        int bytesWritten = 0;
        long startTicks = 0;
        if (event.isEnabled()) {
            startTicks = JfrTicks.elapsedTicks();
        }

        Objects.requireNonNull(buf);
        writeLock.lock();
        try {
            ensureOpenAndConnected();
            boolean blocking = SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractSelectableChannel.class).isBlocking();
            int n = 0;
            try {
                beginWrite(blocking);
                n = Target_sun_nio_ch_IOUtil.write(fd, buf, -1, nd);
                if (blocking) {
                    while (Target_sun_nio_ch_IOStatus.okayToRetry(n) && SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractInterruptibleChannel.class).isOpen()) {
                        SubstrateUtil.cast(this, Target_sun_nio_ch_SelChImpl.class).park(Net.POLLOUT);
                        n = Target_sun_nio_ch_IOUtil.write(fd, buf, -1, nd);
                    }
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed) {
                    throw new AsynchronousCloseException();
                }
            }
            bytesWritten = IOStatus.normalize(n);

            if (event.isEnabled()) {
                JavaNetSocketSubstitutions.commitChannelWriteEvent(event, bytesWritten, getRemoteAddress(), startTicks);
            }

            return bytesWritten;
        } finally {
            writeLock.unlock();
        }
    }

    @Substitute
    public long write(ByteBuffer[] srcs, int offset, int length)
                    throws IOException {
        SocketWriteEvent event = new SocketWriteEvent();
        event.begin();
        long bytesWritten = 0;
        long startTicks = 0;
        if (event.isEnabled()) {
            startTicks = JfrTicks.elapsedTicks();
        }

        Objects.checkFromIndexSize(offset, length, srcs.length);

        writeLock.lock();
        try {
            ensureOpenAndConnected();
            boolean blocking = SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractSelectableChannel.class).isBlocking();
            long n = 0;
            try {
                beginWrite(blocking);
                n = Target_sun_nio_ch_IOUtil.write(fd, srcs, offset, length, nd);
                if (blocking) {
                    while (Target_sun_nio_ch_IOStatus.okayToRetry(n) && SubstrateUtil.cast(this, Target_java_nio_channels_spi_AbstractInterruptibleChannel.class).isOpen()) {
                        SubstrateUtil.cast(this, Target_sun_nio_ch_SelChImpl.class).park(Net.POLLOUT);
                        n = Target_sun_nio_ch_IOUtil.write(fd, srcs, offset, length, nd);
                    }
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed) {
                    throw new AsynchronousCloseException();
                }
            }
            bytesWritten = IOStatus.normalize(n);
            if (event.isEnabled()) {
                JavaNetSocketSubstitutions.commitChannelWriteEvent(event, bytesWritten, getRemoteAddress(), startTicks);
            }
            return bytesWritten;
        } finally {
            writeLock.unlock();
        }
    }
}

@TargetClass(className = "sun.nio.ch.NativeDispatcher", onlyWith = HasJfrSupport.class)
final class Target_sun_nio_ch_NativeDispatcher {

}

@TargetClass(className = "sun.nio.ch.IOUtil", onlyWith = HasJfrSupport.class)
final class Target_sun_nio_ch_IOUtil {
    @Alias
    static native int read(FileDescriptor fd, ByteBuffer dst, long position,
                    Target_sun_nio_ch_NativeDispatcher nd)
                    throws IOException;

    @Alias
    static native long read(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                    Target_sun_nio_ch_NativeDispatcher nd)
                    throws IOException;

    @Alias
    static native int write(FileDescriptor fd, ByteBuffer src, long position,
                    Target_sun_nio_ch_NativeDispatcher nd)
                    throws IOException;

    @Alias
    static native long write(FileDescriptor fd, ByteBuffer[] bufs, int offset, int length,
                    Target_sun_nio_ch_NativeDispatcher nd)
                    throws IOException;
}

@TargetClass(className = "sun.nio.ch.IOStatus", onlyWith = HasJfrSupport.class)
final class Target_sun_nio_ch_IOStatus {
    @Alias
    static native boolean okayToRetry(long n);
}

@TargetClass(className = "sun.nio.ch.SelChImpl", onlyWith = HasJfrSupport.class)
final class Target_sun_nio_ch_SelChImpl {
    @Alias
    native void park(int event) throws IOException;
}

@TargetClass(className = "java.nio.channels.spi.AbstractInterruptibleChannel", onlyWith = HasJfrSupport.class)
final class Target_java_nio_channels_spi_AbstractInterruptibleChannel {
    @Alias
    public native boolean isOpen();
}

@TargetClass(className = "java.nio.channels.spi.AbstractSelectableChannel", onlyWith = HasJfrSupport.class)
final class Target_java_nio_channels_spi_AbstractSelectableChannel {
    @Alias
    public native boolean isBlocking();
}

@TargetClass(className = "jdk.jfr.events.SocketWriteEvent", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_events_SocketWriteEvent {
    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static native void commit(long start, long duration, String host, String address, int port, long bytes);
}

@TargetClass(className = "jdk.jfr.events.SocketReadEvent", onlyWith = HasJfrSupport.class)
final class Target_jdk_jfr_events_SocketReadEvent {
    @Alias
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static native void commit(long start, long duration, String host, String address, int port, long timeout, long byteRead, boolean endOfStream);
}

/** Dummy class to have a class with the file's name. */
public final class JavaNetSocketSubstitutions {
    public static void commitChannelWriteEvent(SocketWriteEvent event, long bytesWritten, SocketAddress remoteAddress, long startTicks) {
        event.end();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) remoteAddress;
            String hostString = isa.getAddress().toString();
            int delimiterIndex = hostString.lastIndexOf('/');
            event.host = hostString.substring(0, delimiterIndex);
            event.address = hostString.substring(delimiterIndex + 1);
            event.port = isa.getPort();
        } else {
            UnixDomainSocketAddress udsa = (UnixDomainSocketAddress) remoteAddress;
            String path = "[" + udsa.getPath().toString() + "]";
            event.host = "Unix domain socket";
            event.address = path;
            event.port = 0;
        }
        event.bytesWritten = bytesWritten;
        event.commit();
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            Target_jdk_jfr_events_SocketWriteEvent.commit(startTicks, JfrTicks.elapsedTicks() - startTicks, event.host,
                            event.address, event.port, event.bytesWritten);
        }
    }

    public static void commitChannelReadEvent(SocketReadEvent event, long bytesRead, SocketAddress remoteAddress, long startTicks) {
        event.end();
        event.timeout = 0;
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) remoteAddress;
            String hostString = isa.getAddress().toString();
            int delimiterIndex = hostString.lastIndexOf('/');
            event.host = hostString.substring(0, delimiterIndex);
            event.address = hostString.substring(delimiterIndex + 1);
            event.port = isa.getPort();
        } else {
            UnixDomainSocketAddress udsa = (UnixDomainSocketAddress) remoteAddress;
            String path = "[" + udsa.getPath().toString() + "]";
            event.host = "Unix domain socket";
            event.address = path;
            event.port = 0;
        }
        event.bytesRead = bytesRead < 0 ? 0L : bytesRead;
        event.endOfStream = bytesRead < 0;
        event.commit();
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            Target_jdk_jfr_events_SocketReadEvent.commit(startTicks, JfrTicks.elapsedTicks() - startTicks, event.host,
                            event.address, event.port, event.timeout, event.bytesRead, event.endOfStream);
        }
    }
}
