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

import static com.oracle.max.criutils.MemoryBarriers.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.CompilationResult.Safepoint;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.JavaType.Representation;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.hotspot.target.amd64.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;
import com.oracle.max.criutils.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 */
public class HotSpotRuntime implements GraalCodeCacheProvider {
    public final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    private final HotSpotRegisterConfig globalStubRegConfig;
    private final HotSpotGraalRuntime compiler;
    private CheckCastSnippets.Templates checkcastSnippets;
    private NewObjectSnippets.Templates newObjectSnippets;

    public HotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime compiler) {
        this.config = config;
        this.compiler = compiler;
        regConfig = new HotSpotRegisterConfig(config, false);
        globalStubRegConfig = new HotSpotRegisterConfig(config, true);

        System.setProperty(Backend.BACKEND_CLASS_PROPERTY, HotSpotAMD64Backend.class.getName());
    }

    public void installSnippets(SnippetInstaller installer) {
        installer.install(SystemSnippets.class);
        installer.install(UnsafeSnippets.class);
        installer.install(ArrayCopySnippets.class);
        installer.install(CheckCastSnippets.class);
        installer.install(NewObjectSnippets.class);
        checkcastSnippets = new CheckCastSnippets.Templates(this);
        newObjectSnippets = new NewObjectSnippets.Templates(this, compiler.getTarget(), config.useTLAB);
    }


    public HotSpotGraalRuntime getCompiler() {
        return compiler;
    }

    @Override
    public String disassemble(CodeInfo info, CompilationResult tm) {
        byte[] code = info.code();
        TargetDescription target = compiler.getTarget();
        HexCodeFile hcf = new HexCodeFile(code, info.start(), target.arch.name, target.wordSize * 8);
        if (tm != null) {
            HexCodeFile.addAnnotations(hcf, tm.annotations());
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
                    if (f.get(config).equals(call.target)) {
                        return f.getName();
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
        Field[] fields = HotSpotXirGenerator.class.getDeclaredFields();
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
    public ResolvedJavaType getResolvedJavaType(Kind kind) {
        return (ResolvedJavaType) compiler.getCompilerToVM().getType(kind.toJavaClass());
    }

    @Override
    public ResolvedJavaType getTypeOf(Constant constant) {
        return (ResolvedJavaType) compiler.getCompilerToVM().getJavaType(constant);
    }

    @Override
    public int sizeOfLockData() {
        // TODO shouldn't be hard coded
        return 8;
    }

    @Override
    public boolean areConstantObjectsEqual(Constant x, Constant y) {
        return compiler.getCompilerToVM().compareConstantObjects(x, y);
    }

    @Override
    public RegisterConfig getRegisterConfig(JavaMethod method) {
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
    public int getArrayLength(Constant array) {
        return compiler.getCompilerToVM().getArrayLength(array);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        if (n instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) n;
            SafeReadNode safeReadArrayLength = safeReadArrayLength(arrayLengthNode.array(), StructuredGraph.INVALID_GRAPH_ID);
            graph.replaceFixedWithFixed(arrayLengthNode, safeReadArrayLength);
        } else if (n instanceof LoadFieldNode) {
            LoadFieldNode field = (LoadFieldNode) n;
            int displacement = ((HotSpotResolvedJavaField) field.field()).offset();
            assert field.kind() != Kind.Illegal;
            ReadNode memoryRead = graph.add(new ReadNode(field.object(), LocationNode.create(field.field(), field.field().kind(), displacement, graph), field.stamp()));
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
            WriteNode memoryWrite = graph.add(new WriteNode(storeField.object(), storeField.value(), LocationNode.create(storeField.field(), storeField.field().kind(), field.offset(), graph)));
            memoryWrite.dependencies().add(tool.createNullCheckGuard(storeField.object(), storeField.leafGraphId()));
            memoryWrite.setStateAfter(storeField.stateAfter());
            graph.replaceFixedWithFixed(storeField, memoryWrite);

            FixedWithNextNode last = memoryWrite;
            if (field.kind() == Kind.Object && !memoryWrite.value().isNullConstant()) {
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
            if (expected.kind() == Kind.Object && !cas.newValue().isNullConstant()) {
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
            CheckCastNode checkcast = null;
            ValueNode array = storeIndexed.array();
            if (elementKind == Kind.Object && !value.isNullConstant()) {
                // Store check!
                ResolvedJavaType arrayType = array.objectStamp().type();
                if (arrayType != null && array.objectStamp().isExactType()) {
                    ResolvedJavaType elementType = arrayType.componentType();
                    if (elementType.superType() != null) {
                        ConstantNode type = ConstantNode.forConstant(elementType.getEncoding(Representation.ObjectHub), this, graph);
                        checkcast = graph.add(new CheckCastNode(type, elementType, value));
                        graph.addBeforeFixed(storeIndexed, checkcast);
                        value = checkcast;
                    } else {
                        assert elementType.name().equals("Ljava/lang/Object;") : elementType.name();
                    }
                } else {
                    ValueNode guard = tool.createNullCheckGuard(array, StructuredGraph.INVALID_GRAPH_ID);
                    FloatingReadNode arrayClass = graph.unique(new FloatingReadNode(array, LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Object, config.hubOffset, graph), null, StampFactory.objectNonNull()));
                    arrayClass.dependencies().add(guard);
                    FloatingReadNode arrayElementKlass = graph.unique(new FloatingReadNode(arrayClass, LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Object, config.arrayClassElementOffset, graph), null, StampFactory.objectNonNull()));
                    checkcast = graph.add(new CheckCastNode(arrayElementKlass, null, value));
                    graph.addBeforeFixed(storeIndexed, checkcast);
                    value = checkcast;
                }
            }
            WriteNode memoryWrite = graph.add(new WriteNode(array, value, arrayLocation));
            memoryWrite.dependencies().add(boundsCheck);
            memoryWrite.setStateAfter(storeIndexed.stateAfter());

            graph.replaceFixedWithFixed(storeIndexed, memoryWrite);

            if (elementKind == Kind.Object && !value.isNullConstant()) {
                graph.addAfterFixed(memoryWrite, graph.add(new ArrayWriteBarrier(array, arrayLocation)));
            }
        } else if (n instanceof UnsafeLoadNode) {
            UnsafeLoadNode load = (UnsafeLoadNode) n;
            assert load.kind() != Kind.Illegal;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, load.loadKind(), load.displacement(), load.offset(), graph, false);
            ReadNode memoryRead = graph.add(new ReadNode(load.object(), location, load.stamp()));
            if (load.object().kind().isObject()) {
                memoryRead.dependencies().add(tool.createNullCheckGuard(load.object(), StructuredGraph.INVALID_GRAPH_ID));
            }
            graph.replaceFixedWithFixed(load, memoryRead);
        } else if (n instanceof UnsafeStoreNode) {
            UnsafeStoreNode store = (UnsafeStoreNode) n;
            IndexedLocationNode location = IndexedLocationNode.create(LocationNode.ANY_LOCATION, store.storeKind(), store.displacement(), store.offset(), graph, false);
            ValueNode object = store.object();
            WriteNode write = graph.add(new WriteNode(object, store.value(), location));
            write.setStateAfter(store.stateAfter());
            graph.replaceFixedWithFixed(store, write);
            if (write.value().kind() == Kind.Object && !write.value().isNullConstant()) {
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
        } else if (n instanceof ReadHubNode) {
            ReadHubNode objectClassNode = (ReadHubNode) n;
            LocationNode location = LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Object, config.hubOffset, graph);
            ReadNode memoryRead = graph.add(new ReadNode(objectClassNode.object(), location, StampFactory.objectNonNull()));
            memoryRead.dependencies().add(tool.createNullCheckGuard(objectClassNode.object(), StructuredGraph.INVALID_GRAPH_ID));
            graph.replaceFixed(objectClassNode, memoryRead);
        } else if (n instanceof CheckCastNode) {
            if (shouldLower(graph, GraalOptions.HIRLowerCheckcast)) {
                checkcastSnippets.lower((CheckCastNode) n, tool);
            }
        } else if (n instanceof NewInstanceNode) {
            if (shouldLower(graph, GraalOptions.HIRLowerNewInstance)) {
                newObjectSnippets.lower((NewInstanceNode) n, tool);
            }
        } else if (n instanceof NewArrayNode) {
            if (shouldLower(graph, GraalOptions.HIRLowerNewArray)) {
                newObjectSnippets.lower((NewArrayNode) n, tool);
            }
        } else if (n instanceof TLABAllocateNode) {
            newObjectSnippets.lower((TLABAllocateNode) n, tool);
        } else if (n instanceof InitializeObjectNode) {
            newObjectSnippets.lower((InitializeObjectNode) n, tool);
        } else if (n instanceof InitializeArrayNode) {
            newObjectSnippets.lower((InitializeArrayNode) n, tool);
        } else {
            assert false : "Node implementing Lowerable not handled: " + n;
        }
    }

    private static boolean shouldLower(StructuredGraph graph, String option) {
        if (option != null) {
            if (option.length() == 0) {
                return true;
            }
            ResolvedJavaMethod method = graph.method();
            return method != null && MetaUtil.format("%H.%n", method).contains(option);
        }
        return false;
    }

    private static IndexedLocationNode createArrayLocation(Graph graph, Kind elementKind, ValueNode index) {
        return IndexedLocationNode.create(LocationNode.getArrayLocation(elementKind), elementKind, elementKind.arrayBaseOffset(), index, graph, true);
    }

    private SafeReadNode safeReadArrayLength(ValueNode array, long leafGraphId) {
        return safeRead(array.graph(), Kind.Int, array, config.arrayLengthOffset, StampFactory.positiveInt(), leafGraphId);
    }

    private static ValueNode createBoundsCheck(AccessIndexedNode n, LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) n.graph();
        ArrayLengthNode arrayLength = graph.add(new ArrayLengthNode(n.array()));
        ValueNode guard = tool.createGuard(graph.unique(new IntegerBelowThanNode(n.index(), arrayLength)), DeoptimizationReason.BoundsCheckException, DeoptimizationAction.InvalidateReprofile, n.leafGraphId());

        graph.addBeforeFixed(n, arrayLength);
        return guard;
    }

    @Override
    public StructuredGraph intrinsicGraph(ResolvedJavaMethod caller, int bci, ResolvedJavaMethod method, List<? extends Node> parameters) {
        JavaType holder = method.holder();
        String fullName = method.name() + method.signature().asString();
        String holderName = holder.name();
        if (holderName.equals("Ljava/lang/Object;")) {
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
                SafeReadNode klassOop = safeReadHub(graph, receiver, StructuredGraph.INVALID_GRAPH_ID);
                Stamp resultStamp = StampFactory.declaredNonNull(getResolvedJavaType(Class.class));
                FloatingReadNode result = graph.unique(new FloatingReadNode(klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Object, config.classMirrorOffset, graph), null, resultStamp));
                ReturnNode ret = graph.add(new ReturnNode(result));
                graph.start().setNext(klassOop);
                klassOop.setNext(ret);
                return graph;
            }
        } else if (holderName.equals("Ljava/lang/Class;")) {
            if (fullName.equals("getModifiers()I")) {
                StructuredGraph graph = new StructuredGraph();
                LocalNode receiver = graph.unique(new LocalNode(0, StampFactory.objectNonNull()));
                SafeReadNode klassOop = safeRead(graph, Kind.Object, receiver, config.klassOopOffset, StampFactory.objectNonNull(), StructuredGraph.INVALID_GRAPH_ID);
                graph.start().setNext(klassOop);
                // TODO(thomaswue): Care about primitive classes! Crashes for primitive classes at the moment (klassOop == null)
                FloatingReadNode result = graph.unique(new FloatingReadNode(klassOop, LocationNode.create(LocationNode.FINAL_LOCATION, Kind.Int, config.klassModifierFlagsOffset, graph), null, StampFactory.intValue()));
                ReturnNode ret = graph.add(new ReturnNode(result));
                klassOop.setNext(ret);
                return graph;
            }
        } else if (holderName.equals("Ljava/lang/Thread;")) {
            if (fullName.equals("currentThread()Ljava/lang/Thread;")) {
                StructuredGraph graph = new StructuredGraph();
                ReturnNode ret = graph.add(new ReturnNode(graph.unique(new CurrentThread(config.threadObjectOffset, this))));
                graph.start().setNext(ret);
                return graph;
            }
        }
        return null;
    }

    private SafeReadNode safeReadHub(Graph graph, ValueNode value, long leafGraphId) {
        return safeRead(graph, Kind.Object, value, config.hubOffset, StampFactory.objectNonNull(), leafGraphId);
    }

    private static SafeReadNode safeRead(Graph graph, Kind kind, ValueNode value, int offset, Stamp stamp, long leafGraphId) {
        return graph.add(new SafeReadNode(value, LocationNode.create(LocationNode.FINAL_LOCATION, kind, offset, graph), stamp, leafGraphId));
    }

    public ResolvedJavaType getResolvedJavaType(Class<?> clazz) {
        return (ResolvedJavaType) compiler.getCompilerToVM().getType(clazz);
    }

    public Object asCallTarget(Object target) {
        return target;
    }

    public long getMaxCallTargetOffset(RuntimeCall rtcall) {
        return compiler.getCompilerToVM().getMaxCallTargetOffset(rtcall);
    }

    public ResolvedJavaMethod getResolvedJavaMethod(Method reflectionMethod) {
        return (ResolvedJavaMethod) compiler.getCompilerToVM().getJavaMethod(reflectionMethod);
    }

    private static HotSpotCodeInfo makeInfo(ResolvedJavaMethod method, CompilationResult code, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = null;
        if (info != null && info.length > 0) {
            hsInfo = new HotSpotCodeInfo(code, (HotSpotResolvedJavaMethod) method);
            info[0] = hsInfo;
        }
        return hsInfo;
    }

    public void installMethod(ResolvedJavaMethod method, CompilationResult code, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = makeInfo(method, code, info);
        compiler.getCompilerToVM().installMethod(new HotSpotTargetMethod((HotSpotResolvedJavaMethod) method, code), true, hsInfo);
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult code, CodeInfo[] info) {
        HotSpotCodeInfo hsInfo = makeInfo(method, code, info);
        return compiler.getCompilerToVM().installMethod(new HotSpotTargetMethod((HotSpotResolvedJavaMethod) method, code), false, hsInfo);
    }

    @Override
    public RegisterConfig getGlobalStubRegisterConfig() {
        return globalStubRegConfig;
    }

    @Override
    public CompilationResult compile(ResolvedJavaMethod method, StructuredGraph graph) {
        OptimisticOptimizations optimisticOpts = OptimisticOptimizations.ALL;
        return compiler.getCompiler().compileMethod(method, graph, -1, compiler.getCache(), compiler.getVMToCompiler().createPhasePlan(optimisticOpts), optimisticOpts);
    }

    @Override
    public int encodeDeoptActionAndReason(DeoptimizationAction action, DeoptimizationReason reason) {
        final int actionShift = 0;
        final int reasonShift = 3;

        int actionValue = convertDeoptAction(action);
        int reasonValue = convertDeoptReason(reason);
        return (~(((reasonValue) << reasonShift) + ((actionValue) << actionShift)));
    }

    @Override
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

    @Override
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
}
