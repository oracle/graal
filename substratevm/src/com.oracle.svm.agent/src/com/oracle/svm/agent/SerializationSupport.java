/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.agent;

import static com.oracle.svm.jvmtiagentbase.Support.fromJniString;
import static com.oracle.svm.jvmtiagentbase.Support.getClassNameOrNull;
import static com.oracle.svm.jvmtiagentbase.Support.getIntArgument;
import static com.oracle.svm.jvmtiagentbase.Support.getObjectArgument;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.oracle.svm.jni.JNIObjectHandles.nullHandle;

import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallIntMethodFunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIFunctionPointerTypes.CallObjectMethod0FunctionPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class SerializationSupport extends DynamicClassGenerationSupport {
    private JNIObjectHandle serializationTargetClass;
    private JNIObjectHandle targetParentClass;

    public SerializationSupport(JNIEnvironment jni, JNIObjectHandle callerClass, JNIObjectHandle serializationTargetClass, String generatedClassName, JNIObjectHandle targetParentClass,
                    TraceWriter traceWriter, NativeImageAgent agent) {
        super(jni, callerClass, generatedClassName, traceWriter, agent);
        this.serializationTargetClass = serializationTargetClass;
        this.targetParentClass = targetParentClass;
    }

    @Override
    protected JNIObjectHandle getClassDefinitionAsBytes() {
        return getObjectArgument(1);
    }

    @Override
    protected int getClassDefinitionBytesLength() {
        return getIntArgument(3);
    }

    /**
     * Serialization/deserialization visits the target class' certain constructors, fields and
     * methods by reflection. This method add configurations for those reflection accesses.
     */
    @Override
    public boolean traceReflects() {
        // trace the newInstance call for the generated class
        traceWriter.traceCall("reflect", "newInstance", generatedClassName);
        // trace all declaredConstructors
        BreakpointInterceptor.traceBreakpoint(jni, serializationTargetClass, nullHandle(), callerClass, "getDeclaredConstructors", null);
        int privateStaticFinalMask = Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;
        int staticFinalMask = Modifier.STATIC | Modifier.FINAL;
        CallObjectMethod0FunctionPointer noArgRetObjectCall = jniFunctions().getCallObjectMethod();
        CallIntMethodFunctionPointer noArgRetIntCall = jniFunctions().getCallIntMethod();
        // call serializationTargetClass.getDeclaredFields();
        JNIObjectHandle javaLangClass = agent.handles().findClass(jni, "java/lang/Class");
        JNIMethodId getDeclaredFieldsMI = agent.handles().getMethodId(jni, javaLangClass, "getDeclaredFields",
                        "()[Ljava/lang/reflect/Field;", false);
        JNIObjectHandle fieldsJArray = noArgRetObjectCall.invoke(jni, serializationTargetClass, getDeclaredFieldsMI);

        // Prepare JNIMethodIds for later calls
        JNIObjectHandle javaLangReflectField = agent.handles().findClass(jni, "java/lang/reflect/Field");
        JNIMethodId getFieldNameId = agent.handles().getMethodId(jni, javaLangReflectField, "getName", "()Ljava/lang/String;", false);
        JNIMethodId getFieldModifiersId = agent.handles().getMethodId(jni, javaLangReflectField, "getModifiers", "()I", false);
        JNIMethodId getFieldTypeId = agent.handles().getMethodId(jni, javaLangReflectField, "getType", "()Ljava/lang/Class;", false);
        // Add serialize and deserialize fields into reflection configs
        // Check each field
        int fieldArrayLength = jniFunctions().getGetArrayLength().invoke(jni, fieldsJArray);
        for (int i = 0; i < fieldArrayLength; i++) {
            // Get field object from array
            JNIObjectHandle field = jniFunctions().getGetObjectArrayElement().invoke(jni, fieldsJArray, i);
            // call field.getName()
            JNIObjectHandle fieldNameJString = noArgRetObjectCall.invoke(jni, field, getFieldNameId);
            String fieldName = fromJniString(jni, fieldNameJString);

            // call field.getModifiers
            int modifiers = noArgRetIntCall.invoke(jni, field, getFieldModifiersId);
            if (fieldName.equals("serialPersistentFields") &&
                            (modifiers & privateStaticFinalMask) == privateStaticFinalMask) {
                BreakpointInterceptor.traceBreakpoint(jni, serializationTargetClass, nullHandle(), callerClass, "getDeclaredField", true,
                                "serialPersistentFields");
            } else if (fieldName.equals("serialVersionUID") &&
                            (modifiers & staticFinalMask) == staticFinalMask) {
                BreakpointInterceptor.traceBreakpoint(jni, serializationTargetClass, nullHandle(), callerClass, "getDeclaredField", true,
                                "serialVersionUID");
            } else if ((modifiers & staticFinalMask) != staticFinalMask) {
                // Set the field's allowWrite and unsafeAccess properties
                BreakpointInterceptor.traceBreakpoint(jni, serializationTargetClass, nullHandle(), callerClass, "getDeclaredField",
                                ((modifiers & Modifier.FINAL) == Modifier.FINAL), ((modifiers & Modifier.STATIC) == 0), true, fieldName);
            }
            // Add field's class in config
            // call field.getType()
            JNIObjectHandle fieldClass = noArgRetObjectCall.invoke(jni, field, getFieldTypeId);
            BreakpointInterceptor.traceBreakpoint(jni, javaLangClass, nullHandle(), callerClass, "forName", true, getClassNameOrNull(jni, fieldClass));
        }

        // Add serialize and deserialize methods into reflection configs
        // Get all methods in the class.
        JNIMethodId getDeclaredMethodssMI = agent.handles().getMethodId(jni, javaLangClass, "getDeclaredMethods",
                        "()[Ljava/lang/reflect/Method;", false);
        JNIObjectHandle methodsJArray = noArgRetObjectCall.invoke(jni, serializationTargetClass, getDeclaredMethodssMI);

        JNIObjectHandle javaLangReflectMethod = agent.handles().findClass(jni, "java/lang/reflect/Method");
        JNIMethodId getMethodNameId = agent.handles().getMethodId(jni, javaLangReflectMethod, "getName", "()Ljava/lang/String;", false);
        // Check each method
        int methodArrayLength = jniFunctions().getGetArrayLength().invoke(jni, methodsJArray);
        for (int i = 0; i < methodArrayLength; i++) {
            // Get method object from array
            JNIObjectHandle method = jniFunctions().getGetObjectArrayElement().invoke(jni, methodsJArray, i);
            // call field.getName()
            JNIObjectHandle methodNameJString = noArgRetObjectCall.invoke(jni, method, getMethodNameId);
            String methodName = fromJniString(jni, methodNameJString);

            List<String> parameterTypes;
            switch (methodName) {
                case "readObject":
                    parameterTypes = Arrays.asList("java.io.ObjectInputStream");
                    break;
                case "writeObject":
                    parameterTypes = Arrays.asList("java.io.ObjectOutputStream");
                    break;
                case "readObjectNoData":
                case "writeReplace":
                case "readResolve":
                    parameterTypes = new ArrayList<>();
                    break;
                default:
                    // Don't need to config other methods
                    continue;
            }

            Object[] args = new Object[2];
            args[0] = methodName;
            args[1] = parameterTypes;
            BreakpointInterceptor.traceBreakpoint(jni, serializationTargetClass, nullHandle(), callerClass, "getMethod", true, args);
        }

        // Add constructor of first non-serializable parent class
        String superClassName = getClassNameOrNull(jni, targetParentClass);
        if (superClassName != null) {
            traceWriter.traceCall("reflect", "getConstructor", superClassName, new ArrayList<>());
        }
        return true;
    }

}
