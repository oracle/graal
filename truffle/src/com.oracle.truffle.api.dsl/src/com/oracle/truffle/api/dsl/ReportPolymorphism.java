/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import com.oracle.truffle.api.nodes.Node;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Target;

/**
 * Nodes annotated with this annotation (and their subclasses) will, if processed by the DSL,
 * automatically {@link Node#reportPolymorphicSpecialize() report polymorphic specializations}.
 *
 * Polymorphic specializations include, but are not limited to, activating another specialization,
 * increasing the number of instances of an active specialization, excluding a specialization, etc.
 * Individual specializations can be excluded from this consideration buy using the
 * {@link ReportPolymorphism.Exclude} Polymorphic specializations are never reported on the first
 * specialization.
 *
 * @since 0.33
 */
@Target(ElementType.TYPE)
@Inherited
public @interface ReportPolymorphism {

    /**
     * Nodes (and their subclasses) or specializations annotated with this annotation will be
     * excluded from consideration when {@link Node#reportPolymorphicSpecialize() reporting
     * polymorphic specializations}.
     *
     * @since 0.33
     */
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Inherited
    @interface Exclude {

    }
}
