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
import static com.oracle.graal.hotspot.nodes.WriteBarrierPostStubCall.*;
import static com.oracle.graal.hotspot.nodes.WriteBarrierPreStubCall.*;
import static com.oracle.graal.hotspot.replacements.MonitorSnippets.*;
import static com.oracle.graal.hotspot.replacements.SystemSubstitutions.*;
import static com.oracle.graal.hotspot.replacements.ThreadSubstitutions.*;
import static com.oracle.graal.hotspot.stubs.ExceptionHandlerStub.*;
import static com.oracle.graal.hotspot.stubs.NewArrayStub.*;
import static com.oracle.graal.hotspot.stubs.NewInstanceStub.*;
import static com.oracle.graal.hotspot.stubs.StubUtil.*;
import static com.oracle.graal.hotspot.stubs.UnwindExceptionToCallerStub.*;
import static com.oracle.graal.java.GraphBuilderPhase.RuntimeCalls.*;
import static com.oracle.graal.nodes.java.RegisterFinalizerNode.*;
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
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage.RegisterEffect;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage.Transition;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.bridge.CompilerToVM.CodeInstallResult;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.extended.WriteNode.WriteBarrierType;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.word.*;

/**
 * HotSpot implementation of {@link GraalCodeCacheProvider}.
 */
public abstract class HotSpotRuntime implements GraalCodeCacheProvider, DisassemblerProvider, BytecodeDisassemblerProvider {

    public static final ForeignCallDescriptor OSR_MIGRATION_END = new ForeignCallDescriptor("OSR_migration_end", void.class, long.class);
    public static final ForeignCallDescriptor IDENTITY_HASHCODE = new ForeignCallDescriptor("identity_hashcode", int.class, Object.class);
    public static final ForeignCallDescriptor VERIFY_OOP = new ForeignCallDescriptor("verify_oop", Object.class, Object.class);

    public final HotSpotVMConfig config;

    protected final RegisterConfig regConfig;
    protected final HotSpotGraalRuntime graalRuntime;

    private CheckCastSnippets.Templates checkcastSnippets;
    private InstanceOfSnippets.Templates instanceofSnippets;
    private NewObjectSnippets.Templates newObjectSnippets;
    private MonitorSnippets.Templates monitorSnippets;
    private WriteBarrierSnippets.Templates writeBarrierSnippets;
    private BoxingSnippets.Templates boxingSnippets;
    private LoadExceptionObjectSnippets.Templates exceptionObjectSnippets;

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
     */
    protected HotSpotForeignCallLinkage registerStubCall(ForeignCallDescriptor descriptor) {
        return register(HotSpotForeignCallLinkage.create(descriptor, 0L, PRESERVES_REGISTERS, JavaCallee, NOT_LEAF));
    }

    /**
     * Creates and registers the linkage for a foreign call.
     */
    protected HotSpotForeignCallLinkage registerForeignCall(ForeignCallDescriptor descriptor, long address, CallingConvention.Type ccType, RegisterEffect effect, Transition transition) {
        Class<?> resultType = descriptor.getResultType();
        assert resultType.isPrimitive() || Word.class.isAssignableFrom(resultType) : "foreign calls must return objects in thread local storage: " + descriptor;
        return register(HotSpotForeignCallLinkage.create(descriptor, address, effect, ccType, transition));
    }

    private static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    /**
     * Creates a {@linkplain ForeignCallStub stub} for a non-leaf foreign call.
     * 
     * @param descriptor the signature of the call to this stub
     * @param address the address of the code to call
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     */
    private void linkForeignCall(ForeignCallDescriptor descriptor, long address, boolean prependThread, Replacements replacements) {
        ForeignCallStub stub = new ForeignCallStub(address, descriptor, prependThread, this, replacements);
        HotSpotForeignCallLinkage linkage = stub.getLinkage();
        HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
        linkage.setCompiledStub(stub);
        register(linkage);
        register(targetLinkage);
    }

    public void registerReplacements(Replacements replacements) {
        HotSpotVMConfig c = config;
        TargetDescription target = getTarget();

        registerForeignCall(UNCOMMON_TRAP, c.uncommonTrapStub, NativeCall, PRESERVES_REGISTERS, LEAF);
        registerForeignCall(DEOPT_HANDLER, c.handleDeoptStub, NativeCall, PRESERVES_REGISTERS, LEAF);
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub, NativeCall, PRESERVES_REGISTERS, LEAF);

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall, DESTROYS_REGISTERS, LEAF);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall, DESTROYS_REGISTERS, LEAF);
        registerForeignCall(ARITHMETIC_SIN, c.arithmeticSinAddress, NativeCall, DESTROYS_REGISTERS, LEAF);
        registerForeignCall(ARITHMETIC_COS, c.arithmeticCosAddress, NativeCall, DESTROYS_REGISTERS, LEAF);
        registerForeignCall(ARITHMETIC_TAN, c.arithmeticTanAddress, NativeCall, DESTROYS_REGISTERS, LEAF);

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF);
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF);
        registerForeignCall(NEW_ARRAY_C, c.newArrayAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF);
        registerForeignCall(NEW_INSTANCE_C, c.newInstanceAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF);
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall, DESTROYS_REGISTERS, NOT_LEAF);

        link(new NewInstanceStub(this, replacements, target, registerStubCall(NEW_INSTANCE)));
        link(new NewArrayStub(this, replacements, target, registerStubCall(NEW_ARRAY)));
        link(new ExceptionHandlerStub(this, replacements, target, foreignCalls.get(EXCEPTION_HANDLER)));
        link(new UnwindExceptionToCallerStub(this, replacements, target, registerStubCall(UNWIND_EXCEPTION_TO_CALLER)));
        link(new VerifyOopStub(this, replacements, target, registerStubCall(VERIFY_OOP)));

        linkForeignCall(IDENTITY_HASHCODE, c.identityHashCodeAddress, true, replacements);
        linkForeignCall(REGISTER_FINALIZER, c.registerFinalizerAddress, true, replacements);
        linkForeignCall(CREATE_NULL_POINTER_EXCEPTION, c.createNullPointerExceptionAddress, true, replacements);
        linkForeignCall(CREATE_OUT_OF_BOUNDS_EXCEPTION, c.createOutOfBoundsExceptionAddress, true, replacements);
        linkForeignCall(MONITORENTER, c.monitorenterAddress, true, replacements);
        linkForeignCall(MONITOREXIT, c.monitorexitAddress, true, replacements);
        linkForeignCall(WRITE_BARRIER_PRE, c.writeBarrierPreAddress, true, replacements);
        linkForeignCall(WRITE_BARRIER_POST, c.writeBarrierPostAddress, true, replacements);
        linkForeignCall(NEW_MULTI_ARRAY, c.newMultiArrayAddress, true, replacements);
        linkForeignCall(LOG_PRINTF, c.logPrintfAddress, true, replacements);
        linkForeignCall(LOG_OBJECT, c.logObjectAddress, true, replacements);
        linkForeignCall(LOG_PRIMITIVE, c.logPrimitiveAddress, true, replacements);
        linkForeignCall(THREAD_IS_INTERRUPTED, c.threadIsInterruptedAddress, true, replacements);
        linkForeignCall(VM_ERROR, c.vmErrorAddress, true, replacements);
        linkForeignCall(OSR_MIGRATION_END, c.osrMigrationEndAddress, false, replacements);

        if (GraalOptions.IntrinsifyObjectMethods) {
            replacements.registerSubstitutions(ObjectSubstitutions.class);
        }
        if (GraalOptions.IntrinsifySystemMethods) {
            replacements.registerSubstitutions(SystemSubstitutions.class);
        }
        if (GraalOptions.IntrinsifyThreadMethods) {
            replacements.registerSubstitutions(ThreadSubstitutions.class);
        }
        if (GraalOptions.IntrinsifyUnsafeMethods) {
            replacements.registerSubstitutions(UnsafeSubstitutions.class);
        }
        if (GraalOptions.IntrinsifyClassMethods) {
            replacements.registerSubstitutions(ClassSubstitutions.class);
        }
        if (GraalOptions.IntrinsifyAESMethods) {
            replacements.registerSubstitutions(AESCryptSubstitutions.class);
            replacements.registerSubstitutions(CipherBlockChainingSubstitutions.class);
        }
        if (GraalOptions.IntrinsifyReflectionMethods) {
            replacements.registerSubstitutions(ReflectionSubstitutions.class);
        }

        checkcastSnippets = new CheckCastSnippets.Templates(this, replacements, graalRuntime.getTarget());
        instanceofSnippets = new InstanceOfSnippets.Templates(this, replacements, graalRuntime.getTarget());
        newObjectSnippets = new NewObjectSnippets.Templates(this, replacements, graalRuntime.getTarget());
        monitorSnippets = new MonitorSnippets.Templates(this, replacements, graalRuntime.getTarget(), c.useFastLocking);
        writeBarrierSnippets = new WriteBarrierSnippets.Templates(this, replacements, graalRuntime.getTarget());
        boxingSnippets = new BoxingSnippets.Templates(this, replacements, graalRuntime.getTarget());
        exceptionObjectSnippets = new LoadExceptionObjectSnippets.Templates(this, replacements, graalRuntime.getTarget());
    }

    public HotSpotGraalRuntime getGraalRuntime() {
        return graalRuntime;
    }

    /**
     * Gets the register holding the current thread.
     */
    public abstract Register threadRegister();

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
                hcf.addOperandComment(site.pcOffset, "{" + site.constant + "}");
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
    public int lookupArrayLength(Constant array) {
        if (array.getKind() != Kind.Object || array.isNull() || !array.asObject().getClass().isArray()) {
            throw new IllegalArgumentException(array + " is not an array");
        }
        return Array.getLength(array.asObject());
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        Kind wordKind = graalRuntime.getTarget().wordKind;
        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            ValueNode array = arrayLengthNode.array();
            ReadNode arrayLengthRead = graph.add(new ReadNode(array, ConstantLocationNode.create(LocationIdentity.FINAL_LOCATION, Kind.Int, config.arrayLengthOffset, graph), StampFactory.positiveInt()));
            tool.createNullCheckGuard(arrayLengthRead, array);
            graph.replaceFixedWithFixed(arrayLengthNode, arrayLengthRead);
        } else if (n instanceof Invoke) {
            Invoke invoke = (Invoke) n;
            if (invoke.callTarget() instanceof MethodCallTargetNode) {
                MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
                NodeInputList<ValueNode> parameters = callTarget.arguments();
                ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
                if (!callTarget.isStatic() && receiver.kind() == Kind.Object && !receiver.objectStamp().nonNull()) {
                    tool.createNullCheckGuard(invoke, receiver);
                }
                JavaType[] signature = MetaUtil.signatureToTypes(callTarget.targetMethod().getSignature(), callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass());

                LoweredCallTargetNode loweredCallTarget = null;
                if (callTarget.invokeKind() == InvokeKind.Virtual && GraalOptions.InlineVTableStubs && (GraalOptions.AlwaysInlineVTableStubs || invoke.isPolymorphic())) {

                    HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                    if (!hsMethod.getDeclaringClass().isInterface()) {
                        if (hsMethod.isInVirtualMethodTable()) {
                            int vtableEntryOffset = hsMethod.vtableEntryOffset();
                            assert vtableEntryOffset > 0;
                            ReadNode hub = this.createReadHub(tool, graph, wordKind, receiver);
                            ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, hub, hsMethod);
                            // We use LocationNode.ANY_LOCATION for the reads that access the
                            // compiled code entry as HotSpot does not guarantee they are final
                            // values.
                            ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, ConstantLocationNode.create(LocationIdentity.ANY_LOCATION, wordKind, config.methodCompiledEntryOffset, graph),
                                            StampFactory.forKind(wordKind())));

                            loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.asNode().stamp(), signature, callTarget.targetMethod(),
                                            CallingConvention.Type.JavaCall));

                            graph.addBeforeFixed(invoke.asNode(), hub);
                            graph.addAfterFixed(hub, metaspaceMethod);
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
            ReadNode memoryRead = graph.add(new ReadNode(object, createFieldLocation(graph, field), loadField.stamp()));
            tool.createNullCheckGuard(memoryRead, object);

            graph.replaceFixedWithFixed(loadField, memoryRead);

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
            WriteBarrierType barrierType = getFieldStoreBarrierType(storeField);
            WriteNode memoryWrite = graph.add(new WriteNode(object, storeField.value(), createFieldLocation(graph, field), barrierType));
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
            LocationNode location = IndexedLocationNode.create(LocationIdentity.ANY_LOCATION, cas.expected().kind(), cas.displacement(), cas.offset(), graph, 1);
            cas.setLocation(location);
            cas.setWriteBarrierType(getCompareAndSwapBarrier(cas));
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            GuardingNode boundsCheck = createBoundsCheck(loadIndexed, tool);
            Kind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.add(new ReadNode(loadIndexed.array(), arrayLocation, loadIndexed.stamp()));
            memoryRead.setGuard(boundsCheck);
            graph.replaceFixedWithFixed(loadIndexed, memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            GuardingNode boundsCheck = createBoundsCheck(storeIndexed, tool);
            Kind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();
            if (elementKind == Kind.Object && !value.objectStamp().alwaysNull()) {
                // Store check!
                ResolvedJavaType arrayType = array.objectStamp().type();
                if (arrayType != null && array.objectStamp().isExactType()) {
                    ResolvedJavaType elementType = arrayType.getComponentType();
                    if (!MetaUtil.isJavaLangObject(elementType)) {
                        CheckCastNode checkcast = graph.add(new CheckCastNode(elementType, value, null, true));
                        graph.addBeforeFixed(storeIndexed, checkcast);
                        value = checkcast;
                    }
                } else {
                    LoadHubNode arrayClass = graph.add(new LoadHubNode(array, wordKind));
                    LocationNode location = ConstantLocationNode.create(LocationIdentity.FINAL_LOCATION, wordKind, config.arrayClassElementOffset, graph);
                    FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, location, null, StampFactory.forKind(wordKind())));
                    CheckCastDynamicNode checkcast = graph.add(new CheckCastDynamicNode(arrayElementKlass, value, true));
                    graph.addBeforeFixed(storeIndexed, checkcast);
                    graph.addBeforeFixed(checkcast, arrayClass);
                    value = checkcast;
                }
            }
            WriteBarrierType barrierType = getArrayStoreBarrierType(storeIndexed);
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation, barrierType));
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());
            graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            assert load.kind() != Kind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationIdentity.ANY_LOCATION, load.accessKind(), load.displacement(), load.offset(), graph, 1);
            ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, load.stamp()));
            // An unsafe read must not floating outside its block as may float above an explicit
            // null check on its object.
            memoryRead.setGuard(AbstractBeginNode.prevBegin(load));
            graph.replaceFixedWithFixed(load, memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(LocationIdentity.ANY_LOCATION, store.accessKind(), store.displacement(), store.offset(), graph, 1);
            ValueNode object = store.object();
            WriteBarrierType barrierType = getUnsafeStoreBarrierType(store);
            WriteNode write = graph.add(new WriteNode(object, store.value(), location, barrierType));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
        } else if (n instanceof LoadHubNode) {
            LoadHubNode loadHub = (LoadHubNode) n;
            assert loadHub.kind() == wordKind;
            ValueNode object = loadHub.object();
            ReadNode hub = createReadHub(tool, graph, wordKind, object);
            graph.replaceFixed(loadHub, hub);
        } else if (n instanceof LoadMethodNode) {
            LoadMethodNode loadMethodNode = (LoadMethodNode) n;
            ResolvedJavaMethod method = loadMethodNode.getMethod();
            ReadNode metaspaceMethod = createReadVirtualMethod(graph, wordKind, loadMethodNode.getHub(), method);
            graph.replaceFixed(loadMethodNode, metaspaceMethod);
        } else if (n instanceof FixedGuardNode) {
            FixedGuardNode node = (FixedGuardNode) n;
            ValueAnchorNode newAnchor = graph.add(new ValueAnchorNode(tool.createGuard(node.condition(), node.getReason(), node.getAction(), node.isNegated()).asNode()));
            graph.replaceFixedWithFixed(node, newAnchor);
        } else if (n instanceof CommitAllocationNode) {
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
                    VirtualInstanceNode instance = (VirtualInstanceNode) virtual;
                    for (int i = 0; i < entryCount; i++) {
                        ValueNode value = commit.getValues().get(valuePos++);
                        if (value instanceof VirtualObjectNode) {
                            value = allocations[commit.getVirtualObjects().indexOf(value)];
                        }
                        if (!(value.isConstant() && value.asConstant().isDefaultForKind())) {
                            graph.addBeforeFixed(commit, graph.add(new WriteNode(newObject, value, createFieldLocation(graph, (HotSpotResolvedJavaField) instance.field(i)), WriteBarrierType.NONE)));
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
                            graph.addBeforeFixed(commit,
                                            graph.add(new WriteNode(newObject, value, createArrayLocation(graph, element.getKind(), ConstantNode.forInt(i, graph)), WriteBarrierType.NONE)));
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
        } else if (n instanceof CheckCastNode) {
            checkcastSnippets.lower((CheckCastNode) n, tool);
        } else if (n instanceof OSRStartNode) {
            OSRStartNode osrStart = (OSRStartNode) n;
            StartNode newStart = graph.add(new StartNode());
            LocalNode buffer = graph.unique(new LocalNode(0, StampFactory.forKind(wordKind())));
            ForeignCallNode migrationEnd = graph.add(new ForeignCallNode(OSR_MIGRATION_END, buffer));
            newStart.setStateAfter(osrStart.stateAfter());

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
                UnsafeLoadNode load = graph.add(new UnsafeLoadNode(buffer, offset, ConstantNode.forInt(0, graph), osrLocal.kind()));
                osrLocal.replaceAndDelete(load);
                graph.addBeforeFixed(migrationEnd, load);
            }
            osrStart.replaceAtUsages(newStart);
            osrStart.safeDelete();
        } else if (n instanceof CheckCastDynamicNode) {
            checkcastSnippets.lower((CheckCastDynamicNode) n);
        } else if (n instanceof InstanceOfNode) {
            instanceofSnippets.lower((InstanceOfNode) n, tool);
        } else if (n instanceof InstanceOfDynamicNode) {
            instanceofSnippets.lower((InstanceOfDynamicNode) n, tool);
        } else if (n instanceof NewInstanceNode) {
            newObjectSnippets.lower((NewInstanceNode) n);
        } else if (n instanceof NewArrayNode) {
            newObjectSnippets.lower((NewArrayNode) n);
        } else if (n instanceof MonitorEnterNode) {
            monitorSnippets.lower((MonitorEnterNode) n, tool);
        } else if (n instanceof MonitorExitNode) {
            monitorSnippets.lower((MonitorExitNode) n, tool);
        } else if (n instanceof SerialWriteBarrier) {
            writeBarrierSnippets.lower((SerialWriteBarrier) n, tool);
        } else if (n instanceof SerialArrayRangeWriteBarrier) {
            writeBarrierSnippets.lower((SerialArrayRangeWriteBarrier) n, tool);
        } else if (n instanceof NewMultiArrayNode) {
            newObjectSnippets.lower((NewMultiArrayNode) n);
        } else if (n instanceof LoadExceptionObjectNode) {
            exceptionObjectSnippets.lower((LoadExceptionObjectNode) n);
        } else if (n instanceof IntegerDivNode || n instanceof IntegerRemNode || n instanceof UnsignedDivNode || n instanceof UnsignedRemNode) {
            // Nothing to do for division nodes. The HotSpot signal handler catches divisions by
            // zero and the MIN_VALUE / -1 cases.
        } else if (n instanceof UnwindNode || n instanceof DeoptimizeNode) {
            // Nothing to do, using direct LIR lowering for these nodes.
        } else if (n instanceof BoxNode) {
            boxingSnippets.lower((BoxNode) n);
        } else if (n instanceof UnboxNode) {
            boxingSnippets.lower((UnboxNode) n);
        } else {
            assert false : "Node implementing Lowerable not handled: " + n;
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static ReadNode createReadVirtualMethod(StructuredGraph graph, Kind wordKind, ValueNode hub, ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        assert !hsMethod.getDeclaringClass().isInterface();
        assert hsMethod.isInVirtualMethodTable();

        int vtableEntryOffset = hsMethod.vtableEntryOffset();
        assert vtableEntryOffset > 0;
        // We use LocationNode.ANY_LOCATION for the reads that access the vtable
        // entry as HotSpot does not guarantee that this is a final value.
        ReadNode metaspaceMethod = graph.add(new ReadNode(hub, ConstantLocationNode.create(LocationIdentity.ANY_LOCATION, wordKind, vtableEntryOffset, graph), StampFactory.forKind(wordKind())));
        return metaspaceMethod;
    }

    private ReadNode createReadHub(LoweringTool tool, StructuredGraph graph, Kind wordKind, ValueNode object) {
        LocationNode location = ConstantLocationNode.create(LocationIdentity.FINAL_LOCATION, wordKind, config.hubOffset, graph);
        assert !object.isConstant() || object.asConstant().isNull();
        ReadNode hub = graph.add(new ReadNode(object, location, StampFactory.forKind(wordKind())));
        tool.createNullCheckGuard(hub, object);
        return hub;
    }

    private static WriteBarrierType getFieldStoreBarrierType(StoreFieldNode storeField) {
        WriteBarrierType barrierType = WriteBarrierType.NONE;
        if (storeField.field().getKind() == Kind.Object && !storeField.value().objectStamp().alwaysNull()) {
            barrierType = WriteBarrierType.IMPRECISE;
        }
        return barrierType;
    }

    private static WriteBarrierType getArrayStoreBarrierType(StoreIndexedNode store) {
        WriteBarrierType barrierType = WriteBarrierType.NONE;
        if (store.elementKind() == Kind.Object && !store.value().objectStamp().alwaysNull()) {
            barrierType = WriteBarrierType.PRECISE;
        }
        return barrierType;
    }

    private static WriteBarrierType getUnsafeStoreBarrierType(UnsafeStoreNode store) {
        WriteBarrierType barrierType = WriteBarrierType.NONE;
        if (store.value().kind() == Kind.Object && !store.value().objectStamp().alwaysNull()) {
            ResolvedJavaType type = store.object().objectStamp().type();
            if (type != null && type.isArray()) {
                barrierType = WriteBarrierType.PRECISE;
            } else {
                barrierType = WriteBarrierType.IMPRECISE;
            }
        }
        return barrierType;
    }

    private static WriteBarrierType getCompareAndSwapBarrier(CompareAndSwapNode cas) {
        WriteBarrierType barrierType = WriteBarrierType.NONE;
        if (cas.expected().kind() == Kind.Object && !cas.newValue().objectStamp().alwaysNull()) {
            ResolvedJavaType type = cas.object().objectStamp().type();
            if (type != null && type.isArray()) {
                barrierType = WriteBarrierType.PRECISE;
            } else {
                barrierType = WriteBarrierType.IMPRECISE;
            }
        }
        return barrierType;
    }

    protected static ConstantLocationNode createFieldLocation(StructuredGraph graph, HotSpotResolvedJavaField field) {
        return ConstantLocationNode.create(field, field.getKind(), field.offset(), graph);
    }

    protected IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index) {
        int scale = this.graalRuntime.getTarget().arch.getSizeInBytes(elementKind);
        return IndexedLocationNode.create(NamedLocationIdentity.getArrayLocation(elementKind), elementKind, getArrayBaseOffset(elementKind), index, graph, scale);
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

    public HotSpotInstalledCode installMethod(HotSpotResolvedJavaMethod method, Graph graph, int entryBCI, CompilationResult compResult) {
        HotSpotInstalledCode installedCode = new HotSpotNmethod(method, graph, true);
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
        HotSpotInstalledCode code = new HotSpotNmethod(hotspotMethod, graph, false);
        CodeInstallResult result = graalRuntime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(hotspotMethod, -1, compResult), code, null);
        if (result != CodeInstallResult.OK) {
            return null;
        }
        return code;
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
    public Constant readUnsafeConstant(Kind kind, Object base, long displacement) {
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
                return Constant.forLong(base == null ? unsafe.getLong(displacement) : unsafe.getLong(base, displacement));
            case Float:
                return Constant.forFloat(base == null ? unsafe.getFloat(displacement) : unsafe.getFloat(base, displacement));
            case Double:
                return Constant.forDouble(base == null ? unsafe.getDouble(displacement) : unsafe.getDouble(base, displacement));
            case Object:
                return Constant.forObject(unsafe.getObject(base, displacement));
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public boolean hasSideEffect(ForeignCallDescriptor descriptor) {
        // Only these two foreign calls are expected to be made with
        // a node that implements StateSplit. They need to be a state
        // split so that the stack trace they produce is accurate.
        assert descriptor == CREATE_NULL_POINTER_EXCEPTION || descriptor == CREATE_OUT_OF_BOUNDS_EXCEPTION : descriptor;
        return false;
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
}
