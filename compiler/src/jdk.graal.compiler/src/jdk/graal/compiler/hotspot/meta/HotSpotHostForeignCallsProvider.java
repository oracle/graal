/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.meta;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.core.target.Backend.ARITHMETIC_DREM;
import static jdk.graal.compiler.core.target.Backend.ARITHMETIC_FREM;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BASE64_DECODE_BLOCK;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BIGINTEGER_LEFT_SHIFT_WORKER;
import static jdk.graal.compiler.hotspot.HotSpotBackend.BIGINTEGER_RIGHT_SHIFT_WORKER;
import static jdk.graal.compiler.hotspot.HotSpotBackend.CHACHA20Block;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DYNAMIC_NEW_INSTANCE_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static jdk.graal.compiler.hotspot.HotSpotBackend.GALOIS_COUNTER_MODE_CRYPT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.IC_MISS_HANDLER;
import static jdk.graal.compiler.hotspot.HotSpotBackend.MD5_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.MONTGOMERY_MULTIPLY;
import static jdk.graal.compiler.hotspot.HotSpotBackend.MONTGOMERY_SQUARE;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.POLY1305_PROCESSBLOCKS;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA3_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT;
import static jdk.graal.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS_MB;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_ADLER32;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32;
import static jdk.graal.compiler.hotspot.HotSpotBackend.UPDATE_BYTES_CRC32C;
import static jdk.graal.compiler.hotspot.HotSpotBackend.VM_ERROR;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNCOMMON_TRAP;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNPACK;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.ENABLE_STACK_RESERVED_ZONE;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.POLLING_PAGE_RETURN_HANDLER;
import static jdk.graal.compiler.hotspot.HotSpotHostBackend.THROW_DELAYED_STACKOVERFLOW_ERROR;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static jdk.graal.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.G1WBPOSTCALL;
import static jdk.graal.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.G1WBPRECALL;
import static jdk.graal.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.VALIDATE_OBJECT;
import static jdk.graal.compiler.hotspot.replacements.Log.LOG_OBJECT;
import static jdk.graal.compiler.hotspot.replacements.Log.LOG_PRIMITIVE;
import static jdk.graal.compiler.hotspot.replacements.Log.LOG_PRINTF;
import static jdk.graal.compiler.hotspot.replacements.MonitorSnippets.MONITORENTER;
import static jdk.graal.compiler.hotspot.replacements.MonitorSnippets.MONITOREXIT;
import static jdk.graal.compiler.hotspot.stubs.ExceptionHandlerStub.EXCEPTION_HANDLER_FOR_PC;
import static jdk.graal.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static jdk.graal.compiler.hotspot.stubs.UnwindExceptionToCallerStub.EXCEPTION_HANDLER_FOR_RETURN_ADDRESS;
import static jdk.graal.compiler.nodes.java.ForeignCallDescriptors.REGISTER_FINALIZER;
import static jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates.findMethod;
import static jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.word.LocationIdentity.any;

import java.util.EnumMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import jdk.graal.compiler.hotspot.stubs.ArrayStoreExceptionStub;
import jdk.graal.compiler.hotspot.stubs.ClassCastExceptionStub;
import jdk.graal.compiler.hotspot.stubs.CreateExceptionStub;
import jdk.graal.compiler.hotspot.stubs.DivisionByZeroExceptionStub;
import jdk.graal.compiler.hotspot.stubs.ExceptionHandlerStub;
import jdk.graal.compiler.hotspot.stubs.IllegalArgumentExceptionArgumentIsNotAnArrayStub;
import jdk.graal.compiler.hotspot.stubs.IntegerExactOverflowExceptionStub;
import jdk.graal.compiler.hotspot.stubs.IntrinsicStubsGen;
import jdk.graal.compiler.hotspot.stubs.LongExactOverflowExceptionStub;
import jdk.graal.compiler.hotspot.stubs.NegativeArraySizeExceptionStub;
import jdk.graal.compiler.hotspot.stubs.NullPointerExceptionStub;
import jdk.graal.compiler.hotspot.stubs.OutOfBoundsExceptionStub;
import jdk.graal.compiler.hotspot.stubs.SnippetStub;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import jdk.graal.compiler.hotspot.stubs.VerifyOopStub;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.StringLatin1InflateNode;
import jdk.graal.compiler.replacements.StringUTF16CompressNode;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import jdk.graal.compiler.replacements.nodes.AESNode;
import jdk.graal.compiler.replacements.nodes.ArrayCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayEqualsWithMaskForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfForeignCalls;
import jdk.graal.compiler.replacements.nodes.ArrayRegionCompareToForeignCalls;
import jdk.graal.compiler.replacements.nodes.BigIntegerMulAddNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMultiplyToLenNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerSquareToLenNode;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesForeignCalls;
import jdk.graal.compiler.replacements.nodes.CipherBlockChainingAESNode;
import jdk.graal.compiler.replacements.nodes.CountPositivesNode;
import jdk.graal.compiler.replacements.nodes.CounterModeAESNode;
import jdk.graal.compiler.replacements.nodes.EncodeArrayNode;
import jdk.graal.compiler.replacements.nodes.GHASHProcessBlocksNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode;
import jdk.graal.compiler.replacements.nodes.VectorizedHashCodeNode;
import jdk.graal.compiler.replacements.nodes.VectorizedMismatchNode;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot implementation of {@link ForeignCallsProvider}.
 */
public abstract class HotSpotHostForeignCallsProvider extends HotSpotForeignCallsProviderImpl implements ArrayCopyForeignCalls {

    public static final HotSpotForeignCallDescriptor JAVA_TIME_MILLIS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "javaTimeMillis", long.class);
    public static final HotSpotForeignCallDescriptor JAVA_TIME_NANOS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "javaTimeNanos", long.class);

    public static final HotSpotForeignCallDescriptor NOTIFY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "object_notify", int.class, Word.class, Object.class);
    public static final HotSpotForeignCallDescriptor NOTIFY_ALL = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, any(), "object_notifyAll", int.class, Word.class, Object.class);

    public static final HotSpotForeignCallDescriptor INVOKE_STATIC_METHOD_ONE_ARG = new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, NO_LOCATIONS,
                    "JVMCIRuntime::invoke_static_method_one_arg", long.class, Word.class, Word.class, long.class);

    public static final HotSpotForeignCallDescriptor NMETHOD_ENTRY_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS, "nmethod_entry_barrier", void.class);

    public static final HotSpotForeignCallDescriptor G1WBPRECALL_STACK_ONLY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "write_barrier_pre-stack-only",
                    void.class, Object.class);
    public static final HotSpotForeignCallDescriptor G1WBPOSTCALL_STACK_ONLY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NO_SIDE_EFFECT, NO_LOCATIONS, "write_barrier_post-stack-only",
                    void.class, Word.class);

    /*
     * Functions from ZBarrierSetRuntime. The weak_ prefix refers to AS_NO_KEEPALIVE while the extra
     * word before oop_field part refers to java.lang.Reference subclass and corresponds to
     * ON_WEAK_OOP_REF/ON_PHANTOM_OOP_REF in HotSpot decorator terminology.
     */
    public static final HotSpotForeignCallDescriptor Z_FIELD_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS, "load_barrier_on_oop_field_preloaded",
                    long.class, long.class, long.class);
    public static final HotSpotForeignCallDescriptor Z_REFERENCE_GET_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS,
                    "load_barrier_on_weak_oop_field_preloaded",
                    long.class, long.class, long.class);
    public static final HotSpotForeignCallDescriptor Z_WEAK_REFERS_TO_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS,
                    "weak_load_barrier_on_weak_oop_field_preloaded", long.class, long.class, long.class);
    public static final HotSpotForeignCallDescriptor Z_PHANTOM_REFERS_TO_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS,
                    "weak_load_barrier_on_phantom_oop_field_preloaded", long.class, long.class, long.class);
    public static final HotSpotForeignCallDescriptor Z_ARRAY_BARRIER = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, NO_LOCATIONS, "load_barrier_on_oop_array",
                    void.class, long.class, long.class);

    /**
     * Signature of an unsafe {@link System#arraycopy} stub.
     *
     * The signature is equivalent to {@link sun.misc.Unsafe#copyMemory(long, long, long)}. For the
     * semantics refer to {@link sun.misc.Unsafe#copyMemory(Object, long, Object, long, long)}.
     *
     * @see sun.misc.Unsafe#copyMemory
     */
    public static final ForeignCallSignature UNSAFE_ARRAYCOPY = new ForeignCallSignature("unsafe_arraycopy", void.class, Word.class, Word.class, Word.class);

    /**
     * Signature of a generic {@link System#arraycopy} stub.
     *
     * Instead of throwing an {@link ArrayStoreException}, the stub is expected to return the number
     * of copied elements xor'd with {@code -1}. A return value of {@code 0} indicates that the
     * operation was successful.
     */
    public static final ForeignCallSignature GENERIC_ARRAYCOPY = new ForeignCallSignature("generic_arraycopy", int.class, Word.class, int.class, Word.class, int.class, int.class);

    public static class TestForeignCalls {

        /**
         * Kinds for which foreign call stubs are created when building the libgraal image.
         */
        public static final JavaKind[] KINDS = {JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Int, JavaKind.Long, JavaKind.Object};

        /**
         * Creates a {@link HotSpotForeignCallDescriptor} for a foreign call stub to a method named
         * {@code <kind>Returns<Kind>} (e.g., "byteReturnsByte") with a signature of
         * {@code (<kind>)<kind>} (e.g., {@code (byte)byte}).
         */
        public static HotSpotForeignCallDescriptor createStubCallDescriptor(JavaKind kind) {
            String name = kind.isObject() ? "objectReturnsObject" : kind.getJavaName() + "Returns" + capitalize(kind.getJavaName());
            Class<?> javaClass = kind.isObject() ? Object.class : kind.toJavaClass();
            return new HotSpotForeignCallDescriptor(SAFEPOINT, NO_SIDE_EFFECT, NO_LOCATIONS, name, javaClass, javaClass);
        }

        static boolean booleanReturnsBoolean(boolean arg) {
            return arg;
        }

        static byte byteReturnsByte(byte arg) {
            return arg;
        }

        static short shortReturnsShort(short arg) {
            return arg;
        }

        static char charReturnsChar(char arg) {
            return arg;
        }

        static int intReturnsInt(int arg) {
            return arg;
        }

        static long longReturnsLong(long arg) {
            return arg;
        }

        static Object objectReturnsObject(Object arg) {
            return arg;
        }
    }

    public HotSpotHostForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime,
                    HotSpotGraalRuntimeProvider runtime,
                    MetaAccessProvider metaAccess,
                    CodeCacheProvider codeCache,
                    WordTypes wordTypes) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
    }

    protected static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    /**
     * Looks up the call descriptor for a fast checkcast {@link System#arraycopy} stub.
     *
     * @see CheckcastArrayCopyCallNode
     */
    public ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        return checkcastArraycopyDescriptors[uninit ? 1 : 0];
    }

    @Override
    public ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation) {
        // We support Object arraycopy killing the Object array location or ANY. We support
        // primitive arraycopy killing the kind's array location or INIT.
        // This is enough for well-typed copies and for the kind of type punning done by
        // StringUTF16Substitutions#getChars. This will need more work if at some point we need to
        // support more general type punning, e.g., writing char-typed data into a byte array.
        boolean killAny = killedLocation.isAny();
        boolean killInit = killedLocation.isInit();
        if (kind.isObject()) {
            assert !killInit : "unsupported";
            assert killAny || killedLocation.equals(NamedLocationIdentity.getArrayLocation(kind));
            return objectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0][uninit ? 1 : 0][killAny ? 1 : 0];
        } else {
            assert kind.isPrimitive();
            assert !killAny : "unsupported";
            assert killInit || killedLocation.equals(NamedLocationIdentity.getArrayLocation(kind));
            return primitiveArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0][killInit ? 1 : 0].get(kind);
        }
    }

    // indexed by aligned, disjoint, killInit
    @SuppressWarnings("unchecked") private static final EnumMap<JavaKind, ForeignCallDescriptor>[][][] primitiveArraycopyDescriptors = (EnumMap<JavaKind, ForeignCallDescriptor>[][][]) new EnumMap<?, ?>[2][2][2];

    // indexed by aligned, disjoint, uninit, killAny
    private static final ForeignCallDescriptor[][][][] objectArraycopyDescriptors = new ForeignCallDescriptor[2][2][2][2];
    // indexed by uninit
    private static final ForeignCallDescriptor[] checkcastArraycopyDescriptors = new ForeignCallDescriptor[2];

    static {
        // Populate the EnumMap instances
        for (int i = 0; i < primitiveArraycopyDescriptors.length; i++) {
            for (int j = 0; j < primitiveArraycopyDescriptors[i].length; j++) {
                for (int k = 0; k < primitiveArraycopyDescriptors[i][j].length; k++) {
                    primitiveArraycopyDescriptors[i][j][k] = new EnumMap<>(JavaKind.class);
                }
            }
        }
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void registerStubCallFunctions(OptionValues options, HotSpotProviders providers, GraalHotSpotVMConfig config) {
        long invokeJavaMethodAddress = config.invokeJavaMethodAddress;
        if (invokeJavaMethodAddress == 0) {
            return;
        }
        registerForeignCall(INVOKE_STATIC_METHOD_ONE_ARG, invokeJavaMethodAddress, NativeCall);

        if (!IS_IN_NATIVE_IMAGE) {
            /*
             * These functions are only used for testing purposes but their registration also
             * ensures that libgraal has support for InvokeJavaMethodStub built into the image,
             * which is required for support of Truffle. Because of the lazy initialization of this
             * support in Truffle we rely on this code to ensure the support is built into the
             * image. In particular this results in InvokeJavaMethodStub being in the image but not
             * TestForeignCalls itself.
             */
            for (JavaKind kind : TestForeignCalls.KINDS) {
                HotSpotForeignCallDescriptor desc = TestForeignCalls.createStubCallDescriptor(kind);
                ResolvedJavaMethod method = findMethod(providers.getMetaAccess(), TestForeignCalls.class, desc.getName());
                invokeJavaMethodStub(options, providers, desc, invokeJavaMethodAddress, method);
            }
        }
    }

    private void registerArraycopyDescriptor(EconomicMap<Long, ForeignCallDescriptor> descMap, JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation,
                    long routine) {
        boolean killAny = killedLocation.isAny();
        boolean killInit = killedLocation.isInit();
        ForeignCallDescriptor desc = descMap.get(routine);
        if (desc == null) {
            desc = buildDescriptor(kind, aligned, disjoint, uninit, killedLocation, routine);
            descMap.put(routine, desc);
        }
        if (kind.isObject()) {
            objectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0][uninit ? 1 : 0][killAny ? 1 : 0] = desc;
        } else {
            primitiveArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0][killInit ? 1 : 0].put(kind, desc);
        }
    }

    private ForeignCallDescriptor buildDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation, long routine) {
        assert !uninit || kind == JavaKind.Object : Assertions.errorMessage(kind, aligned, disjoint, uninit, killedLocation, routine);
        boolean killAny = killedLocation.isAny();
        boolean killInit = killedLocation.isInit();
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + (uninit ? "Uninit" : "") + "Arraycopy" + (killInit ? "KillInit" : killAny ? "KillAny" : "");
        HotSpotForeignCallDescriptor desc = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, killedLocation, name, void.class, Word.class, Word.class, Word.class);
        registerForeignCall(desc, routine, NativeCall);
        return desc;
    }

    private void registerCheckcastArraycopyDescriptor(boolean uninit, long routine) {
        String name = "Object" + (uninit ? "Uninit" : "") + "CheckcastArraycopy";
        // Input:
        // c_rarg0 - source array address
        // c_rarg1 - destination array address
        // c_rarg2 - element count, treated as ssize_t, can be zero
        // c_rarg3 - size_t ckoff (super_check_offset)
        // c_rarg4 - oop ckval (super_klass)
        // return: 0 = success, n = number of copied elements xor'd with -1.
        LocationIdentity killed = NamedLocationIdentity.any();
        HotSpotForeignCallDescriptor desc = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, HAS_SIDE_EFFECT, killed, name, int.class, Word.class, Word.class, Word.class, Word.class,
                        Word.class);
        registerForeignCall(desc, routine, NativeCall);
        checkcastArraycopyDescriptors[uninit ? 1 : 0] = desc;
    }

    private void registerArrayCopy(JavaKind kind,
                    long routine,
                    long alignedRoutine,
                    long disjointRoutine,
                    long alignedDisjointRoutine) {
        registerArrayCopy(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, false);
    }

    private void registerArrayCopy(JavaKind kind,
                    long routine,
                    long alignedRoutine,
                    long disjointRoutine,
                    long alignedDisjointRoutine,
                    boolean uninit) {
        /*
         * Sometimes the same function is used for multiple cases so share them when that's the case
         * but only within the same Kind. For instance short and char are the same copy routines but
         * they kill different memory so they still have to be distinct.
         */
        EconomicMap<Long, ForeignCallDescriptor> descMap = EconomicMap.create();
        LocationIdentity arrayLocation = NamedLocationIdentity.getArrayLocation(kind);
        registerArraycopyDescriptor(descMap, kind, false, false, uninit, arrayLocation, routine);
        registerArraycopyDescriptor(descMap, kind, true, false, uninit, arrayLocation, alignedRoutine);
        registerArraycopyDescriptor(descMap, kind, false, true, uninit, arrayLocation, disjointRoutine);
        registerArraycopyDescriptor(descMap, kind, true, true, uninit, arrayLocation, alignedDisjointRoutine);

        if (kind.isPrimitive()) {
            assert !uninit : Assertions.errorMessage(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, uninit);
            EconomicMap<Long, ForeignCallDescriptor> killInitDescMap = EconomicMap.create();
            registerArraycopyDescriptor(killInitDescMap, kind, false, false, uninit, LocationIdentity.init(), routine);
            registerArraycopyDescriptor(killInitDescMap, kind, true, false, uninit, LocationIdentity.init(), alignedRoutine);
            registerArraycopyDescriptor(killInitDescMap, kind, false, true, uninit, LocationIdentity.init(), disjointRoutine);
            registerArraycopyDescriptor(killInitDescMap, kind, true, true, uninit, LocationIdentity.init(), alignedDisjointRoutine);
        } else {
            assert kind.isObject() : Assertions.errorMessage(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, uninit);
            EconomicMap<Long, ForeignCallDescriptor> killAnyDescMap = EconomicMap.create();
            registerArraycopyDescriptor(killAnyDescMap, kind, false, false, uninit, LocationIdentity.any(), routine);
            registerArraycopyDescriptor(killAnyDescMap, kind, true, false, uninit, LocationIdentity.any(), alignedRoutine);
            registerArraycopyDescriptor(killAnyDescMap, kind, false, true, uninit, LocationIdentity.any(), disjointRoutine);
            registerArraycopyDescriptor(killAnyDescMap, kind, true, true, uninit, LocationIdentity.any(), alignedDisjointRoutine);
        }
    }

    public void initialize(HotSpotProviders providers, OptionValues options) {
        GraalHotSpotVMConfig c = runtime.getVMConfig();
        registerForeignCall(DEOPT_BLOB_UNPACK, c.deoptBlobUnpack, NativeCall);
        if (c.deoptBlobUnpackWithExceptionInTLS != 0) {
            registerForeignCall(DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS, c.deoptBlobUnpackWithExceptionInTLS, NativeCall);
        }
        if (c.pollingPageReturnHandler != 0) {
            registerForeignCall(POLLING_PAGE_RETURN_HANDLER, c.pollingPageReturnHandler, NativeCall);
        }
        registerForeignCall(DEOPT_BLOB_UNCOMMON_TRAP, c.deoptBlobUncommonTrap, NativeCall);
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub, NativeCall);

        if (c.enableStackReservedZoneAddress != 0) {
            assert c.throwDelayedStackOverflowErrorEntry != 0 : "both must exist";
            registerForeignCall(ENABLE_STACK_RESERVED_ZONE, c.enableStackReservedZoneAddress, NativeCall);
            registerForeignCall(THROW_DELAYED_STACKOVERFLOW_ERROR, c.throwDelayedStackOverflowErrorEntry, NativeCall);
        }

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall);

        registerMathStubs(c, providers, options);

        registerForeignCall(createDescriptor(ARITHMETIC_FREM, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), c.fremAddress, NativeCall);
        registerForeignCall(createDescriptor(ARITHMETIC_DREM, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), c.dremAddress, NativeCall);

        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall);

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall);
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall);

        CreateExceptionStub.registerForeignCalls(c, this);

        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall);

        linkForeignCall(options, providers, NEW_INSTANCE_OR_NULL, c.newInstanceOrNullAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, NEW_ARRAY_OR_NULL, c.newArrayOrNullAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, NEW_MULTI_ARRAY_OR_NULL, c.newMultiArrayOrNullAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, DYNAMIC_NEW_INSTANCE_OR_NULL, c.dynamicNewInstanceOrNullAddress, PREPEND_THREAD);

        if (c.supportJVMTIVThreadNotification()) {
            linkForeignCall(options, providers, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_START, c.jvmtiVThreadStart, DONT_PREPEND_THREAD);
            linkForeignCall(options, providers, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_END, c.jvmtiVThreadEnd, DONT_PREPEND_THREAD);
            linkForeignCall(options, providers, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_MOUNT, c.jvmtiVThreadMount, DONT_PREPEND_THREAD);
            linkForeignCall(options, providers, SHAREDRUNTIME_NOTIFY_JVMTI_VTHREAD_UNMOUNT, c.jvmtiVThreadUnmount, DONT_PREPEND_THREAD);
        }

        link(new ExceptionHandlerStub(options, providers, foreignCalls.get(EXCEPTION_HANDLER.getSignature())));
        link(new UnwindExceptionToCallerStub(options, providers,
                        registerStubCall(UNWIND_EXCEPTION_TO_CALLER, DESTROYS_ALL_CALLER_SAVE_REGISTERS)));
        link(new VerifyOopStub(options, providers, registerStubCall(VERIFY_OOP, DESTROYS_ALL_CALLER_SAVE_REGISTERS)));

        EnumMap<BytecodeExceptionKind, ForeignCallSignature> exceptionRuntimeCalls = DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls;
        link(new ArrayStoreExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.ARRAY_STORE), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new ClassCastExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.CLASS_CAST), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new NullPointerExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.NULL_POINTER), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new OutOfBoundsExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.OUT_OF_BOUNDS), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new NegativeArraySizeExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new DivisionByZeroExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.DIVISION_BY_ZERO), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new IntegerExactOverflowExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.INTEGER_EXACT_OVERFLOW), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new LongExactOverflowExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.LONG_EXACT_OVERFLOW), SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new IllegalArgumentExceptionArgumentIsNotAnArrayStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY),
                                        SAFEPOINT, HAS_SIDE_EFFECT, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));

        linkForeignCall(options, providers, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, createDescriptor(REGISTER_FINALIZER, SAFEPOINT, HAS_SIDE_EFFECT, any()), c.registerFinalizerAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD);
        registerForeignCall(NOTIFY, c.notifyAddress, NativeCall);
        registerForeignCall(NOTIFY_ALL, c.notifyAllAddress, NativeCall);

        if (c.nmethodEntryBarrier != 0) {
            registerForeignCall(NMETHOD_ENTRY_BARRIER, c.nmethodEntryBarrier, NativeCall);
        } else if (IS_BUILDING_NATIVE_IMAGE) {
            // Ensure this is known to libgraal
            register(NMETHOD_ENTRY_BARRIER.getSignature());
        }

        if (c.zBarrierSetRuntimeLoadBarrierOnOopFieldPreloaded != 0) {
            linkStackOnlyForeignCall(options, providers, Z_FIELD_BARRIER, c.zBarrierSetRuntimeLoadBarrierOnOopFieldPreloaded, DONT_PREPEND_THREAD);
            linkStackOnlyForeignCall(options, providers, Z_REFERENCE_GET_BARRIER, c.zBarrierSetRuntimeLoadBarrierOnWeakOopFieldPreloaded, DONT_PREPEND_THREAD);
            linkStackOnlyForeignCall(options, providers, Z_WEAK_REFERS_TO_BARRIER, c.zBarrierSetRuntimeWeakLoadBarrierOnWeakOopFieldPreloaded, DONT_PREPEND_THREAD);
            linkStackOnlyForeignCall(options, providers, Z_PHANTOM_REFERS_TO_BARRIER, c.zBarrierSetRuntimeWeakLoadBarrierOnPhantomOopFieldPreloaded, DONT_PREPEND_THREAD);
            linkStackOnlyForeignCall(options, providers, Z_ARRAY_BARRIER, c.zBarrierSetRuntimeLoadBarrierOnOopArray, DONT_PREPEND_THREAD);
        } else if (IS_BUILDING_NATIVE_IMAGE) {
            // Ensure these are known to libgraal
            register(Z_FIELD_BARRIER.getSignature());
            register(Z_REFERENCE_GET_BARRIER.getSignature());
            register(Z_WEAK_REFERS_TO_BARRIER.getSignature());
            register(Z_PHANTOM_REFERS_TO_BARRIER.getSignature());
            register(Z_ARRAY_BARRIER.getSignature());
        }

        linkStackOnlyForeignCall(options, providers, G1WBPRECALL_STACK_ONLY, c.writeBarrierPreAddress, PREPEND_THREAD);
        linkStackOnlyForeignCall(options, providers, G1WBPOSTCALL_STACK_ONLY, c.writeBarrierPostAddress, PREPEND_THREAD);

        linkForeignCall(options, providers, LOG_PRINTF, c.logPrintfAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, LOG_OBJECT, c.logObjectAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, LOG_PRIMITIVE, c.logPrimitiveAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, VM_ERROR, c.vmErrorAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, OSR_MIGRATION_END, c.osrMigrationEndAddress, DONT_PREPEND_THREAD);
        linkForeignCall(options, providers, G1WBPRECALL, c.writeBarrierPreAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, G1WBPOSTCALL, c.writeBarrierPostAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, VALIDATE_OBJECT, c.validateObject, PREPEND_THREAD);

        linkForeignCall(options, providers, TEST_DEOPTIMIZE_CALL_INT, c.testDeoptimizeCallInt, PREPEND_THREAD);

        registerArrayCopy(JavaKind.Byte, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Boolean, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Char, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Short, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Int, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Float, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Long, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Double, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Object, c.oopArraycopy, c.oopAlignedArraycopy, c.oopDisjointArraycopy, c.oopAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Object, c.oopArraycopyUninit, c.oopAlignedArraycopyUninit, c.oopDisjointArraycopyUninit, c.oopAlignedDisjointArraycopyUninit, true);

        registerCheckcastArraycopyDescriptor(true, c.checkcastArraycopyUninit);
        registerCheckcastArraycopyDescriptor(false, c.checkcastArraycopy);

        registerForeignCall(createDescriptor(GENERIC_ARRAYCOPY, LEAF_NO_VZERO, HAS_SIDE_EFFECT, NamedLocationIdentity.any()), c.genericArraycopy, NativeCall);
        registerForeignCall(createDescriptor(UNSAFE_ARRAYCOPY, LEAF_NO_VZERO, HAS_SIDE_EFFECT, NamedLocationIdentity.any()), c.unsafeArraycopy, NativeCall);

        if (c.md5ImplCompressMultiBlock != 0L) {
            registerForeignCall(MD5_IMPL_COMPRESS_MB, c.md5ImplCompressMultiBlock, NativeCall);
        }
        if (c.sha1ImplCompressMultiBlock != 0L) {
            registerForeignCall(SHA_IMPL_COMPRESS_MB, c.sha1ImplCompressMultiBlock, NativeCall);
        }
        if (c.sha256ImplCompressMultiBlock != 0L) {
            registerForeignCall(SHA2_IMPL_COMPRESS_MB, c.sha256ImplCompressMultiBlock, NativeCall);
        }
        if (c.sha512ImplCompressMultiBlock != 0L) {
            registerForeignCall(SHA5_IMPL_COMPRESS_MB, c.sha512ImplCompressMultiBlock, NativeCall);
        }
        if (c.sha3ImplCompressMultiBlock != 0L) {
            registerForeignCall(SHA3_IMPL_COMPRESS_MB, c.sha3ImplCompressMultiBlock, NativeCall);
        }
        if (c.base64EncodeBlock != 0L) {
            registerForeignCall(BASE64_ENCODE_BLOCK, c.base64EncodeBlock, NativeCall);
        }
        if (c.base64DecodeBlock != 0L) {
            registerForeignCall(BASE64_DECODE_BLOCK, c.base64DecodeBlock, NativeCall);
        }
        if (c.montgomeryMultiply != 0L) {
            registerForeignCall(MONTGOMERY_MULTIPLY, c.montgomeryMultiply, NativeCall);
        }
        if (c.montgomerySquare != 0L) {
            registerForeignCall(MONTGOMERY_SQUARE, c.montgomerySquare, NativeCall);
        }
        if (c.updateBytesCRC32Stub != 0L) {
            // This stub does callee saving
            registerForeignCall(UPDATE_BYTES_CRC32, c.updateBytesCRC32Stub, NativeCall);
        }
        if (c.updateBytesCRC32C != 0L) {
            registerForeignCall(UPDATE_BYTES_CRC32C, c.updateBytesCRC32C, NativeCall);
        }
        if (c.updateBytesAdler32 != 0L) {
            registerForeignCall(UPDATE_BYTES_ADLER32, c.updateBytesAdler32, NativeCall);
        }
        if (c.bigIntegerLeftShiftWorker != 0L) {
            registerForeignCall(BIGINTEGER_LEFT_SHIFT_WORKER, c.bigIntegerLeftShiftWorker, NativeCall);
        }
        if (c.bigIntegerRightShiftWorker != 0L) {
            registerForeignCall(BIGINTEGER_RIGHT_SHIFT_WORKER, c.bigIntegerRightShiftWorker, NativeCall);
        }
        if (c.electronicCodeBookEncrypt != 0L) {
            registerForeignCall(ELECTRONIC_CODEBOOK_ENCRYPT_AESCRYPT, c.electronicCodeBookEncrypt, NativeCall);
        }
        if (c.electronicCodeBookDecrypt != 0L) {
            registerForeignCall(ELECTRONIC_CODEBOOK_DECRYPT_AESCRYPT, c.electronicCodeBookDecrypt, NativeCall);
        }
        if (c.galoisCounterModeCrypt != 0L) {
            registerForeignCall(GALOIS_COUNTER_MODE_CRYPT, c.galoisCounterModeCrypt, NativeCall);
        }
        if (c.poly1305ProcessBlocks != 0L) {
            registerForeignCall(POLY1305_PROCESSBLOCKS, c.poly1305ProcessBlocks, NativeCall);
        }
        if (c.chacha20Block != 0L) {
            registerForeignCall(CHACHA20Block, c.chacha20Block, NativeCall);
        }

        registerSnippetStubs(providers, options);
        registerStubCallFunctions(options, providers, runtime.getVMConfig());
    }

    @SuppressWarnings("unused")
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        registerForeignCall(createDescriptor(SIN.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticSinAddress, NativeCall);
        registerForeignCall(createDescriptor(COS.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticCosAddress, NativeCall);
        registerForeignCall(createDescriptor(TAN.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticTanAddress, NativeCall);
        registerForeignCall(createDescriptor(EXP.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticExpAddress, NativeCall);
        registerForeignCall(createDescriptor(LOG.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticLogAddress, NativeCall);
        registerForeignCall(createDescriptor(LOG10.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticLog10Address, NativeCall);
        registerForeignCall(createDescriptor(POW.foreignCallSignature, LEAF, NO_SIDE_EFFECT, NO_LOCATIONS), hotSpotVMConfig.arithmeticPowAddress, NativeCall);
    }

    private void registerSnippetStubs(HotSpotProviders providers, OptionValues options) {
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayIndexOfForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayEqualsForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayEqualsWithMaskForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayCompareToForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayRegionCompareToForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, ArrayCopyWithConversionsForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, CalcStringAttributesForeignCalls.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, StringLatin1InflateNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, StringUTF16CompressNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, EncodeArrayNode.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, CountPositivesNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, VectorizedMismatchNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, VectorizedHashCodeNode.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, BigIntegerMultiplyToLenNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, BigIntegerMulAddNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, BigIntegerSquareToLenNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, AESNode.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, CounterModeAESNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, CipherBlockChainingAESNode.STUBS);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, GHASHProcessBlocksNode.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, MessageDigestNode.SHA1Node.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, MessageDigestNode.SHA256Node.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, MessageDigestNode.SHA3Node.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, MessageDigestNode.SHA512Node.STUB);
        linkSnippetStubs(providers, options, IntrinsicStubsGen::new, MessageDigestNode.MD5Node.STUB);
    }

    @FunctionalInterface
    protected interface SnippetStubConstructor<A extends SnippetStub> {
        A apply(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage);
    }

    protected <A extends SnippetStub> void linkSnippetStubs(HotSpotProviders providers, OptionValues options, SnippetStubConstructor<A> constructor, ForeignCallDescriptor... stubs) {
        for (ForeignCallDescriptor stub : stubs) {
            link(constructor.apply(options, providers, registerStubCall(stub.getSignature(), LEAF, stub.getSideEffect(), COMPUTES_REGISTERS_KILLED, stub.getKilledLocations())));
        }
    }
}
