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
package com.oracle.svm.hosted;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
public class ClassLoaderFeature implements InternalFeature {

    private static final NativeImageSystemClassLoader nativeImageSystemClassLoader = NativeImageSystemClassLoader.singleton();

    public static ClassLoader getRuntimeClassLoader(ClassLoader original) {
        if (needsReplacement(original)) {
            return nativeImageSystemClassLoader.defaultSystemClassLoader;
        }
        return original;
    }

    private static boolean needsReplacement(ClassLoader loader) {
        if (loader == nativeImageSystemClassLoader) {
            return true;
        }
        if (nativeImageSystemClassLoader.isNativeImageClassLoader(loader)) {
            return true;
        }
        return false;
    }

    private Object runtimeClassLoaderObjectReplacer(Object replaceCandidate) {
        if (replaceCandidate instanceof ClassLoader) {
            return getRuntimeClassLoader((ClassLoader) replaceCandidate);
        }
        return replaceCandidate;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::runtimeClassLoaderObjectReplacer);
    }
}
