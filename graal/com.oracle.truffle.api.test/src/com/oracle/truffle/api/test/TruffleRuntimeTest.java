/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test;

import org.junit.*;

import com.oracle.truffle.api.*;

/**
 * <h3>Accessing the Truffle Runtime</h3>
 * 
 * <p>
 * The Truffle runtime can be accessed at any point in time globally using the static method
 * {@link Truffle#getRuntime()}. This method is guaranteed to return a non-null Truffle runtime
 * object with an identifying name. A Java Virtual Machine implementation can chose to replace the
 * default implementation of the {@link TruffleRuntime} interface with its own implementation for
 * providing improved performance.
 * </p>
 * 
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.RootNodeTest}.
 * </p>
 */
public class TruffleRuntimeTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        Assert.assertNotNull(runtime);
        Assert.assertNotNull(runtime.getName());
    }
}
