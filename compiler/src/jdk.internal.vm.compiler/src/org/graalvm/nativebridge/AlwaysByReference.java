/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation configuring a type as a by reference parameter in the whole annotated compilation
 * unit. For classes that have many methods using {@link ByReference} parameters, using this
 * annotation is more convenient. Instead of overriding these methods and specifying
 * {@link ByReference} in each method, the whole class can be annotated with
 * {@link AlwaysByReference}. It is always possible to override {@link AlwaysByReference}
 * configuration with {@link ByReference} annotation on a particular method or parameter.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Repeatable(AlwaysByReferenceRepeated.class)
public @interface AlwaysByReference {
    /**
     * Method parameter or return type that should always be handled as a {@link ByReference} in the
     * annotated compilation unit.
     *
     * @see ByReference
     */
    Class<?> type();

    /**
     * The class to instantiate for a foreign handle.
     *
     * @see ByReference#value() for the start point class requirements.
     */
    Class<?> startPointClass();
}
