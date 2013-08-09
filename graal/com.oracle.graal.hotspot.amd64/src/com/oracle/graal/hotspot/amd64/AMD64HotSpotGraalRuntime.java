/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * AMD64 specific implementation of {@link HotSpotGraalRuntime}.
 */
public class AMD64HotSpotGraalRuntime extends HotSpotGraalRuntime {

    private Value[] nativeABICallerSaveRegisters;

    protected AMD64HotSpotGraalRuntime() {
    }

    /**
     * Called from C++ code to retrieve the singleton instance, creating it first if necessary.
     */
    public static HotSpotGraalRuntime makeInstance() {
        if (graalRuntime() == null) {
            HotSpotGraalRuntimeFactory factory = findFactory("AMD64");
            if (factory != null) {
                setInstance(factory.createRuntime());
            } else {
                setInstance(new AMD64HotSpotGraalRuntime());
            }
        }
        return graalRuntime();
    }

    protected Architecture createArchitecture() {
        return new AMD64(config.useSSE, config.useAVX);
    }

    @Override
    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new TargetDescription(createArchitecture(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Override
    protected HotSpotBackend createBackend() {
        return new AMD64HotSpotBackend(getRuntime(), getTarget());
    }

    @Override
    protected HotSpotRuntime createRuntime() {
        return new AMD64HotSpotRuntime(config, this);
    }

    @Override
    protected Value[] getNativeABICallerSaveRegisters() {
        if (nativeABICallerSaveRegisters == null) {
            List<Register> callerSave = new ArrayList<>(Arrays.asList(getRuntime().lookupRegisterConfig().getAllocatableRegisters()));
            if (getConfig().windowsOs) {
                // http://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
                callerSave.remove(AMD64.rdi);
                callerSave.remove(AMD64.rsi);
                callerSave.remove(AMD64.rbx);
                callerSave.remove(AMD64.rbp);
                callerSave.remove(AMD64.rsp);
                callerSave.remove(AMD64.r12);
                callerSave.remove(AMD64.r13);
                callerSave.remove(AMD64.r14);
                callerSave.remove(AMD64.r15);
                callerSave.remove(AMD64.xmm6);
                callerSave.remove(AMD64.xmm7);
                callerSave.remove(AMD64.xmm8);
                callerSave.remove(AMD64.xmm9);
                callerSave.remove(AMD64.xmm10);
                callerSave.remove(AMD64.xmm11);
                callerSave.remove(AMD64.xmm12);
                callerSave.remove(AMD64.xmm13);
                callerSave.remove(AMD64.xmm14);
                callerSave.remove(AMD64.xmm15);
            } else {
                /*
                 * System V Application Binary Interface, AMD64 Architecture Processor Supplement
                 * 
                 * Draft Version 0.96
                 * 
                 * http://www.uclibc.org/docs/psABI-x86_64.pdf
                 * 
                 * 3.2.1
                 * 
                 * ...
                 * 
                 * This subsection discusses usage of each register. Registers %rbp, %rbx and %r12
                 * through %r15 "belong" to the calling function and the called function is required
                 * to preserve their values. In other words, a called function must preserve these
                 * registers' values for its caller. Remaining registers "belong" to the called
                 * function. If a calling function wants to preserve such a register value across a
                 * function call, it must save the value in its local stack frame.
                 */
                callerSave.remove(AMD64.rbp);
                callerSave.remove(AMD64.rbx);
                callerSave.remove(AMD64.r12);
                callerSave.remove(AMD64.r13);
                callerSave.remove(AMD64.r14);
                callerSave.remove(AMD64.r15);
            }
            nativeABICallerSaveRegisters = new Value[callerSave.size()];
            for (int i = 0; i < callerSave.size(); i++) {
                nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
            }
        }

        return nativeABICallerSaveRegisters;
    }
}
