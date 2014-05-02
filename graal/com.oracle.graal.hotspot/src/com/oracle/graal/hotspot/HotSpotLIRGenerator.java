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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.gen.*;

/**
 * This interface defines the contract a HotSpot backend LIR generator needs to fulfill in addition
 * to abstract methods from {@link LIRGenerator} and {@link LIRGeneratorTool}.
 */
public interface HotSpotLIRGenerator extends LIRGeneratorTool {

    /**
     * Emits an operation to make a tail call.
     *
     * @param args the arguments of the call
     * @param address the target address of the call
     */
    void emitTailcall(Value[] args, Value address);

    void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason);

    /**
     * Emits code for a {@link SaveAllRegistersNode}.
     *
     * @return a {@link SaveRegistersOp} operation
     */
    SaveRegistersOp emitSaveAllRegisters();

    /**
     * Emits code for a {@link LeaveCurrentStackFrameNode}.
     *
     * @param saveRegisterOp saved registers
     */
    default void emitLeaveCurrentStackFrame(SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link LeaveDeoptimizedStackFrameNode}.
     *
     * @param frameSize
     * @param initialInfo
     */
    default void emitLeaveDeoptimizedStackFrame(Value frameSize, Value initialInfo) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link EnterUnpackFramesStackFrameNode}.
     *
     * @param framePc
     * @param senderSp
     * @param senderFp
     * @param saveRegisterOp
     */
    default void emitEnterUnpackFramesStackFrame(Value framePc, Value senderSp, Value senderFp, SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link LeaveUnpackFramesStackFrameNode}.
     *
     * @param saveRegisterOp
     */
    default void emitLeaveUnpackFramesStackFrame(SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link PushInterpreterFrameNode}.
     *
     * @param frameSize
     * @param framePc
     * @param senderSp
     * @param initialInfo
     */
    default void emitPushInterpreterFrame(Value frameSize, Value framePc, Value senderSp, Value initialInfo) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link UncommonTrapCallNode}.
     *
     * @param trapRequest
     * @param saveRegisterOp
     * @return a {@code Deoptimization::UnrollBlock} pointer
     */
    default Value emitUncommonTrapCall(Value trapRequest, SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits code for a {@link DeoptimizationFetchUnrollInfoCallNode}.
     *
     * @param saveRegisterOp
     * @return a {@code Deoptimization::UnrollBlock} pointer
     */
    default Value emitDeoptimizationFetchUnrollInfoCall(SaveRegistersOp saveRegisterOp) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Gets a stack slot for a lock at a given lock nesting depth.
     */
    StackSlot getLockSlot(int lockDepth);

    HotSpotProviders getProviders();

    Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull);

    Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull);
}
