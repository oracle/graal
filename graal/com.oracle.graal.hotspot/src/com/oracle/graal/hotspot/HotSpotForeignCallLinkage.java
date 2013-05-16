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

import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.word.*;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public class HotSpotForeignCallLinkage implements ForeignCallLinkage, InvokeTarget {

    /**
     * Constants for specifying whether a foreign call destroys or preserves registers. A foreign
     * call will always destroy {@link HotSpotForeignCallLinkage#getCallingConvention() its}
     * {@linkplain ForeignCallLinkage#getTemporaries() temporary} registers.
     */
    public enum RegisterEffect {
        DESTROYS_REGISTERS, PRESERVES_REGISTERS
    }

    /**
     * Constants for specifying whether a call is a leaf or not. A leaf function does not lock, GC
     * or throw exceptions. That is, the thread's execution state during the call is never inspected
     * by another thread.
     */
    public enum Transition {
        LEAF, NOT_LEAF;
    }

    /**
     * Sentinel marker for a computed jump address.
     */
    public static final long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    /**
     * The descriptor of the call.
     */
    private final ForeignCallDescriptor descriptor;

    /**
     * The entry point address of this call's target.
     */
    private long address;

    /**
     * Non-null (eventually) iff this is a call to a compiled {@linkplain Stub stub}.
     */
    private Stub stub;

    /**
     * The calling convention for this call.
     */
    private final CallingConvention cc;

    private final RegisterEffect effect;

    private final Transition transition;

    /**
     * The locations defined/killed by the call.
     */
    private Value[] temporaries = AllocatableValue.NONE;

    /**
     * Creates a {@link HotSpotForeignCallLinkage}.
     * 
     * @param descriptor the descriptor of the call
     * @param address the address of the code to call
     * @param effect specifies if the call destroys or preserves all registers (apart from
     *            temporaries which are always destroyed)
     * @param ccType calling convention type
     * @param transition specifies if this is a {@linkplain #isLeaf() leaf} call
     */
    public static HotSpotForeignCallLinkage create(ForeignCallDescriptor descriptor, long address, RegisterEffect effect, Type ccType, Transition transition) {
        CallingConvention targetCc = createCallingConvention(descriptor, ccType);
        return new HotSpotForeignCallLinkage(descriptor, address, effect, transition, targetCc);
    }

    /**
     * Gets a calling convention for a given descriptor and call type.
     */
    public static CallingConvention createCallingConvention(ForeignCallDescriptor descriptor, Type ccType) {
        HotSpotRuntime runtime = graalRuntime().getRuntime();
        Class<?>[] argumentTypes = descriptor.getArgumentTypes();
        JavaType[] parameterTypes = new JavaType[argumentTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = asJavaType(argumentTypes[i], runtime);
        }
        TargetDescription target = graalRuntime().getTarget();
        JavaType returnType = asJavaType(descriptor.getResultType(), runtime);
        return runtime.lookupRegisterConfig().getCallingConvention(ccType, returnType, parameterTypes, target, false);
    }

    private static JavaType asJavaType(Class type, HotSpotRuntime runtime) {
        if (WordBase.class.isAssignableFrom(type)) {
            return runtime.lookupJavaType(wordKind().toJavaClass());
        } else {
            return runtime.lookupJavaType(type);
        }
    }

    public HotSpotForeignCallLinkage(ForeignCallDescriptor descriptor, long address, RegisterEffect effect, Transition transition, CallingConvention cc) {
        this.address = address;
        this.effect = effect;
        this.transition = transition;
        this.descriptor = descriptor;
        this.cc = cc;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(stub == null ? descriptor.toString() : stub.toString());
        sb.append("@0x").append(Long.toHexString(address)).append(':').append(cc);
        if (temporaries != null && temporaries.length != 0) {
            sb.append("; temps=");
            String sep = "";
            for (Value op : temporaries) {
                sb.append(sep).append(op);
                sep = ",";
            }
        }
        return sb.toString();
    }

    public CallingConvention getCallingConvention() {
        return cc;
    }

    public Value[] getTemporaries() {
        if (temporaries.length == 0) {
            return temporaries;
        }
        return temporaries.clone();
    }

    public long getMaxCallTargetOffset() {
        return graalRuntime().getCompilerToVM().getMaxCallTargetOffset(address);
    }

    public ForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    public void setCompiledStub(Stub stub) {
        assert address == 0L : "cannot set stub for linkage that already has an address: " + this;
        this.stub = stub;
    }

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    public boolean isCompiledStub() {
        return address == 0L || stub != null;
    }

    public void finalizeAddress(Backend backend) {
        if (address == 0) {
            assert stub != null : "linkage without an address must be a stub - forgot to register a Stub associated with " + descriptor + "?";
            InstalledCode code = stub.getCode(backend);

            Set<Register> destroyedRegisters = stub.getDestroyedRegisters();
            if (!destroyedRegisters.isEmpty()) {
                AllocatableValue[] temporaryLocations = new AllocatableValue[destroyedRegisters.size()];
                int i = 0;
                for (Register reg : destroyedRegisters) {
                    temporaryLocations[i++] = reg.asValue();
                }
                temporaries = temporaryLocations;
            }
            address = code.getStart();
        }
    }

    public long getAddress() {
        assert address != 0L : "address not yet finalized: " + this;
        return address;
    }

    @Override
    public boolean destroysRegisters() {
        return effect == DESTROYS_REGISTERS;
    }

    /**
     * Determines if this is call to a function that does not lock, GC or throw exceptions. That is,
     * the thread's execution state during the call is never inspected by another thread.
     */
    public boolean isLeaf() {
        return transition == Transition.LEAF;
    }
}
