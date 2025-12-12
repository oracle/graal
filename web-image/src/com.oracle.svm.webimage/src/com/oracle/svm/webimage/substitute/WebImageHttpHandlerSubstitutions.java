/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute;

import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSString;
import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Handle HTTP and HTTPS URLs using XMLHttpRequest (instead of TCP sockets). The handlers are used
 * when either {@link URL#openStream()} or {@link URL#openConnection()} is called on an URL whose
 * protocol is either HTTP or HTTPS. Example:
 * {@code InputStream is = new URL("http://example.com/foo.html").openStream();}.
 * <p>
 * The substitution only takes place if there is an {@code WebImageHttpHandlerSubstitutions}
 * singleton in {@link ImageSingletons}. The singleton is present if and only if the VM type is set
 * to Browser. At run-time, opening the connection fails if attempted on the UI thread as opposed to
 * a worker. This is detected by the presence of a {@code window} object.
 */
public final class WebImageHttpHandlerSubstitutions {
    static final class Enabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(WebImageHttpHandlerSubstitutions.class);
        }
    }

    static URLConnection openConnection(URL u, Proxy p) throws IOException {
        if (p != null) {
            throw new IOException("Cannot set proxy from JavaScript");
        }
        return new XhrUrlConnection(u);
    }

    // The first call to connect() creates the request, sends it, and saves the response.
    // The saved response is used to implement getInputStream().
    private static final class XhrUrlConnection extends URLConnection {
        private JSObject content;

        XhrUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
            if (content != null) {
                return;
            }
            JSValue connectResult = jsConnect(url.toExternalForm());
            switch (connectResult) {
                case JSString jsString -> throw new IOException(jsString.asString());
                case JSObject jsByteArray -> content = jsByteArray;
                default -> throw new IOException("Got unexpected return value from jsConnect: " + connectResult);
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return new JSByteArrayInputStream(content);
        }

        /// Creates the request and sends it.
        ///
        /// @return If the sending returned a 200 status code, returns a [JSObject] that wraps a
        /// `Uint8Array`, otherwise returns a `JSString` containing the error message.
        @JS.Coerce
        @JS("""
                        try {
                            if('window' in self) {
                                return 'HTTP(S) URL connections are not allowed on main thread';
                            }
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', urlString, false);
                            xhr.responseType = 'arraybuffer';
                            xhr.send();
                            if(xhr.status === 200) {
                                return new Uint8Array(xhr.response);
                            } else {
                                return xhr.status + ' ' + xhr.statusText;
                            }
                        } catch(e) {
                            return e.toString();
                        }
                        """)
        private native JSValue jsConnect(String urlString);
    }

    /// [InputStream] implementation that is backed by a `Uint8Array` from JavaScript.
    private static final class JSByteArrayInputStream extends InputStream {
        private final JSObject jsUint8Array;
        private final int length;
        private int idx = 0;

        private JSByteArrayInputStream(JSObject jsUint8Array) {
            this.jsUint8Array = jsUint8Array;
            this.length = jsUint8Array.get("length", Integer.class);
        }

        @Override
        public int read() throws IOException {
            if (idx >= length) {
                return -1;
            }

            int i = jsUint8Array.get(idx, Integer.class);

            if (i < 0 || i > 255) {
                throw new IOException("Invalid byte value at index " + idx + ": " + i);
            }

            idx++;
            return i;
        }
    }
}

@TargetClass(className = "sun.net.www.protocol.http.Handler", onlyWith = WebImageHttpHandlerSubstitutions.Enabled.class)
@SuppressWarnings("all")
final class Target_sun_net_www_protocol_http_Handler_Web {
    @Substitute
    protected java.net.URLConnection openConnection(URL u, Proxy p) throws IOException {
        return WebImageHttpHandlerSubstitutions.openConnection(u, p);
    }
}

@TargetClass(className = "sun.net.www.protocol.https.Handler", onlyWith = WebImageHttpHandlerSubstitutions.Enabled.class)
@SuppressWarnings("all")
final class Target_sun_net_www_protocol_https_Handler_Web {
    @Substitute
    protected java.net.URLConnection openConnection(URL u, Proxy p) throws IOException {
        return WebImageHttpHandlerSubstitutions.openConnection(u, p);
    }
}
