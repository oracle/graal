package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.espresso.meta.Meta;

public abstract class JniSubstitutor {

    public abstract static class Factory {
        public abstract JniSubstitutor create(Meta meta);

        private final String methodName;
        private final String jniNativeSignature;
        private final int parameterCount;
        private final String returnType;

        public Factory(String methodName, String jniNativeSignature, int parameterCount, String returnType) {
            this.methodName = methodName;
            this.jniNativeSignature = jniNativeSignature;
            this.parameterCount = parameterCount;
            this.returnType = returnType;
        }

        public final String methodName() {
            return methodName;
        }

        public final String jniNativeSignature() {
            return jniNativeSignature;
        }

        public final int getParameterCount() {
            return parameterCount;
        }

        public final String returnType() {
            return returnType;
        }
    }

    public abstract Object invoke(JniEnv env, Object[] args);
}
