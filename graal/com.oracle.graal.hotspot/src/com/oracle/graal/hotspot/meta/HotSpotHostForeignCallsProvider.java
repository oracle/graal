/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.compiler.target.Backend.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotBackend.Options.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.*;
import static com.oracle.graal.hotspot.HotSpotHostBackend.*;
import static com.oracle.graal.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls.*;
import static com.oracle.graal.hotspot.replacements.AssertionSnippets.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.MonitorSnippets.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;
import static com.oracle.graal.hotspot.replacements.SystemSubstitutions.*;
import static com.oracle.graal.hotspot.replacements.ThreadSubstitutions.*;
import static com.oracle.graal.hotspot.replacements.WriteBarrierSnippets.*;
import static com.oracle.graal.hotspot.stubs.ExceptionHandlerStub.*;
import static com.oracle.graal.hotspot.stubs.NewArrayStub.*;
import static com.oracle.graal.hotspot.stubs.NewInstanceStub.*;
import static com.oracle.graal.hotspot.stubs.StubUtil.*;
import static com.oracle.graal.hotspot.stubs.UnwindExceptionToCallerStub.*;
import static com.oracle.graal.nodes.NamedLocationIdentity.*;
import static com.oracle.graal.nodes.java.ForeignCallDescriptors.*;
import static com.oracle.graal.replacements.Log.*;
import static jdk.internal.jvmci.code.CallingConvention.Type.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.hotspot.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.word.*;

/**
 * HotSpot implementation of {@link ForeignCallsProvider}.
 */
public abstract class HotSpotHostForeignCallsProvider extends HotSpotForeignCallsProviderImpl {

    public HotSpotHostForeignCallsProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache) {
        super(runtime, metaAccess, codeCache);
    }

    protected static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    public static ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        return checkcastArraycopyDescriptors[uninit ? 1 : 0];
    }

    public static ForeignCallDescriptor lookupArraycopyDescriptor(Kind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny) {
        if (uninit) {
            assert kind == Kind.Object;
            assert !killAny : "unsupported";
            return uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0];
        }
        if (killAny) {
            assert kind == Kind.Object;
            return objectArraycopyDescriptorsKillAny[aligned ? 1 : 0][disjoint ? 1 : 0];
        }
        return arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
    }

    @SuppressWarnings("unchecked") private static final EnumMap<Kind, ForeignCallDescriptor>[][] arraycopyDescriptors = new EnumMap[2][2];

    private static final ForeignCallDescriptor[][] uninitObjectArraycopyDescriptors = new ForeignCallDescriptor[2][2];
    private static final ForeignCallDescriptor[] checkcastArraycopyDescriptors = new ForeignCallDescriptor[2];
    private static ForeignCallDescriptor[][] objectArraycopyDescriptorsKillAny = new ForeignCallDescriptor[2][2];

    static {
        // Populate the EnumMap instances
        for (int i = 0; i < arraycopyDescriptors.length; i++) {
            for (int j = 0; j < arraycopyDescriptors[i].length; j++) {
                arraycopyDescriptors[i][j] = new EnumMap<>(Kind.class);
            }
        }
    }

    private void registerArraycopyDescriptor(Map<Long, ForeignCallDescriptor> descMap, Kind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        ForeignCallDescriptor desc = descMap.get(routine);
        if (desc == null) {
            desc = buildDescriptor(kind, aligned, disjoint, uninit, killAny, routine);
            descMap.put(routine, desc);
        }
        if (uninit) {
            assert kind == Kind.Object;
            uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0] = desc;
        } else {
            arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, desc);
        }
    }

    private ForeignCallDescriptor buildDescriptor(Kind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        assert !killAny || kind == Kind.Object;
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + (uninit ? "Uninit" : "") + "Arraycopy" + (killAny ? "KillAny" : "");
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, void.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = killAny ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(kind);
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        return desc;
    }

    private void registerCheckcastArraycopyDescriptor(boolean uninit, long routine) {
        String name = "Object" + (uninit ? "Uninit" : "") + "Checkcast";
        // Input:
        // c_rarg0 - source array address
        // c_rarg1 - destination array address
        // c_rarg2 - element count, treated as ssize_t, can be zero
        // c_rarg3 - size_t ckoff (super_check_offset)
        // c_rarg4 - oop ckval (super_klass)
        // return: 0 = success, n = number of copied elements xor'd with -1.
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, int.class, Word.class, Word.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = NamedLocationIdentity.getArrayLocation(Kind.Object);
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        checkcastArraycopyDescriptors[uninit ? 1 : 0] = desc;
    }

    private void registerArrayCopy(Kind kind, long routine, long alignedRoutine, long disjointRoutine, long alignedDisjointRoutine) {
        registerArrayCopy(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, false);
    }

    private void registerArrayCopy(Kind kind, long routine, long alignedRoutine, long disjointRoutine, long alignedDisjointRoutine, boolean uninit) {
        /*
         * Sometimes the same function is used for multiple cases so share them when that's the case
         * but only within the same Kind. For instance short and char are the same copy routines but
         * they kill different memory so they still have to be distinct.
         */
        Map<Long, ForeignCallDescriptor> descMap = new HashMap<>();
        registerArraycopyDescriptor(descMap, kind, false, false, uninit, false, routine);
        registerArraycopyDescriptor(descMap, kind, true, false, uninit, false, alignedRoutine);
        registerArraycopyDescriptor(descMap, kind, false, true, uninit, false, disjointRoutine);
        registerArraycopyDescriptor(descMap, kind, true, true, uninit, false, alignedDisjointRoutine);

        if (kind == Kind.Object && !uninit) {
            objectArraycopyDescriptorsKillAny[0][0] = buildDescriptor(kind, false, false, uninit, true, routine);
            objectArraycopyDescriptorsKillAny[1][0] = buildDescriptor(kind, true, false, uninit, true, alignedRoutine);
            objectArraycopyDescriptorsKillAny[0][1] = buildDescriptor(kind, false, true, uninit, true, disjointRoutine);
            objectArraycopyDescriptorsKillAny[1][1] = buildDescriptor(kind, true, true, uninit, true, alignedDisjointRoutine);
        }
    }

    public void initialize(HotSpotProviders providers, HotSpotVMConfig c) {

        if (!PreferGraalStubs.getValue()) {
            registerForeignCall(DEOPTIMIZATION_HANDLER, c.handleDeoptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
            registerForeignCall(UNCOMMON_TRAP_HANDLER, c.uncommonTrapStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        }
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub(), NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_SIN, c.arithmeticSinAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_COS, c.arithmeticCosAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_TAN, c.arithmeticTanAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_EXP, c.arithmeticExpAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_LOG, c.arithmeticLogAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_LOG10, c.arithmeticLog10Address, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_POW, c.arithmeticPowAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, any());

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, any());
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, any());
        registerForeignCall(FETCH_UNROLL_INFO, c.deoptimizationFetchUnrollInfo, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, any());
        registerForeignCall(NEW_ARRAY_C, c.newArrayAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, any());
        registerForeignCall(NEW_INSTANCE_C, c.newInstanceAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, any());
        registerForeignCall(UNCOMMON_TRAP, c.deoptimizationUncommonTrap, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, NOT_REEXECUTABLE, any());

        /*
         * We cannot use LEAF_SP here because on some architectures we have to align the stack
         * manually before calling into the VM. See {@link
         * AMD64HotSpotEnterUnpackFramesStackFrameOp#emitCode}.
         */
        registerForeignCall(UNPACK_FRAMES, c.deoptimizationUnpackFrames, NativeCall, DESTROYS_REGISTERS, LEAF, NOT_REEXECUTABLE, any());

        /*
         * This message call is registered twice, where the second one must only be used for calls
         * that do not return, i.e., that exit the VM.
         */
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ASSERTION_VM_MESSAGE_C, c.vmMessageAddress, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        link(new NewInstanceStub(providers, registerStubCall(NEW_INSTANCE, REEXECUTABLE, NOT_LEAF, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION)));
        link(new NewArrayStub(providers, registerStubCall(NEW_ARRAY, REEXECUTABLE, NOT_LEAF, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION)));
        link(new ExceptionHandlerStub(providers, foreignCalls.get(EXCEPTION_HANDLER)));
        link(new UnwindExceptionToCallerStub(providers, registerStubCall(UNWIND_EXCEPTION_TO_CALLER, NOT_REEXECUTABLE, NOT_LEAF, any())));
        link(new VerifyOopStub(providers, registerStubCall(VERIFY_OOP, REEXECUTABLE, LEAF_NOFP, NO_LOCATIONS)));

        linkForeignCall(providers, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, MARK_WORD_LOCATION);
        linkForeignCall(providers, REGISTER_FINALIZER, c.registerFinalizerAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, CREATE_NULL_POINTER_EXCEPTION, c.createNullPointerExceptionAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, any());
        linkForeignCall(providers, CREATE_OUT_OF_BOUNDS_EXCEPTION, c.createOutOfBoundsExceptionAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, any());
        linkForeignCall(providers, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD, STACK_INSPECTABLE_LEAF, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, NEW_MULTI_ARRAY, c.newMultiArrayAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
        linkForeignCall(providers, DYNAMIC_NEW_ARRAY, c.dynamicNewArrayAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, INIT_LOCATION);
        linkForeignCall(providers, DYNAMIC_NEW_INSTANCE, c.dynamicNewInstanceAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, INIT_LOCATION);
        linkForeignCall(providers, LOG_PRINTF, c.logPrintfAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, LOG_OBJECT, c.logObjectAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, LOG_PRIMITIVE, c.logPrimitiveAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, VM_ERROR, c.vmErrorAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, OSR_MIGRATION_END, c.osrMigrationEndAddress, DONT_PREPEND_THREAD, LEAF_NOFP, NOT_REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, G1WBPRECALL, c.writeBarrierPreAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, G1WBPOSTCALL, c.writeBarrierPostAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, VALIDATE_OBJECT, c.validateObject, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        // Cannot be a leaf as VM acquires Thread_lock which requires thread_in_vm state
        linkForeignCall(providers, THREAD_IS_INTERRUPTED, c.threadIsInterruptedAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, any());

        linkForeignCall(providers, TEST_DEOPTIMIZE_CALL_INT, c.testDeoptimizeCallInt, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, any());

        registerArrayCopy(Kind.Byte, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Boolean, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Char, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Short, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Int, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Float, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Long, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Double, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Object, c.oopArraycopy, c.oopAlignedArraycopy, c.oopDisjointArraycopy, c.oopAlignedDisjointArraycopy);
        registerArrayCopy(Kind.Object, c.oopArraycopyUninit, c.oopAlignedArraycopyUninit, c.oopDisjointArraycopyUninit, c.oopAlignedDisjointArraycopyUninit, true);

        registerCheckcastArraycopyDescriptor(true, c.checkcastArraycopyUninit);
        registerCheckcastArraycopyDescriptor(false, c.checkcastArraycopy);

        if (c.useAESIntrinsics) {
            /*
             * When the java.ext.dirs property is modified then the crypto classes might not be
             * found. If that's the case we ignore the ClassNotFoundException and continue since we
             * cannot replace a non-existing method anyway.
             */
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT_BLOCK, c.aescryptEncryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(Kind.Byte));
                registerForeignCall(DECRYPT_BLOCK, c.aescryptDecryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(Kind.Byte));
            } catch (JVMCIError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT, c.cipherBlockChainingEncryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(Kind.Byte));
                registerForeignCall(DECRYPT, c.cipherBlockChainingDecryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(Kind.Byte));
            } catch (JVMCIError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
        }
    }

    public HotSpotForeignCallLinkage getForeignCall(ForeignCallDescriptor descriptor) {
        assert foreignCalls != null : descriptor;
        return foreignCalls.get(descriptor);
    }
}
