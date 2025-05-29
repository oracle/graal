/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.jdwp.bridge.jniutils.JNIEntryPoint;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;

final class JDWPServerJNIEntryPointsFeature implements InternalFeature {

    @Override
    public String getDescription() {
        return "Registers JNI entry points for the JDWP server";
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.JNI.getValue();
    }

    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        registerNativeMethods(access);
    }

    private static void registerNativeMethods(@SuppressWarnings("unused") Feature.BeforeAnalysisAccess access) {
        try {
            List<Method> methodsToRegister = new ArrayList<>();

            /*
             * JNI entry-points exposed by the Native Bridge.
             */
            methodsToRegister.addAll(findAnnotatedMethodsInClass("com.oracle.svm.jdwp.bridge.jniutils.JNIExceptionWrapperEntryPoints", JNIEntryPoint.class));
            methodsToRegister.addAll(findAnnotatedMethodsInClass("com.oracle.svm.jdwp.bridge.nativebridge.ForeignExceptionEndPoints", JNIEntryPoint.class));

            methodsToRegister.add(ClassLoader.class.getDeclaredMethod("getPlatformClassLoader"));
            methodsToRegister.add(ClassLoader.class.getDeclaredMethod("getSystemClassLoader"));
            methodsToRegister.add(ClassLoader.class.getDeclaredMethod("loadClass", String.class));

            methodsToRegister.addAll(findAnnotatedMethodsInClass("com.oracle.svm.jdwp.bridge.NativeToHSJDWPEventHandlerBridgeGen$EndPoint", JNIEntryPoint.class));

            // JNI entry-points exposed by the JDWP server.
            methodsToRegister.add(JDWPServer.class.getDeclaredMethod("createInstance"));
            methodsToRegister.add(JDWPServer.class.getDeclaredMethod("spawnServer", String.class, String.class, long.class, long.class, long.class, String.class, String.class, boolean.class));

            methodsToRegister.stream().map(Method::getDeclaringClass).distinct().forEach(RuntimeJNIAccess::register);
            methodsToRegister.forEach(RuntimeJNIAccess::register);

        } catch (ReflectiveOperationException re) {
            throw UserError.abort(re, "Failed to register JNI entry points.");
        }
    }

    /**
     * Finds methods annotated by {@code annotation} in class {@code className}.
     * ImageClassLoader#findAnnotatedMethods does not work for classes loaded by the system
     * classloader, we need to process them ourselves.
     */
    private static Collection<Method> findAnnotatedMethodsInClass(String className, Class<? extends Annotation> annotation) throws ReflectiveOperationException {
        List<Method> res = new ArrayList<>();
        Class<?> clazz = Class.forName(className);
        for (Method method : clazz.getDeclaredMethods()) {
            if (AnnotationAccess.getAnnotation(method, annotation) != null) {
                res.add(method);
            }
        }
        return res;
    }
}
