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
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.GenerateAOT.Provider;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.operation.BuilderExceptionHandler;
import com.oracle.truffle.api.operation.BuilderOperationData;
import com.oracle.truffle.api.operation.BuilderOperationLabel;
import com.oracle.truffle.api.operation.OperationBuilder;
import com.oracle.truffle.api.operation.OperationBytecodeNode;
import com.oracle.truffle.api.operation.OperationConfig;
import com.oracle.truffle.api.operation.OperationInstrumentedBytecodeNode;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationLocal;
import com.oracle.truffle.api.operation.OperationNode;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.OperationsBytesSupport;
import com.oracle.truffle.api.profiles.ConditionProfile;
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
import com.oracle.truffle.sl.operations.SLOperations.SLInvoke;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLObject;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

@GeneratedBy(SLOperations.class)
@SuppressWarnings({"unused", "cast", "hiding", "unchecked", "rawtypes", "static-method"})
public abstract class SLOperationsBuilder extends OperationBuilder {

    protected SLOperationsBuilder(OperationNodes nodes, boolean isReparse, OperationConfig config) {
        super(nodes, isReparse, config);
    }

    public abstract void beginBlock();

    public abstract void endBlock();

    public abstract void beginIfThen();

    public abstract void endIfThen();

    public abstract void beginIfThenElse();

    public abstract void endIfThenElse();

    public abstract void beginConditional();

    public abstract void endConditional();

    public abstract void beginWhile();

    public abstract void endWhile();

    public abstract void beginTryCatch(int arg0);

    public abstract void endTryCatch();

    public abstract void beginFinallyTry();

    public abstract void endFinallyTry();

    public abstract void beginFinallyTryNoExcept();

    public abstract void endFinallyTryNoExcept();

    public abstract void emitLabel(OperationLabel arg0);

    public abstract void emitBranch(OperationLabel arg0);

    public abstract void emitConstObject(Object arg0);

    public abstract void emitLoadArgument(int arg0);

    public abstract void beginStoreLocal(OperationLocal arg0);

    public abstract void endStoreLocal();

    public abstract void emitLoadLocal(OperationLocal arg0);

    public abstract void beginReturn();

    public abstract void endReturn();

    public abstract void beginTag(Class<?> arg0);

    public abstract void endTag();

    public abstract void beginSLAdd();

    public abstract void endSLAdd();

    public abstract void beginSLDiv();

    public abstract void endSLDiv();

    public abstract void beginSLEqual();

    public abstract void endSLEqual();

    public abstract void beginSLLessOrEqual();

    public abstract void endSLLessOrEqual();

    public abstract void beginSLLessThan();

    public abstract void endSLLessThan();

    public abstract void beginSLLogicalNot();

    public abstract void endSLLogicalNot();

    public abstract void beginSLMul();

    public abstract void endSLMul();

    public abstract void beginSLReadProperty();

    public abstract void endSLReadProperty();

    public abstract void beginSLSub();

    public abstract void endSLSub();

    public abstract void beginSLWriteProperty();

    public abstract void endSLWriteProperty();

    public abstract void beginSLUnbox();

    public abstract void endSLUnbox();

    public abstract void beginSLFunctionLiteral();

    public abstract void endSLFunctionLiteral();

    public abstract void beginSLToBoolean();

    public abstract void endSLToBoolean();

    public abstract void beginSLAnd();

    public abstract void endSLAnd();

    public abstract void beginSLOr();

    public abstract void endSLOr();

    public abstract void beginSLInvoke();

    public abstract void endSLInvoke();

    public abstract void setMethodName(TruffleString value);

    public static OperationNodes create(OperationConfig config, Consumer<SLOperationsBuilder> generator) {
        OperationNodes nodes = new OperationNodesImpl(generator);
        BuilderImpl builder = new BuilderImpl(nodes, false, config);
        generator.accept(builder);
        builder.finish();
        return nodes;
    }

    @GeneratedBy(SLOperations.class)
    private static final class OperationNodesImpl extends OperationNodes {

        OperationNodesImpl(Consumer<? extends OperationBuilder> parse) {
            super(parse);
        }

        @Override
        protected void reparseImpl(OperationConfig config, Consumer<?> parse, OperationNode[] nodes) {
            BuilderImpl builder = new BuilderImpl(this, true, config);
            ((Consumer) parse).accept(builder);
            builder.finish();
        }

    }
    @GeneratedBy(SLOperations.class)
    private static class BuilderImpl extends SLOperationsBuilder {

        private static final OperationsBytesSupport LE_BYTES = OperationsBytesSupport.littleEndian();
        private static final int OP_BLOCK = 1;
        private static final int OP_IF_THEN = 2;
        private static final int OP_IF_THEN_ELSE = 3;
        private static final int OP_CONDITIONAL = 4;
        private static final int OP_WHILE = 5;
        private static final int OP_TRY_CATCH = 6;
        private static final int OP_FINALLY_TRY = 7;
        private static final int OP_FINALLY_TRY_NO_EXCEPT = 8;
        private static final int OP_LABEL = 9;
        private static final int OP_BRANCH = 10;
        private static final int OP_CONST_OBJECT = 11;
        private static final int OP_LOAD_ARGUMENT = 12;
        private static final int OP_STORE_LOCAL = 13;
        private static final int OP_LOAD_LOCAL = 14;
        private static final int OP_RETURN = 15;
        private static final int OP_TAG = 16;
        private static final int OP_SLADD = 17;
        private static final int OP_SLDIV = 18;
        private static final int OP_SLEQUAL = 19;
        private static final int OP_SLLESS_OR_EQUAL = 20;
        private static final int OP_SLLESS_THAN = 21;
        private static final int OP_SLLOGICAL_NOT = 22;
        private static final int OP_SLMUL = 23;
        private static final int OP_SLREAD_PROPERTY = 24;
        private static final int OP_SLSUB = 25;
        private static final int OP_SLWRITE_PROPERTY = 26;
        private static final int OP_SLUNBOX = 27;
        private static final int OP_SLFUNCTION_LITERAL = 28;
        private static final int OP_SLTO_BOOLEAN = 29;
        private static final int OP_SLAND = 30;
        private static final int OP_SLOR = 31;
        private static final int OP_SLINVOKE = 32;
        private static final int INSTR_POP = 1;
        private static final int INSTR_BRANCH = 2;
        private static final int INSTR_BRANCH_FALSE = 3;
        private static final int INSTR_THROW = 4;
        private static final int INSTR_LOAD_CONSTANT_OBJECT = 5;
        private static final int INSTR_LOAD_CONSTANT_BOOLEAN = 6;
        private static final int INSTR_LOAD_CONSTANT_LONG = 7;
        private static final int INSTR_LOAD_ARGUMENT_OBJECT = 8;
        private static final int INSTR_LOAD_ARGUMENT_BOOLEAN = 9;
        private static final int INSTR_LOAD_ARGUMENT_LONG = 10;
        private static final int INSTR_STORE_LOCAL_OBJECT = 11;
        private static final int INSTR_STORE_LOCAL_BOOLEAN = 12;
        private static final int INSTR_STORE_LOCAL_LONG = 13;
        private static final int INSTR_STORE_LOCAL_UNINIT = 14;
        private static final int INSTR_LOAD_LOCAL_OBJECT = 15;
        private static final int INSTR_LOAD_LOCAL_BOOLEAN = 16;
        private static final int INSTR_LOAD_LOCAL_LONG = 17;
        private static final int INSTR_LOAD_LOCAL_UNINIT = 18;
        private static final int INSTR_RETURN = 19;
        private static final int INSTR_INSTRUMENT_ENTER = 20;
        private static final int INSTR_INSTRUMENT_EXIT_VOID = 21;
        private static final int INSTR_INSTRUMENT_EXIT = 22;
        private static final int INSTR_INSTRUMENT_LEAVE = 23;
        private static final int INSTR_C_SLADD = 24;
        private static final int INSTR_C_SLDIV = 25;
        private static final int INSTR_C_SLEQUAL = 26;
        private static final int INSTR_C_SLLESS_OR_EQUAL = 27;
        private static final int INSTR_C_SLLESS_THAN = 28;
        private static final int INSTR_C_SLLOGICAL_NOT = 29;
        private static final int INSTR_C_SLMUL = 30;
        private static final int INSTR_C_SLREAD_PROPERTY = 31;
        private static final int INSTR_C_SLSUB = 32;
        private static final int INSTR_C_SLWRITE_PROPERTY = 33;
        private static final int INSTR_C_SLUNBOX = 34;
        private static final int INSTR_C_SLFUNCTION_LITERAL = 35;
        private static final int INSTR_C_SLTO_BOOLEAN = 36;
        private static final int INSTR_SC_SLAND = 37;
        private static final int INSTR_SC_SLOR = 38;
        private static final int INSTR_C_SLINVOKE = 39;
        private static final int INSTR_C_SLUNBOX_Q_FROM_LONG = 40;
        private static final int INSTR_C_SLADD_Q_ADD_LONG = 41;
        private static final int INSTR_C_SLREAD_PROPERTY_Q_READ_SLOBJECT0 = 42;
        private static final int INSTR_C_SLUNBOX_Q_FROM_BOOLEAN = 43;
        private static final int INSTR_C_SLTO_BOOLEAN_Q_BOOLEAN = 44;
        private static final int INSTR_C_SLLESS_OR_EQUAL_Q_LESS_OR_EQUAL0 = 45;
        private static final int INSTR_C_SLINVOKE_Q_DIRECT = 46;
        private static final int INSTR_C_SLFUNCTION_LITERAL_Q_PERFORM = 47;
        private static final int INSTR_C_SLWRITE_PROPERTY_Q_WRITE_SLOBJECT0 = 48;
        private static final int INSTR_C_SLLESS_THAN_Q_LESS_THAN0 = 49;
        private static final short[][] BOXING_DESCRIPTORS = {
        // OBJECT
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, 0, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, 0, 0, 0, 0, 0, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, 0, 0, -31487 /* 5,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31487 /* 5,1 */, -31743 /* 4,1 */},
        // LONG
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_LONG, 0, INSTR_LOAD_LOCAL_OBJECT, 0, INSTR_LOAD_LOCAL_LONG, 0, 0, 0, 0, 0, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, 0, 0, -31487 /* 5,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31487 /* 5,1 */, -31743 /* 4,1 */},
        // INT
        null,
        // DOUBLE
        null,
        // FLOAT
        null,
        // BOOLEAN
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, INSTR_LOAD_ARGUMENT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_BOOLEAN, 0, 0, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_BOOLEAN, 0, 0, 0, 0, 0, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, 0, 0, -31487 /* 5,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31743 /* 4,1 */, -31999 /* 3,1 */, -31999 /* 3,1 */, -31743 /* 4,1 */, -31487 /* 5,1 */, -31999 /* 3,1 */, -31487 /* 5,1 */, -31743 /* 4,1 */},
        // BYTE
        null};

        int lastChildPush;
        private TruffleString metadata_MethodName;

        BuilderImpl(OperationNodes nodes, boolean isReparse, OperationConfig config) {
            super(nodes, isReparse, config);
        }

        @Override
        protected OperationNode createNode(OperationNodes arg0, Object arg1, OperationBytecodeNode arg2) {
            return new OperationNodeImpl(arg0, arg1, arg2);
        }

        @Override
        protected OperationBytecodeNode createBytecode(int arg0, int arg1, byte[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5, ConditionProfile[] arg6) {
            return new BytecodeNode(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
        }

        @Override
        protected OperationInstrumentedBytecodeNode createInstrumentedBytecode(int arg0, int arg1, byte[] arg2, Object[] arg3, Node[] arg4, BuilderExceptionHandler[] arg5, ConditionProfile[] arg6) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        protected void doLeaveOperation(BuilderOperationData data) {
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
                    int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                    LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_LEAVE);
                    LE_BYTES.putShort(bc, bci + 2, (short) (int) ((int) data.aux[0]));
                    createOffset(bci + 4, ((BuilderOperationLabel) data.aux[1]));
                    createOffset(bci + 6, ((BuilderOperationLabel) data.aux[2]));
                    bci = bci + 8;
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        void doBeforeChild() {
            int childIndex = operationData.numChildren;
            switch (operationData.operationId) {
                case OP_BLOCK :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    if (childIndex == 1) {
                        setCurStack(((BuilderExceptionHandler) operationData.aux[0]).startStack);
                        ((BuilderExceptionHandler) operationData.aux[0]).handlerBci = bci;
                    }
                    break;
                }
                case OP_TAG :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                    }
                    break;
                }
                case OP_SLAND :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_SC_SLAND);
                        bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        createOffset(bci + 3, ((BuilderOperationLabel) operationData.aux[0]));
                        // additionalData  = 1 bytes: [BITS]
                        //   numChildNodes = 0
                        //   numConsts     = 0
                        bc[bci + 5 + 0] = 0;
                        bci = bci + 6;
                    }
                    break;
                }
                case OP_SLOR :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_SC_SLOR);
                        bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        createOffset(bci + 3, ((BuilderOperationLabel) operationData.aux[0]));
                        // additionalData  = 1 bytes: [BITS]
                        //   numChildNodes = 0
                        //   numConsts     = 0
                        bc[bci + 5 + 0] = 0;
                        bci = bci + 6;
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        void doAfterChild() {
            int childIndex = operationData.numChildren++;
            switch (operationData.operationId) {
                case OP_IF_THEN :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = endLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, endLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) createBranchProfile());
                        bci = bci + 7;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[0]));
                    }
                    break;
                }
                case OP_IF_THEN_ELSE :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        BuilderOperationLabel elseLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = elseLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, elseLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) createBranchProfile());
                        bci = bci + 7;
                    } else if (childIndex == 1) {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, (BuilderOperationLabel) endLabel);
                        int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, endLabel);
                        bci = bci + 4;
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[0]));
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_CONDITIONAL :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        BuilderOperationLabel elseLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = elseLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, elseLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) createBranchProfile());
                        bci = bci + 7;
                    } else if (childIndex == 1) {
                        assert lastChildPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, (BuilderOperationLabel) endLabel);
                        int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, endLabel);
                        bci = bci + 4;
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[0]));
                    } else {
                        assert lastChildPush == 1;
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_WHILE :
                {
                    if (childIndex == 0) {
                        assert lastChildPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, endLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) createBranchProfile());
                        bci = bci + 7;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        calculateLeaves(operationData, (BuilderOperationLabel) ((BuilderOperationLabel) operationData.aux[0]));
                        int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, ((BuilderOperationLabel) operationData.aux[0]));
                        bci = bci + 4;
                        doEmitLabel(((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                        bci = bci + 2;
                    }
                    if (childIndex == 0) {
                        ((BuilderExceptionHandler) operationData.aux[0]).endBci = bci;
                        calculateLeaves(operationData, (BuilderOperationLabel) ((BuilderOperationLabel) operationData.aux[1]));
                        int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, ((BuilderOperationLabel) operationData.aux[1]));
                        bci = bci + 4;
                    } else {
                    }
                    break;
                }
                case OP_FINALLY_TRY :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                        bci = bci + 2;
                    }
                    if (childIndex == 0) {
                        doEndFinallyBlock();
                        BuilderExceptionHandler beh = new BuilderExceptionHandler();
                        beh.startBci = bci;
                        beh.startStack = getCurStack();
                        beh.exceptionIndex = getLocalIndex(operationData.aux[2]);
                        addExceptionHandler(beh);
                        operationData.aux[1] = beh;
                    }
                    break;
                }
                case OP_FINALLY_TRY_NO_EXCEPT :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                        bci = bci + 2;
                    }
                    if (childIndex == 0) {
                        doEndFinallyBlock();
                    }
                    break;
                }
            }
        }

        @Override
        protected int getBlockOperationIndex() {
            return OP_BLOCK;
        }

        @SuppressWarnings("unused")
        @Override
        public void beginBlock() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BLOCK, getCurStack(), 0, false);
            lastChildPush = 0;
        }

        @SuppressWarnings("unused")
        @Override
        public void endBlock() {
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
        @Override
        public void beginIfThen() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN, getCurStack(), 1, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endIfThen() {
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
        @Override
        public void beginIfThenElse() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN_ELSE, getCurStack(), 2, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endIfThenElse() {
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
        @Override
        public void beginConditional() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_CONDITIONAL, getCurStack(), 2, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endConditional() {
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
        @Override
        public void beginWhile() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_WHILE, getCurStack(), 2, false);
            BuilderOperationLabel startLabel = (BuilderOperationLabel) createLabel();
            doEmitLabel(startLabel);
            operationData.aux[0] = startLabel;
        }

        @SuppressWarnings("unused")
        @Override
        public void endWhile() {
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
        @Override
        public void beginTryCatch(int arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_TRY_CATCH, getCurStack(), 2, false, arg0);
            BuilderExceptionHandler beh = new BuilderExceptionHandler();
            beh.startBci = bci;
            beh.startStack = getCurStack();
            beh.exceptionIndex = (int)operationData.arguments[0];
            addExceptionHandler(beh);
            operationData.aux[0] = beh;
            BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
            operationData.aux[1] = endLabel;
        }

        @SuppressWarnings("unused")
        @Override
        public void endTryCatch() {
            if (operationData.operationId != OP_TRY_CATCH) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("TryCatch expected 2 children, got " + numChildren);
            }
            doEmitLabel(((BuilderOperationLabel) operationData.aux[1]));
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginFinallyTry() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_FINALLY_TRY, getCurStack(), 3, true);
            operationData.aux[2] = createParentLocal();
            operationData.aux[0] = doBeginFinallyTry();
        }

        @SuppressWarnings("unused")
        @Override
        public void endFinallyTry() {
            if (operationData.operationId != OP_FINALLY_TRY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("FinallyTry expected 2 children, got " + numChildren);
            }
            int endBci = bci;
            doLeaveFinallyTry(operationData);
            BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
            {
                calculateLeaves(operationData, (BuilderOperationLabel) endLabel);
                int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                createOffset(bci + 2, endLabel);
                bci = bci + 4;
            }
            BuilderExceptionHandler beh = ((BuilderExceptionHandler) operationData.aux[1]);
            beh.endBci = endBci;
            beh.handlerBci = bci;
            doLeaveFinallyTry(operationData);
            {
                int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                LE_BYTES.putShort(bc, bci, (short) INSTR_THROW);
                LE_BYTES.putShort(bc, bci + 2, (short) (int) getLocalIndex(operationData.aux[2]));
                bci = bci + 4;
            }
            doEmitLabel(endLabel);
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginFinallyTryNoExcept() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_FINALLY_TRY_NO_EXCEPT, getCurStack(), 1, true);
            operationData.aux[0] = doBeginFinallyTry();
        }

        @SuppressWarnings("unused")
        @Override
        public void endFinallyTryNoExcept() {
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

        @Override
        public void emitLabel(OperationLabel arg0) {
            doBeforeChild();
            doEmitLabel(arg0);
            lastChildPush = 0;
            doAfterChild();
        }

        @Override
        public void emitBranch(OperationLabel arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BRANCH, getCurStack(), 0, false, arg0);
            calculateLeaves(operationData, (BuilderOperationLabel) operationData.arguments[0]);
            int[] predecessorBcis = doBeforeEmitInstruction(0, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
            createOffset(bci + 2, operationData.arguments[0]);
            bci = bci + 4;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitConstObject(Object arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_CONST_OBJECT, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_CONSTANT_OBJECT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) constPool.add(arg0));
            bci = bci + 4;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadArgument(int arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_ARGUMENT, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_ARGUMENT_OBJECT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) operationData.arguments[0]);
            bci = bci + 4;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginStoreLocal(OperationLocal arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_STORE_LOCAL, getCurStack(), 0, false, arg0);
        }

        @SuppressWarnings("unused")
        @Override
        public void endStoreLocal() {
            if (operationData.operationId != OP_STORE_LOCAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("StoreLocal expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_STORE_LOCAL_UNINIT);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            LE_BYTES.putShort(bc, bci + 3, (short) (int) getLocalIndex(operationData.arguments[0]));
            bci = bci + 5;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadLocal(OperationLocal arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_LOCAL, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_LOCAL_UNINIT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) getLocalIndex(operationData.arguments[0]));
            bci = bci + 4;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginReturn() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_RETURN, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endReturn() {
            if (operationData.operationId != OP_RETURN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("Return expected 1 children, got " + numChildren);
            }
            calculateLeaves(operationData);
            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_RETURN);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bci = bci + 3;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginTag(Class<?> arg0) {
            if (!withInstrumentation) {
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_TAG, getCurStack(), 3, true, arg0);
            int curInstrumentId = doBeginInstrumentation((Class) arg0);
            BuilderOperationLabel startLabel = (BuilderOperationLabel) createLabel();
            BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
            doEmitLabel(startLabel);
            operationData.aux[0] = curInstrumentId;
            operationData.aux[1] = startLabel;
            operationData.aux[2] = endLabel;
            int[] predecessorBcis = doBeforeEmitInstruction(0, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_ENTER);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) curInstrumentId);
            bci = bci + 4;
            lastChildPush = 0;
        }

        @SuppressWarnings("unused")
        @Override
        public void endTag() {
            if (!withInstrumentation) {
                return;
            }
            if (operationData.operationId != OP_TAG) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("Tag expected at least 0 children, got " + numChildren);
            }
            if (lastChildPush != 0) {
                int[] predecessorBcis = doBeforeEmitInstruction(0, false);
                LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_EXIT_VOID);
                LE_BYTES.putShort(bc, bci + 2, (short) (int) ((int) operationData.aux[0]));
                bci = bci + 4;
            } else {
                int[] predecessorBcis = doBeforeEmitInstruction(1, true);
                LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_EXIT);
                LE_BYTES.putShort(bc, bci + 2, (short) (int) ((int) operationData.aux[0]));
                bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                bci = bci + 5;
            }
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLAdd() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLADD, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLAdd() {
            if (operationData.operationId != OP_SLADD) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLAdd expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLADD);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 7 bytes: [BITS, BITS, BITS, CONST, CONTINUATION, CHILD, CONTINUATION]
            //   numChildNodes = 3
            //   numConsts     = 1
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 3, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 4 + 5, createChildNodes(3));
            bci = bci + 11;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLDiv() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLDIV, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLDiv() {
            if (operationData.operationId != OP_SLDIV) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLDiv expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLDIV);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLEqual() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLEQUAL, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLEqual() {
            if (operationData.operationId != OP_SLEQUAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLEqual expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLEQUAL);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 7 bytes: [BITS, BITS, CHILD, CONTINUATION, CONST, CONTINUATION, BITS]
            //   numChildNodes = 3
            //   numConsts     = 1
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 2, createChildNodes(3));
            LE_BYTES.putShort(bc, bci + 4 + 4, (short) constPool.reserve());
            bc[bci + 4 + 6] = 0;
            bci = bci + 11;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessOrEqual() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLESS_OR_EQUAL, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessOrEqual() {
            if (operationData.operationId != OP_SLLESS_OR_EQUAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessOrEqual expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLESS_OR_EQUAL);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bci = bci + 5;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessThan() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLESS_THAN, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessThan() {
            if (operationData.operationId != OP_SLLESS_THAN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessThan expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLESS_THAN);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bci = bci + 5;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLogicalNot() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLOGICAL_NOT, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLogicalNot() {
            if (operationData.operationId != OP_SLLOGICAL_NOT) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLLogicalNot expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLOGICAL_NOT);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 3 + 0] = 0;
            bci = bci + 4;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLMul() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLMUL, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLMul() {
            if (operationData.operationId != OP_SLMUL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLMul expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLMUL);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLReadProperty() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLREAD_PROPERTY, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLReadProperty() {
            if (operationData.operationId != OP_SLREAD_PROPERTY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLReadProperty expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLREAD_PROPERTY);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 6
            //   numConsts     = 3
            bc[bci + 4 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 4 + 3, createChildNodes(6));
            bc[bci + 4 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            bci = bci + 10;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLSub() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLSUB, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLSub() {
            if (operationData.operationId != OP_SLSUB) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLSub expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLSUB);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLWriteProperty() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLWRITE_PROPERTY, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLWriteProperty() {
            if (operationData.operationId != OP_SLWRITE_PROPERTY) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 3) {
                throw new IllegalStateException("SLWriteProperty expected 3 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(3, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLWRITE_PROPERTY);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            bc[bci + 4] = predecessorBcis[2] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[2]);
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 7
            //   numConsts     = 4
            bc[bci + 5 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 5 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 5 + 3, createChildNodes(7));
            bc[bci + 5 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            constPool.reserve();
            bci = bci + 11;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLUnbox() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLUNBOX, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLUnbox() {
            if (operationData.operationId != OP_SLUNBOX) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLUnbox expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLUNBOX);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 7 bytes: [BITS, BITS, CHILD, CONTINUATION, CONST, CONTINUATION, BITS]
            //   numChildNodes = 2
            //   numConsts     = 1
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            LE_BYTES.putShort(bc, bci + 3 + 2, createChildNodes(2));
            LE_BYTES.putShort(bc, bci + 3 + 4, (short) constPool.reserve());
            bc[bci + 3 + 6] = 0;
            bci = bci + 10;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLFunctionLiteral() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLFUNCTION_LITERAL, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLFunctionLiteral() {
            if (operationData.operationId != OP_SLFUNCTION_LITERAL) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLFunctionLiteral expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLFUNCTION_LITERAL);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 3 bytes: [BITS, CONST, CONTINUATION]
            //   numChildNodes = 0
            //   numConsts     = 1
            bc[bci + 3 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 3 + 1, (short) constPool.reserve());
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLToBoolean() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLTO_BOOLEAN, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLToBoolean() {
            if (operationData.operationId != OP_SLTO_BOOLEAN) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLToBoolean expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLTO_BOOLEAN);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 3 + 0] = 0;
            bci = bci + 4;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLAnd() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLAND, getCurStack(), 1, false);
            operationData.aux[0] = (BuilderOperationLabel) createLabel();
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLAnd() {
            if (operationData.operationId != OP_SLAND) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 1) {
                throw new IllegalStateException("SLAnd expected at least 1 children, got " + numChildren);
            }
            doEmitLabel(((BuilderOperationLabel) operationData.aux[0]));
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLOr() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLOR, getCurStack(), 1, false);
            operationData.aux[0] = (BuilderOperationLabel) createLabel();
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLOr() {
            if (operationData.operationId != OP_SLOR) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 1) {
                throw new IllegalStateException("SLOr expected at least 1 children, got " + numChildren);
            }
            doEmitLabel(((BuilderOperationLabel) operationData.aux[0]));
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLInvoke() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLINVOKE, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLInvoke() {
            if (operationData.operationId != OP_SLINVOKE) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("SLInvoke expected at least 0 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(numChildren, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLINVOKE);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            LE_BYTES.putShort(bc, bci + 3, (short) (int) (numChildren - 1));
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 3
            //   numConsts     = 3
            bc[bci + 5 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 5 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 5 + 3, createChildNodes(3));
            bc[bci + 5 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            bci = bci + 11;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void setMethodName(TruffleString value) {
            metadata_MethodName = value;
        }

        @Override
        protected void resetMetadata() {
            metadata_MethodName = null;
        }

        @Override
        protected void assignMetadata(OperationNode node) {
            OperationNodeImpl nodeImpl = (OperationNodeImpl) node;
            nodeImpl.MethodName = metadata_MethodName;
        }

        @GeneratedBy(SLOperations.class)
        private static final class OperationNodeImpl extends OperationNode {

            private TruffleString MethodName;

            private OperationNodeImpl(OperationNodes nodes, Object sourceInfo, OperationBytecodeNode bcNode) {
                super(nodes, sourceInfo, bcNode);
            }

            static  {
                setMetadataAccessor(SLOperations.METHOD_NAME, n -> ((OperationNodeImpl) n).MethodName);
            }

        }
        /**
         * pop
         *   Inputs:
         *     STACK_VALUE_IGNORED
         *   Results:
         *
         * branch
         *   Inputs:
         *     BRANCH_TARGET
         *   Results:
         *     BRANCH
         *
         * branch.false
         *   Inputs:
         *     BRANCH_TARGET
         *     STACK_VALUE
         *     BRANCH_PROFILE
         *   Results:
         *     BRANCH
         *
         * throw
         *   Inputs:
         *     LOCAL
         *   Results:
         *
         * load.constant.object
         *   Inputs:
         *     CONST_POOL
         *   Results:
         *     STACK_VALUE
         *
         * load.constant.boolean
         *   Inputs:
         *     CONST_POOL
         *   Results:
         *     STACK_VALUE
         *
         * load.constant.long
         *   Inputs:
         *     CONST_POOL
         *   Results:
         *     STACK_VALUE
         *
         * load.argument.object
         *   Inputs:
         *     ARGUMENT
         *   Results:
         *     STACK_VALUE
         *
         * load.argument.boolean
         *   Inputs:
         *     ARGUMENT
         *   Results:
         *     STACK_VALUE
         *
         * load.argument.long
         *   Inputs:
         *     ARGUMENT
         *   Results:
         *     STACK_VALUE
         *
         * store.local.object
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     SET_LOCAL
         *
         * store.local.boolean
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     SET_LOCAL
         *
         * store.local.long
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     SET_LOCAL
         *
         * store.local.uninit
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     SET_LOCAL
         *
         * load.local.object
         *   Inputs:
         *     LOCAL
         *   Results:
         *     STACK_VALUE
         *
         * load.local.boolean
         *   Inputs:
         *     LOCAL
         *   Results:
         *     STACK_VALUE
         *
         * load.local.long
         *   Inputs:
         *     LOCAL
         *   Results:
         *     STACK_VALUE
         *
         * load.local.uninit
         *   Inputs:
         *     LOCAL
         *   Results:
         *     STACK_VALUE
         *
         * return
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     RETURN
         *
         * instrument.enter
         *   Inputs:
         *     INSTRUMENT
         *   Results:
         *
         * instrument.exit.void
         *   Inputs:
         *     INSTRUMENT
         *   Results:
         *
         * instrument.exit
         *   Inputs:
         *     INSTRUMENT
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *
         * instrument.leave
         *   Inputs:
         *     INSTRUMENT
         *     BRANCH_TARGET
         *     BRANCH_TARGET
         *   Results:
         *     BRANCH
         *
         * c.SLAdd
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 BITS
         *     3 CONST
         *     5 CHILD
         *   Specializations:
         *     AddLong
         *     Add0
         *     Add1
         *     Fallback
         *
         * c.SLDiv
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *   Specializations:
         *     DivLong
         *     Div
         *     Fallback
         *
         * c.SLEqual
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 CHILD
         *     4 CONST
         *     6 BITS
         *   Specializations:
         *     Long
         *     BigNumber
         *     Boolean
         *     String
         *     TruffleString
         *     Null
         *     Function
         *     Generic0
         *     Generic1
         *     Fallback
         *
         * c.SLLessOrEqual
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     LessOrEqual0
         *     LessOrEqual1
         *     Fallback
         *
         * c.SLLessThan
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     LessThan0
         *     LessThan1
         *     Fallback
         *
         * c.SLLogicalNot
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Boolean
         *     Fallback
         *
         * c.SLMul
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *   Specializations:
         *     MulLong
         *     Mul
         *     Fallback
         *
         * c.SLReadProperty
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     ReadArray0
         *     ReadArray1
         *     ReadSLObject0
         *     ReadSLObject1
         *     ReadObject0
         *     ReadObject1
         *     Fallback
         *
         * c.SLSub
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *   Specializations:
         *     SubLong
         *     Sub
         *     Fallback
         *
         * c.SLWriteProperty
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     WriteArray0
         *     WriteArray1
         *     WriteSLObject0
         *     WriteSLObject1
         *     WriteObject0
         *     WriteObject1
         *     Fallback
         *
         * c.SLUnbox
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 CHILD
         *     4 CONST
         *     6 BITS
         *   Specializations:
         *     FromString
         *     FromTruffleString
         *     FromBoolean
         *     FromLong
         *     FromBigNumber
         *     FromFunction0
         *     FromFunction1
         *     FromForeign0
         *     FromForeign1
         *     Fallback
         *
         * c.SLFunctionLiteral
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *   Specializations:
         *     Perform
         *     Fallback
         *
         * c.SLToBoolean
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Boolean
         *     Fallback
         *
         * sc.SLAnd
         *   Inputs:
         *     STACK_VALUE
         *     BRANCH_TARGET
         *   Results:
         *     BRANCH
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Boolean
         *     Fallback
         *
         * sc.SLOr
         *   Inputs:
         *     STACK_VALUE
         *     BRANCH_TARGET
         *   Results:
         *     BRANCH
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Boolean
         *     Fallback
         *
         * c.SLInvoke
         *   Inputs:
         *     STACK_VALUE
         *     VARARG_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     Direct
         *     Indirect
         *     Interop
         *     Fallback
         *
         * c.SLUnbox.q.FromLong
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 BITS
         *     3 CHILD
         *     5 CONST
         *   Specializations:
         *     FromString
         *     FromTruffleString
         *     FromBoolean
         *     FromLong
         *     FromBigNumber
         *     FromFunction0
         *     FromFunction1
         *     FromForeign0
         *     FromForeign1
         *     Fallback
         *
         * c.SLAdd.q.AddLong
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 BITS
         *     3 CHILD
         *     5 CONST
         *   Specializations:
         *     AddLong
         *     Add0
         *     Add1
         *     Fallback
         *
         * c.SLReadProperty.q.ReadSLObject0
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     ReadArray0
         *     ReadArray1
         *     ReadSLObject0
         *     ReadSLObject1
         *     ReadObject0
         *     ReadObject1
         *     Fallback
         *
         * c.SLUnbox.q.FromBoolean
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 BITS
         *     2 BITS
         *     3 CHILD
         *     5 CONST
         *   Specializations:
         *     FromString
         *     FromTruffleString
         *     FromBoolean
         *     FromLong
         *     FromBigNumber
         *     FromFunction0
         *     FromFunction1
         *     FromForeign0
         *     FromForeign1
         *     Fallback
         *
         * c.SLToBoolean.q.Boolean
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Boolean
         *     Fallback
         *
         * c.SLLessOrEqual.q.LessOrEqual0
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     LessOrEqual0
         *     LessOrEqual1
         *     Fallback
         *
         * c.SLInvoke.q.Direct
         *   Inputs:
         *     STACK_VALUE
         *     VARARG_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     Direct
         *     Indirect
         *     Interop
         *     Fallback
         *
         * c.SLFunctionLiteral.q.Perform
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *   Specializations:
         *     Perform
         *     Fallback
         *
         * c.SLWriteProperty.q.WriteSLObject0
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *     1 CONST
         *     3 CHILD
         *     5 BITS
         *   Specializations:
         *     WriteArray0
         *     WriteArray1
         *     WriteSLObject0
         *     WriteSLObject1
         *     WriteObject0
         *     WriteObject1
         *     Fallback
         *
         * c.SLLessThan.q.LessThan0
         *   Inputs:
         *     STACK_VALUE
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     LessThan0
         *     LessThan1
         *     Fallback
         *

         */
        @GeneratedBy(SLOperations.class)
        private static final class BytecodeNode extends OperationBytecodeNode implements Provider {

            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);

            BytecodeNode(int maxStack, int maxLocals, byte[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers, ConditionProfile[] conditionProfiles) {
                super(maxStack, maxLocals, bc, consts, children, handlers, conditionProfiles);
            }

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            @Override
            protected Object continueAt(VirtualFrame frame, int startBci, int startSp) {
                int sp = startSp;
                int bci = startBci;
                Object returnValue = null;
                loop: while (true) {
                    int nextBci;
                    short curOpcode = LE_BYTES.getShort(bc, bci);
                    try {
                        CompilerAsserts.partialEvaluationConstant(bci);
                        CompilerAsserts.partialEvaluationConstant(sp);
                        CompilerAsserts.partialEvaluationConstant(curOpcode);
                        if (sp < maxLocals + VALUES_OFFSET) {
                            throw CompilerDirectives.shouldNotReachHere("stack underflow");
                        }
                        switch (curOpcode) {
                            case INSTR_POP :
                            {
                                sp = sp - 1;
                                frame.clear(sp);
                                nextBci = bci + 2;
                                break;
                            }
                            case INSTR_BRANCH :
                            {
                                int targetBci = LE_BYTES.getShort(bc, bci + 2);
                                if (targetBci <= bci) {
                                    TruffleSafepoint.poll(this);
                                    if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this)) {
                                        Object osrResult = BytecodeOSRNode.tryOSR(this, targetBci, sp, null, frame);
                                        if (osrResult != null) {
                                            return osrResult;
                                        }
                                    }
                                }
                                bci = targetBci;
                                continue loop;
                            }
                            case INSTR_BRANCH_FALSE :
                            {
                                ConditionProfile profile = conditionProfiles[LE_BYTES.getShort(bc, bci + 5)];
                                boolean cond = (boolean) frame.getObject(sp - 1);
                                sp -= 1;
                                if (profile.profile(cond)) {
                                    bci = bci + 7;
                                    continue loop;
                                } else {
                                    bci = LE_BYTES.getShort(bc, bci + 2);
                                    continue loop;
                                }
                            }
                            case INSTR_THROW :
                            {
                                int slot = LE_BYTES.getShort(bc, bci + 2);
                                throw (AbstractTruffleException) frame.getObject(slot);
                            }
                            case INSTR_LOAD_CONSTANT_OBJECT :
                            {
                                frame.setObject(sp, consts[LE_BYTES.getShort(bc, bci + 2)]);
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_CONSTANT_BOOLEAN :
                            {
                                frame.setBoolean(sp, (boolean) consts[LE_BYTES.getShort(bc, bci + 2)]);
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_CONSTANT_LONG :
                            {
                                frame.setLong(sp, (long) consts[LE_BYTES.getShort(bc, bci + 2)]);
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_ARGUMENT_OBJECT :
                            {
                                Object value = frame.getArguments()[LE_BYTES.getShort(bc, bci + 2)];
                                frame.setObject(sp, value);
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_ARGUMENT_BOOLEAN :
                            {
                                Object value = frame.getArguments()[LE_BYTES.getShort(bc, bci + 2)];
                                if (value instanceof Boolean) {
                                    frame.setBoolean(sp, (boolean) value);
                                } else {
                                    frame.setObject(sp, value);
                                }
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_ARGUMENT_LONG :
                            {
                                Object value = frame.getArguments()[LE_BYTES.getShort(bc, bci + 2)];
                                if (value instanceof Long) {
                                    frame.setLong(sp, (long) value);
                                } else {
                                    frame.setObject(sp, value);
                                }
                                sp = sp + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_STORE_LOCAL_OBJECT :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 3) + VALUES_OFFSET;
                                int sourceSlot = sp - 1;
                                frame.setObject(localIdx, expectObject(frame, sourceSlot));
                                // here:
                                sp--;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_STORE_LOCAL_BOOLEAN :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 3) + VALUES_OFFSET;
                                int sourceSlot = sp - 1;
                                FrameSlotKind localTag = frame.getFrameDescriptor().getSlotKind(localIdx);
                                do {
                                    if (localTag == FrameSlotKind.Boolean) {
                                        try {
                                            frame.setBoolean(localIdx, expectBoolean(frame, sourceSlot));
                                            break /* goto here */;
                                        } catch (UnexpectedResultException ex) {
                                        }
                                    }
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    LE_BYTES.putShort(bc, bci, (short) INSTR_STORE_LOCAL_OBJECT);
                                    doSetResultBoxed(bc, bci, bc[bci + 2], FRAME_TYPE_OBJECT);
                                    frame.setObject(localIdx, expectObject(frame, sourceSlot));
                                } while (false);
                                // here:
                                sp--;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_STORE_LOCAL_LONG :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 3) + VALUES_OFFSET;
                                int sourceSlot = sp - 1;
                                FrameSlotKind localTag = frame.getFrameDescriptor().getSlotKind(localIdx);
                                do {
                                    if (localTag == FrameSlotKind.Long) {
                                        try {
                                            frame.setLong(localIdx, expectLong(frame, sourceSlot));
                                            break /* goto here */;
                                        } catch (UnexpectedResultException ex) {
                                        }
                                    }
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    LE_BYTES.putShort(bc, bci, (short) INSTR_STORE_LOCAL_OBJECT);
                                    doSetResultBoxed(bc, bci, bc[bci + 2], FRAME_TYPE_OBJECT);
                                    frame.setObject(localIdx, expectObject(frame, sourceSlot));
                                } while (false);
                                // here:
                                sp--;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_STORE_LOCAL_UNINIT :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 3) + VALUES_OFFSET;
                                int sourceSlot = sp - 1;
                                FrameSlotKind localTag = frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localTag == FrameSlotKind.Illegal) {
                                    assert frame.isObject(sourceSlot);
                                    frame.copy(sourceSlot, localIdx);
                                } else {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    int resultTag = storeLocalInitialization(frame, localIdx, localTag.tag, sourceSlot);
                                    setResultBoxedImpl(bc, bci, resultTag, BOXING_DESCRIPTORS[resultTag]);
                                    doSetResultBoxed(bc, bci, bc[bci + 2], resultTag);
                                }
                                // here:
                                sp--;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_OBJECT :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET;
                                if (frame.getFrameDescriptor().getSlotKind(localIdx) != FrameSlotKind.Object) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    frame.setObject(localIdx, frame.getValue(localIdx));
                                }
                                frame.copy(localIdx, sp);
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_BOOLEAN :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET;
                                FrameSlotKind localType = frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localType != FrameSlotKind.Boolean) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    Object localValue;
                                    if (localType == FrameSlotKind.Illegal && (localValue = frame.getObject(localIdx)) instanceof Boolean) {
                                        frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Boolean);
                                        frame.setBoolean(localIdx, (boolean) localValue);
                                    } else {
                                        frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    }
                                }
                                frame.copy(localIdx, sp);
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_LONG :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET;
                                FrameSlotKind localType = frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localType != FrameSlotKind.Long) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    Object localValue;
                                    if (localType == FrameSlotKind.Illegal && (localValue = frame.getObject(localIdx)) instanceof Long) {
                                        frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Long);
                                        frame.setLong(localIdx, (long) localValue);
                                    } else {
                                        frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    }
                                }
                                frame.copy(localIdx, sp);
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_UNINIT :
                            {
                                int localIdx = LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET;
                                frame.setObject(sp, expectObject(frame, localIdx));
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_RETURN :
                            {
                                returnValue = frame.getObject(sp - 1);
                                break loop;
                            }
                            case INSTR_C_SLADD :
                            {
                                this.SLAdd_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLDIV :
                            {
                                this.SLDiv_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLEQUAL :
                            {
                                this.SLEqual_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLLESS_OR_EQUAL :
                            {
                                this.SLLessOrEqual_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLLESS_THAN :
                            {
                                this.SLLessThan_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLLOGICAL_NOT :
                            {
                                this.SLLogicalNot_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLMUL :
                            {
                                this.SLMul_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLREAD_PROPERTY :
                            {
                                this.SLReadProperty_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLSUB :
                            {
                                this.SLSub_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLWRITE_PROPERTY :
                            {
                                this.SLWriteProperty_execute_(frame, bci, sp);
                                sp = sp - 3 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLUNBOX :
                            {
                                this.SLUnbox_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLFUNCTION_LITERAL :
                            {
                                this.SLFunctionLiteral_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLTO_BOOLEAN :
                            {
                                this.SLToBoolean_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_SC_SLAND :
                            {
                                if (this.SLAnd_execute_(frame, bci, sp)) {
                                    sp = sp - 1;
                                    bci = bci + 6;
                                    continue loop;
                                } else {
                                    bci = LE_BYTES.getShort(bc, bci + 3);
                                    continue loop;
                                }
                            }
                            case INSTR_SC_SLOR :
                            {
                                if (!this.SLOr_execute_(frame, bci, sp)) {
                                    sp = sp - 1;
                                    bci = bci + 6;
                                    continue loop;
                                } else {
                                    bci = LE_BYTES.getShort(bc, bci + 3);
                                    continue loop;
                                }
                            }
                            case INSTR_C_SLINVOKE :
                            {
                                int numVariadics = LE_BYTES.getShort(bc, bci + 3);
                                Object input_0 = frame.getObject(sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = frame.getObject(sp - numVariadics + varIndex);
                                }
                                Object result = this.SLInvoke_execute_(frame, bci, sp, input_0, input_1);
                                sp = sp - 1 - numVariadics + 1;
                                frame.setObject(sp - 1, result);
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLUNBOX_Q_FROM_LONG :
                            {
                                this.SLUnbox_q_FromLong_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLADD_Q_ADD_LONG :
                            {
                                this.SLAdd_q_AddLong_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLREAD_PROPERTY_Q_READ_SLOBJECT0 :
                            {
                                this.SLReadProperty_q_ReadSLObject0_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLUNBOX_Q_FROM_BOOLEAN :
                            {
                                this.SLUnbox_q_FromBoolean_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLTO_BOOLEAN_Q_BOOLEAN :
                            {
                                this.SLToBoolean_q_Boolean_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLLESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                            {
                                this.SLLessOrEqual_q_LessOrEqual0_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLINVOKE_Q_DIRECT :
                            {
                                int numVariadics = LE_BYTES.getShort(bc, bci + 3);
                                Object input_0 = frame.getObject(sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = frame.getObject(sp - numVariadics + varIndex);
                                }
                                Object result = this.SLInvoke_q_Direct_execute_(frame, bci, sp, input_0, input_1);
                                sp = sp - 1 - numVariadics + 1;
                                frame.setObject(sp - 1, result);
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLFUNCTION_LITERAL_Q_PERFORM :
                            {
                                this.SLFunctionLiteral_q_Perform_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLWRITE_PROPERTY_Q_WRITE_SLOBJECT0 :
                            {
                                this.SLWriteProperty_q_WriteSLObject0_execute_(frame, bci, sp);
                                sp = sp - 3 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLLESS_THAN_Q_LESS_THAN0 :
                            {
                                this.SLLessThan_q_LessThan0_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            default :
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw CompilerDirectives.shouldNotReachHere("unknown opcode encountered");
                        }
                    } catch (AbstractTruffleException ex) {
                        CompilerAsserts.partialEvaluationConstant(bci);
                        for (int handlerIndex = handlers.length - 1; handlerIndex >= 0; handlerIndex--) {
                            CompilerAsserts.partialEvaluationConstant(handlerIndex);
                            BuilderExceptionHandler handler = handlers[handlerIndex];
                            if (handler.startBci > bci || handler.endBci <= bci) continue;
                            sp = handler.startStack + VALUES_OFFSET + maxLocals;
                            frame.setObject(VALUES_OFFSET + handler.exceptionIndex, ex);
                            bci = handler.handlerBci;
                            continue loop;
                        }
                        throw ex;
                    }
                    CompilerAsserts.partialEvaluationConstant(nextBci);
                    bci = nextBci;
                }
                return returnValue;
            }

            private void SLAdd_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */ && ((state_0 & 0b11110) != 0  /* is-not addLong(long, long) && add(SLBigNumber, SLBigNumber) && add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) && typeError(Object, Object, Node, int) */)) {
                    SLAdd_SLAdd_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLAdd_SLAdd_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLAdd_SLAdd_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */);
                try {
                    long value = SLAddNode.addLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    // implicit transferToInterpreterAndInvalidate()
                    Lock lock = getLock();
                    lock.lock();
                    try {
                        bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude addLong(long, long) */);
                        bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLAdd_SLAdd_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if (((state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */) && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        try {
                            long value = SLAddNode.addLong($child0Value__, $child1Value__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setLong($sp - 2, value);
                            }
                            return;
                        } catch (ArithmeticException ex) {
                            // implicit transferToInterpreterAndInvalidate()
                            Lock lock = getLock();
                            lock.lock();
                            try {
                                bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude addLong(long, long) */);
                                bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
                            return;
                        }
                    }
                }
                if (((state_0 & 0b100) != 0 /* is-state_0 add(SLBigNumber, SLBigNumber) */) && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber(bc[$bci + 4 + 2] >>> 0 /* extract-implicit-state_1 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber(bc[$bci + 4 + 2] >>> 0 /* extract-implicit-state_1 1:SLBigNumber */, $child1Value_);
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value__, $child1Value__));
                        return;
                    }
                }
                if (((state_0 & 0b11000) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) || typeError(Object, Object, Node, int) */)) {
                    if (((state_0 & 0b1000) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */)) {
                        SLAdd_Add1Data s2_ = ((SLAdd_Add1Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]);
                        if (s2_ != null) {
                            if ((SLAddNode.isString($child0Value_, $child1Value_))) {
                                $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 0]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 1]), ((ConcatNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 2])));
                                return;
                            }
                        }
                    }
                    if (((state_0 & 0b10000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */)) {
                        {
                            Node fallback_node__ = (this);
                            int fallback_bci__ = ($bci);
                            if (SLAdd_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_, $child1Value_)) {
                                $frame.setObject($sp - 2, SLAddNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLAdd_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte state_1 = bc[$bci + 4 + 2];
                    byte exclude = bc[$bci + 4 + 1];
                    if ((exclude) == 0 /* is-not-exclude addLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 addLong(long, long) */);
                            bc[$bci + 4 + 2] = (byte) (state_1);
                            if ((state_0 & 0b11110) == 0b10/* is-exact-state_0 addLong(long, long) */) {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_Q_ADD_LONG);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            try {
                                lock.unlock();
                                hasLock = false;
                                long value = SLAddNode.addLong($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setLong($sp - 2, value);
                                }
                                return;
                            } catch (ArithmeticException ex) {
                                // implicit transferToInterpreterAndInvalidate()
                                lock.lock();
                                try {
                                    bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude addLong(long, long) */);
                                    bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                                return;
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
                                bc[$bci + 4 + 1] = exclude = (byte) (exclude | 0b1 /* add-exclude addLong(long, long) */);
                                state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 5) /* set-implicit-state_0 0:SLBigNumber */);
                                state_1 = (byte) (state_1 | (sLBigNumberCast1 << 0) /* set-implicit-state_1 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 add(SLBigNumber, SLBigNumber) */);
                                bc[$bci + 4 + 2] = (byte) (state_1);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_));
                                return;
                            }
                        }
                    }
                    if ((SLAddNode.isString($child0Value, $child1Value))) {
                        SLAdd_Add1Data s2_ = super.insert(new SLAdd_Add1Data());
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 0] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 1] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 2] = s2_.insertAccessor((ConcatNode.create()));
                        VarHandle.storeStoreFence();
                        consts[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0] = s2_;
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */);
                        bc[$bci + 4 + 2] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value, $child1Value, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 0]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 1]), ((ConcatNode) children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 2])));
                        return;
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        bc[$bci + 4 + 2] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLAddNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLDiv_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not divLong(long, long) && div(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLDiv_SLDiv_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLDiv_SLDiv_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLDiv_SLDiv_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLDiv_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLDiv_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 divLong(long, long) */;
                try {
                    long value = SLDivNode.divLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    // implicit transferToInterpreterAndInvalidate()
                    Lock lock = getLock();
                    lock.lock();
                    try {
                        bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude divLong(long, long) */);
                        bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLDiv_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLDiv_SLDiv_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 divLong(long, long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        try {
                            long value = SLDivNode.divLong($child0Value__, $child1Value__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setLong($sp - 2, value);
                            }
                            return;
                        } catch (ArithmeticException ex) {
                            // implicit transferToInterpreterAndInvalidate()
                            Lock lock = getLock();
                            lock.lock();
                            try {
                                bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude divLong(long, long) */);
                                bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLDiv_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
                            return;
                        }
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 div(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        $frame.setObject($sp - 2, SLDivNode.div($child0Value__, $child1Value__));
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLDiv_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLDivNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLDiv_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLDiv_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte exclude = bc[$bci + 4 + 1];
                    if ((exclude) == 0 /* is-not-exclude divLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 divLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            try {
                                lock.unlock();
                                hasLock = false;
                                long value = SLDivNode.divLong($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setLong($sp - 2, value);
                                }
                                return;
                            } catch (ArithmeticException ex) {
                                // implicit transferToInterpreterAndInvalidate()
                                lock.lock();
                                try {
                                    bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude divLong(long, long) */);
                                    bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLDiv_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                                return;
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
                                bc[$bci + 4 + 1] = exclude = (byte) (exclude | 0b1 /* add-exclude divLong(long, long) */);
                                state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 div(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLDivNode.div($child0Value_, $child1Value_));
                                return;
                            }
                        }
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLDivNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLEqual_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                byte state_1 = bc[$bci + 4 + 1];
                if ((state_0 & 0b11111100) == 0 /* only-active doLong(long, long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqual_SLEqual_execute__long_long0_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else if ((state_0 & 0b11110110) == 0 /* only-active doBoolean(boolean, boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqual_SLEqual_execute__boolean_boolean1_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else {
                    SLEqual_SLEqual_execute__generic2_($frame, $bci, $sp, state_0, state_1);
                    return;
                }
            }

            private void SLEqual_SLEqual_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLEqual_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b10) != 0 /* is-state_0 doLong(long, long) */);
                boolean value = SLEqualNode.doLong($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private void SLEqual_SLEqual_execute__boolean_boolean1_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLEqual_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                boolean $child1Value_;
                try {
                    $child1Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b1000) != 0 /* is-state_0 doBoolean(boolean, boolean) */);
                boolean value = SLEqualNode.doBoolean($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLEqual_generic1Boundary_(int $bci, int $sp, byte state_0, byte state_1, Object $child0Value_, Object $child1Value_) {
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set(this);
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
            private void SLEqual_SLEqual_execute__generic2_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if (((state_0 & 0b10) != 0 /* is-state_0 doLong(long, long) */) && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        boolean value = SLEqualNode.doLong($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b100) != 0 /* is-state_0 doBigNumber(SLBigNumber, SLBigNumber) */) && SLTypesGen.isImplicitSLBigNumber((state_1 & 0b1100) >>> 2 /* extract-implicit-state_1 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_1 & 0b1100) >>> 2 /* extract-implicit-state_1 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_1 & 0b110000) >>> 4 /* extract-implicit-state_1 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_1 & 0b110000) >>> 4 /* extract-implicit-state_1 1:SLBigNumber */, $child1Value_);
                        boolean value = SLEqualNode.doBigNumber($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b1000) != 0 /* is-state_0 doBoolean(boolean, boolean) */) && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    if ($child1Value_ instanceof Boolean) {
                        boolean $child1Value__ = (boolean) $child1Value_;
                        boolean value = SLEqualNode.doBoolean($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b10000) != 0 /* is-state_0 doString(String, String) */) && $child0Value_ instanceof String) {
                    String $child0Value__ = (String) $child0Value_;
                    if ($child1Value_ instanceof String) {
                        String $child1Value__ = (String) $child1Value_;
                        boolean value = SLEqualNode.doString($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b100000) != 0 /* is-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */) && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    if ($child1Value_ instanceof TruffleString) {
                        TruffleString $child1Value__ = (TruffleString) $child1Value_;
                        boolean value = SLEqualNode.doTruffleString($child0Value__, $child1Value__, ((EqualNode) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 0]));
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b1000000) != 0 /* is-state_0 doNull(SLNull, SLNull) */) && SLTypes.isSLNull($child0Value_)) {
                    SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                    if (SLTypes.isSLNull($child1Value_)) {
                        SLNull $child1Value__ = SLTypes.asSLNull($child1Value_);
                        boolean value = SLEqualNode.doNull($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (((state_0 & 0b10000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0 /* is-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    if (((state_0 & 0b10000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */) && $child0Value_ instanceof SLFunction) {
                        SLFunction $child0Value__ = (SLFunction) $child0Value_;
                        boolean value = SLEqualNode.doFunction($child0Value__, $child1Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                    if (((state_1 & 0b11) != 0 /* is-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                        if (((state_1 & 0b1) != 0 /* is-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                            SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0]);
                            while (s7_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1]).accepts($child0Value_)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2]).accepts($child1Value_))) {
                                    boolean value = SLEqualNode.doGeneric($child0Value_, $child1Value_, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2]));
                                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                        $frame.setObject($sp - 2, value);
                                    } else {
                                        $frame.setBoolean($sp - 2, value);
                                    }
                                    return;
                                }
                                s7_ = s7_.next_;
                            }
                        }
                        if (((state_1 & 0b10) != 0 /* is-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                            $frame.setObject($sp - 2, this.SLEqual_generic1Boundary_($bci, $sp, state_0, state_1, $child0Value_, $child1Value_));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLEqual_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte state_1 = bc[$bci + 4 + 1];
                    byte exclude = bc[$bci + 4 + 6];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doLong(long, long) */);
                            bc[$bci + 4 + 1] = (byte) (state_1);
                            int type0;
                            int type1;
                            if ((state_0 & 0b11111100) == 0 /* only-active doLong(long, long) */ && (state_1 & 0b11) == 0 /* only-active  */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doLong($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    {
                        int sLBigNumberCast0;
                        if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                            SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                            int sLBigNumberCast1;
                            if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                                SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                                state_1 = (byte) (state_1 | (sLBigNumberCast0 << 2) /* set-implicit-state_1 0:SLBigNumber */);
                                state_1 = (byte) (state_1 | (sLBigNumberCast1 << 4) /* set-implicit-state_1 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 doBigNumber(SLBigNumber, SLBigNumber) */);
                                bc[$bci + 4 + 1] = (byte) (state_1);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                boolean value = SLEqualNode.doBigNumber($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setBoolean($sp - 2, value);
                                }
                                return;
                            }
                        }
                    }
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        if ($child1Value instanceof Boolean) {
                            boolean $child1Value_ = (boolean) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 doBoolean(boolean, boolean) */);
                            bc[$bci + 4 + 1] = (byte) (state_1);
                            int type0;
                            int type1;
                            if ((state_0 & 0b11110110) == 0 /* only-active doBoolean(boolean, boolean) */ && (state_1 & 0b11) == 0 /* only-active  */) {
                                type0 = FRAME_TYPE_BOOLEAN;
                                type1 = FRAME_TYPE_BOOLEAN;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doBoolean($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    if ($child0Value instanceof String) {
                        String $child0Value_ = (String) $child0Value;
                        if ($child1Value instanceof String) {
                            String $child1Value_ = (String) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 doString(String, String) */);
                            bc[$bci + 4 + 1] = (byte) (state_1);
                            int type0;
                            int type1;
                            type0 = FRAME_TYPE_OBJECT;
                            type1 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doString($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    if ($child0Value instanceof TruffleString) {
                        TruffleString $child0Value_ = (TruffleString) $child0Value;
                        if ($child1Value instanceof TruffleString) {
                            TruffleString $child1Value_ = (TruffleString) $child1Value;
                            children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 0] = super.insert((EqualNode.create()));
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */);
                            bc[$bci + 4 + 1] = (byte) (state_1);
                            int type0;
                            int type1;
                            type0 = FRAME_TYPE_OBJECT;
                            type1 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doTruffleString($child0Value_, $child1Value_, ((EqualNode) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 0]));
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    if (SLTypes.isSLNull($child0Value)) {
                        SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                        if (SLTypes.isSLNull($child1Value)) {
                            SLNull $child1Value_ = SLTypes.asSLNull($child1Value);
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 doNull(SLNull, SLNull) */);
                            bc[$bci + 4 + 1] = (byte) (state_1);
                            int type0;
                            int type1;
                            type0 = FRAME_TYPE_OBJECT;
                            type1 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doNull($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    if ($child0Value instanceof SLFunction) {
                        SLFunction $child0Value_ = (SLFunction) $child0Value;
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10000000 /* add-state_0 doFunction(SLFunction, Object) */);
                        bc[$bci + 4 + 1] = (byte) (state_1);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        boolean value = SLEqualNode.doFunction($child0Value_, $child1Value);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                    if ((exclude) == 0 /* is-not-exclude doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        int count7_ = 0;
                        SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0]);
                        if (((state_1 & 0b1) != 0 /* is-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                            while (s7_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1]).accepts($child0Value)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2]).accepts($child1Value))) {
                                    break;
                                }
                                s7_ = s7_.next_;
                                count7_++;
                            }
                        }
                        if (s7_ == null) {
                            // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1]).accepts($child0Value));
                            // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2]).accepts($child1Value));
                            if (count7_ < (4)) {
                                s7_ = super.insert(new SLEqual_Generic0Data(((SLEqual_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0])));
                                children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1] = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2] = s7_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                VarHandle.storeStoreFence();
                                consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0] = s7_;
                                bc[$bci + 4 + 0] = (byte) (state_0);
                                bc[$bci + 4 + 1] = state_1 = (byte) (state_1 | 0b1 /* add-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            }
                        }
                        if (s7_ != null) {
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doGeneric($child0Value, $child1Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 1]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 2) + 2]));
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    {
                        InteropLibrary generic1_rightInterop__ = null;
                        InteropLibrary generic1_leftInterop__ = null;
                        {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value));
                                bc[$bci + 4 + 6] = exclude = (byte) (exclude | 0b1 /* add-exclude doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0] = null;
                                state_1 = (byte) (state_1 & 0xfffffffe /* remove-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                bc[$bci + 4 + 0] = (byte) (state_0);
                                bc[$bci + 4 + 1] = state_1 = (byte) (state_1 | 0b10 /* add-state_1 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                boolean value = SLEqualNode.doGeneric($child0Value, $child1Value, generic1_leftInterop__, generic1_rightInterop__);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setBoolean($sp - 2, value);
                                }
                                return;
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

            private void SLLessOrEqual_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessOrEqual_SLLessOrEqual_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLessOrEqual_SLLessOrEqual_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLessOrEqual_SLLessOrEqual_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessOrEqual_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLLessOrEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(long, long) */;
                boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private void SLLessOrEqual_SLLessOrEqual_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(long, long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLLessOrEqual_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessOrEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLLessOrEqual_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 lessOrEqual(long, long) */);
                            if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 lessOrEqual(long, long) */) {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL_Q_LESS_OR_EQUAL0);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    {
                        int sLBigNumberCast0;
                        if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                            SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                            int sLBigNumberCast1;
                            if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                                SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setBoolean($sp - 2, value);
                                }
                                return;
                            }
                        }
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLLessThan_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessThan_SLLessThan_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLessThan_SLLessThan_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLessThan_SLLessThan_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessThan_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLLessThan_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 lessThan(long, long) */;
                boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private void SLLessThan_SLLessThan_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 lessThan(long, long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        boolean value = SLLessThanNode.lessThan($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 lessThan(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        boolean value = SLLessThanNode.lessThan($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLLessThan_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessThan_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLLessThan_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 lessThan(long, long) */);
                            if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 lessThan(long, long) */) {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN_Q_LESS_THAN0);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                    {
                        int sLBigNumberCast0;
                        if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                            SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                            int sLBigNumberCast1;
                            if ((sLBigNumberCast1 = SLTypesGen.specializeImplicitSLBigNumber($child1Value)) != 0) {
                                SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast1, $child1Value);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 lessThan(SLBigNumber, SLBigNumber) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setBoolean($sp - 2, value);
                                }
                                return;
                            }
                        }
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLLogicalNot_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && typeError(Object, Node, int) */)) {
                    SLLogicalNot_SLLogicalNot_execute__boolean0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLogicalNot_SLLogicalNot_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLogicalNot_SLLogicalNot_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLLogicalNot_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */;
                boolean value = SLLogicalNotNode.doBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private void SLLogicalNot_SLLogicalNot_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    boolean value = SLLogicalNotNode.doBoolean($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 typeError(Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLLogicalNot_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                            $frame.setObject($sp - 1, SLLogicalNotNode.typeError($child0Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLogicalNot_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLLogicalNot_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */) {
                            type0 = FRAME_TYPE_BOOLEAN;
                        } else {
                            type0 = FRAME_TYPE_OBJECT;
                        }
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        boolean value = SLLogicalNotNode.doBoolean($child0Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 1, value);
                        } else {
                            $frame.setBoolean($sp - 1, value);
                        }
                        return;
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 typeError(Object, Node, int) */);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLLogicalNotNode.typeError($child0Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLMul_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not mulLong(long, long) && mul(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLMul_SLMul_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLMul_SLMul_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLMul_SLMul_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLMul_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLMul_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 mulLong(long, long) */;
                try {
                    long value = SLMulNode.mulLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    // implicit transferToInterpreterAndInvalidate()
                    Lock lock = getLock();
                    lock.lock();
                    try {
                        bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                        bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLMul_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLMul_SLMul_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 mulLong(long, long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        try {
                            long value = SLMulNode.mulLong($child0Value__, $child1Value__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setLong($sp - 2, value);
                            }
                            return;
                        } catch (ArithmeticException ex) {
                            // implicit transferToInterpreterAndInvalidate()
                            Lock lock = getLock();
                            lock.lock();
                            try {
                                bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                                bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLMul_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
                            return;
                        }
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 mul(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        $frame.setObject($sp - 2, SLMulNode.mul($child0Value__, $child1Value__));
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLMul_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLMulNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLMul_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLMul_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte exclude = bc[$bci + 4 + 1];
                    if ((exclude) == 0 /* is-not-exclude mulLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 mulLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            try {
                                lock.unlock();
                                hasLock = false;
                                long value = SLMulNode.mulLong($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setLong($sp - 2, value);
                                }
                                return;
                            } catch (ArithmeticException ex) {
                                // implicit transferToInterpreterAndInvalidate()
                                lock.lock();
                                try {
                                    bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                                    bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLMul_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                                return;
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
                                bc[$bci + 4 + 1] = exclude = (byte) (exclude | 0b1 /* add-exclude mulLong(long, long) */);
                                state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 mul(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLMulNode.mul($child0Value_, $child1Value_));
                                return;
                            }
                        }
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLMulNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            @ExplodeLoop
            private void SLReadProperty_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
                            while (s0_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).accepts($child0Value_)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1]).accepts($child1Value_)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).hasArrayElements($child0Value_))) {
                                    Node node__ = (this);
                                    int bci__ = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value_, $child1Value_, node__, bci__, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1])));
                                    return;
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                {
                                    InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 2, this.SLReadProperty_readArray1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_));
                                        return;
                                    }
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    if ((state_0 & 0b11000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */ && $child0Value_ instanceof SLObject) {
                        SLObject $child0Value__ = (SLObject) $child0Value_;
                        if ((state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1]);
                            while (s2_ != null) {
                                if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2]).accepts($child0Value__))) {
                                    Node node__1 = (this);
                                    int bci__1 = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, node__1, bci__1, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3])));
                                    return;
                                }
                                s2_ = s2_.next_;
                            }
                        }
                        if ((state_0 & 0b10000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            $frame.setObject($sp - 2, this.SLReadProperty_readSLObject1Boundary_($bci, $sp, state_0, $child0Value__, $child1Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2]);
                            while (s4_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]).accepts($child0Value_)) && (!(SLReadPropertyNode.isSLObject($child0Value_))) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]).hasMembers($child0Value_))) {
                                    Node node__2 = (this);
                                    int bci__2 = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value_, $child1Value_, node__2, bci__2, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]), ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5])));
                                    return;
                                }
                                s4_ = s4_.next_;
                            }
                        }
                        if ((state_0 & 0b1000000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                if ((!(SLReadPropertyNode.isSLObject($child0Value_)))) {
                                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readObject1_objects__.hasMembers($child0Value_))) {
                                        $frame.setObject($sp - 2, this.SLReadProperty_readObject1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_));
                                        return;
                                    }
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLReadProperty_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadProperty_readArray1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_) {
                {
                    Node readArray1_node__ = (this);
                    int readArray1_bci__ = ($bci);
                    InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                    InteropLibrary readArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                    return SLReadPropertyNode.readArray($child0Value_, $child1Value_, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__);
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadProperty_readSLObject1Boundary_(int $bci, int $sp, byte state_0, SLObject $child0Value__, Object $child1Value_) {
                {
                    Node readSLObject1_node__ = (this);
                    int readSLObject1_bci__ = ($bci);
                    DynamicObjectLibrary readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value__));
                    return SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3]));
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadProperty_readObject1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_) {
                {
                    Node readObject1_node__ = (this);
                    int readObject1_bci__ = ($bci);
                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                    return SLReadPropertyNode.readObject($child0Value_, $child1Value_, readObject1_node__, readObject1_bci__, readObject1_objects__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5]));
                }
            }

            private void SLReadProperty_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte exclude = bc[$bci + 4 + 5];
                    {
                        int bci__ = 0;
                        Node node__ = null;
                        if (((exclude & 0b1)) == 0 /* is-not-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            int count0_ = 0;
                            SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
                            if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                                while (s0_ != null) {
                                    if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).accepts($child0Value)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1]).accepts($child1Value)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).hasArrayElements($child0Value))) {
                                        node__ = (this);
                                        bci__ = ($bci);
                                        break;
                                    }
                                    s0_ = s0_.next_;
                                    count0_++;
                                }
                            }
                            if (s0_ == null) {
                                {
                                    InteropLibrary arrays__ = super.insert((INTEROP_LIBRARY_.create($child0Value)));
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).accepts($child0Value));
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1]).accepts($child1Value));
                                    if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                        s0_ = super.insert(new SLReadProperty_ReadArray0Data(((SLReadProperty_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0])));
                                        node__ = (this);
                                        bci__ = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0] = s0_.insertAccessor(arrays__);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1] = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0] = s0_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value, $child1Value, node__, bci__, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1])));
                                return;
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
                            Node prev_ = encapsulating_.set(this);
                            try {
                                {
                                    readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readArray1_arrays__.hasArrayElements($child0Value))) {
                                        readArray1_node__ = (this);
                                        readArray1_bci__ = ($bci);
                                        readArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                                        bc[$bci + 4 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0] = null;
                                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                        lock.unlock();
                                        hasLock = false;
                                        $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value, $child1Value, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__));
                                        return;
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
                                SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1]);
                                if ((state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                    while (s2_ != null) {
                                        if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2]).accepts($child0Value_))) {
                                            node__1 = (this);
                                            bci__1 = ($bci);
                                            break;
                                        }
                                        s2_ = s2_.next_;
                                        count2_++;
                                    }
                                }
                                if (s2_ == null) {
                                    // assert (((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2]).accepts($child0Value_));
                                    if (count2_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                        s2_ = super.insert(new SLReadProperty_ReadSLObject0Data(((SLReadProperty_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1])));
                                        node__1 = (this);
                                        bci__1 = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2] = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1] = s2_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                        if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_Q_READ_SLOBJECT0);
                                        } else {
                                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                                        }
                                        int type0;
                                        int type1;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    }
                                }
                                if (s2_ != null) {
                                    lock.unlock();
                                    hasLock = false;
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, node__1, bci__1, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3])));
                                    return;
                                }
                            }
                        }
                        {
                            DynamicObjectLibrary readSLObject1_objectLibrary__ = null;
                            int readSLObject1_bci__ = 0;
                            Node readSLObject1_node__ = null;
                            readSLObject1_node__ = (this);
                            readSLObject1_bci__ = ($bci);
                            readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                            children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3] = super.insert((SLToTruffleStringNodeGen.create()));
                            bc[$bci + 4 + 5] = exclude = (byte) (exclude | 0b10 /* add-exclude readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1] = null;
                            state_0 = (byte) (state_0 & 0xfffffff7 /* remove-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                            int type0;
                            int type1;
                            type0 = FRAME_TYPE_OBJECT;
                            type1 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3])));
                            return;
                        }
                    }
                    {
                        int bci__2 = 0;
                        Node node__2 = null;
                        if (((exclude & 0b100)) == 0 /* is-not-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            int count4_ = 0;
                            SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2]);
                            if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                                while (s4_ != null) {
                                    if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]).accepts($child0Value)) && (!(SLReadPropertyNode.isSLObject($child0Value))) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]).hasMembers($child0Value))) {
                                        node__2 = (this);
                                        bci__2 = ($bci);
                                        break;
                                    }
                                    s4_ = s4_.next_;
                                    count4_++;
                                }
                            }
                            if (s4_ == null) {
                                if ((!(SLReadPropertyNode.isSLObject($child0Value)))) {
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]).accepts($child0Value));
                                    InteropLibrary objects__ = super.insert((INTEROP_LIBRARY_.create($child0Value)));
                                    if ((objects__.hasMembers($child0Value)) && count4_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                        s4_ = super.insert(new SLReadProperty_ReadObject0Data(((SLReadProperty_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2])));
                                        node__2 = (this);
                                        bci__2 = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4] = s4_.insertAccessor(objects__);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5] = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2] = s4_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    }
                                }
                            }
                            if (s4_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, node__2, bci__2, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4]), ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5])));
                                return;
                            }
                        }
                    }
                    {
                        InteropLibrary readObject1_objects__ = null;
                        int readObject1_bci__ = 0;
                        Node readObject1_node__ = null;
                        {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                if ((!(SLReadPropertyNode.isSLObject($child0Value)))) {
                                    readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readObject1_objects__.hasMembers($child0Value))) {
                                        readObject1_node__ = (this);
                                        readObject1_bci__ = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5] = super.insert((SLToMemberNodeGen.create()));
                                        bc[$bci + 4 + 5] = exclude = (byte) (exclude | 0b100 /* add-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2] = null;
                                        state_0 = (byte) (state_0 & 0xffffffdf /* remove-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                        lock.unlock();
                                        hasLock = false;
                                        $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, readObject1_node__, readObject1_bci__, readObject1_objects__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5])));
                                        return;
                                    }
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    throw new UnsupportedSpecializationException(this, new Node[] {null, null}, $frame.getValue($sp - 2), $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLSub_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not subLong(long, long) && sub(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLSub_SLSub_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLSub_SLSub_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLSub_SLSub_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLSub_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLSub_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 subLong(long, long) */;
                try {
                    long value = SLSubNode.subLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    // implicit transferToInterpreterAndInvalidate()
                    Lock lock = getLock();
                    lock.lock();
                    try {
                        bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude subLong(long, long) */);
                        bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLSub_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLSub_SLSub_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 subLong(long, long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    if ($child1Value_ instanceof Long) {
                        long $child1Value__ = (long) $child1Value_;
                        try {
                            long value = SLSubNode.subLong($child0Value__, $child1Value__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setLong($sp - 2, value);
                            }
                            return;
                        } catch (ArithmeticException ex) {
                            // implicit transferToInterpreterAndInvalidate()
                            Lock lock = getLock();
                            lock.lock();
                            try {
                                bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude subLong(long, long) */);
                                bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLSub_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
                            return;
                        }
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 sub(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000) >>> 4 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000) >>> 6 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        $frame.setObject($sp - 2, SLSubNode.sub($child0Value__, $child1Value__));
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLSub_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLSubNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLSub_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLSub_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 4 + 0];
                    byte exclude = bc[$bci + 4 + 1];
                    if ((exclude) == 0 /* is-not-exclude subLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 subLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */) {
                                type0 = FRAME_TYPE_LONG;
                                type1 = FRAME_TYPE_LONG;
                            } else {
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                            }
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            try {
                                lock.unlock();
                                hasLock = false;
                                long value = SLSubNode.subLong($child0Value_, $child1Value_);
                                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                    $frame.setObject($sp - 2, value);
                                } else {
                                    $frame.setLong($sp - 2, value);
                                }
                                return;
                            } catch (ArithmeticException ex) {
                                // implicit transferToInterpreterAndInvalidate()
                                lock.lock();
                                try {
                                    bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude subLong(long, long) */);
                                    bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLSub_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                                return;
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
                                bc[$bci + 4 + 1] = exclude = (byte) (exclude | 0b1 /* add-exclude subLong(long, long) */);
                                state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (byte) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 sub(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = FRAME_TYPE_OBJECT;
                                type1 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLSubNode.sub($child0Value_, $child1Value_));
                                return;
                            }
                        }
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = FRAME_TYPE_OBJECT;
                        type1 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLSubNode.typeError($child0Value, $child1Value, fallback_node__, fallback_bci__));
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            @ExplodeLoop
            private void SLWriteProperty_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 3);
                Object $child1Value_ = expectObject($frame, $sp - 2);
                Object $child2Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                            while (s0_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value_)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]).accepts($child1Value_)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).hasArrayElements($child0Value_))) {
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, ((Node) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), ((int) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1])));
                                    return;
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                {
                                    InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((writeArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 3, this.SLWriteProperty_writeArray1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_, $child2Value_));
                                        return;
                                    }
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    if ((state_0 & 0b11000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */ && $child0Value_ instanceof SLObject) {
                        SLObject $child0Value__ = (SLObject) $child0Value_;
                        if ((state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]);
                            while (s2_ != null) {
                                if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]).accepts($child0Value__))) {
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4])));
                                    return;
                                }
                                s2_ = s2_.next_;
                            }
                        }
                        if ((state_0 & 0b10000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            $frame.setObject($sp - 3, this.SLWriteProperty_writeSLObject1Boundary_($bci, $sp, state_0, $child0Value__, $child1Value_, $child2Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3]);
                            while (s4_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5]).accepts($child0Value_)) && (!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                                    Node node__ = (this);
                                    int bci__ = ($bci);
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, node__, bci__, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5]), ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6])));
                                    return;
                                }
                                s4_ = s4_.next_;
                            }
                        }
                        if ((state_0 & 0b1000000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            if ((!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                                $frame.setObject($sp - 3, this.SLWriteProperty_writeObject1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_, $child2Value_));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLWriteProperty_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWriteProperty_writeArray1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
                {
                    InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                    InteropLibrary writeArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                    return SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, ((Node) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), ((int) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), writeArray1_arrays__, writeArray1_numbers__);
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWriteProperty_writeSLObject1Boundary_(int $bci, int $sp, byte state_0, SLObject $child0Value__, Object $child1Value_, Object $child2Value_) {
                {
                    DynamicObjectLibrary writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value__));
                    return SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, writeSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4]));
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWriteProperty_writeObject1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set(this);
                try {
                    {
                        Node writeObject1_node__ = (this);
                        int writeObject1_bci__ = ($bci);
                        InteropLibrary writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value_));
                        return SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6]));
                    }
                } finally {
                    encapsulating_.set(prev_);
                }
            }

            private void SLWriteProperty_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value, Object $child2Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 5 + 0];
                    byte exclude = bc[$bci + 5 + 5];
                    if (((exclude & 0b1)) == 0 /* is-not-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        int count0_ = 0;
                        SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                        if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            while (s0_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]).accepts($child1Value)) && (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).hasArrayElements($child0Value))) {
                                    break;
                                }
                                s0_ = s0_.next_;
                                count0_++;
                            }
                        }
                        if (s0_ == null) {
                            {
                                InteropLibrary arrays__ = super.insert((INTEROP_LIBRARY_.create($child0Value)));
                                // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value));
                                // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]).accepts($child1Value));
                                if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                    s0_ = super.insert(new SLWriteProperty_WriteArray0Data(((SLWriteProperty_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0])));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = s0_.insertAccessor((this));
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = ($bci);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0] = s0_.insertAccessor(arrays__);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1] = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = s0_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = FRAME_TYPE_OBJECT;
                                    type1 = FRAME_TYPE_OBJECT;
                                    type2 = FRAME_TYPE_OBJECT;
                                    doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                                }
                            }
                        }
                        if (s0_ != null) {
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, ((Node) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), ((int) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]), ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1])));
                            return;
                        }
                    }
                    {
                        InteropLibrary writeArray1_numbers__ = null;
                        InteropLibrary writeArray1_arrays__ = null;
                        {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                {
                                    writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((writeArray1_arrays__.hasArrayElements($child0Value))) {
                                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = super.insert((this));
                                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = ($bci);
                                        writeArray1_numbers__ = (INTEROP_LIBRARY_.getUncached());
                                        bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = null;
                                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                                        int type0;
                                        int type1;
                                        int type2;
                                        type0 = FRAME_TYPE_OBJECT;
                                        type1 = FRAME_TYPE_OBJECT;
                                        type2 = FRAME_TYPE_OBJECT;
                                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                        doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                                        lock.unlock();
                                        hasLock = false;
                                        $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, ((Node) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), ((int) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), writeArray1_arrays__, writeArray1_numbers__));
                                        return;
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
                            SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]);
                            if ((state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                while (s2_ != null) {
                                    if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]).accepts($child0Value_))) {
                                        break;
                                    }
                                    s2_ = s2_.next_;
                                    count2_++;
                                }
                            }
                            if (s2_ == null) {
                                // assert (((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]).accepts($child0Value_));
                                if (count2_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                    s2_ = super.insert(new SLWriteProperty_WriteSLObject0Data(((SLWriteProperty_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2])));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3] = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = s2_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                    if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_Q_WRITE_SLOBJECT0);
                                    } else {
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                                    }
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = FRAME_TYPE_OBJECT;
                                    type1 = FRAME_TYPE_OBJECT;
                                    type2 = FRAME_TYPE_OBJECT;
                                    doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                                }
                            }
                            if (s2_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4])));
                                return;
                            }
                        }
                        {
                            DynamicObjectLibrary writeSLObject1_objectLibrary__ = null;
                            writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                            children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4] = super.insert((SLToTruffleStringNodeGen.create()));
                            bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b10 /* add-exclude writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = null;
                            state_0 = (byte) (state_0 & 0xfffffff7 /* remove-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                            int type0;
                            int type1;
                            int type2;
                            type0 = FRAME_TYPE_OBJECT;
                            type1 = FRAME_TYPE_OBJECT;
                            type2 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                            doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, writeSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4])));
                            return;
                        }
                    }
                    {
                        int bci__ = 0;
                        Node node__ = null;
                        if (((exclude & 0b100)) == 0 /* is-not-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            int count4_ = 0;
                            SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3]);
                            if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                                while (s4_ != null) {
                                    if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5]).accepts($child0Value)) && (!(SLWritePropertyNode.isSLObject($child0Value)))) {
                                        node__ = (this);
                                        bci__ = ($bci);
                                        break;
                                    }
                                    s4_ = s4_.next_;
                                    count4_++;
                                }
                            }
                            if (s4_ == null) {
                                if ((!(SLWritePropertyNode.isSLObject($child0Value))) && count4_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5]).accepts($child0Value));
                                    s4_ = super.insert(new SLWriteProperty_WriteObject0Data(((SLWriteProperty_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3])));
                                    node__ = (this);
                                    bci__ = ($bci);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5] = s4_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6] = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3] = s4_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = FRAME_TYPE_OBJECT;
                                    type1 = FRAME_TYPE_OBJECT;
                                    type2 = FRAME_TYPE_OBJECT;
                                    doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                                }
                            }
                            if (s4_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, node__, bci__, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5]), ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6])));
                                return;
                            }
                        }
                    }
                    {
                        InteropLibrary writeObject1_objectLibrary__ = null;
                        int writeObject1_bci__ = 0;
                        Node writeObject1_node__ = null;
                        {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                                    writeObject1_node__ = (this);
                                    writeObject1_bci__ = ($bci);
                                    writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6] = super.insert((SLToMemberNodeGen.create()));
                                    bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b100 /* add-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3] = null;
                                    state_0 = (byte) (state_0 & 0xffffffdf /* remove-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY);
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = FRAME_TYPE_OBJECT;
                                    type1 = FRAME_TYPE_OBJECT;
                                    type2 = FRAME_TYPE_OBJECT;
                                    doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 3], type1);
                                    doSetResultBoxed(bc, $bci, bc[$bci + 4], type2);
                                    lock.unlock();
                                    hasLock = false;
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6])));
                                    return;
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    throw new UnsupportedSpecializationException(this, new Node[] {null, null, null}, $frame.getValue($sp - 3), $frame.getValue($sp - 2), $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLUnbox_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                byte state_1 = bc[$bci + 3 + 1];
                if ((state_0 & 0b11110110) == 0 /* only-active fromBoolean(boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnbox_SLUnbox_execute__boolean0_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else if ((state_0 & 0b11101110) == 0 /* only-active fromLong(long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnbox_SLUnbox_execute__long1_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else {
                    SLUnbox_SLUnbox_execute__generic2_($frame, $bci, $sp, state_0, state_1);
                    return;
                }
            }

            private void SLUnbox_SLUnbox_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLUnbox_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */);
                boolean value = SLUnboxNode.fromBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private void SLUnbox_SLUnbox_execute__long1_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLUnbox_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */);
                long value = SLUnboxNode.fromLong($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setLong($sp - 1, value);
                }
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLUnbox_fromForeign1Boundary_(int $bci, int $sp, byte state_0, byte state_1, Object $child0Value_) {
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set(this);
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
            private void SLUnbox_SLUnbox_execute__generic2_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if (((state_0 & 0b10) != 0 /* is-state_0 fromString(String, FromJavaStringNode) */) && $child0Value_ instanceof String) {
                    String $child0Value__ = (String) $child0Value_;
                    $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value__, ((FromJavaStringNode) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 0])));
                    return;
                }
                if (((state_0 & 0b100) != 0 /* is-state_0 fromTruffleString(TruffleString) */) && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value__));
                    return;
                }
                if (((state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */) && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    boolean value = SLUnboxNode.fromBoolean($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if (((state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */) && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    long value = SLUnboxNode.fromLong($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setLong($sp - 1, value);
                    }
                    return;
                }
                if (((state_0 & 0b100000) != 0 /* is-state_0 fromBigNumber(SLBigNumber) */) && SLTypesGen.isImplicitSLBigNumber((state_1 & 0b1100) >>> 2 /* extract-implicit-state_1 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_1 & 0b1100) >>> 2 /* extract-implicit-state_1 0:SLBigNumber */, $child0Value_);
                    $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value__));
                    return;
                }
                if (((state_0 & 0b1000000) != 0 /* is-state_0 fromFunction(SLFunction) */) && $child0Value_ instanceof SLFunction) {
                    SLFunction $child0Value__ = (SLFunction) $child0Value_;
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value__));
                    return;
                }
                if (((state_0 & 0b10000000) != 0 /* is-state_0 fromFunction(SLNull) */) && SLTypes.isSLNull($child0Value_)) {
                    SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value__));
                    return;
                }
                if (((state_1 & 0b11) != 0 /* is-state_1 fromForeign(Object, InteropLibrary) || fromForeign(Object, InteropLibrary) */)) {
                    if (((state_1 & 0b1) != 0 /* is-state_1 fromForeign(Object, InteropLibrary) */)) {
                        SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0]);
                        while (s7_ != null) {
                            if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1]).accepts($child0Value_))) {
                                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value_, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1])));
                                return;
                            }
                            s7_ = s7_.next_;
                        }
                    }
                    if (((state_1 & 0b10) != 0 /* is-state_1 fromForeign(Object, InteropLibrary) */)) {
                        $frame.setObject($sp - 1, this.SLUnbox_fromForeign1Boundary_($bci, $sp, state_0, state_1, $child0Value_));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLUnbox_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLUnbox_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    byte state_1 = bc[$bci + 3 + 1];
                    byte exclude = bc[$bci + 3 + 6];
                    if ($child0Value instanceof String) {
                        String $child0Value_ = (String) $child0Value;
                        children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 0] = super.insert((FromJavaStringNode.create()));
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 fromString(String, FromJavaStringNode) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value_, ((FromJavaStringNode) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 0])));
                        return;
                    }
                    if ($child0Value instanceof TruffleString) {
                        TruffleString $child0Value_ = (TruffleString) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 fromTruffleString(TruffleString) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value_));
                        return;
                    }
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 fromBoolean(boolean) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        if ((state_0 & 0b11111110) == 0b1000/* is-exact-state_0 fromBoolean(boolean) */ && (state_1 & 0b11) == 0/* is-exact-state_1  */) {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_Q_FROM_BOOLEAN);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        }
                        int type0;
                        if ((state_0 & 0b11110110) == 0 /* only-active fromBoolean(boolean) */ && (state_1 & 0b11) == 0 /* only-active  */) {
                            type0 = FRAME_TYPE_BOOLEAN;
                        } else {
                            type0 = FRAME_TYPE_OBJECT;
                        }
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        boolean value = SLUnboxNode.fromBoolean($child0Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 1, value);
                        } else {
                            $frame.setBoolean($sp - 1, value);
                        }
                        return;
                    }
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 fromLong(long) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        if ((state_0 & 0b11111110) == 0b10000/* is-exact-state_0 fromLong(long) */ && (state_1 & 0b11) == 0/* is-exact-state_1  */) {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_Q_FROM_LONG);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        }
                        int type0;
                        if ((state_0 & 0b11101110) == 0 /* only-active fromLong(long) */ && (state_1 & 0b11) == 0 /* only-active  */) {
                            type0 = FRAME_TYPE_LONG;
                        } else {
                            type0 = FRAME_TYPE_OBJECT;
                        }
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        long value = SLUnboxNode.fromLong($child0Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 1, value);
                        } else {
                            $frame.setLong($sp - 1, value);
                        }
                        return;
                    }
                    {
                        int sLBigNumberCast0;
                        if ((sLBigNumberCast0 = SLTypesGen.specializeImplicitSLBigNumber($child0Value)) != 0) {
                            SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber(sLBigNumberCast0, $child0Value);
                            state_1 = (byte) (state_1 | (sLBigNumberCast0 << 2) /* set-implicit-state_1 0:SLBigNumber */);
                            bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 fromBigNumber(SLBigNumber) */);
                            bc[$bci + 3 + 1] = (byte) (state_1);
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                            int type0;
                            type0 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value_));
                            return;
                        }
                    }
                    if ($child0Value instanceof SLFunction) {
                        SLFunction $child0Value_ = (SLFunction) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 fromFunction(SLFunction) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                        return;
                    }
                    if (SLTypes.isSLNull($child0Value)) {
                        SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10000000 /* add-state_0 fromFunction(SLNull) */);
                        bc[$bci + 3 + 1] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                        return;
                    }
                    if ((exclude) == 0 /* is-not-exclude fromForeign(Object, InteropLibrary) */) {
                        int count7_ = 0;
                        SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0]);
                        if (((state_1 & 0b1) != 0 /* is-state_1 fromForeign(Object, InteropLibrary) */)) {
                            while (s7_ != null) {
                                if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1]).accepts($child0Value))) {
                                    break;
                                }
                                s7_ = s7_.next_;
                                count7_++;
                            }
                        }
                        if (s7_ == null) {
                            // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1]).accepts($child0Value));
                            if (count7_ < (SLUnboxNode.LIMIT)) {
                                s7_ = super.insert(new SLUnbox_FromForeign0Data(((SLUnbox_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0])));
                                children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1] = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                VarHandle.storeStoreFence();
                                consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0] = s7_;
                                bc[$bci + 3 + 0] = (byte) (state_0);
                                bc[$bci + 3 + 1] = state_1 = (byte) (state_1 | 0b1 /* add-state_1 fromForeign(Object, InteropLibrary) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                                int type0;
                                type0 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            }
                        }
                        if (s7_ != null) {
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1])));
                            return;
                        }
                    }
                    {
                        InteropLibrary fromForeign1_interop__ = null;
                        {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set(this);
                            try {
                                fromForeign1_interop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                bc[$bci + 3 + 6] = exclude = (byte) (exclude | 0b1 /* add-exclude fromForeign(Object, InteropLibrary) */);
                                consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0] = null;
                                state_1 = (byte) (state_1 & 0xfffffffe /* remove-state_1 fromForeign(Object, InteropLibrary) */);
                                bc[$bci + 3 + 0] = (byte) (state_0);
                                bc[$bci + 3 + 1] = state_1 = (byte) (state_1 | 0b10 /* add-state_1 fromForeign(Object, InteropLibrary) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX);
                                int type0;
                                type0 = FRAME_TYPE_OBJECT;
                                doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, fromForeign1_interop__));
                                return;
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

            private void SLFunctionLiteral_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */ && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    {
                        Node node__ = (this);
                        $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value__, ((SLFunction) consts[LE_BYTES.getShort(bc, $bci + 3 + 1) + 0]), node__));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLFunctionLiteral_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLFunctionLiteral_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    {
                        Node node__ = null;
                        if ($child0Value instanceof TruffleString) {
                            TruffleString $child0Value_ = (TruffleString) $child0Value;
                            consts[LE_BYTES.getShort(bc, $bci + 3 + 1) + 0] = (SLFunctionLiteralNode.lookupFunctionCached($child0Value_, this));
                            node__ = (this);
                            bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 perform(TruffleString, SLFunction, Node) */);
                            if ((state_0 & 0b10) == 0b10/* is-exact-state_0 perform(TruffleString, SLFunction, Node) */) {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLFUNCTION_LITERAL_Q_PERFORM);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLFUNCTION_LITERAL);
                            }
                            int type0;
                            type0 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value_, ((SLFunction) consts[LE_BYTES.getShort(bc, $bci + 3 + 1) + 0]), node__));
                            return;
                        }
                    }
                    throw new UnsupportedSpecializationException(this, new Node[] {null}, $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLToBoolean_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    SLToBoolean_SLToBoolean_execute__boolean0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLToBoolean_SLToBoolean_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLToBoolean_SLToBoolean_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLToBoolean_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */;
                boolean value = SLToBooleanNode.doBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private void SLToBoolean_SLToBoolean_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    boolean value = SLToBooleanNode.doBoolean($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLToBoolean_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                            boolean value = SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 1, value);
                            } else {
                                $frame.setBoolean($sp - 1, value);
                            }
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLToBoolean_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLToBoolean_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        if ((state_0 & 0b110) == 0b10/* is-exact-state_0 doBoolean(boolean) */) {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN_Q_BOOLEAN);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN);
                        }
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */) {
                            type0 = FRAME_TYPE_BOOLEAN;
                        } else {
                            type0 = FRAME_TYPE_OBJECT;
                        }
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        boolean value = SLToBooleanNode.doBoolean($child0Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 1, value);
                        } else {
                            $frame.setBoolean($sp - 1, value);
                        }
                        return;
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 doFallback(Object, Node, int) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN);
                        int type0;
                        type0 = FRAME_TYPE_OBJECT;
                        doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                        lock.unlock();
                        hasLock = false;
                        boolean value = SLToBooleanNode.doFallback($child0Value, fallback_node__, fallback_bci__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 1, value);
                        } else {
                            $frame.setBoolean($sp - 1, value);
                        }
                        return;
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private boolean SLAnd_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    return SLAnd_SLAnd_execute__boolean0_($frame, $bci, $sp, state_0);
                } else {
                    return SLAnd_SLAnd_execute__generic1_($frame, $bci, $sp, state_0);
                }
            }

            private boolean SLAnd_SLAnd_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    return SLAnd_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                }
                assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
                return SLToBooleanNode.doBoolean($child0Value_);
            }

            private boolean SLAnd_SLAnd_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    return SLToBooleanNode.doBoolean($child0Value__);
                }
                if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLAnd_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                            return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLAnd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
            }

            private boolean SLAnd_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 5 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                        lock.unlock();
                        hasLock = false;
                        return SLToBooleanNode.doBoolean($child0Value_);
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doFallback(Object, Node, int) */);
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

            private boolean SLOr_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    return SLOr_SLOr_execute__boolean0_($frame, $bci, $sp, state_0);
                } else {
                    return SLOr_SLOr_execute__generic1_($frame, $bci, $sp, state_0);
                }
            }

            private boolean SLOr_SLOr_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    return SLOr_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                }
                assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
                return SLToBooleanNode.doBoolean($child0Value_);
            }

            private boolean SLOr_SLOr_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    return SLToBooleanNode.doBoolean($child0Value__);
                }
                if ((state_0 & 0b10) != 0 /* is-state_0 doFallback(Object, Node, int) */) {
                    {
                        Node fallback_node__ = (this);
                        int fallback_bci__ = ($bci);
                        if (SLOr_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                            return SLToBooleanNode.doFallback($child0Value_, fallback_node__, fallback_bci__);
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLOr_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
            }

            private boolean SLOr_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 5 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1 /* add-state_0 doBoolean(boolean) */);
                        lock.unlock();
                        hasLock = false;
                        return SLToBooleanNode.doBoolean($child0Value_);
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = (this);
                        fallback_bci__ = ($bci);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doFallback(Object, Node, int) */);
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
            private Object SLInvoke_execute_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
                byte state_0 = bc[$bci + 5 + 0];
                if ((state_0 & 0b1110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) || doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) */ && arg0Value instanceof SLFunction) {
                        SLFunction arg0Value_ = (SLFunction) arg0Value;
                        if ((state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                            while (s0_ != null) {
                                if (!Assumption.isValidAssumption((((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1])))) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    SLInvoke_removeDirect__($frame, $bci, $sp, s0_);
                                    return SLInvoke_executeAndSpecialize_($frame, $bci, $sp, arg0Value_, arg1Value);
                                }
                                if ((arg0Value_.getCallTarget() == ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]))) {
                                    return SLInvoke.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */) {
                            return SLInvoke.doIndirect(arg0Value_, arg1Value, ((IndirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]));
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                        {
                            Node interop_node__ = (this);
                            int interop_bci__ = ($bci);
                            return SLInvoke.doInterop(arg0Value, arg1Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), interop_node__, interop_bci__);
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLInvoke_executeAndSpecialize_($frame, $bci, $sp, arg0Value, arg1Value);
            }

            private Object SLInvoke_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 5 + 0];
                    byte exclude = bc[$bci + 5 + 5];
                    if (arg0Value instanceof SLFunction) {
                        SLFunction arg0Value_ = (SLFunction) arg0Value;
                        if ((exclude) == 0 /* is-not-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            int count0_ = 0;
                            SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                            if ((state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                                while (s0_ != null) {
                                    if ((arg0Value_.getCallTarget() == ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2])) && Assumption.isValidAssumption((((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1])))) {
                                        break;
                                    }
                                    s0_ = s0_.next_;
                                    count0_++;
                                }
                            }
                            if (s0_ == null) {
                                {
                                    RootCallTarget cachedTarget__ = (arg0Value_.getCallTarget());
                                    if ((arg0Value_.getCallTarget() == cachedTarget__)) {
                                        Assumption callTargetStable__ = (arg0Value_.getCallTargetStable());
                                        Assumption assumption0 = (callTargetStable__);
                                        if (Assumption.isValidAssumption(assumption0)) {
                                            if (count0_ < (3)) {
                                                s0_ = super.insert(new SLInvoke_DirectData(((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0])));
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = callTargetStable__;
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = cachedTarget__;
                                                children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0] = s0_.insertAccessor((DirectCallNode.create(cachedTarget__)));
                                                VarHandle.storeStoreFence();
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = s0_;
                                                bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                                                if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE_Q_DIRECT);
                                                } else {
                                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                return SLInvoke.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                            }
                        }
                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1] = super.insert((IndirectCallNode.create()));
                        bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = null;
                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE);
                        lock.unlock();
                        hasLock = false;
                        return SLInvoke.doIndirect(arg0Value_, arg1Value, ((IndirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]));
                    }
                    {
                        int interop_bci__ = 0;
                        Node interop_node__ = null;
                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = super.insert((INTEROP_LIBRARY_.createDispatched(3)));
                        interop_node__ = (this);
                        interop_bci__ = ($bci);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE);
                        lock.unlock();
                        hasLock = false;
                        return SLInvoke.doInterop(arg0Value, arg1Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), interop_node__, interop_bci__);
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLInvoke_removeDirect__(VirtualFrame $frame, int $bci, int $sp, Object s0_) {
                Lock lock = getLock();
                lock.lock();
                try {
                    SLInvoke_DirectData prev = null;
                    SLInvoke_DirectData cur = ((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (cur != null) {
                        if (cur == s0_) {
                            if (prev == null) {
                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = this.insert(cur.next_);
                            } else {
                                prev.next_ = prev.insertAccessor(cur.next_);
                            }
                            break;
                        }
                        prev = cur;
                        cur = cur.next_;
                    }
                    if (consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] == null) {
                        bc[$bci + 5 + 0] = (byte) (bc[$bci + 5 + 0] & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    }
                } finally {
                    lock.unlock();
                }
            }

            private void SLUnbox_q_FromLong_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLUnbox_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */);
                long value = SLUnboxNode.fromLong($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setLong($sp - 1, value);
                }
                return;
            }

            private void SLAdd_q_AddLong_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */);
                try {
                    long value = SLAddNode.addLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    // implicit transferToInterpreterAndInvalidate()
                    Lock lock = getLock();
                    lock.lock();
                    try {
                        bc[$bci + 4 + 1] = (byte) (bc[$bci + 4 + 1] | 0b1 /* add-exclude addLong(long, long) */);
                        bc[$bci + 4 + 0] = (byte) (bc[$bci + 4 + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLAdd_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            @ExplodeLoop
            private void SLReadProperty_q_ReadSLObject0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                assert (state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
                    while (s2_ != null) {
                        if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).accepts($child0Value__))) {
                            Node node__ = (this);
                            int bci__ = ($bci);
                            $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, node__, bci__, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1])));
                            return;
                        }
                        s2_ = s2_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLReadProperty_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLUnbox_q_FromBoolean_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLUnbox_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert ((state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */);
                boolean value = SLUnboxNode.fromBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private void SLToBoolean_q_Boolean_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLToBoolean_executeAndSpecialize_($frame, $bci, $sp, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */;
                boolean value = SLToBooleanNode.doBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private void SLLessOrEqual_q_LessOrEqual0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessOrEqual_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLLessOrEqual_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 lessOrEqual(long, long) */;
                boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            @ExplodeLoop
            private Object SLInvoke_q_Direct_execute_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
                byte state_0 = bc[$bci + 5 + 0];
                assert (state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */;
                if (arg0Value instanceof SLFunction) {
                    SLFunction arg0Value_ = (SLFunction) arg0Value;
                    SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (s0_ != null) {
                        if (!Assumption.isValidAssumption((((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1])))) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            SLInvoke_q_Direct_removeDirect__($frame, $bci, $sp, s0_);
                            return SLInvoke_executeAndSpecialize_($frame, $bci, $sp, arg0Value_, arg1Value);
                        }
                        if ((arg0Value_.getCallTarget() == ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]))) {
                            return SLInvoke.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                        }
                        s0_ = s0_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLInvoke_executeAndSpecialize_($frame, $bci, $sp, arg0Value, arg1Value);
            }

            private void SLInvoke_q_Direct_removeDirect__(VirtualFrame $frame, int $bci, int $sp, Object s0_) {
                Lock lock = getLock();
                lock.lock();
                try {
                    SLInvoke_DirectData prev = null;
                    SLInvoke_DirectData cur = ((SLInvoke_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (cur != null) {
                        if (cur == s0_) {
                            if (prev == null) {
                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = this.insert(cur.next_);
                            } else {
                                prev.next_ = prev.insertAccessor(cur.next_);
                            }
                            break;
                        }
                        prev = cur;
                        cur = cur.next_;
                    }
                    if (consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] == null) {
                        bc[$bci + 5 + 0] = (byte) (bc[$bci + 5 + 0] & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    }
                } finally {
                    lock.unlock();
                }
            }

            private void SLFunctionLiteral_q_Perform_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 1);
                assert (state_0 & 0b10) != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */;
                if ($child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    {
                        Node node__ = (this);
                        $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value__, ((SLFunction) consts[LE_BYTES.getShort(bc, $bci + 3 + 1) + 0]), node__));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLFunctionLiteral_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            @ExplodeLoop
            private void SLWriteProperty_q_WriteSLObject0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                Object $child0Value_ = expectObject($frame, $sp - 3);
                Object $child1Value_ = expectObject($frame, $sp - 2);
                Object $child2Value_ = expectObject($frame, $sp - 1);
                assert (state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (s2_ != null) {
                        if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value__))) {
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1])));
                            return;
                        }
                        s2_ = s2_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLWriteProperty_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            private void SLLessThan_q_LessThan0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessThan_executeAndSpecialize_($frame, $bci, $sp, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    SLLessThan_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 lessThan(long, long) */;
                boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            @Override
            public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
                int bci = 0;
                while (bci < bc.length) {
                    switch (LE_BYTES.getShort(bc, bci)) {
                        case INSTR_POP :
                        {
                            bci = bci + 2;
                            break;
                        }
                        case INSTR_BRANCH :
                        case INSTR_THROW :
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        case INSTR_LOAD_ARGUMENT_LONG :
                        case INSTR_LOAD_LOCAL_OBJECT :
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        case INSTR_LOAD_LOCAL_LONG :
                        case INSTR_LOAD_LOCAL_UNINIT :
                        case INSTR_C_SLLOGICAL_NOT :
                        case INSTR_C_SLTO_BOOLEAN :
                        case INSTR_C_SLTO_BOOLEAN_Q_BOOLEAN :
                        {
                            bci = bci + 4;
                            break;
                        }
                        case INSTR_BRANCH_FALSE :
                        {
                            bci = bci + 7;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_CONSTANT_OBJECT);
                            bci = bci + 4;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        case INSTR_STORE_LOCAL_LONG :
                        case INSTR_STORE_LOCAL_UNINIT :
                        case INSTR_C_SLLESS_OR_EQUAL :
                        case INSTR_C_SLLESS_THAN :
                        case INSTR_C_SLLESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        case INSTR_C_SLLESS_THAN_Q_LESS_THAN0 :
                        {
                            bci = bci + 5;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            bci = bci + 3;
                            break;
                        }
                        case INSTR_C_SLADD :
                        case INSTR_C_SLEQUAL :
                        case INSTR_C_SLWRITE_PROPERTY :
                        case INSTR_C_SLINVOKE :
                        case INSTR_C_SLADD_Q_ADD_LONG :
                        case INSTR_C_SLINVOKE_Q_DIRECT :
                        case INSTR_C_SLWRITE_PROPERTY_Q_WRITE_SLOBJECT0 :
                        {
                            bci = bci + 11;
                            break;
                        }
                        case INSTR_C_SLDIV :
                        case INSTR_C_SLMUL :
                        case INSTR_C_SLSUB :
                        case INSTR_C_SLFUNCTION_LITERAL :
                        case INSTR_SC_SLAND :
                        case INSTR_SC_SLOR :
                        case INSTR_C_SLFUNCTION_LITERAL_Q_PERFORM :
                        {
                            bci = bci + 6;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY :
                        case INSTR_C_SLUNBOX :
                        case INSTR_C_SLUNBOX_Q_FROM_LONG :
                        case INSTR_C_SLREAD_PROPERTY_Q_READ_SLOBJECT0 :
                        case INSTR_C_SLUNBOX_Q_FROM_BOOLEAN :
                        {
                            bci = bci + 10;
                            break;
                        }
                    }
                }
            }

            public String dump() {
                int bci = 0;
                int instrIndex = 0;
                StringBuilder sb = new StringBuilder();
                while (bci < bc.length) {
                    sb.append(String.format(" %04x ", bci));
                    switch (LE_BYTES.getShort(bc, bci)) {
                        default :
                        {
                            sb.append(String.format("unknown 0x%02x", bc[bci++]));
                            break;
                        }
                        case INSTR_POP :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("pop                              ");
                            sb.append("_");
                            sb.append(" -> ");
                            bci += 2;
                            break;
                        }
                        case INSTR_BRANCH :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("branch                           ");
                            sb.append(String.format("%04x", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("branch");
                            bci += 4;
                            break;
                        }
                        case INSTR_BRANCH_FALSE :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("branch.false                     ");
                            sb.append(String.format("%04x", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 4]));
                            sb.append(", ");
                            sb.append(" -> ");
                            sb.append("branch");
                            bci += 7;
                            break;
                        }
                        case INSTR_THROW :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("throw                            ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.constant.object             ");
                            {
                                Object o = consts[LE_BYTES.getShort(bc, bci + 2)];
                                sb.append(String.format("%s %s", o.getClass().getSimpleName(), o));
                            }
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.constant.boolean            ");
                            {
                                Object o = consts[LE_BYTES.getShort(bc, bci + 2)];
                                sb.append(String.format("%s %s", o.getClass().getSimpleName(), o));
                            }
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.constant.long               ");
                            {
                                Object o = consts[LE_BYTES.getShort(bc, bci + 2)];
                                sb.append(String.format("%s %s", o.getClass().getSimpleName(), o));
                            }
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.argument.object             ");
                            sb.append(String.format("arg[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.argument.boolean            ");
                            sb.append(String.format("arg[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.argument.long               ");
                            sb.append(String.format("arg[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("store.local.object               ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 3)));
                            bci += 5;
                            break;
                        }
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("store.local.boolean              ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 3)));
                            bci += 5;
                            break;
                        }
                        case INSTR_STORE_LOCAL_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("store.local.long                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 3)));
                            bci += 5;
                            break;
                        }
                        case INSTR_STORE_LOCAL_UNINIT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("store.local.uninit               ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 3)));
                            bci += 5;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_OBJECT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.local.object                ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.local.boolean               ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.local.long                  ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_UNINIT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("load.local.uninit                ");
                            sb.append(String.format("loc[%d]", LE_BYTES.getShort(bc, bci + 2)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("return                           ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("return");
                            bci += 3;
                            break;
                        }
                        case INSTR_C_SLADD :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLAdd                          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLDIV :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLDiv                          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLEQUAL :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLEqual                        ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLLESS_OR_EQUAL :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLLessOrEqual                  ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLLESS_THAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLLessThan                     ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLLOGICAL_NOT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLLogicalNot                   ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLMUL :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLMul                          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLReadProperty                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLSUB :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLSub                          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLWRITE_PROPERTY :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLWriteProperty                ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 4]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLUNBOX :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLUnbox                        ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLFUNCTION_LITERAL :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLFunctionLiteral              ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLTO_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLToBoolean                    ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_SC_SLAND :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("sc.SLAnd                         ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("%04x", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("branch");
                            bci += 6;
                            break;
                        }
                        case INSTR_SC_SLOR :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("sc.SLOr                          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("%04x", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("branch");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLINVOKE :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLInvoke                       ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("**%d", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLUNBOX_Q_FROM_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLUnbox.q.FromLong             ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLADD_Q_ADD_LONG :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLAdd.q.AddLong                ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY_Q_READ_SLOBJECT0 :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLReadProperty.q.ReadSLObject0 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLUNBOX_Q_FROM_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLUnbox.q.FromBoolean          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLTO_BOOLEAN_Q_BOOLEAN :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLToBoolean.q.Boolean          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLLESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLLessOrEqual.q.LessOrEqual0   ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLINVOKE_Q_DIRECT :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLInvoke.q.Direct              ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("**%d", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLFUNCTION_LITERAL_Q_PERFORM :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLFunctionLiteral.q.Perform    ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLWRITE_PROPERTY_Q_WRITE_SLOBJECT0 :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append(String.format("%02x ", bc[bci + 5]));
                            sb.append(String.format("%02x ", bc[bci + 6]));
                            sb.append(String.format("%02x ", bc[bci + 7]));
                            sb.append(String.format("%02x ", bc[bci + 8]));
                            sb.append(String.format("%02x ", bc[bci + 9]));
                            sb.append(String.format("%02x ", bc[bci + 10]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLWriteProperty.q.WriteSLObject0 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 4]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLLESS_THAN_Q_LESS_THAN0 :
                        {
                            sb.append(String.format("%02x ", bc[bci + 0]));
                            sb.append(String.format("%02x ", bc[bci + 1]));
                            sb.append(String.format("%02x ", bc[bci + 2]));
                            sb.append(String.format("%02x ", bc[bci + 3]));
                            sb.append(String.format("%02x ", bc[bci + 4]));
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("   ");
                            sb.append("c.SLLessThan.q.LessThan0         ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                    }
                    sb.append("\n");
                }
                for (int i = 0; i < handlers.length; i++) {
                    sb.append(handlers[i] + "\n");
                }
                return sb.toString();
            }

            private static boolean SLAdd_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static boolean SLDiv_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessOrEqual_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessThan_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLogicalNot_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLMul_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLSub_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLToBoolean_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLAnd_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLOr_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLAdd_q_AddLong_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static boolean SLToBoolean_q_Boolean_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessOrEqual_q_LessOrEqual0_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessThan_q_LessThan0_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static int storeLocalInitialization(VirtualFrame frame, int localIdx, int localTag, int sourceSlot) {
                Object value = frame.getValue(sourceSlot);
                if (localTag == FRAME_TYPE_BOOLEAN && value instanceof Boolean) {
                    frame.setBoolean(localIdx, (boolean) value);
                    return FRAME_TYPE_BOOLEAN;
                }
                if (localTag == FRAME_TYPE_LONG && value instanceof Long) {
                    frame.setLong(localIdx, (long) value);
                    return FRAME_TYPE_LONG;
                }
                frame.setObject(localIdx, value);
                return FRAME_TYPE_OBJECT;
            }

            private static void doSetResultBoxed(byte[] bc, int startBci, int bciOffset, int targetType) {
                if (bciOffset != 0) {
                    setResultBoxedImpl(bc, startBci - bciOffset, targetType, BOXING_DESCRIPTORS[targetType]);
                }
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
                @Child Node node_;
                @CompilationFinal int bci_;
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
            @GeneratedBy(SLOperations.class)
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
    }
}
