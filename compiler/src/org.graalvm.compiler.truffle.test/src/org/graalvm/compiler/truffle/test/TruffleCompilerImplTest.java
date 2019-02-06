/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.junit.Assume;

public abstract class TruffleCompilerImplTest extends GraalCompilerTest {

    protected final TruffleCompilerImpl truffleCompiler;

    protected TruffleCompilerImplTest() {
        GraalTruffleRuntime runtime = GraalTruffleRuntime.getRuntime();
        Assume.assumeTrue("cannot get whitebox interface to Truffle compiler", runtime.getTruffleCompiler() instanceof TruffleCompilerImpl);
        beforeInitialization();
        TruffleCompiler compiler = runtime.newTruffleCompiler();
        this.truffleCompiler = (TruffleCompilerImpl) compiler;
    }

    /**
     * Executed before initialization. This hook can be used to override specific flags.
     */
    protected void beforeInitialization() {
    }
}
