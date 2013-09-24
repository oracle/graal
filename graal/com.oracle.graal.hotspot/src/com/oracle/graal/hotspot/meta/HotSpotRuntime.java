/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.graph.UnsafeAccess.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect.*;
import static com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.nodes.MonitorExitStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewInstanceStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewMultiArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.VMErrorNode.*;
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
import static com.oracle.graal.java.GraphBuilderPhase.RuntimeCalls.*;
import static com.oracle.graal.nodes.java.RegisterFinalizerNode.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.replacements.Log.*;
import static com.oracle.graal.replacements.MathSubstitutionsX86.*;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.bridge.CompilerToVM.CodeInstallResult;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

/**
 * HotSpot implementation of {@link GraalCodeCacheProvider}.
 */
public abstract class HotSpotRuntime implements GraalCodeCacheProvider, DisassemblerProvider, BytecodeDisassemblerProvider, SuitesProvider {

    public static final ForeignCallDescriptor OSR_MIGRATION_END = new ForeignCallDescriptor("OSR_migration_end", void.class, long.class);
    public static final ForeignCallDescriptor IDENTITY_HASHCODE = new ForeignCallDescriptor("identity_hashcode", int.class, Object.class);
    public static final ForeignCallDescriptor VERIFY_OOP = new ForeignCallDescriptor("verify_oop", Object.class, Object.class);
    public static final ForeignCallDescriptor LOAD_AND_CLEAR_EXCEPTION = new ForeignCallDescriptor("load_and_clear_exception", Object.class, Word.class);

    public final HotSpotVMConfig config;

    protected final RegisterConfig regConfig;
    protected final HotSpotGraalRuntime graalRuntime;
    private final Suites defaultSuites;

    private CheckCastDynamicSnippets.Templates checkcastDynamicSnippets;
    private InstanceOfSnippets.Templates instanceofSnippets;
    private NewObjectSnippets.Templates newObjectSnippets;
    private MonitorSnippets.Templates monitorSnippets;
    protected WriteBarrierSnippets.Templates writeBarrierSnippets;
    private BoxingSnippets.Templates boxingSnippets;
    private LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;
    private UnsafeLoadSnippets.Templates unsafeLoadSnippets;

    private final Map<ForeignCallDescriptor, HotSpotForeignCallLinkage> foreignCalls = new HashMap<>();

    /**
     * The offset from the origin of an array to the first element.
     * 
     * @return the offset in bytes
     */
    public static int getArrayBaseOffset(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     * 
     * @return the scale in order to convert the index into a byte offset
     */
    public static int getArrayIndexScale(Kind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public HotSpotRuntime(HotSpotVMConfig c, HotSpotGraalRuntime graalRuntime) {
        this.config = c;
        this.graalRuntime = graalRuntime;
        regConfig = createRegisterConfig();
        defaultSuites = createSuites();
    }

    protected abstract RegisterConfig createRegisterConfig();

    /**
     * Registers the linkage for a foreign call.
     */
    protected HotSpotForeignCallLinkage register(HotSpotForeignCallLinkage linkage) {
        assert !foreignCalls.containsKey(linkage.getDescriptor()) : "already registered linkage for " + linkage.getDescriptor();
        foreignCalls.put(linkage.getDescriptor(), linkage);
        return linkage;
    }

    /**
     * Creates and registers the details for linking a foreign call to a {@link Stub}.
     * 
     * @param descriptor the signature of the call to the stub
     * @param reexecutable specifies if the stub call can be re-executed without (meaningful) side
     *            effects. Deoptimization will not return to a point before a stub call that cannot
     *            be re-executed.
     * @param transition specifies if this is a {@linkplain Transition#LEAF leaf} call
     * @param killedLocations the memory locations killed by the stub call
     */
    protected HotSpotForeignCallLinkage registerStubCall(ForeignCallDescriptor descriptor, boolean reexecutable, Transition transition, LocationIdentity... killedLocations) {
        return register(HotSpotForeignCallLinkage.create(descriptor, 0L, PRESERVES_REGISTERS, JavaCall, JavaCallee, transition, reexecutable, killedLocations));
    }

    /**
     * Creates and registers the linkage for a foreign call.
     * 
     * @param descriptor the signature of the foreign call
     * @param address the address of the code to call
     * @param outgoingCcType outgoing (caller) calling convention type
     * @param effect specifies if the call destroys or preserves all registers (apart from
     *            temporaries which are always destroyed)
     * @param transition specifies if this is a {@linkplain Transition#LEAF leaf} call
     * @param reexecutable specifies if the foreign call can be re-executed without (meaningful)
     *            side effects. Deoptimization will not return to a point before a foreign call that
     *            cannot be re-executed.
     * @param killedLocations the memory locations killed by the foreign call
     */
    protected HotSpotForeignCallLinkage registerForeignCall(ForeignCallDescriptor descriptor, long address, CallingConvention.Type outgoingCcType, RegisterEffect effect, Transition transition,
                    boolean reexecutable, LocationIdentity... killedLocations) {
        Class<?> resultType = descriptor.getResultType();
        assert transition == LEAF || resultType.isPrimitive() || Word.class.isAssignableFrom(resultType) : "non-leaf foreign calls must return objects in thread local storage: " + descriptor;
        return register(HotSpotForeignCallLinkage.create(descriptor, address, effect, outgoingCcType, null, transition, reexecutable, killedLocations));
    }

    private static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    /**
     * Creates a {@linkplain ForeignCallStub stub} for a foreign call.
     * 
     * @param descriptor the signature of the call to the stub
     * @param address the address of the foreign code to call
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     * @param transition specifies if this is a {@linkplain Transition#LEAF leaf} call
     * @param reexecutable specifies if the foreign call can be re-executed without (meaningful)
     *            side effects. Deoptimization will not return to a point before a foreign call that
     *            cannot be re-executed.
     * @param killedLocations the memory locations killed by the foreign call
     */
    private void linkForeignCall(Replacements replacements, ForeignCallDescriptor descriptor, long address, boolean prependThread, Transition transition, boolean reexecutable,
                    LocationIdentity... killedLocations) {
        ForeignCallStub stub = new ForeignCallStub(this, replacements, address, descriptor, prependThread, transition, reexecutable, killedLocations);
        HotSpotForeignCallLinkage linkage = stub.getLinkage();
        HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
        linkage.setCompiledStub(stub);
        register(linkage);
        register(targetLinkage);
    }

    public static final boolean PREPEND_THREAD = true;
    public static final boolean DONT_PREPEND_THREAD = !PREPEND_THREAD;

    public static final boolean REEXECUTABLE = true;
    public static final boolean NOT_REEXECUTABLE = !REEXECUTABLE;

    public static final LocationIdentity[] NO_LOCATIONS = {};

    public void registerReplacements(Replacements r) {
        HotSpotVMConfig c = config;
        TargetDescription target = getTarget();

        registerForeignCall(UNCOMMON_TRAP, c.uncommonTrapStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(DEOPT_HANDLER, c.handleDeoptStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_SIN, c.arithmeticSinAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_COS, c.arithmeticCosAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_TAN, c.arithmeticTanAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall, DESTROYS_REGISTERS, LEAF, NOT_REEXECUTABLE, ANY_LOCATION);

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        registerForeignCall(NEW_ARRAY_C, c.newArrayAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        registerForeignCall(NEW_INSTANCE_C, c.newInstanceAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF, REEXECUTABLE, NO_LOCATIONS);

        link(new NewInstanceStub(this, r, target, registerStubCall(NEW_INSTANCE, REEXECUTABLE, NOT_LEAF, ANY_LOCATION)));
        link(new NewArrayStub(this, r, target, registerStubCall(NEW_ARRAY, REEXECUTABLE, NOT_LEAF, INIT_LOCATION)));
        link(new ExceptionHandlerStub(this, r, target, foreignCalls.get(EXCEPTION_HANDLER)));
        link(new UnwindExceptionToCallerStub(this, r, target, registerStubCall(UNWIND_EXCEPTION_TO_CALLER, NOT_REEXECUTABLE, NOT_LEAF, ANY_LOCATION)));
        link(new VerifyOopStub(this, r, target, registerStubCall(VERIFY_OOP, REEXECUTABLE, LEAF, NO_LOCATIONS)));

        linkForeignCall(r, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, MARK_WORD_LOCATION);
        linkForeignCall(r, REGISTER_FINALIZER, c.registerFinalizerAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, CREATE_NULL_POINTER_EXCEPTION, c.createNullPointerExceptionAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, CREATE_OUT_OF_BOUNDS_EXCEPTION, c.createOutOfBoundsExceptionAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, NEW_MULTI_ARRAY, c.newMultiArrayAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, DYNAMIC_NEW_ARRAY, c.dynamicNewArrayAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, LOG_PRINTF, c.logPrintfAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, LOG_OBJECT, c.logObjectAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, LOG_PRIMITIVE, c.logPrimitiveAddress, PREPEND_THREAD, NOT_LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, THREAD_IS_INTERRUPTED, c.threadIsInterruptedAddress, PREPEND_THREAD, NOT_LEAF, NOT_REEXECUTABLE, ANY_LOCATION);
        linkForeignCall(r, VM_ERROR, c.vmErrorAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, OSR_MIGRATION_END, c.osrMigrationEndAddress, DONT_PREPEND_THREAD, LEAF, NOT_REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, G1WBPRECALL, c.writeBarrierPreAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, G1WBPOSTCALL, c.writeBarrierPostAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(r, VALIDATE_OBJECT, c.validateObject, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);

        r.registerSubstitutions(ObjectSubstitutions.class);
        r.registerSubstitutions(SystemSubstitutions.class);
        r.registerSubstitutions(ThreadSubstitutions.class);
        r.registerSubstitutions(UnsafeSubstitutions.class);
        r.registerSubstitutions(ClassSubstitutions.class);
        r.registerSubstitutions(AESCryptSubstitutions.class);
        r.registerSubstitutions(CipherBlockChainingSubstitutions.class);
        r.registerSubstitutions(CRC32Substitutions.class);
        r.registerSubstitutions(ReflectionSubstitutions.class);

        checkcastDynamicSnippets = new CheckCastDynamicSnippets.Templates(this, r, graalRuntime.getTarget());
        instanceofSnippets = new InstanceOfSnippets.Templates(this, r, graalRuntime.getTarget());
        newObjectSnippets = new NewObjectSnippets.Templates(this, r, graalRuntime.getTarget());
        monitorSnippets = new MonitorSnippets.Templates(this, r, graalRuntime.getTarget(), c.useFastLocking);
        writeBarrierSnippets = new WriteBarrierSnippets.Templates(this, r, graalRuntime.getTarget());
        boxingSnippets = new BoxingSnippets.Templates(this, r, graalRuntime.getTarget());
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(this, r, graalRuntime.getTarget());
        unsafeLoadSnippets = new UnsafeLoadSnippets.Templates(this, r, graalRuntime.getTarget());

        r.registerSnippetTemplateCache(new UnsafeArrayCopySnippets.Templates(this, r, graalRuntime.getTarget()));
    }

    public HotSpotGraalRuntime getGraalRuntime() {
        return graalRuntime;
    }

    /**
     * Gets the register holding the current thread.
     */
    public abstract Register threadRegister();

    /**
     * Returns the register used by the runtime for maintaining the heap base address for compressed
     * pointers.
     */
    public abstract Register heapBaseRegister();

    /**
     * Gets the stack pointer register.
     */
    public abstract Register stackPointerRegister();

    @Override
    public String disassemble(CompilationResult compResult, InstalledCode installedCode) {
        byte[] code = installedCode == null ? Arrays.copyOf(compResult.getTargetCode(), compResult.getTargetCodeSize()) : installedCode.getCode();
        long start = installedCode == null ? 0L : installedCode.getStart();
        TargetDescription target = graalRuntime.getTarget();
        HexCodeFile hcf = new HexCodeFile(code, start, target.arch.getName(), target.wordSize * 8);
        if (compResult != null) {
            HexCodeFile.addAnnotations(hcf, compResult.getAnnotations());
            addExceptionHandlersComment(compResult, hcf);
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new RefMapFormatter(target.arch, target.wordSize, fp, 0);
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;
                    if (call.debugInfo != null) {
                        hcf.addComment(call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, call.pcOffset, "{" + getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        hcf.addComment(infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }
            for (DataPatch site : compResult.getDataReferences()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.getDataString() + "}");
            }
            for (Mark mark : compResult.getMarks()) {
                hcf.addComment(mark.pcOffset, getMarkName(mark));
            }
        }
        return hcf.toEmbeddedString();
    }

    /**
     * Decodes a call target to a mnemonic if possible.
     */
    private String getTargetName(Call call) {
        Field[] fields = config.getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().endsWith("Stub")) {
                f.setAccessible(true);
                try {
                    Object address = f.get(config);
                    if (address.equals(call.target)) {
                        return f.getName() + ":0x" + Long.toHexString((Long) address);
                    }
                } catch (Exception e) {
                }
            }
        }
        return String.valueOf(call.target);
    }

    /**
     * Decodes a mark to a mnemonic if possible.
     */
    private static String getMarkName(Mark mark) {
        Field[] fields = Marks.class.getDeclaredFields();
        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers()) && f.getName().startsWith("MARK_")) {
                f.setAccessible(true);
                try {
                    if (f.get(null).equals(mark.id)) {
                        return f.getName();
                    }
                } catch (Exception e) {
                }
            }
        }
        return "MARK:" + mark.id;
    }

    private static void addExceptionHandlersComment(CompilationResult compResult, HexCodeFile hcf) {
        if (!compResult.getExceptionHandlers().isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CompilationResult.ExceptionHandler e : compResult.getExceptionHandlers()) {
                buf.append("    ").append(e.pcOffset).append(" -> ").append(e.handlerPos).append(nl);
                hcf.addComment(e.pcOffset, "[exception -> " + e.handlerPos + "]");
                hcf.addComment(e.handlerPos, "[exception handler for " + e.pcOffset + "]");
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(HexCodeFile hcf, int pos, String comment) {
        String oldValue = hcf.addOperandComment(pos, comment);
        assert oldValue == null : "multiple comments for operand of instruction at " + pos + ": " + comment + ", " + oldValue;
    }

    @Override
    public ResolvedJavaType lookupJavaType(Constant constant) {
        if (constant.getKind() != Kind.Object || constant.isNull()) {
            return null;
        }
        Object o = constant.asObject();
        return HotSpotResolvedObjectType.fromClass(o.getClass());
    }

    @Override
    public Signature parseMethodDescriptor(String signature) {
        return new HotSpotSignature(signature);
    }

    @Override
    public boolean constantEquals(Constant x, Constant y) {
        return x.equals(y);
    }

    @Override
    public RegisterConfig lookupRegisterConfig() {
        return regConfig;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return config.runtimeCallStackSize;
    }

    @Override
    public Integer lookupArrayLength(Constant array) {
        if (array.getKind() != Kind.Object || array.isNull() || !array.asObject().getClass().isArray()) {
            return null;
        }
        return Array.getLength(array.asObject());
    }

    public boolean useCompressedOops() {
        return config.useCompressedOops;
    }

    public boolean useCompressedKlassPointers() {
        return config.useCompressedKlassPointers;
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        Kind wordKind = graalRuntime.getTarget().wordKind;
        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            ValueNode array = arrayLengthNode.array();
            ReadNode arrayLengthRead = graph.add(new ReadNode(array, ConstantLocationNode.create(FINAL_LOCATION, Kind.Int, config.arrayLengthOffset, graph), StampFactory.positiveInt(),
                            BarrierType.NONE, false));
            tool.createNullCheckGuard(arrayLengthRead, array);
            graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
        } else if (n instanceof Invoke) {
            Invoke invoke = (Invoke) n;
            if (invoke.callTarget() instanceof MethodCallTargetNode) {

                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                NodeInputList<ValueNode> parameters = callTarget.arguments();
                ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
                GuardingNode receiverNullCheck = null;
                if (!callTarget.isStatic() && receiver.stamp() instanceof ObjectStamp && !ObjectStamp.isObjectNonNull(receiver)) {
                    receiverNullCheck = tool.createNullCheckGuard(invoke, receiver);
                }
                JavaType[] signature = MetaUtil.signatureToTypes(callTarget.targetMethod().getSignature(), callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

                LoweredCallTargetNode loweredCallTarget = null;
                if (callTarget.invokeKind() == InvokeKind.Virtual && InlineVTableStubs.getValue() && (AlwaysInlineVTableStubs.getValue() || invoke.isPolymorphic())) {

                    HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                    if (!hsMethod.getDeclaringClass().isInterface()) {
                        if (hsMethod.isInVirtualMethodTable()) {
                            int vtableEntryOffset = hsMethod.vtableEntryOffset();
                            assert vtableEntryOffset > 0;
                            FloatingReadNode hub = createReadHub(graph, wordKind, receiver, receiverNullCheck);

                            ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, hub, hsMethod);
                            // We use LocationNode.ANY_LOCATION for the reads that access the
                            // compiled code entry as HotSpot does not guarantee they are final
                            // values.
                            ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, ConstantLocationNode.create(ANY_LOCATION, wordKind, config.methodCompiledEntryOffset, graph),
                                            StampFactory.forKind(wordKind()), BarrierType.NONE, false));

                            loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(),
                                            CallingConvention.Type.JavaCall));

                            graph.addBeforeFixed(invoke.asNode(), metaspaceMethod);
                            graph.addAfterFixed(metaspaceMethod, compiledEntry);
                        }
                    }
                }

                if (loweredCallTarget == null) {
                    loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall,
                                    callTarget.invokeKind()));
                }
                callTarget.replaceAndDelete(loweredCallTarget);
            }
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode loadField = (LoadFieldNode) n;
            HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) loadField.field();
            ValueNode object = loadField.isStatic() ? ConstantNode.forObject(field.getDeclaringClass().mirror(), this, graph) : loadField.object();
            assert loadField.kind() != Kind.Illegal;
            BarrierType barrierType = getFieldLoadBarrierType(field);
            ReadNode memoryRead = graph.add(new ReadNode(object, createFieldLocation(graph, field), loadField.stamp(), barrierType, (loadField.kind() == Kind.Object)));
            graph.replaceFixedWithFixed(loadField, memoryRead);
            tool.createNullCheckGuard(memoryRead, object);

            if (loadField.isVolatile()) {
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
                graph.addBeforeFixed(memoryRead, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
                graph.addAfterFixed(memoryRead, postMembar);
            }
        } else if (n instanceof StoreFieldNode) {
            StoreFieldNode storeField = (StoreFieldNode) n;
            HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) storeField.field();
            ValueNode object = storeField.isStatic() ? ConstantNode.forObject(field.getDeclaringClass().mirror(), this, graph) : storeField.object();
            BarrierType barrierType = getFieldStoreBarrierType(storeField);
            WriteNode memoryWrite = graph.add(new WriteNode(object, storeField.value(), createFieldLocation(graph, field), barrierType, storeField.field().getKind() == Kind.Object));
            tool.createNullCheckGuard(memoryWrite, object);
            memoryWrite.setStateAfter(storeField.stateAfter());
            graph.replaceFixedWithFixed(storeField, memoryWrite);
            FixedWithNextNode last = memoryWrite;
            FixedWithNextNode first = memoryWrite;

            if (storeField.isVolatile()) {
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
                graph.addBeforeFixed(first, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
                graph.addAfterFixed(last, postMembar);
            }
        } else if (n instanceof CompareAndSwapNode) {
            // Separate out GC barrier semantics
            CompareAndSwapNode cas = (CompareAndSwapNode) n;
            LocationNode location = IndexedLocationNode.create(ANY_LOCATION, cas.expected().kind(), cas.displacement(), cas.offset(), graph, 1);
            LoweredCompareAndSwapNode atomicNode = graph.add(new LoweredCompareAndSwapNode(cas.object(), location, cas.expected(), cas.newValue(), getCompareAndSwapBarrier(cas),
                            cas.expected().kind() == Kind.Object));
            atomicNode.setStateAfter(cas.stateAfter());
            graph.replaceFixedWithFixed(cas, atomicNode);
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            GuardingNode boundsCheck = createBoundsCheck(loadIndexed, tool);
            Kind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.add(new ReadNode(loadIndexed.array(), arrayLocation, loadIndexed.stamp(), BarrierType.NONE, elementKind == Kind.Object));
            memoryRead.setGuard(boundsCheck);
            graph.replaceFixedWithFixed(loadIndexed, memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            GuardingNode boundsCheck = createBoundsCheck(storeIndexed, tool);
            Kind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();

            CheckCastNode checkcastNode = null;
            CheckCastDynamicNode checkcastDynamicNode = null;
            if (elementKind == Kind.Object && !ObjectStamp.isObjectAlwaysNull(value)) {
                // Store check!
                ResolvedJavaType arrayType = ObjectStamp.typeOrNull(array);
                if (arrayType != null && ObjectStamp.isExactType(array)) {
                    ResolvedJavaType elementType = arrayType.getComponentType();
                    if (!MetaUtil.isJavaLangObject(elementType)) {
                        checkcastNode = graph.add(new CheckCastNode(elementType, value, null, true));
                        graph.addBeforeFixed(storeIndexed, checkcastNode);
                        value = checkcastNode;
                    }
                } else {
                    LoadHubNode arrayClass = graph.unique(new LoadHubNode(array, wordKind, boundsCheck.asNode()));
                    LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, config.arrayClassElementOffset, graph);
                    /*
                     * Anchor the read of the element klass to the cfg, because it is only valid
                     * when arrayClass is an object class, which might not be the case in other
                     * parts of the compiled method.
                     */
                    FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, location, null, StampFactory.forKind(wordKind()), BeginNode.prevBegin(storeIndexed)));
                    checkcastDynamicNode = graph.add(new CheckCastDynamicNode(arrayElementKlass, value, true));
                    graph.addBeforeFixed(storeIndexed, checkcastDynamicNode);
                    value = checkcastDynamicNode;
                }
            }
            BarrierType barrierType = getArrayStoreBarrierType(storeIndexed);
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation, barrierType, elementKind == Kind.Object));
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());
            graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

            // Lower the associated checkcast node.
            if (checkcastNode != null) {
                checkcastNode.lower(tool);
            } else if (checkcastDynamicNode != null) {
                checkcastDynamicSnippets.lower(checkcastDynamicNode);
            }
        } else if (n instanceof UnsafeLoadNode) {
            if (graph.getGuardsStage().ordinal() > StructuredGraph.GuardsStage.FLOATING_GUARDS.ordinal()) {
                UnsafeLoadNode load = (UnsafeLoadNode) n;
                assert load.kind() != Kind.Illegal;
                boolean compressible = (!load.object().isNullConstant() && load.accessKind() == Kind.Object);
                if (addReadBarrier(load)) {
                    unsafeLoadSnippets.lower(load, tool);
                } else {
                    IndexedLocationNode location = IndexedLocationNode.create(ANY_LOCATION, load.accessKind(), load.displacement(), load.offset(), graph, 1);
                    ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, load.stamp(), BarrierType.NONE, compressible));
                    // An unsafe read must not float outside its block otherwise
                    // it may float above an explicit null check on its object.
                    memoryRead.setGuard(AbstractBeginNode.prevBegin(load));
                    graph.replaceFixedWithFixed(load, memoryRead);
                }
            }
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(ANY_LOCATION, store.accessKind(), store.displacement(), store.offset(), graph, 1);
            ValueNode object = store.object();
            BarrierType barrierType = getUnsafeStoreBarrierType(store);
            WriteNode write = graph.add(new WriteNode(object, store.value(), location, barrierType, store.value().kind() == Kind.Object));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
        } else if (n instanceof LoadHubNode) {
            LoadHubNode loadHub = (LoadHubNode) n;
            assert loadHub.kind() == wordKind;
            ValueNode object = loadHub.object();
            GuardingNode guard = loadHub.getGuard();
            FloatingReadNode hub = createReadHub(graph, wordKind, object, guard);
            graph.replaceFloating(loadHub, hub);
        } else if (n instanceof LoadMethodNode) {
            LoadMethodNode loadMethodNode = (LoadMethodNode) n;
            ResolvedJavaMethod method = loadMethodNode.getMethod();
            ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, loadMethodNode.getHub(), method);
            graph.replaceFixed(loadMethodNode, metaspaceMethod);
        } else if (n instanceof StoreHubNode) {
            StoreHubNode storeHub = (StoreHubNode) n;
            WriteNode hub = createWriteHub(graph, wordKind, storeHub.getObject(), storeHub.getValue());
            graph.replaceFixed(storeHub, hub);
        } else if (n instanceof CommitAllocationNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                CommitAllocationNode commit = (CommitAllocationNode) n;

                ValueNode[] allocations = new ValueNode[commit.getVirtualObjects().size()];
                for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                    VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                    int entryCount = virtual.entryCount();

                    FixedWithNextNode newObject;
                    if (virtual instanceof VirtualInstanceNode) {
                        newObject = graph.add(new NewInstanceNode(virtual.type(), true));
                    } else {
                        ResolvedJavaType element = ((VirtualArrayNode) virtual).componentType();
                        newObject = graph.add(new NewArrayNode(element, ConstantNode.forInt(entryCount, graph), true));
                    }
                    graph.addBeforeFixed(commit, newObject);
                    allocations[objIndex] = newObject;
                }
                int valuePos = 0;
                for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                    VirtualObjectNode virtual = commit.getVirtualObjects().get(objIndex);
                    int entryCount = virtual.entryCount();

                    ValueNode newObject = allocations[objIndex];
                    if (virtual instanceof VirtualInstanceNode) {
                        VirtualInstanceNode virtualInstance = (VirtualInstanceNode) virtual;
                        for (int i = 0; i < entryCount; i++) {
                            ValueNode value = commit.getValues().get(valuePos++);
                            if (value instanceof VirtualObjectNode) {
                                value = allocations[commit.getVirtualObjects().indexOf(value)];
                            }
                            if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                                WriteNode write = new WriteNode(newObject, value, createFieldLocation(graph, (HotSpotResolvedJavaField) virtualInstance.field(i)),
                                                virtualInstance.field(i).getKind() == Kind.Object ? BarrierType.IMPRECISE : BarrierType.NONE, virtualInstance.field(i).getKind() == Kind.Object);

                                graph.addBeforeFixed(commit, graph.add(write));
                            }
                        }

                    } else {
                        VirtualArrayNode array = (VirtualArrayNode) virtual;
                        ResolvedJavaType element = array.componentType();
                        for (int i = 0; i < entryCount; i++) {
                            ValueNode value = commit.getValues().get(valuePos++);
                            if (value instanceof VirtualObjectNode) {
                                int indexOf = commit.getVirtualObjects().indexOf(value);
                                assert indexOf != -1 : commit + " " + value;
                                value = allocations[indexOf];
                            }
                            if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                                WriteNode write = new WriteNode(newObject, value, createArrayLocation(graph, element.getKind(), ConstantNode.forInt(i, graph)),
                                                value.kind() == Kind.Object ? BarrierType.PRECISE : BarrierType.NONE, value.kind() == Kind.Object);
                                graph.addBeforeFixed(commit, graph.add(write));
                            }
                        }
                    }
                }
                for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                    FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocations[objIndex]));
                    allocations[objIndex] = anchor;
                    graph.addBeforeFixed(commit, anchor);
                }
                for (int objIndex = 0; objIndex < commit.getVirtualObjects().size(); objIndex++) {
                    for (int lockDepth : commit.getLocks().get(objIndex)) {
                        MonitorEnterNode enter = graph.add(new MonitorEnterNode(allocations[objIndex], lockDepth));
                        graph.addBeforeFixed(commit, enter);
                    }
                }
                for (Node usage : commit.usages().snapshot()) {
                    AllocatedObjectNode addObject = (AllocatedObjectNode) usage;
                    int index = commit.getVirtualObjects().indexOf(addObject.getVirtualObject());
                    graph.replaceFloating(addObject, allocations[index]);
                }
                graph.removeFixed(commit);
            }
        } else if (n instanceof OSRStartNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                OSRStartNode osrStart = (OSRStartNode) n;
                StartNode newStart = graph.add(new StartNode());
                LocalNode buffer = graph.unique(new LocalNode(0, StampFactory.forKind(wordKind())));
                ForeignCallNode migrationEnd = graph.add(new ForeignCallNode(this, OSR_MIGRATION_END, buffer));
                migrationEnd.setStateAfter(osrStart.stateAfter());

                newStart.setNext(migrationEnd);
                FixedNode next = osrStart.next();
                osrStart.setNext(null);
                migrationEnd.setNext(next);
                graph.setStart(newStart);

                // mirroring the calculations in c1_GraphBuilder.cpp (setup_osr_entry_block)
                int localsOffset = (graph.method().getMaxLocals() - 1) * 8;
                for (OSRLocalNode osrLocal : graph.getNodes(OSRLocalNode.class)) {
                    int size = FrameStateBuilder.stackSlots(osrLocal.kind());
                    int offset = localsOffset - (osrLocal.index() + size - 1) * 8;
                    IndexedLocationNode location = IndexedLocationNode.create(ANY_LOCATION, osrLocal.kind(), offset, ConstantNode.forLong(0, graph), graph, 1);
                    ReadNode load = graph.add(new ReadNode(buffer, location, osrLocal.stamp(), BarrierType.NONE, false));
                    osrLocal.replaceAndDelete(load);
                    graph.addBeforeFixed(migrationEnd, load);
                }
                osrStart.replaceAtUsages(newStart);
                osrStart.safeDelete();
            }
        } else if (n instanceof CheckCastDynamicNode) {
            checkcastDynamicSnippets.lower((CheckCastDynamicNode) n);
        } else if (n instanceof InstanceOfNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                instanceofSnippets.lower((InstanceOfNode) n, tool);
            }
        } else if (n instanceof InstanceOfDynamicNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                instanceofSnippets.lower((InstanceOfDynamicNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewInstanceNode) n);
            }
        } else if (n instanceof NewArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewArrayNode) n);
            }
        } else if (n instanceof DynamicNewArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((DynamicNewArrayNode) n);
            }
        } else if (n instanceof MonitorEnterNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                monitorSnippets.lower((MonitorEnterNode) n, tool);
            }
        } else if (n instanceof MonitorExitNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS) {
                monitorSnippets.lower((MonitorExitNode) n, tool);
            }
        } else if (n instanceof G1PreWriteBarrier) {
            writeBarrierSnippets.lower((G1PreWriteBarrier) n, tool);
        } else if (n instanceof G1PostWriteBarrier) {
            writeBarrierSnippets.lower((G1PostWriteBarrier) n, tool);
        } else if (n instanceof G1ReferentFieldReadBarrier) {
            writeBarrierSnippets.lower((G1ReferentFieldReadBarrier) n, tool);
        } else if (n instanceof SerialWriteBarrier) {
            writeBarrierSnippets.lower((SerialWriteBarrier) n, tool);
        } else if (n instanceof SerialArrayRangeWriteBarrier) {
            writeBarrierSnippets.lower((SerialArrayRangeWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePreWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePreWriteBarrier) n, tool);
        } else if (n instanceof G1ArrayRangePostWriteBarrier) {
            writeBarrierSnippets.lower((G1ArrayRangePostWriteBarrier) n, tool);
        } else if (n instanceof NewMultiArrayNode) {
            if (graph.getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
                newObjectSnippets.lower((NewMultiArrayNode) n);
            }
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n);
        } else if (n instanceof IntegerDivNode || n instanceof IntegerRemNode || n instanceof UnsignedDivNode || n instanceof UnsignedRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof BoxNode) {
            boxingSnippets.lower((BoxNode) n, tool);
        } else if (n instanceof UnboxNode) {
            boxingSnippets.lower((UnboxNode) n, tool);
        } else {
            assert false : "Node implementing Lowerable not handled: " + n;
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static boolean addReadBarrier(UnsafeLoadNode load) {
        if (useG1GC() && load.graph().getGuardsStage() == StructuredGraph.GuardsStage.FIXED_DEOPTS && load.object().kind() == Kind.Object && load.accessKind() == Kind.Object &&
                        !ObjectStamp.isObjectAlwaysNull(load.object())) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(load.object());
            if (type != null && !type.isArray()) {
                return true;
            }
        }
        return false;
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, Kind wordKind, ValueNode hub, ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        assert !hsMethod.getDeclaringClass().isInterface();
        assert hsMethod.isInVirtualMethodTable();

        int vtableEntryOffset = hsMethod.vtableEntryOffset();
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        ReadNode metaspaceMethod = graph.add(new ReadNode(hub, ConstantLocationNode.create(ANY_LOCATION, wordKind, vtableEntryOffset, graph), StampFactory.forKind(wordKind()), BarrierType.NONE, false));
        return metaspaceMethod;
    }

    private FloatingReadNode createReadHub(StructuredGraph graph, Kind wordKind, ValueNode object, GuardingNode guard) {
        LocationNode location = ConstantLocationNode.create(FINAL_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();
        return graph.unique(new FloatingReadNode(object, location, null, StampFactory.forKind(wordKind()), guard, BarrierType.NONE, useCompressedKlassPointers()));
    }

    private WriteNode createWriteHub(StructuredGraph graph, Kind wordKind, ValueNode object, ValueNode value) {
        LocationNode location = ConstantLocationNode.create(ANY_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();
        return graph.add(new WriteNode(object, value, location, BarrierType.NONE, useCompressedKlassPointers()));
    }

    private static BarrierType getFieldLoadBarrierType(HotSpotResolvedJavaField loadField) {
        BarrierType barrierType = BarrierType.NONE;
        if (config().useG1GC && loadField.getKind() == Kind.Object && loadField.getDeclaringClass().mirror() == java.lang.ref.Reference.class && loadField.getName().equals("referent")) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    private static BarrierType getFieldStoreBarrierType(StoreFieldNode storeField) {
        BarrierType barrierType = BarrierType.NONE;
        if (storeField.field().getKind() == Kind.Object) {
            barrierType = BarrierType.IMPRECISE;
        }
        return barrierType;
    }

    private static BarrierType getArrayStoreBarrierType(StoreIndexedNode store) {
        BarrierType barrierType = BarrierType.NONE;
        if (store.elementKind() == Kind.Object) {
            barrierType = BarrierType.PRECISE;
        }
        return barrierType;
    }

    private static BarrierType getUnsafeStoreBarrierType(UnsafeStoreNode store) {
        BarrierType barrierType = BarrierType.NONE;
        if (store.value().kind() == Kind.Object) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(store.object());
            if (type != null && !type.isArray()) {
                barrierType = BarrierType.IMPRECISE;
            } else {
                barrierType = BarrierType.PRECISE;
            }
        }
        return barrierType;
    }

    private static BarrierType getCompareAndSwapBarrier(CompareAndSwapNode cas) {
        BarrierType barrierType = BarrierType.NONE;
        if (cas.expected().kind() == Kind.Object) {
            ResolvedJavaType type = ObjectStamp.typeOrNull(cas.object());
            if (type != null && !type.isArray()) {
                barrierType = BarrierType.IMPRECISE;
            } else {
                barrierType = BarrierType.PRECISE;
            }
        }
        return barrierType;
    }

    protected static ConstantLocationNode createFieldLocation(StructuredGraph graph, HotSpotResolvedJavaField field) {
        return ConstantLocationNode.create(field, field.getKind(), field.offset(), graph);
    }

    public int getScalingFactor(Kind kind) {
        if (useCompressedOops() && kind == Kind.Object) {
            return this.graalRuntime.getTarget().arch.getSizeInBytes(Kind.Int);
        } else {
            return this.graalRuntime.getTarget().arch.getSizeInBytes(kind);
        }
    }

    protected IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index) {
        int scale = getScalingFactor(elementKind);
        return IndexedLocationNode.create(NamedLocationIdentity.getArrayLocation(elementKind), elementKind, getArrayBaseOffset(elementKind), index, graph, scale);
    }

    @Override
    public ValueNode reconstructArrayIndex(LocationNode location) {
        Kind elementKind = location.getValueKind();
        assert location.getLocationIdentity().equals(NamedLocationIdentity.getArrayLocation(elementKind));

        long base;
        ValueNode index;
        int scale = getScalingFactor(elementKind);

        if (location instanceof ConstantLocationNode) {
            base = ((ConstantLocationNode) location).getDisplacement();
            index = null;
        } else if (location instanceof IndexedLocationNode) {
            IndexedLocationNode indexedLocation = (IndexedLocationNode) location;
            assert indexedLocation.getIndexScaling() == scale;
            base = indexedLocation.getDisplacement();
            index = indexedLocation.getIndex();
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        base -= getArrayBaseOffset(elementKind);
        assert base >= 0 && base % scale == 0;

        base /= scale;
        assert NumUtil.isInt(base);

        if (index == null) {
            return ConstantNode.forInt((int) base, location.graph());
        } else {
            if (base == 0) {
                return index;
            } else {
                return IntegerArithmeticNode.add(ConstantNode.forInt((int) base, location.graph()), index);
            }
        }
    }

    private static GuardingNode createBoundsCheck(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph graph = n.graph();
        ArrayLengthNode arrayLength = graph.add(new ArrayLengthNode(n.array()));
        GuardingNode guard = tool.createGuard(graph.unique(new IntegerBelowThanNode(n.index(), arrayLength)), BoundsCheckException, InvalidateReprofile);

        graph.addBeforeFixed(n, arrayLength);
        return guard;
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return HotSpotResolvedObjectType.fromClass(clazz);
    }

    public HotSpotForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        HotSpotForeignCallLinkage callTarget = foreignCalls.get(descriptor);
        assert foreignCalls != null : descriptor;
        callTarget.finalizeAddress(graalRuntime.getBackend());
        return callTarget;
    }

    public ResolvedJavaMethod lookupJavaMethod(Method reflectionMethod) {
        CompilerToVM c2vm = graalRuntime.getCompilerToVM();
        HotSpotResolvedObjectType[] resultHolder = {null};
        long metaspaceMethod = c2vm.getMetaspaceMethod(reflectionMethod, resultHolder);
        assert metaspaceMethod != 0L;
        return resultHolder[0].createMethod(metaspaceMethod);
    }

    public ResolvedJavaMethod lookupJavaConstructor(Constructor reflectionConstructor) {
        CompilerToVM c2vm = graalRuntime.getCompilerToVM();
        HotSpotResolvedObjectType[] resultHolder = {null};
        long metaspaceMethod = c2vm.getMetaspaceConstructor(reflectionConstructor, resultHolder);
        assert metaspaceMethod != 0L;
        return resultHolder[0].createMethod(metaspaceMethod);
    }

    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return graalRuntime.getCompilerToVM().getJavaField(reflectionField);
    }

    public HotSpotInstalledCode installMethod(HotSpotResolvedJavaMethod method, int entryBCI, CompilationResult compResult) {
        HotSpotInstalledCode installedCode = new HotSpotNmethod(method, true, null);
        graalRuntime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(method, entryBCI, compResult), installedCode, method.getSpeculationLog());
        return installedCode;
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        return addMethod(method, compResult, null);
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult, Graph graph) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        HotSpotInstalledCode code = new HotSpotNmethod(hotspotMethod, false, graph);
        CodeInstallResult result = graalRuntime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(hotspotMethod, -1, compResult), code, null);
        if (result != CodeInstallResult.OK) {
            return null;
        }
        return code;
    }

    public InstalledCode addExternalMethod(ResolvedJavaMethod method, CompilationResult compResult, Graph graph) {

        // compResult.getTargetCode() == assembled PTX method string

        HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) method;
        HotSpotInstalledCode icode = new HotSpotNmethod(javaMethod, false, true, graph);
        HotSpotCompiledNmethod compiled = new HotSpotCompiledNmethod(javaMethod, -1, compResult);
        CompilerToVM vm = graalRuntime.getCompilerToVM();
        CodeInstallResult result = vm.installCode(compiled, icode, null);
        if (result != CodeInstallResult.OK) {
            return null;
        }
        return icode;
    }

    @Override
    public int encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason) {
        final int actionShift = 0;
        final int reasonShift = 3;

        int actionValue = convertDeoptAction(action);
        int reasonValue = convertDeoptReason(reason);
        return (~(((reasonValue) << reasonShift) + ((actionValue) << actionShift)));
    }

    public int convertDeoptAction(DeoptimizationAction action) {
        switch (action) {
            case None:
                return config.deoptActionNone;
            case RecompileIfTooManyDeopts:
                return config.deoptActionMaybeRecompile;
            case InvalidateReprofile:
                return config.deoptActionReinterpret;
            case InvalidateRecompile:
                return config.deoptActionMakeNotEntrant;
            case InvalidateStopCompiling:
                return config.deoptActionMakeNotCompilable;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public int convertDeoptReason(DeoptimizationReason reason) {
        switch (reason) {
            case None:
                return config.deoptReasonNone;
            case NullCheckException:
                return config.deoptReasonNullCheck;
            case BoundsCheckException:
                return config.deoptReasonRangeCheck;
            case ClassCastException:
                return config.deoptReasonClassCheck;
            case ArrayStoreException:
                return config.deoptReasonArrayCheck;
            case UnreachedCode:
                return config.deoptReasonUnreached0;
            case TypeCheckedInliningViolated:
                return config.deoptReasonTypeCheckInlining;
            case OptimizedTypeCheckViolated:
                return config.deoptReasonOptimizedTypeCheck;
            case NotCompiledExceptionHandler:
                return config.deoptReasonNotCompiledExceptionHandler;
            case Unresolved:
                return config.deoptReasonUnresolved;
            case JavaSubroutineMismatch:
                return config.deoptReasonJsrMismatch;
            case ArithmeticException:
                return config.deoptReasonDiv0Check;
            case RuntimeConstraint:
                return config.deoptReasonConstraint;
            case LoopLimitCheck:
                return config.deoptReasonLoopLimitCheck;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    public boolean needsDataPatch(Constant constant) {
        return constant.getPrimitiveAnnotation() != null;
    }

    @Override
    public Constant readUnsafeConstant(Kind kind, Object base, long displacement, boolean compressible) {
        switch (kind) {
            case Boolean:
                return Constant.forBoolean(base == null ? unsafe.getByte(displacement) != 0 : unsafe.getBoolean(base, displacement));
            case Byte:
                return Constant.forByte(base == null ? unsafe.getByte(displacement) : unsafe.getByte(base, displacement));
            case Char:
                return Constant.forChar(base == null ? unsafe.getChar(displacement) : unsafe.getChar(base, displacement));
            case Short:
                return Constant.forShort(base == null ? unsafe.getShort(displacement) : unsafe.getShort(base, displacement));
            case Int:
                return Constant.forInt(base == null ? unsafe.getInt(displacement) : unsafe.getInt(base, displacement));
            case Long:
                if (displacement == config().hubOffset && useCompressedKlassPointers()) {
                    if (base == null) {
                        throw new GraalInternalError("Base of object must not be null");
                    } else {
                        return Constant.forLong(this.getGraalRuntime().getCompilerToVM().readUnsafeKlassPointer(base));
                    }
                } else {
                    return Constant.forLong(base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement));
                }
            case Float:
                return Constant.forFloat(base == null ? unsafe.getFloat(displacement) : unsafe.getFloat(base, displacement));
            case Double:
                return Constant.forDouble(base == null ? unsafe.getDouble(displacement) : unsafe.getDouble(base, displacement));
            case Object: {
                Object o = null;
                if (compressible) {
                    o = unsafe.getObject(base, displacement);
                } else {
                    o = this.getGraalRuntime().getCompilerToVM().readUnsafeUncompressedPointer(base, displacement);
                }
                return Constant.forObject(o);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
        return foreignCalls.get(descriptor).isReexecutable();
    }

    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
        return foreignCalls.get(descriptor).canDeoptimize();
    }

    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
        return foreignCalls.get(descriptor).getKilledLocations();
    }

    @Override
    public TargetDescription getTarget() {
        return graalRuntime.getTarget();
    }

    public String disassemble(InstalledCode code) {
        if (code.isValid()) {
            long codeBlob = ((HotSpotInstalledCode) code).getCodeBlob();
            return graalRuntime.getCompilerToVM().disassembleCodeBlob(codeBlob);
        }
        return null;
    }

    public String disassemble(ResolvedJavaMethod method) {
        return new BytecodeDisassembler().disassemble(method);
    }

    public Suites getDefaultSuites() {
        return defaultSuites;
    }

    public Suites createSuites() {
        Suites ret = Suites.createDefaultSuites();

        if (AOTCompilation.getValue()) {
            // lowering introduces class constants, therefore it must be after lowering
            ret.getHighTier().appendPhase(new LoadJavaMirrorWithKlassPhase());
            if (VerifyPhases.getValue()) {
                ret.getHighTier().appendPhase(new AheadOfTimeVerificationPhase());
            }
        }

        ret.getMidTier().appendPhase(new WriteBarrierAdditionPhase());
        if (VerifyPhases.getValue()) {
            ret.getMidTier().appendPhase(new WriteBarrierVerificationPhase());
        }

        return ret;
    }
}
