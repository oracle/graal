/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 *     int fiftyeight = fp.invoke(CurrentIsolate.getCurrentThread(), 47, 11);
 * }
 * </pre>
 *
 * @since 19.0
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
     * @since 19.0
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static <T extends CFunctionPointer> CEntryPointLiteral<T> create(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        return new CEntryPointLiteral<>(definingClass, methodName, parameterTypes);
    }

    /**
     * Returns the function pointer to the entry point.
     *
     * @since 19.0
     */
    public T getFunctionPointer() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
