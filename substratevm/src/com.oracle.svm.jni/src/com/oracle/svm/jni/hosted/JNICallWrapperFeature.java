/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.hosted;

import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.jni.JNIThreadLocalEnvironmentFeature;
import com.oracle.svm.jni.access.JNIAccessFeature;
import com.oracle.svm.jni.access.JNIAccessibleMethod;

/**
 * Responsible for generating JNI call wrappers for Java-to-native and native-to-Java invocations.
 *
 * <p>
 * Java-to-native call wrappers are created by {@link JNINativeCallWrapperSubstitutionProcessor}. It
 * creates a {@link JNINativeCallWrapperMethod} for each Java method that is declared with the
 * {@code native} keyword and that was registered via {@link JNIRuntimeAccess} to be accessible via
 * JNI at runtime. The method provides a graph that performs the native code invocation. This graph
 * is visible to the analysis.
 * </p>
 *
 * <p>
 * Native-to-Java call wrappers are generated as follows:
 * <ol>
 * <li>{@link JNIAccessFeature} creates a {@link JNIJavaCallWrapperMethod} for each method that is
 * callable from JNI, associates it with its corresponding {@link JNIAccessibleMethod}, and
 * registers it as an entry point. The method provides a graph that performs the Java method
 * invocation from native code and that is visible to the analysis.</li>
 * <li>Because all {@link JNIJavaCallWrapperMethod call wrappers} are entry points, the call
 * wrappers and any code that is reachable through them is compiled.</li>
 * <li>Before compilation, a {@link CFunctionPointer} is created for each call wrapper that is
 * eventually filled with the call wrapper's final entry point address.</li>
 * <li>Looking up a Java method via JNI finds its {@link JNIAccessibleMethod}, reads its call
 * wrapper's entry address from the {@link CFunctionPointer}, and returns it as the jmethodID. The
 * JNI functions for calling methods, named
 * {@code Call[Static?][ReturnType]Method(env, obj, jmethodID)} only perform a jump to the provided
 * jmethodID argument, and the call wrapper code takes over execution.</li>
 * </ol>
 * </p>
 */
class JNICallWrapperFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(JNIAccessFeature.class, JNIThreadLocalEnvironmentFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        DuringSetupAccessImpl config = (DuringSetupAccessImpl) access;
        config.registerNativeSubstitutionProcessor(new JNINativeCallWrapperSubstitutionProcessor());
    }
}
