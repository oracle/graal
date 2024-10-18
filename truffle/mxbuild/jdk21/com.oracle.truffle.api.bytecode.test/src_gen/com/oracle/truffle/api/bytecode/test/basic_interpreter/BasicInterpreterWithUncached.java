// CheckStyle: start generated
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeConfigEncoder;
import com.oracle.truffle.api.bytecode.BytecodeDSLAccess;
import com.oracle.truffle.api.bytecode.BytecodeEncodingException;
import com.oracle.truffle.api.bytecode.BytecodeLabel;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.ExceptionHandler;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.LocalVariable;
import com.oracle.truffle.api.bytecode.SourceInformation;
import com.oracle.truffle.api.bytecode.SourceInformationTree;
import com.oracle.truffle.api.bytecode.TagTree;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.bytecode.BytecodeSupport.CloneReferenceList;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.serialization.BytecodeDeserializer.DeserializerContext;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer.SerializerContext;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.DSLSupport.SpecializationDataNode;
import com.oracle.truffle.api.dsl.InlineSupport.ReferenceField;
import com.oracle.truffle.api.dsl.InlineSupport.UnsafeAccessedField;
import com.oracle.truffle.api.dsl.Introspection.Provider;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DenyReplace;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnadoptableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Supplier;

/*
 * operations:
 *   - Operation Block
 *     kind: BLOCK
 *   - Operation Root
 *     kind: ROOT
 *   - Operation IfThen
 *     kind: IF_THEN
 *   - Operation IfThenElse
 *     kind: IF_THEN_ELSE
 *   - Operation Conditional
 *     kind: CONDITIONAL
 *   - Operation While
 *     kind: WHILE
 *   - Operation TryCatch
 *     kind: TRY_CATCH
 *   - Operation TryFinally
 *     kind: TRY_FINALLY
 *   - Operation TryCatchOtherwise
 *     kind: TRY_CATCH_OTHERWISE
 *   - Operation FinallyHandler
 *     kind: FINALLY_HANDLER
 *   - Operation Label
 *     kind: LABEL
 *   - Operation Branch
 *     kind: BRANCH
 *   - Operation LoadConstant
 *     kind: LOAD_CONSTANT
 *   - Operation LoadNull
 *     kind: LOAD_NULL
 *   - Operation LoadArgument
 *     kind: LOAD_ARGUMENT
 *   - Operation LoadException
 *     kind: LOAD_EXCEPTION
 *   - Operation LoadLocal
 *     kind: LOAD_LOCAL
 *   - Operation LoadLocalMaterialized
 *     kind: LOAD_LOCAL_MATERIALIZED
 *   - Operation StoreLocal
 *     kind: STORE_LOCAL
 *   - Operation StoreLocalMaterialized
 *     kind: STORE_LOCAL_MATERIALIZED
 *   - Operation Return
 *     kind: RETURN
 *   - Operation Yield
 *     kind: YIELD
 *   - Operation Source
 *     kind: SOURCE
 *   - Operation SourceSection
 *     kind: SOURCE_SECTION
 *   - Operation Tag
 *     kind: TAG
 *   - Operation EarlyReturn
 *     kind: CUSTOM
 *   - Operation AddOperation
 *     kind: CUSTOM
 *   - Operation ToString
 *     kind: CUSTOM
 *   - Operation Call
 *     kind: CUSTOM
 *   - Operation AddConstantOperation
 *     kind: CUSTOM
 *   - Operation AddConstantOperationAtEnd
 *     kind: CUSTOM
 *   - Operation VeryComplexOperation
 *     kind: CUSTOM
 *   - Operation ThrowOperation
 *     kind: CUSTOM
 *   - Operation ReadExceptionOperation
 *     kind: CUSTOM
 *   - Operation AlwaysBoxOperation
 *     kind: CUSTOM
 *   - Operation AppenderOperation
 *     kind: CUSTOM
 *   - Operation TeeLocal
 *     kind: CUSTOM
 *   - Operation TeeLocalRange
 *     kind: CUSTOM
 *   - Operation Invoke
 *     kind: CUSTOM
 *   - Operation MaterializeFrame
 *     kind: CUSTOM
 *   - Operation CreateClosure
 *     kind: CUSTOM
 *   - Operation VoidOperation
 *     kind: CUSTOM
 *   - Operation ToBoolean
 *     kind: CUSTOM
 *   - Operation GetSourcePosition
 *     kind: CUSTOM
 *   - Operation EnsureAndGetSourcePosition
 *     kind: CUSTOM
 *   - Operation GetSourcePositions
 *     kind: CUSTOM
 *   - Operation CopyLocalsToFrame
 *     kind: CUSTOM
 *   - Operation GetBytecodeLocation
 *     kind: CUSTOM
 *   - Operation CollectBytecodeLocations
 *     kind: CUSTOM
 *   - Operation CollectSourceLocations
 *     kind: CUSTOM
 *   - Operation CollectAllSourceLocations
 *     kind: CUSTOM
 *   - Operation Continue
 *     kind: CUSTOM
 *   - Operation CurrentLocation
 *     kind: CUSTOM
 *   - Operation PrintHere
 *     kind: CUSTOM_INSTRUMENTATION
 *   - Operation IncrementValue
 *     kind: CUSTOM_INSTRUMENTATION
 *   - Operation DoubleValue
 *     kind: CUSTOM_INSTRUMENTATION
 *   - Operation EnableIncrementValueInstrumentation
 *     kind: CUSTOM
 *   - Operation Add
 *     kind: CUSTOM
 *   - Operation Mod
 *     kind: CUSTOM
 *   - Operation Less
 *     kind: CUSTOM
 *   - Operation EnableDoubleValueInstrumentation
 *     kind: CUSTOM
 *   - Operation ExplicitBindingsTest
 *     kind: CUSTOM
 *   - Operation ImplicitBindingsTest
 *     kind: CUSTOM
 *   - Operation ScAnd
 *     kind: CUSTOM_SHORT_CIRCUIT
 *   - Operation ScOr
 *     kind: CUSTOM_SHORT_CIRCUIT
 * instructions:
 *   - Instruction pop
 *     kind: POP
 *     encoding: [1 : short]
 *     signature: void (Object)
 *   - Instruction dup
 *     kind: DUP
 *     encoding: [2 : short]
 *     signature: void ()
 *   - Instruction return
 *     kind: RETURN
 *     encoding: [3 : short]
 *     signature: void (Object)
 *   - Instruction branch
 *     kind: BRANCH
 *     encoding: [4 : short, branch_target (bci) : int]
 *     signature: void ()
 *   - Instruction branch.backward
 *     kind: BRANCH_BACKWARD
 *     encoding: [5 : short, branch_target (bci) : int, loop_header_branch_profile (branch_profile) : int]
 *     signature: void ()
 *   - Instruction branch.false
 *     kind: BRANCH_FALSE
 *     encoding: [6 : short, branch_target (bci) : int, branch_profile : int]
 *     signature: void (Object)
 *   - Instruction store.local
 *     kind: STORE_LOCAL
 *     encoding: [7 : short, local_offset : short]
 *     signature: void (Object)
 *   - Instruction throw
 *     kind: THROW
 *     encoding: [8 : short]
 *     signature: void (Object)
 *   - Instruction load.constant
 *     kind: LOAD_CONSTANT
 *     encoding: [9 : short, constant (const) : int]
 *     signature: Object ()
 *   - Instruction load.null
 *     kind: LOAD_NULL
 *     encoding: [10 : short]
 *     signature: Object ()
 *   - Instruction load.argument
 *     kind: LOAD_ARGUMENT
 *     encoding: [11 : short, index (short) : short]
 *     signature: Object ()
 *   - Instruction load.exception
 *     kind: LOAD_EXCEPTION
 *     encoding: [12 : short, exception_sp (sp) : short]
 *     signature: Object ()
 *   - Instruction load.local
 *     kind: LOAD_LOCAL
 *     encoding: [13 : short, local_offset : short]
 *     signature: Object ()
 *   - Instruction load.local.mat
 *     kind: LOAD_LOCAL_MATERIALIZED
 *     encoding: [14 : short, local_offset : short, root_index (local_root) : short]
 *     signature: Object (Object)
 *   - Instruction store.local.mat
 *     kind: STORE_LOCAL_MATERIALIZED
 *     encoding: [15 : short, local_offset : short, root_index (local_root) : short]
 *     signature: void (Object, Object)
 *   - Instruction yield
 *     kind: YIELD
 *     encoding: [16 : short, location (const) : int]
 *     signature: void (Object)
 *   - Instruction tag.enter
 *     kind: TAG_ENTER
 *     encoding: [17 : short, tag : int]
 *     signature: void ()
 *   - Instruction tag.leave
 *     kind: TAG_LEAVE
 *     encoding: [18 : short, tag : int]
 *     signature: Object (Object)
 *   - Instruction tag.leaveVoid
 *     kind: TAG_LEAVE_VOID
 *     encoding: [19 : short, tag : int]
 *     signature: Object ()
 *   - Instruction tag.yield
 *     kind: TAG_YIELD
 *     encoding: [20 : short, tag : int]
 *     signature: Object (Object)
 *   - Instruction tag.resume
 *     kind: TAG_RESUME
 *     encoding: [21 : short, tag : int]
 *     signature: void ()
 *   - Instruction load.variadic_0
 *     kind: LOAD_VARIADIC
 *     encoding: [22 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_1
 *     kind: LOAD_VARIADIC
 *     encoding: [23 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_2
 *     kind: LOAD_VARIADIC
 *     encoding: [24 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_3
 *     kind: LOAD_VARIADIC
 *     encoding: [25 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_4
 *     kind: LOAD_VARIADIC
 *     encoding: [26 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_5
 *     kind: LOAD_VARIADIC
 *     encoding: [27 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_6
 *     kind: LOAD_VARIADIC
 *     encoding: [28 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_7
 *     kind: LOAD_VARIADIC
 *     encoding: [29 : short]
 *     signature: void (Object)
 *   - Instruction load.variadic_8
 *     kind: LOAD_VARIADIC
 *     encoding: [30 : short]
 *     signature: void (Object)
 *   - Instruction merge.variadic
 *     kind: MERGE_VARIADIC
 *     encoding: [31 : short]
 *     signature: Object (Object)
 *   - Instruction constant_null
 *     kind: STORE_NULL
 *     encoding: [32 : short]
 *     signature: Object ()
 *   - Instruction clear.local
 *     kind: CLEAR_LOCAL
 *     encoding: [33 : short, local_offset : short]
 *     signature: void ()
 *   - Instruction c.EarlyReturn
 *     kind: CUSTOM
 *     encoding: [34 : short, node : int]
 *     nodeType: EarlyReturn
 *     signature: void (Object)
 *   - Instruction c.AddOperation
 *     kind: CUSTOM
 *     encoding: [35 : short, node : int]
 *     nodeType: AddOperation
 *     signature: Object (Object, Object)
 *   - Instruction c.ToString
 *     kind: CUSTOM
 *     encoding: [36 : short, node : int]
 *     nodeType: ToString
 *     signature: Object (Object)
 *   - Instruction c.Call
 *     kind: CUSTOM
 *     encoding: [37 : short, interpreter (const) : int, node : int]
 *     nodeType: Call
 *     signature: Object (BasicInterpreter, Object[]...)
 *   - Instruction c.AddConstantOperation
 *     kind: CUSTOM
 *     encoding: [38 : short, constantLhs (const) : int, node : int]
 *     nodeType: AddConstantOperation
 *     signature: Object (long, Object)
 *   - Instruction c.AddConstantOperationAtEnd
 *     kind: CUSTOM
 *     encoding: [39 : short, constantRhs (const) : int, node : int]
 *     nodeType: AddConstantOperationAtEnd
 *     signature: Object (Object, long)
 *   - Instruction c.VeryComplexOperation
 *     kind: CUSTOM
 *     encoding: [40 : short, node : int]
 *     nodeType: VeryComplexOperation
 *     signature: long (long, Object[]...)
 *   - Instruction c.ThrowOperation
 *     kind: CUSTOM
 *     encoding: [41 : short, node : int]
 *     nodeType: ThrowOperation
 *     signature: Object (long)
 *   - Instruction c.ReadExceptionOperation
 *     kind: CUSTOM
 *     encoding: [42 : short, node : int]
 *     nodeType: ReadExceptionOperation
 *     signature: long (TestException)
 *   - Instruction c.AlwaysBoxOperation
 *     kind: CUSTOM
 *     encoding: [43 : short, node : int]
 *     nodeType: AlwaysBoxOperation
 *     signature: Object (Object)
 *   - Instruction c.AppenderOperation
 *     kind: CUSTOM
 *     encoding: [44 : short, node : int]
 *     nodeType: AppenderOperation
 *     signature: void (List<?>, Object)
 *   - Instruction c.TeeLocal
 *     kind: CUSTOM
 *     encoding: [45 : short, setter (const) : int, node : int]
 *     nodeType: TeeLocal
 *     signature: Object (LocalAccessor, Object)
 *   - Instruction c.TeeLocalRange
 *     kind: CUSTOM
 *     encoding: [46 : short, setter (const) : int, node : int]
 *     nodeType: TeeLocalRange
 *     signature: Object (LocalRangeAccessor, Object)
 *   - Instruction c.Invoke
 *     kind: CUSTOM
 *     encoding: [47 : short, node : int]
 *     nodeType: Invoke
 *     signature: Object (Object, Object[]...)
 *   - Instruction c.MaterializeFrame
 *     kind: CUSTOM
 *     encoding: [48 : short, node : int]
 *     nodeType: MaterializeFrame
 *     signature: MaterializedFrame ()
 *   - Instruction c.CreateClosure
 *     kind: CUSTOM
 *     encoding: [49 : short, node : int]
 *     nodeType: CreateClosure
 *     signature: TestClosure (BasicInterpreter)
 *   - Instruction c.VoidOperation
 *     kind: CUSTOM
 *     encoding: [50 : short, node : int]
 *     nodeType: VoidOperation
 *     signature: void ()
 *   - Instruction c.ToBoolean
 *     kind: CUSTOM
 *     encoding: [51 : short, node : int]
 *     nodeType: ToBoolean
 *     signature: boolean (Object)
 *   - Instruction c.GetSourcePosition
 *     kind: CUSTOM
 *     encoding: [52 : short, node : int]
 *     nodeType: GetSourcePosition
 *     signature: SourceSection ()
 *   - Instruction c.EnsureAndGetSourcePosition
 *     kind: CUSTOM
 *     encoding: [53 : short, node : int]
 *     nodeType: EnsureAndGetSourcePosition
 *     signature: SourceSection (boolean)
 *   - Instruction c.GetSourcePositions
 *     kind: CUSTOM
 *     encoding: [54 : short, node : int]
 *     nodeType: GetSourcePositions
 *     signature: SourceSection[] ()
 *   - Instruction c.CopyLocalsToFrame
 *     kind: CUSTOM
 *     encoding: [55 : short, node : int]
 *     nodeType: CopyLocalsToFrame
 *     signature: Frame (Object)
 *   - Instruction c.GetBytecodeLocation
 *     kind: CUSTOM
 *     encoding: [56 : short, node : int]
 *     nodeType: GetBytecodeLocation
 *     signature: BytecodeLocation ()
 *   - Instruction c.CollectBytecodeLocations
 *     kind: CUSTOM
 *     encoding: [57 : short, node : int]
 *     nodeType: CollectBytecodeLocations
 *     signature: List<BytecodeLocation> ()
 *   - Instruction c.CollectSourceLocations
 *     kind: CUSTOM
 *     encoding: [58 : short, node : int]
 *     nodeType: CollectSourceLocations
 *     signature: List<SourceSection> ()
 *   - Instruction c.CollectAllSourceLocations
 *     kind: CUSTOM
 *     encoding: [59 : short, node : int]
 *     nodeType: CollectAllSourceLocations
 *     signature: List<SourceSection[]> ()
 *   - Instruction c.Continue
 *     kind: CUSTOM
 *     encoding: [60 : short, node : int]
 *     nodeType: ContinueNode
 *     signature: Object (ContinuationResult, Object)
 *   - Instruction c.CurrentLocation
 *     kind: CUSTOM
 *     encoding: [61 : short, node : int]
 *     nodeType: CurrentLocation
 *     signature: BytecodeLocation ()
 *   - Instruction c.PrintHere
 *     kind: CUSTOM
 *     encoding: [62 : short, node : int]
 *     nodeType: PrintHere
 *     signature: void ()
 *   - Instruction c.IncrementValue
 *     kind: CUSTOM
 *     encoding: [63 : short, node : int]
 *     nodeType: IncrementValue
 *     signature: long (long)
 *   - Instruction c.DoubleValue
 *     kind: CUSTOM
 *     encoding: [64 : short, node : int]
 *     nodeType: DoubleValue
 *     signature: long (long)
 *   - Instruction c.EnableIncrementValueInstrumentation
 *     kind: CUSTOM
 *     encoding: [65 : short, node : int]
 *     nodeType: EnableIncrementValueInstrumentation
 *     signature: void ()
 *   - Instruction c.Add
 *     kind: CUSTOM
 *     encoding: [66 : short, node : int]
 *     nodeType: Add
 *     signature: long (long, long)
 *   - Instruction c.Mod
 *     kind: CUSTOM
 *     encoding: [67 : short, node : int]
 *     nodeType: Mod
 *     signature: long (long, long)
 *   - Instruction c.Less
 *     kind: CUSTOM
 *     encoding: [68 : short, node : int]
 *     nodeType: Less
 *     signature: boolean (long, long)
 *   - Instruction c.EnableDoubleValueInstrumentation
 *     kind: CUSTOM
 *     encoding: [69 : short, node : int]
 *     nodeType: EnableDoubleValueInstrumentation
 *     signature: void ()
 *   - Instruction c.ExplicitBindingsTest
 *     kind: CUSTOM
 *     encoding: [70 : short, node : int]
 *     nodeType: ExplicitBindingsTest
 *     signature: Bindings ()
 *   - Instruction c.ImplicitBindingsTest
 *     kind: CUSTOM
 *     encoding: [71 : short, node : int]
 *     nodeType: ImplicitBindingsTest
 *     signature: Bindings ()
 *   - Instruction sc.ScAnd
 *     kind: CUSTOM_SHORT_CIRCUIT
 *     encoding: [72 : short, branch_target (bci) : int, branch_profile : int]
 *     signature: Object (boolean, boolean)
 *   - Instruction sc.ScOr
 *     kind: CUSTOM_SHORT_CIRCUIT
 *     encoding: [73 : short, branch_target (bci) : int, branch_profile : int]
 *     signature: Object (boolean, boolean)
 *   - Instruction invalidate0
 *     kind: INVALIDATE
 *     encoding: [74 : short]
 *     signature: void ()
 *   - Instruction invalidate1
 *     kind: INVALIDATE
 *     encoding: [75 : short, invalidated0 (short) : short]
 *     signature: void ()
 *   - Instruction invalidate2
 *     kind: INVALIDATE
 *     encoding: [76 : short, invalidated0 (short) : short, invalidated1 (short) : short]
 *     signature: void ()
 *   - Instruction invalidate3
 *     kind: INVALIDATE
 *     encoding: [77 : short, invalidated0 (short) : short, invalidated1 (short) : short, invalidated2 (short) : short]
 *     signature: void ()
 *   - Instruction invalidate4
 *     kind: INVALIDATE
 *     encoding: [78 : short, invalidated0 (short) : short, invalidated1 (short) : short, invalidated2 (short) : short, invalidated3 (short) : short]
 *     signature: void ()
 */
@SuppressWarnings({"javadoc", "unused", "deprecation", "static-method"})
public final class BasicInterpreterWithUncached extends BasicInterpreter {

    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final BytecodeDSLAccess ACCESS = BytecodeDSLAccess.lookup(BytecodeRootNodesImpl.VISIBLE_TOKEN, true);
    private static final ByteArraySupport BYTES = ACCESS.getByteArraySupport();
    private static final FrameExtensions FRAMES = ACCESS.getFrameExtensions();
    private static final int BCI_INDEX = 0;
    private static final int COROUTINE_FRAME_INDEX = 1;
    private static final int USER_LOCALS_START_INDEX = 2;
    private static final AtomicReferenceFieldUpdater<BasicInterpreterWithUncached, AbstractBytecodeNode> BYTECODE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(BasicInterpreterWithUncached.class, AbstractBytecodeNode.class, "bytecode");
    private static final int EXCEPTION_HANDLER_OFFSET_START_BCI = 0;
    private static final int EXCEPTION_HANDLER_OFFSET_END_BCI = 1;
    private static final int EXCEPTION_HANDLER_OFFSET_KIND = 2;
    private static final int EXCEPTION_HANDLER_OFFSET_HANDLER_BCI = 3;
    private static final int EXCEPTION_HANDLER_OFFSET_HANDLER_SP = 4;
    private static final int EXCEPTION_HANDLER_LENGTH = 5;
    private static final int SOURCE_INFO_OFFSET_START_BCI = 0;
    private static final int SOURCE_INFO_OFFSET_END_BCI = 1;
    private static final int SOURCE_INFO_OFFSET_SOURCE = 2;
    private static final int SOURCE_INFO_OFFSET_START = 3;
    private static final int SOURCE_INFO_OFFSET_LENGTH = 4;
    private static final int SOURCE_INFO_LENGTH = 5;
    private static final int LOCALS_OFFSET_START_BCI = 0;
    private static final int LOCALS_OFFSET_END_BCI = 1;
    private static final int LOCALS_OFFSET_LOCAL_INDEX = 2;
    private static final int LOCALS_OFFSET_FRAME_INDEX = 3;
    private static final int LOCALS_OFFSET_NAME = 4;
    private static final int LOCALS_OFFSET_INFO = 5;
    private static final int LOCALS_LENGTH = 6;
    private static final int HANDLER_CUSTOM = 0;
    private static final int HANDLER_TAG_EXCEPTIONAL = 1;
    private static final ConcurrentHashMap<Integer, Class<? extends Tag>[]> TAG_MASK_TO_TAGS = new ConcurrentHashMap<>();
    private static final ClassValue<Integer> CLASS_TO_TAG_MASK = BasicInterpreterWithUncached.initializeTagMaskToClass();
    private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);

    @Child private volatile AbstractBytecodeNode bytecode;
    private final BytecodeRootNodesImpl nodes;
    /**
     * The number of frame slots required for locals.
     */
    private final int maxLocals;
    private final int buildIndex;
    private CloneReferenceList<BasicInterpreterWithUncached> clones;

    private BasicInterpreterWithUncached(BytecodeDSLTestLanguage language, com.oracle.truffle.api.frame.FrameDescriptor.Builder builder, BytecodeRootNodesImpl nodes, int maxLocals, int buildIndex, byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot) {
        super(language, builder.build());
        this.nodes = nodes;
        this.maxLocals = maxLocals;
        this.buildIndex = buildIndex;
        this.bytecode = insert(new UncachedBytecodeNode(bytecodes, constants, handlers, locals, sourceInfo, sources, numNodes, tagRoot));
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        // Temporary until we can use FrameDescriptor.newBuilder().illegalDefaultValue().
        for (int slot = 0; slot < maxLocals; slot++) {
            FRAMES.clear(frame, slot);
        }
        return continueAt(bytecode, 0, maxLocals, frame, frame, null);
    }

    @SuppressWarnings("all")
    private Object continueAt(AbstractBytecodeNode bc, int bci, int sp, VirtualFrame frame, VirtualFrame localFrame, ContinuationRootNodeImpl continuationRootNode) {
        beforeRootExecute(new InstructionImpl(bc, bci, bc.readValidBytecode(bc.bytecodes, bci)));
        long state = ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
        while (true) {
            state = bc.continueAt(this, frame, localFrame, state);
            if ((int) state == 0xFFFFFFFF) {
                break;
            } else {
                // Bytecode or tier changed
                CompilerDirectives.transferToInterpreterAndInvalidate();
                AbstractBytecodeNode oldBytecode = bc;
                bc = this.bytecode;
                state = oldBytecode.transitionState(bc, state, continuationRootNode);
            }
        }
        return FRAMES.uncheckedGetObject(frame, (short) (state >>> 32));
    }

    private void transitionToCached(Frame frame, int bci) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        AbstractBytecodeNode oldBytecode;
        AbstractBytecodeNode newBytecode;
        do {
            oldBytecode = this.bytecode;
            newBytecode = insert(oldBytecode.toCached());
            if (bci > 0) {
                // initialize local tags
                int localCount = newBytecode.getLocalCount(bci);
                for (int localOffset = 0; localOffset < localCount; localOffset++) {
                    newBytecode.setLocalValue(bci, frame, localOffset, newBytecode.getLocalValue(bci, frame, localOffset));
                }
            }
            VarHandle.storeStoreFence();
            if (oldBytecode == newBytecode) {
                return;
            }
        } while (!BYTECODE_UPDATER.compareAndSet(this, oldBytecode, newBytecode));
    }

    private AbstractBytecodeNode updateBytecode(byte[] bytecodes_, Object[] constants_, int[] handlers_, int[] locals_, int[] sourceInfo_, List<Source> sources_, int numNodes_, TagRootNode tagRoot_, CharSequence reason, ArrayList<ContinuationLocation> continuationLocations) {
        CompilerAsserts.neverPartOfCompilation();
        AbstractBytecodeNode oldBytecode;
        AbstractBytecodeNode newBytecode;
        do {
            oldBytecode = this.bytecode;
            newBytecode = insert(oldBytecode.update(bytecodes_, constants_, handlers_, locals_, sourceInfo_, sources_, numNodes_, tagRoot_));
            if (bytecodes_ == null) {
                // When bytecode doesn't change, nodes are reused and should be re-adopted.
                newBytecode.adoptNodesAfterUpdate();
            }
            VarHandle.storeStoreFence();
        } while (!BYTECODE_UPDATER.compareAndSet(this, oldBytecode, newBytecode));

        if (bytecodes_ != null) {
            oldBytecode.invalidate(newBytecode, reason);
        }
        oldBytecode.updateContinuationRootNodes(newBytecode, reason, continuationLocations, bytecodes_ != null);
        assert Thread.holdsLock(this.nodes);
        var cloneReferences = this.clones;
        if (cloneReferences != null) {
            cloneReferences.forEach((clone) -> {
                AbstractBytecodeNode cloneOldBytecode;
                AbstractBytecodeNode cloneNewBytecode;
                do {
                    cloneOldBytecode = clone.bytecode;
                    cloneNewBytecode = clone.insert(this.bytecode.cloneUninitialized());
                    if (bytecodes_ == null) {
                        // When bytecode doesn't change, nodes are reused and should be re-adopted.
                        cloneNewBytecode.adoptNodesAfterUpdate();
                    }
                    VarHandle.storeStoreFence();
                } while (!BYTECODE_UPDATER.compareAndSet(clone, cloneOldBytecode, cloneNewBytecode));

                if (bytecodes_ != null) {
                    cloneOldBytecode.invalidate(cloneNewBytecode, reason);
                }
                cloneOldBytecode.updateContinuationRootNodes(cloneNewBytecode, reason, continuationLocations, bytecodes_ != null);
            }
            );
        }
        return newBytecode;
    }

    @Override
    protected boolean isInstrumentable() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void prepareForInstrumentation(Set<Class<?>> materializedTags) {
        com.oracle.truffle.api.bytecode.BytecodeConfig.Builder b = newConfigBuilder();
        // Sources are always needed for instrumentation.
        b.addSource();
        for (Class<?> tag : materializedTags) {
            b.addTag((Class<? extends Tag>) tag);
        }
        getRootNodes().update(b.build());
    }

    @Override
    protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
        BytecodeNode bc = BytecodeNode.get(callNode);
        if (bc == null || !(bc instanceof AbstractBytecodeNode bytecodeNode)) {
            return super.findInstrumentableCallNode(callNode, frame, bytecodeIndex);
        }
        return bytecodeNode.findInstrumentableCallNode(bytecodeIndex);
    }

    @Override
    public boolean isCloningAllowed() {
        return true;
    }

    @Override
    protected boolean isCloneUninitializedSupported() {
        return true;
    }

    @Override
    protected RootNode cloneUninitialized() {
        BasicInterpreterWithUncached clone;
        synchronized(nodes){
            clone = (BasicInterpreterWithUncached) this.copy();
            clone.clones = null;
            clone.bytecode = insert(this.bytecode.cloneUninitialized());
            CloneReferenceList<BasicInterpreterWithUncached> localClones = this.clones;
            if (localClones == null) {
                this.clones = localClones = new CloneReferenceList<BasicInterpreterWithUncached>();
            }
            localClones.add(clone);
        }
        VarHandle.storeStoreFence();
        return clone;
    }

    @Override
    @SuppressWarnings("hiding")
    protected int findBytecodeIndex(Node node, Frame frame) {
        AbstractBytecodeNode bytecode = null;
        Node prev = node;
        Node current = node;
        while (current != null) {
            if (current  instanceof AbstractBytecodeNode b) {
                bytecode = b;
                break;
            }
            prev = current;
            current = prev.getParent();
        }
        if (bytecode == null) {
            return -1;
        }
        return bytecode.findBytecodeIndex(frame, prev);
    }

    @Override
    protected boolean isCaptureFramesForTrace(boolean compiled) {
        return !compiled;
    }

    @Override
    public BytecodeNode getBytecodeNode() {
        return bytecode;
    }

    private AbstractBytecodeNode getBytecodeNodeImpl() {
        return bytecode;
    }

    private BasicInterpreterWithUncached getBytecodeRootNodeImpl(int index) {
        return (BasicInterpreterWithUncached) this.nodes.getNode(index);
    }

    @Override
    public BytecodeRootNodes<BasicInterpreter> getRootNodes() {
        return this.nodes;
    }

    @Override
    protected boolean countsTowardsStackTraceLimit() {
        return true;
    }

    @Override
    public SourceSection getSourceSection() {
        return bytecode.getSourceSection();
    }

    @Override
    protected Object translateStackTraceElement(TruffleStackTraceElement stackTraceElement) {
        return AbstractBytecodeNode.createStackTraceElement(stackTraceElement);
    }

    public static com.oracle.truffle.api.bytecode.BytecodeConfig.Builder newConfigBuilder() {
        return BytecodeConfig.newBuilder(BytecodeConfigEncoderImpl.INSTANCE);
    }

    private static int encodeTags(Class<?>... tags) {
        if (tags == null) {
            return 0;
        }
        int tagMask = 0;
        for (Class<?> tag : tags) {
            tagMask |= CLASS_TO_TAG_MASK.get(tag);
        }
        return tagMask;
    }

    /**
     * Creates one or more bytecode nodes. This is the entrypoint for creating new {@link BasicInterpreterWithUncached} instances.
     *
     * @param language the Truffle language instance.
     * @param config indicates whether to parse metadata (e.g., source information).
     * @param parser the parser that invokes a series of builder instructions to generate bytecode.
     */
    public static BytecodeRootNodes<BasicInterpreter> create(BytecodeDSLTestLanguage language, BytecodeConfig config, BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder> parser) {
        BytecodeRootNodesImpl nodes = new BytecodeRootNodesImpl(parser, config);
        Builder builder = new Builder(language, nodes, config);
        parser.parse(builder);
        builder.finish();
        return nodes;
    }

    /**
     * Serializes the bytecode nodes parsed by the {@code parser}.
     * All metadata (e.g., source info) is serialized (even if it has not yet been parsed).
     * <p>
     * Unlike {@link BytecodeRootNodes#serialize}, this method does not use already-constructed root nodes,
     * so it cannot serialize field values that get set outside of the parser.
     *
     * @param buffer the buffer to write the byte output to.
     * @param callback the language-specific serializer for constants in the bytecode.
     * @param parser the parser.
     */
    @TruffleBoundary
    public static void serialize(DataOutput buffer, BytecodeSerializer callback, BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder> parser) throws IOException {
        Builder builder = new Builder(null, new BytecodeRootNodesImpl(parser, BytecodeConfig.COMPLETE), BytecodeConfig.COMPLETE);
        doSerialize(buffer, callback, builder, null);
    }

    @TruffleBoundary
    private static void doSerialize(DataOutput buffer, BytecodeSerializer callback, com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder builder, List<BasicInterpreter> existingNodes) throws IOException {
        try {
            builder.serialize(buffer, callback, existingNodes);
        } catch (IOError e) {
            throw (IOException) e.getCause();
        }
    }

    /**
     * Deserializes a byte sequence to bytecode nodes. The bytes must have been produced by a previous call to {@link #serialize}.").newLine()
     *
     * @param language the language instance.
     * @param config indicates whether to deserialize metadata (e.g., source information).
     * @param input A function that supplies the bytes to deserialize. This supplier must produce a new {@link DataInput} each time, since the bytes may be processed multiple times for reparsing.
     * @param callback The language-specific deserializer for constants in the bytecode. This callback must perform the inverse of the callback that was used to {@link #serialize} the nodes to bytes.
     */
    @TruffleBoundary
    public static BytecodeRootNodes<BasicInterpreter> deserialize(BytecodeDSLTestLanguage language, BytecodeConfig config, Supplier<DataInput> input, BytecodeDeserializer callback) throws IOException {
        try {
            return create(language, config, (b) -> b.deserialize(input, callback, null));
        } catch (IOError e) {
            throw (IOException) e.getCause();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends Tag>[] mapTagMaskToTagsArray(int tagMask) {
        ArrayList<Class<? extends Tag>> tags = new ArrayList<>();
        if ((tagMask & 1) != 0) {
            tags.add(RootTag.class);
        }
        if ((tagMask & 2) != 0) {
            tags.add(RootBodyTag.class);
        }
        if ((tagMask & 4) != 0) {
            tags.add(ExpressionTag.class);
        }
        if ((tagMask & 8) != 0) {
            tags.add(StatementTag.class);
        }
        return tags.toArray(new Class[tags.size()]);
    }

    private static ClassValue<Integer> initializeTagMaskToClass() {
        return new ClassValue<>(){
            protected Integer computeValue(Class<?> type) {
                if (type == RootTag.class) {
                    return 1;
                } else if (type == RootBodyTag.class) {
                    return 2;
                } else if (type == ExpressionTag.class) {
                    return 4;
                } else if (type == StatementTag.class) {
                    return 8;
                }
                throw new IllegalArgumentException(String.format("Invalid tag specified. Tag '%s' not provided by language 'com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage'.", type.getName()));
            }
        }
        ;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @TruffleBoundary
    private static AssertionError assertionFailed(String message) {
        throw new AssertionError(message);
    }

    @ExplodeLoop
    private static Object[] readVariadic(VirtualFrame frame, int sp, int variadicCount) {
        Object[] result = new Object[variadicCount];
        for (int i = 0; i < variadicCount; i++) {
            int index = sp - variadicCount + i;
            result[i] = FRAMES.uncheckedGetObject(frame, index);
            FRAMES.clear(frame, index);
        }
        return result;
    }

    private static Object[] mergeVariadic(Object[] array) {
        Object[] current = array;
        int length = 0;
        do {
            int currentLength = current.length - 1;
            length += currentLength;
            current = (Object[]) current[currentLength];
        } while (current != null);
        Object[] newArray = new Object[length];
        current = array;
        int index = 0;
        do {
            int currentLength = current.length - 1;
            System.arraycopy(current, 0, newArray, index, currentLength);
            index += currentLength;
            current = (Object[]) current[currentLength];
        } while (current != null);
        return newArray;
    }

    private static final class InstructionImpl extends Instruction {

        final AbstractBytecodeNode bytecode;
        final int bci;
        final int opcode;

        InstructionImpl(AbstractBytecodeNode bytecode, int bci, int opcode) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecode = bytecode;
            this.bci = bci;
            this.opcode = opcode;
        }

        @Override
        public int getBytecodeIndex() {
            return bci;
        }

        @Override
        public BytecodeNode getBytecodeNode() {
            return bytecode;
        }

        @Override
        public int getOperationCode() {
            return opcode;
        }

        @Override
        public int getLength() {
            switch (opcode) {
                case Instructions.POP :
                case Instructions.DUP :
                case Instructions.RETURN :
                case Instructions.THROW :
                case Instructions.LOAD_NULL :
                case Instructions.LOAD_VARIADIC_0 :
                case Instructions.LOAD_VARIADIC_1 :
                case Instructions.LOAD_VARIADIC_2 :
                case Instructions.LOAD_VARIADIC_3 :
                case Instructions.LOAD_VARIADIC_4 :
                case Instructions.LOAD_VARIADIC_5 :
                case Instructions.LOAD_VARIADIC_6 :
                case Instructions.LOAD_VARIADIC_7 :
                case Instructions.LOAD_VARIADIC_8 :
                case Instructions.MERGE_VARIADIC :
                case Instructions.CONSTANT_NULL :
                case Instructions.INVALIDATE0 :
                    return 2;
                case Instructions.STORE_LOCAL :
                case Instructions.LOAD_ARGUMENT :
                case Instructions.LOAD_EXCEPTION :
                case Instructions.LOAD_LOCAL :
                case Instructions.CLEAR_LOCAL :
                case Instructions.INVALIDATE1 :
                    return 4;
                case Instructions.BRANCH :
                case Instructions.LOAD_CONSTANT :
                case Instructions.LOAD_LOCAL_MAT :
                case Instructions.STORE_LOCAL_MAT :
                case Instructions.YIELD :
                case Instructions.TAG_ENTER :
                case Instructions.TAG_LEAVE :
                case Instructions.TAG_LEAVE_VOID :
                case Instructions.TAG_YIELD :
                case Instructions.TAG_RESUME :
                case Instructions.EARLY_RETURN_ :
                case Instructions.ADD_OPERATION_ :
                case Instructions.TO_STRING_ :
                case Instructions.VERY_COMPLEX_OPERATION_ :
                case Instructions.THROW_OPERATION_ :
                case Instructions.READ_EXCEPTION_OPERATION_ :
                case Instructions.ALWAYS_BOX_OPERATION_ :
                case Instructions.APPENDER_OPERATION_ :
                case Instructions.INVOKE_ :
                case Instructions.MATERIALIZE_FRAME_ :
                case Instructions.CREATE_CLOSURE_ :
                case Instructions.VOID_OPERATION_ :
                case Instructions.TO_BOOLEAN_ :
                case Instructions.GET_SOURCE_POSITION_ :
                case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                case Instructions.GET_SOURCE_POSITIONS_ :
                case Instructions.COPY_LOCALS_TO_FRAME_ :
                case Instructions.GET_BYTECODE_LOCATION_ :
                case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                case Instructions.CONTINUE_ :
                case Instructions.CURRENT_LOCATION_ :
                case Instructions.PRINT_HERE_ :
                case Instructions.INCREMENT_VALUE_ :
                case Instructions.DOUBLE_VALUE_ :
                case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                case Instructions.ADD_ :
                case Instructions.MOD_ :
                case Instructions.LESS_ :
                case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                case Instructions.EXPLICIT_BINDINGS_TEST_ :
                case Instructions.IMPLICIT_BINDINGS_TEST_ :
                case Instructions.INVALIDATE2 :
                    return 6;
                case Instructions.INVALIDATE3 :
                    return 8;
                case Instructions.BRANCH_BACKWARD :
                case Instructions.BRANCH_FALSE :
                case Instructions.CALL_ :
                case Instructions.ADD_CONSTANT_OPERATION_ :
                case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                case Instructions.TEE_LOCAL_ :
                case Instructions.TEE_LOCAL_RANGE_ :
                case Instructions.SC_AND_ :
                case Instructions.SC_OR_ :
                case Instructions.INVALIDATE4 :
                    return 10;
            }
            throw CompilerDirectives.shouldNotReachHere("Invalid opcode");
        }

        @Override
        public List<Argument> getArguments() {
            switch (opcode) {
                case Instructions.POP :
                case Instructions.DUP :
                case Instructions.RETURN :
                case Instructions.THROW :
                case Instructions.LOAD_NULL :
                case Instructions.LOAD_VARIADIC_0 :
                case Instructions.LOAD_VARIADIC_1 :
                case Instructions.LOAD_VARIADIC_2 :
                case Instructions.LOAD_VARIADIC_3 :
                case Instructions.LOAD_VARIADIC_4 :
                case Instructions.LOAD_VARIADIC_5 :
                case Instructions.LOAD_VARIADIC_6 :
                case Instructions.LOAD_VARIADIC_7 :
                case Instructions.LOAD_VARIADIC_8 :
                case Instructions.MERGE_VARIADIC :
                case Instructions.CONSTANT_NULL :
                case Instructions.INVALIDATE0 :
                    return List.of();
                case Instructions.BRANCH :
                    return List.of(
                        new BytecodeIndexArgument(bytecode, "branch_target", bci + 2));
                case Instructions.BRANCH_BACKWARD :
                    return List.of(
                        new BytecodeIndexArgument(bytecode, "branch_target", bci + 2),
                        new BranchProfileArgument(bytecode, "loop_header_branch_profile", bci + 6));
                case Instructions.BRANCH_FALSE :
                case Instructions.SC_AND_ :
                case Instructions.SC_OR_ :
                    return List.of(
                        new BytecodeIndexArgument(bytecode, "branch_target", bci + 2),
                        new BranchProfileArgument(bytecode, "branch_profile", bci + 6));
                case Instructions.STORE_LOCAL :
                case Instructions.LOAD_LOCAL :
                case Instructions.CLEAR_LOCAL :
                    return List.of(
                        new LocalOffsetArgument(bytecode, "local_offset", bci + 2));
                case Instructions.LOAD_CONSTANT :
                    return List.of(
                        new ConstantArgument(bytecode, "constant", bci + 2));
                case Instructions.LOAD_ARGUMENT :
                    return List.of(
                        new IntegerArgument(bytecode, "index", bci + 2, 2));
                case Instructions.LOAD_EXCEPTION :
                    return List.of(
                        new IntegerArgument(bytecode, "exception_sp", bci + 2, 2));
                case Instructions.LOAD_LOCAL_MAT :
                case Instructions.STORE_LOCAL_MAT :
                    return List.of(
                        new LocalOffsetArgument(bytecode, "local_offset", bci + 2),
                        new IntegerArgument(bytecode, "root_index", bci + 4, 2));
                case Instructions.YIELD :
                    return List.of(
                        new ConstantArgument(bytecode, "location", bci + 2));
                case Instructions.TAG_ENTER :
                case Instructions.TAG_LEAVE :
                case Instructions.TAG_LEAVE_VOID :
                case Instructions.TAG_YIELD :
                case Instructions.TAG_RESUME :
                    return List.of(
                        new TagNodeArgument(bytecode, "tag", bci + 2));
                case Instructions.EARLY_RETURN_ :
                case Instructions.ADD_OPERATION_ :
                case Instructions.TO_STRING_ :
                case Instructions.VERY_COMPLEX_OPERATION_ :
                case Instructions.THROW_OPERATION_ :
                case Instructions.READ_EXCEPTION_OPERATION_ :
                case Instructions.ALWAYS_BOX_OPERATION_ :
                case Instructions.APPENDER_OPERATION_ :
                case Instructions.INVOKE_ :
                case Instructions.MATERIALIZE_FRAME_ :
                case Instructions.CREATE_CLOSURE_ :
                case Instructions.VOID_OPERATION_ :
                case Instructions.TO_BOOLEAN_ :
                case Instructions.GET_SOURCE_POSITION_ :
                case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                case Instructions.GET_SOURCE_POSITIONS_ :
                case Instructions.COPY_LOCALS_TO_FRAME_ :
                case Instructions.GET_BYTECODE_LOCATION_ :
                case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                case Instructions.CONTINUE_ :
                case Instructions.CURRENT_LOCATION_ :
                case Instructions.PRINT_HERE_ :
                case Instructions.INCREMENT_VALUE_ :
                case Instructions.DOUBLE_VALUE_ :
                case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                case Instructions.ADD_ :
                case Instructions.MOD_ :
                case Instructions.LESS_ :
                case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                case Instructions.EXPLICIT_BINDINGS_TEST_ :
                case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    return List.of(
                        new NodeProfileArgument(bytecode, "node", bci + 2));
                case Instructions.CALL_ :
                    return List.of(
                        new ConstantArgument(bytecode, "interpreter", bci + 2),
                        new NodeProfileArgument(bytecode, "node", bci + 6));
                case Instructions.ADD_CONSTANT_OPERATION_ :
                    return List.of(
                        new ConstantArgument(bytecode, "constantLhs", bci + 2),
                        new NodeProfileArgument(bytecode, "node", bci + 6));
                case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    return List.of(
                        new ConstantArgument(bytecode, "constantRhs", bci + 2),
                        new NodeProfileArgument(bytecode, "node", bci + 6));
                case Instructions.TEE_LOCAL_ :
                case Instructions.TEE_LOCAL_RANGE_ :
                    return List.of(
                        new ConstantArgument(bytecode, "setter", bci + 2),
                        new NodeProfileArgument(bytecode, "node", bci + 6));
                case Instructions.INVALIDATE1 :
                    return List.of(
                        new IntegerArgument(bytecode, "invalidated0", bci + 2, 2));
                case Instructions.INVALIDATE2 :
                    return List.of(
                        new IntegerArgument(bytecode, "invalidated0", bci + 2, 2),
                        new IntegerArgument(bytecode, "invalidated1", bci + 4, 2));
                case Instructions.INVALIDATE3 :
                    return List.of(
                        new IntegerArgument(bytecode, "invalidated0", bci + 2, 2),
                        new IntegerArgument(bytecode, "invalidated1", bci + 4, 2),
                        new IntegerArgument(bytecode, "invalidated2", bci + 6, 2));
                case Instructions.INVALIDATE4 :
                    return List.of(
                        new IntegerArgument(bytecode, "invalidated0", bci + 2, 2),
                        new IntegerArgument(bytecode, "invalidated1", bci + 4, 2),
                        new IntegerArgument(bytecode, "invalidated2", bci + 6, 2),
                        new IntegerArgument(bytecode, "invalidated3", bci + 8, 2));
            }
            throw CompilerDirectives.shouldNotReachHere("Invalid opcode");
        }

        @Override
        public String getName() {
            switch (opcode) {
                case Instructions.POP :
                    return "pop";
                case Instructions.DUP :
                    return "dup";
                case Instructions.RETURN :
                    return "return";
                case Instructions.BRANCH :
                    return "branch";
                case Instructions.BRANCH_BACKWARD :
                    return "branch.backward";
                case Instructions.BRANCH_FALSE :
                    return "branch.false";
                case Instructions.STORE_LOCAL :
                    return "store.local";
                case Instructions.THROW :
                    return "throw";
                case Instructions.LOAD_CONSTANT :
                    return "load.constant";
                case Instructions.LOAD_NULL :
                    return "load.null";
                case Instructions.LOAD_ARGUMENT :
                    return "load.argument";
                case Instructions.LOAD_EXCEPTION :
                    return "load.exception";
                case Instructions.LOAD_LOCAL :
                    return "load.local";
                case Instructions.LOAD_LOCAL_MAT :
                    return "load.local.mat";
                case Instructions.STORE_LOCAL_MAT :
                    return "store.local.mat";
                case Instructions.YIELD :
                    return "yield";
                case Instructions.TAG_ENTER :
                    return "tag.enter";
                case Instructions.TAG_LEAVE :
                    return "tag.leave";
                case Instructions.TAG_LEAVE_VOID :
                    return "tag.leaveVoid";
                case Instructions.TAG_YIELD :
                    return "tag.yield";
                case Instructions.TAG_RESUME :
                    return "tag.resume";
                case Instructions.LOAD_VARIADIC_0 :
                    return "load.variadic_0";
                case Instructions.LOAD_VARIADIC_1 :
                    return "load.variadic_1";
                case Instructions.LOAD_VARIADIC_2 :
                    return "load.variadic_2";
                case Instructions.LOAD_VARIADIC_3 :
                    return "load.variadic_3";
                case Instructions.LOAD_VARIADIC_4 :
                    return "load.variadic_4";
                case Instructions.LOAD_VARIADIC_5 :
                    return "load.variadic_5";
                case Instructions.LOAD_VARIADIC_6 :
                    return "load.variadic_6";
                case Instructions.LOAD_VARIADIC_7 :
                    return "load.variadic_7";
                case Instructions.LOAD_VARIADIC_8 :
                    return "load.variadic_8";
                case Instructions.MERGE_VARIADIC :
                    return "merge.variadic";
                case Instructions.CONSTANT_NULL :
                    return "constant_null";
                case Instructions.CLEAR_LOCAL :
                    return "clear.local";
                case Instructions.EARLY_RETURN_ :
                    return "c.EarlyReturn";
                case Instructions.ADD_OPERATION_ :
                    return "c.AddOperation";
                case Instructions.TO_STRING_ :
                    return "c.ToString";
                case Instructions.CALL_ :
                    return "c.Call";
                case Instructions.ADD_CONSTANT_OPERATION_ :
                    return "c.AddConstantOperation";
                case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    return "c.AddConstantOperationAtEnd";
                case Instructions.VERY_COMPLEX_OPERATION_ :
                    return "c.VeryComplexOperation";
                case Instructions.THROW_OPERATION_ :
                    return "c.ThrowOperation";
                case Instructions.READ_EXCEPTION_OPERATION_ :
                    return "c.ReadExceptionOperation";
                case Instructions.ALWAYS_BOX_OPERATION_ :
                    return "c.AlwaysBoxOperation";
                case Instructions.APPENDER_OPERATION_ :
                    return "c.AppenderOperation";
                case Instructions.TEE_LOCAL_ :
                    return "c.TeeLocal";
                case Instructions.TEE_LOCAL_RANGE_ :
                    return "c.TeeLocalRange";
                case Instructions.INVOKE_ :
                    return "c.Invoke";
                case Instructions.MATERIALIZE_FRAME_ :
                    return "c.MaterializeFrame";
                case Instructions.CREATE_CLOSURE_ :
                    return "c.CreateClosure";
                case Instructions.VOID_OPERATION_ :
                    return "c.VoidOperation";
                case Instructions.TO_BOOLEAN_ :
                    return "c.ToBoolean";
                case Instructions.GET_SOURCE_POSITION_ :
                    return "c.GetSourcePosition";
                case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    return "c.EnsureAndGetSourcePosition";
                case Instructions.GET_SOURCE_POSITIONS_ :
                    return "c.GetSourcePositions";
                case Instructions.COPY_LOCALS_TO_FRAME_ :
                    return "c.CopyLocalsToFrame";
                case Instructions.GET_BYTECODE_LOCATION_ :
                    return "c.GetBytecodeLocation";
                case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    return "c.CollectBytecodeLocations";
                case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    return "c.CollectSourceLocations";
                case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    return "c.CollectAllSourceLocations";
                case Instructions.CONTINUE_ :
                    return "c.Continue";
                case Instructions.CURRENT_LOCATION_ :
                    return "c.CurrentLocation";
                case Instructions.PRINT_HERE_ :
                    return "c.PrintHere";
                case Instructions.INCREMENT_VALUE_ :
                    return "c.IncrementValue";
                case Instructions.DOUBLE_VALUE_ :
                    return "c.DoubleValue";
                case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    return "c.EnableIncrementValueInstrumentation";
                case Instructions.ADD_ :
                    return "c.Add";
                case Instructions.MOD_ :
                    return "c.Mod";
                case Instructions.LESS_ :
                    return "c.Less";
                case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    return "c.EnableDoubleValueInstrumentation";
                case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    return "c.ExplicitBindingsTest";
                case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    return "c.ImplicitBindingsTest";
                case Instructions.SC_AND_ :
                    return "sc.ScAnd";
                case Instructions.SC_OR_ :
                    return "sc.ScOr";
                case Instructions.INVALIDATE0 :
                    return "invalidate0";
                case Instructions.INVALIDATE1 :
                    return "invalidate1";
                case Instructions.INVALIDATE2 :
                    return "invalidate2";
                case Instructions.INVALIDATE3 :
                    return "invalidate3";
                case Instructions.INVALIDATE4 :
                    return "invalidate4";
            }
            throw CompilerDirectives.shouldNotReachHere("Invalid opcode");
        }

        @Override
        public boolean isInstrumentation() {
            switch (opcode) {
                case Instructions.POP :
                case Instructions.DUP :
                case Instructions.RETURN :
                case Instructions.BRANCH :
                case Instructions.BRANCH_BACKWARD :
                case Instructions.BRANCH_FALSE :
                case Instructions.STORE_LOCAL :
                case Instructions.THROW :
                case Instructions.LOAD_CONSTANT :
                case Instructions.LOAD_NULL :
                case Instructions.LOAD_ARGUMENT :
                case Instructions.LOAD_EXCEPTION :
                case Instructions.LOAD_LOCAL :
                case Instructions.LOAD_LOCAL_MAT :
                case Instructions.STORE_LOCAL_MAT :
                case Instructions.YIELD :
                case Instructions.LOAD_VARIADIC_0 :
                case Instructions.LOAD_VARIADIC_1 :
                case Instructions.LOAD_VARIADIC_2 :
                case Instructions.LOAD_VARIADIC_3 :
                case Instructions.LOAD_VARIADIC_4 :
                case Instructions.LOAD_VARIADIC_5 :
                case Instructions.LOAD_VARIADIC_6 :
                case Instructions.LOAD_VARIADIC_7 :
                case Instructions.LOAD_VARIADIC_8 :
                case Instructions.MERGE_VARIADIC :
                case Instructions.CONSTANT_NULL :
                case Instructions.CLEAR_LOCAL :
                case Instructions.EARLY_RETURN_ :
                case Instructions.ADD_OPERATION_ :
                case Instructions.TO_STRING_ :
                case Instructions.CALL_ :
                case Instructions.ADD_CONSTANT_OPERATION_ :
                case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                case Instructions.VERY_COMPLEX_OPERATION_ :
                case Instructions.THROW_OPERATION_ :
                case Instructions.READ_EXCEPTION_OPERATION_ :
                case Instructions.ALWAYS_BOX_OPERATION_ :
                case Instructions.APPENDER_OPERATION_ :
                case Instructions.TEE_LOCAL_ :
                case Instructions.TEE_LOCAL_RANGE_ :
                case Instructions.INVOKE_ :
                case Instructions.MATERIALIZE_FRAME_ :
                case Instructions.CREATE_CLOSURE_ :
                case Instructions.VOID_OPERATION_ :
                case Instructions.TO_BOOLEAN_ :
                case Instructions.GET_SOURCE_POSITION_ :
                case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                case Instructions.GET_SOURCE_POSITIONS_ :
                case Instructions.COPY_LOCALS_TO_FRAME_ :
                case Instructions.GET_BYTECODE_LOCATION_ :
                case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                case Instructions.CONTINUE_ :
                case Instructions.CURRENT_LOCATION_ :
                case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                case Instructions.ADD_ :
                case Instructions.MOD_ :
                case Instructions.LESS_ :
                case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                case Instructions.EXPLICIT_BINDINGS_TEST_ :
                case Instructions.IMPLICIT_BINDINGS_TEST_ :
                case Instructions.SC_AND_ :
                case Instructions.SC_OR_ :
                case Instructions.INVALIDATE0 :
                case Instructions.INVALIDATE1 :
                case Instructions.INVALIDATE2 :
                case Instructions.INVALIDATE3 :
                case Instructions.INVALIDATE4 :
                    return false;
                case Instructions.TAG_ENTER :
                case Instructions.TAG_LEAVE :
                case Instructions.TAG_LEAVE_VOID :
                case Instructions.TAG_YIELD :
                case Instructions.TAG_RESUME :
                case Instructions.PRINT_HERE_ :
                case Instructions.INCREMENT_VALUE_ :
                case Instructions.DOUBLE_VALUE_ :
                    return true;
            }
            throw CompilerDirectives.shouldNotReachHere("Invalid opcode");
        }

        @Override
        protected Instruction next() {
            int nextBci = getNextBytecodeIndex();
            if (nextBci >= bytecode.bytecodes.length) {
                return null;
            }
            return new InstructionImpl(bytecode, nextBci, bytecode.readValidBytecode(bytecode.bytecodes, nextBci));
        }

        private abstract static sealed class AbstractArgument extends Argument permits LocalOffsetArgument, LocalIndexArgument, IntegerArgument, BytecodeIndexArgument, ConstantArgument, NodeProfileArgument, TagNodeArgument, BranchProfileArgument {

            protected static final BytecodeDSLAccess SAFE_ACCESS = BytecodeDSLAccess.lookup(BytecodeRootNodesImpl.VISIBLE_TOKEN, false);
            protected static final ByteArraySupport SAFE_BYTES = SAFE_ACCESS.getByteArraySupport();

            final AbstractBytecodeNode bytecode;
            final String name;
            final int bci;

            AbstractArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
                this.bytecode = bytecode;
                this.name = name;
                this.bci = bci;
            }

            @Override
            public final String getName() {
                return name;
            }

        }
        private static final class LocalOffsetArgument extends AbstractArgument {

            LocalOffsetArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.LOCAL_OFFSET;
            }

            @Override
            public int asLocalOffset() {
                byte[] bc = this.bytecode.bytecodes;
                return SAFE_BYTES.getShort(bc, bci) - USER_LOCALS_START_INDEX;
            }

        }
        private static final class LocalIndexArgument extends AbstractArgument {

            LocalIndexArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.LOCAL_INDEX;
            }

            @Override
            public int asLocalIndex() {
                byte[] bc = this.bytecode.bytecodes;
                return SAFE_BYTES.getShort(bc, bci);
            }

        }
        private static final class IntegerArgument extends AbstractArgument {

            private final int width;

            IntegerArgument(AbstractBytecodeNode bytecode, String name, int bci, int width) {
                super(bytecode, name, bci);
                this.width = width;
            }

            @Override
            public Kind getKind() {
                return Kind.INTEGER;
            }

            @Override
            public int asInteger() throws UnsupportedOperationException {
                byte[] bc = this.bytecode.bytecodes;
                switch (width) {
                    case 1 :
                        return SAFE_BYTES.getByte(bc, bci);
                    case 2 :
                        return SAFE_BYTES.getShort(bc, bci);
                    case 4 :
                        return SAFE_BYTES.getInt(bc, bci);
                    default :
                        throw assertionFailed("Unexpected integer width " + width);
                }
            }

        }
        private static final class BytecodeIndexArgument extends AbstractArgument {

            BytecodeIndexArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.BYTECODE_INDEX;
            }

            @Override
            public int asBytecodeIndex() {
                byte[] bc = this.bytecode.bytecodes;
                return SAFE_BYTES.getInt(bc, bci);
            }

        }
        private static final class ConstantArgument extends AbstractArgument {

            ConstantArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.CONSTANT;
            }

            @Override
            public Object asConstant() {
                byte[] bc = this.bytecode.bytecodes;
                Object[] constants = this.bytecode.constants;
                return SAFE_ACCESS.readObject(constants, SAFE_BYTES.getInt(bc, bci));
            }

        }
        private static final class NodeProfileArgument extends AbstractArgument {

            NodeProfileArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.NODE_PROFILE;
            }

            @Override
            public Node asCachedNode() {
                Node[] cachedNodes = this.bytecode.getCachedNodes();
                if (cachedNodes == null) {
                    return null;
                }
                byte[] bc = this.bytecode.bytecodes;
                return cachedNodes[SAFE_BYTES.getInt(bc, bci)];
            }

        }
        private static final class TagNodeArgument extends AbstractArgument {

            TagNodeArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.TAG_NODE;
            }

            @Override
            public TagTreeNode asTagNode() {
                byte[] bc = this.bytecode.bytecodes;
                TagRootNode tagRoot = this.bytecode.tagRoot;
                if (tagRoot == null) {
                    return null;
                }
                return tagRoot.tagNodes[SAFE_BYTES.getInt(bc, bci)];
            }

        }
        private static final class BranchProfileArgument extends AbstractArgument {

            BranchProfileArgument(AbstractBytecodeNode bytecode, String name, int bci) {
                super(bytecode, name, bci);
            }

            @Override
            public Kind getKind() {
                return Kind.BRANCH_PROFILE;
            }

            @Override
            public BranchProfile asBranchProfile() {
                byte[] bc = this.bytecode.bytecodes;
                int index = SAFE_BYTES.getInt(bc, bci);
                int[] profiles = this.bytecode.getBranchProfiles();
                if (profiles == null) {
                    return new BranchProfile(index, 0, 0);
                }
                return new BranchProfile(index, profiles[index * 2], profiles[index * 2 + 1]);
            }

        }
    }
    private static final class TagNode extends TagTreeNode implements InstrumentableNode, TagTree {

        static final TagNode[] EMPTY_ARRAY = new TagNode[0];

        final int tags;
        final int enterBci;
        @CompilationFinal int returnBci;
        @Children TagNode[] children;
        @Child private volatile ProbeNode probe;
        @CompilationFinal private volatile SourceSection sourceSection;

        TagNode(int tags, int enterBci) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.tags = tags;
            this.enterBci = enterBci;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode p) {
            return null;
        }

        @Override
        public ProbeNode findProbe() {
            ProbeNode p = this.probe;
            if (p == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.probe = p = insert(createProbe(getSourceSection()));
            }
            CompilerAsserts.partialEvaluationConstant(p);
            return p;
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == RootTag.class) {
                return (tags & 0x1) != 0;
            } else if (tag == RootBodyTag.class) {
                return (tags & 0x2) != 0;
            } else if (tag == ExpressionTag.class) {
                return (tags & 0x4) != 0;
            } else if (tag == StatementTag.class) {
                return (tags & 0x8) != 0;
            }
            return false;
        }

        @Override
        public Node copy() {
            TagNode copy = (TagNode) super.copy();
            copy.probe = null;
            return copy;
        }

        @Override
        public SourceSection getSourceSection() {
            SourceSection section = this.sourceSection;
            if (section == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.sourceSection = section = createSourceSection();
            }
            return section;
        }

        @Override
        public SourceSection[] getSourceSections() {
            return findBytecodeNode().getSourceLocations(enterBci);
        }

        private SourceSection createSourceSection() {
            if (enterBci == -1) {
                // only happens for synthetic instrumentable root nodes.
                return null;
            }
            return findBytecodeNode().getSourceLocation(enterBci);
        }

        @TruffleBoundary
        private AbstractBytecodeNode findBytecodeNode() {
            Node current = this;
            while (!(current instanceof AbstractBytecodeNode bytecodeNode)) {
                current = current.getParent();
            }
            if (bytecodeNode == null) {
                throw CompilerDirectives.shouldNotReachHere("Unexpected disconnected node.");
            }
            return bytecodeNode;
        }

        @Override
        protected Class<BytecodeDSLTestLanguage> getLanguage() {
            return BytecodeDSLTestLanguage.class;
        }

        @Override
        public List<TagTree> getTreeChildren() {
            return List.of(this.children);
        }

        @Override
        public List<Class<? extends Tag>> getTags() {
            return List.of(mapTagMaskToTagsArray(this.tags));
        }

        @Override
        public int getEnterBytecodeIndex() {
            return this.enterBci;
        }

        @Override
        public int getReturnBytecodeIndex() {
            return this.returnBci;
        }

    }
    private static final class TagRootNode extends Node {

        @Child TagNode root;
        @CompilationFinal(dimensions = 1) final TagNode[] tagNodes;
        @Child ProbeNode probe;

        TagRootNode(TagNode root, TagNode[] tagNodes) {
            this.root = root;
            this.tagNodes = tagNodes;
        }

        ProbeNode getProbe() {
            ProbeNode localProbe = this.probe;
            if (localProbe == null) {
                this.probe = localProbe = insert(root.createProbe(null));
            }
            return localProbe;
        }

        @Override
        public Node copy() {
            TagRootNode copy = (TagRootNode) super.copy();
            copy.probe = null;
            return copy;
        }

    }
    private abstract static sealed class AbstractBytecodeNode extends BytecodeNode permits CachedBytecodeNode, UncachedBytecodeNode {

        @CompilationFinal(dimensions = 1) final byte[] bytecodes;
        @CompilationFinal(dimensions = 1) final Object[] constants;
        @CompilationFinal(dimensions = 1) final int[] handlers;
        @CompilationFinal(dimensions = 1) final int[] locals;
        @CompilationFinal(dimensions = 1) final int[] sourceInfo;
        final List<Source> sources;
        final int numNodes;
        @Child TagRootNode tagRoot;
        volatile byte[] oldBytecodes;

        protected AbstractBytecodeNode(byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecodes = bytecodes;
            this.constants = constants;
            this.handlers = handlers;
            this.locals = locals;
            this.sourceInfo = sourceInfo;
            this.sources = sources;
            this.numNodes = numNodes;
            this.tagRoot = tagRoot;
        }

        final Node findInstrumentableCallNode(int bci) {
            int[] localHandlers = handlers;
            for (int i = 0; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH) {
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci) {
                    continue;
                }
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci) {
                    continue;
                }
                int handlerKind = localHandlers[i + EXCEPTION_HANDLER_OFFSET_KIND];
                if (handlerKind != HANDLER_TAG_EXCEPTIONAL) {
                    continue;
                }
                int nodeId = localHandlers[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                return tagRoot.tagNodes[nodeId];
            }
            return null;
        }

        @Override
        protected abstract int findBytecodeIndex(Frame frame, Node operationNode);

        final int readValidBytecode(byte[] bc, int bci) {
            int op = BYTES.getShort(bc, bci);
            switch (op) {
                case Instructions.INVALIDATE0 :
                case Instructions.INVALIDATE1 :
                case Instructions.INVALIDATE2 :
                case Instructions.INVALIDATE3 :
                case Instructions.INVALIDATE4 :
                    // While we were processing the exception handler the code invalidated.
                    // We need to re-read the op from the old bytecodes.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return oldBytecodes[bci];
                default :
                    return op;
            }
        }

        abstract long continueAt(BasicInterpreterWithUncached $root, VirtualFrame frame, VirtualFrame localFrame, long startState);

        final BasicInterpreterWithUncached getRoot() {
            return (BasicInterpreterWithUncached) getParent();
        }

        abstract AbstractBytecodeNode toCached();

        abstract AbstractBytecodeNode update(byte[] bytecodes_, Object[] constants_, int[] handlers_, int[] locals_, int[] sourceInfo_, List<Source> sources_, int numNodes_, TagRootNode tagRoot_);

        final void invalidate(AbstractBytecodeNode newNode, CharSequence reason) {
            byte[] bc = this.bytecodes;
            int bci = 0;
            int continuationIndex = 0;
            this.oldBytecodes = Arrays.copyOf(bc, bc.length);
            VarHandle.loadLoadFence();
            while (bci < bc.length) {
                short op = BYTES.getShort(bc, bci);
                switch (op) {
                    case Instructions.POP :
                    case Instructions.DUP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.CONSTANT_NULL :
                    case Instructions.INVALIDATE0 :
                        BYTES.putShort(bc, bci, Instructions.INVALIDATE0);
                        this.getRoot().onInvalidateInstruction(new InstructionImpl(this, bci, op), new InstructionImpl(this, bci, Instructions.INVALIDATE0));
                        bci += 2;
                        break;
                    case Instructions.STORE_LOCAL :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                        BYTES.putShort(bc, bci, Instructions.INVALIDATE1);
                        this.getRoot().onInvalidateInstruction(new InstructionImpl(this, bci, op), new InstructionImpl(this, bci, Instructions.INVALIDATE1));
                        bci += 4;
                        break;
                    case Instructions.BRANCH :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.YIELD :
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    case Instructions.EARLY_RETURN_ :
                    case Instructions.ADD_OPERATION_ :
                    case Instructions.TO_STRING_ :
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                    case Instructions.THROW_OPERATION_ :
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                    case Instructions.APPENDER_OPERATION_ :
                    case Instructions.INVOKE_ :
                    case Instructions.MATERIALIZE_FRAME_ :
                    case Instructions.CREATE_CLOSURE_ :
                    case Instructions.VOID_OPERATION_ :
                    case Instructions.TO_BOOLEAN_ :
                    case Instructions.GET_SOURCE_POSITION_ :
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    case Instructions.GET_SOURCE_POSITIONS_ :
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                    case Instructions.GET_BYTECODE_LOCATION_ :
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    case Instructions.CONTINUE_ :
                    case Instructions.CURRENT_LOCATION_ :
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    case Instructions.ADD_ :
                    case Instructions.MOD_ :
                    case Instructions.LESS_ :
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    case Instructions.INVALIDATE2 :
                        BYTES.putShort(bc, bci, Instructions.INVALIDATE2);
                        this.getRoot().onInvalidateInstruction(new InstructionImpl(this, bci, op), new InstructionImpl(this, bci, Instructions.INVALIDATE2));
                        bci += 6;
                        break;
                    case Instructions.INVALIDATE3 :
                        BYTES.putShort(bc, bci, Instructions.INVALIDATE3);
                        this.getRoot().onInvalidateInstruction(new InstructionImpl(this, bci, op), new InstructionImpl(this, bci, Instructions.INVALIDATE3));
                        bci += 8;
                        break;
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.BRANCH_FALSE :
                    case Instructions.CALL_ :
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    case Instructions.TEE_LOCAL_ :
                    case Instructions.TEE_LOCAL_RANGE_ :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                    case Instructions.INVALIDATE4 :
                        BYTES.putShort(bc, bci, Instructions.INVALIDATE4);
                        this.getRoot().onInvalidateInstruction(new InstructionImpl(this, bci, op), new InstructionImpl(this, bci, Instructions.INVALIDATE4));
                        bci += 10;
                        break;
                }
            }
            reportReplace(this, newNode, reason);
        }

        final void updateContinuationRootNodes(AbstractBytecodeNode newNode, CharSequence reason, ArrayList<ContinuationLocation> continuationLocations, boolean bytecodeReparsed) {
            for (ContinuationLocation continuationLocation : continuationLocations) {
                ContinuationRootNodeImpl continuationRootNode = (ContinuationRootNodeImpl) constants[continuationLocation.constantPoolIndex];
                BytecodeLocation newLocation;
                if (continuationLocation.bci == -1) {
                    newLocation = null;
                } else {
                    newLocation = newNode.getBytecodeLocation(continuationLocation.bci);
                }
                if (bytecodeReparsed) {
                    continuationRootNode.updateBytecodeLocation(newLocation, this, newNode, reason);
                } else {
                    continuationRootNode.updateBytecodeLocationWithoutInvalidate(newLocation);
                }
            }
        }

        private final boolean validateBytecodes() {
            BasicInterpreterWithUncached root;
            byte[] bc = this.bytecodes;
            if (bc == null) {
                // bc is null for serialization root nodes.
                return true;
            }
            Node[] cachedNodes = getCachedNodes();
            int[] branchProfiles = getBranchProfiles();
            int bci = 0;
            TagNode[] tagNodes = tagRoot != null ? tagRoot.tagNodes : null;
            if (bc.length == 0) {
                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: bytecode array must not be null%n%s", dumpInvalid(findLocation(bci))));
            }
            while (bci < bc.length) {
                try {
                    switch (BYTES.getShort(bc, bci)) {
                        case Instructions.POP :
                        case Instructions.DUP :
                        case Instructions.RETURN :
                        case Instructions.THROW :
                        case Instructions.LOAD_NULL :
                        case Instructions.LOAD_VARIADIC_0 :
                        case Instructions.LOAD_VARIADIC_1 :
                        case Instructions.LOAD_VARIADIC_2 :
                        case Instructions.LOAD_VARIADIC_3 :
                        case Instructions.LOAD_VARIADIC_4 :
                        case Instructions.LOAD_VARIADIC_5 :
                        case Instructions.LOAD_VARIADIC_6 :
                        case Instructions.LOAD_VARIADIC_7 :
                        case Instructions.LOAD_VARIADIC_8 :
                        case Instructions.MERGE_VARIADIC :
                        case Instructions.CONSTANT_NULL :
                        case Instructions.INVALIDATE0 :
                        {
                            bci = bci + 2;
                            break;
                        }
                        case Instructions.BRANCH :
                        {
                            int branch_target = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                            if (branch_target < 0 || branch_target >= bc.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. bytecode index is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.BRANCH_BACKWARD :
                        {
                            int branch_target = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                            if (branch_target < 0 || branch_target >= bc.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. bytecode index is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int loop_header_branch_profile = BYTES.getIntUnaligned(bc, bci + 6 /* imm loop_header_branch_profile */);
                            if (branchProfiles != null) {
                                if (loop_header_branch_profile < 0 || loop_header_branch_profile >= branchProfiles.length) {
                                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. branch profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                                }
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.BRANCH_FALSE :
                        case Instructions.SC_AND_ :
                        case Instructions.SC_OR_ :
                        {
                            int branch_target = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                            if (branch_target < 0 || branch_target >= bc.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. bytecode index is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int branch_profile = BYTES.getIntUnaligned(bc, bci + 6 /* imm branch_profile */);
                            if (branchProfiles != null) {
                                if (branch_profile < 0 || branch_profile >= branchProfiles.length) {
                                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. branch profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                                }
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.STORE_LOCAL :
                        case Instructions.LOAD_LOCAL :
                        case Instructions.CLEAR_LOCAL :
                        {
                            short local_offset = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
                            root = this.getRoot();
                            if (local_offset < USER_LOCALS_START_INDEX || local_offset >= root.maxLocals) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. local offset is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 4;
                            break;
                        }
                        case Instructions.LOAD_CONSTANT :
                        {
                            int constant = BYTES.getIntUnaligned(bc, bci + 2 /* imm constant */);
                            if (constant < 0 || constant >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.LOAD_ARGUMENT :
                        {
                            bci = bci + 4;
                            break;
                        }
                        case Instructions.LOAD_EXCEPTION :
                        {
                            short exception_sp = BYTES.getShort(bc, bci + 2 /* imm exception_sp */);
                            root = this.getRoot();
                            int maxStackHeight = root.getFrameDescriptor().getNumberOfSlots() - root.maxLocals;
                            if (exception_sp < 0 || exception_sp > maxStackHeight) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. stack pointer is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 4;
                            break;
                        }
                        case Instructions.LOAD_LOCAL_MAT :
                        case Instructions.STORE_LOCAL_MAT :
                        {
                            short local_offset = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
                            root = this.getRoot().getBytecodeRootNodeImpl(BYTES.getShort(bc, bci + 4 /* imm root_index */));
                            if (local_offset < USER_LOCALS_START_INDEX || local_offset >= root.maxLocals) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. local offset is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.YIELD :
                        {
                            int location = BYTES.getIntUnaligned(bc, bci + 2 /* imm location */);
                            if (location < 0 || location >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.TAG_ENTER :
                        case Instructions.TAG_LEAVE :
                        case Instructions.TAG_LEAVE_VOID :
                        case Instructions.TAG_YIELD :
                        case Instructions.TAG_RESUME :
                        {
                            int tag = BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */);
                            if (tagNodes != null) {
                                TagNode node = tagRoot.tagNodes[tag];
                                if (node == null) {
                                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. tagNode is null%n%s", bci, dumpInvalid(findLocation(bci))));
                                }
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.EARLY_RETURN_ :
                        case Instructions.ADD_OPERATION_ :
                        case Instructions.TO_STRING_ :
                        case Instructions.VERY_COMPLEX_OPERATION_ :
                        case Instructions.THROW_OPERATION_ :
                        case Instructions.READ_EXCEPTION_OPERATION_ :
                        case Instructions.ALWAYS_BOX_OPERATION_ :
                        case Instructions.APPENDER_OPERATION_ :
                        case Instructions.INVOKE_ :
                        case Instructions.MATERIALIZE_FRAME_ :
                        case Instructions.CREATE_CLOSURE_ :
                        case Instructions.VOID_OPERATION_ :
                        case Instructions.TO_BOOLEAN_ :
                        case Instructions.GET_SOURCE_POSITION_ :
                        case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                        case Instructions.GET_SOURCE_POSITIONS_ :
                        case Instructions.COPY_LOCALS_TO_FRAME_ :
                        case Instructions.GET_BYTECODE_LOCATION_ :
                        case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                        case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                        case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                        case Instructions.CONTINUE_ :
                        case Instructions.CURRENT_LOCATION_ :
                        case Instructions.PRINT_HERE_ :
                        case Instructions.INCREMENT_VALUE_ :
                        case Instructions.DOUBLE_VALUE_ :
                        case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                        case Instructions.ADD_ :
                        case Instructions.MOD_ :
                        case Instructions.LESS_ :
                        case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                        case Instructions.EXPLICIT_BINDINGS_TEST_ :
                        case Instructions.IMPLICIT_BINDINGS_TEST_ :
                        {
                            int node = BYTES.getIntUnaligned(bc, bci + 2 /* imm node */);
                            if (node < 0 || node >= numNodes) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. node profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.CALL_ :
                        {
                            int interpreter = BYTES.getIntUnaligned(bc, bci + 2 /* imm interpreter */);
                            if (interpreter < 0 || interpreter >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int node = BYTES.getIntUnaligned(bc, bci + 6 /* imm node */);
                            if (node < 0 || node >= numNodes) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. node profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.ADD_CONSTANT_OPERATION_ :
                        {
                            int constantLhs = BYTES.getIntUnaligned(bc, bci + 2 /* imm constantLhs */);
                            if (constantLhs < 0 || constantLhs >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int node = BYTES.getIntUnaligned(bc, bci + 6 /* imm node */);
                            if (node < 0 || node >= numNodes) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. node profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                        {
                            int constantRhs = BYTES.getIntUnaligned(bc, bci + 2 /* imm constantRhs */);
                            if (constantRhs < 0 || constantRhs >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int node = BYTES.getIntUnaligned(bc, bci + 6 /* imm node */);
                            if (node < 0 || node >= numNodes) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. node profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.TEE_LOCAL_ :
                        case Instructions.TEE_LOCAL_RANGE_ :
                        {
                            int setter = BYTES.getIntUnaligned(bc, bci + 2 /* imm setter */);
                            if (setter < 0 || setter >= constants.length) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. constant is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            int node = BYTES.getIntUnaligned(bc, bci + 6 /* imm node */);
                            if (node < 0 || node >= numNodes) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error at index: %s. node profile is out of bounds%n%s", bci, dumpInvalid(findLocation(bci))));
                            }
                            bci = bci + 10;
                            break;
                        }
                        case Instructions.INVALIDATE1 :
                        {
                            bci = bci + 4;
                            break;
                        }
                        case Instructions.INVALIDATE2 :
                        {
                            bci = bci + 6;
                            break;
                        }
                        case Instructions.INVALIDATE3 :
                        {
                            bci = bci + 8;
                            break;
                        }
                        case Instructions.INVALIDATE4 :
                        {
                            bci = bci + 10;
                            break;
                        }
                        default :
                            throw CompilerDirectives.shouldNotReachHere("Invalid BCI at index: " + bci);
                    }
                } catch (AssertionError e) {
                    throw e;
                } catch (Throwable e) {
                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error:%n%s", dumpInvalid(findLocation(bci))), e);
                }
            }
            int[] ex = this.handlers;
            if (ex.length % EXCEPTION_HANDLER_LENGTH != 0) {
                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: exception handler table size is incorrect%n%s", dumpInvalid(findLocation(bci))));
            }
            for (int i = 0; i < ex.length; i = i + EXCEPTION_HANDLER_LENGTH) {
                int startBci = ex[i + EXCEPTION_HANDLER_OFFSET_START_BCI];
                int endBci = ex[i + EXCEPTION_HANDLER_OFFSET_END_BCI];
                int handlerKind = ex[i + EXCEPTION_HANDLER_OFFSET_KIND];
                int handlerBci = ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                int handlerSp = ex[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP];
                if (startBci < 0 || startBci >= bc.length) {
                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: exception handler startBci is out of bounds%n%s", dumpInvalid(findLocation(bci))));
                }
                if (endBci < 0 || endBci > bc.length) {
                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: exception handler endBci is out of bounds%n%s", dumpInvalid(findLocation(bci))));
                }
                if (startBci > endBci) {
                    throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: exception handler bci range is malformed%n%s", dumpInvalid(findLocation(bci))));
                }
                switch (handlerKind) {
                    case HANDLER_TAG_EXCEPTIONAL :
                        if (tagNodes != null) {
                            TagNode node = tagRoot.tagNodes[handlerBci];
                            if (node == null) {
                                throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: tagNode is null%n%s", dumpInvalid(findLocation(bci))));
                            }
                        }
                        break;
                    default :
                        if (handlerKind != HANDLER_CUSTOM) {
                            throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: unexpected handler kind%n%s", dumpInvalid(findLocation(bci))));
                        }
                        if (handlerBci < 0 || handlerBci >= bc.length) {
                            throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: exception handler handlerBci is out of bounds%n%s", dumpInvalid(findLocation(bci))));
                        }
                        break;
                }
            }
            int[] info = this.sourceInfo;
            List<Source> localSources = this.sources;
            if (info != null) {
                for (int i = 0; i < info.length; i += SOURCE_INFO_LENGTH) {
                    int startBci = info[i + SOURCE_INFO_OFFSET_START_BCI];
                    int endBci = info[i + SOURCE_INFO_OFFSET_END_BCI];
                    int sourceIndex = info[i + SOURCE_INFO_OFFSET_SOURCE];
                    if (startBci > endBci) {
                        throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: source bci range is malformed%n%s", dumpInvalid(findLocation(bci))));
                    } else if (sourceIndex < 0 || sourceIndex > localSources.size()) {
                        throw CompilerDirectives.shouldNotReachHere(String.format("Bytecode validation error: source index is out of bounds%n%s", dumpInvalid(findLocation(bci))));
                    }
                }
            }
            return true;
        }

        private final String dumpInvalid(BytecodeLocation highlightedLocation) {
            try {
                return dump(highlightedLocation);
            } catch (Throwable t) {
                return "<dump error>";
            }
        }

        abstract AbstractBytecodeNode cloneUninitialized();

        abstract Node[] getCachedNodes();

        abstract int[] getBranchProfiles();

        @Override
        @TruffleBoundary
        public SourceSection getSourceSection() {
            int[] info = this.sourceInfo;
            if (info == null) {
                return null;
            }
            // The source table encodes a preorder traversal of a logical tree of source sections (with entries in reverse).
            // The most specific source section corresponds to the "lowest" node in the tree that covers the whole bytecode range.
            // We find this node by iterating the entries from the root until we hit a node that does not cover the bytecode range.
            int mostSpecific = -1;
            for (int i = info.length - SOURCE_INFO_LENGTH; i >= 0; i -= SOURCE_INFO_LENGTH) {
                if (info[i + SOURCE_INFO_OFFSET_START_BCI] != 0 ||
                    info[i + SOURCE_INFO_OFFSET_END_BCI] != bytecodes.length) {
                    break;
                }
                mostSpecific = i;
            }
            if (mostSpecific != -1) {
                return createSourceSection(sources, info, mostSpecific);
            }
            return null;
        }

        @Override
        public final SourceSection getSourceLocation(int bci) {
            assert validateBytecodeIndex(bci);
            int[] info = this.sourceInfo;
            if (info == null) {
                return null;
            }
            for (int i = 0; i < info.length; i += SOURCE_INFO_LENGTH) {
                int startBci = info[i + SOURCE_INFO_OFFSET_START_BCI];
                int endBci = info[i + SOURCE_INFO_OFFSET_END_BCI];
                if (startBci <= bci && bci < endBci) {
                    return createSourceSection(sources, info, i);
                }
            }
            return null;
        }

        @Override
        public final SourceSection[] getSourceLocations(int bci) {
            assert validateBytecodeIndex(bci);
            int[] info = this.sourceInfo;
            if (info == null) {
                return null;
            }
            int sectionIndex = 0;
            SourceSection[] sections = new SourceSection[8];
            for (int i = 0; i < info.length; i += SOURCE_INFO_LENGTH) {
                int startBci = info[i + SOURCE_INFO_OFFSET_START_BCI];
                int endBci = info[i + SOURCE_INFO_OFFSET_END_BCI];
                if (startBci <= bci && bci < endBci) {
                    if (sectionIndex == sections.length) {
                        sections = Arrays.copyOf(sections, Math.min(sections.length * 2, info.length / SOURCE_INFO_LENGTH));
                    }
                    sections[sectionIndex++] = createSourceSection(sources, info, i);
                }
            }
            return Arrays.copyOf(sections, sectionIndex);
        }

        @Override
        protected Instruction findInstruction(int bci) {
            return new InstructionImpl(this, bci, readValidBytecode(this.bytecodes, bci));
        }

        @Override
        protected boolean validateBytecodeIndex(int bci) {
            byte[] bc = this.bytecodes;
            if (bci < 0 || bci >= bc.length) {
                throw new IllegalArgumentException("Bytecode index out of range " + bci);
            }
            int op = readValidBytecode(bc, bci);
            if (op < 0 || op > 78) {
                throw new IllegalArgumentException("Invalid op at bytecode index " + op);
            }
            return true;
        }

        @Override
        public List<SourceInformation> getSourceInformation() {
            if (sourceInfo == null) {
                return null;
            }
            return new SourceInformationList(this);
        }

        @Override
        public boolean hasSourceInformation() {
            return sourceInfo != null;
        }

        @Override
        public SourceInformationTree getSourceInformationTree() {
            if (sourceInfo == null) {
                return null;
            }
            return SourceInformationTreeImpl.parse(this);
        }

        @Override
        public List<ExceptionHandler> getExceptionHandlers() {
            return new ExceptionHandlerList(this);
        }

        @Override
        public TagTree getTagTree() {
            if (this.tagRoot == null) {
                return null;
            }
            return this.tagRoot.root;
        }

        @Override
        @ExplodeLoop
        public final int getLocalCount(int bci) {
            assert validateBytecodeIndex(bci);
            CompilerAsserts.partialEvaluationConstant(bci);
            int count = 0;
            for (int index = 0; index < locals.length; index += LOCALS_LENGTH) {
                int startIndex = locals[index + LOCALS_OFFSET_START_BCI];
                int endIndex = locals[index + LOCALS_OFFSET_END_BCI];
                if (bci >= startIndex && bci < endIndex) {
                    count++;
                }
            }
            CompilerAsserts.partialEvaluationConstant(count);
            return count;
        }

        @Override
        protected final void clearLocalValueInternal(Frame frame, int localOffset, int localIndex) {
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            int frameIndex = USER_LOCALS_START_INDEX + localOffset;
            FRAMES.clear(frame, frameIndex);
        }

        @Override
        protected final boolean isLocalClearedInternal(Frame frame, int localOffset, int localIndex) {
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            int frameIndex = USER_LOCALS_START_INDEX + localOffset;
            return FRAMES.getTag(frame, frameIndex) == FrameSlotKind.Illegal.tag;
        }

        @Override
        public final Object getLocalValue(int bci, Frame frame, int localOffset) {
            assert validateBytecodeIndex(bci);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(localOffset);
            assert localOffset >= 0 && localOffset < getLocalCount(bci) : "Invalid out-of-bounds local offset provided.";
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            int frameIndex = USER_LOCALS_START_INDEX + localOffset;
            if (frame.isObject(frameIndex)) {
                return frame.getObject(USER_LOCALS_START_INDEX + localOffset);
            }
            return null;
        }

        @Override
        public void setLocalValue(int bci, Frame frame, int localOffset, Object value) {
            assert validateBytecodeIndex(bci);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(localOffset);
            assert localOffset >= 0 && localOffset < getLocalCount(bci) : "Invalid out-of-bounds local offset provided.";
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            int frameIndex = USER_LOCALS_START_INDEX + localOffset;
            frame.setObject(frameIndex, value);
        }

        @Override
        protected final Object getLocalValueInternal(Frame frame, int localOffset, int localIndex) {
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            int frameIndex = USER_LOCALS_START_INDEX + localOffset;
            return frame.getObject(frameIndex);
        }

        @Override
        protected void setLocalValueInternal(Frame frame, int localOffset, int localIndex, Object value) {
            assert getRoot().getFrameDescriptor() == frame.getFrameDescriptor() : "Invalid frame with invalid descriptor passed.";
            frame.setObject(USER_LOCALS_START_INDEX + localOffset, value);
        }

        @ExplodeLoop
        protected final int localIndexToTableIndex(int bci, int localIndex) {
            for (int index = 0; index < locals.length; index += LOCALS_LENGTH) {
                int startIndex = locals[index + LOCALS_OFFSET_START_BCI];
                int endIndex = locals[index + LOCALS_OFFSET_END_BCI];
                if (bci >= startIndex && bci < endIndex) {
                    if (locals[index + LOCALS_OFFSET_LOCAL_INDEX] == localIndex) {
                        return index;
                    }
                }
            }
            return -1;
        }

        @ExplodeLoop
        protected final int localOffsetToTableIndex(int bci, int localOffset) {
            int count = 0;
            for (int index = 0; index < locals.length; index += LOCALS_LENGTH) {
                int startIndex = locals[index + LOCALS_OFFSET_START_BCI];
                int endIndex = locals[index + LOCALS_OFFSET_END_BCI];
                if (bci >= startIndex && bci < endIndex) {
                    if (count == localOffset) {
                        return index;
                    }
                    count++;
                }
            }
            return -1;
        }

        @Override
        public Object getLocalName(int bci, int localOffset) {
            assert validateBytecodeIndex(bci);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(localOffset);
            assert localOffset >= 0 && localOffset < getLocalCount(bci) : "Invalid out-of-bounds local offset provided.";
            int index = localOffsetToTableIndex(bci, localOffset);
            if (index == -1) {
                return null;
            }
            int nameId = locals[index + LOCALS_OFFSET_NAME];
            if (nameId == -1) {
                return null;
            } else {
                return ACCESS.readObject(constants, nameId);
            }
        }

        @Override
        public Object getLocalInfo(int bci, int localOffset) {
            assert validateBytecodeIndex(bci);
            CompilerAsserts.partialEvaluationConstant(bci);
            CompilerAsserts.partialEvaluationConstant(localOffset);
            assert localOffset >= 0 && localOffset < getLocalCount(bci) : "Invalid out-of-bounds local offset provided.";
            int index = localOffsetToTableIndex(bci, localOffset);
            if (index == -1) {
                return null;
            }
            int infoId = locals[index + LOCALS_OFFSET_INFO];
            if (infoId == -1) {
                return null;
            } else {
                return ACCESS.readObject(constants, infoId);
            }
        }

        @Override
        public List<LocalVariable> getLocals() {
            return new LocalVariableList(this);
        }

        private TagNode[] getTagNodes() {
            return tagRoot != null ? tagRoot.tagNodes : null;
        }

        @Override
        protected int translateBytecodeIndex(BytecodeNode newNode, int bytecodeIndex) {
            return (int) transitionState((AbstractBytecodeNode) newNode, (bytecodeIndex & 0xFFFFFFFFL), null);
        }

        final long transitionState(AbstractBytecodeNode newBytecode, long state, ContinuationRootNodeImpl continuationRootNode) {
            byte[] oldBc = this.oldBytecodes;
            byte[] newBc = newBytecode.bytecodes;
            if (continuationRootNode != null && oldBc == null) {
                // Transition continuationRootNode to cached.
                BytecodeLocation newContinuationLocation = newBytecode.getBytecodeLocation(continuationRootNode.getLocation().getBytecodeIndex());
                continuationRootNode.updateBytecodeLocation(newContinuationLocation, this, newBytecode, "transition to cached");
            }
            if (oldBc == null || this == newBytecode || this.bytecodes == newBc) {
                // No change in bytecodes.
                return state;
            }
            int oldBci = (int) state;
            int newBci = computeNewBci(oldBci, oldBc, newBc, this.getTagNodes(), newBytecode.getTagNodes());
            getRoot().onBytecodeStackTransition(new InstructionImpl(this, oldBci, BYTES.getShort(oldBc, oldBci)), new InstructionImpl(newBytecode, newBci, BYTES.getShort(newBc, newBci)));
            return (state & 0xFFFF00000000L) | (newBci & 0xFFFFFFFFL);
        }

        public void adoptNodesAfterUpdate() {
            // no nodes to adopt
        }

        static BytecodeLocation findLocation(AbstractBytecodeNode node, int bci) {
            return node.findLocation(bci);
        }

        private static SourceSection createSourceSection(List<Source> sources, int[] info, int index) {
            int sourceIndex = info[index + SOURCE_INFO_OFFSET_SOURCE];
            int start = info[index + SOURCE_INFO_OFFSET_START];
            int length = info[index + SOURCE_INFO_OFFSET_LENGTH];
            if (start == -1 && length == -1) {
                return sources.get(sourceIndex).createUnavailableSection();
            }
            assert start >= 0 : "invalid source start index";
            assert length >= 0 : "invalid source length";
            return sources.get(sourceIndex).createSection(start, length);
        }

        private static int toStableBytecodeIndex(byte[] bc, int searchBci) {
            int bci = 0;
            int stableBci = 0;
            while (bci != searchBci && bci < bc.length) {
                switch (BYTES.getShort(bc, bci)) {
                    case Instructions.POP :
                    case Instructions.DUP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.CONSTANT_NULL :
                    case Instructions.INVALIDATE0 :
                        bci += 2;
                        stableBci += 2;
                        break;
                    case Instructions.STORE_LOCAL :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                        bci += 4;
                        stableBci += 4;
                        break;
                    case Instructions.BRANCH :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.YIELD :
                    case Instructions.EARLY_RETURN_ :
                    case Instructions.ADD_OPERATION_ :
                    case Instructions.TO_STRING_ :
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                    case Instructions.THROW_OPERATION_ :
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                    case Instructions.APPENDER_OPERATION_ :
                    case Instructions.INVOKE_ :
                    case Instructions.MATERIALIZE_FRAME_ :
                    case Instructions.CREATE_CLOSURE_ :
                    case Instructions.VOID_OPERATION_ :
                    case Instructions.TO_BOOLEAN_ :
                    case Instructions.GET_SOURCE_POSITION_ :
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    case Instructions.GET_SOURCE_POSITIONS_ :
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                    case Instructions.GET_BYTECODE_LOCATION_ :
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    case Instructions.CONTINUE_ :
                    case Instructions.CURRENT_LOCATION_ :
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    case Instructions.ADD_ :
                    case Instructions.MOD_ :
                    case Instructions.LESS_ :
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    case Instructions.INVALIDATE2 :
                        bci += 6;
                        stableBci += 6;
                        break;
                    case Instructions.INVALIDATE3 :
                        bci += 8;
                        stableBci += 8;
                        break;
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.BRANCH_FALSE :
                    case Instructions.CALL_ :
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    case Instructions.TEE_LOCAL_ :
                    case Instructions.TEE_LOCAL_RANGE_ :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                    case Instructions.INVALIDATE4 :
                        bci += 10;
                        stableBci += 10;
                        break;
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                        bci += 6;
                        break;
                    default :
                        throw CompilerDirectives.shouldNotReachHere("Invalid bytecode.");
                }
            }
            if (bci >= bc.length) {
                throw CompilerDirectives.shouldNotReachHere("Could not translate bytecode index.");
            }
            return stableBci;
        }

        private static int fromStableBytecodeIndex(byte[] bc, int stableSearchBci) {
            int bci = 0;
            int stableBci = 0;
            while (stableBci != stableSearchBci && bci < bc.length) {
                switch (BYTES.getShort(bc, bci)) {
                    case Instructions.POP :
                    case Instructions.DUP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.CONSTANT_NULL :
                    case Instructions.INVALIDATE0 :
                        bci += 2;
                        stableBci += 2;
                        break;
                    case Instructions.STORE_LOCAL :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                        bci += 4;
                        stableBci += 4;
                        break;
                    case Instructions.BRANCH :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.YIELD :
                    case Instructions.EARLY_RETURN_ :
                    case Instructions.ADD_OPERATION_ :
                    case Instructions.TO_STRING_ :
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                    case Instructions.THROW_OPERATION_ :
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                    case Instructions.APPENDER_OPERATION_ :
                    case Instructions.INVOKE_ :
                    case Instructions.MATERIALIZE_FRAME_ :
                    case Instructions.CREATE_CLOSURE_ :
                    case Instructions.VOID_OPERATION_ :
                    case Instructions.TO_BOOLEAN_ :
                    case Instructions.GET_SOURCE_POSITION_ :
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    case Instructions.GET_SOURCE_POSITIONS_ :
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                    case Instructions.GET_BYTECODE_LOCATION_ :
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    case Instructions.CONTINUE_ :
                    case Instructions.CURRENT_LOCATION_ :
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    case Instructions.ADD_ :
                    case Instructions.MOD_ :
                    case Instructions.LESS_ :
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    case Instructions.INVALIDATE2 :
                        bci += 6;
                        stableBci += 6;
                        break;
                    case Instructions.INVALIDATE3 :
                        bci += 8;
                        stableBci += 8;
                        break;
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.BRANCH_FALSE :
                    case Instructions.CALL_ :
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    case Instructions.TEE_LOCAL_ :
                    case Instructions.TEE_LOCAL_RANGE_ :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                    case Instructions.INVALIDATE4 :
                        bci += 10;
                        stableBci += 10;
                        break;
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                        bci += 6;
                        break;
                    default :
                        throw CompilerDirectives.shouldNotReachHere("Invalid bytecode.");
                }
            }
            if (bci >= bc.length) {
                throw CompilerDirectives.shouldNotReachHere("Could not translate bytecode index.");
            }
            return bci;
        }

        private static int transitionInstrumentationIndex(byte[] oldBc, int oldBciBase, int oldBciTarget, byte[] newBc, int newBciBase, TagNode[] oldTagNodes, TagNode[] newTagNodes) {
            int oldBci = oldBciBase;
            int newBci = newBciBase;
            short searchOp = -1;
            int searchTags = -1;
            while (oldBci < oldBciTarget) {
                short op = BYTES.getShort(oldBc, oldBci);
                searchOp = op;
                switch (op) {
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                        searchTags = -1;
                        oldBci += 6;
                        break;
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                        searchTags = ACCESS.uncheckedCast(ACCESS.readObject(oldTagNodes, BYTES.getIntUnaligned(oldBc, oldBci + 2 /* imm tag */)), TagNode.class).tags;
                        oldBci += 6;
                        break;
                    default :
                        throw CompilerDirectives.shouldNotReachHere("Unexpected bytecode.");
                }
            }
            assert searchOp != -1;
            oldBci = oldBciBase;
            int opCounter = 0;
            while (oldBci < oldBciTarget) {
                short op = BYTES.getShort(oldBc, oldBci);
                switch (op) {
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                    {
                        if (searchOp == op) {
                            opCounter++;
                        }
                        oldBci += 6;
                        break;
                    }
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    {
                        int opTags = ACCESS.uncheckedCast(ACCESS.readObject(oldTagNodes, BYTES.getIntUnaligned(oldBc, oldBci + 2 /* imm tag */)), TagNode.class).tags;
                        if (searchOp == op && searchTags == opTags) {
                            opCounter++;
                        }
                        oldBci += 6;
                        break;
                    }
                    default :
                        throw CompilerDirectives.shouldNotReachHere("Unexpected bytecode.");
                }
            }
            assert opCounter > 0;
            while (opCounter > 0) {
                short op = BYTES.getShort(newBc, newBci);
                switch (op) {
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                    {
                        if (searchOp == op) {
                            opCounter--;
                        }
                        newBci += 6;
                        break;
                    }
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    {
                        int opTags = ACCESS.uncheckedCast(ACCESS.readObject(newTagNodes, BYTES.getIntUnaligned(newBc, newBci + 2 /* imm tag */)), TagNode.class).tags;
                        if (searchOp == op && searchTags == opTags) {
                            opCounter--;
                        }
                        newBci += 6;
                        break;
                    }
                    default :
                        throw CompilerDirectives.shouldNotReachHere("Unexpected bytecode.");
                }
            }
            return newBci;
        }

        static final int computeNewBci(int oldBci, byte[] oldBc, byte[] newBc, TagNode[] oldTagNodes, TagNode[] newTagNodes) {
            int stableBci = toStableBytecodeIndex(oldBc, oldBci);
            int newBci = fromStableBytecodeIndex(newBc, stableBci);
            int oldBciBase = fromStableBytecodeIndex(oldBc, stableBci);
            if (oldBci != oldBciBase) {
                // Transition within an in instrumentation bytecode.
                // Needs to compute exact location where to continue.
                newBci = transitionInstrumentationIndex(oldBc, oldBciBase, oldBci, newBc, newBci, oldTagNodes, newTagNodes);
            }
            return newBci;
        }

        private static Object createStackTraceElement(TruffleStackTraceElement stackTraceElement) {
            return createDefaultStackTraceElement(stackTraceElement);
        }

    }
    @DenyReplace
    private static final class CachedBytecodeNode extends AbstractBytecodeNode implements BytecodeOSRNode {

        private static final boolean[] EMPTY_EXCEPTION_PROFILES = new boolean[0];

        @CompilationFinal(dimensions = 1) private Node[] cachedNodes_;
        @CompilationFinal(dimensions = 1) private final boolean[] exceptionProfiles_;
        @CompilationFinal(dimensions = 1) private final int[] branchProfiles_;
        @CompilationFinal private Object osrMetadata_;

        CachedBytecodeNode(byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot) {
            super(bytecodes, constants, handlers, locals, sourceInfo, sources, numNodes, tagRoot);
            CompilerAsserts.neverPartOfCompilation();
            Node[] result = new Node[this.numNodes];
            byte[] bc = bytecodes;
            int bci = 0;
            int numConditionalBranches = 0;
            loop: while (bci < bc.length) {
                switch (BYTES.getShort(bc, bci)) {
                    case Instructions.POP :
                    case Instructions.DUP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.CONSTANT_NULL :
                    case Instructions.INVALIDATE0 :
                        bci += 2;
                        break;
                    case Instructions.STORE_LOCAL :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                        bci += 4;
                        break;
                    case Instructions.BRANCH :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.YIELD :
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    case Instructions.INVALIDATE2 :
                        bci += 6;
                        break;
                    case Instructions.INVALIDATE3 :
                        bci += 8;
                        break;
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.INVALIDATE4 :
                        bci += 10;
                        break;
                    case Instructions.EARLY_RETURN_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new EarlyReturn_Node());
                        bci += 6;
                        break;
                    case Instructions.ADD_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new AddOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.TO_STRING_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ToString_Node());
                        bci += 6;
                        break;
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new VeryComplexOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.THROW_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ThrowOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ReadExceptionOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new AlwaysBoxOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.APPENDER_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new AppenderOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.INVOKE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new Invoke_Node());
                        bci += 6;
                        break;
                    case Instructions.MATERIALIZE_FRAME_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new MaterializeFrame_Node());
                        bci += 6;
                        break;
                    case Instructions.CREATE_CLOSURE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CreateClosure_Node());
                        bci += 6;
                        break;
                    case Instructions.VOID_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new VoidOperation_Node());
                        bci += 6;
                        break;
                    case Instructions.TO_BOOLEAN_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ToBoolean_Node());
                        bci += 6;
                        break;
                    case Instructions.GET_SOURCE_POSITION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new GetSourcePosition_Node());
                        bci += 6;
                        break;
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new EnsureAndGetSourcePosition_Node());
                        bci += 6;
                        break;
                    case Instructions.GET_SOURCE_POSITIONS_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new GetSourcePositions_Node());
                        bci += 6;
                        break;
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CopyLocalsToFrame_Node());
                        bci += 6;
                        break;
                    case Instructions.GET_BYTECODE_LOCATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new GetBytecodeLocation_Node());
                        bci += 6;
                        break;
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CollectBytecodeLocations_Node());
                        bci += 6;
                        break;
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CollectSourceLocations_Node());
                        bci += 6;
                        break;
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CollectAllSourceLocations_Node());
                        bci += 6;
                        break;
                    case Instructions.CONTINUE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new Continue_Node());
                        bci += 6;
                        break;
                    case Instructions.CURRENT_LOCATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new CurrentLocation_Node());
                        bci += 6;
                        break;
                    case Instructions.PRINT_HERE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new PrintHere_Node());
                        bci += 6;
                        break;
                    case Instructions.INCREMENT_VALUE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new IncrementValue_Node());
                        bci += 6;
                        break;
                    case Instructions.DOUBLE_VALUE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new DoubleValue_Node());
                        bci += 6;
                        break;
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new EnableIncrementValueInstrumentation_Node());
                        bci += 6;
                        break;
                    case Instructions.ADD_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new Add_Node());
                        bci += 6;
                        break;
                    case Instructions.MOD_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new Mod_Node());
                        bci += 6;
                        break;
                    case Instructions.LESS_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new Less_Node());
                        bci += 6;
                        break;
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new EnableDoubleValueInstrumentation_Node());
                        bci += 6;
                        break;
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ExplicitBindingsTest_Node());
                        bci += 6;
                        break;
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                        result[BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)] = insert(new ImplicitBindingsTest_Node());
                        bci += 6;
                        break;
                    case Instructions.CALL_ :
                        result[BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)] = insert(new Call_Node());
                        bci += 10;
                        break;
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                        result[BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)] = insert(new AddConstantOperation_Node());
                        bci += 10;
                        break;
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                        result[BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)] = insert(new AddConstantOperationAtEnd_Node());
                        bci += 10;
                        break;
                    case Instructions.TEE_LOCAL_ :
                        result[BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)] = insert(new TeeLocal_Node());
                        bci += 10;
                        break;
                    case Instructions.TEE_LOCAL_RANGE_ :
                        result[BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)] = insert(new TeeLocalRange_Node());
                        bci += 10;
                        break;
                    case Instructions.BRANCH_FALSE :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                        numConditionalBranches++;
                        bci += 10;
                        break;
                    default :
                    {
                        throw assertionFailed("Should not reach here");
                    }
                }
            }
            assert bci == bc.length;
            this.cachedNodes_ = result;
            this.branchProfiles_ = allocateBranchProfiles(numConditionalBranches);
            this.exceptionProfiles_ = handlers.length == 0 ? EMPTY_EXCEPTION_PROFILES : new boolean[handlers.length / 5];
        }

        CachedBytecodeNode(byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot, Node[] cachedNodes_, boolean[] exceptionProfiles_, int[] branchProfiles_, Object osrMetadata_) {
            super(bytecodes, constants, handlers, locals, sourceInfo, sources, numNodes, tagRoot);
            this.cachedNodes_ = cachedNodes_;
            this.exceptionProfiles_ = exceptionProfiles_;
            this.branchProfiles_ = branchProfiles_;
            this.osrMetadata_ = osrMetadata_;
        }

        @Override
        @BytecodeInterpreterSwitch
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        long continueAt(BasicInterpreterWithUncached $root, VirtualFrame frame, VirtualFrame localFrame, long startState) {
            byte[] bc = this.bytecodes;
            Node[] cachedNodes = this.cachedNodes_;
            int[] branchProfiles = this.branchProfiles_;
            int bci = (int) startState;
            int sp = (short) (startState >>> 32);
            int op;
            long temp;
            LoopCounter loopCounter = new LoopCounter();
            FRAMES.setInt(localFrame, BCI_INDEX, -1);
            loop: while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                op = BYTES.getShort(bc, bci);
                CompilerAsserts.partialEvaluationConstant(op);
                $root.beforeInstructionExecute(new InstructionImpl(this, bci, op));
                try {
                    switch (op) {
                        case Instructions.POP :
                        {
                            doPop(this, frame, bc, bci, sp);
                            sp -= 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.DUP :
                        {
                            FRAMES.copy(frame, sp - 1, sp);
                            sp += 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.RETURN :
                        {
                            if (CompilerDirectives.hasNextTier() && loopCounter.value > 0) {
                                LoopNode.reportLoopCount(this, loopCounter.value);
                            }
                            $root.afterRootExecute(new InstructionImpl(this, bci, op), FRAMES.getObject(frame, (sp - 1)), null);
                            return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
                        }
                        case Instructions.BRANCH :
                        {
                            bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                            break;
                        }
                        case Instructions.BRANCH_BACKWARD :
                        {
                            if (CompilerDirectives.hasNextTier() && ++loopCounter.value >= LoopCounter.REPORT_LOOP_STRIDE) {
                                LoopNode.reportLoopCount(this, loopCounter.value);
                                loopCounter.value = 0;
                            }
                            temp = doBranchBackward(frame, localFrame, bc, bci, sp);
                            if (temp != -1) {
                                return temp;
                            }
                            bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                            break;
                        }
                        case Instructions.BRANCH_FALSE :
                        {
                            if (profileBranch(branchProfiles, BYTES.getIntUnaligned(bc, bci + 6 /* imm branch_profile */), (boolean) FRAMES.uncheckedGetObject(frame, sp - 1))) {
                                sp -= 1;
                                bci += 10;
                                break;
                            } else {
                                sp -= 1;
                                bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                break;
                            }
                        }
                        case Instructions.STORE_LOCAL :
                        {
                            doStoreLocal(frame, localFrame, bc, bci, sp);
                            FRAMES.clear(frame, sp - 1);
                            sp -= 1;
                            bci += 4;
                            break;
                        }
                        case Instructions.THROW :
                        {
                            throw sneakyThrow((Throwable) FRAMES.uncheckedGetObject(frame, sp - 1));
                        }
                        case Instructions.LOAD_CONSTANT :
                        {
                            if (CompilerDirectives.inCompiledCode()) {
                                loadConstantCompiled(frame, bc, bci, sp, constants);
                            } else {
                                FRAMES.setObject(frame, sp, ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm constant */)));
                            }
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.LOAD_NULL :
                        {
                            FRAMES.setObject(frame, sp, null);
                            sp += 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_ARGUMENT :
                        {
                            FRAMES.setObject(frame, sp, localFrame.getArguments()[BYTES.getShort(bc, bci + 2 /* imm index */)]);
                            sp += 1;
                            bci += 4;
                            break;
                        }
                        case Instructions.LOAD_EXCEPTION :
                        {
                            FRAMES.setObject(frame, sp, FRAMES.getObject(frame, $root.maxLocals + BYTES.getShort(bc, bci + 2 /* imm exception_sp */)));
                            sp += 1;
                            bci += 4;
                            break;
                        }
                        case Instructions.LOAD_LOCAL :
                        {
                            doLoadLocal(this, frame, localFrame, bc, bci, sp);
                            sp += 1;
                            bci += 4;
                            break;
                        }
                        case Instructions.LOAD_LOCAL_MAT :
                        {
                            doLoadLocalMat(this, frame, ((VirtualFrame) FRAMES.uncheckedGetObject(frame, sp - 1)), bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.STORE_LOCAL_MAT :
                        {
                            doStoreLocalMat(frame, ((VirtualFrame) FRAMES.uncheckedGetObject(frame, sp - 2)), bc, bci, sp);
                            sp -= 2;
                            bci += 6;
                            break;
                        }
                        case Instructions.YIELD :
                        {
                            if (CompilerDirectives.hasNextTier() && loopCounter.value > 0) {
                                LoopNode.reportLoopCount(this, loopCounter.value);
                            }
                            $root.afterRootExecute(new InstructionImpl(this, bci, op), FRAMES.getObject(frame, (sp - 1)), null);
                            doYield(frame, localFrame, bc, bci, sp, $root);
                            return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
                        }
                        case Instructions.TAG_ENTER :
                        {
                            doTagEnter(frame, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.TAG_LEAVE :
                        {
                            doTagLeave(this, frame, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.TAG_LEAVE_VOID :
                        {
                            doTagLeaveVoid(frame, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.TAG_YIELD :
                        {
                            doTagYield(frame, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.TAG_RESUME :
                        {
                            doTagResume(frame, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_0 :
                        {
                            FRAMES.setObject(frame, sp, EMPTY_ARRAY);
                            sp += 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_1 :
                        {
                            FRAMES.setObject(frame, sp - 1, readVariadic(frame, sp, 1));
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_2 :
                        {
                            FRAMES.setObject(frame, sp - 2, readVariadic(frame, sp, 2));
                            sp -= 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_3 :
                        {
                            FRAMES.setObject(frame, sp - 3, readVariadic(frame, sp, 3));
                            sp -= 2;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_4 :
                        {
                            FRAMES.setObject(frame, sp - 4, readVariadic(frame, sp, 4));
                            sp -= 3;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_5 :
                        {
                            FRAMES.setObject(frame, sp - 5, readVariadic(frame, sp, 5));
                            sp -= 4;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_6 :
                        {
                            FRAMES.setObject(frame, sp - 6, readVariadic(frame, sp, 6));
                            sp -= 5;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_7 :
                        {
                            FRAMES.setObject(frame, sp - 7, readVariadic(frame, sp, 7));
                            sp -= 6;
                            bci += 2;
                            break;
                        }
                        case Instructions.LOAD_VARIADIC_8 :
                        {
                            FRAMES.setObject(frame, sp - 8, readVariadic(frame, sp, 8));
                            sp -= 7;
                            bci += 2;
                            break;
                        }
                        case Instructions.MERGE_VARIADIC :
                        {
                            FRAMES.setObject(frame, sp - 1, mergeVariadic((Object[]) FRAMES.uncheckedGetObject(frame, sp - 1)));
                            bci += 2;
                            break;
                        }
                        case Instructions.CONSTANT_NULL :
                        {
                            FRAMES.setObject(frame, sp, null);
                            sp += 1;
                            bci += 2;
                            break;
                        }
                        case Instructions.CLEAR_LOCAL :
                        {
                            FRAMES.clear(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */));
                            bci += 4;
                            break;
                        }
                        case Instructions.EARLY_RETURN_ :
                        {
                            doEarlyReturn_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.ADD_OPERATION_ :
                        {
                            doAddOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.TO_STRING_ :
                        {
                            doToString_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.CALL_ :
                        {
                            doCall_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 10;
                            break;
                        }
                        case Instructions.ADD_CONSTANT_OPERATION_ :
                        {
                            doAddConstantOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 10;
                            break;
                        }
                        case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                        {
                            doAddConstantOperationAtEnd_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 10;
                            break;
                        }
                        case Instructions.VERY_COMPLEX_OPERATION_ :
                        {
                            doVeryComplexOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.THROW_OPERATION_ :
                        {
                            doThrowOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.READ_EXCEPTION_OPERATION_ :
                        {
                            doReadExceptionOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.ALWAYS_BOX_OPERATION_ :
                        {
                            doAlwaysBoxOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.APPENDER_OPERATION_ :
                        {
                            doAppenderOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 2;
                            bci += 6;
                            break;
                        }
                        case Instructions.TEE_LOCAL_ :
                        {
                            doTeeLocal_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 10;
                            break;
                        }
                        case Instructions.TEE_LOCAL_RANGE_ :
                        {
                            doTeeLocalRange_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 10;
                            break;
                        }
                        case Instructions.INVOKE_ :
                        {
                            doInvoke_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.MATERIALIZE_FRAME_ :
                        {
                            doMaterializeFrame_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.CREATE_CLOSURE_ :
                        {
                            doCreateClosure_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.VOID_OPERATION_ :
                        {
                            doVoidOperation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.TO_BOOLEAN_ :
                        {
                            doToBoolean_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.GET_SOURCE_POSITION_ :
                        {
                            doGetSourcePosition_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                        {
                            doEnsureAndGetSourcePosition_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.GET_SOURCE_POSITIONS_ :
                        {
                            doGetSourcePositions_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.COPY_LOCALS_TO_FRAME_ :
                        {
                            doCopyLocalsToFrame_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.GET_BYTECODE_LOCATION_ :
                        {
                            doGetBytecodeLocation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                        {
                            doCollectBytecodeLocations_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                        {
                            doCollectSourceLocations_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                        {
                            doCollectAllSourceLocations_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.CONTINUE_ :
                        {
                            doContinue_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.CURRENT_LOCATION_ :
                        {
                            doCurrentLocation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.PRINT_HERE_ :
                        {
                            doPrintHere_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.INCREMENT_VALUE_ :
                        {
                            doIncrementValue_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.DOUBLE_VALUE_ :
                        {
                            doDoubleValue_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                        {
                            doEnableIncrementValueInstrumentation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.ADD_ :
                        {
                            doAdd_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.MOD_ :
                        {
                            doMod_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.LESS_ :
                        {
                            doLess_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp -= 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                        {
                            doEnableDoubleValueInstrumentation_(frame, localFrame, cachedNodes, bc, bci, sp);
                            bci += 6;
                            break;
                        }
                        case Instructions.EXPLICIT_BINDINGS_TEST_ :
                        {
                            doExplicitBindingsTest_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.IMPLICIT_BINDINGS_TEST_ :
                        {
                            doImplicitBindingsTest_(frame, localFrame, cachedNodes, bc, bci, sp);
                            sp += 1;
                            bci += 6;
                            break;
                        }
                        case Instructions.SC_AND_ :
                        {
                            if (profileBranch(branchProfiles, BYTES.getIntUnaligned(bc, bci + 6 /* imm branch_profile */), !(boolean) FRAMES.uncheckedGetObject(frame, sp - 1))) {
                                FRAMES.clear(frame, sp - 1);
                                sp -= 1;
                                bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                break;
                            } else {
                                FRAMES.clear(frame, sp - 1);
                                FRAMES.clear(frame, sp - 2);
                                sp -= 2;
                                bci += 10;
                                break;
                            }
                        }
                        case Instructions.SC_OR_ :
                        {
                            if (profileBranch(branchProfiles, BYTES.getIntUnaligned(bc, bci + 6 /* imm branch_profile */), (boolean) FRAMES.uncheckedGetObject(frame, sp - 1))) {
                                FRAMES.clear(frame, sp - 1);
                                sp -= 1;
                                bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                break;
                            } else {
                                FRAMES.clear(frame, sp - 1);
                                FRAMES.clear(frame, sp - 2);
                                sp -= 2;
                                bci += 10;
                                break;
                            }
                        }
                        case Instructions.INVALIDATE0 :
                        {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                        }
                        case Instructions.INVALIDATE1 :
                        {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                        }
                        case Instructions.INVALIDATE2 :
                        {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                        }
                        case Instructions.INVALIDATE3 :
                        {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                        }
                        case Instructions.INVALIDATE4 :
                        {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                        }
                    }
                } catch (Throwable throwable) {
                    if (throwable instanceof ControlFlowException) {
                        try {
                            temp = resolveControlFlowException($root, localFrame, bci, (ControlFlowException) throwable);
                            if (CompilerDirectives.hasNextTier() && loopCounter.value > 0) {
                                LoopNode.reportLoopCount(this, loopCounter.value);
                            }
                            return temp;
                        } catch (ControlFlowException rethrownCfe) {
                            throw rethrownCfe;
                        } catch (AbstractTruffleException t) {
                            throwable = t;
                        } catch (Throwable t) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throwable = t;
                        }
                    }
                    throwable = resolveThrowable($root, localFrame, bci, throwable);
                    op = -EXCEPTION_HANDLER_LENGTH;
                    while ((op = resolveHandler(bci, op + EXCEPTION_HANDLER_LENGTH, this.handlers)) != -1) {
                        try {
                            switch (this.handlers[op + EXCEPTION_HANDLER_OFFSET_KIND]) {
                                case HANDLER_TAG_EXCEPTIONAL :
                                    TagNode node = this.tagRoot.tagNodes[this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]];
                                    Object result = doTagExceptional(frame, node, this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI], bc, bci, throwable);
                                    if (result == null) {
                                        throw throwable;
                                    }
                                    temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals;
                                    if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                                        // Reenter by jumping to the begin bci.
                                        bci = node.enterBci;
                                    } else {
                                        switch (readValidBytecode(bc, node.returnBci)) {
                                            case Instructions.TAG_LEAVE :
                                                FRAMES.setObject(frame, (int)temp, result);
                                                temp = temp + 1;
                                                bci = node.returnBci + 6;
                                                break;
                                            case Instructions.TAG_LEAVE_VOID :
                                                bci = node.returnBci + 6;
                                                // discard return value
                                                break;
                                            default :
                                                throw CompilerDirectives.shouldNotReachHere();
                                        }
                                    }
                                    break;
                                default :
                                    if (throwable instanceof java.lang.ThreadDeath) {
                                        continue;
                                    }
                                    assert throwable instanceof AbstractTruffleException;
                                    bci = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                                    temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals;
                                    FRAMES.setObject(frame, ((int) temp) - 1, throwable);
                                    break;
                            }
                        } catch (Throwable t) {
                            if (t != throwable) {
                                throwable = resolveThrowable($root, localFrame, bci, t);
                            }
                            continue;
                        }
                        assert sp >= temp - 1;
                        while (sp > temp) {
                            FRAMES.clear(frame, --sp);
                        }
                        sp = (int) temp;
                        continue loop;
                    }
                    if (CompilerDirectives.hasNextTier() && loopCounter.value > 0) {
                        LoopNode.reportLoopCount(this, loopCounter.value);
                    }
                    $root.afterRootExecute(new InstructionImpl(this, bci, op), null, throwable);
                    throw sneakyThrow(throwable);
                }
            }
        }

        private long doBranchBackward(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this)) {
                int branchProfileIndex = BYTES.getIntUnaligned(bc, bci + 6 /* imm loop_header_branch_profile */);
                ensureFalseProfile(branchProfiles_, branchProfileIndex);
                Object osrResult = BytecodeOSRNode.tryOSR(this, ((frame != localFrame ? 1L : 0L) << 48) | ((sp & 0xFFFFL) << 32) | (BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */) & 0xFFFFFFFFL), null, null, frame);
                if (osrResult != null) {
                    return (long) osrResult;
                }
            }
            return -1;
        }

        private void doStoreLocal(Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            Object local = FRAMES.requireObject(stackFrame, sp - 1);
            FRAMES.setObject(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */), local);
            FRAMES.clear(stackFrame, sp - 1);
        }

        private void doLoadLocal(AbstractBytecodeNode $this, Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            FRAMES.setObject(stackFrame, sp, FRAMES.requireObject(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */)));
        }

        private void doLoadLocalMat(AbstractBytecodeNode $this, Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            int slot = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
            int localRootIndex = BYTES.getShort(bc, bci + 4 /* imm root_index */);
            BasicInterpreterWithUncached localRoot = this.getRoot().getBytecodeRootNodeImpl(localRootIndex);
            if (localRoot.getFrameDescriptor() != frame.getFrameDescriptor()) {
                throw CompilerDirectives.shouldNotReachHere("Materialized frame belongs to the wrong root node.");
            }
            FRAMES.setObject(stackFrame, sp - 1, FRAMES.requireObject(frame, slot));
        }

        private void doStoreLocalMat(Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            Object local = FRAMES.requireObject(stackFrame, sp - 1);
            int slot = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
            int localRootIndex = BYTES.getShort(bc, bci + 4 /* imm root_index */);
            BasicInterpreterWithUncached localRoot = this.getRoot().getBytecodeRootNodeImpl(localRootIndex);
            if (localRoot.getFrameDescriptor() != frame.getFrameDescriptor()) {
                throw CompilerDirectives.shouldNotReachHere("Materialized frame belongs to the wrong root node.");
            }
            FRAMES.setObject(frame, slot, local);
            FRAMES.clear(stackFrame, sp - 1);
            FRAMES.clear(stackFrame, sp - 2);
        }

        private void doYield(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp, BasicInterpreterWithUncached $root) {
            int maxLocals = $root.maxLocals;
            FRAMES.copyTo(frame, maxLocals, localFrame, maxLocals, (sp - 1 - maxLocals));
            ContinuationRootNodeImpl continuationRootNode = ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm location */)), ContinuationRootNodeImpl.class);
            ContinuationResult continuationResult = continuationRootNode.createContinuation(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1));
            FRAMES.setObject(frame, sp - 1, continuationResult);
        }

        @InliningCutoff
        private void doTagEnter(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onEnter(frame);
        }

        private void doTagLeave(AbstractBytecodeNode $this, VirtualFrame frame, byte[] bc, int bci, int sp) {
            Object returnValue;
            returnValue = FRAMES.requireObject(frame, sp - 1);
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onReturnValue(frame, returnValue);
        }

        @InliningCutoff
        private void doTagLeaveVoid(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onReturnValue(frame, null);
        }

        @InliningCutoff
        private void doTagYield(VirtualFrame frame, byte[] bc, int bci, int sp) {
            Object returnValue = FRAMES.requireObject(frame, sp - 1);
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onYield(frame, returnValue);
        }

        @InliningCutoff
        private void doTagResume(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onResume(frame);
        }

        private void doEarlyReturn_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            EarlyReturn_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), EarlyReturn_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.clear(frame, sp - 1);
        }

        private void doAddOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            AddOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), AddOperation_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doToString_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ToString_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ToString_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doCall_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Call_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)), Call_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAddConstantOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            AddConstantOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)), AddConstantOperation_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAddConstantOperationAtEnd_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            AddConstantOperationAtEnd_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)), AddConstantOperationAtEnd_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doVeryComplexOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            VeryComplexOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), VeryComplexOperation_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doThrowOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ThrowOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ThrowOperation_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doReadExceptionOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ReadExceptionOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ReadExceptionOperation_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAlwaysBoxOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            AlwaysBoxOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), AlwaysBoxOperation_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAppenderOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            AppenderOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), AppenderOperation_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.clear(frame, sp - 2);
            FRAMES.clear(frame, sp - 1);
        }

        private void doTeeLocal_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            TeeLocal_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)), TeeLocal_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doTeeLocalRange_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            TeeLocalRange_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 6 /* imm node */)), TeeLocalRange_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doInvoke_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Invoke_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), Invoke_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doMaterializeFrame_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            MaterializeFrame_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), MaterializeFrame_Node.class);
            MaterializedFrame result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCreateClosure_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CreateClosure_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CreateClosure_Node.class);
            TestClosure result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doVoidOperation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            VoidOperation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), VoidOperation_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
        }

        private void doToBoolean_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ToBoolean_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ToBoolean_Node.class);
            boolean result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetSourcePosition_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            GetSourcePosition_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), GetSourcePosition_Node.class);
            SourceSection result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doEnsureAndGetSourcePosition_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            EnsureAndGetSourcePosition_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), EnsureAndGetSourcePosition_Node.class);
            SourceSection result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetSourcePositions_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            GetSourcePositions_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), GetSourcePositions_Node.class);
            SourceSection[] result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCopyLocalsToFrame_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CopyLocalsToFrame_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CopyLocalsToFrame_Node.class);
            Frame result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetBytecodeLocation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            GetBytecodeLocation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), GetBytecodeLocation_Node.class);
            BytecodeLocation result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectBytecodeLocations_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CollectBytecodeLocations_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CollectBytecodeLocations_Node.class);
            List<BytecodeLocation> result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectSourceLocations_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CollectSourceLocations_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CollectSourceLocations_Node.class);
            List<SourceSection> result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectAllSourceLocations_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CollectAllSourceLocations_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CollectAllSourceLocations_Node.class);
            List<SourceSection[]> result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doContinue_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Continue_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), Continue_Node.class);
            Object result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doCurrentLocation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            CurrentLocation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), CurrentLocation_Node.class);
            BytecodeLocation result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doPrintHere_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            PrintHere_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), PrintHere_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
        }

        private void doIncrementValue_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            IncrementValue_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), IncrementValue_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doDoubleValue_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            DoubleValue_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), DoubleValue_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doEnableIncrementValueInstrumentation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            EnableIncrementValueInstrumentation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), EnableIncrementValueInstrumentation_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
        }

        private void doAdd_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Add_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), Add_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doMod_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Mod_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), Mod_Node.class);
            long result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doLess_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            Less_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), Less_Node.class);
            boolean result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doEnableDoubleValueInstrumentation_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            EnableDoubleValueInstrumentation_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), EnableDoubleValueInstrumentation_Node.class);
            node.execute(localFrame, frame, this, bc, bci, sp);
        }

        private void doExplicitBindingsTest_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ExplicitBindingsTest_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ExplicitBindingsTest_Node.class);
            Bindings result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doImplicitBindingsTest_(VirtualFrame frame, VirtualFrame localFrame, Node[] cachedNodes, byte[] bc, int bci, int sp) {
            ImplicitBindingsTest_Node node = ACCESS.uncheckedCast(ACCESS.readObject(cachedNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm node */)), ImplicitBindingsTest_Node.class);
            Bindings result = node.execute(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        @Override
        public void adoptNodesAfterUpdate() {
            insert(this.cachedNodes_);
        }

        @Override
        public Object executeOSR(VirtualFrame frame, long target, Object unused) {
            VirtualFrame localFrame;
            if ((target & (1L << 48)) != 0 /* use continuation frame */) {
                localFrame = (MaterializedFrame) frame.getObject(COROUTINE_FRAME_INDEX);
            } else {
                localFrame = frame;
            }
            return continueAt(getRoot(), frame, localFrame, (target & ~(1L << 48)));
        }

        @Override
        public void prepareOSR(long target) {
            // do nothing
        }

        @Override
        public void copyIntoOSRFrame(VirtualFrame osrFrame, VirtualFrame parentFrame, long target, Object targetMetadata) {
            transferOSRFrame(osrFrame, parentFrame, target, targetMetadata);
        }

        @Override
        public Object getOSRMetadata() {
            return osrMetadata_;
        }

        @Override
        public void setOSRMetadata(Object osrMetadata) {
            osrMetadata_ = osrMetadata;
        }

        @Override
        public Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
            Object[] parentArgs = parentFrame.getArguments();
            Object[] result = Arrays.copyOf(parentArgs, parentArgs.length + 1);
            result[result.length - 1] = parentFrame;
            return result;
        }

        @Override
        public Frame restoreParentFrameFromArguments(Object[] arguments) {
            return (Frame) arguments[arguments.length - 1];
        }

        @Override
        public void setUncachedThreshold(int threshold) {
        }

        @Override
        public BytecodeTier getTier() {
            return BytecodeTier.CACHED;
        }

        @InliningCutoff
        private Throwable resolveThrowable(BasicInterpreterWithUncached $root, VirtualFrame frame, int bci, Throwable throwable) {
            if (throwable instanceof AbstractTruffleException ate) {
                return ate;
            } else if (throwable instanceof ControlFlowException cfe) {
                throw cfe;
            } else if (throwable instanceof java.lang.ThreadDeath cfe) {
                return cfe;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw sneakyThrow(throwable);
            }
        }

        @ExplodeLoop
        private int resolveHandler(int bci, int handler, int[] localHandlers) {
            int handlerEntryIndex = Math.floorDiv(handler, EXCEPTION_HANDLER_LENGTH);
            for (int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH, handlerEntryIndex++) {
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci) {
                    continue;
                }
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci) {
                    continue;
                }
                if (!this.exceptionProfiles_[handlerEntryIndex]) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    this.exceptionProfiles_[handlerEntryIndex] = true;
                }
                return i;
            }
            return -1;
        }

        private Object doTagExceptional(VirtualFrame frame, TagNode node, int nodeId, byte[] bc, int bci, Throwable exception) throws Throwable {
            boolean wasOnReturnExecuted;
            switch (readValidBytecode(bc, bci)) {
                case Instructions.TAG_LEAVE :
                case Instructions.TAG_LEAVE_VOID :
                    wasOnReturnExecuted = BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */) == nodeId;
                    break;
                default :
                    wasOnReturnExecuted = false;
                    break;
            }
            return node.findProbe().onReturnExceptionalOrUnwind(frame, exception, wasOnReturnExecuted);
        }

        private long resolveControlFlowException(BasicInterpreterWithUncached $root, VirtualFrame frame, int bci, ControlFlowException cfe) throws Throwable {
            Object result = $root.interceptControlFlowException(cfe, frame, this, bci);
            FRAMES.setObject(frame, $root.maxLocals, result);
            int sp = $root.maxLocals + 1;
            return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
        }

        @Override
        AbstractBytecodeNode toCached() {
            return this;
        }

        @Override
        AbstractBytecodeNode update(byte[] bytecodes_, Object[] constants_, int[] handlers_, int[] locals_, int[] sourceInfo_, List<Source> sources_, int numNodes_, TagRootNode tagRoot_) {
            assert bytecodes_ != null || sourceInfo_ != null;
            byte[] bytecodes__;
            Object[] constants__;
            int[] handlers__;
            int[] locals__;
            int[] sourceInfo__;
            List<Source> sources__;
            int numNodes__;
            TagRootNode tagRoot__;
            if (bytecodes_ != null) {
                bytecodes__ = bytecodes_;
                constants__ = constants_;
                handlers__ = handlers_;
                numNodes__ = numNodes_;
                locals__ = locals_;
                tagRoot__ = tagRoot_;
            } else {
                bytecodes__ = this.bytecodes;
                constants__ = this.constants;
                handlers__ = this.handlers;
                numNodes__ = this.numNodes;
                locals__ = this.locals;
                tagRoot__ = this.tagRoot;
            }
            if (sourceInfo_ != null) {
                sourceInfo__ = sourceInfo_;
                sources__ = sources_;
            } else {
                sourceInfo__ = this.sourceInfo;
                sources__ = this.sources;
            }
            if (bytecodes_ != null) {
                // Can't reuse profile if bytecodes are changed.
                return new CachedBytecodeNode(bytecodes__, constants__, handlers__, locals__, sourceInfo__, sources__, numNodes__, tagRoot__);
            } else {
                // Can reuse profile if bytecodes are unchanged.
                return new CachedBytecodeNode(bytecodes__, constants__, handlers__, locals__, sourceInfo__, sources__, numNodes__, tagRoot__, this.cachedNodes_, this.exceptionProfiles_, this.branchProfiles_, this.osrMetadata_);
            }
        }

        @Override
        AbstractBytecodeNode cloneUninitialized() {
            return new CachedBytecodeNode(unquickenBytecode(this.bytecodes), this.constants, this.handlers, this.locals, this.sourceInfo, this.sources, this.numNodes, tagRoot != null ? (TagRootNode) tagRoot.deepCopy() : null);
        }

        @Override
        Node[] getCachedNodes() {
            return this.cachedNodes_;
        }

        @Override
        int[] getBranchProfiles() {
            return this.branchProfiles_;
        }

        @Override
        @TruffleBoundary
        protected int findBytecodeIndex(FrameInstance frameInstance) {
            Node prev = null;
            for (Node current = frameInstance.getCallNode(); current != null; current = current.getParent()) {
                if (current == this && prev != null) {
                    return findBytecodeIndexOfOperationNode(prev);
                }
                prev = current;
            }
            return -1;
        }

        @Override
        protected int findBytecodeIndex(Frame frame, Node node) {
            if (node != null) {
                return findBytecodeIndexOfOperationNode(node);
            }
            return -1;
        }

        @TruffleBoundary
        int findBytecodeIndexOfOperationNode(Node operationNode) {
            assert operationNode.getParent() == this : "Passed node must be an operation node of the same bytecode node.";
            Node[] localNodes = this.cachedNodes_;
            byte[] bc = this.bytecodes;
            int bci = 0;
            loop: while (bci < bc.length) {
                int currentBci = bci;
                int nodeIndex;
                switch (BYTES.getShort(bc, bci)) {
                    case Instructions.POP :
                    case Instructions.DUP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.CONSTANT_NULL :
                    case Instructions.INVALIDATE0 :
                    {
                        bci += 2;
                        continue loop;
                    }
                    case Instructions.BRANCH :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.YIELD :
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_YIELD :
                    case Instructions.TAG_RESUME :
                    case Instructions.INVALIDATE2 :
                    {
                        bci += 6;
                        continue loop;
                    }
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.BRANCH_FALSE :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                    case Instructions.INVALIDATE4 :
                    {
                        bci += 10;
                        continue loop;
                    }
                    case Instructions.STORE_LOCAL :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                    {
                        bci += 4;
                        continue loop;
                    }
                    case Instructions.INVALIDATE3 :
                    {
                        bci += 8;
                        continue loop;
                    }
                    case Instructions.EARLY_RETURN_ :
                    case Instructions.ADD_OPERATION_ :
                    case Instructions.TO_STRING_ :
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                    case Instructions.THROW_OPERATION_ :
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                    case Instructions.APPENDER_OPERATION_ :
                    case Instructions.INVOKE_ :
                    case Instructions.MATERIALIZE_FRAME_ :
                    case Instructions.CREATE_CLOSURE_ :
                    case Instructions.VOID_OPERATION_ :
                    case Instructions.TO_BOOLEAN_ :
                    case Instructions.GET_SOURCE_POSITION_ :
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    case Instructions.GET_SOURCE_POSITIONS_ :
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                    case Instructions.GET_BYTECODE_LOCATION_ :
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    case Instructions.CONTINUE_ :
                    case Instructions.CURRENT_LOCATION_ :
                    case Instructions.PRINT_HERE_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.DOUBLE_VALUE_ :
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    case Instructions.ADD_ :
                    case Instructions.MOD_ :
                    case Instructions.LESS_ :
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    {
                        nodeIndex = BYTES.getIntUnaligned(bc, bci + 2 /* imm node */);
                        bci += 6;
                        break;
                    }
                    case Instructions.CALL_ :
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    case Instructions.TEE_LOCAL_ :
                    case Instructions.TEE_LOCAL_RANGE_ :
                    {
                        nodeIndex = BYTES.getIntUnaligned(bc, bci + 6 /* imm node */);
                        bci += 10;
                        break;
                    }
                    default :
                    {
                        throw assertionFailed("Should not reach here");
                    }
                }
                if (localNodes[nodeIndex] == operationNode) {
                    return currentBci;
                }
            }
            return -1;
        }

        @Override
        public String toString() {
            return String.format("BytecodeNode [name=%s, sources=%s, tier=cached]", ((RootNode) getParent()).getQualifiedName(), this.sourceInfo != null);
        }

        private static void doPop(AbstractBytecodeNode $this, Frame frame, byte[] bc, int bci, int sp) {
            FRAMES.clear(frame, sp - 1);
        }

        private static void loadConstantCompiled(VirtualFrame frame, byte[] bc, int bci, int sp, Object[] constants) {
            Object constant = ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm constant */));
            if (constant instanceof Boolean b) {
                FRAMES.setObject(frame, sp, b.booleanValue());
                return;
            } else if (constant instanceof Byte b) {
                FRAMES.setObject(frame, sp, b.byteValue());
                return;
            } else if (constant instanceof Character c) {
                FRAMES.setObject(frame, sp, c.charValue());
                return;
            } else if (constant instanceof Float f) {
                FRAMES.setObject(frame, sp, f.floatValue());
                return;
            } else if (constant instanceof Integer i) {
                FRAMES.setObject(frame, sp, i.intValue());
                return;
            } else if (constant instanceof Long l) {
                FRAMES.setObject(frame, sp, l.longValue());
                return;
            } else if (constant instanceof Short s) {
                FRAMES.setObject(frame, sp, s.shortValue());
                return;
            } else if (constant instanceof Double d) {
                FRAMES.setObject(frame, sp, d.doubleValue());
                return;
            }
            FRAMES.setObject(frame, sp, constant);
        }

        private static int[] allocateBranchProfiles(int numProfiles) {
            // Encoding: [t1, f1, t2, f2, ..., tn, fn]
            return new int[numProfiles * 2];
        }

        private static boolean profileBranch(int[] branchProfiles, int profileIndex, boolean condition) {
            int t = ACCESS.readInt(branchProfiles, profileIndex * 2);
            int f = ACCESS.readInt(branchProfiles, profileIndex * 2 + 1);
            boolean val = condition;
            if (val) {
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (f == 0) {
                    // Make this branch fold during PE
                    val = true;
                }
                if (CompilerDirectives.inInterpreter()) {
                    if (t < Integer.MAX_VALUE) {
                        ACCESS.writeInt(branchProfiles, profileIndex * 2, t + 1);
                    }
                }
            } else {
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (t == 0) {
                    // Make this branch fold during PE
                    val = false;
                }
                if (CompilerDirectives.inInterpreter()) {
                    if (f < Integer.MAX_VALUE) {
                        ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, f + 1);
                    }
                }
            }
            if (CompilerDirectives.inInterpreter()) {
                // no branch probability calculation in the interpreter
                return val;
            } else {
                int sum = t + f;
                return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
            }
        }

        private static void ensureFalseProfile(int[] branchProfiles, int profileIndex) {
            if (ACCESS.readInt(branchProfiles, profileIndex * 2 + 1) == 0) {
                ACCESS.writeInt(branchProfiles, profileIndex * 2 + 1, 1);
            }
        }

        private static byte[] unquickenBytecode(byte[] original) {
            byte[] copy = Arrays.copyOf(original, original.length);
            int bci = 0;
            while (bci < copy.length) {
                switch (BYTES.getShort(copy, bci)) {
                    case Instructions.CONSTANT_NULL :
                    case Instructions.DUP :
                    case Instructions.INVALIDATE0 :
                    case Instructions.LOAD_NULL :
                    case Instructions.LOAD_VARIADIC_0 :
                    case Instructions.LOAD_VARIADIC_1 :
                    case Instructions.LOAD_VARIADIC_2 :
                    case Instructions.LOAD_VARIADIC_3 :
                    case Instructions.LOAD_VARIADIC_4 :
                    case Instructions.LOAD_VARIADIC_5 :
                    case Instructions.LOAD_VARIADIC_6 :
                    case Instructions.LOAD_VARIADIC_7 :
                    case Instructions.LOAD_VARIADIC_8 :
                    case Instructions.MERGE_VARIADIC :
                    case Instructions.POP :
                    case Instructions.RETURN :
                    case Instructions.THROW :
                        bci += 2;
                        break;
                    case Instructions.CLEAR_LOCAL :
                    case Instructions.INVALIDATE1 :
                    case Instructions.LOAD_ARGUMENT :
                    case Instructions.LOAD_EXCEPTION :
                    case Instructions.LOAD_LOCAL :
                    case Instructions.STORE_LOCAL :
                        bci += 4;
                        break;
                    case Instructions.BRANCH :
                    case Instructions.ADD_ :
                    case Instructions.ADD_OPERATION_ :
                    case Instructions.ALWAYS_BOX_OPERATION_ :
                    case Instructions.APPENDER_OPERATION_ :
                    case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                    case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                    case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                    case Instructions.CONTINUE_ :
                    case Instructions.COPY_LOCALS_TO_FRAME_ :
                    case Instructions.CREATE_CLOSURE_ :
                    case Instructions.CURRENT_LOCATION_ :
                    case Instructions.DOUBLE_VALUE_ :
                    case Instructions.EARLY_RETURN_ :
                    case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                    case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                    case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                    case Instructions.EXPLICIT_BINDINGS_TEST_ :
                    case Instructions.GET_BYTECODE_LOCATION_ :
                    case Instructions.GET_SOURCE_POSITION_ :
                    case Instructions.GET_SOURCE_POSITIONS_ :
                    case Instructions.IMPLICIT_BINDINGS_TEST_ :
                    case Instructions.INCREMENT_VALUE_ :
                    case Instructions.INVOKE_ :
                    case Instructions.LESS_ :
                    case Instructions.MATERIALIZE_FRAME_ :
                    case Instructions.MOD_ :
                    case Instructions.PRINT_HERE_ :
                    case Instructions.READ_EXCEPTION_OPERATION_ :
                    case Instructions.THROW_OPERATION_ :
                    case Instructions.TO_BOOLEAN_ :
                    case Instructions.TO_STRING_ :
                    case Instructions.VERY_COMPLEX_OPERATION_ :
                    case Instructions.VOID_OPERATION_ :
                    case Instructions.INVALIDATE2 :
                    case Instructions.LOAD_CONSTANT :
                    case Instructions.LOAD_LOCAL_MAT :
                    case Instructions.STORE_LOCAL_MAT :
                    case Instructions.TAG_ENTER :
                    case Instructions.TAG_LEAVE :
                    case Instructions.TAG_LEAVE_VOID :
                    case Instructions.TAG_RESUME :
                    case Instructions.TAG_YIELD :
                    case Instructions.YIELD :
                        bci += 6;
                        break;
                    case Instructions.INVALIDATE3 :
                        bci += 8;
                        break;
                    case Instructions.BRANCH_BACKWARD :
                    case Instructions.BRANCH_FALSE :
                    case Instructions.ADD_CONSTANT_OPERATION_ :
                    case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                    case Instructions.CALL_ :
                    case Instructions.TEE_LOCAL_ :
                    case Instructions.TEE_LOCAL_RANGE_ :
                    case Instructions.INVALIDATE4 :
                    case Instructions.SC_AND_ :
                    case Instructions.SC_OR_ :
                        bci += 10;
                        break;
                }
            }
            return copy;
        }

    }
    @DenyReplace
    private static final class UncachedBytecodeNode extends AbstractBytecodeNode {

        private int uncachedExecuteCount_ = 16;

        UncachedBytecodeNode(byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot) {
            super(bytecodes, constants, handlers, locals, sourceInfo, sources, numNodes, tagRoot);
        }

        UncachedBytecodeNode(byte[] bytecodes, Object[] constants, int[] handlers, int[] locals, int[] sourceInfo, List<Source> sources, int numNodes, TagRootNode tagRoot, int uncachedExecuteCount_) {
            super(bytecodes, constants, handlers, locals, sourceInfo, sources, numNodes, tagRoot);
            this.uncachedExecuteCount_ = uncachedExecuteCount_;
        }

        @Override
        @BytecodeInterpreterSwitch
        long continueAt(BasicInterpreterWithUncached $root, VirtualFrame frame, VirtualFrame localFrame, long startState) {
            EncapsulatingNodeReference encapsulatingNode = EncapsulatingNodeReference.getCurrent();
            Node prev = encapsulatingNode.set(this);
            try {
                int uncachedExecuteCount = this.uncachedExecuteCount_;
                if (uncachedExecuteCount <= 0 && uncachedExecuteCount != Integer.MIN_VALUE) {
                    $root.transitionToCached(frame, 0);
                    return startState;
                }
                byte[] bc = this.bytecodes;
                int bci = (int) startState;
                int sp = (short) (startState >>> 32);
                int op;
                long temp;
                loop: while (true) {
                    CompilerAsserts.partialEvaluationConstant(bci);
                    op = BYTES.getShort(bc, bci);
                    CompilerAsserts.partialEvaluationConstant(op);
                    $root.beforeInstructionExecute(new InstructionImpl(this, bci, op));
                    try {
                        switch (op) {
                            case Instructions.POP :
                            {
                                doPop(this, frame, bc, bci, sp);
                                sp -= 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.DUP :
                            {
                                FRAMES.copy(frame, sp - 1, sp);
                                sp += 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.RETURN :
                            {
                                FRAMES.setInt(localFrame, BCI_INDEX, bci);
                                if (uncachedExecuteCount <= 1) {
                                    if (uncachedExecuteCount != Integer.MIN_VALUE) {
                                        CompilerDirectives.transferToInterpreterAndInvalidate();
                                        $root.transitionToCached(frame, bci);
                                    }
                                } else {
                                    uncachedExecuteCount--;
                                    this.uncachedExecuteCount_ = uncachedExecuteCount;
                                }
                                $root.afterRootExecute(new InstructionImpl(this, bci, op), FRAMES.getObject(frame, (sp - 1)), null);
                                return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
                            }
                            case Instructions.BRANCH :
                            {
                                bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                break;
                            }
                            case Instructions.BRANCH_BACKWARD :
                            {
                                bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                if (uncachedExecuteCount <= 1) {
                                    if (uncachedExecuteCount != Integer.MIN_VALUE) {
                                        CompilerDirectives.transferToInterpreterAndInvalidate();
                                        $root.transitionToCached(frame, bci);
                                        return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                                    }
                                } else {
                                    uncachedExecuteCount--;
                                }
                                break;
                            }
                            case Instructions.BRANCH_FALSE :
                            {
                                if ((boolean) FRAMES.uncheckedGetObject(frame, sp - 1)) {
                                    sp -= 1;
                                    bci += 10;
                                    break;
                                } else {
                                    sp -= 1;
                                    bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                    break;
                                }
                            }
                            case Instructions.STORE_LOCAL :
                            {
                                doStoreLocal(frame, localFrame, bc, bci, sp);
                                FRAMES.clear(frame, sp - 1);
                                sp -= 1;
                                bci += 4;
                                break;
                            }
                            case Instructions.THROW :
                            {
                                throw sneakyThrow((Throwable) FRAMES.uncheckedGetObject(frame, sp - 1));
                            }
                            case Instructions.LOAD_CONSTANT :
                            {
                                FRAMES.setObject(frame, sp, ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm constant */)));
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.LOAD_NULL :
                            {
                                FRAMES.setObject(frame, sp, null);
                                sp += 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_ARGUMENT :
                            {
                                FRAMES.setObject(frame, sp, localFrame.getArguments()[BYTES.getShort(bc, bci + 2 /* imm index */)]);
                                sp += 1;
                                bci += 4;
                                break;
                            }
                            case Instructions.LOAD_EXCEPTION :
                            {
                                FRAMES.setObject(frame, sp, FRAMES.getObject(frame, $root.maxLocals + BYTES.getShort(bc, bci + 2 /* imm exception_sp */)));
                                sp += 1;
                                bci += 4;
                                break;
                            }
                            case Instructions.LOAD_LOCAL :
                            {
                                doLoadLocal(this, frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 4;
                                break;
                            }
                            case Instructions.LOAD_LOCAL_MAT :
                            {
                                doLoadLocalMat(this, frame, ((VirtualFrame) FRAMES.uncheckedGetObject(frame, sp - 1)), bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.STORE_LOCAL_MAT :
                            {
                                doStoreLocalMat(frame, ((VirtualFrame) FRAMES.uncheckedGetObject(frame, sp - 2)), bc, bci, sp);
                                sp -= 2;
                                bci += 6;
                                break;
                            }
                            case Instructions.YIELD :
                            {
                                FRAMES.setInt(localFrame, BCI_INDEX, bci);
                                if (uncachedExecuteCount <= 1) {
                                    if (uncachedExecuteCount != Integer.MIN_VALUE) {
                                        CompilerDirectives.transferToInterpreterAndInvalidate();
                                        $root.transitionToCached(frame, bci);
                                    }
                                } else {
                                    uncachedExecuteCount--;
                                    this.uncachedExecuteCount_ = uncachedExecuteCount;
                                }
                                $root.afterRootExecute(new InstructionImpl(this, bci, op), FRAMES.getObject(frame, (sp - 1)), null);
                                doYield(frame, localFrame, bc, bci, sp, $root);
                                return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
                            }
                            case Instructions.TAG_ENTER :
                            {
                                doTagEnter(frame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.TAG_LEAVE :
                            {
                                doTagLeave(this, frame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.TAG_LEAVE_VOID :
                            {
                                doTagLeaveVoid(frame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.TAG_YIELD :
                            {
                                doTagYield(frame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.TAG_RESUME :
                            {
                                doTagResume(frame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_0 :
                            {
                                FRAMES.setObject(frame, sp, EMPTY_ARRAY);
                                sp += 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_1 :
                            {
                                FRAMES.setObject(frame, sp - 1, readVariadic(frame, sp, 1));
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_2 :
                            {
                                FRAMES.setObject(frame, sp - 2, readVariadic(frame, sp, 2));
                                sp -= 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_3 :
                            {
                                FRAMES.setObject(frame, sp - 3, readVariadic(frame, sp, 3));
                                sp -= 2;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_4 :
                            {
                                FRAMES.setObject(frame, sp - 4, readVariadic(frame, sp, 4));
                                sp -= 3;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_5 :
                            {
                                FRAMES.setObject(frame, sp - 5, readVariadic(frame, sp, 5));
                                sp -= 4;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_6 :
                            {
                                FRAMES.setObject(frame, sp - 6, readVariadic(frame, sp, 6));
                                sp -= 5;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_7 :
                            {
                                FRAMES.setObject(frame, sp - 7, readVariadic(frame, sp, 7));
                                sp -= 6;
                                bci += 2;
                                break;
                            }
                            case Instructions.LOAD_VARIADIC_8 :
                            {
                                FRAMES.setObject(frame, sp - 8, readVariadic(frame, sp, 8));
                                sp -= 7;
                                bci += 2;
                                break;
                            }
                            case Instructions.MERGE_VARIADIC :
                            {
                                FRAMES.setObject(frame, sp - 1, mergeVariadic((Object[]) FRAMES.uncheckedGetObject(frame, sp - 1)));
                                bci += 2;
                                break;
                            }
                            case Instructions.CONSTANT_NULL :
                            {
                                FRAMES.setObject(frame, sp, null);
                                sp += 1;
                                bci += 2;
                                break;
                            }
                            case Instructions.CLEAR_LOCAL :
                            {
                                FRAMES.clear(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */));
                                bci += 4;
                                break;
                            }
                            case Instructions.EARLY_RETURN_ :
                            {
                                doEarlyReturn_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.ADD_OPERATION_ :
                            {
                                doAddOperation_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.TO_STRING_ :
                            {
                                doToString_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.CALL_ :
                            {
                                doCall_(frame, localFrame, bc, bci, sp);
                                bci += 10;
                                break;
                            }
                            case Instructions.ADD_CONSTANT_OPERATION_ :
                            {
                                doAddConstantOperation_(frame, localFrame, bc, bci, sp);
                                bci += 10;
                                break;
                            }
                            case Instructions.ADD_CONSTANT_OPERATION_AT_END_ :
                            {
                                doAddConstantOperationAtEnd_(frame, localFrame, bc, bci, sp);
                                bci += 10;
                                break;
                            }
                            case Instructions.VERY_COMPLEX_OPERATION_ :
                            {
                                doVeryComplexOperation_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.THROW_OPERATION_ :
                            {
                                doThrowOperation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.READ_EXCEPTION_OPERATION_ :
                            {
                                doReadExceptionOperation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.ALWAYS_BOX_OPERATION_ :
                            {
                                doAlwaysBoxOperation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.APPENDER_OPERATION_ :
                            {
                                doAppenderOperation_(frame, localFrame, bc, bci, sp);
                                sp -= 2;
                                bci += 6;
                                break;
                            }
                            case Instructions.TEE_LOCAL_ :
                            {
                                doTeeLocal_(frame, localFrame, bc, bci, sp);
                                bci += 10;
                                break;
                            }
                            case Instructions.TEE_LOCAL_RANGE_ :
                            {
                                doTeeLocalRange_(frame, localFrame, bc, bci, sp);
                                bci += 10;
                                break;
                            }
                            case Instructions.INVOKE_ :
                            {
                                doInvoke_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.MATERIALIZE_FRAME_ :
                            {
                                doMaterializeFrame_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.CREATE_CLOSURE_ :
                            {
                                doCreateClosure_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.VOID_OPERATION_ :
                            {
                                doVoidOperation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.TO_BOOLEAN_ :
                            {
                                doToBoolean_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.GET_SOURCE_POSITION_ :
                            {
                                doGetSourcePosition_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.ENSURE_AND_GET_SOURCE_POSITION_ :
                            {
                                doEnsureAndGetSourcePosition_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.GET_SOURCE_POSITIONS_ :
                            {
                                doGetSourcePositions_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.COPY_LOCALS_TO_FRAME_ :
                            {
                                doCopyLocalsToFrame_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.GET_BYTECODE_LOCATION_ :
                            {
                                doGetBytecodeLocation_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.COLLECT_BYTECODE_LOCATIONS_ :
                            {
                                doCollectBytecodeLocations_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.COLLECT_SOURCE_LOCATIONS_ :
                            {
                                doCollectSourceLocations_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.COLLECT_ALL_SOURCE_LOCATIONS_ :
                            {
                                doCollectAllSourceLocations_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.CONTINUE_ :
                            {
                                doContinue_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.CURRENT_LOCATION_ :
                            {
                                doCurrentLocation_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.PRINT_HERE_ :
                            {
                                doPrintHere_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.INCREMENT_VALUE_ :
                            {
                                doIncrementValue_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.DOUBLE_VALUE_ :
                            {
                                doDoubleValue_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ :
                            {
                                doEnableIncrementValueInstrumentation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.ADD_ :
                            {
                                doAdd_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.MOD_ :
                            {
                                doMod_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.LESS_ :
                            {
                                doLess_(frame, localFrame, bc, bci, sp);
                                sp -= 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ :
                            {
                                doEnableDoubleValueInstrumentation_(frame, localFrame, bc, bci, sp);
                                bci += 6;
                                break;
                            }
                            case Instructions.EXPLICIT_BINDINGS_TEST_ :
                            {
                                doExplicitBindingsTest_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.IMPLICIT_BINDINGS_TEST_ :
                            {
                                doImplicitBindingsTest_(frame, localFrame, bc, bci, sp);
                                sp += 1;
                                bci += 6;
                                break;
                            }
                            case Instructions.SC_AND_ :
                            {
                                if (!(boolean) FRAMES.uncheckedGetObject(frame, sp - 1)) {
                                    FRAMES.clear(frame, sp - 1);
                                    sp -= 1;
                                    bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                    break;
                                } else {
                                    FRAMES.clear(frame, sp - 1);
                                    FRAMES.clear(frame, sp - 2);
                                    sp -= 2;
                                    bci += 10;
                                    break;
                                }
                            }
                            case Instructions.SC_OR_ :
                            {
                                if ((boolean) FRAMES.uncheckedGetObject(frame, sp - 1)) {
                                    FRAMES.clear(frame, sp - 1);
                                    sp -= 1;
                                    bci = BYTES.getIntUnaligned(bc, bci + 2 /* imm branch_target */);
                                    break;
                                } else {
                                    FRAMES.clear(frame, sp - 1);
                                    FRAMES.clear(frame, sp - 2);
                                    sp -= 2;
                                    bci += 10;
                                    break;
                                }
                            }
                            case Instructions.INVALIDATE0 :
                            {
                                return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                            }
                            case Instructions.INVALIDATE1 :
                            {
                                return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                            }
                            case Instructions.INVALIDATE2 :
                            {
                                return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                            }
                            case Instructions.INVALIDATE3 :
                            {
                                return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                            }
                            case Instructions.INVALIDATE4 :
                            {
                                return ((sp & 0xFFFFL) << 32) | (bci & 0xFFFFFFFFL);
                            }
                        }
                    } catch (Throwable throwable) {
                        FRAMES.setInt(localFrame, BCI_INDEX, bci);
                        if (throwable instanceof ControlFlowException) {
                            try {
                                temp = resolveControlFlowException($root, localFrame, bci, (ControlFlowException) throwable);
                                if (uncachedExecuteCount <= 1) {
                                    if (uncachedExecuteCount != Integer.MIN_VALUE) {
                                        CompilerDirectives.transferToInterpreterAndInvalidate();
                                        $root.transitionToCached(frame, bci);
                                    }
                                } else {
                                    uncachedExecuteCount--;
                                    this.uncachedExecuteCount_ = uncachedExecuteCount;
                                }
                                return temp;
                            } catch (ControlFlowException rethrownCfe) {
                                throw rethrownCfe;
                            } catch (AbstractTruffleException t) {
                                throwable = t;
                            } catch (Throwable t) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throwable = t;
                            }
                        }
                        throwable = resolveThrowable($root, localFrame, bci, throwable);
                        op = -EXCEPTION_HANDLER_LENGTH;
                        while ((op = resolveHandler(bci, op + EXCEPTION_HANDLER_LENGTH, this.handlers)) != -1) {
                            try {
                                switch (this.handlers[op + EXCEPTION_HANDLER_OFFSET_KIND]) {
                                    case HANDLER_TAG_EXCEPTIONAL :
                                        TagNode node = this.tagRoot.tagNodes[this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]];
                                        Object result = doTagExceptional(frame, node, this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI], bc, bci, throwable);
                                        if (result == null) {
                                            throw throwable;
                                        }
                                        temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals;
                                        if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                                            // Reenter by jumping to the begin bci.
                                            bci = node.enterBci;
                                        } else {
                                            switch (readValidBytecode(bc, node.returnBci)) {
                                                case Instructions.TAG_LEAVE :
                                                    FRAMES.setObject(frame, (int)temp, result);
                                                    temp = temp + 1;
                                                    bci = node.returnBci + 6;
                                                    break;
                                                case Instructions.TAG_LEAVE_VOID :
                                                    bci = node.returnBci + 6;
                                                    // discard return value
                                                    break;
                                                default :
                                                    throw CompilerDirectives.shouldNotReachHere();
                                            }
                                        }
                                        break;
                                    default :
                                        if (throwable instanceof java.lang.ThreadDeath) {
                                            continue;
                                        }
                                        assert throwable instanceof AbstractTruffleException;
                                        bci = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                                        temp = this.handlers[op + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] + $root.maxLocals;
                                        FRAMES.setObject(frame, ((int) temp) - 1, throwable);
                                        break;
                                }
                            } catch (Throwable t) {
                                if (t != throwable) {
                                    throwable = resolveThrowable($root, localFrame, bci, t);
                                }
                                continue;
                            }
                            assert sp >= temp - 1;
                            while (sp > temp) {
                                FRAMES.clear(frame, --sp);
                            }
                            sp = (int) temp;
                            continue loop;
                        }
                        if (uncachedExecuteCount <= 1) {
                            if (uncachedExecuteCount != Integer.MIN_VALUE) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                $root.transitionToCached(frame, bci);
                            }
                        } else {
                            uncachedExecuteCount--;
                            this.uncachedExecuteCount_ = uncachedExecuteCount;
                        }
                        $root.afterRootExecute(new InstructionImpl(this, bci, op), null, throwable);
                        throw sneakyThrow(throwable);
                    }
                }
            } finally {
                encapsulatingNode.set(prev);
            }
        }

        private void doStoreLocal(Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            Object local = FRAMES.requireObject(stackFrame, sp - 1);
            FRAMES.setObject(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */), local);
            FRAMES.clear(stackFrame, sp - 1);
        }

        private void doLoadLocal(AbstractBytecodeNode $this, Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            FRAMES.setObject(stackFrame, sp, FRAMES.requireObject(frame, BYTES.getShort(bc, bci + 2 /* imm local_offset */)));
        }

        private void doLoadLocalMat(AbstractBytecodeNode $this, Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            int slot = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
            int localRootIndex = BYTES.getShort(bc, bci + 4 /* imm root_index */);
            BasicInterpreterWithUncached localRoot = this.getRoot().getBytecodeRootNodeImpl(localRootIndex);
            if (localRoot.getFrameDescriptor() != frame.getFrameDescriptor()) {
                throw CompilerDirectives.shouldNotReachHere("Materialized frame belongs to the wrong root node.");
            }
            FRAMES.setObject(stackFrame, sp - 1, FRAMES.requireObject(frame, slot));
        }

        private void doStoreLocalMat(Frame stackFrame, Frame frame, byte[] bc, int bci, int sp) {
            Object local = FRAMES.requireObject(stackFrame, sp - 1);
            int slot = BYTES.getShort(bc, bci + 2 /* imm local_offset */);
            int localRootIndex = BYTES.getShort(bc, bci + 4 /* imm root_index */);
            BasicInterpreterWithUncached localRoot = this.getRoot().getBytecodeRootNodeImpl(localRootIndex);
            if (localRoot.getFrameDescriptor() != frame.getFrameDescriptor()) {
                throw CompilerDirectives.shouldNotReachHere("Materialized frame belongs to the wrong root node.");
            }
            FRAMES.setObject(frame, slot, local);
            FRAMES.clear(stackFrame, sp - 1);
            FRAMES.clear(stackFrame, sp - 2);
        }

        private void doYield(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp, BasicInterpreterWithUncached $root) {
            int maxLocals = $root.maxLocals;
            FRAMES.copyTo(frame, maxLocals, localFrame, maxLocals, (sp - 1 - maxLocals));
            ContinuationRootNodeImpl continuationRootNode = ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm location */)), ContinuationRootNodeImpl.class);
            ContinuationResult continuationResult = continuationRootNode.createContinuation(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1));
            FRAMES.setObject(frame, sp - 1, continuationResult);
        }

        @InliningCutoff
        private void doTagEnter(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onEnter(frame);
        }

        private void doTagLeave(AbstractBytecodeNode $this, VirtualFrame frame, byte[] bc, int bci, int sp) {
            Object returnValue;
            returnValue = FRAMES.requireObject(frame, sp - 1);
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onReturnValue(frame, returnValue);
        }

        @InliningCutoff
        private void doTagLeaveVoid(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onReturnValue(frame, null);
        }

        @InliningCutoff
        private void doTagYield(VirtualFrame frame, byte[] bc, int bci, int sp) {
            Object returnValue = FRAMES.requireObject(frame, sp - 1);
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onYield(frame, returnValue);
        }

        @InliningCutoff
        private void doTagResume(VirtualFrame frame, byte[] bc, int bci, int sp) {
            TagNode tagNode = ACCESS.uncheckedCast(ACCESS.readObject(tagRoot.tagNodes, BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */)), TagNode.class);
            tagNode.findProbe().onResume(frame);
        }

        private void doEarlyReturn_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            EarlyReturn_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.clear(frame, sp - 1);
        }

        private void doAddOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            Object result = AddOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doToString_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = ToString_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doCall_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = Call_Node.UNCACHED.executeUncached(localFrame, ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm interpreter */)), BasicInterpreter.class), (Object[]) FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAddConstantOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            Object result = AddConstantOperation_Node.UNCACHED.executeUncached(localFrame, ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm constantLhs */)), Long.class), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAddConstantOperationAtEnd_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            Object result = AddConstantOperationAtEnd_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), (long) ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm constantRhs */)), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doVeryComplexOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = VeryComplexOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), (Object[]) FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doThrowOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = ThrowOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doReadExceptionOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = ReadExceptionOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAlwaysBoxOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            Object result = AlwaysBoxOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doAppenderOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            AppenderOperation_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.clear(frame, sp - 2);
            FRAMES.clear(frame, sp - 1);
        }

        private void doTeeLocal_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = TeeLocal_Node.UNCACHED.executeUncached(localFrame, ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm setter */)), LocalAccessor.class), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doTeeLocalRange_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = TeeLocalRange_Node.UNCACHED.executeUncached(localFrame, ACCESS.uncheckedCast(ACCESS.readObject(constants, BYTES.getIntUnaligned(bc, bci + 2 /* imm setter */)), LocalRangeAccessor.class), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doInvoke_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = Invoke_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), (Object[]) FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doMaterializeFrame_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            MaterializedFrame result = MaterializeFrame_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCreateClosure_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            TestClosure result = CreateClosure_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doVoidOperation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            VoidOperation_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
        }

        private void doToBoolean_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            boolean result = ToBoolean_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetSourcePosition_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            SourceSection result = GetSourcePosition_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doEnsureAndGetSourcePosition_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            SourceSection result = EnsureAndGetSourcePosition_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetSourcePositions_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            SourceSection[] result = GetSourcePositions_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCopyLocalsToFrame_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Frame result = CopyLocalsToFrame_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doGetBytecodeLocation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            BytecodeLocation result = GetBytecodeLocation_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectBytecodeLocations_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            List<BytecodeLocation> result = CollectBytecodeLocations_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectSourceLocations_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            List<SourceSection> result = CollectSourceLocations_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doCollectAllSourceLocations_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            List<SourceSection[]> result = CollectAllSourceLocations_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doContinue_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Object result = Continue_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doCurrentLocation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            BytecodeLocation result = CurrentLocation_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doPrintHere_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            PrintHere_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
        }

        private void doIncrementValue_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = IncrementValue_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doDoubleValue_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = DoubleValue_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 1, result);
        }

        private void doEnableIncrementValueInstrumentation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            EnableIncrementValueInstrumentation_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
        }

        private void doAdd_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = Add_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doMod_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            long result = Mod_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doLess_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            boolean result = Less_Node.UNCACHED.executeUncached(localFrame, FRAMES.uncheckedGetObject(frame, sp - 2), FRAMES.uncheckedGetObject(frame, sp - 1), frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp - 2, result);
            FRAMES.clear(frame, sp - 1);
        }

        private void doEnableDoubleValueInstrumentation_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            EnableDoubleValueInstrumentation_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
        }

        private void doExplicitBindingsTest_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Bindings result = ExplicitBindingsTest_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        private void doImplicitBindingsTest_(VirtualFrame frame, VirtualFrame localFrame, byte[] bc, int bci, int sp) {
            FRAMES.setInt(localFrame, BCI_INDEX, bci);
            Bindings result = ImplicitBindingsTest_Node.UNCACHED.executeUncached(localFrame, frame, this, bc, bci, sp);
            FRAMES.setObject(frame, sp, result);
        }

        @Override
        public void setUncachedThreshold(int threshold) {
            CompilerAsserts.neverPartOfCompilation();
            if (threshold < 0 && threshold != Integer.MIN_VALUE) {
                throw new IllegalArgumentException("threshold cannot be a negative value other than Integer.MIN_VALUE");
            }
            uncachedExecuteCount_ = threshold;
        }

        @Override
        public BytecodeTier getTier() {
            return BytecodeTier.UNCACHED;
        }

        @InliningCutoff
        private Throwable resolveThrowable(BasicInterpreterWithUncached $root, VirtualFrame frame, int bci, Throwable throwable) {
            if (throwable instanceof AbstractTruffleException ate) {
                return ate;
            } else if (throwable instanceof ControlFlowException cfe) {
                throw cfe;
            } else if (throwable instanceof java.lang.ThreadDeath cfe) {
                return cfe;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw sneakyThrow(throwable);
            }
        }

        private Object doTagExceptional(VirtualFrame frame, TagNode node, int nodeId, byte[] bc, int bci, Throwable exception) throws Throwable {
            boolean wasOnReturnExecuted;
            switch (readValidBytecode(bc, bci)) {
                case Instructions.TAG_LEAVE :
                case Instructions.TAG_LEAVE_VOID :
                    wasOnReturnExecuted = BYTES.getIntUnaligned(bc, bci + 2 /* imm tag */) == nodeId;
                    break;
                default :
                    wasOnReturnExecuted = false;
                    break;
            }
            return node.findProbe().onReturnExceptionalOrUnwind(frame, exception, wasOnReturnExecuted);
        }

        private long resolveControlFlowException(BasicInterpreterWithUncached $root, VirtualFrame frame, int bci, ControlFlowException cfe) throws Throwable {
            Object result = $root.interceptControlFlowException(cfe, frame, this, bci);
            FRAMES.setObject(frame, $root.maxLocals, result);
            int sp = $root.maxLocals + 1;
            return (((sp - 1) & 0xFFFFL) << 32) | 0xFFFFFFFFL;
        }

        @Override
        AbstractBytecodeNode toCached() {
            return new CachedBytecodeNode(this.bytecodes, this.constants, this.handlers, this.locals, this.sourceInfo, this.sources, this.numNodes, this.tagRoot);
        }

        @Override
        AbstractBytecodeNode update(byte[] bytecodes_, Object[] constants_, int[] handlers_, int[] locals_, int[] sourceInfo_, List<Source> sources_, int numNodes_, TagRootNode tagRoot_) {
            assert bytecodes_ != null || sourceInfo_ != null;
            byte[] bytecodes__;
            Object[] constants__;
            int[] handlers__;
            int[] locals__;
            int[] sourceInfo__;
            List<Source> sources__;
            int numNodes__;
            TagRootNode tagRoot__;
            if (bytecodes_ != null) {
                bytecodes__ = bytecodes_;
                constants__ = constants_;
                handlers__ = handlers_;
                numNodes__ = numNodes_;
                locals__ = locals_;
                tagRoot__ = tagRoot_;
            } else {
                bytecodes__ = this.bytecodes;
                constants__ = this.constants;
                handlers__ = this.handlers;
                numNodes__ = this.numNodes;
                locals__ = this.locals;
                tagRoot__ = this.tagRoot;
            }
            if (sourceInfo_ != null) {
                sourceInfo__ = sourceInfo_;
                sources__ = sources_;
            } else {
                sourceInfo__ = this.sourceInfo;
                sources__ = this.sources;
            }
            return new UncachedBytecodeNode(bytecodes__, constants__, handlers__, locals__, sourceInfo__, sources__, numNodes__, tagRoot__, this.uncachedExecuteCount_);
        }

        @Override
        AbstractBytecodeNode cloneUninitialized() {
            return new UncachedBytecodeNode(Arrays.copyOf(this.bytecodes, this.bytecodes.length), this.constants, this.handlers, this.locals, this.sourceInfo, this.sources, this.numNodes, tagRoot != null ? (TagRootNode) tagRoot.deepCopy() : null);
        }

        @Override
        Node[] getCachedNodes() {
            return null;
        }

        @Override
        int[] getBranchProfiles() {
            return null;
        }

        @Override
        @TruffleBoundary
        protected int findBytecodeIndex(FrameInstance frameInstance) {
            Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
            if (frame.isObject(COROUTINE_FRAME_INDEX)) {
                frame = (Frame) frame.getObject(COROUTINE_FRAME_INDEX);
            }
            return frame.getInt(BCI_INDEX);
        }

        @Override
        protected int findBytecodeIndex(Frame frame, Node node) {
            return frame.getInt(BCI_INDEX);
        }

        int findBytecodeIndexOfOperationNode(Node operationNode) {
            return -1;
        }

        @Override
        public String toString() {
            return String.format("BytecodeNode [name=%s, sources=%s, tier=uncached]", ((RootNode) getParent()).getQualifiedName(), this.sourceInfo != null);
        }

        private static void doPop(AbstractBytecodeNode $this, Frame frame, byte[] bc, int bci, int sp) {
            FRAMES.clear(frame, sp - 1);
        }

        @ExplodeLoop
        private static int resolveHandler(int bci, int handler, int[] localHandlers) {
            for (int i = handler; i < localHandlers.length; i += EXCEPTION_HANDLER_LENGTH) {
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_START_BCI] > bci) {
                    continue;
                }
                if (localHandlers[i + EXCEPTION_HANDLER_OFFSET_END_BCI] <= bci) {
                    continue;
                }
                return i;
            }
            return -1;
        }

    }
    /**
     * Builder class to generate bytecode. An interpreter can invoke this class with its {@link com.oracle.truffle.api.bytecode.BytecodeParser} to generate bytecode.
     */
    public static final class Builder extends BasicInterpreterBuilder {

        private static final byte UNINITIALIZED = -1;
        private static final String[] OPERATION_NAMES = new String[] {null, "Block", "Root", "IfThen", "IfThenElse", "Conditional", "While", "TryCatch", "TryFinally", "TryCatchOtherwise", "FinallyHandler", "Label", "Branch", "LoadConstant", "LoadNull", "LoadArgument", "LoadException", "LoadLocal", "LoadLocalMaterialized", "StoreLocal", "StoreLocalMaterialized", "Return", "Yield", "Source", "SourceSection", "Tag", "EarlyReturn", "AddOperation", "ToString", "Call", "AddConstantOperation", "AddConstantOperationAtEnd", "VeryComplexOperation", "ThrowOperation", "ReadExceptionOperation", "AlwaysBoxOperation", "AppenderOperation", "TeeLocal", "TeeLocalRange", "Invoke", "MaterializeFrame", "CreateClosure", "VoidOperation", "ToBoolean", "GetSourcePosition", "EnsureAndGetSourcePosition", "GetSourcePositions", "CopyLocalsToFrame", "GetBytecodeLocation", "CollectBytecodeLocations", "CollectSourceLocations", "CollectAllSourceLocations", "Continue", "CurrentLocation", "PrintHere", "IncrementValue", "DoubleValue", "EnableIncrementValueInstrumentation", "Add", "Mod", "Less", "EnableDoubleValueInstrumentation", "ExplicitBindingsTest", "ImplicitBindingsTest", "ScAnd", "ScOr"};
        private static final Class<?>[] TAGS_ROOT_TAG_ROOT_BODY_TAG = new Class<?>[]{RootTag.class, RootBodyTag.class};

        private int operationSequenceNumber;
        private OperationStackEntry[] operationStack;
        private int operationSp;
        private int rootOperationSp;
        private int numLocals;
        private int numLabels;
        private int numNodes;
        private int numHandlers;
        private int numConditionalBranches;
        private byte[] bc;
        private int bci;
        private int currentStackHeight;
        private int maxStackHeight;
        private int[] sourceInfo;
        private int sourceInfoIndex;
        private int[] handlerTable;
        private int handlerTableSize;
        private int[] locals;
        private int localsTableIndex;
        private HashMap<BytecodeLabel, ArrayList<Integer>> unresolvedLabels;
        private ConstantPool constantPool;
        private boolean reachable = true;
        private ArrayList<ContinuationLocation> continuationLocations;
        private int maxLocals;
        private List<TagNode> tagRoots;
        private List<TagNode> tagNodes;
        private SavedState savedState;
        private final BytecodeDSLTestLanguage language;
        private final BytecodeRootNodesImpl nodes;
        private final CharSequence reparseReason;
        private final boolean parseBytecodes;
        private final int tags;
        private final int instrumentations;
        private final boolean parseSources;
        private final ArrayList<BasicInterpreterWithUncached> builtNodes;
        private int numRoots;
        private final ArrayList<Source> sources;
        private SerializationState serialization;

        /**
         * Constructor for initial parses.
         */
        private Builder(BytecodeDSLTestLanguage language, BytecodeRootNodesImpl nodes, BytecodeConfig config) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.language = language;
            this.nodes = nodes;
            this.reparseReason = null;
            long encoding = BytecodeConfigEncoderImpl.decode(config);
            this.tags = (int)((encoding >> 32) & 0xFFFF_FFFF);
            this.instrumentations = (int)((encoding >> 1) & 0x7FFF_FFFF);
            this.parseSources = (encoding & 0x1) != 0;
            this.parseBytecodes = true;
            this.sources = parseSources ? new ArrayList<>(4) : null;
            this.builtNodes = new ArrayList<>();
            this.operationStack = new OperationStackEntry[8];
            this.rootOperationSp = -1;
        }

        /**
         * Constructor for reparsing.
         */
        private Builder(BytecodeRootNodesImpl nodes, boolean parseBytecodes, int tags, int instrumentations, boolean parseSources, CharSequence reparseReason) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.language = nodes.getLanguage();
            this.nodes = nodes;
            this.reparseReason = reparseReason;
            this.parseBytecodes = parseBytecodes;
            this.tags = tags;
            this.instrumentations = instrumentations;
            this.parseSources = parseSources;
            this.sources = parseSources ? new ArrayList<>(4) : null;
            this.builtNodes = new ArrayList<>();
            this.operationStack = new OperationStackEntry[8];
            this.rootOperationSp = -1;
        }

        /**
         * Creates a new local. Uses default values for the local's metadata.
         */
        @Override
        public BytecodeLocal createLocal() {
            return createLocal(null, null);
        }

        /**
         * Creates a new local. Uses the given {@code name} and {@code info} in its local metadata.
         *
         * @param name the name assigned to the local's slot.
         * @param info the info assigned to the local's slot.
         * @see BytecodeNode#getLocalNames
         * @see BytecodeNode#getLocalInfos
         */
        @Override
        public BytecodeLocal createLocal(Object name, Object info) {
            if (serialization != null) {
                try {
                    int nameId;
                    if (name != null) {
                        nameId = serialization.serializeObject(name);
                    } else {
                        nameId = -1;
                    }
                    int infoId;
                    if (info != null) {
                        infoId = serialization.serializeObject(info);
                    } else {
                        infoId = -1;
                    }
                    serialization.buffer.writeShort(SerializationState.CODE_$CREATE_LOCAL);
                    serialization.buffer.writeInt(nameId);
                    serialization.buffer.writeInt(infoId);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return new SerializationLocal(serialization.depth, serialization.localCount++);
            }
            ScopeData scope = getCurrentScope();
            short localIndex = allocateBytecodeLocal() /* unique global index */;
            short frameIndex = safeCastShort(USER_LOCALS_START_INDEX + scope.frameOffset + scope.numLocals) /* location in frame */;
            int tableIndex = doEmitLocal(localIndex, frameIndex, name, info) /* index in global table */;
            scope.registerLocal(tableIndex);
            BytecodeLocalImpl local = new BytecodeLocalImpl(frameIndex, localIndex, ((RootData) operationStack[this.rootOperationSp].data).index, scope);
            return local;
        }

        /**
         * Creates a new label. The result should be {@link #emitLabel emitted} and can be {@link #emitBranch branched to}.
         */
        @Override
        public BytecodeLabel createLabel() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_$CREATE_LABEL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return new SerializationLabel(serialization.depth, serialization.labelCount++);
            }
            if (operationSp == 0 || (operationStack[operationSp - 1].operation != Operations.BLOCK && operationStack[operationSp - 1].operation != Operations.ROOT)) {
                throw failState("Labels must be created inside either Block or Root operations.");
            }
            BytecodeLabel result = new BytecodeLabelImpl(numLabels++, UNINITIALIZED, operationStack[operationSp - 1].sequenceNumber);
            operationStack[operationSp - 1].addDeclaredLabel(result);
            return result;
        }

        /**
         * Begins a built-in SourceSection operation with an unavailable source section.
         *
         * @see #beginSourceSection(int, int)
         * @see #endSourceSectionUnavailable()
         */
        @Override
        public void beginSourceSectionUnavailable() {
            beginSourceSection(-1, -1);
        }

        /**
         * Ends a built-in SourceSection operation with an unavailable source section.
         *
         * @see #endSourceSection()
         * @see #beginSourceSectionUnavailable()
         */
        @Override
        public void endSourceSectionUnavailable() {
            endSourceSection();
        }

        private void registerUnresolvedLabel(BytecodeLabel label, int immediateBci) {
            ArrayList<Integer> locations = unresolvedLabels.computeIfAbsent(label, k -> new ArrayList<>());
            locations.add(immediateBci);
        }

        private void resolveUnresolvedLabel(BytecodeLabel label, int stackHeight) {
            BytecodeLabelImpl impl = (BytecodeLabelImpl) label;
            assert !impl.isDefined();
            impl.bci = bci;
            List<Integer> sites = unresolvedLabels.remove(impl);
            if (sites != null) {
                for (Integer site : sites) {
                    BYTES.putInt(bc, site, impl.bci);
                }
            }
        }

        /**
         * Begins a built-in Block operation.
         * <p>
         * Signature: Block(body...) -> void/Object
         * <p>
         * Block is a grouping operation that executes each child in its body sequentially, producing the result of the last child (if any).
         * This operation can be used to group multiple operations together in a single operation.
         * The result of a Block is the result produced by the last child (or void, if no value is produced).
         * <p>
         * A corresponding call to {@link #endBlock} is required to end the operation.
         */
        @Override
        public void beginBlock() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_BLOCK);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            ScopeData parentScope = getCurrentScope();
            beforeChild();
            BlockData operationData = new BlockData(this.currentStackHeight);
            beginOperation(Operations.BLOCK, operationData);
            operationData.frameOffset = parentScope.frameOffset + parentScope.numLocals;
        }

        /**
         * Ends a built-in Block operation.
         * <p>
         * Signature: Block(body...) -> void/Object
         *
         * @see #beginBlock
         */
        @Override
        public void endBlock() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_BLOCK);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.BLOCK);
            if (!(operation.data instanceof BlockData operationData)) {
                throw assertionFailed("Data class BlockData expected, but was " + operation.data);
            }
            if (operationData.numLocals > 0) {
                maxLocals = Math.max(maxLocals, operationData.frameOffset + operationData.numLocals);
                for (int index = 0; index < operationData.numLocals; index++) {
                    locals[operationData.locals[index] + LOCALS_OFFSET_END_BCI] = bci;
                    doEmitInstructionS(Instructions.CLEAR_LOCAL, 0, safeCastShort(locals[operationData.locals[index] + LOCALS_OFFSET_FRAME_INDEX]));
                }
            }
            operationData.valid = false;
            afterChild(operationData.producedValue, operationData.childBci);
        }

        /**
         * Begins a new root node.
         * <p>
         * Signature: Root(body...)
         * <p>
         * Each Root operation defines one function (i.e., a {@link BasicInterpreter}).
         * It takes one or more children, which define the body of the function that executes when it is invoked.
         * If control falls through to the end of the body without returning, instructions are inserted to implicitly return {@code null}.
         * <p>
         * A root operation is typically the outermost one. That is, a {@link BytecodeParser} should invoke {@link #beginRoot} first before using other builder methods to generate bytecode.
         * The parser should invoke {@link #endRoot} to finish generating the {@link BasicInterpreter}.
         * <p>
         * A parser *can* nest this operation in Source and SourceSection operations in order to provide a {@link Node#getSourceSection source location} for the entire root node.
         * The result of {@link Node#getSourceSection} on the generated root is undefined if there is no enclosing SourceSection operation.
         * <p>
         * This method can also be called inside of another root operation. Bytecode generation for the outer root node suspends until generation for the inner root node finishes.
         * The inner root node is not lexically nested in the first (you can invoke the inner root node independently), but the inner root *can* manipulate the outer root's locals using
         * materialized local accesses if the outer frame is provided to it.
         * Multiple root nodes can be obtained from the {@link BytecodeNodes} object in the order of their {@link #beginRoot} calls.
         *
         */
        @Override
        public void beginRoot() {
            if (serialization != null) {
                try {
                    SerializationRootNode node = new SerializationRootNode(FrameDescriptor.newBuilder(), serialization.depth, checkOverflowShort(serialization.rootCount++, "Root node count"));
                    serialization.rootStack.push(node);
                    serialization.builtNodes.add(node);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ROOT);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (bc != null) {
                savedState = new SavedState(operationSequenceNumber, operationStack, operationSp, rootOperationSp, numLocals, numLabels, numNodes, numHandlers, numConditionalBranches, bc, bci, currentStackHeight, maxStackHeight, sourceInfo, sourceInfoIndex, handlerTable, handlerTableSize, locals, localsTableIndex, unresolvedLabels, constantPool, reachable, continuationLocations, maxLocals, tagRoots, tagNodes, savedState);
            }
            operationSequenceNumber = 0;
            rootOperationSp = operationSp;
            reachable = true;
            tagRoots = null;
            tagNodes = null;
            numLocals = 0;
            maxLocals = numLocals;
            numLabels = 0;
            numNodes = 0;
            numHandlers = 0;
            numConditionalBranches = 0;
            constantPool = new ConstantPool();
            bc = new byte[32];
            bci = 0;
            currentStackHeight = 0;
            maxStackHeight = 0;
            handlerTable = new int[2 * EXCEPTION_HANDLER_LENGTH];
            handlerTableSize = 0;
            locals = null;
            localsTableIndex = 0;
            unresolvedLabels = new HashMap<>();
            continuationLocations = new ArrayList<>();
            if (parseSources) {
                sourceInfo = new int[3 * SOURCE_INFO_LENGTH];
                sourceInfoIndex = 0;
            }
            RootData operationData = new RootData(safeCastShort(numRoots++));
            if (reparseReason == null) {
                builtNodes.add(null);
                if (builtNodes.size() > Short.MAX_VALUE) {
                    throw BytecodeEncodingException.create("Root node count exceeded maximum value.");
                }
            }
            operationData.frameOffset = numLocals;
            beginOperation(Operations.ROOT, operationData);
            beginTag(TAGS_ROOT_TAG_ROOT_BODY_TAG);
            beginBlock();
        }

        /**
         * Finishes generating bytecode for the current root node.
         * <p>
         * Signature: Root(body...)
         *
         * @returns the root node with generated bytecode.
         */
        @Override
        public BasicInterpreter endRoot() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ROOT);
                    SerializationRootNode result = serialization.rootStack.pop();
                    serialization.buffer.writeInt(result.contextDepth);
                    serialization.buffer.writeInt(result.rootIndex);
                    return result;
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
            }
            if (!(operationStack[operationSp - 1].data instanceof BlockData blockOperation)) {
                throw assertionFailed("Data class BlockData expected, but was " + operationStack[operationSp - 1].data);
            }
            if (!blockOperation.producedValue) {
                emitLoadNull();
            }
            endBlock();
            if (!(operationStack[rootOperationSp].data instanceof RootData operationData)) {
                throw assertionFailed("Data class RootData expected, but was " + operationStack[rootOperationSp].data);
            }
            endTag(TAGS_ROOT_TAG_ROOT_BODY_TAG);
            doEmitInstruction(Instructions.RETURN, -1);
            endOperation(Operations.ROOT);
            if (operationData.numLocals > 0) {
                maxLocals = Math.max(maxLocals, operationData.frameOffset + operationData.numLocals);
                for (int index = 0; index < operationData.numLocals; index++) {
                    locals[operationData.locals[index] + LOCALS_OFFSET_END_BCI] = bci;
                }
            }
            operationData.valid = false;
            byte[] bytecodes_ = null;
            Object[] constants_ = null;
            int[] handlers_ = null;
            int[] locals_ = null;
            int[] sourceInfo_ = null;
            List<Source> sources_ = null;
            int numNodes_ = 0;
            TagRootNode tagRoot_ = null;
            doEmitRoot();
            if (parseSources) {
                sourceInfo_ = Arrays.copyOf(sourceInfo, sourceInfoIndex);
                sources_ = sources;
            }
            if (parseBytecodes) {
                bytecodes_ = Arrays.copyOf(bc, bci);
                constants_ = constantPool.toArray();
                handlers_ = Arrays.copyOf(handlerTable, handlerTableSize);
                sources_ = sources;
                numNodes_ = numNodes;
                locals_ = locals == null ? EMPTY_INT_ARRAY : Arrays.copyOf(locals, localsTableIndex);
            }
            if (tags != 0 && this.tagNodes != null) {
                TagNode[] tagNodes_ = this.tagNodes.toArray(TagNode[]::new);
                TagNode tagTree_;
                assert !this.tagRoots.isEmpty();
                if (this.tagRoots.size() == 1) {
                    tagTree_ = this.tagRoots.get(0);
                } else {
                    tagTree_ = new TagNode(0, -1);
                    tagTree_.children = tagTree_.insert(this.tagRoots.toArray(TagNode[]::new));
                }
                tagRoot_ = new TagRootNode(tagTree_, tagNodes_);
            }
            BasicInterpreterWithUncached result;
            if (reparseReason != null) {
                result = builtNodes.get(operationData.index);
                if (parseBytecodes) {
                    AbstractBytecodeNode oldBytecodeNode = result.bytecode;
                    assert result.maxLocals == maxLocals + USER_LOCALS_START_INDEX;
                    assert result.nodes == this.nodes;
                    assert constants_.length == oldBytecodeNode.constants.length;
                    assert result.getFrameDescriptor().getNumberOfSlots() == maxStackHeight + maxLocals + USER_LOCALS_START_INDEX;
                    for (ContinuationLocation continuationLocation : continuationLocations) {
                        int constantPoolIndex = continuationLocation.constantPoolIndex;
                        ContinuationRootNodeImpl continuationRootNode = (ContinuationRootNodeImpl) oldBytecodeNode.constants[constantPoolIndex];
                        ACCESS.writeObject(constants_, constantPoolIndex, continuationRootNode);
                    }
                }
                AbstractBytecodeNode bytecodeNode = result.updateBytecode(bytecodes_, constants_, handlers_, locals_, sourceInfo_, sources_, numNodes_, tagRoot_, this.reparseReason, continuationLocations);
                assert result.buildIndex == operationData.index;
            } else {
                com.oracle.truffle.api.frame.FrameDescriptor.Builder frameDescriptorBuilder = FrameDescriptor.newBuilder();
                frameDescriptorBuilder.addSlots(maxStackHeight + maxLocals + USER_LOCALS_START_INDEX, FrameSlotKind.Illegal);
                result = new BasicInterpreterWithUncached(language, frameDescriptorBuilder, nodes, maxLocals + USER_LOCALS_START_INDEX, operationData.index, bytecodes_, constants_, handlers_, locals_, sourceInfo_, sources_, numNodes_, tagRoot_);
                BytecodeNode bytecodeNode = result.getBytecodeNode();
                for (ContinuationLocation continuationLocation : continuationLocations) {
                    int constantPoolIndex = continuationLocation.constantPoolIndex;
                    BytecodeLocation location;
                    if (continuationLocation.bci == -1) {
                        location = null;
                    } else {
                        location = bytecodeNode.getBytecodeLocation(continuationLocation.bci);
                    }
                    ContinuationRootNodeImpl continuationRootNode = new ContinuationRootNodeImpl(language, result.getFrameDescriptor(), result, continuationLocation.sp, location);
                    ACCESS.writeObject(constants_, constantPoolIndex, continuationRootNode);
                }
                assert operationData.index <= numRoots;
                builtNodes.set(operationData.index, result);
            }
            rootOperationSp = -1;
            if (savedState == null) {
                // invariant: bc is null when no root node is being built
                bc = null;
            } else {
                this.operationSequenceNumber = savedState.operationSequenceNumber;
                this.operationStack = savedState.operationStack;
                this.operationSp = savedState.operationSp;
                this.rootOperationSp = savedState.rootOperationSp;
                this.numLocals = savedState.numLocals;
                this.numLabels = savedState.numLabels;
                this.numNodes = savedState.numNodes;
                this.numHandlers = savedState.numHandlers;
                this.numConditionalBranches = savedState.numConditionalBranches;
                this.bc = savedState.bc;
                this.bci = savedState.bci;
                this.currentStackHeight = savedState.currentStackHeight;
                this.maxStackHeight = savedState.maxStackHeight;
                this.sourceInfo = savedState.sourceInfo;
                this.sourceInfoIndex = savedState.sourceInfoIndex;
                this.handlerTable = savedState.handlerTable;
                this.handlerTableSize = savedState.handlerTableSize;
                this.locals = savedState.locals;
                this.localsTableIndex = savedState.localsTableIndex;
                this.unresolvedLabels = savedState.unresolvedLabels;
                this.constantPool = savedState.constantPool;
                this.reachable = savedState.reachable;
                this.continuationLocations = savedState.continuationLocations;
                this.maxLocals = savedState.maxLocals;
                this.tagRoots = savedState.tagRoots;
                this.tagNodes = savedState.tagNodes;
                this.savedState = savedState.savedState;
            }
            return result;
        }

        /**
         * Begins a built-in IfThen operation.
         * <p>
         * Signature: IfThen(condition, thens) -> void
         * <p>
         * IfThen implements an if-then statement. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code thens}.
         * This is a void operation; {@code thens} can also be void.
         * <p>
         * A corresponding call to {@link #endIfThen} is required to end the operation.
         */
        @Override
        public void beginIfThen() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_IF_THEN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            IfThenData operationData = new IfThenData(this.reachable);
            beginOperation(Operations.IFTHEN, operationData);
        }

        /**
         * Ends a built-in IfThen operation.
         * <p>
         * Signature: IfThen(condition, thens) -> void
         *
         * @see #beginIfThen
         */
        @Override
        public void endIfThen() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_IF_THEN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.IFTHEN);
            if (operation.childCount != 2) {
                throw failState("Operation IfThen expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            updateReachable();
            afterChild(false, -1);
        }

        /**
         * Begins a built-in IfThenElse operation.
         * <p>
         * Signature: IfThenElse(condition, thens, elses) -> void
         * <p>
         * IfThenElse implements an if-then-else statement. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code thens}; otherwise, it executes {@code elses}.
         * This is a void operation; both {@code thens} and {@code elses} can also be void.
         * <p>
         * A corresponding call to {@link #endIfThenElse} is required to end the operation.
         */
        @Override
        public void beginIfThenElse() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_IF_THEN_ELSE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            IfThenElseData operationData = new IfThenElseData(this.reachable, this.reachable);
            beginOperation(Operations.IFTHENELSE, operationData);
        }

        /**
         * Ends a built-in IfThenElse operation.
         * <p>
         * Signature: IfThenElse(condition, thens, elses) -> void
         *
         * @see #beginIfThenElse
         */
        @Override
        public void endIfThenElse() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_IF_THEN_ELSE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.IFTHENELSE);
            if (operation.childCount != 3) {
                throw failState("Operation IfThenElse expected exactly 3 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof IfThenElseData operationData)) {
                throw assertionFailed("Data class IfThenElseData expected, but was " + operation.data);
            }
            markReachable(operationData.thenReachable || operationData.elseReachable);
            afterChild(false, -1);
        }

        /**
         * Begins a built-in Conditional operation.
         * <p>
         * Signature: Conditional(condition, thens, elses) -> Object
         * <p>
         * Conditional implements a conditional expression (e.g., {@code condition ? thens : elses} in Java). It has the same semantics as IfThenElse, except it produces the value of the conditionally-executed child.
         * <p>
         * A corresponding call to {@link #endConditional} is required to end the operation.
         */
        @Override
        public void beginConditional() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_CONDITIONAL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            ConditionalData operationData = new ConditionalData(this.reachable, this.reachable);
            beginOperation(Operations.CONDITIONAL, operationData);
        }

        /**
         * Ends a built-in Conditional operation.
         * <p>
         * Signature: Conditional(condition, thens, elses) -> Object
         *
         * @see #beginConditional
         */
        @Override
        public void endConditional() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_CONDITIONAL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.CONDITIONAL);
            if (operation.childCount != 3) {
                throw failState("Operation Conditional expected exactly 3 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof ConditionalData operationData)) {
                throw assertionFailed("Data class ConditionalData expected, but was " + operation.data);
            }
            markReachable(operationData.thenReachable || operationData.elseReachable);
            afterChild(true, -1);
        }

        /**
         * Begins a built-in While operation.
         * <p>
         * Signature: While(condition, body) -> void
         * <p>
         * While implements a while loop. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code body} and repeats.
         * This is a void operation; {@code body} can also be void.
         * <p>
         * A corresponding call to {@link #endWhile} is required to end the operation.
         */
        @Override
        public void beginWhile() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_WHILE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            WhileData operationData = new WhileData(bci, this.reachable);
            beginOperation(Operations.WHILE, operationData);
        }

        /**
         * Ends a built-in While operation.
         * <p>
         * Signature: While(condition, body) -> void
         *
         * @see #beginWhile
         */
        @Override
        public void endWhile() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_WHILE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.WHILE);
            if (operation.childCount != 2) {
                throw failState("Operation While expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            updateReachable();
            afterChild(false, -1);
        }

        /**
         * Begins a built-in TryCatch operation.
         * <p>
         * Signature: TryCatch(try, catch) -> void
         * <p>
         * TryCatch implements an exception handler. It executes {@code try}, and if a Truffle exception is thrown, it executes {@code catch}.
         * The exception can be accessed within the {@code catch} operation using LoadException.
         * Unlike a Java try-catch, this operation does not filter the exception based on type.
         * This is a void operation; both {@code try} and {@code catch} can also be void.
         * <p>
         * A corresponding call to {@link #endTryCatch} is required to end the operation.
         */
        @Override
        public void beginTryCatch() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TRY_CATCH);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            TryCatchData operationData = new TryCatchData(++numHandlers, safeCastShort(currentStackHeight), bci, this.reachable, this.reachable, this.reachable);
            beginOperation(Operations.TRYCATCH, operationData);
        }

        /**
         * Ends a built-in TryCatch operation.
         * <p>
         * Signature: TryCatch(try, catch) -> void
         *
         * @see #beginTryCatch
         */
        @Override
        public void endTryCatch() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TRY_CATCH);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TRYCATCH);
            if (operation.childCount != 2) {
                throw failState("Operation TryCatch expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof TryCatchData operationData)) {
                throw assertionFailed("Data class TryCatchData expected, but was " + operation.data);
            }
            markReachable(operationData.tryReachable || operationData.catchReachable);
            afterChild(false, -1);
        }

        /**
         * Begins a built-in TryFinally operation.
         * <p>
         * Signature: TryFinally(try) -> void
         * <p>
         * TryFinally implements a finally handler. It executes {@code try}, and after execution finishes it always executes {@code finally}.
         * If {@code try} finishes normally, {@code finally} executes and control continues after the TryFinally operation.
         * If {@code try} finishes exceptionally, {@code finally} executes and then rethrows the exception.
         * If {@code try} finishes with a control flow operation, {@code finally} executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
         * <p>
         * Unlike other child operations, {@code finally} is emitted multiple times in the bytecode (once for each regular, exceptional, and early control flow exit).
         * To facilitate this, the {@code finally} operation is specified by a {@code finallyGenerator} that can be invoked multiple times. It should be repeatable and not have side effects.
         * <p>
         * This is a void operation; either of {@code try} or {@code finally} can be void.
         * <p>
         * A corresponding call to {@link #endTryFinally} is required to end the operation.
         *
         * @param finallyGenerator an idempotent Runnable that generates the {@code finally} operation using builder calls.
         */
        @Override
        public void beginTryFinally(Runnable finallyGenerator) {
            if (serialization != null) {
                try {
                    short finallyGeneratorIndex = serializeFinallyGenerator(finallyGenerator);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TRY_FINALLY);
                    serialization.buffer.writeShort(serialization.depth);
                    serialization.buffer.writeShort(finallyGeneratorIndex);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            TryFinallyData operationData = new TryFinallyData(++numHandlers, safeCastShort(currentStackHeight), finallyGenerator, bci, this.reachable, this.reachable, false);
            beginOperation(Operations.TRYFINALLY, operationData);
        }

        /**
         * Ends a built-in TryFinally operation.
         * <p>
         * Signature: TryFinally(try) -> void
         *
         * @see #beginTryFinally
         */
        @Override
        public void endTryFinally() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TRY_FINALLY);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TRYFINALLY);
            if (operation.childCount != 1) {
                throw failState("Operation TryFinally expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof TryFinallyData operationData)) {
                throw assertionFailed("Data class TryFinallyData expected, but was " + operation.data);
            }
            int handlerSp = currentStackHeight + 1 /* reserve space for the exception */;
            updateMaxStackHeight(handlerSp);
            int exHandlerIndex = UNINITIALIZED;
            if (operationData.operationReachable) {
                // register exception table entry
                exHandlerIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, handlerSp);
            }
            // emit handler for normal completion case
            doEmitFinallyHandler(operationData, operationSp);
            // the operation was popped, so manually update reachability. try is reachable if neither it nor the finally handler exited early.
            operationData.tryReachable = operationData.tryReachable && this.reachable;
            if (this.reachable) {
                operationData.endBranchFixupBci = bci + 2;
                doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
            }
            if (operationData.operationReachable) {
                // update exception table; force handler code to be reachable
                this.reachable = true;
                patchHandlerTable(operationData.extraTableEntriesStart, operationData.extraTableEntriesEnd, operationData.handlerId, bci, handlerSp);
                if (exHandlerIndex != UNINITIALIZED) {
                    handlerTable[exHandlerIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = bci;
                }
            }
            // emit handler for exceptional case
            currentStackHeight = handlerSp;
            doEmitFinallyHandler(operationData, operationSp);
            doEmitInstruction(Instructions.THROW, -1);
            if (operationData.endBranchFixupBci != UNINITIALIZED) {
                BYTES.putInt(bc, operationData.endBranchFixupBci, bci);
            }
            markReachable(operationData.tryReachable);
            afterChild(false, -1);
        }

        /**
         * Begins a built-in TryCatchOtherwise operation.
         * <p>
         * Signature: TryCatchOtherwise(try, catch) -> void
         * <p>
         * TryCatchOtherwise implements a try block with different handling for regular and exceptional behaviour. It executes {@code try} and then one of the handlers.
         * If {@code try} finishes normally, {@code otherwise} executes and control continues after the TryCatchOtherwise operation.
         * If {@code try} finishes exceptionally, {@code catch} executes. The exception can be accessed using LoadException. Control continues after the TryCatchOtherwise operation.
         * If {@code try} finishes with a control flow operation, {@code otherwise} executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
         * <p>
         * Unlike other child operations, {@code otherwise} is emitted multiple times in the bytecode (once for each regular and early control flow exit).
         * To facilitate this, the {@code otherwise} operation is specified by an {@code otherwiseGenerator} that can be invoked multiple times. It should be repeatable and not have side effects.
         * <p>
         * This operation is effectively a TryFinally operation with a specialized handler for the exception case.
         * It does <strong>not</strong> implement try-catch-finally semantics: if an exception is thrown {@code catch} executes and {@code otherwise} does not.
         * In pseudocode, it implements:
         * <pre>
         * try {
         *     tryOperation
         * } finally {
         *     if (exceptionThrown) {
         *         catchOperation
         *     } else {
         *         otherwiseOperation
         *     }
         * }
         * </pre>
         * <p>
         * This is a void operation; any of {@code try}, {@code catch}, or {@code otherwise} can be void.
         * <p>
         * A corresponding call to {@link #endTryCatchOtherwise} is required to end the operation.
         *
         * @param otherwiseGenerator an idempotent Runnable that generates the {@code otherwise} operation using builder calls.
         */
        @Override
        public void beginTryCatchOtherwise(Runnable otherwiseGenerator) {
            if (serialization != null) {
                try {
                    short finallyGeneratorIndex = serializeFinallyGenerator(otherwiseGenerator);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TRY_CATCH_OTHERWISE);
                    serialization.buffer.writeShort(serialization.depth);
                    serialization.buffer.writeShort(finallyGeneratorIndex);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            TryFinallyData operationData = new TryFinallyData(++numHandlers, safeCastShort(currentStackHeight), otherwiseGenerator, bci, this.reachable, this.reachable, this.reachable);
            beginOperation(Operations.TRYCATCHOTHERWISE, operationData);
        }

        /**
         * Ends a built-in TryCatchOtherwise operation.
         * <p>
         * Signature: TryCatchOtherwise(try, catch) -> void
         *
         * @see #beginTryCatchOtherwise
         */
        @Override
        public void endTryCatchOtherwise() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TRY_CATCH_OTHERWISE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TRYCATCHOTHERWISE);
            if (operation.childCount != 2) {
                throw failState("Operation TryCatchOtherwise expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof TryFinallyData operationData)) {
                throw assertionFailed("Data class TryFinallyData expected, but was " + operation.data);
            }
            markReachable(operationData.tryReachable || operationData.catchReachable);
            afterChild(false, -1);
        }

        /**
         * Begins a built-in FinallyHandler operation.
         * <p>
         * Signature: FinallyHandler(body...) -> void
         * <p>
         * FinallyHandler is an internal operation that has no stack effect. All finally generators execute within a FinallyHandler operation.
         * Executing the generator emits new operations, but these operations should not affect the outer operation's child count/value validation.
         * To accomplish this, FinallyHandler "hides" these operations by popping any produced values and omitting calls to beforeChild/afterChild.
         * When walking the operation stack, we skip over operations above finallyOperationSp since they do not logically enclose the handler.
         * <p>
         * A corresponding call to {@link #endFinallyHandler} is required to end the operation.
         *
         * @param finallyOperationSp the operation stack pointer for the finally operation that created the FinallyHandler.
         */
        private void beginFinallyHandler(short finallyOperationSp) {
            validateRootOperationBegin();
            FinallyHandlerData operationData = new FinallyHandlerData(finallyOperationSp);
            beginOperation(Operations.FINALLYHANDLER, operationData);
        }

        /**
         * Ends a built-in FinallyHandler operation.
         * <p>
         * Signature: FinallyHandler(body...) -> void
         *
         * @see #beginFinallyHandler
         */
        private void endFinallyHandler() {
            endOperation(Operations.FINALLYHANDLER);
        }

        /**
         * Emits a built-in Label operation.
         * <p>
         * Signature: Label() -> void
         * <p>
         * Label assigns {@code label} the current location in the bytecode (so that it can be used as the target of a Branch).
         * This is a void operation.
         * <p>
         * Each {@link BytecodeLabel} must be defined exactly once. It should be defined directly inside the same operation in which it is created (using {@link #createLabel}).
         *
         * @param label the label to define.
         */
        @Override
        public void emitLabel(BytecodeLabel label) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LABEL);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLabel) label).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLabel) label).labelIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            BytecodeLabelImpl labelImpl = (BytecodeLabelImpl) label;
            if (labelImpl.isDefined()) {
                throw failState("BytecodeLabel already emitted. Each label must be emitted exactly once.");
            }
            if (labelImpl.declaringOp != operationStack[operationSp - 1].sequenceNumber) {
                throw failState("BytecodeLabel must be emitted inside the same operation it was created in.");
            }
            if (operationStack[operationSp - 1].data instanceof BlockData blockData) {
                assert this.currentStackHeight == blockData.startStackHeight;
            } else {
                assert operationStack[operationSp - 1].data instanceof RootData;
                assert this.currentStackHeight == 0;
            }
            resolveUnresolvedLabel(labelImpl, currentStackHeight);
            markReachable(true);
            afterChild(false, -1);
        }

        /**
         * Emits a built-in Branch operation.
         * <p>
         * Signature: Branch() -> void
         * <p>
         * Branch performs a branch to {@code label}.
         * This operation only supports unconditional forward branches; use IfThen and While to perform other kinds of branches.
         *
         * @param label the label to branch to.
         */
        @Override
        public void emitBranch(BytecodeLabel label) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_BRANCH);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLabel) label).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLabel) label).labelIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            BytecodeLabelImpl labelImpl = (BytecodeLabelImpl) label;
            int declaringOperationSp = UNINITIALIZED;
            for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                if (operationStack[i].sequenceNumber == labelImpl.declaringOp) {
                    declaringOperationSp = i;
                    break;
                }
            }
            if (declaringOperationSp == UNINITIALIZED) {
                throw failState("Branch must be targeting a label that is declared in an enclosing operation of the current root. Jumps into other operations are not permitted.");
            }
            if (labelImpl.isDefined()) {
                throw failState("Backward branches are unsupported. Use a While operation to model backward control flow.");
            }
            int targetStackHeight;
            if (operationStack[declaringOperationSp].data instanceof BlockData blockData) {
                targetStackHeight = blockData.startStackHeight;
            } else {
                assert operationStack[declaringOperationSp].data instanceof RootData;
                targetStackHeight = 0;
            }
            beforeEmitBranch(declaringOperationSp);
            // Pop any extra values off the stack before branching.
            int stackHeightBeforeBranch = currentStackHeight;
            while (targetStackHeight != currentStackHeight) {
                doEmitInstruction(Instructions.POP, -1);
            }
            // If the branch is not taken (e.g., control branches over it) the values are still on the stack.
            currentStackHeight = stackHeightBeforeBranch;
            if (this.reachable) {
                registerUnresolvedLabel(labelImpl, bci + 2);
            }
            doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
            markReachable(false);
            afterChild(false, bci - 6);
        }

        /**
         * Emits a built-in LoadConstant operation.
         * <p>
         * Signature: LoadConstant() -> Object
         * <p>
         * LoadConstant produces {@code constant}. The constant should be immutable, since it may be shared across multiple LoadConstant operations.
         *
         * @param constant the constant value to load.
         */
        @Override
        public void emitLoadConstant(Object constant) {
            if (serialization != null) {
                try {
                    int constant_index = serialization.serializeObject(constant);
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LOAD_CONSTANT);
                    serialization.buffer.writeInt(constant_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if (constant == null) {
                throw failArgument("The constant parameter must not be null. Use emitLoadNull() instead for null values.");
            }
            if (constant instanceof Node && !(constant instanceof RootNode)) {
                throw failArgument("Nodes cannot be used as constants.");
            }
            beforeChild();
            doEmitInstructionI(Instructions.LOAD_CONSTANT, 1, constantPool.addConstant(constant));
            afterChild(true, bci - 6);
        }

        /**
         * Emits a built-in LoadNull operation.
         * <p>
         * Signature: LoadNull() -> Object
         * <p>
         * LoadNull produces a {@code null} value.
         */
        @Override
        public void emitLoadNull() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LOAD_NULL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstruction(Instructions.LOAD_NULL, 1);
            afterChild(true, bci - 2);
        }

        /**
         * Emits a built-in LoadArgument operation.
         * <p>
         * Signature: LoadArgument() -> Object
         * <p>
         * LoadArgument reads the argument at {@code index} from the frame.
         * Throws {@link IndexOutOfBoundsException} if the index is out of bounds.
         *
         * @param index the index of the argument to load (must fit into a short).
         */
        @Override
        public void emitLoadArgument(int index) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LOAD_ARGUMENT);
                    serialization.buffer.writeInt(index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionS(Instructions.LOAD_ARGUMENT, 1, safeCastShort(index));
            afterChild(true, bci - 4);
        }

        /**
         * Emits a built-in LoadException operation.
         * <p>
         * Signature: LoadException() -> Object
         * <p>
         * LoadException reads the current exception from the frame.
         * This operation is only permitted inside the {@code catch} operation of TryCatch and TryCatchOtherwise operations.
         */
        @Override
        public void emitLoadException() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LOAD_EXCEPTION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            short exceptionStackHeight = UNINITIALIZED;
            loop: for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.TRYCATCH :
                    {
                        if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                            throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 1) {
                            exceptionStackHeight = operationData.stackHeight;
                            break loop;
                        }
                        break;
                    }
                    case Operations.TRYCATCHOTHERWISE :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 1) {
                            exceptionStackHeight = operationData.stackHeight;
                            break loop;
                        }
                        break;
                    }
                }
            }
            if (exceptionStackHeight == UNINITIALIZED) {
                throw failState("LoadException can only be used in the catch operation of a TryCatch/TryCatchOtherwise operation in the current root.");
            }
            doEmitInstructionS(Instructions.LOAD_EXCEPTION, 1, exceptionStackHeight);
            afterChild(true, bci - 4);
        }

        private void validateLocalScope(BytecodeLocal local) {
            if (!((BytecodeLocalImpl) local).scope.valid) {
                throw failArgument("Local variable scope of this local no longer valid.");
            }
        }

        /**
         * Emits a built-in LoadLocal operation.
         * <p>
         * Signature: LoadLocal() -> Object
         * <p>
         * LoadLocal reads {@code local} from the current frame.
         * If a value has not been written to the local, LoadLocal throws a {@link com.oracle.truffle.api.frame.FrameSlotTypeException}.
         *
         * @param local the local to load.
         */
        @Override
        public void emitLoadLocal(BytecodeLocal local) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_LOAD_LOCAL);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).localIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            validateLocalScope(local);
            doEmitInstructionS(Instructions.LOAD_LOCAL, 1, ((BytecodeLocalImpl) local).frameIndex);
            afterChild(true, bci - 4);
        }

        /**
         * Begins a built-in LoadLocalMaterialized operation.
         * <p>
         * Signature: LoadLocalMaterialized(frame) -> Object
         * <p>
         * LoadLocalMaterialized reads {@code local} from the frame produced by {@code frame}.
         * This operation can be used to read locals from materialized frames. The materialized frame must belong to the same root node or an enclosing root node.
         * The given local must be in scope at the point that LoadLocalMaterialized executes, otherwise it may produce unexpected values.
         * The interpreter will validate the scope if the interpreter is configured to store the bytecode index in the frame (see {@code @GenerateBytecode}).
         * <p>
         * A corresponding call to {@link #endLoadLocalMaterialized} is required to end the operation.
         *
         * @param local the local to load.
         */
        @Override
        public void beginLoadLocalMaterialized(BytecodeLocal local) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_LOAD_LOCAL_MATERIALIZED);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).localIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            validateLocalScope(local);
            beforeChild();
            BytecodeLocalImpl operationData = (BytecodeLocalImpl)local;
            beginOperation(Operations.LOADLOCALMATERIALIZED, operationData);
        }

        /**
         * Ends a built-in LoadLocalMaterialized operation.
         * <p>
         * Signature: LoadLocalMaterialized(frame) -> Object
         *
         * @see #beginLoadLocalMaterialized
         */
        @Override
        public void endLoadLocalMaterialized() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_LOAD_LOCAL_MATERIALIZED);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.LOADLOCALMATERIALIZED);
            if (operation.childCount != 1) {
                throw failState("Operation LoadLocalMaterialized expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof BytecodeLocalImpl operationData)) {
                throw assertionFailed("Data class BytecodeLocalImpl expected, but was " + operation.data);
            }
            doEmitInstructionSS(Instructions.LOAD_LOCAL_MAT, 0, operationData.frameIndex, operationData.rootIndex);
            afterChild(true, bci - 6);
        }

        /**
         * Begins a built-in StoreLocal operation.
         * <p>
         * Signature: StoreLocal(value) -> void
         * <p>
         * StoreLocal writes the value produced by {@code value} into the {@code local} in the current frame.
         * <p>
         * A corresponding call to {@link #endStoreLocal} is required to end the operation.
         *
         * @param local the local to store to.
         */
        @Override
        public void beginStoreLocal(BytecodeLocal local) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_STORE_LOCAL);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).localIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            validateLocalScope(local);
            beforeChild();
            BytecodeLocalImpl operationData = (BytecodeLocalImpl)local;
            beginOperation(Operations.STORELOCAL, operationData);
        }

        /**
         * Ends a built-in StoreLocal operation.
         * <p>
         * Signature: StoreLocal(value) -> void
         *
         * @see #beginStoreLocal
         */
        @Override
        public void endStoreLocal() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_STORE_LOCAL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.STORELOCAL);
            if (operation.childCount != 1) {
                throw failState("Operation StoreLocal expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof BytecodeLocalImpl operationData)) {
                throw assertionFailed("Data class BytecodeLocalImpl expected, but was " + operation.data);
            }
            doEmitInstructionS(Instructions.STORE_LOCAL, -1, operationData.frameIndex);
            afterChild(false, bci - 4);
        }

        /**
         * Begins a built-in StoreLocalMaterialized operation.
         * <p>
         * Signature: StoreLocalMaterialized(frame, value) -> void
         * <p>
         * StoreLocalMaterialized writes the value produced by {@code value} into the {@code local} in the frame produced by {@code frame}.
         * This operation can be used to store locals into materialized frames. The materialized frame must belong to the same root node or an enclosing root node.
         * The given local must be in scope at the point that StoreLocalMaterialized executes, otherwise it may produce unexpected values.
         * The interpreter will validate the scope if the interpreter is configured to store the bytecode index in the frame (see {@code @GenerateBytecode}).
         * <p>
         * A corresponding call to {@link #endStoreLocalMaterialized} is required to end the operation.
         *
         * @param local the local to store to.
         */
        @Override
        public void beginStoreLocalMaterialized(BytecodeLocal local) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_STORE_LOCAL_MATERIALIZED);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) local).localIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            validateLocalScope(local);
            beforeChild();
            BytecodeLocalImpl operationData = (BytecodeLocalImpl)local;
            beginOperation(Operations.STORELOCALMATERIALIZED, operationData);
        }

        /**
         * Ends a built-in StoreLocalMaterialized operation.
         * <p>
         * Signature: StoreLocalMaterialized(frame, value) -> void
         *
         * @see #beginStoreLocalMaterialized
         */
        @Override
        public void endStoreLocalMaterialized() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_STORE_LOCAL_MATERIALIZED);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.STORELOCALMATERIALIZED);
            if (operation.childCount != 2) {
                throw failState("Operation StoreLocalMaterialized expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof BytecodeLocalImpl operationData)) {
                throw assertionFailed("Data class BytecodeLocalImpl expected, but was " + operation.data);
            }
            doEmitInstructionSS(Instructions.STORE_LOCAL_MAT, -2, operationData.frameIndex, operationData.rootIndex);
            afterChild(false, bci - 6);
        }

        /**
         * Begins a built-in Return operation.
         * <p>
         * Signature: Return(result) -> void
         * <p>
         * Return returns the value produced by {@code result}.
         * <p>
         * A corresponding call to {@link #endReturn} is required to end the operation.
         */
        @Override
        public void beginReturn() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_RETURN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            ReturnOperationData operationData = new ReturnOperationData();
            beginOperation(Operations.RETURN, operationData);
        }

        /**
         * Ends a built-in Return operation.
         * <p>
         * Signature: Return(result) -> void
         *
         * @see #beginReturn
         */
        @Override
        public void endReturn() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_RETURN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.RETURN);
            if (operation.childCount != 1) {
                throw failState("Operation Return expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof ReturnOperationData operationData)) {
                throw assertionFailed("Data class ReturnOperationData expected, but was " + operation.data);
            }
            beforeEmitReturn(operationData.childBci);
            doEmitInstruction(Instructions.RETURN, -1);
            markReachable(false);
            afterChild(false, bci - 2);
        }

        /**
         * Begins a built-in Yield operation.
         * <p>
         * Signature: Yield(value) -> Object
         * <p>
         * Yield executes {@code value} and suspends execution at the given location, returning a {@link com.oracle.truffle.api.bytecode.ContinuationResult} containing the result.
         * The caller can resume the continuation, which continues execution after the Yield. When resuming, the caller passes a value that becomes the value produced by the Yield.
         * <p>
         * A corresponding call to {@link #endYield} is required to end the operation.
         */
        @Override
        public void beginYield() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_YIELD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            beginOperation(Operations.YIELD, null);
        }

        /**
         * Ends a built-in Yield operation.
         * <p>
         * Signature: Yield(value) -> Object
         *
         * @see #beginYield
         */
        @Override
        public void endYield() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_YIELD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.YIELD);
            if (operation.childCount != 1) {
                throw failState("Operation Yield expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            doEmitTagYield();
            short constantPoolIndex = allocateContinuationConstant();
            int continuationBci;
            if (reachable) {
                continuationBci = bci + 6;
            } else {
                continuationBci = -1;
            }
            continuationLocations.add(new ContinuationLocation(constantPoolIndex, continuationBci, currentStackHeight));
            doEmitInstructionI(Instructions.YIELD, 0, constantPoolIndex);
            doEmitTagResume();
            afterChild(true, bci - 6);
        }

        /**
         * Begins a built-in Source operation.
         * <p>
         * Signature: Source(body...) -> void/Object
         * <p>
         * Source associates the children in its {@code body} with {@code source}. Together with SourceSection, it encodes source locations for operations in the program.
         * <p>
         * A corresponding call to {@link #endSource} is required to end the operation.
         *
         * @param source the source object to associate with the enclosed operations.
         */
        @Override
        public void beginSource(Source source) {
            if (!parseSources) {
                return;
            }
            if (serialization != null) {
                try {
                    int source_index = serialization.serializeObject(source);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_SOURCE);
                    serialization.buffer.writeInt(source_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            beforeChild();
            if (source.hasBytes()) {
                throw failArgument("Byte-based sources are not supported.");
            }
            int index = sources.indexOf(source);
            if (index == -1) {
                index = sources.size();
                sources.add(source);
            }
            SourceData operationData = new SourceData(index);
            beginOperation(Operations.SOURCE, operationData);
        }

        /**
         * Ends a built-in Source operation.
         * <p>
         * Signature: Source(body...) -> void/Object
         *
         * @see #beginSource
         */
        @Override
        public void endSource() {
            if (!parseSources) {
                return;
            }
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_SOURCE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.SOURCE);
            if (!(operation.data instanceof SourceData operationData)) {
                throw assertionFailed("Data class SourceData expected, but was " + operation.data);
            }
            afterChild(operationData.producedValue, operationData.childBci);
        }

        /**
         * Begins a built-in SourceSection operation.
         * <p>
         * Signature: SourceSection(body...) -> void/Object
         * <p>
         * SourceSection associates the children in its {@code body} with the source section with the given character {@code index} and {@code length}.
         * To specify an {@link Source#createUnavailableSection() unavailable source section}, provide {@code -1} for both arguments.
         * This operation must be (directly or indirectly) enclosed within a Source operation.
         * <p>
         * A corresponding call to {@link #endSourceSection} is required to end the operation.
         *
         * @param index the starting character index of the source section, or -1 if the section is unavailable.
         * @param length the length (in characters) of the source section, or -1 if the section is unavailable.
         */
        @Override
        public void beginSourceSection(int index, int length) {
            if (!parseSources) {
                return;
            }
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_SOURCE_SECTION);
                    serialization.buffer.writeInt(index);
                    serialization.buffer.writeInt(length);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            beforeChild();
            int foundSourceIndex = -1;
            loop: for (int i = operationSp - 1; i >= 0; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.SOURCE :
                        if (!(operationStack[i].data instanceof SourceData sourceData)) {
                            throw assertionFailed("Data class SourceData expected, but was " + operationStack[i].data);
                        }
                        foundSourceIndex = sourceData.sourceIndex;
                        break loop;
                }
            }
            if (foundSourceIndex == -1) {
                throw failState("No enclosing Source operation found - each SourceSection must be enclosed in a Source operation.");
            }
            assert (index == -1 && length == -1) || (index >= 0 && length >= 0);
            int startBci;
            if (rootOperationSp == -1) {
                // not in a root yet
                startBci = 0;
            } else {
                startBci = bci;
            }
            SourceSectionData operationData = new SourceSectionData(foundSourceIndex, startBci, index, length);
            beginOperation(Operations.SOURCESECTION, operationData);
        }

        /**
         * Ends a built-in SourceSection operation.
         * <p>
         * Signature: SourceSection(body...) -> void/Object
         *
         * @see #beginSourceSection
         */
        @Override
        public void endSourceSection() {
            if (!parseSources) {
                return;
            }
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_SOURCE_SECTION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.SOURCESECTION);
            if (!(operation.data instanceof SourceSectionData operationData)) {
                throw assertionFailed("Data class SourceSectionData expected, but was " + operation.data);
            }
            doEmitSourceInfo(operationData.sourceIndex, operationData.startBci, bci, operationData.start, operationData.length);
            afterChild(operationData.producedValue, operationData.childBci);
        }

        /**
         * Begins a built-in Tag operation.
         * <p>
         * Signature: Tag(tagged) -> void/Object
         * <p>
         * Tag associates {@code tagged} with the given tags.
         * When the {@link BytecodeConfig} includes one or more of the given tags, the interpreter will automatically invoke instrumentation probes when entering/leaving {@code tagged}.
         * <p>
         * A corresponding call to {@link #endTag} is required to end the operation.
         *
         * @param newTags the tags to associate with the enclosed operations.
         */
        @Override
        public void beginTag(Class<?>... newTags) {
            if (newTags.length == 0) {
                throw failArgument("The tags parameter for beginTag must not be empty. Please specify at least one tag.");
            }
            int encodedTags = encodeTags(newTags);
            if ((encodedTags & this.tags) == 0) {
                return;
            }
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TAG);
                    serialization.buffer.writeInt(encodedTags);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            TagNode node = new TagNode(encodedTags & this.tags, bci);
            if (tagNodes == null) {
                tagNodes = new ArrayList<>();
            }
            int nodeId = tagNodes.size();
            tagNodes.add(node);
            beforeChild();
            TagOperationData operationData = new TagOperationData(nodeId, this.reachable, this.currentStackHeight, node);
            beginOperation(Operations.TAG, operationData);
            doEmitInstructionI(Instructions.TAG_ENTER, 0, nodeId);
        }

        /**
         * Ends a built-in Tag operation.
         * <p>
         * Signature: Tag(tagged) -> void/Object
         * <p>
         * The tags passed to this method should match the ones used in the corresponding {@link #beginTag} call.
         *
         * @param newTags the tags to associate with the enclosed operations.
         * @see #beginTag
         */
        @Override
        public void endTag(Class<?>... newTags) {
            if (newTags.length == 0) {
                throw failArgument("The tags parameter for beginTag must not be empty. Please specify at least one tag.");
            }
            int encodedTags = encodeTags(newTags);
            if ((encodedTags & this.tags) == 0) {
                return;
            }
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TAG);
                    serialization.buffer.writeInt(encodedTags);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TAG);
            if (operation.childCount != 1) {
                throw failState("Operation Tag expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof TagOperationData operationData)) {
                throw assertionFailed("Data class TagOperationData expected, but was " + operation.data);
            }
            TagNode tagNode = operationData.node;
            if ((encodedTags & this.tags) != tagNode.tags) {
                throw new IllegalArgumentException("The tags provided to endTag do not match the tags provided to the corresponding beginTag call.");
            }
            // If this tag operation is nested in another, add it to the outer tag tree. Otherwise, it becomes a tag root.
            boolean outerTagFound = false;
            for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                if (operationStack[i].data instanceof TagOperationData t) {
                    if (t.children == null) {
                        t.children = new ArrayList<>(3);
                    }
                    t.children.add(tagNode);
                    outerTagFound = true;
                    break;
                }
            }
            if (!outerTagFound) {
                if (tagRoots == null) {
                    tagRoots = new ArrayList<>(3);
                }
                tagRoots.add(tagNode);
            }
            TagNode[] children;
            List<TagNode> operationChildren = operationData.children;
            if (operationChildren == null) {
                children = TagNode.EMPTY_ARRAY;
            } else {
                children = new TagNode[operationChildren.size()];
                for (int i = 0; i < children.length; i++) {
                    children[i] = tagNode.insert(operationChildren.get(i));
                }
            }
            tagNode.children = children;
            tagNode.returnBci = bci;
            if (operationData.producedValue) {
                if (operationData.operationReachable) {
                    markReachable(true);
                    doEmitInstructionI(Instructions.TAG_LEAVE, 0, operationData.nodeId);
                    doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight);
                } else {
                    doEmitInstructionI(Instructions.TAG_LEAVE, 0, operationData.nodeId);
                }
                afterChild(true, bci - 6);
            } else {
                if (operationData.operationReachable) {
                    markReachable(true);
                    doEmitInstructionI(Instructions.TAG_LEAVE_VOID, 0, operationData.nodeId);
                    doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight);
                } else {
                    doEmitInstructionI(Instructions.TAG_LEAVE_VOID, 0, operationData.nodeId);
                }
                afterChild(false, -1);
            }
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EarlyReturn EarlyReturn} operation.
         * <p>
         * Signature: EarlyReturn(result) -> void
         * <p>
         * A corresponding call to {@link #endEarlyReturn} is required to end the operation.
         */
        @Override
        public void beginEarlyReturn() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_EARLY_RETURN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.EARLYRETURN, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EarlyReturn EarlyReturn} operation.
         * <p>
         * Signature: EarlyReturn(result) -> void
         *
         * @see #beginEarlyReturn
         */
        @Override
        public void endEarlyReturn() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_EARLY_RETURN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.EARLYRETURN);
            if (operation.childCount != 1) {
                throw failState("Operation EarlyReturn expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.EARLY_RETURN_, -1, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddOperation AddOperation} operation.
         * <p>
         * Signature: AddOperation(lhs, rhs) -> Object
         * <p>
         * Adds the two operand values, which must either be longs or Strings.
         * <p>
         * A corresponding call to {@link #endAddOperation} is required to end the operation.
         */
        @Override
        public void beginAddOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ADD_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.ADDOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddOperation AddOperation} operation.
         * <p>
         * Signature: AddOperation(lhs, rhs) -> Object
         *
         * @see #beginAddOperation
         */
        @Override
        public void endAddOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ADD_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.ADDOPERATION);
            if (operation.childCount != 2) {
                throw failState("Operation AddOperation expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.ADD_OPERATION_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ToString ToString} operation.
         * <p>
         * Signature: ToString(value) -> Object
         * <p>
         * Exercises interop on the operand.
         * <p>
         * A corresponding call to {@link #endToString} is required to end the operation.
         */
        @Override
        public void beginToString() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TO_STRING);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.TOSTRING, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ToString ToString} operation.
         * <p>
         * Signature: ToString(value) -> Object
         *
         * @see #beginToString
         */
        @Override
        public void endToString() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TO_STRING);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TOSTRING);
            if (operation.childCount != 1) {
                throw failState("Operation ToString expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.TO_STRING_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Call Call} operation.
         * <p>
         * Signature: Call(arguments...) -> Object
         * <p>
         * A corresponding call to {@link #endCall} is required to end the operation.
         *
         * @param interpreterValue
         */
        @Override
        public void beginCall(BasicInterpreter interpreterValue) {
            if (serialization != null) {
                try {
                    int interpreterValue_index = serialization.serializeObject(interpreterValue);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_CALL);
                    serialization.buffer.writeInt(interpreterValue_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if (interpreterValue == null) {
                throw failArgument("The interpreterValue parameter must not be null. Constant operands do not permit null values.");
            }
            int interpreterIndex = constantPool.addConstant(interpreterValue);
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, new int[] {interpreterIndex});
            beginOperation(Operations.CALL, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Call Call} operation.
         * <p>
         * Signature: Call(arguments...) -> Object
         *
         * @see #beginCall
         */
        @Override
        public void endCall() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_CALL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.CALL);
            doEmitVariadic(operation.childCount - 0);
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionII(Instructions.CALL_, 0, operationData.constants[0], allocateNode());
            afterChild(true, bci - 10);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddConstantOperation AddConstantOperation} operation.
         * <p>
         * Signature: AddConstantOperation(rhs) -> Object
         * <p>
         * A corresponding call to {@link #endAddConstantOperation} is required to end the operation.
         *
         * @param constantLhsValue
         */
        @Override
        public void beginAddConstantOperation(long constantLhsValue) {
            if (serialization != null) {
                try {
                    int constantLhsValue_index = serialization.serializeObject(constantLhsValue);
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ADD_CONSTANT_OPERATION);
                    serialization.buffer.writeInt(constantLhsValue_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            int constantLhsIndex = constantPool.addConstant(constantLhsValue);
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, new int[] {constantLhsIndex});
            beginOperation(Operations.ADDCONSTANTOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddConstantOperation AddConstantOperation} operation.
         * <p>
         * Signature: AddConstantOperation(rhs) -> Object
         *
         * @see #beginAddConstantOperation
         */
        @Override
        public void endAddConstantOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ADD_CONSTANT_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.ADDCONSTANTOPERATION);
            if (operation.childCount != 1) {
                throw failState("Operation AddConstantOperation expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionII(Instructions.ADD_CONSTANT_OPERATION_, 0, operationData.constants[0], allocateNode());
            afterChild(true, bci - 10);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddConstantOperationAtEnd AddConstantOperationAtEnd} operation.
         * <p>
         * Signature: AddConstantOperationAtEnd(lhs) -> Object
         * <p>
         * A corresponding call to {@link #endAddConstantOperationAtEnd} is required to end the operation.
         */
        @Override
        public void beginAddConstantOperationAtEnd() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ADD_CONSTANT_OPERATION_AT_END);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.ADDCONSTANTOPERATIONATEND, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AddConstantOperationAtEnd AddConstantOperationAtEnd} operation.
         * <p>
         * Signature: AddConstantOperationAtEnd(lhs) -> Object
         *
         * @param constantRhsValue
         * @see #beginAddConstantOperationAtEnd
         */
        @Override
        public void endAddConstantOperationAtEnd(long constantRhsValue) {
            if (serialization != null) {
                try {
                    int constantRhsValue_index = serialization.serializeObject(constantRhsValue);
                    serialization.buffer.writeShort(SerializationState.CODE_END_ADD_CONSTANT_OPERATION_AT_END);
                    serialization.buffer.writeInt(constantRhsValue_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            int constantRhsIndex = constantPool.addConstant(constantRhsValue);
            OperationStackEntry operation = endOperation(Operations.ADDCONSTANTOPERATIONATEND);
            if (operation.childCount != 1) {
                throw failState("Operation AddConstantOperationAtEnd expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionII(Instructions.ADD_CONSTANT_OPERATION_AT_END_, 0, constantRhsIndex, allocateNode());
            afterChild(true, bci - 10);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.VeryComplexOperation VeryComplexOperation} operation.
         * <p>
         * Signature: VeryComplexOperation(a1, a2...) -> long
         * <p>
         * A corresponding call to {@link #endVeryComplexOperation} is required to end the operation.
         */
        @Override
        public void beginVeryComplexOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_VERY_COMPLEX_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.VERYCOMPLEXOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.VeryComplexOperation VeryComplexOperation} operation.
         * <p>
         * Signature: VeryComplexOperation(a1, a2...) -> long
         *
         * @see #beginVeryComplexOperation
         */
        @Override
        public void endVeryComplexOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_VERY_COMPLEX_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.VERYCOMPLEXOPERATION);
            if (operation.childCount < 1) {
                throw failState("Operation VeryComplexOperation expected at least 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            doEmitVariadic(operation.childCount - 1);
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.VERY_COMPLEX_OPERATION_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ThrowOperation ThrowOperation} operation.
         * <p>
         * Signature: ThrowOperation(value) -> Object
         * <p>
         * A corresponding call to {@link #endThrowOperation} is required to end the operation.
         */
        @Override
        public void beginThrowOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_THROW_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.THROWOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ThrowOperation ThrowOperation} operation.
         * <p>
         * Signature: ThrowOperation(value) -> Object
         *
         * @see #beginThrowOperation
         */
        @Override
        public void endThrowOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_THROW_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.THROWOPERATION);
            if (operation.childCount != 1) {
                throw failState("Operation ThrowOperation expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.THROW_OPERATION_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ReadExceptionOperation ReadExceptionOperation} operation.
         * <p>
         * Signature: ReadExceptionOperation(ex) -> long
         * <p>
         * A corresponding call to {@link #endReadExceptionOperation} is required to end the operation.
         */
        @Override
        public void beginReadExceptionOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_READ_EXCEPTION_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.READEXCEPTIONOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ReadExceptionOperation ReadExceptionOperation} operation.
         * <p>
         * Signature: ReadExceptionOperation(ex) -> long
         *
         * @see #beginReadExceptionOperation
         */
        @Override
        public void endReadExceptionOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_READ_EXCEPTION_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.READEXCEPTIONOPERATION);
            if (operation.childCount != 1) {
                throw failState("Operation ReadExceptionOperation expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.READ_EXCEPTION_OPERATION_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AlwaysBoxOperation AlwaysBoxOperation} operation.
         * <p>
         * Signature: AlwaysBoxOperation(value) -> Object
         * <p>
         * A corresponding call to {@link #endAlwaysBoxOperation} is required to end the operation.
         */
        @Override
        public void beginAlwaysBoxOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ALWAYS_BOX_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.ALWAYSBOXOPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AlwaysBoxOperation AlwaysBoxOperation} operation.
         * <p>
         * Signature: AlwaysBoxOperation(value) -> Object
         *
         * @see #beginAlwaysBoxOperation
         */
        @Override
        public void endAlwaysBoxOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ALWAYS_BOX_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.ALWAYSBOXOPERATION);
            if (operation.childCount != 1) {
                throw failState("Operation AlwaysBoxOperation expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.ALWAYS_BOX_OPERATION_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AppenderOperation AppenderOperation} operation.
         * <p>
         * Signature: AppenderOperation(list, value) -> void
         * <p>
         * A corresponding call to {@link #endAppenderOperation} is required to end the operation.
         */
        @Override
        public void beginAppenderOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_APPENDER_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.APPENDEROPERATION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.AppenderOperation AppenderOperation} operation.
         * <p>
         * Signature: AppenderOperation(list, value) -> void
         *
         * @see #beginAppenderOperation
         */
        @Override
        public void endAppenderOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_APPENDER_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.APPENDEROPERATION);
            if (operation.childCount != 2) {
                throw failState("Operation AppenderOperation expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.APPENDER_OPERATION_, -2, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.TeeLocal TeeLocal} operation.
         * <p>
         * Signature: TeeLocal(value) -> Object
         * <p>
         * A corresponding call to {@link #endTeeLocal} is required to end the operation.
         *
         * @param setterValue
         */
        @Override
        public void beginTeeLocal(BytecodeLocal setterValue) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TEE_LOCAL);
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) setterValue).contextDepth));
                    serialization.buffer.writeShort(safeCastShort(((SerializationLocal) setterValue).localIndex));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if (setterValue == null) {
                throw failArgument("The setterValue parameter must not be null. Constant operands do not permit null values.");
            }
            int setterIndex = constantPool.addConstant(LocalAccessor.constantOf(setterValue));
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, new int[] {setterIndex});
            beginOperation(Operations.TEELOCAL, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.TeeLocal TeeLocal} operation.
         * <p>
         * Signature: TeeLocal(value) -> Object
         *
         * @see #beginTeeLocal
         */
        @Override
        public void endTeeLocal() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TEE_LOCAL);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TEELOCAL);
            if (operation.childCount != 1) {
                throw failState("Operation TeeLocal expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionII(Instructions.TEE_LOCAL_, 0, operationData.constants[0], allocateNode());
            afterChild(true, bci - 10);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.TeeLocalRange TeeLocalRange} operation.
         * <p>
         * Signature: TeeLocalRange(value) -> Object
         * <p>
         * A corresponding call to {@link #endTeeLocalRange} is required to end the operation.
         *
         * @param setterValue
         */
        @Override
        public void beginTeeLocalRange(BytecodeLocal[] setterValue) {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TEE_LOCAL_RANGE);
                    serialization.buffer.writeShort(safeCastShort(setterValue.length));
                    if (setterValue.length > 0) {
                        short setterValueDepth = safeCastShort(((SerializationLocal) setterValue[0]).contextDepth);
                        serialization.buffer.writeShort(setterValueDepth);
                        for (int i = 0; i < setterValue.length; i++) {
                            SerializationLocal localImpl = (SerializationLocal) setterValue[i];
                            assert setterValueDepth == safeCastShort(localImpl.contextDepth);
                            serialization.buffer.writeShort(safeCastShort(localImpl.localIndex));
                        }
                    }
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if (setterValue == null) {
                throw failArgument("The setterValue parameter must not be null. Constant operands do not permit null values.");
            }
            int setterIndex = constantPool.addConstant(LocalRangeAccessor.constantOf(setterValue));
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, new int[] {setterIndex});
            beginOperation(Operations.TEELOCALRANGE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.TeeLocalRange TeeLocalRange} operation.
         * <p>
         * Signature: TeeLocalRange(value) -> Object
         *
         * @see #beginTeeLocalRange
         */
        @Override
        public void endTeeLocalRange() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TEE_LOCAL_RANGE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TEELOCALRANGE);
            if (operation.childCount != 1) {
                throw failState("Operation TeeLocalRange expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionII(Instructions.TEE_LOCAL_RANGE_, 0, operationData.constants[0], allocateNode());
            afterChild(true, bci - 10);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Invoke Invoke} operation.
         * <p>
         * Signature: Invoke(root, args...) -> Object
         * <p>
         * A corresponding call to {@link #endInvoke} is required to end the operation.
         */
        @Override
        public void beginInvoke() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_INVOKE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.INVOKE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Invoke Invoke} operation.
         * <p>
         * Signature: Invoke(root, args...) -> Object
         *
         * @see #beginInvoke
         */
        @Override
        public void endInvoke() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_INVOKE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.INVOKE);
            if (operation.childCount < 1) {
                throw failState("Operation Invoke expected at least 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            doEmitVariadic(operation.childCount - 1);
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.INVOKE_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.MaterializeFrame MaterializeFrame} operation.
         * <p>
         * Signature: MaterializeFrame() -> MaterializedFrame
         */
        @Override
        public void emitMaterializeFrame() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_MATERIALIZE_FRAME);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.MATERIALIZE_FRAME_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CreateClosure CreateClosure} operation.
         * <p>
         * Signature: CreateClosure(root) -> TestClosure
         * <p>
         * A corresponding call to {@link #endCreateClosure} is required to end the operation.
         */
        @Override
        public void beginCreateClosure() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_CREATE_CLOSURE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.CREATECLOSURE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CreateClosure CreateClosure} operation.
         * <p>
         * Signature: CreateClosure(root) -> TestClosure
         *
         * @see #beginCreateClosure
         */
        @Override
        public void endCreateClosure() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_CREATE_CLOSURE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.CREATECLOSURE);
            if (operation.childCount != 1) {
                throw failState("Operation CreateClosure expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.CREATE_CLOSURE_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.VoidOperation VoidOperation} operation.
         * <p>
         * Signature: VoidOperation() -> void
         * <p>
         * Does nothing.
         */
        @Override
        public void emitVoidOperation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_VOID_OPERATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.VOID_OPERATION_, 0, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ToBoolean ToBoolean} operation.
         * <p>
         * Signature: ToBoolean(l|b|s) -> boolean
         * <p>
         * A corresponding call to {@link #endToBoolean} is required to end the operation.
         */
        @Override
        public void beginToBoolean() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_TO_BOOLEAN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.TOBOOLEAN, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ToBoolean ToBoolean} operation.
         * <p>
         * Signature: ToBoolean(l|b|s) -> boolean
         *
         * @see #beginToBoolean
         */
        @Override
        public void endToBoolean() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_TO_BOOLEAN);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.TOBOOLEAN);
            if (operation.childCount != 1) {
                throw failState("Operation ToBoolean expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.TO_BOOLEAN_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.GetSourcePosition GetSourcePosition} operation.
         * <p>
         * Signature: GetSourcePosition() -> SourceSection
         */
        @Override
        public void emitGetSourcePosition() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_GET_SOURCE_POSITION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.GET_SOURCE_POSITION_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EnsureAndGetSourcePosition EnsureAndGetSourcePosition} operation.
         * <p>
         * Signature: EnsureAndGetSourcePosition(ensure) -> SourceSection
         * <p>
         * A corresponding call to {@link #endEnsureAndGetSourcePosition} is required to end the operation.
         */
        @Override
        public void beginEnsureAndGetSourcePosition() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ENSURE_AND_GET_SOURCE_POSITION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.ENSUREANDGETSOURCEPOSITION, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EnsureAndGetSourcePosition EnsureAndGetSourcePosition} operation.
         * <p>
         * Signature: EnsureAndGetSourcePosition(ensure) -> SourceSection
         *
         * @see #beginEnsureAndGetSourcePosition
         */
        @Override
        public void endEnsureAndGetSourcePosition() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ENSURE_AND_GET_SOURCE_POSITION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.ENSUREANDGETSOURCEPOSITION);
            if (operation.childCount != 1) {
                throw failState("Operation EnsureAndGetSourcePosition expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.ENSURE_AND_GET_SOURCE_POSITION_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.GetSourcePositions GetSourcePositions} operation.
         * <p>
         * Signature: GetSourcePositions() -> SourceSection[]
         */
        @Override
        public void emitGetSourcePositions() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_GET_SOURCE_POSITIONS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.GET_SOURCE_POSITIONS_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CopyLocalsToFrame CopyLocalsToFrame} operation.
         * <p>
         * Signature: CopyLocalsToFrame(length) -> Frame
         * <p>
         * A corresponding call to {@link #endCopyLocalsToFrame} is required to end the operation.
         */
        @Override
        public void beginCopyLocalsToFrame() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_COPY_LOCALS_TO_FRAME);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.COPYLOCALSTOFRAME, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CopyLocalsToFrame CopyLocalsToFrame} operation.
         * <p>
         * Signature: CopyLocalsToFrame(length) -> Frame
         *
         * @see #beginCopyLocalsToFrame
         */
        @Override
        public void endCopyLocalsToFrame() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_COPY_LOCALS_TO_FRAME);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.COPYLOCALSTOFRAME);
            if (operation.childCount != 1) {
                throw failState("Operation CopyLocalsToFrame expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.COPY_LOCALS_TO_FRAME_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.GetBytecodeLocation GetBytecodeLocation} operation.
         * <p>
         * Signature: GetBytecodeLocation() -> BytecodeLocation
         */
        @Override
        public void emitGetBytecodeLocation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_GET_BYTECODE_LOCATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.GET_BYTECODE_LOCATION_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CollectBytecodeLocations CollectBytecodeLocations} operation.
         * <p>
         * Signature: CollectBytecodeLocations() -> List<BytecodeLocation>
         */
        @Override
        public void emitCollectBytecodeLocations() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_COLLECT_BYTECODE_LOCATIONS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.COLLECT_BYTECODE_LOCATIONS_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CollectSourceLocations CollectSourceLocations} operation.
         * <p>
         * Signature: CollectSourceLocations() -> List<SourceSection>
         */
        @Override
        public void emitCollectSourceLocations() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_COLLECT_SOURCE_LOCATIONS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.COLLECT_SOURCE_LOCATIONS_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CollectAllSourceLocations CollectAllSourceLocations} operation.
         * <p>
         * Signature: CollectAllSourceLocations() -> List<SourceSection[]>
         */
        @Override
        public void emitCollectAllSourceLocations() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_COLLECT_ALL_SOURCE_LOCATIONS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.COLLECT_ALL_SOURCE_LOCATIONS_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ContinueNode Continue} operation.
         * <p>
         * Signature: Continue(result, value) -> Object
         * <p>
         * A corresponding call to {@link #endContinue} is required to end the operation.
         */
        @Override
        public void beginContinue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_CONTINUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.CONTINUE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ContinueNode Continue} operation.
         * <p>
         * Signature: Continue(result, value) -> Object
         *
         * @see #beginContinue
         */
        @Override
        public void endContinue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_CONTINUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.CONTINUE);
            if (operation.childCount != 2) {
                throw failState("Operation Continue expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.CONTINUE_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.CurrentLocation CurrentLocation} operation.
         * <p>
         * Signature: CurrentLocation() -> BytecodeLocation
         */
        @Override
        public void emitCurrentLocation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_CURRENT_LOCATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.CURRENT_LOCATION_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.PrintHere PrintHere} operation.
         * <p>
         * Signature: PrintHere() -> void
         */
        @Override
        public void emitPrintHere() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_PRINT_HERE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if ((instrumentations & 0x1) == 0) {
                return;
            }
            beforeChild();
            doEmitInstructionI(Instructions.PRINT_HERE_, 0, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.IncrementValue IncrementValue} operation.
         * <p>
         * Signature: IncrementValue(value) -> long
         * <p>
         * Increments the instrumented value by 1.
         * <p>
         * A corresponding call to {@link #endIncrementValue} is required to end the operation.
         */
        @Override
        public void beginIncrementValue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_INCREMENT_VALUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if ((instrumentations & 0x2) == 0) {
                return;
            }
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.INCREMENTVALUE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.IncrementValue IncrementValue} operation.
         * <p>
         * Signature: IncrementValue(value) -> long
         *
         * @see #beginIncrementValue
         */
        @Override
        public void endIncrementValue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_INCREMENT_VALUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if ((instrumentations & 0x2) == 0) {
                return;
            }
            OperationStackEntry operation = endOperation(Operations.INCREMENTVALUE);
            if (operation.childCount != 1) {
                throw failState("Operation IncrementValue expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.INCREMENT_VALUE_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.DoubleValue DoubleValue} operation.
         * <p>
         * Signature: DoubleValue(value) -> long
         * <p>
         * A corresponding call to {@link #endDoubleValue} is required to end the operation.
         */
        @Override
        public void beginDoubleValue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_DOUBLE_VALUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            if ((instrumentations & 0x4) == 0) {
                return;
            }
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.DOUBLEVALUE, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.DoubleValue DoubleValue} operation.
         * <p>
         * Signature: DoubleValue(value) -> long
         *
         * @see #beginDoubleValue
         */
        @Override
        public void endDoubleValue() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_DOUBLE_VALUE);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if ((instrumentations & 0x4) == 0) {
                return;
            }
            OperationStackEntry operation = endOperation(Operations.DOUBLEVALUE);
            if (operation.childCount != 1) {
                throw failState("Operation DoubleValue expected exactly 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.DOUBLE_VALUE_, 0, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EnableIncrementValueInstrumentation EnableIncrementValueInstrumentation} operation.
         * <p>
         * Signature: EnableIncrementValueInstrumentation() -> void
         */
        @Override
        public void emitEnableIncrementValueInstrumentation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_ENABLE_INCREMENT_VALUE_INSTRUMENTATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.ENABLE_INCREMENT_VALUE_INSTRUMENTATION_, 0, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Add Add} operation.
         * <p>
         * Signature: Add(left, right) -> long
         * <p>
         * A corresponding call to {@link #endAdd} is required to end the operation.
         */
        @Override
        public void beginAdd() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_ADD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.ADD, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Add Add} operation.
         * <p>
         * Signature: Add(left, right) -> long
         *
         * @see #beginAdd
         */
        @Override
        public void endAdd() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_ADD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.ADD);
            if (operation.childCount != 2) {
                throw failState("Operation Add expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.ADD_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Mod Mod} operation.
         * <p>
         * Signature: Mod(left, right) -> long
         * <p>
         * A corresponding call to {@link #endMod} is required to end the operation.
         */
        @Override
        public void beginMod() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_MOD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.MOD, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Mod Mod} operation.
         * <p>
         * Signature: Mod(left, right) -> long
         *
         * @see #beginMod
         */
        @Override
        public void endMod() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_MOD);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.MOD);
            if (operation.childCount != 2) {
                throw failState("Operation Mod expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.MOD_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Less Less} operation.
         * <p>
         * Signature: Less(left, right) -> boolean
         * <p>
         * A corresponding call to {@link #endLess} is required to end the operation.
         */
        @Override
        public void beginLess() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_LESS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomOperationData operationData = new CustomOperationData(EMPTY_INT_ARRAY, EMPTY_INT_ARRAY);
            beginOperation(Operations.LESS, operationData);
        }

        /**
         * Ends a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.Less Less} operation.
         * <p>
         * Signature: Less(left, right) -> boolean
         *
         * @see #beginLess
         */
        @Override
        public void endLess() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_LESS);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.LESS);
            if (operation.childCount != 2) {
                throw failState("Operation Less expected exactly 2 children, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomOperationData operationData)) {
                throw assertionFailed("Data class CustomOperationData expected, but was " + operation.data);
            }
            doEmitInstructionI(Instructions.LESS_, -1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.EnableDoubleValueInstrumentation EnableDoubleValueInstrumentation} operation.
         * <p>
         * Signature: EnableDoubleValueInstrumentation() -> void
         */
        @Override
        public void emitEnableDoubleValueInstrumentation() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_ENABLE_DOUBLE_VALUE_INSTRUMENTATION);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.ENABLE_DOUBLE_VALUE_INSTRUMENTATION_, 0, allocateNode());
            afterChild(false, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ExplicitBindingsTest ExplicitBindingsTest} operation.
         * <p>
         * Signature: ExplicitBindingsTest() -> Bindings
         */
        @Override
        public void emitExplicitBindingsTest() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_EXPLICIT_BINDINGS_TEST);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.EXPLICIT_BINDINGS_TEST_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Emits a custom {@link com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter.ImplicitBindingsTest ImplicitBindingsTest} operation.
         * <p>
         * Signature: ImplicitBindingsTest() -> Bindings
         */
        @Override
        public void emitImplicitBindingsTest() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_EMIT_IMPLICIT_BINDINGS_TEST);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            doEmitInstructionI(Instructions.IMPLICIT_BINDINGS_TEST_, 1, allocateNode());
            afterChild(true, bci - 6);
        }

        /**
         * Begins a custom ScAnd operation.
         * <p>
         * Signature: ScAnd(value) -> Object
         * <p>
         * A corresponding call to {@link #endScAnd} is required to end the operation.
         */
        @Override
        public void beginScAnd() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_SC_AND);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomShortCircuitOperationData operationData = new CustomShortCircuitOperationData();
            beginOperation(Operations.SCAND, operationData);
        }

        /**
         * Ends a custom ScAnd operation.
         * <p>
         * Signature: ScAnd(value) -> Object
         *
         * @see #beginScAnd
         */
        @Override
        public void endScAnd() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_SC_AND);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.SCAND);
            if (operation.childCount == 0) {
                throw failState("Operation ScAnd expected at least 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomShortCircuitOperationData operationData)) {
                throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operation.data);
            }
            for (int site : operationData.branchFixupBcis) {
                BYTES.putInt(bc, site, bci);
            }
            afterChild(true, operationData.childBci);
        }

        /**
         * Begins a custom ScOr operation.
         * <p>
         * Signature: ScOr(value) -> Object
         * <p>
         * ScOr returns the first truthy operand value.
         * <p>
         * A corresponding call to {@link #endScOr} is required to end the operation.
         */
        @Override
        public void beginScOr() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_BEGIN_SC_OR);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            validateRootOperationBegin();
            beforeChild();
            CustomShortCircuitOperationData operationData = new CustomShortCircuitOperationData();
            beginOperation(Operations.SCOR, operationData);
        }

        /**
         * Ends a custom ScOr operation.
         * <p>
         * Signature: ScOr(value) -> Object
         *
         * @see #beginScOr
         */
        @Override
        public void endScOr() {
            if (serialization != null) {
                try {
                    serialization.buffer.writeShort(SerializationState.CODE_END_SC_OR);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            OperationStackEntry operation = endOperation(Operations.SCOR);
            if (operation.childCount == 0) {
                throw failState("Operation ScOr expected at least 1 child, but " + operation.childCount + " provided. This is probably a bug in the parser.");
            }
            if (!(operation.data instanceof CustomShortCircuitOperationData operationData)) {
                throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operation.data);
            }
            for (int site : operationData.branchFixupBcis) {
                BYTES.putInt(bc, site, bci);
            }
            afterChild(true, operationData.childBci);
        }

        private void markReachable(boolean newReachable) {
            this.reachable = newReachable;
            try {
                for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                    if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                        i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                        continue;
                    }
                    OperationStackEntry operation = operationStack[i];
                    switch (operation.operation) {
                        case Operations.ROOT :
                        {
                            if (!(operationStack[i].data instanceof RootData operationData)) {
                                throw assertionFailed("Data class RootData expected, but was " + operationStack[i].data);
                            }
                            operationData.reachable = newReachable;
                            return;
                        }
                        case Operations.IFTHEN :
                        {
                            if (!(operationStack[i].data instanceof IfThenData operationData)) {
                                throw assertionFailed("Data class IfThenData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                // Unreachable condition branch makes the if and parent block unreachable.
                                operationData.thenReachable = newReachable;
                                continue;
                            } else if (operation.childCount == 1) {
                                operationData.thenReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.IFTHENELSE :
                        {
                            if (!(operationStack[i].data instanceof IfThenElseData operationData)) {
                                throw assertionFailed("Data class IfThenElseData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                // Unreachable condition branch makes the if, then and parent block unreachable.
                                operationData.thenReachable = newReachable;
                                operationData.elseReachable = newReachable;
                                continue;
                            } else if (operation.childCount == 1) {
                                operationData.thenReachable = newReachable;
                            } else if (operation.childCount == 2) {
                                operationData.elseReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.CONDITIONAL :
                        {
                            if (!(operationStack[i].data instanceof ConditionalData operationData)) {
                                throw assertionFailed("Data class ConditionalData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                // Unreachable condition branch makes the if, then and parent block unreachable.
                                operationData.thenReachable = newReachable;
                                operationData.elseReachable = newReachable;
                                continue;
                            } else if (operation.childCount == 1) {
                                operationData.thenReachable = newReachable;
                            } else if (operation.childCount == 2) {
                                operationData.elseReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.WHILE :
                        {
                            if (!(operationStack[i].data instanceof WhileData operationData)) {
                                throw assertionFailed("Data class WhileData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                operationData.bodyReachable = newReachable;
                                continue;
                            } else if (operation.childCount == 1) {
                                operationData.bodyReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.TRYCATCH :
                        {
                            if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                                throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                operationData.tryReachable = newReachable;
                            } else if (operation.childCount == 1) {
                                operationData.catchReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.TRYFINALLY :
                        {
                            if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                                throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                operationData.tryReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                        case Operations.TRYCATCHOTHERWISE :
                        {
                            if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                                throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                            }
                            if (operation.childCount == 0) {
                                operationData.tryReachable = newReachable;
                            } else if (operation.childCount == 1) {
                                operationData.catchReachable = newReachable;
                            } else {
                                // Invalid child index, but we will fail in the end method.
                            }
                            return;
                        }
                    }
                }
            } finally {
                assert updateReachable() == this.reachable : "Inconsistent reachability detected.";
            }
        }

        /**
         * Updates the reachable field from the current operation. Typically invoked when the operation ended or the child is changing.
         */
        private boolean updateReachable() {
            boolean oldReachable = reachable;
            for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                OperationStackEntry operation = operationStack[i];
                switch (operation.operation) {
                    case Operations.ROOT :
                    {
                        if (!(operationStack[i].data instanceof RootData operationData)) {
                            throw assertionFailed("Data class RootData expected, but was " + operationStack[i].data);
                        }
                        this.reachable = operationData.reachable;
                        return oldReachable;
                    }
                    case Operations.IFTHEN :
                    {
                        if (!(operationStack[i].data instanceof IfThenData operationData)) {
                            throw assertionFailed("Data class IfThenData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            continue;
                        } else if (operation.childCount == 1) {
                            this.reachable = operationData.thenReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.IFTHENELSE :
                    {
                        if (!(operationStack[i].data instanceof IfThenElseData operationData)) {
                            throw assertionFailed("Data class IfThenElseData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            // Unreachable condition branch makes the if, then and parent block unreachable.
                            continue;
                        } else if (operation.childCount == 1) {
                            this.reachable = operationData.thenReachable;
                        } else if (operation.childCount == 2) {
                            this.reachable = operationData.elseReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.CONDITIONAL :
                    {
                        if (!(operationStack[i].data instanceof ConditionalData operationData)) {
                            throw assertionFailed("Data class ConditionalData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            // Unreachable condition branch makes the if, then and parent block unreachable.
                            continue;
                        } else if (operation.childCount == 1) {
                            this.reachable = operationData.thenReachable;
                        } else if (operation.childCount == 2) {
                            this.reachable = operationData.elseReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.WHILE :
                    {
                        if (!(operationStack[i].data instanceof WhileData operationData)) {
                            throw assertionFailed("Data class WhileData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            continue;
                        } else if (operation.childCount == 1) {
                            this.reachable = operationData.bodyReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.TRYCATCH :
                    {
                        if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                            throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            this.reachable = operationData.tryReachable;
                        } else if (operation.childCount == 1) {
                            this.reachable = operationData.catchReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.TRYFINALLY :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            this.reachable = operationData.tryReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                    case Operations.TRYCATCHOTHERWISE :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operation.childCount == 0) {
                            this.reachable = operationData.tryReachable;
                        } else if (operation.childCount == 2) {
                            this.reachable = operationData.catchReachable;
                        } else {
                            // Invalid child index, but we will fail in the end method.
                        }
                        return oldReachable;
                    }
                }
            }
            return oldReachable;
        }

        private void beginOperation(int id, Object data) {
            if (operationSp == operationStack.length) {
                operationStack = Arrays.copyOf(operationStack, operationStack.length * 2);
            }
            operationStack[operationSp++] = new OperationStackEntry(id, data, operationSequenceNumber++);
        }

        private OperationStackEntry endOperation(int id) {
            if (operationSp == 0) {
                throw failState("Unexpected operation end - there are no operations on the stack. Did you forget a beginRoot()?");
            }
            OperationStackEntry entry = operationStack[operationSp - 1];
            if (entry.operation != id) {
                throw failState("Unexpected operation end, expected end" + OPERATION_NAMES[entry.operation] + ", but got end" +  OPERATION_NAMES[id]);
            }
            if (entry.declaredLabels != null) {
                for (BytecodeLabel label : entry.declaredLabels) {
                    BytecodeLabelImpl impl = (BytecodeLabelImpl) label;
                    if (!impl.isDefined()) {
                        throw failState("Operation " + OPERATION_NAMES[id] + " ended without emitting one or more declared labels.");
                    }
                }
            }
            operationStack[operationSp - 1] = null;
            operationSp -= 1;
            return entry;
        }

        private void validateRootOperationBegin() {
            if (rootOperationSp == -1) {
                throw failState("Unexpected operation emit - no root operation present. Did you forget a beginRoot()?");
            }
        }

        private void beforeChild() {
            if (operationSp == 0) {
                return;
            }
            int childIndex = operationStack[operationSp - 1].childCount;
            switch (operationStack[operationSp - 1].operation) {
                case Operations.BLOCK :
                {
                    if (!(operationStack[operationSp - 1].data instanceof BlockData operationData)) {
                        throw assertionFailed("Data class BlockData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (operationData.producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.ROOT :
                {
                    if (!(operationStack[operationSp - 1].data instanceof RootData operationData)) {
                        throw assertionFailed("Data class RootData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (operationData.producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.SOURCE :
                {
                    if (!(operationStack[operationSp - 1].data instanceof SourceData operationData)) {
                        throw assertionFailed("Data class SourceData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (operationData.producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.SOURCESECTION :
                {
                    if (!(operationStack[operationSp - 1].data instanceof SourceSectionData operationData)) {
                        throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (operationData.producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.SCAND :
                {
                    if (!(operationStack[operationSp - 1].data instanceof CustomShortCircuitOperationData operationData)) {
                        throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex != 0) {
                        doEmitInstruction(Instructions.DUP, 1);
                        doEmitInstructionI(Instructions.TO_BOOLEAN_, 0, allocateNode());
                        if (this.reachable) {
                            operationData.branchFixupBcis.add(bci + 2);
                        }
                        doEmitInstructionII(Instructions.SC_AND_, -2, UNINITIALIZED, allocateBranchProfile());
                    }
                    break;
                }
                case Operations.SCOR :
                {
                    if (!(operationStack[operationSp - 1].data instanceof CustomShortCircuitOperationData operationData)) {
                        throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex != 0) {
                        doEmitInstruction(Instructions.DUP, 1);
                        doEmitInstructionI(Instructions.TO_BOOLEAN_, 0, allocateNode());
                        if (this.reachable) {
                            operationData.branchFixupBcis.add(bci + 2);
                        }
                        doEmitInstructionII(Instructions.SC_OR_, -2, UNINITIALIZED, allocateBranchProfile());
                    }
                    break;
                }
                case Operations.IFTHEN :
                case Operations.IFTHENELSE :
                case Operations.CONDITIONAL :
                case Operations.TRYFINALLY :
                    if (childIndex >= 1) {
                        updateReachable();
                    }
                    break;
                case Operations.TRYCATCH :
                {
                    if (!(operationStack[operationSp - 1].data instanceof TryCatchData operationData)) {
                        throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 1) {
                        updateReachable();
                        // The exception dispatch logic pushes the exception onto the stack.
                        currentStackHeight = currentStackHeight + 1;
                        updateMaxStackHeight(currentStackHeight);
                    }
                    break;
                }
                case Operations.TRYCATCHOTHERWISE :
                {
                    if (!(operationStack[operationSp - 1].data instanceof TryFinallyData operationData)) {
                        throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 1) {
                        updateReachable();
                        // The exception dispatch logic pushes the exception onto the stack.
                        currentStackHeight = currentStackHeight + 1;
                        updateMaxStackHeight(currentStackHeight);
                    }
                    break;
                }
                case Operations.WHILE :
                case Operations.FINALLYHANDLER :
                case Operations.LOADLOCALMATERIALIZED :
                case Operations.STORELOCAL :
                case Operations.STORELOCALMATERIALIZED :
                case Operations.RETURN :
                case Operations.YIELD :
                case Operations.TAG :
                case Operations.EARLYRETURN :
                case Operations.ADDOPERATION :
                case Operations.TOSTRING :
                case Operations.CALL :
                case Operations.ADDCONSTANTOPERATION :
                case Operations.ADDCONSTANTOPERATIONATEND :
                case Operations.VERYCOMPLEXOPERATION :
                case Operations.THROWOPERATION :
                case Operations.READEXCEPTIONOPERATION :
                case Operations.ALWAYSBOXOPERATION :
                case Operations.APPENDEROPERATION :
                case Operations.TEELOCAL :
                case Operations.TEELOCALRANGE :
                case Operations.INVOKE :
                case Operations.CREATECLOSURE :
                case Operations.TOBOOLEAN :
                case Operations.ENSUREANDGETSOURCEPOSITION :
                case Operations.COPYLOCALSTOFRAME :
                case Operations.CONTINUE :
                case Operations.INCREMENTVALUE :
                case Operations.DOUBLEVALUE :
                case Operations.ADD :
                case Operations.MOD :
                case Operations.LESS :
                    break;
                default :
                    throw assertionFailed("beforeChild should not be called on an operation with no children.");
            }
        }

        private void afterChild(boolean producedValue, int childBci) {
            if (operationSp == 0) {
                return;
            }
            int childIndex = operationStack[operationSp - 1].childCount;
            switch (operationStack[operationSp - 1].operation) {
                case Operations.BLOCK :
                {
                    if (!(operationStack[operationSp - 1].data instanceof BlockData operationData)) {
                        throw assertionFailed("Data class BlockData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.ROOT :
                {
                    if (!(operationStack[operationSp - 1].data instanceof RootData operationData)) {
                        throw assertionFailed("Data class RootData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.SOURCE :
                {
                    if (!(operationStack[operationSp - 1].data instanceof SourceData operationData)) {
                        throw assertionFailed("Data class SourceData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.SOURCESECTION :
                {
                    if (!(operationStack[operationSp - 1].data instanceof SourceSectionData operationData)) {
                        throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.TAG :
                {
                    if (!(operationStack[operationSp - 1].data instanceof TagOperationData operationData)) {
                        throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.IFTHEN :
                {
                    if ((childIndex == 0) && !producedValue) {
                        throw failState("Operation IfThen expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    } else if ((childIndex == 1) && producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    if (!(operationStack[operationSp - 1].data instanceof IfThenData operationData)) {
                        throw assertionFailed("Data class IfThenData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        if (reachable) {
                            operationData.falseBranchFixupBci = bci + 2;
                        }
                        doEmitInstructionII(Instructions.BRANCH_FALSE, -1, UNINITIALIZED, allocateBranchProfile());
                    } else {
                        int toUpdate = operationData.falseBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    }
                    break;
                }
                case Operations.IFTHENELSE :
                {
                    if ((childIndex == 0) && !producedValue) {
                        throw failState("Operation IfThenElse expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    } else if ((childIndex == 1 || childIndex == 2) && producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    if (!(operationStack[operationSp - 1].data instanceof IfThenElseData operationData)) {
                        throw assertionFailed("Data class IfThenElseData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        if (reachable) {
                            operationData.falseBranchFixupBci = bci + 2;
                        }
                        doEmitInstructionII(Instructions.BRANCH_FALSE, -1, UNINITIALIZED, allocateBranchProfile());
                    } else if (childIndex == 1) {
                        if (reachable) {
                            operationData.endBranchFixupBci = bci + 2;
                        }
                        doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
                        int toUpdate = operationData.falseBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    } else {
                        int toUpdate = operationData.endBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    }
                    break;
                }
                case Operations.CONDITIONAL :
                {
                    if (!producedValue) {
                        throw failState("Operation Conditional expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    if (!(operationStack[operationSp - 1].data instanceof ConditionalData operationData)) {
                        throw assertionFailed("Data class ConditionalData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        if (reachable) {
                            operationData.falseBranchFixupBci = bci + 2;
                        }
                        doEmitInstructionII(Instructions.BRANCH_FALSE, -1, UNINITIALIZED, allocateBranchProfile());
                    } else if (childIndex == 1) {
                        if (reachable) {
                            operationData.endBranchFixupBci = bci + 2;
                            doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
                        }
                        currentStackHeight -= 1;
                        int toUpdate = operationData.falseBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    } else {
                        int toUpdate = operationData.endBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    }
                    break;
                }
                case Operations.WHILE :
                {
                    if ((childIndex == 0) && !producedValue) {
                        throw failState("Operation While expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    } else if ((childIndex == 1) && producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    if (!(operationStack[operationSp - 1].data instanceof WhileData operationData)) {
                        throw assertionFailed("Data class WhileData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        if (reachable) {
                            operationData.endBranchFixupBci = bci + 2;
                        }
                        doEmitInstructionII(Instructions.BRANCH_FALSE, -1, UNINITIALIZED, allocateBranchProfile());
                    } else {
                        int toUpdate = operationData.endBranchFixupBci;
                        if (toUpdate != UNINITIALIZED) {
                            doEmitInstructionII(Instructions.BRANCH_BACKWARD, 0, operationData.whileStartBci, BYTES.getInt(bc, toUpdate + 4 /* loop branch profile */));
                            BYTES.putInt(bc, toUpdate, bci);
                        }
                    }
                    break;
                }
                case Operations.TRYCATCH :
                {
                    if (producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    if (!(operationStack[operationSp - 1].data instanceof TryCatchData operationData)) {
                        throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        if (operationData.operationReachable) {
                            int tryEndBci = bci;
                            if (operationData.tryReachable) {
                                operationData.endBranchFixupBci = bci + 2;
                                doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
                            }
                            int handlerSp = currentStackHeight + 1;
                            patchHandlerTable(operationData.extraTableEntriesStart, operationData.extraTableEntriesEnd, operationData.handlerId, bci, handlerSp);
                            doCreateExceptionHandler(operationData.tryStartBci, tryEndBci, HANDLER_CUSTOM, bci, handlerSp);
                        }
                    } else if (childIndex == 1) {
                        // pop the exception
                        doEmitInstruction(Instructions.POP, -1);
                        if (operationData.endBranchFixupBci != UNINITIALIZED) {
                            BYTES.putInt(bc, operationData.endBranchFixupBci, bci);
                        }
                    }
                    break;
                }
                case Operations.TRYFINALLY :
                {
                    if (producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.TRYCATCHOTHERWISE :
                {
                    if (producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    if (!(operationStack[operationSp - 1].data instanceof TryFinallyData operationData)) {
                        throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    if (childIndex == 0) {
                        int handlerSp = currentStackHeight + 1 /* reserve space for the exception */;
                        updateMaxStackHeight(handlerSp);
                        int exHandlerIndex = UNINITIALIZED;
                        if (operationData.operationReachable) {
                            // register exception table entry
                            exHandlerIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, handlerSp);
                        }
                        // emit handler for normal completion case
                        doEmitFinallyHandler(operationData, operationSp - 1);
                        // the operation was popped, so manually update reachability. try is reachable if neither it nor the finally handler exited early.
                        operationData.tryReachable = operationData.tryReachable && this.reachable;
                        if (this.reachable) {
                            operationData.endBranchFixupBci = bci + 2;
                            doEmitInstructionI(Instructions.BRANCH, 0, UNINITIALIZED);
                        }
                        if (operationData.operationReachable) {
                            // update exception table; force handler code to be reachable
                            this.reachable = true;
                            patchHandlerTable(operationData.extraTableEntriesStart, operationData.extraTableEntriesEnd, operationData.handlerId, bci, handlerSp);
                            if (exHandlerIndex != UNINITIALIZED) {
                                handlerTable[exHandlerIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = bci;
                            }
                        }
                    } else {
                        // pop the exception
                        doEmitInstruction(Instructions.POP, -1);
                        if (operationData.endBranchFixupBci != UNINITIALIZED) {
                            BYTES.putInt(bc, operationData.endBranchFixupBci, bci);
                        }
                    }
                    break;
                }
                case Operations.FINALLYHANDLER :
                {
                    if (producedValue) {
                        doEmitInstruction(Instructions.POP, -1);
                    }
                    break;
                }
                case Operations.LOADLOCALMATERIALIZED :
                {
                    if (!producedValue) {
                        throw failState("Operation LoadLocalMaterialized expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.STORELOCAL :
                {
                    if (!producedValue) {
                        throw failState("Operation StoreLocal expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.STORELOCALMATERIALIZED :
                {
                    if (!producedValue) {
                        throw failState("Operation StoreLocalMaterialized expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.RETURN :
                {
                    if (!producedValue) {
                        throw failState("Operation Return expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    if (!(operationStack[operationSp - 1].data instanceof ReturnOperationData operationData)) {
                        throw assertionFailed("Data class ReturnOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.producedValue = producedValue;
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.YIELD :
                {
                    if (!producedValue) {
                        throw failState("Operation Yield expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.EARLYRETURN :
                {
                    if (!producedValue) {
                        throw failState("Operation EarlyReturn expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ADDOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation AddOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.TOSTRING :
                {
                    if (!producedValue) {
                        throw failState("Operation ToString expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.CALL :
                {
                    if (!producedValue) {
                        throw failState("Operation Call expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ADDCONSTANTOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation AddConstantOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ADDCONSTANTOPERATIONATEND :
                {
                    if (!producedValue) {
                        throw failState("Operation AddConstantOperationAtEnd expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.VERYCOMPLEXOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation VeryComplexOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.THROWOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation ThrowOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.READEXCEPTIONOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation ReadExceptionOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ALWAYSBOXOPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation AlwaysBoxOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.APPENDEROPERATION :
                {
                    if (!producedValue) {
                        throw failState("Operation AppenderOperation expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.TEELOCAL :
                {
                    if (!producedValue) {
                        throw failState("Operation TeeLocal expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.TEELOCALRANGE :
                {
                    if (!producedValue) {
                        throw failState("Operation TeeLocalRange expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.INVOKE :
                {
                    if (!producedValue) {
                        throw failState("Operation Invoke expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.CREATECLOSURE :
                {
                    if (!producedValue) {
                        throw failState("Operation CreateClosure expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.TOBOOLEAN :
                {
                    if (!producedValue) {
                        throw failState("Operation ToBoolean expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ENSUREANDGETSOURCEPOSITION :
                {
                    if (!producedValue) {
                        throw failState("Operation EnsureAndGetSourcePosition expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.COPYLOCALSTOFRAME :
                {
                    if (!producedValue) {
                        throw failState("Operation CopyLocalsToFrame expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.CONTINUE :
                {
                    if (!producedValue) {
                        throw failState("Operation Continue expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.INCREMENTVALUE :
                {
                    if (!producedValue) {
                        throw failState("Operation IncrementValue expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.DOUBLEVALUE :
                {
                    if (!producedValue) {
                        throw failState("Operation DoubleValue expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.ADD :
                {
                    if (!producedValue) {
                        throw failState("Operation Add expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.MOD :
                {
                    if (!producedValue) {
                        throw failState("Operation Mod expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.LESS :
                {
                    if (!producedValue) {
                        throw failState("Operation Less expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    break;
                }
                case Operations.SCAND :
                {
                    if (!producedValue) {
                        throw failState("Operation ScAnd expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    if (!(operationStack[operationSp - 1].data instanceof CustomShortCircuitOperationData operationData)) {
                        throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.childBci = childBci;
                    break;
                }
                case Operations.SCOR :
                {
                    if (!producedValue) {
                        throw failState("Operation ScOr expected a value-producing child at position " + childIndex + ", but a void one was provided.");
                    }
                    if (!(operationStack[operationSp - 1].data instanceof CustomShortCircuitOperationData operationData)) {
                        throw assertionFailed("Data class CustomShortCircuitOperationData expected, but was " + operationStack[operationSp - 1].data);
                    }
                    operationData.childBci = childBci;
                    break;
                }
            }
            operationStack[operationSp - 1].childCount = childIndex + 1;
        }

        private void updateMaxStackHeight(int stackHeight) {
            maxStackHeight = Math.max(maxStackHeight, stackHeight);
            if (maxStackHeight > Short.MAX_VALUE) {
                throw BytecodeEncodingException.create("Maximum stack height exceeded.");
            }
        }

        private void ensureBytecodeCapacity(int size) {
            if (size > bc.length) {
                bc = Arrays.copyOf(bc, Math.max(size, bc.length * 2));
            }
        }

        private void doEmitVariadic(int count) {
            currentStackHeight -= count - 1;
            if (!reachable) {
                return;
            }
            if (count <= 8) {
                doEmitInstruction(safeCastShort(Instructions.LOAD_VARIADIC_0 + count), 0);
            } else {
                updateMaxStackHeight(currentStackHeight + count);
                int elementCount = count + 1;
                doEmitInstruction(Instructions.CONSTANT_NULL, 0);
                while (elementCount > 8) {
                    doEmitInstruction(Instructions.LOAD_VARIADIC_8, 0);
                    elementCount -= 7;
                }
                if (elementCount > 0) {
                    doEmitInstruction(safeCastShort(Instructions.LOAD_VARIADIC_0 + elementCount), 0);
                }
                doEmitInstruction(Instructions.MERGE_VARIADIC, 0);
            }
            if (count == 0) {
                // pushed empty array
                updateMaxStackHeight(currentStackHeight);
            }
        }

        private void doEmitFinallyHandler(TryFinallyData TryFinallyData, int finallyOperationSp) {
            assert TryFinallyData.finallyHandlerSp == UNINITIALIZED;
            try {
                TryFinallyData.finallyHandlerSp = operationSp;
                beginFinallyHandler(safeCastShort(finallyOperationSp));
                TryFinallyData.finallyGenerator.run();
                endFinallyHandler();
            } finally {
                TryFinallyData.finallyHandlerSp = UNINITIALIZED;
            }
        }

        private int doCreateExceptionHandler(int startBci, int endBci, int handlerKind, int handlerBci, int handlerSp) {
            assert startBci <= endBci;
            // Don't create empty handler ranges.
            if (startBci == endBci) {
                return UNINITIALIZED;
            }
            // If the previous entry is for the same handler and the ranges are contiguous, combine them.
            if (handlerTableSize > 0) {
                int previousEntry = handlerTableSize - EXCEPTION_HANDLER_LENGTH;
                int previousEndBci = handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI];
                int previousKind = handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_KIND];
                int previousHandlerBci = handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                if (previousEndBci == startBci && previousKind == handlerKind && previousHandlerBci == handlerBci) {
                    handlerTable[previousEntry + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci;
                    return UNINITIALIZED;
                }
            }
            if (handlerTable.length <= handlerTableSize + EXCEPTION_HANDLER_LENGTH) {
                handlerTable = Arrays.copyOf(handlerTable, handlerTable.length * 2);
            }
            int result = handlerTableSize;
            handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_START_BCI] = startBci;
            handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_END_BCI] = endBci;
            handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_KIND] = handlerKind;
            handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci;
            handlerTable[handlerTableSize + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp;
            handlerTableSize += EXCEPTION_HANDLER_LENGTH;
            return result;
        }

        private void doEmitSourceInfo(int sourceIndex, int startBci, int endBci, int start, int length) {
            assert parseSources;
            if (rootOperationSp == -1) {
                return;
            }
            int index = sourceInfoIndex;
            int prevIndex = index - SOURCE_INFO_LENGTH;
            if (prevIndex >= 0
                 && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_SOURCE]) == sourceIndex
                 && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START]) == start
                 && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_LENGTH]) == length) {
                if ((sourceInfo[prevIndex + SOURCE_INFO_OFFSET_START_BCI]) == startBci
                     && (sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == endBci) {
                    // duplicate entry
                    return;
                } else if ((sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI]) == startBci) {
                    // contiguous entry
                    sourceInfo[prevIndex + SOURCE_INFO_OFFSET_END_BCI] = endBci;
                    return;
                }
            }
            if (index >= sourceInfo.length) {
                sourceInfo = Arrays.copyOf(sourceInfo, sourceInfo.length * 2);
            }
            sourceInfo[index + SOURCE_INFO_OFFSET_START_BCI] = startBci;
            sourceInfo[index + SOURCE_INFO_OFFSET_END_BCI] = endBci;
            sourceInfo[index + SOURCE_INFO_OFFSET_SOURCE] = sourceIndex;
            sourceInfo[index + SOURCE_INFO_OFFSET_START] = start;
            sourceInfo[index + SOURCE_INFO_OFFSET_LENGTH] = length;
            sourceInfoIndex = index + SOURCE_INFO_LENGTH;
        }

        private void finish() {
            if (operationSp != 0) {
                throw failState("Unexpected parser end - there are still operations on the stack. Did you forget to end them?");
            }
            if (reparseReason == null) {
                nodes.setNodes(builtNodes.toArray(new BasicInterpreterWithUncached[0]));
            }
            assert nodes.validate();
        }

        /**
         * Walks the operation stack, emitting instructions for any operations that need to complete before the branch (and fixing up bytecode ranges to exclude these instructions).
         */
        private void beforeEmitBranch(int declaringOperationSp) {
            /**
             * Emit "exit" instructions for any pending operations, and close any bytecode ranges that should not apply to the emitted instructions.
             */
            boolean needsRewind = false;
            for (int i = operationSp - 1; i >= declaringOperationSp + 1; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.TAG :
                    {
                        if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                            throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                        }
                        if (reachable) {
                            doEmitInstructionI(Instructions.TAG_LEAVE_VOID, 0, operationData.nodeId);
                            doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight);
                            needsRewind = true;
                        }
                        break;
                    }
                    case Operations.TRYFINALLY :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */) {
                            if (reachable) {
                                int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                                if (handlerTableIndex != UNINITIALIZED) {
                                    if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                        operationData.extraTableEntriesStart = handlerTableIndex;
                                    }
                                    operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                                }
                                needsRewind = true;
                            }
                            doEmitFinallyHandler(operationData, i);
                        }
                        break;
                    }
                    case Operations.TRYCATCHOTHERWISE :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */) {
                            if (reachable) {
                                int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                                if (handlerTableIndex != UNINITIALIZED) {
                                    if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                        operationData.extraTableEntriesStart = handlerTableIndex;
                                    }
                                    operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                                }
                                needsRewind = true;
                            }
                            doEmitFinallyHandler(operationData, i);
                        }
                        break;
                    }
                    case Operations.TRYCATCH :
                    {
                        if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                            throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */ && reachable) {
                            int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                            if (handlerTableIndex != UNINITIALIZED) {
                                if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                    operationData.extraTableEntriesStart = handlerTableIndex;
                                }
                                operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                            }
                            needsRewind = true;
                        }
                        break;
                    }
                    case Operations.SOURCESECTION :
                    {
                        if (!(operationStack[i].data instanceof SourceSectionData operationData)) {
                            throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[i].data);
                        }
                        doEmitSourceInfo(operationData.sourceIndex, operationData.startBci, bci, operationData.start, operationData.length);
                        needsRewind = true;
                        break;
                    }
                    case Operations.BLOCK :
                    {
                        if (!(operationStack[i].data instanceof BlockData operationData)) {
                            throw assertionFailed("Data class BlockData expected, but was " + operationStack[i].data);
                        }
                        for (int j = 0; j < operationData.numLocals; j++) {
                            locals[operationData.locals[j] + LOCALS_OFFSET_END_BCI] = bci;
                            doEmitInstructionS(Instructions.CLEAR_LOCAL, 0, safeCastShort(locals[operationData.locals[j] + LOCALS_OFFSET_FRAME_INDEX]));
                            needsRewind = true;
                        }
                        break;
                    }
                }
            }
            /**
             * Now that all "exit" instructions have been emitted, reopen bytecode ranges.
             */
            if (needsRewind) {
                for (int i = declaringOperationSp + 1; i < operationSp; i++) {
                    if (operationStack[i].operation == Operations.TRYFINALLY || operationStack[i].operation == Operations.TRYCATCHOTHERWISE) {
                        int finallyHandlerSp = ((TryFinallyData) operationStack[i].data).finallyHandlerSp;
                        if (finallyHandlerSp != UNINITIALIZED) {
                            i = finallyHandlerSp - 1;
                            continue;
                        }
                    }
                    switch (operationStack[i].operation) {
                        case Operations.TAG :
                        {
                            if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                                throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                            }
                            operationData.handlerStartBci = bci;
                            break;
                        }
                        case Operations.TRYFINALLY :
                        case Operations.TRYCATCHOTHERWISE :
                            if (operationStack[i].childCount == 0 /* still in try */) {
                                if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                                    throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                                }
                                operationData.tryStartBci = bci;
                            }
                            break;
                        case Operations.TRYCATCH :
                            if (operationStack[i].childCount == 0 /* still in try */) {
                                if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                                    throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                                }
                                operationData.tryStartBci = bci;
                            }
                            break;
                        case Operations.SOURCESECTION :
                        {
                            if (!(operationStack[i].data instanceof SourceSectionData operationData)) {
                                throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[i].data);
                            }
                            operationData.startBci = bci;
                            break;
                        }
                        case Operations.BLOCK :
                        {
                            if (!(operationStack[i].data instanceof BlockData operationData)) {
                                throw assertionFailed("Data class BlockData expected, but was " + operationStack[i].data);
                            }
                            for (int j = 0; j < operationData.numLocals; j++) {
                                int prevTableIndex = operationData.locals[j];
                                // Create a new table entry with a new bytecode range and the same metadata.
                                int localIndex = locals[prevTableIndex + LOCALS_OFFSET_LOCAL_INDEX];
                                int frameIndex = locals[prevTableIndex + LOCALS_OFFSET_FRAME_INDEX];
                                int nameIndex = locals[prevTableIndex + LOCALS_OFFSET_NAME];
                                int infoIndex = locals[prevTableIndex + LOCALS_OFFSET_INFO];
                                operationData.locals[j] = doEmitLocal(localIndex, frameIndex, nameIndex, infoIndex);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Walks the operation stack, emitting instructions for any operations that need to complete before the return (and fixing up bytecode ranges to exclude these instructions).
         */
        private void beforeEmitReturn(int parentBci) {
            /**
             * Emit "exit" instructions for any pending operations, and close any bytecode ranges that should not apply to the emitted instructions.
             */
            int childBci = parentBci;
            boolean needsRewind = false;
            for (int i = operationSp - 1; i >= rootOperationSp + 1; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.TAG :
                    {
                        if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                            throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                        }
                        if (reachable) {
                            doEmitInstructionI(Instructions.TAG_LEAVE, 0, operationData.nodeId);
                            childBci = bci - 6;
                            doCreateExceptionHandler(operationData.handlerStartBci, bci, HANDLER_TAG_EXCEPTIONAL, operationData.nodeId, operationData.startStackHeight);
                            needsRewind = true;
                        }
                        break;
                    }
                    case Operations.TRYFINALLY :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */) {
                            if (reachable) {
                                int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                                if (handlerTableIndex != UNINITIALIZED) {
                                    if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                        operationData.extraTableEntriesStart = handlerTableIndex;
                                    }
                                    operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                                }
                                needsRewind = true;
                            }
                            doEmitFinallyHandler(operationData, i);
                        }
                        break;
                    }
                    case Operations.TRYCATCHOTHERWISE :
                    {
                        if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                            throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */) {
                            if (reachable) {
                                int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                                if (handlerTableIndex != UNINITIALIZED) {
                                    if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                        operationData.extraTableEntriesStart = handlerTableIndex;
                                    }
                                    operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                                }
                                needsRewind = true;
                            }
                            doEmitFinallyHandler(operationData, i);
                        }
                        break;
                    }
                    case Operations.TRYCATCH :
                    {
                        if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                            throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                        }
                        if (operationStack[i].childCount == 0 /* still in try */ && reachable) {
                            int handlerTableIndex = doCreateExceptionHandler(operationData.tryStartBci, bci, HANDLER_CUSTOM, -operationData.handlerId, UNINITIALIZED /* stack height */);
                            if (handlerTableIndex != UNINITIALIZED) {
                                if (operationData.extraTableEntriesStart == UNINITIALIZED) {
                                    operationData.extraTableEntriesStart = handlerTableIndex;
                                }
                                operationData.extraTableEntriesEnd = handlerTableIndex + EXCEPTION_HANDLER_LENGTH;
                            }
                            needsRewind = true;
                        }
                        break;
                    }
                    case Operations.SOURCESECTION :
                    {
                        if (!(operationStack[i].data instanceof SourceSectionData operationData)) {
                            throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[i].data);
                        }
                        doEmitSourceInfo(operationData.sourceIndex, operationData.startBci, bci, operationData.start, operationData.length);
                        needsRewind = true;
                        break;
                    }
                    case Operations.BLOCK :
                    {
                        if (!(operationStack[i].data instanceof BlockData operationData)) {
                            throw assertionFailed("Data class BlockData expected, but was " + operationStack[i].data);
                        }
                        for (int j = 0; j < operationData.numLocals; j++) {
                            locals[operationData.locals[j] + LOCALS_OFFSET_END_BCI] = bci;
                            needsRewind = true;
                        }
                        break;
                    }
                }
            }
            /**
             * Now that all "exit" instructions have been emitted, reopen bytecode ranges.
             */
            if (needsRewind) {
                for (int i = rootOperationSp + 1; i < operationSp; i++) {
                    if (operationStack[i].operation == Operations.TRYFINALLY || operationStack[i].operation == Operations.TRYCATCHOTHERWISE) {
                        int finallyHandlerSp = ((TryFinallyData) operationStack[i].data).finallyHandlerSp;
                        if (finallyHandlerSp != UNINITIALIZED) {
                            i = finallyHandlerSp - 1;
                            continue;
                        }
                    }
                    switch (operationStack[i].operation) {
                        case Operations.TAG :
                        {
                            if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                                throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                            }
                            operationData.handlerStartBci = bci;
                            break;
                        }
                        case Operations.TRYFINALLY :
                        case Operations.TRYCATCHOTHERWISE :
                            if (operationStack[i].childCount == 0 /* still in try */) {
                                if (!(operationStack[i].data instanceof TryFinallyData operationData)) {
                                    throw assertionFailed("Data class TryFinallyData expected, but was " + operationStack[i].data);
                                }
                                operationData.tryStartBci = bci;
                            }
                            break;
                        case Operations.TRYCATCH :
                            if (operationStack[i].childCount == 0 /* still in try */) {
                                if (!(operationStack[i].data instanceof TryCatchData operationData)) {
                                    throw assertionFailed("Data class TryCatchData expected, but was " + operationStack[i].data);
                                }
                                operationData.tryStartBci = bci;
                            }
                            break;
                        case Operations.SOURCESECTION :
                        {
                            if (!(operationStack[i].data instanceof SourceSectionData operationData)) {
                                throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[i].data);
                            }
                            operationData.startBci = bci;
                            break;
                        }
                        case Operations.BLOCK :
                        {
                            if (!(operationStack[i].data instanceof BlockData operationData)) {
                                throw assertionFailed("Data class BlockData expected, but was " + operationStack[i].data);
                            }
                            for (int j = 0; j < operationData.numLocals; j++) {
                                int prevTableIndex = operationData.locals[j];
                                int endBci = locals[prevTableIndex + LOCALS_OFFSET_END_BCI];
                                if (endBci == bci) {
                                    // No need to split. Reuse the existing entry.
                                    locals[prevTableIndex + LOCALS_OFFSET_END_BCI] = UNINITIALIZED;
                                    continue;
                                }
                                // Create a new table entry with a new bytecode range and the same metadata.
                                int localIndex = locals[prevTableIndex + LOCALS_OFFSET_LOCAL_INDEX];
                                int frameIndex = locals[prevTableIndex + LOCALS_OFFSET_FRAME_INDEX];
                                int nameIndex = locals[prevTableIndex + LOCALS_OFFSET_NAME];
                                int infoIndex = locals[prevTableIndex + LOCALS_OFFSET_INFO];
                                operationData.locals[j] = doEmitLocal(localIndex, frameIndex, nameIndex, infoIndex);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Iterates the handler table, searching for unresolved entries corresponding to the given handlerId.
         * Patches them with the handlerBci and handlerSp now that those values are known.
         */
        private void patchHandlerTable(int tableStart, int tableEnd, int handlerId, int handlerBci, int handlerSp) {
            for (int i = tableStart; i < tableEnd; i += EXCEPTION_HANDLER_LENGTH) {
                if (handlerTable[i + EXCEPTION_HANDLER_OFFSET_KIND] != HANDLER_CUSTOM) {
                    continue;
                }
                if (handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] != -handlerId) {
                    continue;
                }
                handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI] = handlerBci;
                handlerTable[i + EXCEPTION_HANDLER_OFFSET_HANDLER_SP] = handlerSp;
            }
        }

        private void doEmitRoot() {
            if (!parseSources) {
                // Nothing to do here without sources
                return;
            }
            for (int i = operationSp - 1; i >= 0; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.SOURCESECTION :
                        if (!(operationStack[i].data instanceof SourceSectionData operationData)) {
                            throw assertionFailed("Data class SourceSectionData expected, but was " + operationStack[i].data);
                        }
                        doEmitSourceInfo(operationData.sourceIndex, 0, bci, operationData.start, operationData.length);
                        break;
                }
            }
        }

        private int allocateNode() {
            if (!reachable) {
                return -1;
            }
            return checkOverflowInt(numNodes++, "Node counter");
        }

        private short allocateBytecodeLocal() {
            return checkOverflowShort((short) numLocals++, "Number of locals");
        }

        private int allocateBranchProfile() {
            if (!reachable) {
                return -1;
            }
            return checkOverflowInt(numConditionalBranches++, "Number of branch profiles");
        }

        private short allocateContinuationConstant() {
            return constantPool.allocateSlot();
        }

        private void doEmitTagYield() {
            if (tags == 0) {
                return;
            }
            for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                switch (operationStack[i].operation) {
                    case Operations.TAG :
                    {
                        if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                            throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                        }
                        doEmitInstructionI(Instructions.TAG_YIELD, 0, operationData.nodeId);
                        break;
                    }
                }
            }
        }

        private void doEmitTagResume() {
            if (tags == 0) {
                return;
            }
            for (int i = rootOperationSp; i < operationSp; i++) {
                if (operationStack[i].operation == Operations.TRYFINALLY || operationStack[i].operation == Operations.TRYCATCHOTHERWISE) {
                    int finallyHandlerSp = ((TryFinallyData) operationStack[i].data).finallyHandlerSp;
                    if (finallyHandlerSp != UNINITIALIZED) {
                        i = finallyHandlerSp - 1;
                        continue;
                    }
                }
                switch (operationStack[i].operation) {
                    case Operations.TAG :
                    {
                        if (!(operationStack[i].data instanceof TagOperationData operationData)) {
                            throw assertionFailed("Data class TagOperationData expected, but was " + operationStack[i].data);
                        }
                        doEmitInstructionI(Instructions.TAG_RESUME, 0, operationData.nodeId);
                        break;
                    }
                }
            }
        }

        private ScopeData getCurrentScope() {
            for (int i = operationSp - 1; i >= rootOperationSp; i--) {
                if (operationStack[i].operation == Operations.FINALLYHANDLER) {
                    i = ((FinallyHandlerData) operationStack[i].data).finallyOperationSp;
                    continue;
                }
                if (operationStack[i].data instanceof ScopeData e) {
                    return e;
                }
            }
            throw failState("Invalid scope for local variable.");
        }

        private int doEmitLocal(int localIndex, int frameIndex, Object name, Object info) {
            int nameIndex = -1;
            if (name != null) {
                nameIndex = constantPool.addConstant(name);
            }
            int infoIndex = -1;
            if (info != null) {
                infoIndex = constantPool.addConstant(info);
            }
            return doEmitLocal(localIndex, frameIndex, nameIndex, infoIndex);
        }

        private int doEmitLocal(int localIndex, int frameIndex, int nameIndex, int infoIndex) {
            int tableIndex = allocateLocalsTableEntry();
            assert frameIndex - USER_LOCALS_START_INDEX >= 0;
            locals[tableIndex + LOCALS_OFFSET_START_BCI] = bci;
            // will be patched later at the end of the block
            locals[tableIndex + LOCALS_OFFSET_END_BCI] = -1;
            locals[tableIndex + LOCALS_OFFSET_LOCAL_INDEX] = localIndex;
            locals[tableIndex + LOCALS_OFFSET_FRAME_INDEX] = frameIndex;
            locals[tableIndex + LOCALS_OFFSET_NAME] = nameIndex;
            locals[tableIndex + LOCALS_OFFSET_INFO] = infoIndex;
            return tableIndex;
        }

        private int allocateLocalsTableEntry() {
            int result = localsTableIndex;
            if (locals == null) {
                assert result == 0;
                locals = new int[LOCALS_LENGTH * 8];
            } else if (result + LOCALS_LENGTH > locals.length) {
                locals = Arrays.copyOf(locals, Math.max(result + LOCALS_LENGTH, locals.length * 2));
            }
            localsTableIndex += LOCALS_LENGTH;
            return result;
        }

        private void serialize(DataOutput buffer, BytecodeSerializer callback, List<BasicInterpreter> existingNodes) throws IOException {
            this.serialization = new SerializationState(buffer, callback);
            try {
                // 1. Serialize the root nodes and their constants.
                nodes.getParserImpl().parse(this);
                // 2. Serialize the fields stored on each root node. If existingNodes is provided, serialize those fields instead of the new root nodes' fields.
                List<BasicInterpreter> nodesToSerialize = existingNodes != null ? existingNodes : serialization.builtNodes;
                int[][] nodeFields = new int[nodesToSerialize.size()][];
                for (int i = 0; i < nodeFields.length; i ++) {
                    BasicInterpreter node = nodesToSerialize.get(i);
                    int[] fields = nodeFields[i] = new int[1];
                    fields[0] = serialization.serializeObject(node.name);
                }
                serialization.buffer.writeShort(SerializationState.CODE_$END);
                // 3. Encode the constant pool indices for each root node's fields.
                for (int i = 0; i < nodeFields.length; i++) {
                    int[] fields = nodeFields[i];
                    serialization.buffer.writeInt(fields[0]);
                }
            } finally {
                this.serialization = null;
            }
        }

        private short serializeFinallyGenerator(Runnable finallyGenerator) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            SerializationState outerSerialization = serialization;
            try {
                serialization = new SerializationState(new DataOutputStream(baos), serialization);
                finallyGenerator.run();
                serialization.buffer.writeShort(SerializationState.CODE_$END_FINALLY_GENERATOR);
            } finally {
                serialization = outerSerialization;
            }
            byte[] bytes = baos.toByteArray();
            serialization.buffer.writeShort(SerializationState.CODE_$CREATE_FINALLY_GENERATOR);
            serialization.buffer.writeInt(bytes.length);
            serialization.buffer.write(bytes);
            return safeCastShort(serialization.finallyGeneratorCount++);
        }

        @SuppressWarnings("hiding")
        private void deserialize(Supplier<DataInput> bufferSupplier, BytecodeDeserializer callback, DeserializationState outerContext) {
            try {
                DeserializationState context = new DeserializationState(outerContext);
                DataInput buffer = bufferSupplier.get();
                while (true) {
                    short code = buffer.readShort();
                    switch (code) {
                        case SerializationState.CODE_$CREATE_LABEL :
                        {
                            context.labels.add(createLabel());
                            break;
                        }
                        case SerializationState.CODE_$CREATE_LOCAL :
                        {
                            int nameId = buffer.readInt();
                            Object name = null;
                            if (nameId != -1) {
                                name = context.consts.get(nameId);
                            }
                            int infoId = buffer.readInt();
                            Object info = null;
                            if (infoId != -1) {
                                info = context.consts.get(infoId);
                            }
                            context.locals.add(createLocal(name, info));
                            break;
                        }
                        case SerializationState.CODE_$CREATE_NULL :
                        {
                            context.consts.add(null);
                            break;
                        }
                        case SerializationState.CODE_$CREATE_OBJECT :
                        {
                            context.consts.add(Objects.requireNonNull(callback.deserialize(context, buffer)));
                            break;
                        }
                        case SerializationState.CODE_$CREATE_FINALLY_GENERATOR :
                        {
                            byte[] finallyGeneratorBytes = new byte[buffer.readInt()];
                            buffer.readFully(finallyGeneratorBytes);
                            context.finallyGenerators.add(() -> deserialize(() -> SerializationUtils.createDataInput(ByteBuffer.wrap(finallyGeneratorBytes)), callback, context));
                            break;
                        }
                        case SerializationState.CODE_$END_FINALLY_GENERATOR :
                        {
                            return;
                        }
                        case SerializationState.CODE_$END :
                        {
                            for (int i = 0; i < this.builtNodes.size(); i++) {
                                BasicInterpreterWithUncached node = this.builtNodes.get(i);
                                node.name = (String) context.consts.get(buffer.readInt());
                            }
                            return;
                        }
                        case SerializationState.CODE_BEGIN_BLOCK :
                        {
                            beginBlock();
                            break;
                        }
                        case SerializationState.CODE_END_BLOCK :
                        {
                            endBlock();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ROOT :
                        {
                            context.builtNodes.add(null);
                            beginRoot();
                            break;
                        }
                        case SerializationState.CODE_END_ROOT :
                        {
                            BasicInterpreterWithUncached node = (BasicInterpreterWithUncached) endRoot();
                            int serializedContextDepth = buffer.readInt();
                            if (context.depth != serializedContextDepth) {
                                throw new AssertionError("Invalid context depth. Expected " + context.depth + " but got " + serializedContextDepth);
                            }
                            context.builtNodes.set(buffer.readInt(), node);
                            break;
                        }
                        case SerializationState.CODE_BEGIN_IF_THEN :
                        {
                            beginIfThen();
                            break;
                        }
                        case SerializationState.CODE_END_IF_THEN :
                        {
                            endIfThen();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_IF_THEN_ELSE :
                        {
                            beginIfThenElse();
                            break;
                        }
                        case SerializationState.CODE_END_IF_THEN_ELSE :
                        {
                            endIfThenElse();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_CONDITIONAL :
                        {
                            beginConditional();
                            break;
                        }
                        case SerializationState.CODE_END_CONDITIONAL :
                        {
                            endConditional();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_WHILE :
                        {
                            beginWhile();
                            break;
                        }
                        case SerializationState.CODE_END_WHILE :
                        {
                            endWhile();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TRY_CATCH :
                        {
                            beginTryCatch();
                            break;
                        }
                        case SerializationState.CODE_END_TRY_CATCH :
                        {
                            endTryCatch();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TRY_FINALLY :
                        {
                            Runnable finallyGenerator = context.getContext(buffer.readShort()).finallyGenerators.get(buffer.readShort());
                            beginTryFinally(finallyGenerator);
                            break;
                        }
                        case SerializationState.CODE_END_TRY_FINALLY :
                        {
                            endTryFinally();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TRY_CATCH_OTHERWISE :
                        {
                            Runnable otherwiseGenerator = context.getContext(buffer.readShort()).finallyGenerators.get(buffer.readShort());
                            beginTryCatchOtherwise(otherwiseGenerator);
                            break;
                        }
                        case SerializationState.CODE_END_TRY_CATCH_OTHERWISE :
                        {
                            endTryCatchOtherwise();
                            break;
                        }
                        case SerializationState.CODE_EMIT_LABEL :
                        {
                            BytecodeLabel label = context.getContext(buffer.readShort()).labels.get(buffer.readShort());
                            emitLabel(label);
                            break;
                        }
                        case SerializationState.CODE_EMIT_BRANCH :
                        {
                            BytecodeLabel label = context.getContext(buffer.readShort()).labels.get(buffer.readShort());
                            emitBranch(label);
                            break;
                        }
                        case SerializationState.CODE_EMIT_LOAD_CONSTANT :
                        {
                            Object constant = context.consts.get(buffer.readInt());
                            emitLoadConstant(constant);
                            break;
                        }
                        case SerializationState.CODE_EMIT_LOAD_NULL :
                        {
                            emitLoadNull();
                            break;
                        }
                        case SerializationState.CODE_EMIT_LOAD_ARGUMENT :
                        {
                            int index = buffer.readInt();
                            emitLoadArgument(index);
                            break;
                        }
                        case SerializationState.CODE_EMIT_LOAD_EXCEPTION :
                        {
                            emitLoadException();
                            break;
                        }
                        case SerializationState.CODE_EMIT_LOAD_LOCAL :
                        {
                            BytecodeLocal local = context.getContext(buffer.readShort()).locals.get(buffer.readShort());
                            emitLoadLocal(local);
                            break;
                        }
                        case SerializationState.CODE_BEGIN_LOAD_LOCAL_MATERIALIZED :
                        {
                            BytecodeLocal local = context.getContext(buffer.readShort()).locals.get(buffer.readShort());
                            beginLoadLocalMaterialized(local);
                            break;
                        }
                        case SerializationState.CODE_END_LOAD_LOCAL_MATERIALIZED :
                        {
                            endLoadLocalMaterialized();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_STORE_LOCAL :
                        {
                            BytecodeLocal local = context.getContext(buffer.readShort()).locals.get(buffer.readShort());
                            beginStoreLocal(local);
                            break;
                        }
                        case SerializationState.CODE_END_STORE_LOCAL :
                        {
                            endStoreLocal();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_STORE_LOCAL_MATERIALIZED :
                        {
                            BytecodeLocal local = context.getContext(buffer.readShort()).locals.get(buffer.readShort());
                            beginStoreLocalMaterialized(local);
                            break;
                        }
                        case SerializationState.CODE_END_STORE_LOCAL_MATERIALIZED :
                        {
                            endStoreLocalMaterialized();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_RETURN :
                        {
                            beginReturn();
                            break;
                        }
                        case SerializationState.CODE_END_RETURN :
                        {
                            endReturn();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_YIELD :
                        {
                            beginYield();
                            break;
                        }
                        case SerializationState.CODE_END_YIELD :
                        {
                            endYield();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_SOURCE :
                        {
                            Source source = (Source) context.consts.get(buffer.readInt());
                            beginSource(source);
                            break;
                        }
                        case SerializationState.CODE_END_SOURCE :
                        {
                            endSource();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_SOURCE_SECTION :
                        {
                            int index = buffer.readInt();
                            int length = buffer.readInt();
                            beginSourceSection(index, length);
                            break;
                        }
                        case SerializationState.CODE_END_SOURCE_SECTION :
                        {
                            endSourceSection();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TAG :
                        {
                            Class<?>[] newTags = TAG_MASK_TO_TAGS.computeIfAbsent(buffer.readInt(), (v) -> mapTagMaskToTagsArray(v));
                            beginTag(newTags);
                            break;
                        }
                        case SerializationState.CODE_END_TAG :
                        {
                            Class<?>[] newTags = TAG_MASK_TO_TAGS.computeIfAbsent(buffer.readInt(), (v) -> mapTagMaskToTagsArray(v));
                            endTag(newTags);
                            break;
                        }
                        case SerializationState.CODE_BEGIN_EARLY_RETURN :
                        {
                            beginEarlyReturn();
                            break;
                        }
                        case SerializationState.CODE_END_EARLY_RETURN :
                        {
                            endEarlyReturn();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ADD_OPERATION :
                        {
                            beginAddOperation();
                            break;
                        }
                        case SerializationState.CODE_END_ADD_OPERATION :
                        {
                            endAddOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TO_STRING :
                        {
                            beginToString();
                            break;
                        }
                        case SerializationState.CODE_END_TO_STRING :
                        {
                            endToString();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_CALL :
                        {
                            BasicInterpreter interpreterValue = (BasicInterpreter) context.consts.get(buffer.readInt());
                            beginCall(interpreterValue);
                            break;
                        }
                        case SerializationState.CODE_END_CALL :
                        {
                            endCall();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ADD_CONSTANT_OPERATION :
                        {
                            long constantLhsValue = (long) context.consts.get(buffer.readInt());
                            beginAddConstantOperation(constantLhsValue);
                            break;
                        }
                        case SerializationState.CODE_END_ADD_CONSTANT_OPERATION :
                        {
                            endAddConstantOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ADD_CONSTANT_OPERATION_AT_END :
                        {
                            beginAddConstantOperationAtEnd();
                            break;
                        }
                        case SerializationState.CODE_END_ADD_CONSTANT_OPERATION_AT_END :
                        {
                            long constantRhsValue = (long) context.consts.get(buffer.readInt());
                            endAddConstantOperationAtEnd(constantRhsValue);
                            break;
                        }
                        case SerializationState.CODE_BEGIN_VERY_COMPLEX_OPERATION :
                        {
                            beginVeryComplexOperation();
                            break;
                        }
                        case SerializationState.CODE_END_VERY_COMPLEX_OPERATION :
                        {
                            endVeryComplexOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_THROW_OPERATION :
                        {
                            beginThrowOperation();
                            break;
                        }
                        case SerializationState.CODE_END_THROW_OPERATION :
                        {
                            endThrowOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_READ_EXCEPTION_OPERATION :
                        {
                            beginReadExceptionOperation();
                            break;
                        }
                        case SerializationState.CODE_END_READ_EXCEPTION_OPERATION :
                        {
                            endReadExceptionOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ALWAYS_BOX_OPERATION :
                        {
                            beginAlwaysBoxOperation();
                            break;
                        }
                        case SerializationState.CODE_END_ALWAYS_BOX_OPERATION :
                        {
                            endAlwaysBoxOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_APPENDER_OPERATION :
                        {
                            beginAppenderOperation();
                            break;
                        }
                        case SerializationState.CODE_END_APPENDER_OPERATION :
                        {
                            endAppenderOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TEE_LOCAL :
                        {
                            BytecodeLocal setterValue = context.getContext(buffer.readShort()).locals.get(buffer.readShort());
                            beginTeeLocal(setterValue);
                            break;
                        }
                        case SerializationState.CODE_END_TEE_LOCAL :
                        {
                            endTeeLocal();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TEE_LOCAL_RANGE :
                        {
                            BytecodeLocal[] setterValue = new BytecodeLocal[buffer.readShort()];
                            if (setterValue.length != 0) {
                                DeserializationState setterContext = context.getContext(buffer.readShort());
                                for (int i = 0; i < setterValue.length; i++) {
                                    setterValue[i] = setterContext.locals.get(buffer.readShort());
                                }
                            }
                            beginTeeLocalRange(setterValue);
                            break;
                        }
                        case SerializationState.CODE_END_TEE_LOCAL_RANGE :
                        {
                            endTeeLocalRange();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_INVOKE :
                        {
                            beginInvoke();
                            break;
                        }
                        case SerializationState.CODE_END_INVOKE :
                        {
                            endInvoke();
                            break;
                        }
                        case SerializationState.CODE_EMIT_MATERIALIZE_FRAME :
                        {
                            emitMaterializeFrame();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_CREATE_CLOSURE :
                        {
                            beginCreateClosure();
                            break;
                        }
                        case SerializationState.CODE_END_CREATE_CLOSURE :
                        {
                            endCreateClosure();
                            break;
                        }
                        case SerializationState.CODE_EMIT_VOID_OPERATION :
                        {
                            emitVoidOperation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_TO_BOOLEAN :
                        {
                            beginToBoolean();
                            break;
                        }
                        case SerializationState.CODE_END_TO_BOOLEAN :
                        {
                            endToBoolean();
                            break;
                        }
                        case SerializationState.CODE_EMIT_GET_SOURCE_POSITION :
                        {
                            emitGetSourcePosition();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ENSURE_AND_GET_SOURCE_POSITION :
                        {
                            beginEnsureAndGetSourcePosition();
                            break;
                        }
                        case SerializationState.CODE_END_ENSURE_AND_GET_SOURCE_POSITION :
                        {
                            endEnsureAndGetSourcePosition();
                            break;
                        }
                        case SerializationState.CODE_EMIT_GET_SOURCE_POSITIONS :
                        {
                            emitGetSourcePositions();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_COPY_LOCALS_TO_FRAME :
                        {
                            beginCopyLocalsToFrame();
                            break;
                        }
                        case SerializationState.CODE_END_COPY_LOCALS_TO_FRAME :
                        {
                            endCopyLocalsToFrame();
                            break;
                        }
                        case SerializationState.CODE_EMIT_GET_BYTECODE_LOCATION :
                        {
                            emitGetBytecodeLocation();
                            break;
                        }
                        case SerializationState.CODE_EMIT_COLLECT_BYTECODE_LOCATIONS :
                        {
                            emitCollectBytecodeLocations();
                            break;
                        }
                        case SerializationState.CODE_EMIT_COLLECT_SOURCE_LOCATIONS :
                        {
                            emitCollectSourceLocations();
                            break;
                        }
                        case SerializationState.CODE_EMIT_COLLECT_ALL_SOURCE_LOCATIONS :
                        {
                            emitCollectAllSourceLocations();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_CONTINUE :
                        {
                            beginContinue();
                            break;
                        }
                        case SerializationState.CODE_END_CONTINUE :
                        {
                            endContinue();
                            break;
                        }
                        case SerializationState.CODE_EMIT_CURRENT_LOCATION :
                        {
                            emitCurrentLocation();
                            break;
                        }
                        case SerializationState.CODE_EMIT_PRINT_HERE :
                        {
                            emitPrintHere();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_INCREMENT_VALUE :
                        {
                            beginIncrementValue();
                            break;
                        }
                        case SerializationState.CODE_END_INCREMENT_VALUE :
                        {
                            endIncrementValue();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_DOUBLE_VALUE :
                        {
                            beginDoubleValue();
                            break;
                        }
                        case SerializationState.CODE_END_DOUBLE_VALUE :
                        {
                            endDoubleValue();
                            break;
                        }
                        case SerializationState.CODE_EMIT_ENABLE_INCREMENT_VALUE_INSTRUMENTATION :
                        {
                            emitEnableIncrementValueInstrumentation();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_ADD :
                        {
                            beginAdd();
                            break;
                        }
                        case SerializationState.CODE_END_ADD :
                        {
                            endAdd();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_MOD :
                        {
                            beginMod();
                            break;
                        }
                        case SerializationState.CODE_END_MOD :
                        {
                            endMod();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_LESS :
                        {
                            beginLess();
                            break;
                        }
                        case SerializationState.CODE_END_LESS :
                        {
                            endLess();
                            break;
                        }
                        case SerializationState.CODE_EMIT_ENABLE_DOUBLE_VALUE_INSTRUMENTATION :
                        {
                            emitEnableDoubleValueInstrumentation();
                            break;
                        }
                        case SerializationState.CODE_EMIT_EXPLICIT_BINDINGS_TEST :
                        {
                            emitExplicitBindingsTest();
                            break;
                        }
                        case SerializationState.CODE_EMIT_IMPLICIT_BINDINGS_TEST :
                        {
                            emitImplicitBindingsTest();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_SC_AND :
                        {
                            beginScAnd();
                            break;
                        }
                        case SerializationState.CODE_END_SC_AND :
                        {
                            endScAnd();
                            break;
                        }
                        case SerializationState.CODE_BEGIN_SC_OR :
                        {
                            beginScOr();
                            break;
                        }
                        case SerializationState.CODE_END_SC_OR :
                        {
                            endScOr();
                            break;
                        }
                        default :
                        {
                            throw new AssertionError("Unknown operation code " + code);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append(BasicInterpreterWithUncached.class.getSimpleName());
            b.append('.');
            b.append(com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder.class.getSimpleName());
            b.append("[");
            b.append("at=");
            for (int i = 0; i < operationSp; i++) {
                b.append("(");
                b.append(operationStack[i].toString(this));
            }
            for (int i = 0; i < operationSp; i++) {
                b.append(")");
            }
            b.append(", mode=");
            if (serialization != null) {
                b.append("serializing");
            } else if (reparseReason != null) {
                b.append("reparsing");
            } else {
                b.append("default");
            }
            b.append(", bytecodeIndex=").append(bci);
            b.append(", stackPointer=").append(currentStackHeight);
            b.append(", bytecodes=").append(parseBytecodes);
            b.append(", sources=").append(parseSources);
            b.append(", instruments=[");
            String sep = "";
            if ((instrumentations & 0x1) != 0) {
                b.append(sep);
                b.append("PrintHere");
                sep = ",";
            }
            if ((instrumentations & 0x2) != 0) {
                b.append(sep);
                b.append("IncrementValue");
                sep = ",";
            }
            if ((instrumentations & 0x4) != 0) {
                b.append(sep);
                b.append("DoubleValue");
                sep = ",";
            }
            b.append("]");
            b.append(", tags=");
            String sepTag = "";
            if ((tags & CLASS_TO_TAG_MASK.get(RootTag.class)) != 0) {
                b.append(sepTag);
                b.append(Tag.getIdentifier(RootTag.class));
                sepTag = ",";
            }
            if ((tags & CLASS_TO_TAG_MASK.get(RootBodyTag.class)) != 0) {
                b.append(sepTag);
                b.append(Tag.getIdentifier(RootBodyTag.class));
                sepTag = ",";
            }
            if ((tags & CLASS_TO_TAG_MASK.get(ExpressionTag.class)) != 0) {
                b.append(sepTag);
                b.append(Tag.getIdentifier(ExpressionTag.class));
                sepTag = ",";
            }
            if ((tags & CLASS_TO_TAG_MASK.get(StatementTag.class)) != 0) {
                b.append(sepTag);
                b.append(Tag.getIdentifier(StatementTag.class));
                sepTag = ",";
            }
            b.append("]");
            return b.toString();
        }

        private RuntimeException failState(String message) {
            throw new IllegalStateException("Invalid builder usage: " + message + " Operation stack: " + dumpAt());
        }

        private RuntimeException failArgument(String message) {
            throw new IllegalArgumentException("Invalid builder operation argument: " + message + " Operation stack: " + dumpAt());
        }

        private String dumpAt() {
            try {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < operationSp; i++) {
                    b.append("(");
                    b.append(operationStack[i].toString(this));
                }
                for (int i = 0; i < operationSp; i++) {
                    b.append(")");
                }
                return b.toString();
            } catch (Exception e) {
                return "<invalid-location>";
            }
        }

        private boolean doEmitInstruction(short instruction, int stackEffect) {
            if (stackEffect != 0) {
                currentStackHeight += stackEffect;
                assert currentStackHeight >= 0;
            }
            if (stackEffect > 0) {
                updateMaxStackHeight(currentStackHeight);
            }
            if (!reachable) {
                return false;
            }
            int newBci = checkBci(bci + 2);
            if (newBci > bc.length) {
                ensureBytecodeCapacity(newBci);
            }
            BYTES.putShort(bc, bci + 0, instruction);
            bci = newBci;
            return true;
        }

        private boolean doEmitInstructionS(short instruction, int stackEffect, short data0) {
            if (stackEffect != 0) {
                currentStackHeight += stackEffect;
                assert currentStackHeight >= 0;
            }
            if (stackEffect > 0) {
                updateMaxStackHeight(currentStackHeight);
            }
            if (!reachable) {
                return false;
            }
            int newBci = checkBci(bci + 4);
            if (newBci > bc.length) {
                ensureBytecodeCapacity(newBci);
            }
            BYTES.putShort(bc, bci + 0, instruction);
            BYTES.putShort(bc, bci + 2 /* imm 0 */, data0);
            bci = newBci;
            return true;
        }

        private boolean doEmitInstructionI(short instruction, int stackEffect, int data0) {
            if (stackEffect != 0) {
                currentStackHeight += stackEffect;
                assert currentStackHeight >= 0;
            }
            if (stackEffect > 0) {
                updateMaxStackHeight(currentStackHeight);
            }
            if (!reachable) {
                return false;
            }
            int newBci = checkBci(bci + 6);
            if (newBci > bc.length) {
                ensureBytecodeCapacity(newBci);
            }
            BYTES.putShort(bc, bci + 0, instruction);
            BYTES.putInt(bc, bci + 2 /* imm 0 */, data0);
            bci = newBci;
            return true;
        }

        private boolean doEmitInstructionSS(short instruction, int stackEffect, short data0, short data1) {
            if (stackEffect != 0) {
                currentStackHeight += stackEffect;
                assert currentStackHeight >= 0;
            }
            if (stackEffect > 0) {
                updateMaxStackHeight(currentStackHeight);
            }
            if (!reachable) {
                return false;
            }
            int newBci = checkBci(bci + 6);
            if (newBci > bc.length) {
                ensureBytecodeCapacity(newBci);
            }
            BYTES.putShort(bc, bci + 0, instruction);
            BYTES.putShort(bc, bci + 2 /* imm 0 */, data0);
            BYTES.putShort(bc, bci + 4 /* imm 1 */, data1);
            bci = newBci;
            return true;
        }

        private boolean doEmitInstructionII(short instruction, int stackEffect, int data0, int data1) {
            if (stackEffect != 0) {
                currentStackHeight += stackEffect;
                assert currentStackHeight >= 0;
            }
            if (stackEffect > 0) {
                updateMaxStackHeight(currentStackHeight);
            }
            if (!reachable) {
                return false;
            }
            int newBci = checkBci(bci + 10);
            if (newBci > bc.length) {
                ensureBytecodeCapacity(newBci);
            }
            BYTES.putShort(bc, bci + 0, instruction);
            BYTES.putInt(bc, bci + 2 /* imm 0 */, data0);
            BYTES.putInt(bc, bci + 6 /* imm 1 */, data1);
            bci = newBci;
            return true;
        }

        private static short safeCastShort(int num) {
            if (Short.MIN_VALUE <= num && num <= Short.MAX_VALUE) {
                return (short) num;
            }
            throw BytecodeEncodingException.create("Value " + num + " cannot be encoded as a short.");
        }

        private static short checkOverflowShort(short num, String valueName) {
            if (num < 0) {
                throw BytecodeEncodingException.create(valueName + " overflowed.");
            }
            return num;
        }

        private static int checkOverflowInt(int num, String valueName) {
            if (num < 0) {
                throw BytecodeEncodingException.create(valueName + " overflowed.");
            }
            return num;
        }

        private static int checkBci(int newBci) {
            return checkOverflowInt(newBci, "Bytecode index");
        }

        private static class SavedState {

            private int operationSequenceNumber;
            private OperationStackEntry[] operationStack;
            private int operationSp;
            private int rootOperationSp;
            private int numLocals;
            private int numLabels;
            private int numNodes;
            private int numHandlers;
            private int numConditionalBranches;
            private byte[] bc;
            private int bci;
            private int currentStackHeight;
            private int maxStackHeight;
            private int[] sourceInfo;
            private int sourceInfoIndex;
            private int[] handlerTable;
            private int handlerTableSize;
            private int[] locals;
            private int localsTableIndex;
            private HashMap<BytecodeLabel, ArrayList<Integer>> unresolvedLabels;
            private ConstantPool constantPool;
            private boolean reachable = true;
            private ArrayList<ContinuationLocation> continuationLocations;
            private int maxLocals;
            private List<TagNode> tagRoots;
            private List<TagNode> tagNodes;
            private SavedState savedState;

            SavedState(int operationSequenceNumber, OperationStackEntry[] operationStack, int operationSp, int rootOperationSp, int numLocals, int numLabels, int numNodes, int numHandlers, int numConditionalBranches, byte[] bc, int bci, int currentStackHeight, int maxStackHeight, int[] sourceInfo, int sourceInfoIndex, int[] handlerTable, int handlerTableSize, int[] locals, int localsTableIndex, HashMap<BytecodeLabel, ArrayList<Integer>> unresolvedLabels, ConstantPool constantPool, boolean reachable, ArrayList<ContinuationLocation> continuationLocations, int maxLocals, List<TagNode> tagRoots, List<TagNode> tagNodes, SavedState savedState) {
                this.operationSequenceNumber = operationSequenceNumber;
                this.operationStack = operationStack;
                this.operationSp = operationSp;
                this.rootOperationSp = rootOperationSp;
                this.numLocals = numLocals;
                this.numLabels = numLabels;
                this.numNodes = numNodes;
                this.numHandlers = numHandlers;
                this.numConditionalBranches = numConditionalBranches;
                this.bc = bc;
                this.bci = bci;
                this.currentStackHeight = currentStackHeight;
                this.maxStackHeight = maxStackHeight;
                this.sourceInfo = sourceInfo;
                this.sourceInfoIndex = sourceInfoIndex;
                this.handlerTable = handlerTable;
                this.handlerTableSize = handlerTableSize;
                this.locals = locals;
                this.localsTableIndex = localsTableIndex;
                this.unresolvedLabels = unresolvedLabels;
                this.constantPool = constantPool;
                this.reachable = reachable;
                this.continuationLocations = continuationLocations;
                this.maxLocals = maxLocals;
                this.tagRoots = tagRoots;
                this.tagNodes = tagNodes;
                this.savedState = savedState;
            }

        }
        private static class OperationStackEntry {

            private final int operation;
            private final Object data;
            private final int sequenceNumber;
            private int childCount = 0;
            private ArrayList<BytecodeLabel> declaredLabels = null;

            OperationStackEntry(int operation, Object data, int sequenceNumber) {
                this.operation = operation;
                this.data = data;
                this.sequenceNumber = sequenceNumber;
            }

            public void addDeclaredLabel(BytecodeLabel label) {
                if (declaredLabels == null) {
                    declaredLabels = new ArrayList<>(8);
                }
                declaredLabels.add(label);
            }

            @Override
            public String toString() {
                return "(" + toString(null) + ")";
            }

            private String toString(com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder builder) {
                StringBuilder b = new StringBuilder();
                b.append(OPERATION_NAMES[operation]);
                switch (operation) {
                    case Operations.BLOCK :
                    {
                        BlockData operationData = (BlockData) data;
                        if (operationData.numLocals > 0) {
                            b.append(" locals=");
                            b.append(operationData.numLocals);
                        }
                    }
                    break;
                    case Operations.ROOT :
                    {
                        RootData operationData = (RootData) data;
                        if (operationData.numLocals > 0) {
                            b.append(" locals=");
                            b.append(operationData.numLocals);
                        }
                    }
                    break;
                    case Operations.STORELOCAL :
                    {
                        b.append(" ");
                        BytecodeLocalImpl operationData = (BytecodeLocalImpl) data;
                        b.append(operationData.frameIndex);
                    }
                    break;
                    case Operations.STORELOCALMATERIALIZED :
                    {
                        b.append(" ");
                        BytecodeLocalImpl operationData = (BytecodeLocalImpl) data;
                        b.append(operationData.frameIndex);
                    }
                    break;
                    case Operations.SOURCE :
                    {
                        b.append(" ");
                        SourceData operationData = (SourceData) data;
                        b.append(operationData.sourceIndex);
                        if (builder != null) {
                            b.append(":");
                            b.append(builder.sources.get(operationData.sourceIndex).getName());
                        }
                    }
                    break;
                    case Operations.SOURCESECTION :
                    {
                        b.append(" ");
                        SourceSectionData operationData = (SourceSectionData) data;
                        b.append(operationData.start);
                        b.append(":");
                        b.append(operationData.length);
                    }
                    break;
                    case Operations.TAG :
                    {
                        b.append(" ");
                        TagOperationData operationData = (TagOperationData) data;
                        b.append(operationData.node);
                    }
                    break;
                }
                return b.toString();
            }

        }
        private static class ConstantPool {

            private final ArrayList<Object> constants;
            private final HashMap<Object, Integer> map;

            ConstantPool() {
                constants = new ArrayList<>();
                map = new HashMap<>();
            }

            private int addConstant(Object constant) {
                if (map.containsKey(constant)) {
                    return map.get(constant);
                }
                int index = constants.size();
                constants.add(constant);
                map.put(constant, index);
                return index;
            }

            /**
             * Allocates a slot for a constant which will be manually added to the constant pool later.
             */
            private short allocateSlot() {
                short index = safeCastShort(constants.size());
                constants.add(null);
                return index;
            }

            private Object[] toArray() {
                return constants.toArray();
            }

        }
        private static final class BytecodeLocalImpl extends BytecodeLocal {

            private final short frameIndex;
            private final short localIndex;
            private final short rootIndex;
            private final ScopeData scope;

            BytecodeLocalImpl(short frameIndex, short localIndex, short rootIndex, ScopeData scope) {
                super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
                this.frameIndex = frameIndex;
                this.localIndex = localIndex;
                this.rootIndex = rootIndex;
                this.scope = scope;
            }

            @Override
            public int getLocalOffset() {
                return frameIndex - USER_LOCALS_START_INDEX;
            }

            @Override
            public int getLocalIndex() {
                return localIndex;
            }

        }
        private static final class BytecodeLabelImpl extends BytecodeLabel {

            private final int id;
            int bci;
            private final int declaringOp;

            BytecodeLabelImpl(int id, int bci, int declaringOp) {
                super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
                this.id = id;
                this.bci = bci;
                this.declaringOp = declaringOp;
            }

            public boolean isDefined() {
                return bci != -1;
            }

            @Override
            public boolean equals(Object other) {
                if (!(other instanceof BytecodeLabelImpl)) {
                    return false;
                }
                return this.id == ((BytecodeLabelImpl) other).id;
            }

            @Override
            public int hashCode() {
                return this.id;
            }

        }
        private abstract static class ScopeData {

            int frameOffset;
            /**
             * The indices of this scope's locals in the global locals table. Used to patch in the end bci at the end of the scope.
             */
            int[] locals = null;
            /**
             * The number of locals allocated in the frame for this scope.
             */
            int numLocals = 0;
            boolean valid = true;

            public void registerLocal(int tableIndex) {
                int localTableIndex = numLocals++;
                if (locals == null) {
                    locals = new int[8];
                } else if (localTableIndex >= locals.length) {
                    locals = Arrays.copyOf(locals, locals.length * 2);
                }
                locals[localTableIndex] = tableIndex;
            }

        }
        private static final class BlockData extends ScopeData {

            final int startStackHeight;
            boolean producedValue;
            int childBci;

            BlockData(int startStackHeight) {
                this.startStackHeight = startStackHeight;
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
            }

        }
        private static final class RootData extends ScopeData {

            final short index;
            boolean producedValue;
            int childBci;
            boolean reachable;

            RootData(short index) {
                this.index = index;
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
                this.reachable = true;
            }

        }
        private static final class IfThenData {

            boolean thenReachable;
            int falseBranchFixupBci;

            IfThenData(boolean thenReachable) {
                this.thenReachable = thenReachable;
                this.falseBranchFixupBci = UNINITIALIZED;
            }

        }
        private static final class IfThenElseData {

            boolean thenReachable;
            boolean elseReachable;
            int falseBranchFixupBci;
            int endBranchFixupBci;

            IfThenElseData(boolean thenReachable, boolean elseReachable) {
                this.thenReachable = thenReachable;
                this.elseReachable = elseReachable;
                this.falseBranchFixupBci = UNINITIALIZED;
                this.endBranchFixupBci = UNINITIALIZED;
            }

        }
        private static final class ConditionalData {

            boolean thenReachable;
            boolean elseReachable;
            int falseBranchFixupBci;
            int endBranchFixupBci;

            ConditionalData(boolean thenReachable, boolean elseReachable) {
                this.thenReachable = thenReachable;
                this.elseReachable = elseReachable;
                this.falseBranchFixupBci = UNINITIALIZED;
                this.endBranchFixupBci = UNINITIALIZED;
            }

        }
        private static final class WhileData {

            final int whileStartBci;
            boolean bodyReachable;
            int endBranchFixupBci;

            WhileData(int whileStartBci, boolean bodyReachable) {
                this.whileStartBci = whileStartBci;
                this.bodyReachable = bodyReachable;
                this.endBranchFixupBci = UNINITIALIZED;
            }

        }
        private static final class TryCatchData {

            final int handlerId;
            final short stackHeight;
            int tryStartBci;
            final boolean operationReachable;
            boolean tryReachable;
            boolean catchReachable;
            int endBranchFixupBci;
            int extraTableEntriesStart;
            int extraTableEntriesEnd;

            TryCatchData(int handlerId, short stackHeight, int tryStartBci, boolean operationReachable, boolean tryReachable, boolean catchReachable) {
                this.handlerId = handlerId;
                this.stackHeight = stackHeight;
                this.tryStartBci = tryStartBci;
                this.operationReachable = operationReachable;
                this.tryReachable = tryReachable;
                this.catchReachable = catchReachable;
                this.endBranchFixupBci = UNINITIALIZED;
                this.extraTableEntriesStart = UNINITIALIZED;
                this.extraTableEntriesEnd = UNINITIALIZED;
            }

        }
        private static final class TryFinallyData {

            final int handlerId;
            final short stackHeight;
            final Runnable finallyGenerator;
            int tryStartBci;
            final boolean operationReachable;
            boolean tryReachable;
            boolean catchReachable;
            int endBranchFixupBci;
            int extraTableEntriesStart;
            int extraTableEntriesEnd;
            /**
             * The index of the finally handler operation on the operation stack.
             * This value is uninitialized unless a finally handler is being emitted, and allows us to
             * walk the operation stack from bottom to top.
             */
            int finallyHandlerSp;

            TryFinallyData(int handlerId, short stackHeight, Runnable finallyGenerator, int tryStartBci, boolean operationReachable, boolean tryReachable, boolean catchReachable) {
                this.handlerId = handlerId;
                this.stackHeight = stackHeight;
                this.finallyGenerator = finallyGenerator;
                this.tryStartBci = tryStartBci;
                this.operationReachable = operationReachable;
                this.tryReachable = tryReachable;
                this.catchReachable = catchReachable;
                this.endBranchFixupBci = UNINITIALIZED;
                this.extraTableEntriesStart = UNINITIALIZED;
                this.extraTableEntriesEnd = UNINITIALIZED;
                this.finallyHandlerSp = UNINITIALIZED;
            }

        }
        private static final class FinallyHandlerData {

            /**
             * The index of the finally operation (TryFinally/TryCatchOtherwise) on the operation stack.
             * This index should only be used to skip over the handler when walking the operation stack.
             * It should *not* be used to access the finally operation data, because a FinallyHandler is
             * sometimes emitted after the finally operation has already been popped.
             */
            final int finallyOperationSp;

            FinallyHandlerData(int finallyOperationSp) {
                this.finallyOperationSp = finallyOperationSp;
            }

        }
        private static final class ReturnOperationData {

            boolean producedValue;
            int childBci;

            ReturnOperationData() {
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
            }

        }
        private static final class SourceData {

            final int sourceIndex;
            boolean producedValue;
            int childBci;

            SourceData(int sourceIndex) {
                this.sourceIndex = sourceIndex;
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
            }

        }
        private static final class SourceSectionData {

            final int sourceIndex;
            int startBci;
            final int start;
            final int length;
            boolean producedValue;
            int childBci;

            SourceSectionData(int sourceIndex, int startBci, int start, int length) {
                this.sourceIndex = sourceIndex;
                this.startBci = startBci;
                this.start = start;
                this.length = length;
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
            }

        }
        private static final class TagOperationData {

            final int nodeId;
            final boolean operationReachable;
            final int startStackHeight;
            final TagNode node;
            int handlerStartBci;
            boolean producedValue;
            int childBci;
            List<TagNode> children;

            TagOperationData(int nodeId, boolean operationReachable, int startStackHeight, TagNode node) {
                this.nodeId = nodeId;
                this.operationReachable = operationReachable;
                this.startStackHeight = startStackHeight;
                this.node = node;
                this.handlerStartBci = node.enterBci;
                this.producedValue = false;
                this.childBci = UNINITIALIZED;
                this.children = null;
            }

        }
        private static final class CustomOperationData {

            final int[] childBcis;
            final int[] constants;
            final Object[] locals;

            CustomOperationData(int[] childBcis, int[] constants, Object... locals) {
                this.childBcis = childBcis;
                this.constants = constants;
                this.locals = locals;
            }

        }
        private static final class CustomShortCircuitOperationData {

            int childBci;
            List<Integer> branchFixupBcis;

            CustomShortCircuitOperationData() {
                this.childBci = UNINITIALIZED;
                this.branchFixupBcis = new ArrayList<>(4);
            }

        }
        private static final class SerializationRootNode extends BasicInterpreter {

            private final int contextDepth;
            private final int rootIndex;

            private SerializationRootNode(com.oracle.truffle.api.frame.FrameDescriptor.Builder builder, int contextDepth, int rootIndex) {
                super(null, builder.build());
                this.contextDepth = contextDepth;
                this.rootIndex = rootIndex;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected boolean isInstrumentable() {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected void prepareForInstrumentation(Set<Class<?>> materializedTags) {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            public boolean isCloningAllowed() {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected boolean isCloneUninitializedSupported() {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected RootNode cloneUninitialized() {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected int findBytecodeIndex(Node node, Frame frame) {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected boolean isCaptureFramesForTrace(boolean compiled) {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            public SourceSection getSourceSection() {
                throw new IllegalStateException("method should not be called");
            }

            @Override
            protected Object translateStackTraceElement(TruffleStackTraceElement stackTraceElement) {
                throw new IllegalStateException("method should not be called");
            }

        }
        private static final class SerializationLocal extends BytecodeLocal {

            private final int contextDepth;
            private final int localIndex;

            SerializationLocal(int contextDepth, int localIndex) {
                super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
                this.contextDepth = contextDepth;
                this.localIndex = localIndex;
            }

            @Override
            public int getLocalOffset() {
                throw new IllegalStateException();
            }

            @Override
            public int getLocalIndex() {
                throw new IllegalStateException();
            }

        }
        private static final class SerializationLabel extends BytecodeLabel {

            private final int contextDepth;
            private final int labelIndex;

            SerializationLabel(int contextDepth, int labelIndex) {
                super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
                this.contextDepth = contextDepth;
                this.labelIndex = labelIndex;
            }

        }
        private static class SerializationState implements SerializerContext {

            private static final short CODE_$CREATE_LABEL = -2;
            private static final short CODE_$CREATE_LOCAL = -3;
            private static final short CODE_$CREATE_OBJECT = -4;
            private static final short CODE_$CREATE_NULL = -5;
            private static final short CODE_$CREATE_FINALLY_GENERATOR = -6;
            private static final short CODE_$END_FINALLY_GENERATOR = -7;
            private static final short CODE_$END = -8;
            private static final short CODE_BEGIN_BLOCK = 1 << 1;
            private static final short CODE_END_BLOCK = (1 << 1) | 0b1;
            private static final short CODE_BEGIN_ROOT = 2 << 1;
            private static final short CODE_END_ROOT = (2 << 1) | 0b1;
            private static final short CODE_BEGIN_IF_THEN = 3 << 1;
            private static final short CODE_END_IF_THEN = (3 << 1) | 0b1;
            private static final short CODE_BEGIN_IF_THEN_ELSE = 4 << 1;
            private static final short CODE_END_IF_THEN_ELSE = (4 << 1) | 0b1;
            private static final short CODE_BEGIN_CONDITIONAL = 5 << 1;
            private static final short CODE_END_CONDITIONAL = (5 << 1) | 0b1;
            private static final short CODE_BEGIN_WHILE = 6 << 1;
            private static final short CODE_END_WHILE = (6 << 1) | 0b1;
            private static final short CODE_BEGIN_TRY_CATCH = 7 << 1;
            private static final short CODE_END_TRY_CATCH = (7 << 1) | 0b1;
            private static final short CODE_BEGIN_TRY_FINALLY = 8 << 1;
            private static final short CODE_END_TRY_FINALLY = (8 << 1) | 0b1;
            private static final short CODE_BEGIN_TRY_CATCH_OTHERWISE = 9 << 1;
            private static final short CODE_END_TRY_CATCH_OTHERWISE = (9 << 1) | 0b1;
            private static final short CODE_EMIT_LABEL = 11 << 1;
            private static final short CODE_EMIT_BRANCH = 12 << 1;
            private static final short CODE_EMIT_LOAD_CONSTANT = 13 << 1;
            private static final short CODE_EMIT_LOAD_NULL = 14 << 1;
            private static final short CODE_EMIT_LOAD_ARGUMENT = 15 << 1;
            private static final short CODE_EMIT_LOAD_EXCEPTION = 16 << 1;
            private static final short CODE_EMIT_LOAD_LOCAL = 17 << 1;
            private static final short CODE_BEGIN_LOAD_LOCAL_MATERIALIZED = 18 << 1;
            private static final short CODE_END_LOAD_LOCAL_MATERIALIZED = (18 << 1) | 0b1;
            private static final short CODE_BEGIN_STORE_LOCAL = 19 << 1;
            private static final short CODE_END_STORE_LOCAL = (19 << 1) | 0b1;
            private static final short CODE_BEGIN_STORE_LOCAL_MATERIALIZED = 20 << 1;
            private static final short CODE_END_STORE_LOCAL_MATERIALIZED = (20 << 1) | 0b1;
            private static final short CODE_BEGIN_RETURN = 21 << 1;
            private static final short CODE_END_RETURN = (21 << 1) | 0b1;
            private static final short CODE_BEGIN_YIELD = 22 << 1;
            private static final short CODE_END_YIELD = (22 << 1) | 0b1;
            private static final short CODE_BEGIN_SOURCE = 23 << 1;
            private static final short CODE_END_SOURCE = (23 << 1) | 0b1;
            private static final short CODE_BEGIN_SOURCE_SECTION = 24 << 1;
            private static final short CODE_END_SOURCE_SECTION = (24 << 1) | 0b1;
            private static final short CODE_BEGIN_TAG = 25 << 1;
            private static final short CODE_END_TAG = (25 << 1) | 0b1;
            private static final short CODE_BEGIN_EARLY_RETURN = 26 << 1;
            private static final short CODE_END_EARLY_RETURN = (26 << 1) | 0b1;
            private static final short CODE_BEGIN_ADD_OPERATION = 27 << 1;
            private static final short CODE_END_ADD_OPERATION = (27 << 1) | 0b1;
            private static final short CODE_BEGIN_TO_STRING = 28 << 1;
            private static final short CODE_END_TO_STRING = (28 << 1) | 0b1;
            private static final short CODE_BEGIN_CALL = 29 << 1;
            private static final short CODE_END_CALL = (29 << 1) | 0b1;
            private static final short CODE_BEGIN_ADD_CONSTANT_OPERATION = 30 << 1;
            private static final short CODE_END_ADD_CONSTANT_OPERATION = (30 << 1) | 0b1;
            private static final short CODE_BEGIN_ADD_CONSTANT_OPERATION_AT_END = 31 << 1;
            private static final short CODE_END_ADD_CONSTANT_OPERATION_AT_END = (31 << 1) | 0b1;
            private static final short CODE_BEGIN_VERY_COMPLEX_OPERATION = 32 << 1;
            private static final short CODE_END_VERY_COMPLEX_OPERATION = (32 << 1) | 0b1;
            private static final short CODE_BEGIN_THROW_OPERATION = 33 << 1;
            private static final short CODE_END_THROW_OPERATION = (33 << 1) | 0b1;
            private static final short CODE_BEGIN_READ_EXCEPTION_OPERATION = 34 << 1;
            private static final short CODE_END_READ_EXCEPTION_OPERATION = (34 << 1) | 0b1;
            private static final short CODE_BEGIN_ALWAYS_BOX_OPERATION = 35 << 1;
            private static final short CODE_END_ALWAYS_BOX_OPERATION = (35 << 1) | 0b1;
            private static final short CODE_BEGIN_APPENDER_OPERATION = 36 << 1;
            private static final short CODE_END_APPENDER_OPERATION = (36 << 1) | 0b1;
            private static final short CODE_BEGIN_TEE_LOCAL = 37 << 1;
            private static final short CODE_END_TEE_LOCAL = (37 << 1) | 0b1;
            private static final short CODE_BEGIN_TEE_LOCAL_RANGE = 38 << 1;
            private static final short CODE_END_TEE_LOCAL_RANGE = (38 << 1) | 0b1;
            private static final short CODE_BEGIN_INVOKE = 39 << 1;
            private static final short CODE_END_INVOKE = (39 << 1) | 0b1;
            private static final short CODE_EMIT_MATERIALIZE_FRAME = 40 << 1;
            private static final short CODE_BEGIN_CREATE_CLOSURE = 41 << 1;
            private static final short CODE_END_CREATE_CLOSURE = (41 << 1) | 0b1;
            private static final short CODE_EMIT_VOID_OPERATION = 42 << 1;
            private static final short CODE_BEGIN_TO_BOOLEAN = 43 << 1;
            private static final short CODE_END_TO_BOOLEAN = (43 << 1) | 0b1;
            private static final short CODE_EMIT_GET_SOURCE_POSITION = 44 << 1;
            private static final short CODE_BEGIN_ENSURE_AND_GET_SOURCE_POSITION = 45 << 1;
            private static final short CODE_END_ENSURE_AND_GET_SOURCE_POSITION = (45 << 1) | 0b1;
            private static final short CODE_EMIT_GET_SOURCE_POSITIONS = 46 << 1;
            private static final short CODE_BEGIN_COPY_LOCALS_TO_FRAME = 47 << 1;
            private static final short CODE_END_COPY_LOCALS_TO_FRAME = (47 << 1) | 0b1;
            private static final short CODE_EMIT_GET_BYTECODE_LOCATION = 48 << 1;
            private static final short CODE_EMIT_COLLECT_BYTECODE_LOCATIONS = 49 << 1;
            private static final short CODE_EMIT_COLLECT_SOURCE_LOCATIONS = 50 << 1;
            private static final short CODE_EMIT_COLLECT_ALL_SOURCE_LOCATIONS = 51 << 1;
            private static final short CODE_BEGIN_CONTINUE = 52 << 1;
            private static final short CODE_END_CONTINUE = (52 << 1) | 0b1;
            private static final short CODE_EMIT_CURRENT_LOCATION = 53 << 1;
            private static final short CODE_EMIT_PRINT_HERE = 54 << 1;
            private static final short CODE_BEGIN_INCREMENT_VALUE = 55 << 1;
            private static final short CODE_END_INCREMENT_VALUE = (55 << 1) | 0b1;
            private static final short CODE_BEGIN_DOUBLE_VALUE = 56 << 1;
            private static final short CODE_END_DOUBLE_VALUE = (56 << 1) | 0b1;
            private static final short CODE_EMIT_ENABLE_INCREMENT_VALUE_INSTRUMENTATION = 57 << 1;
            private static final short CODE_BEGIN_ADD = 58 << 1;
            private static final short CODE_END_ADD = (58 << 1) | 0b1;
            private static final short CODE_BEGIN_MOD = 59 << 1;
            private static final short CODE_END_MOD = (59 << 1) | 0b1;
            private static final short CODE_BEGIN_LESS = 60 << 1;
            private static final short CODE_END_LESS = (60 << 1) | 0b1;
            private static final short CODE_EMIT_ENABLE_DOUBLE_VALUE_INSTRUMENTATION = 61 << 1;
            private static final short CODE_EMIT_EXPLICIT_BINDINGS_TEST = 62 << 1;
            private static final short CODE_EMIT_IMPLICIT_BINDINGS_TEST = 63 << 1;
            private static final short CODE_BEGIN_SC_AND = 64 << 1;
            private static final short CODE_END_SC_AND = (64 << 1) | 0b1;
            private static final short CODE_BEGIN_SC_OR = 65 << 1;
            private static final short CODE_END_SC_OR = (65 << 1) | 0b1;

            private final DataOutput buffer;
            private final BytecodeSerializer callback;
            private final SerializationState outer;
            private final int depth;
            private final HashMap<Object, Integer> objects = new HashMap<>();
            private final ArrayList<BasicInterpreter> builtNodes = new ArrayList<>();
            private final ArrayDeque<SerializationRootNode> rootStack = new ArrayDeque<>();
            private int labelCount;
            private int localCount;
            private short rootCount;
            private int finallyGeneratorCount;

            private SerializationState(DataOutput buffer, BytecodeSerializer callback) {
                this.buffer = buffer;
                this.callback = callback;
                this.outer = null;
                this.depth = 0;
            }

            private SerializationState(DataOutput buffer, SerializationState outer) {
                this.buffer = buffer;
                this.callback = outer.callback;
                this.outer = outer;
                this.depth = safeCastShort(outer.depth + 1);
            }

            private int serializeObject(Object object) throws IOException {
                Integer index = objects.get(object);
                if (index == null) {
                    index = objects.size();
                    objects.put(object, index);
                    if (object == null) {
                        buffer.writeShort(CODE_$CREATE_NULL);
                    } else {
                        buffer.writeShort(CODE_$CREATE_OBJECT);
                        callback.serialize(this, buffer, object);
                    }
                }
                return index;
            }

            @Override
            @SuppressWarnings("hiding")
            public void writeBytecodeNode(DataOutput buffer, BytecodeRootNode node) throws IOException {
                SerializationRootNode serializationRoot = (SerializationRootNode) node;
                buffer.writeInt(serializationRoot.contextDepth);
                buffer.writeInt(serializationRoot.rootIndex);
            }

        }
        private static final class DeserializationState implements DeserializerContext {

            private final DeserializationState outer;
            private final int depth;
            private final ArrayList<Object> consts = new ArrayList<>();
            private final ArrayList<BasicInterpreterWithUncached> builtNodes = new ArrayList<>();
            private final ArrayList<BytecodeLabel> labels = new ArrayList<>();
            private final ArrayList<BytecodeLocal> locals = new ArrayList<>();
            private final ArrayList<Runnable> finallyGenerators = new ArrayList<>();

            private DeserializationState(DeserializationState outer) {
                this.outer = outer;
                this.depth = (outer == null) ? 0 : outer.depth + 1;
            }

            @Override
            public BytecodeRootNode readBytecodeNode(DataInput buffer) throws IOException {
                return getContext(buffer.readInt()).builtNodes.get(buffer.readInt());
            }

            private DeserializationState getContext(int targetDepth) {
                assert targetDepth >= 0;
                DeserializationState ctx = this;
                while (ctx.depth != targetDepth) {
                    ctx = ctx.outer;
                }
                return ctx;
            }

        }
    }
    private static final class BytecodeConfigEncoderImpl extends BytecodeConfigEncoder {

        private static final BytecodeConfigEncoderImpl INSTANCE = new BytecodeConfigEncoderImpl();

        private BytecodeConfigEncoderImpl() {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
        }

        @Override
        protected long encodeInstrumentation(Class<?> c) throws IllegalArgumentException {
            long encoding = 0L;
            if (c == PrintHere.class) {
                encoding |= 0x1;
            } else if (c == IncrementValue.class) {
                encoding |= 0x2;
            } else if (c == DoubleValue.class) {
                encoding |= 0x4;
            } else {
                throw new IllegalArgumentException(String.format("Invalid instrumentation specified. Instrumentation '%s' does not exist or is not an instrumentation for 'com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreter'. Instrumentations can be specified using the @Instrumentation annotation.", c.getName()));
            }
            return encoding << 1;
        }

        @Override
        protected long encodeTag(Class<?> c) throws IllegalArgumentException {
            return ((long) CLASS_TO_TAG_MASK.get(c)) << 32;
        }

        private static long decode(BytecodeConfig config) {
            return decode(getEncoder(config), getEncoding(config));
        }

        private static long decode(BytecodeConfigEncoder encoder, long encoding) {
            if (encoder != null && encoder  != BytecodeConfigEncoderImpl.INSTANCE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Encoded config is not compatible with this bytecode node.");
            }
            return (encoding & 0xf0000000fL);
        }

    }
    private static final class BytecodeRootNodesImpl extends BytecodeRootNodes<BasicInterpreter> {

        private static final Object VISIBLE_TOKEN = TOKEN;

        @CompilationFinal private volatile long encoding;

        BytecodeRootNodesImpl(BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder> generator, BytecodeConfig config) {
            super(VISIBLE_TOKEN, generator);
            this.encoding = BytecodeConfigEncoderImpl.decode(config);
        }

        @Override
        @SuppressWarnings("hiding")
        protected boolean updateImpl(BytecodeConfigEncoder encoder, long encoding) {
            long maskedEncoding = BytecodeConfigEncoderImpl.decode(encoder, encoding);
            long oldEncoding = this.encoding;
            long newEncoding = maskedEncoding | oldEncoding;
            if ((oldEncoding | newEncoding) == oldEncoding) {
                return false;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return performUpdate(maskedEncoding);
        }

        private synchronized boolean performUpdate(long maskedEncoding) {
            CompilerAsserts.neverPartOfCompilation();
            long oldEncoding = this.encoding;
            long newEncoding = maskedEncoding | oldEncoding;
            if ((oldEncoding | newEncoding) == oldEncoding) {
                // double checked locking
                return false;
            }
            boolean oldSources = (oldEncoding & 0b1) != 0;
            int oldInstrumentations = (int)((oldEncoding >> 1) & 0x7FFF_FFFF);
            int oldTags = (int)((oldEncoding >> 32) & 0xFFFF_FFFF);
            boolean newSources = (newEncoding & 0b1) != 0;
            int newInstrumentations = (int)((newEncoding >> 1) & 0x7FFF_FFFF);
            int newTags = (int)((newEncoding >> 32) & 0xFFFF_FFFF);
            boolean needsBytecodeReparse = newInstrumentations != oldInstrumentations || newTags != oldTags;
            boolean needsSourceReparse = newSources != oldSources || (needsBytecodeReparse && newSources);
            if (!needsBytecodeReparse && !needsSourceReparse) {
                return false;
            }
            BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder> parser = getParserImpl();
            UpdateReason reason = new UpdateReason(oldSources != newSources, newInstrumentations & ~oldInstrumentations, newTags & ~oldTags);
            Builder builder = new Builder(this, needsBytecodeReparse, newTags, newInstrumentations, needsSourceReparse, reason);
            for (BasicInterpreter node : nodes) {
                builder.builtNodes.add((BasicInterpreterWithUncached) node);
            }
            parser.parse(builder);
            builder.finish();
            this.encoding = newEncoding;
            return true;
        }

        private void setNodes(BasicInterpreterWithUncached[] nodes) {
            if (this.nodes != null) {
                throw new AssertionError();
            }
            this.nodes = nodes;
            for (BasicInterpreterWithUncached node : nodes) {
                if (node.getRootNodes() != this) {
                    throw new AssertionError();
                }
                if (node != nodes[node.buildIndex]) {
                    throw new AssertionError();
                }
            }
        }

        @SuppressWarnings("unchecked")
        private BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder> getParserImpl() {
            return (BytecodeParser<com.oracle.truffle.api.bytecode.test.basic_interpreter.BasicInterpreterWithUncached.Builder>) super.getParser();
        }

        private boolean validate() {
            for (BasicInterpreter node : nodes) {
                ((BasicInterpreterWithUncached) node).getBytecodeNodeImpl().validateBytecodes();
            }
            return true;
        }

        private BytecodeDSLTestLanguage getLanguage() {
            if (nodes.length == 0) {
                return null;
            }
            return nodes[0].getLanguage(BytecodeDSLTestLanguage.class);
        }

        /**
         * Serializes the given bytecode nodes
         * All metadata (e.g., source info) is serialized (even if it has not yet been parsed).
         * <p>
         * This method serializes the root nodes with their current field values.
         *
         * @param buffer the buffer to write the byte output to.
         * @param callback the language-specific serializer for constants in the bytecode.
         */
        @Override
        @SuppressWarnings("cast")
        public void serialize(DataOutput buffer, BytecodeSerializer callback) throws IOException {
            ArrayList<BasicInterpreter> existingNodes = new ArrayList<>(nodes.length);
            for (int i = 0; i < nodes.length; i++) {
                existingNodes.add((BasicInterpreterWithUncached) nodes[i]);
            }
            BasicInterpreterWithUncached.doSerialize(buffer, callback, new Builder(getLanguage(), this, BytecodeConfig.COMPLETE), existingNodes);
        }

        private static final class UpdateReason implements CharSequence {

            private final boolean newSources;
            private final int newInstrumentations;
            private final int newTags;

            UpdateReason(boolean newSources, int newInstrumentations, int newTags) {
                this.newSources = newSources;
                this.newInstrumentations = newInstrumentations;
                this.newTags = newTags;
            }

            @Override
            public int length() {
                return toString().length();
            }

            @Override
            public char charAt(int index) {
                return toString().charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return toString().subSequence(start, end);
            }

            @Override
            public String toString() {
                StringBuilder message = new StringBuilder();
                message.append("BasicInterpreter requested ");
                String sep = "";
                if (newSources) {
                    message.append("SourceInformation");
                    sep = ", ";
                }
                if (newInstrumentations != 0) {
                    if ((newInstrumentations & 0x1) != 0) {
                        message.append(sep);
                        message.append("Instrumentation[PrintHere]");
                        sep = ", ";
                    }
                    if ((newInstrumentations & 0x2) != 0) {
                        message.append(sep);
                        message.append("Instrumentation[IncrementValue]");
                        sep = ", ";
                    }
                    if ((newInstrumentations & 0x4) != 0) {
                        message.append(sep);
                        message.append("Instrumentation[DoubleValue]");
                        sep = ", ";
                    }
                }
                if (newTags != 0) {
                    if ((newTags & 0x1) != 0) {
                        message.append(sep);
                        message.append("Tag[RootTag]");
                        sep = ", ";
                    }
                    if ((newTags & 0x2) != 0) {
                        message.append(sep);
                        message.append("Tag[RootBodyTag]");
                        sep = ", ";
                    }
                    if ((newTags & 0x4) != 0) {
                        message.append(sep);
                        message.append("Tag[ExpressionTag]");
                        sep = ", ";
                    }
                    if ((newTags & 0x8) != 0) {
                        message.append(sep);
                        message.append("Tag[StatementTag]");
                        sep = ", ";
                    }
                }
                message.append(".");
                return message.toString();
            }

        }
    }
    private static final class Instructions {

        /*
         * Instruction pop
         * kind: POP
         * encoding: [1 : short]
         * signature: void (Object)
         */
        private static final short POP = 1;
        /*
         * Instruction dup
         * kind: DUP
         * encoding: [2 : short]
         * signature: void ()
         */
        private static final short DUP = 2;
        /*
         * Instruction return
         * kind: RETURN
         * encoding: [3 : short]
         * signature: void (Object)
         */
        private static final short RETURN = 3;
        /*
         * Instruction branch
         * kind: BRANCH
         * encoding: [4 : short, branch_target (bci) : int]
         * signature: void ()
         */
        private static final short BRANCH = 4;
        /*
         * Instruction branch.backward
         * kind: BRANCH_BACKWARD
         * encoding: [5 : short, branch_target (bci) : int, loop_header_branch_profile (branch_profile) : int]
         * signature: void ()
         */
        private static final short BRANCH_BACKWARD = 5;
        /*
         * Instruction branch.false
         * kind: BRANCH_FALSE
         * encoding: [6 : short, branch_target (bci) : int, branch_profile : int]
         * signature: void (Object)
         */
        private static final short BRANCH_FALSE = 6;
        /*
         * Instruction store.local
         * kind: STORE_LOCAL
         * encoding: [7 : short, local_offset : short]
         * signature: void (Object)
         */
        private static final short STORE_LOCAL = 7;
        /*
         * Instruction throw
         * kind: THROW
         * encoding: [8 : short]
         * signature: void (Object)
         */
        private static final short THROW = 8;
        /*
         * Instruction load.constant
         * kind: LOAD_CONSTANT
         * encoding: [9 : short, constant (const) : int]
         * signature: Object ()
         */
        private static final short LOAD_CONSTANT = 9;
        /*
         * Instruction load.null
         * kind: LOAD_NULL
         * encoding: [10 : short]
         * signature: Object ()
         */
        private static final short LOAD_NULL = 10;
        /*
         * Instruction load.argument
         * kind: LOAD_ARGUMENT
         * encoding: [11 : short, index (short) : short]
         * signature: Object ()
         */
        private static final short LOAD_ARGUMENT = 11;
        /*
         * Instruction load.exception
         * kind: LOAD_EXCEPTION
         * encoding: [12 : short, exception_sp (sp) : short]
         * signature: Object ()
         */
        private static final short LOAD_EXCEPTION = 12;
        /*
         * Instruction load.local
         * kind: LOAD_LOCAL
         * encoding: [13 : short, local_offset : short]
         * signature: Object ()
         */
        private static final short LOAD_LOCAL = 13;
        /*
         * Instruction load.local.mat
         * kind: LOAD_LOCAL_MATERIALIZED
         * encoding: [14 : short, local_offset : short, root_index (local_root) : short]
         * signature: Object (Object)
         */
        private static final short LOAD_LOCAL_MAT = 14;
        /*
         * Instruction store.local.mat
         * kind: STORE_LOCAL_MATERIALIZED
         * encoding: [15 : short, local_offset : short, root_index (local_root) : short]
         * signature: void (Object, Object)
         */
        private static final short STORE_LOCAL_MAT = 15;
        /*
         * Instruction yield
         * kind: YIELD
         * encoding: [16 : short, location (const) : int]
         * signature: void (Object)
         */
        private static final short YIELD = 16;
        /*
         * Instruction tag.enter
         * kind: TAG_ENTER
         * encoding: [17 : short, tag : int]
         * signature: void ()
         */
        private static final short TAG_ENTER = 17;
        /*
         * Instruction tag.leave
         * kind: TAG_LEAVE
         * encoding: [18 : short, tag : int]
         * signature: Object (Object)
         */
        private static final short TAG_LEAVE = 18;
        /*
         * Instruction tag.leaveVoid
         * kind: TAG_LEAVE_VOID
         * encoding: [19 : short, tag : int]
         * signature: Object ()
         */
        private static final short TAG_LEAVE_VOID = 19;
        /*
         * Instruction tag.yield
         * kind: TAG_YIELD
         * encoding: [20 : short, tag : int]
         * signature: Object (Object)
         */
        private static final short TAG_YIELD = 20;
        /*
         * Instruction tag.resume
         * kind: TAG_RESUME
         * encoding: [21 : short, tag : int]
         * signature: void ()
         */
        private static final short TAG_RESUME = 21;
        /*
         * Instruction load.variadic_0
         * kind: LOAD_VARIADIC
         * encoding: [22 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_0 = 22;
        /*
         * Instruction load.variadic_1
         * kind: LOAD_VARIADIC
         * encoding: [23 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_1 = 23;
        /*
         * Instruction load.variadic_2
         * kind: LOAD_VARIADIC
         * encoding: [24 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_2 = 24;
        /*
         * Instruction load.variadic_3
         * kind: LOAD_VARIADIC
         * encoding: [25 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_3 = 25;
        /*
         * Instruction load.variadic_4
         * kind: LOAD_VARIADIC
         * encoding: [26 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_4 = 26;
        /*
         * Instruction load.variadic_5
         * kind: LOAD_VARIADIC
         * encoding: [27 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_5 = 27;
        /*
         * Instruction load.variadic_6
         * kind: LOAD_VARIADIC
         * encoding: [28 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_6 = 28;
        /*
         * Instruction load.variadic_7
         * kind: LOAD_VARIADIC
         * encoding: [29 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_7 = 29;
        /*
         * Instruction load.variadic_8
         * kind: LOAD_VARIADIC
         * encoding: [30 : short]
         * signature: void (Object)
         */
        private static final short LOAD_VARIADIC_8 = 30;
        /*
         * Instruction merge.variadic
         * kind: MERGE_VARIADIC
         * encoding: [31 : short]
         * signature: Object (Object)
         */
        private static final short MERGE_VARIADIC = 31;
        /*
         * Instruction constant_null
         * kind: STORE_NULL
         * encoding: [32 : short]
         * signature: Object ()
         */
        private static final short CONSTANT_NULL = 32;
        /*
         * Instruction clear.local
         * kind: CLEAR_LOCAL
         * encoding: [33 : short, local_offset : short]
         * signature: void ()
         */
        private static final short CLEAR_LOCAL = 33;
        /*
         * Instruction c.EarlyReturn
         * kind: CUSTOM
         * encoding: [34 : short, node : int]
         * nodeType: EarlyReturn
         * signature: void (Object)
         */
        private static final short EARLY_RETURN_ = 34;
        /*
         * Instruction c.AddOperation
         * kind: CUSTOM
         * encoding: [35 : short, node : int]
         * nodeType: AddOperation
         * signature: Object (Object, Object)
         */
        private static final short ADD_OPERATION_ = 35;
        /*
         * Instruction c.ToString
         * kind: CUSTOM
         * encoding: [36 : short, node : int]
         * nodeType: ToString
         * signature: Object (Object)
         */
        private static final short TO_STRING_ = 36;
        /*
         * Instruction c.Call
         * kind: CUSTOM
         * encoding: [37 : short, interpreter (const) : int, node : int]
         * nodeType: Call
         * signature: Object (BasicInterpreter, Object[]...)
         */
        private static final short CALL_ = 37;
        /*
         * Instruction c.AddConstantOperation
         * kind: CUSTOM
         * encoding: [38 : short, constantLhs (const) : int, node : int]
         * nodeType: AddConstantOperation
         * signature: Object (long, Object)
         */
        private static final short ADD_CONSTANT_OPERATION_ = 38;
        /*
         * Instruction c.AddConstantOperationAtEnd
         * kind: CUSTOM
         * encoding: [39 : short, constantRhs (const) : int, node : int]
         * nodeType: AddConstantOperationAtEnd
         * signature: Object (Object, long)
         */
        private static final short ADD_CONSTANT_OPERATION_AT_END_ = 39;
        /*
         * Instruction c.VeryComplexOperation
         * kind: CUSTOM
         * encoding: [40 : short, node : int]
         * nodeType: VeryComplexOperation
         * signature: long (long, Object[]...)
         */
        private static final short VERY_COMPLEX_OPERATION_ = 40;
        /*
         * Instruction c.ThrowOperation
         * kind: CUSTOM
         * encoding: [41 : short, node : int]
         * nodeType: ThrowOperation
         * signature: Object (long)
         */
        private static final short THROW_OPERATION_ = 41;
        /*
         * Instruction c.ReadExceptionOperation
         * kind: CUSTOM
         * encoding: [42 : short, node : int]
         * nodeType: ReadExceptionOperation
         * signature: long (TestException)
         */
        private static final short READ_EXCEPTION_OPERATION_ = 42;
        /*
         * Instruction c.AlwaysBoxOperation
         * kind: CUSTOM
         * encoding: [43 : short, node : int]
         * nodeType: AlwaysBoxOperation
         * signature: Object (Object)
         */
        private static final short ALWAYS_BOX_OPERATION_ = 43;
        /*
         * Instruction c.AppenderOperation
         * kind: CUSTOM
         * encoding: [44 : short, node : int]
         * nodeType: AppenderOperation
         * signature: void (List<?>, Object)
         */
        private static final short APPENDER_OPERATION_ = 44;
        /*
         * Instruction c.TeeLocal
         * kind: CUSTOM
         * encoding: [45 : short, setter (const) : int, node : int]
         * nodeType: TeeLocal
         * signature: Object (LocalAccessor, Object)
         */
        private static final short TEE_LOCAL_ = 45;
        /*
         * Instruction c.TeeLocalRange
         * kind: CUSTOM
         * encoding: [46 : short, setter (const) : int, node : int]
         * nodeType: TeeLocalRange
         * signature: Object (LocalRangeAccessor, Object)
         */
        private static final short TEE_LOCAL_RANGE_ = 46;
        /*
         * Instruction c.Invoke
         * kind: CUSTOM
         * encoding: [47 : short, node : int]
         * nodeType: Invoke
         * signature: Object (Object, Object[]...)
         */
        private static final short INVOKE_ = 47;
        /*
         * Instruction c.MaterializeFrame
         * kind: CUSTOM
         * encoding: [48 : short, node : int]
         * nodeType: MaterializeFrame
         * signature: MaterializedFrame ()
         */
        private static final short MATERIALIZE_FRAME_ = 48;
        /*
         * Instruction c.CreateClosure
         * kind: CUSTOM
         * encoding: [49 : short, node : int]
         * nodeType: CreateClosure
         * signature: TestClosure (BasicInterpreter)
         */
        private static final short CREATE_CLOSURE_ = 49;
        /*
         * Instruction c.VoidOperation
         * kind: CUSTOM
         * encoding: [50 : short, node : int]
         * nodeType: VoidOperation
         * signature: void ()
         */
        private static final short VOID_OPERATION_ = 50;
        /*
         * Instruction c.ToBoolean
         * kind: CUSTOM
         * encoding: [51 : short, node : int]
         * nodeType: ToBoolean
         * signature: boolean (Object)
         */
        private static final short TO_BOOLEAN_ = 51;
        /*
         * Instruction c.GetSourcePosition
         * kind: CUSTOM
         * encoding: [52 : short, node : int]
         * nodeType: GetSourcePosition
         * signature: SourceSection ()
         */
        private static final short GET_SOURCE_POSITION_ = 52;
        /*
         * Instruction c.EnsureAndGetSourcePosition
         * kind: CUSTOM
         * encoding: [53 : short, node : int]
         * nodeType: EnsureAndGetSourcePosition
         * signature: SourceSection (boolean)
         */
        private static final short ENSURE_AND_GET_SOURCE_POSITION_ = 53;
        /*
         * Instruction c.GetSourcePositions
         * kind: CUSTOM
         * encoding: [54 : short, node : int]
         * nodeType: GetSourcePositions
         * signature: SourceSection[] ()
         */
        private static final short GET_SOURCE_POSITIONS_ = 54;
        /*
         * Instruction c.CopyLocalsToFrame
         * kind: CUSTOM
         * encoding: [55 : short, node : int]
         * nodeType: CopyLocalsToFrame
         * signature: Frame (Object)
         */
        private static final short COPY_LOCALS_TO_FRAME_ = 55;
        /*
         * Instruction c.GetBytecodeLocation
         * kind: CUSTOM
         * encoding: [56 : short, node : int]
         * nodeType: GetBytecodeLocation
         * signature: BytecodeLocation ()
         */
        private static final short GET_BYTECODE_LOCATION_ = 56;
        /*
         * Instruction c.CollectBytecodeLocations
         * kind: CUSTOM
         * encoding: [57 : short, node : int]
         * nodeType: CollectBytecodeLocations
         * signature: List<BytecodeLocation> ()
         */
        private static final short COLLECT_BYTECODE_LOCATIONS_ = 57;
        /*
         * Instruction c.CollectSourceLocations
         * kind: CUSTOM
         * encoding: [58 : short, node : int]
         * nodeType: CollectSourceLocations
         * signature: List<SourceSection> ()
         */
        private static final short COLLECT_SOURCE_LOCATIONS_ = 58;
        /*
         * Instruction c.CollectAllSourceLocations
         * kind: CUSTOM
         * encoding: [59 : short, node : int]
         * nodeType: CollectAllSourceLocations
         * signature: List<SourceSection[]> ()
         */
        private static final short COLLECT_ALL_SOURCE_LOCATIONS_ = 59;
        /*
         * Instruction c.Continue
         * kind: CUSTOM
         * encoding: [60 : short, node : int]
         * nodeType: ContinueNode
         * signature: Object (ContinuationResult, Object)
         */
        private static final short CONTINUE_ = 60;
        /*
         * Instruction c.CurrentLocation
         * kind: CUSTOM
         * encoding: [61 : short, node : int]
         * nodeType: CurrentLocation
         * signature: BytecodeLocation ()
         */
        private static final short CURRENT_LOCATION_ = 61;
        /*
         * Instruction c.PrintHere
         * kind: CUSTOM
         * encoding: [62 : short, node : int]
         * nodeType: PrintHere
         * signature: void ()
         */
        private static final short PRINT_HERE_ = 62;
        /*
         * Instruction c.IncrementValue
         * kind: CUSTOM
         * encoding: [63 : short, node : int]
         * nodeType: IncrementValue
         * signature: long (long)
         */
        private static final short INCREMENT_VALUE_ = 63;
        /*
         * Instruction c.DoubleValue
         * kind: CUSTOM
         * encoding: [64 : short, node : int]
         * nodeType: DoubleValue
         * signature: long (long)
         */
        private static final short DOUBLE_VALUE_ = 64;
        /*
         * Instruction c.EnableIncrementValueInstrumentation
         * kind: CUSTOM
         * encoding: [65 : short, node : int]
         * nodeType: EnableIncrementValueInstrumentation
         * signature: void ()
         */
        private static final short ENABLE_INCREMENT_VALUE_INSTRUMENTATION_ = 65;
        /*
         * Instruction c.Add
         * kind: CUSTOM
         * encoding: [66 : short, node : int]
         * nodeType: Add
         * signature: long (long, long)
         */
        private static final short ADD_ = 66;
        /*
         * Instruction c.Mod
         * kind: CUSTOM
         * encoding: [67 : short, node : int]
         * nodeType: Mod
         * signature: long (long, long)
         */
        private static final short MOD_ = 67;
        /*
         * Instruction c.Less
         * kind: CUSTOM
         * encoding: [68 : short, node : int]
         * nodeType: Less
         * signature: boolean (long, long)
         */
        private static final short LESS_ = 68;
        /*
         * Instruction c.EnableDoubleValueInstrumentation
         * kind: CUSTOM
         * encoding: [69 : short, node : int]
         * nodeType: EnableDoubleValueInstrumentation
         * signature: void ()
         */
        private static final short ENABLE_DOUBLE_VALUE_INSTRUMENTATION_ = 69;
        /*
         * Instruction c.ExplicitBindingsTest
         * kind: CUSTOM
         * encoding: [70 : short, node : int]
         * nodeType: ExplicitBindingsTest
         * signature: Bindings ()
         */
        private static final short EXPLICIT_BINDINGS_TEST_ = 70;
        /*
         * Instruction c.ImplicitBindingsTest
         * kind: CUSTOM
         * encoding: [71 : short, node : int]
         * nodeType: ImplicitBindingsTest
         * signature: Bindings ()
         */
        private static final short IMPLICIT_BINDINGS_TEST_ = 71;
        /*
         * Instruction sc.ScAnd
         * kind: CUSTOM_SHORT_CIRCUIT
         * encoding: [72 : short, branch_target (bci) : int, branch_profile : int]
         * signature: Object (boolean, boolean)
         */
        private static final short SC_AND_ = 72;
        /*
         * Instruction sc.ScOr
         * kind: CUSTOM_SHORT_CIRCUIT
         * encoding: [73 : short, branch_target (bci) : int, branch_profile : int]
         * signature: Object (boolean, boolean)
         */
        private static final short SC_OR_ = 73;
        /*
         * Instruction invalidate0
         * kind: INVALIDATE
         * encoding: [74 : short]
         * signature: void ()
         */
        private static final short INVALIDATE0 = 74;
        /*
         * Instruction invalidate1
         * kind: INVALIDATE
         * encoding: [75 : short, invalidated0 (short) : short]
         * signature: void ()
         */
        private static final short INVALIDATE1 = 75;
        /*
         * Instruction invalidate2
         * kind: INVALIDATE
         * encoding: [76 : short, invalidated0 (short) : short, invalidated1 (short) : short]
         * signature: void ()
         */
        private static final short INVALIDATE2 = 76;
        /*
         * Instruction invalidate3
         * kind: INVALIDATE
         * encoding: [77 : short, invalidated0 (short) : short, invalidated1 (short) : short, invalidated2 (short) : short]
         * signature: void ()
         */
        private static final short INVALIDATE3 = 77;
        /*
         * Instruction invalidate4
         * kind: INVALIDATE
         * encoding: [78 : short, invalidated0 (short) : short, invalidated1 (short) : short, invalidated2 (short) : short, invalidated3 (short) : short]
         * signature: void ()
         */
        private static final short INVALIDATE4 = 78;

    }
    private static final class Operations {

        private static final int BLOCK = 1;
        private static final int ROOT = 2;
        private static final int IFTHEN = 3;
        private static final int IFTHENELSE = 4;
        private static final int CONDITIONAL = 5;
        private static final int WHILE = 6;
        private static final int TRYCATCH = 7;
        private static final int TRYFINALLY = 8;
        private static final int TRYCATCHOTHERWISE = 9;
        private static final int FINALLYHANDLER = 10;
        private static final int LABEL = 11;
        private static final int BRANCH = 12;
        private static final int LOADCONSTANT = 13;
        private static final int LOADNULL = 14;
        private static final int LOADARGUMENT = 15;
        private static final int LOADEXCEPTION = 16;
        private static final int LOADLOCAL = 17;
        private static final int LOADLOCALMATERIALIZED = 18;
        private static final int STORELOCAL = 19;
        private static final int STORELOCALMATERIALIZED = 20;
        private static final int RETURN = 21;
        private static final int YIELD = 22;
        private static final int SOURCE = 23;
        private static final int SOURCESECTION = 24;
        private static final int TAG = 25;
        private static final int EARLYRETURN = 26;
        private static final int ADDOPERATION = 27;
        private static final int TOSTRING = 28;
        private static final int CALL = 29;
        private static final int ADDCONSTANTOPERATION = 30;
        private static final int ADDCONSTANTOPERATIONATEND = 31;
        private static final int VERYCOMPLEXOPERATION = 32;
        private static final int THROWOPERATION = 33;
        private static final int READEXCEPTIONOPERATION = 34;
        private static final int ALWAYSBOXOPERATION = 35;
        private static final int APPENDEROPERATION = 36;
        private static final int TEELOCAL = 37;
        private static final int TEELOCALRANGE = 38;
        private static final int INVOKE = 39;
        private static final int MATERIALIZEFRAME = 40;
        private static final int CREATECLOSURE = 41;
        private static final int VOIDOPERATION = 42;
        private static final int TOBOOLEAN = 43;
        private static final int GETSOURCEPOSITION = 44;
        private static final int ENSUREANDGETSOURCEPOSITION = 45;
        private static final int GETSOURCEPOSITIONS = 46;
        private static final int COPYLOCALSTOFRAME = 47;
        private static final int GETBYTECODELOCATION = 48;
        private static final int COLLECTBYTECODELOCATIONS = 49;
        private static final int COLLECTSOURCELOCATIONS = 50;
        private static final int COLLECTALLSOURCELOCATIONS = 51;
        private static final int CONTINUE = 52;
        private static final int CURRENTLOCATION = 53;
        private static final int PRINTHERE = 54;
        private static final int INCREMENTVALUE = 55;
        private static final int DOUBLEVALUE = 56;
        private static final int ENABLEINCREMENTVALUEINSTRUMENTATION = 57;
        private static final int ADD = 58;
        private static final int MOD = 59;
        private static final int LESS = 60;
        private static final int ENABLEDOUBLEVALUEINSTRUMENTATION = 61;
        private static final int EXPLICITBINDINGSTEST = 62;
        private static final int IMPLICITBINDINGSTEST = 63;
        private static final int SCAND = 64;
        private static final int SCOR = 65;

    }
    private static final class ExceptionHandlerImpl extends ExceptionHandler {

        final AbstractBytecodeNode bytecode;
        final int baseIndex;

        ExceptionHandlerImpl(AbstractBytecodeNode bytecode, int baseIndex) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecode = bytecode;
            this.baseIndex = baseIndex;
        }

        @Override
        public HandlerKind getKind() {
            switch (bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_KIND]) {
                case HANDLER_TAG_EXCEPTIONAL :
                    return HandlerKind.TAG;
                default :
                    return HandlerKind.CUSTOM;
            }
        }

        @Override
        public int getStartBytecodeIndex() {
            return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_START_BCI];
        }

        @Override
        public int getEndBytecodeIndex() {
            return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_END_BCI];
        }

        @Override
        public int getHandlerBytecodeIndex() throws UnsupportedOperationException {
            switch (getKind()) {
                case TAG :
                    return super.getHandlerBytecodeIndex();
                default :
                    return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
            }
        }

        @Override
        public TagTree getTagTree() throws UnsupportedOperationException {
            if (getKind() == HandlerKind.TAG) {
                int nodeId = bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI];
                return bytecode.tagRoot.tagNodes[nodeId];
            } else {
                return super.getTagTree();
            }
        }

    }
    private static final class ExceptionHandlerList extends AbstractList<ExceptionHandler> {

        final AbstractBytecodeNode bytecode;

        ExceptionHandlerList(AbstractBytecodeNode bytecode) {
            this.bytecode = bytecode;
        }

        @Override
        public ExceptionHandler get(int index) {
            int baseIndex = index * EXCEPTION_HANDLER_LENGTH;
            if (baseIndex < 0 || baseIndex >= bytecode.handlers.length) {
                throw new IndexOutOfBoundsException(String.valueOf(index));
            }
            return new ExceptionHandlerImpl(bytecode, baseIndex);
        }

        @Override
        public int size() {
            return bytecode.handlers.length / EXCEPTION_HANDLER_LENGTH;
        }

    }
    private static final class SourceInformationImpl extends SourceInformation {

        final AbstractBytecodeNode bytecode;
        final int baseIndex;

        SourceInformationImpl(AbstractBytecodeNode bytecode, int baseIndex) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecode = bytecode;
            this.baseIndex = baseIndex;
        }

        @Override
        public int getStartBytecodeIndex() {
            return bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_START_BCI];
        }

        @Override
        public int getEndBytecodeIndex() {
            return bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_END_BCI];
        }

        @Override
        public SourceSection getSourceSection() {
            return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex);
        }

    }
    private static final class SourceInformationList extends AbstractList<SourceInformation> {

        final AbstractBytecodeNode bytecode;

        SourceInformationList(AbstractBytecodeNode bytecode) {
            this.bytecode = bytecode;
        }

        @Override
        public SourceInformation get(int index) {
            int baseIndex = index * SOURCE_INFO_LENGTH;
            if (baseIndex < 0 || baseIndex >= bytecode.sourceInfo.length) {
                throw new IndexOutOfBoundsException(String.valueOf(index));
            }
            return new SourceInformationImpl(bytecode, baseIndex);
        }

        @Override
        public int size() {
            return bytecode.sourceInfo.length / SOURCE_INFO_LENGTH;
        }

    }
    private static final class SourceInformationTreeImpl extends SourceInformationTree {

        static final int UNAVAILABLE_ROOT = -1;

        final AbstractBytecodeNode bytecode;
        final int baseIndex;
        final List<SourceInformationTree> children;

        SourceInformationTreeImpl(AbstractBytecodeNode bytecode, int baseIndex) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecode = bytecode;
            this.baseIndex = baseIndex;
            this.children = new LinkedList<SourceInformationTree>();
        }

        @Override
        public int getStartBytecodeIndex() {
            if (baseIndex == UNAVAILABLE_ROOT) {
                return 0;
            }
            return bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_START_BCI];
        }

        @Override
        public int getEndBytecodeIndex() {
            if (baseIndex == UNAVAILABLE_ROOT) {
                return bytecode.bytecodes.length;
            }
            return bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_END_BCI];
        }

        @Override
        public SourceSection getSourceSection() {
            if (baseIndex == UNAVAILABLE_ROOT) {
                return null;
            }
            return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex);
        }

        @Override
        public List<SourceInformationTree> getChildren() {
            return children;
        }

        private boolean contains(SourceInformationTreeImpl other) {
            if (baseIndex == UNAVAILABLE_ROOT) {
                return true;
            }
            return this.getStartBytecodeIndex() <= other.getStartBytecodeIndex() && other.getEndBytecodeIndex() <= this.getEndBytecodeIndex();
        }

        @TruffleBoundary
        private static SourceInformationTree parse(AbstractBytecodeNode bytecode) {
            int[] sourceInfo = bytecode.sourceInfo;
            if (sourceInfo.length == 0) {
                return null;
            }
            // Create a synthetic root node that contains all other SourceInformationTrees.
            SourceInformationTreeImpl root = new SourceInformationTreeImpl(bytecode, UNAVAILABLE_ROOT);
            int baseIndex = sourceInfo.length;
            SourceInformationTreeImpl current = root;
            ArrayDeque<SourceInformationTreeImpl> stack = new ArrayDeque<>();
            do {
                baseIndex -= SOURCE_INFO_LENGTH;
                SourceInformationTreeImpl newNode = new SourceInformationTreeImpl(bytecode, baseIndex);
                while (!current.contains(newNode)) {
                    current = stack.pop();
                }
                current.children.addFirst(newNode);
                stack.push(current);
                current = newNode;
            } while (baseIndex > 0);
            if (root.getChildren().size() == 1) {
                // If there is an actual root source section, ignore the synthetic root we created.
                return root.getChildren().getFirst();
            } else {
                return root;
            }
        }

    }
    private static final class LocalVariableImpl extends LocalVariable {

        final AbstractBytecodeNode bytecode;
        final int baseIndex;

        LocalVariableImpl(AbstractBytecodeNode bytecode, int baseIndex) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN);
            this.bytecode = bytecode;
            this.baseIndex = baseIndex;
        }

        @Override
        public int getStartIndex() {
            return bytecode.locals[baseIndex + LOCALS_OFFSET_START_BCI];
        }

        @Override
        public int getEndIndex() {
            return bytecode.locals[baseIndex + LOCALS_OFFSET_END_BCI];
        }

        @Override
        public Object getInfo() {
            int infoId = bytecode.locals[baseIndex + LOCALS_OFFSET_INFO];
            if (infoId == -1) {
                return null;
            } else {
                return ACCESS.readObject(bytecode.constants, infoId);
            }
        }

        @Override
        public Object getName() {
            int nameId = bytecode.locals[baseIndex + LOCALS_OFFSET_NAME];
            if (nameId == -1) {
                return null;
            } else {
                return ACCESS.readObject(bytecode.constants, nameId);
            }
        }

        @Override
        public int getLocalIndex() {
            return bytecode.locals[baseIndex + LOCALS_OFFSET_LOCAL_INDEX];
        }

        @Override
        public int getLocalOffset() {
            return bytecode.locals[baseIndex + LOCALS_OFFSET_FRAME_INDEX] - USER_LOCALS_START_INDEX;
        }

        @Override
        public FrameSlotKind getTypeProfile() {
            return null;
        }

    }
    private static final class LocalVariableList extends AbstractList<LocalVariable> {

        final AbstractBytecodeNode bytecode;

        LocalVariableList(AbstractBytecodeNode bytecode) {
            this.bytecode = bytecode;
        }

        @Override
        public LocalVariable get(int index) {
            int baseIndex = index * LOCALS_LENGTH;
            if (baseIndex < 0 || baseIndex >= bytecode.locals.length) {
                throw new IndexOutOfBoundsException(String.valueOf(index));
            }
            return new LocalVariableImpl(bytecode, baseIndex);
        }

        @Override
        public int size() {
            return bytecode.locals.length / LOCALS_LENGTH;
        }

    }
    private static final class ContinuationRootNodeImpl extends ContinuationRootNode {

        final BasicInterpreterWithUncached root;
        final int sp;
        @CompilationFinal volatile BytecodeLocation location;

        ContinuationRootNodeImpl(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, BasicInterpreterWithUncached root, int sp, BytecodeLocation location) {
            super(BytecodeRootNodesImpl.VISIBLE_TOKEN, language, frameDescriptor);
            this.root = root;
            this.sp = sp;
            this.location = location;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            if (args.length != 2) {
                throw CompilerDirectives.shouldNotReachHere("Expected 2 arguments: (parentFrame, inputValue)");
            }
            MaterializedFrame parentFrame = (MaterializedFrame) args[0];
            Object inputValue = args[1];
            if (parentFrame.getFrameDescriptor() != frame.getFrameDescriptor()) {
                throw CompilerDirectives.shouldNotReachHere("Invalid continuation parent frame passed");
            }
            // Copy any existing stack values (from numLocals to sp - 1) to the current frame, which will be used for stack accesses.
            FRAMES.copyTo(parentFrame, root.maxLocals, frame, root.maxLocals, sp - 1);
            FRAMES.setObject(frame, COROUTINE_FRAME_INDEX, parentFrame);
            FRAMES.setObject(frame, root.maxLocals + sp - 1, inputValue);
            BytecodeLocation bytecodeLocation = location;
            return root.continueAt((AbstractBytecodeNode) bytecodeLocation.getBytecodeNode(), bytecodeLocation.getBytecodeIndex(), sp + root.maxLocals, frame, parentFrame, this);
        }

        @Override
        public BytecodeRootNode getSourceRootNode() {
            return root;
        }

        @Override
        public BytecodeLocation getLocation() {
            return location;
        }

        @Override
        protected Frame findFrame(Frame frame) {
            return (Frame) frame.getObject(COROUTINE_FRAME_INDEX);
        }

        private void updateBytecodeLocation(BytecodeLocation newLocation, BytecodeNode oldBytecode, BytecodeNode newBytecode, CharSequence replaceReason) {
            CompilerAsserts.neverPartOfCompilation();
            location = newLocation;
            reportReplace(oldBytecode, newBytecode, replaceReason);
        }

        /**
         * Updates the location without reporting replacement (i.e., without invalidating compiled code).
         * <p>
         * We avoid reporting replacement when an update does not change the bytecode (e.g., a source reparse).
         * Any code path that depends on observing an up-to-date BytecodeNode (e.g., location computations) should
         * not be compiled (it must be guarded by a {@link TruffleBoundary}).
         */
        private void updateBytecodeLocationWithoutInvalidate(BytecodeLocation newLocation) {
            CompilerAsserts.neverPartOfCompilation();
            location = newLocation;
        }

        private ContinuationResult createContinuation(VirtualFrame frame, Object result) {
            return new ContinuationResult(this, frame.materialize(), result);
        }

        @Override
        public String toString() {
            return String.format("ContinuationRootNode [location=%s]", location);
        }

        @Override
        public boolean isCloningAllowed() {
            // Continuations are unique.
            return false;
        }

        @Override
        protected boolean isCloneUninitializedSupported() {
            // Continuations are unique.
            return false;
        }

        @Override
        public String getName() {
            return root.getName();
        }

    }
    private static final class ContinuationLocation {

        private final int constantPoolIndex;
        private final int bci;
        private final int sp;

        ContinuationLocation(int constantPoolIndex, int bci, int sp) {
            this.constantPoolIndex = constantPoolIndex;
            this.bci = bci;
            this.sp = sp;
        }

    }
    private static class LoopCounter {

        private static final int REPORT_LOOP_STRIDE = 1 << 8;

        private int value;

    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link EarlyReturn#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class EarlyReturn_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            EarlyReturn.perform(child0Value_);
            return;
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                EarlyReturn.perform(child0Value);
                return;
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link AddOperation#addLongs}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link AddOperation#addStrings}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class AddOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link AddOperation#addLongs}
         *   1: SpecializationActive {@link AddOperation#addStrings}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.AddOperation.addLongs(long, long)] || SpecializationActive[BasicInterpreter.AddOperation.addStrings(String, String)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddOperation.addLongs(long, long)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    if (child1Value_ instanceof Long) {
                        long child1Value__ = (long) child1Value_;
                        return AddOperation.addLongs(child0Value__, child1Value__);
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddOperation.addStrings(String, String)] */ && child0Value_ instanceof String) {
                    String child0Value__ = (String) child0Value_;
                    if (child1Value_ instanceof String) {
                        String child1Value__ = (String) child1Value_;
                        return AddOperation.addStrings(child0Value__, child1Value__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                if (child1Value instanceof Long) {
                    long child1Value_ = (long) child1Value;
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.AddOperation.addLongs(long, long)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddOperation$AddLongs");
                    return AddOperation.addLongs(child0Value_, child1Value_);
                }
            }
            if (child0Value instanceof String) {
                String child0Value_ = (String) child0Value;
                if (child1Value instanceof String) {
                    String child1Value_ = (String) child1Value;
                    state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.AddOperation.addStrings(String, String)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddOperation$AddStrings");
                    return AddOperation.addStrings(child0Value_, child1Value_);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "addLongs";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddOperation.addLongs(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "addStrings";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddOperation.addStrings(String, String)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    if (child1Value instanceof Long) {
                        long child1Value_ = (long) child1Value;
                        return AddOperation.addLongs(child0Value_, child1Value_);
                    }
                }
                if (child0Value instanceof String) {
                    String child0Value_ = (String) child0Value;
                    if (child1Value instanceof String) {
                        String child1Value_ = (String) child1Value;
                        return AddOperation.addStrings(child0Value_, child1Value_);
                    }
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ToString#doForeignObject}
     *     Activation probability: 0.65000
     *     With/without class size: 19/4 bytes
     *   Specialization {@link ToString#doForeignObject}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ToString_Node extends Node implements Introspection.Provider {

        static final ReferenceField<ForeignObject0Data> FOREIGN_OBJECT0_CACHE_UPDATER = ReferenceField.create(MethodHandles.lookup(), "foreignObject0_cache", ForeignObject0Data.class);
        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link ToString#doForeignObject}
         *   1: SpecializationActive {@link ToString#doForeignObject}
         * </pre> */
        @CompilationFinal private int state_0_;
        @UnsafeAccessedField @Child private ForeignObject0Data foreignObject0_cache;

        @ExplodeLoop
        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] || SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                    ForeignObject0Data s0_ = this.foreignObject0_cache;
                    while (s0_ != null) {
                        if ((s0_.interop_.accepts(child0Value_))) {
                            return ToString.doForeignObject(child0Value_, s0_.interop_);
                        }
                        s0_ = s0_.next_;
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                    EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                    Node prev_ = encapsulating_.set(this);
                    try {
                        {
                            InteropLibrary interop__ = (INTEROP_LIBRARY_.getUncached(child0Value_));
                            return ToString.doForeignObject(child0Value_, interop__);
                        }
                    } finally {
                        encapsulating_.set(prev_);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (((state_0 & 0b10)) == 0 /* is-not SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                while (true) {
                    int count0_ = 0;
                    ForeignObject0Data s0_ = FOREIGN_OBJECT0_CACHE_UPDATER.getVolatile(this);
                    ForeignObject0Data s0_original = s0_;
                    while (s0_ != null) {
                        if ((s0_.interop_.accepts(child0Value))) {
                            break;
                        }
                        count0_++;
                        s0_ = s0_.next_;
                    }
                    if (s0_ == null) {
                        // assert (s0_.interop_.accepts(child0Value));
                        if (count0_ < (2)) {
                            s0_ = this.insert(new ForeignObject0Data(s0_original));
                            InteropLibrary interop__ = s0_.insert((INTEROP_LIBRARY_.create(child0Value)));
                            Objects.requireNonNull(interop__, "A specialization cache returned a default value. The cache initializer must never return a default value for this cache. Use @Cached(neverDefault=false) to allow default values for this cached value or make sure the cache initializer never returns the default value.");
                            s0_.interop_ = interop__;
                            if (!FOREIGN_OBJECT0_CACHE_UPDATER.compareAndSet(this, s0_original, s0_)) {
                                continue;
                            }
                            state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */;
                            this.state_0_ = state_0;
                            $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ToString$ForeignObject0");
                        }
                    }
                    if (s0_ != null) {
                        return ToString.doForeignObject(child0Value, s0_.interop_);
                    }
                    break;
                }
            }
            {
                InteropLibrary interop__ = null;
                {
                    EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                    Node prev_ = encapsulating_.set(this);
                    try {
                        interop__ = (INTEROP_LIBRARY_.getUncached(child0Value));
                        this.foreignObject0_cache = null;
                        state_0 = state_0 & 0xfffffffe /* remove SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */;
                        state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */;
                        this.state_0_ = state_0;
                        $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ToString$ForeignObject1");
                        return ToString.doForeignObject(child0Value, interop__);
                    } finally {
                        encapsulating_.set(prev_);
                    }
                }
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doForeignObject";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                ForeignObject0Data s0_ = this.foreignObject0_cache;
                while (s0_ != null) {
                    cached.add(Arrays.<Object>asList(s0_.interop_));
                    s0_ = s0_.next_;
                }
                s[2] = cached;
            }
            if (s[1] == null) {
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                    s[1] = (byte)0b10 /* excluded */;
                } else {
                    s[1] = (byte)0b00 /* inactive */;
                }
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doForeignObject";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ToString.doForeignObject(Object, InteropLibrary)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class ForeignObject0Data extends Node implements SpecializationDataNode {

            @Child ForeignObject0Data next_;
            /**
             * Source Info: <pre>
             *   Specialization: {@link ToString#doForeignObject}
             *   Parameter: {@link InteropLibrary} interop</pre> */
            @Child InteropLibrary interop_;

            ForeignObject0Data(ForeignObject0Data next_) {
                this.next_ = next_;
            }

        }
        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return ToString.doForeignObject(child0Value, (INTEROP_LIBRARY_.getUncached(child0Value)));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link Call#call}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Call_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            BasicInterpreter interpreterValue_ = ACCESS.uncheckedCast(ACCESS.readObject($bytecode.constants, BYTES.getIntUnaligned($bc, $bci + 2 /* imm interpreter */)), BasicInterpreter.class);
            Object[] child0Value_ = (Object[]) FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            {
                Node location__ = (this);
                return Call.call(interpreterValue_, child0Value_, location__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "call";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, BasicInterpreter interpreterValue, Object[] child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return Call.call(interpreterValue, child0Value, ($bytecode));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link AddConstantOperation#addLongs}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link AddConstantOperation#addStrings}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class AddConstantOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link AddConstantOperation#addLongs}
         *   1: SpecializationActive {@link AddConstantOperation#addStrings}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            long constantLhsValue_ = ACCESS.uncheckedCast(ACCESS.readObject($bytecode.constants, BYTES.getIntUnaligned($bc, $bci + 2 /* imm constantLhs */)), Long.class);
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperation.addLongs(long, long)] || SpecializationActive[BasicInterpreter.AddConstantOperation.addStrings(long, String)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperation.addLongs(long, long)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    return AddConstantOperation.addLongs(constantLhsValue_, child0Value__);
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperation.addStrings(long, String)] */ && child0Value_ instanceof String) {
                    String child0Value__ = (String) child0Value_;
                    return AddConstantOperation.addStrings(constantLhsValue_, child0Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(constantLhsValue_, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(long constantLhsValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.AddConstantOperation.addLongs(long, long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddConstantOperation$AddLongs");
                return AddConstantOperation.addLongs(constantLhsValue, child0Value_);
            }
            if (child0Value instanceof String) {
                String child0Value_ = (String) child0Value;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.AddConstantOperation.addStrings(long, String)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddConstantOperation$AddStrings");
                return AddConstantOperation.addStrings(constantLhsValue, child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, constantLhsValue, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "addLongs";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperation.addLongs(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "addStrings";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperation.addStrings(long, String)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2JL(Node thisNode_, long constantLhsValue, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, constantLhsValue, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, long constantLhsValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return AddConstantOperation.addLongs(constantLhsValue, child0Value_);
                }
                if (child0Value instanceof String) {
                    String child0Value_ = (String) child0Value;
                    return AddConstantOperation.addStrings(constantLhsValue, child0Value_);
                }
                throw newUnsupportedSpecializationException2JL(this, constantLhsValue, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link AddConstantOperationAtEnd#addLongs}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link AddConstantOperationAtEnd#addStrings}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class AddConstantOperationAtEnd_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link AddConstantOperationAtEnd#addLongs}
         *   1: SpecializationActive {@link AddConstantOperationAtEnd#addStrings}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            long constantRhsValue_ = ACCESS.uncheckedCast(ACCESS.readObject($bytecode.constants, BYTES.getIntUnaligned($bc, $bci + 2 /* imm constantRhs */)), Long.class);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addLongs(long, long)] || SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addStrings(String, long)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addLongs(long, long)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    return AddConstantOperationAtEnd.addLongs(child0Value__, constantRhsValue_);
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addStrings(String, long)] */ && child0Value_ instanceof String) {
                    String child0Value__ = (String) child0Value_;
                    return AddConstantOperationAtEnd.addStrings(child0Value__, constantRhsValue_);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, constantRhsValue_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, long constantRhsValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addLongs(long, long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddConstantOperationAtEnd$AddLongs");
                return AddConstantOperationAtEnd.addLongs(child0Value_, constantRhsValue);
            }
            if (child0Value instanceof String) {
                String child0Value_ = (String) child0Value;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addStrings(String, long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AddConstantOperationAtEnd$AddStrings");
                return AddConstantOperationAtEnd.addStrings(child0Value_, constantRhsValue);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, constantRhsValue);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "addLongs";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addLongs(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "addStrings";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.AddConstantOperationAtEnd.addStrings(String, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2LJ(Node thisNode_, Object child0Value, long constantRhsValue) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, constantRhsValue);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, long constantRhsValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return AddConstantOperationAtEnd.addLongs(child0Value_, constantRhsValue);
                }
                if (child0Value instanceof String) {
                    String child0Value_ = (String) child0Value;
                    return AddConstantOperationAtEnd.addStrings(child0Value_, constantRhsValue);
                }
                throw newUnsupportedSpecializationException2LJ(this, child0Value, constantRhsValue);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link VeryComplexOperation#bla}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class VeryComplexOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link VeryComplexOperation#bla}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object[] child1Value_ = (Object[]) FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.VeryComplexOperation.bla(long, Object[])] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                return VeryComplexOperation.bla(child0Value__, child1Value_);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, Object[] child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.VeryComplexOperation.bla(long, Object[])] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "VeryComplexOperation$Bla");
                return VeryComplexOperation.bla(child0Value_, child1Value);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "bla";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.VeryComplexOperation.bla(long, Object[])] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, Object[] child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return VeryComplexOperation.bla(child0Value_, child1Value);
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ThrowOperation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ThrowOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link ThrowOperation#perform}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ThrowOperation.perform(long, Node)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                {
                    Node node__ = (this);
                    return ThrowOperation.perform(child0Value__, node__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                Node node__ = null;
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    node__ = (this);
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.ThrowOperation.perform(long, Node)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ThrowOperation$Perform");
                    return ThrowOperation.perform(child0Value_, node__);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "perform";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ThrowOperation.perform(long, Node)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return ThrowOperation.perform(child0Value_, ($bytecode));
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ReadExceptionOperation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ReadExceptionOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link ReadExceptionOperation#perform}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ReadExceptionOperation.perform(TestException)] */ && child0Value_ instanceof TestException) {
                TestException child0Value__ = (TestException) child0Value_;
                return ReadExceptionOperation.perform(child0Value__);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof TestException) {
                TestException child0Value_ = (TestException) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.ReadExceptionOperation.perform(TestException)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ReadExceptionOperation$Perform");
                return ReadExceptionOperation.perform(child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "perform";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ReadExceptionOperation.perform(TestException)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof TestException) {
                    TestException child0Value_ = (TestException) child0Value;
                    return ReadExceptionOperation.perform(child0Value_);
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link AlwaysBoxOperation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class AlwaysBoxOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            return AlwaysBoxOperation.perform(child0Value_);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return AlwaysBoxOperation.perform(child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link AppenderOperation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class AppenderOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link AppenderOperation#perform}
         * </pre> */
        @CompilationFinal private int state_0_;

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.AppenderOperation.perform(List<?>, Object)] */ && child0Value_ instanceof List<?>) {
                List<?> child0Value__ = (List<?>) child0Value_;
                AppenderOperation.perform(child0Value__, child1Value_);
                return;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
            return;
        }

        private void executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof List<?>) {
                List<?> child0Value_ = (List<?>) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.AppenderOperation.perform(List<?>, Object)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "AppenderOperation$Perform");
                AppenderOperation.perform(child0Value_, child1Value);
                return;
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "perform";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.AppenderOperation.perform(List<?>, Object)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof List<?>) {
                    List<?> child0Value_ = (List<?>) child0Value;
                    AppenderOperation.perform(child0Value_, child1Value);
                    return;
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link TeeLocal#doLong}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link TeeLocal#doGeneric}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class TeeLocal_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link TeeLocal#doLong}
         *   1: SpecializationActive {@link TeeLocal#doGeneric}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            LocalAccessor setterValue_ = ACCESS.uncheckedCast(ACCESS.readObject($bytecode.constants, BYTES.getIntUnaligned($bc, $bci + 2 /* imm setter */)), LocalAccessor.class);
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doLong(VirtualFrame, LocalAccessor, long, BytecodeNode)] || SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doLong(VirtualFrame, LocalAccessor, long, BytecodeNode)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    {
                        BytecodeNode bytecode__ = ($bytecode);
                        return TeeLocal.doLong(frameValue, setterValue_, child0Value__, bytecode__);
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */) {
                    {
                        BytecodeNode bytecode__1 = ($bytecode);
                        return TeeLocal.doGeneric(frameValue, setterValue_, child0Value_, bytecode__1);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(frameValue, setterValue_, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(VirtualFrame frameValue, LocalAccessor setterValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                BytecodeNode bytecode__ = null;
                if (((state_0 & 0b10)) == 0 /* is-not SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */ && child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    bytecode__ = ($bytecode);
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.TeeLocal.doLong(VirtualFrame, LocalAccessor, long, BytecodeNode)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "TeeLocal$Long");
                    return TeeLocal.doLong(frameValue, setterValue, child0Value_, bytecode__);
                }
            }
            {
                BytecodeNode bytecode__1 = null;
                bytecode__1 = ($bytecode);
                state_0 = state_0 & 0xfffffffe /* remove SpecializationActive[BasicInterpreter.TeeLocal.doLong(VirtualFrame, LocalAccessor, long, BytecodeNode)] */;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "TeeLocal$Generic");
                return TeeLocal.doGeneric(frameValue, setterValue, child0Value, bytecode__1);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doLong";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doLong(VirtualFrame, LocalAccessor, long, BytecodeNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */) {
                    s[1] = (byte)0b10 /* excluded */;
                } else {
                    s[1] = (byte)0b00 /* inactive */;
                }
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doGeneric";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocal.doGeneric(VirtualFrame, LocalAccessor, Object, BytecodeNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, LocalAccessor setterValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return TeeLocal.doGeneric(frameValue, setterValue, child0Value, ($bytecode));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link TeeLocalRange#doLong}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link TeeLocalRange#doGeneric}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class TeeLocalRange_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link TeeLocalRange#doLong}
         *   1: SpecializationActive {@link TeeLocalRange#doGeneric}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            LocalRangeAccessor setterValue_ = ACCESS.uncheckedCast(ACCESS.readObject($bytecode.constants, BYTES.getIntUnaligned($bc, $bci + 2 /* imm setter */)), LocalRangeAccessor.class);
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.TeeLocalRange.doLong(VirtualFrame, LocalRangeAccessor, long[], BytecodeNode)] || SpecializationActive[BasicInterpreter.TeeLocalRange.doGeneric(VirtualFrame, LocalRangeAccessor, Object[], BytecodeNode)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocalRange.doLong(VirtualFrame, LocalRangeAccessor, long[], BytecodeNode)] */ && child0Value_ instanceof long[]) {
                    long[] child0Value__ = (long[]) child0Value_;
                    {
                        BytecodeNode bytecode__ = ($bytecode);
                        return TeeLocalRange.doLong(frameValue, setterValue_, child0Value__, bytecode__);
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocalRange.doGeneric(VirtualFrame, LocalRangeAccessor, Object[], BytecodeNode)] */ && child0Value_ instanceof Object[]) {
                    Object[] child0Value__ = (Object[]) child0Value_;
                    {
                        BytecodeNode bytecode__1 = ($bytecode);
                        return TeeLocalRange.doGeneric(frameValue, setterValue_, child0Value__, bytecode__1);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(frameValue, setterValue_, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(VirtualFrame frameValue, LocalRangeAccessor setterValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                BytecodeNode bytecode__ = null;
                if (child0Value instanceof long[]) {
                    long[] child0Value_ = (long[]) child0Value;
                    bytecode__ = ($bytecode);
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.TeeLocalRange.doLong(VirtualFrame, LocalRangeAccessor, long[], BytecodeNode)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "TeeLocalRange$Long");
                    return TeeLocalRange.doLong(frameValue, setterValue, child0Value_, bytecode__);
                }
            }
            {
                BytecodeNode bytecode__1 = null;
                if (child0Value instanceof Object[]) {
                    Object[] child0Value_ = (Object[]) child0Value;
                    bytecode__1 = ($bytecode);
                    state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.TeeLocalRange.doGeneric(VirtualFrame, LocalRangeAccessor, Object[], BytecodeNode)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "TeeLocalRange$Generic");
                    return TeeLocalRange.doGeneric(frameValue, setterValue, child0Value_, bytecode__1);
                }
            }
            throw new UnsupportedSpecializationException(this, null, setterValue, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doLong";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocalRange.doLong(VirtualFrame, LocalRangeAccessor, long[], BytecodeNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doGeneric";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.TeeLocalRange.doGeneric(VirtualFrame, LocalRangeAccessor, Object[], BytecodeNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object setterValue, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, setterValue, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, LocalRangeAccessor setterValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof long[]) {
                    long[] child0Value_ = (long[]) child0Value;
                    return TeeLocalRange.doLong(frameValue, setterValue, child0Value_, ($bytecode));
                }
                if (child0Value instanceof Object[]) {
                    Object[] child0Value_ = (Object[]) child0Value;
                    return TeeLocalRange.doGeneric(frameValue, setterValue, child0Value_, ($bytecode));
                }
                throw newUnsupportedSpecializationException2(this, setterValue, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link Invoke#doRootNode}
     *     Activation probability: 0.38500
     *     With/without class size: 11/4 bytes
     *   Specialization {@link Invoke#doRootNodeUncached}
     *     Activation probability: 0.29500
     *     With/without class size: 7/0 bytes
     *   Specialization {@link Invoke#doClosure}
     *     Activation probability: 0.20500
     *     With/without class size: 8/4 bytes
     *   Specialization {@link Invoke#doClosureUncached}
     *     Activation probability: 0.11500
     *     With/without class size: 5/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Invoke_Node extends Node implements Introspection.Provider {

        static final ReferenceField<RootNodeData> ROOT_NODE_CACHE_UPDATER = ReferenceField.create(MethodHandles.lookup(), "rootNode_cache", RootNodeData.class);
        static final ReferenceField<ClosureData> CLOSURE_CACHE_UPDATER = ReferenceField.create(MethodHandles.lookup(), "closure_cache", ClosureData.class);
        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link Invoke#doRootNode}
         *   1: SpecializationActive {@link Invoke#doRootNodeUncached}
         *   2: SpecializationActive {@link Invoke#doClosure}
         *   3: SpecializationActive {@link Invoke#doClosureUncached}
         * </pre> */
        @CompilationFinal private int state_0_;
        /**
         * Source Info: <pre>
         *   Specialization: {@link Invoke#doRootNodeUncached}
         *   Parameter: {@link IndirectCallNode} callNode</pre> */
        @Child private IndirectCallNode callNode;
        @UnsafeAccessedField @Child private RootNodeData rootNode_cache;
        @UnsafeAccessedField @Child private ClosureData closure_cache;

        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object[] child1Value_ = (Object[]) FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] || SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] || SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] || SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */) {
                if ((state_0 & 0b11) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] || SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */ && child0Value_ instanceof BasicInterpreter) {
                    BasicInterpreter child0Value__ = (BasicInterpreter) child0Value_;
                    if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] */) {
                        RootNodeData s0_ = this.rootNode_cache;
                        if (s0_ != null) {
                            if ((Invoke.callTargetMatches(child0Value__.getCallTarget(), s0_.callNode_.getCallTarget()))) {
                                return Invoke.doRootNode(child0Value__, child1Value_, s0_.callNode_);
                            }
                        }
                    }
                    if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */) {
                        {
                            IndirectCallNode callNode_ = this.callNode;
                            if (callNode_ != null) {
                                return Invoke.doRootNodeUncached(child0Value__, child1Value_, callNode_);
                            }
                        }
                    }
                }
                if ((state_0 & 0b1100) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] || SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */ && child0Value_ instanceof TestClosure) {
                    TestClosure child0Value__ = (TestClosure) child0Value_;
                    if ((state_0 & 0b100) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] */) {
                        ClosureData s2_ = this.closure_cache;
                        if (s2_ != null) {
                            if ((Invoke.callTargetMatches(child0Value__.getCallTarget(), s2_.callNode_.getCallTarget()))) {
                                return Invoke.doClosure(child0Value__, child1Value_, s2_.callNode_);
                            }
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */) {
                        {
                            IndirectCallNode callNode_1 = this.callNode;
                            if (callNode_1 != null) {
                                return Invoke.doClosureUncached(child0Value__, child1Value_, callNode_1);
                            }
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, Object[] child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof BasicInterpreter) {
                BasicInterpreter child0Value_ = (BasicInterpreter) child0Value;
                if (((state_0 & 0b10)) == 0 /* is-not SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */) {
                    while (true) {
                        int count0_ = 0;
                        RootNodeData s0_ = ROOT_NODE_CACHE_UPDATER.getVolatile(this);
                        RootNodeData s0_original = s0_;
                        while (s0_ != null) {
                            if ((Invoke.callTargetMatches(child0Value_.getCallTarget(), s0_.callNode_.getCallTarget()))) {
                                break;
                            }
                            count0_++;
                            s0_ = null;
                            break;
                        }
                        if (s0_ == null && count0_ < 1) {
                            {
                                DirectCallNode callNode__ = this.insert((DirectCallNode.create(child0Value_.getCallTarget())));
                                if ((Invoke.callTargetMatches(child0Value_.getCallTarget(), callNode__.getCallTarget()))) {
                                    s0_ = this.insert(new RootNodeData());
                                    s0_.callNode_ = s0_.insert(callNode__);
                                    if (!ROOT_NODE_CACHE_UPDATER.compareAndSet(this, s0_original, s0_)) {
                                        continue;
                                    }
                                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] */;
                                    this.state_0_ = state_0;
                                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Invoke$RootNode");
                                }
                            }
                        }
                        if (s0_ != null) {
                            return Invoke.doRootNode(child0Value_, child1Value, s0_.callNode_);
                        }
                        break;
                    }
                }
                IndirectCallNode callNode_;
                IndirectCallNode callNode__shared = this.callNode;
                if (callNode__shared != null) {
                    callNode_ = callNode__shared;
                } else {
                    callNode_ = this.insert((IndirectCallNode.create()));
                    if (callNode_ == null) {
                        throw new IllegalStateException("A specialization returned a default value for a cached initializer. Default values are not supported for shared cached initializers because the default value is reserved for the uninitialized state.");
                    }
                }
                if (this.callNode == null) {
                    VarHandle.storeStoreFence();
                    this.callNode = callNode_;
                }
                this.rootNode_cache = null;
                state_0 = state_0 & 0xfffffffe /* remove SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] */;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Invoke$RootNodeUncached");
                return Invoke.doRootNodeUncached(child0Value_, child1Value, callNode_);
            }
            if (child0Value instanceof TestClosure) {
                TestClosure child0Value_ = (TestClosure) child0Value;
                if (((state_0 & 0b1000)) == 0 /* is-not SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */) {
                    while (true) {
                        int count2_ = 0;
                        ClosureData s2_ = CLOSURE_CACHE_UPDATER.getVolatile(this);
                        ClosureData s2_original = s2_;
                        while (s2_ != null) {
                            if ((Invoke.callTargetMatches(child0Value_.getCallTarget(), s2_.callNode_.getCallTarget()))) {
                                break;
                            }
                            count2_++;
                            s2_ = null;
                            break;
                        }
                        if (s2_ == null && count2_ < 1) {
                            {
                                DirectCallNode callNode__1 = this.insert((DirectCallNode.create(child0Value_.getCallTarget())));
                                if ((Invoke.callTargetMatches(child0Value_.getCallTarget(), callNode__1.getCallTarget()))) {
                                    s2_ = this.insert(new ClosureData());
                                    s2_.callNode_ = s2_.insert(callNode__1);
                                    if (!CLOSURE_CACHE_UPDATER.compareAndSet(this, s2_original, s2_)) {
                                        continue;
                                    }
                                    state_0 = state_0 | 0b100 /* add SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] */;
                                    this.state_0_ = state_0;
                                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Invoke$Closure");
                                }
                            }
                        }
                        if (s2_ != null) {
                            return Invoke.doClosure(child0Value_, child1Value, s2_.callNode_);
                        }
                        break;
                    }
                }
                IndirectCallNode callNode_1;
                IndirectCallNode callNode_1_shared = this.callNode;
                if (callNode_1_shared != null) {
                    callNode_1 = callNode_1_shared;
                } else {
                    callNode_1 = this.insert((IndirectCallNode.create()));
                    if (callNode_1 == null) {
                        throw new IllegalStateException("A specialization returned a default value for a cached initializer. Default values are not supported for shared cached initializers because the default value is reserved for the uninitialized state.");
                    }
                }
                if (this.callNode == null) {
                    VarHandle.storeStoreFence();
                    this.callNode = callNode_1;
                }
                this.closure_cache = null;
                state_0 = state_0 & 0xfffffffb /* remove SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] */;
                state_0 = state_0 | 0b1000 /* add SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Invoke$ClosureUncached");
                return Invoke.doClosureUncached(child0Value_, child1Value, callNode_1);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[5];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doRootNode";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNode(BasicInterpreter, Object[], DirectCallNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                RootNodeData s0_ = this.rootNode_cache;
                if (s0_ != null) {
                    cached.add(Arrays.<Object>asList(s0_.callNode_));
                }
                s[2] = cached;
            }
            if (s[1] == null) {
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */) {
                    s[1] = (byte)0b10 /* excluded */;
                } else {
                    s[1] = (byte)0b00 /* inactive */;
                }
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doRootNodeUncached";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doRootNodeUncached(BasicInterpreter, Object[], IndirectCallNode)] */) {
                {
                    IndirectCallNode callNode_ = this.callNode;
                    if (callNode_ != null) {
                        s[1] = (byte)0b01 /* active */;
                        ArrayList<Object> cached = new ArrayList<>();
                        cached.add(Arrays.<Object>asList(this.callNode));
                        s[2] = cached;
                    }
                }
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            s = new Object[3];
            s[0] = "doClosure";
            if ((state_0 & 0b100) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosure(TestClosure, Object[], DirectCallNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                ClosureData s2_ = this.closure_cache;
                if (s2_ != null) {
                    cached.add(Arrays.<Object>asList(s2_.callNode_));
                }
                s[2] = cached;
            }
            if (s[1] == null) {
                if ((state_0 & 0b1000) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */) {
                    s[1] = (byte)0b10 /* excluded */;
                } else {
                    s[1] = (byte)0b00 /* inactive */;
                }
            }
            data[3] = s;
            s = new Object[3];
            s[0] = "doClosureUncached";
            if ((state_0 & 0b1000) != 0 /* is SpecializationActive[BasicInterpreter.Invoke.doClosureUncached(TestClosure, Object[], IndirectCallNode)] */) {
                {
                    IndirectCallNode callNode_1 = this.callNode;
                    if (callNode_1 != null) {
                        s[1] = (byte)0b01 /* active */;
                        ArrayList<Object> cached = new ArrayList<>();
                        cached.add(Arrays.<Object>asList(this.callNode));
                        s[2] = cached;
                    }
                }
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[4] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class RootNodeData extends Node implements SpecializationDataNode {

            /**
             * Source Info: <pre>
             *   Specialization: {@link Invoke#doRootNode}
             *   Parameter: {@link DirectCallNode} callNode</pre> */
            @Child DirectCallNode callNode_;

            RootNodeData() {
            }

        }
        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class ClosureData extends Node implements SpecializationDataNode {

            /**
             * Source Info: <pre>
             *   Specialization: {@link Invoke#doClosure}
             *   Parameter: {@link DirectCallNode} callNode</pre> */
            @Child DirectCallNode callNode_;

            ClosureData() {
            }

        }
        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, Object[] child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof BasicInterpreter) {
                    BasicInterpreter child0Value_ = (BasicInterpreter) child0Value;
                    return Invoke.doRootNodeUncached(child0Value_, child1Value, (IndirectCallNode.getUncached()));
                }
                if (child0Value instanceof TestClosure) {
                    TestClosure child0Value_ = (TestClosure) child0Value;
                    return Invoke.doClosureUncached(child0Value_, child1Value, (IndirectCallNode.getUncached()));
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link MaterializeFrame#materialize}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class MaterializeFrame_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private MaterializedFrame execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            return MaterializeFrame.materialize(frameValue);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "materialize";
            s[1] = (byte)0b01 /* active */;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public MaterializedFrame executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return MaterializeFrame.materialize(frameValue);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CreateClosure#materialize}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CreateClosure_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link CreateClosure#materialize}
         * </pre> */
        @CompilationFinal private int state_0_;

        private TestClosure execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.CreateClosure.materialize(VirtualFrame, BasicInterpreter)] */ && child0Value_ instanceof BasicInterpreter) {
                BasicInterpreter child0Value__ = (BasicInterpreter) child0Value_;
                return CreateClosure.materialize(frameValue, child0Value__);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(frameValue, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private TestClosure executeAndSpecialize(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof BasicInterpreter) {
                BasicInterpreter child0Value_ = (BasicInterpreter) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.CreateClosure.materialize(VirtualFrame, BasicInterpreter)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "CreateClosure$Materialize");
                return CreateClosure.materialize(frameValue, child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "materialize";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.CreateClosure.materialize(VirtualFrame, BasicInterpreter)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public TestClosure executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof BasicInterpreter) {
                    BasicInterpreter child0Value_ = (BasicInterpreter) child0Value;
                    return CreateClosure.materialize(frameValue, child0Value_);
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link VoidOperation#doNothing}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class VoidOperation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            VoidOperation.doNothing();
            return;
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "doNothing";
            s[1] = (byte)0b01 /* active */;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                VoidOperation.doNothing();
                return;
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ToBoolean#doLong}
     *     Activation probability: 0.48333
     *     With/without class size: 9/0 bytes
     *   Specialization {@link ToBoolean#doBoolean}
     *     Activation probability: 0.33333
     *     With/without class size: 8/0 bytes
     *   Specialization {@link ToBoolean#doString}
     *     Activation probability: 0.18333
     *     With/without class size: 6/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ToBoolean_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link ToBoolean#doLong}
         *   1: SpecializationActive {@link ToBoolean#doBoolean}
         *   2: SpecializationActive {@link ToBoolean#doString}
         * </pre> */
        @CompilationFinal private int state_0_;

        private boolean execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doLong(long)] || SpecializationActive[BasicInterpreter.ToBoolean.doBoolean(boolean)] || SpecializationActive[BasicInterpreter.ToBoolean.doString(String)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doLong(long)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    return ToBoolean.doLong(child0Value__);
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doBoolean(boolean)] */ && child0Value_ instanceof Boolean) {
                    boolean child0Value__ = (boolean) child0Value_;
                    return ToBoolean.doBoolean(child0Value__);
                }
                if ((state_0 & 0b100) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doString(String)] */ && child0Value_ instanceof String) {
                    String child0Value__ = (String) child0Value_;
                    return ToBoolean.doString(child0Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private boolean executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.ToBoolean.doLong(long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ToBoolean$Long");
                return ToBoolean.doLong(child0Value_);
            }
            if (child0Value instanceof Boolean) {
                boolean child0Value_ = (boolean) child0Value;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.ToBoolean.doBoolean(boolean)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ToBoolean$Boolean");
                return ToBoolean.doBoolean(child0Value_);
            }
            if (child0Value instanceof String) {
                String child0Value_ = (String) child0Value;
                state_0 = state_0 | 0b100 /* add SpecializationActive[BasicInterpreter.ToBoolean.doString(String)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ToBoolean$String");
                return ToBoolean.doString(child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[4];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doLong";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doLong(long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doBoolean";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doBoolean(boolean)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            s = new Object[3];
            s[0] = "doString";
            if ((state_0 & 0b100) != 0 /* is SpecializationActive[BasicInterpreter.ToBoolean.doString(String)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[3] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public boolean executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return ToBoolean.doLong(child0Value_);
                }
                if (child0Value instanceof Boolean) {
                    boolean child0Value_ = (boolean) child0Value;
                    return ToBoolean.doBoolean(child0Value_);
                }
                if (child0Value instanceof String) {
                    String child0Value_ = (String) child0Value;
                    return ToBoolean.doString(child0Value_);
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link GetSourcePosition#doOperation}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class GetSourcePosition_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private SourceSection execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                Node node__ = (this);
                BytecodeNode bytecode__ = ($bytecode);
                return GetSourcePosition.doOperation(frameValue, node__, bytecode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "doOperation";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public SourceSection executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return GetSourcePosition.doOperation(frameValue, ($bytecode), ($bytecode));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link EnsureAndGetSourcePosition#doOperation}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class EnsureAndGetSourcePosition_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link EnsureAndGetSourcePosition#doOperation}
         * </pre> */
        @CompilationFinal private int state_0_;

        private SourceSection execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnsureAndGetSourcePosition.doOperation(VirtualFrame, boolean, Node, BytecodeNode)] */ && child0Value_ instanceof Boolean) {
                boolean child0Value__ = (boolean) child0Value_;
                {
                    Node node__ = (this);
                    BytecodeNode bytecode__ = ($bytecode);
                    return EnsureAndGetSourcePosition.doOperation(frameValue, child0Value__, node__, bytecode__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(frameValue, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private SourceSection executeAndSpecialize(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                BytecodeNode bytecode__ = null;
                Node node__ = null;
                if (child0Value instanceof Boolean) {
                    boolean child0Value_ = (boolean) child0Value;
                    node__ = (this);
                    bytecode__ = ($bytecode);
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.EnsureAndGetSourcePosition.doOperation(VirtualFrame, boolean, Node, BytecodeNode)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "EnsureAndGetSourcePosition$Operation");
                    return EnsureAndGetSourcePosition.doOperation(frameValue, child0Value_, node__, bytecode__);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doOperation";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnsureAndGetSourcePosition.doOperation(VirtualFrame, boolean, Node, BytecodeNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public SourceSection executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Boolean) {
                    boolean child0Value_ = (boolean) child0Value;
                    return EnsureAndGetSourcePosition.doOperation(frameValue, child0Value_, ($bytecode), ($bytecode));
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link GetSourcePositions#doOperation}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class GetSourcePositions_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private SourceSection[] execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                Node node__ = (this);
                BytecodeNode bytecode__ = ($bytecode);
                return GetSourcePositions.doOperation(frameValue, node__, bytecode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "doOperation";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public SourceSection[] executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return GetSourcePositions.doOperation(frameValue, ($bytecode), ($bytecode));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CopyLocalsToFrame#doSomeLocals}
     *     Activation probability: 0.65000
     *     With/without class size: 11/0 bytes
     *   Specialization {@link CopyLocalsToFrame#doAllLocals}
     *     Activation probability: 0.35000
     *     With/without class size: 8/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CopyLocalsToFrame_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link CopyLocalsToFrame#doSomeLocals}
         *   1: SpecializationActive {@link CopyLocalsToFrame#doAllLocals}
         * </pre> */
        @CompilationFinal private int state_0_;

        private Frame execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doSomeLocals(VirtualFrame, long, BytecodeNode, int)] || SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doAllLocals(VirtualFrame, Object, BytecodeNode, int)] */) {
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doSomeLocals(VirtualFrame, long, BytecodeNode, int)] */ && child0Value_ instanceof Long) {
                    long child0Value__ = (long) child0Value_;
                    {
                        BytecodeNode bytecodeNode__ = ($bytecode);
                        int bci__ = ($bci);
                        return CopyLocalsToFrame.doSomeLocals(frameValue, child0Value__, bytecodeNode__, bci__);
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doAllLocals(VirtualFrame, Object, BytecodeNode, int)] */) {
                    if ((child0Value_ == null)) {
                        BytecodeNode bytecodeNode__1 = ($bytecode);
                        int bci__1 = ($bci);
                        return CopyLocalsToFrame.doAllLocals(frameValue, child0Value_, bytecodeNode__1, bci__1);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(frameValue, child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Frame executeAndSpecialize(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                int bci__ = 0;
                BytecodeNode bytecodeNode__ = null;
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    bytecodeNode__ = ($bytecode);
                    bci__ = ($bci);
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doSomeLocals(VirtualFrame, long, BytecodeNode, int)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "CopyLocalsToFrame$SomeLocals");
                    return CopyLocalsToFrame.doSomeLocals(frameValue, child0Value_, bytecodeNode__, bci__);
                }
            }
            {
                int bci__1 = 0;
                BytecodeNode bytecodeNode__1 = null;
                if ((child0Value == null)) {
                    bytecodeNode__1 = ($bytecode);
                    bci__1 = ($bci);
                    state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doAllLocals(VirtualFrame, Object, BytecodeNode, int)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "CopyLocalsToFrame$AllLocals");
                    return CopyLocalsToFrame.doAllLocals(frameValue, child0Value, bytecodeNode__1, bci__1);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doSomeLocals";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doSomeLocals(VirtualFrame, long, BytecodeNode, int)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "doAllLocals";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.CopyLocalsToFrame.doAllLocals(VirtualFrame, Object, BytecodeNode, int)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                cached.add(Arrays.<Object>asList());
                s[2] = cached;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Frame executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return CopyLocalsToFrame.doSomeLocals(frameValue, child0Value_, ($bytecode), ($bci));
                }
                if ((child0Value == null)) {
                    return CopyLocalsToFrame.doAllLocals(frameValue, child0Value, ($bytecode), ($bci));
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link GetBytecodeLocation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class GetBytecodeLocation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private BytecodeLocation execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                Node node__ = (this);
                BytecodeNode bytecode__ = ($bytecode);
                return GetBytecodeLocation.perform(frameValue, node__, bytecode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public BytecodeLocation executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return GetBytecodeLocation.perform(frameValue, ($bytecode), ($bytecode));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CollectBytecodeLocations#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CollectBytecodeLocations_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private List<BytecodeLocation> execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeNode bytecode__ = ($bytecode);
                BasicInterpreter currentRootNode__ = ($bytecode.getRoot());
                return CollectBytecodeLocations.perform(bytecode__, currentRootNode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public List<BytecodeLocation> executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return CollectBytecodeLocations.perform(($bytecode), ($bytecode.getRoot()));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CollectSourceLocations#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CollectSourceLocations_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private List<SourceSection> execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeLocation location__ = ($bytecode.getBytecodeLocation($bci));
                BasicInterpreter currentRootNode__ = ($bytecode.getRoot());
                return CollectSourceLocations.perform(location__, currentRootNode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public List<SourceSection> executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return CollectSourceLocations.perform(($bytecode.getBytecodeLocation($bci)), ($bytecode.getRoot()));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CollectAllSourceLocations#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CollectAllSourceLocations_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private List<SourceSection[]> execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeLocation location__ = ($bytecode.getBytecodeLocation($bci));
                BasicInterpreter currentRootNode__ = ($bytecode.getRoot());
                return CollectAllSourceLocations.perform(location__, currentRootNode__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public List<SourceSection[]> executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return CollectAllSourceLocations.perform(($bytecode.getBytecodeLocation($bci)), ($bytecode.getRoot()));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ContinueNode#invokeDirect}
     *     Activation probability: 0.65000
     *     With/without class size: 22/8 bytes
     *   Specialization {@link ContinueNode#invokeIndirect}
     *     Activation probability: 0.35000
     *     With/without class size: 11/4 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Continue_Node extends Node implements Introspection.Provider {

        static final ReferenceField<InvokeDirectData> INVOKE_DIRECT_CACHE_UPDATER = ReferenceField.create(MethodHandles.lookup(), "invokeDirect_cache", InvokeDirectData.class);
        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link ContinueNode#invokeDirect}
         *   1: SpecializationActive {@link ContinueNode#invokeIndirect}
         * </pre> */
        @CompilationFinal private int state_0_;
        @UnsafeAccessedField @Child private InvokeDirectData invokeDirect_cache;
        /**
         * Source Info: <pre>
         *   Specialization: {@link ContinueNode#invokeIndirect}
         *   Parameter: {@link IndirectCallNode} callNode</pre> */
        @Child private IndirectCallNode invokeIndirect_callNode_;

        @ExplodeLoop
        private Object execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeDirect(ContinuationResult, Object, ContinuationRootNode, DirectCallNode)] || SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */ && child0Value_ instanceof ContinuationResult) {
                ContinuationResult child0Value__ = (ContinuationResult) child0Value_;
                if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeDirect(ContinuationResult, Object, ContinuationRootNode, DirectCallNode)] */) {
                    InvokeDirectData s0_ = this.invokeDirect_cache;
                    while (s0_ != null) {
                        if ((child0Value__.getContinuationRootNode() == s0_.rootNode_)) {
                            return ContinueNode.invokeDirect(child0Value__, child1Value_, s0_.rootNode_, s0_.callNode_);
                        }
                        s0_ = s0_.next_;
                    }
                }
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */) {
                    {
                        IndirectCallNode callNode__ = this.invokeIndirect_callNode_;
                        if (callNode__ != null) {
                            return ContinueNode.invokeIndirect(child0Value__, child1Value_, callNode__);
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private Object executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof ContinuationResult) {
                ContinuationResult child0Value_ = (ContinuationResult) child0Value;
                if (((state_0 & 0b10)) == 0 /* is-not SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */) {
                    while (true) {
                        int count0_ = 0;
                        InvokeDirectData s0_ = INVOKE_DIRECT_CACHE_UPDATER.getVolatile(this);
                        InvokeDirectData s0_original = s0_;
                        while (s0_ != null) {
                            if ((child0Value_.getContinuationRootNode() == s0_.rootNode_)) {
                                break;
                            }
                            count0_++;
                            s0_ = s0_.next_;
                        }
                        if (s0_ == null) {
                            {
                                ContinuationRootNode rootNode__ = this.insert((child0Value_.getContinuationRootNode()));
                                if ((child0Value_.getContinuationRootNode() == rootNode__) && count0_ < (ContinueNode.LIMIT)) {
                                    s0_ = this.insert(new InvokeDirectData(s0_original));
                                    s0_.rootNode_ = s0_.insert(rootNode__);
                                    s0_.callNode_ = s0_.insert((DirectCallNode.create(rootNode__.getCallTarget())));
                                    if (!INVOKE_DIRECT_CACHE_UPDATER.compareAndSet(this, s0_original, s0_)) {
                                        continue;
                                    }
                                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.ContinueNode.invokeDirect(ContinuationResult, Object, ContinuationRootNode, DirectCallNode)] */;
                                    this.state_0_ = state_0;
                                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ContinueNode$InvokeDirect");
                                }
                            }
                        }
                        if (s0_ != null) {
                            return ContinueNode.invokeDirect(child0Value_, child1Value, s0_.rootNode_, s0_.callNode_);
                        }
                        break;
                    }
                }
                VarHandle.storeStoreFence();
                this.invokeIndirect_callNode_ = this.insert((IndirectCallNode.create()));
                this.invokeDirect_cache = null;
                state_0 = state_0 & 0xfffffffe /* remove SpecializationActive[BasicInterpreter.ContinueNode.invokeDirect(ContinuationResult, Object, ContinuationRootNode, DirectCallNode)] */;
                state_0 = state_0 | 0b10 /* add SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "ContinueNode$InvokeIndirect");
                return ContinueNode.invokeIndirect(child0Value_, child1Value, this.invokeIndirect_callNode_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[3];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "invokeDirect";
            if ((state_0 & 0b1) != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeDirect(ContinuationResult, Object, ContinuationRootNode, DirectCallNode)] */) {
                s[1] = (byte)0b01 /* active */;
                ArrayList<Object> cached = new ArrayList<>();
                InvokeDirectData s0_ = this.invokeDirect_cache;
                while (s0_ != null) {
                    cached.add(Arrays.<Object>asList(s0_.rootNode_, s0_.callNode_));
                    s0_ = s0_.next_;
                }
                s[2] = cached;
            }
            if (s[1] == null) {
                if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */) {
                    s[1] = (byte)0b10 /* excluded */;
                } else {
                    s[1] = (byte)0b00 /* inactive */;
                }
            }
            data[1] = s;
            s = new Object[3];
            s[0] = "invokeIndirect";
            if ((state_0 & 0b10) != 0 /* is SpecializationActive[BasicInterpreter.ContinueNode.invokeIndirect(ContinuationResult, Object, IndirectCallNode)] */) {
                {
                    IndirectCallNode callNode__ = this.invokeIndirect_callNode_;
                    if (callNode__ != null) {
                        s[1] = (byte)0b01 /* active */;
                        ArrayList<Object> cached = new ArrayList<>();
                        cached.add(Arrays.<Object>asList(this.invokeIndirect_callNode_));
                        s[2] = cached;
                    }
                }
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[2] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class InvokeDirectData extends Node implements SpecializationDataNode {

            @Child InvokeDirectData next_;
            /**
             * Source Info: <pre>
             *   Specialization: {@link ContinueNode#invokeDirect}
             *   Parameter: {@link ContinuationRootNode} rootNode</pre> */
            @Child ContinuationRootNode rootNode_;
            /**
             * Source Info: <pre>
             *   Specialization: {@link ContinueNode#invokeDirect}
             *   Parameter: {@link DirectCallNode} callNode</pre> */
            @Child DirectCallNode callNode_;

            InvokeDirectData(InvokeDirectData next_) {
                this.next_ = next_;
            }

        }
        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Object executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof ContinuationResult) {
                    ContinuationResult child0Value_ = (ContinuationResult) child0Value;
                    return ContinueNode.invokeIndirect(child0Value_, child1Value, (IndirectCallNode.getUncached()));
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link CurrentLocation#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class CurrentLocation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private BytecodeLocation execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeLocation location__ = ($bytecode.getBytecodeLocation($bci));
                return CurrentLocation.perform(location__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public BytecodeLocation executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return CurrentLocation.perform(($bytecode.getBytecodeLocation($bci)));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link PrintHere#perform}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class PrintHere_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            PrintHere.perform();
            return;
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "perform";
            s[1] = (byte)0b01 /* active */;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                PrintHere.perform();
                return;
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link IncrementValue#doIncrement}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class IncrementValue_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link IncrementValue#doIncrement}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.IncrementValue.doIncrement(long)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                return IncrementValue.doIncrement(child0Value__);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.IncrementValue.doIncrement(long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "IncrementValue$Increment");
                return IncrementValue.doIncrement(child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doIncrement";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.IncrementValue.doIncrement(long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return IncrementValue.doIncrement(child0Value_);
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link DoubleValue#doDouble}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class DoubleValue_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link DoubleValue#doDouble}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.DoubleValue.doDouble(long)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                return DoubleValue.doDouble(child0Value__);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.DoubleValue.doDouble(long)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "DoubleValue$Double");
                return DoubleValue.doDouble(child0Value_);
            }
            throw new UnsupportedSpecializationException(this, null, child0Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doDouble";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.DoubleValue.doDouble(long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException1(Node thisNode_, Object child0Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    return DoubleValue.doDouble(child0Value_);
                }
                throw newUnsupportedSpecializationException1(this, child0Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link EnableIncrementValueInstrumentation#doEnable}
     *     Activation probability: 1.00000
     *     With/without class size: 20/4 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class EnableIncrementValueInstrumentation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link EnableIncrementValueInstrumentation#doEnable}
         * </pre> */
        @CompilationFinal private int state_0_;
        /**
         * Source Info: <pre>
         *   Specialization: {@link EnableIncrementValueInstrumentation#doEnable}
         *   Parameter: {@link BytecodeConfig} config</pre> */
        @CompilationFinal private BytecodeConfig config_;

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnableIncrementValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */) {
                {
                    BytecodeConfig config__ = this.config_;
                    if (config__ != null) {
                        BasicInterpreter root__ = ($bytecode.getRoot());
                        EnableIncrementValueInstrumentation.doEnable(root__, config__);
                        return;
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeAndSpecialize($stackFrame, $bytecode, $bc, $bci, $sp);
            return;
        }

        private void executeAndSpecialize(VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                BasicInterpreter root__ = null;
                root__ = ($bytecode.getRoot());
                BytecodeConfig config__ = (EnableIncrementValueInstrumentation.getConfig(root__));
                Objects.requireNonNull(config__, "A specialization cache returned a default value. The cache initializer must never return a default value for this cache. Use @Cached(neverDefault=false) to allow default values for this cached value or make sure the cache initializer never returns the default value.");
                VarHandle.storeStoreFence();
                this.config_ = config__;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.EnableIncrementValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "EnableIncrementValueInstrumentation$Enable");
                EnableIncrementValueInstrumentation.doEnable(root__, config__);
                return;
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doEnable";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnableIncrementValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */) {
                {
                    BytecodeConfig config__ = this.config_;
                    if (config__ != null) {
                        s[1] = (byte)0b01 /* active */;
                        ArrayList<Object> cached = new ArrayList<>();
                        cached.add(Arrays.<Object>asList(this.config_));
                        s[2] = cached;
                    }
                }
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                EnableIncrementValueInstrumentation.doEnable(($bytecode.getRoot()), (EnableIncrementValueInstrumentation.getConfig(($bytecode.getRoot()))));
                return;
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link Add#doInts}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Add_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link Add#doInts}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Add.doInts(long, long)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                if (child1Value_ instanceof Long) {
                    long child1Value__ = (long) child1Value_;
                    return Add.doInts(child0Value__, child1Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                if (child1Value instanceof Long) {
                    long child1Value_ = (long) child1Value;
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.Add.doInts(long, long)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Add$Ints");
                    return Add.doInts(child0Value_, child1Value_);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doInts";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Add.doInts(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    if (child1Value instanceof Long) {
                        long child1Value_ = (long) child1Value;
                        return Add.doInts(child0Value_, child1Value_);
                    }
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link Mod#doInts}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Mod_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link Mod#doInts}
         * </pre> */
        @CompilationFinal private int state_0_;

        private long execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Mod.doInts(long, long)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                if (child1Value_ instanceof Long) {
                    long child1Value__ = (long) child1Value_;
                    return Mod.doInts(child0Value__, child1Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private long executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                if (child1Value instanceof Long) {
                    long child1Value_ = (long) child1Value;
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.Mod.doInts(long, long)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Mod$Ints");
                    return Mod.doInts(child0Value_, child1Value_);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doInts";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Mod.doInts(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public long executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    if (child1Value instanceof Long) {
                        long child1Value_ = (long) child1Value;
                        return Mod.doInts(child0Value_, child1Value_);
                    }
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link Less#doInts}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class Less_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link Less#doInts}
         * </pre> */
        @CompilationFinal private int state_0_;

        private boolean execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            Object child0Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 2);
            Object child1Value_ = FRAMES.uncheckedGetObject($stackFrame, $sp - 1);
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Less.doInts(long, long)] */ && child0Value_ instanceof Long) {
                long child0Value__ = (long) child0Value_;
                if (child1Value_ instanceof Long) {
                    long child1Value__ = (long) child1Value_;
                    return Less.doInts(child0Value__, child1Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return executeAndSpecialize(child0Value_, child1Value_, $stackFrame, $bytecode, $bc, $bci, $sp);
        }

        private boolean executeAndSpecialize(Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (child0Value instanceof Long) {
                long child0Value_ = (long) child0Value;
                if (child1Value instanceof Long) {
                    long child1Value_ = (long) child1Value;
                    state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.Less.doInts(long, long)] */;
                    this.state_0_ = state_0;
                    $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "Less$Ints");
                    return Less.doInts(child0Value_, child1Value_);
                }
            }
            throw new UnsupportedSpecializationException(this, null, child0Value, child1Value);
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doInts";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.Less.doInts(long, long)] */) {
                s[1] = (byte)0b01 /* active */;
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @TruffleBoundary
        private static UnsupportedSpecializationException newUnsupportedSpecializationException2(Node thisNode_, Object child0Value, Object child1Value) {
            return new UnsupportedSpecializationException(thisNode_, null, child0Value, child1Value);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public boolean executeUncached(VirtualFrame frameValue, Object child0Value, Object child1Value, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (child0Value instanceof Long) {
                    long child0Value_ = (long) child0Value;
                    if (child1Value instanceof Long) {
                        long child1Value_ = (long) child1Value;
                        return Less.doInts(child0Value_, child1Value_);
                    }
                }
                throw newUnsupportedSpecializationException2(this, child0Value, child1Value);
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link EnableDoubleValueInstrumentation#doEnable}
     *     Activation probability: 1.00000
     *     With/without class size: 20/4 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class EnableDoubleValueInstrumentation_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        /**
         * State Info: <pre>
         *   0: SpecializationActive {@link EnableDoubleValueInstrumentation#doEnable}
         * </pre> */
        @CompilationFinal private int state_0_;
        /**
         * Source Info: <pre>
         *   Specialization: {@link EnableDoubleValueInstrumentation#doEnable}
         *   Parameter: {@link BytecodeConfig} config</pre> */
        @CompilationFinal private BytecodeConfig config_;

        private void execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnableDoubleValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */) {
                {
                    BytecodeConfig config__ = this.config_;
                    if (config__ != null) {
                        BasicInterpreter root__ = ($bytecode.getRoot());
                        EnableDoubleValueInstrumentation.doEnable(root__, config__);
                        return;
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            executeAndSpecialize($stackFrame, $bytecode, $bc, $bci, $sp);
            return;
        }

        private void executeAndSpecialize(VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            int state_0 = this.state_0_;
            {
                BasicInterpreter root__ = null;
                root__ = ($bytecode.getRoot());
                BytecodeConfig config__ = (EnableDoubleValueInstrumentation.getConfig(root__));
                Objects.requireNonNull(config__, "A specialization cache returned a default value. The cache initializer must never return a default value for this cache. Use @Cached(neverDefault=false) to allow default values for this cached value or make sure the cache initializer never returns the default value.");
                VarHandle.storeStoreFence();
                this.config_ = config__;
                state_0 = state_0 | 0b1 /* add SpecializationActive[BasicInterpreter.EnableDoubleValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */;
                this.state_0_ = state_0;
                $bytecode.getRoot().onSpecialize(new InstructionImpl($bytecode, $bci, BYTES.getShort($bc, $bci)), "EnableDoubleValueInstrumentation$Enable");
                EnableDoubleValueInstrumentation.doEnable(root__, config__);
                return;
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            int state_0 = this.state_0_;
            s = new Object[3];
            s[0] = "doEnable";
            if (state_0 != 0 /* is SpecializationActive[BasicInterpreter.EnableDoubleValueInstrumentation.doEnable(BasicInterpreter, BytecodeConfig)] */) {
                {
                    BytecodeConfig config__ = this.config_;
                    if (config__ != null) {
                        s[1] = (byte)0b01 /* active */;
                        ArrayList<Object> cached = new ArrayList<>();
                        cached.add(Arrays.<Object>asList(this.config_));
                        s[2] = cached;
                    }
                }
            }
            if (s[1] == null) {
                s[1] = (byte)0b00 /* inactive */;
            }
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public void executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                EnableDoubleValueInstrumentation.doEnable(($bytecode.getRoot()), (EnableDoubleValueInstrumentation.getConfig(($bytecode.getRoot()))));
                return;
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ExplicitBindingsTest#doDefault}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ExplicitBindingsTest_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private Bindings execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeNode bytecode__ = ($bytecode);
                BasicInterpreter root1__ = ($bytecode.getRoot());
                BytecodeRootNode root2__ = ($bytecode.getRoot());
                RootNode root3__ = ($bytecode.getRoot());
                BytecodeLocation location__ = ($bytecode.getBytecodeLocation($bci));
                Instruction instruction__ = ($bytecode.getInstruction($bci));
                Node node1__ = (this);
                Node node2__ = (this);
                int bytecodeIndex__ = ($bci);
                return ExplicitBindingsTest.doDefault(bytecode__, root1__, root2__, root3__, location__, instruction__, node1__, node2__, bytecodeIndex__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "doDefault";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Bindings executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return ExplicitBindingsTest.doDefault(($bytecode), ($bytecode.getRoot()), ($bytecode.getRoot()), ($bytecode.getRoot()), ($bytecode.getBytecodeLocation($bci)), ($bytecode.getInstruction($bci)), ($bytecode), ($bytecode), ($bci));
            }

        }
    }
    /**
     * Debug Info: <pre>
     *   Specialization {@link ImplicitBindingsTest#doDefault}
     *     Activation probability: 1.00000
     *     With/without class size: 16/0 bytes
     * </pre> */
    @SuppressWarnings("javadoc")
    private static final class ImplicitBindingsTest_Node extends Node implements Introspection.Provider {

        private static final Uncached UNCACHED = new Uncached();

        private Bindings execute(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
            {
                BytecodeNode bytecode__ = ($bytecode);
                BasicInterpreter root1__ = ($bytecode.getRoot());
                BytecodeRootNode root2__ = ($bytecode.getRoot());
                RootNode root3__ = ($bytecode.getRoot());
                BytecodeLocation location__ = ($bytecode.getBytecodeLocation($bci));
                Instruction instruction__ = ($bytecode.getInstruction($bci));
                Node node__ = (this);
                int bytecodeIndex__ = ($bci);
                return ImplicitBindingsTest.doDefault(bytecode__, root1__, root2__, root3__, location__, instruction__, node__, bytecodeIndex__);
            }
        }

        @Override
        public Introspection getIntrospectionData() {
            Object[] data = new Object[2];
            Object[] s;
            data[0] = 0;
            s = new Object[3];
            s[0] = "doDefault";
            s[1] = (byte)0b01 /* active */;
            ArrayList<Object> cached = new ArrayList<>();
            cached.add(Arrays.<Object>asList());
            s[2] = cached;
            data[1] = s;
            return Provider.create(data);
        }

        @GeneratedBy(BasicInterpreter.class)
        @DenyReplace
        private static final class Uncached extends Node implements UnadoptableNode {

            public Bindings executeUncached(VirtualFrame frameValue, VirtualFrame $stackFrame, AbstractBytecodeNode $bytecode, byte[] $bc, int $bci, int $sp) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return ImplicitBindingsTest.doDefault(($bytecode), ($bytecode.getRoot()), ($bytecode.getRoot()), ($bytecode.getRoot()), ($bytecode.getBytecodeLocation($bci)), ($bytecode.getInstruction($bci)), ($bytecode), ($bci));
            }

        }
    }
}
