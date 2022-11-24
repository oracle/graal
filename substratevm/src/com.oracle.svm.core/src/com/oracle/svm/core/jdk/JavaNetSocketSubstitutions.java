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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import com.oracle.svm.core.annotate.Alias;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrTicks;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import jdk.jfr.events.SocketReadEvent;
import jdk.jfr.events.SocketWriteEvent;

/**
 * This class essentially does what
 * {@link jdk.jfr.internal.instrument.SocketInputStreamInstrumentor} does to emit jdk.SocketRead
 * events.
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
 * This class essentially does what
 * {@link jdk.jfr.internal.instrument.SocketOutputStreamInstrumentor} does to emit jdk.SocketWrite
 * events.
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
}
