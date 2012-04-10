/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.loop;

import java.util.*;

import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.util.*;


public class LoopTransformUtil {

    public static void peel(Loop loop) {
        GraphUtil.normalizeLoopBegin(loop.loopBegin());
        SuperBlock block = wholeLoop(loop);
        SuperBlock peel = block.duplicate(); // duplicates the nodes, merges early exits

        peel.insertBefore(loop.loopBegin().forwardEnd()); // connects peeled part's CFG

        LoopTransformDataResolver resolver = new LoopTransformDataResolver();
        resolver.wholeLoop(block).peeled(peel); // block (comming from the loop) was peeled into peel
        resolver.resolve();

        peel.finish();
    }

    public static SuperBlock wholeLoop(Loop loop) {
        List<BeginNode> blocks = new LinkedList<>();
        List<BeginNode> earlyExits = new LinkedList<>();
        for (Block b : loop.blocks) {
            blocks.add(b.getBeginNode());
        }
        for (Block b : loop.exits) {
            earlyExits.add(b.getBeginNode());
        }
        return new SuperBlock(loop.loopBegin(), loop.loopBegin(), blocks, earlyExits, loop.loopBegin());
    }

    public static int estimateSize(Loop loop) {
        int fixed = 0;
        for (Block b : loop.blocks) {
            fixed += b.getBeginNode().getBlockNodes().count();
        }
        return fixed * 3;
    }
}
