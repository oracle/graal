/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Fallback {

}
