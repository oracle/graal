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
package com.oracle.graal.hotspot;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.ValueProcedure;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.word.*;

/**
 * HotSpot specific backend.
 */
public abstract class HotSpotBackend extends Backend {

    /**
     * Descriptor for SharedRuntime::deopt_blob()->uncommon_trap().
     */
    public static final ForeignCallDescriptor UNCOMMON_TRAP = new ForeignCallDescriptor("deoptimize", void.class);

    /**
     * Descriptor for {@link ExceptionHandlerStub}. This stub is called by the
     * {@linkplain HotSpotVMConfig#codeInstallerMarkIdExceptionHandlerEntry exception handler} in a
     * compiled method.
     */
    public static final ForeignCallDescriptor EXCEPTION_HANDLER = new ForeignCallDescriptor("exceptionHandler", void.class, Object.class, Word.class);

    /**
     * Descriptor for SharedRuntime::deopt_blob()->unpack().
     */
    public static final ForeignCallDescriptor DEOPT_HANDLER = new ForeignCallDescriptor("deoptHandler", void.class);

    /**
     * Descriptor for SharedRuntime::get_ic_miss_stub().
     */
    public static final ForeignCallDescriptor IC_MISS_HANDLER = new ForeignCallDescriptor("icMissHandler", void.class);

    /**
     * Descriptor for {@link UnwindExceptionToCallerStub}. This stub is called by code generated
     * from {@link UnwindNode}.
     */
    public static final ForeignCallDescriptor UNWIND_EXCEPTION_TO_CALLER = new ForeignCallDescriptor("unwindExceptionToCaller", void.class, Object.class, Word.class);

    /**
     * Descriptor for the arguments when unwinding to an exception handler in a caller.
     */
    public static final ForeignCallDescriptor EXCEPTION_HANDLER_IN_CALLER = new ForeignCallDescriptor("exceptionHandlerInCaller", void.class, Object.class, Word.class);

    private final HotSpotGraalRuntime runtime;

    public HotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(providers);
        this.runtime = runtime;
    }

    public HotSpotGraalRuntime getRuntime() {
        return runtime;
    }

    /**
     * Performs any remaining initialization that was deferred until the {@linkplain #getRuntime()
     * runtime} object was initialized and this backend was registered with it.
     */
    public void completeInitialization() {
    }

    /**
     * Finds all the registers that are defined by some given LIR.
     * 
     * @param lir the LIR to examine
     * @return the registers that are defined by or used as temps for any instruction in {@code lir}
     */
    protected static Set<Register> gatherDefinedRegisters(LIR lir) {
        final Set<Register> definedRegisters = new HashSet<>();
        ValueProcedure defProc = new ValueProcedure() {

            @Override
            public Value doValue(Value value) {
                if (ValueUtil.isRegister(value)) {
                    final Register reg = ValueUtil.asRegister(value);
                    definedRegisters.add(reg);
                }
                return value;
            }
        };
        for (Block block : lir.codeEmittingOrder()) {
            for (LIRInstruction op : lir.lir(block)) {
                if (op instanceof LabelOp) {
                    // Don't consider this as a definition
                } else {
                    op.forEachTemp(defProc);
                    op.forEachOutput(defProc);
                }
            }
        }
        return definedRegisters;
    }

    /**
     * Updates a given stub with respect to the registers it destroys.
     * <p>
     * Any entry in {@code calleeSaveInfo} that {@linkplain SaveRegistersOp#supportsRemove()
     * supports} pruning will have {@code destroyedRegisters}
     * {@linkplain SaveRegistersOp#remove(Set) removed} as these registers are declared as
     * temporaries in the stub's {@linkplain ForeignCallLinkage linkage} (and thus will be saved by
     * the stub's caller).
     * 
     * @param stub the stub to update
     * @param destroyedRegisters the registers destroyed by the stub
     * @param calleeSaveInfo a map from debug infos to the operations that provide their
     *            {@linkplain RegisterSaveLayout callee-save information}
     * @param frameMap used to {@linkplain FrameMap#indexForStackSlot(StackSlot) convert} a virtual
     *            slot to a frame slot index
     */
    protected void updateStub(Stub stub, Set<Register> destroyedRegisters, Map<LIRFrameState, SaveRegistersOp> calleeSaveInfo, FrameMap frameMap) {
        stub.initDestroyedRegisters(destroyedRegisters);

        for (Map.Entry<LIRFrameState, SaveRegistersOp> e : calleeSaveInfo.entrySet()) {
            SaveRegistersOp save = e.getValue();
            if (save.supportsRemove()) {
                save.remove(destroyedRegisters);
            }
            DebugInfo info = e.getKey() == null ? null : e.getKey().debugInfo();
            if (info != null) {
                info.setCalleeSaveInfo(save.getMap(frameMap));
            }
        }
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    @Override
    public SuitesProvider getSuites() {
        return getProviders().getSuites();
    }

    @Override
    public DisassemblerProvider getDisassembler() {
        return getProviders().getDisassembler();
    }
}
