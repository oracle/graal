/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.test;

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
 * Test of DAP socket address.
 */
public final class DAPAddressTest {

    private ByteArrayOutputStream output;

    @Before
    public void setUp() {
        output = new ByteArrayOutputStream();
    }

    @After
    public void tearDown() {
        output.reset();
    }

    @Test
    public void testHostPortEnabled() {
        try (Context context = Context.newBuilder().option("dap", "true").out(output).build()) {
            assert context != null;
        }
        String[] address = parseSocketAddress(output);
        assertAddress("127.0.0.1", "4711", address);
    }

    @Test
    public void testHostPortDisabled() {
        try (Context context = Context.newBuilder().option("dap", "false").out(output).build()) {
            assert context != null;
        }
        String out = output.toString();
        assertTrue(out, out.isEmpty());
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
        try (Context context = Context.newBuilder().option("dap", Integer.toString(p)).out(output).build()) {
            assert context != null;
            fail();
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage(), ex.getMessage().contains("Invalid port number: " + p + "."));
        }
    }

    @Test
    public void testHostPort() {
        Assume.assumeTrue(System.getProperty("os.name").contains("Linux")); // Extra IPs are used
        try (Context context = Context.newBuilder().option("dap", "127.0.0.2:0").out(output).build()) {
            assert context != null;
        }
        String[] address = parseSocketAddress(output);
        assertAddress("127.0.0.2", "?", address);
    }

    @Test
    public void testPort0() {
        try (Context context = Context.newBuilder().option("dap", "0").out(output).build()) {
            assert context != null;
        }
        String[] address = parseSocketAddress(output);
        assertAddress("127.0.0.1", "?", address);
    }

    private static String[] parseSocketAddress(ByteArrayOutputStream output) {
        String out = output.toString();
        int index = out.indexOf("[Graal DAP] Starting server and listening on ");
        assertTrue(out, index >= 0);
        int hostStartIndex = out.indexOf("/", index);
        assertTrue(out, hostStartIndex > 0);
        hostStartIndex++;
        int portStartIndex = out.indexOf(":", hostStartIndex);
        assertTrue(out, portStartIndex > 0);
        String host = out.substring(hostStartIndex, portStartIndex);
        portStartIndex++;
        int portEndIndex = portStartIndex;
        while (portEndIndex < out.length() && !Character.isWhitespace(out.charAt(portEndIndex))) {
            portEndIndex++;
        }
        assertTrue(out, portEndIndex > 0);
        String port = out.substring(portStartIndex, portEndIndex);
        return new String[]{host, port};
    }

    private static void assertAddress(String host, String port, String[] address) {
        assertEquals(host, address[0]);
        if (!"?".equals(port)) {
            assertEquals(port, address[1]);
        } else {
            int portNumber = Integer.parseInt(address[1]);
            assertTrue(port, 1024 <= portNumber && portNumber < 65536);
        }
    }
}
