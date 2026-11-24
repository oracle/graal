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
 * classes specified by {@link #types()} and {@link #fullyQualifiedTypes()} as instantiated and uses
 * them as bounds for a conservative field summary, with nullability specified by
 * {@link #canBeNull()}. If no class is specified, the declared field type must be concrete and is
 * used as the bound. The specified classes and their permitted subtypes must cover all summarized
 * non-null values.
 * <p>
 * Closed bounds use instantiated assignable types; open bounds also account for unknown extension
 * types. Saturation may lose bound and nullability precision. Hosted availability and bound openness
 * are independent: processing a later hosted value adds heap and reachability information without
 * narrowing the existing summary. Write permissions are determined separately.
 * <p>
 * Availability is polled rather than notified. Becoming available does not enqueue field
 * processing; a later scan, rescan, or heap verification must revisit the field. Object values then
 * follow normal reachability processing, including callbacks registered through
 * {@link Feature.DuringSetupAccess#registerObjectReachabilityHandler}, even if an equal speculative
 * snapshot exists. Clients that require earlier processing must explicitly rescan the static root
 * or instance field.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Platforms(Platform.HOSTED_ONLY.class)
public @interface UnknownObjectField {

    /**
     * Specifies concrete classes used as value bounds, including their permitted subtypes. Each
     * class must be assignable to the declared field type.
     */
    Class<?>[] types() default {};

    /**
     * Fully qualified names of additional value bounds, with the same requirements as
     * {@link #types()}.
     */
    String[] fullyQualifiedTypes() default {};

    /**
     * Whether the unavailable field value may be null (false by default). Array elements may still
     * be null.
     */
    boolean canBeNull() default false;

    /**
     * Specifies the {@link BooleanSupplier}; its {@link BooleanSupplier#getAsBoolean()} result
     * determines when the hosted field value can be read.
     */
    Class<? extends BooleanSupplier> availability();
}
