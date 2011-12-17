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
package com.oracle.max.graal.hotspot;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.hotspot.nodes.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.java.*;
import com.oracle.max.graal.snippets.nodes.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.max.asm.dis.*;
import com.sun.max.lang.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 */
public class HotSpotRuntime implements GraalRuntime {
    private static final long DOUBLENAN_RAW_LONG_BITS = Double.doubleToRawLongBits(Double.NaN);
    private static final int FLOATNAN_RAW_INT_BITS = Float.floatToRawIntBits(Float.NaN);

    final GraalContext context;
    final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    final HotSpotRegisterConfig globalStubRegConfig;
    private final Compiler compiler;
    // TODO(ls) this is not a permanent solution - there should be a more sophisticated compiler oracle
    private HashSet<RiResolvedMethod> notInlineableMethods = new HashSet<RiResolvedMethod>();

    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();

    public HotSpotRuntime(GraalContext context, HotSpotVMConfig config, Compiler compiler) {
        this.context = context;
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

            private String siteInfo(int pcOffset) {
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
    public String disassemble(RiResolvedMethod method) {
        return "No disassembler available";
    }

    public Class<?> getJavaClass(CiConstant c) {
        return null;
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
    public boolean mustInline(RiResolvedMethod method) {
        return false;
    }

    @Override
    public boolean mustNotCompile(RiResolvedMethod method) {
        return false;
    }

    @Override
    public boolean mustNotInline(RiResolvedMethod method) {
        if (notInlineableMethods.contains(method)) {
            return true;
        }
        return Modifier.isNative(method.accessFlags());
    }

    public void makeNotInlineable(RiResolvedMethod method) {
        notInlineableMethods.add(method);
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

    public boolean isFoldable(RiResolvedMethod method) {
        return false;
    }

    @Override
    public CiConstant fold(RiResolvedMethod method, CiConstant[] args) {
        return null;
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

        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            SafeReadNode safeReadArrayLength = safeReadArrayLength(arrayLengthNode.graph(), arrayLengthNode.array());
            FixedNode nextNode = arrayLengthNode.next();
            arrayLengthNode.clearSuccessors();
            safeReadArrayLength.setNext(nextNode);
            arrayLengthNode.replaceAndDelete(safeReadArrayLength);
            safeReadArrayLength.lower(tool);
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode field = (LoadFieldNode) n;
            if (field.isVolatile()) {
                return;
            }
            Graph graph = field.graph();
            int displacement = ((HotSpotField) field.field()).offset();
            assert field.kind() != CiKind.Illegal;
            ReadNode memoryRead = graph.unique(new ReadNode(field.field().kind(true).stackKind(), field.object(), LocationNode.create(field.field(), field.field().kind(true), displacement, graph)));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(field.object(), false))));
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
            WriteNode memoryWrite = graph.add(new WriteNode(field.object(), field.value(), LocationNode.create(field.field(), field.field().kind(true), displacement, graph)));
            memoryWrite.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(field.object(), false))));
            memoryWrite.setStateAfter(field.stateAfter());
            FixedNode next = field.next();
            field.setNext(null);
            if (field.field().kind(true) == CiKind.Object && !field.value().isNullConstant()) {
                FieldWriteBarrier writeBarrier = graph.add(new FieldWriteBarrier(field.object()));
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
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, loadIndexed.index());
            ReadNode memoryRead = graph.unique(new ReadNode(elementKind.stackKind(), loadIndexed.array(), arrayLocation));
            memoryRead.setGuard(boundsCheck);
            FixedNode next = loadIndexed.next();
            loadIndexed.setNext(null);
            memoryRead.setNext(next);
            loadIndexed.replaceAndDelete(memoryRead);
        } else if (n instanceof StoreIndexedNode) {
            StoreIndexedNode storeIndexed = (StoreIndexedNode) n;
            Graph graph = storeIndexed.graph();
            AnchorNode anchor = graph.add(new AnchorNode());
            GuardNode boundsCheck = createBoundsCheck(storeIndexed, tool);

            FixedWithNextNode append = anchor;

            CiKind elementKind = storeIndexed.elementKind();
            LocationNode arrayLocation = createArrayLocation(graph, elementKind, storeIndexed.index());
            ValueNode value = storeIndexed.value();
            ValueNode array = storeIndexed.array();
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                // Store check!
                if (array.exactType() != null) {
                    RiResolvedType elementType = array.exactType().componentType();
                    if (elementType.superType() != null) {
                        ConstantNode type = graph.unique(ConstantNode.forCiConstant(elementType.getEncoding(Representation.ObjectHub), this, graph));
                        value = graph.unique(new CheckCastNode(anchor, type, elementType, value));
                    } else {
                        assert elementType.name().equals("Ljava/lang/Object;") : elementType.name();
                    }
                } else {
                    GuardNode guard = (GuardNode) tool.createGuard(graph.unique(new NullCheckNode(array, false)));
                    ReadNode arrayClass = graph.unique(new ReadNode(CiKind.Object, array, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.hubOffset, graph)));
                    arrayClass.setGuard(guard);
                    append.setNext(arrayClass);
                    append = arrayClass;
                    ReadNode arrayElementKlass = graph.unique(new ReadNode(CiKind.Object, arrayClass, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Object, config.arrayClassElementOffset, graph)));
                    value = graph.unique(new CheckCastNode(anchor, arrayElementKlass, null, value));
                }
            }
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation));
            memoryWrite.setGuard(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());
            FixedNode next = storeIndexed.next();
            storeIndexed.setNext(null);
            append.setNext(memoryWrite);
            if (elementKind == CiKind.Object && !value.isNullConstant()) {
                ArrayWriteBarrier writeBarrier = graph.add(new ArrayWriteBarrier(array, arrayLocation));
                memoryWrite.setNext(writeBarrier);
                writeBarrier.setNext(next);
            } else {
                memoryWrite.setNext(next);
            }
            storeIndexed.replaceAtPredecessors(anchor);
            storeIndexed.safeDelete();
        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            Graph graph = load.graph();
            assert load.kind() != CiKind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, load.loadKind(), load.displacement(), load.offset(), graph);
            location.setIndexScalingEnabled(false);
            ReadNode memoryRead = graph.unique(new ReadNode(load.kind(), load.object(), location));
            memoryRead.setGuard((GuardNode) tool.createGuard(graph.unique(new NullCheckNode(load.object(), false))));
            FixedNode next = load.next();
            load.setNext(null);
            memoryRead.setNext(next);
            load.replaceAndDelete(memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            Graph graph = store.graph();
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, store.storeKind(), store.displacement(), store.offset(), graph);
            location.setIndexScalingEnabled(false);
            WriteNode write = graph.add(new WriteNode(store.object(), store.value(), location));
            FieldWriteBarrier barrier = graph.add(new FieldWriteBarrier(store.object()));
            FixedNode next = store.next();
            store.setNext(null);
            barrier.setNext(next);
            write.setNext(barrier);
            write.setStateAfter(store.stateAfter());
            store.replaceAtPredecessors(write);
            store.safeDelete();
        } else if (n instanceof ArrayHeaderSizeNode) {
            n.replaceAndDelete(ConstantNode.forLong(config.getArrayOffset(((ArrayHeaderSizeNode) n).elementKind()), n.graph()));
        }
    }

    private IndexedLocationNode createArrayLocation(Graph graph, CiKind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, config.getArrayOffset(elementKind), index, graph);
    }

    private GuardNode createBoundsCheck(AccessIndexedNode n, CiLoweringTool tool) {
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
                ReadNode result = graph.unique(new ReadNode(CiKind.Int, klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, CiKind.Int, config.klassModifierFlagsOffset, graph)));
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

    private boolean containsGraph(RiResolvedMethod method) {
        return method.compilerStorage().containsKey(Graph.class);
    }

    private SafeReadNode safeReadHub(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Object, value, config.hubOffset);
    }

    private SafeReadNode safeReadArrayLength(Graph graph, ValueNode value) {
        return safeRead(graph, CiKind.Int, value, config.arrayLengthOffset);
    }

    private SafeReadNode safeRead(Graph graph, CiKind kind, ValueNode value, int offset) {
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

    public void installMethod(RiMethod method, CiTargetMethod code) {
        HotSpotTargetMethod.installMethod(CompilerImpl.getInstance(), (HotSpotMethodResolved) method, code, true);
    }

    @Override
    public void executeOnCompilerThread(Runnable r) {
        tasks.add(r);
        compiler.getVMEntries().notifyJavaQueue();
    }

    public void pollJavaQueue() {
        Runnable r = tasks.poll();
        while (r != null) {
            try {
                r.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
            r = tasks.poll();
        }
    }

    @Override
    public RiCompiledMethod addMethod(RiResolvedMethod method, CiTargetMethod code) {
        Compiler compilerInstance = CompilerImpl.getInstance();
        return HotSpotTargetMethod.installMethod(compilerInstance, (HotSpotMethodResolved) method, code, false);
    }
}
