/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

/** Tests of {@link InetAddress}. */
public class TestInetAddress {

    /**
     * Test that {@link InetAddress#getByName(String)} works for a host that exists, here, the
     * loopback host, represented by the null argument.
     */
    @Test
    public void testGetByNameExistingHost() {
        boolean found;
        try {
            @SuppressWarnings({"unused"})
            final InetAddress byName = InetAddress.getByName(null);
            found = true;
        } catch (UnknownHostException uhe) {
            found = false;
        }
        Assert.assertTrue("Should have found an InetAddress", found);
    }

    /**
     * Test that {@link InetAddress#getByName(String)} throws an exception for a host that does not
     * exist.
     *
     * This used to segfault, as reported by https://github.com/oracle/graal/issues/657.
     */
    @Test
    public void testGetByNameNonExistingHost() {
        boolean found;
        try {
            @SuppressWarnings({"unused"})
            final InetAddress byName = InetAddress.getByName("oopsydaisyidontexist");
            found = true;
        } catch (UnknownHostException uhe) {
            found = false;
        }
        Assert.assertFalse("Should not have found an InetAddress", found);
    }
}
