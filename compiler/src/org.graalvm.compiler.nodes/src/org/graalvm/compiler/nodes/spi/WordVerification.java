/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.spi;

import jdk.vm.ci.meta.JavaType;

/**
 * Provides a capability to verify that {@linkplain JavaType types} represent or do not represent
 * raw words (as opposed to Objects).
 * <p>
 * All methods will either return {@code true} in case the assertion holds or throw an exception if
 * it does not. Users are not supposed to catch the exception and make decisions based on it. This
 * is a debugging tool. Only use it for assertions and verification.
 * </p>
 *
 * <h1>Motivation</h1>
 *
 * This interface exists to avoid exposing {@code WordTypes} in {@link CoreProviders}. Word values
 * must be transformed to other types at the very beginning of the pipeline (i.e., during graph
 * building). {@link WordVerification} is used to detect when this invariant is violated and to
 * issue proper error messages.
 */
public interface WordVerification {

    /**
     * Verifies that a given type is a word type.
     *
     * @return {@code true}
     * @throws Error if the assertion does not hold
     */
    boolean guaranteeWord(JavaType type);

    /**
     * Verifies that a given type is not a word type.
     *
     * @return {@code true}
     * @throws Error if the assertion does not hold
     */
    boolean guaranteeNotWord(JavaType type);

}
