/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ri;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.sun.cri.ci.*;

/**
 * Represents a reference to a resolved field. Fields, like methods and types, are
 * resolved through {@link RiConstantPool constant pools}, and their actual implementation is provided by the
 * {@link RiRuntime runtime} to the compiler.
 */
public interface RiResolvedField extends RiField {

    /**
     * Gets the access flags for this field. Only the flags specified in the JVM specification
     * will be included in the returned mask. The utility methods in the {@link Modifier} class
     * should be used to query the returned mask for the presence/absence of individual flags.
     * @return the mask of JVM defined field access flags defined for this field
     */
    int accessFlags();

    /**
     * Gets the constant value of this field if available.
     * @param receiver object from which this field's value is to be read. This value is ignored if this field is static.
     * @return the constant value of this field or {@code null} if the constant value is not available
     */
    CiConstant constantValue(CiConstant receiver);

    /**
     * Gets the holder of this field as a compiler-runtime interface type.
     * @return the holder of this field
     */
    RiResolvedType holder();

    /**
     * Returns this field's annotation of a specified type.
     *
     * @param annotationClass the Class object corresponding to the annotation type
     * @return the annotation of type {@code annotationClass} for this field if present, else null
     */
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
