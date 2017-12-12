/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Specifies additional properties for an element also annotated with {@link Alias}, {@link Delete},
 * {@link Substitute}, {@link AnnotateOriginal}, or {@link KeepOriginal}.
 * <p>
 * See {@link TargetClass} for an overview of the annotation system.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface TargetElement {

    String CONSTRUCTOR_NAME = "<init>";

    /**
     * The name of the field or method in the original class. If the default value is specified for
     * this element, then the name of the annotated field or method is used.
     * <p>
     * To make a reference to a constructor, use the name {@link #CONSTRUCTOR_NAME}.
     */
    String name() default "";

    /**
     * Specifies if the aliased target must exist. If the property is false (the default value), a
     * non-existing target element raises an error. If the property is true, a non-existing target
     * element is silently ignored.
     *
     * This property is useful, e.g., to handle differences in JDK versions.
     */
    boolean optional() default false;
}
