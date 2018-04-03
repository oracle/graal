/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.CEntryPointLiteralCodePointer;

/**
 * A function pointer to an {@link CEntryPoint entry point method} that can be, for example, handed
 * out to C code so that C code can call back into Java code. The method that is referred to must be
 * annotated with {@link CEntryPoint}, which imposes certain restrictions.
 * <p>
 * The actual value of the function pointer is only available at run time. To prevent accidental
 * access to it during native image generation, the actual {@link #getFunctionPointer() function
 * pointer} is encapsulated in this class. The call to {@link #getFunctionPointer()} fails with an
 * exception during native image generation.
 * <p>
 * Instances of this class can only be created during native image generation. It is not possible to
 * look up a function by name at run time. The intended use case is therefore as follows:
 *
 * <pre>
 * // Function that is externally accessible
 * &#064;CEntryPoint
 * static int myFunction(IsolateThread thread, int x, int y) {
 *     ...
 * }
 *
 * // Invocation interface (for calls from Java, otherwise CFunctionPointer suffices)
 * interface MyFunctionPointer extends FunctionPointer {
 *     &#064;InvokeCFunctionPointer
 *     int invoke(IsolateThread thread, int x, int y);
 * }
 *
 * // Function pointer literal
 * public static final CEntryPointLiteral&lt;MyFunctionPointer&gt; myFunctionLiteral = CEntryPointLiteral.create(MyClass.class, &quot;myFunction&quot;, new Class<?>[]{IsolateThread.class, int.class, int.class});
 *
 * // Call from Java
 * void caller() {
 *     MyFunctionPointer fp = myFunctionLiteral.getFunctionPointer(); // entry point, could be returned to C code
 *     int fiftyeight = fp.invoke(CEntryPointContext.getCurrentIsolateThread(), 47, 11);
 * }
 * </pre>
 *
 * @since 1.0
 */
public final class CEntryPointLiteral<T extends CFunctionPointer> {

    /* Field is accessed using alias. */
    @SuppressWarnings("unused") //
    private CFunctionPointer functionPointer;

    private CEntryPointLiteral(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        this.functionPointer = new CEntryPointLiteralCodePointer(definingClass, methodName, parameterTypes);
    }

    /**
     * Creates a new function pointer to an entry point.
     *
     * @since 1.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends CFunctionPointer> CEntryPointLiteral<T> create(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        return new CEntryPointLiteral<>(definingClass, methodName, parameterTypes);
    }

    /**
     * Returns the function pointer to the entry point.
     * 
     * @since 1.0
     */
    public T getFunctionPointer() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
