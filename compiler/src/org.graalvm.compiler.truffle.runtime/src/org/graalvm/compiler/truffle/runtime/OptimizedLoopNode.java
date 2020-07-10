/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RepeatingNode;

public final class OptimizedLoopNode extends LoopNode {

    @Child private RepeatingNode repeatingNode;

    OptimizedLoopNode(RepeatingNode repeatingNode) {
        this.repeatingNode = repeatingNode;
    }

    @Override
    public RepeatingNode getRepeatingNode() {
        return repeatingNode;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void executeLoop(VirtualFrame frame) {
        execute(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object status;
        int loopCount = 0;
        try {
            while (repeatingNode.shouldContinue(status = repeatingNode.executeRepeatingWithValue(frame))) {
                if (CompilerDirectives.inInterpreter()) {
                    loopCount++;
                }
            }
            return status;
        } finally {
            if (CompilerDirectives.inInterpreter()) {
                reportLoopCount(this, loopCount);
            }
        }
    }

    static LoopNode create(RepeatingNode repeatingNode) {
        return new OptimizedLoopNode(repeatingNode);
    }

}
