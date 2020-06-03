/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.GCUtils;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CompilationMemoryTest extends TestWithPolyglotOptions {

    @Before
    public void setUp() {
        setupContext("engine.CompileImmediately", "true", "engine.BackgroundCompilation", "false");
    }

    @Test
    public void testFieldsFreedAfterCompilation() {
        TestObject expected = new TestObject();
        OptimizedCallTarget callTarget = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(expected));
        Assert.assertEquals(expected, callTarget.call());
        Assert.assertEquals(expected, callTarget.call());
        Assert.assertTrue(callTarget.isValid());
        Reference<?> ref = new WeakReference<>(expected);
        expected = null;
        callTarget = null;
        GCUtils.assertGc("JavaConstant for TestObject should be freed after compilation.", ref);
    }

    private static final class TestObject implements TruffleObject {
    }
}
