/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature.AfterAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.svm.core.feature.InternalFeature.InternalFeatureAccess;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.JVMCIRuntimeClassInitializationSupport;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeJNIAccess;
import com.oracle.svm.util.dynamicaccess.JVMCIRuntimeReflection;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility methods used by features that perform JNI registration.
 */
public class JNIRegistrationUtil {

    protected static boolean isPosix() {
        return Platform.includedIn(Platform.LINUX.class) || Platform.includedIn(Platform.DARWIN.class);
    }

    protected static boolean isLinux() {
        return Platform.includedIn(Platform.LINUX.class);
    }

    protected static boolean isDarwin() {
        return Platform.includedIn(Platform.DARWIN.class);
    }

    protected static boolean isWindows() {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return true;
        }
        assert !Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) : "Should never be called when targeting a non-JNI platform";
        return false;
    }

    protected static void initializeAtRunTime(FeatureAccess access, String... classNames) {
        InternalFeatureAccess internalAccess = (InternalFeatureAccess) access;
        JVMCIRuntimeClassInitializationSupport classInitSupport = JVMCIRuntimeClassInitializationSupport.singleton();
        for (String className : classNames) {
            classInitSupport.initializeAtRunTime(type(internalAccess, className), "for JDK native code support via JNI");
        }
    }

    protected static ResolvedJavaType type(FeatureAccess access, String className) {
        ResolvedJavaType typeByName = ((InternalFeatureAccess) access).findTypeByName(className);
        VMError.guarantee(typeByName != null, "class %s not found", className);
        return typeByName;
    }

    protected static Optional<? extends ResolvedJavaType> optionalType(FeatureAccess access, String className) {
        ResolvedJavaType typeByName = ((InternalFeatureAccess) access).findTypeByName(className);
        return Optional.ofNullable(typeByName);
    }

    protected static Optional<ResolvedJavaMethod> optionalMethod(FeatureAccess access, String className, String methodName, Class<?>... parameterTypes) {
        InternalFeatureAccess internalAccess = (InternalFeatureAccess) access;
        return optionalType(access, className)
                        .flatMap(clazz -> Optional.ofNullable(JVMCIReflectionUtil.getUniqueDeclaredMethod(true, internalAccess.getMetaAccess(), clazz, methodName, parameterTypes)));
    }

    protected static ResolvedJavaMethod method(FeatureAccess access, String className, String methodName, Class<?>... parameterTypes) {
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(((InternalFeatureAccess) access).getMetaAccess(), type(access, className), methodName, parameterTypes);
    }

    protected static ResolvedJavaMethod constructor(FeatureAccess access, String className, Class<?>... parameterTypes) {
        return JVMCIReflectionUtil.getDeclaredConstructor(((InternalFeatureAccess) access).getMetaAccess(), type(access, className), parameterTypes);
    }

    protected static ResolvedJavaField[] fields(FeatureAccess access, String className, String... fieldNames) {
        ResolvedJavaType type = type(access, className);
        ResolvedJavaField[] result = new ResolvedJavaField[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            result[i] = JVMCIReflectionUtil.getUniqueDeclaredField(type, fieldNames[i]);
        }
        return result;
    }

    protected static void registerForThrowNew(FeatureAccess access, String... exceptionClassNames) {
        InternalFeatureAccess internalAccess = (InternalFeatureAccess) access;
        for (String exceptionClassName : exceptionClassNames) {
            JVMCIRuntimeJNIAccess.register(type(internalAccess, exceptionClassName));
            JVMCIRuntimeJNIAccess.register(constructor(internalAccess, exceptionClassName, String.class));

            JVMCIRuntimeReflection.register(type(internalAccess, exceptionClassName));
            JVMCIRuntimeReflection.register(constructor(internalAccess, exceptionClassName, String.class));
        }
    }

    private static final Set<Consumer<DuringAnalysisAccess>> runOnceCallbacks = Collections.newSetFromMap(new ConcurrentIdentityHashMap<>());

    /** Intended to be used from within a callback to ensure that it is run only once. */
    protected static boolean isRunOnce(Consumer<DuringAnalysisAccess> callback) {
        return !runOnceCallbacks.add(callback);
    }

    public void afterAnalysis(@SuppressWarnings("unused") AfterAnalysisAccess access) {
        runOnceCallbacks.clear();
    }
}
