package com.oracle.truffle.api.operation;

public class OperationConfig {

    public static final OperationConfig DEFAULT = new OperationConfig(false, false);
    public static final OperationConfig WITH_SOURCE = new OperationConfig(true, false);

    public static final OperationConfig COMPLETE = new OperationConfig(true, true);

    private final boolean withSource;
    private final boolean withInstrumentation;

    public OperationConfig(boolean withSource, boolean withInstrumentation) {
        this.withSource = withSource;
        this.withInstrumentation = withInstrumentation;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public boolean isWithSource() {
        return withSource;
    }

    public boolean isWithInstrumentation() {
        return withInstrumentation;
    }

    public static class Builder {
        private boolean withSource;
        private boolean withInstrumentation;

        Builder() {
        }

        public Builder withSource(boolean value) {
            this.withSource = value;
            return this;
        }

        public Builder withInstrumentation(boolean value) {
            this.withInstrumentation = value;
            return this;
        }

        public OperationConfig build() {
            return new OperationConfig(withSource, withInstrumentation);
        }
    }
}
