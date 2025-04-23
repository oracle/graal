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

package com.oracle.svm.hosted.webimage.codegen;

import java.lang.reflect.Constructor;

import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.util.ReflectionUtil;

/**
 * Interface to run source code through the closure compiler without having to directly reference
 * classes that, when fully linked, would cause a {@link NoClassDefFoundError}.
 * <p>
 * The closure compiler is an optional dependency and any references to any of its classes would
 * result in a {@link NoClassDefFoundError} when it is not available.
 * <p>
 * To avoid that, all references to closure compiler classes are in implementers of this interface.
 * Implementer classes of this interface must not be referenced from anywhere as a JVM may fully
 * link all referenced classes beforehand (as opposed only when the instruction containing the
 * reference is executed), which would cause an exception.
 * <p>
 * Instances of this interface can be constructed using
 * {@link #getClosureSupport(String, DeadlockWatchdog, JSCodeGenTool, String)}, given their fully
 * qualified name. Implementers must have a constructor with a signature as used in that method.
 */
public interface ClosureCompilerSupport {
    /**
     * Checks if the closure compiler library is available for use.
     * <p>
     * The closure compiler cannot be used if this method returns {@code false}
     */
    static boolean isAvailable() {
        return ModuleLayer.boot().findModule("org.graalvm.wrapped.google.closure").isPresent();
    }

    /**
     * Constructs an instance of this interface given a fully qualified name.
     * <p>
     * This method must only be called if {@link #isAvailable()} returns {@code true}.
     *
     * @param className Fully qualified name of a known implementer of this interface.
     */
    static ClosureCompilerSupport getClosureSupport(String className, DeadlockWatchdog watchdog, JSCodeGenTool codeGenTool, String imageName) {
        assert isAvailable() : "Tried to get closure compiler even though it is not available";
        Class<?> clazz = ReflectionUtil.lookupClass(className);
        Constructor<?> constructor = ReflectionUtil.lookupConstructor(clazz, DeadlockWatchdog.class, JSCodeGenTool.class, String.class);
        return (ClosureCompilerSupport) ReflectionUtil.newInstance(constructor, watchdog, codeGenTool, imageName);
    }

    /**
     * Compiles the entire source code with the Google Closure compiler.
     */
    String applyClosureCompiler(String inputSourceCode);
}
