/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.phases;

import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.LabeledBlockGeneration;
import com.oracle.svm.hosted.webimage.codegen.reconstruction.stackifier.StackifierData;

import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * WASM-specific logic for generating labeled blocks.
 */
public class WasmLabeledBlockGeneration extends LabeledBlockGeneration {
    public WasmLabeledBlockGeneration(StackifierData stackifierData, ControlFlowGraph cfg) {
        super(stackifierData, cfg);
    }

    @Override
    public boolean isLabeledBlockNeeded(HIRBlock block, HIRBlock successor) {
        if (super.isLabeledBlockNeeded(block, successor)) {
            return true;
        }

        if (isNormalLoopExit(block, successor, stackifierData)) {
            /*
             * In WASM, loop exits need a labeled block since the break instruction acts as a loop
             * continue.
             */
            return true;
        }

        if (!WebImageWasmOptions.LegacyExceptions.getValue() && block.getEndNode() instanceof WithExceptionNode withExceptionNode) {
            HIRBlock normSucc = stackifierData.getCfg().blockFor(withExceptionNode.next());
            if (normSucc.equals(successor)) {
                /*
                 * With the new exception handling, we need an explicit labeled block when going
                 * from the WithExceptionNode to its regular successor because in the Wasm code, the
                 * successor does not appear directly after, the catch block does, and we would then
                 * fall through to that.
                 */
                return true;
            }
        }

        return false;
    }
}
