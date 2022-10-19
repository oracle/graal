package com.oracle.truffle.api.operation.introspection;

public final class ExceptionHandler {

    private final Object[] data;

    ExceptionHandler(Object[] data) {
        this.data = data;
    }

    public int getStartIndex() {
        return (int) data[0];
    }

    public int getEndIndex() {
        return (int) data[1];
    }

    public int getHandlerIndex() {
        return (int) data[2];
    }

    @Override
    public String toString() {
        return String.format("[%04x : %04x] -> %04x", getStartIndex(), getEndIndex(), getHandlerIndex());
    }
}
