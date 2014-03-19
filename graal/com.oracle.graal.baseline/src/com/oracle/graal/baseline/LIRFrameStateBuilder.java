package com.oracle.graal.baseline;

import static com.oracle.graal.nodes.ValueNodeUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;

public class LIRFrameStateBuilder extends AbstractFrameStateBuilder<Variable> {

    private final Variable[] locals;
    private final Variable[] stack;
    private Variable[] lockedObjects;

    public LIRFrameStateBuilder(ResolvedJavaMethod method, boolean eagerResolve) {
        super(method);

        this.locals = new Variable[method.getMaxLocals()];
        // we always need at least one stack slot (for exceptions)
        this.stack = new Variable[Math.max(1, method.getMaxStackSize())];
    }

    protected LIRFrameStateBuilder(LIRFrameStateBuilder other) {
        super(other);
        // TODO Auto-generated constructor stub
        locals = other.locals;
        stack = other.stack;
        lockedObjects = other.lockedObjects;
    }

    @Override
    public int localsSize() {
        return locals.length;
    }

    @Override
    public Variable localAt(int i) {
        return locals[i];
    }

    @Override
    public Variable stackAt(int i) {
        return stack[i];
    }

    @Override
    public Variable loadLocal(int i) {
        Variable x = locals[i];
        assert !isTwoSlot(x.getKind()) || locals[i + 1] == null;
        assert i == 0 || locals[i - 1] == null || !isTwoSlot(locals[i - 1].getKind());
        return x;
    }

    @Override
    public void storeLocal(int i, Variable x) {
        assert x == null || x.getKind() != Kind.Void && x.getKind() != Kind.Illegal : "unexpected value: " + x;
        locals[i] = x;
        if (x != null && isTwoSlot(x.getKind())) {
            // if this is a double word, then kill i+1
            locals[i + 1] = null;
        }
        if (x != null && i > 0) {
            Variable p = locals[i - 1];
            if (p != null && isTwoSlot(p.getKind())) {
                // if there was a double word at i - 1, then kill it
                locals[i - 1] = null;
            }
        }
    }

    @Override
    public void storeStack(int i, Variable x) {
        assert x == null || (stack[i] == null || x.getKind() == stack[i].getKind()) : "Method does not handle changes from one-slot to two-slot values or non-alive values";
        stack[i] = x;
    }

    @Override
    public void push(Kind kind, Variable x) {
        assert x.getKind() != Kind.Void && x.getKind() != Kind.Illegal;
        xpush(assertKind(kind, x));
        if (isTwoSlot(kind)) {
            xpush(null);
        }
    }

    @Override
    public void xpush(Variable x) {
        assert x == null || (x.getKind() != Kind.Void && x.getKind() != Kind.Illegal);
        stack[stackSize++] = x;
    }

    @Override
    public void ipush(Variable x) {
        xpush(assertInt(x));
    }

    @Override
    public void fpush(Variable x) {
        xpush(assertFloat(x));
    }

    @Override
    public void apush(Variable x) {
        xpush(assertObject(x));
    }

    @Override
    public void lpush(Variable x) {
        xpush(assertLong(x));
    }

    @Override
    public void dpush(Variable x) {
        xpush(assertDouble(x));

    }

    @Override
    public void pushReturn(Kind kind, Variable x) {
        if (kind != Kind.Void) {
            push(kind.getStackKind(), x);
        }
    }

    @Override
    public Variable pop(Kind kind) {
        assert kind != Kind.Void;
        if (isTwoSlot(kind)) {
            xpop();
        }
        return assertKind(kind, xpop());
    }

    @Override
    public Variable xpop() {
        Variable result = stack[--stackSize];
        return result;
    }

    @Override
    public Variable ipop() {
        return assertInt(xpop());
    }

    @Override
    public Variable fpop() {
        return assertFloat(xpop());
    }

    @Override
    public Variable apop() {
        return assertObject(xpop());
    }

    @Override
    public Variable lpop() {
        assertHigh(xpop());
        return assertLong(xpop());
    }

    @Override
    public Variable dpop() {
        assertHigh(xpop());
        return assertDouble(xpop());
    }

    @Override
    public Variable[] popArguments(int slotSize, int argSize) {
        int base = stackSize - slotSize;
        Variable[] r = new Variable[argSize];
        int argIndex = 0;
        int stackindex = 0;
        while (stackindex < slotSize) {
            Variable element = stack[base + stackindex];
            assert element != null;
            r[argIndex++] = element;
            stackindex += stackSlots(element.getKind());
        }
        stackSize = base;
        return r;
    }

    @Override
    public Variable peek(int argumentNumber) {
        int idx = stackSize() - 1;
        for (int i = 0; i < argumentNumber; i++) {
            if (stackAt(idx) == null) {
                idx--;
                assert isTwoSlot(stackAt(idx).getKind());
            }
            idx--;
        }
        return stackAt(idx);
    }

    private static Variable assertKind(Kind kind, Variable x) {
        assert x != null && x.getKind() == kind : "kind=" + kind + ", value=" + x + ((x == null) ? "" : ", value.kind=" + x.getKind());
        return x;
    }

    private static Variable assertLong(Variable x) {
        assert x != null && (x.getKind() == Kind.Long);
        return x;
    }

    private static Variable assertInt(Variable x) {
        assert x != null && (x.getKind() == Kind.Int);
        return x;
    }

    private static Variable assertFloat(Variable x) {
        assert x != null && (x.getKind() == Kind.Float);
        return x;
    }

    private static Variable assertObject(Variable x) {
        assert x != null && (x.getKind() == Kind.Object);
        return x;
    }

    private static Variable assertDouble(Variable x) {
        assert x != null && (x.getKind() == Kind.Double);
        return x;
    }

    private static void assertHigh(Variable x) {
        assert x == null;
    }
}
