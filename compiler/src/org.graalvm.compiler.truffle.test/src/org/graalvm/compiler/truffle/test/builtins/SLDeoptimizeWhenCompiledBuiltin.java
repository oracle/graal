/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLNull;

/**
 * Forces a deoptimization as soon as the method runs in compiled code.
 */
@NodeInfo(shortName = "deoptimizeWhenCompiled")
public abstract class SLDeoptimizeWhenCompiledBuiltin extends SLGraalRuntimeBuiltin {

    @Specialization
    public SLNull deoptimzeWhenCompiled(boolean condition) {
        if (CompilerDirectives.inCompiledCode()) {
            if (condition) {
                printMessage();
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
        }
        return SLNull.SINGLETON;
    }

    @TruffleBoundary
    private static void printMessage() {
        SLLanguage.getCurrentContext().getOutput().println("[deoptimizeWhenCompiled]");
    }
}
