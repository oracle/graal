/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.word.WordTypes;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CallingConvention.Type;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotForeignCallTarget;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * The details required to link a HotSpot runtime or stub call.
 */
public class HotSpotForeignCallLinkageImpl extends HotSpotForeignCallTarget implements HotSpotForeignCallLinkage {

    /**
     * A calling convention where all arguments are passed through the stack and any return values
     * are passed through the stack. This is useful for assembly slow paths where we don't want to
     * perturb the register allocation of the fast path.
     */
    public enum StackOnlyCallingConvention implements CallingConvention.Type {

        /**
         * The stack only convention from the perspective of the caller.
         */
        StackOnlyCall(true),

        /**
         * The stack only convention from the perspective of the callee.
         */
        StackOnlyCallee(false);

        /**
         * Determines if this is a request for the outgoing argument locations at a call site.
         */
        public final boolean out;

        StackOnlyCallingConvention(boolean out) {
            this.out = out;
        }

        /**
         * Creates a calling convention were all arguments and the return value are passed on the
         * stack. This follows the stack layout of normal Java calling convention.
         *
         * A platform specific definition of this could be provided but currently AMD64 and AArch64
         * do exactly the same thing so for simplicity it is provided here.
         */
        public CallingConvention getCallingConvention(TargetDescription target, JavaType returnType, JavaType[] parameterTypes,
                        ValueKindFactory<?> valueKindFactory) {
            AllocatableValue[] locations = new AllocatableValue[parameterTypes.length];
            int currentStackOffset = 0;
            for (int i = 0; i < parameterTypes.length; i++) {
                final JavaKind kind = parameterTypes[i].getJavaKind().getStackKind();
                switch (kind) {
                    case Illegal:
                    case Void:
                        throw GraalError.shouldNotReachHere(kind.toString());
                }

                ValueKind<?> valueKind = valueKindFactory.getValueKind(kind);
                locations[i] = StackSlot.get(valueKind, currentStackOffset, !this.out);
                currentStackOffset += Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
            }

            JavaKind returnKind = returnType == null ? JavaKind.Void : returnType.getJavaKind();
            AllocatableValue returnLocation = Value.ILLEGAL;
            if (returnKind != JavaKind.Void) {
                /*
                 * The return value is also passed through the stack so use the same location that
                 * would be used if it were an incoming argument.
                 */
                ValueKind<?> valueKind = valueKindFactory.getValueKind(returnKind);
                returnLocation = StackSlot.get(valueKind, 0, !this.out);
                int slotSize = Math.max(valueKind.getPlatformKind().getSizeInBytes(), target.wordSize);
                currentStackOffset = Math.max(currentStackOffset, slotSize);
            }

            return new CallingConvention(currentStackOffset, returnLocation, locations);
        }
    }

    /**
     * The descriptor of the call.
     */
    protected final HotSpotForeignCallDescriptor descriptor;

    /**
     * Non-null (eventually) iff this is a call to a compiled {@linkplain Stub stub}.
     */
    private Stub stub;

    /**
     * The calling convention for this call.
     */
    private final CallingConvention outgoingCallingConvention;

    /**
     * The calling convention for incoming arguments to the stub, iff this call uses a compiled
     * {@linkplain Stub stub}.
     */
    private final CallingConvention incomingCallingConvention;

    private final RegisterEffect effect;

    /**
     * The registers and stack slots defined/killed by the call.
     */
    private Value[] temporaries = AllocatableValue.NONE;

    /**
     * Creates a {@link HotSpotForeignCallLinkage}.
     *
     * @param descriptor the descriptor of the call
     * @param address the address of the code to call
     * @param effect specifies if the call destroys or preserves all registers (apart from
     *            temporaries which are always destroyed)
     * @param outgoingCcType outgoing (caller) calling convention type
     * @param incomingCcType incoming (callee) calling convention type (can be null)
     */
    public static HotSpotForeignCallLinkage create(MetaAccessProvider metaAccess,
                    CodeCacheProvider codeCache,
                    WordTypes wordTypes,
                    HotSpotForeignCallsProvider foreignCalls,
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    RegisterEffect effect,
                    Type outgoingCcType,
                    Type incomingCcType) {
        CallingConvention outgoingCc = createCallingConvention(metaAccess, codeCache, wordTypes, foreignCalls, descriptor, outgoingCcType);
        CallingConvention incomingCc = incomingCcType == null ? null : createCallingConvention(metaAccess, codeCache, wordTypes, foreignCalls, descriptor, incomingCcType);
        HotSpotForeignCallLinkageImpl linkage = new HotSpotForeignCallLinkageImpl(descriptor, address, effect, outgoingCc, incomingCc);
        if (outgoingCcType == HotSpotCallingConventionType.NativeCall) {
            linkage.temporaries = foreignCalls.getNativeABICallerSaveRegisters();
        }
        return linkage;
    }

    /**
     * Gets a calling convention for a given descriptor and call type.
     */
    public static CallingConvention createCallingConvention(MetaAccessProvider metaAccess,
                    CodeCacheProvider codeCache,
                    WordTypes wordTypes,
                    ValueKindFactory<?> valueKindFactory,
                    ForeignCallDescriptor descriptor,
                    Type ccType) {
        assert ccType != null;
        Class<?>[] argumentTypes = descriptor.getArgumentTypes();
        JavaType[] parameterTypes = new JavaType[argumentTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = asJavaType(argumentTypes[i], metaAccess, wordTypes);
        }
        JavaType returnType = asJavaType(descriptor.getResultType(), metaAccess, wordTypes);
        RegisterConfig regConfig = codeCache.getRegisterConfig();
        if (ccType instanceof StackOnlyCallingConvention) {
            StackOnlyCallingConvention conventionType = (StackOnlyCallingConvention) ccType;
            return conventionType.getCallingConvention(codeCache.getTarget(), returnType, parameterTypes, valueKindFactory);
        }
        return regConfig.getCallingConvention(ccType, returnType, parameterTypes, valueKindFactory);
    }

    private static JavaType asJavaType(Class<?> type, MetaAccessProvider metaAccess, WordTypes wordTypes) {
        if (wordTypes.isWord(type)) {
            return metaAccess.lookupJavaType(wordTypes.getWordKind().toJavaClass());
        }
        return metaAccess.lookupJavaType(type);
    }

    public HotSpotForeignCallLinkageImpl(HotSpotForeignCallDescriptor descriptor, long address, RegisterEffect effect,
                    CallingConvention outgoingCallingConvention, CallingConvention incomingCallingConvention) {
        super(address);
        this.descriptor = descriptor;
        this.address = address;
        this.effect = effect;
        assert outgoingCallingConvention != null : "only incomingCallingConvention can be null";
        this.outgoingCallingConvention = outgoingCallingConvention;
        this.incomingCallingConvention = incomingCallingConvention != null ? incomingCallingConvention : outgoingCallingConvention;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(stub == null ? descriptor.toString() : stub.toString());
        sb.append("@0x").append(Long.toHexString(address)).append(':').append(outgoingCallingConvention).append(":").append(incomingCallingConvention);
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

    @Override
    public RegisterEffect getEffect() {
        return effect;
    }

    @Override
    public CallingConvention getOutgoingCallingConvention() {
        return outgoingCallingConvention;
    }

    @Override
    public CallingConvention getIncomingCallingConvention() {
        return incomingCallingConvention;
    }

    @Override
    public Value[] getTemporaries() {
        if (temporaries.length == 0) {
            return temporaries;
        }
        return temporaries.clone();
    }

    @Override
    public long getMaxCallTargetOffset() {
        return runtime().getHostJVMCIBackend().getCodeCache().getMaxCallTargetOffset(address);
    }

    @Override
    public HotSpotForeignCallDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void setCompiledStub(Stub stub) {
        assert address == 0L : "cannot set stub for linkage that already has an address: " + this;
        this.stub = stub;
    }

    /**
     * Determines if this is a call to a compiled {@linkplain Stub stub}.
     */
    @Override
    public boolean isCompiledStub() {
        return address == 0L || stub != null;
    }

    @Override
    public Stub getStub() {
        assert checkStubCondition();
        return stub;
    }

    private boolean checkStubCondition() {
        assert stub != null : "linkage without an address must be a stub - forgot to register a Stub associated with " + descriptor + "?";
        return true;
    }

    /**
     * Encapsulates a stub's entry point and set of killed registers.
     */
    public static final class CodeInfo {
        /**
         * Address of first instruction in the stub.
         */
        final long start;

        /**
         * @see Stub#getDestroyedCallerRegisters()
         */
        final EconomicSet<Register> killedRegisters;

        public CodeInfo(long start, EconomicSet<Register> killedRegisters) {
            this.start = start;
            this.killedRegisters = killedRegisters;
        }
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_jdk_graal_compiler_hotspot_HotSpotForeignCallLinkageImpl}.
     */
    private static CodeInfo getCodeInfo(Stub stub, Backend backend) {
        return new CodeInfo(stub.getCode(backend).getStart(), stub.getDestroyedCallerRegisters());
    }

    @Override
    public void finalizeAddress(Backend backend) {
        if (address == 0) {
            assert checkStubCondition();
            CodeInfo codeInfo = getCodeInfo(stub, backend);

            EconomicSet<Register> killedRegisters = codeInfo.killedRegisters;
            if (!killedRegisters.isEmpty()) {
                AllocatableValue[] temporaryLocations = new AllocatableValue[killedRegisters.size()];
                int i = 0;
                for (Register reg : killedRegisters) {
                    temporaryLocations[i++] = reg.asValue();
                }
                if (stub.getLinkage().getEffect() == HotSpotForeignCallLinkage.RegisterEffect.KILLS_NO_REGISTERS) {
                    GraalError.guarantee(temporaryLocations.length == 0, "no registers are expected to be killed: %s %s", this, temporaryLocations);
                }
                temporaries = temporaryLocations;
            }
            address = codeInfo.start;
        }
    }

    @Override
    public long getAddress() {
        assert address != 0L : "address not yet finalized: " + this;
        return address;
    }

    @Override
    public boolean destroysRegisters() {
        return effect == RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
    }

    @Override
    public boolean needsDebugInfo() {
        return descriptor.canDeoptimize();
    }

    @Override
    public boolean mayContainFP() {
        return descriptor.getTransition() != HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
    }

    @Override
    public boolean needsJavaFrameAnchor() {
        if (descriptor.getTransition() == HotSpotForeignCallDescriptor.Transition.SAFEPOINT || descriptor.getTransition() == HotSpotForeignCallDescriptor.Transition.STACK_INSPECTABLE_LEAF) {
            if (stub != null) {
                // The stub will do the JavaFrameAnchor management
                // around the runtime call(s) it makes
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getSymbol() {
        return stub == null ? null : stub.toString();
    }

    @Override
    public boolean needsClearUpperVectorRegisters() {
        return isCompiledStub() && mayContainFP();
    }

}
