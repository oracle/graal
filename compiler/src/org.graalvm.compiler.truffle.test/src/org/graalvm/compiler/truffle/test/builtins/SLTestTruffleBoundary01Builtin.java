/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.builtins;

import java.util.concurrent.Callable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Just used in TestTruffleBoundary01.sl. Verifies that all intrinsics have no effect inside of
 * a @TruffleBoundary annotated method.
 */
@NodeInfo(shortName = "testTruffleBoundary01")
public abstract class SLTestTruffleBoundary01Builtin extends SLGraalRuntimeBuiltin {

    private static Object nonConstantValue = new Object();

    @Specialization
    @TruffleBoundary
    public Object testTruffleBoundary() {
        CompilerAsserts.neverPartOfCompilation();
        CompilerAsserts.neverPartOfCompilation("Should never throw an exception when compiling.");
        CompilerAsserts.compilationConstant(nonConstantValue);
        CompilerDirectives.transferToInterpreter();
        CompilerDirectives.transferToInterpreterAndInvalidate();
        CompilerDirectives.bailout("Should not fail");
        if (CompilerDirectives.inCompiledCode()) {
            throw new AssertionError();
        }
        if (!CompilerDirectives.inInterpreter()) {
            throw new AssertionError();
        }
        try {
            int result = (int) CompilerDirectives.interpreterOnly(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return 1;
                }
            });
            if (result != 1) {
                throw new AssertionError();
            }
        } catch (Exception e) {
            throw new AssertionError();
        }

        return SLNull.SINGLETON;
    }
}
