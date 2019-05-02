package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

public class MemoryErrorDelegate extends VirtualMachineError {

    private static final long serialVersionUID = 8733484410412601660L;

    public static StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    public static FrameInstance[] EMPTY_FRAMES = new FrameInstance[0];
    private static int DEFAULT_DESTACK = 10;

    private int emptyTheStack = DEFAULT_DESTACK;
    private boolean isStackOverflow;

    public MemoryErrorDelegate() {
        super();
    }

    public MemoryErrorDelegate deStack() {
        emptyTheStack--;
        return this;
    }

    public boolean check() {
        return emptyTheStack <= 0;
    }

    public MemoryErrorDelegate delegate(boolean _isStackOverflow) {
        this.isStackOverflow = _isStackOverflow;
        return this;
    }

    public EspressoException act(EspressoContext context, Meta meta) {
        EspressoException EE;
        if (isStackOverflow) {
            EE = context.getStackOverflow();
        } else {
            EE = context.getOutOfMemory();
        }
        StaticObject exception = EE.getException();
        InterpreterToVM.fillInStackTrace(context.getFrames(), exception, meta);
        return EE;
    }
}
