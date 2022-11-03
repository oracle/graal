/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.ImageHeapMap;

@AutomaticallyRegisteredImageSingleton
public class ClassLoadingExceptionSupport {

    /** The exit code used when terminating abruptly due to missing metadata. */
    private static final int EXIT_CODE = 172;

    public static class Options {
        @Option(help = "Enable termination caused by missing metadata.")//
        public static final HostedOptionKey<Boolean> ExitOnUnknownClassLoadingFailure = new HostedOptionKey<>(false);
    }

    static ClassLoadingExceptionSupport singleton() {
        return ImageSingletons.lookup(ClassLoadingExceptionSupport.class);
    }

    /** The map used to collect registered problematic classes. */
    private final EconomicMap<String, Throwable> inaccessibleClasses = ImageHeapMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(String className, Throwable t) {
        singleton().inaccessibleClasses.put(className, t);
    }

    public static Throwable getExceptionForClass(String className, Throwable original) {
        Throwable t = singleton().inaccessibleClasses.get(className);
        if (t == null && Options.ExitOnUnknownClassLoadingFailure.getValue()) {
            terminateUnconfigured(className);
        }
        if (t == null || t.getClass() == original.getClass()) {
            return original;
        }
        return t;
    }

    private static void terminateUnconfigured(String className) {
        System.err.println("Missing metadata error: Unable to process Class.forName invocation for class name " + className);
        System.exit(EXIT_CODE);
    }
}
