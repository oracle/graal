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
package com.oracle.svm.guest.staging.core.heap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * Marks an object field whose hosted value is not available until a later image-build phase. The
 * annotation prevents static analysis from reading and wrongly constant-folding the hosted value
 * before {@link #availability()} returns {@code true}. Instead, analysis registers the concrete
 * classes specified by {@link #types()} and {@link #fullyQualifiedTypes()} as instantiated and
 * injects their type flows, together with the null state specified by {@link #canBeNull()}, into the
 * field. If no class is specified, the declared field type must be concrete and is used as the
 * field's analysis type. The specified classes must conservatively cover all values that the field
 * can contain after it becomes available.
 * <p>
 * Making the value available does not automatically rescan the field. Late heap verification
 * performed by {@code HeapSnapshotVerifier} can materialize, scan, and include an object value
 * without guaranteeing that reachability callbacks registered through
 * {@link Feature.DuringSetupAccess#registerObjectReachabilityHandler} are executed. Internal users
 * that require these callbacks must arrange an explicit
 * {@code ImageHeapScanner.rescanField(...)} at a supported phase after the value becomes available.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface UnknownObjectField {

    /**
     * Specifies concrete types that this field can take. Each type must be assignable to the
     * declared field type.
     */
    Class<?>[] types() default {};

    /**
     * Specifies fully qualified names of concrete types that this field can take. Each type must be
     * assignable to the declared field type.
     */
    String[] fullyQualifiedTypes() default {};

    /**
     * Specify if this field can be null. By default unknown value object fields cannot be null.
     */
    boolean canBeNull() default false;

    /**
     * Specifies the {@link BooleanSupplier}; its {@link BooleanSupplier#getAsBoolean()} result
     * determines when the hosted field value can be read.
     */
    Class<? extends BooleanSupplier> availability();
}
