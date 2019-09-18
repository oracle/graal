/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test of basic datagram channel send/receive functionality.
 */
public class DatagramChannelTest {

    private static StandardProtocolFamily[] supportedProtocolValues() {
        /* Disable IPv6 testing as it is not currently supported on the CI infrastructure. */
        // return StandardProtocolFamily.values();
        return new StandardProtocolFamily[]{StandardProtocolFamily.INET};
    }

    @Test
    public void testBasicFunctions() throws IOException {
        for (StandardProtocolFamily family : supportedProtocolValues()) {
            try (DatagramChannel a = DatagramChannel.open(family)) {
                try (DatagramChannel b = DatagramChannel.open(family)) {

                    a.bind(new InetSocketAddress(0));
                    b.bind(new InetSocketAddress(0));

                    final InetSocketAddress bindAddress = (InetSocketAddress) a.getLocalAddress();
                    final InetSocketAddress senderAddress = (InetSocketAddress) b.getLocalAddress();

                    a.configureBlocking(false);
                    b.configureBlocking(false);

                    // a datagram to send
                    ByteBuffer payload = ByteBuffer.allocateDirect(100);
                    ByteBuffer comparison = ByteBuffer.allocateDirect(100);
                    for (int i = 99; i >= 0; i--) {
                        payload.put((byte) i);
                    }
                    payload.flip(); // ready to send

                    // verify no data waiting on either socket
                    Assert.assertNull("Received " + comparison.position() + " unexpected bytes", a.receive(comparison));
                    Assert.assertNull("Received " + comparison.position() + " unexpected bytes", b.receive(comparison));

                    // here's where it gets tricky: UDP is unreliable, so we need to avoid false
                    // negatives...

                    InetSocketAddress src;
                    int retry = 0;
                    do {
                        // send it from b to a
                        Assert.assertEquals(100, b.send(payload, bindAddress));
                        // expect to receive it, soon-ish
                        int innerRetry = 0;
                        do {
                            src = (InetSocketAddress) a.receive(comparison);
                        } while (src == null && ++innerRetry < 16);
                        payload.rewind();
                    } while (src == null && ++retry < 16);

                    Assert.assertNotNull(src);

                    comparison.flip();
                    Assert.assertEquals(payload, comparison);

                    Assert.assertTrue(senderAddress.getAddress().isAnyLocalAddress());
                    Assert.assertEquals(senderAddress.getPort(), src.getPort());
                }
            }
        }
    }
}
