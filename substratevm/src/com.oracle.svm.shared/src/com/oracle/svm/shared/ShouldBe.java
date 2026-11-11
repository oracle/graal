/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents the Java type that an annotated declaration should use after a temporary migration
 * constraint is removed. The intended type is identified by name so that recording it does not
 * introduce a dependency on a type that is not currently accessible.
 * <p>
 * The type name follows {@link Class#getTypeName()}: package names use dots, nested classes use
 * {@code $}, and array dimensions use {@code []}. The associated removal condition and tracking
 * issue must be documented near the annotated declaration.
 * <p>
 * This annotation is informational, retained only in source, and has no runtime, conversion,
 * invocation, or validation semantics. Unlike {@code GuestType}, it describes a future source
 * declaration type rather than the guest type represented by a builder-side value.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE_USE)
public @interface ShouldBe {
    /**
     * Intended Java type name, in the format returned by {@link Class#getTypeName()}.
     */
    String typeName();
}
