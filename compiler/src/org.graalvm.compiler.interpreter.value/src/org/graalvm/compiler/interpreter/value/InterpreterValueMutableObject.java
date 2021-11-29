package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.HashMap;
import java.util.function.Function;

public class InterpreterValueMutableObject extends InterpreterValueObject {
    final HashMap<ResolvedJavaField, InterpreterValue> instanceFields;

    public InterpreterValueMutableObject(ResolvedJavaType type, Function<JavaType, JavaKind> getStorageKind) {
        super(type);
        this.instanceFields = new HashMap<>();

        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            this.instanceFields.put(field, InterpreterValue.createDefaultOfKind(getStorageKind.apply(field.getType())));
        }
    }

    @Override
    public boolean hasField(ResolvedJavaField field) {
        return instanceFields.containsKey(field);
    }

    @Override
    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        instanceFields.replace(field, value);
    }

    @Override
    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        return instanceFields.get(field);
    }

}
