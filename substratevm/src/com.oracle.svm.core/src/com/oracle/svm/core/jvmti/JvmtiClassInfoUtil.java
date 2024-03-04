/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import static com.oracle.svm.core.jvmti.utils.JvmtiUninterruptibleUtils.ReplaceDotWithSlash;
import static com.oracle.svm.core.jvmti.utils.JvmtiUninterruptibleUtils.writeStringToCCharArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.JavaLangSubstitutions;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.access.JNIAccessibleMethod;
import com.oracle.svm.core.jni.access.JNIAccessibleMethodDescriptor;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIFieldIdPointer;
import com.oracle.svm.core.jni.headers.JNIFieldIdPointerPointer;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIMethodIdPointer;
import com.oracle.svm.core.jni.headers.JNIMethodIdPointerPointer;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.BooleanPointer;
import com.oracle.svm.core.jvmti.headers.JClass;
import com.oracle.svm.core.jvmti.headers.JClassPointer;
import com.oracle.svm.core.jvmti.headers.JClassPointerPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_Method;

import jdk.graal.compiler.api.replacements.Fold;

public final class JvmtiClassInfoUtil {

    private static final byte JNI_FALSE = 0;
    private static final byte JNI_TRUE = 1;
    private static final int MAX_SIGNATURE_LENGTH = 256;
    private final char[] signatureCharBuffer;
    private final ReplaceDotWithSlash nameToSigReplacer = new ReplaceDotWithSlash();
    private final VMMutex mutex = new VMMutex("jvmtiGetClassSignature");

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiClassInfoUtil() {
        this.signatureCharBuffer = new char[MAX_SIGNATURE_LENGTH];
    }

    @Fold
    public static JvmtiClassInfoUtil singleton() {
        return ImageSingletons.lookup(JvmtiClassInfoUtil.class);
    }

    /*
     * Classes
     */
    public static JvmtiError getClassSignature(JClass klass, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        return singleton().getClassSignatureInternal(klass, signaturePtr, genericPtr);
    }

    public static JvmtiError isModifiableClass(JClass klass, BooleanPointer isModifiableClassPtr) {
        // result is always the same, but checked for consistency
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        ((Pointer) isModifiableClassPtr).writeInt(0, JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError isArrayClass(JClass klass, BooleanPointer isArrayClassPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        // TODO @dprcci make sure of the BooleanPointer type
        ((Pointer) isArrayClassPtr).writeByte(0, javaClass.isArray() ? JNI_TRUE : JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError isInterface(JClass klass, BooleanPointer isInterfacePtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        ((Pointer) isInterfacePtr).writeByte(0, javaClass.isInterface() ? JNI_TRUE : JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getImplementedInterfaces(JClass klass, CIntPointer interfaceCountPtr, JClassPointerPointer interfacesPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }

        /*
         * An empty interface list is returned for array classes and primitive classes (for example,
         * java.lang.Integer.TYPE)
         */
        if (javaClass.isPrimitive() || javaClass.isArray()) {
            ((Pointer) interfacesPtr).writeWord(0, WordFactory.nullPointer());
            interfaceCountPtr.write(0);
            return JvmtiError.JVMTI_ERROR_NONE;
        }

        Class<?>[] classInterfaces = javaClass.getInterfaces();
        int nbInterfaces = classInterfaces.length;
        /*
         * if(nbInterfaces == 0){ ((Pointer) interfacesPtr).writeWord(0, WordFactory.nullPointer());
         * interfaceCountPtr.write(nbInterfaces); return JvmtiError.JVMTI_ERROR_NONE; }
         */

        JClassPointer interfaceArray = getResultArray(nbInterfaces, ConfigurationValues.getTarget().wordSize);
        for (int i = 0; i < nbInterfaces; i++) {
            JClass interfaceHandle = (JClass) JNIObjectHandles.createLocal(classInterfaces[i]);
            writeArrayAtIndex(interfaceArray, i, ConfigurationValues.getTarget().wordSize, interfaceHandle);
        }

        ((Pointer) interfacesPtr).writeWord(0, interfaceArray);
        interfaceCountPtr.write(nbInterfaces);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getClassFields(JClass klass, CIntPointer fieldCountPtr, JNIFieldIdPointerPointer fieldsPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        /*
         * An empty field list is returned for array classes and primitive classes (for example,
         * java.lang.Integer.TYPE)
         */
        if (javaClass.isPrimitive() || javaClass.isArray()) {
            ((Pointer) fieldsPtr).writeWord(0, WordFactory.nullPointer());
            fieldCountPtr.write(0);
            return JvmtiError.JVMTI_ERROR_NONE;
        }

        Field[] declaredFields = javaClass.getDeclaredFields();
        int nbDeclaredFields = declaredFields.length;
        JNIFieldIdPointer fieldIDArray = getResultArray(nbDeclaredFields, ConfigurationValues.getTarget().wordSize);

        int nbWritten = 0;
        for (int i = 0; i < nbDeclaredFields; i++) {
            Field field = declaredFields[i];
            String name = field.getName();
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            JNIFieldId fieldID = JNIReflectionDictionary.singleton().getDeclaredFieldID(javaClass, name, isStatic);
            if (fieldID.isNonNull()) {
                writeArrayAtIndex(fieldIDArray, nbWritten++, ConfigurationValues.getTarget().wordSize, fieldID);
            }
        }
        // We could realloc if nbWritten != nbDeclaredFields but the time costs seem greater than
        // the space gains
        ((Pointer) fieldsPtr).writeWord(0, fieldIDArray);
        fieldCountPtr.write(nbWritten);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getClassMethods(JClass klass, CIntPointer methodCountPtr, JNIMethodIdPointerPointer methodsPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }

        /*
         * An empty method list is returned for array classes and primitive classes (for example,
         * java.lang.Integer.TYPE)
         */
        if (javaClass.isPrimitive() || javaClass.isArray()) {
            ((Pointer) methodsPtr).writeWord(0, WordFactory.nullPointer());
            methodCountPtr.write(0);
            return JvmtiError.JVMTI_ERROR_NONE;
        }

        Method[] declaredMethods = javaClass.getDeclaredMethods();
        int nbMethods = declaredMethods.length;
        JNIMethodIdPointer methodsIDArray = getResultArray(nbMethods, ConfigurationValues.getTarget().wordSize);

        int nbWritten = 0;
        for (int i = 0; i < nbMethods; i++) {
            String methodName = declaredMethods[i].getName();
            JNIMethodId methodId = JNIReflectionDictionary.singleton().toMethodID(javaClass, methodName);
            if (methodId.isNonNull()) {
                writeArrayAtIndex(methodsIDArray, nbWritten++, ConfigurationValues.getTarget().wordSize, methodId);
            }
        }
        ((Pointer) methodsPtr).writeWord(0, methodsIDArray);
        methodCountPtr.write(nbWritten);
        return JvmtiError.JVMTI_ERROR_NONE;

    }

    public static JvmtiError getClassModifiers(JClass klass, CIntPointer modifiersPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        modifiersPtr.write(javaClass.getModifiers());
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getSourceFileName(JClass klass, CCharPointerPointer sourceNamePtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        String sourceFileName = DynamicHub.fromClass(javaClass).getSourceFileName();
        UnsignedWord bufferSize = WordFactory.unsigned(UninterruptibleUtils.String.modifiedUTF8Length(sourceFileName, true, null));
        CCharPointer buffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(bufferSize);
        UninterruptibleUtils.String.toModifiedUTF8(sourceFileName, (Pointer) buffer, ((Pointer) buffer).add(bufferSize), true);
        ((Pointer) sourceNamePtr).writeWord(0, buffer);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    /*
     * Fields
     */
    public static JvmtiError getFieldName(JClass klass, JNIFieldId fieldId, CCharPointerPointer namePtr, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        Field field = getFieldFromHandle(javaClass, fieldId, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        return singleton().writeFieldInfo(field, namePtr, signaturePtr, genericPtr);
    }

    public static JvmtiError getFieldDeclaringClass(JClass klass, JNIFieldId fieldId, JClassPointer declaringClassPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        Field field = getFieldFromHandle(javaClass, fieldId, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }

        if (!isValidFieldId(javaClass, fieldId)) {
            return JvmtiError.JVMTI_ERROR_INVALID_FIELDID;
        }

        Class<?> declaringClass = field.getDeclaringClass();
        JClass jclass = (JClass) JNIObjectHandles.createLocal(declaringClass);
        ((Pointer) declaringClassPtr).writeWord(0, jclass);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getFieldModifiers(JClass klass, JNIFieldId fieldId, CIntPointer modifiersPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        Field field = getFieldFromHandle(javaClass, fieldId, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }

        if (!isValidFieldId(javaClass, fieldId)) {
            return JvmtiError.JVMTI_ERROR_INVALID_FIELDID;
        }

        int fieldModifiers = field.getModifiers();
        modifiersPtr.write(fieldModifiers);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError isFieldSynthetic(JClass klass, JNIFieldId fieldId, BooleanPointer isSyntheticPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        Field field = getFieldFromHandle(javaClass, fieldId, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }

        if (!isValidFieldId(javaClass, fieldId)) {
            return JvmtiError.JVMTI_ERROR_INVALID_FIELDID;
        }

        boolean isSynthetic = field.isSynthetic();
        ((Pointer) isSyntheticPtr).writeByte(0, isSynthetic ? JNI_TRUE : JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    /*
     * Methods
     */
    public static JvmtiError getMethodDeclaringClass(JNIMethodId method, JClassPointer declaringClassPtr) {
        JNIAccessibleMethod accessibleMethod = JNIReflectionDictionary.getMethodByID(method);
        if (accessibleMethod == null) {
            return null;
        }
        Class<?> declaringClass = accessibleMethod.getDeclaringClass().getClassObject();
        if (declaringClass == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_METHODID;
        }
        JNIObjectHandle jClass = JNIObjectHandles.createLocal(declaringClass);
        ((Pointer) declaringClassPtr).writeWord(0, jClass);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getMethodModifiers(JNIMethodId methodId, CIntPointer modifiersPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Method m = getMethodFromHandle(methodId, error);
        if (JvmtiError.fromValue(error.read()) != JvmtiError.JVMTI_ERROR_NONE) {
            return JvmtiError.fromValue(error.read());
        }
        modifiersPtr.write(m.getModifiers());
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError isMethodNative(JNIMethodId methodId, BooleanPointer isNativePtr) {
        if (JNIReflectionDictionary.getMethodByID(methodId) == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_METHODID;
        }
        ((Pointer) isNativePtr).writeByte(0, JNIReflectionDictionary.isMethodNative(methodId) ? JNI_TRUE : JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError isMethodSynthetic(JNIMethodId methodId, BooleanPointer isSyntheticPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Method m = getMethodFromHandle(methodId, error);
        if (JvmtiError.fromValue(error.read()) != JvmtiError.JVMTI_ERROR_NONE) {
            return JvmtiError.fromValue(error.read());
        }
        ((Pointer) isSyntheticPtr).writeByte(0, m.isSynthetic() ? JNI_TRUE : JNI_FALSE);
        return JvmtiError.JVMTI_ERROR_INVALID_METHODID;
    }

    public static JvmtiError getMethodName(JNIMethodId methodId, CCharPointerPointer namePtr, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        JNIAccessibleMethod accessibleMethod = JNIReflectionDictionary.getMethodByID(methodId);
        if (accessibleMethod == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_METHODID;
        }
        JNIAccessibleMethodDescriptor descriptor = JNIReflectionDictionary.getMethodDescriptor(accessibleMethod);
        JvmtiError error;
        // name
        if ((error = writeStringOrNull(descriptor.getName(), namePtr)) != JvmtiError.JVMTI_ERROR_NONE) {
            return error;
        }
        // signature
        if ((error = writeStringOrNull(descriptor.getSignature(), signaturePtr)) != JvmtiError.JVMTI_ERROR_NONE) {
            return error;
        }

        // generic pointer
        if (genericPtr.isNonNull()) {
            CIntPointer errorPtr = StackValue.get(CIntPointer.class);
            Method m = getMethodFromHandle(methodId, errorPtr);
            if (JvmtiError.fromValue(errorPtr.read()) != JvmtiError.JVMTI_ERROR_NONE) {
                genericPtr.write(0, WordFactory.nullPointer());
                return JvmtiError.fromValue(errorPtr.read());
            }
            String genericSignature = SubstrateUtil.cast(m, Target_java_lang_reflect_Method.class).signature;
            if (genericSignature == null) {
                genericPtr.write(0, WordFactory.nullPointer());
                return JvmtiError.JVMTI_ERROR_NONE;
            }
            return writeStringToUTF8(genericSignature, genericPtr);
        }
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    /*
     * Helpers
     */
    private static <T extends ComparableWord, P extends PointerBase> void writeArrayAtIndex(P array, int index, int elementSize, T value) {
        ((Pointer) array).writeWord(index * elementSize, value);
    }

    private static <T extends PointerBase> T getResultArray(int arraySize, int elementSize) {
        int arrayByteSize = ConfigurationValues.getTarget().wordSize * arraySize;
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(arrayByteSize));
    }

    private static boolean isValidFieldId(Class<?> javaClass, JNIFieldId fieldId) {
        return JNIReflectionDictionary.singleton().getFieldNameByID(javaClass, fieldId) != null;
    }

    private static Class<?> getClassFromHandle(JClass klass, CIntPointer error) {
        Class<?> javaClass;
        try {
            javaClass = JNIObjectHandles.getObject(klass);
        } catch (ClassCastException | IllegalArgumentException e) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_CLASS.getCValue());
            return null;
        }
        if (javaClass == null) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_CLASS.getCValue());
            return null;
        }
        error.write(JvmtiError.JVMTI_ERROR_NONE.getCValue());
        return javaClass;
    }

    private static Field getFieldFromHandle(Class<?> javaClass, JNIFieldId fieldId, CIntPointer error) {
        String name = JNIReflectionDictionary.singleton().getFieldNameByID(javaClass, fieldId);
        if (name == null) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_FIELDID.getCValue());
            return null;
        }
        Field field;
        try {
            field = javaClass.getField(name);
        } catch (NoSuchFieldException e) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_FIELDID.getCValue());
            return null;
        }
        error.write(JvmtiError.JVMTI_ERROR_NONE.getCValue());
        return field;
    }

    private static Method getMethodFromHandle(JNIMethodId methodID, CIntPointer error) {
        JNIAccessibleMethod accessibleMethod = JNIReflectionDictionary.getMethodByID(methodID);
        if (accessibleMethod == null) {
            error.write(JvmtiError.JVMTI_ERROR_INVALID_METHODID.getCValue());
            return null;
        }
        Class<?> declaringClass = accessibleMethod.getDeclaringClass().getClassObject();
        String signature = JNIReflectionDictionary.getMethodDescriptor(accessibleMethod).getSignature();
        for (Method m : declaringClass.getMethods()) {
            // TODO @dprcci fix
            String sig = SubstrateUtil.cast(m, Target_java_lang_reflect_Method.class).signature;
            if (Objects.equals(sig, signature)) {
                error.write(JvmtiError.JVMTI_ERROR_NONE.getCValue());
                return m;
            }
            error.write(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
            return null;
        }
        error.write(JvmtiError.JVMTI_ERROR_INVALID_METHODID.getCValue());
        return null;
    }

    private static JvmtiError writeStringOrNull(String value, CCharPointerPointer bufferPtr) {
        if (bufferPtr.isNonNull()) {
            return writeStringToUTF8(value, bufferPtr);
        } else {
            bufferPtr.write(0, WordFactory.nullPointer());
            return JvmtiError.JVMTI_ERROR_NONE;
        }
    }

    private JvmtiError writeFieldInfo(Field field, CCharPointerPointer namePtr, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        try {
            mutex.lock();
            JvmtiError error;
            if (namePtr.isNonNull()) {
                String name = field.getName();
                error = writeStringToUTF8(name, namePtr);
                if (error != JvmtiError.JVMTI_ERROR_NONE) {
                    return error;
                }
            }
            if (signaturePtr.isNonNull()) {
                error = createFieldSignature(field, signaturePtr);
                if (error != JvmtiError.JVMTI_ERROR_NONE) {
                    return error;
                }
            }
            if (genericPtr.isNonNull()) {
                if (!field.getType().equals(field.getGenericType())) {
                    return createGenericFieldSignature(field, genericPtr);
                } else {
                    genericPtr.write(WordFactory.nullPointer());
                }
            }
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    private JvmtiError createFieldSignature(Field field, CCharPointerPointer signaturePtr) {
        Class<?> fieldType = field.getType();
        int signatureSize;
        if (fieldType.isPrimitive()) {
            signatureSize = addTypeToName(fieldType, signatureCharBuffer);
        } else {
            signatureSize = encodeNameFromString(fieldType.getName(), signatureCharBuffer, 0);
        }
        return writeStringToCCharArray(signatureCharBuffer, signatureSize, signaturePtr, nameToSigReplacer);
    }

    // TODO @dprcci temp
    private JvmtiError createGenericFieldSignature(Field field, CCharPointerPointer genericSignaturePtr) {
        String sig = field.toGenericString();
        return writeStringToUTF8(sig, genericSignaturePtr);
    }

    private JvmtiError getClassSignatureInternal(JClass klass, CCharPointerPointer signaturePtr, CCharPointerPointer genericPtr) {
        CIntPointer error = StackValue.get(CIntPointer.class);
        Class<?> javaClass = getClassFromHandle(klass, error);
        if (error.read() != JvmtiError.JVMTI_ERROR_NONE.getCValue()) {
            return JvmtiError.fromValue(error.read());
        }
        return writeClassSignatures(javaClass, signaturePtr, genericPtr);
    }

    private JvmtiError writeClassSignatures(Class<?> javaClass, CCharPointerPointer signaturePtr, CCharPointerPointer genericSignaturePtr) {
        try {
            mutex.lock();
            if (signaturePtr.isNonNull()) {
                JvmtiError error = createClassSignature(javaClass, signaturePtr);
                if (error != JvmtiError.JVMTI_ERROR_NONE) {
                    return error;
                }
            }
            if (genericSignaturePtr.isNonNull()) {
                String genericSignature = JVMTIGenericInfoMap.singleton().classSignatures.getOrDefault(javaClass, null);
                if (genericSignature != null) {
                    String signature = JVMTIGenericInfoMap.singleton().classSignatures.get(javaClass);
                    return writeStringToUTF8(signature, genericSignaturePtr);
                } else {
                    genericSignaturePtr.write(WordFactory.nullPointer());
                }
            }
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    private JvmtiError createClassSignature(Class<?> javaClass, CCharPointerPointer signaturePtr) {
        String signature = DynamicHub.fromClass(javaClass).getName();
        int startIndex = addTypeToName(javaClass, signatureCharBuffer);
        int signatureSize = encodeNameFromString(signature, signatureCharBuffer, startIndex);
        return writeStringToCCharArray(signatureCharBuffer, signatureSize, signaturePtr, nameToSigReplacer);
    }

    private static int addTypeToName(Class<?> clazz, char[] buffer) {
        char desc;
        int index = 0;
        if (clazz.isArray()) {
            return 0;
        }
        if (clazz == boolean.class) {
            desc = 'Z';
        } else if (clazz == byte.class) {
            desc = 'B';
        } else if (clazz == char.class) {
            desc = 'C';
        } else if (clazz == short.class) {
            desc = 'S';
        } else if (clazz == int.class) {
            desc = 'I';
        } else if (clazz == long.class) {
            desc = 'J';
        } else if (clazz == float.class) {
            desc = 'F';
        } else if (clazz == double.class) {
            desc = 'D';
        } else if (clazz == void.class) {
            desc = 'V';
        } else {
            desc = 'L';
        }
        buffer[index++] = desc;
        return index;
    }

    private static int encodeNameFromString(String name, char[] buffer, int fromIndex) {
        if (fromIndex + name.length() >= MAX_SIGNATURE_LENGTH) {
            return -1;
        }
        for (int i = 0; i < name.length(); i++) {
            char curr = JavaLangSubstitutions.StringUtil.charAt(name, i);
            buffer[fromIndex + i] = curr;
        }
        return fromIndex + name.length();
    }

    private static JvmtiError writeStringToUTF8(String signature, CCharPointerPointer signaturePtr) {
        UnsignedWord bufferSize = WordFactory.unsigned(UninterruptibleUtils.String.modifiedUTF8Length(signature, true, null));
        CCharPointer cStringBuffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(bufferSize);
        if (cStringBuffer.isNull()) {
            signaturePtr.write(WordFactory.nullPointer());
            return JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY;
        }
        UninterruptibleUtils.String.toModifiedUTF8(signature, (Pointer) cStringBuffer, ((Pointer) cStringBuffer).add(bufferSize), true, null);
        signaturePtr.write(0, cStringBuffer);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    // TODO @dprcci might actually be useless thanks to Target_class. Must test and check
    public static class JVMTIGenericInfoMap {

        private IdentityHashMap<Class<?>, String> classSignatures;
        private static final int HEURISTIC_NB_CLASSES = 512;

        @Platforms(Platform.HOSTED_ONLY.class)
        public JVMTIGenericInfoMap() {
            this.classSignatures = new IdentityHashMap<>(HEURISTIC_NB_CLASSES);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public void addSignature(Class<?> clazz, String signature) {
            classSignatures.putIfAbsent(clazz, signature);
        }

        @Fold
        public static JVMTIGenericInfoMap singleton() {
            return ImageSingletons.lookup(JVMTIGenericInfoMap.class);
        }

        @Platforms(Platform.HOSTED_ONLY.class)
        public void discardUnused() {
            IdentityHashMap<Class<?>, String> jniSignatures = new IdentityHashMap<>();
            Set<Class<?>> jniRegisteredClasses = JNIReflectionDictionary.singleton().getRegisteredClasses();
            // TODO @dprcci necessary to reduce Map size?
            classSignatures.entrySet().stream().filter(e -> jniRegisteredClasses.contains(e.getKey())).forEach(e -> jniSignatures.putIfAbsent(e.getKey(), e.getValue()));
            classSignatures = jniSignatures;
        }

    }
}