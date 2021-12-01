/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_DREM;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_FREM;
import static org.graalvm.compiler.hotspot.HotSpotBackend.AESCRYPT_DECRYPTBLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.AESCRYPT_ENCRYPTBLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BASE64_ENCODE_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CIPHER_BLOCK_CHAINING_DECRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.CIPHER_BLOCK_CHAINING_ENCRYPT_AESCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.COUNTERMODE_IMPL_CRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.GHASH_PROCESS_BLOCKS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.IC_MISS_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_MULTIPLY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_SQUARE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MULTIPLY_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MUL_ADD;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SQUARE_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.VECTORIZED_MISMATCH;
import static org.graalvm.compiler.hotspot.HotSpotBackend.VM_ERROR;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.COMPUTES_REGISTERS_KILLED;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNCOMMON_TRAP;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNPACK;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.ENABLE_STACK_RESERVED_ZONE;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.THROW_DELAYED_STACKOVERFLOW_ERROR;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.replacements.AssertionSnippets.ASSERTION_VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.DYNAMIC_NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.replacements.HotSpotAllocationSnippets.DYNAMIC_NEW_INSTANCE_OR_NULL;
import static org.graalvm.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.G1WBPOSTCALL;
import static org.graalvm.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.G1WBPRECALL;
import static org.graalvm.compiler.hotspot.replacements.HotSpotG1WriteBarrierSnippets.VALIDATE_OBJECT;
import static org.graalvm.compiler.hotspot.replacements.Log.LOG_OBJECT;
import static org.graalvm.compiler.hotspot.replacements.Log.LOG_PRIMITIVE;
import static org.graalvm.compiler.hotspot.replacements.Log.LOG_PRINTF;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITORENTER;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITOREXIT;
import static org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub.EXCEPTION_HANDLER_FOR_PC;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub.EXCEPTION_HANDLER_FOR_RETURN_ADDRESS;
import static org.graalvm.compiler.nodes.java.ForeignCallDescriptors.REGISTER_FINALIZER;
import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static org.graalvm.word.LocationIdentity.any;

import java.util.EnumMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.ArrayIndexOfStub;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.replacements.arraycopy.CheckcastArrayCopyCallNode;
import org.graalvm.compiler.hotspot.stubs.ArrayStoreExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ClassCastExceptionStub;
import org.graalvm.compiler.hotspot.stubs.CreateExceptionStub;
import org.graalvm.compiler.hotspot.stubs.DivisionByZeroExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub;
import org.graalvm.compiler.hotspot.stubs.IllegalArgumentExceptionArgumentIsNotAnArrayStub;
import org.graalvm.compiler.hotspot.stubs.IntegerExactOverflowExceptionStub;
import org.graalvm.compiler.hotspot.stubs.LongExactOverflowExceptionStub;
import org.graalvm.compiler.hotspot.stubs.NegativeArraySizeExceptionStub;
import org.graalvm.compiler.hotspot.stubs.NullPointerExceptionStub;
import org.graalvm.compiler.hotspot.stubs.OutOfBoundsExceptionStub;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import org.graalvm.compiler.hotspot.stubs.VerifyOopStub;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.ArrayIndexOf;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot implementation of {@link ForeignCallsProvider}.
 */
public abstract class HotSpotHostForeignCallsProvider extends HotSpotForeignCallsProviderImpl implements ArrayCopyForeignCalls {

    public static final HotSpotForeignCallDescriptor JAVA_TIME_MILLIS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "javaTimeMillis", long.class);
    public static final HotSpotForeignCallDescriptor JAVA_TIME_NANOS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "javaTimeNanos", long.class);

    public static final HotSpotForeignCallDescriptor NOTIFY = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, any(), "object_notify", boolean.class, Object.class);
    public static final HotSpotForeignCallDescriptor NOTIFY_ALL = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, any(), "object_notifyAll", boolean.class, Object.class);

    public static final HotSpotForeignCallDescriptor INVOKE_STATIC_METHOD_ONE_ARG = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS,
                    "JVMCIRuntime::invoke_static_method_one_arg", long.class, Word.class, Word.class, long.class);

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
        public static final HotSpotForeignCallDescriptor BOOLEAN_RETURNS_BOOLEAN = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "boolean returns boolean",
                        Boolean.TYPE, Boolean.TYPE);
        public static final HotSpotForeignCallDescriptor BYTE_RETURNS_BYTE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "byte returns byte",
                        Byte.TYPE, Byte.TYPE);
        public static final HotSpotForeignCallDescriptor SHORT_RETURNS_SHORT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "short returns short",
                        Short.TYPE, Short.TYPE);
        public static final HotSpotForeignCallDescriptor CHAR_RETURNS_CHAR = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "char returns char",
                        Character.TYPE, Character.TYPE);
        public static final HotSpotForeignCallDescriptor INT_RETURNS_INT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "int returns int",
                        Integer.TYPE, Integer.TYPE);
        public static final HotSpotForeignCallDescriptor LONG_RETURNS_LONG = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "long returns long",
                        Long.TYPE, Long.TYPE);
        public static final HotSpotForeignCallDescriptor FLOAT_RETURNS_FLOAT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "float returns float",
                        Float.TYPE, Float.TYPE);
        public static final HotSpotForeignCallDescriptor DOUBLE_RETURNS_DOUBLE = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "double returns double",
                        Double.TYPE, Double.TYPE);
        public static final HotSpotForeignCallDescriptor OBJECT_RETURNS_OBJECT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, NO_LOCATIONS, "object returns object",
                        Object.class, Object.class);

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

    public HotSpotHostForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
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

    private boolean registerStubCallFunctions(OptionValues options, HotSpotProviders providers, GraalHotSpotVMConfig config) {
        if (config.invokeJavaMethodAddress == 0) {
            return true;
        }
        ResolvedJavaMethod booleanReturnsBoolean = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "booleanReturnsBoolean");
        invokeJavaMethodStub(options, providers, TestForeignCalls.BOOLEAN_RETURNS_BOOLEAN, config.invokeJavaMethodAddress, booleanReturnsBoolean);
        ResolvedJavaMethod byteReturnsByte = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "byteReturnsByte");
        invokeJavaMethodStub(options, providers, TestForeignCalls.BYTE_RETURNS_BYTE, config.invokeJavaMethodAddress, byteReturnsByte);
        ResolvedJavaMethod shortReturnsShort = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "shortReturnsShort");
        invokeJavaMethodStub(options, providers, TestForeignCalls.SHORT_RETURNS_SHORT, config.invokeJavaMethodAddress, shortReturnsShort);
        ResolvedJavaMethod charReturnsChar = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "charReturnsChar");
        invokeJavaMethodStub(options, providers, TestForeignCalls.CHAR_RETURNS_CHAR, config.invokeJavaMethodAddress, charReturnsChar);
        ResolvedJavaMethod intReturnsInt = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "intReturnsInt");
        invokeJavaMethodStub(options, providers, TestForeignCalls.INT_RETURNS_INT, config.invokeJavaMethodAddress, intReturnsInt);
        ResolvedJavaMethod longReturnsLong = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "longReturnsLong");
        invokeJavaMethodStub(options, providers, TestForeignCalls.LONG_RETURNS_LONG, config.invokeJavaMethodAddress, longReturnsLong);
        ResolvedJavaMethod objectReturnsObject = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), TestForeignCalls.class, "objectReturnsObject");
        invokeJavaMethodStub(options, providers, TestForeignCalls.OBJECT_RETURNS_OBJECT, config.invokeJavaMethodAddress, objectReturnsObject);

        return true;
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
        assert !uninit || kind == JavaKind.Object;
        boolean killAny = killedLocation.isAny();
        boolean killInit = killedLocation.isInit();
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + (uninit ? "Uninit" : "") + "Arraycopy" + (killInit ? "KillInit" : killAny ? "KillAny" : "");
        HotSpotForeignCallDescriptor desc = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, killedLocation, name, void.class, Word.class, Word.class, Word.class);
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
        HotSpotForeignCallDescriptor desc = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, killed, name, int.class, Word.class, Word.class, Word.class, Word.class,
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
            assert !uninit;
            EconomicMap<Long, ForeignCallDescriptor> killInitDescMap = EconomicMap.create();
            registerArraycopyDescriptor(killInitDescMap, kind, false, false, uninit, LocationIdentity.init(), routine);
            registerArraycopyDescriptor(killInitDescMap, kind, true, false, uninit, LocationIdentity.init(), alignedRoutine);
            registerArraycopyDescriptor(killInitDescMap, kind, false, true, uninit, LocationIdentity.init(), disjointRoutine);
            registerArraycopyDescriptor(killInitDescMap, kind, true, true, uninit, LocationIdentity.init(), alignedDisjointRoutine);
        } else {
            assert kind.isObject();
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

        registerForeignCall(createDescriptor(ARITHMETIC_FREM, LEAF, REEXECUTABLE, NO_LOCATIONS), c.fremAddress, NativeCall);
        registerForeignCall(createDescriptor(ARITHMETIC_DREM, LEAF, REEXECUTABLE, NO_LOCATIONS), c.dremAddress, NativeCall);

        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall);

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall);
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall);

        CreateExceptionStub.registerForeignCalls(c, this);

        /*
         * This message call is registered twice, where the second one must only be used for calls
         * that do not return, i.e., that exit the VM.
         */
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall);
        registerForeignCall(ASSERTION_VM_MESSAGE_C, c.vmMessageAddress, NativeCall);

        linkForeignCall(options, providers, NEW_INSTANCE, c.newInstanceAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, NEW_ARRAY, c.newArrayAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, NEW_MULTI_ARRAY, c.newMultiArrayAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, DYNAMIC_NEW_INSTANCE, c.dynamicNewInstanceAddress, PREPEND_THREAD);

        if (c.areNullAllocationStubsAvailable()) {
            linkForeignCall(options, providers, NEW_INSTANCE_OR_NULL, c.newInstanceOrNullAddress, PREPEND_THREAD);
            linkForeignCall(options, providers, NEW_ARRAY_OR_NULL, c.newArrayOrNullAddress, PREPEND_THREAD);
            linkForeignCall(options, providers, NEW_MULTI_ARRAY_OR_NULL, c.newMultiArrayOrNullAddress, PREPEND_THREAD);
            linkForeignCall(options, providers, DYNAMIC_NEW_INSTANCE_OR_NULL, c.dynamicNewInstanceOrNullAddress, PREPEND_THREAD);
        }

        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_1_BYTE, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_1_CHAR_COMPACT, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS_COMPACT, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_2_BYTES, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_3_BYTES, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_4_BYTES, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_1_CHAR, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_2_CHARS, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_3_CHARS, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_4_CHARS, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_2_CHARS_COMPACT, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_3_CHARS_COMPACT, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));
        link(new ArrayIndexOfStub(options, providers,
                        registerStubCall(ArrayIndexOf.STUB_INDEX_OF_4_CHARS_COMPACT, LEAF, REEXECUTABLE, COMPUTES_REGISTERS_KILLED, NO_LOCATIONS)));

        link(new ExceptionHandlerStub(options, providers, foreignCalls.get(EXCEPTION_HANDLER.getSignature())));
        link(new UnwindExceptionToCallerStub(options, providers,
                        registerStubCall(UNWIND_EXCEPTION_TO_CALLER, DESTROYS_ALL_CALLER_SAVE_REGISTERS)));
        link(new VerifyOopStub(options, providers, registerStubCall(VERIFY_OOP, DESTROYS_ALL_CALLER_SAVE_REGISTERS)));

        EnumMap<BytecodeExceptionKind, ForeignCallSignature> exceptionRuntimeCalls = DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls;
        link(new ArrayStoreExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.ARRAY_STORE), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new ClassCastExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.CLASS_CAST), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new NullPointerExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.NULL_POINTER), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new OutOfBoundsExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.OUT_OF_BOUNDS), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new NegativeArraySizeExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.NEGATIVE_ARRAY_SIZE), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new DivisionByZeroExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.DIVISION_BY_ZERO), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new IntegerExactOverflowExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.INTEGER_EXACT_OVERFLOW), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new LongExactOverflowExceptionStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.LONG_EXACT_OVERFLOW), SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));
        link(new IllegalArgumentExceptionArgumentIsNotAnArrayStub(options, providers,
                        registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.ILLEGAL_ARGUMENT_EXCEPTION_ARGUMENT_IS_NOT_AN_ARRAY),
                                        SAFEPOINT, NOT_REEXECUTABLE, DESTROYS_ALL_CALLER_SAVE_REGISTERS, any())));

        linkForeignCall(options, providers, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, createDescriptor(REGISTER_FINALIZER, SAFEPOINT, NOT_REEXECUTABLE, any()), c.registerFinalizerAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD);
        linkForeignCall(options, providers, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD);
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            linkForeignCall(options, providers, NOTIFY, c.notifyAddress, PREPEND_THREAD);
            linkForeignCall(options, providers, NOTIFY_ALL, c.notifyAllAddress, PREPEND_THREAD);
        } else {
            assert c.notifyAddress == 0 : "unexpected value";
        }
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

        registerForeignCall(createDescriptor(GENERIC_ARRAYCOPY, LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.any()), c.genericArraycopy, NativeCall);
        registerForeignCall(createDescriptor(UNSAFE_ARRAYCOPY, LEAF_NO_VZERO, NOT_REEXECUTABLE, NamedLocationIdentity.any()), c.unsafeArraycopy, NativeCall);

        if (c.useMultiplyToLenIntrinsic()) {
            registerForeignCall(MULTIPLY_TO_LEN, c.multiplyToLen, NativeCall);
        }

        if (c.useSHA1Intrinsics()) {
            registerForeignCall(SHA_IMPL_COMPRESS, c.sha1ImplCompress, NativeCall);
            registerForeignCall(SHA_IMPL_COMPRESS_MB, c.sha1ImplCompressMultiBlock, NativeCall);
        }
        if (c.useSHA256Intrinsics()) {
            registerForeignCall(SHA2_IMPL_COMPRESS, c.sha256ImplCompress, NativeCall);
            registerForeignCall(SHA2_IMPL_COMPRESS_MB, c.sha256ImplCompressMultiBlock, NativeCall);
        }
        if (c.useSHA512Intrinsics()) {
            registerForeignCall(SHA5_IMPL_COMPRESS, c.sha512ImplCompress, NativeCall);
            registerForeignCall(SHA5_IMPL_COMPRESS_MB, c.sha512ImplCompressMultiBlock, NativeCall);
        }
        if (c.useGHASHIntrinsics()) {
            registerForeignCall(GHASH_PROCESS_BLOCKS, c.ghashProcessBlocks, NativeCall);
        }
        if (c.useBase64Intrinsics()) {
            registerForeignCall(BASE64_ENCODE_BLOCK, c.base64EncodeBlock, NativeCall);
        }
        if (c.useMulAddIntrinsic()) {
            registerForeignCall(MUL_ADD, c.mulAdd, NativeCall);
        }
        if (c.useMontgomeryMultiplyIntrinsic()) {
            registerForeignCall(MONTGOMERY_MULTIPLY, c.montgomeryMultiply, NativeCall);
        }
        if (c.useMontgomerySquareIntrinsic()) {
            registerForeignCall(MONTGOMERY_SQUARE, c.montgomerySquare, NativeCall);
        }
        if (c.useSquareToLenIntrinsic()) {
            registerForeignCall(SQUARE_TO_LEN, c.squareToLen, NativeCall);
        }

        if (c.useAESIntrinsics) {
            /*
             * When the java.ext.dirs property is modified then the crypto classes might not be
             * found. If that's the case we ignore the ClassNotFoundException and continue since we
             * cannot replace a non-existing method anyway.
             */
            try {
                // These stubs do callee saving
                registerForeignCall(AESCRYPT_ENCRYPTBLOCK, c.aescryptEncryptBlockStub, NativeCall);
                registerForeignCall(AESCRYPT_DECRYPTBLOCK, c.aescryptDecryptBlockStub, NativeCall);
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
            try {
                // These stubs do callee saving
                registerForeignCall(CIPHER_BLOCK_CHAINING_ENCRYPT_AESCRYPT, c.cipherBlockChainingEncryptAESCryptStub, NativeCall);
                registerForeignCall(CIPHER_BLOCK_CHAINING_DECRYPT_AESCRYPT, c.cipherBlockChainingDecryptAESCryptStub, NativeCall);
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
        }

        if (c.useAESCTRIntrinsics) {
            assert (c.counterModeAESCrypt != 0L);
            registerForeignCall(COUNTERMODE_IMPL_CRYPT, c.counterModeAESCrypt, NativeCall);
        }

        if (c.useVectorizedMismatchIntrinsic) {
            assert (c.vectorizedMismatch != 0L);
            registerForeignCall(VECTORIZED_MISMATCH, c.vectorizedMismatch, NativeCall);
        }

        assert registerStubCallFunctions(options, providers, runtime.getVMConfig());
    }

    @SuppressWarnings("unused")
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        registerForeignCall(createDescriptor(SIN.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticSinAddress, NativeCall);
        registerForeignCall(createDescriptor(COS.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticCosAddress, NativeCall);
        registerForeignCall(createDescriptor(TAN.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticTanAddress, NativeCall);
        registerForeignCall(createDescriptor(EXP.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticExpAddress, NativeCall);
        registerForeignCall(createDescriptor(LOG.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticLogAddress, NativeCall);
        registerForeignCall(createDescriptor(LOG10.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticLog10Address, NativeCall);
        registerForeignCall(createDescriptor(POW.foreignCallSignature, LEAF, REEXECUTABLE, NO_LOCATIONS), hotSpotVMConfig.arithmeticPowAddress, NativeCall);
    }
}
