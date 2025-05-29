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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.webimage.api.JS;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.webimage.annotation.JSRawCall;

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
        private byte[] content;

        XhrUrlConnection(URL url) {
            super(url);
        }

        @Override
        public void connect() throws IOException {
            if (content != null) {
                return;
            }
            if (jsConnect(url.toExternalForm())) {
                content = jsGetContent();
            } else {
                throw new IOException(jsGetMessage());
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            connect();
            return new ByteArrayInputStream(content);
        }

        // Creates the request and sends it. In addition to the return value,
        // a temporary JavaScript field is set and can be retrieved using
        // jsGetContent (if this method returns true) or
        // jsGetMessage (if this method returns false).
        // @formatter:off
        @JSRawCall
        @JS(""
                + "try {\n"
                + "    if('window' in self) {\n" // are we on the main thread and not worker?
                + "        this.r = 'HTTP(S) URL connections are not allowed on main thread';\n"
                + "        return false;\n"
                + "    }\n"
                + "    var xhr = new XMLHttpRequest();\n"
                + "    xhr.open('GET', urlString.toJSString(), false);\n"
                + "    xhr.responseType = 'arraybuffer';\n"
                + "    xhr.send();\n"
                + "    if(xhr.status === 200) {\n"
                + "        this.r = new Int8Array(xhr.response);\n"
                + "        return true;\n"
                + "    } else {\n"
                + "        this.r = xhr.status + ' ' + xhr.statusText;\n" // e.g. "404 Not Found"
                + "        return false;\n"
                + "    }\n"
                + "} catch(e) {\n"
                + "    this.r = e.toString();\n"
                + "    return false;\n"
                + "}\n"
        )
        // @formatter:on
        private native boolean jsConnect(String urlString);

        // Returns and clears the field set by jsConnect.
        @JS.Coerce
        @JS("var request = this[runtime.symbol.javaNative]; var r = request.r; request.r = null; return r;")
        private native byte[] jsGetContent();

        // Returns and clears the field set by jsConnect.
        @JS.Coerce
        @JS("var request = this[runtime.symbol.javaNative]; var r = request.r; request.r = null; return r;")
        private native String jsGetMessage();
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
