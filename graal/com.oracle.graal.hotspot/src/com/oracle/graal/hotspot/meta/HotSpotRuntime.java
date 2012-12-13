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

import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.code.MemoryBarriers.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.snippets.SystemSnippets.*;
import static com.oracle.graal.java.GraphBuilderPhase.*;
import static com.oracle.graal.nodes.UnwindNode.*;
import static com.oracle.graal.nodes.java.RegisterFinalizerNode.*;
import static com.oracle.graal.snippets.Log.*;
import static com.oracle.graal.snippets.MathSnippetsX86.*;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.CompilationResult.Safepoint;
import com.oracle.graal.api.code.Register.RegisterFlag;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.bridge.CompilerToVM.CodeInstallResult;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.snippets.*;

/**
 * HotSpot implementation of {@link GraalCodeCacheProvider}.
 */
public abstract class HotSpotRuntime implements GraalCodeCacheProvider {
    public final HotSpotVMConfig config;

    protected final RegisterConfig regConfig;
    protected final RegisterConfig globalStubRegConfig;
    protected final HotSpotGraalRuntime graalRuntime;

    private CheckCastSnippets.Templates checkcastSnippets;
    private InstanceOfSnippets.Templates instanceofSnippets;
    private NewObjectSnippets.Templates newObjectSnippets;
    private MonitorSnippets.Templates monitorSnippets;

    private NewInstanceStub newInstanceStub;
    private NewArrayStub newArrayStub;

    private final Map<Descriptor, HotSpotRuntimeCallTarget> runtimeCalls = new HashMap<>();
    private final Map<ResolvedJavaMethod, Stub> stubs = new HashMap<>();

    /**
     * Holds onto objects that will be embedded in compiled code. HotSpot treats oops
     * embedded in code as weak references so without an external strong root, such
     * an embedded oop will quickly die. This in turn will cause the nmethod to
     * be unloaded.
     */
    private final Map<Object, Object> gcRoots = new HashMap<>();

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

    protected Value ret(Kind kind) {
        if (kind == Kind.Void) {
            return ILLEGAL;
        }
        return globalStubRegConfig.getReturnRegister(kind).asValue(kind);
    }

    protected Value arg(int index, Kind kind) {
        if (kind == Kind.Float || kind == Kind.Double) {
            return globalStubRegConfig.getCallingConventionRegisters(CallingConvention.Type.RuntimeCall, RegisterFlag.FPU)[index].asValue(kind);
        }
        return globalStubRegConfig.getCallingConventionRegisters(CallingConvention.Type.RuntimeCall, RegisterFlag.CPU)[index].asValue(kind);
    }

    protected Value scratch(Kind kind) {
        return globalStubRegConfig.getScratchRegister().asValue(kind);
    }

    public HotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime graalRuntime) {
        this.config = config;
        this.graalRuntime = graalRuntime;
        regConfig = createRegisterConfig(false);
        globalStubRegConfig = createRegisterConfig(true);

        addRuntimeCall(UNWIND_EXCEPTION, config.unwindExceptionStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0: exception */ arg(0, Kind.Object));

        addRuntimeCall(OnStackReplacementPhase.OSR_MIGRATION_END, config.osrMigrationEndStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0:      long */ arg(0, Kind.Long));

        addRuntimeCall(REGISTER_FINALIZER, config.registerFinalizerStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0:    object */ arg(0, Kind.Object));

        addRuntimeCall(CREATE_NULL_POINTER_EXCEPTION, config.createNullPointerExceptionStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Object));

        addRuntimeCall(CREATE_OUT_OF_BOUNDS_EXCEPTION, config.createOutOfBoundsExceptionStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Object),
                        /* arg0:     index */ arg(0, Kind.Int));

        addRuntimeCall(JAVA_TIME_MILLIS, config.javaTimeMillisStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Long));

        addRuntimeCall(JAVA_TIME_NANOS, config.javaTimeNanosStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Long));

        addRuntimeCall(ARITHMETIC_SIN, config.arithmeticSinStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Double),
                        /* arg0:     index */ arg(0, Kind.Double));

        addRuntimeCall(ARITHMETIC_COS, config.arithmeticCosStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Double),
                        /* arg0:     index */ arg(0, Kind.Double));

        addRuntimeCall(ARITHMETIC_TAN, config.arithmeticTanStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Double),
                        /* arg0:     index */ arg(0, Kind.Double));

        addRuntimeCall(LOG_PRIMITIVE, config.logPrimitiveStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0:  typeChar */ arg(0, Kind.Int),
                        /* arg1:     value */ arg(1, Kind.Long),
                        /* arg2:   newline */ arg(2, Kind.Boolean));

        addRuntimeCall(LOG_PRINTF, config.logPrintfStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0:    format */ arg(0, Kind.Object),
                        /* arg1:     value */ arg(1, Kind.Long));

        addRuntimeCall(LOG_OBJECT, config.logObjectStub,
                        /*           temps */ null,
                        /*             ret */ ret(Kind.Void),
                        /* arg0:    object */ arg(0, Kind.Object),
                        /* arg1:     flags */ arg(1, Kind.Int));
    }


    /**
     * Registers the details for linking a runtime call.
     *
     * @param descriptor name and signature of the call
     * @param address target address of the call
     * @param tempRegs temporary registers used (and killed) by the call (null if none)
     * @param ret where the call returns its result
     * @param args where arguments are passed to the call
     */
    protected void addRuntimeCall(Descriptor descriptor, long address, Register[] tempRegs, Value ret, Value... args) {
        Value[] temps = tempRegs == null || tempRegs.length == 0 ? Value.NONE : new Value[tempRegs.length];
        for (int i = 0; i < temps.length; i++) {
            temps[i] = tempRegs[i].asValue();
        }
        Kind retKind = ret.getKind();
        if (retKind == Kind.Illegal) {
            retKind = Kind.Void;
        }
        assert retKind.equals(descriptor.getResultKind()) : descriptor + " incompatible with result location " + ret;
        Kind[] argKinds = descriptor.getArgumentKinds();
        assert argKinds.length == args.length : descriptor + " incompatible with number of argument locations: " + args.length;
        for (int i = 0; i < argKinds.length; i++) {
            assert argKinds[i].equals(args[i].getKind()) : descriptor + " incompatible with argument location " + i + ": " + args[i];
        }
        HotSpotRuntimeCallTarget runtimeCall = new HotSpotRuntimeCallTarget(descriptor, address, new CallingConvention(temps, 0, ret, args), graalRuntime.getCompilerToVM());
        runtimeCalls.put(descriptor, runtimeCall);
    }

    /**
     * Binds a snippet-base {@link Stub} to a runtime call descriptor.
     *
     * @return the linkage information for a call to the stub
     */
    public HotSpotRuntimeCallTarget registerStub(Descriptor descriptor, Stub stub) {
        HotSpotRuntimeCallTarget linkage = runtimeCalls.get(descriptor);
        assert linkage != null;
        linkage.setStub(stub);
        stubs.put(stub.getMethod(), stub);
        return linkage;
    }

    protected abstract RegisterConfig createRegisterConfig(boolean globalStubConfig);

    public void installSnippets(SnippetInstaller installer, Assumptions assumptions) {
        installer.install(ObjectSnippets.class);
        installer.install(ClassSnippets.class);
        installer.install(ThreadSnippets.class);
        installer.install(SystemSnippets.class);
        installer.install(UnsafeSnippets.class);
        installer.install(ArrayCopySnippets.class);

        installer.install(CheckCastSnippets.class);
        installer.install(InstanceOfSnippets.class);
        installer.install(NewObjectSnippets.class);
        installer.install(MonitorSnippets.class);

        installer.install(NewInstanceStub.class);
        installer.install(NewArrayStub.class);

        checkcastSnippets = new CheckCastSnippets.Templates(this, assumptions, graalRuntime.getTarget());
        instanceofSnippets = new InstanceOfSnippets.Templates(this, assumptions, graalRuntime.getTarget());
        newObjectSnippets = new NewObjectSnippets.Templates(this, assumptions, graalRuntime.getTarget(), config.useTLAB);
        monitorSnippets = new MonitorSnippets.Templates(this, assumptions, graalRuntime.getTarget(), config.useFastLocking);

        newInstanceStub = new NewInstanceStub(this, assumptions, graalRuntime.getTarget());
        newArrayStub = new NewArrayStub(this, assumptions, graalRuntime.getTarget());
        newInstanceStub.install(graalRuntime.getCompiler());
        newArrayStub.install(graalRuntime.getCompiler());
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
    public String disassemble(CodeInfo info, CompilationResult tm) {
        byte[] code = info.getCode();
        TargetDescription target = graalRuntime.getTarget();
        HexCodeFile hcf = new HexCodeFile(code, info.getStart(), target.arch.getName(), target.wordSize * 8);
        if (tm != null) {
            HexCodeFile.addAnnotations(hcf, tm.getAnnotations());
            addExceptionHandlersComment(tm, hcf);
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new RefMapFormatter(target.arch, target.wordSize, fp, 0);
            for (Safepoint safepoint : tm.getSafepoints()) {
                if (safepoint instanceof Call) {
                    Call call = (Call) safepoint;
                    if (call.debugInfo != null) {
                        hcf.addComment(call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, call.pcOffset, "{" + getTargetName(call) + "}");
                } else {
                    if (safepoint.debugInfo != null) {
                        hcf.addComment(safepoint.pcOffset, CodeUtil.append(new StringBuilder(100), safepoint.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, safepoint.pcOffset, "{safepoint}");
                }
            }
            for (DataPatch site : tm.getDataReferences()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.constant + "}");
            }
            for (Mark mark : tm.getMarks()) {
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

    private static void addExceptionHandlersComment(CompilationResult tm, HexCodeFile hcf) {
        if (!tm.getExceptionHandlers().isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CompilationResult.ExceptionHandler e : tm.getExceptionHandlers()) {
                buf.append("    ").
                    append(e.pcOffset).append(" -> ").
                    append(e.handlerPos).
                    append(nl);
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
    public int getSizeOfLockData() {
        return config.basicLockSize;
    }

    @Override
    public boolean constantEquals(Constant x, Constant y) {
        return x.equals(y);
    }

    @Override
    public RegisterConfig lookupRegisterConfig(ResolvedJavaMethod method) {
        return regConfig;
    }

    /**
     * HotSpots needs an area suitable for storing a program counter for temporary use during the deoptimization process.
     */
    @Override
    public int getCustomStackAreaSize() {
        return graalRuntime.getTarget().wordSize;
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
            SafeReadNode safeReadArrayLength = safeReadArrayLength(arrayLengthNode.array(), StructuredGraph.INVALID_GRAPH_ID);
            graph.replaceFixedWithFixed(arrayLengthNode, safeReadArrayLength);
        } else if (n instanceof Invoke) {
            Invoke invoke = (Invoke) n;
            if (invoke.callTarget() instanceof MethodCallTargetNode) {
                MethodCallTargetNode callTarget = invoke.methodCallTarget();
                NodeInputList<ValueNode> parameters = callTarget.arguments();
                ValueNode receiver = parameters.size() <= 0 ? null : parameters.get(0);
                if (!callTarget.isStatic() && receiver.kind() == Kind.Object && !receiver.objectStamp().nonNull()) {
                    invoke.node().dependencies().add(tool.createNullCheckGuard(receiver, invoke.leafGraphId()));
                }
                Kind[] signature = MetaUtil.signatureToKinds(callTarget.targetMethod().getSignature(), callTarget.isStatic() ? null : callTarget.targetMethod().getDeclaringClass().getKind());

                AbstractCallTargetNode loweredCallTarget = null;
                if (callTarget.invokeKind() == InvokeKind.Virtual &&
                    GraalOptions.InlineVTableStubs &&
                    (GraalOptions.AlwaysInlineVTableStubs || invoke.isPolymorphic())) {

                    HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
                    if (!hsMethod.getDeclaringClass().isInterface()) {
                        int vtableEntryOffset = hsMethod.vtableEntryOffset();
                        if (vtableEntryOffset > 0) {
                            // We use LocationNode.ANY_LOCATION for the reads that access the vtable entry and the compiled code entry
                            // as HotSpot does not guarantee they are final values.
                            assert vtableEntryOffset > 0;
                            LoadHubNode hub = graph.add(new LoadHubNode(receiver, wordKind));
                            ReadNode metaspaceMethod = graph.add(new ReadNode(hub, LocationNode.create(LocationNode.ANY_LOCATION, wordKind, vtableEntryOffset, graph), StampFactory.forKind(wordKind())));
                            ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, LocationNode.create(LocationNode.ANY_LOCATION, wordKind, config.methodCompiledEntryOffset, graph), StampFactory.forKind(wordKind())));

                            loweredCallTarget = graph.add(new HotSpotIndirectCallTargetNode(metaspaceMethod, compiledEntry, parameters, invoke.node().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall));

                            graph.addBeforeFixed(invoke.node(), hub);
                            graph.addAfterFixed(hub, metaspaceMethod);
                            graph.addAfterFixed(metaspaceMethod, compiledEntry);
                        }
                    }
                }

                if (loweredCallTarget == null) {
                    loweredCallTarget = graph.add(new HotSpotDirectCallTargetNode(parameters, invoke.node().stamp(), signature, callTarget.targetMethod(), CallingConvention.Type.JavaCall, callTarget.invokeKind()));
                }
                callTarget.replaceAndDelete(loweredCallTarget);
            }
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode loadField = (LoadFieldNode) n;
            HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) loadField.field();
            ValueNode object = loadField.isStatic() ? ConstantNode.forObject(field.getDeclaringClass().mirror(), this, graph) : loadField.object();
            assert loadField.kind() != Kind.Illegal;
            ReadNode memoryRead = graph.add(new ReadNode(object, LocationNode.create(field, field.getKind(), field.offset(), graph), loadField.stamp()));
            memoryRead.dependencies().add(tool.createNullCheckGuard(object, loadField.leafGraphId()));
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
            WriteNode memoryWrite = graph.add(new WriteNode(object, storeField.value(), LocationNode.create(field, field.getKind(), field.offset(), graph)));
            memoryWrite.dependencies().add(tool.createNullCheckGuard(object, storeField.leafGraphId()));
            memoryWrite.setStateAfter(storeField.stateAfter());
            graph.replaceFixedWithFixed(storeField, memoryWrite);
            FixedWithNextNode last = memoryWrite;
            if (field.getKind() == Kind.Object && !memoryWrite.value().objectStamp().alwaysNull()) {
                FieldWriteBarrier writeBarrier = graph.add(new FieldWriteBarrier(memoryWrite.object()));
                graph.addAfterFixed(memoryWrite, writeBarrier);
                last = writeBarrier;
            }
            if (storeField.isVolatile()) {
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_WRITE));
                graph.addBeforeFixed(memoryWrite, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_WRITE));
                graph.addAfterFixed(last, postMembar);
            }
        } else if (n instanceof CompareAndSwapNode) {
            // Separate out GC barrier semantics
            CompareAndSwapNode cas = (CompareAndSwapNode) n;
            ValueNode expected = cas.expected();
            if (expected.kind() == Kind.Object && !cas.newValue().objectStamp().alwaysNull()) {
                ResolvedJavaType type = cas.object().objectStamp().type();
                if (type != null && !type.isArray() && !MetaUtil.isJavaLangObject(type)) {
                    // Use a field write barrier since it's not an array store
                    FieldWriteBarrier writeBarrier = graph.add(new FieldWriteBarrier(cas.object()));
                    graph.addAfterFixed(cas, writeBarrier);
                } else {
                    // This may be an array store so use an array write barrier
                    LocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, cas.expected().kind(), cas.displacement(), cas.offset(), graph, false);
                    graph.addAfterFixed(cas, graph.add(new ArrayWriteBarrier(cas.object(), location)));
                }
            }
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            ValueNode boundsCheck = createBoundsCheck(loadIndexed, tool);
            Kind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.add(new ReadNode(loadIndexed.array(), arrayLocation, loadIndexed.stamp()));
            memoryRead.dependencies().add(boundsCheck);
            graph.replaceFixedWithFixed(loadIndexed, memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            ValueNode boundsCheck = createBoundsCheck(storeIndexed, tool);

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
                        CheckCastNode checkcast = graph.add(new CheckCastNode(elementType, value, null));
                        graph.addBeforeFixed(storeIndexed, checkcast);
                        value = checkcast;
                    }
                } else {
                    LoadHubNode arrayClass = graph.add(new LoadHubNode(array, wordKind));
                    LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, wordKind, config.arrayClassElementOffset, graph);
                    FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, location, null, StampFactory.forKind(wordKind())));
                    CheckCastDynamicNode checkcast = graph.add(new CheckCastDynamicNode(arrayElementKlass, value));
                    graph.addBeforeFixed(storeIndexed, checkcast);
                    graph.addBeforeFixed(checkcast, arrayClass);
                    value = checkcast;
                }
            }
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation));
            memoryWrite.dependencies().add(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());

            graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

            if (elementKind == Kind.Object && !value.objectStamp().alwaysNull()) {
                graph.addAfterFixed(memoryWrite, graph.add(new ArrayWriteBarrier(array, arrayLocation)));
            }
        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            assert load.kind() != Kind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, load.accessKind(), load.displacement(), load.offset(), graph, false);
            ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, load.stamp()));
            // An unsafe read must not floating outside its block as may float above an explicit null check on its object.
            memoryRead.dependencies().add(BeginNode.prevBegin(load));
            graph.replaceFixedWithFixed(load, memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, store.accessKind(), store.displacement(), store.offset(), graph, false);
            ValueNode object = store.object();
            WriteNode write = graph.add(new WriteNode(object, store.value(), location));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
            if (write.value().kind() == Kind.Object && !write.value().objectStamp().alwaysNull()) {
                ResolvedJavaType type = object.objectStamp().type();
                WriteBarrier writeBarrier;
                if (type != null && !type.isArray() && !MetaUtil.isJavaLangObject(type)) {
                    // Use a field write barrier since it's not an array store
                    writeBarrier = graph.add(new FieldWriteBarrier(object));
                } else {
                    // This may be an array store so use an array write barrier
                    writeBarrier = graph.add(new ArrayWriteBarrier(object, location));
                }
                graph.addAfterFixed(write, writeBarrier);
            }
        } else if (n instanceof LoadHubNode) {
            LoadHubNode loadHub = (LoadHubNode) n;
            assert loadHub.kind() == wordKind;
            LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, wordKind, config.hubOffset, graph);
            ValueNode object = loadHub.object();
            assert !object.isConstant();
            ValueNode guard = tool.createNullCheckGuard(object, StructuredGraph.INVALID_GRAPH_ID);
            ReadNode hub = graph.add(new ReadNode(object, location, StampFactory.forKind(wordKind())));
            hub.dependencies().add(guard);
            graph.replaceFixed(loadHub, hub);
        } else if (n instanceof FixedGuardNode) {
            FixedGuardNode node = (FixedGuardNode) n;
            ValueAnchorNode newAnchor = graph.add(new ValueAnchorNode(tool.createGuard(node.condition(), node.getReason(), node.getAction(), node.isNegated(), node.getLeafGraphId())));
            graph.replaceFixedWithFixed(node, newAnchor);
        } else if (n instanceof CheckCastNode) {
            checkcastSnippets.lower((CheckCastNode) n, tool);
        } else if (n instanceof CheckCastDynamicNode) {
            checkcastSnippets.lower((CheckCastDynamicNode) n);
        } else if (n instanceof InstanceOfNode) {
            instanceofSnippets.lower((InstanceOfNode) n, tool);
        } else if (n instanceof NewInstanceNode) {
            newObjectSnippets.lower((NewInstanceNode) n, tool);
        } else if (n instanceof NewArrayNode) {
            newObjectSnippets.lower((NewArrayNode) n, tool);
        } else if (n instanceof MonitorEnterNode) {
            monitorSnippets.lower((MonitorEnterNode) n, tool);
        } else if (n instanceof MonitorExitNode) {
            monitorSnippets.lower((MonitorExitNode) n, tool);
        } else if (n instanceof TLABAllocateNode) {
            newObjectSnippets.lower((TLABAllocateNode) n, tool);
        } else if (n instanceof InitializeObjectNode) {
            newObjectSnippets.lower((InitializeObjectNode) n, tool);
        } else if (n instanceof InitializeArrayNode) {
            newObjectSnippets.lower((InitializeArrayNode) n, tool);
        } else if (n instanceof NewMultiArrayNode) {
            newObjectSnippets.lower((NewMultiArrayNode) n, tool);
        } else {
            assert false : "Node implementing Lowerable not handled: " + n;
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, getArrayBaseOffset(elementKind), index, graph, true);
    }

    private SafeReadNode safeReadArrayLength(ValueNode array, long leafGraphId) {
        return safeRead(array.graph(), Kind.Int, array, config.arrayLengthOffset, StampFactory.positiveInt(), leafGraphId);
    }

    private static ValueNode createBoundsCheck(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        ArrayLengthNode arrayLength = graph.add(new ArrayLengthNode(n.array()));
        ValueNode guard = tool.createGuard(graph.unique(new IntegerBelowThanNode(n.index(), arrayLength)), BoundsCheckException, InvalidateReprofile, n.leafGraphId());

        graph.addBeforeFixed(n, arrayLength);
        return guard;
    }

    @Override
    public StructuredGraph intrinsicGraph(ResolvedJavaMethod caller, int bci, ResolvedJavaMethod method, List<? extends Node> parameters) {
        return null;
    }

    private static SafeReadNode safeRead(Graph graph, Kind kind, ValueNode value, int offset, Stamp stamp, long leafGraphId) {
        return graph.add(new SafeReadNode(value, LocationNode.create(LocationNode.FINAL_LOCATION, kind, offset, graph), stamp, leafGraphId));
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return HotSpotResolvedObjectType.fromClass(clazz);
    }

    public Object lookupCallTarget(Object callTarget) {
        if (callTarget instanceof HotSpotRuntimeCallTarget) {
            return ((HotSpotRuntimeCallTarget) callTarget).getAddress();
        }
        return callTarget;
    }

    /**
     * Gets the stub corresponding to a given method.
     *
     * @return the stub {@linkplain Stub#getMethod() implemented} by {@code method} or null if {@code method} does not
     *         implement a stub
     */
    public Stub asStub(ResolvedJavaMethod method) {
        return stubs.get(method);
    }

    public HotSpotRuntimeCallTarget lookupRuntimeCall(Descriptor descriptor) {
        assert runtimeCalls.containsKey(descriptor) : descriptor;
        return runtimeCalls.get(descriptor);
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

    private static HotSpotCodeInfo makeInfo(ResolvedJavaMethod method, CompilationResult compResult, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = null;
        if (info != null && info.length > 0) {
            hsInfo = new HotSpotCodeInfo(compResult, (HotSpotResolvedJavaMethod) method);
            info[0] = hsInfo;
        }
        return hsInfo;
    }

    public void installMethod(HotSpotResolvedJavaMethod method, int entryBCI, CompilationResult compResult, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = makeInfo(method, compResult, info);
        graalRuntime.getCompilerToVM().installCode(new HotSpotCompilationResult(method, entryBCI, compResult), null, hsInfo);
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = makeInfo(method, compResult, info);
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        HotSpotInstalledCode code = new HotSpotInstalledCode(hotspotMethod);
        CodeInstallResult result = graalRuntime.getCompilerToVM().installCode(new HotSpotCompilationResult(hotspotMethod, -1, compResult), code, hsInfo);
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
        switch(action) {
            case None: return config.deoptActionNone;
            case RecompileIfTooManyDeopts: return config.deoptActionMaybeRecompile;
            case InvalidateReprofile: return config.deoptActionReinterpret;
            case InvalidateRecompile: return config.deoptActionMakeNotEntrant;
            case InvalidateStopCompiling: return config.deoptActionMakeNotCompilable;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    public int convertDeoptReason(DeoptimizationReason reason) {
        switch(reason) {
            case None: return config.deoptReasonNone;
            case NullCheckException: return config.deoptReasonNullCheck;
            case BoundsCheckException: return config.deoptReasonRangeCheck;
            case ClassCastException: return config.deoptReasonClassCheck;
            case ArrayStoreException: return config.deoptReasonArrayCheck;
            case UnreachedCode: return config.deoptReasonUnreached0;
            case TypeCheckedInliningViolated: return config.deoptReasonTypeCheckInlining;
            case OptimizedTypeCheckViolated: return config.deoptReasonOptimizedTypeCheck;
            case NotCompiledExceptionHandler: return config.deoptReasonNotCompiledExceptionHandler;
            case Unresolved: return config.deoptReasonUnresolved;
            case JavaSubroutineMismatch: return config.deoptReasonJsrMismatch;
            case ArithmeticException: return config.deoptReasonDiv0Check;
            case RuntimeConstraint: return config.deoptReasonConstraint;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    public boolean needsDataPatch(Constant constant) {
        return constant.getPrimitiveAnnotation() instanceof HotSpotResolvedObjectType;
    }

    /**
     * Registers an object created by the compiler and referenced by some generated code.
     * HotSpot treats oops embedded in code as weak references so without an external strong root, such
     * an embedded oop will quickly die. This in turn will cause the nmethod to be unloaded.
     */
    public synchronized Object registerGCRoot(Object object) {
        Object existing = gcRoots.get(object);
        if (existing != null) {
            return existing;
        }
        gcRoots.put(object, object);
        return object;
    }
}
