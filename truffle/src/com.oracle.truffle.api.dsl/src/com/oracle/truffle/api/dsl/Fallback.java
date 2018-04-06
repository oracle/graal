/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>
 * A method annotated with {@link Fallback} is treated as a {@link Specialization} that implicitly
 * links all the guards of all other declared {@link Specialization} annotated methods of the
 * operation in a negated form. As a consequence it cannot declare any other guards. The expected
 * signature of the method must match to the signature of a {@link Specialization} with the
 * additional limitation that only generically executable argument types are allowed. A generically
 * executable argument is an argument that can be executed from the child {@link Node} using an
 * execute method without {@link UnsupportedOperationException}. In many cases the generically
 * executable type is {@link Object}. An operation is limited to just one {@link Fallback}
 * specialization which is always ordered at the end of the specialization chain.
 * </p>
 *
 * <p>
 * A simple example showing the use of the {@link Fallback} annotation in a DSL operation:
 * </p>
 *
 * <pre>
 * &#064;Specialization int doInt(int a) {..}
 * &#064;Specialization int doDouble(double a) {..}
 * &#064;Fallback int orElse(Object a) {..}
 * </pre>
 *
 * <p>
 * The previous example could be redeclared just using {@link Specialization} annotated methods as
 * follows:
 * </p>
 *
 * <pre>
 * &#064;Specialization int doInt(int a) {..}
 * &#064;Specialization int doDouble(double a) {..}
 * &#064;Specialization(guard={"!isInt(a)", "!isDouble(a)"})
 * int orElse(Object a) {..}
 * </pre>
 *
 * <p>
 * <b>Performance note:</b> For operations with a lot of {@link Specialization} annotated methods
 * the use of {@link Fallback} might generate a guard that is very big. Try to avoid the use of
 * {@link Fallback} for specializations that are significantly important for peak performance.
 * </p>
 *
 * @see Specialization
 * @see NodeChild
 * @since 0.8 or earlier
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Fallback {

}
