/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.functions;

// Checkstyle: allow reflection

import java.io.CharConversionException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MonitorSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.function.CEntryPointActions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.Publish;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.Utf8;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.JNIGlobalHandles;
import com.oracle.svm.jni.JNIObjectHandles;
import com.oracle.svm.jni.JNIThreadLocalHandles;
import com.oracle.svm.jni.JNIThreadLocalPendingException;
import com.oracle.svm.jni.JNIThreadLocalPinnedObjects;
import com.oracle.svm.jni.JNIThreadOwnedMonitors;
import com.oracle.svm.jni.access.JNIAccessFeature;
import com.oracle.svm.jni.access.JNIAccessibleMethod;
import com.oracle.svm.jni.access.JNIAccessibleMethodDescriptor;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.access.JNIReflectionDictionary;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIEnvironmentEnterPrologue;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnFalse;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnJniErr;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnMinusOne;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnNullHandle;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerReturnNullWord;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIExceptionHandlerVoid;
import com.oracle.svm.jni.functions.JNIFunctions.Support.JNIJavaVMEnterAttachThreadPrologue;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIErrors;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIJavaVM;
import com.oracle.svm.jni.nativeapi.JNIJavaVMPointer;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeMethod;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIObjectRefType;
import com.oracle.svm.jni.nativeapi.JNIVersion;

import jdk.vm.ci.meta.MetaUtil;

/**
 * Implementations of the functions defined by the Java Native Interface. Not all functions are
 * currently implemented.
 *
 * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html">Java
 *      Native Interface Specification: JNI Functions</a>
 */
@SuppressWarnings("unused")
final class JNIFunctions {

    // Checkstyle: stop

    /*
     * jint GetVersion(JNIEnv *env);
     */

    @CEntryPoint
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetVersion(JNIEnvironment env) {
        return JNIVersion.JNI_VERSION_1_8();
    }

    /*
     * jobject NewLocalRef(JNIEnv *env, jobject ref);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewLocalRef(JNIEnvironment env, JNIObjectHandle ref) {
        return JNIThreadLocalHandles.get().create(JNIObjectHandles.getObject(ref));
    }

    /*
     * void DeleteLocalRef(JNIEnv *env, jobject localRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void DeleteLocalRef(JNIEnvironment env, JNIObjectHandle localRef) {
        JNIThreadLocalHandles.get().delete(localRef);
    }

    /*
     * jint EnsureLocalCapacity(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int EnsureLocalCapacity(JNIEnvironment env, int capacity) {
        if (capacity < 0) {
            return JNIErrors.JNI_ERR();
        }
        JNIThreadLocalHandles.get().ensureCapacity(capacity);
        return 0;
    }

    /*
     * jint PushLocalFrame(JNIEnv *env, jint capacity);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int PushLocalFrame(JNIEnvironment env, int capacity) {
        if (capacity < 0) {
            return JNIErrors.JNI_ERR();
        }
        JNIThreadLocalHandles.get().pushFrame(capacity);
        return 0;
    }

    /*
     * jobject PopLocalFrame(JNIEnv *env, jobject result);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle PopLocalFrame(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        JNIThreadLocalHandles.get().popFrame();
        return JNIThreadLocalHandles.get().create(obj);
    }

    /*
     * jboolean IsSameObject(JNIEnv *env, jobject ref1, jobject ref2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static boolean IsSameObject(JNIEnvironment env, JNIObjectHandle ref1, JNIObjectHandle ref2) {
        Object obj1 = JNIObjectHandles.getObject(ref1);
        Object obj2 = JNIObjectHandles.getObject(ref2);
        return obj1 == obj2;
    }

    /*
     * jboolean IsInstanceOf(JNIEnv *env, jobject obj, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static boolean IsInstanceOf(JNIEnvironment env, JNIObjectHandle obj, JNIObjectHandle clazz) {
        Object o = JNIObjectHandles.getObject(obj);
        if (o == null) {
            // JNI specifies: "A NULL object can be cast to any class"
            return true;
        }
        Class<?> c = JNIObjectHandles.getObject(clazz);
        return c.isInstance(o);
    }

    /*
     * jclass GetObjectClass(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle GetObjectClass(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        Class<?> clazz = obj.getClass();
        return JNIThreadLocalHandles.get().create(clazz);
    }

    /*
     * jclass GetSuperclass(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle GetSuperclass(JNIEnvironment env, JNIObjectHandle handle) {
        Class<?> clazz = JNIObjectHandles.getObject(handle);
        return JNIThreadLocalHandles.get().create(clazz.getSuperclass());
    }

    /*
     * jboolean IsAssignableFrom(JNIEnv *env, jclass clazz1, jclass clazz2);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnFalse.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static boolean IsAssignableFrom(JNIEnvironment env, JNIObjectHandle handle1, JNIObjectHandle handle2) {
        Class<?> clazz1 = JNIObjectHandles.getObject(handle1);
        Class<?> clazz2 = JNIObjectHandles.getObject(handle2);
        return clazz2.isAssignableFrom(clazz1);
    }

    /*
     * jobject NewGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        JNIObjectHandle result = JNIObjectHandles.nullHandle();
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj != null) {
            result = (JNIObjectHandle) JNIGlobalHandles.singleton().create(obj);
        }
        return result;
    }

    /*
     * void DeleteGlobalRef(JNIEnv *env, jobject globalRef);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void DeleteGlobalRef(JNIEnvironment env, JNIObjectHandle globalRef) {
        JNIGlobalHandles.singleton().destroy(globalRef);
    }

    /*
     * jweak NewWeakGlobalRef(JNIEnv *env, jobject obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewWeakGlobalRef(JNIEnvironment env, JNIObjectHandle handle) {
        JNIObjectHandle result = JNIObjectHandles.nullHandle();
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj != null) {
            result = (JNIObjectHandle) JNIGlobalHandles.singleton().createWeak(obj);
        }
        return result;
    }

    /*
     * void DeleteWeakGlobalRef(JNIEnv *env, jweak obj);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void DeleteWeakGlobalRef(JNIEnvironment env, JNIObjectHandle weak) {
        JNIGlobalHandles.singleton().destroyWeak(weak);
    }

    /*
     * jobjectRefType GetObjectRefType(JNIEnv* env, jobject obj);
     */

    @CEntryPoint
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectRefType GetObjectRefType(JNIEnvironment env, JNIObjectHandle handle) {
        try {
            return JNIObjectHandles.getHandleType(handle);
        } catch (Throwable t) {
            return JNIObjectRefType.Invalid;
        }
    }

    /*
     * jclass FindClass(JNIEnv *env, const char *name);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle FindClass(JNIEnvironment env, CCharPointer cname) {
        String name = CTypeConversion.toJavaString(cname);
        if (!name.startsWith("[")) {
            name = "L" + name + ";";
        }
        Class<?> clazz = JNIReflectionDictionary.singleton().getClassObjectByName(name);
        return JNIThreadLocalHandles.get().create(clazz);
    }

    /*
     * jint RegisterNatives(JNIEnv *env, jclass clazz, const JNINativeMethod *methods, jint
     * nMethods);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int RegisterNatives(JNIEnvironment env, JNIObjectHandle hclazz, JNINativeMethod methods, int nmethods) {
        Class<?> clazz = JNIObjectHandles.getObject(hclazz);
        Pointer p = (Pointer) methods;
        for (int i = 0; i < nmethods; i++) {
            JNINativeMethod entry = (JNINativeMethod) p;
            String name = CTypeConversion.toJavaString(entry.name());
            String signature = CTypeConversion.toJavaString(entry.signature());
            CFunctionPointer fnPtr = entry.fnPtr();

            String declaringClass = MetaUtil.toInternalName(clazz.getName());
            JNINativeLinkage linkage = JNIReflectionDictionary.singleton().getLinkage(declaringClass, name, signature);
            if (linkage != null) {
                linkage.setEntryPoint(fnPtr);
            } else {
                throw new NoSuchMethodError(clazz.getName() + '.' + name + signature);
            }

            p = p.add(SizeOf.get(JNINativeMethod.class));
        }
        return JNIErrors.JNI_OK();
    }

    /*
     * jint UnregisterNatives(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int UnregisterNatives(JNIEnvironment env, JNIObjectHandle hclazz) {
        Class<?> clazz = JNIObjectHandles.getObject(hclazz);
        String internalName = MetaUtil.toInternalName(clazz.getName());
        for (JNINativeLinkage linkage : JNIReflectionDictionary.singleton().getLinkages(internalName)) {
            linkage.unsetEntryPoint();
        }
        return JNIErrors.JNI_OK();
    }

    /*
     * jmethodID GetMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     *
     * jmethodID GetStaticMethodID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIMethodId GetMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getMethodID(hclazz, cname, csig, false);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIMethodId GetStaticMethodID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getMethodID(hclazz, cname, csig, true);
    }

    /*
     * jfieldID GetFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);\
     *
     * jfieldID GetStaticFieldID(JNIEnv *env, jclass clazz, const char *name, const char *sig);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIFieldId GetFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getFieldID(hclazz, cname, csig);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIFieldId GetStaticFieldID(JNIEnvironment env, JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
        return Support.getFieldID(hclazz, cname, csig);
    }

    /*
     * jobject AllocObject(JNIEnv *env, jclass clazz);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle AllocObject(JNIEnvironment env, JNIObjectHandle classHandle) {
        Class<?> clazz = JNIObjectHandles.getObject(classHandle);
        Object instance;
        try {
            instance = UnsafeAccess.UNSAFE.allocateInstance(clazz);
        } catch (InstantiationException e) {
            instance = null;
        }
        return JNIThreadLocalHandles.get().create(instance);
    }

    /*
     * jstring NewString(JNIEnv *env, const jchar *unicodeChars, jsize len);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewString(JNIEnvironment env, CShortPointer unicode, int len) {
        String str;
        char[] chars = new char[len];
        for (int i = 0; i < chars.length; i++) {
            int value = Short.toUnsignedInt(unicode.read(i));
            chars[i] = (char) value;
        }
        str = new String(chars);
        return JNIThreadLocalHandles.get().create(str);
    }

    /*
     * jstring NewStringUTF(JNIEnv *env, const char *bytes);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewStringUTF(JNIEnvironment env, CCharPointer bytes) {
        String str = null;
        if (bytes.isNonNull()) {
            ByteBuffer buffer = SubstrateUtil.wrapAsByteBuffer(bytes, Integer.MAX_VALUE);
            try {
                str = Utf8.utf8ToString(true, buffer);
            } catch (CharConversionException ignore) {
            }
        }
        return JNIThreadLocalHandles.get().create(str);
    }

    /*
     * jsize GetStringLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetStringLength(JNIEnvironment env, JNIObjectHandle hstr) {
        String str = JNIObjectHandles.getObject(hstr);
        return (str != null) ? str.length() : 0;
    }

    /*
     * jsize GetStringUTFLength(JNIEnv *env, jstring string);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetStringUTFLength(JNIEnvironment env, JNIObjectHandle hstr) {
        String str = JNIObjectHandles.getObject(hstr);
        return Utf8.utf8Length(str);
    }

    /*
     * const jchar * GetStringChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringChars(JNIEnv *env, jstring string, const jchar *chars);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static CShortPointer GetStringChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        return Support.pinStringAndGetChars(hstr, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ReleaseStringChars(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer chars) {
        Support.unpinString(chars);
    }

    /*
     * const char * GetStringUTFChars(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringUTFChars(JNIEnv *env, jstring string, const char *utf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static CCharPointer GetStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        String str = JNIObjectHandles.getObject(hstr);
        if (str == null) {
            return WordFactory.nullPointer();
        }
        if (isCopy.isNonNull()) {
            isCopy.write((byte) 1);
        }
        byte[] utf = Utf8.stringToUtf8(str, true);
        return JNIThreadLocalPinnedObjects.pinArrayAndGetAddress(utf);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ReleaseStringUTFChars(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer chars) {
        JNIThreadLocalPinnedObjects.unpinArrayByAddress(chars);
    }

    /*
     * const jchar * GetStringCritical(JNIEnv *env, jstring string, jboolean *isCopy);
     *
     * void ReleaseStringCritical(JNIEnv *env, jstring string, const jchar *carray);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static CShortPointer GetStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CCharPointer isCopy) {
        return Support.pinStringAndGetChars(hstr, isCopy);
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ReleaseStringCritical(JNIEnvironment env, JNIObjectHandle hstr, CShortPointer carray) {
        Support.unpinString(carray);
    }

    /*
     * void GetStringRegion(JNIEnv *env, jstring str, jsize start, jsize len, jchar *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void GetStringRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CShortPointer buf) {
        String str = JNIObjectHandles.getObject(hstr);
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (start + len > str.length()) {
            throw new StringIndexOutOfBoundsException(start + len);
        }
        if (len < 0) {
            throw new StringIndexOutOfBoundsException(len);
        }
        for (int i = 0; i < len; i++) {
            char c = str.charAt(start + i);
            buf.write(i, (short) c);
        }
    }

    /*
     * void GetStringUTFRegion(JNIEnv *env, jstring str, jsize start, jsize len, char *buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void GetStringUTFRegion(JNIEnvironment env, JNIObjectHandle hstr, int start, int len, CCharPointer buf) {
        String str = JNIObjectHandles.getObject(hstr);
        if (start < 0) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (start + len > str.length()) {
            throw new StringIndexOutOfBoundsException(start + len);
        }
        if (len < 0) {
            throw new StringIndexOutOfBoundsException(len);
        }
        int capacity = Utf8.maxUtf8ByteLength(len, true); // estimate: caller must pre-allocate
                                                          // enough
        ByteBuffer buffer = SubstrateUtil.wrapAsByteBuffer(buf, capacity);
        Utf8.substringToUtf8(buffer, str, start, start + len, true);
    }

    /*
     * jobject NewDirectByteBuffer(JNIEnv* env, void* address, jlong capacity);
     */

    @TargetClass(className = "java.nio.DirectByteBuffer")
    static final class Target_java_nio_DirectByteBuffer {
        @Alias
        Target_java_nio_DirectByteBuffer(long addr, int cap) {
        }

    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewDirectByteBuffer(JNIEnvironment env, WordPointer address, long capacity) {
        Target_java_nio_DirectByteBuffer bb = new Target_java_nio_DirectByteBuffer(address.rawValue(), (int) capacity);
        return JNIThreadLocalHandles.get().create(bb);
    }

    /*
     * void* GetDirectBufferAddress(JNIEnv* env, jobject buf);
     */

    @TargetClass(java.nio.Buffer.class)
    static final class Target_java_nio_Buffer {
        @Alias long address;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static WordPointer GetDirectBufferAddress(JNIEnvironment env, JNIObjectHandle handle) {
        WordPointer address = WordFactory.nullPointer();
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj instanceof Target_java_nio_Buffer) {
            Target_java_nio_Buffer buf = (Target_java_nio_Buffer) obj;
            address = WordFactory.pointer(buf.address);
        }
        return address;
    }

    /*
     * jlong GetDirectBufferCapacity(JNIEnv* env, jobject buf);
     */

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static long GetDirectBufferCapacity(JNIEnvironment env, JNIObjectHandle hbuf) {
        Buffer buffer = JNIObjectHandles.getObject(hbuf);
        return buffer.capacity();
    }

    /*
     * jobjectArray NewObjectArray(JNIEnv *env, jsize length, jclass elementClass, jobject
     * initialElement);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle NewObjectArray(JNIEnvironment env, int length, JNIObjectHandle hElementClass, JNIObjectHandle hInitialElement) {
        if (length < 0) {
            return JNIObjectHandles.nullHandle();
        }
        Class<?> elementClass = JNIObjectHandles.getObject(hElementClass);
        Object[] array = null;
        if (elementClass != null) {
            Object initialElement = JNIObjectHandles.getObject(hInitialElement);
            array = (Object[]) Array.newInstance(elementClass, length);
            Arrays.fill(array, initialElement);
        }
        return JNIThreadLocalHandles.get().create(array);
    }

    /*
     * jobject GetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle GetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index) {
        Object[] array = JNIObjectHandles.getObject(harray);
        Object value = array[index];
        return JNIThreadLocalHandles.get().create(value);
    }

    /*
     * void SetObjectArrayElement(JNIEnv *env, jobjectArray array, jsize index, jobject value);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void SetObjectArrayElement(JNIEnvironment env, JNIObjectHandle harray, int index, JNIObjectHandle hvalue) {
        Object[] array = JNIObjectHandles.getObject(harray);
        Object value = JNIObjectHandles.getObject(hvalue);
        array[index] = value;
    }

    /*
     * jvoid * GetPrimitiveArrayCritical(JNIEnv *env, jarray array, jboolean *isCopy);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static WordPointer GetPrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, CIntPointer isCopy) {
        Object array = JNIObjectHandles.getObject(harray);
        if (array == null) {
            return WordFactory.nullPointer();
        }
        if (isCopy.isNonNull()) {
            isCopy.write((byte) 0);
        }
        return JNIThreadLocalPinnedObjects.pinArrayAndGetAddress(array);
    }

    /*
     * void ReleasePrimitiveArrayCritical(JNIEnv *env, jarray array, void *carray, jint mode);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ReleasePrimitiveArrayCritical(JNIEnvironment env, JNIObjectHandle harray, WordPointer carray, CIntPointer isCopy) {
        JNIThreadLocalPinnedObjects.unpinArrayByAddress(carray);
    }

    /*
     * jsize GetArrayLength(JNIEnv *env, jarray array);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnMinusOne.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetArrayLength(JNIEnvironment env, JNIObjectHandle harray) {
        return KnownIntrinsics.readArrayLength(JNIObjectHandles.getObject(harray));
    }

    /*
     * jboolean ExceptionCheck(JNIEnv *env);
     *
     * jthrowable ExceptionOccurred(JNIEnv *env);
     */

    @CEntryPoint
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static boolean ExceptionCheck(JNIEnvironment env) {
        return JNIThreadLocalPendingException.get() != null;
    }

    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle ExceptionOccurred(JNIEnvironment env) {
        return JNIThreadLocalHandles.get().create(JNIThreadLocalPendingException.get());
    }

    /*
     * void ExceptionClear(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ExceptionClear(JNIEnvironment env) {
        JNIThreadLocalPendingException.clear();
    }

    /*
     * void ExceptionDescribe(JNIEnv *env);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ExceptionDescribe(JNIEnvironment env) {
        Throwable t = JNIThreadLocalPendingException.get();
        JNIThreadLocalPendingException.clear();
        if (t != null) {
            if (t instanceof ThreadDeath) {
                // this thread is being killed, do not print anything
            } else {
                System.err.println("Exception in thread \"" + Thread.currentThread().getName() + "\": " + t.getClass().getCanonicalName());
                try {
                    t.printStackTrace();
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
    }

    /*
     * jint Throw(JNIEnv *env, jthrowable obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void Throw(JNIEnvironment env, JNIObjectHandle handle) throws Throwable {
        throw (Throwable) JNIObjectHandles.getObject(handle);
    }

    interface NewObjectWithObjectArgFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JNIObjectHandle invoke(JNIEnvironment env, JNIObjectHandle clazz, JNIMethodId ctor, JNIObjectHandle arg);
    }

    /*
     * jint ThrowNew(JNIEnv *env, jclass clazz, const char *message);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void ThrowNew(JNIEnvironment env, JNIObjectHandle clazzHandle, CCharPointer message) throws Throwable {
        Class<?> clazz = JNIObjectHandles.getObject(clazzHandle);
        JNIMethodId ctor = JNIReflectionDictionary.singleton().getMethodID(clazz, "<init>", "(Ljava/lang/String;)V", false);
        JNIObjectHandle messageHandle = NewStringUTF(env, message);
        NewObjectWithObjectArgFunctionPointer newObject = (NewObjectWithObjectArgFunctionPointer) env.getFunctions().getNewObject();
        JNIObjectHandle exception = newObject.invoke(env, clazzHandle, ctor, messageHandle);
        throw (Throwable) JNIObjectHandles.getObject(exception);
    }

    /*
     * void FatalError(JNIEnv *env, const char *msg);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerVoid.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static void FatalError(JNIEnvironment env, CCharPointer message) {
        Log log = Log.log().autoflush(true);
        log.string("Fatal error reported via JNI: ").string(message).newline();
        VMThreads.StatusSupport.setStatusIgnoreSafepoints();
        SubstrateUtil.printDiagnostics(log, KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress());
        ImageSingletons.lookup(LogHandler.class).fatalError();
    }

    /*
     * jint GetJavaVM(JNIEnv *env, JavaVM **vm);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int GetJavaVM(JNIEnvironment env, JNIJavaVMPointer vm) throws Throwable {
        vm.write(JNIFunctionTables.singleton().getGlobalJavaVM());
        return JNIErrors.JNI_OK();
    }

    /*
     * jfieldID FromReflectedField(JNIEnv *env, jobject field);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIFieldId FromReflectedField(JNIEnvironment env, JNIObjectHandle fieldHandle) {
        JNIFieldId fieldId = WordFactory.zero();
        if (JNIAccessFeature.singleton().haveJavaRuntimeReflectionSupport()) {
            Field obj = JNIObjectHandles.getObject(fieldHandle);
            if (obj != null) {
                boolean isStatic = Modifier.isStatic(obj.getModifiers());
                fieldId = JNIReflectionDictionary.singleton().getFieldID(obj.getDeclaringClass(), obj.getName());
            }
        }
        return fieldId;
    }

    /*
     * jobject ToReflectedField(JNIEnv *env, jclass cls, jfieldID fieldID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle ToReflectedField(JNIEnvironment env, JNIObjectHandle classHandle, JNIFieldId fieldId) {
        Field field = null;
        if (JNIAccessFeature.singleton().haveJavaRuntimeReflectionSupport()) {
            Class<?> clazz = JNIObjectHandles.getObject(classHandle);
            if (clazz != null) {
                String name = JNIReflectionDictionary.singleton().getFieldNameByID(clazz, fieldId);
                if (name != null) {
                    try {
                        field = clazz.getDeclaredField(name);
                    } catch (NoSuchFieldException ignored) {
                        // proceed and return null
                    }
                }
            }
        }
        return JNIThreadLocalHandles.get().create(field);
    }

    /*
     * jmethodID FromReflectedMethod(JNIEnv *env, jobject method);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullWord.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIMethodId FromReflectedMethod(JNIEnvironment env, JNIObjectHandle methodHandle) {
        JNIMethodId methodId = WordFactory.nullPointer();
        if (JNIAccessFeature.singleton().haveJavaRuntimeReflectionSupport()) {
            Executable method = JNIObjectHandles.getObject(methodHandle);
            if (method != null) {
                boolean isStatic = Modifier.isStatic(method.getModifiers());
                JNIAccessibleMethodDescriptor descriptor = JNIAccessibleMethodDescriptor.of(method);
                methodId = JNIReflectionDictionary.singleton().getMethodID(method.getDeclaringClass(), descriptor, isStatic);
            }
        }
        return methodId;
    }

    /*
     * jobject ToReflectedMethod(JNIEnv *env, jclass cls, jmethodID methodID, jboolean isStatic);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnNullHandle.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static JNIObjectHandle ToReflectedMethod(JNIEnvironment env, JNIObjectHandle classHandle, JNIMethodId methodId, boolean isStatic) {
        Executable result = null;
        if (JNIAccessFeature.singleton().haveJavaRuntimeReflectionSupport()) {
            JNIAccessibleMethod jniMethod = JNIReflectionDictionary.getMethodByID(methodId);
            JNIAccessibleMethodDescriptor descriptor = JNIReflectionDictionary.getMethodDescriptor(jniMethod);
            if (descriptor != null) {
                Class<?> clazz = jniMethod.getDeclaringClass().getClassObject();
                if (descriptor.isConstructor()) {
                    for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                        if (descriptor.equals(JNIAccessibleMethodDescriptor.of(ctor))) {
                            result = ctor;
                            break;
                        }
                    }
                } else {
                    for (Method method : clazz.getDeclaredMethods()) {
                        if (descriptor.getName().equals(method.getName())) {
                            if (descriptor.equals(JNIAccessibleMethodDescriptor.of(method))) {
                                result = method;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return JNIThreadLocalHandles.get().create(result);
    }

    /*
     * jint MonitorEnter(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int MonitorEnter(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj == null) {
            throw new NullPointerException();
        }
        MonitorSupport.monitorEnter(obj);
        assert Thread.holdsLock(obj);
        JNIThreadOwnedMonitors.entered(obj);
        return JNIErrors.JNI_OK();
    }

    /*
     * jint MonitorExit(JNIEnv *env, jobject obj);
     */
    @CEntryPoint(exceptionHandler = JNIExceptionHandlerReturnJniErr.class)
    @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
    static int MonitorExit(JNIEnvironment env, JNIObjectHandle handle) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (obj == null) {
            throw new NullPointerException();
        }
        if (!Thread.holdsLock(obj)) {
            throw new IllegalMonitorStateException();
        }
        MonitorSupport.monitorExit(obj);
        JNIThreadOwnedMonitors.exited(obj);
        return JNIErrors.JNI_OK();
    }

    // Checkstyle: resume

    /**
     * Helper code for JNI functions. This is an inner class because the outer methods must match
     * JNI functions.
     */
    static class Support {
        static class JNIEnvironmentEnterPrologue {
            public static void enter(JNIEnvironment env) {
                CEntryPointActions.enter((IsolateThread) env);
            }
        }

        static class JNIJavaVMEnterAttachThreadPrologue {
            static void enter(JNIJavaVM vm) {
                CEntryPointActions.enterAttachThread(vm.getFunctions().getIsolate());
            }
        }

        static class JNIExceptionHandlerVoid {
            static void handle(Throwable t) {
                Support.handleException(t);
            }
        }

        static class JNIExceptionHandlerReturnNullHandle {
            static JNIObjectHandle handle(Throwable t) {
                Support.handleException(t);
                return JNIObjectHandles.nullHandle();
            }
        }

        static class JNIExceptionHandlerReturnNullWord {
            static WordBase handle(Throwable t) {
                Support.handleException(t);
                return WordFactory.nullPointer();
            }
        }

        static class JNIExceptionHandlerReturnFalse {
            static boolean handle(Throwable t) {
                Support.handleException(t);
                return false;
            }
        }

        static class JNIExceptionHandlerReturnMinusOne {
            static int handle(Throwable t) {
                Support.handleException(t);
                return -1;
            }
        }

        static class JNIExceptionHandlerReturnJniErr {
            static int handle(Throwable t) {
                Support.handleException(t);
                return JNIErrors.JNI_ERR();
            }
        }

        static JNIMethodId getMethodID(JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig, boolean isStatic) {
            Class<?> clazz = JNIObjectHandles.getObject(hclazz);
            String name = CTypeConversion.toJavaString(cname);
            String signature = CTypeConversion.toJavaString(csig);
            return JNIReflectionDictionary.singleton().getMethodID(clazz, name, signature, isStatic);
        }

        static JNIFieldId getFieldID(JNIObjectHandle hclazz, CCharPointer cname, CCharPointer csig) {
            // TODO: check signature
            Class<?> clazz = JNIObjectHandles.getObject(hclazz);
            String name = CTypeConversion.toJavaString(cname);
            return JNIReflectionDictionary.singleton().getFieldID(clazz, name);
        }

        static CShortPointer pinStringAndGetChars(JNIObjectHandle hstr, CCharPointer isCopy) {
            String str = JNIObjectHandles.getObject(hstr);
            if (str == null) {
                return WordFactory.nullPointer();
            }
            if (isCopy.isNonNull()) {
                isCopy.write((byte) 1);
            }
            /*
             * With compressed strings (introduced in JDK 9), a Java String can have different
             * encodings. So we always request a copy as a char[] array. For a JDK 8 String, or for
             * a JDK 9 UTF16 encoded String, we could avoid the copying. But it would require us to
             * know internals of the String implementation, so we do not do it for now.
             */
            char[] chars = str.toCharArray();
            return JNIThreadLocalPinnedObjects.pinArrayAndGetAddress(chars);
        }

        static void unpinString(CShortPointer cstr) {
            JNIThreadLocalPinnedObjects.unpinArrayByAddress(cstr);
        }

        static void handleException(Throwable t) {
            /*
             * The JNI specification requires that native code may call only certain functions while
             * an exception is pending. However, the JNI implementation in OpenJDK generally does
             * not enforce this and when another exception occurs in a JNI function, it replaces the
             * exception that was already pending. So does ours.
             */
            JNIThreadLocalPendingException.set(t);
        }
    }

    static class UnimplementedWithJNIEnvArgument {
        /**
         * Stub for unimplemented JNI functionality with a JNIEnv argument.
         */
        @CEntryPoint
        @CEntryPointOptions(prologue = JNIEnvironmentEnterPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static void unimplemented(JNIEnvironment env) {
            /*
             * We do not catch and preserve this exception like we normally would with JNI because
             * it is unlikely that native code checks for a pending exception after each JNI
             * function call. As a result, the caller would likely crash at a different location or
             * encounter the pending exception in an unrelated context, making this unnecessarily
             * difficult to debug.
             */
            VMError.shouldNotReachHere("An unimplemented JNI function was called. Please refer to the stack trace.");
        }
    }

    static class UnimplementedWithJavaVMArgument {
        /**
         * Stub for unimplemented JNI functionality with a JavaVM argument.
         */
        @CEntryPoint
        @CEntryPointOptions(prologue = JNIJavaVMEnterAttachThreadPrologue.class, publishAs = Publish.NotPublished, include = CEntryPointOptions.NotIncludedAutomatically.class)
        static void unimplemented(JNIJavaVM vm) {
            VMError.shouldNotReachHere("An unimplemented JNI function was called. Please refer to the stack trace.");
        }
    }
}
