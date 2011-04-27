/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.lir;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * This class represents debugging and deoptimization information attached to a LIR instruction.
 *
 * @author Marcelo Cintra
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 */
public class LIRDebugInfo {

    public abstract static class ValueLocator {
        public abstract CiValue getLocation(Value value);
    }

    public final FrameState state;
    public final List<ExceptionHandler> exceptionHandlers;
    public CiDebugInfo debugInfo;

    public LIRDebugInfo(FrameState state, List<ExceptionHandler> exceptionHandlers) {
        assert state != null;
        this.state = state;
        this.exceptionHandlers = exceptionHandlers;
    }

    private LIRDebugInfo(LIRDebugInfo info) {
        this.state = info.state;

        // deep copy of exception handlers
        if (info.exceptionHandlers != null) {
            this.exceptionHandlers = new ArrayList<ExceptionHandler>(info.exceptionHandlers.size());
            for (ExceptionHandler h : info.exceptionHandlers) {
                this.exceptionHandlers.add(new ExceptionHandler(h));
            }
        } else {
            this.exceptionHandlers = null;
        }
    }

    public LIRDebugInfo copy() {
        return new LIRDebugInfo(this);
    }

    public void setOop(CiValue location, C1XCompilation compilation, CiBitMap frameRefMap, CiBitMap regRefMap) {
        CiTarget target = compilation.target;
        if (location.isAddress()) {
            CiAddress stackLocation = (CiAddress) location;
            assert stackLocation.index.isIllegal();
            if (stackLocation.base == CiRegister.Frame.asValue()) {
                int offset = stackLocation.displacement;
                assert offset % target.wordSize == 0 : "must be aligned";
                int stackMapIndex = offset / target.wordSize;
                setBit(frameRefMap, stackMapIndex);
            }
        } else if (location.isStackSlot()) {
            CiStackSlot stackSlot = (CiStackSlot) location;
            assert !stackSlot.inCallerFrame();
            assert target.spillSlotSize == target.wordSize;
            setBit(frameRefMap, stackSlot.index());
        } else {
            assert location.isRegister() : "objects can only be in a register";
            CiRegisterValue registerLocation = (CiRegisterValue) location;
            int reg = registerLocation.reg.number;
            assert reg >= 0 : "object cannot be in non-object register " + registerLocation.reg;
            assert reg < target.arch.registerReferenceMapBitCount;
            setBit(regRefMap, reg);
        }
    }

    public CiDebugInfo debugInfo() {
        assert debugInfo != null : "debug info not allocated yet";
        return debugInfo;
    }

    public boolean hasDebugInfo() {
        return debugInfo != null;
    }

    public static void setBit(CiBitMap refMap, int bit) {
        assert !refMap.get(bit) : "Ref map entry " + bit + " is already set.";
        refMap.set(bit);
    }

    @Override
    public String toString() {
        return state.toString();
    }
}
