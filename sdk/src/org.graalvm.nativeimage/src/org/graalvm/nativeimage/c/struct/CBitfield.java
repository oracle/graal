/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.struct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

/**
 * Denotes a method as a bitfield access of a {@link CStruct C struct}.
 * <p>
 * If the method has a non-void return type, it is a get-method of the field. Calls of the method
 * are replaced with a memory read. The possible signatures are
 * {@code FieldType getFieldName([LocationIdentity locationIdentity]);}
 * <p>
 * If the method has the return type void, it is a set-method of the field. Calls of the method are
 * replaced with memory accesses. The possible signatures are
 * {@code void setFieldName(FieldType value, [LocationIdentity locationIdentity]);} Most
 * architectures do not provide write instructions on the bit-level. Therefore, the memory write of
 * the new value is preceded by a memory read of the old value. This makes bitfield writes
 * non-atomic.
 * <p>
 * The receiver is the pointer to the struct that is accessed, i.e., the base address of the memory
 * access.
 * <p>
 * The {@code FieldType} must be must be a primitive integer type or a {@link WordBase word type}.
 * <p>
 * The optional parameter {@code locationIdentity} specifies the {@link LocationIdentity} to be used
 * for the memory access. Two memory accesses with two different location identities are guaranteed
 * to not alias. Note that {@link UniqueLocationIdentity} annotation, cannot be used on bitfields.
 * <p>
 * Multiple accessor methods, with different signatures according to the rules of allowed
 * signatures, are allowed for a single field.
 * 
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CBitfield {

    /**
     * Specifies the field name inside the {@link CStruct C struct}. If no name is provided, the
     * method name is used as the field name. A possible "get" or "set" prefix of the method name is
     * removed.
     *
     * @since 1.0
     */
    String value() default "";
}
