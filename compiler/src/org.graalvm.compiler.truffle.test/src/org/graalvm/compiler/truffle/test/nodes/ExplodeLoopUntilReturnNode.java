/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.IntSupplier;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.NodeInfo;

@NodeInfo
public class ExplodeLoopUntilReturnNode extends AbstractTestNode {
    static class One implements IntSupplier {
        @Override
        public int getAsInt() {
            return 42;
        }
    }

    static class Two implements IntSupplier {
        @Override
        public int getAsInt() {
            return 42;
        }
    }

    static class Three implements IntSupplier {
        @Override
        public int getAsInt() {
            return 42;
        }
    }

    @CompilationFinal(dimensions = 1) IntSupplier[] array = new IntSupplier[]{new One(), new Two(), new Three()};
    int search = 2;

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    @Override
    public int execute(VirtualFrame frame) {
        for (int i = 0; i < array.length; i++) {
            if (i == search) {
                /*
                 * The test only passes when loop explosion also explodes this block with the return
                 * value, i.e., inlines through the calls of getAsInt().
                 */
                return array[i].getAsInt();
            }
        }
        return 42;
    }
}
