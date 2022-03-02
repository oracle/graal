package com.oracle.truffle.api.operation.test.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Stack;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.operation.OperationLabel;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.test.example.SlOperations.AddOperation;
import com.oracle.truffle.api.operation.test.example.SlOperations.LessThanOperation;

class SlOperationsBuilderImpl extends SlOperationsBuilder {

    public SlOperationsBuilderImpl() {
        reset();
    }

    static final byte PRIM_OP_NOP = 0;
    static final byte PRIM_OP_JUMP_FALSE = 1;
    static final byte PRIM_OP_UNCOND_JUMP = 2;
    static final byte PRIM_OP_CONST_OBJECT = 3;
    static final byte PRIM_OP_POP = 4;
    static final byte PRIM_OP_RETURN = 5;
    static final byte PRIM_OP_LOAD_ARGUMENT = 6;
    static final byte PRIM_OP_LOAD_LOCAL = 8;
    static final byte PRIM_OP_STORE_LOCAL = 9;
    static final byte OP_ADD_OPERATION = 20;
    static final byte OP_LESS_THAN_OPERATION = 21;

    static final int IMM_DEST_LENGTH = 2;

    static final void putDestination(byte[] bc, int bci, int value) {
        BYTES.putShort(bc, bci, (short) value);
    }

    static final int IMM_CONST_LENGTH = 2;

    static final void putConst(byte[] bc, int bci, Object value, ArrayList<Object> consts) {
        int index = consts.size();
        consts.add(value);
        BYTES.putShort(bc, bci, (short) index);
    }

    static final ByteArraySupport BYTES = ByteArraySupport.littleEndian();

    private static class SlOperationsOperationLabelImpl extends OperationLabel {
        final int pointer;

        static final SlOperationsBuilderImpl.SlOperationsOperationLabelImpl ZERO = new SlOperationsOperationLabelImpl(0);

        SlOperationsOperationLabelImpl(int ptr) {
            pointer = ptr;
        }
    }

    private static class SlOperationsBytecodeNode extends OperationsNode {

        final byte[] bc;
        final Object[] consts;

        public SlOperationsBytecodeNode(byte[] bc, Object[] consts) {
            this.bc = bc;
            this.consts = consts;
        }

        @Override
        public String dump() {
            return SlOperationsBuilderNode.dump(bc, consts);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return continueAt(frame, SlOperationsOperationLabelImpl.ZERO);
        }

        @Override
        public Object continueAt(VirtualFrame frame, OperationLabel startIndex) {

            final Object[] stack = new Object[1024];
            final Object[] locals = new Object[1024];

            int sp = 0;
            int bci = ((SlOperationsBuilderImpl.SlOperationsOperationLabelImpl) startIndex).pointer;

            Object returnValue;
            loop: while (true) {
                int nextBci;

                // System.out.println(" op: " + bc[bci]);
                switch (bc[bci]) {
                    case PRIM_OP_NOP:
                        nextBci = bci + 1;
                        break;
                    case PRIM_OP_JUMP_FALSE: {
                        boolean value = (boolean) stack[sp - 1];
                        sp -= 1;
                        if (value) {
                            nextBci = bci + 3;
                        } else {
                            nextBci = BYTES.getShort(bc, bci + 1);
                        }
                        break;
                    }
                    case PRIM_OP_UNCOND_JUMP: {
                        nextBci = BYTES.getShort(bc, bci + 1);
                        break;
                    }
                    case PRIM_OP_CONST_OBJECT: {
                        Object value = consts[BYTES.getShort(bc, bci + 1)];
                        stack[sp] = value;
                        sp += 1;
                        nextBci = bci + 3;
                        break;
                    }
                    case PRIM_OP_POP:
                        sp -= 1;
                        nextBci = bci + 1;
                        break;
                    case PRIM_OP_LOAD_ARGUMENT: {
                        int index = BYTES.getShort(bc, bci + 1);
                        Object value = frame.getArguments()[index];
                        stack[sp++] = value;
                        nextBci = bci + 3;
                        break;
                    }
                    case PRIM_OP_LOAD_LOCAL: {
                        int index = BYTES.getShort(bc, bci + 1);
                        Object value = locals[index];
                        stack[sp++] = value;
                        nextBci = bci + 3;
                        break;
                    }
                    case PRIM_OP_STORE_LOCAL: {
                        int index = BYTES.getShort(bc, bci + 1);
                        Object value = stack[--sp];
                        locals[index] = value;
                        nextBci = bci + 3;
                        break;
                    }
                    case PRIM_OP_RETURN: {
                        returnValue = stack[sp - 1];
                        break loop;
                    }
                    case OP_ADD_OPERATION: {
                        Object arg1 = stack[sp - 2];
                        Object arg2 = stack[sp - 1];
                        Object result = AddOperation.add((long) arg1, (long) arg2);
                        stack[sp - 2] = result;
                        sp -= 1;
                        nextBci = bci + 1;
                        break;
                    }
                    case OP_LESS_THAN_OPERATION: {
                        Object arg1 = stack[sp - 2];
                        Object arg2 = stack[sp - 1];
                        Object result = LessThanOperation.lessThan((long) arg1, (long) arg2);
                        stack[sp - 2] = result;
                        sp -= 1;
                        nextBci = bci + 1;
                        break;
                    }
                    default:
                        throw new RuntimeException("invalid opcode: " + bc[bci]);
                }
                // System.out.printf(" stack: ");
                // for (int i = 0; i < sp; i++) {
                // System.out.printf("(%s) %s, ", stack[i].getClass().getName(), stack[i]);
                // }
                // System.out.println();
                bci = nextBci;
            }

            return returnValue;
        }
    }

    Stack<SlOperationsBuilderNode.Type> typeStack = new Stack<>();
    Stack<ArrayList<SlOperationsBuilderNode>> childStack = new Stack<>();
    Stack<Object[]> argStack = new Stack<>();

    static class SlOperationsLabel extends OperationLabel {
        private boolean marked = false;
        private ArrayList<Integer> toBackfill;
        private boolean hasValue = false;
        private int value = 0;

        void resolve(byte[] bc, int labelValue) {
            assert !hasValue;
            hasValue = true;
            value = labelValue;

            if (toBackfill != null) {
                for (int bci : toBackfill) {
                    putDestination(bc, bci, value);
                }
                toBackfill = null;
            }
        }

        void putValue(byte[] bc, int bci) {
            if (hasValue) {
                putDestination(bc, bci, value);
            } else {
                if (toBackfill == null)
                    toBackfill = new ArrayList<>();
                toBackfill.add(bci);
            }
        }
    }

    @Override
    public void reset() {
        typeStack.clear();
        childStack.clear();
        childStack.add(new ArrayList<>());
        argStack.clear();
    }

    @Override
    public OperationsNode build() {
        assert childStack.size() == 1;
        SlOperationsBuilderNode[] operations = childStack.get(0).toArray(EMPTY_CHILDREN);
        SlOperationsBuilderNode rootNode = new SlOperationsBuilderNode(SlOperationsBuilderNode.Type.BLOCK, new Object[0], operations);

        System.out.printf("resulting tree: %s\n", rootNode);

        byte[] bc = new byte[65535];
        ArrayList<Object> consts = new ArrayList<>();
        int len = rootNode.build(bc, 0, consts);
        Object[] constsArr = consts.toArray(Object[]::new);
        byte[] bcCopy = Arrays.copyOf(bc, len);

        SlOperationsBuilderImpl.SlOperationsBytecodeNode bcnode = new SlOperationsBytecodeNode(bcCopy, constsArr);
        return bcnode;
    }

    private static final SlOperationsBuilderNode[] EMPTY_CHILDREN = new SlOperationsBuilderNode[0];

    private void beginOperation(SlOperationsBuilderNode.Type type, Object... arguments) {
        assert arguments.length == type.argCount;
        typeStack.add(type);
        childStack.add(new ArrayList<>());
        argStack.add(arguments);
    }

    private void endOperation(SlOperationsBuilderNode.Type type) {
        SlOperationsBuilderNode.Type type2 = typeStack.remove(typeStack.size() - 1);
        assert type == type2 : "unbalanced begin/ends";
        Object[] args = argStack.remove(argStack.size() - 1);
        SlOperationsBuilderNode[] children = childStack.remove(childStack.size() - 1).toArray(EMPTY_CHILDREN);

        SlOperationsBuilderNode node = new SlOperationsBuilderNode(type, args, children);
        childStack.get(childStack.size() - 1).add(node);
    }

    private void emitOperation(SlOperationsBuilderNode.Type type, Object... args) {
        SlOperationsBuilderNode node = new SlOperationsBuilderNode(type, args, EMPTY_CHILDREN);
        childStack.get(childStack.size() - 1).add(node);
    }

    @Override
    public void beginIfThen() {
        beginOperation(SlOperationsBuilderNode.Type.IF_THEN);
    }

    @Override
    public void endIfThen() {
        endOperation(SlOperationsBuilderNode.Type.IF_THEN);
    }

    @Override
    public void beginIfThenElse() {
        beginOperation(SlOperationsBuilderNode.Type.IF_THEN_ELSE);
    }

    @Override
    public void endIfThenElse() {
        endOperation(SlOperationsBuilderNode.Type.IF_THEN_ELSE);
    }

    @Override
    public void beginWhile() {
        beginOperation(SlOperationsBuilderNode.Type.WHILE);
    }

    @Override
    public void endWhile() {
        endOperation(SlOperationsBuilderNode.Type.WHILE);
    }

    @Override
    public void beginBlock() {
        beginOperation(SlOperationsBuilderNode.Type.BLOCK);
    }

    @Override
    public void endBlock() {
        endOperation(SlOperationsBuilderNode.Type.BLOCK);
    }

    @Override
    public void emitConstObject(Object value) {
        emitOperation(SlOperationsBuilderNode.Type.CONST_OBJECT, value);
    }

    @Override
    public void emitConstLong(long value) {
        emitOperation(SlOperationsBuilderNode.Type.CONST_LONG, value);
    }

    @Override
    public void emitLoadLocal(int index) {
        emitOperation(SlOperationsBuilderNode.Type.LOAD_LOCAL, index);
    }

    @Override
    public void beginStoreLocal(int index) {
        beginOperation(SlOperationsBuilderNode.Type.STORE_LOCAL, index);
    }

    @Override
    public void endStoreLocal() {
        endOperation(SlOperationsBuilderNode.Type.STORE_LOCAL);
    }

    @Override
    public void emitLoadArgument(int index) {
        emitOperation(SlOperationsBuilderNode.Type.LOAD_ARGUMENT, index);
    }

    @Override
    public void beginAddOperation() {
        beginOperation(SlOperationsBuilderNode.Type.OP_ADD_OPERATION);
    }

    @Override
    public void endAddOperation() {
        endOperation(SlOperationsBuilderNode.Type.OP_ADD_OPERATION);
    }

    @Override
    public void beginReturn() {
        beginOperation(SlOperationsBuilderNode.Type.RETURN);
    }

    @Override
    public void endReturn() {
        endOperation(SlOperationsBuilderNode.Type.RETURN);
    }

    @Override
    public void beginLessThanOperation() {
        beginOperation(SlOperationsBuilderNode.Type.OP_LESS_THAN_OPERATION);
    }

    @Override
    public void endLessThanOperation() {
        endOperation(SlOperationsBuilderNode.Type.OP_LESS_THAN_OPERATION);
    }

    @Override
    public OperationLabel createLabel() {
        return new SlOperationsLabel();
    }

    @Override
    public void markLabel(OperationLabel label) {
        SlOperationsLabel lbl = (SlOperationsLabel) label;
        assert !lbl.marked;
        lbl.marked = true;
        emitOperation(SlOperationsBuilderNode.Type.LABEL, label);
    }

    @Override
    public void emitBranch(OperationLabel label) {
        emitOperation(SlOperationsBuilderNode.Type.BRANCH, label);
    }
}