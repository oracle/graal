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

import java.net.Socket;
import java.net.SocketException;

import org.junit.Assert;
import org.junit.Test;

public class GitHub1087 {

    /**
     * Inspired by the reproducer in https://github.com/oracle/graal/issues/1087.
     *
     * This used to fail with
     *
     * <pre>
     * Exception in thread "main" java.lang.ClassCastException: java.lang.Boolean cannot be cast to
     *   java.lang.Integer at java.net.PlainSocketImpl.socketSetOption(PlainSocketImpl.java:5640) at
     *   java.net.AbstractPlainSocketImpl.setOption(AbstractPlainSocketImpl.java:275) at
     *   java.net.Socket.setSoLinger(Socket.java:1018)
     * </pre>
     */
    @Test
    public void testSoLinger() {
        try {
            final Socket socket = new Socket();
            socket.setSoLinger(false, 42); // (with any value of i)
        } catch (SocketException se) {
            Assert.fail("Caught: " + se);
        }
    }

}
