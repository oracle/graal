/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Inject accessor methods for the field denoted using an {@link Alias} annotation. All loads and
 * stores to the original field are redirected to accessor methods located in the class provided in
 * the {@link #value} property. The class must implement the marker interface
 * {@link InjectAccessors}. The accessor methods are static methods in that class, named either
 * {@code get} / {@code set} or {@code getFoo} / {@code setFoo} for a field name {@code foo}.
 * Depending on the kind of accessor (get / set for a static / non-static field), the accessor must
 * have 0, 1, or 2 parameters.
 *
 * If the field is non-static, the first method parameter is the accessed object. The type of the
 * parameter must be the class that declared the field. The null check on the object is performed
 * before the accessor is called, in the same way as the null check for a regular field access.
 *
 * For get-accessors, the return type of the method must be the type of the field. For
 * set-accessors, the last method parameter must be the type of the field and denotes the value
 * stored to the field.
 *
 * If no set-accessor is provided, stores to the field lead to a fatal error during image
 * generation. If no get-accessor is provided, loads of the field lead to a fatal error during image
 * generation.
 *
 * The injected accessors must not access the original field. Since all field accesses use the
 * accessors, that would lead to a recursive call of the accessors. Instead, data must be stored in
 * either a new static field, or an {@link Inject injected} instance field.
 *
 * @since 22.3
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface InjectAccessors {

    /**
     * @since 22.3
     */
    Class<?> value();
}
