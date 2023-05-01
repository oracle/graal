/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to generate the {@code FromLibGraalCalls} for given id type. The annotation can be
 * applied to packages, types and methods. When applied to method the generated
 * {@code FromLibGraalCalls} subclass delegates to the annotated method. The annotated method must
 * be static and its return type must be {@code JClass}. When applied to package or type the
 * {@link #entryPointsClassName} is used to resolve the {@code JClass}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({PACKAGE, TYPE, METHOD})
public @interface FromLibGraalEntryPointsResolver {
    /**
     * The id class. It has to implement the {@link FromLibGraalId}.
     */
    Class<? extends Enum<?>> value();

    /**
     * The fully qualified name of the entry points class on the HotSpot side. The
     * {@code entryPointsClassName} is mandatory when the annotation is present on package or type
     * element.
     */
    String entryPointsClassName() default "";
}
