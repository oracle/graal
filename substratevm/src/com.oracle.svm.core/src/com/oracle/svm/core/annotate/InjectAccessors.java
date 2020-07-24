/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inject accessors methods for the field denoted using a {@link Alias} annotation. All loads and
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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectAccessors {

    Class<?> value();
}
