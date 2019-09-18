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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;

public class NetworkInterfaceTest {

    @Test
    public void testLoopback() throws SocketException {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        boolean foundLoopback = false;
        while (ifaces.hasMoreElements()) {
            NetworkInterface each = ifaces.nextElement();
            foundLoopback = each.isLoopback() || foundLoopback;
        }

        Assert.assertTrue("At least one loopback found", foundLoopback);
    }

    @Test
    public void testIsUp() throws SocketException {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        boolean somethingIsUp = false;
        while (ifaces.hasMoreElements()) {
            NetworkInterface each = ifaces.nextElement();
            somethingIsUp = somethingIsUp || each.isUp();
        }
        Assert.assertTrue("At least one interface is up", somethingIsUp);
    }

    @Test
    public void testIsP2P() throws SocketException {
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        boolean somethingIsP2p = false;
        while (ifaces.hasMoreElements()) {
            NetworkInterface each = ifaces.nextElement();
            somethingIsP2p = somethingIsP2p || each.isPointToPoint();
        }
    }

}
