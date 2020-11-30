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
package com.oracle.svm.core.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.svm.core.hub.DynamicHub;

/**
 * Defines that the annotated class should have a Hybrid layout. Hybrid layouts are hybrids between
 * instance layouts and array layouts. The contents of a specified member array and (optional)
 * member type id slots are directly placed within the class layout. This saves one indirection when
 * accessing the array or type id slots.
 *
 * <p>
 * The array length is located directly after the HUB pointer, like in regular array. Then (if
 * present) the type id slots follow. Then the instance fields are placed. Then, with the default
 * GC, there is an optional identity hashcode. At the end of the layout, the array elements are
 * located.
 * 
 * <pre>
 *    +--------------------------------+
 *    | pointer to DynamicHub          |
 *    +--------------------------------+
 *    | Array length                   |
 *    +--------------------------------+
 *    | type id slots (optional)|
 *    |     ...                        |
 *    +--------------------------------+
 *    | instance fields                |
 *    |     ...                        |
 *    +--------------------------------+
 *    | identity hashcode (optional)   |
 *    +--------------------------------+
 *    | array elements                 |
 *    :     ...                        :
 * </pre>
 * 
 * Currently only the {@link DynamicHub} class has a hybrid layout.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Hybrid {

    /**
     * Specifies a single member array as the hybrid array.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Array {
    }

    /**
     * Specifies a single member type slots.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface TypeIDSlots {
    }
}
