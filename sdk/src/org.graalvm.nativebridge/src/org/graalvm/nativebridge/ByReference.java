/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.jniutils.HSObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Instruments the native bridge processor to marshall annotated method return type or method
 * parameter as a reference to a foreign object.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.PARAMETER})
public @interface ByReference {

    /**
     * The class to instantiate for a foreign handle.
     * <p>
     * For HotSpot to native calls.
     * <ul>
     * <li>If the bridged type is an interface, the class must be assignable to the
     * {@link NativeObject}.</li>
     * <li>If the bridged type is a class, the class must have a field of the {@link NativeObject}
     * type annotated with the {@link EndPointHandle}.</li>
     * <li>If the bridged has a custom dispatch, the class must be the dispatch class with a
     * {@link CustomDispatchFactory factory}.</li>
     * </ul>
     * </p>
     * <p>
     * For native to HotSpot calls.
     * <li>If the bridged type is an interface, the class must be assignable to the
     * {@link HSObject}.</li>
     * <li>If the bridged type is a class, the class must have a field of the {@link HSObject} type
     * annotated with the {@link EndPointHandle}.</li>
     * <li>If the bridged has a custom dispatch, the class must be a dispatch class with a
     * {@link CustomDispatchFactory factory}.</li>
     * </p>
     */
    Class<?> value();

    /**
     * For classes with a custom dispatch, when set to {@code true} the foreign object is translated
     * by a custom receiver accessor before it's passed to the target method.
     *
     * @see CustomReceiverAccessor
     * @see CustomDispatchAccessor
     */
    boolean useCustomReceiverAccessor() default false;
}
