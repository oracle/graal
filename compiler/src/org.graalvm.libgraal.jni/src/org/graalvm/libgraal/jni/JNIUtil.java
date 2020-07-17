/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.Set;
import jdk.vm.ci.services.Services;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.serviceprovider.IsolateUtil;
import org.graalvm.libgraal.jni.JNI.JArray;
import org.graalvm.libgraal.jni.JNI.JByteArray;
import org.graalvm.libgraal.jni.JNI.JClass;
import org.graalvm.libgraal.jni.JNI.JLongArray;
import org.graalvm.libgraal.jni.JNI.JMethodID;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JObjectArray;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNI.JThrowable;
import org.graalvm.libgraal.jni.JNI.JValue;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;
import static org.graalvm.word.WordFactory.nullPointer;

/**
 * Helpers for calling JNI functions.
 */

public final class JNIUtil {

    private static final String CLASS_SERVICES = "jdk/vm/ci/services/Services";

    private static final String[] METHOD_GET_JVMCI_CLASS_LOADER = {
                    "getJVMCIClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_GET_PLATFORM_CLASS_LOADER = {
                    "getPlatformClassLoader",
                    "()Ljava/lang/ClassLoader;"
    };
    private static final String[] METHOD_LOAD_CLASS = {
                    "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;"
    };

    // Checkstyle: stop
    public static boolean IsSameObject(JNIEnv env, JObject ref1, JObject ref2) {
        traceJNI("IsSameObject");
        return env.getFunctions().getIsSameObject().call(env, ref1, ref2);
    }

    public static void DeleteLocalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteLocalRef");
        env.getFunctions().getDeleteLocalRef().call(env, ref);
    }

    public static int PushLocalFrame(JNIEnv env, int capacity) {
        traceJNI("PushLocalFrame");
        return env.getFunctions().getPushLocalFrame().call(env, capacity);
    }

    public static JObject PopLocalFrame(JNIEnv env, JObject result) {
        traceJNI("PopLocalFrame");
        return env.getFunctions().getPopLocalFrame().call(env, result);
    }

    public static JClass DefineClass(JNIEnv env, CCharPointer name, JObject loader, CCharPointer buf, int bufLen) {
        return env.getFunctions().getDefineClass().call(env, name, loader, buf, bufLen);
    }

    public static JClass FindClass(JNIEnv env, CCharPointer name) {
        traceJNI("FindClass");
        return env.getFunctions().getFindClass().call(env, name);
    }

    public static JClass GetObjectClass(JNIEnv env, JObject object) {
        traceJNI("GetObjectClass");
        return env.getFunctions().getGetObjectClass().call(env, object);
    }

    public static JMethodID GetStaticMethodID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetStaticMethodID");
        return env.getFunctions().getGetStaticMethodID().call(env, clazz, name, sig);
    }

    public static JMethodID GetMethodID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetMethodID");
        return env.getFunctions().getGetMethodID().call(env, clazz, name, sig);
    }

    public static JNI.JFieldID GetStaticFieldID(JNIEnv env, JClass clazz, CCharPointer name, CCharPointer sig) {
        traceJNI("GetStaticFieldID");
        return env.getFunctions().getGetStaticFieldID().call(env, clazz, name, sig);
    }

    public static JObjectArray NewObjectArray(JNIEnv env, int len, JClass componentClass, JObject initialElement) {
        traceJNI("NewObjectArray");
        return env.getFunctions().getNewObjectArray().call(env, len, componentClass, initialElement);
    }

    public static JByteArray NewByteArray(JNIEnv env, int len) {
        traceJNI("NewByteArray");
        return env.getFunctions().getNewByteArray().call(env, len);
    }

    public static JLongArray NewLongArray(JNIEnv env, int len) {
        traceJNI("NewLongArray");
        return env.getFunctions().getNewLongArray().call(env, len);
    }

    public static int GetArrayLength(JNIEnv env, JArray array) {
        traceJNI("GetArrayLength");
        return env.getFunctions().getGetArrayLength().call(env, array);
    }

    public static void SetObjectArrayElement(JNIEnv env, JObjectArray array, int index, JObject value) {
        traceJNI("SetObjectArrayElement");
        env.getFunctions().getSetObjectArrayElement().call(env, array, index, value);
    }

    public static JObject GetObjectArrayElement(JNIEnv env, JObjectArray array, int index) {
        traceJNI("GetObjectArrayElement");
        return env.getFunctions().getGetObjectArrayElement().call(env, array, index);
    }

    public static CLongPointer GetLongArrayElements(JNIEnv env, JLongArray array, JValue isCopy) {
        traceJNI("GetLongArrayElements");
        return env.getFunctions().getGetLongArrayElements().call(env, array, isCopy);
    }

    public static void ReleaseLongArrayElements(JNIEnv env, JLongArray array, CLongPointer elems, int mode) {
        traceJNI("ReleaseLongArrayElements");
        env.getFunctions().getReleaseLongArrayElements().call(env, array, elems, mode);
    }

    public static CCharPointer GetByteArrayElements(JNIEnv env, JByteArray array, JValue isCopy) {
        traceJNI("GetByteArrayElements");
        return env.getFunctions().getGetByteArrayElements().call(env, array, isCopy);
    }

    public static void ReleaseByteArrayElements(JNIEnv env, JByteArray array, CCharPointer elems, int mode) {
        traceJNI("ReleaseByteArrayElements");
        env.getFunctions().getReleaseByteArrayElements().call(env, array, elems, mode);
    }

    public static void Throw(JNIEnv env, JThrowable throwable) {
        traceJNI("Throw");
        env.getFunctions().getThrow().call(env, throwable);
    }

    public static boolean ExceptionCheck(JNIEnv env) {
        traceJNI("ExceptionCheck");
        return env.getFunctions().getExceptionCheck().call(env);
    }

    public static void ExceptionClear(JNIEnv env) {
        traceJNI("ExceptionClear");
        env.getFunctions().getExceptionClear().call(env);
    }

    public static void ExceptionDescribe(JNIEnv env) {
        traceJNI("ExceptionDescribe");
        env.getFunctions().getExceptionDescribe().call(env);
    }

    public static JThrowable ExceptionOccurred(JNIEnv env) {
        traceJNI("ExceptionOccurred");
        return env.getFunctions().getExceptionOccurred().call(env);
    }

    /**
     * Creates a new global reference.
     *
     * @param env the JNIEnv
     * @param ref JObject to create JNI global reference for
     * @param type type of the object, used only for tracing to distinguish global references
     * @return JNI global reference for given {@link JObject}
     */
    @SuppressWarnings("unchecked")
    public static <T extends JObject> T NewGlobalRef(JNIEnv env, T ref, String type) {
        traceJNI("NewGlobalRef");
        T res = (T) env.getFunctions().getNewGlobalRef().call(env, ref);
        if (tracingAt(3)) {
            trace(3, "New global reference for 0x%x of type %s -> 0x%x", ref.rawValue(), type, res.rawValue());
        }
        return res;
    }

    public static void DeleteGlobalRef(JNIEnv env, JObject ref) {
        traceJNI("DeleteGlobalRef");
        if (tracingAt(3)) {
            trace(3, "Delete global reference 0x%x", ref.rawValue());
        }
        env.getFunctions().getDeleteGlobalRef().call(env, ref);
    }

    public static VoidPointer GetDirectBufferAddress(JNIEnv env, JObject buf) {
        traceJNI("GetDirectBufferAddress");
        return env.getFunctions().getGetDirectBufferAddress().call(env, buf);
    }

    // Checkstyle: resume

    private static void traceJNI(String function) {
        trace(2, "LIBGRAAL->JNI: %s", function);
    }

    private JNIUtil() {
    }

    /**
     * Decodes a string in the HotSpot heap to a local {@link String}.
     */
    public static String createString(JNIEnv env, JString hsString) {
        if (hsString.isNull()) {
            return null;
        }
        int len = env.getFunctions().getGetStringLength().call(env, hsString);
        CShortPointer unicode = env.getFunctions().getGetStringChars().call(env, hsString, WordFactory.nullPointer());
        try {
            char[] data = new char[len];
            for (int i = 0; i < len; i++) {
                data[i] = (char) unicode.read(i);
            }
            return new String(data);
        } finally {
            env.getFunctions().getReleaseStringChars().call(env, hsString, unicode);
        }
    }

    /**
     * Creates a String in the HotSpot heap from {@code string}.
     */
    public static JString createHSString(JNIEnv env, String string) {
        if (string == null) {
            return WordFactory.nullPointer();
        }
        int len = string.length();
        CShortPointer buffer = UnmanagedMemory.malloc(len << 1);
        try {
            for (int i = 0; i < len; i++) {
                buffer.write(i, (short) string.charAt(i));
            }
            return env.getFunctions().getNewString().call(env, buffer, len);
        } finally {
            UnmanagedMemory.free(buffer);
        }
    }

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to internal format (e.g. {@code "Ljava/lang/getString;"}.
     */
    public static String getInternalName(String fqn) {
        return "L" + getBinaryName(fqn) + ";";
    }

    /**
     * Converts a fully qualified Java class name from Java source format (e.g.
     * {@code "java.lang.getString"}) to binary format (e.g. {@code "java/lang/getString"}.
     */
    public static String getBinaryName(String fqn) {
        return fqn.replace('.', '/');
    }

    /**
     * Returns a {@link JClass} for given binary name.
     */
    public static JClass findClass(JNIEnv env, String binaryName) {
        try (CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString(binaryName)) {
            return JNIUtil.FindClass(env, name.get());
        }
    }

    /**
     * Finds a class in HotSpot heap using a given {@code ClassLoader}.
     *
     * @param env the {@code JNIEnv}
     * @param binaryName the class binary name
     */
    public static JNI.JClass findClass(JNI.JNIEnv env, JNI.JObject classLoader, String binaryName) {
        if (classLoader.isNull()) {
            throw new IllegalArgumentException("ClassLoader must be non null.");
        }
        trace(1, "LIBGRAAL->HS: findClass");
        JNI.JMethodID findClassId = findMethod(env, JNIUtil.GetObjectClass(env, classLoader), false, false, METHOD_LOAD_CLASS);
        JNI.JValue params = StackValue.get(1, JNI.JValue.class);
        params.addressOf(0).setJObject(JNIUtil.createHSString(env, binaryName.replace('/', '.')));
        return (JNI.JClass) env.getFunctions().getCallObjectMethodA().call(env, classLoader, findClassId, params);
    }

    /**
     * Returns a ClassLoader used to load the compiler classes.
     */
    public static JNI.JObject getJVMCIClassLoader(JNI.JNIEnv env) {
        JNI.JClass clazz;
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(CLASS_SERVICES)) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + CLASS_SERVICES);
        }
        JNI.JMethodID getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_JVMCI_CLASS_LOADER);
        if (getClassLoaderId.isNonNull()) {
            return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, nullPointer());
        }
        try (CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString(JNIUtil.getBinaryName(ClassLoader.class.getName()))) {
            clazz = JNIUtil.FindClass(env, className.get());
        }
        if (clazz.isNull()) {
            throw new InternalError("No such class " + ClassLoader.class.getName());
        }
        getClassLoaderId = findMethod(env, clazz, true, true, METHOD_GET_PLATFORM_CLASS_LOADER);
        if (getClassLoaderId.isNonNull()) {
            return env.getFunctions().getCallStaticObjectMethodA().call(env, clazz, getClassLoaderId, nullPointer());
        }
        return WordFactory.nullPointer();
    }

    private static JNI.JMethodID findMethod(JNI.JNIEnv env, JNI.JClass clazz, boolean staticMethod, boolean optional, String[] descriptor) {
        assert descriptor.length == 2;
        JNI.JMethodID result;
        try (CTypeConversion.CCharPointerHolder name = toCString(descriptor[0]); CTypeConversion.CCharPointerHolder sig = toCString(descriptor[1])) {
            result = staticMethod ? GetStaticMethodID(env, clazz, name.get(), sig.get()) : GetMethodID(env, clazz, name.get(), sig.get());
            if (optional) {
                JNIExceptionWrapper.wrapAndThrowPendingJNIException(env, NoSuchMethodError.class);
            } else {
                JNIExceptionWrapper.wrapAndThrowPendingJNIException(env);
            }
            return result;
        }
    }

    /*----------------- TRACING ------------------*/

    private static Integer traceLevel;

    private static final String JNI_LIBGRAAL_TRACE_LEVEL_PROPERTY_NAME = "JNI_LIBGRAAL_TRACE_LEVEL";

    /**
     * Checks if JNI calls are verbose.
     */
    private static int traceLevel() {
        if (traceLevel == null) {
            String var = Services.getSavedProperties().get(JNI_LIBGRAAL_TRACE_LEVEL_PROPERTY_NAME);
            if (var != null) {
                try {
                    traceLevel = Integer.parseInt(var);
                } catch (NumberFormatException e) {
                    TTY.printf("Invalid value for %s: %s%n", JNI_LIBGRAAL_TRACE_LEVEL_PROPERTY_NAME, e);
                    traceLevel = 0;
                }
            } else {
                traceLevel = 0;
            }
        }
        return traceLevel;
    }

    public static boolean tracingAt(int level) {
        return traceLevel() >= level;
    }

    /**
     * Emits a trace line composed of {@code format} and {@code args} if the tracing level equal to
     * or greater than {@code level}.
     */
    public static void trace(int level, String format, Object... args) {
        if (traceLevel() >= level) {
            JNILibGraalScope<?> scope = JNILibGraalScope.scopeOrNull();
            String indent = scope == null ? "" : new String(new char[2 + (scope.depth() * 2)]).replace('\0', ' ');
            String prefix = "[" + IsolateUtil.getIsolateID() + ":" + Thread.currentThread().getName() + "]";
            TTY.printf(prefix + indent + format + "%n", args);
        }
    }

    /*----------------- CHECKING ------------------*/

    /**
     * Checks that all {@code ToLibGraal}s are implemented and their HotSpot/libgraal ends points
     * match.
     */
    @Platforms(HOSTED_ONLY.class)
    public static void checkToLibGraalCalls(Class<?> toLibGraalEntryPointsClass, Class<?> toLibGraalCallsClass, Class<? extends Annotation> annotationClass) throws InternalError {
        try {
            Method valueMethod = annotationClass.getDeclaredMethod("value");
            Type t = valueMethod.getGenericReturnType();
            check(t instanceof Class<?> && ((Class<?>) t).isEnum(), "Annotation value must be enum.");
            @SuppressWarnings("unchecked")
            Set<? extends Enum<?>> unimplemented = EnumSet.allOf(((Class<?>) t).asSubclass(Enum.class));
            for (Method libGraalMethod : toLibGraalEntryPointsClass.getDeclaredMethods()) {
                Annotation call = libGraalMethod.getAnnotation(annotationClass);
                if (call != null) {
                    check(Modifier.isStatic(libGraalMethod.getModifiers()), "Method annotated by %s must be static: %s", annotationClass, libGraalMethod);
                    CEntryPoint ep = libGraalMethod.getAnnotation(CEntryPoint.class);
                    check(ep != null, "Method annotated by %s must also be annotated by %s: %s", annotationClass, CEntryPoint.class, libGraalMethod);
                    String name = ep.name();
                    String prefix = "Java_" + toLibGraalCallsClass.getName().replace('.', '_') + '_';
                    check(name.startsWith(prefix), "Method must be a JNI entry point for a method in %s: %s", toLibGraalCallsClass, libGraalMethod);
                    name = name.substring(prefix.length());
                    Method hsMethod = findHSMethod(toLibGraalCallsClass, name, annotationClass);
                    Class<?>[] libGraalParameters = libGraalMethod.getParameterTypes();
                    Class<?>[] hsParameters = hsMethod.getParameterTypes();
                    check(hsParameters.length + 2 == libGraalParameters.length, "%s should have 2 more parameters than %s", libGraalMethod, hsMethod);
                    check(libGraalParameters.length >= 3, "Expect at least 3 parameters: %s", libGraalMethod);
                    check(libGraalParameters[0] == JNIEnv.class, "Parameter 0 must be of type %s: %s", JNIEnv.class, libGraalMethod);
                    check(libGraalParameters[1] == JClass.class, "Parameter 1 must be of type %s: %s", JClass.class, libGraalMethod);
                    check(libGraalParameters[2] == long.class, "Parameter 2 must be of type long: %s", libGraalMethod);

                    check(hsParameters[0] == long.class, "Parameter 0 must be of type long: %s", hsMethod);

                    for (int i = 3, j = 1; i < libGraalParameters.length; i++, j++) {
                        Class<?> libgraal = libGraalParameters[i];
                        Class<?> hs = hsParameters[j];
                        Class<?> hsExpect;
                        if (hs.isPrimitive()) {
                            hsExpect = libgraal;
                        } else {
                            if (libgraal == JString.class) {
                                hsExpect = String.class;
                            } else if (libgraal == JByteArray.class) {
                                hsExpect = byte[].class;
                            } else if (libgraal == JLongArray.class) {
                                hsExpect = long[].class;
                            } else if (libgraal == JObjectArray.class) {
                                hsExpect = Object[].class;
                            } else {
                                check(libgraal == JObject.class, "must be");
                                hsExpect = Object.class;
                            }
                        }
                        check(hsExpect.isAssignableFrom(hs), "HotSpot parameter %d (%s) incompatible with libgraal parameter %d (%s): %s", j, hs.getName(), i, libgraal.getName(), hsMethod);
                    }
                    unimplemented.remove(valueMethod.invoke(call));
                }
            }
            check(unimplemented.isEmpty(), "Unimplemented libgraal calls: %s", unimplemented);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    @Platforms(HOSTED_ONLY.class)
    private static void check(boolean condition, String format, Object... args) {
        if (!condition) {
            throw new InternalError(String.format(format, args));
        }
    }

    @Platforms(HOSTED_ONLY.class)
    private static Method findHSMethod(Class<?> hsClass, String name, Class<? extends Annotation> annotationClass) {
        Method res = null;
        for (Method m : hsClass.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                check(res == null, "More than one method named \"%s\" in %s", name, hsClass);
                Annotation call = m.getAnnotation(annotationClass);
                check(call != null, "Method must be annotated by %s: %s", annotationClass, m);
                check(Modifier.isStatic(m.getModifiers()) && Modifier.isNative(m.getModifiers()), "Method must be static and native: %s", m);
                res = m;
            }
        }
        check(res != null, "Could not find method named \"%s\" in %s", name, hsClass);
        return res;
    }
}
