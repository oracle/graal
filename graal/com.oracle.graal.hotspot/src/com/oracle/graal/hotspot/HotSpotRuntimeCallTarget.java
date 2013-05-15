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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.HotSpotRuntimeCallTarget.RegisterEffect.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.word.*;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public class HotSpotRuntimeCallTarget implements RuntimeCallTarget, InvokeTarget {

    /**
     * Constants for specifying whether a call destroys or preserves registers. A call will always
     * destroy {@link HotSpotRuntimeCallTarget#getCallingConvention() its}
     * {@linkplain CallingConvention#getTemporaries() temporary} registers.
     */
    public enum RegisterEffect {
        DESTROYS_REGISTERS, PRESERVES_REGISTERS
    }

    /**
     * Sentinel marker for a computed jump address.
     */
    public static final long JUMP_ADDRESS = 0xDEADDEADBEEFBEEFL;

    /**
     * The descriptor of the call.
     */
    private final Descriptor descriptor;

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
    private CallingConvention cc;

    private final CompilerToVM vm;

    private final RegisterEffect effect;

    /**
     * Creates a {@link HotSpotRuntimeCallTarget}.
     * 
     * @param descriptor the descriptor of the call
     * @param address the address of the code to call
     * @param effect specifies if the call destroys or preserves all registers (apart from
     *            temporaries which are always destroyed)
     * @param ccType calling convention type
     * @param ccProvider calling convention provider
     * @param vm the Java to HotSpot C/C++ runtime interface
     */
    public static HotSpotRuntimeCallTarget create(Descriptor descriptor, long address, RegisterEffect effect, Type ccType, RegisterConfig ccProvider, HotSpotRuntime runtime, CompilerToVM vm) {
        CallingConvention targetCc = createCallingConvention(descriptor, ccType, ccProvider, runtime);
        return new HotSpotRuntimeCallTarget(descriptor, address, effect, targetCc, vm);
    }

    /**
     * Gets a calling convention for a given descriptor and call type.
     */
    public static CallingConvention createCallingConvention(Descriptor descriptor, Type ccType, RegisterConfig ccProvider, HotSpotRuntime runtime) {
        Class<?>[] argumentTypes = descriptor.getArgumentTypes();
        JavaType[] parameterTypes = new JavaType[argumentTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = asJavaType(argumentTypes[i], runtime);
        }
        TargetDescription target = graalRuntime().getTarget();
        JavaType returnType = asJavaType(descriptor.getResultType(), runtime);
        return ccProvider.getCallingConvention(ccType, returnType, parameterTypes, target, false);
    }

    private static JavaType asJavaType(Class type, HotSpotRuntime runtime) {
        if (WordBase.class.isAssignableFrom(type)) {
            return runtime.lookupJavaType(wordKind().toJavaClass());
        } else {
            return runtime.lookupJavaType(type);
        }
    }

    public HotSpotRuntimeCallTarget(Descriptor descriptor, long address, RegisterEffect effect, CallingConvention cc, CompilerToVM vm) {
        this.address = address;
        this.effect = effect;
        this.descriptor = descriptor;
        this.cc = cc;
        this.vm = vm;
    }

    @Override
    public String toString() {
        return (stub == null ? descriptor.toString() : stub) + "@0x" + Long.toHexString(address) + ":" + cc;
    }

    public CallingConvention getCallingConvention() {
        return cc;
    }

    public long getMaxCallTargetOffset() {
        return vm.getMaxCallTargetOffset(address);
    }

    public Descriptor getDescriptor() {
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
            AllocatableValue[] temporaryLocations = new AllocatableValue[destroyedRegisters.size()];
            int i = 0;
            for (Register reg : destroyedRegisters) {
                temporaryLocations[i++] = reg.asValue();
            }
            // Update calling convention with temporaries
            cc = new CallingConvention(temporaryLocations, cc.getStackSize(), cc.getReturn(), cc.getArguments());
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
}
