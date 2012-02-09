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
package com.oracle.max.graal.hotspot.ri;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiTargetMethod.Call;
import com.oracle.max.cri.ci.CiTargetMethod.DataPatch;
import com.oracle.max.cri.ci.CiTargetMethod.Safepoint;
import com.oracle.max.cri.ci.CiUtil.RefMapFormatter;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.ri.RiType.Representation;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.hotspot.*;
import com.oracle.max.graal.hotspot.Compiler;
import com.oracle.max.graal.hotspot.nodes.*;
import com.oracle.max.graal.java.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.snippets.nodes.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 */
public class HotSpotRuntime implements GraalRuntime {
    final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    private final HotSpotRegisterConfig globalStubRegConfig;
    private final Compiler compiler;

    public HotSpotRuntime(HotSpotVMConfig config, Compiler compiler) {
        this.config = config;
        this.compiler = compiler;
        regConfig = new HotSpotRegisterConfig(config, false);
        globalStubRegConfig = new HotSpotRegisterConfig(config, true);
    }

    @Override
    public int codeOffset() {
        return 0;
    }


    public Compiler getCompiler() {
        return compiler;
    }

    @Override
    public String disassemble(byte[] code, long address) {
        return compiler.getVMEntries().disassembleNative(code, address);
    }

    @Override
    public String disassemble(CiTargetMethod tm) {
        byte[] code = Arrays.copyOf(tm.targetCode(), tm.targetCodeSize());
        CiTarget target = compiler.getTarget();
        HexCodeFile hcf = new HexCodeFile(code, 0L, target.arch.name, target.wordSize * 8);
        HexCodeFile.addAnnotations(hcf, tm.annotations());
        addExceptionHandlersComment(tm, hcf);
        CiRegister fp = regConfig.getFrameRegister();
        RefMapFormatter slotFormatter = new RefMapFormatter(target.arch, target.wordSize, fp, 0);
        for (Safepoint safepoint : tm.safepoints) {
            if (safepoint instanceof Call) {
                Call call = (Call) safepoint;
                if (call.debugInfo != null) {
                    hcf.addComment(call.pcOffset + call.size, CiUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                }
                addOperandComment(hcf, call.pcOffset, "{" + call.target + "}");
            } else {
                if (safepoint.debugInfo != null) {
                    hcf.addComment(safepoint.pcOffset, CiUtil.append(new StringBuilder(100), safepoint.debugInfo, slotFormatter).toString());
                }
                addOperandComment(hcf, safepoint.pcOffset, "{safepoint}");
            }
        }
        for (DataPatch site : tm.dataReferences) {
            hcf.addOperandComment(site.pcOffset, "{" + site.constant + "}");
        }
        return hcf.toEmbeddedString();
    }

    private static void addExceptionHandlersComment(CiTargetMethod tm, HexCodeFile hcf) {
        if (!tm.exceptionHandlers.isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CiTargetMethod.ExceptionHandler e : tm.exceptionHandlers) {
                buf.append("    ").
                    append(e.pcOffset).append(" -> ").
                    append(e.handlerPos).
                    append("  ").append(e.exceptionType == null ? "<any>" : e.exceptionType).
                    append(nl);
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(HexCodeFile hcf, int pos, String comment) {
        String oldValue = hcf.addOperandComment(pos, comment);
        assert oldValue == null : "multiple comments for operand of instruction at " + pos + ": " + comment + ", " + oldValue;
    }

    @Override
    public String disassemble(RiResolvedMethod method) {
        return compiler.getVMEntries().disassembleJava((HotSpotMethodResolved) method);
    }

    @Override
    public RiResolvedType asRiType(CiKind kind) {
        return (RiResolvedType) compiler.getVMEntries().getType(kind.toJavaClass());
    }

    @Override
    public RiResolvedType getTypeOf(CiConstant constant) {
        return (RiResolvedType) compiler.getVMEntries().getRiType(constant);
    }

    @Override
    public boolean isExceptionType(RiResolvedType type) {
        return type.isSubtypeOf((RiResolvedType) compiler.getVMEntries().getType(Throwable.class));
    }

    @Override
    public Object registerCompilerStub(CiTargetMethod targetMethod, String name) {
        return HotSpotTargetMethod.installStub(compiler, targetMethod, name);
    }

    @Override
    public int sizeOfLockData() {
        // TODO shouldn't be hard coded
        return 8;
    }

    @Override
    public int sizeOfBasicObjectLock() {
        // TODO shouldn't be hard coded
        return 2 * 8;
    }

    @Override
    public int basicObjectLockOffsetInBytes() {
        return 8;
    }

    @Override
    public boolean areConstantObjectsEqual(CiConstant x, CiConstant y) {
        return compiler.getVMEntries().compareConstantObjects(x, y);
    }

    @Override
    public RiRegisterConfig getRegisterConfig(RiMethod method) {
        return regConfig;
    }

    /**
     * HotSpots needs an area suitable for storing a program counter for temporary use during the deoptimization process.
     */
    @Override
    public int getCustomStackAreaSize() {
        // TODO shouldn't be hard coded
        return 8;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return config.runtimeCallStackSize;
    }

    @Override
    public int getArrayLength(CiConstant array) {
        return compiler.getVMEntries().getArrayLength(array);
    }

    @Override
    public Class<?> asJavaClass(CiConstant c) {
        return (Class<?>) c.asObject();
    }

    @Override
    public Object asJavaObject(CiConstant c) {
        return c.asObject();
    }

    @Override
    public void lower(Node n, CiLoweringTool tool) {
        if (!GraalOptions.Lower) {
            return;
        }
        StructuredGraph graph = (StructuredGraph) n.graph();

        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            SafeReadNode safeReadArrayLength = safeReadArrayLength(arrayLengthNode.graph(), arrayLengthNode.array());
            graph.replaceFixedWithFixed(arrayLengthNode, safeReadArrayLength);
            safeReadArrayLength.lower(tool);
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode field = (LoadFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            int displacement = ((HotSpotField) field.field()).offset();
            assert field.kind() != CiKind.Illegal;
            ReadNode memoryRead = graph.add(new ReadNode(field.field().kind(true).stackKind(), field.object(), LocationNode.create(field.field(), field.field().kind(true), displacement, graph)));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(field.object(), false))));
            graph.replaceFixedWithFixed(field, memoryRead);
        } else if (n instanceof StoreFieldNode) {
            StoreFieldNode storeField = (StoreFieldNode) n;
            if (storeField.isVolatile()) {
                return;
            }
            HotSpotField field = (HotSpotField) storeField.field();
            WriteNode memoryWrite = graph.add(new WriteNode(storeField.object(), storeField.value(), LocationNode.create(storeField.field(), storeField.field().kind(true), field.offset(), graph)));
            memoryWrite.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(storeField.object(), false))));
            memoryWrite.setStateAfter(storeField.stateAfter());
            graph.replaceFixedWithFixed(storeField, memoryWrite);

            if (field.kind(true) == CiKind.Object && !memoryWrite.value().isNullConstant()) {
                graph.addAfterFixed(memoryWrite, graph.add(new FieldWriteBarrier(memoryWrite.object())));
            }
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            GuardNode boundsCheck = createBoundsCheck(loadIndexed, tool);

            CiKind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.add(new ReadNode(elementKind.stackKind(), loadIndexed.array(), arrayLocation));
            memoryRead.setGuard(boundsCheck);
            graph.replaceFixedWithFixed(loadIndexed, memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            GuardNode boundsCheck = createBoundsCheck(storeIndexed, tool);

            CiKind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                // Store check!
                if (array.exactType() != null) {
                    RiResolvedType elementType = array.exactType().componentType();
                    if (elementType.superType() != null) {
                        AnchorNode anchor = graph.add(new AnchorNode());
                        graph.addBeforeFixed(storeIndexed, anchor);
                        ConstantNode type = graph.unique(ConstantNode.forCiConstant(elementType.getEncoding(Representation.ObjectHub), this, graph));
                        value = graph.unique(new CheckCastNode(anchor, type, elementType, value));
                    } else {
                        assert elementType.name().equals("Ljava/lang/Object;") : elementType.name();
                    }
                } else {
                    AnchorNode anchor = graph.add(new AnchorNode());
                    graph.addBeforeFixed(storeIndexed, anchor);
                    GuardNode guard = (GuardNode) tool.createGuard(graph.unique(new NullCheckNode(array, false)));
                    ReadNode arrayClass = graph.add(new ReadNode(CiKind.Object, array, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph)));
                    arrayClass.setGuard(guard);
                    graph.addBeforeFixed(storeIndexed, arrayClass);
                    ReadNode arrayElementKlass = graph.add(new ReadNode(CiKind.Object, arrayClass, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.arrayClassElementOffset, graph)));
                    value = graph.unique(new CheckCastNode(anchor, arrayElementKlass, null, value));
                }
            }
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation));
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());

            graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                graph.addAfterFixed(memoryWrite, graph.add(new ArrayWriteBarrier(array, arrayLocation)));
            }
        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            assert load.kind() != CiKind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, load.loadKind(), load.displacement(), load.offset(), graph);
            location.setIndexScalingEnabled(false);
            ReadNode memoryRead = graph.add(new ReadNode(load.kind(), load.object(), location));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(load.object(), false))));
            graph.replaceFixedWithFixed(load, memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, store.storeKind(), store.displacement(), store.offset(), graph);
            location.setIndexScalingEnabled(false);
            WriteNode write = graph.add(new WriteNode(store.object(), store.value(), location));
            FieldWriteBarrier barrier = graph.add(new FieldWriteBarrier(store.object()));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
            graph.addBeforeFixed(write, barrier);
        } else if (n instanceof ArrayHeaderSizeNode) {
            ArrayHeaderSizeNode arrayHeaderSize = (ArrayHeaderSizeNode) n;
            graph.replaceFloating(arrayHeaderSize, ConstantNode.forLong(config.getArrayOffset(arrayHeaderSize.elementKind()), n.graph()));
        } else if (n instanceof ReadHubNode) {
            ReadHubNode objectClassNode = (ReadHubNode) n;
            LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph);
            ReadNode memoryRead = graph.add(new ReadNode(CiKind.Object, objectClassNode.object(), location));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(objectClassNode.object(), false))));
            graph.replaceFixed(objectClassNode, memoryRead);
        }
    }

    private IndexedLocationNode createArrayLocation(Graph graph, CiKind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, config.getArrayOffset(elementKind), index, graph);
    }

    private static GuardNode createBoundsCheck(AccessIndexedNode n, CiLoweringTool tool) {
        return (GuardNode) tool.createGuard(n.graph().unique(new CompareNode(n.index(), Condition.BT, n.length())));
    }

    @Override
    public StructuredGraph intrinsicGraph(RiResolvedMethod caller, int bci, RiResolvedMethod method, List<? extends Node> parameters) {
        RiType holder = method.holder();
        String fullName = method.name() + method.signature().asString();
        String holderName = holder.name();
        if (holderName.equals("Ljava/lang/Object;")) {
            if (fullName.equals("getClass()Ljava/lang/Class;")) {
                ValueNode obj = (ValueNode) parameters.get(0);
                if (obj.stamp().nonNull() && obj.stamp().exactType() != null) {
                    StructuredGraph graph = new StructuredGraph();
                    ValueNode result = ConstantNode.forObject(obj.stamp().exactType().toJava(), this, graph);
                    ReturnNode ret = graph.add(new ReturnNode(result));
                    graph.start().setNext(ret);
                    return graph;
                }
                StructuredGraph graph = new StructuredGraph();
                LocalNode receiver = graph.unique(new LocalNode(CiKind.Object, 0));
                SafeReadNode klassOop = safeReadHub(graph, receiver);
                ReadNode result = graph.add(new ReadNode(CiKind.Object, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.classMirrorOffset, graph)));
                ReturnNode ret = graph.add(new ReturnNode(result));
                graph.start().setNext(klassOop);
                klassOop.setNext(ret);
                return graph;
            }
        } else if (holderName.equals("Ljava/lang/Class;")) {
            if (fullName.equals("getModifiers()I")) {
                StructuredGraph graph = new StructuredGraph();
                LocalNode receiver = graph.unique(new LocalNode(CiKind.Object, 0));
                SafeReadNode klassOop = safeRead(graph, CiKind.Object, receiver, config.klassOopOffset);
                graph.start().setNext(klassOop);
                // TODO(tw): Care about primitive classes!
                ReadNode result = graph.add(new ReadNode(CiKind.Int, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Int, config.klassModifierFlagsOffset, graph)));
                ReturnNode ret = graph.add(new ReturnNode(result));
                klassOop.setNext(ret);
                return graph;
            }
        } else if (holderName.equals("Ljava/lang/Thread;")) {
            if (fullName.equals("currentThread()Ljava/lang/Thread;")) {
                StructuredGraph graph = new StructuredGraph();
                ReturnNode ret = graph.add(new ReturnNode(graph.unique(new CurrentThread(config.threadObjectOffset))));
                graph.start().setNext(ret);
                return graph;
            }
        }
        return null;
    }

    private SafeReadNode safeReadHub(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Object, value, config.hubOffset);
    }

    private SafeReadNode safeReadArrayLength(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Int, value, config.arrayLengthOffset);
    }

    private static SafeReadNode safeRead(Graph graph, CiKind kind, ValueNode value, int offset) {
        return graph.add(new SafeReadNode(kind, value, LocationNode.create(LocationNode.FINAL_LOCATION, kind, offset, graph)));
    }

    public RiResolvedType getType(Class<?> clazz) {
        return (RiResolvedType) compiler.getVMEntries().getType(clazz);
    }

    public Object asCallTarget(Object target) {
        return target;
    }

    public long getMaxCallTargetOffset(CiRuntimeCall rtcall) {
        return compiler.getVMEntries().getMaxCallTargetOffset(rtcall);
    }

    public RiResolvedMethod getRiMethod(Method reflectionMethod) {
        return (RiResolvedMethod) compiler.getVMEntries().getRiMethod(reflectionMethod);
    }

    @Override
    public void installMethod(RiResolvedMethod method, CiTargetMethod code) {
        synchronized (method) {
            if (((HotSpotMethodResolvedImpl) method).callback() == null) {
                compiler.getVMEntries().installMethod(new HotSpotTargetMethod(compiler, (HotSpotMethodResolved) method, code), true);
            } else {
                // callback stub is installed.
            }
        }
    }

    @Override
    public RiCompiledMethod addMethod(RiResolvedMethod method, CiTargetMethod code) {
        return compiler.getVMEntries().installMethod(new HotSpotTargetMethod(compiler, (HotSpotMethodResolved) method, code), false);
    }

    public void installMethodCallback(RiResolvedMethod method, CiGenericCallback callback) {
        synchronized (method) {
            ((HotSpotMethodResolvedImpl) method).setCallback(callback);
            CiTargetMethod callbackStub = createCallbackStub(method, callback);
            compiler.getVMEntries().installMethod(new HotSpotTargetMethod(compiler, (HotSpotMethodResolved) method, callbackStub), true);
        }
    }

    @Override
    public RiRegisterConfig getGlobalStubRegisterConfig() {
        return globalStubRegConfig;
    }

    private CiTargetMethod createCallbackStub(RiResolvedMethod method, CiGenericCallback callback) {
        StructuredGraph graph = new StructuredGraph();
        FrameStateBuilder frameState = new FrameStateBuilder(method, method.maxLocals(), method.maxStackSize(), graph, false);
        ValueNode local0 = frameState.loadLocal(0);

        FrameState initialFrameState = frameState.create(0);
        graph.start().setStateAfter(initialFrameState);

        ConstantNode callbackNode = ConstantNode.forObject(callback, this, graph);

        RuntimeCallNode runtimeCall = graph.add(new RuntimeCallNode(CiRuntimeCall.GenericCallback, new ValueNode[] {callbackNode, local0}));
        runtimeCall.setStateAfter(initialFrameState.duplicateModified(0, false, CiKind.Void, runtimeCall));

        @SuppressWarnings("unused")
        HotSpotCompiledMethod hotSpotCompiledMethod = new HotSpotCompiledMethod(null); // initialize class...
        RiResolvedType compiledMethodClass = getType(HotSpotCompiledMethod.class);
        RiResolvedField nmethodField = null;
        for (RiResolvedField field : compiledMethodClass.declaredFields()) {
            if (field.name().equals("nmethod")) {
                nmethodField = field;
                break;
            }
        }
        assert nmethodField != null;
        LoadFieldNode loadField = graph.add(new LoadFieldNode(runtimeCall, nmethodField));

        CompareNode compare = graph.unique(new CompareNode(loadField, Condition.EQ, ConstantNode.forLong(0, graph)));

        IfNode ifNull = graph.add(new IfNode(compare, 0.01));

        BeginNode beginInvalidated = graph.add(new BeginNode());
        DeoptimizeNode deoptInvalidated = graph.add(new DeoptimizeNode(DeoptAction.None));

        BeginNode beginTailcall = graph.add(new BeginNode());
        TailcallNode tailcall = graph.add(new TailcallNode(loadField, initialFrameState));
        DeoptimizeNode deoptEnd = graph.add(new DeoptimizeNode(DeoptAction.InvalidateRecompile));

        graph.start().setNext(runtimeCall);
        runtimeCall.setNext(loadField);
        loadField.setNext(ifNull);
        ifNull.setTrueSuccessor(beginInvalidated);
        ifNull.setFalseSuccessor(beginTailcall);
        beginInvalidated.setNext(deoptInvalidated);
        beginTailcall.setNext(tailcall);
        tailcall.setNext(deoptEnd);

        CiTargetMethod result = compiler.getCompiler().compileMethod(method, graph, -1, PhasePlan.DEFAULT);
        return result;
    }
}
