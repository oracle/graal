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
package com.oracle.svm.interpreter;

import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.nativeimage.ImageSingletons;

public final class InterpreterDirectives {
    /**
     * Build-time marker to force inclusion of methods prefixed with "<code>test</code>" in its name
     * for a given class.
     *
     * Only meant for testing purposes.
     */
    public static void markKlass(Class<?> targetKlass) {
        ImageSingletons.lookup(InterpreterDirectivesSupport.class).markKlass(targetKlass);
    }

    /**
     * Tries to divert execution for the specified method Swaps the GOT entry for the specified
     * method with an interpreter entry point. This requires for the given method to exist as a
     * compiled method.
     *
     * Only meant for testing purposes.
     *
     * @param method This is an interpreter method obtained via
     *            {@link DebuggerSupport#lookupMethod(ResolvedJavaType, String, Class, Class[])}.
     *
     * @return True if it was possible to divert the execution for a given interpreter method.
     */
    public static boolean forceInterpreterExecution(Object method) {
        return ImageSingletons.lookup(InterpreterDirectivesSupport.class).forceInterpreterExecution(method);
    }

    /**
     * Undo operation to above. {@link #forceInterpreterExecution(Object)} <it>must</it> have
     * happened before calling {@link #resetInterpreterExecution(Object)}.
     *
     * Only meant for testing purposes.
     *
     * @param method This is an interpreter method obtained via
     *            {@link DebuggerSupport#lookupMethod(ResolvedJavaType, String, Class, Class[])}.
     */
    public static void resetInterpreterExecution(Object method) {
        ImageSingletons.lookup(InterpreterDirectivesSupport.class).resetInterpreterExecution(method);
    }

    /**
     * Makes sure that the given method is executed in the interpreter, even if it doesn't have a
     * dedicated compiled companion. For example, if the given method is practically inlined by all
     * its callers, said callers will also be executed in the interpreter.
     *
     * @param method This is an interpreter method obtained via
     *            {@link DebuggerSupport#lookupMethod(ResolvedJavaType, String, Class, Class[])}.
     *
     * @return A token that can be used to undo the actions done by this operation.
     */
    public static Object ensureInterpreterExecution(Object method) {
        return ImageSingletons.lookup(InterpreterDirectivesSupport.class).ensureInterpreterExecution(method);
    }

    /**
     * Undo operation for a given token.
     *
     * <it>Note:</it> Undo operations must be applied in first-in last-out order.
     *
     * @param token Obtained by a call to {@link #ensureInterpreterExecution(Object)}, and defines
     *            which actions should be reverted.
     *
     * @throws IllegalStateException If an expired token is used.
     */
    public static void undoExecutionOperation(Object token) {
        ImageSingletons.lookup(InterpreterDirectivesSupport.class).undoExecutionOperation(token);
    }

    /**
     * Executes the given method in the interpreter with the provided arguments.
     *
     * Only meant for testing purposes.
     */
    public static Object callIntoInterpreter(Object method, Object... args) {
        return ImageSingletons.lookup(InterpreterDirectivesSupport.class).callIntoInterpreter(method, args);
    }

    /**
     * Similar to {@link #callIntoInterpreter(Object, Object...)}, but depending on the run-time
     * state it either calls into the AOT compiled version or into the interpreter.
     *
     * Only meant for testing purposes.
     */
    public static Object callIntoUnknown(Object method, Object... args) {
        return ImageSingletons.lookup(InterpreterDirectivesSupport.class).callIntoUnknown(method, args);
    }

    /**
     * Returns true when executed in the interpreter, and false when used in compiled code.
     *
     * Only meant for testing purposes.
     *
     * <it>Note:</it> Be careful around its usage and folding done by the compiler.
     */
    public static boolean inInterpreter() {
        return true;
    }
}
