/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.alloc.util;

import static com.sun.cri.ci.CiUtil.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.lir.*;
import com.sun.cri.ci.*;


public class SpillSlots {
    public final GraalContext context;
    public final FrameMap frameMap;

    /**
     * Number of stack slots used for intervals allocated to memory.
     */
    private int maxSpills;

    /**
     * Unused spill slot for a single-word value because of alignment of a double-word value.
     */
    private CiStackSlot unusedSpillSlot;

    public SpillSlots(GraalContext context, FrameMap frameMap) {
        this.context = context;
        this.frameMap = frameMap;
        this.maxSpills = frameMap.initialSpillSlot();
        this.unusedSpillSlot = null;

        assert maxSpills >= 0;
    }

    private int numberOfSpillSlots(CiKind kind) {
        return frameMap.target.spillSlots(kind);
    }

    /**
     * Allocates the next available spill slot for a value of a given kind.
     */
    public CiStackSlot allocateSpillSlot(CiKind kind) {
        assert maxSpills >= 0 : "cannot allocate new spill slots after finish() has been called";

        CiStackSlot spillSlot;
        if (numberOfSpillSlots(kind) == 2) {
            if (isOdd(maxSpills)) {
                // alignment of double-slot values
                // the hole because of the alignment is filled with the next single-slot value
                assert unusedSpillSlot == null : "wasting a spill slot";
                unusedSpillSlot = CiStackSlot.get(kind, maxSpills);
                maxSpills++;
            }
            spillSlot = CiStackSlot.get(kind, maxSpills);
            maxSpills += 2;
        } else if (unusedSpillSlot != null) {
            // re-use hole that was the result of a previous double-word alignment
            spillSlot = unusedSpillSlot;
            unusedSpillSlot = null;
        } else {
            spillSlot = CiStackSlot.get(kind, maxSpills);
            maxSpills++;
        }

        return spillSlot;
    }

    public void finish() {
        if (GraalOptions.Meter) {
            context.metrics.LSRASpills += (maxSpills - frameMap.initialSpillSlot());
        }

        // fill in number of spill slots into frameMap
        frameMap.finalizeFrame(maxSpills);
        // Mark this object as finished
        maxSpills = -1;
    }
}
