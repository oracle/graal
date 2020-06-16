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
package org.graalvm.compiler.truffle.test.nodes.explosion;

import org.graalvm.compiler.truffle.test.nodes.AbstractTestNode;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo
public class LoopExplosionPhiNode extends AbstractTestNode {

    @CompilationFinal int iterations = 2;
    int x = 1;

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    @Override
    public int execute(VirtualFrame frame) {
        int result = -1;
        if (x > 100) {
            /*
             * First value for result, registered before processing the loop: a possible value that
             * is registered for the phi function during decoding.
             */
            result = 42;
        } else {
            for (int i = 0; i < iterations; i++) {
                if (i == x) {
                    /*
                     * More values for result. But since we are exploding until the return, no merge
                     * and no phi function will be created. The first value for the result
                     * registered before processing the loop needs to be ignored.
                     */
                    result = i;
                    break;
                }
            }
        }

        /*
         * Ensure that the canonicalizer does not eliminate the merge by tail-duplicating the return
         * immediately after bytecode parsing.
         */
        nonInlinedCall();

        return result;
    }

    @TruffleBoundary
    static void nonInlinedCall() {
    }
}
