package com.oracle.graal.compiler.gen;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.type.*;

public interface LIRGeneratorCommon {

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     * 
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    boolean canStoreConstant(Constant c, boolean isCompressed);

    /**
     * Returns true if the redundant move elimination optimization should be done after register
     * allocation.
     */
    boolean canEliminateRedundantMoves();

    TargetDescription target();

    MetaAccessProvider getMetaAccess();

    CodeCacheProvider getCodeCache();

    ForeignCallsProvider getForeignCalls();

    /**
     * Creates a new {@linkplain Variable variable}.
     * 
     * @param platformKind The kind of the new variable.
     * @return a new variable
     */
    Variable newVariable(PlatformKind platformKind);

    RegisterAttributes attributes(Register register);

    Variable emitMove(Value input);

    AllocatableValue asAllocatable(Value value);

    Variable load(Value value);

    Value loadNonConst(Value value);

    void doBlock(AbstractBlock<?> block);

    /**
     * Gets the ABI specific operand used to return a value of a given kind from a method.
     * 
     * @param kind the kind of value being returned
     * @return the operand representing the ABI defined location used return a value of kind
     *         {@code kind}
     */
    AllocatableValue resultOperandFor(Kind kind);

    void append(LIRInstruction op);

    void emitIncomingValues(Value[] params);

    void emitConstantBranch(boolean value, LabelRef trueSuccessorBlock, LabelRef falseSuccessorBlock);

    void emitJump(LabelRef label);

    void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability);

    void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability);

    void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability);

    Variable emitConditionalMove(Value leftVal, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue);

    Variable emitIntegerTestMove(Value leftVal, Value right, Value trueValue, Value falseValue);

    CallingConvention getCallingConvention();

    /**
     * Default implementation: Return the Java stack kind for each stamp.
     */
    PlatformKind getPlatformKind(Stamp stamp);

    PlatformKind getIntegerKind(int bits, boolean unsigned);

    PlatformKind getFloatingKind(int bits);

    PlatformKind getObjectKind();

    void emitBitCount(Variable result, Value operand);

    void emitBitScanForward(Variable result, Value operand);

    void emitBitScanReverse(Variable result, Value operand);

    void emitByteSwap(Variable result, Value operand);

    void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length);

}