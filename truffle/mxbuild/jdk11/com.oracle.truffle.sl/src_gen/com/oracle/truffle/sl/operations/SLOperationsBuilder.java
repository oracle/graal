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
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
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
import com.oracle.truffle.api.operation.OperationNode;
import com.oracle.truffle.api.operation.OperationNodes;
import com.oracle.truffle.api.operation.serialization.OperationDeserializationCallback;
import com.oracle.truffle.api.operation.serialization.OperationSerializationCallback;
import com.oracle.truffle.api.source.Source;
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

@GeneratedBy(SLOperations.class)
@SuppressWarnings({"serial", "unused", "cast", "hiding", "unchecked", "rawtypes", "static-method"})
public abstract class SLOperationsBuilder extends OperationBuilder {

    protected SLOperationsBuilder() {
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

    public abstract void beginTryCatch(OperationLocal arg0);

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

    public abstract void beginSource(Source arg0);

    public abstract void endSource();

    public abstract void beginSourceSection(int arg0, int arg1);

    public abstract void endSourceSection();

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

    public abstract void beginSLInvoke();

    public abstract void endSLInvoke();

    public abstract void beginSLAnd();

    public abstract void endSLAnd();

    public abstract void beginSLOr();

    public abstract void endSLOr();

    public abstract OperationLocal createLocal();

    public abstract OperationLabel createLabel();

    public abstract OperationNode publish();

    public abstract void setMethodName(TruffleString value);

    private static short unsafeFromBytecode(short[] bc, int index) {
        return bc[index];
    }

    public static OperationNodes create(OperationConfig config, Consumer<SLOperationsBuilder> generator) {
        OperationNodesImpl nodes = new OperationNodesImpl(generator);
        BuilderImpl builder = new BuilderImpl(nodes, false, config);
        generator.accept(builder);
        builder.finish();
        return nodes;
    }

    public static OperationNodes deserialize(OperationConfig config, DataInputStream input, OperationDeserializationCallback callback) throws IOException {
        try {
            return create(config, b -> BuilderImpl.deserializeParser(input, callback, b));
        } catch (WrappedIOException ex) {
            throw (IOException) ex.getCause();
        }
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

        void setSources(Source[] sources) {
            this.sources = sources;
        }

        void setNodes(OperationNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        public void serialize(DataOutputStream buffer, OperationSerializationCallback callback) throws IOException {
            BuilderImpl builder = new BuilderImpl(null, false, OperationConfig.COMPLETE);
            builder.isSerializing = true;
            builder.serBuffer = buffer;
            builder.serCallback = callback;
            ((Consumer) parse).accept(builder);
            buffer.writeShort((short) -5);
        }

    }
    @GeneratedBy(SLOperations.class)
    private static class BuilderImpl extends SLOperationsBuilder {

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
        private static final int OP_SOURCE = 16;
        private static final int OP_SOURCE_SECTION = 17;
        private static final int OP_TAG = 18;
        private static final int OP_SL_ADD = 19;
        private static final int OP_SL_DIV = 20;
        private static final int OP_SL_EQUAL = 21;
        private static final int OP_SL_LESS_OR_EQUAL = 22;
        private static final int OP_SL_LESS_THAN = 23;
        private static final int OP_SL_LOGICAL_NOT = 24;
        private static final int OP_SL_MUL = 25;
        private static final int OP_SL_READ_PROPERTY = 26;
        private static final int OP_SL_SUB = 27;
        private static final int OP_SL_WRITE_PROPERTY = 28;
        private static final int OP_SL_UNBOX = 29;
        private static final int OP_SL_FUNCTION_LITERAL = 30;
        private static final int OP_SL_TO_BOOLEAN = 31;
        private static final int OP_SL_INVOKE = 32;
        private static final int OP_SL_AND = 33;
        private static final int OP_SL_OR = 34;
        static final int INSTR_POP = 1;
        static final int POP_LENGTH = 1;
        static final int INSTR_BRANCH = 2;
        static final int BRANCH_BRANCH_TARGET_OFFSET = 1;
        static final int BRANCH_LENGTH = 2;
        static final int INSTR_BRANCH_FALSE = 3;
        static final int BRANCH_FALSE_BRANCH_TARGET_OFFSET = 1;
        static final int BRANCH_FALSE_BRANCH_PROFILE_OFFSET = 2;
        static final int BRANCH_FALSE_LENGTH = 3;
        static final int INSTR_THROW = 4;
        static final int THROW_LOCALS_OFFSET = 1;
        static final int THROW_LENGTH = 2;
        static final int INSTR_LOAD_CONSTANT_OBJECT = 5;
        static final int LOAD_CONSTANT_OBJECT_CONSTANT_OFFSET = 1;
        static final int LOAD_CONSTANT_OBJECT_LENGTH = 2;
        static final int INSTR_LOAD_CONSTANT_LONG = 6;
        static final int LOAD_CONSTANT_LONG_CONSTANT_OFFSET = 1;
        static final int LOAD_CONSTANT_LONG_LENGTH = 2;
        static final int INSTR_LOAD_CONSTANT_BOOLEAN = 7;
        static final int LOAD_CONSTANT_BOOLEAN_CONSTANT_OFFSET = 1;
        static final int LOAD_CONSTANT_BOOLEAN_LENGTH = 2;
        static final int INSTR_LOAD_ARGUMENT_OBJECT = 8;
        static final int LOAD_ARGUMENT_OBJECT_ARGUMENT_OFFSET = 1;
        static final int LOAD_ARGUMENT_OBJECT_LENGTH = 2;
        static final int INSTR_LOAD_ARGUMENT_LONG = 9;
        static final int LOAD_ARGUMENT_LONG_ARGUMENT_OFFSET = 1;
        static final int LOAD_ARGUMENT_LONG_LENGTH = 2;
        static final int INSTR_LOAD_ARGUMENT_BOOLEAN = 10;
        static final int LOAD_ARGUMENT_BOOLEAN_ARGUMENT_OFFSET = 1;
        static final int LOAD_ARGUMENT_BOOLEAN_LENGTH = 2;
        static final int INSTR_STORE_LOCAL_OBJECT = 11;
        static final int STORE_LOCAL_OBJECT_LOCALS_OFFSET = 1;
        static final int STORE_LOCAL_OBJECT_POP_INDEXED_OFFSET = 2;
        static final int STORE_LOCAL_OBJECT_LENGTH = 3;
        static final int INSTR_STORE_LOCAL_LONG = 12;
        static final int STORE_LOCAL_LONG_LOCALS_OFFSET = 1;
        static final int STORE_LOCAL_LONG_POP_INDEXED_OFFSET = 2;
        static final int STORE_LOCAL_LONG_LENGTH = 3;
        static final int INSTR_STORE_LOCAL_BOOLEAN = 13;
        static final int STORE_LOCAL_BOOLEAN_LOCALS_OFFSET = 1;
        static final int STORE_LOCAL_BOOLEAN_POP_INDEXED_OFFSET = 2;
        static final int STORE_LOCAL_BOOLEAN_LENGTH = 3;
        static final int INSTR_STORE_LOCAL_UNINIT = 14;
        static final int STORE_LOCAL_UNINIT_LOCALS_OFFSET = 1;
        static final int STORE_LOCAL_UNINIT_POP_INDEXED_OFFSET = 2;
        static final int STORE_LOCAL_UNINIT_LENGTH = 3;
        static final int INSTR_LOAD_LOCAL_OBJECT = 15;
        static final int LOAD_LOCAL_OBJECT_LOCALS_OFFSET = 1;
        static final int LOAD_LOCAL_OBJECT_LENGTH = 2;
        static final int INSTR_LOAD_LOCAL_LONG = 16;
        static final int LOAD_LOCAL_LONG_LOCALS_OFFSET = 1;
        static final int LOAD_LOCAL_LONG_LENGTH = 2;
        static final int INSTR_LOAD_LOCAL_BOOLEAN = 17;
        static final int LOAD_LOCAL_BOOLEAN_LOCALS_OFFSET = 1;
        static final int LOAD_LOCAL_BOOLEAN_LENGTH = 2;
        static final int INSTR_LOAD_LOCAL_UNINIT = 18;
        static final int LOAD_LOCAL_UNINIT_LOCALS_OFFSET = 1;
        static final int LOAD_LOCAL_UNINIT_LENGTH = 2;
        static final int INSTR_RETURN = 19;
        static final int RETURN_LENGTH = 1;
        static final int INSTR_INSTRUMENT_ENTER = 20;
        static final int INSTRUMENT_ENTER_LENGTH = 2;
        static final int INSTR_INSTRUMENT_EXIT_VOID = 21;
        static final int INSTRUMENT_EXIT_VOID_LENGTH = 2;
        static final int INSTR_INSTRUMENT_EXIT = 22;
        static final int INSTRUMENT_EXIT_LENGTH = 2;
        static final int INSTR_INSTRUMENT_LEAVE = 23;
        static final int INSTRUMENT_LEAVE_BRANCH_TARGET_OFFSET = 1;
        static final int INSTRUMENT_LEAVE_LENGTH = 4;
        static final int INSTR_C_SL_ADD = 24;
        static final int C_SL_ADD_CONSTANT_OFFSET = 1;
        static final int C_SL_ADD_CHILDREN_OFFSET = 2;
        static final int C_SL_ADD_POP_INDEXED_OFFSET = 3;
        static final int C_SL_ADD_STATE_BITS_OFFSET = 4;
        static final int C_SL_ADD_LENGTH = 8;
        static final int INSTR_C_SL_DIV = 25;
        static final int C_SL_DIV_CONSTANT_OFFSET = 1;
        static final int C_SL_DIV_CHILDREN_OFFSET = 2;
        static final int C_SL_DIV_POP_INDEXED_OFFSET = 3;
        static final int C_SL_DIV_STATE_BITS_OFFSET = 4;
        static final int C_SL_DIV_LENGTH = 8;
        static final int INSTR_C_SL_EQUAL = 26;
        static final int C_SL_EQUAL_CHILDREN_OFFSET = 1;
        static final int C_SL_EQUAL_POP_INDEXED_OFFSET = 2;
        static final int C_SL_EQUAL_STATE_BITS_OFFSET = 3;
        static final int C_SL_EQUAL_LENGTH = 7;
        static final int INSTR_C_SL_LESS_OR_EQUAL = 27;
        static final int C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET = 1;
        static final int C_SL_LESS_OR_EQUAL_CHILDREN_OFFSET = 2;
        static final int C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET = 3;
        static final int C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET = 4;
        static final int C_SL_LESS_OR_EQUAL_LENGTH = 6;
        static final int INSTR_C_SL_LESS_THAN = 28;
        static final int C_SL_LESS_THAN_CONSTANT_OFFSET = 1;
        static final int C_SL_LESS_THAN_CHILDREN_OFFSET = 2;
        static final int C_SL_LESS_THAN_POP_INDEXED_OFFSET = 3;
        static final int C_SL_LESS_THAN_STATE_BITS_OFFSET = 4;
        static final int C_SL_LESS_THAN_LENGTH = 6;
        static final int INSTR_C_SL_LOGICAL_NOT = 29;
        static final int C_SL_LOGICAL_NOT_CONSTANT_OFFSET = 1;
        static final int C_SL_LOGICAL_NOT_CHILDREN_OFFSET = 2;
        static final int C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET = 3;
        static final int C_SL_LOGICAL_NOT_STATE_BITS_OFFSET = 4;
        static final int C_SL_LOGICAL_NOT_LENGTH = 6;
        static final int INSTR_C_SL_MUL = 30;
        static final int C_SL_MUL_CONSTANT_OFFSET = 1;
        static final int C_SL_MUL_CHILDREN_OFFSET = 2;
        static final int C_SL_MUL_POP_INDEXED_OFFSET = 3;
        static final int C_SL_MUL_STATE_BITS_OFFSET = 4;
        static final int C_SL_MUL_LENGTH = 8;
        static final int INSTR_C_SL_READ_PROPERTY = 31;
        static final int C_SL_READ_PROPERTY_CONSTANT_OFFSET = 1;
        static final int C_SL_READ_PROPERTY_CHILDREN_OFFSET = 2;
        static final int C_SL_READ_PROPERTY_POP_INDEXED_OFFSET = 3;
        static final int C_SL_READ_PROPERTY_STATE_BITS_OFFSET = 4;
        static final int C_SL_READ_PROPERTY_LENGTH = 8;
        static final int INSTR_C_SL_SUB = 32;
        static final int C_SL_SUB_CONSTANT_OFFSET = 1;
        static final int C_SL_SUB_CHILDREN_OFFSET = 2;
        static final int C_SL_SUB_POP_INDEXED_OFFSET = 3;
        static final int C_SL_SUB_STATE_BITS_OFFSET = 4;
        static final int C_SL_SUB_LENGTH = 8;
        static final int INSTR_C_SL_WRITE_PROPERTY = 33;
        static final int C_SL_WRITE_PROPERTY_CONSTANT_OFFSET = 1;
        static final int C_SL_WRITE_PROPERTY_CHILDREN_OFFSET = 2;
        static final int C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET = 3;
        static final int C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET = 5;
        static final int C_SL_WRITE_PROPERTY_LENGTH = 9;
        static final int INSTR_C_SL_UNBOX = 34;
        static final int C_SL_UNBOX_CHILDREN_OFFSET = 1;
        static final int C_SL_UNBOX_POP_INDEXED_OFFSET = 2;
        static final int C_SL_UNBOX_STATE_BITS_OFFSET = 3;
        static final int C_SL_UNBOX_LENGTH = 7;
        static final int INSTR_C_SL_FUNCTION_LITERAL = 35;
        static final int C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET = 1;
        static final int C_SL_FUNCTION_LITERAL_CHILDREN_OFFSET = 2;
        static final int C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET = 3;
        static final int C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET = 4;
        static final int C_SL_FUNCTION_LITERAL_LENGTH = 6;
        static final int INSTR_C_SL_TO_BOOLEAN = 36;
        static final int C_SL_TO_BOOLEAN_CONSTANT_OFFSET = 1;
        static final int C_SL_TO_BOOLEAN_CHILDREN_OFFSET = 2;
        static final int C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET = 3;
        static final int C_SL_TO_BOOLEAN_STATE_BITS_OFFSET = 4;
        static final int C_SL_TO_BOOLEAN_LENGTH = 6;
        static final int INSTR_C_SL_INVOKE = 37;
        static final int C_SL_INVOKE_CONSTANT_OFFSET = 1;
        static final int C_SL_INVOKE_CHILDREN_OFFSET = 2;
        static final int C_SL_INVOKE_VARIADIC_OFFSET = 3;
        static final int C_SL_INVOKE_STATE_BITS_OFFSET = 4;
        static final int C_SL_INVOKE_LENGTH = 8;
        static final int INSTR_SC_SL_AND = 38;
        static final int SC_SL_AND_CONSTANT_OFFSET = 1;
        static final int SC_SL_AND_CHILDREN_OFFSET = 2;
        static final int SC_SL_AND_POP_INDEXED_OFFSET = 3;
        static final int SC_SL_AND_BRANCH_TARGET_OFFSET = 4;
        static final int SC_SL_AND_STATE_BITS_OFFSET = 5;
        static final int SC_SL_AND_LENGTH = 7;
        static final int INSTR_SC_SL_OR = 39;
        static final int SC_SL_OR_CONSTANT_OFFSET = 1;
        static final int SC_SL_OR_CHILDREN_OFFSET = 2;
        static final int SC_SL_OR_POP_INDEXED_OFFSET = 3;
        static final int SC_SL_OR_BRANCH_TARGET_OFFSET = 4;
        static final int SC_SL_OR_STATE_BITS_OFFSET = 5;
        static final int SC_SL_OR_LENGTH = 7;
        static final int INSTR_C_SL_UNBOX_Q_FROM_LONG = 40;
        static final int C_SL_UNBOX_Q_FROM_LONG_CHILDREN_OFFSET = 1;
        static final int C_SL_UNBOX_Q_FROM_LONG_POP_INDEXED_OFFSET = 2;
        static final int C_SL_UNBOX_Q_FROM_LONG_STATE_BITS_OFFSET = 3;
        static final int C_SL_UNBOX_Q_FROM_LONG_LENGTH = 7;
        static final int INSTR_C_SL_ADD_Q_ADD_LONG = 41;
        static final int C_SL_ADD_Q_ADD_LONG_CONSTANT_OFFSET = 1;
        static final int C_SL_ADD_Q_ADD_LONG_CHILDREN_OFFSET = 2;
        static final int C_SL_ADD_Q_ADD_LONG_POP_INDEXED_OFFSET = 3;
        static final int C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET = 4;
        static final int C_SL_ADD_Q_ADD_LONG_LENGTH = 8;
        static final int INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 = 42;
        static final int C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET = 1;
        static final int C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CHILDREN_OFFSET = 2;
        static final int C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_POP_INDEXED_OFFSET = 3;
        static final int C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_STATE_BITS_OFFSET = 4;
        static final int C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH = 8;
        static final int INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN = 43;
        static final int C_SL_UNBOX_Q_FROM_BOOLEAN_CHILDREN_OFFSET = 1;
        static final int C_SL_UNBOX_Q_FROM_BOOLEAN_POP_INDEXED_OFFSET = 2;
        static final int C_SL_UNBOX_Q_FROM_BOOLEAN_STATE_BITS_OFFSET = 3;
        static final int C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH = 7;
        static final int INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN = 44;
        static final int C_SL_TO_BOOLEAN_Q_BOOLEAN_CONSTANT_OFFSET = 1;
        static final int C_SL_TO_BOOLEAN_Q_BOOLEAN_CHILDREN_OFFSET = 2;
        static final int C_SL_TO_BOOLEAN_Q_BOOLEAN_POP_INDEXED_OFFSET = 3;
        static final int C_SL_TO_BOOLEAN_Q_BOOLEAN_STATE_BITS_OFFSET = 4;
        static final int C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH = 6;
        static final int INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 = 45;
        static final int C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_CONSTANT_OFFSET = 1;
        static final int C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_CHILDREN_OFFSET = 2;
        static final int C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_POP_INDEXED_OFFSET = 3;
        static final int C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_STATE_BITS_OFFSET = 4;
        static final int C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH = 6;
        static final int INSTR_C_SL_INVOKE_Q_DIRECT = 46;
        static final int C_SL_INVOKE_Q_DIRECT_CONSTANT_OFFSET = 1;
        static final int C_SL_INVOKE_Q_DIRECT_CHILDREN_OFFSET = 2;
        static final int C_SL_INVOKE_Q_DIRECT_VARIADIC_OFFSET = 3;
        static final int C_SL_INVOKE_Q_DIRECT_STATE_BITS_OFFSET = 4;
        static final int C_SL_INVOKE_Q_DIRECT_LENGTH = 8;
        static final int INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM = 47;
        static final int C_SL_FUNCTION_LITERAL_Q_PERFORM_CONSTANT_OFFSET = 1;
        static final int C_SL_FUNCTION_LITERAL_Q_PERFORM_CHILDREN_OFFSET = 2;
        static final int C_SL_FUNCTION_LITERAL_Q_PERFORM_POP_INDEXED_OFFSET = 3;
        static final int C_SL_FUNCTION_LITERAL_Q_PERFORM_STATE_BITS_OFFSET = 4;
        static final int C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH = 6;
        static final int INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 = 48;
        static final int C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CONSTANT_OFFSET = 1;
        static final int C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CHILDREN_OFFSET = 2;
        static final int C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET = 3;
        static final int C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_STATE_BITS_OFFSET = 5;
        static final int C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH = 9;
        static final int INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 = 49;
        static final int C_SL_LESS_THAN_Q_LESS_THAN0_CONSTANT_OFFSET = 1;
        static final int C_SL_LESS_THAN_Q_LESS_THAN0_CHILDREN_OFFSET = 2;
        static final int C_SL_LESS_THAN_Q_LESS_THAN0_POP_INDEXED_OFFSET = 3;
        static final int C_SL_LESS_THAN_Q_LESS_THAN0_STATE_BITS_OFFSET = 4;
        static final int C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH = 6;
        private static final short[][] BOXING_DESCRIPTORS = {
        // OBJECT
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_CONSTANT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, INSTR_LOAD_ARGUMENT_OBJECT, 0, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_OBJECT, 0, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_OBJECT, 0, 0, 0, 0, 0, (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_DIV_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_EQUAL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_MUL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_SUB_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), 0, 0, (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1)},
        // LONG
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, 0, 0, INSTR_STORE_LOCAL_OBJECT, INSTR_STORE_LOCAL_LONG, 0, 0, INSTR_LOAD_LOCAL_OBJECT, INSTR_LOAD_LOCAL_LONG, 0, 0, 0, 0, 0, (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_DIV_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_EQUAL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_MUL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_SUB_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), 0, 0, (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1)},
        // INT
        null,
        // DOUBLE
        null,
        // FLOAT
        null,
        // BOOLEAN
        {-1, 0, 0, 0, 0, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_CONSTANT_LONG, INSTR_LOAD_CONSTANT_BOOLEAN, INSTR_LOAD_ARGUMENT_BOOLEAN, INSTR_LOAD_ARGUMENT_LONG, INSTR_LOAD_ARGUMENT_BOOLEAN, 0, INSTR_STORE_LOCAL_OBJECT, 0, INSTR_STORE_LOCAL_BOOLEAN, 0, INSTR_LOAD_LOCAL_OBJECT, 0, INSTR_LOAD_LOCAL_BOOLEAN, 0, 0, 0, 0, 0, (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_DIV_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_EQUAL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_MUL_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_SUB_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), 0, 0, (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_ADD_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_UNBOX_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_INVOKE_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 1) << 8) | 1), (short) (0x8000 | ((C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 2) << 8) | 1), (short) (0x8000 | ((C_SL_LESS_THAN_STATE_BITS_OFFSET + 1) << 8) | 1)},
        // BYTE
        null};

        private final OperationNodesImpl nodes;
        private final boolean isReparse;
        private final boolean withSource;
        private final boolean withInstrumentation;
        private final SourceInfoBuilder sourceBuilder;
        private short[] bc = new short[65535];
        private int bci;
        private int curStack;
        private int maxStack;
        private int numLocals;
        private int numLabels;
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
        private DataOutputStream serBuffer;
        private OperationSerializationCallback serCallback;
        private int[] stackSourceBci = new int[1024];
        com.oracle.truffle.api.operation.serialization.OperationSerializationCallback.Context SER_CONTEXT = new com.oracle.truffle.api.operation.serialization.OperationSerializationCallback.Context() {
            @Override
            public void serializeOperationNode(DataOutputStream buffer, OperationNode node) throws IOException {
                buffer.writeShort((short) ((OperationSerNodeImpl) node).buildOrder);
            }
        }
        ;
        private final ArrayList<OperationNodeImpl> builtNodes;
        int lastChildPush;
        private TruffleString metadata_MethodName;

        private BuilderImpl(OperationNodesImpl nodes, boolean isReparse, OperationConfig config) {
            this.nodes = nodes;
            this.isReparse = isReparse;
            builtNodes = new ArrayList<>();
            if (isReparse) {
                builtNodes.addAll((java.util.Collection) nodes.getNodes());
            }
            this.withSource = config.isWithSource();
            this.withInstrumentation = config.isWithInstrumentation();
            this.sourceBuilder = withSource ? new SourceInfoBuilder() : null;
            reset();
        }

        private void finish() {
            if (withSource) {
                nodes.setSources(sourceBuilder.buildSource());
            }
            if (!isReparse) {
                nodes.setNodes(builtNodes.toArray(new OperationNode[0]));
            }
        }

        private void reset() {
            bci = 0;
            curStack = 0;
            maxStack = 0;
            numLocals = 0;
            constPool.clear();
            operationData = new BuilderOperationData(null, OP_BLOCK, 0, 0, false, 0);
            labelFills.clear();
            numChildNodes = 0;
            numConditionProfiles = 0;
            exceptionHandlers.clear();
            metadata_MethodName = null;
        }

        private int[] doBeforeEmitInstruction(int numPops, boolean pushValue) {
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
                stackSourceBci[curStack] = bci;
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

        @Override
        public OperationLocal createLocal() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -3);
                    return new OperationLocalImpl(null, numLocals++);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
            }
            return new OperationLocalImpl(operationData, numLocals++);
        }

        OperationLocalImpl createParentLocal() {
            return new OperationLocalImpl(operationData.parent, numLocals++);
        }

        @Override
        public OperationLabel createLabel() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -2);
                    return new OperationSerLabelImpl(numLabels++);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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

        @Override
        public OperationNode publish() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -1);
                    OperationNode result = new OperationSerNodeImpl(null, buildIndex++);
                    numLocals = 0;
                    numLabels = 0;
                    return result;
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
            }
            if (operationData.depth != 0) {
                throw new UnsupportedOperationException("Not all operations closed");
            }
            OperationNodeImpl result;
            if (!isReparse) {
                result = new OperationNodeImpl(nodes);
                labelPass(null);
                result._bc = Arrays.copyOf(bc, bci);
                result._consts = constPool.toArray();
                result._children = new Node[numChildNodes];
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
            reset();
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
        void doBeforeChild() {
            int childIndex = operationData.numChildren;
            switch (operationData.operationId) {
                case OP_BLOCK :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
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
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
                    }
                    break;
                }
                case OP_SOURCE_SECTION :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
                    }
                    break;
                }
                case OP_TAG :
                {
                    if (childIndex != 0) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
                    }
                    break;
                }
                case OP_SL_AND :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_SC_SL_AND);
                        int constantsStart = constPool.size();
                        bc[bci + 1] = (short) constantsStart;
                        constPool.add(null);
                        bc[bci + 2] = (short) numChildNodes;
                        numChildNodes += 1;
                        bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
                        labelFills.add(new LabelFill(bci + 4 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        bc[bci + 5 + 0] = 0;
                        bc[bci + 5 + 1] = 0;
                        bci = bci + 7;
                    }
                    break;
                }
                case OP_SL_OR :
                {
                    if (childIndex > 0) {
                        int[] predecessorBcis = doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_SC_SL_OR);
                        int constantsStart = constPool.size();
                        bc[bci + 1] = (short) constantsStart;
                        constPool.add(null);
                        bc[bci + 2] = (short) numChildNodes;
                        numChildNodes += 1;
                        bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
                        labelFills.add(new LabelFill(bci + 4 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        bc[bci + 5 + 0] = 0;
                        bc[bci + 5 + 1] = 0;
                        bci = bci + 7;
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
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[0] = endLabel;
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_BRANCH_FALSE);
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        bci = bci + 3;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
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
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_BRANCH_FALSE);
                        labelFills.add(new LabelFill(bci + 1 + 0, elseLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        bci = bci + 3;
                    } else if (childIndex == 1) {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, endLabel);
                        doBeforeEmitInstruction(0, false);
                        bc[bci] = (short) (INSTR_BRANCH);
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bci = bci + 2;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
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
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_BRANCH_FALSE);
                        labelFills.add(new LabelFill(bci + 1 + 0, elseLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        bci = bci + 3;
                    } else if (childIndex == 1) {
                        assert lastChildPush == 1;
                        OperationLabelImpl endLabel = (OperationLabelImpl) createLabel();
                        operationData.aux[1] = endLabel;
                        calculateLeaves(operationData, endLabel);
                        doBeforeEmitInstruction(0, false);
                        bc[bci] = (short) (INSTR_BRANCH);
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bci = bci + 2;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[0]));
                    } else {
                        assert lastChildPush == 1;
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
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_BRANCH_FALSE);
                        labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                        bc[bci + 2] = (short) numConditionProfiles;
                        numConditionProfiles += 2;
                        bci = bci + 3;
                    } else {
                        for (int i = 0; i < lastChildPush; i++) {
                            doBeforeEmitInstruction(1, false);
                            bc[bci] = (short) (INSTR_POP);
                            bci = bci + 1;
                        }
                        calculateLeaves(operationData, ((OperationLabelImpl) operationData.aux[0]));
                        doBeforeEmitInstruction(0, false);
                        bc[bci] = (short) (INSTR_BRANCH);
                        labelFills.add(new LabelFill(bci + 1 + 0, ((OperationLabelImpl) operationData.aux[0])));
                        bci = bci + 2;
                        doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
                    }
                    break;
                }
                case OP_TRY_CATCH :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_POP);
                        bci = bci + 1;
                    }
                    if (childIndex == 0) {
                        ((ExceptionHandler) operationData.aux[0]).endBci = bci;
                        calculateLeaves(operationData, ((OperationLabelImpl) operationData.aux[1]));
                        doBeforeEmitInstruction(0, false);
                        bc[bci] = (short) (INSTR_BRANCH);
                        labelFills.add(new LabelFill(bci + 1 + 0, ((OperationLabelImpl) operationData.aux[1])));
                        bci = bci + 2;
                    } else {
                    }
                    break;
                }
                case OP_FINALLY_TRY :
                {
                    for (int i = 0; i < lastChildPush; i++) {
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_POP);
                        bci = bci + 1;
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
                        doBeforeEmitInstruction(1, false);
                        bc[bci] = (short) (INSTR_POP);
                        bci = bci + 1;
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
        @Override
        public void beginBlock() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_BLOCK << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BLOCK, curStack, 0, false);
            lastChildPush = 0;
        }

        @SuppressWarnings("unused")
        @Override
        public void endBlock() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_BLOCK << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginIfThen() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_IF_THEN << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN, curStack, 1, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endIfThen() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_IF_THEN << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginIfThenElse() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_IF_THEN_ELSE << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_IF_THEN_ELSE, curStack, 2, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endIfThenElse() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_IF_THEN_ELSE << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginConditional() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_CONDITIONAL << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_CONDITIONAL, curStack, 2, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endConditional() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_CONDITIONAL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginWhile() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_WHILE << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_WHILE, curStack, 2, false);
            OperationLabelImpl startLabel = (OperationLabelImpl) createLabel();
            doEmitLabel(startLabel);
            operationData.aux[0] = startLabel;
        }

        @SuppressWarnings("unused")
        @Override
        public void endWhile() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_WHILE << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginTryCatch(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_TRY_CATCH << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void endTryCatch() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_TRY_CATCH << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            doEmitLabel(((OperationLabelImpl) operationData.aux[1]));
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginFinallyTry() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_FINALLY_TRY << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void endFinallyTry() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_FINALLY_TRY << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
                doBeforeEmitInstruction(0, false);
                bc[bci] = (short) (INSTR_BRANCH);
                labelFills.add(new LabelFill(bci + 1 + 0, endLabel));
                bci = bci + 2;
            }
            ExceptionHandler beh = ((ExceptionHandler) operationData.aux[1]);
            beh.endBci = endBci;
            beh.handlerBci = bci;
            doLeaveFinallyTry(operationData);
            {
                doBeforeEmitInstruction(0, false);
                bc[bci] = (short) (INSTR_THROW);
                bc[bci + 1 + 0] = (short) getLocalIndex(operationData.aux[2]);
                bci = bci + 2;
            }
            doEmitLabel(endLabel);
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginFinallyTryNoExcept() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_FINALLY_TRY_NO_EXCEPT << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void endFinallyTryNoExcept() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_FINALLY_TRY_NO_EXCEPT << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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

        @Override
        public void emitLabel(OperationLabel arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LABEL << 1));
                    serBuffer.writeShort((short) ((OperationSerLabelImpl) arg0).id);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            doEmitLabel(arg0);
            lastChildPush = 0;
            doAfterChild();
        }

        @Override
        public void emitBranch(OperationLabel arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_BRANCH << 1));
                    serBuffer.writeShort((short) ((OperationSerLabelImpl) arg0).id);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_BRANCH, curStack, 0, false, arg0);
            calculateLeaves(operationData, (OperationLabelImpl) operationData.arguments[0]);
            doBeforeEmitInstruction(0, false);
            bc[bci] = (short) (INSTR_BRANCH);
            labelFills.add(new LabelFill(bci + 1 + 0, (OperationLabelImpl) operationData.arguments[0]));
            bci = bci + 2;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitConstObject(Object arg0) {
            if (isSerializing) {
                try {
                    int arg0_index = constPool.indexOf(arg0);
                    if (arg0_index == -1) {
                        arg0_index = constPool.size();
                        constPool.add(arg0);
                        serBuffer.writeShort((short) -4);
                        serCallback.serialize(SER_CONTEXT, serBuffer, arg0);
                    }
                    serBuffer.writeShort((short) (OP_CONST_OBJECT << 1));
                    serBuffer.writeShort((short) arg0_index);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_CONST_OBJECT, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true);
            bc[bci] = (short) (INSTR_LOAD_CONSTANT_OBJECT);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(arg0);
            bci = bci + 2;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadArgument(int arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LOAD_ARGUMENT << 1));
                    serBuffer.writeInt(arg0);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_ARGUMENT, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true);
            bc[bci] = (short) (INSTR_LOAD_ARGUMENT_OBJECT);
            bc[bci + 1 + 0] = (short) (int) operationData.arguments[0];
            bci = bci + 2;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginStoreLocal(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_STORE_LOCAL << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_STORE_LOCAL, curStack, 0, false, (Object) arg0);
        }

        @SuppressWarnings("unused")
        @Override
        public void endStoreLocal() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_STORE_LOCAL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(1, false);
            bc[bci] = (short) (INSTR_STORE_LOCAL_UNINIT);
            bc[bci + 1 + 0] = (short) getLocalIndex(operationData.arguments[0]);
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bci = bci + 3;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @Override
        public void emitLoadLocal(OperationLocal arg0) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_LOAD_LOCAL << 1));
                    serBuffer.writeShort((short) ((OperationLocalImpl) arg0).id);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_LOAD_LOCAL, curStack, 0, false, arg0);
            doBeforeEmitInstruction(0, true);
            bc[bci] = (short) (INSTR_LOAD_LOCAL_UNINIT);
            bc[bci + 1 + 0] = (short) getLocalIndex(operationData.arguments[0]);
            bci = bci + 2;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginReturn() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_RETURN << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_RETURN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endReturn() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_RETURN << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            doBeforeEmitInstruction(1, false);
            bc[bci] = (short) (INSTR_RETURN);
            bci = bci + 1;
            lastChildPush = 0;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
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
                    throw new WrappedIOException(ex);
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
        @Override
        public void endSource() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SOURCE << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginSourceSection(int arg0, int arg1) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SOURCE_SECTION << 1));
                    serBuffer.writeInt(arg0);
                    serBuffer.writeInt(arg1);
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void endSourceSection() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SOURCE_SECTION << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
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
                    throw new WrappedIOException(ex);
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
                doBeforeEmitInstruction(0, false);
                bc[bci] = (short) (INSTR_INSTRUMENT_ENTER);
                bci = bci + 2;
                lastChildPush = 0;
            }
        }

        @SuppressWarnings("unused")
        @Override
        public void endTag() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_TAG << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
                    doBeforeEmitInstruction(0, false);
                    bc[bci] = (short) (INSTR_INSTRUMENT_EXIT_VOID);
                    bci = bci + 2;
                } else {
                    doBeforeEmitInstruction(0, false);
                    bc[bci] = (short) (INSTR_INSTRUMENT_EXIT);
                    bci = bci + 2;
                }
                operationData = operationData.parent;
                doAfterChild();
            }
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLAdd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_ADD << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_ADD, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLAdd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_ADD << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_ADD);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 2;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLDiv() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_DIV << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_DIV, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLDiv() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_DIV << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_DIV);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_EQUAL << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_EQUAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_EQUAL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_EQUAL);
            bc[bci + 1] = (short) numChildNodes;
            numChildNodes += 4;
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 2 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            bc[bci + 3 + 2] = 0;
            bc[bci + 3 + 3] = 0;
            bci = bci + 7;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessOrEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LESS_OR_EQUAL << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LESS_OR_EQUAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessOrEqual() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LESS_OR_EQUAL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLessThan() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LESS_THAN << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LESS_THAN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLessThan() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LESS_THAN << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_LESS_THAN);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLLogicalNot() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_LOGICAL_NOT << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_LOGICAL_NOT, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLLogicalNot() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_LOGICAL_NOT << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            bc[bci] = (short) (INSTR_C_SL_LOGICAL_NOT);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLMul() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_MUL << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_MUL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLMul() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_MUL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_MUL);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLReadProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_READ_PROPERTY << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_READ_PROPERTY, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLReadProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_READ_PROPERTY << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_READ_PROPERTY);
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
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLSub() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_SUB << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_SUB, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLSub() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_SUB << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(2, true);
            bc[bci] = (short) (INSTR_C_SL_SUB);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] |= (short) ((bci - predecessorBcis[1] < 256 ? bci - predecessorBcis[1] : 0) << 8);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLWriteProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_WRITE_PROPERTY << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_WRITE_PROPERTY, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLWriteProperty() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_WRITE_PROPERTY << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(3, true);
            bc[bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
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
            bc[bci + 5 + 2] = 0;
            bc[bci + 5 + 3] = 0;
            bci = bci + 9;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLUnbox() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_UNBOX << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_UNBOX, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLUnbox() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_UNBOX << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            bc[bci] = (short) (INSTR_C_SL_UNBOX);
            bc[bci + 1] = (short) numChildNodes;
            numChildNodes += 3;
            bc[bci + 2 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 3 + 0] = 0;
            bc[bci + 3 + 1] = 0;
            bc[bci + 3 + 2] = 0;
            bc[bci + 3 + 3] = 0;
            bci = bci + 7;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLFunctionLiteral() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_FUNCTION_LITERAL << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_FUNCTION_LITERAL, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLFunctionLiteral() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_FUNCTION_LITERAL << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            bc[bci] = (short) (INSTR_C_SL_FUNCTION_LITERAL);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLToBoolean() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_TO_BOOLEAN << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_TO_BOOLEAN, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLToBoolean() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_TO_BOOLEAN << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            int[] predecessorBcis = doBeforeEmitInstruction(1, true);
            bc[bci] = (short) (INSTR_C_SL_TO_BOOLEAN);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 1;
            bc[bci + 3 + 0] = (short) ((bci - predecessorBcis[0] < 256 ? bci - predecessorBcis[0] : 0));
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bci = bci + 6;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLInvoke() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_INVOKE << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_INVOKE, curStack, 0, false);
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLInvoke() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_INVOKE << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
            doBeforeEmitInstruction(numChildren - 1 + 1, true);
            bc[bci] = (short) (INSTR_C_SL_INVOKE);
            int constantsStart = constPool.size();
            bc[bci + 1] = (short) constantsStart;
            constPool.add(null);
            bc[bci + 2] = (short) numChildNodes;
            numChildNodes += 4;
            bc[bci + 3] = (short) (numChildren - 1);
            bc[bci + 4 + 0] = 0;
            bc[bci + 4 + 1] = 0;
            bc[bci + 4 + 2] = 0;
            bc[bci + 4 + 3] = 0;
            bci = bci + 8;
            lastChildPush = 1;
            operationData = operationData.parent;
            doAfterChild();
        }

        @SuppressWarnings("unused")
        @Override
        public void beginSLAnd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_AND << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_AND, curStack, 1, false);
            operationData.aux[0] = (OperationLabelImpl) createLabel();
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLAnd() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_AND << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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
        @Override
        public void beginSLOr() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) (OP_SL_OR << 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
                return;
            }
            doBeforeChild();
            operationData = new BuilderOperationData(operationData, OP_SL_OR, curStack, 1, false);
            operationData.aux[0] = (OperationLabelImpl) createLabel();
        }

        @SuppressWarnings("unused")
        @Override
        public void endSLOr() {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) ((OP_SL_OR << 1) | 1));
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
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

        @Override
        public void setMethodName(TruffleString value) {
            if (isSerializing) {
                try {
                    serBuffer.writeShort((short) -6);
                    serBuffer.writeShort(0);
                    serCallback.serialize(SER_CONTEXT, serBuffer, value);
                    return;
                } catch (IOException ex) {
                    throw new WrappedIOException(ex);
                }
            }
            metadata_MethodName = value;
        }

        private static boolean do_profileCondition(boolean value, int[] profiles, int index) {
            int t = profiles[index];
            int f = profiles[index + 1];
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
                        profiles[index] = t + 1;
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
                        profiles[index + 1] = f + 1;
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

        private static void deserializeParser(DataInputStream buffer, OperationDeserializationCallback callback, SLOperationsBuilder builder) {
            try {
                ArrayList<Object> consts = new ArrayList<>();
                ArrayList<OperationLocal> locals = new ArrayList<>();
                ArrayList<OperationLabel> labels = new ArrayList<>();
                ArrayList<OperationNode> builtNodes = new ArrayList<>();
                com.oracle.truffle.api.operation.serialization.OperationDeserializationCallback.Context context = new com.oracle.truffle.api.operation.serialization.OperationDeserializationCallback.Context(){
                    @Override
                    public OperationNode deserializeOperationNode(DataInputStream buffer) throws IOException {
                        return builtNodes.get(buffer.readInt());
                    }
                }
                ;
                while (true) {
                    switch (buffer.readShort()) {
                        case -1 :
                        {
                            builtNodes.add(builder.publish());
                            locals.clear();
                            labels.clear();
                            break;
                        }
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
                            consts.add(callback.deserialize(context, buffer));
                            break;
                        }
                        case -5 :
                        {
                            return;
                        }
                        case -6 :
                        {
                            switch (buffer.readShort()) {
                                case 0 :
                                    builder.setMethodName((TruffleString) callback.deserialize(context, buffer));
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
                            OperationLocal arg0 = locals.get(buffer.readShort());
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
                            OperationLabel arg0 = labels.get(buffer.readShort());
                            builder.emitLabel(arg0);
                            break;
                        }
                        case OP_BRANCH << 1 :
                        {
                            OperationLabel arg0 = labels.get(buffer.readShort());
                            builder.emitBranch(arg0);
                            break;
                        }
                        case OP_CONST_OBJECT << 1 :
                        {
                            Object arg0 = (Object) consts.get(buffer.readShort());
                            builder.emitConstObject(arg0);
                            break;
                        }
                        case OP_LOAD_ARGUMENT << 1 :
                        {
                            int arg0 = buffer.readInt();
                            builder.emitLoadArgument(arg0);
                            break;
                        }
                        case OP_STORE_LOCAL << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.readShort());
                            builder.beginStoreLocal(arg0);
                            break;
                        }
                        case (OP_STORE_LOCAL << 1) | 1 :
                        {
                            builder.endStoreLocal();
                            break;
                        }
                        case OP_LOAD_LOCAL << 1 :
                        {
                            OperationLocal arg0 = locals.get(buffer.readShort());
                            builder.emitLoadLocal(arg0);
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
                        case OP_SOURCE << 1 :
                        {
                            Source arg0 = (Source) consts.get(buffer.readShort());
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
                            int arg0 = buffer.readInt();
                            int arg1 = buffer.readInt();
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
                            Class<?> arg0 = (Class<?>) consts.get(buffer.readShort());
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
                throw new WrappedIOException(ex);
            }
        }

        @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
        private static final class OperationSerLabelImpl extends OperationLabel {

            int id;

            OperationSerLabelImpl(int id) {
                this.id = id;
            }

        }
        @GeneratedBy(SLOperations.class)
        private static final class OperationLocalImpl extends OperationLocal {

            final BuilderOperationData owner;
            final int id;

            OperationLocalImpl(BuilderOperationData owner, int id) {
                this.owner = owner;
                this.id = id;
            }

        }
        @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
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

            @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
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
        @GeneratedBy(SLOperations.class)
        private static final class OperationNodeImpl extends OperationNode implements BytecodeOSRNode {

            private static final BytecodeLoopBase UNCACHED_EXECUTE = new UncachedBytecodeNode();
            private static final BytecodeLoopBase COMMON_EXECUTE = new BytecodeNode();
            private static final BytecodeLoopBase INITIAL_EXECUTE = UNCACHED_EXECUTE;

            @CompilationFinal(dimensions = 1) short[] _bc;
            @CompilationFinal(dimensions = 1) Object[] _consts;
            @Children Node[] _children;
            @CompilationFinal(dimensions = 1) ExceptionHandler[] _handlers;
            @CompilationFinal(dimensions = 1) int[] _conditionProfiles;
            @CompilationFinal int _maxLocals;
            @CompilationFinal int _maxStack;
            @CompilationFinal(dimensions = 1) int[] sourceInfo;
            @CompilationFinal int uncachedExecuteCount = 16;
            @CompilationFinal private Object _osrMetadata;
            private TruffleString _metadata_MethodName;
            @CompilationFinal private BytecodeLoopBase switchImpl = INITIAL_EXECUTE;

            private OperationNodeImpl(OperationNodes nodes) {
                super(nodes);
            }

            static  {
                setMetadataAccessor(SLOperations.MethodName, n -> ((OperationNodeImpl) n)._metadata_MethodName);
            }

            private <T extends Node> T insertAccessor(T node) { // () {
                return insert(node);
            }

            private Object executeAt(VirtualFrame frame, int storedLocation) {
                int result = storedLocation;
                while (true) {
                    result = switchImpl.continueAt(this, frame, _bc, result & 0xffff, (result >> 16) & 0xffff, _consts, _children, _handlers, _conditionProfiles, _maxLocals);
                    if ((result & 0xffff) == 0xffff) {
                        break;
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                    }
                }
                return frame.getObject((result >> 16) & 0xffff);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return executeAt(frame, _maxLocals << 16);
            }

            @Override
            protected int[] getSourceInfo() {
                return sourceInfo;
            }

            @Override
            public String dump() {
                return switchImpl.dump(_bc, _handlers, _consts);
            }

            private Lock getLockAccessor() {
                return getLock();
            }

            @Override
            public FrameDescriptor createFrameDescriptor() {
                FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
                builder.addSlots(_maxLocals + _maxStack, FrameSlotKind.Illegal);
                return builder.build();
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
                OperationNodeImpl result = new OperationNodeImpl(nodes);
                result._bc = Arrays.copyOf(_bc, _bc.length);
                result._consts = Arrays.copyOf(_consts, _consts.length);
                result._children = Arrays.copyOf(_children, _children.length);
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
                OperationNodeImpl result = new OperationNodeImpl(nodes);
                result._bc = _bc;
                result._consts = _consts;
                result._children = _children;
                result._handlers = _handlers;
                result._conditionProfiles = _conditionProfiles;
                result._maxLocals = _maxLocals;
                result._maxStack = _maxStack;
                result.sourceInfo = sourceInfo;
                result._metadata_MethodName = _metadata_MethodName;
                return result;
            }

            void changeInterpreters(BytecodeLoopBase impl) {
                this.switchImpl = impl;
            }

        }
        @GeneratedBy(SLOperations.class)
        private static final class OperationSerNodeImpl extends OperationNode {

            @CompilationFinal int buildOrder;

            private OperationSerNodeImpl(OperationNodes nodes, int buildOrder) {
                super(nodes);
                this.buildOrder = buildOrder;
            }

            @Override
            public String dump() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int[] getSourceInfo() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Object execute(VirtualFrame frame) {
                throw new UnsupportedOperationException();
            }

            @Override
            public FrameDescriptor createFrameDescriptor() {
                throw new UnsupportedOperationException();
            }

        }
        @GeneratedBy(SLOperations.class)
        private abstract static class BytecodeLoopBase {

            abstract int continueAt(OperationNodeImpl $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals);

            abstract String dump(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts);

            abstract void prepareForAOT(OperationNodeImpl $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root);

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

            protected static Object expectObject(VirtualFrame frame, int slot) {
                if (frame.isObject(slot)) {
                    return frame.getObject(slot);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return frame.getValue(slot);
                }
            }

            protected static void setResultBoxedImpl(short[] bc, int bci, int targetType, short[] descriptor) {
                int op = bc[bci] & 0xffff;
                short todo = descriptor[op];
                if (todo > 0) {
                    bc[bci] = todo;
                } else {
                    int offset = (todo >> 8) & 0x7f;
                    int bit = todo & 0xff;
                    if (targetType == 0 /* OBJECT */) {
                        bc[bci + offset] &= ~bit;
                    } else {
                        bc[bci + offset] |= bit;
                    }
                }
            }

            protected static long expectLong(VirtualFrame frame, int slot) throws UnexpectedResultException {
                switch (frame.getTag(slot)) {
                    case 1 /* LONG */ :
                        return frame.getLong(slot);
                    case 0 /* OBJECT */ :
                        Object value = frame.getObject(slot);
                        if (value instanceof Long) {
                            return (long) value;
                        }
                        break;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnexpectedResultException(frame.getValue(slot));
            }

            protected static boolean storeLocalLongCheck(VirtualFrame frame, int localSlot, int stackSlot) {
                FrameDescriptor descriptor = frame.getFrameDescriptor();
                if (descriptor.getSlotKind(localSlot) == FrameSlotKind.Long) {
                    try {
                        frame.setLong(localSlot, expectLong(frame, stackSlot));
                        return true;
                    } catch (UnexpectedResultException ex) {
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                descriptor.setSlotKind(localSlot, FrameSlotKind.Object);
                frame.setObject(localSlot, frame.getValue(stackSlot));
                return false;
            }

            protected static boolean expectBoolean(VirtualFrame frame, int slot) throws UnexpectedResultException {
                switch (frame.getTag(slot)) {
                    case 5 /* BOOLEAN */ :
                        return frame.getBoolean(slot);
                    case 0 /* OBJECT */ :
                        Object value = frame.getObject(slot);
                        if (value instanceof Boolean) {
                            return (boolean) value;
                        }
                        break;
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnexpectedResultException(frame.getValue(slot));
            }

            protected static boolean storeLocalBooleanCheck(VirtualFrame frame, int localSlot, int stackSlot) {
                FrameDescriptor descriptor = frame.getFrameDescriptor();
                if (descriptor.getSlotKind(localSlot) == FrameSlotKind.Boolean) {
                    try {
                        frame.setBoolean(localSlot, expectBoolean(frame, stackSlot));
                        return true;
                    } catch (UnexpectedResultException ex) {
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                descriptor.setSlotKind(localSlot, FrameSlotKind.Object);
                frame.setObject(localSlot, frame.getValue(stackSlot));
                return false;
            }

        }
        @GeneratedBy(SLOperations.class)
        private static final class BytecodeNode extends BytecodeLoopBase {

            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            @BytecodeInterpreterSwitch
            @Override
            int continueAt(OperationNodeImpl $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals) {
                int $sp = $startSp;
                int $bci = $startBci;
                Counter loopCounter = new Counter();
                loop: while (true) {
                    CompilerAsserts.partialEvaluationConstant($bci);
                    CompilerAsserts.partialEvaluationConstant($sp);
                    short curOpcode = unsafeFromBytecode($bc, $bci);
                    CompilerAsserts.partialEvaluationConstant(curOpcode);
                    try {
                        if ($sp < maxLocals) {
                            throw CompilerDirectives.shouldNotReachHere("stack underflow");
                        }
                        switch (curOpcode) {
                            // pop
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_POP :
                            {
                                $sp = $sp - 1;
                                $frame.clear($sp);
                                $bci = $bci + POP_LENGTH;
                                continue loop;
                            }
                            // branch
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            //   Boxing Elimination: Do Nothing
                            case INSTR_BRANCH :
                            {
                                int targetBci = $bc[$bci + BRANCH_BRANCH_TARGET_OFFSET + 0];
                                if (targetBci <= $bci) {
                                    if (CompilerDirectives.hasNextTier() && ++loopCounter.count >= 256) {
                                        TruffleSafepoint.poll($this);
                                        LoopNode.reportLoopCount($this, 256);
                                        loopCounter.count = 0;
                                    }
                                    if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge($this)) {
                                        Object osrResult = BytecodeOSRNode.tryOSR($this, targetBci, $sp, null, $frame);
                                        if (osrResult != null) {
                                            $frame.setObject(0, osrResult);
                                            return 0x0000ffff;
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
                            //   Boxing Elimination: Do Nothing
                            case INSTR_BRANCH_FALSE :
                            {
                                boolean cond = $frame.getObject($sp - 1) == Boolean.TRUE;
                                $sp = $sp - 1;
                                if (do_profileCondition(cond, $conditionProfiles, $bc[$bci + BRANCH_FALSE_BRANCH_PROFILE_OFFSET + 0])) {
                                    $bci = $bci + BRANCH_FALSE_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0];
                                    continue loop;
                                }
                            }
                            // throw
                            //   Locals:
                            //     [ 0] exception
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_THROW :
                            {
                                int slot = $bc[$bci + THROW_LOCALS_OFFSET + 0];
                                throw (AbstractTruffleException) $frame.getObject(slot);
                            }
                            // load.constant.object
                            //   Constants:
                            //     [ 0] constant
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_CONSTANT_OBJECT
                            //     LONG -> INSTR_LOAD_CONSTANT_LONG
                            //     BOOLEAN -> INSTR_LOAD_CONSTANT_BOOLEAN
                            case INSTR_LOAD_CONSTANT_OBJECT :
                            {
                                $frame.setObject($sp, $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_OBJECT_CONSTANT_OFFSET) + 0]);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                                continue loop;
                            }
                            // load.constant.long
                            //   Constants:
                            //     [ 0] constant
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_CONSTANT_OBJECT
                            //     LONG -> INSTR_LOAD_CONSTANT_LONG
                            //     INT -> INSTR_LOAD_CONSTANT_LONG
                            //     DOUBLE -> INSTR_LOAD_CONSTANT_LONG
                            //     FLOAT -> INSTR_LOAD_CONSTANT_LONG
                            //     BOOLEAN -> INSTR_LOAD_CONSTANT_LONG
                            //     BYTE -> INSTR_LOAD_CONSTANT_LONG
                            case INSTR_LOAD_CONSTANT_LONG :
                            {
                                $frame.setLong($sp, (long) $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_LONG_CONSTANT_OFFSET) + 0]);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_CONSTANT_LONG_LENGTH;
                                continue loop;
                            }
                            // load.constant.boolean
                            //   Constants:
                            //     [ 0] constant
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_CONSTANT_OBJECT
                            //     LONG -> INSTR_LOAD_CONSTANT_BOOLEAN
                            //     INT -> INSTR_LOAD_CONSTANT_BOOLEAN
                            //     DOUBLE -> INSTR_LOAD_CONSTANT_BOOLEAN
                            //     FLOAT -> INSTR_LOAD_CONSTANT_BOOLEAN
                            //     BOOLEAN -> INSTR_LOAD_CONSTANT_BOOLEAN
                            //     BYTE -> INSTR_LOAD_CONSTANT_BOOLEAN
                            case INSTR_LOAD_CONSTANT_BOOLEAN :
                            {
                                $frame.setBoolean($sp, (boolean) $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_BOOLEAN_CONSTANT_OFFSET) + 0]);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_CONSTANT_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // load.argument.object
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_LONG
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            case INSTR_LOAD_ARGUMENT_OBJECT :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_OBJECT_ARGUMENT_OFFSET + 0]];
                                $frame.setObject($sp, value);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                                continue loop;
                            }
                            // load.argument.long
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_LONG
                            //     INT -> INSTR_LOAD_ARGUMENT_LONG
                            //     DOUBLE -> INSTR_LOAD_ARGUMENT_LONG
                            //     FLOAT -> INSTR_LOAD_ARGUMENT_LONG
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_LONG
                            //     BYTE -> INSTR_LOAD_ARGUMENT_LONG
                            case INSTR_LOAD_ARGUMENT_LONG :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_LONG_ARGUMENT_OFFSET + 0]];
                                if (value instanceof Long) {
                                    $frame.setLong($sp, (long) value);
                                } else {
                                    $frame.setObject($sp, value);
                                }
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                                continue loop;
                            }
                            // load.argument.boolean
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     INT -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     DOUBLE -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     FLOAT -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     BYTE -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            case INSTR_LOAD_ARGUMENT_BOOLEAN :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_BOOLEAN_ARGUMENT_OFFSET + 0]];
                                if (value instanceof Boolean) {
                                    $frame.setBoolean($sp, (boolean) value);
                                } else {
                                    $frame.setObject($sp, value);
                                }
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // store.local.object
                            //   Locals:
                            //     [ 0] target
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_STORE_LOCAL_OBJECT :
                            {
                                int localIdx = $bc[$bci + STORE_LOCAL_OBJECT_LOCALS_OFFSET + 0];
                                int sourceSlot = $sp - 1;
                                $frame.setObject(localIdx, expectObject($frame, sourceSlot));
                                $sp--;
                                $bci = $bci + STORE_LOCAL_OBJECT_LENGTH;
                                continue loop;
                            }
                            // store.local.long
                            //   Locals:
                            //     [ 0] target
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_STORE_LOCAL_OBJECT
                            //     LONG -> 0
                            //     INT -> INSTR_STORE_LOCAL_OBJECT
                            //     DOUBLE -> INSTR_STORE_LOCAL_OBJECT
                            //     FLOAT -> INSTR_STORE_LOCAL_OBJECT
                            //     BOOLEAN -> INSTR_STORE_LOCAL_OBJECT
                            //     BYTE -> INSTR_STORE_LOCAL_OBJECT
                            case INSTR_STORE_LOCAL_LONG :
                            {
                                int localIdx = $bc[$bci + STORE_LOCAL_LONG_LOCALS_OFFSET + 0];
                                int sourceSlot = $sp - 1;
                                if (!storeLocalLongCheck($frame, localIdx, sourceSlot)) {
                                    $bc[$bci] = (short) (INSTR_STORE_LOCAL_OBJECT);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + STORE_LOCAL_LONG_POP_INDEXED_OFFSET + 0] & 0xff), 0 /* OBJECT */);
                                }
                                $sp--;
                                $bci = $bci + STORE_LOCAL_LONG_LENGTH;
                                continue loop;
                            }
                            // store.local.boolean
                            //   Locals:
                            //     [ 0] target
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_STORE_LOCAL_OBJECT
                            //     LONG -> INSTR_STORE_LOCAL_OBJECT
                            //     INT -> INSTR_STORE_LOCAL_OBJECT
                            //     DOUBLE -> INSTR_STORE_LOCAL_OBJECT
                            //     FLOAT -> INSTR_STORE_LOCAL_OBJECT
                            //     BOOLEAN -> 0
                            //     BYTE -> INSTR_STORE_LOCAL_OBJECT
                            case INSTR_STORE_LOCAL_BOOLEAN :
                            {
                                int localIdx = $bc[$bci + STORE_LOCAL_BOOLEAN_LOCALS_OFFSET + 0];
                                int sourceSlot = $sp - 1;
                                if (!storeLocalBooleanCheck($frame, localIdx, sourceSlot)) {
                                    $bc[$bci] = (short) (INSTR_STORE_LOCAL_OBJECT);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + STORE_LOCAL_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff), 0 /* OBJECT */);
                                }
                                $sp--;
                                $bci = $bci + STORE_LOCAL_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // store.local.uninit
                            //   Locals:
                            //     [ 0] target
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_STORE_LOCAL_OBJECT
                            //     LONG -> INSTR_STORE_LOCAL_LONG
                            //     BOOLEAN -> INSTR_STORE_LOCAL_BOOLEAN
                            case INSTR_STORE_LOCAL_UNINIT :
                            {
                                int localIdx = $bc[$bci + STORE_LOCAL_UNINIT_LOCALS_OFFSET + 0];
                                int sourceSlot = $sp - 1;
                                FrameSlotKind localTag = $frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localTag == FrameSlotKind.Illegal) {
                                    assert $frame.isObject(sourceSlot);
                                    $frame.copyObject(sourceSlot, localIdx);
                                } else {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    int resultTag = storeLocalInitialization($frame, localIdx, localTag.tag, sourceSlot);
                                    setResultBoxedImpl($bc, $bci, resultTag, BOXING_DESCRIPTORS[resultTag]);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + STORE_LOCAL_UNINIT_POP_INDEXED_OFFSET + 0] & 0xff), resultTag);
                                }
                                $sp--;
                                $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                                continue loop;
                            }
                            // load.local.object
                            //   Locals:
                            //     [ 0] local
                            //   Pushed Values: 1
                            //   Boxing Elimination: Do Nothing
                            case INSTR_LOAD_LOCAL_OBJECT :
                            {
                                int localIdx = $bc[$bci + LOAD_LOCAL_OBJECT_LOCALS_OFFSET + 0];
                                if ($frame.getFrameDescriptor().getSlotKind(localIdx) != FrameSlotKind.Object) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    $frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                    $frame.setObject(localIdx, $frame.getValue(localIdx));
                                }
                                $frame.copyObject(localIdx, $sp);
                                $sp++;
                                $bci = $bci + LOAD_LOCAL_OBJECT_LENGTH;
                                continue loop;
                            }
                            // load.local.long
                            //   Locals:
                            //     [ 0] local
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_LOCAL_OBJECT
                            //     LONG -> 0
                            //     INT -> INSTR_LOAD_LOCAL_OBJECT
                            //     DOUBLE -> INSTR_LOAD_LOCAL_OBJECT
                            //     FLOAT -> INSTR_LOAD_LOCAL_OBJECT
                            //     BOOLEAN -> INSTR_LOAD_LOCAL_OBJECT
                            //     BYTE -> INSTR_LOAD_LOCAL_OBJECT
                            case INSTR_LOAD_LOCAL_LONG :
                            {
                                int localIdx = $bc[$bci + LOAD_LOCAL_LONG_LOCALS_OFFSET + 0];
                                FrameSlotKind localType = $frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localType != FrameSlotKind.Long) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    Object localValue;
                                    if (localType == FrameSlotKind.Illegal && (localValue = $frame.getObject(localIdx)) instanceof Long) {
                                        $frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Long);
                                        $frame.setLong(localIdx, (long) localValue);
                                        $frame.copyPrimitive(localIdx, $sp);
                                    } else {
                                        $frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                        $bc[$bci] = (short) (INSTR_LOAD_LOCAL_OBJECT);
                                        $frame.copyObject(localIdx, $sp);
                                    }
                                } else {
                                    $frame.copyPrimitive(localIdx, $sp);
                                }
                                $sp++;
                                $bci = $bci + LOAD_LOCAL_LONG_LENGTH;
                                continue loop;
                            }
                            // load.local.boolean
                            //   Locals:
                            //     [ 0] local
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_LOCAL_OBJECT
                            //     LONG -> INSTR_LOAD_LOCAL_OBJECT
                            //     INT -> INSTR_LOAD_LOCAL_OBJECT
                            //     DOUBLE -> INSTR_LOAD_LOCAL_OBJECT
                            //     FLOAT -> INSTR_LOAD_LOCAL_OBJECT
                            //     BOOLEAN -> 0
                            //     BYTE -> INSTR_LOAD_LOCAL_OBJECT
                            case INSTR_LOAD_LOCAL_BOOLEAN :
                            {
                                int localIdx = $bc[$bci + LOAD_LOCAL_BOOLEAN_LOCALS_OFFSET + 0];
                                FrameSlotKind localType = $frame.getFrameDescriptor().getSlotKind(localIdx);
                                if (localType != FrameSlotKind.Boolean) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    Object localValue;
                                    if (localType == FrameSlotKind.Illegal && (localValue = $frame.getObject(localIdx)) instanceof Boolean) {
                                        $frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Boolean);
                                        $frame.setBoolean(localIdx, (boolean) localValue);
                                        $frame.copyPrimitive(localIdx, $sp);
                                    } else {
                                        $frame.getFrameDescriptor().setSlotKind(localIdx, FrameSlotKind.Object);
                                        $bc[$bci] = (short) (INSTR_LOAD_LOCAL_OBJECT);
                                        $frame.copyObject(localIdx, $sp);
                                    }
                                } else {
                                    $frame.copyPrimitive(localIdx, $sp);
                                }
                                $sp++;
                                $bci = $bci + LOAD_LOCAL_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // load.local.uninit
                            //   Locals:
                            //     [ 0] local
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_LOCAL_OBJECT
                            //     LONG -> INSTR_LOAD_LOCAL_LONG
                            //     BOOLEAN -> INSTR_LOAD_LOCAL_BOOLEAN
                            case INSTR_LOAD_LOCAL_UNINIT :
                            {
                                int localIdx = $bc[$bci + LOAD_LOCAL_UNINIT_LOCALS_OFFSET + 0];
                                $frame.setObject($sp, expectObject($frame, localIdx));
                                $sp++;
                                $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                                continue loop;
                            }
                            // return
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_RETURN :
                            {
                                return (($sp - 1) << 16) | 0xffff;
                            }
                            // c.SLAdd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Add1, method = public static com.oracle.truffle.api.strings.TruffleString add(java.lang.Object, java.lang.Object, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.api.strings.TruffleString.ConcatNode) , guards = [Guard[(SLAddNode.isString(left, right))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@19cfc07
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@643f16e7
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_ADD :
                            {
                                BytecodeNode.SLAdd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_ADD_LENGTH;
                                continue loop;
                            }
                            // c.SLDiv
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@587558fe
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@6e56aaae
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_DIV :
                            {
                                BytecodeNode.SLDiv_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_DIV_LENGTH;
                                continue loop;
                            }
                            // c.SLEqual
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = equalNode]
                            //     [ 1] SpecializationData [id = Generic0, method = public static boolean doGeneric(java.lang.Object, java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(leftInterop.accepts(left))], Guard[(rightInterop.accepts(right))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = leftInterop]
                            //     [ 3] CacheExpression [sourceParameter = rightInterop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@8cc0fa7
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@14d8a877
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_EQUAL :
                            {
                                BytecodeNode.SLEqual_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_EQUAL_LENGTH;
                                continue loop;
                            }
                            // c.SLLessOrEqual
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@6618a0ba
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_OR_EQUAL :
                            {
                                BytecodeNode.SLLessOrEqual_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                                continue loop;
                            }
                            // c.SLLessThan
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@3d40650d
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_THAN :
                            {
                                BytecodeNode.SLLessThan_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_THAN_LENGTH;
                                continue loop;
                            }
                            // c.SLLogicalNot
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@5599f435
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LOGICAL_NOT :
                            {
                                BytecodeNode.SLLogicalNot_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                                continue loop;
                            }
                            // c.SLMul
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@45365e1
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@276d18a1
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_MUL :
                            {
                                BytecodeNode.SLMul_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_MUL_LENGTH;
                                continue loop;
                            }
                            // c.SLReadProperty
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //     [ 2] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = ReadArray0, method = public static java.lang.Object readArray(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = ReadSLObject0, method = public static java.lang.Object readSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = node]
                            //     [ 6] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 7] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 8] SpecializationData [id = ReadObject0, method = public static java.lang.Object readObject(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objects.accepts(receiver))], Guard[(!(SLReadPropertyNode.isSLObject(receiver)))], Guard[(objects.hasMembers(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 9] CacheExpression [sourceParameter = node]
                            //     [10] CacheExpression [sourceParameter = objects]
                            //     [11] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@19bb694c
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@3990e12
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_READ_PROPERTY :
                            {
                                BytecodeNode.SLReadProperty_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                                continue loop;
                            }
                            // c.SLSub
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@1cfa1fed
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@539da4c3
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_SUB :
                            {
                                BytecodeNode.SLSub_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_SUB_LENGTH;
                                continue loop;
                            }
                            // c.SLWriteProperty
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = WriteArray0, method = public static java.lang.Object writeArray(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = WriteSLObject0, method = public static java.lang.Object writeSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 6] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 7] SpecializationData [id = WriteObject0, method = public static java.lang.Object writeObject(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objectLibrary.accepts(receiver))], Guard[(!(SLWritePropertyNode.isSLObject(receiver)))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 8] CacheExpression [sourceParameter = node]
                            //     [ 9] CacheExpression [sourceParameter = objectLibrary]
                            //     [10] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //     [ 2] arg2
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@11ae46f5
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@5a8c334c
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_WRITE_PROPERTY :
                            {
                                BytecodeNode.SLWriteProperty_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 3 + 1;
                                $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                                continue loop;
                            }
                            // c.SLUnbox
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                            //     [ 1] SpecializationData [id = FromForeign0, method = public static java.lang.Object fromForeign(java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(interop.accepts(value))]], signature = [java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = interop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@16455a87
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@28c05f30
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_UNBOX :
                            {
                                BytecodeNode.SLUnbox_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_UNBOX_LENGTH;
                                continue loop;
                            }
                            // c.SLFunctionLiteral
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = result]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4b3a2d33
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_FUNCTION_LITERAL :
                            {
                                BytecodeNode.SLFunctionLiteral_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                                continue loop;
                            }
                            // c.SLToBoolean
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7097614b
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_TO_BOOLEAN :
                            {
                                BytecodeNode.SLToBoolean_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // c.SLInvoke
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Direct, method = protected static java.lang.Object doDirect(com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[], com.oracle.truffle.api.Assumption, com.oracle.truffle.api.RootCallTarget, com.oracle.truffle.api.nodes.DirectCallNode) , guards = [Guard[(function.getCallTarget() == cachedTarget)]], signature = [com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[]]]
                            //     [ 1] CacheExpression [sourceParameter = callNode]
                            //     [ 2] CacheExpression [sourceParameter = library]
                            //     [ 3] CacheExpression [sourceParameter = node]
                            //   Simple Pops:
                            //     [ 0] arg0
                            //   Variadic
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4c3a8cbc
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@662973d4
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_INVOKE :
                            {
                                int numVariadics = $bc[$bci + C_SL_INVOKE_VARIADIC_OFFSET + 0];
                                Object input_0 = $frame.getObject($sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = $frame.getObject($sp - numVariadics + varIndex);
                                }
                                Object result = BytecodeNode.SLInvoke_execute_($frame, $this, $bc, $bci, $sp, $consts, $children, input_0, input_1);
                                $sp = $sp - 1 - numVariadics + 1;
                                $frame.setObject($sp - 1, result);
                                $bci = $bci + C_SL_INVOKE_LENGTH;
                                continue loop;
                            }
                            // sc.SLAnd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7ce08997
                            //   Boxing Elimination: Do Nothing
                            case INSTR_SC_SL_AND :
                            {
                                if (BytecodeNode.SLAnd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_AND_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0];
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
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@37362279
                            //   Boxing Elimination: Do Nothing
                            case INSTR_SC_SL_OR :
                            {
                                if (!BytecodeNode.SLOr_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_OR_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0];
                                    continue loop;
                                }
                            }
                            // c.SLUnbox.q.FromLong
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                            //     [ 1] SpecializationData [id = FromForeign0, method = public static java.lang.Object fromForeign(java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(interop.accepts(value))]], signature = [java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = interop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@2f71174f
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@26b76a6
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_UNBOX_Q_FROM_LONG :
                            {
                                BytecodeNode.SLUnbox_q_FromLong_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_UNBOX_Q_FROM_LONG_LENGTH;
                                continue loop;
                            }
                            // c.SLAdd.q.AddLong
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Add1, method = public static com.oracle.truffle.api.strings.TruffleString add(java.lang.Object, java.lang.Object, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.api.strings.TruffleString.ConcatNode) , guards = [Guard[(SLAddNode.isString(left, right))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7abffb4d
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@7c1c2b89
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_ADD_Q_ADD_LONG :
                            {
                                BytecodeNode.SLAdd_q_AddLong_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_ADD_Q_ADD_LONG_LENGTH;
                                continue loop;
                            }
                            // c.SLReadProperty.q.ReadSLObject0
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //     [ 2] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = ReadArray0, method = public static java.lang.Object readArray(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = ReadSLObject0, method = public static java.lang.Object readSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = node]
                            //     [ 6] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 7] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 8] SpecializationData [id = ReadObject0, method = public static java.lang.Object readObject(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objects.accepts(receiver))], Guard[(!(SLReadPropertyNode.isSLObject(receiver)))], Guard[(objects.hasMembers(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 9] CacheExpression [sourceParameter = node]
                            //     [10] CacheExpression [sourceParameter = objects]
                            //     [11] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@5b70802a
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@27703a4
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 :
                            {
                                BytecodeNode.SLReadProperty_q_ReadSLObject0_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH;
                                continue loop;
                            }
                            // c.SLUnbox.q.FromBoolean
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                            //     [ 1] SpecializationData [id = FromForeign0, method = public static java.lang.Object fromForeign(java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(interop.accepts(value))]], signature = [java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = interop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@717f534
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@1acad63c
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN :
                            {
                                BytecodeNode.SLUnbox_q_FromBoolean_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // c.SLToBoolean.q.Boolean
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@45e4649f
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN :
                            {
                                BytecodeNode.SLToBoolean_q_Boolean_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // c.SLLessOrEqual.q.LessOrEqual0
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7f3435a4
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                            {
                                BytecodeNode.SLLessOrEqual_q_LessOrEqual0_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH;
                                continue loop;
                            }
                            // c.SLInvoke.q.Direct
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Direct, method = protected static java.lang.Object doDirect(com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[], com.oracle.truffle.api.Assumption, com.oracle.truffle.api.RootCallTarget, com.oracle.truffle.api.nodes.DirectCallNode) , guards = [Guard[(function.getCallTarget() == cachedTarget)]], signature = [com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[]]]
                            //     [ 1] CacheExpression [sourceParameter = callNode]
                            //     [ 2] CacheExpression [sourceParameter = library]
                            //     [ 3] CacheExpression [sourceParameter = node]
                            //   Simple Pops:
                            //     [ 0] arg0
                            //   Variadic
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4732d123
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@b8584cf
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_INVOKE_Q_DIRECT :
                            {
                                int numVariadics = $bc[$bci + C_SL_INVOKE_Q_DIRECT_VARIADIC_OFFSET + 0];
                                Object input_0 = $frame.getObject($sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = $frame.getObject($sp - numVariadics + varIndex);
                                }
                                Object result = BytecodeNode.SLInvoke_q_Direct_execute_($frame, $this, $bc, $bci, $sp, $consts, $children, input_0, input_1);
                                $sp = $sp - 1 - numVariadics + 1;
                                $frame.setObject($sp - 1, result);
                                $bci = $bci + C_SL_INVOKE_Q_DIRECT_LENGTH;
                                continue loop;
                            }
                            // c.SLFunctionLiteral.q.Perform
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = result]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@6da3d9bc
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM :
                            {
                                BytecodeNode.SLFunctionLiteral_q_Perform_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH;
                                continue loop;
                            }
                            // c.SLWriteProperty.q.WriteSLObject0
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = WriteArray0, method = public static java.lang.Object writeArray(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = WriteSLObject0, method = public static java.lang.Object writeSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 6] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 7] SpecializationData [id = WriteObject0, method = public static java.lang.Object writeObject(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objectLibrary.accepts(receiver))], Guard[(!(SLWritePropertyNode.isSLObject(receiver)))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 8] CacheExpression [sourceParameter = node]
                            //     [ 9] CacheExpression [sourceParameter = objectLibrary]
                            //     [10] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //     [ 2] arg2
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@33e448b9
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@620e3c2f
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 :
                            {
                                BytecodeNode.SLWriteProperty_q_WriteSLObject0_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 3 + 1;
                                $bci = $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH;
                                continue loop;
                            }
                            // c.SLLessThan.q.LessThan0
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@458a6f5d
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 :
                            {
                                BytecodeNode.SLLessThan_q_LessThan0_execute_($frame, $this, $bc, $bci, $sp, $consts, $children);
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH;
                                continue loop;
                            }
                            default :
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw CompilerDirectives.shouldNotReachHere("unknown opcode encountered: " + curOpcode + "");
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
            }

            @Override
            void prepareForAOT(OperationNodeImpl $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root) {
                int $bci = 0;
                while ($bci < $bc.length) {
                    switch (unsafeFromBytecode($bc, $bci)) {
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
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        {
                            $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            $bc[$bci] = (short) (INSTR_LOAD_CONSTANT_OBJECT);
                            $bci = $bci + LOAD_CONSTANT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            $bc[$bci] = (short) (INSTR_LOAD_CONSTANT_OBJECT);
                            $bci = $bci + LOAD_CONSTANT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        {
                            $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_LONG :
                        {
                            $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        {
                            $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        {
                            $bci = $bci + STORE_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_LONG :
                        {
                            $bci = $bci + STORE_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        {
                            $bci = $bci + STORE_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_UNINIT :
                        {
                            $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_OBJECT :
                        {
                            $bci = $bci + LOAD_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_LONG :
                        {
                            $bci = $bci + LOAD_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        {
                            $bci = $bci + LOAD_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_UNINIT :
                        {
                            $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            $bci = $bci + RETURN_LENGTH;
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
                        case INSTR_C_SL_UNBOX_Q_FROM_LONG :
                        {
                            $bci = $bci + C_SL_UNBOX_Q_FROM_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD_Q_ADD_LONG :
                        {
                            $bci = $bci + C_SL_ADD_Q_ADD_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 :
                        {
                            $bci = $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN :
                        {
                            $bci = $bci + C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN :
                        {
                            $bci = $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        {
                            $bci = $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE_Q_DIRECT :
                        {
                            $bci = $bci + C_SL_INVOKE_Q_DIRECT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM :
                        {
                            $bci = $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 :
                        {
                            $bci = $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 :
                        {
                            $bci = $bci + C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH;
                            break;
                        }
                    }
                }
            }

            @Override
            String dump(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts) {
                int $bci = 0;
                StringBuilder sb = new StringBuilder();
                while ($bci < $bc.length) {
                    sb.append(String.format(" [%04x]", $bci));
                    switch (unsafeFromBytecode($bc, $bci)) {
                        default :
                        {
                            sb.append(String.format(" unknown 0x%02x", $bc[$bci++]));
                            break;
                        }
                        case INSTR_POP :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("pop                           ");
                            $bci = $bci + POP_LENGTH;
                            break;
                        }
                        case INSTR_BRANCH :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("branch                        ");
                            sb.append(String.format(" branch(%04x)", $bc[$bci + BRANCH_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + BRANCH_LENGTH;
                            break;
                        }
                        case INSTR_BRANCH_FALSE :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("branch.false                  ");
                            sb.append(String.format(" branch(%04x)", $bc[$bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + BRANCH_FALSE_LENGTH;
                            break;
                        }
                        case INSTR_THROW :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("throw                         ");
                            sb.append(String.format(" local(%s)", $bc[$bci + THROW_LOCALS_OFFSET + 0]));
                            $bci = $bci + THROW_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.object          ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_OBJECT_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.long            ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_LONG_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.boolean         ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.object          ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_OBJECT_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.long            ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_LONG_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.boolean         ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_BOOLEAN_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.object            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_OBJECT_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_OBJECT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.long              ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_LONG_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.boolean           ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_BOOLEAN_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_UNINIT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.uninit            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_UNINIT_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_UNINIT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.object             ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_OBJECT_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.long               ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_LONG_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.boolean            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_BOOLEAN_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_UNINIT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.uninit             ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_UNINIT_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("return                        ");
                            $bci = $bci + RETURN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLAdd                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_ADD_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_DIV :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLDiv                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_DIV_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_DIV_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_EQUAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLEqual                     ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_EQUAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessOrEqual               ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessThan                  ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_THAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LOGICAL_NOT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLogicalNot                ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_MUL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLMul                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_MUL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_MUL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLReadProperty              ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 2])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_SUB :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLSub                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_SUB_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_SUB_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLWriteProperty             ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff)));
                            $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLUnbox                     ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLFunctionLiteral           ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLToBoolean                 ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLInvoke                    ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" var(%s)", $bc[$bci + C_SL_INVOKE_VARIADIC_OFFSET + 0]));
                            $bci = $bci + C_SL_INVOKE_LENGTH;
                            break;
                        }
                        case INSTR_SC_SL_AND :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("sc.SLAnd                      ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + SC_SL_AND_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + SC_SL_AND_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" branch(%04x)", $bc[$bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + SC_SL_AND_LENGTH;
                            break;
                        }
                        case INSTR_SC_SL_OR :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("sc.SLOr                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + SC_SL_OR_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + SC_SL_OR_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" branch(%04x)", $bc[$bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + SC_SL_OR_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLUnbox.q.FromLong          ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_Q_FROM_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_Q_FROM_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD_Q_ADD_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLAdd.q.AddLong             ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_Q_ADD_LONG_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_ADD_Q_ADD_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_ADD_Q_ADD_LONG_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_ADD_Q_ADD_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLReadProperty.q.ReadSLObject0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 2])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLUnbox.q.FromBoolean       ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_Q_FROM_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLToBoolean.q.Boolean       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessOrEqual.q.LessOrEqual0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE_Q_DIRECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLInvoke.q.Direct           ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_Q_DIRECT_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" var(%s)", $bc[$bci + C_SL_INVOKE_Q_DIRECT_VARIADIC_OFFSET + 0]));
                            $bci = $bci + C_SL_INVOKE_Q_DIRECT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLFunctionLiteral.q.Perform ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLWriteProperty.q.WriteSLObject0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 1] & 0xff)));
                            $bci = $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessThan.q.LessThan0      ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_Q_LESS_THAN0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH;
                            break;
                        }
                    }
                    sb.append("\n");
                }
                for (int i = 0; i < $handlers.length; i++) {
                    sb.append($handlers[i] + "\n");
                }
                return sb.toString();
            }

            private static boolean SLAdd_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static void SLAdd_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */ && ((state_0 & 0b11110) != 0  /* is-not addLong(long, long) && add(SLBigNumber, SLBigNumber) && add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) && typeError(Object, Object, Node, int) */)) {
                    SLAdd_SLAdd_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLAdd_SLAdd_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLAdd_SLAdd_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */;
                try {
                    long value = SLAddNode.addLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                    return;
                }
            }

            private static void SLAdd_SLAdd_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */ && $child0Value_ instanceof Long) {
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
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            Lock lock = $this.getLockAccessor();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
                            return;
                        }
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 add(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b1100000) >>> 5 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000000) >>> 7 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000000) >>> 7 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value__, $child1Value__));
                        return;
                    }
                }
                if ((state_0 & 0b11000) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) || typeError(Object, Object, Node, int) */) {
                    if ((state_0 & 0b1000) != 0 /* is-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */) {
                        SLAdd_Add1Data s2_ = ((SLAdd_Add1Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_ADD_CHILDREN_OFFSET) + 0) + 0]);
                        if (s2_ != null) {
                            if ((SLAddNode.isString($child0Value_, $child1Value_))) {
                                $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_, s2_.toTruffleStringNodeLeft_, s2_.toTruffleStringNodeRight_, s2_.concatNode_));
                                return;
                            }
                        }
                    }
                    if ((state_0 & 0b10000) != 0 /* is-state_0 typeError(Object, Object, Node, int) */) {
                        {
                            Node fallback_node__ = ($this);
                            int fallback_bci__ = ($bci);
                            if (SLAdd_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_)) {
                                $frame.setObject($sp - 2, SLAddNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLAdd_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1];
                    if ((exclude) == 0 /* is-not-exclude addLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 addLong(long, long) */);
                            if ((state_0 & 0b11110) == 0b10/* is-exact-state_0 addLong(long, long) */) {
                                $bc[$bci] = (short) (INSTR_C_SL_ADD_Q_ADD_LONG);
                            } else {
                                $bc[$bci] = (short) (INSTR_C_SL_ADD);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b11100) == 0 /* only-active addLong(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                lock.lock();
                                try {
                                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                                    $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
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
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude addLong(long, long) */);
                                state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 5) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 7) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 add(SLBigNumber, SLBigNumber) */);
                                $bc[$bci] = (short) (INSTR_C_SL_ADD);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_));
                                return;
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
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */);
                        $bc[$bci] = (short) (INSTR_C_SL_ADD);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value, $child1Value, s2_.toTruffleStringNodeLeft_, s2_.toTruffleStringNodeRight_, s2_.concatNode_));
                        return;
                    }
                    {
                        int fallback_bci__ = 0;
                        Node fallback_node__ = null;
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_ADD_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        $bc[$bci] = (short) (INSTR_C_SL_ADD);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            private static boolean SLDiv_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLDiv_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not divLong(long, long) && div(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLDiv_SLDiv_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLDiv_SLDiv_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLDiv_SLDiv_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                    return;
                }
            }

            private static void SLDiv_SLDiv_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            Lock lock = $this.getLockAccessor();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLDiv_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLDivNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLDiv_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1];
                    if ((exclude) == 0 /* is-not-exclude divLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 divLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active divLong(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                lock.lock();
                                try {
                                    $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude divLong(long, long) */);
                                    $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLDiv_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
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
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude divLong(long, long) */);
                                state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 divLong(long, long) */);
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 div(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_DIV_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLEqual_generic1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
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

            private static void SLEqual_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1111111100) == 0 /* only-active doLong(long, long) */ && ((state_0 & 0b1111111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqual_SLEqual_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else if ((state_0 & 0b1111110110) == 0 /* only-active doBoolean(boolean, boolean) */ && ((state_0 & 0b1111111110) != 0  /* is-not doLong(long, long) && doBigNumber(SLBigNumber, SLBigNumber) && doBoolean(boolean, boolean) && doString(String, String) && doTruffleString(TruffleString, TruffleString, EqualNode) && doNull(SLNull, SLNull) && doFunction(SLFunction, Object) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) && doGeneric(Object, Object, InteropLibrary, InteropLibrary) */)) {
                    SLEqual_SLEqual_execute__boolean_boolean1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLEqual_SLEqual_execute__generic2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLEqual_SLEqual_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10) != 0 /* is-state_0 doLong(long, long) */;
                boolean value = SLEqualNode.doLong($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private static void SLEqual_SLEqual_execute__boolean_boolean1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                boolean $child1Value_;
                try {
                    $child1Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                assert (state_0 & 0b1000) != 0 /* is-state_0 doBoolean(boolean, boolean) */;
                boolean value = SLEqualNode.doBoolean($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            @ExplodeLoop
            private static void SLEqual_SLEqual_execute__generic2_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 doLong(long, long) */ && $child0Value_ instanceof Long) {
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
                if ((state_0 & 0b100) != 0 /* is-state_0 doBigNumber(SLBigNumber, SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000000000) >>> 10 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000000000) >>> 10 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    if (SLTypesGen.isImplicitSLBigNumber((state_0 & 0b11000000000000) >>> 12 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_)) {
                        SLBigNumber $child1Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b11000000000000) >>> 12 /* extract-implicit-state_0 1:SLBigNumber */, $child1Value_);
                        boolean value = SLEqualNode.doBigNumber($child0Value__, $child1Value__);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 doBoolean(boolean, boolean) */ && $child0Value_ instanceof Boolean) {
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
                if ((state_0 & 0b10000) != 0 /* is-state_0 doString(String, String) */ && $child0Value_ instanceof String) {
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
                if ((state_0 & 0b100000) != 0 /* is-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */ && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    if ($child1Value_ instanceof TruffleString) {
                        TruffleString $child1Value__ = (TruffleString) $child1Value_;
                        EqualNode truffleString_equalNode__ = ((EqualNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 0]);
                        if (truffleString_equalNode__ != null) {
                            boolean value = SLEqualNode.doTruffleString($child0Value__, $child1Value__, truffleString_equalNode__);
                            if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                                $frame.setObject($sp - 2, value);
                            } else {
                                $frame.setBoolean($sp - 2, value);
                            }
                            return;
                        }
                    }
                }
                if ((state_0 & 0b1000000) != 0 /* is-state_0 doNull(SLNull, SLNull) */ && SLTypes.isSLNull($child0Value_)) {
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
                if ((state_0 & 0b1110000000) != 0 /* is-state_0 doFunction(SLFunction, Object) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                    if ((state_0 & 0b10000000) != 0 /* is-state_0 doFunction(SLFunction, Object) */ && $child0Value_ instanceof SLFunction) {
                        SLFunction $child0Value__ = (SLFunction) $child0Value_;
                        boolean value = SLEqualNode.doFunction($child0Value__, $child1Value_);
                        if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                    if ((state_0 & 0b1100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) || doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                            SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1]);
                            while (s7_ != null) {
                                if ((s7_.leftInterop_.accepts($child0Value_)) && (s7_.rightInterop_.accepts($child1Value_))) {
                                    boolean value = SLEqualNode.doGeneric($child0Value_, $child1Value_, s7_.leftInterop_, s7_.rightInterop_);
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
                        if ((state_0 & 0b1000000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
                            $frame.setObject($sp - 2, SLEqual_generic1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLEqual_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 1];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1111111100) == 0 /* only-active doLong(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 10) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 12) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 doBigNumber(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 doBoolean(boolean, boolean) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1111110110) == 0 /* only-active doBoolean(boolean, boolean) */) {
                                type0 = 5 /* BOOLEAN */;
                                type1 = 5 /* BOOLEAN */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 doString(String, String) */);
                            int type0;
                            int type1;
                            type0 = 0 /* OBJECT */;
                            type1 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                            $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 0] = $this.insertAccessor((EqualNode.create()));
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 doTruffleString(TruffleString, TruffleString, EqualNode) */);
                            int type0;
                            int type1;
                            type0 = 0 /* OBJECT */;
                            type1 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doTruffleString($child0Value_, $child1Value_, ((EqualNode) $children[childArrayOffset_ + 0]));
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
                            $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 doNull(SLNull, SLNull) */);
                            int type0;
                            int type1;
                            type0 = 0 /* OBJECT */;
                            type1 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000000 /* add-state_0 doFunction(SLFunction, Object) */);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        SLEqual_Generic0Data s7_ = ((SLEqual_Generic0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1]);
                        if ((state_0 & 0b100000000) != 0 /* is-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */) {
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
                                s7_ = $this.insertAccessor(new SLEqual_Generic0Data(((SLEqual_Generic0Data) $children[childArrayOffset_ + 1])));
                                s7_.leftInterop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                s7_.rightInterop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                VarHandle.storeStoreFence();
                                $children[childArrayOffset_ + 1] = s7_;
                                $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000000 /* add-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                            }
                        }
                        if (s7_ != null) {
                            lock.unlock();
                            hasLock = false;
                            boolean value = SLEqualNode.doGeneric($child0Value, $child1Value, s7_.leftInterop_, s7_.rightInterop_);
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
                            Node prev_ = encapsulating_.set($this);
                            try {
                                generic1_leftInterop__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                generic1_rightInterop__ = (INTEROP_LIBRARY_.getUncached($child1Value));
                                $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_EQUAL_CHILDREN_OFFSET) + 0) + 1] = null;
                                state_0 = (short) (state_0 & 0xfffffeff /* remove-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                $bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000000 /* add-state_0 doGeneric(Object, Object, InteropLibrary, InteropLibrary) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            private static boolean SLLessOrEqual_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLLessOrEqual_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessOrEqual(long, long) && lessOrEqual(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessOrEqual_SLLessOrEqual_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLLessOrEqual_SLLessOrEqual_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLLessOrEqual_SLLessOrEqual_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
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

            private static void SLLessOrEqual_SLLessOrEqual_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLLessOrEqual_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLLessOrEqual_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 lessOrEqual(long, long) */);
                            if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 lessOrEqual(long, long) */) {
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0);
                            } else {
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessOrEqual(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 lessOrEqual(SLBigNumber, SLBigNumber) */);
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            private static boolean SLLessThan_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLLessThan_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not lessThan(long, long) && lessThan(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLLessThan_SLLessThan_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLLessThan_SLLessThan_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLLessThan_SLLessThan_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
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

            private static void SLLessThan_SLLessThan_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLLessThan_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLLessThan_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0];
                    if ($child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 lessThan(long, long) */);
                            if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 lessThan(long, long) */) {
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN_Q_LESS_THAN0);
                            } else {
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN);
                            }
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active lessThan(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 lessThan(SLBigNumber, SLBigNumber) */);
                                $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            private static boolean SLLogicalNot_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static void SLLogicalNot_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && typeError(Object, Node, int) */)) {
                    SLLogicalNot_SLLogicalNot_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLLogicalNot_SLLogicalNot_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLLogicalNot_SLLogicalNot_execute__boolean0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
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

            private static void SLLogicalNot_SLLogicalNot_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLLogicalNot_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
                            $frame.setObject($sp - 1, SLLogicalNotNode.typeError($child0Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLLogicalNot_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
                return;
            }

            private static void SLLogicalNot_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */) {
                            type0 = 5 /* BOOLEAN */;
                        } else {
                            type0 = 0 /* OBJECT */;
                        }
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 typeError(Object, Node, int) */);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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

            private static boolean SLMul_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLMul_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not mulLong(long, long) && mul(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLMul_SLMul_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLMul_SLMul_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLMul_SLMul_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                    return;
                }
            }

            private static void SLMul_SLMul_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            Lock lock = $this.getLockAccessor();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLMul_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLMulNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLMul_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1];
                    if ((exclude) == 0 /* is-not-exclude mulLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 mulLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active mulLong(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                lock.lock();
                                try {
                                    $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude mulLong(long, long) */);
                                    $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLMul_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
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
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude mulLong(long, long) */);
                                state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 mulLong(long, long) */);
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 mul(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_MUL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLReadProperty_readArray1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
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
            private static Object SLReadProperty_readSLObject1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value_, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                {
                    Node readSLObject1_node__ = ($this);
                    int readSLObject1_bci__ = ($bci);
                    DynamicObjectLibrary readSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                    SLToTruffleStringNode readSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 7]);
                    if (readSLObject1_toTruffleStringNode__ != null) {
                        return SLReadPropertyNode.readSLObject($child0Value_, $child1Value, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, readSLObject1_toTruffleStringNode__);
                    }
                    throw BoundaryCallFailedException.INSTANCE;
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLReadProperty_readObject1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                {
                    Node readObject1_node__ = ($this);
                    int readObject1_bci__ = ($bci);
                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                    SLToMemberNode readObject1_asMember__ = ((SLToMemberNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 11]);
                    if (readObject1_asMember__ != null) {
                        return SLReadPropertyNode.readObject($child0Value, $child1Value, readObject1_node__, readObject1_bci__, readObject1_objects__, readObject1_asMember__);
                    }
                    throw BoundaryCallFailedException.INSTANCE;
                }
            }

            @ExplodeLoop
            private static void SLReadProperty_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0];
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) || readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 0]);
                            while (s0_ != null) {
                                if ((s0_.arrays_.accepts($child0Value_)) && (s0_.numbers_.accepts($child1Value_)) && (s0_.arrays_.hasArrayElements($child0Value_))) {
                                    Node node__ = ($this);
                                    int bci__ = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value_, $child1Value_, node__, bci__, s0_.arrays_, s0_.numbers_));
                                    return;
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set($this);
                            try {
                                {
                                    InteropLibrary readArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 2, SLReadProperty_readArray1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_));
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
                            SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 4]);
                            while (s2_ != null) {
                                if ((s2_.objectLibrary_.accepts($child0Value__))) {
                                    Node node__1 = ($this);
                                    int bci__1 = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, node__1, bci__1, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                                    return;
                                }
                                s2_ = s2_.next_;
                            }
                        }
                        if ((state_0 & 0b10000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            $frame.setObject($sp - 2, SLReadProperty_readSLObject1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value__, $child1Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) || readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 8]);
                            while (s4_ != null) {
                                if ((s4_.objects_.accepts($child0Value_)) && (!(SLReadPropertyNode.isSLObject($child0Value_))) && (s4_.objects_.hasMembers($child0Value_))) {
                                    Node node__2 = ($this);
                                    int bci__2 = ($bci);
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value_, $child1Value_, node__2, bci__2, s4_.objects_, s4_.asMember_));
                                    return;
                                }
                                s4_ = s4_.next_;
                            }
                        }
                        if ((state_0 & 0b1000000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set($this);
                            try {
                                if ((!(SLReadPropertyNode.isSLObject($child0Value_)))) {
                                    InteropLibrary readObject1_objects__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((readObject1_objects__.hasMembers($child0Value_))) {
                                        $frame.setObject($sp - 2, SLReadProperty_readObject1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_));
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
                SLReadProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLReadProperty_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 1];
                    {
                        int bci__ = 0;
                        Node node__ = null;
                        if (((exclude & 0b1)) == 0 /* is-not-exclude readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            int count0_ = 0;
                            SLReadProperty_ReadArray0Data s0_ = ((SLReadProperty_ReadArray0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 0]);
                            if ((state_0 & 0b10) != 0 /* is-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
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
                                        s0_ = $this.insertAccessor(new SLReadProperty_ReadArray0Data(((SLReadProperty_ReadArray0Data) $children[childArrayOffset_ + 0])));
                                        node__ = ($this);
                                        bci__ = ($bci);
                                        s0_.arrays_ = s0_.insertAccessor(arrays__);
                                        s0_.numbers_ = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                        VarHandle.storeStoreFence();
                                        $children[childArrayOffset_ + 0] = s0_;
                                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value, $child1Value, node__, bci__, s0_.arrays_, s0_.numbers_));
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
                                        state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 readArray(Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 4]);
                                if ((state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
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
                                        s2_ = $this.insertAccessor(new SLReadProperty_ReadSLObject0Data(((SLReadProperty_ReadSLObject0Data) $children[childArrayOffset_ + 4])));
                                        node__1 = ($this);
                                        bci__1 = ($bci);
                                        s2_.objectLibrary_ = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                        s2_.toTruffleStringNode_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        $children[childArrayOffset_ + 4] = s2_;
                                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                        if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                            $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0);
                                        } else {
                                            $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                                        }
                                        int type0;
                                        int type1;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    }
                                }
                                if (s2_ != null) {
                                    lock.unlock();
                                    hasLock = false;
                                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, node__1, bci__1, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                                    return;
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
                            state_0 = (short) (state_0 & 0xfffffff7 /* remove-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                            int type0;
                            int type1;
                            type0 = 0 /* OBJECT */;
                            type1 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, readSLObject1_node__, readSLObject1_bci__, readSLObject1_objectLibrary__, ((SLToTruffleStringNode) $children[childArrayOffset_ + 7])));
                            return;
                        }
                    }
                    {
                        int bci__2 = 0;
                        Node node__2 = null;
                        if (((exclude & 0b100)) == 0 /* is-not-exclude readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            int count4_ = 0;
                            SLReadProperty_ReadObject0Data s4_ = ((SLReadProperty_ReadObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CHILDREN_OFFSET) + 0) + 8]);
                            if ((state_0 & 0b100000) != 0 /* is-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
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
                                        s4_ = $this.insertAccessor(new SLReadProperty_ReadObject0Data(((SLReadProperty_ReadObject0Data) $children[childArrayOffset_ + 8])));
                                        node__2 = ($this);
                                        bci__2 = ($bci);
                                        s4_.objects_ = s4_.insertAccessor(objects__);
                                        s4_.asMember_ = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                        VarHandle.storeStoreFence();
                                        $children[childArrayOffset_ + 8] = s4_;
                                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    }
                                }
                            }
                            if (s4_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, node__2, bci__2, s4_.objects_, s4_.asMember_));
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
                                        state_0 = (short) (state_0 & 0xffffffdf /* remove-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        $bc[$bci + C_SL_READ_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 readObject(Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                                        int type0;
                                        int type1;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                        lock.unlock();
                                        hasLock = false;
                                        $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, readObject1_node__, readObject1_bci__, readObject1_objects__, ((SLToMemberNode) $children[childArrayOffset_ + 11])));
                                        return;
                                    }
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    throw new UnsupportedSpecializationException($this, new Node[] {null, null}, $frame.getValue($sp - 2), $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private static boolean SLSub_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLSub_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */ && ((state_0 & 0b1110) != 0  /* is-not subLong(long, long) && sub(SLBigNumber, SLBigNumber) && typeError(Object, Object, Node, int) */)) {
                    SLSub_SLSub_execute__long_long0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLSub_SLSub_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLSub_SLSub_execute__long_long0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
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
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                    return;
                }
            }

            private static void SLSub_SLSub_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            Lock lock = $this.getLockAccessor();
                            lock.lock();
                            try {
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                            } finally {
                                lock.unlock();
                            }
                            SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value__, $child1Value__);
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLSub_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_)) {
                            $frame.setObject($sp - 2, SLSubNode.typeError($child0Value_, $child1Value_, fallback_node__, fallback_bci__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLSub_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1];
                    if ((exclude) == 0 /* is-not-exclude subLong(long, long) */ && $child0Value instanceof Long) {
                        long $child0Value_ = (long) $child0Value;
                        if ($child1Value instanceof Long) {
                            long $child1Value_ = (long) $child1Value;
                            $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 subLong(long, long) */);
                            int type0;
                            int type1;
                            if ((state_0 & 0b1100) == 0 /* only-active subLong(long, long) */) {
                                type0 = 1 /* LONG */;
                                type1 = 1 /* LONG */;
                            } else {
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                            }
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                lock.lock();
                                try {
                                    $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude subLong(long, long) */);
                                    $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                                } finally {
                                    lock.unlock();
                                }
                                SLSub_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
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
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude subLong(long, long) */);
                                state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 subLong(long, long) */);
                                state_0 = (short) (state_0 | (sLBigNumberCast0 << 4) /* set-implicit-state_0 0:SLBigNumber */);
                                state_0 = (short) (state_0 | (sLBigNumberCast1 << 6) /* set-implicit-state_0 1:SLBigNumber */);
                                $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 sub(SLBigNumber, SLBigNumber) */);
                                int type0;
                                int type1;
                                type0 = 0 /* OBJECT */;
                                type1 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_SUB_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 typeError(Object, Object, Node, int) */);
                        int type0;
                        int type1;
                        type0 = 0 /* OBJECT */;
                        type1 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
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

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLWriteProperty_writeArray1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value, Object $child2Value) {
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
            private static Object SLWriteProperty_writeSLObject1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, SLObject $child0Value_, Object $child1Value, Object $child2Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                {
                    DynamicObjectLibrary writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                    SLToTruffleStringNode writeSLObject1_toTruffleStringNode__ = ((SLToTruffleStringNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 6]);
                    if (writeSLObject1_toTruffleStringNode__ != null) {
                        return SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, writeSLObject1_objectLibrary__, writeSLObject1_toTruffleStringNode__);
                    }
                    throw BoundaryCallFailedException.INSTANCE;
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLWriteProperty_writeObject1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value, Object $child2Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                Node prev_ = encapsulating_.set($this);
                try {
                    {
                        Node writeObject1_node__ = ($this);
                        int writeObject1_bci__ = ($bci);
                        InteropLibrary writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                        SLToMemberNode writeObject1_asMember__ = ((SLToMemberNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 10]);
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
            private static void SLWriteProperty_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0];
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 3);
                Object $child1Value_ = expectObject($frame, $sp - 2);
                Object $child2Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b1111110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) || writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                        if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 0]);
                            while (s0_ != null) {
                                if ((s0_.arrays_.accepts($child0Value_)) && (s0_.numbers_.accepts($child1Value_)) && (s0_.arrays_.hasArrayElements($child0Value_))) {
                                    Node node__ = ($this);
                                    int bci__ = ($bci);
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value_, $child1Value_, $child2Value_, node__, bci__, s0_.arrays_, s0_.numbers_));
                                    return;
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            EncapsulatingNodeReference encapsulating_ = EncapsulatingNodeReference.getCurrent();
                            Node prev_ = encapsulating_.set($this);
                            try {
                                {
                                    InteropLibrary writeArray1_arrays__ = (INTEROP_LIBRARY_.getUncached());
                                    if ((writeArray1_arrays__.hasArrayElements($child0Value_))) {
                                        $frame.setObject($sp - 3, SLWriteProperty_writeArray1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_, $child2Value_));
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
                            SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 4]);
                            while (s2_ != null) {
                                if ((s2_.objectLibrary_.accepts($child0Value__))) {
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                                    return;
                                }
                                s2_ = s2_.next_;
                            }
                        }
                        if ((state_0 & 0b10000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                            $frame.setObject($sp - 3, SLWriteProperty_writeSLObject1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value__, $child1Value_, $child2Value_));
                            return;
                        }
                    }
                    if ((state_0 & 0b1100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) || writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                        if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 7]);
                            while (s4_ != null) {
                                if ((s4_.objectLibrary_.accepts($child0Value_)) && (!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                                    Node node__1 = ($this);
                                    int bci__1 = ($bci);
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value_, $child1Value_, $child2Value_, node__1, bci__1, s4_.objectLibrary_, s4_.asMember_));
                                    return;
                                }
                                s4_ = s4_.next_;
                            }
                        }
                        if ((state_0 & 0b1000000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            if ((!(SLWritePropertyNode.isSLObject($child0Value_)))) {
                                $frame.setObject($sp - 3, SLWriteProperty_writeObject1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_, $child1Value_, $child2Value_));
                                return;
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLWriteProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            private static void SLWriteProperty_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value, Object $child2Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1];
                    {
                        int bci__ = 0;
                        Node node__ = null;
                        if (((exclude & 0b1)) == 0 /* is-not-exclude writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
                            int count0_ = 0;
                            SLWriteProperty_WriteArray0Data s0_ = ((SLWriteProperty_WriteArray0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 0]);
                            if ((state_0 & 0b10) != 0 /* is-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */) {
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
                                        s0_ = $this.insertAccessor(new SLWriteProperty_WriteArray0Data(((SLWriteProperty_WriteArray0Data) $children[childArrayOffset_ + 0])));
                                        node__ = ($this);
                                        bci__ = ($bci);
                                        s0_.arrays_ = s0_.insertAccessor(arrays__);
                                        s0_.numbers_ = s0_.insertAccessor((INTEROP_LIBRARY_.create($child1Value)));
                                        VarHandle.storeStoreFence();
                                        $children[childArrayOffset_ + 0] = s0_;
                                        $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                                        int type0;
                                        int type1;
                                        int type2;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        type2 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, node__, bci__, s0_.arrays_, s0_.numbers_));
                                return;
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
                                        state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 writeArray(Object, Object, Object, Node, int, InteropLibrary, InteropLibrary) */);
                                        $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                                        int type0;
                                        int type1;
                                        int type2;
                                        type0 = 0 /* OBJECT */;
                                        type1 = 0 /* OBJECT */;
                                        type2 = 0 /* OBJECT */;
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                        doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                                        lock.unlock();
                                        hasLock = false;
                                        $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, writeArray1_node__, writeArray1_bci__, writeArray1_arrays__, writeArray1_numbers__));
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
                            SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 4]);
                            if ((state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
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
                                    s2_ = $this.insertAccessor(new SLWriteProperty_WriteSLObject0Data(((SLWriteProperty_WriteSLObject0Data) $children[childArrayOffset_ + 4])));
                                    s2_.objectLibrary_ = s2_.insertAccessor((DYNAMIC_OBJECT_LIBRARY_.create($child0Value_)));
                                    s2_.toTruffleStringNode_ = s2_.insertAccessor((SLToTruffleStringNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 4] = s2_;
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                                    if ((state_0 & 0b1111110) == 0b1000/* is-exact-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */) {
                                        $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0);
                                    } else {
                                        $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                                    }
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = 0 /* OBJECT */;
                                    type1 = 0 /* OBJECT */;
                                    type2 = 0 /* OBJECT */;
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                    doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                                }
                            }
                            if (s2_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                                return;
                            }
                        }
                        {
                            DynamicObjectLibrary writeSLObject1_objectLibrary__ = null;
                            writeSLObject1_objectLibrary__ = (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_));
                            $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 6] = $this.insertAccessor((SLToTruffleStringNodeGen.create()));
                            $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b10 /* add-exclude writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            $children[childArrayOffset_ + 4] = null;
                            state_0 = (short) (state_0 & 0xfffffff7 /* remove-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */);
                            $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                            int type0;
                            int type1;
                            int type2;
                            type0 = 0 /* OBJECT */;
                            type1 = 0 /* OBJECT */;
                            type2 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, writeSLObject1_objectLibrary__, ((SLToTruffleStringNode) $children[childArrayOffset_ + 6])));
                            return;
                        }
                    }
                    {
                        int bci__1 = 0;
                        Node node__1 = null;
                        if (((exclude & 0b100)) == 0 /* is-not-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
                            int count4_ = 0;
                            SLWriteProperty_WriteObject0Data s4_ = ((SLWriteProperty_WriteObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 7]);
                            if ((state_0 & 0b100000) != 0 /* is-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */) {
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
                                    s4_ = $this.insertAccessor(new SLWriteProperty_WriteObject0Data(((SLWriteProperty_WriteObject0Data) $children[childArrayOffset_ + 7])));
                                    node__1 = ($this);
                                    bci__1 = ($bci);
                                    s4_.objectLibrary_ = s4_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                    s4_.asMember_ = s4_.insertAccessor((SLToMemberNodeGen.create()));
                                    VarHandle.storeStoreFence();
                                    $children[childArrayOffset_ + 7] = s4_;
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = 0 /* OBJECT */;
                                    type1 = 0 /* OBJECT */;
                                    type2 = 0 /* OBJECT */;
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                    doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                                }
                            }
                            if (s4_ != null) {
                                lock.unlock();
                                hasLock = false;
                                $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, node__1, bci__1, s4_.objectLibrary_, s4_.asMember_));
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
                            Node prev_ = encapsulating_.set($this);
                            try {
                                if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                                    writeObject1_node__ = ($this);
                                    writeObject1_bci__ = ($bci);
                                    writeObject1_objectLibrary__ = (INTEROP_LIBRARY_.getUncached($child0Value));
                                    $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CHILDREN_OFFSET) + 0) + 10] = $this.insertAccessor((SLToMemberNodeGen.create()));
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b100 /* add-exclude writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $children[childArrayOffset_ + 7] = null;
                                    state_0 = (short) (state_0 & 0xffffffdf /* remove-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $bc[$bci + C_SL_WRITE_PROPERTY_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 writeObject(Object, Object, Object, Node, int, InteropLibrary, SLToMemberNode) */);
                                    $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                                    int type0;
                                    int type1;
                                    int type2;
                                    type0 = 0 /* OBJECT */;
                                    type1 = 0 /* OBJECT */;
                                    type2 = 0 /* OBJECT */;
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                                    doSetResultBoxed($bc, $bci, (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff), type1);
                                    doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff), type2);
                                    lock.unlock();
                                    hasLock = false;
                                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, writeObject1_node__, writeObject1_bci__, writeObject1_objectLibrary__, ((SLToMemberNode) $children[childArrayOffset_ + 10])));
                                    return;
                                }
                            } finally {
                                encapsulating_.set(prev_);
                            }
                        }
                    }
                    throw new UnsupportedSpecializationException($this, new Node[] {null, null, null}, $frame.getValue($sp - 3), $frame.getValue($sp - 2), $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            @SuppressWarnings("static-method")
            @TruffleBoundary
            private static Object SLUnbox_fromForeign1Boundary_(OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
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

            private static void SLUnbox_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b1111110110) == 0 /* only-active fromBoolean(boolean) */ && ((state_0 & 0b1111111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) && fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnbox_SLUnbox_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else if ((state_0 & 0b1111101110) == 0 /* only-active fromLong(long) */ && ((state_0 & 0b1111111110) != 0  /* is-not fromString(String, FromJavaStringNode) && fromTruffleString(TruffleString) && fromBoolean(boolean) && fromLong(long) && fromBigNumber(SLBigNumber) && fromFunction(SLFunction) && fromFunction(SLNull) && fromForeign(Object, InteropLibrary) && fromForeign(Object, InteropLibrary) */)) {
                    SLUnbox_SLUnbox_execute__long1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLUnbox_SLUnbox_execute__generic2_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLUnbox_SLUnbox_execute__boolean0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                    return;
                }
                assert (state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */;
                boolean value = SLUnboxNode.fromBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private static void SLUnbox_SLUnbox_execute__long1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                    return;
                }
                assert (state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */;
                long value = SLUnboxNode.fromLong($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setLong($sp - 1, value);
                }
                return;
            }

            @ExplodeLoop
            private static void SLUnbox_SLUnbox_execute__generic2_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 1);
                if ((state_0 & 0b10) != 0 /* is-state_0 fromString(String, FromJavaStringNode) */ && $child0Value_ instanceof String) {
                    String $child0Value__ = (String) $child0Value_;
                    FromJavaStringNode fromString_fromJavaStringNode__ = ((FromJavaStringNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 0]);
                    if (fromString_fromJavaStringNode__ != null) {
                        $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value__, fromString_fromJavaStringNode__));
                        return;
                    }
                }
                if ((state_0 & 0b100) != 0 /* is-state_0 fromTruffleString(TruffleString) */ && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value__));
                    return;
                }
                if ((state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */ && $child0Value_ instanceof Boolean) {
                    boolean $child0Value__ = (boolean) $child0Value_;
                    boolean value = SLUnboxNode.fromBoolean($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ((state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */ && $child0Value_ instanceof Long) {
                    long $child0Value__ = (long) $child0Value_;
                    long value = SLUnboxNode.fromLong($child0Value__);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setLong($sp - 1, value);
                    }
                    return;
                }
                if ((state_0 & 0b100000) != 0 /* is-state_0 fromBigNumber(SLBigNumber) */ && SLTypesGen.isImplicitSLBigNumber((state_0 & 0b110000000000) >>> 10 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_)) {
                    SLBigNumber $child0Value__ = SLTypesGen.asImplicitSLBigNumber((state_0 & 0b110000000000) >>> 10 /* extract-implicit-state_0 0:SLBigNumber */, $child0Value_);
                    $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value__));
                    return;
                }
                if ((state_0 & 0b1000000) != 0 /* is-state_0 fromFunction(SLFunction) */ && $child0Value_ instanceof SLFunction) {
                    SLFunction $child0Value__ = (SLFunction) $child0Value_;
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value__));
                    return;
                }
                if ((state_0 & 0b10000000) != 0 /* is-state_0 fromFunction(SLNull) */ && SLTypes.isSLNull($child0Value_)) {
                    SLNull $child0Value__ = SLTypes.asSLNull($child0Value_);
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value__));
                    return;
                }
                if ((state_0 & 0b1100000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) || fromForeign(Object, InteropLibrary) */) {
                    if ((state_0 & 0b100000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
                        SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 1]);
                        while (s7_ != null) {
                            if ((s7_.interop_.accepts($child0Value_))) {
                                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value_, s7_.interop_));
                                return;
                            }
                            s7_ = s7_.next_;
                        }
                    }
                    if ((state_0 & 0b1000000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
                        $frame.setObject($sp - 1, SLUnbox_fromForeign1Boundary_($this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_));
                        return;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
                return;
            }

            private static void SLUnbox_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 1];
                    if ($child0Value instanceof String) {
                        String $child0Value_ = (String) $child0Value;
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 0] = $this.insertAccessor((FromJavaStringNode.create()));
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 fromString(String, FromJavaStringNode) */);
                        $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value_, ((FromJavaStringNode) $children[childArrayOffset_ + 0])));
                        return;
                    }
                    if ($child0Value instanceof TruffleString) {
                        TruffleString $child0Value_ = (TruffleString) $child0Value;
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 fromTruffleString(TruffleString) */);
                        $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value_));
                        return;
                    }
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 fromBoolean(boolean) */);
                        if ((state_0 & 0b1111111110) == 0b1000/* is-exact-state_0 fromBoolean(boolean) */) {
                            $bc[$bci] = (short) (INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN);
                        } else {
                            $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        }
                        int type0;
                        if ((state_0 & 0b1111110110) == 0 /* only-active fromBoolean(boolean) */) {
                            type0 = 5 /* BOOLEAN */;
                        } else {
                            type0 = 0 /* OBJECT */;
                        }
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000 /* add-state_0 fromLong(long) */);
                        if ((state_0 & 0b1111111110) == 0b10000/* is-exact-state_0 fromLong(long) */) {
                            $bc[$bci] = (short) (INSTR_C_SL_UNBOX_Q_FROM_LONG);
                        } else {
                            $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        }
                        int type0;
                        if ((state_0 & 0b1111101110) == 0 /* only-active fromLong(long) */) {
                            type0 = 1 /* LONG */;
                        } else {
                            type0 = 0 /* OBJECT */;
                        }
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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
                            state_0 = (short) (state_0 | (sLBigNumberCast0 << 10) /* set-implicit-state_0 0:SLBigNumber */);
                            $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000 /* add-state_0 fromBigNumber(SLBigNumber) */);
                            $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                            int type0;
                            type0 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value_));
                            return;
                        }
                    }
                    if ($child0Value instanceof SLFunction) {
                        SLFunction $child0Value_ = (SLFunction) $child0Value;
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000 /* add-state_0 fromFunction(SLFunction) */);
                        $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                        return;
                    }
                    if (SLTypes.isSLNull($child0Value)) {
                        SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                        $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10000000 /* add-state_0 fromFunction(SLNull) */);
                        $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                        lock.unlock();
                        hasLock = false;
                        $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                        return;
                    }
                    if ((exclude) == 0 /* is-not-exclude fromForeign(Object, InteropLibrary) */) {
                        int count7_ = 0;
                        SLUnbox_FromForeign0Data s7_ = ((SLUnbox_FromForeign0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_UNBOX_CHILDREN_OFFSET) + 0) + 1]);
                        if ((state_0 & 0b100000000) != 0 /* is-state_0 fromForeign(Object, InteropLibrary) */) {
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
                                s7_ = $this.insertAccessor(new SLUnbox_FromForeign0Data(((SLUnbox_FromForeign0Data) $children[childArrayOffset_ + 1])));
                                s7_.interop_ = s7_.insertAccessor((INTEROP_LIBRARY_.create($child0Value)));
                                VarHandle.storeStoreFence();
                                $children[childArrayOffset_ + 1] = s7_;
                                $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100000000 /* add-state_0 fromForeign(Object, InteropLibrary) */);
                                $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                                int type0;
                                type0 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            }
                        }
                        if (s7_ != null) {
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, s7_.interop_));
                            return;
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
                                state_0 = (short) (state_0 & 0xfffffeff /* remove-state_0 fromForeign(Object, InteropLibrary) */);
                                $bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000000000 /* add-state_0 fromForeign(Object, InteropLibrary) */);
                                $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                                int type0;
                                type0 = 0 /* OBJECT */;
                                doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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

            private static void SLFunctionLiteral_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0];
                Object $child0Value_ = expectObject($frame, $sp - 1);
                int childArrayOffset_;
                int constArrayOffset_;
                if ((state_0 & 0b10) != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */ && $child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    {
                        Node node__ = ($this);
                        SLFunction result__ = ((SLFunction) $consts[(constArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0) + 0]);
                        if (result__ != null) {
                            $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value__, result__, node__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SLFunctionLiteral_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
                return;
            }

            private static void SLFunctionLiteral_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0];
                    {
                        Node node__ = null;
                        if ($child0Value instanceof TruffleString) {
                            TruffleString $child0Value_ = (TruffleString) $child0Value;
                            $consts[(constArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0) + 0] = (SLFunctionLiteralNode.lookupFunctionCached($child0Value_, $this));
                            node__ = ($this);
                            $bc[$bci + C_SL_FUNCTION_LITERAL_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 perform(TruffleString, SLFunction, Node) */);
                            if ((state_0 & 0b10) == 0b10/* is-exact-state_0 perform(TruffleString, SLFunction, Node) */) {
                                $bc[$bci] = (short) (INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM);
                            } else {
                                $bc[$bci] = (short) (INSTR_C_SL_FUNCTION_LITERAL);
                            }
                            int type0;
                            type0 = 0 /* OBJECT */;
                            doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0] & 0xff), type0);
                            lock.unlock();
                            hasLock = false;
                            $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value_, ((SLFunction) $consts[constArrayOffset_ + 0]), node__));
                            return;
                        }
                    }
                    throw new UnsupportedSpecializationException($this, new Node[] {null}, $frame.getValue($sp - 1));
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private static boolean SLToBoolean_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static void SLToBoolean_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */ && ((state_0 & 0b110) != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    SLToBoolean_SLToBoolean_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                } else {
                    SLToBoolean_SLToBoolean_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                    return;
                }
            }

            private static void SLToBoolean_SLToBoolean_execute__boolean0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
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

            private static void SLToBoolean_SLToBoolean_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
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
                        Node fallback_node__ = ($this);
                        int fallback_bci__ = ($bci);
                        if (SLToBoolean_fallbackGuard__($frame, $this, $bc, $bci, $sp, $consts, $children, state_0, $child0Value_)) {
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
                SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
                return;
            }

            private static void SLToBoolean_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0];
                    if ($child0Value instanceof Boolean) {
                        boolean $child0Value_ = (boolean) $child0Value;
                        $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doBoolean(boolean) */);
                        if ((state_0 & 0b110) == 0b10/* is-exact-state_0 doBoolean(boolean) */) {
                            $bc[$bci] = (short) (INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN);
                        } else {
                            $bc[$bci] = (short) (INSTR_C_SL_TO_BOOLEAN);
                        }
                        int type0;
                        if ((state_0 & 0b100) == 0 /* only-active doBoolean(boolean) */) {
                            type0 = 5 /* BOOLEAN */;
                        } else {
                            type0 = 0 /* OBJECT */;
                        }
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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
                        fallback_node__ = ($this);
                        fallback_bci__ = ($bci);
                        $bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 doFallback(Object, Node, int) */);
                        $bc[$bci] = (short) (INSTR_C_SL_TO_BOOLEAN);
                        int type0;
                        type0 = 0 /* OBJECT */;
                        doSetResultBoxed($bc, $bci, ($bc[$bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff), type0);
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

            @ExplodeLoop
            private static Object SLInvoke_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object arg0Value, Object[] arg1Value) {
                short state_0 = $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0];
                int childArrayOffset_;
                int constArrayOffset_;
                if ((state_0 & 0b1110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) || doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                    if ((state_0 & 0b110) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) || doIndirect(SLFunction, Object[], IndirectCallNode) */ && arg0Value instanceof SLFunction) {
                        SLFunction arg0Value_ = (SLFunction) arg0Value;
                        if ((state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0]);
                            while (s0_ != null) {
                                if (!Assumption.isValidAssumption((s0_.callTargetStable_))) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    SLInvoke_removeDirect__($frame, $this, $bc, $bci, $sp, $consts, $children, s0_);
                                    return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, arg0Value_, arg1Value);
                                }
                                if ((arg0Value_.getCallTarget() == s0_.cachedTarget_)) {
                                    return SLInvoke.doDirect(arg0Value_, arg1Value, s0_.callTargetStable_, s0_.cachedTarget_, s0_.callNode_);
                                }
                                s0_ = s0_.next_;
                            }
                        }
                        if ((state_0 & 0b100) != 0 /* is-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */) {
                            IndirectCallNode indirect_callNode__ = ((IndirectCallNode) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 1]);
                            if (indirect_callNode__ != null) {
                                return SLInvoke.doIndirect(arg0Value_, arg1Value, indirect_callNode__);
                            }
                        }
                    }
                    if ((state_0 & 0b1000) != 0 /* is-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */) {
                        {
                            Node interop_node__ = ($this);
                            int interop_bci__ = ($bci);
                            InteropLibrary interop_library__ = ((InteropLibrary) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 2]);
                            if (interop_library__ != null) {
                                return SLInvoke.doInterop(arg0Value, arg1Value, interop_library__, interop_node__, interop_bci__);
                            }
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, arg0Value, arg1Value);
            }

            private static Object SLInvoke_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object arg0Value, Object[] arg1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0];
                    short exclude = $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 1];
                    if (arg0Value instanceof SLFunction) {
                        SLFunction arg0Value_ = (SLFunction) arg0Value;
                        if ((exclude) == 0 /* is-not-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                            int count0_ = 0;
                            SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0]);
                            if ((state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                                while (s0_ != null) {
                                    if ((arg0Value_.getCallTarget() == s0_.cachedTarget_) && Assumption.isValidAssumption((s0_.callTargetStable_))) {
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
                                                s0_ = $this.insertAccessor(new SLInvoke_DirectData(((SLInvoke_DirectData) $children[childArrayOffset_ + 0])));
                                                s0_.callTargetStable_ = callTargetStable__;
                                                s0_.cachedTarget_ = cachedTarget__;
                                                s0_.callNode_ = s0_.insertAccessor((DirectCallNode.create(cachedTarget__)));
                                                VarHandle.storeStoreFence();
                                                $children[childArrayOffset_ + 0] = s0_;
                                                $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b10 /* add-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                                                if ((state_0 & 0b1110) == 0b10/* is-exact-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */) {
                                                    $bc[$bci] = (short) (INSTR_C_SL_INVOKE_Q_DIRECT);
                                                } else {
                                                    $bc[$bci] = (short) (INSTR_C_SL_INVOKE);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (s0_ != null) {
                                lock.unlock();
                                hasLock = false;
                                return SLInvoke.doDirect(arg0Value_, arg1Value, s0_.callTargetStable_, s0_.cachedTarget_, s0_.callNode_);
                            }
                        }
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 1] = $this.insertAccessor((IndirectCallNode.create()));
                        $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 1] = exclude = (short) (exclude | 0b1 /* add-exclude doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        $children[childArrayOffset_ + 0] = null;
                        state_0 = (short) (state_0 & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                        $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b100 /* add-state_0 doIndirect(SLFunction, Object[], IndirectCallNode) */);
                        $bc[$bci] = (short) (INSTR_C_SL_INVOKE);
                        lock.unlock();
                        hasLock = false;
                        return SLInvoke.doIndirect(arg0Value_, arg1Value, ((IndirectCallNode) $children[childArrayOffset_ + 1]));
                    }
                    {
                        int interop_bci__ = 0;
                        Node interop_node__ = null;
                        $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 2] = $this.insertAccessor((INTEROP_LIBRARY_.createDispatched(3)));
                        interop_node__ = ($this);
                        interop_bci__ = ($bci);
                        $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = state_0 = (short) (state_0 | 0b1000 /* add-state_0 doInterop(Object, Object[], InteropLibrary, Node, int) */);
                        $bc[$bci] = (short) (INSTR_C_SL_INVOKE);
                        lock.unlock();
                        hasLock = false;
                        return SLInvoke.doInterop(arg0Value, arg1Value, ((InteropLibrary) $children[childArrayOffset_ + 2]), interop_node__, interop_bci__);
                    }
                } finally {
                    if (hasLock) {
                        lock.unlock();
                    }
                }
            }

            private static void SLInvoke_removeDirect__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object s0_) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    SLInvoke_DirectData prev = null;
                    SLInvoke_DirectData cur = ((SLInvoke_DirectData) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CHILDREN_OFFSET) + 0) + 0]);
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
                        $bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_INVOKE_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    }
                } finally {
                    lock.unlock();
                }
            }

            private static boolean SLAnd_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLAnd_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + SC_SL_AND_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    return SLAnd_SLAnd_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                } else {
                    return SLAnd_SLAnd_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                }
            }

            private static boolean SLAnd_SLAnd_execute__boolean0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return SLAnd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                }
                assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
                return SLToBooleanNode.doBoolean($child0Value_);
            }

            private static boolean SLAnd_SLAnd_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 1);
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

            private static boolean SLAnd_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + SC_SL_AND_STATE_BITS_OFFSET + 0];
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

            private static boolean SLOr_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static boolean SLOr_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + SC_SL_OR_STATE_BITS_OFFSET + 0];
                if ((state_0 & 0b10) == 0 /* only-active doBoolean(boolean) */ && (state_0 != 0  /* is-not doBoolean(boolean) && doFallback(Object, Node, int) */)) {
                    return SLOr_SLOr_execute__boolean0_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                } else {
                    return SLOr_SLOr_execute__generic1_($frame, $this, $bc, $bci, $sp, $consts, $children, state_0);
                }
            }

            private static boolean SLOr_SLOr_execute__boolean0_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    return SLOr_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                }
                assert (state_0 & 0b1) != 0 /* is-state_0 doBoolean(boolean) */;
                return SLToBooleanNode.doBoolean($child0Value_);
            }

            private static boolean SLOr_SLOr_execute__generic1_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0) {
                int childArrayOffset_;
                int constArrayOffset_;
                Object $child0Value_ = expectObject($frame, $sp - 1);
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

            private static boolean SLOr_executeAndSpecialize_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                boolean hasLock = true;
                lock.lock();
                try {
                    short state_0 = $bc[$bci + SC_SL_OR_STATE_BITS_OFFSET + 0];
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

            private static void SLUnbox_q_FromLong_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_UNBOX_Q_FROM_LONG_STATE_BITS_OFFSET + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                    SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10000) != 0 /* is-state_0 fromLong(long) */;
                long value = SLUnboxNode.fromLong($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setLong($sp - 1, value);
                }
                return;
            }

            private static boolean SLAdd_q_AddLong_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                if (((state_0 & 0b1000)) == 0 /* is-not-state_0 add(Object, Object, SLToTruffleStringNode, SLToTruffleStringNode, ConcatNode) */ && (SLAddNode.isString($child0Value, $child1Value))) {
                    return false;
                }
                return true;
            }

            private static void SLAdd_q_AddLong_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    $bc[$bci] = (short) (INSTR_C_SL_ADD);
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_ADD);
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10) != 0 /* is-state_0 addLong(long, long) */;
                try {
                    long value = SLAddNode.addLong($child0Value_, $child1Value_);
                    if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setLong($sp - 2, value);
                    }
                    return;
                } catch (ArithmeticException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Lock lock = $this.getLockAccessor();
                    lock.lock();
                    try {
                        $bc[$bci + C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET + 1] = (short) ($bc[$bci + C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET + 1] | 0b1 /* add-exclude addLong(long, long) */);
                        $bc[$bci + C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_ADD_Q_ADD_LONG_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 addLong(long, long) */);
                    } finally {
                        lock.unlock();
                    }
                    $bc[$bci] = (short) (INSTR_C_SL_ADD);
                    SLAdd_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                    return;
                }
            }

            @ExplodeLoop
            private static void SLReadProperty_q_ReadSLObject0_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_STATE_BITS_OFFSET + 0];
                Object $child0Value_ = expectObject($frame, $sp - 2);
                Object $child1Value_ = expectObject($frame, $sp - 1);
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b1000) != 0 /* is-state_0 readSLObject(SLObject, Object, Node, int, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLReadProperty_ReadSLObject0Data s2_ = ((SLReadProperty_ReadSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CHILDREN_OFFSET) + 0) + 4]);
                    while (s2_ != null) {
                        if ((s2_.objectLibrary_.accepts($child0Value__))) {
                            Node node__ = ($this);
                            int bci__ = ($bci);
                            $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value__, $child1Value_, node__, bci__, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                            return;
                        }
                        s2_ = s2_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                $bc[$bci] = (short) (INSTR_C_SL_READ_PROPERTY);
                SLReadProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_);
                return;
            }

            private static void SLUnbox_q_FromBoolean_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_UNBOX_Q_FROM_BOOLEAN_STATE_BITS_OFFSET + 0];
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_UNBOX);
                    SLUnbox_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b1000) != 0 /* is-state_0 fromBoolean(boolean) */;
                boolean value = SLUnboxNode.fromBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private static boolean SLToBoolean_q_Boolean_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, short state_0, Object $child0Value) {
                if (((state_0 & 0b10)) == 0 /* is-not-state_0 doBoolean(boolean) */ && $child0Value instanceof Boolean) {
                    return false;
                }
                return true;
            }

            private static void SLToBoolean_q_Boolean_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_STATE_BITS_OFFSET + 0];
                boolean $child0Value_;
                try {
                    $child0Value_ = expectBoolean($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_TO_BOOLEAN);
                    SLToBoolean_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10) != 0 /* is-state_0 doBoolean(boolean) */;
                boolean value = SLToBooleanNode.doBoolean($child0Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private static boolean SLLessOrEqual_q_LessOrEqual0_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLLessOrEqual_q_LessOrEqual0_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_STATE_BITS_OFFSET + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
                    SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_LESS_OR_EQUAL);
                    SLLessOrEqual_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
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
            private static Object SLInvoke_q_Direct_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object arg0Value, Object[] arg1Value) {
                short state_0 = $bc[$bci + C_SL_INVOKE_Q_DIRECT_STATE_BITS_OFFSET + 0];
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10) != 0 /* is-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */;
                if (arg0Value instanceof SLFunction) {
                    SLFunction arg0Value_ = (SLFunction) arg0Value;
                    SLInvoke_DirectData s0_ = ((SLInvoke_DirectData) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_Q_DIRECT_CHILDREN_OFFSET) + 0) + 0]);
                    while (s0_ != null) {
                        if (!Assumption.isValidAssumption((s0_.callTargetStable_))) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            SLInvoke_q_Direct_removeDirect__($frame, $this, $bc, $bci, $sp, $consts, $children, s0_);
                            $bc[$bci] = (short) (INSTR_C_SL_INVOKE);
                            return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, arg0Value_, arg1Value);
                        }
                        if ((arg0Value_.getCallTarget() == s0_.cachedTarget_)) {
                            return SLInvoke.doDirect(arg0Value_, arg1Value, s0_.callTargetStable_, s0_.cachedTarget_, s0_.callNode_);
                        }
                        s0_ = s0_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                $bc[$bci] = (short) (INSTR_C_SL_INVOKE);
                return SLInvoke_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, arg0Value, arg1Value);
            }

            private static void SLInvoke_q_Direct_removeDirect__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object s0_) {
                int childArrayOffset_;
                int constArrayOffset_;
                Lock lock = $this.getLockAccessor();
                lock.lock();
                try {
                    SLInvoke_DirectData prev = null;
                    SLInvoke_DirectData cur = ((SLInvoke_DirectData) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_INVOKE_Q_DIRECT_CHILDREN_OFFSET) + 0) + 0]);
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
                        $bc[$bci + C_SL_INVOKE_Q_DIRECT_STATE_BITS_OFFSET + 0] = (short) ($bc[$bci + C_SL_INVOKE_Q_DIRECT_STATE_BITS_OFFSET + 0] & 0xfffffffd /* remove-state_0 doDirect(SLFunction, Object[], Assumption, RootCallTarget, DirectCallNode) */);
                    }
                } finally {
                    lock.unlock();
                }
            }

            private static void SLFunctionLiteral_q_Perform_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_STATE_BITS_OFFSET + 0];
                Object $child0Value_ = expectObject($frame, $sp - 1);
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10) != 0 /* is-state_0 perform(TruffleString, SLFunction, Node) */;
                if ($child0Value_ instanceof TruffleString) {
                    TruffleString $child0Value__ = (TruffleString) $child0Value_;
                    {
                        Node node__ = ($this);
                        SLFunction result__ = ((SLFunction) $consts[(constArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_CONSTANT_OFFSET) + 0) + 0]);
                        if (result__ != null) {
                            $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value__, result__, node__));
                            return;
                        }
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                $bc[$bci] = (short) (INSTR_C_SL_FUNCTION_LITERAL);
                SLFunctionLiteral_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_);
                return;
            }

            @ExplodeLoop
            private static void SLWriteProperty_q_WriteSLObject0_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_STATE_BITS_OFFSET + 0];
                Object $child0Value_ = expectObject($frame, $sp - 3);
                Object $child1Value_ = expectObject($frame, $sp - 2);
                Object $child2Value_ = expectObject($frame, $sp - 1);
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b1000) != 0 /* is-state_0 writeSLObject(SLObject, Object, Object, DynamicObjectLibrary, SLToTruffleStringNode) */;
                if ($child0Value_ instanceof SLObject) {
                    SLObject $child0Value__ = (SLObject) $child0Value_;
                    SLWriteProperty_WriteSLObject0Data s2_ = ((SLWriteProperty_WriteSLObject0Data) $children[(childArrayOffset_ = unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CHILDREN_OFFSET) + 0) + 4]);
                    while (s2_ != null) {
                        if ((s2_.objectLibrary_.accepts($child0Value__))) {
                            $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value__, $child1Value_, $child2Value_, s2_.objectLibrary_, s2_.toTruffleStringNode_));
                            return;
                        }
                        s2_ = s2_.next_;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                $bc[$bci] = (short) (INSTR_C_SL_WRITE_PROPERTY);
                SLWriteProperty_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, $child1Value_, $child2Value_);
                return;
            }

            private static boolean SLLessThan_q_LessThan0_fallbackGuard__(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                if (SLTypesGen.isImplicitSLBigNumber($child0Value) && SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                    return false;
                }
                return true;
            }

            private static void SLLessThan_q_LessThan0_execute_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children) {
                short state_0 = $bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_STATE_BITS_OFFSET + 0];
                long $child0Value_;
                try {
                    $child0Value_ = expectLong($frame, $sp - 2);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    Object $child1Value = expectObject($frame, $sp - 1);
                    $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN);
                    SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, ex.getResult(), $child1Value);
                    return;
                }
                long $child1Value_;
                try {
                    $child1Value_ = expectLong($frame, $sp - 1);
                } catch (UnexpectedResultException ex) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    $bc[$bci] = (short) (INSTR_C_SL_LESS_THAN);
                    SLLessThan_executeAndSpecialize_($frame, $this, $bc, $bci, $sp, $consts, $children, $child0Value_, ex.getResult());
                    return;
                }
                int childArrayOffset_;
                int constArrayOffset_;
                assert (state_0 & 0b10) != 0 /* is-state_0 lessThan(long, long) */;
                boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                if (((state_0 & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private static int storeLocalInitialization(VirtualFrame frame, int localIdx, int localTag, int sourceSlot) {
                Object value = frame.getValue(sourceSlot);
                if (localTag == 1 /* LONG */ && value instanceof Long) {
                    frame.setLong(localIdx, (long) value);
                    return 1 /* LONG */;
                }
                if (localTag == 5 /* BOOLEAN */ && value instanceof Boolean) {
                    frame.setBoolean(localIdx, (boolean) value);
                    return 5 /* BOOLEAN */;
                }
                frame.setObject(localIdx, value);
                return 0 /* OBJECT */;
            }

            private static boolean isAdoptable() {
                return true;
            }

            private static void doSetResultBoxed(short[] bc, int startBci, int bciOffset, int targetType) {
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
        @GeneratedBy(SLOperations.class)
        private static final class UncachedBytecodeNode extends BytecodeLoopBase {

            private static final LibraryFactory<InteropLibrary> INTEROP_LIBRARY_ = LibraryFactory.resolve(InteropLibrary.class);
            private static final LibraryFactory<DynamicObjectLibrary> DYNAMIC_OBJECT_LIBRARY_ = LibraryFactory.resolve(DynamicObjectLibrary.class);

            @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
            @BytecodeInterpreterSwitch
            @Override
            int continueAt(OperationNodeImpl $this, VirtualFrame $frame, short[] $bc, int $startBci, int $startSp, Object[] $consts, Node[] $children, ExceptionHandler[] $handlers, int[] $conditionProfiles, int maxLocals) {
                int $sp = $startSp;
                int $bci = $startBci;
                Counter loopCounter = new Counter();
                CompilerDirectives.transferToInterpreterAndInvalidate();
                int uncachedExecuteCount = $this.uncachedExecuteCount;
                loop: while (true) {
                    CompilerAsserts.partialEvaluationConstant($bci);
                    CompilerAsserts.partialEvaluationConstant($sp);
                    short curOpcode = unsafeFromBytecode($bc, $bci);
                    CompilerAsserts.partialEvaluationConstant(curOpcode);
                    try {
                        if ($sp < maxLocals) {
                            throw CompilerDirectives.shouldNotReachHere("stack underflow");
                        }
                        switch (curOpcode) {
                            // pop
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_POP :
                            {
                                $sp = $sp - 1;
                                $frame.clear($sp);
                                $bci = $bci + POP_LENGTH;
                                continue loop;
                            }
                            // branch
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] target
                            //   Boxing Elimination: Do Nothing
                            case INSTR_BRANCH :
                            {
                                int targetBci = $bc[$bci + BRANCH_BRANCH_TARGET_OFFSET + 0];
                                if (targetBci <= $bci) {
                                    if (CompilerDirectives.hasNextTier() && ++loopCounter.count >= 256) {
                                        TruffleSafepoint.poll($this);
                                        LoopNode.reportLoopCount($this, 256);
                                        loopCounter.count = 0;
                                    }
                                    if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge($this)) {
                                        Object osrResult = BytecodeOSRNode.tryOSR($this, targetBci, $sp, null, $frame);
                                        if (osrResult != null) {
                                            $frame.setObject(0, osrResult);
                                            return 0x0000ffff;
                                        }
                                    }
                                    uncachedExecuteCount--;
                                    if (uncachedExecuteCount <= 0) {
                                        $this.changeInterpreters(OperationNodeImpl.COMMON_EXECUTE);
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
                            //   Boxing Elimination: Do Nothing
                            case INSTR_BRANCH_FALSE :
                            {
                                boolean cond = $frame.getObject($sp - 1) == Boolean.TRUE;
                                $sp = $sp - 1;
                                if (do_profileCondition(cond, $conditionProfiles, $bc[$bci + BRANCH_FALSE_BRANCH_PROFILE_OFFSET + 0])) {
                                    $bci = $bci + BRANCH_FALSE_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0];
                                    continue loop;
                                }
                            }
                            // throw
                            //   Locals:
                            //     [ 0] exception
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_THROW :
                            {
                                int slot = $bc[$bci + THROW_LOCALS_OFFSET + 0];
                                throw (AbstractTruffleException) $frame.getObject(slot);
                            }
                            // load.constant.object
                            //   Constants:
                            //     [ 0] constant
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_CONSTANT_OBJECT
                            //     LONG -> INSTR_LOAD_CONSTANT_LONG
                            //     BOOLEAN -> INSTR_LOAD_CONSTANT_BOOLEAN
                            case INSTR_LOAD_CONSTANT_OBJECT :
                            {
                                $frame.setObject($sp, $consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_OBJECT_CONSTANT_OFFSET) + 0]);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                                continue loop;
                            }
                            // load.argument.object
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_LONG
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            case INSTR_LOAD_ARGUMENT_OBJECT :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_OBJECT_ARGUMENT_OFFSET + 0]];
                                $frame.setObject($sp, value);
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                                continue loop;
                            }
                            // load.argument.long
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_LONG
                            //     INT -> INSTR_LOAD_ARGUMENT_LONG
                            //     DOUBLE -> INSTR_LOAD_ARGUMENT_LONG
                            //     FLOAT -> INSTR_LOAD_ARGUMENT_LONG
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_LONG
                            //     BYTE -> INSTR_LOAD_ARGUMENT_LONG
                            case INSTR_LOAD_ARGUMENT_LONG :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_LONG_ARGUMENT_OFFSET + 0]];
                                if (value instanceof Long) {
                                    $frame.setLong($sp, (long) value);
                                } else {
                                    $frame.setObject($sp, value);
                                }
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                                continue loop;
                            }
                            // load.argument.boolean
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_ARGUMENT_OBJECT
                            //     LONG -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     INT -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     DOUBLE -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     FLOAT -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     BOOLEAN -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            //     BYTE -> INSTR_LOAD_ARGUMENT_BOOLEAN
                            case INSTR_LOAD_ARGUMENT_BOOLEAN :
                            {
                                Object value = $frame.getArguments()[$bc[$bci + LOAD_ARGUMENT_BOOLEAN_ARGUMENT_OFFSET + 0]];
                                if (value instanceof Boolean) {
                                    $frame.setBoolean($sp, (boolean) value);
                                } else {
                                    $frame.setObject($sp, value);
                                }
                                $sp = $sp + 1;
                                $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // store.local.uninit
                            //   Locals:
                            //     [ 0] target
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_STORE_LOCAL_OBJECT
                            //     LONG -> INSTR_STORE_LOCAL_LONG
                            //     BOOLEAN -> INSTR_STORE_LOCAL_BOOLEAN
                            case INSTR_STORE_LOCAL_UNINIT :
                            {
                                int localIdx = $bc[$bci + STORE_LOCAL_UNINIT_LOCALS_OFFSET + 0];
                                int sourceSlot = $sp - 1;
                                $frame.copyObject(sourceSlot, localIdx);
                                $sp--;
                                $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                                continue loop;
                            }
                            // load.local.uninit
                            //   Locals:
                            //     [ 0] local
                            //   Pushed Values: 1
                            //   Boxing Elimination: Replace
                            //     OBJECT -> INSTR_LOAD_LOCAL_OBJECT
                            //     LONG -> INSTR_LOAD_LOCAL_LONG
                            //     BOOLEAN -> INSTR_LOAD_LOCAL_BOOLEAN
                            case INSTR_LOAD_LOCAL_UNINIT :
                            {
                                int localIdx = $bc[$bci + LOAD_LOCAL_UNINIT_LOCALS_OFFSET + 0];
                                $frame.setObject($sp, expectObject($frame, localIdx));
                                $sp++;
                                $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                                continue loop;
                            }
                            // return
                            //   Simple Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Boxing Elimination: Do Nothing
                            case INSTR_RETURN :
                            {
                                uncachedExecuteCount--;
                                if (uncachedExecuteCount <= 0) {
                                    $this.changeInterpreters(OperationNodeImpl.COMMON_EXECUTE);
                                } else {
                                    $this.uncachedExecuteCount = uncachedExecuteCount;
                                }
                                return (($sp - 1) << 16) | 0xffff;
                            }
                            // c.SLAdd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Add1, method = public static com.oracle.truffle.api.strings.TruffleString add(java.lang.Object, java.lang.Object, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode, com.oracle.truffle.api.strings.TruffleString.ConcatNode) , guards = [Guard[(SLAddNode.isString(left, right))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@19cfc07
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@643f16e7
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@5ec7fc1
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@45a827e9
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_ADD :
                            {
                                UncachedBytecodeNode.SLAdd_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_ADD_LENGTH;
                                continue loop;
                            }
                            // c.SLDiv
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@587558fe
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@6e56aaae
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@3e16eb33
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@2f88b46
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_DIV :
                            {
                                UncachedBytecodeNode.SLDiv_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_DIV_LENGTH;
                                continue loop;
                            }
                            // c.SLEqual
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = equalNode]
                            //     [ 1] SpecializationData [id = Generic0, method = public static boolean doGeneric(java.lang.Object, java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(leftInterop.accepts(left))], Guard[(rightInterop.accepts(right))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = leftInterop]
                            //     [ 3] CacheExpression [sourceParameter = rightInterop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@8cc0fa7
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@14d8a877
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@640cfeaf
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@609f0aa8
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_EQUAL :
                            {
                                UncachedBytecodeNode.SLEqual_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_EQUAL_LENGTH;
                                continue loop;
                            }
                            // c.SLLessOrEqual
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@6618a0ba
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@69d93afb
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_OR_EQUAL :
                            {
                                UncachedBytecodeNode.SLLessOrEqual_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                                continue loop;
                            }
                            // c.SLLessThan
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@3d40650d
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@2f3d2e3d
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LESS_THAN :
                            {
                                UncachedBytecodeNode.SLLessThan_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_LESS_THAN_LENGTH;
                                continue loop;
                            }
                            // c.SLLogicalNot
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@5599f435
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@2cc754f3
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_LOGICAL_NOT :
                            {
                                UncachedBytecodeNode.SLLogicalNot_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 1));
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                                continue loop;
                            }
                            // c.SLMul
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@45365e1
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@276d18a1
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@3495d189
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@143b2b02
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_MUL :
                            {
                                UncachedBytecodeNode.SLMul_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_MUL_LENGTH;
                                continue loop;
                            }
                            // c.SLReadProperty
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //     [ 2] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = ReadArray0, method = public static java.lang.Object readArray(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = ReadSLObject0, method = public static java.lang.Object readSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = node]
                            //     [ 6] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 7] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 8] SpecializationData [id = ReadObject0, method = public static java.lang.Object readObject(java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objects.accepts(receiver))], Guard[(!(SLReadPropertyNode.isSLObject(receiver)))], Guard[(objects.hasMembers(receiver))]], signature = [java.lang.Object, java.lang.Object]]
                            //     [ 9] CacheExpression [sourceParameter = node]
                            //     [10] CacheExpression [sourceParameter = objects]
                            //     [11] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@19bb694c
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@3990e12
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@52b1a37e
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@44c71517
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_READ_PROPERTY :
                            {
                                UncachedBytecodeNode.SLReadProperty_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                                continue loop;
                            }
                            // c.SLSub
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@1cfa1fed
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@539da4c3
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@5ec48d9f
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@136ace7e
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_SUB :
                            {
                                UncachedBytecodeNode.SLSub_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 2 + 1;
                                $bci = $bci + C_SL_SUB_LENGTH;
                                continue loop;
                            }
                            // c.SLWriteProperty
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //     [ 1] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = WriteArray0, method = public static java.lang.Object writeArray(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(arrays.accepts(receiver))], Guard[(numbers.accepts(index))], Guard[(arrays.hasArrayElements(receiver))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 1] CacheExpression [sourceParameter = node]
                            //     [ 2] CacheExpression [sourceParameter = arrays]
                            //     [ 3] CacheExpression [sourceParameter = numbers]
                            //     [ 4] SpecializationData [id = WriteSLObject0, method = public static java.lang.Object writeSLObject(com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object, com.oracle.truffle.api.object.DynamicObjectLibrary, com.oracle.truffle.sl.nodes.util.SLToTruffleStringNode) , guards = [Guard[(objectLibrary.accepts(receiver))]], signature = [com.oracle.truffle.sl.runtime.SLObject, java.lang.Object, java.lang.Object]]
                            //     [ 5] CacheExpression [sourceParameter = objectLibrary]
                            //     [ 6] CacheExpression [sourceParameter = toTruffleStringNode]
                            //     [ 7] SpecializationData [id = WriteObject0, method = public static java.lang.Object writeObject(java.lang.Object, java.lang.Object, java.lang.Object, com.oracle.truffle.api.nodes.Node, int, com.oracle.truffle.api.interop.InteropLibrary, com.oracle.truffle.sl.nodes.util.SLToMemberNode) , guards = [Guard[(objectLibrary.accepts(receiver))], Guard[(!(SLWritePropertyNode.isSLObject(receiver)))]], signature = [java.lang.Object, java.lang.Object, java.lang.Object]]
                            //     [ 8] CacheExpression [sourceParameter = node]
                            //     [ 9] CacheExpression [sourceParameter = objectLibrary]
                            //     [10] CacheExpression [sourceParameter = asMember]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //     [ 1] arg1
                            //     [ 2] arg2
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@11ae46f5
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@5a8c334c
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4c9f353e
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@300cfe17
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_WRITE_PROPERTY :
                            {
                                UncachedBytecodeNode.SLWriteProperty_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 3), $frame.getObject($sp - 2), $frame.getObject($sp - 1));
                                $sp = $sp - 3 + 1;
                                $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                                continue loop;
                            }
                            // c.SLUnbox
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = fromJavaStringNode]
                            //     [ 1] SpecializationData [id = FromForeign0, method = public static java.lang.Object fromForeign(java.lang.Object, com.oracle.truffle.api.interop.InteropLibrary) , guards = [Guard[(interop.accepts(value))]], signature = [java.lang.Object]]
                            //     [ 2] CacheExpression [sourceParameter = interop]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@16455a87
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@28c05f30
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4a5e35fc
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@f021bf7
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_UNBOX :
                            {
                                UncachedBytecodeNode.SLUnbox_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 1));
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_UNBOX_LENGTH;
                                continue loop;
                            }
                            // c.SLFunctionLiteral
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = result]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4b3a2d33
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@3a81e9ec
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_FUNCTION_LITERAL :
                            {
                                UncachedBytecodeNode.SLFunctionLiteral_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 1));
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                                continue loop;
                            }
                            // c.SLToBoolean
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] arg0
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7097614b
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@6daa44ed
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_TO_BOOLEAN :
                            {
                                UncachedBytecodeNode.SLToBoolean_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, $frame.getObject($sp - 1));
                                $sp = $sp - 1 + 1;
                                $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                                continue loop;
                            }
                            // c.SLInvoke
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] SpecializationData [id = Direct, method = protected static java.lang.Object doDirect(com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[], com.oracle.truffle.api.Assumption, com.oracle.truffle.api.RootCallTarget, com.oracle.truffle.api.nodes.DirectCallNode) , guards = [Guard[(function.getCallTarget() == cachedTarget)]], signature = [com.oracle.truffle.sl.runtime.SLFunction, java.lang.Object[]]]
                            //     [ 1] CacheExpression [sourceParameter = callNode]
                            //     [ 2] CacheExpression [sourceParameter = library]
                            //     [ 3] CacheExpression [sourceParameter = node]
                            //   Simple Pops:
                            //     [ 0] arg0
                            //   Variadic
                            //   Pushed Values: 1
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@4c3a8cbc
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@662973d4
                            //     [ 2] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@56e06d71
                            //     [ 3] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$ExcludeBitSet@492209f9
                            //   Boxing Elimination: Bit Mask
                            case INSTR_C_SL_INVOKE :
                            {
                                int numVariadics = $bc[$bci + C_SL_INVOKE_VARIADIC_OFFSET + 0];
                                Object input_0 = $frame.getObject($sp - numVariadics - 1);
                                Object[] input_1 = new Object[numVariadics];
                                for (int varIndex = 0; varIndex < numVariadics; varIndex++) {
                                    input_1[varIndex] = $frame.getObject($sp - numVariadics + varIndex);
                                }
                                Object result = UncachedBytecodeNode.SLInvoke_executeUncached_($frame, $this, $bc, $bci, $sp, $consts, $children, input_0, input_1);
                                $sp = $sp - 1 - numVariadics + 1;
                                $frame.setObject($sp - 1, result);
                                $bci = $bci + C_SL_INVOKE_LENGTH;
                                continue loop;
                            }
                            // sc.SLAnd
                            //   Constants:
                            //     [ 0] CacheExpression [sourceParameter = bci]
                            //   Children:
                            //     [ 0] CacheExpression [sourceParameter = node]
                            //   Indexed Pops:
                            //     [ 0] value
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7ce08997
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@7c5957b8
                            //   Boxing Elimination: Do Nothing
                            case INSTR_SC_SL_AND :
                            {
                                if (BytecodeNode.SLAnd_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_AND_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0];
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
                            //   Pushed Values: 0
                            //   Branch Targets:
                            //     [ 0] end
                            //   State Bitsets:
                            //     [ 0] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@37362279
                            //     [ 1] com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory$StateBitSet@43ebf9f2
                            //   Boxing Elimination: Do Nothing
                            case INSTR_SC_SL_OR :
                            {
                                if (!BytecodeNode.SLOr_execute_($frame, $this, $bc, $bci, $sp, $consts, $children)) {
                                    $sp = $sp - 1;
                                    $bci = $bci + SC_SL_OR_LENGTH;
                                    continue loop;
                                } else {
                                    $bci = $bc[$bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0];
                                    continue loop;
                                }
                            }
                            default :
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw CompilerDirectives.shouldNotReachHere("unknown opcode encountered: " + curOpcode + "");
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
            }

            @Override
            void prepareForAOT(OperationNodeImpl $this, short[] $bc, Object[] $consts, Node[] $children, TruffleLanguage<?> language, RootNode root) {
                int $bci = 0;
                while ($bci < $bc.length) {
                    switch (unsafeFromBytecode($bc, $bci)) {
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
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        {
                            $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            $bc[$bci] = (short) (INSTR_LOAD_CONSTANT_OBJECT);
                            $bci = $bci + LOAD_CONSTANT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            $bc[$bci] = (short) (INSTR_LOAD_CONSTANT_OBJECT);
                            $bci = $bci + LOAD_CONSTANT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        {
                            $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_LONG :
                        {
                            $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        {
                            $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        {
                            $bci = $bci + STORE_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_LONG :
                        {
                            $bci = $bci + STORE_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        {
                            $bci = $bci + STORE_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_UNINIT :
                        {
                            $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_OBJECT :
                        {
                            $bci = $bci + LOAD_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_LONG :
                        {
                            $bci = $bci + LOAD_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        {
                            $bci = $bci + LOAD_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_UNINIT :
                        {
                            $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            $bci = $bci + RETURN_LENGTH;
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
                        case INSTR_C_SL_UNBOX_Q_FROM_LONG :
                        {
                            $bci = $bci + C_SL_UNBOX_Q_FROM_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD_Q_ADD_LONG :
                        {
                            $bci = $bci + C_SL_ADD_Q_ADD_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 :
                        {
                            $bci = $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN :
                        {
                            $bci = $bci + C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN :
                        {
                            $bci = $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        {
                            $bci = $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE_Q_DIRECT :
                        {
                            $bci = $bci + C_SL_INVOKE_Q_DIRECT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM :
                        {
                            $bci = $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 :
                        {
                            $bci = $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 :
                        {
                            $bci = $bci + C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH;
                            break;
                        }
                    }
                }
            }

            @Override
            String dump(short[] $bc, ExceptionHandler[] $handlers, Object[] $consts) {
                int $bci = 0;
                StringBuilder sb = new StringBuilder();
                while ($bci < $bc.length) {
                    sb.append(String.format(" [%04x]", $bci));
                    switch (unsafeFromBytecode($bc, $bci)) {
                        default :
                        {
                            sb.append(String.format(" unknown 0x%02x", $bc[$bci++]));
                            break;
                        }
                        case INSTR_POP :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("pop                           ");
                            $bci = $bci + POP_LENGTH;
                            break;
                        }
                        case INSTR_BRANCH :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("branch                        ");
                            sb.append(String.format(" branch(%04x)", $bc[$bci + BRANCH_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + BRANCH_LENGTH;
                            break;
                        }
                        case INSTR_BRANCH_FALSE :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("branch.false                  ");
                            sb.append(String.format(" branch(%04x)", $bc[$bci + BRANCH_FALSE_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + BRANCH_FALSE_LENGTH;
                            break;
                        }
                        case INSTR_THROW :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("throw                         ");
                            sb.append(String.format(" local(%s)", $bc[$bci + THROW_LOCALS_OFFSET + 0]));
                            $bci = $bci + THROW_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.object          ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_OBJECT_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.long            ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_LONG_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_CONSTANT_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.constant.boolean         ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + LOAD_CONSTANT_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            $bci = $bci + LOAD_CONSTANT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.object          ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_OBJECT_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.long            ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_LONG_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_ARGUMENT_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.argument.boolean         ");
                            sb.append(String.format(" arg(%s)", $bc[$bci + LOAD_ARGUMENT_BOOLEAN_ARGUMENT_OFFSET + 0]));
                            $bci = $bci + LOAD_ARGUMENT_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.object            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_OBJECT_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_OBJECT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.long              ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_LONG_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.boolean           ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_BOOLEAN_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_STORE_LOCAL_UNINIT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("store.local.uninit            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + STORE_LOCAL_UNINIT_LOCALS_OFFSET + 0]));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + STORE_LOCAL_UNINIT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + STORE_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_OBJECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.object             ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_OBJECT_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_OBJECT_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.long               ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_LONG_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_LONG_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.boolean            ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_BOOLEAN_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_LOAD_LOCAL_UNINIT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("load.local.uninit             ");
                            sb.append(String.format(" local(%s)", $bc[$bci + LOAD_LOCAL_UNINIT_LOCALS_OFFSET + 0]));
                            $bci = $bci + LOAD_LOCAL_UNINIT_LENGTH;
                            break;
                        }
                        case INSTR_RETURN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("return                        ");
                            $bci = $bci + RETURN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLAdd                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_ADD_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_ADD_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_DIV :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLDiv                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_DIV_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_DIV_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_DIV_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_EQUAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLEqual                     ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_EQUAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessOrEqual               ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_OR_EQUAL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_OR_EQUAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessThan                  ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_THAN_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_THAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LOGICAL_NOT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLogicalNot                ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LOGICAL_NOT_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LOGICAL_NOT_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_LOGICAL_NOT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_MUL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLMul                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_MUL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_MUL_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_MUL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLReadProperty              ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_CONSTANT_OFFSET) + 2])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_READ_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_READ_PROPERTY_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_SUB :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLSub                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_SUB_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_SUB_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_SUB_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append(String.format(" %04x", $bc[$bci + 8]));
                            sb.append("c.SLWriteProperty             ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_POP_INDEXED_OFFSET + 1] & 0xff)));
                            $bci = $bci + C_SL_WRITE_PROPERTY_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLUnbox                     ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLFunctionLiteral           ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_FUNCTION_LITERAL_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_FUNCTION_LITERAL_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLToBoolean                 ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_TO_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_TO_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLInvoke                    ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" var(%s)", $bc[$bci + C_SL_INVOKE_VARIADIC_OFFSET + 0]));
                            $bci = $bci + C_SL_INVOKE_LENGTH;
                            break;
                        }
                        case INSTR_SC_SL_AND :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("sc.SLAnd                      ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + SC_SL_AND_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + SC_SL_AND_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" branch(%04x)", $bc[$bci + SC_SL_AND_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + SC_SL_AND_LENGTH;
                            break;
                        }
                        case INSTR_SC_SL_OR :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("sc.SLOr                       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + SC_SL_OR_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + SC_SL_OR_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" branch(%04x)", $bc[$bci + SC_SL_OR_BRANCH_TARGET_OFFSET + 0]));
                            $bci = $bci + SC_SL_OR_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLUnbox.q.FromLong          ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_Q_FROM_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_Q_FROM_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_ADD_Q_ADD_LONG :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLAdd.q.AddLong             ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_ADD_Q_ADD_LONG_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_ADD_Q_ADD_LONG_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_ADD_Q_ADD_LONG_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_ADD_Q_ADD_LONG_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLReadProperty.q.ReadSLObject0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_CONSTANT_OFFSET) + 2])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_READ_PROPERTY_Q_READ_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_UNBOX_Q_FROM_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append("     ");
                            sb.append("c.SLUnbox.q.FromBoolean       ");
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_UNBOX_Q_FROM_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_UNBOX_Q_FROM_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_TO_BOOLEAN_Q_BOOLEAN :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLToBoolean.q.Boolean       ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessOrEqual.q.LessOrEqual0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_INVOKE_Q_DIRECT :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append("c.SLInvoke.q.Direct           ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_INVOKE_Q_DIRECT_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" var(%s)", $bc[$bci + C_SL_INVOKE_Q_DIRECT_VARIADIC_OFFSET + 0]));
                            $bci = $bci + C_SL_INVOKE_Q_DIRECT_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_FUNCTION_LITERAL_Q_PERFORM :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLFunctionLiteral.q.Perform ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_POP_INDEXED_OFFSET + 0] & 0xff)));
                            $bci = $bci + C_SL_FUNCTION_LITERAL_Q_PERFORM_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append(String.format(" %04x", $bc[$bci + 6]));
                            sb.append(String.format(" %04x", $bc[$bci + 7]));
                            sb.append(String.format(" %04x", $bc[$bci + 8]));
                            sb.append("c.SLWriteProperty.q.WriteSLObject0");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_CONSTANT_OFFSET) + 1])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_POP_INDEXED_OFFSET + 1] & 0xff)));
                            $bci = $bci + C_SL_WRITE_PROPERTY_Q_WRITE_SL_OBJECT0_LENGTH;
                            break;
                        }
                        case INSTR_C_SL_LESS_THAN_Q_LESS_THAN0 :
                        {
                            sb.append(String.format(" %04x", $bc[$bci + 0]));
                            sb.append(String.format(" %04x", $bc[$bci + 1]));
                            sb.append(String.format(" %04x", $bc[$bci + 2]));
                            sb.append(String.format(" %04x", $bc[$bci + 3]));
                            sb.append(String.format(" %04x", $bc[$bci + 4]));
                            sb.append(String.format(" %04x", $bc[$bci + 5]));
                            sb.append("     ");
                            sb.append("     ");
                            sb.append("c.SLLessThan.q.LessThan0      ");
                            sb.append(String.format(" const(%s)", formatConstant($consts[unsafeFromBytecode($bc, $bci + C_SL_LESS_THAN_Q_LESS_THAN0_CONSTANT_OFFSET) + 0])));
                            sb.append(String.format(" pop(-%s)", ($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_POP_INDEXED_OFFSET + 0] & 0xff)));
                            sb.append(String.format(" pop(-%s)", (($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_POP_INDEXED_OFFSET + 0] >> 8) & 0xff)));
                            $bci = $bci + C_SL_LESS_THAN_Q_LESS_THAN0_LENGTH;
                            break;
                        }
                    }
                    sb.append("\n");
                }
                for (int i = 0; i < $handlers.length; i++) {
                    sb.append($handlers[i] + "\n");
                }
                return sb.toString();
            }

            private static void SLAdd_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_));
                        return;
                    }
                }
                if ((SLAddNode.isString($child0Value, $child1Value))) {
                    $frame.setObject($sp - 2, SLAddNode.add($child0Value, $child1Value, (SLToTruffleStringNodeGen.getUncached()), (SLToTruffleStringNodeGen.getUncached()), (ConcatNode.getUncached())));
                    return;
                }
                $frame.setObject($sp - 2, SLAddNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLDiv_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        $frame.setObject($sp - 2, SLDivNode.div($child0Value_, $child1Value_));
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLDivNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLEqual_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        boolean value = SLEqualNode.doLong($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        boolean value = SLEqualNode.doBigNumber($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    if ($child1Value instanceof Boolean) {
                        boolean $child1Value_ = (boolean) $child1Value;
                        boolean value = SLEqualNode.doBoolean($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
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
                        boolean value = SLEqualNode.doString($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
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
                        boolean value = SLEqualNode.doTruffleString($child0Value_, $child1Value_, (EqualNode.getUncached()));
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
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
                        boolean value = SLEqualNode.doNull($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    boolean value = SLEqualNode.doFunction($child0Value_, $child1Value);
                    if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 2, value);
                    } else {
                        $frame.setBoolean($sp - 2, value);
                    }
                    return;
                }
                boolean value = SLEqualNode.doGeneric($child0Value, $child1Value, (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value)));
                if ((($bc[$bci + C_SL_EQUAL_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 2, value);
                } else {
                    $frame.setBoolean($sp - 2, value);
                }
                return;
            }

            private static void SLLessOrEqual_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_OR_EQUAL_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLLessThan_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_THAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLLogicalNot_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLLogicalNotNode.doBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_LOGICAL_NOT_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                $frame.setObject($sp - 1, SLLogicalNotNode.typeError($child0Value, ($this), ($bci)));
                return;
            }

            private static void SLMul_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        $frame.setObject($sp - 2, SLMulNode.mul($child0Value_, $child1Value_));
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLMulNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLReadProperty_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                    $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value))));
                    return;
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, ($this), ($bci), (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached())));
                    return;
                }
                if ((!(SLReadPropertyNode.isSLObject($child0Value))) && ((INTEROP_LIBRARY_.getUncached($child0Value)).hasMembers($child0Value))) {
                    $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached())));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null}, $frame.getValue($sp - 2), $frame.getValue($sp - 1));
            }

            private static void SLSub_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        $frame.setObject($sp - 2, SLSubNode.sub($child0Value_, $child1Value_));
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLSubNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLWriteProperty_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value, Object $child2Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value))));
                    return;
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached())));
                    return;
                }
                if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached())));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null, null}, $frame.getValue($sp - 3), $frame.getValue($sp - 2), $frame.getValue($sp - 1));
            }

            private static void SLUnbox_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof String) {
                    String $child0Value_ = (String) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value_, (FromJavaStringNode.getUncached())));
                    return;
                }
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value_));
                    return;
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLUnboxNode.fromBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    long value = SLUnboxNode.fromLong($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setLong($sp - 1, value);
                    }
                    return;
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value_));
                    return;
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                if (SLTypes.isSLNull($child0Value)) {
                    SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, (INTEROP_LIBRARY_.getUncached($child0Value))));
                return;
            }

            private static void SLFunctionLiteral_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value_, (SLFunctionLiteralNode.lookupFunction($child0Value_, $this)), ($this)));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null}, $frame.getValue($sp - 1));
            }

            private static void SLToBoolean_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLToBooleanNode.doBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                boolean value = SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
                if ((($bc[$bci + C_SL_TO_BOOLEAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private static Object SLInvoke_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object arg0Value, Object[] arg1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (arg0Value instanceof SLFunction) {
                    SLFunction arg0Value_ = (SLFunction) arg0Value;
                    return SLInvoke.doIndirect(arg0Value_, arg1Value, (IndirectCallNode.getUncached()));
                }
                return SLInvoke.doInterop(arg0Value, arg1Value, (INTEROP_LIBRARY_.getUncached()), ($this), ($bci));
            }

            private static boolean SLAnd_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    return SLToBooleanNode.doBoolean($child0Value_);
                }
                return SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
            }

            private static boolean SLOr_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    return SLToBooleanNode.doBoolean($child0Value_);
                }
                return SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
            }

            private static void SLUnbox_q_FromLong_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof String) {
                    String $child0Value_ = (String) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value_, (FromJavaStringNode.getUncached())));
                    return;
                }
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value_));
                    return;
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLUnboxNode.fromBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_Q_FROM_LONG_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    long value = SLUnboxNode.fromLong($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_Q_FROM_LONG_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setLong($sp - 1, value);
                    }
                    return;
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value_));
                    return;
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                if (SLTypes.isSLNull($child0Value)) {
                    SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, (INTEROP_LIBRARY_.getUncached($child0Value))));
                return;
            }

            private static void SLAdd_q_AddLong_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        $frame.setObject($sp - 2, SLAddNode.add($child0Value_, $child1Value_));
                        return;
                    }
                }
                if ((SLAddNode.isString($child0Value, $child1Value))) {
                    $frame.setObject($sp - 2, SLAddNode.add($child0Value, $child1Value, (SLToTruffleStringNodeGen.getUncached()), (SLToTruffleStringNodeGen.getUncached()), (ConcatNode.getUncached())));
                    return;
                }
                $frame.setObject($sp - 2, SLAddNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static void SLReadProperty_q_ReadSLObject0_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                    $frame.setObject($sp - 2, SLReadPropertyNode.readArray($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value))));
                    return;
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    $frame.setObject($sp - 2, SLReadPropertyNode.readSLObject($child0Value_, $child1Value, ($this), ($bci), (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached())));
                    return;
                }
                if ((!(SLReadPropertyNode.isSLObject($child0Value))) && ((INTEROP_LIBRARY_.getUncached($child0Value)).hasMembers($child0Value))) {
                    $frame.setObject($sp - 2, SLReadPropertyNode.readObject($child0Value, $child1Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached())));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null}, $frame.getValue($sp - 2), $frame.getValue($sp - 1));
            }

            private static void SLUnbox_q_FromBoolean_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof String) {
                    String $child0Value_ = (String) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromString($child0Value_, (FromJavaStringNode.getUncached())));
                    return;
                }
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromTruffleString($child0Value_));
                    return;
                }
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLUnboxNode.fromBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_Q_FROM_BOOLEAN_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    long value = SLUnboxNode.fromLong($child0Value_);
                    if ((($bc[$bci + C_SL_UNBOX_Q_FROM_BOOLEAN_STATE_BITS_OFFSET + 2] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setLong($sp - 1, value);
                    }
                    return;
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromBigNumber($child0Value_));
                    return;
                }
                if ($child0Value instanceof SLFunction) {
                    SLFunction $child0Value_ = (SLFunction) $child0Value;
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                if (SLTypes.isSLNull($child0Value)) {
                    SLNull $child0Value_ = SLTypes.asSLNull($child0Value);
                    $frame.setObject($sp - 1, SLUnboxNode.fromFunction($child0Value_));
                    return;
                }
                $frame.setObject($sp - 1, SLUnboxNode.fromForeign($child0Value, (INTEROP_LIBRARY_.getUncached($child0Value))));
                return;
            }

            private static void SLToBoolean_q_Boolean_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Boolean) {
                    boolean $child0Value_ = (boolean) $child0Value;
                    boolean value = SLToBooleanNode.doBoolean($child0Value_);
                    if ((($bc[$bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                        $frame.setObject($sp - 1, value);
                    } else {
                        $frame.setBoolean($sp - 1, value);
                    }
                    return;
                }
                boolean value = SLToBooleanNode.doFallback($child0Value, ($this), ($bci));
                if ((($bc[$bci + C_SL_TO_BOOLEAN_Q_BOOLEAN_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                    $frame.setObject($sp - 1, value);
                } else {
                    $frame.setBoolean($sp - 1, value);
                }
                return;
            }

            private static void SLLessOrEqual_q_LessOrEqual0_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        boolean value = SLLessOrEqualNode.lessOrEqual($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_OR_EQUAL_Q_LESS_OR_EQUAL0_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLLessOrEqualNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static Object SLInvoke_q_Direct_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object arg0Value, Object[] arg1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (arg0Value instanceof SLFunction) {
                    SLFunction arg0Value_ = (SLFunction) arg0Value;
                    return SLInvoke.doIndirect(arg0Value_, arg1Value, (IndirectCallNode.getUncached()));
                }
                return SLInvoke.doInterop(arg0Value, arg1Value, (INTEROP_LIBRARY_.getUncached()), ($this), ($bci));
            }

            private static void SLFunctionLiteral_q_Perform_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof TruffleString) {
                    TruffleString $child0Value_ = (TruffleString) $child0Value;
                    $frame.setObject($sp - 1, SLFunctionLiteralNode.perform($child0Value_, (SLFunctionLiteralNode.lookupFunction($child0Value_, $this)), ($this)));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null}, $frame.getValue($sp - 1));
            }

            private static void SLWriteProperty_q_WriteSLObject0_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value, Object $child2Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if (((INTEROP_LIBRARY_.getUncached($child0Value)).hasArrayElements($child0Value))) {
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeArray($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (INTEROP_LIBRARY_.getUncached($child1Value))));
                    return;
                }
                if ($child0Value instanceof SLObject) {
                    SLObject $child0Value_ = (SLObject) $child0Value;
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeSLObject($child0Value_, $child1Value, $child2Value, (DYNAMIC_OBJECT_LIBRARY_.getUncached($child0Value_)), (SLToTruffleStringNodeGen.getUncached())));
                    return;
                }
                if ((!(SLWritePropertyNode.isSLObject($child0Value)))) {
                    $frame.setObject($sp - 3, SLWritePropertyNode.writeObject($child0Value, $child1Value, $child2Value, ($this), ($bci), (INTEROP_LIBRARY_.getUncached($child0Value)), (SLToMemberNodeGen.getUncached())));
                    return;
                }
                throw new UnsupportedSpecializationException($this, new Node[] {null, null, null}, $frame.getValue($sp - 3), $frame.getValue($sp - 2), $frame.getValue($sp - 1));
            }

            private static void SLLessThan_q_LessThan0_executeUncached_(VirtualFrame $frame, OperationNodeImpl $this, short[] $bc, int $bci, int $sp, Object[] $consts, Node[] $children, Object $child0Value, Object $child1Value) {
                int childArrayOffset_;
                int constArrayOffset_;
                if ($child0Value instanceof Long) {
                    long $child0Value_ = (long) $child0Value;
                    if ($child1Value instanceof Long) {
                        long $child1Value_ = (long) $child1Value;
                        boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                if (SLTypesGen.isImplicitSLBigNumber($child0Value)) {
                    SLBigNumber $child0Value_ = SLTypesGen.asImplicitSLBigNumber($child0Value);
                    if (SLTypesGen.isImplicitSLBigNumber($child1Value)) {
                        SLBigNumber $child1Value_ = SLTypesGen.asImplicitSLBigNumber($child1Value);
                        boolean value = SLLessThanNode.lessThan($child0Value_, $child1Value_);
                        if ((($bc[$bci + C_SL_LESS_THAN_Q_LESS_THAN0_STATE_BITS_OFFSET + 1] & 0b1)) == 0 /* is-not-state_0 RESULT-UNBOXED */) {
                            $frame.setObject($sp - 2, value);
                        } else {
                            $frame.setBoolean($sp - 2, value);
                        }
                        return;
                    }
                }
                $frame.setObject($sp - 2, SLLessThanNode.typeError($child0Value, $child1Value, ($this), ($bci)));
                return;
            }

            private static int storeLocalInitialization(VirtualFrame frame, int localIdx, int localTag, int sourceSlot) {
                Object value = frame.getValue(sourceSlot);
                if (localTag == 1 /* LONG */ && value instanceof Long) {
                    frame.setLong(localIdx, (long) value);
                    return 1 /* LONG */;
                }
                if (localTag == 5 /* BOOLEAN */ && value instanceof Boolean) {
                    frame.setBoolean(localIdx, (boolean) value);
                    return 5 /* BOOLEAN */;
                }
                frame.setObject(localIdx, value);
                return 0 /* OBJECT */;
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
        @GeneratedBy(SLOperations.class)
        private static final class WrappedIOException extends RuntimeException {

            WrappedIOException(IOException ex) {
                super(ex);
            }

        }
    }
    @GeneratedBy(SLOperations.class)
    private static final class WrappedIOException extends RuntimeException {

        WrappedIOException(IOException ex) {
            super(ex);
        }

    }
    private static final class Counter {

        public int count;

    }
}
