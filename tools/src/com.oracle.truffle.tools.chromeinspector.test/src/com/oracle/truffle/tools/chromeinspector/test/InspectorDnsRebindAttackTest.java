/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static java.lang.Integer.parseInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class InspectorDnsRebindAttackTest {

    private Context context;
    private ByteArrayOutputStream errorOutput;

    @Before
    public void setUp() {
        errorOutput = new ByteArrayOutputStream();
        final String testPath = "testPath-" + SecureInspectorPathGenerator.getToken();
        context = Context.newBuilder().option("inspect.Path", testPath).err(errorOutput).build();
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        errorOutput.reset();
    }

    @Test
    public void testMissingHost() throws IOException {
        testDnsRebindForInvalidHost(null);
    }

    @Test
    public void testInvalidHostWithoutPort() throws IOException {
        testDnsRebindForInvalidHost("evil.example.com");
    }

    @Test
    public void testInvalidHostWithStandardPort() throws IOException {
        testDnsRebindForInvalidHost("evil.example.com:9229");
    }

    @Test
    public void testInvalidHostWithNonstandardPort() throws IOException {
        testDnsRebindForInvalidHost("evil.example.com:9228");
    }

    @Test
    public void testLocalhostIpv4() throws IOException {
        testDnsRebindForValidAddress("127.0.0.1");
    }

    @Test
    public void testLocalhostIpv6() throws IOException {
        testDnsRebindForValidAddress("[::1]");
    }

    @Test
    public void testLocalhostHostname() throws IOException {
        testDnsRebindForValidAddress("localhost");
    }

    @Test
    public void testLocalNetworkIp() throws IOException {
        testDnsRebindForValidAddress("192.168.6.6");
    }

    @Test
    public void testPublicIpv4() throws IOException {
        testDnsRebindForValidAddress("1.2.3.4");
    }

    @Test
    public void testIpv6Long() throws IOException {
        testDnsRebindForValidAddress("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]");
    }

    @Test
    public void testIpv6Short() throws IOException {
        testDnsRebindForValidAddress("[2001:0db8:85a3::0370:7334]");
    }

    private void testDnsRebindForValidAddress(String address) throws IOException {
        testDnsRebindForValidHost(address);
        testDnsRebindForValidHost(address + ":9229");
        testDnsRebindForValidHost(address + ":9228");
    }

    private void testDnsRebindForInvalidHost(String host) throws IOException {
        testDnsRebindForHost(host, false);
    }

    private void testDnsRebindForValidHost(String host) throws IOException {
        testDnsRebindForHost(host, true);
    }

    private void testDnsRebindForHost(String host, boolean valid) throws IOException {
        testDnsRebind(host, "/", valid);
        testDnsRebind(host, "/json", valid);
        testDnsRebind(host, "/json/version", valid);
        testDnsRebind(host, "/some-nonsense", valid);
    }

    private void testDnsRebind(String host, String path, boolean valid) throws IOException {
        testDnsRebindForHostCapitalization("host", host, path, valid);
        testDnsRebindForHostCapitalization("Host", host, path, valid);
        testDnsRebindForHostCapitalization("HoSt", host, path, valid);
        testDnsRebindForHostCapitalization("HOST", host, path, valid);
    }

    private void testDnsRebindForHostCapitalization(String hostCapitalization, String host, String path, boolean valid) throws IOException {
        // We try to connect localhost with various Host headers. If the DNS rebind protection works
        // properly, foreign domains are rejected.
        try (
                        Socket socket = new Socket("127.0.0.1", 9229);
                        OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
                        InputStream in = socket.getInputStream();) {
            // We cannot easily use HttpUrlConnection, because it does not allow overriding the Host
            // header by default. So, we use raw sockets.
            out.write("GET " + path + " HTTP/1.1\r\n" +
                            "User-Agent: Test\r\n" +
                            (host != null ? (hostCapitalization + ": " + host + "\r\n") : "") +
                            "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2\r\n" +
                            "\r\n");
            out.flush();

            final String httpStatus = readAsciiLine(in);
            final Set<String> headers = new HashSet<>();
            String line;
            int length = -1;
            while (!"".equals(line = readAsciiLine(in))) {
                final String prefix = "content-length:";
                if (line.toLowerCase().startsWith(prefix)) {
                    length = parseInt(line.substring(prefix.length()).trim());
                }
                assertNotNull(line);
                headers.add(line);
            }
            assertNotEquals(-1, length);
            final byte[] rawBody = readBytes(in, length);
            final String body = new String(rawBody, StandardCharsets.UTF_8);
            String badHost = host != null ? "Bad host " + host + ". Please use IP address." : "Missing host header. Use an up-to-date client.";
            String errorMessage = badHost + " This request cannot be served because it looks like DNS rebind attack.";
            if (valid) {
                assertNotEquals("HTTP/1.1 400 Bad Request ", httpStatus);
                assertNotEquals(errorMessage, body);
            } else {
                assertEquals("HTTP/1.1 400 Bad Request ", httpStatus);
                assertEquals(errorMessage, body);
                errorOutput.toString().endsWith(errorMessage);
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        final StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != '\n') {
            assertNotEquals(c, -1);
            sb.append((char) c);
        }
        if (sb.charAt(sb.length() - 1) == '\r') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static byte[] readBytes(InputStream in, int length) throws IOException {
        int missing = length;
        int pos = 0;
        final byte[] buffer = new byte[length];
        while (missing > 0) {
            final int read = in.read(buffer, pos, missing);
            if (read == -1) {
                throw new EOFException();
            }
            missing -= read;
            pos += read;
        }
        return buffer;
    }

}
