/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.persist.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Handler extends URLStreamHandler {
    private static Map<String, URL> bindings = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, URLConnection> connections = Collections.synchronizedMap(new HashMap<>());
    private static Map<String, Collection<URLConnection>> multiConnections = Collections.synchronizedMap(new HashMap<>());
    private static Set<String> visitedURLs = Collections.synchronizedSet(new HashSet<>());
    private static Map<String, URLConnection> httpProxyConnections = Collections.synchronizedMap(new HashMap<>());

    public Handler() {
    }

    public static void bind(String s, URL u) {
        bindings.put(s, u);
    }

    public static void bind(String s, URLConnection con) {
        connections.put(s, con);
    }

    public static void bindMulti(String s, URLConnection con) {
        multiConnections.computeIfAbsent(s, (k) -> new ArrayList<>()).add(con);
    }

    public static void bindProxy(String s, URLConnection con) {
        httpProxyConnections.put(s, con);
    }

    public static void clear() {
        bindings.clear();
        connections.clear();
        httpProxyConnections.clear();
        multiConnections.clear();
        visitedURLs.clear();
    }

    public static void clearVisited() {
        visitedURLs.clear();
    }

    public static boolean isVisited(String u) {
        return visitedURLs.contains(u);
    }

    public static boolean isVisited(URL u) {
        return visitedURLs.contains(u.toString());
    }

    @Override
    protected URLConnection openConnection(URL u, Proxy p) throws IOException {
        if (p.type() == Proxy.Type.DIRECT) {
            return openConnection(u);
        } else if (p.type() != Proxy.Type.HTTP) {
            return null;
        }
        URLConnection c = httpProxyConnections.get(u.toString());
        if (c != null) {
            return doOpenConnection(u, c);
        } else {
            throw new ConnectException(u.toExternalForm());
        }
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        URLConnection c = connections.get(u.toString());
        if (c == null) {
            Collection<URLConnection> col = multiConnections.getOrDefault(u.toString(), Collections.emptyList());
            Iterator<URLConnection> it = col.iterator();
            if (it.hasNext()) {
                c = it.next();
                it.remove();
            }
        }
        return doOpenConnection(u, c);
    }

    private static URLConnection doOpenConnection(URL u, URLConnection c) throws IOException {
        visitedURLs.add(u.toString());
        if (c != null) {
            return c;
        }
        URL x = bindings.get(u.toString());
        if (x != null) {
            return x.openConnection();
        }
        throw new FileNotFoundException("Unsupported");
    }
}
