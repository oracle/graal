package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.espresso.meta.Meta;

public abstract class VMSubstitutor {

    public abstract static class Factory {
        public abstract VMSubstitutor create(Meta meta);

        private final String methodName;
        private final String jniNativeSignature;
        private final int parameterCount;
        private final String returnType;
        private final boolean isJni;

        Factory(String methodName, String jniNativeSignature, int parameterCount, String returnType, boolean isJni) {
            this.methodName = methodName;
            this.jniNativeSignature = jniNativeSignature;
            this.parameterCount = parameterCount;
            this.returnType = returnType;
            this.isJni = isJni;
        }

        public String methodName() {
            return methodName;
        }

        public String jniNativeSignature() {
            return jniNativeSignature;
        }

        public int parameterCount() {
            return parameterCount;
        }

        public String returnType() {
            return returnType;
        }

        public boolean isJni() {
            return isJni;
        }
    }

    public abstract Object invoke(VM vm, Object[] args);
}
