/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import org.graalvm.polyglot.Context;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Test of inspector websocket address.
 */
public class InspectorAddressTest {

    private Context context;
    private ByteArrayOutputStream errorOutput;

    @Before
    public void setUp() {
        errorOutput = new ByteArrayOutputStream();
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
    public void testHostPortDefault() {
        context = Context.newBuilder().option("inspect", "").err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.1", "9229", "?", wsAddress);
    }

    @Test
    public void testHost() {
        Assume.assumeTrue(System.getProperty("os.name").contains("Linux")); // Extra IPs are used
        context = Context.newBuilder().option("inspect", "127.0.0.2").err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.2", "9229", "?", wsAddress);
    }

    @Test
    public void testPort() {
        context = Context.newBuilder().option("inspect", "2992").err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.1", "2992", "?", wsAddress);
    }

    @Test
    public void testBadPorts() {
        // Negative
        assertBadPort(-2);
        // Too small
        for (int p = 1; p < 1024; p += 1022) {
            assertBadPort(p);
        }
        // Too big
        assertBadPort(65536);
    }

    private void assertBadPort(int p) {
        try {
            Context.newBuilder().option("inspect", Integer.toString(p)).err(errorOutput).build();
            fail();
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("Invalid port number: " + p + "."));
        }
    }

    @Test
    public void testHostPort() {
        Assume.assumeTrue(System.getProperty("os.name").contains("Linux")); // Extra IPs are used
        context = Context.newBuilder().option("inspect", "127.0.0.2:2992").err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.2", "2992", "?", wsAddress);
    }

    @Test
    public void testPort0() {
        context = Context.newBuilder().option("inspect", "0").err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.1", "?", "?", wsAddress);
    }

    @Test
    public void testPath() {
        final String testPath = "testPath-" + SecureInspectorPathGenerator.getToken();
        context = Context.newBuilder().option("inspect.Path", testPath).err(errorOutput).build();
        String[] wsAddress = parseWSAddress(errorOutput.toString());
        assertAddress("127.0.0.1", "9229", "/" + testPath, wsAddress);
    }

    private static String[] parseWSAddress(String out) {
        int wsIndex = out.indexOf("ws=");
        assertTrue(out, wsIndex > 0);
        int portStartIndex = out.indexOf(":", wsIndex);
        String host = out.substring(wsIndex + 3, portStartIndex);
        assertTrue(out, portStartIndex > 0);
        portStartIndex++;
        int portEndIndex = out.indexOf("/", portStartIndex);
        assertTrue(out, portEndIndex > 0);
        String port = out.substring(portStartIndex, portEndIndex);
        int pathEnd = portEndIndex;
        while (pathEnd < out.length() && !Character.isWhitespace(out.charAt(pathEnd))) {
            pathEnd++;
        }
        String path = out.substring(portEndIndex, pathEnd);
        return new String[]{host, port, path};
    }

    private static void assertAddress(String host, String port, String path, String[] wsAddress) {
        assertEquals(host, wsAddress[0]);
        if (!"?".equals(port)) {
            assertEquals(port, wsAddress[1]);
        } else {
            int portNumber = Integer.parseInt(wsAddress[1]);
            assertTrue(port, 1024 <= portNumber && portNumber < 65536);
        }
        if (!"?".equals(path)) {
            assertEquals(path, wsAddress[2]);
        }
    }

}
