package com.oracle.truffle.dsl.processor.operations;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationsData.Operation;

class OperationsBytecodeCodeGenerator {

    final int BC_CONSTS_TARGET_LENGTH = 2;

    private final TruffleTypes types;
    private final ProcessorContext context;

    private CodeTreeBuilder builder;

    private static final int COMMON_OP_PRIM_JUMP_FALSE = 0;
    private static final int COMMON_OP_PRIM_JUMP_UNCOND = 1;
    private static final int COMMON_OP_PRIM_POP = 2;
    static String[] commonOpcodeNames = new String[]{
                    "PRIM_JUMP_FALSE",
                    "PRIM_JUMP_UNCOND",
                    "PRIM_POP",
    };

    CodeVariableElement[] commonOpcodeConstants;

    private int labelCounter = 0;
    private int backrefCounter = 0;

    private final DeclaredType byteArraySupportType;

    private final CodeTree leBytes;

    OperationsBytecodeCodeGenerator(TruffleTypes types, ProcessorContext context) {
        this.types = types;
        this.context = context;

        byteArraySupportType = context.getDeclaredType("com.oracle.truffle.api.memory.ByteArraySupport");
        leBytes = CodeTreeBuilder.createBuilder() //
                        .startCall(CodeTreeBuilder.singleType(byteArraySupportType), "littleEndian") //
                        .end().build();
    }

    class Builder {

        private final CodeTreeBuilder builder;

        public Builder(CodeTreeBuilder builder) {
            this.builder = builder;
        }

        public void buildOperation(OperationsData.Operation operation) {
            switch (operation.type) {
                case CUSTOM:
                case PRIM_CONST_OBJECT:
                case PRIM_LOAD_LOCAL:
                case PRIM_STORE_LOCAL:
                case PRIM_LOAD_ARGUMENT:
                case PRIM_RETURN:
                    buildSimple(operation);
                    break;
                case PRIM_BLOCK:
                    buildBlock();
                    break;
                case PRIM_IF_THEN:
                    buildIfThen();
                    break;
                case PRIM_IF_THEN_ELSE:
                    buildIfThenElse();
                    break;
                case PRIM_WHILE:
                    buildWhile();
                    break;
                default:
                    // TODO
                    break;
            }
        }

        private void buildSimple(Operation operation) {
            for (int i = 0; i < operation.children; i++) {
                buildChildCall(i);
            }

            buildOp(operation, 0);

            int argIdx = 0;
            for (TypeMirror argType : operation.getArguments(types, context)) {
                switch (argType.getKind()) {
                    case BYTE:
                        putByte("(byte) arguments[" + argIdx + "]");
                        break;
                    case BOOLEAN:
                        putByte("((boolean) arguments[" + argIdx + "]) ? 1 : 0");
                        break;
                    case CHAR:
                        putShort("(short)(char) arguments[" + argIdx + "]");
                        break;
                    case SHORT:
                        putShort("(short) arguments[" + argIdx + "]");
                        break;
                    case INT:
                        putInt("(int) arguments[" + argIdx + "]");
                        break;
                    case LONG:
                        putLong("(long) arguments[" + argIdx + "]");
                        break;
                    default:
                        if (argType.equals(types.OperationLabel)) {
                            // TODO: resolve
                        } else {
                            // TODO: constant pool
                        }
                        putShort("(short) 0");
                        break;
                }
                argIdx++;
            }
        }

        private void buildBlock() {
            builder.startFor();
            builder.string("int i = 0; i < children.length; i++");
            builder.end();

            builder.startBlock();

            buildChildCall("i");

            builder.end();
        }

        private void buildIfThen() {
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int afterBackref = reserveDestination();

            buildChildCall(1);

            fillReservedDestination(afterBackref);
        }

        private void buildIfThenElse() {
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int elseBackref = reserveDestination();

            buildChildCall(1);

            buildCommonOp(COMMON_OP_PRIM_JUMP_UNCOND);
            int afterBackref = reserveDestination();

            fillReservedDestination(elseBackref);

            buildChildCall(2);

            fillReservedDestination(afterBackref);
        }

        private void buildWhile() {

            int startLabel = saveLabel();
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int afterBackref = reserveDestination();

            buildChildCall(1);

            buildCommonOp(COMMON_OP_PRIM_JUMP_UNCOND);
            fillSavedLabel(startLabel);

            fillReservedDestination(afterBackref);
        }

        private void buildChildCall(int id) {
            buildChildCall("" + id);
        }

        private void buildChildCall(String id) {
            builder.startStatement();
            builder.string("bci = ");
            builder.startCall("children[" + id + "]", builder.findMethod());
            builder.string("bc");
            builder.string("bci");
            builder.end();
            builder.end();
        }

        private void buildCommonOp(int id) {
            builder.startStatement();
            builder.string("bc[bci++] = " + commonOpcodeConstants[id].getName());
            builder.end();
        }

        private void buildOp(Operation operation, int id) {
            builder.startStatement();
            builder.string("bc[bci++] = " + operation.getOpcodeConstant()[id].getName());
            builder.end();
        }

        private int saveLabel() {
            int id = labelCounter++;

            builder.startStatement();
            builder.string("int label_" + id + " = bci");
            builder.end();

            return id;
        }

        private int reserveDestination() {
            int id = backrefCounter++;

            builder.startStatement();
            builder.string("int backref_" + id + " = bci");
            builder.end();

            builder.startStatement();
            builder.string("bci += " + BC_CONSTS_TARGET_LENGTH);
            builder.end();

            return id;
        }

        private void fillReservedDestination(int target) {
            fillLabel("backref_" + target, "bci");
        }

        private void fillSavedLabel(int label) {
            fillLabel("bci", "label_" + label);
            builder.startStatement();
            builder.string("bci += " + BC_CONSTS_TARGET_LENGTH);
            builder.end();
        }

        private void fillLabel(String dest, String label) {
            builder.startStatement();
            CodeTree le = CodeTreeBuilder.createBuilder().startCall(CodeTreeBuilder.singleType(byteArraySupportType), "littleEndian").end().build();
            builder.startCall(le, "putShort");
            builder.string("bc");
            builder.string(dest);
            builder.string("(short) " + label);
            builder.end(2);
        }

        private void putByte(String value) {
            builder.startStatement();
            builder.string("bc[bci++] = " + value);
            builder.end();
        }

        private void putBytesHelper(String method, String value, int length) {
            builder.startStatement();
            builder.startCall(leBytes, method);
            builder.string("bc");
            builder.string("bci");
            builder.string(value);
            builder.end(2);

            builder.startStatement();
            builder.string("bci += " + length);
            builder.end();
        }

        private void putShort(String value) {
            putBytesHelper("putShort", value, 2);
        }

        private void putInt(String value) {
            putBytesHelper("putInt", value, 4);
        }

        private void putLong(String value) {
            putBytesHelper("putLong", value, 8);
        }
    }

    abstract class ReaderCommon {
        protected final CodeTreeBuilder builder;

        protected boolean doNotBreak = false;

        protected ReaderCommon(CodeTreeBuilder builder) {
            this.builder = builder;
        }

        void buildCommonOperations() {
            startOperation(commonOpcodeConstants[COMMON_OP_PRIM_JUMP_FALSE].getName());
            buildJumpFalseOperation();
            endOperation();

            startOperation(commonOpcodeConstants[COMMON_OP_PRIM_JUMP_UNCOND].getName());
            buildJumpUncondOperation();
            endOperation();

            startOperation(commonOpcodeConstants[COMMON_OP_PRIM_POP].getName());
            buildPopOperation();
            endOperation();
        }

        void buildOperation(Operation op) {
            if (op.type.numOpcodes == 0)
                return;

            doNotBreak = false;
            startOperation(op.getOpcodeConstant()[0].getName());
            switch (op.type) {
                case CUSTOM:
                    buildCustomOperation(op);
                    break;
                case PRIM_LOAD_LOCAL:
                    buildLoadLocal(op);
                    break;
                case PRIM_STORE_LOCAL:
                    buildStoreLocal(op);
                    break;
                case PRIM_CONST_OBJECT:
                    buildConstObject(op);
                    break;
                case PRIM_LOAD_ARGUMENT:
                    buildLoadArgument(op);
                    break;
                case PRIM_RETURN:
                    buildReturn(op);
                    break;
            }
            endOperation();
        }

        private void getBytesHelper(String var, String method, String offset) {
            builder.startStatement();
            builder.string(var + " = ");
            builder.startCall(leBytes, method);
            builder.string("bc");
            builder.string("bci + " + offset);
            builder.end(2);
        }

        protected void getByte(String var, String offset) {
            builder.statement("byte " + var + " = bc[bci + " + offset + "]");
        }

        protected void getShort(String var, String offset) {
            getBytesHelper(var, "getShort", offset);
        }

        protected void getInt(String var, String offset) {
            getBytesHelper(var, "getInt", offset);
        }

        protected abstract void buildJumpFalseOperation();

        protected abstract void buildJumpUncondOperation();

        protected abstract void buildPopOperation();

        protected abstract void buildCustomOperation(Operation op);

        protected abstract void buildConstObject(Operation op);

        protected abstract void buildLoadLocal(Operation op);

        protected abstract void buildStoreLocal(Operation op);

        protected abstract void buildLoadArgument(Operation op);

        protected abstract void buildReturn(Operation op);

        private void startOperation(String name) {
            builder.startCase().string(name).end();
            builder.startBlock();
        }

        private void endOperation() {
            if (!doNotBreak) {
                builder.statement("break");
            }
            builder.end();
        }
    }

    class Executor extends ReaderCommon {

        Executor(CodeTreeBuilder builder) {
            super(builder);
        }

        @Override
        protected void buildJumpFalseOperation() {
            popValue("boolean", "condition");

            builder.startIf().string("condition").end();

            builder.startBlock();
            gotoRelative(1 + BC_CONSTS_TARGET_LENGTH);
            builder.end();

            builder.startElseBlock();
            gotoDestination(1);
            builder.end();

        }

        @Override
        protected void buildJumpUncondOperation() {
            gotoDestination(1);
        }

        @Override
        protected void buildPopOperation() {
            builder.statement("stack[--sp] = null");
            gotoRelative(1);
        }

        @Override
        protected void buildCustomOperation(Operation op) {
            // read all child values

            for (int i = op.children - 1; i >= 0; i--) {
                popValue("Object", "value" + i);
            }

            // TODO: read all arguments

            // TODO: invoke

            // TODO: push result

            gotoRelative(1);
        }

        @Override
        protected void buildConstObject(Operation op) {

            getShort("int index", "1");
            pushValue("locals[index]");

            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadLocal(Operation op) {

            getShort("int index", "1");
            pushValue("locals[index]");

            gotoRelative(1 + 2);
        }

        @Override
        protected void buildStoreLocal(Operation op) {

            getShort("int index", "1");
            popValue("Object", "value");
            builder.statement("locals[index] = value");

            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadArgument(Operation op) {
            getShort("int index", "1");
            pushValue("frame.getArguments()[index]");

            gotoRelative(1 + 2);
        }

        @Override
        protected void buildReturn(Operation op) {
            popValue("Object", "rv");
            builder.statement("returnValue = rv");
            builder.statement("break loop");
            doNotBreak = true;
        }

        private void gotoDestination(int offset) {
            gotoDestination("" + offset);
        }

        private void gotoDestination(String offset) {
            getShort("nextBci", offset);
        }

        private void gotoRelative(int offset) {
            builder.statement("nextBci = bci + " + offset);
        }

        private void popValue(String type, String name) {
            String cast = type.equals("Object") ? "" : "(" + type + ") ";
            builder.declaration(type, name, cast + "stack[--sp]");
        }

        private void pushValue(String value) {
            builder.statement("stack[sp++] = " + value);
        }

    }

    class Dumper extends ReaderCommon {

        protected Dumper(CodeTreeBuilder builder) {
            super(builder);
        }

        @Override
        protected void buildJumpFalseOperation() {
            appendOpcode("brfalse");
            getShort("int dest", "1");
            append("dest");
            gotoRelative(1 + BC_CONSTS_TARGET_LENGTH);
        }

        @Override
        protected void buildJumpUncondOperation() {
            appendOpcode("br");
            getShort("int dest", "1");
            append("dest");
            gotoRelative(1 + BC_CONSTS_TARGET_LENGTH);
        }

        @Override
        protected void buildPopOperation() {
            appendOpcode("pop");
            gotoRelative(1);
        }

        @Override
        protected void buildCustomOperation(Operation op) {
            // TODO Auto-generated method stub
            appendOpcode("op");
            append("\"" + op.getName() + "\"");
            gotoRelative(1);
        }

        @Override
        protected void buildConstObject(Operation op) {
            appendOpcode("ldconst");
            getShort("int index", "1");
            append("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadLocal(Operation op) {
            appendOpcode("ldloc");
            getShort("int index", "1");
            append("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildStoreLocal(Operation op) {
            appendOpcode("stloc");
            getShort("int index", "1");
            appendDest("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadArgument(Operation op) {
            appendOpcode("ldarg");
            getShort("int index", "1");
            append("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildReturn(Operation op) {
            appendOpcode("return");
            gotoRelative(1);
        }

        private void appendOpcode(String op) {
            String opPadded = String.format("%-8s", op);
            append("\"" + opPadded + "\"");
        }

        private void appendDest(String code) {
            append("String.format(\"%04x\", " + code + ")");
        }

        private void append(String code) {
            builder.statement("sb.append(" + code + ")");
            builder.statement("sb.append(' ')");
        }

        private void gotoRelative(int offset) {
            builder.statement("bci += " + offset);
        }

    }
}
