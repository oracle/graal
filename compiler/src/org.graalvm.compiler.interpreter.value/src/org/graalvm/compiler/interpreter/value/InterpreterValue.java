package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class InterpreterValue {

    public boolean isPrimitive() {
        return false;
    }

    public boolean isArray() {
        return false;
    }

    public PrimitiveConstant asPrimitiveConstant() {
        throw new UnsupportedOperationException("asPrimitiveConstant called on non primitive value");
    }

    public ResolvedJavaType getObjectType() {
        throw new UnsupportedOperationException();
    }

    public abstract JavaKind getJavaKind();

    public boolean isException() {
        return false;
    }

    public abstract boolean isNull();

    public abstract Object asObject();

    public static InterpreterValue createDefaultOfKind(JavaKind kind) {
        if (kind == JavaKind.Void) {
            return InterpreterValueVoid.INSTANCE;
        } else if (kind == JavaKind.Object) {
            return InterpreterValueNullPointer.INSTANCE;
        } else if (kind.isPrimitive()) {
            return InterpreterValuePrimitive.defaultForPrimitiveKind(kind);
        }
        throw new IllegalArgumentException("Illegal JavaKind");
    }

    public static class InterpreterValueNullPointer extends InterpreterValue {
        public static final InterpreterValueNullPointer INSTANCE = new InterpreterValueNullPointer();

        private InterpreterValueNullPointer() {
        }

        @Override
        public JavaKind getJavaKind() {
            return JavaKind.Object;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public Object asObject() {
            return null;
        }
    }

    public static class InterpreterValueVoid extends InterpreterValue {
        public static final InterpreterValueVoid INSTANCE = new InterpreterValueVoid();

        private InterpreterValueVoid() {
        }

        @Override
        public boolean isPrimitive() {
            return true;
        }

        @Override
        public JavaKind getJavaKind() {
            return JavaKind.Void;
        }

        @Override
        public boolean isNull() {
            return true;
        }

        @Override
        public Object asObject() {
            // Void is uninstantiable
            throw new UnsupportedOperationException();
        }
    }
}
