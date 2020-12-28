package com.oracle.truffle.api.operation.test.example;

import java.util.Arrays;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.Operation.Kind;
import com.oracle.truffle.api.operation.OperationPointer;
import com.oracle.truffle.api.operation.OperationsBuilder;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.operation.test.example.TestOperations.Add;
import com.oracle.truffle.api.operation.test.example.TestOperations.Body;
import com.oracle.truffle.api.operation.test.example.TestOperations.ConstantInt;
import com.oracle.truffle.api.operation.test.example.TestOperations.ConstantString;
import com.oracle.truffle.api.operation.test.example.TestOperations.GreaterThan;
import com.oracle.truffle.api.operation.test.example.TestOperations.If;
import com.oracle.truffle.api.operation.test.example.TestOperations.ReadLocal;
import com.oracle.truffle.api.operation.test.example.TestOperations.SourceOffsetProvider;

abstract class TestOperationsBuilder extends OperationsBuilder {

    private TestOperationsBuilder() {
    }

    private void startTag(int opCode) {
    }

    private void endTag(int opCode) {
    }

    public void startSource(Object source, SourceOffsetProvider provider) {
    }

    public void endSource() {
    }

    private void endSourceOp() {

    }

    private boolean verifyStart(int opAdd) {
        return true;
    }

    private boolean verifyEnd(int opAdd) {
        return true;
    }

    public void startBody() {

    }

    public void endBody(int localCount, int argumentCount) {

    }

    public void startStatement() {

    }

    public void endStatement() {

    }

    public void startReturn() {

    }

    public void startAdd() {

    }

    public void endAdd() {

    }

    public void startWhile() {
    }

    public void endWhile() {
    }

    public void startIf() {
    }

    public void startGreaterThan() {
    }

    public void startLessThan() {
    }

    public void endLessThan() {
    }

    public void endGreaterThan() {
    }

    public void endIf() {
    }

    public void endReturn() {
    }

    public void startLocalWrite(int localIndex) {
    }

    public void endLocalWrite() {
    }

    public void pushLocalRead(int varIndex) {
    }

    public void pushNoOp() {
    }

    public int resolveVariableRead(String identifier) {
        return 0;
    }

    public void pushConstantInt(int value) {
    }

    public void pushBreakWhile(int skipIndex) {
    }

    public void pushContinueWhile(int skipIndex) {
    }

    public abstract OperationsNode build();

    public void reset() {
    }

    public static TestOperationsBuilder createASTBuilder() {
        return new ASTInterpreterBuilder();
    }

    public static TestOperationsBuilder createBytecodeBuilder() {
        return new BytecodeInterpreterBuilder();
    }

    @ValueType
    static final class ASTPointer extends OperationPointer {

        ASTNode node;

        ASTPointer(ASTNode node) {
            this.node = node;
        }

        @Override
        public boolean parent() {
            this.node = (ASTNode) node.getParent();
            return this.node != null;
        }

        @Override
        public void child(int childIndex) {
            node = node.childAt(childIndex);
        }

        @Override
        public int childCount() {
            return node.childCount();
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public Class<?> get() throws NoSuchElementException {
            return null;
        }

        @Override
        public Kind getKind() throws NoSuchElementException {
            return get().getAnnotation(Operation.class).value();
        }

        @Override
        public <T> T getConstant(Class<T> expectedClass, int constantIndex) throws NoSuchElementException {
            return expectedClass.cast(node.getConstant(constantIndex));
        }

    }

    static final class AST extends OperationsNode {

        @Child ASTNode content;

        AST(ASTNode content) {
            this.content = content;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return content.execute(frame, null);
            } catch (ReturnException e) {
                return e.value;
            }
        }

        @Override
        public OperationPointer createPointer() {
            return new ASTPointer(content);
        }

        @Override
        public Object continueAt(VirtualFrame frame, OperationPointer index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationsNode copyUninitialized() {
            throw new UnsupportedOperationException();
        }

    }

    static abstract class ASTNode extends Node {

        abstract int execute(VirtualFrame frame, int[] locals);

        void onBuild() {
        }

        ASTNode childAt(int childIndex) {
            int index = 0;
            for (Node child : getChildren()) {
                if (child instanceof ASTNode) {
                    if (index == childIndex) {
                        return (ASTNode) child;
                    }
                    index++;
                }
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new ArrayIndexOutOfBoundsException(String.valueOf(childIndex));
        }

        @TruffleBoundary
        int childCount() {
            int count = 0;
            for (Node node : getChildren()) {
                if (node instanceof ASTNode) {
                    count++;
                }
            }
            return count;
        }

        Class<?> getOperation() {
            return null;
        }

        Object getConstant(int index) {
            throw new ArrayIndexOutOfBoundsException();
        }

    }

    static final class AddNode extends ASTNode {

        @Child private ASTNode left;
        @Child private ASTNode right;

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            int leftValue = left.execute(frame, locals);
            int rightValue = right.execute(frame, locals);
            return TestOperations.Add.doAdd(leftValue, rightValue);
        }

    }

    static final class GreaterThanNode extends ASTNode {

        @Child private ASTNode left;
        @Child private ASTNode right;

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            int leftValue = left.execute(frame, locals);
            int rightValue = right.execute(frame, locals);
            return TestOperations.GreaterThan.doDefault(leftValue, rightValue) ? 1 : 0;
        }

    }

    static final class ReturnNode extends ASTNode {

        @Child ASTNode operand;

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            int v = operand.execute(frame, locals);
            throw new ReturnException(v);
        }

    }

    static final class ReturnException extends ControlFlowException {

        final Object value;

        ReturnException(Object value) {
            this.value = value;
        }
    }

    static final class BodyNode extends ASTNode {

        @Child private ASTNode child;
        @CompilationFinal private int localCount;
        @CompilationFinal private int argumentCount;

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            int[] newLocals = Body.locals(frame, locals, localCount, argumentCount);
            return child.execute(frame, newLocals);
        }

    }

    static final class ConstantNode extends ASTNode {

        private final int constantValue;

        ConstantNode(int constantValue) {
            this.constantValue = constantValue;
        }

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            return constantValue;
        }

    }

    static final class LocalReadNode extends ASTNode {

        private final int index;

        LocalReadNode(int index) {
            this.index = index;
        }

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            return ReadLocal.doDefault(locals, index);
        }

    }

    static final class IfNode extends ASTNode {

        @Child private ASTNode condition;
        @Child private ASTNode thenNode;
        @Child private ASTNode elseNode;

        @Override
        int execute(VirtualFrame frame, int[] locals) {
            boolean cond = condition.execute(frame, locals) != 0;
            if (TestOperations.If.execute(cond)) {
                return thenNode.execute(frame, locals);
            } else {
                return elseNode.execute(frame, locals);
            }
        }

    }

    static final class ASTInterpreterBuilder extends TestOperationsBuilder {

        final ASTNode[] nodeStack = new ASTNode[1024];

        int currentIndex = -1;

        @Override
        public void pushConstantInt(int value) {
            pushNode(new ConstantNode(value));
        }

        @Override
        public void pushLocalRead(int localIndex) {
            assert assertEnclosingBody();
            pushNode(new LocalReadNode(localIndex));
        }

        @Override
        public void startBody() {
            pushNode(new BodyNode());
        }

        @Override
        public void startGreaterThan() {
            pushNode(new GreaterThanNode());
        }

        @Override
        public void endGreaterThan() {
            ASTNode right = popNode(ASTNode.class);
            ASTNode left = popNode(ASTNode.class);
            GreaterThanNode op = peekNode(GreaterThanNode.class);
            op.left = left;
            op.right = right;
        }

        @Override
        public void endBody(int localCount, int argumentCount) {
            ASTNode child = popNode(ASTNode.class);
            BodyNode locals = peekNode(BodyNode.class);
            locals.child = child;
            locals.localCount = localCount;
            locals.argumentCount = argumentCount;
        }

        @Override
        public void startReturn() {
            pushNode(new ReturnNode());
        }

        private boolean assertEnclosingBody() {
            BodyNode body = findNode(BodyNode.class);
            if (body == null) {
                throw new AssertionError("No enclosing Body operation found but expected by operations use of LocalInput.");
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        private <T extends ASTNode> T findNode(Class<T> nodeClass) {
            for (int i = 0; i < nodeStack.length; i++) {
                ASTNode node = nodeStack[i];
                if (nodeClass.isInstance(node)) {
                    return nodeClass.cast(node);
                }
            }
            return null;
        }

        @Override
        public void endReturn() {
            ASTNode node = popNode(ASTNode.class);
            peekNode(ReturnNode.class).operand = node;
        }

        @Override
        public void startAdd() {
            pushNode(new AddNode());
        }

        @Override
        public void endAdd() {
            ASTNode right = popNode(ASTNode.class);
            ASTNode left = popNode(ASTNode.class);
            AddNode add = peekNode(AddNode.class);
            add.left = left;
            add.right = right;
        }

        @Override
        public void startIf() {
            pushNode(new IfNode());
        }

        @Override
        public void endIf() {
            ASTNode elseBranch = popNode(ASTNode.class);
            ASTNode thenBranch = popNode(ASTNode.class);
            ASTNode condition = popNode(ASTNode.class);
            IfNode op = peekNode(IfNode.class);
            op.condition = condition;
            op.thenNode = thenBranch;
            op.elseNode = elseBranch;
        }

        private void pushNode(ASTNode node) {
            nodeStack[++currentIndex] = node;
        }

        private <T extends ASTNode> T popNode(Class<T> nodeClass) {
            if (currentIndex == -1) {
                throw new IllegalStateException("No nodes on stack.");
            }
            ASTNode node = nodeStack[currentIndex];
            nodeStack[currentIndex--] = null;
            return nodeClass.cast(node);
        }

        private <T extends ASTNode> T peekNode(Class<T> nodeClass) {
            return nodeClass.cast(nodeStack[currentIndex]);
        }

        @Override
        public OperationsNode build() {
            ASTNode node = popNode(ASTNode.class);
            AST ast = new AST(node);
            node.adoptChildren();
            node.onBuild();
            onBuild(ast);
            return ast;
        }

        @Override
        public void reset() {
            if (currentIndex != -1) {
                Arrays.fill(nodeStack, 0, currentIndex, null);
            }
        }

        @Override
        public String toString() {
            return "ASTInterpreterBuilder";
        }

    }

    static final class BytecodePointer extends OperationPointer {

        private final byte[] bytecodes;
        private int bci;

        BytecodePointer(byte[] bytecodes) {
            this.bytecodes = bytecodes;
        }

        @Override
        public boolean parent() {
            return false;
        }

        @Override
        public void child(int childIndex) {
        }

        @Override
        public int childCount() {
            return 0;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public Class<?> get() throws NoSuchElementException {
            return null;
        }

        @Override
        public Kind getKind() throws NoSuchElementException {
            return null;
        }

        @Override
        public <T> T getConstant(Class<T> expectedClass, int constantIndex) throws NoSuchElementException {
            return null;
        }

    }

    static final class BytecodeNode extends OperationsNode {

        private static final int OP_BODY = 1;
        private static final int OP_READ_LOCAL = 2;
        private static final int OP_WRITE_LOCAL = 3;
        private static final int OP_IF = 4;
        private static final int OP_BREAK_WHILE = 5;
        private static final int OP_CONTINUE_WHILE = 6;
        private static final int OP_LESS_THAN = 7;
        private static final int OP_GREATER_THAN = 8;
        private static final int OP_CONSTANT_INT = 9;
        private static final int OP_CONSTANT_STRING_BYTE = 10;
        private static final int OP_CONSTANT_STRING_SHORT = 11;
        private static final int OP_ADD = 12;
        private static final int OP_RETURN = 13;

        private static final int OP_BRANCH = 14;

        private static final ByteArraySupport BYTES = ByteArraySupport.littleEndian();

        @CompilationFinal(dimensions = 1) private final byte[] bytecodes;
        private final int maxStack;
        @CompilationFinal(dimensions = 1) final Object[] finalData;
        @CompilationFinal(dimensions = 1) final Object[] mutableData;

        protected BytecodeNode(byte[] bytecodes, int maxStack, Object[] finalData, Object[] mutableData) {
            this.bytecodes = bytecodes;
            this.maxStack = maxStack;
            this.finalData = finalData;
            this.mutableData = mutableData;
        }

        @Override
        @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
        @HostCompilerDirectives.BytecodeInterpreterSwitch
        public Object execute(VirtualFrame frame) {
            byte[] b = this.bytecodes; // bytecodes
            int bLength = b.length;
            int bci = 0; // bytecode index
            int sp = 0; // stack pointer
            final int[] pStack = new int[maxStack];
            final Object[] rStack = new Object[maxStack];
            final Object[] constants = this.finalData;

            int[] locals = null;
            while (bci < bLength) {
                int op = b[bci++];
                switch (op) {
                    case OP_BODY:
                        locals = Body.locals(frame, locals, BYTES.getInt(b, bci), BYTES.getInt(b, bci + 4));
                        bci += 8;
                        break;
                    case OP_ADD:
                        executeAdd(pStack, sp);
                        sp--;
                        break;
                    case OP_RETURN:
                        Object value = TestOperations.Return.doDefault(pStack[sp - 1]);
                        sp--;
                        return value;
                    case OP_READ_LOCAL:
                        writeInt(pStack, sp, ReadLocal.doDefault(locals, BYTES.getInt(b, bci)));
                        sp++;
                        bci += 4;
                        break;
                    case OP_GREATER_THAN:
                        executeGreaterThan(pStack, sp);
                        sp--;
                        break;
                    case OP_CONSTANT_INT:
                        writeInt(pStack, sp, ConstantInt.value(BYTES.getInt(b, bci)));
                        sp++;
                        bci += 4;
                        break;
                    case OP_CONSTANT_STRING_BYTE:
                        writeObject(rStack, sp, ConstantString.value(readByteConstantString(b, bci, constants)));
                        bci += 1;
                        sp++;
                        break;
                    case OP_CONSTANT_STRING_SHORT:
                        writeObject(rStack, sp, ConstantString.value(readShortConstantString(b, bci, constants)));
                        bci += 2;
                        sp++;
                        break;
                    case OP_IF:
                        if (If.execute(readInt(pStack, sp - 1) == 1)) {
                            bci += 4;
                        } else {
                            bci = BYTES.getInt(b, bci);
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException(String.valueOf(op));

                }
            }
            return null;
        }

        private static int bytecodeIndexOf(byte[] b, int byteIndex) {
            int bLength = b.length;
            int bci = 0; // bytecode index
            int bytecodeIndex = 0;
            while (bci < bLength) {
                if (bci == byteIndex) {
                    return bytecodeIndex;
                }
                int op = b[bci++];
                try {
                    bci += bytecodeSize(op);
                } catch (UnsupportedOperationException e) {
                    return -1;
                }
                bytecodeIndex++;
            }
            return -1;
        }

        private static int bytecodeSize(int op) {
            switch (op) {
                case OP_BODY:
                    return 8;
                case OP_ADD:
                case OP_RETURN:
                case OP_GREATER_THAN:
                    return 0;
                case OP_CONSTANT_STRING_BYTE:
                    return 1;
                case OP_CONSTANT_STRING_SHORT:
                    return 2;
                case OP_READ_LOCAL:
                case OP_CONSTANT_INT:
                case OP_IF:
                case OP_BRANCH:
                    return 4;
                default:
                    throw new UnsupportedOperationException(String.valueOf(op));
            }
        }

        private static String printBytecodes(String indent, byte[] b, Object[] constants) {
            StringBuilder s = new StringBuilder();
            int bLength = b.length;
            int bci = 0; // bytecode index

            while (bci < bLength) {
                s.append(indent);
                s.append(bci).append(":");
                int op = b[bci++];
                switch (op) {
                    case OP_BODY:
                        s.append("Body");
                        s.append(" locals:").append(BYTES.getInt(b, bci));
                        s.append(" arguments:").append(BYTES.getInt(b, bci + 4));
                        bci += 8;
                        break;
                    case OP_ADD:
                        s.append("Add");
                        break;
                    case OP_GREATER_THAN:
                        s.append("GreaterThan");
                        break;
                    case OP_RETURN:
                        s.append("Return");
                        break;
                    case OP_READ_LOCAL:
                        s.append("ReadLocal");
                        s.append(" index:").append(BYTES.getInt(b, bci));
                        bci += 4;
                        break;
                    case OP_CONSTANT_INT:
                        s.append("ConstantInt");
                        s.append(" value:").append(BYTES.getInt(b, bci));
                        bci += 4;
                        break;
                    case OP_CONSTANT_STRING_BYTE:
                        s.append("ConstantString");
                        s.append(" value:\"").append(readByteConstantString(b, bci, constants)).append("\"");
                        bci += 1;
                        break;
                    case OP_CONSTANT_STRING_SHORT:
                        s.append("ConstantString");
                        s.append(" value:\"").append(readShortConstantString(b, bci, constants)).append("\"");
                        bci += 2;
                        break;
                    case OP_IF:
                        s.append("If");
                        s.append(" !target:").append(BYTES.getInt(b, bci));
                        bci += 4;
                        break;
                    case OP_BRANCH:
                        s.append("Branch");
                        s.append(" target:").append(BYTES.getInt(b, bci));
                        bci += 4;
                        break;
                    case 0:
                        break;
                    default:
                        throw new UnsupportedOperationException(String.valueOf(op) + "\n" + s.toString());
                }
                s.append(System.lineSeparator());
            }
            return s.toString();
        }

        private static String formatTarget(int target) {
            return "0x" + Integer.toHexString(target);
        }

        private static String readByteConstantString(byte[] bytecodes, int bci, Object[] constants) {
            return (String) constants[Byte.toUnsignedInt(BYTES.getByte(bytecodes, bci))];
        }

        private static String readShortConstantString(byte[] bytecodes, int bci, Object[] constants) {
            return (String) constants[Short.toUnsignedInt(BYTES.getShort(bytecodes, bci))];
        }

        @Override
        public OperationPointer createPointer() {
            return null;
        }

        private static void executeAdd(int[] stack, int stackPointer) {
            int right = readInt(stack, stackPointer - 1);
            int left = readInt(stack, stackPointer - 2);
            writeInt(stack, stackPointer - 2, Add.doAdd(left, right));
        }

        private static void executeGreaterThan(int[] stack, int stackPointer) {
            int right = readInt(stack, stackPointer - 1);
            int left = readInt(stack, stackPointer - 2);
            writeInt(stack, stackPointer - 2, GreaterThan.doDefault(left, right) ? 1 : 0);
        }

        private static void writeInt(int[] stack, int index, int value) {
            stack[index] = value;
        }

        private static void writeObject(Object[] stack, int index, Object value) {
            // TODO
        }

        private static int readInt(int[] stack, int index) {
            int result = stack[index];
            if (CompilerDirectives.inCompiledCode()) {
                // Needed to avoid keeping track of popped slots in FrameStates.
                stack[index] = 0;
            }
            return result;
        }

        @Override
        public OperationsNode copyUninitialized() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object continueAt(VirtualFrame frame, OperationPointer index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return printBytecodes("", this.bytecodes, this.finalData);
        }

    }

    final static class BytecodeInterpreterBuilder extends TestOperationsBuilder {

        private static final ByteArraySupport BYTES = ByteArraySupport.littleEndian();

        private static final int SHORT_MASK = 0x000000AF;
        private static final int WIDE_MASK = 0x0000AFFF;

        private byte[] byteBuffer = new byte[1024];
        private int bufferIndex;

        private int[] opBuffer = new int[1024];
        private int opIndex;

        private int finalBufferIndex;
        private Object[] finalBuffer = new Object[1024];

        private int stackSize;
        private int maxStack;

        BytecodeInterpreterBuilder() {
            this.opIndex = 0;
        }

        @Override
        public OperationsNode build() {
            byte[] bc = Arrays.copyOf(byteBuffer, bufferIndex);
            Object[] imBuffer = Arrays.copyOf(finalBuffer, finalBufferIndex);
            Object[] mutableBuffer = new Object[0];
            BytecodeNode bcNode = new BytecodeNode(bc, maxStack, imBuffer, mutableBuffer);
            onBuild(bcNode);
            return bcNode;
        }

        @Override
        public void startBody() {
            pushStack();
            writeOp(BytecodeNode.OP_BODY);
            writeInt(0); // patched in end
            writeInt(0); // patched in end
        }

        @Override
        public void endBody(int localCount, int argumentCount) {
            popStack(); // child
            int index = peekStack(BytecodeNode.OP_BODY);
            BYTES.putInt(byteBuffer, index + 1, localCount);
            BYTES.putInt(byteBuffer, index + 5, argumentCount);
        }

        @Override
        public void pushLocalRead(int index) {
            pushStack();
            writeOp(BytecodeNode.OP_READ_LOCAL);
            writeInt(index);
            stackChange(1);
        }

        @Override
        public void startReturn() {
            pushStack();
        }

        @Override
        public void endReturn() {
            popStack();
            peekStack(BytecodeNode.OP_RETURN);
            writeOp(BytecodeNode.OP_RETURN);
            stackChange(-1);
        }

        @Override
        public void reset() {
            bufferIndex = 0;
            opIndex = 0;
            finalBufferIndex = 0;
            stackSize = 0;
            maxStack = 0;
        }

        @Override
        public void startGreaterThan() {
            pushStack();
        }

        @Override
        public void endGreaterThan() {
            popStack(); // right
            popStack(); // left
            peekStack(BytecodeNode.OP_ADD);

            writeOp(BytecodeNode.OP_GREATER_THAN);
            stackChange(-1);
        }

        @Override
        public void startIf() {
            pushStack();
        }

        @Override
        public void endIf() {
            int elseBranch = popStack();
            int thenIndex = popStack(); // then index
            int condition = popStack();

            insertSpace(thenIndex, 5);
            elseBranch += 5;
            writeByte(byteBuffer, thenIndex, BytecodeNode.OP_IF);
            writeInt(byteBuffer, thenIndex + 1, elseBranch + 5); // else index

            insertSpace(elseBranch, 5);
            writeByte(byteBuffer, elseBranch, BytecodeNode.OP_BRANCH);
            writeInt(byteBuffer, elseBranch + 1, bufferIndex);
        }

        private void insertSpace(int index, int bytes) {
            System.arraycopy(byteBuffer, index, byteBuffer, index + bytes, byteBuffer.length - index - bytes);
            Arrays.fill(byteBuffer, index, index + bytes, (byte) 0);
            bufferIndex += bytes;
        }

        @Override
        public void pushConstantInt(int value) {
            pushStack();
            writeOp(BytecodeNode.OP_CONSTANT_INT);
            writeInt(value);
            stackChange(1);
        }

        @Override
        public void startAdd() {
            pushStack();
        }

        @Override
        public void endAdd() {
            popStack(); // right
            popStack(); // left
            peekStack(BytecodeNode.OP_ADD);

            writeOp(BytecodeNode.OP_ADD);
            stackChange(-1);
        }

        public void stackChange(int change) {
            stackSize += change;
            if (stackSize > maxStack) {
                maxStack = stackSize;
            }
        }

        protected final void pushRef(int cpIndex) {
            pushShort(cpIndex);
        }

        private void pushByte(int v) {
            ensureSize(1);
            writeByte(byteBuffer, bufferIndex, v);
            bufferIndex = bufferIndex + 1;
        }

        private void pushShort(int v) {
            ensureSize(2);
            writeShort(byteBuffer, bufferIndex, v);
            bufferIndex = bufferIndex + 2;
        }

        private void ensureSize(int length) {
            if (bufferIndex + length >= byteBuffer.length) {
                byteBuffer = Arrays.copyOf(byteBuffer, byteBuffer.length << 1);
            }
        }

        private void writeInt(int v) {
            ensureSize(4);
            writeInt(byteBuffer, bufferIndex, v);
            bufferIndex = bufferIndex + 4;
        }

        private int popInt() {
            bufferIndex = bufferIndex - 4;
            return readInt(byteBuffer, bufferIndex);
        }

        private int popShort() {
            bufferIndex = bufferIndex - 2;
            return readShort(byteBuffer, bufferIndex);
        }

        private int popByte() {
            bufferIndex = bufferIndex - 1;
            return readByte(byteBuffer, bufferIndex);
        }

        private static void writeInt(byte[] b, int index, int v) {
            BYTES.putInt(b, index, v);
        }

        private static void writeByte(byte[] b, int index, int v) {
            BYTES.putByte(b, index, (byte) (v & 0x000000FF));
        }

        private static void writeShort(byte[] b, int index, int v) {
            BYTES.putShort(b, index, (short) (v & 0x0000FFFF));
        }

        private static int readInt(byte[] b, int index) {
            return BYTES.getInt(b, index);
        }

        private static int readShort(byte[] b, int index) {
            return BYTES.getShort(b, index);
        }

        private static int readByte(byte[] b, int index) {
            return BYTES.getByte(b, index);
        }

        private int peekOp(int skip) {
            return opBuffer[opIndex - skip - 1];
        }

        private void writeOp(int op) {
            if ((op & SHORT_MASK) == op) {
                pushByte(op);
            } else if ((op & WIDE_MASK) == op) {
                pushShort(op);
            } else {
                writeInt(op);
            }
        }

        private void pushStack() {
            int i = opIndex++;
            if (i >= opBuffer.length) {
                opBuffer = Arrays.copyOf(opBuffer, opBuffer.length << 1);
            }
            opBuffer[i] = bufferIndex;
        }

        private int popStack() {
            return opBuffer[--opIndex];
        }

        private int peekStack(int expectedOp) {
            int op = opBuffer[opIndex - 1];

            return op;
        }

        @Override
        public String toString() {
            if (bufferIndex == 0) {
                return "BytecodeInterpreterBuilder";
            }
            String s = BytecodeNode.printBytecodes("  ", Arrays.copyOf(byteBuffer, bufferIndex + 1),
                            Arrays.copyOf(finalBuffer, finalBufferIndex + 1));
            return "BytecodeInterpreterBuilder[\n" + s + "]";
        }

    }

}
