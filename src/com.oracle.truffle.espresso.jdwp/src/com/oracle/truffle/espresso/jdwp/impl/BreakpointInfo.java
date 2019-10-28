package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.debug.Breakpoint;

public class BreakpointInfo {

    private final int requestId;
    private final byte typeTag;
    private final long classId;
    private final long methodId;
    private final long bci;

    private Breakpoint breakpoint;

    public BreakpointInfo(int requestId, byte tag, long classId, long methodId, long bci) {
        this.requestId = requestId;
        this.typeTag = tag;
        this.classId = classId;
        this.methodId = methodId;
        this.bci = bci;
    }

    public int getRequestId() {
        return requestId;
    }

    public long getClassId() {
        return classId;
    }

    public long getMethodId() {
        return methodId;
    }

    public byte getTypeTag() {
        return typeTag;
    }

    public long getBci() {
        return bci;
    }

    @Override
    public String toString() {
        return "typeTag: " + typeTag + ", classId: " + classId + ", methodId: " + methodId + ", bci: " + bci;
    }

    public void setBreakpoint(Breakpoint bp) {
        this.breakpoint = bp;
    }

    public Breakpoint getBreakpoint() {
        return breakpoint;
    }
}
