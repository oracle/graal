/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.preserve;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.Word;
import org.junit.Test;

import com.oracle.svm.core.jni.JNIMethodSupport;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.test.NativeImageBuildArgs;

@NativeImageBuildArgs({
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jni.headers=ALL-UNNAMED",
                "--add-exports=org.graalvm.nativeimage.guest.staging/com.oracle.svm.guest.staging.c=ALL-UNNAMED",
                "-H:+UnlockExperimentalVMOptions",
                "-H:Preserve=package=com.oracle.svm.test.preserve",
                "-H:-UnlockExperimentalVMOptions",
                "--exact-reachability-metadata=com.oracle.svm.test.preserve"
})
public class PreserveLambdaProxyClassesTest {
    private static final String EXPECTED = "preserved lambda";

    private interface SerializableSupplier extends Supplier<String>, Serializable {
    }

    private static final class PreservedCapturingClass {
        static SerializableSupplier createSupplier() {
            String captured = EXPECTED;
            return () -> captured;
        }
    }

    @Test
    public void preserveIncludesReachedLambdaProxyClasses() throws Exception {
        SerializableSupplier supplier = PreservedCapturingClass.createSupplier();
        assertReflectiveAccess(supplier);
        assertSerializationAccess(supplier);
        assertJniAccess(supplier);
    }

    private static void assertReflectiveAccess(SerializableSupplier supplier) throws ReflectiveOperationException {
        Method get = supplier.getClass().getDeclaredMethod("get");
        assertEquals(EXPECTED, get.invoke(supplier));

        Field capturedField = getCapturedField(supplier.getClass());
        capturedField.setAccessible(true);
        assertEquals(EXPECTED, capturedField.get(supplier));
    }

    private static void assertSerializationAccess(SerializableSupplier supplier) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            stream.writeObject(supplier);
        }

        Object deserialized;
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            deserialized = stream.readObject();
        }
        assertEquals(EXPECTED, ((Supplier<?>) deserialized).get());
    }

    private static Field getCapturedField(Class<?> lambdaClass) {
        for (Field field : lambdaClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                return field;
            }
        }
        throw new AssertionError("Expected lambda class to have a captured field: " + lambdaClass.getName());
    }

    private static void assertJniAccess(SerializableSupplier supplier) {
        if (!ImageInfo.inImageRuntimeCode()) {
            return;
        }

        JNIEnvironment env = JNIMethodSupport.environment();
        JNIObjectHandle lambdaClass = JNIObjectHandles.createLocal(supplier.getClass());
        JNIObjectHandle lambdaObject = JNIObjectHandles.createLocal(supplier);
        try {
            JNIMethodId get = getMethodId(env, lambdaClass, "get", "()Ljava/lang/Object;");
            JNIObjectHandle result = env.getFunctions().getCallObjectMethodA().invoke(env, lambdaObject, get, Word.nullPointer());
            try {
                assertNoJniException(env);
                assertEquals(EXPECTED, JNIObjectHandles.getObject(result));
            } finally {
                deleteLocalRef(result);
            }

            Field capturedField = getCapturedField(supplier.getClass());
            JNIFieldId field = getFieldId(env, lambdaClass, capturedField.getName(), "Ljava/lang/String;");
            JNIObjectHandle capturedValue = env.getFunctions().getGetObjectField().invoke(env, lambdaObject, field);
            try {
                assertNoJniException(env);
                assertEquals(EXPECTED, JNIObjectHandles.getObject(capturedValue));
            } finally {
                deleteLocalRef(capturedValue);
            }
        } finally {
            deleteLocalRef(lambdaObject);
            deleteLocalRef(lambdaClass);
        }
    }

    private static JNIMethodId getMethodId(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature) {
        try (CTypeConversion.CCharPointerHolder cName = CTypeConversion.toCString(name);
                        CTypeConversion.CCharPointerHolder cSignature = CTypeConversion.toCString(signature)) {
            JNIMethodId id = env.getFunctions().getGetMethodID().invoke(env, clazz, cName.get(), cSignature.get());
            assertNoJniException(env);
            assertFalse("Expected JNI method ID for " + name + signature, id.isNull());
            return id;
        }
    }

    private static JNIFieldId getFieldId(JNIEnvironment env, JNIObjectHandle clazz, String name, String signature) {
        try (CTypeConversion.CCharPointerHolder cName = CTypeConversion.toCString(name);
                        CTypeConversion.CCharPointerHolder cSignature = CTypeConversion.toCString(signature)) {
            JNIFieldId id = env.getFunctions().getGetFieldID().invoke(env, clazz, cName.get(), cSignature.get());
            assertNoJniException(env);
            assertFalse("Expected JNI field ID for " + name + ":" + signature, id.isNull());
            return id;
        }
    }

    private static void assertNoJniException(JNIEnvironment env) {
        JNIObjectHandle exception = env.getFunctions().getExceptionOccurred().invoke(env);
        if (exception.isNonNull()) {
            Throwable throwable = JNIObjectHandles.getObject(exception);
            env.getFunctions().getExceptionClear().invoke(env);
            deleteLocalRef(exception);
            throw new AssertionError("Unexpected JNI exception", throwable);
        }
    }

    private static void deleteLocalRef(JNIObjectHandle handle) {
        if (handle.isNonNull()) {
            JNIObjectHandles.deleteLocalRef(handle);
        }
    }
}
