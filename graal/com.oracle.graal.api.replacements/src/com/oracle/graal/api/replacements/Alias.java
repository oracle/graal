/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.replacements;

import java.lang.annotation.*;

/**
 * Mechanism for accessing fields and methods otherwise inaccessible due to Java language access
 * control rules.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Alias {

    /**
     * The name of the aliased field or method. If the default value is specified for this element,
     * then the name of the annotated field or method is implied.
     */
    String name() default "";

    /**
     * Gets the <a
     * href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">field
     * descriptor</a> of the aliased field or the <a
     * href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">method
     * descriptor</a> of the aliased method.
     * <p>
     * If the default value is specified for this element, then the descriptor of the annotated
     * field or method is implied.
     */
    String descriptor() default "";

    /**
     * Specifies the class in which the aliased field or method is declared. If the default value is
     * specified for this element, then a non-default value must be given for the
     * {@link #declaringClassName()} element.
     */
    Class declaringClass() default Alias.class;

    /**
     * Specifies the class in which the aliased field or method is declared. This method is provided
     * for cases where the declaring class is not accessible (according to Java language access
     * control rules) in the scope of the alias method.
     * 
     * If the default value is specified for this element, then a non-default value must be given
     * for the {@link #declaringClassName()} element.
     */
    String declaringClassName() default "";

    /**
     * Specifies the suffix of the declaring class name if it is an inner class.
     */
    String innerClass() default "";

    /**
     * Specifies if the aliased target must exist. This property is useful, for example, to handle
     * differences in JDK versions for private methods.
     */
    boolean optional() default false;
}
