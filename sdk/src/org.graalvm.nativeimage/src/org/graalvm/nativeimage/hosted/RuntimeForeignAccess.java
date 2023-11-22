/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.hosted;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

@Platforms(Platform.HOSTED_ONLY.class)
public final class RuntimeForeignAccess {

    /**
     * Registers the provided descriptor and options pair for foreign downcalls at runtime. Needed
     * in order to get a method handle using <a href=
     * "https://download.java.net/java/early_access/jdk21/docs/api/java.base/java/lang/foreign/Linker.html#downcallHandle(java.lang.foreign.FunctionDescriptor,java.lang.foreign.Linker.Option...)">downcallHandle</a>
     * <p>
     * Since the foreign functions feature is currently a preview of the Java platform (as part of
     * <a href="https://openjdk.org/projects/panama/">Project Panama</a>), this API is itself a
     * preview subject to changes following changes in the feature itself. This also means that this
     * feature must be manually enabled, using '--enable-preview'.
     *
     * Even though this method is weakly typed for compatibility reasons, runtime checks will be
     * performed to ensure that the arguments have the expected type. It will be deprecated in favor
     * of strongly typed variant as soon as possible.
     *
     * @param desc A {@link java.lang.foreign.FunctionDescriptor} to register for downcalls.
     * @param options An array of {@link java.lang.foreign.Linker.Option} used for the downcalls.
     *
     * @since 23.1
     */
    public static void registerForDowncall(Object desc, Object... options) {
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForDowncall(ConfigurationCondition.alwaysTrue(), desc, options);
    }

    private RuntimeForeignAccess() {
    }
}
