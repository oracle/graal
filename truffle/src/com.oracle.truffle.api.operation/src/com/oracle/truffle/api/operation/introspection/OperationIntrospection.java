package com.oracle.truffle.api.operation.introspection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class OperationIntrospection {

    public static interface Provider {
        default OperationIntrospection getIntrospectionData() {
            throw new UnsupportedOperationException();
        }

        static OperationIntrospection create(Object... data) {
            return new OperationIntrospection(data);
        }
    }

    private final Object[] data;

    private OperationIntrospection(Object[] data) {
        if (data.length == 0 || (int) data[0] != 0) {
            throw new UnsupportedOperationException("Illegal operation introspection version");
        }

        this.data = data;
    }

    public List<Instruction> getInstructions() {
        return Arrays.stream((Object[]) data[1]).map(x -> new Instruction((Object[]) x)).collect(Collectors.toUnmodifiableList());
    }

    public List<ExceptionHandler> getExceptionHandlers() {
        return Arrays.stream((Object[]) data[2]).map(x -> new ExceptionHandler((Object[]) x)).collect(Collectors.toUnmodifiableList());
    }
}