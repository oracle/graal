/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;

/**
 * Defines that the annotated class should have a Hybrid layout. Hybrid layouts are hybrids between
 * instance layouts and array layouts. The contents of a specified member array and (optional)
 * member type id slots are directly placed within the class layout. This saves one indirection when
 * accessing the array or type id slots.
 *
 * <p>
 * The location of the identity hashcode is configuration-dependent and will follow the same
 * placement convention as an array. The See {@link ObjectLayout} for more information on where the
 * identity hash can be placed. @Hybrid objects are treated the same way as instance classes for
 * determining whether (and where) they have a monitor slot; See {@link MultiThreadedMonitorSupport}
 * for more information on monitor slot placement.
 *
 * <pre>
 *    +--------------------------------------------------+
 *    | object header (same header as for arrays)        |
 *    +--------------------------------------------------+
 *    | array length                                     |
 *    +--------------------------------------------------+
 *    | instance fields (i.e., primitive or object data) |
 *    |     ...                                          |
 *    +--------------------------------------------------+
 *    | array elements (i.e., primitive data)            |
 *    |     ...                                          |
 *    +--------------------------------------------------+
 * </pre>
 *
 * <p>
 * Hybrid objects have one of the instance {@link HubType}s but a {@link LayoutEncoding} like an
 * array. This is important to keep in mind because methods such as {@link Class#isInstance} will
 * return {@code true} and {@link Class#isArray()} will return {@code false}, while
 * {@link LayoutEncoding#isPureInstance} will return {@code false} and
 * {@link LayoutEncoding#isArrayLike} will return {@code true} for hybrid objects.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Hybrid {

    /**
     * The component type of the array part of the hybrid class.
     */
    Class<?> componentType();
}
