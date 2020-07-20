/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;

@NodeInfo
public class TwoMergesExplodedLoopTestNode extends AbstractTestNode {

    static class Flag {
        boolean flag = true;
    }

    private final int count;

    public TwoMergesExplodedLoopTestNode(int count) {
        this.count = count;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE)
    @Override
    public int execute(VirtualFrame frame) {
        Flag flag = new Flag();
        int result = 0;
        int i = 0;
        while (i < count) {
            i++;

            CompilerAsserts.partialEvaluationConstant(result);

            if (flag.flag) {
                result++;
                continue;
            } else {
                result--;
                continue;
            }
        }
        return result;
    }
}
