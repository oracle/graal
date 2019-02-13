/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Mechanism for referring to fields and methods otherwise inaccessible due to Java language access
 * control rules. This enables VM code to directly access a private field or invoke a private method
 * in a JDK class without using reflection. Aliases avoid the boxing/unboxing required by reflection
 * and they type check an aliased field access or method invocation statically.
 * <p>
 * The idiom for using {@link Alias} is somewhat related to the {@link Substitute} annotation, but
 * reversed; both are often used in combination. In both cases a separate class is used to declare
 * the aliased or substituted methods. In the substitution case occurrences of {@code this} actually
 * refer to the instance of the class being substituted. In the aliased case we pretend that the
 * class declaring the aliased method is an instance of the aliasee in order to access its fields or
 * invoke its methods.
 * <p>
 * The element can also be annotated with {@link TargetElement} to specify additional properties.
 * See {@link TargetClass} for an overview of the annotation system.
 * <p>
 * When aliasing *non-static* inner classes the constructors are passed a hidden argument which is
 * the outer class. When writing an @Alias for a constructor of a *non-static* inner classes, you
 * have to (a) explicitly declare that parameter, and (b) supply it in the calls.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Alias {
}
