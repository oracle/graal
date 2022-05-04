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
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.operation.BuilderExceptionHandler;
import com.oracle.truffle.api.operation.BuilderOperationData;
import com.oracle.truffle.api.operation.BuilderOperationLabel;
import com.oracle.truffle.api.operation.BuilderSourceInfo;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsBuilder;
import com.oracle.truffle.api.operation.OperationsBytesSupport;
import com.oracle.truffle.api.operation.OperationsConstantPool;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.ConcatNode;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;
import com.oracle.truffle.sl.SLLanguage;
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
import com.oracle.truffle.sl.operations.SLOperations.SLEvalRootOperation;
import com.oracle.truffle.sl.operations.SLOperations.SLInvokeOperation;
import com.oracle.truffle.sl.parser.SLSource;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLObject;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

@GeneratedBy(SLOperations.class)
@SuppressWarnings("unused")
public abstract class SLOperationsBuilder extends OperationsBuilder {

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

    public abstract void emitLabel(OperationLabel arg0);

    public abstract void emitBranch(OperationLabel arg0);

    public abstract void emitConstObject(Object arg0);

    public abstract void emitLoadArgument(int arg0);

    public abstract void beginStoreLocal(int arg0);

    public abstract void endStoreLocal();

    public abstract void emitLoadLocal(int arg0);

    public abstract void beginReturn();

    public abstract void endReturn();

    public abstract void beginInstrumentation(Class<?> arg0);

    public abstract void endInstrumentation();

    public abstract void beginSLAddOperation();

    public abstract void endSLAddOperation();

    public abstract void beginSLDivOperation();

    public abstract void endSLDivOperation();

    public abstract void beginSLEqualOperation();

    public abstract void endSLEqualOperation();

    public abstract void beginSLLessOrEqualOperation();

    public abstract void endSLLessOrEqualOperation();

    public abstract void beginSLLessThanOperation();

    public abstract void endSLLessThanOperation();

    public abstract void beginSLLogicalNotOperation();

    public abstract void endSLLogicalNotOperation();

    public abstract void beginSLMulOperation();

    public abstract void endSLMulOperation();

    public abstract void beginSLReadPropertyOperation();

    public abstract void endSLReadPropertyOperation();

    public abstract void beginSLSubOperation();

    public abstract void endSLSubOperation();

    public abstract void beginSLWritePropertyOperation();

    public abstract void endSLWritePropertyOperation();

    public abstract void beginSLUnboxOperation();

    public abstract void endSLUnboxOperation();

    public abstract void beginSLFunctionLiteralOperation();

    public abstract void endSLFunctionLiteralOperation();

    public abstract void beginSLToBooleanOperation();

    public abstract void endSLToBooleanOperation();

    public abstract void beginSLEvalRootOperation();

    public abstract void endSLEvalRootOperation();

    public abstract void beginSLInvokeOperation();

    public abstract void endSLInvokeOperation();

    public static OperationsNode[] parse(SLLanguage language, SLSource context) {
        SLOperationsBuilderImpl builder = new SLOperationsBuilderImpl(language, context, false, false);
        SLOperations.parse(language, context, builder);
        return builder.collect();
    }

    public static OperationsNode[] parseWithSourceInfo(SLLanguage language, SLSource context) {
        SLOperationsBuilderImpl builder = new SLOperationsBuilderImpl(language, context, true, true);
        SLOperations.parse(language, context, builder);
        return builder.collect();
    }

    @GeneratedBy(SLOperations.class)
    @SuppressWarnings({"cast", "hiding", "unchecked", "rawtypes", "static-method"})
    private static class SLOperationsBuilderImpl extends SLOperationsBuilder {

        private static final OperationsBytesSupport LE_BYTES = OperationsBytesSupport.littleEndian();
        private static final int OP_BLOCK = 1;
        private static final int OP_IF_THEN = 2;
        private static final int OP_IF_THEN_ELSE = 3;
        private static final int OP_CONDITIONAL = 4;
        private static final int OP_WHILE = 5;
        private static final int OP_TRY_CATCH = 6;
        private static final int OP_LABEL = 7;
        private static final int OP_BRANCH = 8;
        private static final int OP_CONST_OBJECT = 9;
        private static final int OP_LOAD_ARGUMENT = 10;
        private static final int OP_STORE_LOCAL = 11;
        private static final int OP_LOAD_LOCAL = 12;
        private static final int OP_RETURN = 13;
        private static final int OP_INSTRUMENTATION = 14;
        private static final int OP_SLADD_OPERATION = 15;
        private static final int OP_SLDIV_OPERATION = 16;
        private static final int OP_SLEQUAL_OPERATION = 17;
        private static final int OP_SLLESS_OR_EQUAL_OPERATION = 18;
        private static final int OP_SLLESS_THAN_OPERATION = 19;
        private static final int OP_SLLOGICAL_NOT_OPERATION = 20;
        private static final int OP_SLMUL_OPERATION = 21;
        private static final int OP_SLREAD_PROPERTY_OPERATION = 22;
        private static final int OP_SLSUB_OPERATION = 23;
        private static final int OP_SLWRITE_PROPERTY_OPERATION = 24;
        private static final int OP_SLUNBOX_OPERATION = 25;
        private static final int OP_SLFUNCTION_LITERAL_OPERATION = 26;
        private static final int OP_SLTO_BOOLEAN_OPERATION = 27;
        private static final int OP_SLEVAL_ROOT_OPERATION = 28;
        private static final int OP_SLINVOKE_OPERATION = 29;
        private static final int INSTR_POP = 1;
        private static final int INSTR_BRANCH = 2;
        private static final int INSTR_BRANCH_FALSE = 3;
        private static final int INSTR_LOAD_CONSTANT_OBJECT = 4;
        private static final int INSTR_LOAD_CONSTANT_LONG = 5;
        private static final int INSTR_LOAD_CONSTANT_BOOLEAN = 6;
        private static final int INSTR_LOAD_ARGUMENT_OBJECT = 7;
        private static final int INSTR_LOAD_ARGUMENT_LONG = 8;
        private static final int INSTR_LOAD_ARGUMENT_BOOLEAN = 9;
        private static final int INSTR_STORE_LOCAL = 10;
        private static final int INSTR_LOAD_LOCAL_OBJECT = 11;
        private static final int INSTR_LOAD_LOCAL_LONG = 12;
        private static final int INSTR_LOAD_LOCAL_BOOLEAN = 13;
        private static final int INSTR_RETURN = 14;
        private static final int INSTR_INSTRUMENT_ENTER = 15;
        private static final int INSTR_INSTRUMENT_EXIT_VOID = 16;
        private static final int INSTR_INSTRUMENT_EXIT = 17;
        private static final int INSTR_INSTRUMENT_LEAVE = 18;
        private static final int INSTR_C_SLADD_OPERATION = 19;
        private static final int INSTR_C_SLDIV_OPERATION = 20;
        private static final int INSTR_C_SLEQUAL_OPERATION = 21;
        private static final int INSTR_C_SLLESS_OR_EQUAL_OPERATION = 22;
        private static final int INSTR_C_SLLESS_THAN_OPERATION = 23;
        private static final int INSTR_C_SLLOGICAL_NOT_OPERATION = 24;
        private static final int INSTR_C_SLMUL_OPERATION = 25;
        private static final int INSTR_C_SLREAD_PROPERTY_OPERATION = 26;
        private static final int INSTR_C_SLSUB_OPERATION = 27;
        private static final int INSTR_C_SLWRITE_PROPERTY_OPERATION = 28;
        private static final int INSTR_C_SLUNBOX_OPERATION = 29;
        private static final int INSTR_C_SLFUNCTION_LITERAL_OPERATION = 30;
        private static final int INSTR_C_SLTO_BOOLEAN_OPERATION = 31;
        private static final int INSTR_C_SLEVAL_ROOT_OPERATION = 32;
        private static final int INSTR_C_SLINVOKE_OPERATION = 33;
        private static final int INSTR_C_SLUNBOX_OPERATION_Q_FROM_LONG = 34;
        private static final int INSTR_C_SLADD_OPERATION_Q_ADD_LONG = 35;
        private static final int INSTR_C_SLREAD_PROPERTY_OPERATION_Q_READ_SLOBJECT0 = 36;
        private static final int INSTR_C_SLUNBOX_OPERATION_Q_FROM_BOOLEAN = 37;
        private static final int INSTR_C_SLTO_BOOLEAN_OPERATION_Q_BOOLEAN = 38;
        private static final int INSTR_C_SLLESS_OR_EQUAL_OPERATION_Q_LESS_OR_EQUAL0 = 39;
        private static final int INSTR_C_SLINVOKE_OPERATION_Q_DIRECT = 40;
        private static final int INSTR_C_SLFUNCTION_LITERAL_OPERATION_Q_PERFORM = 41;
        private static final int INSTR_C_SLWRITE_PROPERTY_OPERATION_Q_WRITE_SLOBJECT0 = 42;
        private static final int INSTR_C_SLLESS_THAN_OPERATION_Q_LESS_THAN0 = 43;
        private static final short[][] BOXING_DESCRIPTORS = {
        // OBJECT
        {-1, 0, 0, 0, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, 0, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, 0, 0, 0, 0, 0, (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1)},
        // BYTE
        null,
        // BOOLEAN
        {-1, 0, 0, 0, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_ARGUMENT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, 0, INSTR_LOAD_LOCAL_BOOLEAN, INSTR_LOAD_LOCAL_LONG, INSTR_LOAD_LOCAL_BOOLEAN, 0, 0, 0, 0, 0, (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1)},
        // INT
        null,
        // FLOAT
        null,
        // LONG
        {-1, 0, 0, 0, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, 0, INSTR_LOAD_LOCAL_LONG, INSTR_LOAD_LOCAL_LONG, INSTR_LOAD_LOCAL_BOOLEAN, 0, 0, 0, 0, 0, (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (3 << 8) | 0x1), (short) (0x8000 | (5 << 8) | 0x1), (short) (0x8000 | (4 << 8) | 0x1)},
        // DOUBLE
        null};

        private final SLLanguage language;
        private final SLSource parseContext;
        private final boolean keepSourceInfo;
        private final boolean keepInstrumentation;
        private final BuilderSourceInfo sourceInfoBuilder;
        byte[] bc = new byte[65535];
        ArrayList<BuilderExceptionHandler> exceptionHandlers = new ArrayList<>();
        int lastPush;
        int bci;
        int numChildNodes;
        int numBranchProfiles;
        ArrayList<OperationsNode> builtNodes = new ArrayList<>();
        int nodeNumber = 0;
        OperationsConstantPool constPool = new OperationsConstantPool();

        SLOperationsBuilderImpl(SLLanguage language, SLSource parseContext, boolean keepSourceInfo, boolean keepInstrumentation) {
            this.language = language;
            this.parseContext = parseContext;
            this.keepSourceInfo = keepSourceInfo;
            this.keepInstrumentation = keepInstrumentation;
            if (keepSourceInfo) {
                sourceInfoBuilder = new BuilderSourceInfo();
            } else {
                sourceInfoBuilder = null;
            }
            reset();
        }

        @Override
        public void reset() {
            super.reset();
            bci = 0;
            numChildNodes = 0;
            numBranchProfiles = 0;
            operationData = new BuilderOperationData(null, 0, 0, 0, false);
            exceptionHandlers.clear();
            constPool.reset();
            if (keepSourceInfo) {
                sourceInfoBuilder.reset();
            }
        }

        @Override
        public OperationsNode buildImpl() {
            labelPass(bc);
            if (operationData.depth != 0) {
                throw new IllegalStateException("Not all operations ended");
            }
            byte[] bcCopy = java.util.Arrays.copyOf(bc, bci);
            Object[] cpCopy = constPool.getValues();
            BuilderExceptionHandler[] handlers = exceptionHandlers.toArray(new BuilderExceptionHandler[0]);
            int[][] sourceInfo = null;
            Source[] sources = null;
            if (keepSourceInfo) {
                sourceInfo = sourceInfoBuilder.build();
                sources = sourceInfoBuilder.buildSource();
            }
            OperationsNode result;
            ConditionProfile[] condProfiles = new ConditionProfile[numBranchProfiles];
            for (int i = 0; i < numBranchProfiles; i++) {
                condProfiles[i] = ConditionProfile.createCountingProfile();
            }
            result = new SLOperationsBuilderImplBytecodeNode(parseContext, sourceInfo, sources, nodeNumber, createMaxStack(), maxLocals + 1, bcCopy, cpCopy, new Node[numChildNodes], handlers, condProfiles);
            builtNodes.add(result);
            nodeNumber++;
            reset();
            return result;
        }

        @Override
        protected void doLeaveOperation(BuilderOperationData data) {
            while (getCurStack() > data.stackDepth) {
                int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                bci = bci + 2;
            }
            switch (data.operationId) {
                case OP_INSTRUMENTATION :
                {
                    int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                    LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_LEAVE);
                    LE_BYTES.putShort(bc, bci + 2, (short) (int) ((int) data.aux[0]));
                    createOffset(bci + 4, ((BuilderOperationLabel) data.aux[1]));
                    createOffset(bci + 6, ((BuilderOperationLabel) data.aux[2]));
                    bci = bci + 8;
                    break;
                }
            }
        }

        @Override
        public void beginSource(Supplier<Source> supplier) {
            if (!keepSourceInfo) {
                return;
            }
            beginSource(supplier.get());
        }

        @Override
        public void beginSource(Source source) {
            if (!keepSourceInfo) {
                return;
            }
            sourceInfoBuilder.beginSource(bci, source);
        }

        @Override
        public void endSource() {
            if (!keepSourceInfo) {
                return;
            }
            sourceInfoBuilder.endSource(bci);
        }

        @Override
        public void beginSourceSection(int start) {
            if (!keepSourceInfo) {
                return;
            }
            sourceInfoBuilder.beginSourceSection(bci, start);
        }

        @Override
        public void endSourceSection(int length) {
            if (!keepSourceInfo) {
                return;
            }
            sourceInfoBuilder.endSourceSection(bci, length);
        }

        @SuppressWarnings("unused")
        void doBeforeChild() {
            int childIndex = operationData.numChildren;
            switch (operationData.operationId) {
                case OP_BLOCK :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
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
                case OP_INSTRUMENTATION :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
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
                        assert lastPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = endLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, endLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) numBranchProfiles++);
                        bci = bci + 7;
                    } else {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[0]));
                    }
                    break;
                }
                case OP_IF_THEN_ELSE :
                {
                    if (childIndex == 0) {
                        assert lastPush == 1;
                        BuilderOperationLabel elseLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = elseLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, elseLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) numBranchProfiles++);
                        bci = bci + 7;
                    } else if (childIndex == 1) {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, (BuilderOperationLabel) endLabel);
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, endLabel);
                        bci = bci + 4;
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[0]));
                    } else {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_CONDITIONAL :
                {
                    if (childIndex == 0) {
                        assert lastPush == 1;
                        BuilderOperationLabel elseLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[0] = elseLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, elseLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) numBranchProfiles++);
                        bci = bci + 7;
                    } else if (childIndex == 1) {
                        assert lastPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, (BuilderOperationLabel) endLabel);
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, endLabel);
                        bci = bci + 4;
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[0]));
                    } else {
                        assert lastPush == 1;
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_WHILE :
                {
                    if (childIndex == 0) {
                        assert lastPush == 1;
                        BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
                        operationData.aux[1] = endLabel;
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH_FALSE);
                        createOffset(bci + 2, endLabel);
                        bc[bci + 4] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
                        LE_BYTES.putShort(bc, bci + 5, (short) (int) numBranchProfiles++);
                        bci = bci + 7;
                    } else {
                        for (int i = 0; i < lastPush; i++) {
                            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                            LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                            bci = bci + 2;
                        }
                        calculateLeaves(operationData, (BuilderOperationLabel) ((BuilderOperationLabel) operationData.aux[0]));
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, ((BuilderOperationLabel) operationData.aux[0]));
                        bci = bci + 4;
                        doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[1]));
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    for (int i = 0; i < lastPush; i++) {
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_POP);
                        bci = bci + 2;
                    }
                    if (childIndex == 0) {
                        ((BuilderExceptionHandler) operationData.aux[0]).endBci = bci;
                        calculateLeaves(operationData, (BuilderOperationLabel) ((BuilderOperationLabel) operationData.aux[1]));
                        int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                        LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
                        createOffset(bci + 2, ((BuilderOperationLabel) operationData.aux[1]));
                        bci = bci + 4;
                    } else {
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unused")
        @Override
        public void beginBlock() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BLOCK, getCurStack(), 0, false);
            lastPush = 0;
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
            lastPush = 0;
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
            lastPush = 0;
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
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginWhile() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_WHILE, getCurStack(), 2, false);
            BuilderOperationLabel startLabel = (BuilderOperationLabel) createLabel();
            doEmitLabel(bci, startLabel);
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
            lastPush = 0;
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
            exceptionHandlers.add(beh);
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
            doEmitLabel(bci, ((BuilderOperationLabel) operationData.aux[1]));
            lastPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLabel(OperationLabel arg0) {
            doBeforeChild();
            BuilderOperationData operationData = new BuilderOperationData(this.operationData, OP_LABEL, getCurStack(), 0, false, arg0);
            doEmitLabel(bci, ((BuilderOperationLabel) operationData.arguments[0]));
            lastPush = 0;
            doAfterChild();
        }

        @Override
        public void emitBranch(OperationLabel arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(this.operationData, OP_BRANCH, getCurStack(), 0, false, arg0);
            calculateLeaves(operationData, (BuilderOperationLabel) operationData.arguments[0]);
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_BRANCH);
            createOffset(bci + 2, operationData.arguments[0]);
            bci = bci + 4;
            lastPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitConstObject(Object arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(this.operationData, OP_CONST_OBJECT, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_CONSTANT_OBJECT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) constPool.add(arg0));
            bci = bci + 4;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadArgument(int arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(this.operationData, OP_LOAD_ARGUMENT, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_ARGUMENT_OBJECT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) operationData.arguments[0]);
            bci = bci + 4;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginStoreLocal(int arg0) {
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
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_STORE_LOCAL);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            LE_BYTES.putShort(bc, bci + 3, (short) (int) trackLocalsHelper(operationData.arguments[0]));
            bci = bci + 5;
            lastPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadLocal(int arg0) {
            doBeforeChild();
            operationData = new BuilderOperationData(this.operationData, OP_LOAD_LOCAL, getCurStack(), 0, false, arg0);
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_LOCAL_OBJECT);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) operationData.arguments[0]);
            bci = bci + 4;
            lastPush = 1;
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
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_RETURN);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bci = bci + 3;
            lastPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginInstrumentation(Class<?> arg0) {
            if (true) {
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_INSTRUMENTATION, getCurStack(), 3, true, arg0);
            int curInstrumentId = doBeginInstrumentation((Class) arg0);
            BuilderOperationLabel startLabel = (BuilderOperationLabel) createLabel();
            BuilderOperationLabel endLabel = (BuilderOperationLabel) createLabel();
            doEmitLabel(bci, startLabel);
            operationData.aux[0] = curInstrumentId;
            operationData.aux[1] = startLabel;
            operationData.aux[2] = endLabel;
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
            LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_ENTER);
            LE_BYTES.putShort(bc, bci + 2, (short) (int) curInstrumentId);
            bci = bci + 4;
            lastPush = 0;
        }

        @SuppressWarnings("unused")
        @Override
        public void endInstrumentation() {
            if (true) {
                return;
            }
            if (operationData.operationId != OP_INSTRUMENTATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("Instrumentation expected at least 0 children, got " + numChildren);
            }
            if (lastPush != 0) {
                int[] predecessorBcis = doBeforeEmitInstruction(bci, 0, false);
                LE_BYTES.putShort(bc, bci, (short) INSTR_INSTRUMENT_EXIT_VOID);
                LE_BYTES.putShort(bc, bci + 2, (short) (int) ((int) operationData.aux[0]));
                bci = bci + 4;
            } else {
                int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
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
        public void beginSLAddOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLADD_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLAddOperation() {
            if (operationData.operationId != OP_SLADD_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLAddOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLADD_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 7 bytes: [BITS, BITS, BITS, CONST, CONTINUATION, CHILD, CONTINUATION]
            //   numChildNodes = 3
            //   numConsts     = 1
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 3, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 4 + 5, (short) numChildNodes);
            numChildNodes += 3;
            bci = bci + 11;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLDivOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLDIV_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLDivOperation() {
            if (operationData.operationId != OP_SLDIV_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLDivOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLDIV_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLEqualOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLEQUAL_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLEqualOperation() {
            if (operationData.operationId != OP_SLEQUAL_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLEqualOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLEQUAL_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 7 bytes: [BITS, BITS, CHILD, CONTINUATION, CONST, CONTINUATION, BITS]
            //   numChildNodes = 3
            //   numConsts     = 1
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 2, (short) numChildNodes);
            LE_BYTES.putShort(bc, bci + 4 + 4, (short) constPool.reserve());
            bc[bci + 4 + 6] = 0;
            numChildNodes += 3;
            bci = bci + 11;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessOrEqualOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLESS_OR_EQUAL_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessOrEqualOperation() {
            if (operationData.operationId != OP_SLLESS_OR_EQUAL_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessOrEqualOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLESS_OR_EQUAL_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bci = bci + 5;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessThanOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLESS_THAN_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessThanOperation() {
            if (operationData.operationId != OP_SLLESS_THAN_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLLessThanOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLESS_THAN_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bci = bci + 5;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLogicalNotOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLLOGICAL_NOT_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLogicalNotOperation() {
            if (operationData.operationId != OP_SLLOGICAL_NOT_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLLogicalNotOperation expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLLOGICAL_NOT_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 3 + 0] = 0;
            bci = bci + 4;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLMulOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLMUL_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLMulOperation() {
            if (operationData.operationId != OP_SLMUL_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLMulOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLMUL_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLReadPropertyOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLREAD_PROPERTY_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLReadPropertyOperation() {
            if (operationData.operationId != OP_SLREAD_PROPERTY_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLReadPropertyOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 6
            //   numConsts     = 3
            bc[bci + 4 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 4 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 4 + 3, (short) numChildNodes);
            bc[bci + 4 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            numChildNodes += 6;
            bci = bci + 10;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLSubOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLSUB_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLSubOperation() {
            if (operationData.operationId != OP_SLSUB_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 2) {
                throw new IllegalStateException("SLSubOperation expected 2 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 2, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLSUB_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            // additionalData  = 2 bytes: [BITS, BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLWritePropertyOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLWRITE_PROPERTY_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLWritePropertyOperation() {
            if (operationData.operationId != OP_SLWRITE_PROPERTY_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 3) {
                throw new IllegalStateException("SLWritePropertyOperation expected 3 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 3, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            bc[bci + 3] = predecessorBcis[1] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[1]);
            bc[bci + 4] = predecessorBcis[2] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[2]);
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 7
            //   numConsts     = 4
            bc[bci + 5 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 5 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 5 + 3, (short) numChildNodes);
            bc[bci + 5 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            constPool.reserve();
            numChildNodes += 7;
            bci = bci + 11;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLUnboxOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLUNBOX_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLUnboxOperation() {
            if (operationData.operationId != OP_SLUNBOX_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLUnboxOperation expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLUNBOX_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 7 bytes: [BITS, BITS, CHILD, CONTINUATION, CONST, CONTINUATION, BITS]
            //   numChildNodes = 2
            //   numConsts     = 1
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            LE_BYTES.putShort(bc, bci + 3 + 2, (short) numChildNodes);
            LE_BYTES.putShort(bc, bci + 3 + 4, (short) constPool.reserve());
            bc[bci + 3 + 6] = 0;
            numChildNodes += 2;
            bci = bci + 10;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLFunctionLiteralOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLFUNCTION_LITERAL_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLFunctionLiteralOperation() {
            if (operationData.operationId != OP_SLFUNCTION_LITERAL_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLFunctionLiteralOperation expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLFUNCTION_LITERAL_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 3 bytes: [BITS, CONST, CONTINUATION]
            //   numChildNodes = 0
            //   numConsts     = 1
            bc[bci + 3 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 3 + 1, (short) constPool.reserve());
            bci = bci + 6;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLToBooleanOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLTO_BOOLEAN_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLToBooleanOperation() {
            if (operationData.operationId != OP_SLTO_BOOLEAN_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLToBooleanOperation expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLTO_BOOLEAN_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 3 + 0] = 0;
            bci = bci + 4;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLEvalRootOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLEVAL_ROOT_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLEvalRootOperation() {
            if (operationData.operationId != OP_SLEVAL_ROOT_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren != 1) {
                throw new IllegalStateException("SLEvalRootOperation expected 1 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, 1, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLEVAL_ROOT_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            // additionalData  = 1 bytes: [BITS]
            //   numChildNodes = 0
            //   numConsts     = 0
            bc[bci + 3 + 0] = 0;
            bci = bci + 4;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLInvokeOperation() {
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SLINVOKE_OPERATION, getCurStack(), 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLInvokeOperation() {
            if (operationData.operationId != OP_SLINVOKE_OPERATION) {
                throw new IllegalStateException("Mismatched begin/end, expected " + operationData.operationId);
            }
            int numChildren = operationData.numChildren;
            if (numChildren < 0) {
                throw new IllegalStateException("SLInvokeOperation expected at least 0 children, got " + numChildren);
            }
            int[] predecessorBcis = doBeforeEmitInstruction(bci, numChildren, true);
            LE_BYTES.putShort(bc, bci, (short) INSTR_C_SLINVOKE_OPERATION);
            bc[bci + 2] = predecessorBcis[0] < bci - 255 ? 0 : (byte)(bci - predecessorBcis[0]);
            LE_BYTES.putShort(bc, bci + 3, (short) (int) (numChildren - 1));
            // additionalData  = 6 bytes: [BITS, CONST, CONTINUATION, CHILD, CONTINUATION, BITS]
            //   numChildNodes = 3
            //   numConsts     = 3
            bc[bci + 5 + 0] = 0;
            LE_BYTES.putShort(bc, bci + 5 + 1, (short) constPool.reserve());
            LE_BYTES.putShort(bc, bci + 5 + 3, (short) numChildNodes);
            bc[bci + 5 + 5] = 0;
            constPool.reserve();
            constPool.reserve();
            numChildNodes += 3;
            bci = bci + 11;
            lastPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        private static OperationsNode reparse(SLLanguage language, SLSource context, int buildOrder) {
            SLOperationsBuilderImpl builder = new SLOperationsBuilderImpl(language, context, true, true);
            SLOperations.parse(language, context, builder);
            return builder.collect()[buildOrder];
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
         * load.constant.object
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
         * load.constant.boolean
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
         * load.argument.long
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
         * store.local
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
         * load.local.long
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
         * c.SLAddOperation
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
         * c.SLDivOperation
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
         * c.SLEqualOperation
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
         * c.SLLessOrEqualOperation
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
         * c.SLLessThanOperation
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
         * c.SLLogicalNotOperation
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
         * c.SLMulOperation
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
         * c.SLReadPropertyOperation
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
         * c.SLSubOperation
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
         * c.SLWritePropertyOperation
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
         * c.SLUnboxOperation
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
         * c.SLFunctionLiteralOperation
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
         * c.SLToBooleanOperation
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
         * c.SLEvalRootOperation
         *   Inputs:
         *     STACK_VALUE
         *   Results:
         *     STACK_VALUE
         *   Additional Data:
         *     0 BITS
         *   Specializations:
         *     Execute
         *     Fallback
         *
         * c.SLInvokeOperation
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
         * c.SLUnboxOperation.q.FromLong
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
         * c.SLAddOperation.q.AddLong
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
         * c.SLReadPropertyOperation.q.ReadSLObject0
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
         * c.SLUnboxOperation.q.FromBoolean
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
         * c.SLToBooleanOperation.q.Boolean
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
         * c.SLLessOrEqualOperation.q.LessOrEqual0
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
         * c.SLInvokeOperation.q.Direct
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
         * c.SLFunctionLiteralOperation.q.Perform
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
         * c.SLWritePropertyOperation.q.WriteSLObject0
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
         * c.SLLessThanOperation.q.LessThan0
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
        private static final class SLOperationsBuilderImplBytecodeNode extends OperationsNode implements Provider {

            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY__ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY___ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY__ = LibraryFactory.resolve(DynamicObjectLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY____ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_____ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY______ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_______ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY___ = LibraryFactory.resolve(DynamicObjectLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY________ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_________ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY__________ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY____ = LibraryFactory.resolve(DynamicObjectLibrary.class);

            @CompilationFinal(dimensions = 1) private final byte[] bc;
            @CompilationFinal(dimensions = 1) private final Object[] consts;
            @Children private final Node[] children;
            @CompilationFinal(dimensions = 1) private final BuilderExceptionHandler[] handlers;
            @CompilationFinal(dimensions = 1) private final ConditionProfile[] conditionProfiles;

            SLOperationsBuilderImplBytecodeNode(Object parseContext, int[][] sourceInfo, Source[] sources, int buildOrder, int maxStack, int maxLocals, byte[] bc, Object[] consts, Node[] children, BuilderExceptionHandler[] handlers, ConditionProfile[] conditionProfiles) {
                super(parseContext, sourceInfo, sources, buildOrder, maxStack, maxLocals);
                this.bc = bc;
                this.consts = consts;
                this.children = children;
                this.handlers = handlers;
                this.conditionProfiles = conditionProfiles;
            }

            private void SLAddOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */ && ((state_0 & 0b11110) != 0  /* is-not addLong(long, long) && add(SLBigNumber, SLBigNumber) && add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) && typeError(Object, Object, Node, int) */)) {
                    SLAddOperation_SLAddOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLAddOperation_SLAddOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLAddOperation_SLAddOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLAddOperation_SLAddOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                            SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
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
                        SLAddOperation_Add1Data s2_ = ((SLAddOperation_Add1Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]);
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
                            if (SLAddOperation_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_, $child1Value_)) {
                                $frame.setObject($sp - 2, SLAddNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLAddOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_OPERATION_Q_ADD_LONG);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_OPERATION);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */ && ((state_0 & 0b11110) != 0  /* is-not addLong(long, long) && add(SLBigNumber, SLBigNumber) && add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) && typeError(Object, Object, Node, int) */)) {
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
                                SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_OPERATION);
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
                        SLAddOperation_Add1Data s2_ = super.insert(new SLAddOperation_Add1Data());
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 0] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 1] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                        children[LE_BYTES.getShort(bc, $bci + 4 + 5) + 2] = s2_.insertAccessor((ConcatNode.create()));
                        VarHandle.storeStoreFence();
                        consts[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0] = s2_;
                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */);
                        bc[$bci + 4 + 2] = (byte) (state_1);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLADD_OPERATION);
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

            private void SLDivOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not divLong(long, long) && div(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLDivOperation_SLDivOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLDivOperation_SLDivOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLDivOperation_SLDivOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                    SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLDivOperation_SLDivOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                            SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
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
                        if (SLDivOperation_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLDivNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLDivOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                            if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not divLong(long, long) && div(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
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
                                SLDivOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
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

            private void SLEqualOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                byte state_1 = bc[$bci + 4 + 1];
                if ((state_0 & 0b11111100) == 0 /* only-active doLong(long, long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqualOperation_SLEqualOperation_execute__long_long0_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else if ((state_0 & 0b11110110) == 0 /* only-active doBoolean(boolean, boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqualOperation_SLEqualOperation_execute__boolean_boolean1_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else {
                    SLEqualOperation_SLEqualOperation_execute__generic2_($frame, $bci, $sp, state_0, state_1);
                    return;
                }
            }

            private void SLEqualOperation_SLEqualOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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

            private void SLEqualOperation_SLEqualOperation_execute__boolean_boolean1_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 2)) {
                    $child0Value_ = $frame.getBoolean($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                boolean $child1Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child1Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child1Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
            private Object SLEqualOperation_generic1Boundary_(int $bci, int $sp, byte state_0, byte state_1, Object $child0Value_, Object $child1Value_) {
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
            private void SLEqualOperation_SLEqualOperation_execute__generic2_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                            SLEqualOperation_Generic0Data s7_ = ((SLEqualOperation_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0]);
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
                            $frame.setObject($sp - 2, this.SLEqualOperation_generic1Boundary_($bci, $sp, state_0, state_1, $child0Value_, $child1Value_));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLEqualOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                            if ((state_0 & 0b11111100) == 0 /* only-active doLong(long, long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
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
                            if ((state_0 & 0b11110110) == 0 /* only-active doBoolean(boolean, boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) */ || (state_1 & 0b11) != 0  /* is-not doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
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
                        SLEqualOperation_Generic0Data s7_ = ((SLEqualOperation_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0]);
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
                                s7_ = super.insert(new SLEqualOperation_Generic0Data(((SLEqualOperation_Generic0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 4) + 0])));
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

            private void SLLessOrEqualOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessOrEqualOperation_SLLessOrEqualOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLessOrEqualOperation_SLLessOrEqualOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLessOrEqualOperation_SLLessOrEqualOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLLessOrEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessOrEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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

            private void SLLessOrEqualOperation_SLLessOrEqualOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                        if (SLLessOrEqualOperation_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessOrEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLLessOrEqualOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL_OPERATION_Q_LESS_OR_EQUAL0);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL_OPERATION);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_OR_EQUAL_OPERATION);
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

            private void SLLessThanOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessThanOperation_SLLessThanOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLessThanOperation_SLLessThanOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLessThanOperation_SLLessThanOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLLessThanOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessThanOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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

            private void SLLessThanOperation_SLLessThanOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                        if (SLLessThanOperation_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessThanOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLLessThanOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN_OPERATION_Q_LESS_THAN0);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN_OPERATION);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLLESS_THAN_OPERATION);
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

            private void SLLogicalNotOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && typeError(Object, Node, int) */)) {
                    SLLogicalNotOperation_SLLogicalNotOperation_execute__boolean0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLLogicalNotOperation_SLLogicalNotOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLLogicalNotOperation_SLLogicalNotOperation_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child0Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLogicalNotOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLLogicalNotOperation_SLLogicalNotOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
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
                        if (SLLogicalNotOperation_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                            $frame.setObject($sp - 1, SLLogicalNotNode.typeError($child0Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLogicalNotOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLLogicalNotOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && typeError(Object, Node, int) */)) {
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

            private void SLMulOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not mulLong(long, long) && mul(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLMulOperation_SLMulOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLMulOperation_SLMulOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLMulOperation_SLMulOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                    SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLMulOperation_SLMulOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                            SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
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
                        if (SLMulOperation_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLMulNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLMulOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                            if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not mulLong(long, long) && mul(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
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
                                SLMulOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
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
            private void SLReadPropertyOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLReadPropertyOperation_ReadArray0Data s0_ = ((SLReadPropertyOperation_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
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
                                    InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY__.getUncached());
                                    if ((readArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 2, this.SLReadPropertyOperation_readArray1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_));
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
                            SLReadPropertyOperation_ReadSLObject0Data s2_ = ((SLReadPropertyOperation_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1]);
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
                            $frame.setObject($sp - 2, this.SLReadPropertyOperation_readSLObject1Boundary_($bci, $sp, state_0, $child0Value__, $child1Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLReadPropertyOperation_ReadObject0Data s4_ = ((SLReadPropertyOperation_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2]);
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
                                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY__.getUncached());
                                    if ((readObject1_objects__.hasMembers($child0Value_))) {
                                        $frame.setObject($sp - 2, this.SLReadPropertyOperation_readObject1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_));
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
                SLReadPropertyOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadPropertyOperation_readArray1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_) {
                {
                    Node readArray1_node__ = (this);
                    int readArray1_bci__ = ($bci);
                    InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY__.getUncached());
                    InteropLibrary readArray1_numbers__ = (INTEROP_LIBRARY__.getUncached());
                    return SLReadPropertyNode.readArray($child0Value_, $child1Value_, readArray1_node__, readArray1_bci__, readArray1_arrays__, readArray1_numbers__);
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadPropertyOperation_readSLObject1Boundary_(int $bci, int $sp, byte state_0, SLObject $child0Value__, Object $child1Value_) {
                {
                    Node readSLObject1_node__ = (this);
                    int readSLObject1_bci__ = ($bci);
                    DynamicObjectLibrary readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value__));
                    return SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3]));
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLReadPropertyOperation_readObject1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_) {
                {
                    Node readObject1_node__ = (this);
                    int readObject1_bci__ = ($bci);
                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY__.getUncached());
                    return SLReadPropertyNode.readObject($child0Value_, $child1Value_, readObject1_node__, readObject1_bci__, readObject1_objects__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5]));
                }
            }

            private void SLReadPropertyOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                            SLReadPropertyOperation_ReadArray0Data s0_ = ((SLReadPropertyOperation_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
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
                                    InteropLibrary arrays__ = super.insert((INTEROP_LIBRARY__.create($child0Value)));
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0]).accepts($child0Value));
                                    // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1]).accepts($child1Value));
                                    if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                        s0_ = super.insert(new SLReadPropertyOperation_ReadArray0Data(((SLReadPropertyOperation_ReadArray0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0])));
                                        node__ = (this);
                                        bci__ = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 0] = s0_.insertAccessor(arrays__);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 1] = s0_.insertAccessor((INTEROP_LIBRARY__.create($child1Value)));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0] = s0_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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
                                    readArray1_arrays__ = (INTEROP_LIBRARY__.getUncached());
                                    if ((readArray1_arrays__.hasArrayElements($child0Value))) {
                                        readArray1_node__ = (this);
                                        readArray1_bci__ = ($bci);
                                        readArray1_numbers__ = (INTEROP_LIBRARY__.getUncached());
                                        bc[$bci + 4 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0] = null;
                                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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
                                SLReadPropertyOperation_ReadSLObject0Data s2_ = ((SLReadPropertyOperation_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1]);
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
                                        s2_ = super.insert(new SLReadPropertyOperation_ReadSLObject0Data(((SLReadPropertyOperation_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1])));
                                        node__1 = (this);
                                        bci__1 = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 2] = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 3] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 1] = s2_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                        if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION_Q_READ_SLOBJECT0);
                                        } else {
                                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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
                            SLReadPropertyOperation_ReadObject0Data s4_ = ((SLReadPropertyOperation_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2]);
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
                                    InteropLibrary objects__ = super.insert((INTEROP_LIBRARY__.create($child0Value)));
                                    if ((objects__.hasMembers($child0Value)) && count4_ < (SLReadPropertyNode.LIBRARY_LIMIT)) {
                                        s4_ = super.insert(new SLReadPropertyOperation_ReadObject0Data(((SLReadPropertyOperation_ReadObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2])));
                                        node__2 = (this);
                                        bci__2 = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 4] = s4_.insertAccessor(objects__);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5] = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2] = s4_;
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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
                                    readObject1_objects__ = (INTEROP_LIBRARY__.getUncached());
                                    if ((readObject1_objects__.hasMembers($child0Value))) {
                                        readObject1_node__ = (this);
                                        readObject1_bci__ = ($bci);
                                        children[LE_BYTES.getShort(bc, $bci + 4 + 3) + 5] = super.insert((SLToMemberNodeGen.create()));
                                        bc[$bci + 4 + 5] = exclude = (byte) (exclude | 0b100 /* add-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 2] = null;
                                        state_0 = (byte) (state_0 & 0xffffffdf /* remove-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        bc[$bci + 4 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLREAD_PROPERTY_OPERATION);
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

            private void SLSubOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not subLong(long, long) && sub(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLSubOperation_SLSubOperation_execute__long_long0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLSubOperation_SLSubOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLSubOperation_SLSubOperation_execute__long_long0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                    SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            private void SLSubOperation_SLSubOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
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
                            SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value__, $child1Value__);
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
                        if (SLSubOperation_fallbackGuard__($frame, $bci, $sp, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLSubNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLSubOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
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
                            if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not subLong(long, long) && sub(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
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
                                SLSubOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
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
            private void SLWritePropertyOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 3);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 2);
                Object $child2Value_;
                $child2Value_ = $frame.getObject($sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLWritePropertyOperation_WriteArray0Data s0_ = ((SLWritePropertyOperation_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
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
                                    InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY___.getUncached());
                                    if ((writeArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 3, this.SLWritePropertyOperation_writeArray1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_, $child2Value_));
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
                            SLWritePropertyOperation_WriteSLObject0Data s2_ = ((SLWritePropertyOperation_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]);
                            while (s2_ != null) {
                                if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]).accepts($child0Value__))) {
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4])));
                                    return;
                                }
                                s2_ = s2_.next_;
                            }
                        }
                        if ((state_0 & 0b10000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            $frame.setObject($sp - 3, this.SLWritePropertyOperation_writeSLObject1Boundary_($bci, $sp, state_0, $child0Value__, $child1Value_, $child2Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLWritePropertyOperation_WriteObject0Data s4_ = ((SLWritePropertyOperation_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3]);
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
                                $frame.setObject($sp - 3, this.SLWritePropertyOperation_writeObject1Boundary_($bci, $sp, state_0, $child0Value_, $child1Value_, $child2Value_));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLWritePropertyOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWritePropertyOperation_writeArray1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
                {
                    InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY___.getUncached());
                    InteropLibrary writeArray1_numbers__ = (INTEROP_LIBRARY___.getUncached());
                    return SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, ((Node) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), ((int) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), writeArray1_arrays__, writeArray1_numbers__);
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWritePropertyOperation_writeSLObject1Boundary_(int $bci, int $sp, byte state_0, SLObject $child0Value__, Object $child1Value_, Object $child2Value_) {
                {
                    DynamicObjectLibrary writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY__.getUncached($child0Value__));
                    return SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, writeSLObject1_objectLibrary__, ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4]));
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private Object SLWritePropertyOperation_writeObject1Boundary_(int $bci, int $sp, byte state_0, Object $child0Value_, Object $child1Value_, Object $child2Value_) {
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set(this);
                try {
                    {
                        Node writeObject1_node__ = (this);
                        int writeObject1_bci__ = ($bci);
                        InteropLibrary writeObject1_objectLibrary__ = (INTEROP_LIBRARY___.getUncached($child0Value_));
                        return SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, ((SLToMemberNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6]));
                    }
                } finally {
                    encapsulating_.set(prev_);
                }
            }

            private void SLWritePropertyOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value, Object $child2Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 5 + 0];
                    byte exclude = bc[$bci + 5 + 5];
                    if (((exclude & 0b1)) == 0 /* is-not-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        int count0_ = 0;
                        SLWritePropertyOperation_WriteArray0Data s0_ = ((SLWritePropertyOperation_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
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
                                InteropLibrary arrays__ = super.insert((INTEROP_LIBRARY___.create($child0Value)));
                                // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value));
                                // assert (((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]).accepts($child1Value));
                                if ((arrays__.hasArrayElements($child0Value)) && count0_ < (SLWritePropertyNode.LIBRARY_LIMIT)) {
                                    s0_ = super.insert(new SLWritePropertyOperation_WriteArray0Data(((SLWritePropertyOperation_WriteArray0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0])));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = s0_.insertAccessor((this));
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = ($bci);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0] = s0_.insertAccessor(arrays__);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1] = s0_.insertAccessor((INTEROP_LIBRARY___.create($child1Value)));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = s0_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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
                                    writeArray1_arrays__ = (INTEROP_LIBRARY___.getUncached());
                                    if ((writeArray1_arrays__.hasArrayElements($child0Value))) {
                                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = super.insert((this));
                                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = ($bci);
                                        writeArray1_numbers__ = (INTEROP_LIBRARY___.getUncached());
                                        bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = null;
                                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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
                            SLWritePropertyOperation_WriteSLObject0Data s2_ = ((SLWritePropertyOperation_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]);
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
                                    s2_ = super.insert(new SLWritePropertyOperation_WriteSLObject0Data(((SLWritePropertyOperation_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2])));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 3] = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY__.create($child0Value_)));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4] = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = s2_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                    if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION_Q_WRITE_SLOBJECT0);
                                    } else {
                                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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
                            writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY__.getUncached($child0Value_));
                            children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 4] = super.insert((SLToTruffleStringNodeGen.create()));
                            bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b10 /* add-exclude writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = null;
                            state_0 = (byte) (state_0 & 0xfffffff7 /* remove-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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
                            SLWritePropertyOperation_WriteObject0Data s4_ = ((SLWritePropertyOperation_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3]);
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
                                    s4_ = super.insert(new SLWritePropertyOperation_WriteObject0Data(((SLWritePropertyOperation_WriteObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3])));
                                    node__ = (this);
                                    bci__ = ($bci);
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 5] = s4_.insertAccessor((INTEROP_LIBRARY___.create($child0Value)));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6] = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3] = s4_;
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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
                                    writeObject1_objectLibrary__ = (INTEROP_LIBRARY___.getUncached($child0Value));
                                    children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 6] = super.insert((SLToMemberNodeGen.create()));
                                    bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b100 /* add-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 3] = null;
                                    state_0 = (byte) (state_0 & 0xffffffdf /* remove-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLWRITE_PROPERTY_OPERATION);
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

            private void SLUnboxOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                byte state_1 = bc[$bci + 3 + 1];
                if ((state_0 & 0b11110110) == 0 /* only-active fromBoolean(boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnboxOperation_SLUnboxOperation_execute__boolean0_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else if ((state_0 & 0b11101110) == 0 /* only-active fromLong(long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnboxOperation_SLUnboxOperation_execute__long1_($frame, $bci, $sp, state_0, state_1);
                    return;
                } else {
                    SLUnboxOperation_SLUnboxOperation_execute__generic2_($frame, $bci, $sp, state_0, state_1);
                    return;
                }
            }

            private void SLUnboxOperation_SLUnboxOperation_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child0Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnboxOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLUnboxOperation_SLUnboxOperation_execute__long1_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                long $child0Value_;
                if ($frame.isLong($sp - 1)) {
                    $child0Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnboxOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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
            private Object SLUnboxOperation_fromForeign1Boundary_(int $bci, int $sp, byte state_0, byte state_1, Object $child0Value_) {
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set(this);
                try {
                    {
                        InteropLibrary fromForeign1_interop__ = (INTEROP_LIBRARY____.getUncached($child0Value_));
                        return SLUnboxNode.fromForeign($child0Value_, fromForeign1_interop__);
                    }
                } finally {
                    encapsulating_.set(prev_);
                }
            }

            @ExplodeLoop
            private void SLUnboxOperation_SLUnboxOperation_execute__generic2_(VirtualFrame $frame, int $bci, int $sp, byte state_0, byte state_1) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
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
                        SLUnboxOperation_FromForeign0Data s7_ = ((SLUnboxOperation_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0]);
                        while (s7_ != null) {
                            if ((((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1]).accepts($child0Value_))) {
                                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value_, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1])));
                                return;
                            }
                            s7_ = s7_.next_;
                        }
                    }
                    if (((state_1 & 0b10) != 0 /* is-state_1 fromForeign(Object, InteropLibrary) */)) {
                        $frame.setObject($sp - 1, this.SLUnboxOperation_fromForeign1Boundary_($bci, $sp, state_0, state_1, $child0Value_));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLUnboxOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLUnboxOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION_Q_FROM_BOOLEAN);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
                        }
                        int type0;
                        if ((state_0 & 0b11110110) == 0 /* only-active fromBoolean(boolean) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
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
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION_Q_FROM_LONG);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
                        }
                        int type0;
                        if ((state_0 & 0b11101110) == 0 /* only-active fromLong(long) */ && (state_1 & 0b11) == 0 /* only-active  */ && ((state_0 & 0b11111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) */ || (state_1 & 0b11) != 0  /* is-not fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
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
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                        SLUnboxOperation_FromForeign0Data s7_ = ((SLUnboxOperation_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0]);
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
                                s7_ = super.insert(new SLUnboxOperation_FromForeign0Data(((SLUnboxOperation_FromForeign0Data) consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0])));
                                children[LE_BYTES.getShort(bc, $bci + 3 + 2) + 1] = s7_.insertAccessor((INTEROP_LIBRARY____.create($child0Value)));
                                VarHandle.storeStoreFence();
                                consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0] = s7_;
                                bc[$bci + 3 + 0] = (byte) (state_0);
                                bc[$bci + 3 + 1] = state_1 = (byte) (state_1 | 0b1 /* add-state_1 fromForeign(Object, InteropLibrary) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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
                                fromForeign1_interop__ = (INTEROP_LIBRARY____.getUncached($child0Value));
                                bc[$bci + 3 + 6] = exclude = (byte) (exclude | 0b1 /* add-exclude fromForeign(Object, InteropLibrary) */);
                                consts[LE_BYTES.getShort(bc, $bci + 3 + 4) + 0] = null;
                                state_1 = (byte) (state_1 & 0xfffffffe /* remove-state_1 fromForeign(Object, InteropLibrary) */);
                                bc[$bci + 3 + 0] = (byte) (state_0);
                                bc[$bci + 3 + 1] = state_1 = (byte) (state_1 | 0b10 /* add-state_1 fromForeign(Object, InteropLibrary) */);
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLUNBOX_OPERATION);
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

            private void SLFunctionLiteralOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */ && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    {
                        Node node__ = (this);
                        $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value__, ((SLFunction) consts[LE_BYTES.getShort(bc, $bci + 3 + 1) + 0]), node__));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLFunctionLiteralOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLFunctionLiteralOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
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
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLFUNCTION_LITERAL_OPERATION_Q_PERFORM);
                            } else {
                                LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLFUNCTION_LITERAL_OPERATION);
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

            private void SLToBooleanOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    SLToBooleanOperation_SLToBooleanOperation_execute__boolean0_($frame, $bci, $sp, state_0);
                    return;
                } else {
                    SLToBooleanOperation_SLToBooleanOperation_execute__generic1_($frame, $bci, $sp, state_0);
                    return;
                }
            }

            private void SLToBooleanOperation_SLToBooleanOperation_execute__boolean0_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child0Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLToBooleanOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLToBooleanOperation_SLToBooleanOperation_execute__generic1_(VirtualFrame $frame, int $bci, int $sp, byte state_0) {
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
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
                        if (SLToBooleanOperation_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
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
                SLToBooleanOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLToBooleanOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        if ((state_0 & 0b110) == 0b10/* is-exact-state_0 doBoolean(boolean) */) {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN_OPERATION_Q_BOOLEAN);
                        } else {
                            LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN_OPERATION);
                        }
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
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
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLTO_BOOLEAN_OPERATION);
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

            private void SLEvalRootOperation_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 doExecute(VirtualFrame, Map<TruffleString, RootCallTarget>, Node) */ && $child0Value_ instanceof Map<?, ?>) {
                    Map<TruffleString, RootCallTarget> $child0Value__ = (Map<TruffleString, RootCallTarget>) $child0Value_;
                    {
                        Node execute_node__ = (this);
                        $frame.setObject($sp - 1, SLEvalRootOperation.doExecute($frame, $child0Value__, execute_node__));
                        return;
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 fallback(Object) */) {
                    if (SLEvalRootOperation_fallbackGuard__($frame, $bci, $sp, state_0, $child0Value_)) {
                        $frame.setObject($sp - 1, SLEvalRootOperation.fallback($child0Value_));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLEvalRootOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            private void SLEvalRootOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object $child0Value) {
                Lock lock = getLock();
                boolean hasLock = true;
                lock.lock();
                try {
                    byte state_0 = bc[$bci + 3 + 0];
                    {
                        Node execute_node__ = null;
                        if ($child0Value instanceof Map<?, ?>) {
                            Map<TruffleString, RootCallTarget> $child0Value_ = (Map<TruffleString, RootCallTarget>) $child0Value;
                            execute_node__ = (this);
                            bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doExecute(VirtualFrame, Map<TruffleString, RootCallTarget>, Node) */);
                            int type0;
                            type0 = FRAME_TYPE_OBJECT;
                            doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLEvalRootOperation.doExecute($frame, $child0Value_, execute_node__));
                            return;
                        }
                    }
                    bc[$bci + 3 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 fallback(Object) */);
                    int type0;
                    type0 = FRAME_TYPE_OBJECT;
                    doSetResultBoxed(bc, $bci, bc[$bci + 2], type0);
                    lock.unlock();
                    hasLock = false;
                    $frame.setObject($sp - 1, SLEvalRootOperation.fallback($child0Value));
                    return;
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            @ExplodeLoop
            private Object SLInvokeOperation_execute_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
                byte state_0 = bc[$bci + 5 + 0];
                if ((state_0 & 0b1110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) || doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) */ && arg0Value instanceof SLFunction) {
                        SLFunction arg0Value_ = (SLFunction) arg0Value;
                        if ((state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            SLInvokeOperation_DirectData s0_ = ((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                            while (s0_ != null) {
                                if (!Assumption.isValidAssumption((((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1])))) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    SLInvokeOperation_removeDirect__($frame, $bci, $sp, s0_);
                                    return SLInvokeOperation_executeAndSpecialize_($frame, $bci, $sp, arg0Value_, arg1Value);
                                }
                                if ((arg0Value_.getCallTarget() == ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]))) {
                                    return SLInvokeOperation.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */) {
                            return SLInvokeOperation.doIndirect(arg0Value_, arg1Value, ((IndirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]));
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                        {
                            Node interop_node__ = (this);
                            int interop_bci__ = ($bci);
                            return SLInvokeOperation.doInterop(arg0Value, arg1Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), interop_node__, interop_bci__);
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLInvokeOperation_executeAndSpecialize_($frame, $bci, $sp, arg0Value, arg1Value);
            }

            private Object SLInvokeOperation_executeAndSpecialize_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
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
                            SLInvokeOperation_DirectData s0_ = ((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
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
                                                s0_ = super.insert(new SLInvokeOperation_DirectData(((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0])));
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1] = callTargetStable__;
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2] = cachedTarget__;
                                                children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0] = s0_.insertAccessor((DirectCallNode.create(cachedTarget__)));
                                                VarHandle.storeStoreFence();
                                                consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = s0_;
                                                bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b10 /* add-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                                                if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE_OPERATION_Q_DIRECT);
                                                } else {
                                                    LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE_OPERATION);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                return SLInvokeOperation.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                            }
                        }
                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1] = super.insert((IndirectCallNode.create()));
                        bc[$bci + 5 + 5] = exclude = (byte) (exclude | 0b1 /* add-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0] = null;
                        state_0 = (byte) (state_0 & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b100 /* add-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE_OPERATION);
                        lock.unlock();
                        hasLock = false;
                        return SLInvokeOperation.doIndirect(arg0Value_, arg1Value, ((IndirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1]));
                    }
                    {
                        int interop_bci__ = 0;
                        Node interop_node__ = null;
                        children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2] = super.insert((INTEROP_LIBRARY_____.createDispatched(3)));
                        interop_node__ = (this);
                        interop_bci__ = ($bci);
                        bc[$bci + 5 + 0] = state_0 = (byte) (state_0 | 0b1000 /* add-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */);
                        LE_BYTES.putShort(bc, $bci, (short) INSTR_C_SLINVOKE_OPERATION);
                        lock.unlock();
                        hasLock = false;
                        return SLInvokeOperation.doInterop(arg0Value, arg1Value, ((InteropLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 2]), interop_node__, interop_bci__);
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private void SLInvokeOperation_removeDirect__(VirtualFrame $frame, int $bci, int $sp, Object s0_) {
                Lock lock = getLock();
                lock.lock();
                try {
                    SLInvokeOperation_DirectData prev = null;
                    SLInvokeOperation_DirectData cur = ((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
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

            private void SLUnboxOperation_q_FromLong_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                long $child0Value_;
                if ($frame.isLong($sp - 1)) {
                    $child0Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnboxOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLAddOperation_q_AddLong_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                    SLAddOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                    return;
                }
            }

            @ExplodeLoop
            private void SLReadPropertyOperation_q_ReadSLObject0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 2);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 1);
                assert (state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLReadPropertyOperation_ReadSLObject0Data s2_ = ((SLReadPropertyOperation_ReadSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 4 + 1) + 0]);
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
                SLReadPropertyOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_);
                return;
            }

            private void SLUnboxOperation_q_FromBoolean_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child0Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnboxOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLToBooleanOperation_q_Boolean_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                boolean $child0Value_;
                if ($frame.isBoolean($sp - 1)) {
                    $child0Value_ = $frame.getBoolean($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Boolean) {
                    $child0Value_ = (boolean) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLToBooleanOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 1));
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

            private void SLLessOrEqualOperation_q_LessOrEqual0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLLessOrEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessOrEqualOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
            private Object SLInvokeOperation_q_Direct_execute_(VirtualFrame $frame, int $bci, int $sp, Object arg0Value, Object[] arg1Value) {
                byte state_0 = bc[$bci + 5 + 0];
                assert (state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */;
                if (arg0Value instanceof SLFunction) {
                    SLFunction arg0Value_ = (SLFunction) arg0Value;
                    SLInvokeOperation_DirectData s0_ = ((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (s0_ != null) {
                        if (!Assumption.isValidAssumption((((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1])))) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            SLInvokeOperation_q_Direct_removeDirect__($frame, $bci, $sp, s0_);
                            return SLInvokeOperation_executeAndSpecialize_($frame, $bci, $sp, arg0Value_, arg1Value);
                        }
                        if ((arg0Value_.getCallTarget() == ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]))) {
                            return SLInvokeOperation.doDirect(arg0Value_, arg1Value, ((Assumption) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 1]), ((RootCallTarget) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 2]), ((DirectCallNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]));
                        }
                        s0_ = s0_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLInvokeOperation_executeAndSpecialize_($frame, $bci, $sp, arg0Value, arg1Value);
            }

            private void SLInvokeOperation_q_Direct_removeDirect__(VirtualFrame $frame, int $bci, int $sp, Object s0_) {
                Lock lock = getLock();
                lock.lock();
                try {
                    SLInvokeOperation_DirectData prev = null;
                    SLInvokeOperation_DirectData cur = ((SLInvokeOperation_DirectData) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
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

            private void SLFunctionLiteralOperation_q_Perform_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 3 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 1);
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
                SLFunctionLiteralOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_);
                return;
            }

            @ExplodeLoop
            private void SLWritePropertyOperation_q_WriteSLObject0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 5 + 0];
                Object $child0Value_;
                $child0Value_ = $frame.getObject($sp - 3);
                Object $child1Value_;
                $child1Value_ = $frame.getObject($sp - 2);
                Object $child2Value_;
                $child2Value_ = $frame.getObject($sp - 1);
                assert (state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLWritePropertyOperation_WriteSLObject0Data s2_ = ((SLWritePropertyOperation_WriteSLObject0Data) consts[LE_BYTES.getShort(bc, $bci + 5 + 1) + 0]);
                    while (s2_ != null) {
                        if ((((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]).accepts($child0Value__))) {
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, ((DynamicObjectLibrary) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 0]), ((SLToTruffleStringNode) children[LE_BYTES.getShort(bc, $bci + 5 + 3) + 1])));
                            return;
                        }
                        s2_ = s2_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLWritePropertyOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            private void SLLessThanOperation_q_LessThan0_execute_(VirtualFrame $frame, int $bci, int $sp) {
                byte state_0 = bc[$bci + 4 + 0];
                long $child0Value_;
                if ($frame.isLong($sp - 2)) {
                    $child0Value_ = $frame.getLong($sp - 2);
                } else if ($frame.isObject($sp - 2) && $frame.getObject($sp - 2) instanceof Long) {
                    $child0Value_ = (long) $frame.getObject($sp - 2);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = $frame.getValue($sp - 1);
                    SLLessThanOperation_executeAndSpecialize_($frame, $bci, $sp, $frame.getValue($sp - 2), $child1Value);
                    return;
                }
                long $child1Value_;
                if ($frame.isLong($sp - 1)) {
                    $child1Value_ = $frame.getLong($sp - 1);
                } else if ($frame.isObject($sp - 1) && $frame.getObject($sp - 1) instanceof Long) {
                    $child1Value_ = (long) $frame.getObject($sp - 1);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessThanOperation_executeAndSpecialize_($frame, $bci, $sp, $child0Value_, $frame.getValue($sp - 1));
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
                            case INSTR_LOAD_CONSTANT_OBJECT :
                            {
                                frame.setObject(sp, consts[LE_BYTES.getShort(bc, bci + 2)]);
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
                            case INSTR_LOAD_CONSTANT_BOOLEAN :
                            {
                                frame.setBoolean(sp, (boolean) consts[LE_BYTES.getShort(bc, bci + 2)]);
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
                            case INSTR_STORE_LOCAL :
                            {
                                assert frame.isObject(sp - 1);
                                frame.copy(sp - 1, LE_BYTES.getShort(bc, bci + 3) + VALUES_OFFSET);
                                frame.clear(--sp);
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_OBJECT :
                            {
                                frame.copy(LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET, sp);
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_LONG :
                            {
                                Object value = frame.getObject(LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET);
                                if (value instanceof Long) {
                                    frame.setLong(sp, (long) value);
                                } else {
                                    frame.setObject(sp, value);
                                }
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_LOAD_LOCAL_BOOLEAN :
                            {
                                Object value = frame.getObject(LE_BYTES.getShort(bc, bci + 2) + VALUES_OFFSET);
                                if (value instanceof Boolean) {
                                    frame.setBoolean(sp, (boolean) value);
                                } else {
                                    frame.setObject(sp, value);
                                }
                                sp++;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_RETURN :
                            {
                                returnValue = frame.getObject(sp - 1);
                                break loop;
                            }
                            case INSTR_C_SLADD_OPERATION :
                            {
                                this.SLAddOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLDIV_OPERATION :
                            {
                                this.SLDivOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLEQUAL_OPERATION :
                            {
                                this.SLEqualOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLLESS_OR_EQUAL_OPERATION :
                            {
                                this.SLLessOrEqualOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLLESS_THAN_OPERATION :
                            {
                                this.SLLessThanOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLLOGICAL_NOT_OPERATION :
                            {
                                this.SLLogicalNotOperation_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLMUL_OPERATION :
                            {
                                this.SLMulOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLREAD_PROPERTY_OPERATION :
                            {
                                this.SLReadPropertyOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLSUB_OPERATION :
                            {
                                this.SLSubOperation_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLWRITE_PROPERTY_OPERATION :
                            {
                                this.SLWritePropertyOperation_execute_(frame, bci, sp);
                                sp = sp - 3 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLUNBOX_OPERATION :
                            {
                                this.SLUnboxOperation_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLFUNCTION_LITERAL_OPERATION :
                            {
                                this.SLFunctionLiteralOperation_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLTO_BOOLEAN_OPERATION :
                            {
                                this.SLToBooleanOperation_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLEVAL_ROOT_OPERATION :
                            {
                                this.SLEvalRootOperation_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLINVOKE_OPERATION :
                            {
                                int numVariadics = LE_BYTES.getShort(bc, bci + 3);
                                Object input_0 = frame.getObject(sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = frame.getObject(sp - numVariadics + varIndex);
                                }
                                Object result = this.SLInvokeOperation_execute_(frame, bci, sp, input_0, input_1);
                                sp = sp - 1 - numVariadics + 1;
                                frame.setObject(sp - 1, result);
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLUNBOX_OPERATION_Q_FROM_LONG :
                            {
                                this.SLUnboxOperation_q_FromLong_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLADD_OPERATION_Q_ADD_LONG :
                            {
                                this.SLAddOperation_q_AddLong_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLREAD_PROPERTY_OPERATION_Q_READ_SLOBJECT0 :
                            {
                                this.SLReadPropertyOperation_q_ReadSLObject0_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLUNBOX_OPERATION_Q_FROM_BOOLEAN :
                            {
                                this.SLUnboxOperation_q_FromBoolean_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 10;
                                break;
                            }
                            case INSTR_C_SLTO_BOOLEAN_OPERATION_Q_BOOLEAN :
                            {
                                this.SLToBooleanOperation_q_Boolean_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 4;
                                break;
                            }
                            case INSTR_C_SLLESS_OR_EQUAL_OPERATION_Q_LESS_OR_EQUAL0 :
                            {
                                this.SLLessOrEqualOperation_q_LessOrEqual0_execute_(frame, bci, sp);
                                sp = sp - 2 + 1;
                                nextBci = bci + 5;
                                break;
                            }
                            case INSTR_C_SLINVOKE_OPERATION_Q_DIRECT :
                            {
                                int numVariadics = LE_BYTES.getShort(bc, bci + 3);
                                Object input_0 = frame.getObject(sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = frame.getObject(sp - numVariadics + varIndex);
                                }
                                Object result = this.SLInvokeOperation_q_Direct_execute_(frame, bci, sp, input_0, input_1);
                                sp = sp - 1 - numVariadics + 1;
                                frame.setObject(sp - 1, result);
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLFUNCTION_LITERAL_OPERATION_Q_PERFORM :
                            {
                                this.SLFunctionLiteralOperation_q_Perform_execute_(frame, bci, sp);
                                sp = sp - 1 + 1;
                                nextBci = bci + 6;
                                break;
                            }
                            case INSTR_C_SLWRITE_PROPERTY_OPERATION_Q_WRITE_SLOBJECT0 :
                            {
                                this.SLWritePropertyOperation_q_WriteSLObject0_execute_(frame, bci, sp);
                                sp = sp - 3 + 1;
                                nextBci = bci + 11;
                                break;
                            }
                            case INSTR_C_SLLESS_THAN_OPERATION_Q_LESS_THAN0 :
                            {
                                this.SLLessThanOperation_q_LessThan0_execute_(frame, bci, sp);
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
                        for (int handlerIndex = 0; handlerIndex < handlers.length; handlerIndex++) {
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
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        case INSTR_LOAD_ARGUMENT_LONG :
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        case INSTR_LOAD_LOCAL_OBJECT :
                        case INSTR_LOAD_LOCAL_LONG :
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        case INSTR_C_SLLOGICAL_NOT_OPERATION :
                        case INSTR_C_SLTO_BOOLEAN_OPERATION :
                        case INSTR_C_SLEVAL_ROOT_OPERATION :
                        case INSTR_C_SLTO_BOOLEAN_OPERATION_Q_BOOLEAN :
                        {
                            bci = bci + 4;
                            break;
                        }
                        case INSTR_BRANCH_FALSE :
                        {
                            bci = bci + 7;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            LE_BYTES.putShort(bc, bci, (short) INSTR_LOAD_CONSTANT_OBJECT);
                            bci = bci + 4;
                            break;
                        }
                        case INSTR_STORE_LOCAL :
                        case INSTR_C_SLLESS_OR_EQUAL_OPERATION :
                        case INSTR_C_SLLESS_THAN_OPERATION :
                        case INSTR_C_SLLESS_OR_EQUAL_OPERATION_Q_LESS_OR_EQUAL0 :
                        case INSTR_C_SLLESS_THAN_OPERATION_Q_LESS_THAN0 :
                        {
                            bci = bci + 5;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            bci = bci + 3;
                            break;
                        }
                        case INSTR_C_SLADD_OPERATION :
                        case INSTR_C_SLEQUAL_OPERATION :
                        case INSTR_C_SLWRITE_PROPERTY_OPERATION :
                        case INSTR_C_SLINVOKE_OPERATION :
                        case INSTR_C_SLADD_OPERATION_Q_ADD_LONG :
                        case INSTR_C_SLINVOKE_OPERATION_Q_DIRECT :
                        case INSTR_C_SLWRITE_PROPERTY_OPERATION_Q_WRITE_SLOBJECT0 :
                        {
                            bci = bci + 11;
                            break;
                        }
                        case INSTR_C_SLDIV_OPERATION :
                        case INSTR_C_SLMUL_OPERATION :
                        case INSTR_C_SLSUB_OPERATION :
                        case INSTR_C_SLFUNCTION_LITERAL_OPERATION :
                        case INSTR_C_SLFUNCTION_LITERAL_OPERATION_Q_PERFORM :
                        {
                            bci = bci + 6;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY_OPERATION :
                        case INSTR_C_SLUNBOX_OPERATION :
                        case INSTR_C_SLUNBOX_OPERATION_Q_FROM_LONG :
                        case INSTR_C_SLREAD_PROPERTY_OPERATION_Q_READ_SLOBJECT0 :
                        case INSTR_C_SLUNBOX_OPERATION_Q_FROM_BOOLEAN :
                        {
                            bci = bci + 10;
                            break;
                        }
                    }
                }
            }

            @Override
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
                        case INSTR_STORE_LOCAL :
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
                            sb.append("store.local                      ");
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
                        case INSTR_C_SLADD_OPERATION :
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
                            sb.append("c.SLAddOperation                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLDIV_OPERATION :
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
                            sb.append("c.SLDivOperation                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLEQUAL_OPERATION :
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
                            sb.append("c.SLEqualOperation               ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLLESS_OR_EQUAL_OPERATION :
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
                            sb.append("c.SLLessOrEqualOperation         ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLLESS_THAN_OPERATION :
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
                            sb.append("c.SLLessThanOperation            ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLLOGICAL_NOT_OPERATION :
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
                            sb.append("c.SLLogicalNotOperation          ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLMUL_OPERATION :
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
                            sb.append("c.SLMulOperation                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY_OPERATION :
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
                            sb.append("c.SLReadPropertyOperation        ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLSUB_OPERATION :
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
                            sb.append("c.SLSubOperation                 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLWRITE_PROPERTY_OPERATION :
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
                            sb.append("c.SLWritePropertyOperation       ");
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
                        case INSTR_C_SLUNBOX_OPERATION :
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
                            sb.append("c.SLUnboxOperation               ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLFUNCTION_LITERAL_OPERATION :
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
                            sb.append("c.SLFunctionLiteralOperation     ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLTO_BOOLEAN_OPERATION :
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
                            sb.append("c.SLToBooleanOperation           ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLEVAL_ROOT_OPERATION :
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
                            sb.append("c.SLEvalRootOperation            ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLINVOKE_OPERATION :
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
                            sb.append("c.SLInvokeOperation              ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("**%d", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLUNBOX_OPERATION_Q_FROM_LONG :
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
                            sb.append("c.SLUnboxOperation.q.FromLong    ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLADD_OPERATION_Q_ADD_LONG :
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
                            sb.append("c.SLAddOperation.q.AddLong       ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLREAD_PROPERTY_OPERATION_Q_READ_SLOBJECT0 :
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
                            sb.append("c.SLReadPropertyOperation.q.ReadSLObject0 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLUNBOX_OPERATION_Q_FROM_BOOLEAN :
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
                            sb.append("c.SLUnboxOperation.q.FromBoolean ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 10;
                            break;
                        }
                        case INSTR_C_SLTO_BOOLEAN_OPERATION_Q_BOOLEAN :
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
                            sb.append("c.SLToBooleanOperation.q.Boolean ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 4;
                            break;
                        }
                        case INSTR_C_SLLESS_OR_EQUAL_OPERATION_Q_LESS_OR_EQUAL0 :
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
                            sb.append("c.SLLessOrEqualOperation.q.LessOrEqual0 ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("pop[-%d]", bc[bci + 3]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 5;
                            break;
                        }
                        case INSTR_C_SLINVOKE_OPERATION_Q_DIRECT :
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
                            sb.append("c.SLInvokeOperation.q.Direct     ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(", ");
                            sb.append(String.format("**%d", LE_BYTES.getShort(bc, bci + 3)));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 11;
                            break;
                        }
                        case INSTR_C_SLFUNCTION_LITERAL_OPERATION_Q_PERFORM :
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
                            sb.append("c.SLFunctionLiteralOperation.q.Perform ");
                            sb.append(String.format("pop[-%d]", bc[bci + 2]));
                            sb.append(" -> ");
                            sb.append("x");
                            bci += 6;
                            break;
                        }
                        case INSTR_C_SLWRITE_PROPERTY_OPERATION_Q_WRITE_SLOBJECT0 :
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
                            sb.append("c.SLWritePropertyOperation.q.WriteSLObject0 ");
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
                        case INSTR_C_SLLESS_THAN_OPERATION_Q_LESS_THAN0 :
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
                            sb.append("c.SLLessThanOperation.q.LessThan0 ");
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
                if (sourceInfo != null) {
                    sb.append("Source info:\n");
                    for (int i = 0; i < sourceInfo[0].length; i++) {
                        sb.append(String.format("  bci=%04x, offset=%d, length=%d\n", sourceInfo[0][i], sourceInfo[1][i], sourceInfo[2][i]));
                    }
                }
                return sb.toString();
            }

            @Override
            public SourceSection getSourceSection() {
                if (sourceInfo == null) {
                    OperationsNode reparsed = SLOperationsBuilderImpl.reparse(getRootNode().getLanguage(SLLanguage.class), (SLSource) parseContext, buildOrder);
                    copyReparsedInfo(reparsed);
                }
                return this.getSourceSectionImpl();
            }

            @Override
            protected SourceSection getSourceSectionAtBci(int bci) {
                if (sourceInfo == null) {
                    OperationsNode reparsed = SLOperationsBuilderImpl.reparse(getRootNode().getLanguage(SLLanguage.class), (SLSource) parseContext, buildOrder);
                    copyReparsedInfo(reparsed);
                }
                return this.getSourceSectionAtBciImpl(bci);
            }

            private static boolean SLAddOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static boolean SLDivOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessOrEqualOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessThanOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLogicalNotOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLMulOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLSubOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLToBooleanOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLEvalRootOperation_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doExecute(VirtualFrame, Map<TruffleString, RootCallTarget>, Node) */ && $child0Value instanceof Map<?, ?>) {
                    return false;
                }
                return true;
            }

            private static boolean SLAddOperation_q_AddLong_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static boolean SLToBooleanOperation_q_Boolean_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, byte state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessOrEqualOperation_q_LessOrEqual0_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static boolean SLLessThanOperation_q_LessThan0_fallbackGuard__(VirtualFrame $frame, int $bci, int $sp, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void doSetResultBoxed(byte[] bc, int startBci, int bciOffset, int targetType) {
                if (bciOffset != 0) {
                    setResultBoxedImpl(bc, startBci - bciOffset, targetType, BOXING_DESCRIPTORS[targetType]);
                }
            }

            private static final class SLAddOperation_Add1Data extends Node {

                @Child SLToTruffleStringNode toTruffleStringNodeLeft_;
                @Child SLToTruffleStringNode toTruffleStringNodeRight_;
                @Child ConcatNode concatNode_;

                SLAddOperation_Add1Data() {
                }

                @Override
                public NodeCost getCost() {
                    return NodeCost.NONE;
                }

                <T extends Node> T insertAccessor(T node) {
                    return super.insert(node);
                }

            }
            private static final class SLEqualOperation_Generic0Data extends Node {

                @Child SLEqualOperation_Generic0Data next_;
                @Child InteropLibrary leftInterop_;
                @Child InteropLibrary rightInterop_;

                SLEqualOperation_Generic0Data(SLEqualOperation_Generic0Data next_) {
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
            private static final class SLReadPropertyOperation_ReadArray0Data extends Node {

                @Child SLReadPropertyOperation_ReadArray0Data next_;
                @Child InteropLibrary arrays_;
                @Child InteropLibrary numbers_;

                SLReadPropertyOperation_ReadArray0Data(SLReadPropertyOperation_ReadArray0Data next_) {
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
            private static final class SLReadPropertyOperation_ReadSLObject0Data extends Node {

                @Child SLReadPropertyOperation_ReadSLObject0Data next_;
                @Child DynamicObjectLibrary objectLibrary_;
                @Child SLToTruffleStringNode toTruffleStringNode_;

                SLReadPropertyOperation_ReadSLObject0Data(SLReadPropertyOperation_ReadSLObject0Data next_) {
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
            private static final class SLReadPropertyOperation_ReadObject0Data extends Node {

                @Child SLReadPropertyOperation_ReadObject0Data next_;
                @Child InteropLibrary objects_;
                @Child SLToMemberNode asMember_;

                SLReadPropertyOperation_ReadObject0Data(SLReadPropertyOperation_ReadObject0Data next_) {
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
            private static final class SLWritePropertyOperation_WriteArray0Data extends Node {

                @Child SLWritePropertyOperation_WriteArray0Data next_;
                @Child Node node_;
                @CompilationFinal int bci_;
                @Child InteropLibrary arrays_;
                @Child InteropLibrary numbers_;

                SLWritePropertyOperation_WriteArray0Data(SLWritePropertyOperation_WriteArray0Data next_) {
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
            private static final class SLWritePropertyOperation_WriteSLObject0Data extends Node {

                @Child SLWritePropertyOperation_WriteSLObject0Data next_;
                @Child DynamicObjectLibrary objectLibrary_;
                @Child SLToTruffleStringNode toTruffleStringNode_;

                SLWritePropertyOperation_WriteSLObject0Data(SLWritePropertyOperation_WriteSLObject0Data next_) {
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
            private static final class SLWritePropertyOperation_WriteObject0Data extends Node {

                @Child SLWritePropertyOperation_WriteObject0Data next_;
                @Child InteropLibrary objectLibrary_;
                @Child SLToMemberNode asMember_;

                SLWritePropertyOperation_WriteObject0Data(SLWritePropertyOperation_WriteObject0Data next_) {
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
            private static final class SLUnboxOperation_FromForeign0Data extends Node {

                @Child SLUnboxOperation_FromForeign0Data next_;
                @Child InteropLibrary interop_;

                SLUnboxOperation_FromForeign0Data(SLUnboxOperation_FromForeign0Data next_) {
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
            private static final class SLInvokeOperation_DirectData extends Node {

                @Child SLInvokeOperation_DirectData next_;
                @CompilationFinal Assumption callTargetStable_;
                @CompilationFinal RootCallTarget cachedTarget_;
                @Child DirectCallNode callNode_;

                SLInvokeOperation_DirectData(SLInvokeOperation_DirectData next_) {
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
