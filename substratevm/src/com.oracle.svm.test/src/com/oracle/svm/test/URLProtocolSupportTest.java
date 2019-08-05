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

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

public class URLProtocolSupportTest {

    @Test
    public void testEnabledProtocol() throws MalformedURLException {
        URL url = new URL("file:///");
        Assert.assertNotNull(url);
    }

    @Test
    @SuppressWarnings("unused")
    public void testNonEnabledProtocol() {
        try {
            URL url = new URL("http://redhat.com/");
            Assert.fail("Should have thrown a MalformedURLException");
        } catch (MalformedURLException e) {
            Assert.assertTrue(e.getMessage().contains("The URL protocol http is supported but not enabled by default."));
        } catch (Throwable t) {
            // anything else is an error
            Assert.fail("Expected a MalformedURLException");
        }
    }

    @Test
    @SuppressWarnings("unused")
    public void testUntestedProtocol() {
        try {
            URL url = new URL("tacos://redhat.com/");
            Assert.fail("Should have thrown a MalformedURLException");
        } catch (MalformedURLException e) {
            Assert.assertTrue(e.getMessage().contains("The URL protocol tacos is not tested"));
        } catch (Throwable t) {
            // anything else is an error
            Assert.fail("Expected a MalformedURLException");
        }
    }
}
