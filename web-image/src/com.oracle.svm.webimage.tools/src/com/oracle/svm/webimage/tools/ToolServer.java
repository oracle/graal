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

package com.oracle.svm.webimage.tools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Simple HTTP server that serves static webpages for various Web Image debugging and analysis
 * tools.
 *
 * The server is started by running the main class, and optionally specifying the port number.
 */
public class ToolServer {
    public static void main(String[] args) throws Exception {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : 8000;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new DefaultHandler());
            server.setExecutor(null);
            System.out.println("Starting Web Image tool server at http://localhost:" + port);
            server.start();
            System.out.println("[press Ctrl+C to stop]");
        } catch (Exception e) {
            System.out.println("Exception occurred: " + e.getMessage());
            throw e;
        }
    }

    private static final class PageHandler implements HttpHandler {
        private final String resourcePath;

        private PageHandler(String resourcePath) {
            this.resourcePath = resourcePath;
        }

        private static byte[] toBytes(InputStream is) throws IOException {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] chunk = new byte[16384];
                int count;
                while ((count = is.read(chunk, 0, chunk.length)) != -1) {
                    baos.write(chunk, 0, count);
                }
                return baos.toByteArray();
            } finally {
                is.close();
            }
        }

        @Override
        public void handle(HttpExchange http) throws IOException {
            try {
                InputStream is = getClass().getResourceAsStream(resourcePath);
                if (is == null) {
                    String message = "Cannot find resource " + resourcePath;
                    http.sendResponseHeaders(404, message.length());
                    OutputStream os = http.getResponseBody();
                    os.write(message.getBytes());
                    return;
                }
                byte[] bytes = toBytes(is);
                http.sendResponseHeaders(200, bytes.length);
                OutputStream os = http.getResponseBody();
                os.write(bytes);
            } finally {
                http.close();
            }
        }
    }

    private static final class DefaultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange http) throws IOException {
            switch (http.getRequestURI().getPath()) {
                case "/":
                    new PageHandler("/index.html").handle(http);
                    break;
                case "/debug/offheap":
                    new PageHandler("/debug/offheap.html").handle(http);
                    break;
                default:
                    new PageHandler(http.getRequestURI().getPath()).handle(http);
                    break;
            }
        }
    }
}
