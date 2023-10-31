/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;
import static jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.UNSAFE_ARRAYCOPY;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static org.graalvm.word.LocationIdentity.any;

import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.nodes.VMErrorNode;
import jdk.graal.compiler.hotspot.stubs.ExceptionHandlerStub;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.StandardOp.LabelOp;
import jdk.graal.compiler.lir.StandardOp.RestoreRegistersOp;
import jdk.graal.compiler.lir.StandardOp.SaveRegistersOp;
import jdk.graal.compiler.lir.ValueConsumer;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.SuitesProvider;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * HotSpot specific backend.
 */
public abstract class HotSpotBackend extends Backend implements FrameMap.ReferenceMapBuilderFactory {

    public static class Options {
        // @formatter:off
        @Option(help = "Use Graal arithmetic stubs instead of HotSpot stubs where possible")
        public static final OptionKey<Boolean> GraalArithmeticStubs = new OptionKey<>(true);
        // @formatter:on
    }

    /**
     * Descriptor for {@link ExceptionHandlerStub}. This stub is called by the
     * {@linkplain HotSpotMarkId#EXCEPTION_HANDLER_ENTRY exception handler} in a compiled method.
     */
    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "exceptionHandler", void.class, Object.class,
                    Word.class);

    /**
     * Descriptor for SharedRuntime::get_ic_miss_stub().
     */
    public static final HotSpotForeignCallDescriptor IC_MISS_HANDLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "icMissHandler", void.class);

    /**
     * Descriptor for {@link UnwindExceptionToCallerStub}. This stub is called by code generated
     * from {@link UnwindNode}.
     */
    public static final HotSpotForeignCallDescriptor UNWIND_EXCEPTION_TO_CALLER = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(), "unwindExceptionToCaller", void.class,
                    Object.class, Word.class);

    /**
     * Descriptor for the arguments when unwinding to an exception handler in a caller.
     */
    public static final HotSpotForeignCallDescriptor EXCEPTION_HANDLER_IN_CALLER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "exceptionHandlerInCaller",
                    void.class, Object.class, Word.class);

    private final HotSpotGraalRuntimeProvider runtime;

    public static final HotSpotForeignCallDescriptor MONTGOMERY_MULTIPLY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "implMontgomeryMultiply", void.class, Word.class, Word.class, Word.class, int.class, long.class, Word.class);

    public static final HotSpotForeignCallDescriptor MONTGOMERY_SQUARE = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NamedLocationIdentity.getArrayLocation(JavaKind.Int),
                    "implMontgomerySquare", void.class, Word.class, Word.class, int.class, long.class, Word.class);

    public static final HotSpotForeignCallDescriptor MD5_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "md5ImplCompress", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int md5ImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return md5ImplCompressMBStub(HotSpotBackend.MD5_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int md5ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "shaImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int shaImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return shaImplCompressMBStub(HotSpotBackend.SHA_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int shaImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA2_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "sha2ImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int sha2ImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return sha2ImplCompressMBStub(HotSpotBackend.SHA2_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int sha2ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA5_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "sha5ImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class);

    public static int sha5ImplCompressMBStub(Word bufAddr, Object stateAddr, int ofs, int limit) {
        return sha5ImplCompressMBStub(HotSpotBackend.SHA5_IMPL_COMPRESS_MB, bufAddr, stateAddr, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int sha5ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int ofs, int limit);

    public static final HotSpotForeignCallDescriptor SHA3_IMPL_COMPRESS_MB = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "sha3ImplCompressMB", int.class, Word.class,
                    Object.class, int.class, int.class, int.class);

    public static int sha3ImplCompressMBStub(Word bufAddr, Object stateAddr, int blockSize, int ofs, int limit) {
        return sha3ImplCompressMBStub(HotSpotBackend.SHA3_IMPL_COMPRESS_MB, bufAddr, stateAddr, blockSize, ofs, limit);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native int sha3ImplCompressMBStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word bufAddr, Object state, int blockSize, int ofs, int limit);

    public static void unsafeArraycopy(Word srcAddr, Word dstAddr, Word size) {
        unsafeArraycopyStub(UNSAFE_ARRAYCOPY, srcAddr, dstAddr, size);
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void unsafeArraycopyStub(@ConstantNodeParameter ForeignCallSignature descriptor, Word srcAddr, Word dstAddr, Word size);

    /**
     * Descriptor for {@code StubRoutines::_base64_encodeBlock}.
     */
    public static final HotSpotForeignCallDescriptor BASE64_ENCODE_BLOCK = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "base64EncodeBlock", void.class, Word.class,
                    int.class, int.class, Word.class, int.class, boolean.class);

    /**
     * Descriptor for {@code StubRoutines::_base64_decodeBlock}.
     */
    public static final HotSpotForeignCallDescriptor BASE64_DECODE_BLOCK = new HotSpotForeignCallDescriptor(LEAF, HAS_SIDE_EFFECT, any(), "base64DecodeBlock", int.class, Word.class,
                    int.class, int.class, Word.class, int.class, boolean.class, boolean.class);

    public static final LocationIdentity CRC_TABLE_LOCATION = NamedLocationIdentity.immutable("crc32_table");

    public static final HotSpotForeignCallDescriptor UPDATE_BYTES_CRC32 = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "updateBytesCRC32", int.class, int.class,
                    WordBase.class, int.class);

    public static final HotSpotForeignCallDescriptor UPDATE_BYTES_CRC32C = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "updateBytesCRC32C", int.class, int.class,
                    WordBase.class, int.class);

    public static final HotSpotForeignCallDescriptor UPDATE_BYTES_ADLER32 = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "updateBytesAdler32", int.class, int.class,
                    WordBase.class, int.class);

    public static final HotSpotForeignCallDescriptor BIGINTEGER_LEFT_SHIFT_WORKER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "bigIntegerLeftShiftWorker", void.class,
                    WordBase.class, WordBase.class, int.class, int.class, int.class);

    public static final HotSpotForeignCallDescriptor BIGINTEGER_RIGHT_SHIFT_WORKER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "bigIntegerRightShiftWorker", void.class,
                    WordBase.class, WordBase.class, int.class, int.class, int.class);

    public static final HotSpotForeignCallDescriptor ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(),
                    "_electronicCodeBook_encryptAESCrypt", int.class,
                    WordBase.class, WordBase.class, WordBase.class, int.class);

    public static final HotSpotForeignCallDescriptor ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(),
                    "_electronicCodeBook_decryptAESCrypt", int.class,
                    WordBase.class, WordBase.class, WordBase.class, int.class);

    public static final HotSpotForeignCallDescriptor GALOIS_COUNTER_MODE_CRYPT = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "_galoisCounterMode_AESCrypt", int.class,
                    WordBase.class, int.class, WordBase.class, WordBase.class, WordBase.class, WordBase.class, WordBase.class, WordBase.class);

    public static final HotSpotForeignCallDescriptor POLY1305_PROCESSBLOCKS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "_poly1305_processBlocks", int.class,
                    WordBase.class, int.class, WordBase.class, WordBase.class);

    public static final HotSpotForeignCallDescriptor CHACHA20Block = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "_chacha20Block", int.class,
                    WordBase.class, WordBase.class);

    public static final HotSpotForeignCallDescriptor SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(),
                    "notify_jvmti_vthread_start", void.class,
                    Object.class, boolean.class, Word.class);

    public static final HotSpotForeignCallDescriptor SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(),
                    "notify_jvmti_vthread_end", void.class,
                    Object.class, boolean.class, Word.class);

    public static final HotSpotForeignCallDescriptor SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(),
                    "notify_jvmti_vthread_mount", void.class,
                    Object.class, boolean.class, Word.class);

    public static final HotSpotForeignCallDescriptor SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT = new HotSpotForeignCallDescriptor(SAFEPOINT, HAS_SIDE_EFFECT, any(),
                    "notify_jvmti_vthread_unmount", void.class,
                    Object.class, boolean.class, Word.class);

    /**
     * @see VMErrorNode
     */
    public static final HotSpotForeignCallDescriptor VM_ERROR = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "vm_error", void.class, Object.class, Object.class,
                    long.class);

    private static final LocationIdentity[] TLAB_LOCATIONS = new LocationIdentity[]{TLAB_TOP_LOCATION, TLAB_END_LOCATION};

    /**
     * New multi array stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_MULTI_ARRAY = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_multi_array", Object.class, KlassPointer.class,
                    int.class, Word.class);

    /**
     * New multi array stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_MULTI_ARRAY_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_multi_array_or_null", Object.class,
                    KlassPointer.class, int.class, Word.class);

    /**
     * New array stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_ARRAY = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_array", Object.class, KlassPointer.class, int.class);

    /**
     * New array stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_ARRAY_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_array_or_null", Object.class,
                    KlassPointer.class,
                    int.class);

    /**
     * New instance stub that throws an {@link OutOfMemoryError} on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_INSTANCE = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_instance", Object.class, KlassPointer.class);

    /**
     * New instance stub that will return null on allocation failure.
     */
    public static final HotSpotForeignCallDescriptor NEW_INSTANCE_OR_NULL = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, TLAB_LOCATIONS, "new_instance_or_null", Object.class,
                    KlassPointer.class);

    public HotSpotBackend(HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(providers);
        this.runtime = runtime;
    }

    public HotSpotGraalRuntimeProvider getRuntime() {
        return runtime;
    }

    /**
     * Performs any remaining initialization that was deferred until the {@linkplain #getRuntime()
     * runtime} object was initialized and this backend was registered with it.
     *
     * @param jvmciRuntime
     * @param options
     */
    public void completeInitialization(HotSpotJVMCIRuntime jvmciRuntime, OptionValues options) {
    }

    /**
     * Finds all the registers that are defined by some given LIR.
     *
     * @param gen the result to examine
     * @return the registers that are defined by or used as temps for any instruction in {@code lir}
     */
    private static EconomicSet<Register> gatherDestroyedCallerRegisters(HotSpotLIRGenerationResult gen) {
        LIR lir = gen.getLIR();
        final EconomicSet<Register> preservedRegisters = EconomicSet.create(Equivalence.IDENTITY);
        final EconomicSet<Register> destroyedRegisters = EconomicSet.create(Equivalence.IDENTITY);
        ValueConsumer defConsumer = new ValueConsumer() {

            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (ValueUtil.isRegister(value)) {
                    final Register reg = ValueUtil.asRegister(value);
                    if (!preservedRegisters.contains(reg)) {
                        destroyedRegisters.add(reg);
                    }
                }
            }
        };
        boolean sawSaveRegisters = false;
        for (int blockId : lir.getBlocks()) {
            if (AbstractControlFlowGraph.blockIsDeletedOrNew(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            // Ignore the effects of instructions bracketed by save/restore
            SaveRegistersOp save = null;
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op instanceof LabelOp) {
                    // Don't consider this as a definition
                } else if (op instanceof SaveRegistersOp) {
                    save = (SaveRegistersOp) op;
                    sawSaveRegisters = true;
                    preservedRegisters.addAll(save.getSaveableRegisters());
                } else if (op instanceof RestoreRegistersOp) {
                    save = null;
                    preservedRegisters.clear();
                } else {
                    op.visitEachTemp(defConsumer);
                    op.visitEachOutput(defConsumer);
                }
            }
            assert save == null : "missing RestoreRegistersOp";
        }

        if (sawSaveRegisters) {
            // The return value must be killed so it can be propagated out
            CallingConvention cc = gen.getCallingConvention();
            AllocatableValue returnValue = cc.getReturn();
            if (returnValue != null) {
                if (ValueUtil.isRegister(returnValue)) {
                    destroyedRegisters.add(ValueUtil.asRegister(returnValue));
                }
            }
        }
        return destroyedRegisters;
    }

    /**
     * Updates a given stub with respect to the registers it destroys by
     * {@link #gatherDestroyedCallerRegisters(HotSpotLIRGenerationResult) computing the destroyed
     * registers} and removing those registers from the {@linkplain SaveRegistersOp SaveRegistersOp}
     * as these registers are declared as temporaries in the stub's {@linkplain ForeignCallLinkage
     * linkage} (and thus will be saved by the stub's caller).
     *
     * @param stub the stub to update
     * @param gen the HotSpotLIRGenerationResult being emitted
     * @param frameMap used to {@linkplain FrameMap#offsetForStackSlot(StackSlot) convert} a virtual
     */
    protected void updateStub(Stub stub, HotSpotLIRGenerationResult gen, FrameMap frameMap) {
        EconomicSet<Register> destroyedRegisters = gatherDestroyedCallerRegisters(gen);
        EconomicMap<LIRFrameState, SaveRegistersOp> calleeSaveInfo = gen.getCalleeSaveInfo();

        if (stub.getLinkage().needsDebugInfo() && calleeSaveInfo.isEmpty()) {
            // This call is a safepoint but no register saving was done so we must ensure that all
            // registers appear to be killed. The Native ABI may allow caller save registers but
            // for HotSpot they must be described in a RegisterMap so they are accessible.
            for (Register r : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                destroyedRegisters.add(r);
            }
        }

        // Only allocatable registers must be described as killed. This works around an issue where
        // the set of allocatable registers is different than the registers actually used for
        // allocation by linear scan on AVX512.
        RegisterAllocationConfig registerAllocationConfig = newRegisterAllocationConfig(frameMap.getRegisterConfig(), null);
        EconomicSet<Register> allocatableRegisters = EconomicSet.create();
        for (Register r : registerAllocationConfig.getAllocatableRegisters()) {
            allocatableRegisters.add(r);
        }
        destroyedRegisters.retainAll(allocatableRegisters);

        stub.initDestroyedCallerRegisters(destroyedRegisters);

        MapCursor<LIRFrameState, SaveRegistersOp> cursor = calleeSaveInfo.getEntries();
        while (cursor.advance()) {
            SaveRegistersOp save = cursor.getValue();
            save.remove(destroyedRegisters);
            if (cursor.getKey().hasDebugInfo()) {
                cursor.getKey().debugInfo().setCalleeSaveInfo(save.getRegisterSaveLayout(frameMap));
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
    public CompiledCode createCompiledCode(ResolvedJavaMethod method,
                    CompilationRequest compilationRequest,
                    CompilationResult compResult,
                    boolean isDefault,
                    OptionValues options) {
        assert !isDefault || compResult.getName() == null : "a default nmethod should have a null name since it is associated with a Method*";
        HotSpotCompilationRequest compRequest = compilationRequest instanceof HotSpotCompilationRequest ? (HotSpotCompilationRequest) compilationRequest : null;
        return HotSpotCompiledCodeBuilder.createCompiledCode(getCodeCache(), method, compRequest, compResult, options);
    }

    @Override
    public CompilationIdentifier getCompilationIdentifier(ResolvedJavaMethod resolvedJavaMethod) {
        if (resolvedJavaMethod instanceof HotSpotResolvedJavaMethod) {
            HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) resolvedJavaMethod, JVMCICompiler.INVOCATION_ENTRY_BCI, 0L);
            return new HotSpotCompilationIdentifier(request);
        }
        return super.getCompilationIdentifier(resolvedJavaMethod);
    }

    /**
     * Gets the minimum alignment for an item in the {@linkplain DataSectionReference data section}.
     */
    protected int getMinDataSectionItemAlignment() {
        HotSpotVMConfigAccess vmAccess = new HotSpotVMConfigAccess(runtime.getVMConfig().getStore());
        int vmValue = vmAccess.getFieldValue("CompilerToVM::Data::data_section_item_alignment", Integer.class, "int", -1);
        // Choosing 4 as minimum (JDK-8283626) if JVMCI does not expose the VM value
        return Math.max(4, vmValue);
    }
}
