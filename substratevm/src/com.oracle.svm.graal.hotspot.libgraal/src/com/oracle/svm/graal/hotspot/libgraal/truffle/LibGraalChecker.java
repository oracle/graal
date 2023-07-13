/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.Set;

import org.graalvm.jniutils.JNI;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;

final class LibGraalChecker {

    private LibGraalChecker() {
    }

    /*----------------- CHECKING ------------------*/

    /**
     * Checks that all {@code ToLibGraal}s are implemented and their HotSpot/libgraal ends points
     * match.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
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
                    check(libGraalParameters[0] == JNI.JNIEnv.class, "Parameter 0 must be of type %s: %s", JNI.JNIEnv.class, libGraalMethod);
                    check(libGraalParameters[1] == JNI.JClass.class, "Parameter 1 must be of type %s: %s", JNI.JClass.class, libGraalMethod);
                    check(libGraalParameters[2] == long.class, "Parameter 2 must be of type long: %s", libGraalMethod);

                    check(hsParameters[0] == long.class, "Parameter 0 must be of type long: %s", hsMethod);

                    for (int i = 3, j = 1; i < libGraalParameters.length; i++, j++) {
                        Class<?> libgraal = libGraalParameters[i];
                        Class<?> hs = hsParameters[j];
                        Class<?> hsExpect;
                        if (hs.isPrimitive()) {
                            hsExpect = libgraal;
                        } else {
                            if (libgraal == JNI.JString.class) {
                                hsExpect = String.class;
                            } else if (libgraal == JNI.JByteArray.class) {
                                hsExpect = byte[].class;
                            } else if (libgraal == JNI.JLongArray.class) {
                                hsExpect = long[].class;
                            } else if (libgraal == JNI.JObjectArray.class) {
                                hsExpect = Object[].class;
                            } else if (libgraal == JNI.JObject.class) {
                                hsExpect = Object.class;
                            } else if (libgraal == JNI.JClass.class) {
                                hsExpect = Class.class;
                            } else {
                                throw fail("Method %s must only use supported parameters but uses unsupported class %s", libGraalMethod, libgraal.getName());
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

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void check(boolean condition, String format, Object... args) {
        if (!condition) {
            throw fail(format, args);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static InternalError fail(String format, Object... args) {
        return new InternalError(String.format(format, args));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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
