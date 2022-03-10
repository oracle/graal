package com.oracle.truffle.api.operation;

public class BuilderExceptionHandler {
    public int startBci;
    public int startStack;
    public int endBci;
    public int exceptionIndex;
    public int handlerBci;

    @Override
    public String toString() {
        return String.format("{start=%04x, end=%04x, handler=%04x}", startBci, endBci, handlerBci);
    }
}
