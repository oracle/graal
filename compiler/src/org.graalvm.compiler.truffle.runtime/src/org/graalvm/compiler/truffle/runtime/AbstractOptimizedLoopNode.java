/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;

abstract class AbstractOptimizedLoopNode extends LoopNode {

    @Child protected RepeatingNode repeatingNode;

    @CompilationFinal private long trueCount; // long for long running loops.
    @CompilationFinal private int falseCount;

    protected AbstractOptimizedLoopNode(RepeatingNode repeatingNode) {
        this.repeatingNode = Objects.requireNonNull(repeatingNode);
    }

    @Override
    public final RepeatingNode getRepeatingNode() {
        return repeatingNode;
    }

    protected final void profileCounted(long iterations) {
        if (CompilerDirectives.inInterpreter()) {
            long trueCountLocal = trueCount + iterations;
            if (trueCountLocal >= 0) { // don't write overflow values
                trueCount = trueCountLocal;
                int falseCountLocal = falseCount;
                if (falseCountLocal < Integer.MAX_VALUE) {
                    falseCount = falseCountLocal + 1;
                }
            }
        }
    }

    protected final boolean inject(boolean condition) {
        if (CompilerDirectives.inCompiledCode()) {
            return CompilerDirectives.injectBranchProbability(calculateProbability(trueCount, falseCount), condition);
        } else {
            return condition;
        }
    }

    private static double calculateProbability(long trueCountLocal, int falseCountLocal) {
        if (falseCountLocal == 0 && trueCountLocal == 0) {
            // Avoid division by zero and assume default probability for AOT.
            return 0.5;
        } else {
            return (double) trueCountLocal / (double) (trueCountLocal + falseCountLocal);
        }
    }
}
