/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code IRScope} class represents an inlining context in the compilation
 * of a method.
 *
 * @author Ben L. Titzer
 */
public class IRScope {

    public final IRScope caller;
    public final RiMethod method;
    public final int level;
    CiCodePos callerCodePos;

    /**
     * The frame state at the call site of this scope's caller or {@code null}
     * if this is not a nested scope.
     */
    public final FrameState callerState;

    /**
     * The maximum number of locks held in this scope at any one time
     * (c.f. maxStack and maxLocals of the Code attribute in a class file).
     */
    int maxLocks;

    CiBitMap storesInLoops;

    public IRScope(IRScope caller, FrameState callerState, RiMethod method, int osrBCI) {
        this.caller = caller;
        this.callerState = callerState;
        this.method = method;
        this.level = caller == null ? 0 : 1 + caller.level;
    }

    /**
     * Updates the maximum number of locks held in this scope at any one time.
     *
     * @param locks a lock count that will replace the current {@linkplain #maxLocks() max locks} for this scope if it is greater
     */
    public void updateMaxLocks(int locks) {
        if (locks > maxLocks) {
            maxLocks = locks;
        }
    }

    /**
     * Gets the number of locks in this IR scope.
     * @return the number of locks
     */
    public final int maxLocks() {
        return maxLocks;
    }

    /**
     * Gets the bytecode index of the call site that called this method.
     * @return the call site's bytecode index
     */
    public final int callerBCI() {
        return callerState == null ? -1 : callerState.bci;
    }

    /**
     * Returns whether this IR scope is the top scope (i.e. has no caller).
     * @return {@code true} if this inlining scope has no parent
     */
    public final boolean isTopScope() {
        return caller == null;
    }

    /**
     * Gets the phi bitmap for this IR scope. The phi bitmap stores
     * whether a phi instruction is required for each local variable.
     * @return the phi bitmap for this IR scope
     */
    public final CiBitMap getStoresInLoops() {
        return storesInLoops;
    }

    public final void setStoresInLoops(CiBitMap storesInLoops) {
        this.storesInLoops = storesInLoops;
    }

    @Override
    public String toString() {
        if (caller == null) {
            return "root-scope: " + method;
        } else {
            return "inlined-scope: " + method + " [caller bci: " + callerState.bci + "]";
        }
    }

    public CiCodePos callerCodePos() {
        if (caller != null && callerCodePos == null) {
            callerCodePos = caller.toCodePos(callerBCI());
        }
        return callerCodePos;
    }

    public CiCodePos toCodePos(int bci) {
        return new CiCodePos(callerCodePos(), method, bci);
    }
}
