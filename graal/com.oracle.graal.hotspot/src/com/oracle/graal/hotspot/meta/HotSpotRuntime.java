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
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.nodes.UnwindNode.*;
import static com.oracle.graal.nodes.java.RegisterFinalizerNode.*;
import static com.oracle.graal.snippets.Log.*;
import static com.oracle.graal.snippets.MathSnippetsX86.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.CompilationResult.Safepoint;
import com.oracle.graal.api.code.Register.RegisterFlag;
import com.oracle.graal.api.code.RuntimeCall.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.hotspot.snippets.*;
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

    private final Map<Descriptor, RuntimeCall> runtimeCalls = new HashMap<>();

    protected Value ret(Kind kind) {
        if (kind.isVoid()) {
            return ILLEGAL;
        }
        return globalStubRegConfig.getReturnRegister(kind).asValue(kind);
    }

    protected Value arg(int index, Kind kind) {
        if (kind.isFloat() || kind.isDouble()) {
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
        HotSpotRuntimeCall runtimeCall = new HotSpotRuntimeCall(descriptor, address, new CallingConvention(temps, 0, ret, args), graalRuntime.getCompilerToVM());
        runtimeCalls.put(descriptor, runtimeCall);
    }

    protected abstract RegisterConfig createRegisterConfig(boolean globalStubConfig);

    public void installSnippets(SnippetInstaller installer) {
        installer.install(SystemSnippets.class);
        installer.install(UnsafeSnippets.class);
        installer.install(ArrayCopySnippets.class);

        installer.install(CheckCastSnippets.class);
        installer.install(InstanceOfSnippets.class);
        installer.install(NewObjectSnippets.class);
        installer.install(MonitorSnippets.class);

        checkcastSnippets = new CheckCastSnippets.Templates(this);
        instanceofSnippets = new InstanceOfSnippets.Templates(this);
        newObjectSnippets = new NewObjectSnippets.Templates(this, graalRuntime.getTarget(), config.useTLAB);
        monitorSnippets = new MonitorSnippets.Templates(this, config.useFastLocking);
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
        if (!constant.getKind().isObject() || constant.isNull()) {
            return null;
        }
        Object o = constant.asObject();
        return HotSpotResolvedJavaType.fromClass(o.getClass());
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
    public RegisterConfig lookupRegisterConfig(JavaMethod method) {
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
        if (!array.getKind().isObject() || array.isNull() || !array.asObject().getClass().isArray()) {
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
                            ReadNode metaspaceMethod = graph.add(new ReadNode(hub, LocationNode.create(LocationNode.ANY_LOCATION, wordKind, vtableEntryOffset, graph), wordStamp()));
                            ReadNode compiledEntry = graph.add(new ReadNode(metaspaceMethod, LocationNode.create(LocationNode.ANY_LOCATION, wordKind, config.methodCompiledEntryOffset, graph), wordStamp()));

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
            LoadFieldNode field = (LoadFieldNode) n;
            int displacement = ((HotSpotResolvedJavaField) field.field()).offset();
            assert field.kind() != Kind.Illegal;
            ReadNode memoryRead = graph.add(new ReadNode(field.object(), LocationNode.create(field.field(), field.field().getKind(), displacement, graph), field.stamp()));
            memoryRead.dependencies().add(tool.createNullCheckGuard(field.object(), field.leafGraphId()));
            graph.replaceFixedWithFixed(field, memoryRead);
            if (field.isVolatile()) {
                MembarNode preMembar = graph.add(new MembarNode(JMM_PRE_VOLATILE_READ));
                graph.addBeforeFixed(memoryRead, preMembar);
                MembarNode postMembar = graph.add(new MembarNode(JMM_POST_VOLATILE_READ));
                graph.addAfterFixed(memoryRead, postMembar);
            }
        } else if (n instanceof StoreFieldNode) {
            StoreFieldNode storeField = (StoreFieldNode) n;
            HotSpotResolvedJavaField field = (HotSpotResolvedJavaField) storeField.field();
            WriteNode memoryWrite = graph.add(new WriteNode(storeField.object(), storeField.value(), LocationNode.create(field, field.getKind(), field.offset(), graph)));
            memoryWrite.dependencies().add(tool.createNullCheckGuard(storeField.object(), storeField.leafGraphId()));
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
                if (type != null && !type.isArrayClass() && type.toJava() != Object.class) {
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
                    FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, location, null, wordStamp()));
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
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, load.loadKind(), load.displacement(), load.offset(), graph, false);
            ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, load.stamp()));
            // An unsafe read must not floating outside its block as may float above an explicit null check on its object.
            memoryRead.dependencies().add(BeginNode.prevBegin(load));
            graph.replaceFixedWithFixed(load, memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, store.storeKind(), store.displacement(), store.offset(), graph, false);
            ValueNode object = store.object();
            WriteNode write = graph.add(new WriteNode(object, store.value(), location));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
            if (write.value().kind() == Kind.Object && !write.value().objectStamp().alwaysNull()) {
                ResolvedJavaType type = object.objectStamp().type();
                WriteBarrier writeBarrier;
                if (type != null && !type.isArrayClass() && type.toJava() != Object.class) {
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
            ValueNode guard = tool.createNullCheckGuard(object, StructuredGraph.INVALID_GRAPH_ID);
            ReadNode hub = graph.add(new ReadNode(object, location, wordStamp()));
            hub.dependencies().add(guard);
            graph.replaceFixed(loadHub, hub);
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
        }
    }

    private static IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, elementKind.getArrayBaseOffset(), index, graph, true);
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
        ResolvedJavaType holder = method.getDeclaringClass();
        String fullName = method.getName() + ((HotSpotSignature) method.getSignature()).asString();
        Kind wordKind = graalRuntime.getTarget().wordKind;
        if (holder.toJava() == Object.class) {
            if (fullName.equals("getClass()Ljava/lang/Class;")) {
                ValueNode obj = (ValueNode) parameters.get(0);
                ObjectStamp stamp = (ObjectStamp) obj.stamp();
                if (stamp.nonNull() && stamp.isExactType()) {
                    StructuredGraph graph = new StructuredGraph();
                    ValueNode result = ConstantNode.forObject(stamp.type().toJava(), this, graph);
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    graph.start().setNext(ret);
                    return graph;
                }
                StructuredGraph graph = new StructuredGraph();
                LocalNode receiver = graph.unique(new LocalNode(0, StampFactory.objectNonNull()));
                LoadHubNode hub = graph.add(new LoadHubNode(receiver, wordKind));
                Stamp resultStamp = StampFactory.declaredNonNull(lookupJavaType(Class.class));
                FloatingReadNode result = graph.unique(new FloatingReadNode(hub, LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Object, config.classMirrorOffset, graph), null, resultStamp));
                ReturnNode ret = graph.add(new ReturnNode(result));
                graph.start().setNext(hub);
                hub.setNext(ret);
                return graph;
            }
        } else if (holder.toJava() == Class.class) {
            if (fullName.equals("getModifiers()I")) {
                StructuredGraph graph = new StructuredGraph();
                LocalNode receiver = graph.unique(new LocalNode(0, StampFactory.objectNonNull()));
                SafeReadNode klass = safeRead(graph, wordKind, receiver, config.klassOffset, wordStamp(), INVALID_GRAPH_ID);
                graph.start().setNext(klass);
                LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Int, config.klassModifierFlagsOffset, graph);
                FloatingReadNode readModifiers = graph.unique(new FloatingReadNode(klass, location, null, StampFactory.intValue()));
                CompareNode isZero = CompareNode.createCompareNode(Condition.EQ, klass, ConstantNode.defaultForKind(wordKind, graph));
                GuardNode guard = graph.unique(new GuardNode(isZero, graph.start(), NullCheckException, InvalidateReprofile, true, INVALID_GRAPH_ID));
                readModifiers.dependencies().add(guard);
                ReturnNode ret = graph.add(new ReturnNode(readModifiers));
                klass.setNext(ret);
                return graph;
            }
        } else if (holder.toJava() == Thread.class) {
            if (fullName.equals("currentThread()Ljava/lang/Thread;")) {
                StructuredGraph graph = new StructuredGraph();
                ReturnNode ret = graph.add(new ReturnNode(graph.unique(new CurrentThread(config.threadObjectOffset, this))));
                graph.start().setNext(ret);
                return graph;
            }
        }
        return null;
    }

    private static SafeReadNode safeRead(Graph graph, Kind kind, ValueNode value, int offset, Stamp stamp, long leafGraphId) {
        return graph.add(new SafeReadNode(value, LocationNode.create(LocationNode.FINAL_LOCATION, kind, offset, graph), stamp, leafGraphId));
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return HotSpotResolvedJavaType.fromClass(clazz);
    }

    public Object lookupCallTarget(Object target) {
        if (target instanceof HotSpotRuntimeCall) {
            return ((HotSpotRuntimeCall) target).address;
        }
        return target;
    }

    public RuntimeCall lookupRuntimeCall(Descriptor descriptor) {
        assert runtimeCalls.containsKey(descriptor) : descriptor;
        return runtimeCalls.get(descriptor);
    }

    public ResolvedJavaMethod lookupJavaMethod(Method reflectionMethod) {
        CompilerToVM c2vm = graalRuntime.getCompilerToVM();
        HotSpotResolvedJavaType[] resultHolder = {null};
        long metaspaceMethod = c2vm.getMetaspaceMethod(reflectionMethod, resultHolder);
        assert metaspaceMethod != 0L;
        return resultHolder[0].createMethod(metaspaceMethod);
    }

    @Override
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
        return graalRuntime.getCompilerToVM().installCode(new HotSpotCompilationResult(hotspotMethod, -1, compResult), new HotSpotInstalledCode(hotspotMethod), hsInfo);
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
        // This must be kept in sync with the DeoptAction enum defined in deoptimization.hpp
        switch(action) {
            case None: return 0;
            case RecompileIfTooManyDeopts: return 1;
            case InvalidateReprofile: return 2;
            case InvalidateRecompile: return 3;
            case InvalidateStopCompiling: return 4;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    public int convertDeoptReason(DeoptimizationReason reason) {
        // This must be kept in sync with the DeoptReason enum defined in deoptimization.hpp
        switch(reason) {
            case None: return 0;
            case NullCheckException: return 1;
            case BoundsCheckException: return 2;
            case ClassCastException: return 3;
            case ArrayStoreException: return 4;
            case UnreachedCode: return 5;
            case TypeCheckedInliningViolated: return 6;
            case OptimizedTypeCheckViolated: return 7;
            case NotCompiledExceptionHandler: return 8;
            case Unresolved: return 9;
            case JavaSubroutineMismatch: return 10;
            case ArithmeticException: return 11;
            case RuntimeConstraint: return 12;
            default: throw GraalInternalError.shouldNotReachHere();
        }
    }

    public boolean needsDataPatch(Constant constant) {
        return constant.getPrimitiveAnnotation() instanceof HotSpotResolvedJavaType;
    }
}
