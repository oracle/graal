// CheckStyle: start generated
package com.oracle.truffle.sl.operations;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.dsl.BoundaryCallFailedException;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.UnsafeFrameAccess;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.operation.OperationBuilder;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationParser;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.introspection.OperationIntrospection;
import com.oracle.truffle.api.operation.introspection.Argument.ArgumentKind;
import com.oracle.truffle.api.operation.serialization.OperationDeserializer;
import com.oracle.truffle.api.operation.serialization.OperationSerializer;
import com.oracle.truffle.api.operation.serialization.OperationDeserializer.DeserializerContext;
import com.oracle.truffle.api.operation.serialization.OperationSerializer.SerializerContext;
import com.oracle.truffle.api.operation.tracing.ExecutionTracer;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.ConcatNode;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.SLTypesGen;
import com.oracle.truffle.sl.nodes.expression.SLAddNode;
import com.oracle.truffle.sl.nodes.expression.SLDivNode;
import com.oracle.truffle.sl.nodes.expression.SLEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLFunctionLiteralNode;
import com.oracle.truffle.sl.nodes.expression.SLLessOrEqualNode;
import com.oracle.truffle.sl.nodes.expression.SLLessThanNode;
import com.oracle.truffle.sl.nodes.expression.SLLogicalNotNode;
import com.oracle.truffle.sl.nodes.expression.SLMulNode;
import com.oracle.truffle.sl.nodes.expression.SLReadPropertyNode;
import com.oracle.truffle.sl.nodes.expression.SLSubNode;
import com.oracle.truffle.sl.nodes.expression.SLWritePropertyNode;
import com.oracle.truffle.sl.nodes.util.SLToBooleanNode;
import com.oracle.truffle.sl.nodes.util.SLToMemberNode;
import com.oracle.truffle.sl.nodes.util.SLToMemberNodeGen;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode;
import com.oracle.truffle.sl.nodes.util.SLToTruffleStringNodeGen;
import com.oracle.truffle.sl.nodes.util.SLUnboxNode;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLObject;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;

@GeneratedBy(SLOperationRootNode.class)
@SuppressWarnings({"unused", "cast", "unchecked", "hiding", "rawtypes", "static-method"})
public final class SLOperationRootNodeGen extends SLOperationRootNode implements BytecodeOSRNode {

    @GeneratedBy(SLOperationRootNode.class)
    private static final class BytecodeNode extends BytecodeLoopBase {

        private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
        private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);

        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        @BytecodeInterpreterSwitch
        @Override
        int continueAt(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals) {
            int $sp = $startSp;
            int $bci = $startBci;
            Counter loopCounter = new Counter();
            $frame.getArguments();
            ExecutionTracer tracer = ExecutionTracer.get(SLOperationRootNode.class);
            tracer.startFunction($this);
            try {
                loop: while (true) {
                    CompilerAsserts.partialEvaluationConstant($bci);
                    CompilerAsserts.partialEvaluationConstant($sp);
                    int curOpcode = unsafeFromBytecode($bc, $bci) & 0xffff;
                    CompilerAsserts.partialEvaluationConstant(curOpcode);
                    if ($this.isBbStart[$bci]) {
                        tracer.traceStartBasicBlock($bci);
                    }
                    try {
                        assert $sp >= maxLocals : "stack underflow @ " + $bci;
                        switch (curOpcode) {
                            // branch
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            case ((INSTR_BRANCH << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_BRANCH, 1, 0);
                                int targetBci = unsafeFromBytecode($bc, $bci + BRANCH_BRANCH_TARGET_OFFSET + 0);
                                if (targetBci <= $bci) {
                                    if (CompilerDirectives.hasNextTier() && ++loopCounter.count >= 256) {
                                        TruffleSafepoint.poll($this);
                                        LoopNode.reportLoopCount($this, 256);
                                        loopCounter.count = 0;
                                        if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge($this)) {
                                            Object osrResult = BytecodeOSRNode.tryOSR($this, ($sp << 16) | targetBci, $frame, null, $frame);
                                            if (osrResult != null) {
                                                $frame.setObject(0, osrResult);
                                                return 0x0000ffff;
                                            }
                                        }
                                    }
                                }
                                $bci = targetBci;
                                continue loop;
                            }
                            // branch.false
                            //   Simple Pops:
                            //     [ 0] condition
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            //   Branch Profiles:
                            //     [ 0] profile
                            case ((INSTR_BRANCH_FALSE << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_BRANCH_FALSE, 1, 0);
                                boolean cond = UFA.unsafeGetObject($frame, $sp - 1) == Boolean.TRUE;
                                $sp = $sp - 1;
                                if (do_profileCondition($this, cond, $conditionProfiles, unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_PROFILE_OFFSET + 0))) {
                                    $bci = $bci + BRANCH_FALSE_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // throw
                            //   Locals:
                            //     [ 0] exception
                            //   Pushed Values: 0
                            case ((INSTR_THROW << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_THROW, 1, 0);
                                int slot = unsafeFromBytecode($bc, $bci + THROW_LOCALS_OFFSET + 0);
                                throw (AbstractTruffleException) UFA.unsafeUncheckedGetObject($frame, slot);
                            }
                            // return
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            case ((INSTR_RETURN << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_RETURN, 1, 0);
                                return (($sp - 1) << 16) | 0xffff;
                            }
                            // sc.SLAnd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Split on Boxing Elimination
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                            case ((INSTR_SC_SL_AND << 3) | 0 /* OBJECT */) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_AND, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_AND, SLOperationRootNodeGen.doGetStateBits_SLAnd_($bc, $bci));
                                if (BytecodeNode.SLAnd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_AND_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            case ((INSTR_SC_SL_AND << 3) | 5 /* BOOLEAN */) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_AND, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_AND, SLOperationRootNodeGen.doGetStateBits_SLAnd_($bc, $bci));
                                if (BytecodeNode.SLAnd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_AND_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // sc.SLOr
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Split on Boxing Elimination
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                            case ((INSTR_SC_SL_OR << 3) | 0 /* OBJECT */) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_OR, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_OR, SLOperationRootNodeGen.doGetStateBits_SLOr_($bc, $bci));
                                if (!BytecodeNode.SLOr_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_OR_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            case ((INSTR_SC_SL_OR << 3) | 5 /* BOOLEAN */) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_OR, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_OR, SLOperationRootNodeGen.doGetStateBits_SLOr_($bc, $bci));
                                if (!BytecodeNode.SLOr_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_OR_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // length group 1 (1 / 1)
                            case ((INSTR_POP << 3) | 0) :
                                $sp = instructionGroup_1_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 1;
                                continue loop;
                            // length group 2 (1 / 1)
                            case ((INSTR_LOAD_CONSTANT << 3) | 0) :
                            case ((INSTR_LOAD_ARGUMENT << 3) | 0) :
                            case ((INSTR_LOAD_LOCAL << 3) | 0 /* OBJECT */) :
                            case ((INSTR_LOAD_LOCAL << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_LOAD_LOCAL << 3) | 1 /* LONG */) :
                            case ((INSTR_LOAD_LOCAL_BOXED << 3) | 0 /* OBJECT */) :
                            case ((INSTR_LOAD_LOCAL_BOXED << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_LOAD_LOCAL_BOXED << 3) | 1 /* LONG */) :
                            case ((INSTR_LOAD_LOCAL_MAT << 3) | 0) :
                            case ((INSTR_STORE_LOCAL_MAT << 3) | 0) :
                                $sp = instructionGroup_2_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 2;
                                continue loop;
                            // length group 3 (1 / 1)
                            case ((INSTR_STORE_LOCAL << 3) | 0 /* OBJECT */) :
                            case ((INSTR_STORE_LOCAL << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_STORE_LOCAL << 3) | 1 /* LONG */) :
                            case ((INSTR_STORE_LOCAL << 3) | 7) :
                                $sp = instructionGroup_3_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 3;
                                continue loop;
                            // length group 5 (1 / 1)
                            case ((INSTR_C_SL_EQUAL << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_EQUAL << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_C_SL_LESS_THAN << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_LESS_THAN << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_C_SL_LOGICAL_NOT << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_LOGICAL_NOT << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_C_SL_UNBOX << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_UNBOX << 3) | 5 /* BOOLEAN */) :
                            case ((INSTR_C_SL_UNBOX << 3) | 1 /* LONG */) :
                            case ((INSTR_C_SL_FUNCTION_LITERAL << 3) | 0) :
                            case ((INSTR_C_SL_TO_BOOLEAN << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_TO_BOOLEAN << 3) | 5 /* BOOLEAN */) :
                                $sp = instructionGroup_5_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 5;
                                continue loop;
                            // length group 6 (1 / 1)
                            case ((INSTR_C_SL_ADD << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_ADD << 3) | 1 /* LONG */) :
                            case ((INSTR_C_SL_DIV << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_DIV << 3) | 1 /* LONG */) :
                            case ((INSTR_C_SL_MUL << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_MUL << 3) | 1 /* LONG */) :
                            case ((INSTR_C_SL_READ_PROPERTY << 3) | 0) :
                            case ((INSTR_C_SL_SUB << 3) | 0 /* OBJECT */) :
                            case ((INSTR_C_SL_SUB << 3) | 1 /* LONG */) :
                                $sp = instructionGroup_6_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 6;
                                continue loop;
                            // length group 7 (1 / 1)
                            case ((INSTR_C_SL_WRITE_PROPERTY << 3) | 0) :
                            case ((INSTR_C_SL_INVOKE << 3) | 0) :
                                $sp = instructionGroup_7_0($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 7;
                                continue loop;
                            default :
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw CompilerDirectives.shouldNotReachHere("unknown opcode encountered: " + curOpcode + " @ " + $bci + "");
                        }
                    } catch (AbstractTruffleException ex) {
                        CompilerAsserts.partialEvaluationConstant($bci);
                        for (int handlerIndex = $handlers.length - 1; handlerIndex >= 0; handlerIndex--) {
                            CompilerAsserts.partialEvaluationConstant(handlerIndex);
                            ExceptionHandler handler = $handlers[handlerIndex];
                            if (handler.startBci > $bci || handler.endBci <= $bci) continue;
                            $sp = handler.startStack + maxLocals;
                            $frame.setObject(handler.exceptionIndex, ex);
                            $bci = handler.handlerBci;
                            continue loop;
                        }
                        throw ex;
                    }
                }
            } finally {
                tracer.endFunction($this);
            }
        }

        @Override
        void prepareForAOT(SLOperationRootNodeGen $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root) {
            int $bci = 0;
            while ($bci < $bc.length) {
                switch ((unsafeFromBytecode($bc, $bci) >> 3) & 8191) {
                    case INSTR_POP :
                    {
                        $bci = $bci + POP_LENGTH;
                        break;
                    }
                    case INSTR_BRANCH :
                    {
                        $bci = $bci + BRANCH_LENGTH;
                        break;
                    }
                    case INSTR_BRANCH_FALSE :
                    {
                        $bci = $bci + BRANCH_FALSE_LENGTH;
                        break;
                    }
                    case INSTR_THROW :
                    {
                        $bci = $bci + THROW_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_CONSTANT :
                    {
                        $bci = $bci + LOAD_CONSTANT_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_ARGUMENT :
                    {
                        $bci = $bci + LOAD_ARGUMENT_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL :
                    {
                        $bci = $bci + LOAD_LOCAL_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL_BOXED :
                    {
                        $bci = $bci + LOAD_LOCAL_BOXED_LENGTH;
                        break;
                    }
                    case INSTR_STORE_LOCAL :
                    {
                        $bci = $bci + STORE_LOCAL_LENGTH;
                        break;
                    }
                    case INSTR_RETURN :
                    {
                        $bci = $bci + RETURN_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL_MAT :
                    {
                        $bci = $bci + LOAD_LOCAL_MAT_LENGTH;
                        break;
                    }
                    case INSTR_STORE_LOCAL_MAT :
                    {
                        $bci = $bci + STORE_LOCAL_MAT_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_ADD :
                    {
                        $bci = $bci + C_SL_ADD_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_DIV :
                    {
                        $bci = $bci + C_SL_DIV_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_EQUAL :
                    {
                        $bci = $bci + C_SL_EQUAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LESS_OR_EQUAL :
                    {
                        $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LESS_THAN :
                    {
                        $bci = $bci + C_SL_LESS_THAN_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LOGICAL_NOT :
                    {
                        $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_MUL :
                    {
                        $bci = $bci + C_SL_MUL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_READ_PROPERTY :
                    {
                        $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_SUB :
                    {
                        $bci = $bci + C_SL_SUB_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_WRITE_PROPERTY :
                    {
                        $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_UNBOX :
                    {
                        $bci = $bci + C_SL_UNBOX_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_FUNCTION_LITERAL :
                    {
                        $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_TO_BOOLEAN :
                    {
                        $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_INVOKE :
                    {
                        $bci = $bci + C_SL_INVOKE_LENGTH;
                        break;
                    }
                    case INSTR_SC_SL_AND :
                    {
                        $bci = $bci + SC_SL_AND_LENGTH;
                        break;
                    }
                    case INSTR_SC_SL_OR :
                    {
                        $bci = $bci + SC_SL_OR_LENGTH;
                        break;
                    }
                }
            }
        }

        @Override
        OperationIntrospection getIntrospectionData(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts, OperationNodesImpl nodes, int[] sourceInfo) {
            int $bci = 0;
            ArrayList<Object[]> target = new ArrayList<>();
            while ($bci < $bc.length) {
                switch ((unsafeFromBytecode($bc, $bci) >> 3) & 8191) {
                    default :
                    {
                        Object[] dec = new Object[]{$bci, "unknown", Arrays.copyOfRange($bc, $bci, $bci + 1), null};
                        $bci++;
                        target.add(dec);
                        break;
                    }
                    case INSTR_POP :
                    {
                        Object[] dec = new Object[] {$bci, "pop", Arrays.copyOfRange($bc, $bci, $bci + POP_LENGTH), new Object[] {}};
                        $bci = $bci + POP_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_BRANCH :
                    {
                        Object[] dec = new Object[] {$bci, "branch", Arrays.copyOfRange($bc, $bci, $bci + BRANCH_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + BRANCH_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + BRANCH_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_BRANCH_FALSE :
                    {
                        Object[] dec = new Object[] {$bci, "branch.false", Arrays.copyOfRange($bc, $bci, $bci + BRANCH_FALSE_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + BRANCH_FALSE_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_THROW :
                    {
                        Object[] dec = new Object[] {$bci, "throw", Arrays.copyOfRange($bc, $bci, $bci + THROW_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + THROW_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + THROW_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_CONSTANT :
                    {
                        Object[] dec = new Object[] {$bci, "load.constant", Arrays.copyOfRange($bc, $bci, $bci + LOAD_CONSTANT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_CONSTANT_OFFSET) + 0]}}};
                        $bci = $bci + LOAD_CONSTANT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_ARGUMENT :
                    {
                        Object[] dec = new Object[] {$bci, "load.argument", Arrays.copyOfRange($bc, $bci, $bci + LOAD_ARGUMENT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + LOAD_ARGUMENT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + LOAD_ARGUMENT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL :
                    {
                        Object[] dec = new Object[] {$bci, "load.local", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL_BOXED :
                    {
                        Object[] dec = new Object[] {$bci, "load.local.boxed", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_BOXED_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_BOXED_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_STORE_LOCAL :
                    {
                        Object[] dec = new Object[] {$bci, "store.local", Arrays.copyOfRange($bc, $bci, $bci + STORE_LOCAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + STORE_LOCAL_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + STORE_LOCAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_RETURN :
                    {
                        Object[] dec = new Object[] {$bci, "return", Arrays.copyOfRange($bc, $bci, $bci + RETURN_LENGTH), new Object[] {}};
                        $bci = $bci + RETURN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL_MAT :
                    {
                        Object[] dec = new Object[] {$bci, "load.local.mat", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_MAT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_MAT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_MAT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_STORE_LOCAL_MAT :
                    {
                        Object[] dec = new Object[] {$bci, "store.local.mat", Arrays.copyOfRange($bc, $bci, $bci + STORE_LOCAL_MAT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + STORE_LOCAL_MAT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + STORE_LOCAL_MAT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_ADD :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLAdd", Arrays.copyOfRange($bc, $bci, $bci + C_SL_ADD_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_ADD_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_DIV :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLDiv", Arrays.copyOfRange($bc, $bci, $bci + C_SL_DIV_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_DIV_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_DIV_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_EQUAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLEqual", Arrays.copyOfRange($bc, $bci, $bci + C_SL_EQUAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_EQUAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LESS_OR_EQUAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLessOrEqual", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LESS_OR_EQUAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LESS_THAN :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLessThan", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LESS_THAN_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_LESS_THAN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LOGICAL_NOT :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLogicalNot", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LOGICAL_NOT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_MUL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLMul", Arrays.copyOfRange($bc, $bci, $bci + C_SL_MUL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_MUL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_MUL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_READ_PROPERTY :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLReadProperty", Arrays.copyOfRange($bc, $bci, $bci + C_SL_READ_PROPERTY_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 1]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 2]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_SUB :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLSub", Arrays.copyOfRange($bc, $bci, $bci + C_SL_SUB_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_SUB_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_SUB_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_WRITE_PROPERTY :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLWriteProperty", Arrays.copyOfRange($bc, $bci, $bci + C_SL_WRITE_PROPERTY_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 1]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1) & 0xff)}}};
                        $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_UNBOX :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLUnbox", Arrays.copyOfRange($bc, $bci, $bci + C_SL_UNBOX_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_UNBOX_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_FUNCTION_LITERAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLFunctionLiteral", Arrays.copyOfRange($bc, $bci, $bci + C_SL_FUNCTION_LITERAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_TO_BOOLEAN :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLToBoolean", Arrays.copyOfRange($bc, $bci, $bci + C_SL_TO_BOOLEAN_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_INVOKE :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLInvoke", Arrays.copyOfRange($bc, $bci, $bci + C_SL_INVOKE_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_INVOKE_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.VARIADIC, (int) unsafeFromBytecode($bc, $bci + C_SL_INVOKE_VARIADIC_OFFSET + 0)}}};
                        $bci = $bci + C_SL_INVOKE_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_SC_SL_AND :
                    {
                        Object[] dec = new Object[] {$bci, "sc.SLAnd", Arrays.copyOfRange($bc, $bci, $bci + SC_SL_AND_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + SC_SL_AND_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + SC_SL_AND_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + SC_SL_AND_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_SC_SL_OR :
                    {
                        Object[] dec = new Object[] {$bci, "sc.SLOr", Arrays.copyOfRange($bc, $bci, $bci + SC_SL_OR_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + SC_SL_OR_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + SC_SL_OR_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + SC_SL_OR_LENGTH;
                        target.add(dec);
                        break;
                    }
                }
            }
            ArrayList<Object[]> ehTarget = new ArrayList<>();
            for (int i = 0; i < $handlers.length; i++) {
                ehTarget.add(new Object[] {$handlers[i].startBci, $handlers[i].endBci, $handlers[i].handlerBci});
            }
            Object[] si = null;
            if (nodes != null && nodes.getSources() != null && sourceInfo != null) {
                ArrayList<Object[]> siTarget = new ArrayList<>();
                for (int i = 0; i < sourceInfo.length; i += 3) {
                    int startBci = sourceInfo[i] & 0xffff;
                    int endBci = i + 3 == sourceInfo.length ? $bc.length : sourceInfo[i + 3] & 0xffff;
                    if (startBci == endBci) {
                        continue;
                    }
                    int sourceIndex = sourceInfo[i] >> 16;
                    int sourceStart = sourceInfo[i + 1];
                    int sourceLength = sourceInfo[i + 2];
                    siTarget.add(new Object[] {startBci, endBci, sourceIndex < 0 || sourceStart < 0 ? null : nodes.getSources()[sourceIndex].createSection(sourceStart, sourceLength)});
                }
                si = siTarget.toArray();
            }
            return OperationIntrospection.Provider.create(new Object[]{0, target.toArray(), ehTarget.toArray(), si});
        }

        private static boolean SLAdd_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            if (((state_0 & 0b100)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                return false;
            }
            return true;
        }

        private static Object SLAdd_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_ADD_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1110) == 0 /* only-active addLong(long, long) */ && ((state_0 & 0b1111) != 0  /* is-not addLong(long, long) && add(SLBigNumber, SLBigNumber) && add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) && typeError(Object, Object, Node, int) */)) {
                return SLAdd_SLAdd_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLAdd_SLAdd_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLAdd_SLAdd_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 addLong(long, long) */;
            try {
                return SLAddNode.addLong($child0Value_, $child1Value_);
            } catch (ArithmeticException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 addLong(long, long) */);
                } finally {
                    lock.unlock();
                }
                return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
            }
        }

        private static Object SLAdd_SLAdd_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 addLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    try {
                        return SLAddNode.addLong($child0Value__, $child1Value__);
                    } catch (ArithmeticException ex) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Lock lock = $this.getLockAccessor();
                        lock.lock();
                        try {
                            $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                            $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 addLong(long, long) */);
                        } finally {
                            lock.unlock();
                        }
                        return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
                    }
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 add(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLAddNode.add($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b1100) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) || typeError(Object, Object, Node, int) */) {
                if ((state_0 & 0b100) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */) {
                    SLAdd_Add1Data s2_ = ((SLAdd_Add1Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_ADD_CHILDREN_OFFSET) + 0) + 0));
                    if (s2_ != null) {
                        if ((SLAddNode.isString($child0Value_, $child1Value_))) {
                            return SLAddNode.add($child0Value_, $child1Value_, s2_.toTruffleStringNodeLeft_, s2_.toTruffleStringNodeRight_, s2_.concatNode_);
                        }
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLAdd_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_)) {
                            return SLAddNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static long SLAdd_executeLong_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_ADD_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectLong(SLAdd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectLong(SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectLong(SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1) != 0 /* is-state_0 addLong(long, long) */) {
                try {
                    return SLAddNode.addLong($child0Value_, $child1Value_);
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 addLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    return SLTypesGen.expectLong(SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectLong(SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLAdd_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_ADD_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_ADD_STATE_BITS_OFFSET + 1);
                if ((exclude) == 0 /* is-not-exclude addLong(long, long) */ && $child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 addLong(long, long) */);
                        try {
                            lock.unlock();
                            hasLock = false;
                            return SLAddNode.addLong($child0Value_, $child1Value_);
                        } catch (ArithmeticException ex) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 addLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            return SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude addLong(long, long) */);
                            state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 addLong(long, long) */);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 add(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLAddNode.add($child0Value_, $child1Value_);
                        }
                    }
                }
                if ((SLAddNode.isString($child0Value, $child1Value))) {
                    SLAdd_Add1Data s2_ = $this.insertAccessor(new SLAdd_Add1Data());
                    s2_.toTruffleStringNodeLeft_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                    s2_.toTruffleStringNodeRight_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                    s2_.concatNode_ = s2_.insertAccessor((ConcatNode.create()));
                    VarHandle.storeStoreFence();
                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_ADD_CHILDREN_OFFSET) + 0) + 0] = s2_;
                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */);
                    lock.unlock();
                    hasLock = false;
                    return SLAddNode.add($child0Value, $child1Value, s2_.toTruffleStringNodeLeft_, s2_.toTruffleStringNodeRight_, s2_.concatNode_);
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLAddNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLDiv_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            return true;
        }

        private static Object SLDiv_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_DIV_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b110) == 0 /* only-active divLong(long, long) */ && ((state_0 & 0b111) != 0  /* is-not divLong(long, long) && div(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                return SLDiv_SLDiv_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLDiv_SLDiv_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLDiv_SLDiv_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 divLong(long, long) */;
            try {
                return SLDivNode.divLong($child0Value_, $child1Value_);
            } catch (ArithmeticException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                    $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 divLong(long, long) */);
                } finally {
                    lock.unlock();
                }
                return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
            }
        }

        private static Object SLDiv_SLDiv_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 divLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    try {
                        return SLDivNode.divLong($child0Value__, $child1Value__);
                    } catch (ArithmeticException ex) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Lock lock = $this.getLockAccessor();
                        lock.lock();
                        try {
                            $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                            $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 divLong(long, long) */);
                        } finally {
                            lock.unlock();
                        }
                        return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
                    }
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 div(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLDivNode.div($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLDiv_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                        return SLDivNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static long SLDiv_executeLong_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_DIV_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectLong(SLDiv_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectLong(SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectLong(SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1) != 0 /* is-state_0 divLong(long, long) */) {
                try {
                    return SLDivNode.divLong($child0Value_, $child1Value_);
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 divLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    return SLTypesGen.expectLong(SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectLong(SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLDiv_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_DIV_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_DIV_STATE_BITS_OFFSET + 1);
                if ((exclude) == 0 /* is-not-exclude divLong(long, long) */ && $child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 divLong(long, long) */);
                        try {
                            lock.unlock();
                            hasLock = false;
                            return SLDivNode.divLong($child0Value_, $child1Value_);
                        } catch (ArithmeticException ex) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 divLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            return SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude divLong(long, long) */);
                            state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 divLong(long, long) */);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 3) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 5) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 div(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLDivNode.div($child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLDivNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLEqual_generic1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    InteropLibrary generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                    InteropLibrary generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value));
                    return SLEqualNode.doGeneric($child0Value, $child1Value, generic1_leftInterop__, generic1_rightInterop__);
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        private static Object SLEqual_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b111111110) == 0 /* only-active doLong(long, long) */ && ((state_0 & 0b111111111) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                return SLEqual_SLEqual_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else if ((state_0 & 0b111111011) == 0 /* only-active doBoolean(boolean, boolean) */ && ((state_0 & 0b111111111) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                return SLEqual_SLEqual_execute__boolean_boolean1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLEqual_SLEqual_execute__generic2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLEqual_SLEqual_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doLong(long, long) */;
            return SLEqualNode.doLong($child0Value_, $child1Value_);
        }

        private static Object SLEqual_SLEqual_execute__boolean_boolean1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            boolean $child1Value_;
            try {
                $child1Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b100) != 0 /* is-state_0 doBoolean(boolean, boolean) */;
            return SLEqualNode.doBoolean($child0Value_, $child1Value_);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLEqual_generic1Boundary0_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    InteropLibrary generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value_));
                    InteropLibrary generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value_));
                    return SLEqualNode.doGeneric($child0Value_, $child1Value_, generic1_leftInterop__, generic1_rightInterop__);
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        @ExplodeLoop
        private static Object SLEqual_SLEqual_execute__generic2_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLEqualNode.doLong($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doBigNumber(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000000000) >>> 11 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000000000) >>> 11 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLEqualNode.doBigNumber($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 doBoolean(boolean, boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                if ($child1Value_ instanceof Boolean) {
                    boolean $child1Value__ = (boolean) $child1Value_;
                    return SLEqualNode.doBoolean($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b1000) != 0 /* is-state_0 doString(String, String) */ && $child0Value_ instanceof String) {
                String $child0Value__ = (String) $child0Value_;
                if ($child1Value_ instanceof String) {
                    String $child1Value__ = (String) $child1Value_;
                    return SLEqualNode.doString($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10000) != 0 /* is-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */ && $child0Value_ instanceof TruffleString) {
                TruffleString $child0Value__ = (TruffleString) $child0Value_;
                if ($child1Value_ instanceof TruffleString) {
                    TruffleString $child1Value__ = (TruffleString) $child1Value_;
                    EqualNode truffleString_equalNode__ = ((EqualNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 0));
                    if (truffleString_equalNode__ != null) {
                        return SLEqualNode.doTruffleString($child0Value__, $child1Value__, truffleString_equalNode__);
                    }
                }
            }
            if ((state_0 & 0b100000) != 0 /* is-state_0 doNull(SLNull, SLNull) */ && SLTypes.isSLNull($child0Value_)) {
                SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                if (SLTypes.isSLNull($child1Value_)) {
                    SLNull $child1Value__ = SLTypes.asSLNull($child1Value_);
                    return SLEqualNode.doNull($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b111000000) != 0 /* is-state_0 doFunction(SLFunction, Object) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                if ((state_0 & 0b1000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */ && $child0Value_ instanceof SLFunction) {
                    SLFunction $child0Value__ = (SLFunction) $child0Value_;
                    return SLEqualNode.doFunction($child0Value__, $child1Value_);
                }
                if ((state_0 & 0b110000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                    if ((state_0 & 0b10000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1));
                        while (s7_ != null) {
                            if ((s7_.leftInterop_.accepts($child0Value_)) && (s7_.rightInterop_.accepts($child1Value_))) {
                                return SLEqualNode.doGeneric($child0Value_, $child1Value_, s7_.leftInterop_, s7_.rightInterop_);
                            }
                            s7_ = s7_.next_;
                        }
                    }
                    if ((state_0 & 0b100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        try {
                            return SLEqual_generic1Boundary0_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_);
                        } catch (BoundaryCallFailedException ex) {
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static boolean SLEqual_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b111111110) == 0 /* only-active doLong(long, long) */ && ((state_0 & 0b111111111) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                return SLEqual_SLEqual_executeBoolean__long_long3_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else if ((state_0 & 0b111111011) == 0 /* only-active doBoolean(boolean, boolean) */ && ((state_0 & 0b111111111) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                return SLEqual_SLEqual_executeBoolean__boolean_boolean4_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLEqual_SLEqual_executeBoolean__generic5_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLEqual_SLEqual_executeBoolean__long_long3_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doLong(long, long) */;
            return SLEqualNode.doLong($child0Value_, $child1Value_);
        }

        private static boolean SLEqual_SLEqual_executeBoolean__boolean_boolean4_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            boolean $child1Value_;
            try {
                $child1Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b100) != 0 /* is-state_0 doBoolean(boolean, boolean) */;
            return SLEqualNode.doBoolean($child0Value_, $child1Value_);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static boolean SLEqual_generic1Boundary1_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    InteropLibrary generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value_));
                    InteropLibrary generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value_));
                    return SLEqualNode.doGeneric($child0Value_, $child1Value_, generic1_leftInterop__, generic1_rightInterop__);
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        @ExplodeLoop
        private static boolean SLEqual_SLEqual_executeBoolean__generic5_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLEqualNode.doLong($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doBigNumber(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000000000) >>> 11 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000000000) >>> 11 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLEqualNode.doBigNumber($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 doBoolean(boolean, boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                if ($child1Value_ instanceof Boolean) {
                    boolean $child1Value__ = (boolean) $child1Value_;
                    return SLEqualNode.doBoolean($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b1000) != 0 /* is-state_0 doString(String, String) */ && $child0Value_ instanceof String) {
                String $child0Value__ = (String) $child0Value_;
                if ($child1Value_ instanceof String) {
                    String $child1Value__ = (String) $child1Value_;
                    return SLEqualNode.doString($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10000) != 0 /* is-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */ && $child0Value_ instanceof TruffleString) {
                TruffleString $child0Value__ = (TruffleString) $child0Value_;
                if ($child1Value_ instanceof TruffleString) {
                    TruffleString $child1Value__ = (TruffleString) $child1Value_;
                    EqualNode truffleString_equalNode__ = ((EqualNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 0));
                    if (truffleString_equalNode__ != null) {
                        return SLEqualNode.doTruffleString($child0Value__, $child1Value__, truffleString_equalNode__);
                    }
                }
            }
            if ((state_0 & 0b100000) != 0 /* is-state_0 doNull(SLNull, SLNull) */ && SLTypes.isSLNull($child0Value_)) {
                SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                if (SLTypes.isSLNull($child1Value_)) {
                    SLNull $child1Value__ = SLTypes.asSLNull($child1Value_);
                    return SLEqualNode.doNull($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b111000000) != 0 /* is-state_0 doFunction(SLFunction, Object) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                if ((state_0 & 0b1000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */ && $child0Value_ instanceof SLFunction) {
                    SLFunction $child0Value__ = (SLFunction) $child0Value_;
                    return SLEqualNode.doFunction($child0Value__, $child1Value_);
                }
                if ((state_0 & 0b110000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                    if ((state_0 & 0b10000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1));
                        while (s7_ != null) {
                            if ((s7_.leftInterop_.accepts($child0Value_)) && (s7_.rightInterop_.accepts($child1Value_))) {
                                return SLEqualNode.doGeneric($child0Value_, $child1Value_, s7_.leftInterop_, s7_.rightInterop_);
                            }
                            s7_ = s7_.next_;
                        }
                    }
                    if ((state_0 & 0b100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        try {
                            return SLEqual_generic1Boundary1_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_);
                        } catch (BoundaryCallFailedException ex) {
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static boolean SLEqual_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_STATE_BITS_OFFSET + 1);
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doLong(long, long) */);
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doLong($child0Value_, $child1Value_);
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 9) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 11) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doBigNumber(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLEqualNode.doBigNumber($child0Value_, $child1Value_);
                        }
                    }
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    if ($child1Value instanceof Boolean) {
                        boolean $child1Value_ = (boolean) $child1Value;
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 doBoolean(boolean, boolean) */);
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doBoolean($child0Value_, $child1Value_);
                    }
                }
                if ($child0Value instanceof String) {
                    String $child0Value_ = (String) $child0Value;
                    if ($child1Value instanceof String) {
                        String $child1Value_ = (String) $child1Value;
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 doString(String, String) */);
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doString($child0Value_, $child1Value_);
                    }
                }
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    if ($child1Value instanceof TruffleString) {
                        TruffleString $child1Value_ = (TruffleString) $child1Value;
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 0] = $this.insertAccessor((EqualNode.create()));
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */);
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doTruffleString($child0Value_, $child1Value_, ((EqualNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 0)));
                    }
                }
                if (SLTypes.isSLNull($child0Value)) {
                    SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                    if (SLTypes.isSLNull($child1Value)) {
                        SLNull $child1Value_ = SLTypes.asSLNull($child1Value);
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 doNull(SLNull, SLNull) */);
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doNull($child0Value_, $child1Value_);
                    }
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 doFunction(SLFunction, Object) */);
                    lock.unlock();
                    hasLock = false;
                    return SLEqualNode.doFunction($child0Value_, $child1Value);
                }
                if ((exclude) == 0 /* is-not-exclude doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                    int count7_ = 0;
                    SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1));
                    if ((state_0 & 0b10000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        while (s7_ != null) {
                            if ((s7_.leftInterop_.accepts($child0Value)) && (s7_.rightInterop_.accepts($child1Value))) {
                                break;
                            }
                            s7_ = s7_.next_;
                            count7_++;
                        }
                    }
                    if (s7_ == null) {
                        // assert (s7_.leftInterop_.accepts($child0Value));
                        // assert (s7_.rightInterop_.accepts($child1Value));
                        if (count7_ < (4)) {
                            s7_ = $this.insertAccessor(new SLEqual_Generic0Data(((SLEqual_Generic0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 1))));
                            s7_.leftInterop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                            s7_.rightInterop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                            VarHandle.storeStoreFence();
                            $children[childArrayOffset_ + 1] = s7_;
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000000 /* add-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                        }
                    }
                    if (s7_ != null) {
                        lock.unlock();
                        hasLock = false;
                        return SLEqualNode.doGeneric($child0Value, $child1Value, s7_.leftInterop_, s7_.rightInterop_);
                    }
                }
                {
                    InteropLibrary generic1_rightInterop__ = null;
                    InteropLibrary generic1_leftInterop__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                            generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value));
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                            $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1] = null;
                            state_0 = (short) (state_0 & 0xffffff7f /* remove-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000000 /* add-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                            lock.unlock();
                            hasLock = false;
                            return SLEqualNode.doGeneric($child0Value, $child1Value, generic1_leftInterop__, generic1_rightInterop__);
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLLessOrEqual_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            return true;
        }

        private static Object SLLessOrEqual_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b110) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b111) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                return SLLessOrEqual_SLLessOrEqual_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLLessOrEqual_SLLessOrEqual_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLLessOrEqual_SLLessOrEqual_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 lessOrEqual(long, long) */;
            return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
        }

        private static Object SLLessOrEqual_SLLessOrEqual_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 lessOrEqual(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLLessOrEqual_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                        return SLLessOrEqualNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static boolean SLLessOrEqual_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectBoolean(SLLessOrEqual_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b11) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) */)) {
                return SLLessOrEqual_SLLessOrEqual_executeBoolean__long_long2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLLessOrEqual_SLLessOrEqual_executeBoolean__generic3_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLLessOrEqual_SLLessOrEqual_executeBoolean__long_long2_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) throws UnexpectedResultException {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectBoolean(SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectBoolean(SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 lessOrEqual(long, long) */;
            return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
        }

        private static boolean SLLessOrEqual_SLLessOrEqual_executeBoolean__generic3_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) throws UnexpectedResultException {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 lessOrEqual(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectBoolean(SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLLessOrEqual_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 lessOrEqual(long, long) */);
                        lock.unlock();
                        hasLock = false;
                        return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 3) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 5) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLLessOrEqualNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLLessThan_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            return true;
        }

        private static Object SLLessThan_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b110) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b111) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                return SLLessThan_SLLessThan_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLLessThan_SLLessThan_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLLessThan_SLLessThan_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 lessThan(long, long) */;
            return SLLessThanNode.lessThan($child0Value_, $child1Value_);
        }

        private static Object SLLessThan_SLLessThan_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 lessThan(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLLessThanNode.lessThan($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lessThan(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLLessThanNode.lessThan($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLLessThan_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                        return SLLessThanNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static boolean SLLessThan_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectBoolean(SLLessThan_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b11) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) */)) {
                return SLLessThan_SLLessThan_executeBoolean__long_long2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLLessThan_SLLessThan_executeBoolean__generic3_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLLessThan_SLLessThan_executeBoolean__long_long2_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) throws UnexpectedResultException {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectBoolean(SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectBoolean(SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 lessThan(long, long) */;
            return SLLessThanNode.lessThan($child0Value_, $child1Value_);
        }

        private static boolean SLLessThan_SLLessThan_executeBoolean__generic3_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) throws UnexpectedResultException {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 lessThan(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    return SLLessThanNode.lessThan($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 lessThan(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLLessThanNode.lessThan($child0Value__, $child1Value__);
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectBoolean(SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLLessThan_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 lessThan(long, long) */);
                        lock.unlock();
                        hasLock = false;
                        return SLLessThanNode.lessThan($child0Value_, $child1Value_);
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 3) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 5) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 lessThan(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLLessThanNode.lessThan($child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLLessThanNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLLogicalNot_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
            if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                return false;
            }
            return true;
        }

        private static Object SLLogicalNot_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && typeError(Object, Node, int) */)) {
                return SLLogicalNot_SLLogicalNot_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLLogicalNot_SLLogicalNot_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLLogicalNot_SLLogicalNot_execute__boolean0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
            return SLLogicalNotNode.doBoolean($child0Value_);
        }

        private static Object SLLogicalNot_SLLogicalNot_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLLogicalNotNode.doBoolean($child0Value__);
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 typeError(Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLLogicalNot_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                        return SLLogicalNotNode.typeError($child0Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLLogicalNot_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b10) != 0 /* is-state_0 typeError(Object, Node, int) */) {
                return SLTypesGen.expectBoolean(SLLogicalNot_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectBoolean(SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */) {
                return SLLogicalNotNode.doBoolean($child0Value_);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectBoolean(SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_));
        }

        private static Object SLLogicalNot_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                    lock.unlock();
                    hasLock = false;
                    return SLLogicalNotNode.doBoolean($child0Value_);
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 typeError(Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLLogicalNotNode.typeError($child0Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLMul_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            return true;
        }

        private static Object SLMul_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_MUL_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b110) == 0 /* only-active mulLong(long, long) */ && ((state_0 & 0b111) != 0  /* is-not mulLong(long, long) && mul(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                return SLMul_SLMul_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLMul_SLMul_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLMul_SLMul_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 mulLong(long, long) */;
            try {
                return SLMulNode.mulLong($child0Value_, $child1Value_);
            } catch (ArithmeticException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                    $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 mulLong(long, long) */);
                } finally {
                    lock.unlock();
                }
                return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
            }
        }

        private static Object SLMul_SLMul_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 mulLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    try {
                        return SLMulNode.mulLong($child0Value__, $child1Value__);
                    } catch (ArithmeticException ex) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Lock lock = $this.getLockAccessor();
                        lock.lock();
                        try {
                            $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                            $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 mulLong(long, long) */);
                        } finally {
                            lock.unlock();
                        }
                        return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
                    }
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 mul(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLMulNode.mul($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLMul_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                        return SLMulNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static long SLMul_executeLong_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_MUL_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectLong(SLMul_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectLong(SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectLong(SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1) != 0 /* is-state_0 mulLong(long, long) */) {
                try {
                    return SLMulNode.mulLong($child0Value_, $child1Value_);
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 mulLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    return SLTypesGen.expectLong(SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectLong(SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLMul_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_MUL_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_MUL_STATE_BITS_OFFSET + 1);
                if ((exclude) == 0 /* is-not-exclude mulLong(long, long) */ && $child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 mulLong(long, long) */);
                        try {
                            lock.unlock();
                            hasLock = false;
                            return SLMulNode.mulLong($child0Value_, $child1Value_);
                        } catch (ArithmeticException ex) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 mulLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            return SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude mulLong(long, long) */);
                            state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 mulLong(long, long) */);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 3) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 5) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 mul(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLMulNode.mul($child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLMulNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readArray1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readArray1_node__ = ($this);
                int readArray1_bci__ = ($bci);
                InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                InteropLibrary readArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                return SLReadPropertyNode.readArray($child0Value, $child1Value, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__);
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readSLObject1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value_, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readSLObject1_node__ = ($this);
                int readSLObject1_bci__ = ($bci);
                DynamicObjectLibrary readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                SLToTruffleStringNode readSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 7));
                if (readSLObject1_toTruffleStringNode__ != null) {
                    return SLReadPropertyNode.readSLObject($child0Value_, $child1Value, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, readSLObject1_toTruffleStringNode__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readObject1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readObject1_node__ = ($this);
                int readObject1_bci__ = ($bci);
                InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                SLToMemberNode readObject1_asMember__ = ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 11));
                if (readObject1_asMember__ != null) {
                    return SLReadPropertyNode.readObject($child0Value, $child1Value, readObject1_node__, readObject1_bci__, readObject1_objects__, readObject1_asMember__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        @ExplodeLoop
        private static Object SLReadProperty_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if (state_0 != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                if ((state_0 & 0b11) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                    if ((state_0 & 0b1) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 0));
                        while (s0_ != null) {
                            if ((s0_.arrays_.accepts($child0Value_)) && (s0_.numbers_.accepts($child1Value_)) && (s0_.arrays_.hasArrayElements($child0Value_))) {
                                Node node__ = ($this);
                                int bci__ = ($bci);
                                return SLReadPropertyNode.readArray($child0Value_, $child1Value_, node__, bci__, s0_.arrays_, s0_.numbers_);
                            }
                            s0_ = s0_.next_;
                        }
                    }
                    if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            {
                                InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                if ((readArray1_arrays__.hasArrayElements($child0Value_))) {
                                    try {
                                        return SLReadProperty_readArray1Boundary0_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_);
                                    } catch (BoundaryCallFailedException ex) {
                                    }
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                if ((state_0 & 0b1100) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */ && $child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    if ((state_0 & 0b100) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                        SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 4));
                        while (s2_ != null) {
                            if ((s2_.objectLibrary_.accepts($child0Value__))) {
                                Node node__1 = ($this);
                                int bci__1 = ($bci);
                                return SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, node__1, bci__1, s2_.objectLibrary_, s2_.toTruffleStringNode_);
                            }
                            s2_ = s2_.next_;
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                        try {
                            return SLReadProperty_readSLObject1Boundary1_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value__, $child1Value_);
                        } catch (BoundaryCallFailedException ex) {
                        }
                    }
                }
                if ((state_0 & 0b110000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b10000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 8));
                        while (s4_ != null) {
                            if ((s4_.objects_.accepts($child0Value_)) && (!(SLReadPropertyNode.isSLObject($child0Value_))) && (s4_.objects_.hasMembers($child0Value_))) {
                                Node node__2 = ($this);
                                int bci__2 = ($bci);
                                return SLReadPropertyNode.readObject($child0Value_, $child1Value_, node__2, bci__2, s4_.objects_, s4_.asMember_);
                            }
                            s4_ = s4_.next_;
                        }
                    }
                    if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            if ((!(SLReadPropertyNode.isSLObject($child0Value_)))) {
                                InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                                if ((readObject1_objects__.hasMembers($child0Value_))) {
                                    try {
                                        return SLReadProperty_readObject1Boundary2_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_);
                                    } catch (BoundaryCallFailedException ex) {
                                    }
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLReadProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readArray1Boundary0_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readArray1_node__ = ($this);
                int readArray1_bci__ = ($bci);
                InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                InteropLibrary readArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                return SLReadPropertyNode.readArray($child0Value_, $child1Value_, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__);
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readSLObject1Boundary1_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value__, Object $child1Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readSLObject1_node__ = ($this);
                int readSLObject1_bci__ = ($bci);
                DynamicObjectLibrary readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value__));
                SLToTruffleStringNode readSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 7));
                if (readSLObject1_toTruffleStringNode__ != null) {
                    return SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, readSLObject1_toTruffleStringNode__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLReadProperty_readObject1Boundary2_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node readObject1_node__ = ($this);
                int readObject1_bci__ = ($bci);
                InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                SLToMemberNode readObject1_asMember__ = ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 11));
                if (readObject1_asMember__ != null) {
                    return SLReadPropertyNode.readObject($child0Value_, $child1Value_, readObject1_node__, readObject1_bci__, readObject1_objects__, readObject1_asMember__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        private static Object SLReadProperty_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 1);
                {
                    int bci__ = 0;
                    Node node__ = null;
                    if (((exclude & 0b1)) == 0 /* is-not-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        int count0_ = 0;
                        SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 0));
                        if ((state_0 & 0b1) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            while (s0_ != null) {
                                if ((s0_.arrays_.accepts($child0Value)) && (s0_.numbers_.accepts($child1Value)) && (s0_.arrays_.hasArrayElements($child0Value))) {
                                    node__ = ($this);
                                    bci__ = ($bci);
                                    break;
                                }
                                s0_ = s0_.next_;
                                count0_++;
                            }
                        }
                        if (s0_ == null) {
                            {
                                InteropLibrary arrays__ = $this.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                // assert (s0_.arrays_.accepts($child0Value));
                                // assert (s0_.numbers_.accepts($child1Value));
                                if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                    s0_ = $this.insertAccessor(new SLReadProperty_ReadArray0Data(((SLReadProperty_ReadArray0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 0))));
                                    node__ = ($this);
                                    bci__ = ($bci);
                                    s0_.arrays_ = s0_.insertAccessor(arrays__);
                                    s0_.numbers_ = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 0] = s0_;
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                }
                            }
                        }
                        if (s0_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLReadPropertyNode.readArray($child0Value, $child1Value, node__, bci__, s0_.arrays_, s0_.numbers_);
                        }
                    }
                }
                {
                    InteropLibrary readArray1_numbers__ = null;
                    InteropLibrary readArray1_arrays__ = null;
                    int readArray1_bci__ = 0;
                    Node readArray1_node__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            {
                                readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                if ((readArray1_arrays__.hasArrayElements($child0Value))) {
                                    readArray1_node__ = ($this);
                                    readArray1_bci__ = ($bci);
                                    readArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 0] = null;
                                    state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    lock.unlock();
                                    hasLock = false;
                                    return SLReadPropertyNode.readArray($child0Value, $child1Value, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__);
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    {
                        int bci__1 = 0;
                        Node node__1 = null;
                        if (((exclude & 0b10)) == 0 /* is-not-exclude readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            int count2_ = 0;
                            SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 4));
                            if ((state_0 & 0b100) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                while (s2_ != null) {
                                    if ((s2_.objectLibrary_.accepts($child0Value_))) {
                                        node__1 = ($this);
                                        bci__1 = ($bci);
                                        break;
                                    }
                                    s2_ = s2_.next_;
                                    count2_++;
                                }
                            }
                            if (s2_ == null) {
                                // assert (s2_.objectLibrary_.accepts($child0Value_));
                                if (count2_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                    s2_ = $this.insertAccessor(new SLReadProperty_ReadSLObject0Data(((SLReadProperty_ReadSLObject0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 4))));
                                    node__1 = ($this);
                                    bci__1 = ($bci);
                                    s2_.objectLibrary_ = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                    s2_.toTruffleStringNode_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 4] = s2_;
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                }
                            }
                            if (s2_ != null) {
                                lock.unlock();
                                hasLock = false;
                                return SLReadPropertyNode.readSLObject($child0Value_, $child1Value, node__1, bci__1, s2_.objectLibrary_, s2_.toTruffleStringNode_);
                            }
                        }
                    }
                    {
                        DynamicObjectLibrary readSLObject1_objectLibrary__ = null;
                        int readSLObject1_bci__ = 0;
                        Node readSLObject1_node__ = null;
                        readSLObject1_node__ = ($this);
                        readSLObject1_bci__ = ($bci);
                        readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 7] = $this.insertAccessor((SLToTruffleStringNodeGen.create()));
                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b10 /* add-exclude readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        $children[childArrayOffset_ + 4] = null;
                        state_0 = (short) (state_0 & 0xfffffffb /* remove-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        lock.unlock();
                        hasLock = false;
                        return SLReadPropertyNode.readSLObject($child0Value_, $child1Value, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 7)));
                    }
                }
                {
                    int bci__2 = 0;
                    Node node__2 = null;
                    if (((exclude & 0b100)) == 0 /* is-not-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        int count4_ = 0;
                        SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 8));
                        if ((state_0 & 0b10000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            while (s4_ != null) {
                                if ((s4_.objects_.accepts($child0Value)) && (!(SLReadPropertyNode.isSLObject($child0Value))) && (s4_.objects_.hasMembers($child0Value))) {
                                    node__2 = ($this);
                                    bci__2 = ($bci);
                                    break;
                                }
                                s4_ = s4_.next_;
                                count4_++;
                            }
                        }
                        if (s4_ == null) {
                            if ((!(SLReadPropertyNode.isSLObject($child0Value)))) {
                                // assert (s4_.objects_.accepts($child0Value));
                                InteropLibrary objects__ = $this.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                if ((objects__.hasMembers($child0Value)) && count4_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                    s4_ = $this.insertAccessor(new SLReadProperty_ReadObject0Data(((SLReadProperty_ReadObject0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 8))));
                                    node__2 = ($this);
                                    bci__2 = ($bci);
                                    s4_.objects_ = s4_.insertAccessor(objects__);
                                    s4_.asMember_ = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 8] = s4_;
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                }
                            }
                        }
                        if (s4_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLReadPropertyNode.readObject($child0Value, $child1Value, node__2, bci__2, s4_.objects_, s4_.asMember_);
                        }
                    }
                }
                {
                    InteropLibrary readObject1_objects__ = null;
                    int readObject1_bci__ = 0;
                    Node readObject1_node__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            if ((!(SLReadPropertyNode.isSLObject($child0Value)))) {
                                readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                                if ((readObject1_objects__.hasMembers($child0Value))) {
                                    readObject1_node__ = ($this);
                                    readObject1_bci__ = ($bci);
                                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 11] = $this.insertAccessor((SLToMemberNodeGen.create()));
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b100 /* add-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $children[childArrayOffset_ + 8] = null;
                                    state_0 = (short) (state_0 & 0xffffffef /* remove-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    lock.unlock();
                                    hasLock = false;
                                    return SLReadPropertyNode.readObject($child0Value, $child1Value, readObject1_node__, readObject1_bci__, readObject1_objects__, ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 11)));
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null}, $child0Value, $child1Value);
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLSub_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                return false;
            }
            return true;
        }

        private static Object SLSub_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_SUB_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b110) == 0 /* only-active subLong(long, long) */ && ((state_0 & 0b111) != 0  /* is-not subLong(long, long) && sub(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                return SLSub_SLSub_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLSub_SLSub_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLSub_SLSub_execute__long_long0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 subLong(long, long) */;
            try {
                return SLSubNode.subLong($child0Value_, $child1Value_);
            } catch (ArithmeticException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                    $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 subLong(long, long) */);
                } finally {
                    lock.unlock();
                }
                return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
            }
        }

        private static Object SLSub_SLSub_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 subLong(long, long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                if ($child1Value_ instanceof Long) {
                    long $child1Value__ = (long) $child1Value_;
                    try {
                        return SLSubNode.subLong($child0Value__, $child1Value__);
                    } catch (ArithmeticException ex) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Lock lock = $this.getLockAccessor();
                        lock.lock();
                        try {
                            $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                            $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 subLong(long, long) */);
                        } finally {
                            lock.unlock();
                        }
                        return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
                    }
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 sub(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000) >>> 3 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                    SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                    return SLSubNode.sub($child0Value__, $child1Value__);
                }
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLSub_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                        return SLSubNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
        }

        private static long SLSub_executeLong_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_SUB_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                return SLTypesGen.expectLong(SLSub_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 2, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Object $child1Value = expectObject($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
                return SLTypesGen.expectLong(SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value));
            }
            long $child1Value_;
            try {
                $child1Value_ = expectLong($frame, $sp - 1, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectLong(SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1) != 0 /* is-state_0 subLong(long, long) */) {
                try {
                    return SLSubNode.subLong($child0Value_, $child1Value_);
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 subLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    return SLTypesGen.expectLong(SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectLong(SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_));
        }

        private static Object SLSub_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_SUB_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_SUB_STATE_BITS_OFFSET + 1);
                if ((exclude) == 0 /* is-not-exclude subLong(long, long) */ && $child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 subLong(long, long) */);
                        try {
                            lock.unlock();
                            hasLock = false;
                            return SLSubNode.subLong($child0Value_, $child1Value_);
                        } catch (ArithmeticException ex) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 subLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            return SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        int sLBigNumberCast1;
                        if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                            SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                            $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude subLong(long, long) */);
                            state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 subLong(long, long) */);
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 3) /* set-implicit-state_0 0:SLBigNumber */);
                            state_0 = (short) (state_0 | (sLBigNumberCast1 << 5) /* set-implicit-state_0 1:SLBigNumber */);
                            $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 sub(SLBigNumber, SLBigNumber) */);
                            lock.unlock();
                            hasLock = false;
                            return SLSubNode.sub($child0Value_, $child1Value_);
                        }
                    }
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLSubNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeArray1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value, Object $child2Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node writeArray1_node__ = ($this);
                int writeArray1_bci__ = ($bci);
                InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                InteropLibrary writeArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                return SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, writeArray1_node__, writeArray1_bci__, writeArray1_arrays__, writeArray1_numbers__);
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeSLObject1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value_, Object $child1Value, Object $child2Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                DynamicObjectLibrary writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                SLToTruffleStringNode writeSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 6));
                if (writeSLObject1_toTruffleStringNode__ != null) {
                    return SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, writeSLObject1_objectLibrary__, writeSLObject1_toTruffleStringNode__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeObject1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value, Object $child2Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    Node writeObject1_node__ = ($this);
                    int writeObject1_bci__ = ($bci);
                    InteropLibrary writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                    SLToMemberNode writeObject1_asMember__ = ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 10));
                    if (writeObject1_asMember__ != null) {
                        return SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, writeObject1_asMember__);
                    }
                    throw BoundaryCallFailedException.INSTANCE;
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        @ExplodeLoop
        private static Object SLWriteProperty_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 3, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff));
            Object $child1Value_ = expectObject($frame, $sp - 2, $bc, $bci, ((unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff));
            Object $child2Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1) & 0xff));
            if (state_0 != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                if ((state_0 & 0b11) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                    if ((state_0 & 0b1) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 0));
                        while (s0_ != null) {
                            if ((s0_.arrays_.accepts($child0Value_)) && (s0_.numbers_.accepts($child1Value_)) && (s0_.arrays_.hasArrayElements($child0Value_))) {
                                Node node__ = ($this);
                                int bci__ = ($bci);
                                return SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, node__, bci__, s0_.arrays_, s0_.numbers_);
                            }
                            s0_ = s0_.next_;
                        }
                    }
                    if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            {
                                InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                if ((writeArray1_arrays__.hasArrayElements($child0Value_))) {
                                    try {
                                        return SLWriteProperty_writeArray1Boundary0_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_, $child2Value_);
                                    } catch (BoundaryCallFailedException ex) {
                                    }
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                if ((state_0 & 0b1100) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */ && $child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    if ((state_0 & 0b100) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                        SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 4));
                        while (s2_ != null) {
                            if ((s2_.objectLibrary_.accepts($child0Value__))) {
                                return SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, s2_.objectLibrary_, s2_.toTruffleStringNode_);
                            }
                            s2_ = s2_.next_;
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                        try {
                            return SLWriteProperty_writeSLObject1Boundary1_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value__, $child1Value_, $child2Value_);
                        } catch (BoundaryCallFailedException ex) {
                        }
                    }
                }
                if ((state_0 & 0b110000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b10000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 7));
                        while (s4_ != null) {
                            if ((s4_.objectLibrary_.accepts($child0Value_)) && (!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                                Node node__1 = ($this);
                                int bci__1 = ($bci);
                                return SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, node__1, bci__1, s4_.objectLibrary_, s4_.asMember_);
                            }
                            s4_ = s4_.next_;
                        }
                    }
                    if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                            try {
                                return SLWriteProperty_writeObject1Boundary2_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_, $child2Value_);
                            } catch (BoundaryCallFailedException ex) {
                            }
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLWriteProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_, $child2Value_);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeArray1Boundary0_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                Node writeArray1_node__ = ($this);
                int writeArray1_bci__ = ($bci);
                InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                InteropLibrary writeArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                return SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, writeArray1_node__, writeArray1_bci__, writeArray1_arrays__, writeArray1_numbers__);
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeSLObject1Boundary1_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value__, Object $child1Value_, Object $child2Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            {
                DynamicObjectLibrary writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value__));
                SLToTruffleStringNode writeSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 6));
                if (writeSLObject1_toTruffleStringNode__ != null) {
                    return SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, writeSLObject1_objectLibrary__, writeSLObject1_toTruffleStringNode__);
                }
                throw BoundaryCallFailedException.INSTANCE;
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLWriteProperty_writeObject1Boundary2_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    Node writeObject1_node__ = ($this);
                    int writeObject1_bci__ = ($bci);
                    InteropLibrary writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value_));
                    SLToMemberNode writeObject1_asMember__ = ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 10));
                    if (writeObject1_asMember__ != null) {
                        return SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, writeObject1_asMember__);
                    }
                    throw BoundaryCallFailedException.INSTANCE;
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        private static Object SLWriteProperty_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value, Object $child2Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1);
                {
                    int bci__ = 0;
                    Node node__ = null;
                    if (((exclude & 0b1)) == 0 /* is-not-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        int count0_ = 0;
                        SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 0));
                        if ((state_0 & 0b1) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            while (s0_ != null) {
                                if ((s0_.arrays_.accepts($child0Value)) && (s0_.numbers_.accepts($child1Value)) && (s0_.arrays_.hasArrayElements($child0Value))) {
                                    node__ = ($this);
                                    bci__ = ($bci);
                                    break;
                                }
                                s0_ = s0_.next_;
                                count0_++;
                            }
                        }
                        if (s0_ == null) {
                            {
                                InteropLibrary arrays__ = $this.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                // assert (s0_.arrays_.accepts($child0Value));
                                // assert (s0_.numbers_.accepts($child1Value));
                                if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                    s0_ = $this.insertAccessor(new SLWriteProperty_WriteArray0Data(((SLWriteProperty_WriteArray0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 0))));
                                    node__ = ($this);
                                    bci__ = ($bci);
                                    s0_.arrays_ = s0_.insertAccessor(arrays__);
                                    s0_.numbers_ = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 0] = s0_;
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                }
                            }
                        }
                        if (s0_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, node__, bci__, s0_.arrays_, s0_.numbers_);
                        }
                    }
                }
                {
                    InteropLibrary writeArray1_numbers__ = null;
                    InteropLibrary writeArray1_arrays__ = null;
                    int writeArray1_bci__ = 0;
                    Node writeArray1_node__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            {
                                writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                if ((writeArray1_arrays__.hasArrayElements($child0Value))) {
                                    writeArray1_node__ = ($this);
                                    writeArray1_bci__ = ($bci);
                                    writeArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 0] = null;
                                    state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    lock.unlock();
                                    hasLock = false;
                                    return SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, writeArray1_node__, writeArray1_bci__, writeArray1_arrays__, writeArray1_numbers__);
                                }
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    if (((exclude & 0b10)) == 0 /* is-not-exclude writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                        int count2_ = 0;
                        SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 4));
                        if ((state_0 & 0b100) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            while (s2_ != null) {
                                if ((s2_.objectLibrary_.accepts($child0Value_))) {
                                    break;
                                }
                                s2_ = s2_.next_;
                                count2_++;
                            }
                        }
                        if (s2_ == null) {
                            // assert (s2_.objectLibrary_.accepts($child0Value_));
                            if (count2_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                s2_ = $this.insertAccessor(new SLWriteProperty_WriteSLObject0Data(((SLWriteProperty_WriteSLObject0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 4))));
                                s2_.objectLibrary_ = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                s2_.toTruffleStringNode_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                VarHandle.storeStoreFence();
                                $children[childArrayOffset_ + 4] = s2_;
                                $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            }
                        }
                        if (s2_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, s2_.objectLibrary_, s2_.toTruffleStringNode_);
                        }
                    }
                    {
                        DynamicObjectLibrary writeSLObject1_objectLibrary__ = null;
                        writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 6] = $this.insertAccessor((SLToTruffleStringNodeGen.create()));
                        $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b10 /* add-exclude writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        $children[childArrayOffset_ + 4] = null;
                        state_0 = (short) (state_0 & 0xfffffffb /* remove-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                        lock.unlock();
                        hasLock = false;
                        return SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, writeSLObject1_objectLibrary__, ((SLToTruffleStringNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 6)));
                    }
                }
                {
                    int bci__1 = 0;
                    Node node__1 = null;
                    if (((exclude & 0b100)) == 0 /* is-not-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        int count4_ = 0;
                        SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 7));
                        if ((state_0 & 0b10000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            while (s4_ != null) {
                                if ((s4_.objectLibrary_.accepts($child0Value)) && (!(SLWritePropertyNode.isSLObject($child0Value)))) {
                                    node__1 = ($this);
                                    bci__1 = ($bci);
                                    break;
                                }
                                s4_ = s4_.next_;
                                count4_++;
                            }
                        }
                        if (s4_ == null) {
                            if ((!(SLWritePropertyNode.isSLObject($child0Value))) && count4_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                // assert (s4_.objectLibrary_.accepts($child0Value));
                                s4_ = $this.insertAccessor(new SLWriteProperty_WriteObject0Data(((SLWriteProperty_WriteObject0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 7))));
                                node__1 = ($this);
                                bci__1 = ($bci);
                                s4_.objectLibrary_ = s4_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                s4_.asMember_ = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                VarHandle.storeStoreFence();
                                $children[childArrayOffset_ + 7] = s4_;
                                $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                            }
                        }
                        if (s4_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, node__1, bci__1, s4_.objectLibrary_, s4_.asMember_);
                        }
                    }
                }
                {
                    InteropLibrary writeObject1_objectLibrary__ = null;
                    int writeObject1_bci__ = 0;
                    Node writeObject1_node__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                                writeObject1_node__ = ($this);
                                writeObject1_bci__ = ($bci);
                                writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 10] = $this.insertAccessor((SLToMemberNodeGen.create()));
                                $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b100 /* add-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                $children[childArrayOffset_ + 7] = null;
                                state_0 = (short) (state_0 & 0xffffffef /* remove-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                lock.unlock();
                                hasLock = false;
                                return SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, ((SLToMemberNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 10)));
                            }
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null, null}, $child0Value, $child1Value, $child2Value);
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLUnbox_fromForeign1Boundary_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    InteropLibrary fromForeign1_interop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                    return SLUnboxNode.fromForeign($child0Value, fromForeign1_interop__);
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        private static Object SLUnbox_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b111111011) == 0 /* only-active fromBoolean(boolean) */ && ((state_0 & 0b111111111) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) && fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                return SLUnbox_SLUnbox_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else if ((state_0 & 0b111110111) == 0 /* only-active fromLong(long) */ && ((state_0 & 0b111111111) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) && fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                return SLUnbox_SLUnbox_execute__long1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLUnbox_SLUnbox_execute__generic2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLUnbox_SLUnbox_execute__boolean0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b100) != 0 /* is-state_0 fromBoolean(boolean) */;
            return SLUnboxNode.fromBoolean($child0Value_);
        }

        private static Object SLUnbox_SLUnbox_execute__long1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1000) != 0 /* is-state_0 fromLong(long) */;
            return SLUnboxNode.fromLong($child0Value_);
        }

        @SuppressWarnings("static-method")
        @TruffleBoundary
        private static Object SLUnbox_fromForeign1Boundary0_(SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value_) {
            int childArrayOffset_;
            int constArrayOffset_;
            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
            Node prev_ = encapsulating_.set($this);
            try {
                {
                    InteropLibrary fromForeign1_interop__ = (INTEROP_LIBRARY_.getUncached($child0Value_));
                    return SLUnboxNode.fromForeign($child0Value_, fromForeign1_interop__);
                }
            } finally {
                encapsulating_.set(prev_);
            }
        }

        @ExplodeLoop
        private static Object SLUnbox_SLUnbox_execute__generic2_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 fromString(String, FromJavaStringNode) */ && $child0Value_ instanceof String) {
                String $child0Value__ = (String) $child0Value_;
                FromJavaStringNode fromString_fromJavaStringNode__ = ((FromJavaStringNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 0));
                if (fromString_fromJavaStringNode__ != null) {
                    return SLUnboxNode.fromString($child0Value__, fromString_fromJavaStringNode__);
                }
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 fromTruffleString(TruffleString) */ && $child0Value_ instanceof TruffleString) {
                TruffleString $child0Value__ = (TruffleString) $child0Value_;
                return SLUnboxNode.fromTruffleString($child0Value__);
            }
            if ((state_0 & 0b100) != 0 /* is-state_0 fromBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLUnboxNode.fromBoolean($child0Value__);
            }
            if ((state_0 & 0b1000) != 0 /* is-state_0 fromLong(long) */ && $child0Value_ instanceof Long) {
                long $child0Value__ = (long) $child0Value_;
                return SLUnboxNode.fromLong($child0Value__);
            }
            if ((state_0 & 0b10000) != 0 /* is-state_0 fromBigNumber(SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000000) >>> 9 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                return SLUnboxNode.fromBigNumber($child0Value__);
            }
            if ((state_0 & 0b100000) != 0 /* is-state_0 fromFunction(SLFunction) */ && $child0Value_ instanceof SLFunction) {
                SLFunction $child0Value__ = (SLFunction) $child0Value_;
                return SLUnboxNode.fromFunction($child0Value__);
            }
            if ((state_0 & 0b1000000) != 0 /* is-state_0 fromFunction(SLNull) */ && SLTypes.isSLNull($child0Value_)) {
                SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                return SLUnboxNode.fromFunction($child0Value__);
            }
            if ((state_0 & 0b110000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) || fromForeign(Object, InteropLibrary) */) {
                if ((state_0 & 0b10000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
                    SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 1));
                    while (s7_ != null) {
                        if ((s7_.interop_.accepts($child0Value_))) {
                            return SLUnboxNode.fromForeign($child0Value_, s7_.interop_);
                        }
                        s7_ = s7_.next_;
                    }
                }
                if ((state_0 & 0b100000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
                    try {
                        return SLUnbox_fromForeign1Boundary0_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_);
                    } catch (BoundaryCallFailedException ex) {
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLUnbox_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b110000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) || fromForeign(Object, InteropLibrary) */) {
                return SLTypesGen.expectBoolean(SLUnbox_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectBoolean(SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b100) != 0 /* is-state_0 fromBoolean(boolean) */) {
                return SLUnboxNode.fromBoolean($child0Value_);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectBoolean(SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_));
        }

        private static long SLUnbox_executeLong_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) throws UnexpectedResultException {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0);
            if ((state_0 & 0b110000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) || fromForeign(Object, InteropLibrary) */) {
                return SLTypesGen.expectLong(SLUnbox_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
            }
            long $child0Value_;
            try {
                $child0Value_ = expectLong($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLTypesGen.expectLong(SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult()));
            }
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b1000) != 0 /* is-state_0 fromLong(long) */) {
                return SLUnboxNode.fromLong($child0Value_);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLTypesGen.expectLong(SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_));
        }

        private static Object SLUnbox_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 1);
                if ($child0Value instanceof String) {
                    String $child0Value_ = (String) $child0Value;
                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 0] = $this.insertAccessor((FromJavaStringNode.create()));
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 fromString(String, FromJavaStringNode) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromString($child0Value_, ((FromJavaStringNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 0)));
                }
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 fromTruffleString(TruffleString) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromTruffleString($child0Value_);
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 fromBoolean(boolean) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromBoolean($child0Value_);
                }
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 fromLong(long) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromLong($child0Value_);
                }
                {
                    int sLBigNumberCast0;
                    if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                        SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                        state_0 = (short) (state_0 | (sLBigNumberCast0 << 9) /* set-implicit-state_0 0:SLBigNumber */);
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 fromBigNumber(SLBigNumber) */);
                        lock.unlock();
                        hasLock = false;
                        return SLUnboxNode.fromBigNumber($child0Value_);
                    }
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 fromFunction(SLFunction) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromFunction($child0Value_);
                }
                if (SLTypes.isSLNull($child0Value)) {
                    SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                    $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 fromFunction(SLNull) */);
                    lock.unlock();
                    hasLock = false;
                    return SLUnboxNode.fromFunction($child0Value_);
                }
                if ((exclude) == 0 /* is-not-exclude fromForeign(Object, InteropLibrary) */) {
                    int count7_ = 0;
                    SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 1));
                    if ((state_0 & 0b10000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
                        while (s7_ != null) {
                            if ((s7_.interop_.accepts($child0Value))) {
                                break;
                            }
                            s7_ = s7_.next_;
                            count7_++;
                        }
                    }
                    if (s7_ == null) {
                        // assert (s7_.interop_.accepts($child0Value));
                        if (count7_ < (SLUnboxNode.LIMIT)) {
                            s7_ = $this.insertAccessor(new SLUnbox_FromForeign0Data(((SLUnbox_FromForeign0Data) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 1))));
                            s7_.interop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                            VarHandle.storeStoreFence();
                            $children[childArrayOffset_ + 1] = s7_;
                            $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000000 /* add-state_0 fromForeign(Object, InteropLibrary) */);
                        }
                    }
                    if (s7_ != null) {
                        lock.unlock();
                        hasLock = false;
                        return SLUnboxNode.fromForeign($child0Value, s7_.interop_);
                    }
                }
                {
                    InteropLibrary fromForeign1_interop__ = null;
                    {
                        EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                        Node prev_ = encapsulating_.set($this);
                        try {
                            fromForeign1_interop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                            $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude fromForeign(Object, InteropLibrary) */);
                            $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 1] = null;
                            state_0 = (short) (state_0 & 0xffffff7f /* remove-state_0 fromForeign(Object, InteropLibrary) */);
                            $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000000 /* add-state_0 fromForeign(Object, InteropLibrary) */);
                            lock.unlock();
                            hasLock = false;
                            return SLUnboxNode.fromForeign($child0Value, fromForeign1_interop__);
                        } finally {
                            encapsulating_.set(prev_);
                        }
                    }
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static Object SLFunctionLiteral_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0);
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0) & 0xff));
            int childArrayOffset_;
            int constArrayOffset_;
            if (state_0 != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */ && $child0Value_ instanceof TruffleString) {
                TruffleString $child0Value__ = (TruffleString) $child0Value_;
                {
                    Node node__ = ($this);
                    SLFunction result__ = ((SLFunction) UFA.unsafeObjectArrayRead($consts, (constArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0) + 0));
                    if (result__ != null) {
                        return SLFunctionLiteralNode.perform($child0Value__, result__, node__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLFunctionLiteral_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static SLFunction SLFunctionLiteral_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0);
                {
                    Node node__ = null;
                    if ($child0Value instanceof TruffleString) {
                        TruffleString $child0Value_ = (TruffleString) $child0Value;
                        $consts[(constArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0) + 0] = (SLFunctionLiteralNode.lookupFunctionCached($child0Value_, $this));
                        node__ = ($this);
                        $bc[$bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 perform(TruffleString, SLFunction, Node) */);
                        lock.unlock();
                        hasLock = false;
                        return SLFunctionLiteralNode.perform($child0Value_, ((SLFunction) UFA.unsafeObjectArrayRead($consts, constArrayOffset_ + 0)), node__);
                    }
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null}, $child0Value);
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLToBoolean_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
            if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                return false;
            }
            return true;
        }

        private static Object SLToBoolean_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                return SLToBoolean_SLToBoolean_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLToBoolean_SLToBoolean_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static Object SLToBoolean_SLToBoolean_execute__boolean0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
            return SLToBooleanNode.doBoolean($child0Value_);
        }

        private static Object SLToBoolean_SLToBoolean_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLToBooleanNode.doBoolean($child0Value__);
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLToBoolean_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                        return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLToBoolean_executeBoolean_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                return SLToBoolean_SLToBoolean_executeBoolean__boolean2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLToBoolean_SLToBoolean_executeBoolean__generic3_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLToBoolean_SLToBoolean_executeBoolean__boolean2_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
            return SLToBooleanNode.doBoolean($child0Value_);
        }

        private static boolean SLToBoolean_SLToBoolean_executeBoolean__generic3_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLToBooleanNode.doBoolean($child0Value__);
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLToBoolean_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                        return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLToBoolean_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doBoolean($child0Value_);
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doFallback(Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doFallback($child0Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        @ExplodeLoop
        private static Object SLInvoke_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics) {
            short state_0 = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1 - $numVariadics, $bc, $bci, (unsafeFromBytecode($bc, $bci + C_SL_INVOKE_POP_INDEXED_OFFSET + 0) & 0xff));
            Object[] $variadicChildValue_ = do_loadVariadicArguments($frame, $sp, $numVariadics);
            if (state_0 != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) || doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                if ((state_0 & 0b11) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) */ && $child0Value_ instanceof SLFunction) {
                    SLFunction $child0Value__ = (SLFunction) $child0Value_;
                    if ((state_0 & 0b1) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                        SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0));
                        while (s0_ != null) {
                            if (!Assumption.isValidAssumption((s0_.callTargetStable_))) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                SLInvoke_removeDirect__($frame, $this, $bc, $bci, $sp, $consts, $children, $numVariadics, s0_);
                                return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $numVariadics, $child0Value__, $variadicChildValue_);
                            }
                            if (($child0Value__.getCallTarget() == s0_.cachedTarget_)) {
                                return SLInvoke.doDirect($child0Value__, $variadicChildValue_, s0_.callTargetStable_, s0_.cachedTarget_, s0_.callNode_);
                            }
                            s0_ = s0_.next_;
                        }
                    }
                    if ((state_0 & 0b10) != 0 /* is-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */) {
                        IndirectCallNode indirect_callNode__ = ((IndirectCallNode) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 1));
                        if (indirect_callNode__ != null) {
                            return SLInvoke.doIndirect($child0Value__, $variadicChildValue_, indirect_callNode__);
                        }
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                    {
                        Node interop_node__ = ($this);
                        int interop_bci__ = ($bci);
                        InteropLibrary interop_library__ = ((InteropLibrary) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 2));
                        if (interop_library__ != null) {
                            return SLInvoke.doInterop($child0Value_, $variadicChildValue_, interop_library__, interop_node__, interop_bci__);
                        }
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $numVariadics, $child0Value_, $variadicChildValue_);
        }

        private static Object SLInvoke_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics, Object $child0Value, Object[] $variadicChildValue) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0);
                short exclude = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_STATE_BITS_OFFSET + 1);
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    if ((exclude) == 0 /* is-not-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                        int count0_ = 0;
                        SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0));
                        if ((state_0 & 0b1) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            while (s0_ != null) {
                                if (($child0Value_.getCallTarget() == s0_.cachedTarget_) && Assumption.isValidAssumption((s0_.callTargetStable_))) {
                                    break;
                                }
                                s0_ = s0_.next_;
                                count0_++;
                            }
                        }
                        if (s0_ == null) {
                            {
                                RootCallTarget cachedTarget__ = ($child0Value_.getCallTarget());
                                if (($child0Value_.getCallTarget() == cachedTarget__)) {
                                    Assumption callTargetStable__ = ($child0Value_.getCallTargetStable());
                                    Assumption assumption0 = (callTargetStable__);
                                    if (Assumption.isValidAssumption(assumption0)) {
                                        if (count0_ < (3)) {
                                            s0_ = $this.insertAccessor(new SLInvoke_DirectData(((SLInvoke_DirectData) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 0))));
                                            s0_.callTargetStable_ = callTargetStable__;
                                            s0_.cachedTarget_ = cachedTarget__;
                                            s0_.callNode_ = s0_.insertAccessor((DirectCallNode.create(cachedTarget__)));
                                            VarHandle.storeStoreFence();
                                            $children[childArrayOffset_ + 0] = s0_;
                                            $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                                        }
                                    }
                                }
                            }
                        }
                        if (s0_ != null) {
                            lock.unlock();
                            hasLock = false;
                            return SLInvoke.doDirect($child0Value_, $variadicChildValue, s0_.callTargetStable_, s0_.cachedTarget_, s0_.callNode_);
                        }
                    }
                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 1] = $this.insertAccessor((IndirectCallNode.create()));
                    $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    $children[childArrayOffset_ + 0] = null;
                    state_0 = (short) (state_0 & 0xfffffffe /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */);
                    lock.unlock();
                    hasLock = false;
                    return SLInvoke.doIndirect($child0Value_, $variadicChildValue, ((IndirectCallNode) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 1)));
                }
                {
                    int interop_bci__ = 0;
                    Node interop_node__ = null;
                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 2] = $this.insertAccessor((INTEROP_LIBRARY_.createDispatched(3)));
                    interop_node__ = ($this);
                    interop_bci__ = ($bci);
                    $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLInvoke.doInterop($child0Value, $variadicChildValue, ((InteropLibrary) UFA.unsafeObjectArrayRead($children, childArrayOffset_ + 2)), interop_node__, interop_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static void SLInvoke_removeDirect__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics, Object s0_) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            lock.lock();
            try {
                SLInvoke_DirectData prev = null;
                SLInvoke_DirectData cur = ((SLInvoke_DirectData) UFA.unsafeObjectArrayRead($children, (childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0));
                while (cur != null) {
                    if (cur == s0_) {
                        if (prev == null) {
                            $children[childArrayOffset_ + 0] = $this.insertAccessor(cur.next_);
                        } else {
                            prev.next_ = prev.insertAccessor(cur.next_);
                        }
                        break;
                    }
                    prev = cur;
                    cur = cur.next_;
                }
                if ($children[childArrayOffset_ + 0] == null) {
                    $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] & 0xfffffffe /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                }
            } finally {
                lock.unlock();
            }
        }

        private static boolean SLAnd_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
            if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                return false;
            }
            return true;
        }

        private static boolean SLAnd_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_AND_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                return SLAnd_SLAnd_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLAnd_SLAnd_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLAnd_SLAnd_execute__boolean0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + SC_SL_AND_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLAnd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
            return SLToBooleanNode.doBoolean($child0Value_);
        }

        private static boolean SLAnd_SLAnd_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + SC_SL_AND_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLToBooleanNode.doBoolean($child0Value__);
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLAnd_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                        return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLAnd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLAnd_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_AND_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    $bc[$bci + SC_SL_AND_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doBoolean($child0Value_);
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + SC_SL_AND_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doFallback(Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doFallback($child0Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static boolean SLOr_fallbackGuard__(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
            if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                return false;
            }
            return true;
        }

        private static boolean SLOr_execute_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
            short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_OR_STATE_BITS_OFFSET + 0);
            int childArrayOffset_;
            int constArrayOffset_;
            if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                return SLOr_SLOr_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            } else {
                return SLOr_SLOr_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
            }
        }

        private static boolean SLOr_SLOr_execute__boolean0_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            boolean $child0Value_;
            try {
                $child0Value_ = expectBoolean($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + SC_SL_OR_POP_INDEXED_OFFSET + 0) & 0xff));
            } catch (UnexpectedResultException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLOr_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
            }
            assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
            return SLToBooleanNode.doBoolean($child0Value_);
        }

        private static boolean SLOr_SLOr_execute__generic1_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
            int childArrayOffset_;
            int constArrayOffset_;
            Object $child0Value_ = expectObject($frame, $sp - 1, $bc, $bci, (unsafeFromBytecode($bc, $bci + SC_SL_OR_POP_INDEXED_OFFSET + 0) & 0xff));
            if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                boolean $child0Value__ = (boolean) $child0Value_;
                return SLToBooleanNode.doBoolean($child0Value__);
            }
            if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                {
                    Node fallback_node__ = ($this);
                    int fallback_bci__ = ($bci);
                    if (SLOr_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                        return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                    }
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return SLOr_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
        }

        private static boolean SLOr_executeAndSpecialize_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            Lock lock = $this.getLockAccessor();
            boolean hasLock = true;
            lock.lock();
            try {
                short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_OR_STATE_BITS_OFFSET + 0);
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    $bc[$bci + SC_SL_OR_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doBoolean($child0Value_);
                }
                {
                    int fallback_bci__ = 0;
                    Node fallback_node__ = null;
                    fallback_node__ = ($this);
                    fallback_bci__ = ($bci);
                    $bc[$bci + SC_SL_OR_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doFallback(Object, Node, int) */);
                    lock.unlock();
                    hasLock = false;
                    return SLToBooleanNode.doFallback($child0Value, fallback_node__, fallback_bci__);
                }
            } finally {
                if (hasLock) {
                    lock.unlock();
                }
            }
        }

        private static int instructionGroup_1_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // pop
                //   Simple Pops:
                //     [ 0] value
                //   Pushed Values: 0
                case ((INSTR_POP << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_POP, 0, 0);
                    $sp = $sp - 1;
                    $frame.clear($sp);
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_2_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // load.constant
                //   Constants:
                //     [ 0] constant
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_CONSTANT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_CONSTANT, 0, 0);
                    UFA.unsafeSetObject($frame, $sp, UFA.unsafeObjectArrayRead($consts, unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_CONSTANT_OFFSET) + 0));
                    $sp = $sp + 1;
                    return $sp;
                }
                // load.argument
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_ARGUMENT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_ARGUMENT, 0, 0);
                    UFA.unsafeSetObject($frame, $sp, $frame.getArguments()[unsafeFromBytecode($bc, $bci + LOAD_ARGUMENT_ARGUMENT_OFFSET + 0)]);
                    $sp = $sp + 1;
                    return $sp;
                }
                // load.local
                //   Locals:
                //     [ 0] local
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0);
                    do_loadLocal_OBJECT($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                case ((INSTR_LOAD_LOCAL << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0);
                    do_loadLocal_BOOLEAN($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                case ((INSTR_LOAD_LOCAL << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0);
                    do_loadLocal_LONG($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                // load.local.boxed
                //   Locals:
                //     [ 0] local
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL_BOXED << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_BOXED, 0, 0);
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_LOAD_LOCAL << 3) | 0));
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0);
                    do_loadLocal_OBJECT($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                case ((INSTR_LOAD_LOCAL_BOXED << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_BOXED, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0);
                    do_loadLocalBoxed_BOOLEAN($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                case ((INSTR_LOAD_LOCAL_BOXED << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_BOXED, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0);
                    do_loadLocalBoxed_LONG($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp++;
                    return $sp;
                }
                // load.local.mat
                //   Simple Pops:
                //     [ 0] frame
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL_MAT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_MAT, 0, 0);
                    Frame outerFrame;
                    outerFrame = (Frame) UFA.unsafeGetObject($frame, $sp - 1);
                    UFA.unsafeSetObject($frame, $sp - 1, outerFrame.getObject(unsafeFromBytecode($bc, $bci + LOAD_LOCAL_MAT_ARGUMENT_OFFSET + 0)));
                    return $sp;
                }
                // store.local.mat
                //   Simple Pops:
                //     [ 0] frame
                //     [ 1] value
                //   Pushed Values: 0
                case ((INSTR_STORE_LOCAL_MAT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL_MAT, 0, 0);
                    Frame outerFrame;
                    outerFrame = (Frame) UFA.unsafeGetObject($frame, $sp - 2);
                    outerFrame.setObject(unsafeFromBytecode($bc, $bci + STORE_LOCAL_MAT_ARGUMENT_OFFSET + 0), UFA.unsafeGetObject($frame, $sp - 1));
                    $sp -= 2;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_3_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // store.local
                //   Locals:
                //     [ 0] target
                //   Indexed Pops:
                //     [ 0] value
                //   Split on Boxing Elimination
                //   Pushed Values: 0
                case ((INSTR_STORE_LOCAL << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0);
                    do_storeLocal_OBJECT($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp--;
                    return $sp;
                }
                case ((INSTR_STORE_LOCAL << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0);
                    do_storeLocal_BOOLEAN($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp--;
                    return $sp;
                }
                case ((INSTR_STORE_LOCAL << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0);
                    do_storeLocal_LONG($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp--;
                    return $sp;
                }
                case ((INSTR_STORE_LOCAL << 3) | 7) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0);
                    do_storeLocal_null($this, $frame, $bc, $bci, $sp, $localTags, localIdx);
                    $sp--;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_5_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLEqual
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = equalNode]
                //     [ 1] SpecializationData [id = Generic0]
                //     [ 2] CacheExpression [sourceParameter = leftInterop]
                //     [ 3] CacheExpression [sourceParameter = rightInterop]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Long], SpecializationData [id = BigNumber], SpecializationData [id = Boolean], SpecializationData [id = String], SpecializationData [id = TruffleString], SpecializationData [id = Null], SpecializationData [id = Function], SpecializationData [id = Generic0], SpecializationData [id = Generic1], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = Long], SpecializationData [id = BigNumber], SpecializationData [id = Boolean], SpecializationData [id = String], SpecializationData [id = TruffleString], SpecializationData [id = Null], SpecializationData [id = Function], SpecializationData [id = Generic0], SpecializationData [id = Generic1]]
                case ((INSTR_C_SL_EQUAL << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLEqual_($bc, $bci));
                    SLEqual_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_EQUAL << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLEqual_($bc, $bci));
                    SLEqual_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLessOrEqual
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = LessOrEqual0], SpecializationData [id = LessOrEqual1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_OR_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_OR_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLLessOrEqual_($bc, $bci));
                    SLLessOrEqual_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_OR_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_OR_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLLessOrEqual_($bc, $bci));
                    SLLessOrEqual_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLessThan
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = LessThan0], SpecializationData [id = LessThan1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                case ((INSTR_C_SL_LESS_THAN << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_THAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_THAN, SLOperationRootNodeGen.doGetStateBits_SLLessThan_($bc, $bci));
                    SLLessThan_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_LESS_THAN << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_THAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_THAN, SLOperationRootNodeGen.doGetStateBits_SLLessThan_($bc, $bci));
                    SLLessThan_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLogicalNot
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_LOGICAL_NOT << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LOGICAL_NOT, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LOGICAL_NOT, SLOperationRootNodeGen.doGetStateBits_SLLogicalNot_($bc, $bci));
                    SLLogicalNot_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                case ((INSTR_C_SL_LOGICAL_NOT << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LOGICAL_NOT, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LOGICAL_NOT, SLOperationRootNodeGen.doGetStateBits_SLLogicalNot_($bc, $bci));
                    SLLogicalNot_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLUnbox
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                //     [ 1] SpecializationData [id = FromForeign0]
                //     [ 2] CacheExpression [sourceParameter = interop]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = FromString], SpecializationData [id = FromTruffleString], SpecializationData [id = FromBoolean], SpecializationData [id = FromLong], SpecializationData [id = FromBigNumber], SpecializationData [id = FromFunction0], SpecializationData [id = FromFunction1], SpecializationData [id = FromForeign0], SpecializationData [id = FromForeign1], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = FromString], SpecializationData [id = FromTruffleString], SpecializationData [id = FromBoolean], SpecializationData [id = FromLong], SpecializationData [id = FromBigNumber], SpecializationData [id = FromFunction0], SpecializationData [id = FromFunction1], SpecializationData [id = FromForeign0], SpecializationData [id = FromForeign1]]
                case ((INSTR_C_SL_UNBOX << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_UNBOX, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_UNBOX, SLOperationRootNodeGen.doGetStateBits_SLUnbox_($bc, $bci));
                    SLUnbox_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                case ((INSTR_C_SL_UNBOX << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_UNBOX, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_UNBOX, SLOperationRootNodeGen.doGetStateBits_SLUnbox_($bc, $bci));
                    SLUnbox_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                case ((INSTR_C_SL_UNBOX << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_UNBOX, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_UNBOX, SLOperationRootNodeGen.doGetStateBits_SLUnbox_($bc, $bci));
                    SLUnbox_entryPoint_LONG($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLFunctionLiteral
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = result]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Perform]]
                case ((INSTR_C_SL_FUNCTION_LITERAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_FUNCTION_LITERAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_FUNCTION_LITERAL, SLOperationRootNodeGen.doGetStateBits_SLFunctionLiteral_($bc, $bci));
                    SLFunctionLiteral_entryPoint_($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLToBoolean
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_TO_BOOLEAN << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_TO_BOOLEAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_TO_BOOLEAN, SLOperationRootNodeGen.doGetStateBits_SLToBoolean_($bc, $bci));
                    SLToBoolean_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                case ((INSTR_C_SL_TO_BOOLEAN << 3) | 5 /* BOOLEAN */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_TO_BOOLEAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_TO_BOOLEAN, SLOperationRootNodeGen.doGetStateBits_SLToBoolean_($bc, $bci));
                    SLToBoolean_entryPoint_BOOLEAN($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_6_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLAdd
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = Add1]
                //     [ 1] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = AddLong], SpecializationData [id = Add0], SpecializationData [id = Add1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = AddLong], SpecializationData [id = Add0], SpecializationData [id = Add1], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_ADD << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_ADD, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_ADD, SLOperationRootNodeGen.doGetStateBits_SLAdd_($bc, $bci));
                    SLAdd_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_ADD << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_ADD, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_ADD, SLOperationRootNodeGen.doGetStateBits_SLAdd_($bc, $bci));
                    SLAdd_entryPoint_LONG($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLDiv
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = DivLong], SpecializationData [id = Div], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = DivLong], SpecializationData [id = Div], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_DIV << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_DIV, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_DIV, SLOperationRootNodeGen.doGetStateBits_SLDiv_($bc, $bci));
                    SLDiv_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_DIV << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_DIV, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_DIV, SLOperationRootNodeGen.doGetStateBits_SLDiv_($bc, $bci));
                    SLDiv_entryPoint_LONG($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLMul
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = MulLong], SpecializationData [id = Mul], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = MulLong], SpecializationData [id = Mul], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_MUL << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_MUL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_MUL, SLOperationRootNodeGen.doGetStateBits_SLMul_($bc, $bci));
                    SLMul_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_MUL << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_MUL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_MUL, SLOperationRootNodeGen.doGetStateBits_SLMul_($bc, $bci));
                    SLMul_entryPoint_LONG($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLReadProperty
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //     [ 1] CacheExpression [sourceParameter = bci]
                //     [ 2] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = ReadArray0]
                //     [ 1] CacheExpression [sourceParameter = node]
                //     [ 2] CacheExpression [sourceParameter = arrays]
                //     [ 3] CacheExpression [sourceParameter = numbers]
                //     [ 4] SpecializationData [id = ReadSLObject0]
                //     [ 5] CacheExpression [sourceParameter = node]
                //     [ 6] CacheExpression [sourceParameter = objectLibrary]
                //     [ 7] CacheExpression [sourceParameter = toTruffleStringNode]
                //     [ 8] SpecializationData [id = ReadObject0]
                //     [ 9] CacheExpression [sourceParameter = node]
                //     [10] CacheExpression [sourceParameter = objects]
                //     [11] CacheExpression [sourceParameter = asMember]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = ReadArray0], SpecializationData [id = ReadArray1], SpecializationData [id = ReadSLObject0], SpecializationData [id = ReadSLObject1], SpecializationData [id = ReadObject0], SpecializationData [id = ReadObject1]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = ReadArray0], SpecializationData [id = ReadArray1], SpecializationData [id = ReadSLObject0], SpecializationData [id = ReadSLObject1], SpecializationData [id = ReadObject0], SpecializationData [id = ReadObject1]]
                case ((INSTR_C_SL_READ_PROPERTY << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_READ_PROPERTY, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_READ_PROPERTY, SLOperationRootNodeGen.doGetStateBits_SLReadProperty_($bc, $bci));
                    SLReadProperty_entryPoint_($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLSub
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = SubLong], SpecializationData [id = Sub], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = SubLong], SpecializationData [id = Sub], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_SUB << 3) | 0 /* OBJECT */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_SUB, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_SUB, SLOperationRootNodeGen.doGetStateBits_SLSub_($bc, $bci));
                    SLSub_entryPoint_OBJECT($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                case ((INSTR_C_SL_SUB << 3) | 1 /* LONG */) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_SUB, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_SUB, SLOperationRootNodeGen.doGetStateBits_SLSub_($bc, $bci));
                    SLSub_entryPoint_LONG($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_7_0(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLWriteProperty
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //     [ 1] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = WriteArray0]
                //     [ 1] CacheExpression [sourceParameter = node]
                //     [ 2] CacheExpression [sourceParameter = arrays]
                //     [ 3] CacheExpression [sourceParameter = numbers]
                //     [ 4] SpecializationData [id = WriteSLObject0]
                //     [ 5] CacheExpression [sourceParameter = objectLibrary]
                //     [ 6] CacheExpression [sourceParameter = toTruffleStringNode]
                //     [ 7] SpecializationData [id = WriteObject0]
                //     [ 8] CacheExpression [sourceParameter = node]
                //     [ 9] CacheExpression [sourceParameter = objectLibrary]
                //     [10] CacheExpression [sourceParameter = asMember]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //     [ 2] arg2
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = WriteArray0], SpecializationData [id = WriteArray1], SpecializationData [id = WriteSLObject0], SpecializationData [id = WriteSLObject1], SpecializationData [id = WriteObject0], SpecializationData [id = WriteObject1]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = WriteArray0], SpecializationData [id = WriteArray1], SpecializationData [id = WriteSLObject0], SpecializationData [id = WriteSLObject1], SpecializationData [id = WriteObject0], SpecializationData [id = WriteObject1]]
                case ((INSTR_C_SL_WRITE_PROPERTY << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_WRITE_PROPERTY, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_WRITE_PROPERTY, SLOperationRootNodeGen.doGetStateBits_SLWriteProperty_($bc, $bci));
                    SLWriteProperty_entryPoint_($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -2;
                    return $sp;
                }
                // c.SLInvoke
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = Direct]
                //     [ 1] CacheExpression [sourceParameter = callNode]
                //     [ 2] CacheExpression [sourceParameter = library]
                //     [ 3] CacheExpression [sourceParameter = node]
                //     [ 4] NodeExecutionData[child=NodeFieldData[name=$variadicChild, kind=ONE, node=NodeData[C]], name=$variadicChild, index=1]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Variadic
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Direct], SpecializationData [id = Indirect], SpecializationData [id = Interop]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = Direct], SpecializationData [id = Indirect], SpecializationData [id = Interop]]
                case ((INSTR_C_SL_INVOKE << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_INVOKE, 0, 1);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_INVOKE, SLOperationRootNodeGen.doGetStateBits_SLInvoke_($bc, $bci));
                    int numVariadics = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_VARIADIC_OFFSET + 0);
                    SLInvoke_entryPoint_($frame, $this, $bc, $bci, $sp, $consts, $children, numVariadics);
                    $sp +=  - numVariadics;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static boolean isAdoptable() {
            return true;
        }

        private static final class SLAdd_Add1Data extends Node {

            @Child SLToTruffleStringNode toTruffleStringNodeLeft_;
            @Child SLToTruffleStringNode toTruffleStringNodeRight_;
            @Child ConcatNode concatNode_;

            SLAdd_Add1Data() {
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLEqual_Generic0Data extends Node {

            @Child SLEqual_Generic0Data next_;
            @Child InteropLibrary leftInterop_;
            @Child InteropLibrary rightInterop_;

            SLEqual_Generic0Data(SLEqual_Generic0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadArray0Data extends Node {

            @Child SLReadProperty_ReadArray0Data next_;
            @Child InteropLibrary arrays_;
            @Child InteropLibrary numbers_;

            SLReadProperty_ReadArray0Data(SLReadProperty_ReadArray0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadSLObject0Data extends Node {

            @Child SLReadProperty_ReadSLObject0Data next_;
            @Child DynamicObjectLibrary objectLibrary_;
            @Child SLToTruffleStringNode toTruffleStringNode_;

            SLReadProperty_ReadSLObject0Data(SLReadProperty_ReadSLObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadObject0Data extends Node {

            @Child SLReadProperty_ReadObject0Data next_;
            @Child InteropLibrary objects_;
            @Child SLToMemberNode asMember_;

            SLReadProperty_ReadObject0Data(SLReadProperty_ReadObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteArray0Data extends Node {

            @Child SLWriteProperty_WriteArray0Data next_;
            @Child InteropLibrary arrays_;
            @Child InteropLibrary numbers_;

            SLWriteProperty_WriteArray0Data(SLWriteProperty_WriteArray0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteSLObject0Data extends Node {

            @Child SLWriteProperty_WriteSLObject0Data next_;
            @Child DynamicObjectLibrary objectLibrary_;
            @Child SLToTruffleStringNode toTruffleStringNode_;

            SLWriteProperty_WriteSLObject0Data(SLWriteProperty_WriteSLObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteObject0Data extends Node {

            @Child SLWriteProperty_WriteObject0Data next_;
            @Child InteropLibrary objectLibrary_;
            @Child SLToMemberNode asMember_;

            SLWriteProperty_WriteObject0Data(SLWriteProperty_WriteObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLUnbox_FromForeign0Data extends Node {

            @Child SLUnbox_FromForeign0Data next_;
            @Child InteropLibrary interop_;

            SLUnbox_FromForeign0Data(SLUnbox_FromForeign0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class SLInvoke_DirectData extends Node {

            @Child SLInvoke_DirectData next_;
            @CompilationFinal Assumption callTargetStable_;
            @CompilationFinal RootCallTarget cachedTarget_;
            @Child DirectCallNode callNode_;

            SLInvoke_DirectData(SLInvoke_DirectData next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
    }
    @GeneratedBy(SLOperationRootNode.class)
    private static final class UncachedBytecodeNode extends BytecodeLoopBase {

        private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
        private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);

        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        @BytecodeInterpreterSwitch
        @Override
        int continueAt(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals) {
            int $sp = $startSp;
            int $bci = $startBci;
            $frame.getArguments();
            Counter uncachedExecuteCount = new Counter();
            uncachedExecuteCount.count = $this.uncachedExecuteCount;
            ExecutionTracer tracer = ExecutionTracer.get(SLOperationRootNode.class);
            tracer.startFunction($this);
            try {
                loop: while (true) {
                    CompilerAsserts.partialEvaluationConstant($bci);
                    CompilerAsserts.partialEvaluationConstant($sp);
                    int curOpcode = unsafeFromBytecode($bc, $bci) & 0xffff;
                    CompilerAsserts.partialEvaluationConstant(curOpcode);
                    if ($this.isBbStart[$bci]) {
                        tracer.traceStartBasicBlock($bci);
                    }
                    try {
                        assert $sp >= maxLocals : "stack underflow @ " + $bci;
                        switch (curOpcode) {
                            // branch
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            case ((INSTR_BRANCH << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_BRANCH, 1, 0);
                                int targetBci = unsafeFromBytecode($bc, $bci + BRANCH_BRANCH_TARGET_OFFSET + 0);
                                if (targetBci <= $bci) {
                                    uncachedExecuteCount.count--;
                                    if (uncachedExecuteCount.count <= 0) {
                                        $this.changeInterpreters(COMMON_EXECUTE);
                                        return ($sp << 16) | targetBci;
                                    }
                                }
                                $bci = targetBci;
                                continue loop;
                            }
                            // branch.false
                            //   Simple Pops:
                            //     [ 0] condition
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            //   Branch Profiles:
                            //     [ 0] profile
                            case ((INSTR_BRANCH_FALSE << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_BRANCH_FALSE, 1, 0);
                                boolean cond = UFA.unsafeGetObject($frame, $sp - 1) == Boolean.TRUE;
                                $sp = $sp - 1;
                                if (do_profileCondition($this, cond, $conditionProfiles, unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_PROFILE_OFFSET + 0))) {
                                    $bci = $bci + BRANCH_FALSE_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // throw
                            //   Locals:
                            //     [ 0] exception
                            //   Pushed Values: 0
                            case ((INSTR_THROW << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_THROW, 1, 0);
                                int slot = unsafeFromBytecode($bc, $bci + THROW_LOCALS_OFFSET + 0);
                                throw (AbstractTruffleException) UFA.unsafeUncheckedGetObject($frame, slot);
                            }
                            // return
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            case ((INSTR_RETURN << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_RETURN, 1, 0);
                                uncachedExecuteCount.count--;
                                if (uncachedExecuteCount.count <= 0) {
                                    $this.changeInterpreters(COMMON_EXECUTE);
                                } else {
                                    $this.uncachedExecuteCount = uncachedExecuteCount.count;
                                }
                                return (($sp - 1) << 16) | 0xffff;
                            }
                            // sc.SLAnd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Split on Boxing Elimination
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                            case ((INSTR_SC_SL_AND << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_AND, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_AND, SLOperationRootNodeGen.doGetStateBits_SLAnd_($bc, $bci));
                                if (UncachedBytecodeNode.SLAnd_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1))) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_AND_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // sc.SLOr
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Split on Boxing Elimination
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                            case ((INSTR_SC_SL_OR << 3) | 0) :
                            {
                                tracer.traceInstruction($bci, INSTR_SC_SL_OR, 1, 0);
                                tracer.traceActiveSpecializations($bci, INSTR_SC_SL_OR, SLOperationRootNodeGen.doGetStateBits_SLOr_($bc, $bci));
                                if (!UncachedBytecodeNode.SLOr_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1))) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_OR_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = unsafeFromBytecode($bc, $bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0);
                                    continue loop;
                                }
                            }
                            // length group 1 (1 / 1)
                            case ((INSTR_POP << 3) | 0) :
                                $sp = instructionGroup_1_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 1;
                                continue loop;
                            // length group 2 (1 / 1)
                            case ((INSTR_LOAD_CONSTANT << 3) | 0) :
                            case ((INSTR_LOAD_ARGUMENT << 3) | 0) :
                            case ((INSTR_LOAD_LOCAL << 3) | 0) :
                            case ((INSTR_LOAD_LOCAL_BOXED << 3) | 0) :
                            case ((INSTR_LOAD_LOCAL_MAT << 3) | 0) :
                            case ((INSTR_STORE_LOCAL_MAT << 3) | 0) :
                                $sp = instructionGroup_2_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 2;
                                continue loop;
                            // length group 3 (1 / 1)
                            case ((INSTR_STORE_LOCAL << 3) | 0) :
                                $sp = instructionGroup_3_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 3;
                                continue loop;
                            // length group 5 (1 / 1)
                            case ((INSTR_C_SL_EQUAL << 3) | 0) :
                            case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 0) :
                            case ((INSTR_C_SL_LESS_THAN << 3) | 0) :
                            case ((INSTR_C_SL_LOGICAL_NOT << 3) | 0) :
                            case ((INSTR_C_SL_UNBOX << 3) | 0) :
                            case ((INSTR_C_SL_FUNCTION_LITERAL << 3) | 0) :
                            case ((INSTR_C_SL_TO_BOOLEAN << 3) | 0) :
                                $sp = instructionGroup_5_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 5;
                                continue loop;
                            // length group 6 (1 / 1)
                            case ((INSTR_C_SL_ADD << 3) | 0) :
                            case ((INSTR_C_SL_DIV << 3) | 0) :
                            case ((INSTR_C_SL_MUL << 3) | 0) :
                            case ((INSTR_C_SL_READ_PROPERTY << 3) | 0) :
                            case ((INSTR_C_SL_SUB << 3) | 0) :
                                $sp = instructionGroup_6_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 6;
                                continue loop;
                            // length group 7 (1 / 1)
                            case ((INSTR_C_SL_WRITE_PROPERTY << 3) | 0) :
                            case ((INSTR_C_SL_INVOKE << 3) | 0) :
                                $sp = instructionGroup_7_0_uncached($this, $frame, $bc, $bci, $sp, $consts, $children, $localTags, $conditionProfiles, curOpcode, tracer);
                                $bci = $bci + 7;
                                continue loop;
                            default :
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw CompilerDirectives.shouldNotReachHere("unknown opcode encountered: " + curOpcode + " @ " + $bci + "");
                        }
                    } catch (AbstractTruffleException ex) {
                        CompilerAsserts.partialEvaluationConstant($bci);
                        for (int handlerIndex = $handlers.length - 1; handlerIndex >= 0; handlerIndex--) {
                            CompilerAsserts.partialEvaluationConstant(handlerIndex);
                            ExceptionHandler handler = $handlers[handlerIndex];
                            if (handler.startBci > $bci || handler.endBci <= $bci) continue;
                            $sp = handler.startStack + maxLocals;
                            $frame.setObject(handler.exceptionIndex, ex);
                            $bci = handler.handlerBci;
                            continue loop;
                        }
                        throw ex;
                    }
                }
            } finally {
                tracer.endFunction($this);
            }
        }

        @Override
        void prepareForAOT(SLOperationRootNodeGen $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root) {
            int $bci = 0;
            while ($bci < $bc.length) {
                switch ((unsafeFromBytecode($bc, $bci) >> 3) & 8191) {
                    case INSTR_POP :
                    {
                        $bci = $bci + POP_LENGTH;
                        break;
                    }
                    case INSTR_BRANCH :
                    {
                        $bci = $bci + BRANCH_LENGTH;
                        break;
                    }
                    case INSTR_BRANCH_FALSE :
                    {
                        $bci = $bci + BRANCH_FALSE_LENGTH;
                        break;
                    }
                    case INSTR_THROW :
                    {
                        $bci = $bci + THROW_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_CONSTANT :
                    {
                        $bci = $bci + LOAD_CONSTANT_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_ARGUMENT :
                    {
                        $bci = $bci + LOAD_ARGUMENT_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL :
                    {
                        $bci = $bci + LOAD_LOCAL_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL_BOXED :
                    {
                        $bci = $bci + LOAD_LOCAL_BOXED_LENGTH;
                        break;
                    }
                    case INSTR_STORE_LOCAL :
                    {
                        $bci = $bci + STORE_LOCAL_LENGTH;
                        break;
                    }
                    case INSTR_RETURN :
                    {
                        $bci = $bci + RETURN_LENGTH;
                        break;
                    }
                    case INSTR_LOAD_LOCAL_MAT :
                    {
                        $bci = $bci + LOAD_LOCAL_MAT_LENGTH;
                        break;
                    }
                    case INSTR_STORE_LOCAL_MAT :
                    {
                        $bci = $bci + STORE_LOCAL_MAT_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_ADD :
                    {
                        $bci = $bci + C_SL_ADD_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_DIV :
                    {
                        $bci = $bci + C_SL_DIV_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_EQUAL :
                    {
                        $bci = $bci + C_SL_EQUAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LESS_OR_EQUAL :
                    {
                        $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LESS_THAN :
                    {
                        $bci = $bci + C_SL_LESS_THAN_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_LOGICAL_NOT :
                    {
                        $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_MUL :
                    {
                        $bci = $bci + C_SL_MUL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_READ_PROPERTY :
                    {
                        $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_SUB :
                    {
                        $bci = $bci + C_SL_SUB_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_WRITE_PROPERTY :
                    {
                        $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_UNBOX :
                    {
                        $bci = $bci + C_SL_UNBOX_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_FUNCTION_LITERAL :
                    {
                        $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_TO_BOOLEAN :
                    {
                        $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                        break;
                    }
                    case INSTR_C_SL_INVOKE :
                    {
                        $bci = $bci + C_SL_INVOKE_LENGTH;
                        break;
                    }
                    case INSTR_SC_SL_AND :
                    {
                        $bci = $bci + SC_SL_AND_LENGTH;
                        break;
                    }
                    case INSTR_SC_SL_OR :
                    {
                        $bci = $bci + SC_SL_OR_LENGTH;
                        break;
                    }
                }
            }
        }

        @Override
        OperationIntrospection getIntrospectionData(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts, OperationNodesImpl nodes, int[] sourceInfo) {
            int $bci = 0;
            ArrayList<Object[]> target = new ArrayList<>();
            while ($bci < $bc.length) {
                switch ((unsafeFromBytecode($bc, $bci) >> 3) & 8191) {
                    default :
                    {
                        Object[] dec = new Object[]{$bci, "unknown", Arrays.copyOfRange($bc, $bci, $bci + 1), null};
                        $bci++;
                        target.add(dec);
                        break;
                    }
                    case INSTR_POP :
                    {
                        Object[] dec = new Object[] {$bci, "pop", Arrays.copyOfRange($bc, $bci, $bci + POP_LENGTH), new Object[] {}};
                        $bci = $bci + POP_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_BRANCH :
                    {
                        Object[] dec = new Object[] {$bci, "branch", Arrays.copyOfRange($bc, $bci, $bci + BRANCH_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + BRANCH_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + BRANCH_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_BRANCH_FALSE :
                    {
                        Object[] dec = new Object[] {$bci, "branch.false", Arrays.copyOfRange($bc, $bci, $bci + BRANCH_FALSE_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + BRANCH_FALSE_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_THROW :
                    {
                        Object[] dec = new Object[] {$bci, "throw", Arrays.copyOfRange($bc, $bci, $bci + THROW_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + THROW_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + THROW_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_CONSTANT :
                    {
                        Object[] dec = new Object[] {$bci, "load.constant", Arrays.copyOfRange($bc, $bci, $bci + LOAD_CONSTANT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_CONSTANT_OFFSET) + 0]}}};
                        $bci = $bci + LOAD_CONSTANT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_ARGUMENT :
                    {
                        Object[] dec = new Object[] {$bci, "load.argument", Arrays.copyOfRange($bc, $bci, $bci + LOAD_ARGUMENT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + LOAD_ARGUMENT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + LOAD_ARGUMENT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL :
                    {
                        Object[] dec = new Object[] {$bci, "load.local", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL_BOXED :
                    {
                        Object[] dec = new Object[] {$bci, "load.local.boxed", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_BOXED_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_BOXED_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_STORE_LOCAL :
                    {
                        Object[] dec = new Object[] {$bci, "store.local", Arrays.copyOfRange($bc, $bci, $bci + STORE_LOCAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.LOCAL, (int) unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + STORE_LOCAL_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + STORE_LOCAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_RETURN :
                    {
                        Object[] dec = new Object[] {$bci, "return", Arrays.copyOfRange($bc, $bci, $bci + RETURN_LENGTH), new Object[] {}};
                        $bci = $bci + RETURN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_LOAD_LOCAL_MAT :
                    {
                        Object[] dec = new Object[] {$bci, "load.local.mat", Arrays.copyOfRange($bc, $bci, $bci + LOAD_LOCAL_MAT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + LOAD_LOCAL_MAT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + LOAD_LOCAL_MAT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_STORE_LOCAL_MAT :
                    {
                        Object[] dec = new Object[] {$bci, "store.local.mat", Arrays.copyOfRange($bc, $bci, $bci + STORE_LOCAL_MAT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.ARGUMENT, (int) unsafeFromBytecode($bc, $bci + STORE_LOCAL_MAT_ARGUMENT_OFFSET + 0)}}};
                        $bci = $bci + STORE_LOCAL_MAT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_ADD :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLAdd", Arrays.copyOfRange($bc, $bci, $bci + C_SL_ADD_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_ADD_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_ADD_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_DIV :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLDiv", Arrays.copyOfRange($bc, $bci, $bci + C_SL_DIV_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_DIV_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_DIV_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_DIV_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_EQUAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLEqual", Arrays.copyOfRange($bc, $bci, $bci + C_SL_EQUAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_EQUAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LESS_OR_EQUAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLessOrEqual", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LESS_OR_EQUAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LESS_THAN :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLessThan", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LESS_THAN_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_LESS_THAN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_LOGICAL_NOT :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLLogicalNot", Arrays.copyOfRange($bc, $bci, $bci + C_SL_LOGICAL_NOT_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_MUL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLMul", Arrays.copyOfRange($bc, $bci, $bci + C_SL_MUL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_MUL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_MUL_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_MUL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_READ_PROPERTY :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLReadProperty", Arrays.copyOfRange($bc, $bci, $bci + C_SL_READ_PROPERTY_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 1]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 2]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_SUB :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLSub", Arrays.copyOfRange($bc, $bci, $bci + C_SL_SUB_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_SUB_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_SUB_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)}}};
                        $bci = $bci + C_SL_SUB_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_WRITE_PROPERTY :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLWriteProperty", Arrays.copyOfRange($bc, $bci, $bci + C_SL_WRITE_PROPERTY_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 1]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) ((unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0) >> 8) & 0xff)},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1) & 0xff)}}};
                        $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_UNBOX :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLUnbox", Arrays.copyOfRange($bc, $bci, $bci + C_SL_UNBOX_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_UNBOX_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_FUNCTION_LITERAL :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLFunctionLiteral", Arrays.copyOfRange($bc, $bci, $bci + C_SL_FUNCTION_LITERAL_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_TO_BOOLEAN :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLToBoolean", Arrays.copyOfRange($bc, $bci, $bci + C_SL_TO_BOOLEAN_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0) & 0xff)}}};
                        $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_C_SL_INVOKE :
                    {
                        Object[] dec = new Object[] {$bci, "c.SLInvoke", Arrays.copyOfRange($bc, $bci, $bci + C_SL_INVOKE_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + C_SL_INVOKE_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.VARIADIC, (int) unsafeFromBytecode($bc, $bci + C_SL_INVOKE_VARIADIC_OFFSET + 0)}}};
                        $bci = $bci + C_SL_INVOKE_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_SC_SL_AND :
                    {
                        Object[] dec = new Object[] {$bci, "sc.SLAnd", Arrays.copyOfRange($bc, $bci, $bci + SC_SL_AND_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + SC_SL_AND_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + SC_SL_AND_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + SC_SL_AND_LENGTH;
                        target.add(dec);
                        break;
                    }
                    case INSTR_SC_SL_OR :
                    {
                        Object[] dec = new Object[] {$bci, "sc.SLOr", Arrays.copyOfRange($bc, $bci, $bci + SC_SL_OR_LENGTH), new Object[] {
                            new Object[] {ArgumentKind.CONSTANT, $consts[unsafeFromBytecode($bc, $bci + SC_SL_OR_CONSTANT_OFFSET) + 0]},
                            new Object[] {ArgumentKind.CHILD_OFFSET, (int) (unsafeFromBytecode($bc, $bci + SC_SL_OR_POP_INDEXED_OFFSET + 0) & 0xff)},
                            new Object[] {ArgumentKind.BRANCH_OFFSET, (int) unsafeFromBytecode($bc, $bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0)}}};
                        $bci = $bci + SC_SL_OR_LENGTH;
                        target.add(dec);
                        break;
                    }
                }
            }
            ArrayList<Object[]> ehTarget = new ArrayList<>();
            for (int i = 0; i < $handlers.length; i++) {
                ehTarget.add(new Object[] {$handlers[i].startBci, $handlers[i].endBci, $handlers[i].handlerBci});
            }
            Object[] si = null;
            if (nodes != null && nodes.getSources() != null && sourceInfo != null) {
                ArrayList<Object[]> siTarget = new ArrayList<>();
                for (int i = 0; i < sourceInfo.length; i += 3) {
                    int startBci = sourceInfo[i] & 0xffff;
                    int endBci = i + 3 == sourceInfo.length ? $bc.length : sourceInfo[i + 3] & 0xffff;
                    if (startBci == endBci) {
                        continue;
                    }
                    int sourceIndex = sourceInfo[i] >> 16;
                    int sourceStart = sourceInfo[i + 1];
                    int sourceLength = sourceInfo[i + 2];
                    siTarget.add(new Object[] {startBci, endBci, sourceIndex < 0 || sourceStart < 0 ? null : nodes.getSources()[sourceIndex].createSection(sourceStart, sourceLength)});
                }
                si = siTarget.toArray();
            }
            return OperationIntrospection.Provider.create(new Object[]{0, target.toArray(), ehTarget.toArray(), si});
        }

        private static Object SLAdd_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLAddNode.add($child0Value_, $child1Value_);
                }
            }
            if ((SLAddNode.isString($child0Value, $child1Value))) {
                return SLAddNode.add($child0Value, $child1Value, (SLToTruffleStringNodeGen.getUncached()), (SLToTruffleStringNodeGen.getUncached()), (ConcatNode.getUncached()));
            }
            return SLAddNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLDiv_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLDivNode.div($child0Value_, $child1Value_);
                }
            }
            return SLDivNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLEqual_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Long) {
                long $child0Value_ = (long) $child0Value;
                if ($child1Value instanceof Long) {
                    long $child1Value_ = (long) $child1Value;
                    return SLEqualNode.doLong($child0Value_, $child1Value_);
                }
            }
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLEqualNode.doBigNumber($child0Value_, $child1Value_);
                }
            }
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                if ($child1Value instanceof Boolean) {
                    boolean $child1Value_ = (boolean) $child1Value;
                    return SLEqualNode.doBoolean($child0Value_, $child1Value_);
                }
            }
            if ($child0Value instanceof String) {
                String $child0Value_ = (String) $child0Value;
                if ($child1Value instanceof String) {
                    String $child1Value_ = (String) $child1Value;
                    return SLEqualNode.doString($child0Value_, $child1Value_);
                }
            }
            if ($child0Value instanceof TruffleString) {
                TruffleString $child0Value_ = (TruffleString) $child0Value;
                if ($child1Value instanceof TruffleString) {
                    TruffleString $child1Value_ = (TruffleString) $child1Value;
                    return SLEqualNode.doTruffleString($child0Value_, $child1Value_, (EqualNode.getUncached()));
                }
            }
            if (SLTypes.isSLNull($child0Value)) {
                SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                if (SLTypes.isSLNull($child1Value)) {
                    SLNull $child1Value_ = SLTypes.asSLNull($child1Value);
                    return SLEqualNode.doNull($child0Value_, $child1Value_);
                }
            }
            if ($child0Value instanceof SLFunction) {
                SLFunction $child0Value_ = (SLFunction) $child0Value;
                return SLEqualNode.doFunction($child0Value_, $child1Value);
            }
            return SLEqualNode.doGeneric($child0Value, $child1Value, (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value)));
        }

        private static Object SLLessOrEqual_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Long) {
                long $child0Value_ = (long) $child0Value;
                if ($child1Value instanceof Long) {
                    long $child1Value_ = (long) $child1Value;
                    return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                }
            }
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                }
            }
            return SLLessOrEqualNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLLessThan_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Long) {
                long $child0Value_ = (long) $child0Value;
                if ($child1Value instanceof Long) {
                    long $child1Value_ = (long) $child1Value;
                    return SLLessThanNode.lessThan($child0Value_, $child1Value_);
                }
            }
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLLessThanNode.lessThan($child0Value_, $child1Value_);
                }
            }
            return SLLessThanNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLLogicalNot_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                return SLLogicalNotNode.doBoolean($child0Value_);
            }
            return SLLogicalNotNode.typeError($child0Value, ($this), ($bci));
        }

        private static Object SLMul_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLMulNode.mul($child0Value_, $child1Value_);
                }
            }
            return SLMulNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLReadProperty_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                return SLReadPropertyNode.readArray($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value)));
            }
            if ($child0Value instanceof SLObject) {
                SLObject $child0Value_ = (SLObject) $child0Value;
                return SLReadPropertyNode.readSLObject($child0Value_, $child1Value, ($this), ($bci), (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached()));
            }
            if ((!(SLReadPropertyNode.isSLObject($child0Value))) && ((INTEROP_LIBRARY_.getUncached($child0Value)).hasMembers($child0Value))) {
                return SLReadPropertyNode.readObject($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached()));
            }
            throw new UnsupportedSpecializationException($this, new Node[] {null, null}, $child0Value, $child1Value);
        }

        private static Object SLSub_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                    return SLSubNode.sub($child0Value_, $child1Value_);
                }
            }
            return SLSubNode.typeError($child0Value, $child1Value, ($this), ($bci));
        }

        private static Object SLWriteProperty_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value, Object $child2Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                return SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value)));
            }
            if ($child0Value instanceof SLObject) {
                SLObject $child0Value_ = (SLObject) $child0Value;
                return SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached()));
            }
            if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                return SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached()));
            }
            throw new UnsupportedSpecializationException($this, new Node[] {null, null, null}, $child0Value, $child1Value, $child2Value);
        }

        private static Object SLUnbox_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof String) {
                String $child0Value_ = (String) $child0Value;
                return SLUnboxNode.fromString($child0Value_, (FromJavaStringNode.getUncached()));
            }
            if ($child0Value instanceof TruffleString) {
                TruffleString $child0Value_ = (TruffleString) $child0Value;
                return SLUnboxNode.fromTruffleString($child0Value_);
            }
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                return SLUnboxNode.fromBoolean($child0Value_);
            }
            if ($child0Value instanceof Long) {
                long $child0Value_ = (long) $child0Value;
                return SLUnboxNode.fromLong($child0Value_);
            }
            if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                return SLUnboxNode.fromBigNumber($child0Value_);
            }
            if ($child0Value instanceof SLFunction) {
                SLFunction $child0Value_ = (SLFunction) $child0Value;
                return SLUnboxNode.fromFunction($child0Value_);
            }
            if (SLTypes.isSLNull($child0Value)) {
                SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                return SLUnboxNode.fromFunction($child0Value_);
            }
            return SLUnboxNode.fromForeign($child0Value, (INTEROP_LIBRARY_.getUncached($child0Value)));
        }

        private static Object SLFunctionLiteral_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof TruffleString) {
                TruffleString $child0Value_ = (TruffleString) $child0Value;
                return SLFunctionLiteralNode.perform($child0Value_, (SLFunctionLiteralNode.lookupFunction($child0Value_, $this)), ($this));
            }
            throw new UnsupportedSpecializationException($this, new Node[] {null}, $child0Value);
        }

        private static Object SLToBoolean_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                return SLToBooleanNode.doBoolean($child0Value_);
            }
            return SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
        }

        private static Object SLInvoke_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics, Object $child0Value, Object[] $variadicChildValue) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof SLFunction) {
                SLFunction $child0Value_ = (SLFunction) $child0Value;
                return SLInvoke.doIndirect($child0Value_, $variadicChildValue, (IndirectCallNode.getUncached()));
            }
            return SLInvoke.doInterop($child0Value, $variadicChildValue, (INTEROP_LIBRARY_.getUncached()), ($this), ($bci));
        }

        private static boolean SLAnd_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                return SLToBooleanNode.doBoolean($child0Value_);
            }
            return SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
        }

        private static boolean SLOr_executeUncached_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
            int childArrayOffset_;
            int constArrayOffset_;
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if ($child0Value instanceof Boolean) {
                boolean $child0Value_ = (boolean) $child0Value;
                return SLToBooleanNode.doBoolean($child0Value_);
            }
            return SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
        }

        private static int instructionGroup_1_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // pop
                //   Simple Pops:
                //     [ 0] value
                //   Pushed Values: 0
                case ((INSTR_POP << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_POP, 0, 0);
                    $sp = $sp - 1;
                    $frame.clear($sp);
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_2_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // load.constant
                //   Constants:
                //     [ 0] constant
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_CONSTANT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_CONSTANT, 0, 0);
                    UFA.unsafeSetObject($frame, $sp, UFA.unsafeObjectArrayRead($consts, unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_CONSTANT_OFFSET) + 0));
                    $sp = $sp + 1;
                    return $sp;
                }
                // load.argument
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_ARGUMENT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_ARGUMENT, 0, 0);
                    UFA.unsafeSetObject($frame, $sp, $frame.getArguments()[unsafeFromBytecode($bc, $bci + LOAD_ARGUMENT_ARGUMENT_OFFSET + 0)]);
                    $sp = $sp + 1;
                    return $sp;
                }
                // load.local
                //   Locals:
                //     [ 0] local
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_LOCALS_OFFSET + 0);
                    UFA.unsafeCopyObject($frame, localIdx, $sp);
                    $sp += 1;
                    return $sp;
                }
                // load.local.boxed
                //   Locals:
                //     [ 0] local
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL_BOXED << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_BOXED, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + LOAD_LOCAL_BOXED_LOCALS_OFFSET + 0);
                    UFA.unsafeCopyObject($frame, localIdx, $sp);
                    $sp += 1;
                    return $sp;
                }
                // load.local.mat
                //   Simple Pops:
                //     [ 0] frame
                //   Always Boxed
                //   Pushed Values: 1
                case ((INSTR_LOAD_LOCAL_MAT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_LOAD_LOCAL_MAT, 0, 0);
                    Frame outerFrame;
                    outerFrame = (Frame) UFA.unsafeGetObject($frame, $sp - 1);
                    UFA.unsafeSetObject($frame, $sp - 1, outerFrame.getObject(unsafeFromBytecode($bc, $bci + LOAD_LOCAL_MAT_ARGUMENT_OFFSET + 0)));
                    return $sp;
                }
                // store.local.mat
                //   Simple Pops:
                //     [ 0] frame
                //     [ 1] value
                //   Pushed Values: 0
                case ((INSTR_STORE_LOCAL_MAT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL_MAT, 0, 0);
                    Frame outerFrame;
                    outerFrame = (Frame) UFA.unsafeGetObject($frame, $sp - 2);
                    outerFrame.setObject(unsafeFromBytecode($bc, $bci + STORE_LOCAL_MAT_ARGUMENT_OFFSET + 0), UFA.unsafeGetObject($frame, $sp - 1));
                    $sp -= 2;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_3_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // store.local
                //   Locals:
                //     [ 0] target
                //   Indexed Pops:
                //     [ 0] value
                //   Split on Boxing Elimination
                //   Pushed Values: 0
                case ((INSTR_STORE_LOCAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_STORE_LOCAL, 0, 0);
                    int localIdx = unsafeFromBytecode($bc, $bci + STORE_LOCAL_LOCALS_OFFSET + 0);
                    UFA.unsafeCopyObject($frame, $sp - 1, localIdx);
                    $sp -= 1;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_5_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLEqual
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = equalNode]
                //     [ 1] SpecializationData [id = Generic0]
                //     [ 2] CacheExpression [sourceParameter = leftInterop]
                //     [ 3] CacheExpression [sourceParameter = rightInterop]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Long], SpecializationData [id = BigNumber], SpecializationData [id = Boolean], SpecializationData [id = String], SpecializationData [id = TruffleString], SpecializationData [id = Null], SpecializationData [id = Function], SpecializationData [id = Generic0], SpecializationData [id = Generic1], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = Long], SpecializationData [id = BigNumber], SpecializationData [id = Boolean], SpecializationData [id = String], SpecializationData [id = TruffleString], SpecializationData [id = Null], SpecializationData [id = Function], SpecializationData [id = Generic0], SpecializationData [id = Generic1]]
                case ((INSTR_C_SL_EQUAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLEqual_($bc, $bci));
                    SLEqual_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLessOrEqual
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = LessOrEqual0], SpecializationData [id = LessOrEqual1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                case ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_OR_EQUAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_OR_EQUAL, SLOperationRootNodeGen.doGetStateBits_SLLessOrEqual_($bc, $bci));
                    SLLessOrEqual_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLessThan
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = LessThan0], SpecializationData [id = LessThan1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                case ((INSTR_C_SL_LESS_THAN << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LESS_THAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LESS_THAN, SLOperationRootNodeGen.doGetStateBits_SLLessThan_($bc, $bci));
                    SLLessThan_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLLogicalNot
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_LOGICAL_NOT << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_LOGICAL_NOT, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_LOGICAL_NOT, SLOperationRootNodeGen.doGetStateBits_SLLogicalNot_($bc, $bci));
                    SLLogicalNot_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLUnbox
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                //     [ 1] SpecializationData [id = FromForeign0]
                //     [ 2] CacheExpression [sourceParameter = interop]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = FromString], SpecializationData [id = FromTruffleString], SpecializationData [id = FromBoolean], SpecializationData [id = FromLong], SpecializationData [id = FromBigNumber], SpecializationData [id = FromFunction0], SpecializationData [id = FromFunction1], SpecializationData [id = FromForeign0], SpecializationData [id = FromForeign1], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = FromString], SpecializationData [id = FromTruffleString], SpecializationData [id = FromBoolean], SpecializationData [id = FromLong], SpecializationData [id = FromBigNumber], SpecializationData [id = FromFunction0], SpecializationData [id = FromFunction1], SpecializationData [id = FromForeign0], SpecializationData [id = FromForeign1]]
                case ((INSTR_C_SL_UNBOX << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_UNBOX, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_UNBOX, SLOperationRootNodeGen.doGetStateBits_SLUnbox_($bc, $bci));
                    SLUnbox_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLFunctionLiteral
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = result]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Perform]]
                case ((INSTR_C_SL_FUNCTION_LITERAL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_FUNCTION_LITERAL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_FUNCTION_LITERAL, SLOperationRootNodeGen.doGetStateBits_SLFunctionLiteral_($bc, $bci));
                    SLFunctionLiteral_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                // c.SLToBoolean
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Boolean], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_TO_BOOLEAN << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_TO_BOOLEAN, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_TO_BOOLEAN, SLOperationRootNodeGen.doGetStateBits_SLToBoolean_($bc, $bci));
                    SLToBoolean_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_6_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLAdd
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = Add1]
                //     [ 1] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = AddLong], SpecializationData [id = Add0], SpecializationData [id = Add1], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = AddLong], SpecializationData [id = Add0], SpecializationData [id = Add1], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_ADD << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_ADD, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_ADD, SLOperationRootNodeGen.doGetStateBits_SLAdd_($bc, $bci));
                    SLAdd_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLDiv
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = DivLong], SpecializationData [id = Div], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = DivLong], SpecializationData [id = Div], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_DIV << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_DIV, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_DIV, SLOperationRootNodeGen.doGetStateBits_SLDiv_($bc, $bci));
                    SLDiv_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLMul
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = MulLong], SpecializationData [id = Mul], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = MulLong], SpecializationData [id = Mul], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_MUL << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_MUL, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_MUL, SLOperationRootNodeGen.doGetStateBits_SLMul_($bc, $bci));
                    SLMul_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLReadProperty
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //     [ 1] CacheExpression [sourceParameter = bci]
                //     [ 2] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = ReadArray0]
                //     [ 1] CacheExpression [sourceParameter = node]
                //     [ 2] CacheExpression [sourceParameter = arrays]
                //     [ 3] CacheExpression [sourceParameter = numbers]
                //     [ 4] SpecializationData [id = ReadSLObject0]
                //     [ 5] CacheExpression [sourceParameter = node]
                //     [ 6] CacheExpression [sourceParameter = objectLibrary]
                //     [ 7] CacheExpression [sourceParameter = toTruffleStringNode]
                //     [ 8] SpecializationData [id = ReadObject0]
                //     [ 9] CacheExpression [sourceParameter = node]
                //     [10] CacheExpression [sourceParameter = objects]
                //     [11] CacheExpression [sourceParameter = asMember]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = ReadArray0], SpecializationData [id = ReadArray1], SpecializationData [id = ReadSLObject0], SpecializationData [id = ReadSLObject1], SpecializationData [id = ReadObject0], SpecializationData [id = ReadObject1]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = ReadArray0], SpecializationData [id = ReadArray1], SpecializationData [id = ReadSLObject0], SpecializationData [id = ReadSLObject1], SpecializationData [id = ReadObject0], SpecializationData [id = ReadObject1]]
                case ((INSTR_C_SL_READ_PROPERTY << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_READ_PROPERTY, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_READ_PROPERTY, SLOperationRootNodeGen.doGetStateBits_SLReadProperty_($bc, $bci));
                    SLReadProperty_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                // c.SLSub
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] CacheExpression [sourceParameter = node]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //   Split on Boxing Elimination
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = SubLong], SpecializationData [id = Sub], SpecializationData [id = Fallback], com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d74f, com.oracle.truffle.dsl.processor.parser.SpecializationGroup$TypeGuard@7d04d76e]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = SubLong], SpecializationData [id = Sub], SpecializationData [id = Fallback]]
                case ((INSTR_C_SL_SUB << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_SUB, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_SUB, SLOperationRootNodeGen.doGetStateBits_SLSub_($bc, $bci));
                    SLSub_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -1;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static int instructionGroup_7_0_uncached(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, int[] $conditionProfiles, int curOpcode, ExecutionTracer tracer) {
            int $sp = $startSp;
            switch (curOpcode) {
                // c.SLWriteProperty
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //     [ 1] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = WriteArray0]
                //     [ 1] CacheExpression [sourceParameter = node]
                //     [ 2] CacheExpression [sourceParameter = arrays]
                //     [ 3] CacheExpression [sourceParameter = numbers]
                //     [ 4] SpecializationData [id = WriteSLObject0]
                //     [ 5] CacheExpression [sourceParameter = objectLibrary]
                //     [ 6] CacheExpression [sourceParameter = toTruffleStringNode]
                //     [ 7] SpecializationData [id = WriteObject0]
                //     [ 8] CacheExpression [sourceParameter = node]
                //     [ 9] CacheExpression [sourceParameter = objectLibrary]
                //     [10] CacheExpression [sourceParameter = asMember]
                //   Indexed Pops:
                //     [ 0] arg0
                //     [ 1] arg1
                //     [ 2] arg2
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = WriteArray0], SpecializationData [id = WriteArray1], SpecializationData [id = WriteSLObject0], SpecializationData [id = WriteSLObject1], SpecializationData [id = WriteObject0], SpecializationData [id = WriteObject1]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = WriteArray0], SpecializationData [id = WriteArray1], SpecializationData [id = WriteSLObject0], SpecializationData [id = WriteSLObject1], SpecializationData [id = WriteObject0], SpecializationData [id = WriteObject1]]
                case ((INSTR_C_SL_WRITE_PROPERTY << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_WRITE_PROPERTY, 0, 0);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_WRITE_PROPERTY, SLOperationRootNodeGen.doGetStateBits_SLWriteProperty_($bc, $bci));
                    SLWriteProperty_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children);
                    $sp += -2;
                    return $sp;
                }
                // c.SLInvoke
                //   Constants:
                //     [ 0] CacheExpression [sourceParameter = bci]
                //   Children:
                //     [ 0] SpecializationData [id = Direct]
                //     [ 1] CacheExpression [sourceParameter = callNode]
                //     [ 2] CacheExpression [sourceParameter = library]
                //     [ 3] CacheExpression [sourceParameter = node]
                //     [ 4] NodeExecutionData[child=NodeFieldData[name=$variadicChild, kind=ONE, node=NodeData[C]], name=$variadicChild, index=1]
                //   Indexed Pops:
                //     [ 0] arg0
                //   Variadic
                //   Always Boxed
                //   Pushed Values: 1
                //   State Bitsets:
                //     [ 0] StateBitSet state_0 [SpecializationData [id = Direct], SpecializationData [id = Indirect], SpecializationData [id = Interop]]
                //     [ 1] ExcludeBitSet exclude [SpecializationData [id = Direct], SpecializationData [id = Indirect], SpecializationData [id = Interop]]
                case ((INSTR_C_SL_INVOKE << 3) | 0) :
                {
                    tracer.traceInstruction($bci, INSTR_C_SL_INVOKE, 0, 1);
                    tracer.traceActiveSpecializations($bci, INSTR_C_SL_INVOKE, SLOperationRootNodeGen.doGetStateBits_SLInvoke_($bc, $bci));
                    int numVariadics = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_VARIADIC_OFFSET + 0);
                    SLInvoke_entryPoint_uncached($frame, $this, $bc, $bci, $sp, $consts, $children, numVariadics);
                    $sp +=  - numVariadics;
                    return $sp;
                }
                default :
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private static boolean isAdoptable() {
            return true;
        }

        private static final class SLAdd_Add1Data extends Node {

            @Child SLToTruffleStringNode toTruffleStringNodeLeft_;
            @Child SLToTruffleStringNode toTruffleStringNodeRight_;
            @Child ConcatNode concatNode_;

            SLAdd_Add1Data() {
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLEqual_Generic0Data extends Node {

            @Child SLEqual_Generic0Data next_;
            @Child InteropLibrary leftInterop_;
            @Child InteropLibrary rightInterop_;

            SLEqual_Generic0Data(SLEqual_Generic0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadArray0Data extends Node {

            @Child SLReadProperty_ReadArray0Data next_;
            @Child InteropLibrary arrays_;
            @Child InteropLibrary numbers_;

            SLReadProperty_ReadArray0Data(SLReadProperty_ReadArray0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadSLObject0Data extends Node {

            @Child SLReadProperty_ReadSLObject0Data next_;
            @Child DynamicObjectLibrary objectLibrary_;
            @Child SLToTruffleStringNode toTruffleStringNode_;

            SLReadProperty_ReadSLObject0Data(SLReadProperty_ReadSLObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLReadProperty_ReadObject0Data extends Node {

            @Child SLReadProperty_ReadObject0Data next_;
            @Child InteropLibrary objects_;
            @Child SLToMemberNode asMember_;

            SLReadProperty_ReadObject0Data(SLReadProperty_ReadObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteArray0Data extends Node {

            @Child SLWriteProperty_WriteArray0Data next_;
            @Child InteropLibrary arrays_;
            @Child InteropLibrary numbers_;

            SLWriteProperty_WriteArray0Data(SLWriteProperty_WriteArray0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteSLObject0Data extends Node {

            @Child SLWriteProperty_WriteSLObject0Data next_;
            @Child DynamicObjectLibrary objectLibrary_;
            @Child SLToTruffleStringNode toTruffleStringNode_;

            SLWriteProperty_WriteSLObject0Data(SLWriteProperty_WriteSLObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLWriteProperty_WriteObject0Data extends Node {

            @Child SLWriteProperty_WriteObject0Data next_;
            @Child InteropLibrary objectLibrary_;
            @Child SLToMemberNode asMember_;

            SLWriteProperty_WriteObject0Data(SLWriteProperty_WriteObject0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        private static final class SLUnbox_FromForeign0Data extends Node {

            @Child SLUnbox_FromForeign0Data next_;
            @Child InteropLibrary interop_;

            SLUnbox_FromForeign0Data(SLUnbox_FromForeign0Data next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class SLInvoke_DirectData extends Node {

            @Child SLInvoke_DirectData next_;
            @CompilationFinal Assumption callTargetStable_;
            @CompilationFinal RootCallTarget cachedTarget_;
            @Child DirectCallNode callNode_;

            SLInvoke_DirectData(SLInvoke_DirectData next_) {
                this.next_ = next_;
            }

            @Override
            public NodeCost getCost() {
                return NodeCost.NONE;
            }

            <T extends Node> T insertAccessor(T node) {
                return super.insert(node);
            }

        }
    }
    private static final UnsafeFrameAccess UFA = UnsafeFrameAccess.lookup();
    private static final BytecodeLoopBase UNCOMMON_EXECUTE = new BytecodeNode();
    private static final BytecodeLoopBase COMMON_EXECUTE = UNCOMMON_EXECUTE;
    private static final BytecodeLoopBase UNCACHED_EXECUTE = new UncachedBytecodeNode();
    private static final BytecodeLoopBase INITIAL_EXECUTE = UNCACHED_EXECUTE;
    private static final short INSTR_POP = 1;
    private static final int POP_LENGTH = 1;
    private static final short INSTR_BRANCH = 2;
    private static final int BRANCH_BRANCH_TARGET_OFFSET = 1;
    private static final int BRANCH_LENGTH = 2;
    private static final short INSTR_BRANCH_FALSE = 3;
    private static final int BRANCH_FALSE_BRANCH_TARGET_OFFSET = 1;
    private static final int BRANCH_FALSE_BRANCH_PROFILE_OFFSET = 2;
    private static final int BRANCH_FALSE_LENGTH = 3;
    private static final short INSTR_THROW = 4;
    private static final int THROW_LOCALS_OFFSET = 1;
    private static final int THROW_LENGTH = 2;
    private static final short INSTR_LOAD_CONSTANT = 5;
    private static final int LOAD_CONSTANT_CONSTANT_OFFSET = 1;
    private static final int LOAD_CONSTANT_LENGTH = 2;
    private static final short INSTR_LOAD_ARGUMENT = 6;
    private static final int LOAD_ARGUMENT_ARGUMENT_OFFSET = 1;
    private static final int LOAD_ARGUMENT_LENGTH = 2;
    private static final short INSTR_LOAD_LOCAL = 7;
    private static final int LOAD_LOCAL_LOCALS_OFFSET = 1;
    private static final int LOAD_LOCAL_LENGTH = 2;
    private static final short INSTR_LOAD_LOCAL_BOXED = 8;
    private static final int LOAD_LOCAL_BOXED_LOCALS_OFFSET = 1;
    private static final int LOAD_LOCAL_BOXED_LENGTH = 2;
    private static final short INSTR_STORE_LOCAL = 9;
    private static final int STORE_LOCAL_LOCALS_OFFSET = 1;
    private static final int STORE_LOCAL_POP_INDEXED_OFFSET = 2;
    private static final int STORE_LOCAL_LENGTH = 3;
    private static final short INSTR_RETURN = 10;
    private static final int RETURN_LENGTH = 1;
    private static final short INSTR_LOAD_LOCAL_MAT = 11;
    private static final int LOAD_LOCAL_MAT_ARGUMENT_OFFSET = 1;
    private static final int LOAD_LOCAL_MAT_LENGTH = 2;
    private static final short INSTR_STORE_LOCAL_MAT = 12;
    private static final int STORE_LOCAL_MAT_ARGUMENT_OFFSET = 1;
    private static final int STORE_LOCAL_MAT_LENGTH = 2;
    private static final short INSTR_INSTRUMENT_ENTER = 13;
    private static final int INSTRUMENT_ENTER_LENGTH = 2;
    private static final short INSTR_INSTRUMENT_EXIT_VOID = 14;
    private static final int INSTRUMENT_EXIT_VOID_LENGTH = 2;
    private static final short INSTR_INSTRUMENT_EXIT = 15;
    private static final int INSTRUMENT_EXIT_LENGTH = 2;
    private static final short INSTR_INSTRUMENT_LEAVE = 16;
    private static final int INSTRUMENT_LEAVE_BRANCH_TARGET_OFFSET = 1;
    private static final int INSTRUMENT_LEAVE_LENGTH = 4;
    private static final short INSTR_C_SL_ADD = 17;
    private static final int C_SL_ADD_CONSTANT_OFFSET = 1;
    private static final int C_SL_ADD_CHILDREN_OFFSET = 2;
    private static final int C_SL_ADD_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_ADD_STATE_BITS_OFFSET = 4;
    private static final int C_SL_ADD_LENGTH = 6;
    private static final short INSTR_C_SL_DIV = 18;
    private static final int C_SL_DIV_CONSTANT_OFFSET = 1;
    private static final int C_SL_DIV_CHILDREN_OFFSET = 2;
    private static final int C_SL_DIV_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_DIV_STATE_BITS_OFFSET = 4;
    private static final int C_SL_DIV_LENGTH = 6;
    private static final short INSTR_C_SL_EQUAL = 19;
    private static final int C_SL_EQUAL_CHILDREN_OFFSET = 1;
    private static final int C_SL_EQUAL_POP_INDEXED_OFFSET = 2;
    private static final int C_SL_EQUAL_STATE_BITS_OFFSET = 3;
    private static final int C_SL_EQUAL_LENGTH = 5;
    private static final short INSTR_C_SL_LESS_OR_EQUAL = 20;
    private static final int C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET = 1;
    private static final int C_SL_LESS_OR_EQUAL_CHILDREN_OFFSET = 2;
    private static final int C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET = 4;
    private static final int C_SL_LESS_OR_EQUAL_LENGTH = 5;
    private static final short INSTR_C_SL_LESS_THAN = 21;
    private static final int C_SL_LESS_THAN_CONSTANT_OFFSET = 1;
    private static final int C_SL_LESS_THAN_CHILDREN_OFFSET = 2;
    private static final int C_SL_LESS_THAN_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_LESS_THAN_STATE_BITS_OFFSET = 4;
    private static final int C_SL_LESS_THAN_LENGTH = 5;
    private static final short INSTR_C_SL_LOGICAL_NOT = 22;
    private static final int C_SL_LOGICAL_NOT_CONSTANT_OFFSET = 1;
    private static final int C_SL_LOGICAL_NOT_CHILDREN_OFFSET = 2;
    private static final int C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_LOGICAL_NOT_STATE_BITS_OFFSET = 4;
    private static final int C_SL_LOGICAL_NOT_LENGTH = 5;
    private static final short INSTR_C_SL_MUL = 23;
    private static final int C_SL_MUL_CONSTANT_OFFSET = 1;
    private static final int C_SL_MUL_CHILDREN_OFFSET = 2;
    private static final int C_SL_MUL_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_MUL_STATE_BITS_OFFSET = 4;
    private static final int C_SL_MUL_LENGTH = 6;
    private static final short INSTR_C_SL_READ_PROPERTY = 24;
    private static final int C_SL_READ_PROPERTY_CONSTANT_OFFSET = 1;
    private static final int C_SL_READ_PROPERTY_CHILDREN_OFFSET = 2;
    private static final int C_SL_READ_PROPERTY_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_READ_PROPERTY_STATE_BITS_OFFSET = 4;
    private static final int C_SL_READ_PROPERTY_LENGTH = 6;
    private static final short INSTR_C_SL_SUB = 25;
    private static final int C_SL_SUB_CONSTANT_OFFSET = 1;
    private static final int C_SL_SUB_CHILDREN_OFFSET = 2;
    private static final int C_SL_SUB_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_SUB_STATE_BITS_OFFSET = 4;
    private static final int C_SL_SUB_LENGTH = 6;
    private static final short INSTR_C_SL_WRITE_PROPERTY = 26;
    private static final int C_SL_WRITE_PROPERTY_CONSTANT_OFFSET = 1;
    private static final int C_SL_WRITE_PROPERTY_CHILDREN_OFFSET = 2;
    private static final int C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET = 5;
    private static final int C_SL_WRITE_PROPERTY_LENGTH = 7;
    private static final short INSTR_C_SL_UNBOX = 27;
    private static final int C_SL_UNBOX_CHILDREN_OFFSET = 1;
    private static final int C_SL_UNBOX_POP_INDEXED_OFFSET = 2;
    private static final int C_SL_UNBOX_STATE_BITS_OFFSET = 3;
    private static final int C_SL_UNBOX_LENGTH = 5;
    private static final short INSTR_C_SL_FUNCTION_LITERAL = 28;
    private static final int C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET = 1;
    private static final int C_SL_FUNCTION_LITERAL_CHILDREN_OFFSET = 2;
    private static final int C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET = 4;
    private static final int C_SL_FUNCTION_LITERAL_LENGTH = 5;
    private static final short INSTR_C_SL_TO_BOOLEAN = 29;
    private static final int C_SL_TO_BOOLEAN_CONSTANT_OFFSET = 1;
    private static final int C_SL_TO_BOOLEAN_CHILDREN_OFFSET = 2;
    private static final int C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_TO_BOOLEAN_STATE_BITS_OFFSET = 4;
    private static final int C_SL_TO_BOOLEAN_LENGTH = 5;
    private static final short INSTR_C_SL_INVOKE = 30;
    private static final int C_SL_INVOKE_CONSTANT_OFFSET = 1;
    private static final int C_SL_INVOKE_CHILDREN_OFFSET = 2;
    private static final int C_SL_INVOKE_POP_INDEXED_OFFSET = 3;
    private static final int C_SL_INVOKE_VARIADIC_OFFSET = 4;
    private static final int C_SL_INVOKE_STATE_BITS_OFFSET = 5;
    private static final int C_SL_INVOKE_LENGTH = 7;
    private static final short INSTR_SC_SL_AND = 31;
    private static final int SC_SL_AND_CONSTANT_OFFSET = 1;
    private static final int SC_SL_AND_CHILDREN_OFFSET = 2;
    private static final int SC_SL_AND_POP_INDEXED_OFFSET = 3;
    private static final int SC_SL_AND_BRANCH_TARGET_OFFSET = 4;
    private static final int SC_SL_AND_STATE_BITS_OFFSET = 5;
    private static final int SC_SL_AND_LENGTH = 6;
    private static final short INSTR_SC_SL_OR = 32;
    private static final int SC_SL_OR_CONSTANT_OFFSET = 1;
    private static final int SC_SL_OR_CHILDREN_OFFSET = 2;
    private static final int SC_SL_OR_POP_INDEXED_OFFSET = 3;
    private static final int SC_SL_OR_BRANCH_TARGET_OFFSET = 4;
    private static final int SC_SL_OR_STATE_BITS_OFFSET = 5;
    private static final int SC_SL_OR_LENGTH = 6;

    @CompilationFinal private OperationNodesImpl nodes;
    @CompilationFinal(dimensions = 1) private short[] _bc;
    @CompilationFinal(dimensions = 1) private Object[] _consts;
    @Children private Node[] _children;
    @CompilationFinal(dimensions = 1) private byte[] _localTags;
    @CompilationFinal(dimensions = 1) private ExceptionHandler[] _handlers;
    @CompilationFinal(dimensions = 1) private int[] _conditionProfiles;
    @CompilationFinal private int _maxLocals;
    @CompilationFinal private int _maxStack;
    @CompilationFinal(dimensions = 1) private int[] sourceInfo;
    @CompilationFinal(dimensions = 1) private boolean[] isBbStart;
    @CompilationFinal private BytecodeLoopBase switchImpl = INITIAL_EXECUTE;
    @CompilationFinal private int uncachedExecuteCount = 16;
    @CompilationFinal private Object _osrMetadata;
    private TruffleString _metadata_MethodName;

    private SLOperationRootNodeGen(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    private SLOperationRootNodeGen(TruffleLanguage<?> language, com.oracle.truffle.api.frame.FrameDescriptor.Builder builder) {
        this(language, builder.build());
    }

    static  {
        OperationRootNode.setMetadataAccessor(SLOperationRootNode.MethodName, n -> ((SLOperationRootNodeGen) n)._metadata_MethodName);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E { //() {
        throw (E) e;
    }

    private <T extends Node> T insertAccessor(T node) { // () {
        return insert(node);
    }

    private Object executeAt(VirtualFrame frame, int storedLocation) {
        int result = storedLocation;
        while (true) {
            result = switchImpl.continueAt(this, frame, _bc, result & 0xffff, (result >> 16) & 0xffff, _consts, _children, _localTags, _handlers, _conditionProfiles, _maxLocals);
            if ((result & 0xffff) == 0xffff) {
                break;
            } else {
                SLOperationRootNodeGen $this = this;
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
        }
        return frame.getObject((result >> 16) & 0xffff);
    }

    @Override
    public SourceSection getSourceSection() {
        int[] sourceInfo = this.sourceInfo;
        if (sourceInfo == null) {
            return null;
        }
        int i;
        for (i = 0; i < sourceInfo.length; i += 3) {
            if (sourceInfo[i + 1] >= 0) {
                int sourceIndex = sourceInfo[i + 0] >> 16;
                int sourceStart = sourceInfo[i + 1];
                int sourceLength = sourceInfo[i + 2];
                return nodes.getSources()[sourceIndex].createSection(sourceStart, sourceLength);
            }
        }
        return null;
    }

    @Override
    public SourceSection getSourceSectionAtBci(int bci) {
        int[] sourceInfo = this.sourceInfo;
        if (sourceInfo == null) {
            return null;
        }
        int i;
        for (i = 0; i < sourceInfo.length; i += 3) {
            if ((sourceInfo[i + 0] & 0xffff) > bci) {
                break;
            }
        }
        if (i == 0) {
            return null;
        } else {
            i -= 3;
            int sourceIndex = sourceInfo[i + 0] >> 16;
            if (sourceIndex < 0) {
                return null;
            }
            int sourceStart = sourceInfo[i + 1];
            if (sourceStart < 0) {
                return null;
            }
            int sourceLength = sourceInfo[i + 2];
            return nodes.getSources()[sourceIndex].createSection(sourceStart, sourceLength);
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object returnValue = null;
        Throwable throwable = null;
        executeProlog(frame);
        try {
            returnValue = executeAt(frame, _maxLocals << 16);
            return returnValue;
        } catch (Throwable th) {
            throw sneakyThrow(throwable = th);
        } finally {
            executeEpilog(frame, returnValue, throwable);
        }
    }

    @Override
    public OperationIntrospection getIntrospectionData() {
        return switchImpl.getIntrospectionData(_bc, _handlers, _consts, nodes, sourceInfo);
    }

    private Lock getLockAccessor() {
        return getLock();
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        return executeAt(osrFrame, target);
    }

    @Override
    public Object getOSRMetadata() {
        return _osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        _osrMetadata = osrMetadata;
    }

    @Override
    public Node deepCopy() {
        SLOperationRootNodeGen result = new SLOperationRootNodeGen(getLanguage(), getFrameDescriptor().copy());
        result.nodes = nodes;
        result._bc = Arrays.copyOf(_bc, _bc.length);
        result._consts = Arrays.copyOf(_consts, _consts.length);
        result._children = Arrays.copyOf(_children, _children.length);
        result._localTags = Arrays.copyOf(_localTags, _localTags.length);
        result.isBbStart = isBbStart;
        result._handlers = _handlers;
        result._conditionProfiles = Arrays.copyOf(_conditionProfiles, _conditionProfiles.length);
        result._maxLocals = _maxLocals;
        result._maxStack = _maxStack;
        result.sourceInfo = sourceInfo;
        result._metadata_MethodName = _metadata_MethodName;
        return result;
    }

    @Override
    public Node copy() {
        SLOperationRootNodeGen result = new SLOperationRootNodeGen(getLanguage(), getFrameDescriptor().copy());
        result.nodes = nodes;
        result._bc = _bc;
        result._consts = _consts;
        result._children = _children;
        result._localTags = _localTags;
        result._handlers = _handlers;
        result._conditionProfiles = _conditionProfiles;
        result._maxLocals = _maxLocals;
        result._maxStack = _maxStack;
        result.sourceInfo = sourceInfo;
        result._metadata_MethodName = _metadata_MethodName;
        return result;
    }

    private void changeInterpreters(BytecodeLoopBase impl) {
        this.switchImpl = impl;
    }

    private static boolean[] doGetStateBits_SLAdd_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_ADD_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[4];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 addLong(long, long) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 add(SLBigNumber, SLBigNumber) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 add(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */;
        result[3] = (state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLDiv_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_DIV_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 divLong(long, long) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 div(SLBigNumber, SLBigNumber) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 div(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLEqual_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[9];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doLong(long, long) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 doBigNumber(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 doBoolean(boolean, boolean) */;
        result[3] = (state_0 & 0b1000) != 0 /* is-state_0 doString(String, String) */;
        result[4] = (state_0 & 0b10000) != 0 /* is-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */;
        result[5] = (state_0 & 0b100000) != 0 /* is-state_0 doNull(SLNull, SLNull) */;
        result[6] = (state_0 & 0b1000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */;
        result[7] = (state_0 & 0b10000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */ && ((state_0 & 0b100000000)) == 0 /* is-not-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */;
        result[8] = (state_0 & 0b100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLLessOrEqual_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 lessOrEqual(long, long) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLLessThan_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 lessThan(long, long) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 lessThan(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLLogicalNot_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[2];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 typeError(Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLMul_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_MUL_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 mulLong(long, long) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 mul(SLBigNumber, SLBigNumber) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 mul(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLReadProperty_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[6];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */ && ((state_0 & 0b1000)) == 0 /* is-not-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */;
        result[3] = (state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */;
        result[4] = (state_0 & 0b10000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */ && ((state_0 & 0b100000)) == 0 /* is-not-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */;
        result[5] = (state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLSub_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_SUB_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 subLong(long, long) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 sub(SLBigNumber, SLBigNumber) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 sub(SLBigNumber, SLBigNumber) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLWriteProperty_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[6];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */ && ((state_0 & 0b1000)) == 0 /* is-not-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */;
        result[3] = (state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */;
        result[4] = (state_0 & 0b10000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */ && ((state_0 & 0b100000)) == 0 /* is-not-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */;
        result[5] = (state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLUnbox_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[9];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 fromString(String, FromJavaStringNode) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 fromTruffleString(TruffleString) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 fromBoolean(boolean) */;
        result[3] = (state_0 & 0b1000) != 0 /* is-state_0 fromLong(long) */;
        result[4] = (state_0 & 0b10000) != 0 /* is-state_0 fromBigNumber(SLBigNumber) */;
        result[5] = (state_0 & 0b100000) != 0 /* is-state_0 fromFunction(SLFunction) */;
        result[6] = (state_0 & 0b1000000) != 0 /* is-state_0 fromFunction(SLNull) */;
        result[7] = (state_0 & 0b10000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */ && ((state_0 & 0b100000000)) == 0 /* is-not-state_0 fromForeign(Object, InteropLibrary) */;
        result[8] = (state_0 & 0b100000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLFunctionLiteral_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[1];
        result[0] = state_0 != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLToBoolean_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[2];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLInvoke_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[3];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */ && ((state_0 & 0b10)) == 0 /* is-not-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */;
        result[2] = (state_0 & 0b100) != 0 /* is-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLAnd_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_AND_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[2];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */;
        return result;
    }

    private static boolean[] doGetStateBits_SLOr_(short[] $bc, int $bci) {
        short state_0 = unsafeFromBytecode($bc, $bci + SC_SL_OR_STATE_BITS_OFFSET + 0);
        boolean[] result = new boolean[2];
        result[0] = (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
        result[1] = (state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */;
        return result;
    }

    private static void do_loadLocal_OBJECT(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        Object value;
        switch (UFA.unsafeGetTag($frame, localIdx)) {
            case 0 /* OBJECT */ :
                value = UFA.unsafeUncheckedGetObject($frame, localIdx);
                break;
            case 5 /* BOOLEAN */ :
                value = UFA.unsafeUncheckedGetBoolean($frame, localIdx);
                break;
            case 1 /* LONG */ :
                value = UFA.unsafeUncheckedGetLong($frame, localIdx);
                break;
            default :
                throw CompilerDirectives.shouldNotReachHere();
        }
        UFA.unsafeSetObject($frame, $sp, value);
        return;
    }

    private static void do_loadLocal_BOOLEAN(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        try {
            UFA.unsafeSetBoolean($frame, $sp, UFA.unsafeGetBoolean($frame, localIdx));
            return;
        } catch (FrameSlotTypeException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Object result = $frame.getValue(localIdx);
            if (result instanceof Boolean) {
                if (UFA.unsafeByteArrayRead(localTags, localIdx) == 7) {
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_LOAD_LOCAL_BOXED << 3) | 5 /* BOOLEAN */));
                } else {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 5 /* BOOLEAN */);
                    UFA.unsafeSetBoolean($frame, localIdx, (boolean) result);
                    UFA.unsafeSetBoolean($frame, $sp, (boolean) result);
                    return;
                }
            }
        }
        Object result = $frame.getValue(localIdx);
        UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 7);
        UFA.unsafeSetObject($frame, localIdx, result);
        UFA.unsafeSetObject($frame, $sp, result);
    }

    private static void do_loadLocal_LONG(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        try {
            UFA.unsafeSetLong($frame, $sp, UFA.unsafeGetLong($frame, localIdx));
            return;
        } catch (FrameSlotTypeException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Object result = $frame.getValue(localIdx);
            if (result instanceof Long) {
                if (UFA.unsafeByteArrayRead(localTags, localIdx) == 7) {
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_LOAD_LOCAL_BOXED << 3) | 1 /* LONG */));
                } else {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 1 /* LONG */);
                    UFA.unsafeSetLong($frame, localIdx, (long) result);
                    UFA.unsafeSetLong($frame, $sp, (long) result);
                    return;
                }
            }
        }
        Object result = $frame.getValue(localIdx);
        UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 7);
        UFA.unsafeSetObject($frame, localIdx, result);
        UFA.unsafeSetObject($frame, $sp, result);
    }

    private static void do_loadLocalBoxed_BOOLEAN(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        try {
            UFA.unsafeSetBoolean($frame, $sp, (boolean) UFA.unsafeGetObject($frame, localIdx));
            return;
        } catch (FrameSlotTypeException | ClassCastException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Object result = $frame.getValue(localIdx);
            if (result instanceof Boolean) {
                if (UFA.unsafeByteArrayRead(localTags, localIdx) == 7) {
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_LOAD_LOCAL_BOXED << 3) | 5 /* BOOLEAN */));
                } else {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 5 /* BOOLEAN */);
                    UFA.unsafeSetBoolean($frame, localIdx, (boolean) result);
                    UFA.unsafeSetBoolean($frame, $sp, (boolean) result);
                    return;
                }
            }
        }
        Object result = $frame.getValue(localIdx);
        UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 7);
        UFA.unsafeSetObject($frame, localIdx, result);
        UFA.unsafeSetObject($frame, $sp, result);
    }

    private static void do_loadLocalBoxed_LONG(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        try {
            UFA.unsafeSetLong($frame, $sp, (long) UFA.unsafeGetObject($frame, localIdx));
            return;
        } catch (FrameSlotTypeException | ClassCastException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Object result = $frame.getValue(localIdx);
            if (result instanceof Long) {
                if (UFA.unsafeByteArrayRead(localTags, localIdx) == 7) {
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_LOAD_LOCAL_BOXED << 3) | 1 /* LONG */));
                } else {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 1 /* LONG */);
                    UFA.unsafeSetLong($frame, localIdx, (long) result);
                    UFA.unsafeSetLong($frame, $sp, (long) result);
                    return;
                }
            }
        }
        Object result = $frame.getValue(localIdx);
        UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 7);
        UFA.unsafeSetObject($frame, localIdx, result);
        UFA.unsafeSetObject($frame, $sp, result);
    }

    private static void do_storeLocalSpecialize(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int primitiveTag, int localIdx, int sourceSlot) {
        CompilerAsserts.neverPartOfCompilation();
        Object value = $frame.getValue(sourceSlot);
        byte curKind = UFA.unsafeByteArrayRead(localTags, localIdx);
        int bciOffset = (unsafeFromBytecode($bc, $bci + STORE_LOCAL_POP_INDEXED_OFFSET + 0) & 0xff);
        // System.err.printf("primitiveTag=%d value=%s %s curKind=%s tag=%s%n", primitiveTag, value.getClass(), value, curKind, $frame.getTag(sourceSlot));
        if (bciOffset != 0) {
            if ((primitiveTag == 0 || primitiveTag == 5 /* BOOLEAN */) && (curKind == 0 || curKind == 5 /* BOOLEAN */)) {
                if (value instanceof Boolean) {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 5 /* BOOLEAN */);
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_STORE_LOCAL << 3) | 5 /* BOOLEAN */));
                    doSetResultBoxed($bc, $bci, bciOffset, 5 /* BOOLEAN */);
                    UFA.unsafeSetBoolean($frame, localIdx, (boolean) value);
                    return;
                }
            }
            if ((primitiveTag == 0 || primitiveTag == 1 /* LONG */) && (curKind == 0 || curKind == 1 /* LONG */)) {
                if (value instanceof Long) {
                    UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 1 /* LONG */);
                    unsafeWriteBytecode($bc, $bci, (short) ((INSTR_STORE_LOCAL << 3) | 1 /* LONG */));
                    doSetResultBoxed($bc, $bci, bciOffset, 1 /* LONG */);
                    UFA.unsafeSetLong($frame, localIdx, (long) value);
                    return;
                }
            }
            doSetResultBoxed($bc, $bci, bciOffset, 0 /* OBJECT */);
        }
        UFA.unsafeByteArrayWrite(localTags, localIdx, (byte) 7 /* generic */);
        unsafeWriteBytecode($bc, $bci, (short) ((INSTR_STORE_LOCAL << 3) | 7 /* generic */));
        UFA.unsafeSetObject($frame, localIdx, value);
    }

    private static void do_storeLocal_OBJECT(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        int sourceSlot = $sp - 1;
        byte curKind = UFA.unsafeByteArrayRead(localTags, localIdx);
        CompilerDirectives.transferToInterpreterAndInvalidate();
        do_storeLocalSpecialize($this, $frame, $bc, $bci, $sp, localTags, 0 /* OBJECT */, localIdx, sourceSlot);
    }

    private static void do_storeLocal_BOOLEAN(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        int sourceSlot = $sp - 1;
        byte curKind = UFA.unsafeByteArrayRead(localTags, localIdx);
        if (curKind == 5 /* BOOLEAN */) {
            try {
                UFA.unsafeSetBoolean($frame, localIdx, UFA.unsafeGetBoolean($frame, sourceSlot));
                return;
            } catch (FrameSlotTypeException ex) {
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        do_storeLocalSpecialize($this, $frame, $bc, $bci, $sp, localTags, 5 /* BOOLEAN */, localIdx, sourceSlot);
    }

    private static void do_storeLocal_LONG(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        int sourceSlot = $sp - 1;
        byte curKind = UFA.unsafeByteArrayRead(localTags, localIdx);
        if (curKind == 1 /* LONG */) {
            try {
                UFA.unsafeSetLong($frame, localIdx, UFA.unsafeGetLong($frame, sourceSlot));
                return;
            } catch (FrameSlotTypeException ex) {
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        do_storeLocalSpecialize($this, $frame, $bc, $bci, $sp, localTags, 1 /* LONG */, localIdx, sourceSlot);
    }

    private static void do_storeLocal_null(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $bci, int $sp, byte[] localTags, int localIdx) {
        int sourceSlot = $sp - 1;
        byte curKind = UFA.unsafeByteArrayRead(localTags, localIdx);
        try {
            UFA.unsafeSetObject($frame, localIdx, UFA.unsafeGetObject($frame, sourceSlot));
        } catch (FrameSlotTypeException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            UFA.unsafeSetObject($frame, localIdx, $frame.getValue(sourceSlot));
        }
    }

    private static void SLEqual_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLEqual_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLEqual_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLEqual_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLLessOrEqual_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLLessOrEqual_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLLessOrEqual_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLLessOrEqual_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLLessThan_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLLessThan_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLLessThan_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLLessThan_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLLogicalNot_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLLogicalNot_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLLogicalNot_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        try {
            UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLLogicalNot_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLUnbox_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLUnbox_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLUnbox_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        try {
            UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLUnbox_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLUnbox_entryPoint_LONG(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        try {
            UFA.unsafeSetLong($frame, destSlot, BytecodeNode.SLUnbox_executeLong_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLFunctionLiteral_entryPoint_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLFunctionLiteral_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
    }

    private static void SLToBoolean_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLToBoolean_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLToBoolean_entryPoint_BOOLEAN(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetBoolean($frame, destSlot, BytecodeNode.SLToBoolean_executeBoolean_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLAdd_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLAdd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLAdd_entryPoint_LONG(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetLong($frame, destSlot, BytecodeNode.SLAdd_executeLong_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLDiv_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLDiv_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLDiv_entryPoint_LONG(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetLong($frame, destSlot, BytecodeNode.SLDiv_executeLong_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLMul_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLMul_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLMul_entryPoint_LONG(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetLong($frame, destSlot, BytecodeNode.SLMul_executeLong_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLReadProperty_entryPoint_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLReadProperty_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
    }

    private static void SLSub_entryPoint_OBJECT(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLSub_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
        return;
    }

    private static void SLSub_entryPoint_LONG(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        try {
            UFA.unsafeSetLong($frame, destSlot, BytecodeNode.SLSub_executeLong_($frame, $this, $bc, $bci, $sp, $consts, $children));
            return;
        } catch (UnexpectedResultException ex) {
            UFA.unsafeSetObject($frame, destSlot, ex.getResult());
        }
    }

    private static void SLWriteProperty_entryPoint_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 3;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLWriteProperty_execute_($frame, $this, $bc, $bci, $sp, $consts, $children));
    }

    private static void SLInvoke_entryPoint_(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics) {
        int destSlot = $sp - 1 - $numVariadics;
        UFA.unsafeSetObject($frame, destSlot, BytecodeNode.SLInvoke_execute_($frame, $this, $bc, $bci, $sp, $consts, $children, $numVariadics));
    }

    private static void SLEqual_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLEqual_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLLessOrEqual_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLLessOrEqual_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLLessThan_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLLessThan_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLLogicalNot_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLLogicalNot_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLUnbox_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLUnbox_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLFunctionLiteral_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLFunctionLiteral_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLToBoolean_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 1;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLToBoolean_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLAdd_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLAdd_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLDiv_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLDiv_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLMul_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLMul_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLReadProperty_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLReadProperty_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLSub_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 2;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLSub_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLWriteProperty_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
        int destSlot = $sp - 3;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLWriteProperty_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, UFA.unsafeUncheckedGetObject($frame, $sp - 3), UFA.unsafeUncheckedGetObject($frame, $sp - 2), UFA.unsafeUncheckedGetObject($frame, $sp - 1)));
    }

    private static void SLInvoke_entryPoint_uncached(VirtualFrame $frame, SLOperationRootNodeGen $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, int $numVariadics) {
        int destSlot = $sp - 1 - $numVariadics;
        UFA.unsafeSetObject($frame, destSlot, UncachedBytecodeNode.SLInvoke_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $numVariadics, UFA.unsafeUncheckedGetObject($frame, $sp - 1 - $numVariadics), do_loadVariadicArguments($frame, $sp, $numVariadics)));
    }

    private static void doSetResultBoxed(short[] bc, int startBci, int bciOffset, int targetType) {
        if (bciOffset != 0) {
            setResultBoxedImpl(bc, startBci - bciOffset, targetType);
        }
    }

    @ExplodeLoop
    private static Object[] do_loadVariadicArguments(VirtualFrame $frame, int $sp, int numVariadics) {
        CompilerAsserts.partialEvaluationConstant($sp);
        CompilerAsserts.partialEvaluationConstant(numVariadics);
        Object[] result = new Object[numVariadics];
        for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
            result[varIndex] = UFA.unsafeUncheckedGetObject($frame, $sp - numVariadics + varIndex);
        }
        return result;
    }

    protected static void setResultBoxedImpl(short[] bc, int bci, int targetType) {
        bc[bci] = (short) (targetType | (bc[bci] & 0xfff8));
    }

    private static short unsafeFromBytecode(short[] bc, int index) {
        return UFA.unsafeShortArrayRead(bc, index);
    }

    private static void unsafeWriteBytecode(short[] bc, int index, short value) {
        UFA.unsafeShortArrayWrite(bc, index, value);
    }

    private static boolean do_profileCondition(SLOperationRootNodeGen $this, boolean value, int[] profiles, int index) {
        int t = UFA.unsafeIntArrayRead(profiles, index);
        int f = UFA.unsafeIntArrayRead(profiles, index + 1);
        boolean val = value;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < 1073741823) {
                    UFA.unsafeIntArrayWrite(profiles, index, t + 1);
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < 1073741823) {
                    UFA.unsafeIntArrayWrite(profiles, index + 1, f + 1);
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

    public static OperationNodes<SLOperationRootNode> create(OperationConfig config, OperationParser<com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder> generator) {
        OperationNodesImpl nodes = new OperationNodesImpl(generator);
        Builder builder = new com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder(nodes, false, config);
        generator.parse(builder);
        builder.finish();
        return nodes;
    }

    public static OperationNodes<SLOperationRootNode> deserialize(TruffleLanguage<?> language, OperationConfig config, ByteBuffer input, OperationDeserializer callback) throws IOException {
        try {
            return create(config, b -> Builder.deserializeParser(language, input, callback, b));
        } catch (IOError ex) {
            throw (IOException) ex.getCause();
        }
    }

    public static void serialize(OperationConfig config, DataOutput buffer, OperationSerializer callback, OperationParser<com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder> generator) throws IOException {
        Builder builder = new com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder(null, false, config);
        builder.isSerializing = true;
        builder.serBuffer = buffer;
        builder.serCallback = callback;
        try {
            generator.parse(builder);
        } catch (IOError ex) {
            throw (IOException) ex.getCause();
        }
        buffer.writeShort((short) -5);
    }

    @GeneratedBy(SLOperationRootNode.class)
    private static final class ExceptionHandler {

        int startBci;
        int startStack;
        int endBci;
        int exceptionIndex;
        int handlerBci;

        ExceptionHandler(int startBci, int startStack, int endBci, int exceptionIndex, int handlerBci) {
            this.startBci = startBci;
            this.startStack = startStack;
            this.endBci = endBci;
            this.exceptionIndex = exceptionIndex;
            this.handlerBci = handlerBci;
        }

        ExceptionHandler() {
        }

        ExceptionHandler offset(int offset, int stackOffset) {
            return new ExceptionHandler(startBci + offset, startStack + stackOffset, endBci + offset, exceptionIndex, handlerBci + offset);
        }

        @Override
        public String toString() {
            return String.format("handler {start=%04x, end=%04x, stack=%d, local=%d, handler=%04x}", startBci, endBci, startStack, exceptionIndex, handlerBci);
        }

    }
    @GeneratedBy(SLOperationRootNode.class)
    private abstract static class BytecodeLoopBase {

        abstract int continueAt(SLOperationRootNodeGen $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, byte[] $localTags, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals);

        abstract OperationIntrospection getIntrospectionData(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts, OperationNodesImpl nodes, int[] sourceInfo);

        abstract void prepareForAOT(SLOperationRootNodeGen $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root);

        protected static String formatConstant(Object obj) {
            if (obj == null) {
                return "null";
            } else {
                Object repr = obj;
                if (obj instanceof Object[]) {
                    repr = Arrays.deepToString((Object[]) obj);
                }
                return String.format("%s %s", obj.getClass().getSimpleName(), repr);
            }
        }

        protected static Object expectObject(VirtualFrame frame, int slot, short[] bc, int bci, int bciOffset) {
            if (bciOffset == 0 || UFA.unsafeIsObject(frame, slot)) {
                return UFA.unsafeUncheckedGetObject(frame, slot);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setResultBoxedImpl(bc, bci - bciOffset, 0);
                return frame.getValue(slot);
            }
        }

        protected static void setResultBoxedImpl(short[] bc, int bci, int targetType) {
            bc[bci] = (short) (targetType | (bc[bci] & 0xfff8));
        }

        protected static long expectLong(VirtualFrame frame, int slot, short[] bc, int bci, int bciOffset) throws UnexpectedResultException {
            if (bciOffset == 0) {
                Object value = UFA.unsafeUncheckedGetObject(frame, slot);
                if (value instanceof Long) {
                    return (long) value;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnexpectedResultException(value);
                }
            } else {
                switch (UFA.unsafeGetTag(frame, slot)) {
                    case 1 /* LONG */ :
                        return UFA.unsafeUncheckedGetLong(frame, slot);
                    case 0 /* OBJECT */ :
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Object value = UFA.unsafeUncheckedGetObject(frame, slot);
                        if (value instanceof Long) {
                            setResultBoxedImpl(bc, bci - bciOffset, 1 /* LONG */);
                            return (long) value;
                        }
                        break;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setResultBoxedImpl(bc, bci - bciOffset, 0);
                throw new UnexpectedResultException(frame.getValue(slot));
            }
        }

        protected static boolean expectBoolean(VirtualFrame frame, int slot, short[] bc, int bci, int bciOffset) throws UnexpectedResultException {
            if (bciOffset == 0) {
                Object value = UFA.unsafeUncheckedGetObject(frame, slot);
                if (value instanceof Boolean) {
                    return (boolean) value;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnexpectedResultException(value);
                }
            } else {
                switch (UFA.unsafeGetTag(frame, slot)) {
                    case 5 /* BOOLEAN */ :
                        return UFA.unsafeUncheckedGetBoolean(frame, slot);
                    case 0 /* OBJECT */ :
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        Object value = UFA.unsafeUncheckedGetObject(frame, slot);
                        if (value instanceof Boolean) {
                            setResultBoxedImpl(bc, bci - bciOffset, 5 /* BOOLEAN */);
                            return (boolean) value;
                        }
                        break;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setResultBoxedImpl(bc, bci - bciOffset, 0);
                throw new UnexpectedResultException(frame.getValue(slot));
            }
        }

    }
    @GeneratedBy(SLOperationRootNode.class)
    private static final class OperationNodesImpl extends OperationNodes<SLOperationRootNode> {

        OperationNodesImpl(OperationParser<? extends OperationBuilder> parse) {
            super(parse);
        }

        @Override
        protected void reparseImpl(OperationConfig config, OperationParser<? extends OperationBuilder> parse, OperationRootNode[] nodes) {
            Builder builder = new Builder(this, true, config);
            ((OperationParser) parse).parse(builder);
            builder.finish();
        }

        void setSources(Source[] sources) {
            this.sources = sources;
        }

        Source[] getSources() {
            return sources;
        }

        void setNodes(SLOperationRootNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        public void serialize(OperationConfig config, DataOutputStream buffer, OperationSerializer callback) throws IOException {
            Builder builder = new Builder(null, false, config);
            builder.isSerializing = true;
            builder.serBuffer = buffer;
            builder.serCallback = callback;
            try {
                ((OperationParser) parse).parse(builder);
            } catch (IOError ex) {
                throw (IOException) ex.getCause();
            }
            buffer.writeShort((short) -5);
        }

    }
    @GeneratedBy(SLOperationRootNode.class)
    public static final class Builder extends OperationBuilder {

        private static final int OP_BLOCK = 1;
        private static final int OP_ROOT = 2;
        private static final int OP_IF_THEN = 3;
        private static final int OP_IF_THEN_ELSE = 4;
        private static final int OP_CONDITIONAL = 5;
        private static final int OP_WHILE = 6;
        private static final int OP_TRY_CATCH = 7;
        private static final int OP_FINALLY_TRY = 8;
        private static final int OP_FINALLY_TRY_NO_EXCEPT = 9;
        private static final int OP_LABEL = 10;
        private static final int OP_BRANCH = 11;
        private static final int OP_LOAD_CONSTANT = 12;
        private static final int OP_LOAD_ARGUMENT = 13;
        private static final int OP_LOAD_LOCAL = 14;
        private static final int OP_STORE_LOCAL = 15;
        private static final int OP_RETURN = 16;
        private static final int OP_LOAD_LOCAL_MATERIALIZED = 17;
        private static final int OP_STORE_LOCAL_MATERIALIZED = 18;
        private static final int OP_SOURCE = 19;
        private static final int OP_SOURCE_SECTION = 20;
        private static final int OP_TAG = 21;
        private static final int OP_SL_ADD = 22;
        private static final int OP_SL_DIV = 23;
        private static final int OP_SL_EQUAL = 24;
        private static final int OP_SL_LESS_OR_EQUAL = 25;
        private static final int OP_SL_LESS_THAN = 26;
        private static final int OP_SL_LOGICAL_NOT = 27;
        private static final int OP_SL_MUL = 28;
        private static final int OP_SL_READ_PROPERTY = 29;
        private static final int OP_SL_SUB = 30;
        private static final int OP_SL_WRITE_PROPERTY = 31;
        private static final int OP_SL_UNBOX = 32;
        private static final int OP_SL_FUNCTION_LITERAL = 33;
        private static final int OP_SL_TO_BOOLEAN = 34;
        private static final int OP_SL_INVOKE = 35;
        private static final int OP_SL_AND = 36;
        private static final int OP_SL_OR = 37;

        private final com.oracle.truffle.sl.operations.SLOperationRootNodeGen.OperationNodesImpl nodes;
        private final boolean isReparse;
        private final boolean withSource;
        private final boolean withInstrumentation;
        private final SourceInfoBuilder sourceBuilder;
        private short[] bc = new short[65535];
        private int bci;
        private int curStack;
        private int maxStack;
        private int numLocals;
        private int[] instructionHistory = new int[8];
        private int instructionHistoryIndex = 0;
        private int numLabels;
        private boolean[] isBbStart = new boolean[65535];
        private ArrayList<Object> constPool = new ArrayList<>();
        private BuilderOperationData operationData;
        private ArrayList<OperationLabelImpl> labels = new ArrayList<>();
        private ArrayList<LabelFill> labelFills = new ArrayList<>();
        private int numChildNodes;
        private int numConditionProfiles;
        private ArrayList<ExceptionHandler> exceptionHandlers = new ArrayList<>();
        private BuilderFinallyTryContext currentFinallyTry;
        private int buildIndex;
        private boolean isSerializing;
        private DataOutput serBuffer;
        private OperationSerializer serCallback;
        private int[] stackSourceBci = new int[1024];
        private BuilderState parentData;
        private SerializerContext SER_CONTEXT = new SerializerContext() {
            @Override
            public void serializeOperationNode(DataOutput buffer, OperationRootNode node) throws IOException {
                buffer.writeShort((short) ((OperationSerNodeImpl) node).buildOrder);
            }
        }
        ;
        private final ArrayList<SLOperationRootNodeGen> builtNodes;
        private int lastChildPush;
        private TruffleString metadata_MethodName;

        static  {
            ExecutionTracer.initialize(SLOperationRootNode.class, "/home/prof/ec/ws1/graal/truffle/src/com.oracle.truffle.sl/src/com/oracle/truffle/sl/operations/decisions.json", new String[] {null, "pop", "branch", "branch.false", "throw", "load.constant", "load.argument", "load.local", "load.local.boxed", "store.local", "return", "load.local.mat", "store.local.mat", "instrument.enter", "instrument.exit.void", "instrument.exit", "instrument.leave", "c.SLAdd", "c.SLDiv", "c.SLEqual", "c.SLLessOrEqual", "c.SLLessThan", "c.SLLogicalNot", "c.SLMul", "c.SLReadProperty", "c.SLSub", "c.SLWriteProperty", "c.SLUnbox", "c.SLFunctionLiteral", "c.SLToBoolean", "c.SLInvoke", "sc.SLAnd", "sc.SLOr"}, new String[][] {null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, new String[] {"AddLong", "Add0", "Add1", "Fallback"}, new String[] {"DivLong", "Div", "Fallback"}, new String[] {"Long", "BigNumber", "Boolean", "String", "TruffleString", "Null", "Function", "Generic0", "Generic1", "Fallback"}, new String[] {"LessOrEqual0", "LessOrEqual1", "Fallback"}, new String[] {"LessThan0", "LessThan1", "Fallback"}, new String[] {"Boolean", "Fallback"}, new String[] {"MulLong", "Mul", "Fallback"}, new String[] {"ReadArray0", "ReadArray1", "ReadSLObject0", "ReadSLObject1", "ReadObject0", "ReadObject1", "Fallback"}, new String[] {"SubLong", "Sub", "Fallback"}, new String[] {"WriteArray0", "WriteArray1", "WriteSLObject0", "WriteSLObject1", "WriteObject0", "WriteObject1", "Fallback"}, new String[] {"FromString", "FromTruffleString", "FromBoolean", "FromLong", "FromBigNumber", "FromFunction0", "FromFunction1", "FromForeign0", "FromForeign1", "Fallback"}, new String[] {"Perform", "Fallback"}, new String[] {"Boolean", "Fallback"}, new String[] {"Direct", "Indirect", "Interop", "Fallback"}, new String[] {"Boolean", "Fallback"}, new String[] {"Boolean", "Fallback"}});
        }

        private Builder(com.oracle.truffle.sl.operations.SLOperationRootNodeGen.OperationNodesImpl nodes, boolean isReparse, OperationConfig config) {
            this.nodes = nodes;
            this.isReparse = isReparse;
            builtNodes = new ArrayList<>();
            if (isReparse) {
                builtNodes.addAll((java.util.Collection) nodes.getNodes());
            }
            this.withSource = config.isWithSource();
            this.withInstrumentation = config.isWithInstrumentation();
            this.sourceBuilder = withSource ? new SourceInfoBuilder() : null;
        }

        private void finish() {
            if (withSource) {
                nodes.setSources(sourceBuilder.buildSource());
            }
            if (!isReparse) {
                nodes.setNodes(builtNodes.toArray(new SLOperationRootNode[0]));
            }
        }

        private void reset(TruffleLanguage<?> language) {
            bci = 0;
            curStack = 0;
            maxStack = 0;
            numLocals = 0;
            constPool.clear();
            operationData = new BuilderOperationData(null, OP_ROOT, 0, 0, false, language);
            labelFills.clear();
            numChildNodes = 0;
            numConditionProfiles = 0;
            exceptionHandlers.clear();
            Arrays.fill(instructionHistory, -1);
            instructionHistoryIndex = 0;
            if (sourceBuilder != null) {
                sourceBuilder.reset();
            }
            metadata_MethodName = null;
        }

        private int[] doBeforeEmitInstruction(int numPops, boolean pushValue, boolean doBoxing) {
            int[] result = new int[numPops];
            for (int i = numPops - 1; i >= 0; i--) {
                curStack--;
                int predBci = stackSourceBci[curStack];
                result[i] = predBci;
            }
            if (pushValue) {
                if (curStack >= stackSourceBci.length) {
                    stackSourceBci = Arrays.copyOf(stackSourceBci, stackSourceBci.length * 2);
                }
                stackSourceBci[curStack] = doBoxing ? bci : -65535;
                curStack++;
                if (curStack > maxStack) {
                    maxStack = curStack;
                }
            }
            return result;
        }

        private void doLeaveFinallyTry(BuilderOperationData opData) {
            BuilderFinallyTryContext context = (BuilderFinallyTryContext) opData.aux[0];
            if (context.handlerBc == null) {
                return;
            }
            System.arraycopy(context.handlerBc, 0, bc, bci, context.handlerBc.length);
            for (int offset : context.relocationOffsets) {
                short oldOffset = bc[bci + offset];
                bc[bci + offset] = (short) (oldOffset + bci);
            }
            for (ExceptionHandler handler : context.handlerHandlers) {
                exceptionHandlers.add(handler.offset(bci, curStack));
            }
            for (LabelFill fill : context.handlerLabelFills) {
                labelFills.add(fill.offset(bci));
            }
            if (maxStack < curStack + context.handlerMaxStack) maxStack = curStack + context.handlerMaxStack;
            bci += context.handlerBc.length;
        }

        private void doEmitLabel(OperationLabel label) {
            OperationLabelImpl lbl = (OperationLabelImpl) label;
            if (lbl.hasValue) {
                throw new UnsupportedOperationException("label already emitted");
            }
            if (operationData != lbl.data) {
                throw new UnsupportedOperationException("label must be created and emitted inside same opeartion");
            }
            lbl.hasValue = true;
            lbl.targetBci = bci;
            isBbStart[bci] = true;
        }

        private void calculateLeaves(BuilderOperationData fromData, BuilderOperationData toData) {
            if (toData != null && fromData.depth < toData.depth) {
                throw new UnsupportedOperationException("illegal jump to deeper operation");
            }
            if (fromData == toData) {
                return;
            }
            BuilderOperationData cur = fromData;
            while (true) {
                doLeaveOperation(cur);
                cur = cur.parent;
                if (toData == null && cur == null) {
                    break;
                } else if (toData != null && cur.depth <= toData.depth) break;
            }
            if (cur != toData) throw new UnsupportedOperationException("illegal jump to non-parent operation");
        }

        private void calculateLeaves(BuilderOperationData fromData) {
            calculateLeaves(fromData, (BuilderOperationData) null);
        }

        private void calculateLeaves(BuilderOperationData fromData, Object toLabel) {
            calculateLeaves(fromData, ((OperationLabelImpl) toLabel).data);
        }

        public OperationLocal createLocal() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -3);
                    return new OperationLocalImpl(null, numLocals++);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
            }
            return new OperationLocalImpl(operationData, numLocals++);
        }

        private OperationLocalImpl createParentLocal() {
            return new OperationLocalImpl(operationData.parent, numLocals++);
        }

        public OperationLabel createLabel() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -2);
                    return new OperationSerLabelImpl(numLabels++);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
            }
            OperationLabelImpl label = new OperationLabelImpl(operationData, currentFinallyTry);
            labels.add(label);
            return label;
        }

        private short getLocalIndex(Object value) {
            OperationLocalImpl local = (OperationLocalImpl) value;
            assert verifyNesting(local.owner, operationData) : "local access not nested properly";
            return (short) local.id;
        }

        private int[] getLocalIndices(Object value) {
            OperationLocal[] locals = (OperationLocal[]) value;
            int[] result = new int[locals.length];
            for (int i = 0; i < locals.length; i++) {
                result[i] = getLocalIndex(locals[i]);
            }
            return result;
        }

        private boolean verifyNesting(BuilderOperationData parent, BuilderOperationData child) {
            BuilderOperationData cur = child;
            while (cur.depth > parent.depth) {
                cur = cur.parent;
            }
            return cur == parent;
        }

        private SLOperationRootNode publish(TruffleLanguage<?> language) {
            if (isSerializing) {
                SLOperationRootNode result = new OperationSerNodeImpl(null, FrameDescriptor.newBuilder().build(), buildIndex++);
                numLocals = 0;
                numLabels = 0;
                return result;
            }
            if (operationData.depth != 0) {
                throw new UnsupportedOperationException("Not all operations closed");
            }
            SLOperationRootNodeGen result;
            if (!isReparse) {
                FrameDescriptor .Builder frameDescriptor;
                frameDescriptor = FrameDescriptor.newBuilder(numLocals + maxStack);
                frameDescriptor.addSlots(numLocals + maxStack, FrameSlotKind.Illegal);
                result = new SLOperationRootNodeGen(language, frameDescriptor);
                result.nodes = nodes;
                labelPass(null);
                result._bc = Arrays.copyOf(bc, bci);
                result._consts = constPool.toArray();
                result._children = new Node[numChildNodes];
                result._localTags = new byte[numLocals];
                result.isBbStart = Arrays.copyOf(isBbStart, bci);
                result._handlers = exceptionHandlers.toArray(new ExceptionHandler[0]);
                result._conditionProfiles = new int[numConditionProfiles];
                result._maxLocals = numLocals;
                result._maxStack = maxStack;
                if (sourceBuilder != null) {
                    result.sourceInfo = sourceBuilder.build();
                }
                result._metadata_MethodName = metadata_MethodName;
                assert builtNodes.size() == buildIndex;
                builtNodes.add(result);
            } else {
                result = builtNodes.get(buildIndex);
                if (withSource && result.sourceInfo == null) {
                    result.sourceInfo = sourceBuilder.build();
                }
            }
            buildIndex++;
            reset(language);
            return result;
        }

        private void labelPass(BuilderFinallyTryContext finallyTry) {
            for (LabelFill fill : labelFills) {
                if (finallyTry != null) {
                    if (fill.label.belongsTo(finallyTry)) {
                        assert fill.label.hasValue : "inner label should have been resolved by now";
                        finallyTry.relocationOffsets.add(fill.locationBci);
                    } else {
                        finallyTry.handlerLabelFills.add(fill);
                    }
                }
                bc[fill.locationBci] = (short) fill.label.targetBci;
            }
        }

        private void doLeaveOperation(BuilderOperationData data) {
            switch (data.operationId) {
                case OP_FINALLY_TRY :
                {
                    doLeaveFinallyTry(data);
                    break;
                }
                case OP_FINALLY_TRY_NO_EXCEPT :
                {
                    doLeaveFinallyTry(data);
                    break;
                }
                case OP_TAG :
                {
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        private void doBeforeChild() {
            int childIndex = operationData.numChildren;
            switch (operationData.operationId) {
                case OP_BLOCK :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                    }
                    break;
                }
                case OP_ROOT :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    if (childIndex == 1) {
                        curStack = ((ExceptionHandler) operationData.aux[0]).startStack;
                        ((ExceptionHandler) operationData.aux[0]).handlerBci = bci;
                    }
                    break;
                }
                case OP_SOURCE :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                    }
                    break;
                }
                case OP_SOURCE_SECTION :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                    }
                    break;
                }
                case OP_TAG :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                    }
                    break;
                }
                case OP_SL_AND :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_SC_SL_AND << 3) | 0));
                        int constantsStart = constPool.size();
                        bc[bci + 1] = (short) constantsStart;
                        constPool.add(null);
                        bc[bci + 2] = (short) numChildNodes;
                        numChildNodes += 1;
                        bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
                        labelFills.add(new LabelFill(bci + 4 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        bc[bci + 5 + 0] = 0;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_SC_SL_AND;
                        bci = bci + SC_SL_AND_LENGTH;
                    }
                    break;
                }
                case OP_SL_OR :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_SC_SL_OR << 3) | 0));
                        int constantsStart = constPool.size();
                        bc[bci + 1] = (short) constantsStart;
                        constPool.add(null);
                        bc[bci + 2] = (short) numChildNodes;
                        numChildNodes += 1;
                        bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
                        labelFills.add(new LabelFill(bci + 4 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        bc[bci + 5 + 0] = 0;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_SC_SL_OR;
                        bci = bci + SC_SL_OR_LENGTH;
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        private void doAfterChild() {
            int childIndex = operationData.numChildren++;
            switch (operationData.operationId) {
                case OP_IF_THEN :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[0] = endLabel;
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH_FALSE << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH_FALSE;
                        bci = bci + BRANCH_FALSE_LENGTH;
                        isBbStart[bci] = true;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
                    }
                    break;
                }
                case OP_IF_THEN_ELSE :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        OperationLabelImpl elseLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[0] = elseLabel;
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH_FALSE << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, elseLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH_FALSE;
                        bci = bci + BRANCH_FALSE_LENGTH;
                        isBbStart[bci] = true;
                    } else if (childIndex == 1) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, endLabel);
                        doBeforeEmitInstruction(0, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
                        bci = bci + BRANCH_LENGTH;
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
                    }
                    break;
                }
                case OP_CONDITIONAL :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        OperationLabelImpl elseLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[0] = elseLabel;
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH_FALSE << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, elseLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH_FALSE;
                        bci = bci + BRANCH_FALSE_LENGTH;
                        isBbStart[bci] = true;
                    } else if (childIndex == 1) {
                        assert lastChildPush == 1;
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, endLabel);
                        doBeforeEmitInstruction(0, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
                        bci = bci + BRANCH_LENGTH;
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
                    } else {
                        assert lastChildPush == 1;
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
                    }
                    break;
                }
                case OP_WHILE :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[1] = endLabel;
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH_FALSE << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH_FALSE;
                        bci = bci + BRANCH_FALSE_LENGTH;
                        isBbStart[bci] = true;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false, false);
                            unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                            instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                            bci = bci + POP_LENGTH;
                        }
                        calculateLeaves(operationData, ((OperationLabelImpl) operationData.aux[0]));
                        doBeforeEmitInstruction(0, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
                        bci = bci + BRANCH_LENGTH;
                        isBbStart[bci] = true;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                        bci = bci + POP_LENGTH;
                    }
                    if (childIndex == 0) {
                        ((ExceptionHandler) operationData.aux[0]).endBci = bci;
                        calculateLeaves(operationData, ((OperationLabelImpl) operationData.aux[1]));
                        doBeforeEmitInstruction(0, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
                        labelFills.add(new LabelFill(bci + 1 + 0, ((OperationLabelImpl) operationData.aux[1])));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
                        bci = bci + BRANCH_LENGTH;
                        isBbStart[bci] = true;
                    } else {
                    }
                    break;
                }
                case OP_FINALLY_TRY :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                        bci = bci + POP_LENGTH;
                    }
                    if (childIndex == 0) {
                        labelPass(currentFinallyTry);
                        currentFinallyTry.handlerBc = Arrays.copyOf(bc, bci);
                        currentFinallyTry.handlerHandlers = exceptionHandlers;
                        currentFinallyTry.handlerMaxStack = maxStack;
                        System.arraycopy(currentFinallyTry.bc, 0, bc, 0, currentFinallyTry.bc.length);
                        bci = currentFinallyTry.bc.length;
                        exceptionHandlers = currentFinallyTry.exceptionHandlers;
                        labelFills = currentFinallyTry.labelFills;
                        labels = currentFinallyTry.labels;
                        curStack = currentFinallyTry.curStack;
                        maxStack = currentFinallyTry.maxStack;
                        currentFinallyTry = currentFinallyTry.prev;
                        ExceptionHandler beh = new ExceptionHandler();
                        beh.startBci = bci;
                        beh.startStack = curStack;
                        beh.exceptionIndex = getLocalIndex(operationData.aux[2]);
                        exceptionHandlers.add(beh);
                        operationData.aux[1] = beh;
                    }
                    break;
                }
                case OP_FINALLY_TRY_NO_EXCEPT :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        doBeforeEmitInstruction(1, false, false);
                        unsafeWriteBytecode(bc, bci, (short) ((INSTR_POP << 3) | 0));
                        instructionHistory[++instructionHistoryIndex % 8] = INSTR_POP;
                        bci = bci + POP_LENGTH;
                    }
                    if (childIndex == 0) {
                        labelPass(currentFinallyTry);
                        currentFinallyTry.handlerBc = Arrays.copyOf(bc, bci);
                        currentFinallyTry.handlerHandlers = exceptionHandlers;
                        currentFinallyTry.handlerMaxStack = maxStack;
                        System.arraycopy(currentFinallyTry.bc, 0, bc, 0, currentFinallyTry.bc.length);
                        bci = currentFinallyTry.bc.length;
                        exceptionHandlers = currentFinallyTry.exceptionHandlers;
                        labelFills = currentFinallyTry.labelFills;
                        labels = currentFinallyTry.labels;
                        curStack = currentFinallyTry.curStack;
                        maxStack = currentFinallyTry.maxStack;
                        currentFinallyTry = currentFinallyTry.prev;
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        public void beginBlock() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_BLOCK << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BLOCK, curStack, 0, false);
            lastChildPush = 0;
        }

        @SuppressWarnings("unused")
        public void endBlock() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_BLOCK << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_BLOCK) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("Block expected at least 0 children, got " + numChildren);
            }
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginRoot(TruffleLanguage<?> arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_ROOT << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            lastChildPush = 0;
            this.parentData = new BuilderState(this);
            this.bc = new short[65535];
            this.constPool = new ArrayList<>();
            this.labels = new ArrayList<>();
            this.labelFills = new ArrayList<>();
            this.exceptionHandlers = new ArrayList<>();
            this.stackSourceBci = new int[1024];
            reset(arg0);
        }

        @SuppressWarnings("unused")
        public SLOperationRootNode endRoot() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_ROOT << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return publish(null);
            }
            if (operationData.operationId != OP_ROOT) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("Root expected at least 0 children, got " + numChildren);
            }
            lastChildPush = 0;
            SLOperationRootNode endCodeResult;
            endCodeResult = publish((TruffleLanguage<?>) operationData.arguments[0]);
            this.bc = parentData.bc;
            this.bci = parentData.bci;
            this.curStack = parentData.curStack;
            this.maxStack = parentData.maxStack;
            this.numLocals = parentData.numLocals;
            this.numLabels = parentData.numLabels;
            this.constPool = parentData.constPool;
            this.operationData = parentData.operationData;
            this.labels = parentData.labels;
            this.labelFills = parentData.labelFills;
            this.numChildNodes = parentData.numChildNodes;
            this.numConditionProfiles = parentData.numConditionProfiles;
            this.exceptionHandlers = parentData.exceptionHandlers;
            this.currentFinallyTry = parentData.currentFinallyTry;
            this.stackSourceBci = parentData.stackSourceBci;
            this.parentData = parentData.parentData;
            return endCodeResult;
        }

        @SuppressWarnings("unused")
        public void beginIfThen() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_IF_THEN << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN, curStack, 1, false);
        }

        @SuppressWarnings("unused")
        public void endIfThen() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_IF_THEN << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_IF_THEN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("IfThen expected 2 children, got " + numChildren);
            }
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginIfThenElse() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_IF_THEN_ELSE << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN_ELSE, curStack, 2, false);
        }

        @SuppressWarnings("unused")
        public void endIfThenElse() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_IF_THEN_ELSE << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_IF_THEN_ELSE) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 3) {
                throw new IllegalStateException("IfThenElse expected 3 children, got " + numChildren);
            }
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginConditional() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_CONDITIONAL << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_CONDITIONAL, curStack, 2, false);
        }

        @SuppressWarnings("unused")
        public void endConditional() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_CONDITIONAL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_CONDITIONAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 3) {
                throw new IllegalStateException("Conditional expected 3 children, got " + numChildren);
            }
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginWhile() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_WHILE << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_WHILE, curStack, 2, false);
            OperationLabelImpl startLabel = (OperationLabelImpl) createLabel();
            isBbStart[bci] = true;
            doEmitLabel(startLabel);
            operationData.aux[0] = startLabel;
        }

        @SuppressWarnings("unused")
        public void endWhile() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_WHILE << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_WHILE) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("While expected 2 children, got " + numChildren);
            }
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginTryCatch(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_TRY_CATCH << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_TRY_CATCH, curStack, 2, false, (Object) arg0);
            ExceptionHandler beh = new ExceptionHandler();
            beh.startBci = bci;
            beh.startStack = curStack;
            beh.exceptionIndex = getLocalIndex(operationData.arguments[0]);
            exceptionHandlers.add(beh);
            operationData.aux[0] = beh;
            OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
            operationData.aux[1] = endLabel;
        }

        @SuppressWarnings("unused")
        public void endTryCatch() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_TRY_CATCH << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_TRY_CATCH) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("TryCatch expected 2 children, got " + numChildren);
            }
            isBbStart[bci] = true;
            doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginFinallyTry() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_FINALLY_TRY << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_FINALLY_TRY, curStack, 3, true);
            operationData.aux[2] = createParentLocal();
            currentFinallyTry = new BuilderFinallyTryContext(currentFinallyTry, Arrays.copyOf(bc, bci), exceptionHandlers, labelFills, labels, curStack, maxStack);
            bci = 0;
            exceptionHandlers = new ArrayList<>();
            labelFills = new ArrayList<>();
            labels = new ArrayList<>();
            curStack = 0;
            maxStack = 0;
            operationData.aux[0] = currentFinallyTry;
        }

        @SuppressWarnings("unused")
        public void endFinallyTry() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_FINALLY_TRY << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_FINALLY_TRY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("FinallyTry expected 2 children, got " + numChildren);
            }
            int endBci = bci;
            doLeaveFinallyTry(operationData);
            OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
            {
                calculateLeaves(operationData, endLabel);
                doBeforeEmitInstruction(0, false, false);
                unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
                labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
                bci = bci + BRANCH_LENGTH;
                isBbStart[bci] = true;
            }
            ExceptionHandler beh = ((ExceptionHandler) operationData.aux[1]);
            beh.endBci = endBci;
            beh.handlerBci = bci;
            doLeaveFinallyTry(operationData);
            {
                doBeforeEmitInstruction(0, false, false);
                unsafeWriteBytecode(bc, bci, (short) ((INSTR_THROW << 3) | 0));
                bc[bci + 1 + 0] = (short) getLocalIndex(operationData.aux[2]);
                instructionHistory[++instructionHistoryIndex % 8] = INSTR_THROW;
                bci = bci + THROW_LENGTH;
            }
            doEmitLabel(endLabel);
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginFinallyTryNoExcept() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_FINALLY_TRY_NO_EXCEPT << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_FINALLY_TRY_NO_EXCEPT, curStack, 1, true);
            currentFinallyTry = new BuilderFinallyTryContext(currentFinallyTry, Arrays.copyOf(bc, bci), exceptionHandlers, labelFills, labels, curStack, maxStack);
            bci = 0;
            exceptionHandlers = new ArrayList<>();
            labelFills = new ArrayList<>();
            labels = new ArrayList<>();
            curStack = 0;
            maxStack = 0;
            operationData.aux[0] = currentFinallyTry;
        }

        @SuppressWarnings("unused")
        public void endFinallyTryNoExcept() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_FINALLY_TRY_NO_EXCEPT << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_FINALLY_TRY_NO_EXCEPT) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("FinallyTryNoExcept expected 2 children, got " + numChildren);
            }
            doLeaveFinallyTry(operationData);
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        public void emitLabel(OperationLabel arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LABEL << 1));
                    serBuffer.writeShort((short) ((OperationSerLabelImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            isBbStart[bci] = true;
            doEmitLabel(arg0);
            lastChildPush = 0;
            doAfterChild();
        }

        public void emitBranch(OperationLabel arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_BRANCH << 1));
                    serBuffer.writeShort((short) ((OperationSerLabelImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BRANCH, curStack, 0, false, arg0);
            calculateLeaves(operationData, (OperationLabelImpl) operationData.arguments[0]);
            doBeforeEmitInstruction(0, false, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_BRANCH << 3) | 0));
            labelFills.add(new LabelFill(bci + 1 + 0, (OperationLabelImpl) operationData.arguments[0]));
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_BRANCH;
            bci = bci + BRANCH_LENGTH;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        public void emitLoadConstant(Object arg0) {
            if (isSerializing) {
                try {
                    int arg0_index = constPool.indexOf(arg0);
                    if (arg0_index == -1) {
                        arg0_index = constPool.size();
                        constPool.add(arg0);
                        serBuffer.writeShort((short) -4);
                        serCallback.serialize(SER_CONTEXT, serBuffer, arg0);
                    }
                    serBuffer.writeShort((short) (OP_LOAD_CONSTANT << 1));
                    serBuffer.writeShort((short) arg0_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_CONSTANT, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_LOAD_CONSTANT << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(operationData.arguments[0]);
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_LOAD_CONSTANT;
            bci = bci + LOAD_CONSTANT_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        public void emitLoadArgument(int arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LOAD_ARGUMENT << 1));
                    serBuffer.writeInt(arg0);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_ARGUMENT, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_LOAD_ARGUMENT << 3) | 0));
            bc[bci + 1 + 0] = (short) (int) operationData.arguments[0];
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_LOAD_ARGUMENT;
            bci = bci + LOAD_ARGUMENT_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        public void emitLoadLocal(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LOAD_LOCAL << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_LOCAL, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_LOAD_LOCAL << 3) | 0));
            bc[bci + 1 + 0] = (short) getLocalIndex(operationData.arguments[0]);
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_LOAD_LOCAL;
            bci = bci + LOAD_LOCAL_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginStoreLocal(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_STORE_LOCAL << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_STORE_LOCAL, curStack, 0, false, (Object) arg0);
        }

        @SuppressWarnings("unused")
        public void endStoreLocal() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_STORE_LOCAL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_STORE_LOCAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("StoreLocal expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, false, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_STORE_LOCAL << 3) | 0));
            bc[bci + 1 + 0] = (short) getLocalIndex(operationData.arguments[0]);
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_STORE_LOCAL;
            bci = bci + STORE_LOCAL_LENGTH;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginReturn() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_RETURN << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_RETURN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endReturn() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_RETURN << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_RETURN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("Return expected 1 children, got " + numChildren);
            }
            calculateLeaves(operationData);
            doBeforeEmitInstruction(1, false, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_RETURN << 3) | 0));
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_RETURN;
            bci = bci + RETURN_LENGTH;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginLoadLocalMaterialized(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LOAD_LOCAL_MATERIALIZED << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_LOCAL_MATERIALIZED, curStack, 0, false, (Object) arg0);
        }

        @SuppressWarnings("unused")
        public void endLoadLocalMaterialized() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_LOAD_LOCAL_MATERIALIZED << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_LOAD_LOCAL_MATERIALIZED) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("LoadLocalMaterialized expected 1 children, got " + numChildren);
            }
            doBeforeEmitInstruction(1, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_LOAD_LOCAL_MAT << 3) | 0));
            bc[bci + 1 + 0] = (short) (int) ((OperationLocalImpl)operationData.arguments[0]).id;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_LOAD_LOCAL_MAT;
            bci = bci + LOAD_LOCAL_MAT_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginStoreLocalMaterialized(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_STORE_LOCAL_MATERIALIZED << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_STORE_LOCAL_MATERIALIZED, curStack, 0, false, (Object) arg0);
        }

        @SuppressWarnings("unused")
        public void endStoreLocalMaterialized() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_STORE_LOCAL_MATERIALIZED << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_STORE_LOCAL_MATERIALIZED) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("StoreLocalMaterialized expected 2 children, got " + numChildren);
            }
            doBeforeEmitInstruction(2, false, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_STORE_LOCAL_MAT << 3) | 0));
            bc[bci + 1 + 0] = (short) (int) ((OperationLocalImpl)operationData.arguments[0]).id;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_STORE_LOCAL_MAT;
            bci = bci + STORE_LOCAL_MAT_LENGTH;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSource(Source arg0) {
            if (isSerializing) {
                try {
                    int arg0_index = constPool.indexOf(arg0);
                    if (arg0_index == -1) {
                        arg0_index = constPool.size();
                        constPool.add(arg0);
                        serBuffer.writeShort((short) -4);
                        serCallback.serialize(SER_CONTEXT, serBuffer, arg0);
                    }
                    serBuffer.writeShort((short) (OP_SOURCE << 1));
                    serBuffer.writeShort((short) arg0_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withSource) {
                doBeforeChild();
                operationData = new BuilderOperationData(operationData, OP_SOURCE, curStack, 0, false, (Object) arg0);
                sourceBuilder.beginSource(bci, arg0);
            }
        }

        @SuppressWarnings("unused")
        public void endSource() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SOURCE << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withSource) {
                if (operationData.operationId != OP_SOURCE) {
                    throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
                }
                int numChildren = operationData.numChildren;
                if (numChildren < 0) {
                    throw new IllegalStateException("Source expected at least 0 children, got " + numChildren);
                }
                sourceBuilder.endSource(bci);
                operationData = operationData.parent;
                doAfterChild();
            }
        }

        @SuppressWarnings("unused")
        public void beginSourceSection(int arg0, int arg1) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SOURCE_SECTION << 1));
                    serBuffer.writeInt(arg0);
                    serBuffer.writeInt(arg1);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withSource) {
                doBeforeChild();
                operationData = new BuilderOperationData(operationData, OP_SOURCE_SECTION, curStack, 0, false, (Object) arg0, (Object) arg1);
                sourceBuilder.beginSourceSection(bci, arg0, arg1);
            }
        }

        @SuppressWarnings("unused")
        public void endSourceSection() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SOURCE_SECTION << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withSource) {
                if (operationData.operationId != OP_SOURCE_SECTION) {
                    throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
                }
                int numChildren = operationData.numChildren;
                if (numChildren < 0) {
                    throw new IllegalStateException("SourceSection expected at least 0 children, got " + numChildren);
                }
                sourceBuilder.endSourceSection(bci);
                operationData = operationData.parent;
                doAfterChild();
            }
        }

        @SuppressWarnings("unused")
        public void beginTag(Class<?> arg0) {
            if (isSerializing) {
                try {
                    int arg0_index = constPool.indexOf(arg0);
                    if (arg0_index == -1) {
                        arg0_index = constPool.size();
                        constPool.add(arg0);
                        serBuffer.writeShort((short) -4);
                        serCallback.serialize(SER_CONTEXT, serBuffer, arg0);
                    }
                    serBuffer.writeShort((short) (OP_TAG << 1));
                    serBuffer.writeShort((short) arg0_index);
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withInstrumentation) {
                doBeforeChild();
                operationData = new BuilderOperationData(operationData, OP_TAG, curStack, 3, true, (Object) arg0);
                int curInstrumentId = 0;
                OperationLabelImpl startLabel = (OperationLabelImpl) createLabel();
                OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                doEmitLabel(startLabel);
                operationData.aux[0] = curInstrumentId;
                operationData.aux[1] = startLabel;
                operationData.aux[2] = endLabel;
                doBeforeEmitInstruction(0, false, false);
                unsafeWriteBytecode(bc, bci, (short) ((INSTR_INSTRUMENT_ENTER << 3) | 0));
                instructionHistory[++instructionHistoryIndex % 8] = INSTR_INSTRUMENT_ENTER;
                bci = bci + INSTRUMENT_ENTER_LENGTH;
                isBbStart[bci] = true;
                lastChildPush = 0;
            }
        }

        @SuppressWarnings("unused")
        public void endTag() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_TAG << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (withInstrumentation) {
                if (operationData.operationId != OP_TAG) {
                    throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
                }
                int numChildren = operationData.numChildren;
                if (numChildren < 0) {
                    throw new IllegalStateException("Tag expected at least 0 children, got " + numChildren);
                }
                if (lastChildPush != 0) {
                    doBeforeEmitInstruction(0, false, false);
                    unsafeWriteBytecode(bc, bci, (short) ((INSTR_INSTRUMENT_EXIT_VOID << 3) | 0));
                    instructionHistory[++instructionHistoryIndex % 8] = INSTR_INSTRUMENT_EXIT_VOID;
                    bci = bci + INSTRUMENT_EXIT_VOID_LENGTH;
                } else {
                    doBeforeEmitInstruction(0, false, false);
                    unsafeWriteBytecode(bc, bci, (short) ((INSTR_INSTRUMENT_EXIT << 3) | 0));
                    instructionHistory[++instructionHistoryIndex % 8] = INSTR_INSTRUMENT_EXIT;
                    bci = bci + INSTRUMENT_EXIT_LENGTH;
                }
                operationData = operationData.parent;
                doAfterChild();
            }
        }

        @SuppressWarnings("unused")
        public void beginSLAdd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_ADD << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_ADD, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLAdd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_ADD << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_ADD) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLAdd expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_ADD << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 2;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_ADD;
            bci = bci + C_SL_ADD_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLDiv() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_DIV << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_DIV, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLDiv() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_DIV << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_DIV) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLDiv expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_DIV << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_DIV;
            bci = bci + C_SL_DIV_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_EQUAL << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_EQUAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_EQUAL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_EQUAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLEqual expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_EQUAL << 3) | 0));
            bc[bci + 1] = (short) numChildNodes;
            numChildNodes += 4;
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 2 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_EQUAL;
            bci = bci + C_SL_EQUAL_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLLessOrEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LESS_OR_EQUAL << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LESS_OR_EQUAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLLessOrEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LESS_OR_EQUAL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_LESS_OR_EQUAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessOrEqual expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_LESS_OR_EQUAL << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_LESS_OR_EQUAL;
            bci = bci + C_SL_LESS_OR_EQUAL_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLLessThan() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LESS_THAN << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LESS_THAN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLLessThan() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LESS_THAN << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_LESS_THAN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessThan expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_LESS_THAN << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_LESS_THAN;
            bci = bci + C_SL_LESS_THAN_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLLogicalNot() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LOGICAL_NOT << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LOGICAL_NOT, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLLogicalNot() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LOGICAL_NOT << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_LOGICAL_NOT) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLLogicalNot expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_LOGICAL_NOT << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_LOGICAL_NOT;
            bci = bci + C_SL_LOGICAL_NOT_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLMul() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_MUL << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_MUL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLMul() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_MUL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_MUL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLMul expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_MUL << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_MUL;
            bci = bci + C_SL_MUL_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLReadProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_READ_PROPERTY << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_READ_PROPERTY, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLReadProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_READ_PROPERTY << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_READ_PROPERTY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLReadProperty expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_READ_PROPERTY << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            constPool.add(null);
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 12;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_READ_PROPERTY;
            bci = bci + C_SL_READ_PROPERTY_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLSub() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_SUB << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_SUB, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLSub() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_SUB << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_SUB) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLSub expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_SUB << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_SUB;
            bci = bci + C_SL_SUB_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLWriteProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_WRITE_PROPERTY << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_WRITE_PROPERTY, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLWriteProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_WRITE_PROPERTY << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_WRITE_PROPERTY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 3) {
                throw new IllegalStateException("SLWriteProperty expected 3 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(3, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_WRITE_PROPERTY << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 11;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 3 + 1] = (short) ((bci - predecessorBcis[2] < 256 ? bci - predecessorBcis[2] : 0));
            bc[bci + 5 + 0] = 0;
            bc[bci + 5 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_WRITE_PROPERTY;
            bci = bci + C_SL_WRITE_PROPERTY_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLUnbox() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_UNBOX << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_UNBOX, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLUnbox() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_UNBOX << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_UNBOX) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLUnbox expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_UNBOX << 3) | 0));
            bc[bci + 1] = (short) numChildNodes;
            numChildNodes += 3;
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_UNBOX;
            bci = bci + C_SL_UNBOX_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLFunctionLiteral() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_FUNCTION_LITERAL << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_FUNCTION_LITERAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLFunctionLiteral() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_FUNCTION_LITERAL << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_FUNCTION_LITERAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLFunctionLiteral expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_FUNCTION_LITERAL << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_FUNCTION_LITERAL;
            bci = bci + C_SL_FUNCTION_LITERAL_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLToBoolean() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_TO_BOOLEAN << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_TO_BOOLEAN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLToBoolean() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_TO_BOOLEAN << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_TO_BOOLEAN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLToBoolean expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true, true);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_TO_BOOLEAN << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_TO_BOOLEAN;
            bci = bci + C_SL_TO_BOOLEAN_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLInvoke() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_INVOKE << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_INVOKE, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        public void endSLInvoke() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_INVOKE << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_INVOKE) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("SLInvoke expected at least 0 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(numChildren - 1 + 0, true, false);
            unsafeWriteBytecode(bc, bci, (short) ((INSTR_C_SL_INVOKE << 3) | 0));
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 5;
            bc[bci + 4] = (short) (numChildren - 1);
            bc[bci + 5 + 0] = 0;
            bc[bci + 5 + 1] = 0;
            instructionHistory[++instructionHistoryIndex % 8] = INSTR_C_SL_INVOKE;
            bci = bci + C_SL_INVOKE_LENGTH;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLAnd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_AND << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_AND, curStack, 1, false);
            operationData.aux[0] = (OperationLabelImpl) createLabel();
        }

        @SuppressWarnings("unused")
        public void endSLAnd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_AND << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_AND) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 1) {
                throw new IllegalStateException("SLAnd expected at least 1 children, got " + numChildren);
            }
            doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        public void beginSLOr() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_OR << 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_OR, curStack, 1, false);
            operationData.aux[0] = (OperationLabelImpl) createLabel();
        }

        @SuppressWarnings("unused")
        public void endSLOr() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_OR << 1) | 1));
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
                return;
            }
            if (operationData.operationId != OP_SL_OR) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 1) {
                throw new IllegalStateException("SLOr expected at least 1 children, got " + numChildren);
            }
            doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        public void setMethodName(TruffleString value) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -6);
                    serBuffer.writeShort(0);
                    serCallback.serialize(SER_CONTEXT, serBuffer, value);
                    return;
                } catch (IOException ex) {
                    throw new IOError(ex);
                }
            }
            metadata_MethodName = value;
        }

        private static void deserializeParser(TruffleLanguage<?> language, ByteBuffer buffer, OperationDeserializer callback, com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder builder) {
            try {
                ArrayList<Object> consts = new ArrayList<>();
                ArrayList<OperationLocal> locals = new ArrayList<>();
                ArrayList<OperationLabel> labels = new ArrayList<>();
                ArrayList<SLOperationRootNode> builtNodes = new ArrayList<>();
                buffer.rewind();
                DataInput dataInput = com.oracle.truffle.api.operation.serialization.SerializationUtils.createDataInput(buffer);
                DeserializerContext context = new DeserializerContext(){
                    @Override
                    public SLOperationRootNode deserializeOperationNode(DataInput buffer) throws IOException {
                        return builtNodes.get(buffer.readInt());
                    }
                }
                ;
                while (true) {
                    switch (buffer.getShort()) {
                        case -2 :
                        {
                            labels.add(builder.createLabel());
                            break;
                        }
                        case -3 :
                        {
                            locals.add(builder.createLocal());
                            break;
                        }
                        case -4 :
                        {
                            consts.add(callback.deserialize(context, dataInput));
                            break;
                        }
                        case -5 :
                        {
                            return;
                        }
                        case -6 :
                        {
                            switch (buffer.getShort()) {
                                case 0 :
                                    builder.setMethodName((TruffleString) callback.deserialize(context, dataInput));
                                    break;
                            }
                            break;
                        }
                        case OP_BLOCK << 1 :
                        {
                            builder.beginBlock();
                            break;
                        }
                        case (OP_BLOCK << 1) | 1 :
                        {
                            builder.endBlock();
                            break;
                        }
                        case OP_ROOT << 1 :
                        {
                            TruffleLanguage<?> arg0 = language;
                            builder.beginRoot(arg0);
                            break;
                        }
                        case (OP_ROOT << 1) | 1 :
                        {
                            builtNodes.add(builder.endRoot());
                            break;
                        }
                        case OP_IF_THEN << 1 :
                        {
                            builder.beginIfThen();
                            break;
                        }
                        case (OP_IF_THEN << 1) | 1 :
                        {
                            builder.endIfThen();
                            break;
                        }
                        case OP_IF_THEN_ELSE << 1 :
                        {
                            builder.beginIfThenElse();
                            break;
                        }
                        case (OP_IF_THEN_ELSE << 1) | 1 :
                        {
                            builder.endIfThenElse();
                            break;
                        }
                        case OP_CONDITIONAL << 1 :
                        {
                            builder.beginConditional();
                            break;
                        }
                        case (OP_CONDITIONAL << 1) | 1 :
                        {
                            builder.endConditional();
                            break;
                        }
                        case OP_WHILE << 1 :
                        {
                            builder.beginWhile();
                            break;
                        }
                        case (OP_WHILE << 1) | 1 :
                        {
                            builder.endWhile();
                            break;
                        }
                        case OP_TRY_CATCH << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.getShort());
                            builder.beginTryCatch(arg0);
                            break;
                        }
                        case (OP_TRY_CATCH << 1) | 1 :
                        {
                            builder.endTryCatch();
                            break;
                        }
                        case OP_FINALLY_TRY << 1 :
                        {
                            builder.beginFinallyTry();
                            break;
                        }
                        case (OP_FINALLY_TRY << 1) | 1 :
                        {
                            builder.endFinallyTry();
                            break;
                        }
                        case OP_FINALLY_TRY_NO_EXCEPT << 1 :
                        {
                            builder.beginFinallyTryNoExcept();
                            break;
                        }
                        case (OP_FINALLY_TRY_NO_EXCEPT << 1) | 1 :
                        {
                            builder.endFinallyTryNoExcept();
                            break;
                        }
                        case OP_LABEL << 1 :
                        {
                            OperationLabel arg0 = labels.get(buffer.getShort());
                            builder.emitLabel(arg0);
                            break;
                        }
                        case OP_BRANCH << 1 :
                        {
                            OperationLabel arg0 = labels.get(buffer.getShort());
                            builder.emitBranch(arg0);
                            break;
                        }
                        case OP_LOAD_CONSTANT << 1 :
                        {
                            Object arg0 = (Object) consts.get(buffer.getShort());
                            builder.emitLoadConstant(arg0);
                            break;
                        }
                        case OP_LOAD_ARGUMENT << 1 :
                        {
                            int arg0 = buffer.getInt();
                            builder.emitLoadArgument(arg0);
                            break;
                        }
                        case OP_LOAD_LOCAL << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.getShort());
                            builder.emitLoadLocal(arg0);
                            break;
                        }
                        case OP_STORE_LOCAL << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.getShort());
                            builder.beginStoreLocal(arg0);
                            break;
                        }
                        case (OP_STORE_LOCAL << 1) | 1 :
                        {
                            builder.endStoreLocal();
                            break;
                        }
                        case OP_RETURN << 1 :
                        {
                            builder.beginReturn();
                            break;
                        }
                        case (OP_RETURN << 1) | 1 :
                        {
                            builder.endReturn();
                            break;
                        }
                        case OP_LOAD_LOCAL_MATERIALIZED << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.getShort());
                            builder.beginLoadLocalMaterialized(arg0);
                            break;
                        }
                        case (OP_LOAD_LOCAL_MATERIALIZED << 1) | 1 :
                        {
                            builder.endLoadLocalMaterialized();
                            break;
                        }
                        case OP_STORE_LOCAL_MATERIALIZED << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.getShort());
                            builder.beginStoreLocalMaterialized(arg0);
                            break;
                        }
                        case (OP_STORE_LOCAL_MATERIALIZED << 1) | 1 :
                        {
                            builder.endStoreLocalMaterialized();
                            break;
                        }
                        case OP_SOURCE << 1 :
                        {
                            Source arg0 = (Source) consts.get(buffer.getShort());
                            builder.beginSource(arg0);
                            break;
                        }
                        case (OP_SOURCE << 1) | 1 :
                        {
                            builder.endSource();
                            break;
                        }
                        case OP_SOURCE_SECTION << 1 :
                        {
                            int arg0 = buffer.getInt();
                            int arg1 = buffer.getInt();
                            builder.beginSourceSection(arg0, arg1);
                            break;
                        }
                        case (OP_SOURCE_SECTION << 1) | 1 :
                        {
                            builder.endSourceSection();
                            break;
                        }
                        case OP_TAG << 1 :
                        {
                            Class<?> arg0 = (Class<?>) consts.get(buffer.getShort());
                            builder.beginTag(arg0);
                            break;
                        }
                        case (OP_TAG << 1) | 1 :
                        {
                            builder.endTag();
                            break;
                        }
                        case OP_SL_ADD << 1 :
                        {
                            builder.beginSLAdd();
                            break;
                        }
                        case (OP_SL_ADD << 1) | 1 :
                        {
                            builder.endSLAdd();
                            break;
                        }
                        case OP_SL_DIV << 1 :
                        {
                            builder.beginSLDiv();
                            break;
                        }
                        case (OP_SL_DIV << 1) | 1 :
                        {
                            builder.endSLDiv();
                            break;
                        }
                        case OP_SL_EQUAL << 1 :
                        {
                            builder.beginSLEqual();
                            break;
                        }
                        case (OP_SL_EQUAL << 1) | 1 :
                        {
                            builder.endSLEqual();
                            break;
                        }
                        case OP_SL_LESS_OR_EQUAL << 1 :
                        {
                            builder.beginSLLessOrEqual();
                            break;
                        }
                        case (OP_SL_LESS_OR_EQUAL << 1) | 1 :
                        {
                            builder.endSLLessOrEqual();
                            break;
                        }
                        case OP_SL_LESS_THAN << 1 :
                        {
                            builder.beginSLLessThan();
                            break;
                        }
                        case (OP_SL_LESS_THAN << 1) | 1 :
                        {
                            builder.endSLLessThan();
                            break;
                        }
                        case OP_SL_LOGICAL_NOT << 1 :
                        {
                            builder.beginSLLogicalNot();
                            break;
                        }
                        case (OP_SL_LOGICAL_NOT << 1) | 1 :
                        {
                            builder.endSLLogicalNot();
                            break;
                        }
                        case OP_SL_MUL << 1 :
                        {
                            builder.beginSLMul();
                            break;
                        }
                        case (OP_SL_MUL << 1) | 1 :
                        {
                            builder.endSLMul();
                            break;
                        }
                        case OP_SL_READ_PROPERTY << 1 :
                        {
                            builder.beginSLReadProperty();
                            break;
                        }
                        case (OP_SL_READ_PROPERTY << 1) | 1 :
                        {
                            builder.endSLReadProperty();
                            break;
                        }
                        case OP_SL_SUB << 1 :
                        {
                            builder.beginSLSub();
                            break;
                        }
                        case (OP_SL_SUB << 1) | 1 :
                        {
                            builder.endSLSub();
                            break;
                        }
                        case OP_SL_WRITE_PROPERTY << 1 :
                        {
                            builder.beginSLWriteProperty();
                            break;
                        }
                        case (OP_SL_WRITE_PROPERTY << 1) | 1 :
                        {
                            builder.endSLWriteProperty();
                            break;
                        }
                        case OP_SL_UNBOX << 1 :
                        {
                            builder.beginSLUnbox();
                            break;
                        }
                        case (OP_SL_UNBOX << 1) | 1 :
                        {
                            builder.endSLUnbox();
                            break;
                        }
                        case OP_SL_FUNCTION_LITERAL << 1 :
                        {
                            builder.beginSLFunctionLiteral();
                            break;
                        }
                        case (OP_SL_FUNCTION_LITERAL << 1) | 1 :
                        {
                            builder.endSLFunctionLiteral();
                            break;
                        }
                        case OP_SL_TO_BOOLEAN << 1 :
                        {
                            builder.beginSLToBoolean();
                            break;
                        }
                        case (OP_SL_TO_BOOLEAN << 1) | 1 :
                        {
                            builder.endSLToBoolean();
                            break;
                        }
                        case OP_SL_INVOKE << 1 :
                        {
                            builder.beginSLInvoke();
                            break;
                        }
                        case (OP_SL_INVOKE << 1) | 1 :
                        {
                            builder.endSLInvoke();
                            break;
                        }
                        case OP_SL_AND << 1 :
                        {
                            builder.beginSLAnd();
                            break;
                        }
                        case (OP_SL_AND << 1) | 1 :
                        {
                            builder.endSLAnd();
                            break;
                        }
                        case OP_SL_OR << 1 :
                        {
                            builder.beginSLOr();
                            break;
                        }
                        case (OP_SL_OR << 1) | 1 :
                        {
                            builder.endSLOr();
                            break;
                        }
                    }
                }
            } catch (IOException ex) {
                throw new IOError(ex);
            }
        }

        @GeneratedBy(SLOperationRootNode.class)
        private static final class BuilderOperationData {

            final BuilderOperationData parent;
            final int operationId;
            final int stackDepth;
            final boolean needsLeave;
            final int depth;
            final Object[] arguments;
            final Object[] aux;
            int numChildren;

            private BuilderOperationData(BuilderOperationData parent, int operationId, int stackDepth, int numAux, boolean needsLeave, Object ...arguments) {
                this.parent = parent;
                this.operationId = operationId;
                this.stackDepth = stackDepth;
                this.depth = parent == null ? 0 : parent.depth + 1;
                this.aux = numAux > 0 ? new Object[numAux] : null;
                this.needsLeave = needsLeave || (parent != null && parent.needsLeave);
                this.arguments = arguments;
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class OperationLabelImpl extends OperationLabel {

            BuilderOperationData data;
            BuilderFinallyTryContext finallyTry;
            int targetBci = 0;
            boolean hasValue = false;

            OperationLabelImpl(BuilderOperationData data, BuilderFinallyTryContext finallyTry) {
                this.data = data;
                this.finallyTry = finallyTry;
            }

            boolean belongsTo(BuilderFinallyTryContext context) {
                BuilderFinallyTryContext cur = finallyTry;
                while (cur != null) {
                    if (cur == context) {
                        return true;
                    }
                    cur = cur.prev;
                }
                return false;
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class OperationSerLabelImpl extends OperationLabel {

            int id;

            OperationSerLabelImpl(int id) {
                this.id = id;
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class OperationLocalImpl extends OperationLocal {

            final BuilderOperationData owner;
            final int id;

            OperationLocalImpl(BuilderOperationData owner, int id) {
                this.owner = owner;
                this.id = id;
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class LabelFill {

            int locationBci;
            OperationLabelImpl label;

            LabelFill(int locationBci, OperationLabelImpl label) {
                this.locationBci = locationBci;
                this.label = label;
            }

            LabelFill offset(int offset) {
                return new LabelFill(offset + locationBci, label);
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class SourceInfoBuilder {

            private final ArrayList<Integer> sourceStack = new ArrayList<>();
            private final ArrayList<Source> sourceList = new ArrayList<>();
            private int currentSource = -1;
            private final ArrayList<Integer> bciList = new ArrayList<>();
            private final ArrayList<SourceData> sourceDataList = new ArrayList<>();
            private final ArrayList<SourceData> sourceDataStack = new ArrayList<>();

            void reset() {
                sourceStack.clear();
                sourceDataList.clear();
                sourceDataStack.clear();
                bciList.clear();
            }

            void beginSource(int bci, Source src) {
                int idx = sourceList.indexOf(src);
                if (idx == -1) {
                    idx = sourceList.size();
                    sourceList.add(src);
                }
                sourceStack.add(currentSource);
                currentSource = idx;
                beginSourceSection(bci, -1, -1);
            }

            void endSource(int bci) {
                endSourceSection(bci);
                currentSource = sourceStack.remove(sourceStack.size() - 1);
            }

            void beginSourceSection(int bci, int start, int length) {
                SourceData data = new SourceData(start, length, currentSource);
                bciList.add(bci);
                sourceDataList.add(data);
                sourceDataStack.add(data);
            }

            void endSourceSection(int bci) {
                SourceData data = sourceDataStack.remove(sourceDataStack.size() - 1);
                SourceData prev;
                if (sourceDataStack.isEmpty()) {
                    prev = new SourceData(-1, -1, currentSource);
                } else {
                    prev = sourceDataStack.get(sourceDataStack.size() - 1);
                }
                bciList.add(bci);
                sourceDataList.add(prev);
            }

            int[] build() {
                if (!sourceStack.isEmpty()) {
                    throw new IllegalStateException("not all sources ended");
                }
                if (!sourceDataStack.isEmpty()) {
                    throw new IllegalStateException("not all source sections ended");
                }
                int size = bciList.size();
                int[] resultArray = new int[size * 3];
                int index = 0;
                int lastBci = -1;
                boolean isFirst = true;
                for (int i = 0; i < size; i++) {
                    SourceData data = sourceDataList.get(i);
                    int curBci = bciList.get(i);
                    if (data.start == -1 && isFirst) {
                        continue;
                    }
                    isFirst = false;
                    if (curBci == lastBci && index > 1) {
                        index -= 3;
                    }
                    resultArray[index + 0] = curBci | (data.sourceIndex << 16);
                    resultArray[index + 1] = data.start;
                    resultArray[index + 2] = data.length;
                    index += 3;
                    lastBci = curBci;
                }
                return Arrays.copyOf(resultArray, index);
            }

            Source[] buildSource() {
                return sourceList.toArray(new Source[0]);
            }

            @GeneratedBy(SLOperationRootNode.class)
            private static final class SourceData {

                final int start;
                final int length;
                final int sourceIndex;

                SourceData(int start, int length, int sourceIndex) {
                    this.start = start;
                    this.length = length;
                    this.sourceIndex = sourceIndex;
                }

            }
        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class BuilderFinallyTryContext {

            final BuilderFinallyTryContext prev;
            final short[] bc;
            final ArrayList<ExceptionHandler> exceptionHandlers;
            final ArrayList<LabelFill> labelFills;
            final ArrayList<OperationLabelImpl> labels;
            final int curStack;
            final int maxStack;
            short[] handlerBc;
            ArrayList<ExceptionHandler> handlerHandlers;
            ArrayList<LabelFill> handlerLabelFills = new ArrayList<>();
            ArrayList<Integer> relocationOffsets = new ArrayList<>();
            int handlerMaxStack;

            BuilderFinallyTryContext(BuilderFinallyTryContext prev, short[] bc, ArrayList<ExceptionHandler> exceptionHandlers, ArrayList<LabelFill> labelFills, ArrayList<OperationLabelImpl> labels, int curStack, int maxStack) {
                this.prev = prev;
                this.bc = bc;
                this.exceptionHandlers = exceptionHandlers;
                this.labelFills = labelFills;
                this.labels = labels;
                this.curStack = curStack;
                this.maxStack = maxStack;
            }

        }
        private final class BuilderState {

            BuilderState parentData;
            private short[] bc = new short[65535];
            private int bci;
            private int curStack;
            private int maxStack;
            private int numLocals;
            private int numLabels;
            private ArrayList<Object> constPool;
            private BuilderOperationData operationData;
            private ArrayList<OperationLabelImpl> labels = new ArrayList<>();
            private ArrayList<LabelFill> labelFills = new ArrayList<>();
            private int numChildNodes;
            private int numConditionProfiles;
            private ArrayList<ExceptionHandler> exceptionHandlers = new ArrayList<>();
            private BuilderFinallyTryContext currentFinallyTry;
            private int[] stackSourceBci = new int[1024];

            BuilderState(com.oracle.truffle.sl.operations.SLOperationRootNodeGen.Builder p) {
                this.bc = p.bc;
                this.bci = p.bci;
                this.curStack = p.curStack;
                this.maxStack = p.maxStack;
                this.numLocals = p.numLocals;
                this.numLabels = p.numLabels;
                this.constPool = p.constPool;
                this.operationData = p.operationData;
                this.labels = p.labels;
                this.labelFills = p.labelFills;
                this.numChildNodes = p.numChildNodes;
                this.numConditionProfiles = p.numConditionProfiles;
                this.exceptionHandlers = p.exceptionHandlers;
                this.currentFinallyTry = p.currentFinallyTry;
                this.stackSourceBci = p.stackSourceBci;
                this.parentData = p.parentData;
            }

        }
        @GeneratedBy(SLOperationRootNode.class)
        private static final class OperationSerNodeImpl extends SLOperationRootNode {

            @CompilationFinal int buildOrder;

            private OperationSerNodeImpl(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, int buildOrder) {
                super(language, frameDescriptor);
                this.buildOrder = buildOrder;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                throw new UnsupportedOperationException();
            }

            @Override
            public SourceSection getSourceSectionAtBci(int bci) {
                throw new UnsupportedOperationException();
            }

        }
    }
    private static final class Counter {

        int count;

    }
}
