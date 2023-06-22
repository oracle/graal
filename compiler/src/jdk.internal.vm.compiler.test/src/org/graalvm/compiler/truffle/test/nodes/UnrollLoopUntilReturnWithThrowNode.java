/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo
public class UnrollLoopUntilReturnWithThrowNode extends AbstractTestNode {
    int search = 2;
    @CompilationFinal int zero = 0;

    public UnrollLoopUntilReturnWithThrowNode() {
        try {
            /*
             * Execute code so that all classes are loaded, since execute() does not actually call
             * this method due to the 0-iteration loop.
             */
            doSearch();
        } catch (Throwable ex) {
        }
    }

    @Override
    public int execute(VirtualFrame frame) {
        for (int i = 0; i < zero; i++) {
            /*
             * Loop unrolling will remove the code later on, but the partial evaluator still
             * processes it.
             */
            doSearch();
        }
        return 42;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    private void doSearch() {
        for (int i = 0; i < 3; i++) {
            if (i == search) {
                /*
                 * Test that the partial evaluator can handle exception throws in
                 * FULL_EXPLODE_UNTIL_RETURN loops.
                 */
                throw new ControlFlowException();
            }
        }
    }
}
