/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.runtime;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.graph.*;
import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.calc.*;
import com.oracle.max.graal.compiler.nodes.calc.ConditionalNode.ConditionalStructure;
import com.oracle.max.graal.compiler.nodes.extended.*;
import com.oracle.max.graal.compiler.nodes.java.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.runtime.nodes.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Call;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.max.asm.dis.*;
import com.sun.max.lang.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 */
public class HotSpotRuntime implements RiRuntime {
    private static final long DOUBLENAN_RAW_LONG_BITS = Double.doubleToRawLongBits(Double.NaN);
    private static final int FLOATNAN_RAW_INT_BITS = Float.floatToRawIntBits(Float.NaN);

    final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    final HotSpotRegisterConfig globalStubRegConfig;
    private final Compiler compiler;
    private IdentityHashMap<RiMethod, CompilerGraph> intrinsicGraphs = new IdentityHashMap<RiMethod, CompilerGraph>();


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

    @Override
    public String disassemble(byte[] code, long address) {
        return disassemble(code, new DisassemblyPrinter(false), address);
    }

    private String disassemble(byte[] code, DisassemblyPrinter disassemblyPrinter, long address) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ISA instructionSet = ISA.AMD64;
        Disassembler.disassemble(byteArrayOutputStream, code, instructionSet, WordWidth.BITS_64, address, null, disassemblyPrinter);
        return byteArrayOutputStream.toString();
    }

    @Override
    public String disassemble(final CiTargetMethod targetMethod) {

        final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {

            private String toString(Call call) {
                if (call.runtimeCall != null) {
                    return "{" + call.runtimeCall.name() + "}";
                } else if (call.symbol != null) {
                    return "{" + call.symbol + "}";
                } else if (call.stubID != null) {
                    return "{" + call.stubID + "}";
                } else {
                    return "{" + call.method + "}";
                }
            }

            private String siteInfo(int pcOffset) {
                for (Call call : targetMethod.directCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Call call : targetMethod.indirectCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Safepoint site : targetMethod.safepoints) {
                    if (site.pcOffset == pcOffset) {
                        return "{safepoint}";
                    }
                }
                for (DataPatch site : targetMethod.dataReferences) {
                    if (site.pcOffset == pcOffset) {
                        return "{" + site.constant + "}";
                    }
                }
                return null;
            }

            @Override
            protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                final String string = super.disassembledObjectString(disassembler, disassembledObject);

                String site = siteInfo(disassembledObject.startPosition());
                if (site != null) {
                    return string + " " + site;
                }
                return string;
            }
        };
        final byte[] code = Arrays.copyOf(targetMethod.targetCode(), targetMethod.targetCodeSize());
        return disassemble(code, disassemblyPrinter, 0L);
    }

    @Override
    public String disassemble(RiMethod method) {
        return "No disassembler available";
    }

    @Override
    public RiConstantPool getConstantPool(RiMethod method) {
        return ((HotSpotTypeResolved) method.holder()).constantPool();
    }

    public Class<?> getJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public RiType asRiType(CiKind kind) {
        return compiler.getVMEntries().getType(kind.toJavaClass());
    }

    @Override
    public RiType getTypeOf(CiConstant constant) {
        return compiler.getVMEntries().getRiType(constant);
    }

    @Override
    public boolean isExceptionType(RiType type) {
        return type.isSubtypeOf(compiler.getVMEntries().getType(Throwable.class));
    }

    @Override
    public RiSnippets getSnippets() {
        throw new UnsupportedOperationException("getSnippets");
    }

    @Override
    public boolean mustInline(RiMethod method) {
        return false;
    }

    @Override
    public boolean mustNotCompile(RiMethod method) {
        return false;
    }

    @Override
    public boolean mustNotInline(RiMethod method) {
        return Modifier.isNative(method.accessFlags());
    }

    @Override
    public Object registerCompilerStub(CiTargetMethod targetMethod, String name) {
        return HotSpotTargetMethod.installStub(compiler, targetMethod, name);
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
    public CiConstant invoke(RiMethod method, CiMethodInvokeArguments args) {
        return null;
    }

    @Override
    public CiConstant foldWordOperation(int opcode, CiMethodInvokeArguments args) {
        throw new UnsupportedOperationException("foldWordOperation");
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
        return 8;
    }

    @Override
    public boolean supportsArrayIntrinsics() {
        return true;
    }

    @Override
    public int getArrayLength(CiConstant array) {
        return compiler.getVMEntries().getArrayLength(array);
    }

    @Override
    public Class<?> asJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public Object asJavaObject(CiConstant c) {
        return null;
    }

    @Override
    public void lower(Node n, CiLoweringTool tool) {
        if (!GraalOptions.Lower) {
            return;
        }

        if (n instanceof LoadFieldNode) {
            LoadFieldNode field = (LoadFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            Graph graph = field.graph();
            int displacement = ((HotSpotField) field.field()).offset();
            assert field.kind != CiKind.Illegal;
            ReadNode memoryRead = new ReadNode(field.field().kind().stackKind(), field.object(), LocationNode.create(field.field(), field.field().kind(), displacement, graph), graph);
            memoryRead.setGuard((GuardNode) tool.createGuard(new IsNonNullNode(field.object(), graph)));
            FixedNode next = field.next();
            field.setNext(null);
            memoryRead.setNext(next);
            field.replaceAndDelete(memoryRead);
        } else if (n instanceof StoreFieldNode) {
            StoreFieldNode field = (StoreFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            Graph graph = field.graph();
            int displacement = ((HotSpotField) field.field()).offset();
            WriteNode memoryWrite = new WriteNode(CiKind.Illegal, field.object(), field.value(), LocationNode.create(field.field(), field.field().kind(), displacement, graph), graph);
            memoryWrite.setGuard((GuardNode) tool.createGuard(new IsNonNullNode(field.object(), graph)));
            memoryWrite.setStateAfter(field.stateAfter());
            FixedNode next = field.next();
            field.setNext(null);
            if (field.field().kind() == CiKind.Object && !field.value().isNullConstant()) {
                FieldWriteBarrier writeBarrier = new FieldWriteBarrier(field.object(), graph);
                memoryWrite.setNext(writeBarrier);
                writeBarrier.setNext(next);
            } else {
                memoryWrite.setNext(next);
            }
            field.replaceAndDelete(memoryWrite);
        } else if (n instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexed = (LoadIndexedNode) n;
            Graph graph = loadIndexed.graph();
            GuardNode boundsCheck = createBoundsCheck(loadIndexed, tool);

            CiKind elementKind = loadIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind);
            arrayLocation.setIndex(loadIndexed.index());
            ReadNode memoryRead = new ReadNode(elementKind.stackKind(), loadIndexed.array(), arrayLocation, graph);
            memoryRead.setGuard(boundsCheck);
            FixedNode next = loadIndexed.next();
            loadIndexed.setNext(null);
            memoryRead.setNext(next);
            loadIndexed.replaceAndDelete(memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            Graph graph = storeIndexed.graph();
            AnchorNode anchor = new AnchorNode(graph);
            GuardNode boundsCheck = createBoundsCheck(storeIndexed, tool);
            anchor.addGuard(boundsCheck);


            CiKind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind);
            arrayLocation.setIndex(storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                // Store check!
                if (array.exactType() != null) {
                    RiType elementType = array.exactType().componentType();
                    if (elementType.superType() != null) {
                        ConstantNode type = new ConstantNode(elementType.getEncoding(Representation.ObjectHub), graph);
                        value = new CheckCastNode(type, value, graph);
                    } else {
                        assert elementType.name().equals("Ljava/lang/Object;") : elementType.name();
                    }
                } else {
                    ReadNode arrayElementKlass = readArrayElementKlass(graph, array);
                    value = new CheckCastNode(arrayElementKlass, value, graph);
                }
            }
            WriteNode memoryWrite = new WriteNode(elementKind.stackKind(), array, value, arrayLocation, graph);
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());
            FixedNode next = storeIndexed.next();
            storeIndexed.setNext(null);
            anchor.setNext(memoryWrite);
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                ArrayWriteBarrier writeBarrier = new ArrayWriteBarrier(array, arrayLocation, graph);
                memoryWrite.setNext(writeBarrier);
                writeBarrier.setNext(next);
            } else {
                memoryWrite.setNext(next);
            }
            storeIndexed.replaceAtPredecessors(anchor);
            storeIndexed.delete();
        } else if (n instanceof UnsafeLoad) {
            UnsafeLoad load = (UnsafeLoad) n;
            Graph graph = load.graph();
            assert load.kind != CiKind.Illegal;
            LocationNode location = LocationNode.create(LocationNode.UNSAFE_ACCESS_LOCATION, load.kind, 0, graph);
            location.setIndex(load.offset());
            location.setIndexScalingEnabled(false);
            ReadNode memoryRead = new ReadNode(load.kind.stackKind(), load.object(), location, graph);
            memoryRead.setGuard((GuardNode) tool.createGuard(new IsNonNullNode(load.object(), graph)));
            FixedNode next = load.next();
            load.setNext(null);
            memoryRead.setNext(next);
            load.replaceAndDelete(memoryRead);
        } else if (n instanceof UnsafeStore) {
            UnsafeStore store = (UnsafeStore) n;
            Graph graph = store.graph();
            assert store.kind != CiKind.Illegal;
            LocationNode location = LocationNode.create(LocationNode.UNSAFE_ACCESS_LOCATION, store.kind, 0, graph);
            location.setIndex(store.offset());
            location.setIndexScalingEnabled(false);
            WriteNode write = new WriteNode(store.kind.stackKind(), store.object(), store.value(), location, graph);
            FieldWriteBarrier barrier = new FieldWriteBarrier(store.object(), graph);
            FixedNode next = store.next();
            store.setNext(null);
            barrier.setNext(next);
            write.setNext(barrier);
            write.setStateAfter(store.stateAfter());
            store.replaceAtPredecessors(write);
            store.delete();
        }
    }

    private ReadNode readArrayElementKlass(Graph graph, ValueNode array) {
        ReadNode arrayKlass = readHub(graph, array);
        ReadNode arrayElementKlass = new ReadNode(CiKind.Object, arrayKlass, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.arrayClassElementOffset, graph), graph);
        return arrayElementKlass;
    }

    private LocationNode createArrayLocation(Graph graph, CiKind elementKind) {
        return LocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, config.getArrayOffset(elementKind), graph);
    }

    private GuardNode createBoundsCheck(AccessIndexedNode n, CiLoweringTool tool) {
        return (GuardNode) tool.createGuard(new CompareNode(n.index(), Condition.BT, n.length(), n.graph()));
    }

    @Override
    public Graph intrinsicGraph(RiMethod caller, int bci, RiMethod method, List<? extends Node> parameters) {
        if (!intrinsicGraphs.containsKey(method)) {
            RiType holder = method.holder();
            String fullName = method.name() + method.signature().asString();
            String holderName = holder.name();
            if (holderName.equals("Ljava/lang/Object;")) {
                if (fullName.equals("getClass()Ljava/lang/Class;")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode receiver = new LocalNode(CiKind.Object, 0, graph);
                    ReadNode klassOop = readHub(graph, receiver);
                    ReturnNode ret = new ReturnNode(new ReadNode(CiKind.Object, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.classMirrorOffset, graph), graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/System;")) {
                if (fullName.equals("arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode src = new LocalNode(CiKind.Object, 0, graph);
                    LocalNode srcPos = new LocalNode(CiKind.Int, 1, graph);
                    LocalNode dest = new LocalNode(CiKind.Object, 2, graph);
                    LocalNode destPos = new LocalNode(CiKind.Int, 3, graph);
                    ValueNode length = new LocalNode(CiKind.Int, 4, graph);
                    src.setDeclaredType(((ValueNode) parameters.get(0)).declaredType());
                    dest.setDeclaredType(((ValueNode) parameters.get(2)).declaredType());

                    if (src.declaredType() == null || dest.declaredType() == null) {
                        return null;
                    }

                    if (src.declaredType() != dest.declaredType()) {
                        return null;
                    }

                    if (!src.declaredType().isArrayClass()) {
                        return null;
                    }

                    CiKind componentType = src.declaredType().componentType().kind();
                    if (componentType == CiKind.Object) {
                        return null;
                    }

                    FrameState stateBefore = new FrameState(method, FrameState.BEFORE_BCI, 0, 0, 0, false, graph);
                    FrameState stateAfter = new FrameState(method, FrameState.AFTER_BCI, 0, 0, 0, false, graph);

                    // Add preconditions.
                    FixedGuardNode guard = new FixedGuardNode(graph);
                    ArrayLengthNode srcLength = new ArrayLengthNode(src, graph);
                    ArrayLengthNode destLength = new ArrayLengthNode(dest, graph);
                    IntegerAddNode upperLimitSrc = new IntegerAddNode(CiKind.Int, srcPos, length, graph);
                    IntegerAddNode upperLimitDest = new IntegerAddNode(CiKind.Int, destPos, length, graph);
                    guard.addNode(new CompareNode(srcPos, Condition.BE, srcLength, graph));
                    guard.addNode(new CompareNode(destPos, Condition.BE, destLength, graph));
                    guard.addNode(new CompareNode(length, Condition.GE, ConstantNode.forInt(0, graph), graph));
                    guard.addNode(new CompareNode(upperLimitSrc, Condition.LE, srcLength, graph));
                    guard.addNode(new CompareNode(upperLimitDest, Condition.LE, destLength, graph));
                    graph.start().setNext(guard);

                    LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, componentType, config.getArrayOffset(componentType), graph);

                    // Build normal vector instruction.
                    CreateVectorNode normalVector = new CreateVectorNode(false, length, graph);
                    ReadVectorNode values = new ReadVectorNode(new IntegerAddVectorNode(normalVector, srcPos, graph), src, location, graph);
                    new WriteVectorNode(new IntegerAddVectorNode(normalVector, destPos, graph), dest, location, values, graph);
                    normalVector.setStateAfter(stateAfter);

                    // Build reverse vector instruction.
                    CreateVectorNode reverseVector = new CreateVectorNode(true, length, graph);
                    ReadVectorNode reverseValues = new ReadVectorNode(new IntegerAddVectorNode(reverseVector, srcPos, graph), src, location, graph);
                    new WriteVectorNode(new IntegerAddVectorNode(reverseVector, destPos, graph), dest, location, reverseValues, graph);
                    reverseVector.setStateAfter(stateAfter);

                    IfNode ifNode = new IfNode(new CompareNode(src, Condition.EQ, dest, graph), 0.5, graph);
                    guard.setNext(ifNode);

                    IfNode secondIf = new IfNode(new CompareNode(srcPos, Condition.LT, destPos, graph), 0.5, graph);
                    ifNode.setTrueSuccessor(secondIf);

                    secondIf.setTrueSuccessor(reverseVector);

                    MergeNode merge1 = new MergeNode(graph);
                    merge1.addEnd(new EndNode(graph));
                    merge1.addEnd(new EndNode(graph));
                    merge1.setStateAfter(stateBefore);


                    InvokeNode newInvoke = null;
                    if (componentType == CiKind.Object) {
                        ValueNode srcClass = readHub(graph, src);
                        ValueNode destClass = readHub(graph, dest);
                        IfNode elementClassIf = new IfNode(new CompareNode(srcClass, Condition.EQ, destClass, graph), 0.5, graph);
                        ifNode.setFalseSuccessor(elementClassIf);
                        newInvoke = new InvokeNode(bci, Bytecodes.INVOKESTATIC, CiKind.Void, new ValueNode[]{src, srcPos, dest, destPos, length}, method, method.signature().returnType(method.holder()), graph);
                        newInvoke.setCanInline(false);
                        newInvoke.setStateAfter(stateAfter);
                        elementClassIf.setFalseSuccessor(newInvoke);
                        elementClassIf.setTrueSuccessor(merge1.endAt(0));
                    } else {
                        ifNode.setFalseSuccessor(merge1.endAt(0));
                    }

                    secondIf.setFalseSuccessor(merge1.endAt(1));
                    merge1.setNext(normalVector);

                    MergeNode merge2 = new MergeNode(graph);
                    merge2.addEnd(new EndNode(graph));
                    merge2.addEnd(new EndNode(graph));
                    merge2.setStateAfter(stateAfter);

                    normalVector.setNext(merge2.endAt(0));
                    reverseVector.setNext(merge2.endAt(1));

                    if (newInvoke != null) {
                        merge2.addEnd(new EndNode(graph));
                        newInvoke.setNext(merge2.endAt(2));
                    }

                    ReturnNode ret = new ReturnNode(null, graph);
                    merge2.setNext(ret);
                    graph.setReturn(ret);
                    return graph;
                }
            } else if (holderName.equals("Ljava/lang/Float;")) { //XXX (gd) the non-raw versions of (F/D)2(I/L) should return a sanitized NaN in the NaN case.
                if (fullName.equals("floatToRawIntBits(F)I")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    ReturnNode ret = new ReturnNode(new FPConversionNode(CiKind.Int, new LocalNode(CiKind.Float, 0, graph), graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("floatToIntBits(F)I")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode arg = new LocalNode(CiKind.Float, 0, graph);
                    CompareNode isNan = new CompareNode(arg, Condition.NE, arg, graph);
                    isNan.setUnorderedIsTrue(true);
                    FPConversionNode fpConv = new FPConversionNode(CiKind.Int, arg, graph);
                    ConditionalStructure conditionalStructure = ConditionalNode.createConditionalStructure(isNan, ConstantNode.forInt(FLOATNAN_RAW_INT_BITS, graph), fpConv, 0.1);
                    ReturnNode ret = new ReturnNode(conditionalStructure.phi, graph);
                    graph.start().setNext(conditionalStructure.ifNode);
                    conditionalStructure.merge.setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("intBitsToFloat(I)F")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    ReturnNode ret = new ReturnNode(new FPConversionNode(CiKind.Float, new LocalNode(CiKind.Int, 0, graph), graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Double;")) {
                if (fullName.equals("doubleToRawLongBits(D)J")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    ReturnNode ret = new ReturnNode(new FPConversionNode(CiKind.Long, new LocalNode(CiKind.Double, 0, graph), graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("doubleToLongBits(D)J")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode arg = new LocalNode(CiKind.Double, 0, graph);
                    CompareNode isNan = new CompareNode(arg, Condition.NE, arg, graph);
                    isNan.setUnorderedIsTrue(true);
                    FPConversionNode fpConv = new FPConversionNode(CiKind.Long, arg, graph);
                    ConditionalStructure conditionalStructure = ConditionalNode.createConditionalStructure(isNan, ConstantNode.forLong(DOUBLENAN_RAW_LONG_BITS, graph), fpConv, 0.1);
                    ReturnNode ret = new ReturnNode(conditionalStructure.phi, graph);
                    graph.start().setNext(conditionalStructure.ifNode);
                    conditionalStructure.merge.setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("longBitsToDouble(J)D")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    ReturnNode ret = new ReturnNode(new FPConversionNode(CiKind.Double, new LocalNode(CiKind.Long, 0, graph), graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Thread;")) {
                if (fullName.equals("currentThread()Ljava/lang/Thread;")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    ReturnNode ret = new ReturnNode(new CurrentThread(config.threadObjectOffset, graph), graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Lsun/misc/Unsafe;")) {
                if (fullName.equals("getObject(Ljava/lang/Object;J)Ljava/lang/Object;")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode object = new LocalNode(CiKind.Object, 1, graph);
                    LocalNode offset = new LocalNode(CiKind.Long, 2, graph);
                    UnsafeLoad load = new UnsafeLoad(object, offset, CiKind.Object, graph);
                    ReturnNode ret = new ReturnNode(load, graph);
                    load.setNext(ret);
                    graph.start().setNext(load);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                } else if (fullName.equals("putObject(Ljava/lang/Object;JLjava/lang/Object;)V")) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode object = new LocalNode(CiKind.Object, 1, graph);
                    LocalNode offset = new LocalNode(CiKind.Long, 2, graph);
                    LocalNode value = new LocalNode(CiKind.Object, 3, graph);
                    UnsafeStore store = new UnsafeStore(object, offset, value, CiKind.Object, graph);
                    FrameState frameState = new FrameState(method, FrameState.AFTER_BCI, 0, 0, 0, false, graph);
                    store.setStateAfter(frameState);
                    ReturnNode ret = new ReturnNode(null, graph);
                    store.setNext(ret);
                    graph.start().setNext(store);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            } else if (holderName.equals("Ljava/lang/Math;")) {
                MathIntrinsicNode.Operation op = null;
                if (fullName.equals("abs(D)D")) {
                    op = MathIntrinsicNode.Operation.ABS;
                } else if (fullName.equals("sqrt(D)D")) {
                    op = MathIntrinsicNode.Operation.SQRT;
                }
                if (op != null) {
                    CompilerGraph graph = new CompilerGraph(this);
                    LocalNode value = new LocalNode(CiKind.Double, 0, graph);
                    MathIntrinsicNode min = new MathIntrinsicNode(value, op, graph);
                    ReturnNode ret = new ReturnNode(min, graph);
                    graph.start().setNext(ret);
                    graph.setReturn(ret);
                    intrinsicGraphs.put(method, graph);
                }
            }

            if (!intrinsicGraphs.containsKey(method)) {
                intrinsicGraphs.put(method, null);
            }
        }
        return intrinsicGraphs.get(method);
    }

    private ReadNode readHub(Graph graph, ValueNode value) {
        return new ReadNode(CiKind.Object, value, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph), graph);
    }

    @Override
    public RiType getType(Class<?> clazz) {
        return compiler.getVMEntries().getType(clazz);
    }
}
