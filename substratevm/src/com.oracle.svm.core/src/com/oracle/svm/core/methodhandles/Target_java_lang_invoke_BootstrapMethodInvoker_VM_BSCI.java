/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.RuntimeClassLoading.WithRuntimeClassLoading;
import com.oracle.svm.core.util.BasedOnJDKFile;

/**
 * This is used in a special case of {@code BootstrapMethodInvoker.invoke} (See
 * {@code @BasedOnJDKFile} annotation on this class) which is not currently used in SVM.
 * <p>
 * These substitutions cut paths that would lead to useless code being included and some deleted
 * methods being reached.
 */
@TargetClass(className = "java.lang.invoke.BootstrapMethodInvoker", innerClass = "VM_BSCI", onlyWith = WithRuntimeClassLoading.class)
@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+36/src/java.base/share/classes/java/lang/invoke/BootstrapMethodInvoker.java#L113-L126")
final class Target_java_lang_invoke_BootstrapMethodInvoker_VM_BSCI {
    @Substitute
    @SuppressWarnings("unused")
    Target_java_lang_invoke_BootstrapMethodInvoker_VM_BSCI(MethodHandle bsm, String name, Object type, MethodHandles.Lookup lookup, int[] indexInfo) {
        throw unsupportedFeature("BootstrapMethodInvoker$VM_BSCI");
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    Object fillCache(int i) {
        throw unsupportedFeature("BootstrapMethodInvoker$VM_BSCI.fillCache");
    }

    @Substitute
    @SuppressWarnings({"unused", "static-method"})
    public int copyConstants(int start, int end, Object[] buf, int pos) {
        throw unsupportedFeature("BootstrapMethodInvoker$VM_BSCI.copyConstants");
    }
}
