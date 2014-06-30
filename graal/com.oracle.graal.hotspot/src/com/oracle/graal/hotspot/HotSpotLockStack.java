/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;

/**
 * Manages allocation and re-use of lock slots in a scoped manner. The slots are used in HotSpot's
 * lightweight locking mechanism to store the mark word of an object being locked.
 */
public class HotSpotLockStack {

    private StackSlot[] locks;
    private final FrameMap frameMap;
    private final LIRKind slotKind;

    public HotSpotLockStack(FrameMap frameMap, LIRKind slotKind) {
        this.frameMap = frameMap;
        this.slotKind = slotKind;
    }

    /**
     * Gets a stack slot for a lock at a given lock nesting depth, allocating it first if necessary.
     */
    public StackSlot makeLockSlot(int lockDepth) {
        if (locks == null) {
            locks = new StackSlot[lockDepth + 1];
        } else if (locks.length < lockDepth + 1) {
            locks = Arrays.copyOf(locks, lockDepth + 1);
        }
        if (locks[lockDepth] == null) {
            locks[lockDepth] = frameMap.allocateSpillSlot(slotKind);
        }
        return locks[lockDepth];
    }
}
