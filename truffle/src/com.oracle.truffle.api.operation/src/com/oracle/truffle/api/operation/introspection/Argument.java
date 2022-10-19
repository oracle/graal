package com.oracle.truffle.api.operation.introspection;

public final class Argument {

    private final Object[] data;

    Argument(Object[] data) {
        this.data = data;
    }

    public enum ArgumentKind {
        LOCAL,
        ARGUMENT,
        BOXING,
        CONSTANT,
        CHILD_OFFSET,
        VARIADIC,
        BRANCH_OFFSET;

        public String toString(Object value) {
            switch (this) {
                case LOCAL:
                    return String.format("local(%d)", (int) value);
                case ARGUMENT:
                    return String.format("arg(%d)", (int) value);
                case BOXING:
                    return String.format("boxing(%s)", value);
                case CONSTANT:
                    if (value == null) {
                        return "const(null)";
                    } else {
                        return String.format("const(%s %s)", value.getClass().getSimpleName(), value);
                    }
                case CHILD_OFFSET:
                    return String.format("child(-%d)", (int) value);
                case VARIADIC:
                    return String.format("variadic(%d)", (int) value);
                case BRANCH_OFFSET:
                    return String.format("branch(%04x)", (int) value);
                default:
                    throw new UnsupportedOperationException("Unexpected value: " + this);
            }
        }
    }

    public ArgumentKind getKind() {
        return (ArgumentKind) data[0];
    }

    public Object getValue() {
        return data[1];
    }

    @Override
    public String toString() {
        return getKind().toString(getValue());
    }

}