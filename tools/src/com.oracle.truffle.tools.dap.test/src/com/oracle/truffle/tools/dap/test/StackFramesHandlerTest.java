/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.tools.dap.server.StackFramesHandler;

public class StackFramesHandlerTest {

    @Test
    public void topScopeDapNameNullAndBlankFallbackToGlobal() {
        Assert.assertEquals(StackFramesHandler.DEFAULT_TOP_SCOPE_NAME, StackFramesHandler.topScopeDapName((String) null));
        Assert.assertEquals(StackFramesHandler.DEFAULT_TOP_SCOPE_NAME, StackFramesHandler.topScopeDapName(""));
        Assert.assertEquals(StackFramesHandler.DEFAULT_TOP_SCOPE_NAME, StackFramesHandler.topScopeDapName(" \t\r\n "));
    }

    @Test
    public void topScopeDapNameTrimsWhitespace() {
        Assert.assertEquals("Module", StackFramesHandler.topScopeDapName("  Module  "));
        Assert.assertEquals("x", StackFramesHandler.topScopeDapName("\nx\t"));
    }

    @Test
    public void topScopeDapNamePreservesGuestString() {
        Assert.assertEquals("global", StackFramesHandler.topScopeDapName("global"));
        Assert.assertEquals("DAP-Custom", StackFramesHandler.topScopeDapName("DAP-Custom"));
    }
}
