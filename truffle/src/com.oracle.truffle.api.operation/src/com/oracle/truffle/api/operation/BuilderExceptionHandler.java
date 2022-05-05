package com.oracle.truffle.api.operation;

public class BuilderExceptionHandler {
    public int startBci;
    public int startStack;
    public int endBci;
    public int exceptionIndex;
    public int handlerBci;

    public BuilderExceptionHandler() {
    }

    private BuilderExceptionHandler(int startBci, int startStack, int endBci, int exceptionIndex, int handlerBci) {
        this.startBci = startBci;
        this.startStack = startStack;
        this.endBci = endBci;
        this.exceptionIndex = exceptionIndex;
        this.handlerBci = handlerBci;
    }

    public BuilderExceptionHandler offset(int offset, int stackOffset) {
        return new BuilderExceptionHandler(startBci + offset, startStack + stackOffset, endBci + offset, exceptionIndex, handlerBci + offset);
    }

    @Override
    public String toString() {
        return String.format("{start=%04x, end=%04x, handler=%04x, index=%d}", startBci, endBci, handlerBci, exceptionIndex);
    }
}
