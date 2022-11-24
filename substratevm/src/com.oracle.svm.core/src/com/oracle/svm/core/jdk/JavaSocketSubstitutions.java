/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package scom.oracle.svm.core.jdk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import com.oracle.svm.core.annotate.Alias;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.HasJfrSupport;
import jdk.jfr.events.SocketReadEvent;
import jdk.jfr.events.SocketWriteEvent;

@TargetClass(className = "java.net.Socket$SocketInputStream", onlyWith = HasJfrSupport.class)
final class Target_java_net_Socket_SocketInputStream {
    @Alias
    private InputStream in = null;
    @Alias
    private Socket parent = null;

    @Substitute
    public int read(byte[] b, int off, int len) throws IOException {
        SocketReadEvent event = new SocketReadEvent();
        int bytesRead = 0;
        event.begin();
        try {
            bytesRead = in.read(b, off, len);
        } finally {
            event.end();
            if (bytesRead < 0) {
                event.bytesRead = 0;
                event.endOfStream = true;
            } else {
                event.bytesRead = bytesRead;
                event.endOfStream = false;
            }
            InetAddress remote = parent.getInetAddress();
            event.host = remote.getHostName();
            event.address = remote.getHostAddress();
            event.port = parent.getPort();
            event.timeout = parent.getSoTimeout();
            event.commit();
            return bytesRead;
        }
    }
}

@TargetClass(className = "java.net.Socket$SocketOutputStream", onlyWith = HasJfrSupport.class)
final class Target_java_net_Socket_SocketOutputStream {
    @Alias
    private OutputStream out = null;
    @Alias
    private Socket parent = null;

    @Substitute
    public void write(byte[] b, int off, int len) throws IOException {
        SocketWriteEvent event = new SocketWriteEvent();
        int bytesWritten = 0;
        event.begin();
        try {
            out.write(b, off, len);
            bytesWritten = len;
        } finally {
            event.end();
            InetAddress remote = parent.getInetAddress();
            event.host = remote.getHostName();
            event.address = remote.getHostAddress();
            event.port = parent.getPort();
            event.bytesWritten = bytesWritten;
            event.commit();
        }
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaSocketSubstitutions {
}
