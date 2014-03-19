package com.oracle.graal.compiler.gen;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;

public interface NodeBasedLIRGenerator {

    /**
     * Returns the operand that has been previously initialized by
     * {@link #setResult(ValueNode, Value)} with the result of an instruction.
     * 
     * @param node A node that produces a result value.
     */
    Value operand(ValueNode node);

    ValueNode valueForOperand(Value value);

    Value setResult(ValueNode x, Value operand);

    LabelRef getLIRBlock(FixedNode b);

    LIRFrameState state(DeoptimizingNode deopt);

    LIRFrameState stateWithExceptionEdge(DeoptimizingNode deopt, LabelRef exceptionEdge);

    LIRFrameState stateFor(FrameState state);

    LIRFrameState stateForWithExceptionEdge(FrameState state, LabelRef exceptionEdge);

    void append(LIRInstruction op);

    void doBlock(Block block, StructuredGraph graph, BlockMap<List<ScheduledNode>> blockMap);

    void visitReturn(ReturnNode x);

    void visitMerge(MergeNode x);

    void visitEndNode(AbstractEndNode end);

    /**
     * Runtime specific classes can override this to insert a safepoint at the end of a loop.
     */
    void visitLoopEnd(LoopEndNode x);

    void emitIf(IfNode x);

    void emitBranch(LogicNode node, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability);

    void emitCompareBranch(CompareNode compare, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability);

    void emitIntegerTestBranch(IntegerTestNode test, LabelRef trueSuccessor, LabelRef falseSuccessor, double trueSuccessorProbability);

    void emitConditional(ConditionalNode conditional);

    Variable emitConditional(LogicNode node, Value trueValue, Value falseValue);

    void emitInvoke(Invoke x);

    Value[] visitInvokeArguments(CallingConvention invokeCc, Collection<ValueNode> arguments);

    Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args);

    /**
     * This method tries to create a switch implementation that is optimal for the given switch. It
     * will either generate a sequential if/then/else cascade, a set of range tests or a table
     * switch.
     * 
     * If the given switch does not contain int keys, it will always create a sequential
     * implementation.
     */
    void emitSwitch(SwitchNode x);

    void beforeRegisterAllocation();

}