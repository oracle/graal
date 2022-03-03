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

    private final CodeTree leBytes;

    OperationsBytecodeCodeGenerator(TruffleTypes types, ProcessorContext context) {
        this.types = types;
        this.context = context;

        leBytes = CodeTreeBuilder.singleString("LE_BYTES");
    }

    private CodeTree castToLabel(String arg) {
        return CodeTreeBuilder.createBuilder().cast(types.BuilderOperationLabel, CodeTreeBuilder.singleString(arg)).build();
    }

    private static final int RV_ALWAYS = 0;
    private static final int RV_NEVER = 1;
    private static final int RV_DIVERGES = 2;

    class ReturnsValueChecker {
        private final CodeTreeBuilder builder;

        public ReturnsValueChecker(CodeTreeBuilder builder) {
            this.builder = builder;
        }

        public void buildOperation(OperationsData.Operation operation) {
            switch (operation.type) {
                case CUSTOM:
                    buildSimple(operation, operation.returnsValue ? RV_ALWAYS : RV_NEVER);
                    break;
                case PRIM_STORE_LOCAL:
                case PRIM_LABEL:
                    buildSimple(operation, RV_NEVER);
                    break;
                case PRIM_CONST_OBJECT:
                case PRIM_LOAD_LOCAL:
                case PRIM_LOAD_ARGUMENT:
                    buildSimple(operation, RV_ALWAYS);
                    break;
                case PRIM_BRANCH:
                case PRIM_RETURN:
                    buildSimple(operation, RV_DIVERGES);
                    break;
                case PRIM_IF_THEN:
                case PRIM_WHILE:
                    buildCondAndBody();
                    break;
                case PRIM_IF_THEN_ELSE:
                    buildIfThenElse();
                    break;
                case PRIM_BLOCK:
                    buildBlock();
                    break;
                default:
                    throw new UnsupportedOperationException("unknown operation type: " + operation.type);
            }
        }

        private void buildSimple(OperationsData.Operation operation, int rv) {
            for (int i = 0; i < operation.children; i++) {
                builder.startAssert().string("children[" + i + "].returnsValue != " + RV_NEVER).end();
            }

            builder.statement("this.returnsValue = " + rv);
        }

        private void buildCondAndBody() {
            builder.startAssert().string("children[0].returnsValue != " + RV_NEVER).end();
            builder.statement("this.returnsValue = " + RV_NEVER);
        }

        private void buildIfThenElse() {
            builder.startAssert().string("children[0].returnsValue != " + RV_NEVER).end();
            builder.startIf().string("children[1].returnsValue != " + RV_NEVER + " && children[2].returnsValue != " + RV_NEVER).end();
            builder.startBlock();
            builder.statement("this.returnsValue = " + RV_ALWAYS);
            builder.end();
            builder.startElseBlock();
            builder.statement("this.returnsValue = " + RV_NEVER);
            builder.end();
        }

        private void buildBlock() {
            builder.statement("this.returnsValue = children[children.length - 1].returnsValue");
        }
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
                case PRIM_LABEL:
                    buildLabel();
                    break;
                case PRIM_BRANCH:
                    buildBranch();
                    break;
                default:
                    throw new IllegalArgumentException("unknown operation type: " + operation.type);
            }
        }

        private void buildSimple(Operation operation) {
            for (int i = 0; i < operation.children; i++) {
                builder.lineComment("child" + i);
                buildChildCall(i);
            }

            builder.lineComment("opcode");
            buildOp(operation, 0);

            int argIdx = 0;
            for (TypeMirror argType : operation.getArguments(types, context)) {
                builder.lineComment("argument" + argIdx + ": " + argType.toString());
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
                        builder.declaration("short", "index", "(short) constPool.size()");
                        builder.statement("constPool.add(arguments[" + argIdx + "])");
                        putShort("index");
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

            builder.startIf().string("i != children.length - 1").end();
            builder.startBlock();
            buildPopChild("i");
            builder.end();

            builder.end();
        }

        private void buildIfThen() {
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int afterBackref = reserveDestination();

            buildChildCall(1);
            buildPopChild(1);

            fillReservedDestination(afterBackref);
        }

        private void buildIfThenElse() {
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int elseBackref = reserveDestination();

            buildChildCall(1);

            builder.startIf().string("returnsValue != " + RV_ALWAYS).end();
            builder.startBlock();
            buildPopChild(1);
            builder.end();

            buildCommonOp(COMMON_OP_PRIM_JUMP_UNCOND);
            int afterBackref = reserveDestination();

            fillReservedDestination(elseBackref);

            buildChildCall(2);

            builder.startIf().string("returnsValue != " + RV_ALWAYS).end();
            builder.startBlock();
            buildPopChild(2);
            builder.end();

            fillReservedDestination(afterBackref);
        }

        private void buildWhile() {

            int startLabel = saveLabel();
            buildChildCall(0);

            buildCommonOp(COMMON_OP_PRIM_JUMP_FALSE);
            int afterBackref = reserveDestination();

            buildChildCall(1);
            buildPopChild(1);

            buildCommonOp(COMMON_OP_PRIM_JUMP_UNCOND);
            fillSavedLabel(startLabel);

            fillReservedDestination(afterBackref);
        }

        private void buildLabel() {
            builder.declaration(types.BuilderOperationLabel, "label", CodeTreeBuilder.createBuilder().cast(types.BuilderOperationLabel,
                            CodeTreeBuilder.singleString("arguments[0]")));
            builder.startStatement().startCall("label", "resolve").string("bc").string("bci").end(2);
        }

        private void buildBranch() {
            buildCommonOp(COMMON_OP_PRIM_JUMP_UNCOND);
            builder.declaration(types.BuilderOperationLabel, "lbl", castToLabel("arguments[0]"));
            builder.startStatement().startCall("lbl", "putValue").string("bc").string("bci").end(2);
            builder.statement("bci += " + BC_CONSTS_TARGET_LENGTH);
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
            builder.string("constPool");
            builder.end();
            builder.end();
        }

        private void buildPopChild(int id) {
            buildPopChild("" + id);
        }

        private void buildPopChild(String id) {
            builder.startIf();
            builder.string("children[" + id + "].returnsValue == " + RV_ALWAYS);
            builder.end();

            builder.startBlock();
            buildCommonOp(COMMON_OP_PRIM_POP);
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
            builder.startCall(leBytes, "putShort");
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

            builder.startStatement();
            if (op.returnsValue) {
                builder.string("Object result = ");
            }
            builder.startStaticCall(op.mainMethod);

            for (int i = 0; i < op.children; i++) {
                builder.string("value" + i);
            }

            builder.end(2);

            if (op.returnsValue) {
                pushValue("result");
            }

            gotoRelative(1);
        }

        @Override
        protected void buildConstObject(Operation op) {

            getShort("int index", "1");
            pushValue("constPool[index]");

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
            appendHexa("dest");
            gotoRelative(1 + BC_CONSTS_TARGET_LENGTH);
        }

        @Override
        protected void buildJumpUncondOperation() {
            appendOpcode("br");
            getShort("int dest", "1");
            appendHexa("dest");
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
            append("\"// (\" + constPool[index].getClass().getName() + \") \" + constPool[index]");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadLocal(Operation op) {
            appendOpcode("ldloc");
            getShort("int index", "1");
            appendHexa("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildStoreLocal(Operation op) {
            appendOpcode("stloc");
            getShort("int index", "1");
            appendHexa("index");
            gotoRelative(1 + 2);
        }

        @Override
        protected void buildLoadArgument(Operation op) {
            appendOpcode("ldarg");
            getShort("int index", "1");
            appendHexa("index");
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

        private void appendHexa(String code) {
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
