/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedRuntimeOptions;

public class OverrideOptionsTest extends TruffleCompilerImplTest {

    @Test
    @SuppressWarnings("try")
    public void testOverrideOptionsUsingContext() {
        setupContext(Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true).option("engine.BackgroundCompilation", Boolean.FALSE.toString()).option("engine.CompileImmediately",
                        Boolean.TRUE.toString()).build());
        OptimizedCallTarget callTarget = (OptimizedCallTarget) RootNode.createConstantNode(42).getCallTarget();
        OptionValues values = callTarget.engine.getEngineOptions();
        Assert.assertEquals(false, values.get(OptimizedRuntimeOptions.BackgroundCompilation));
        Assert.assertEquals(true, values.get(OptimizedRuntimeOptions.CompileImmediately));
    }
}
