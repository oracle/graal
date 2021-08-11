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
 * Provides a capability for verify whether nodes, types or classes are or are not raw words (as
 * opposed to Objects).
 * <p>
 * All methods will either return {@code true} in case the assertion holds or throw an exception if
 * it does not. Users are not supposed to catch the exception and make decisions based on it. This
 * is a debugging tool. Only use it for assertions and verification.
 * </p>
 *
 * <h1>Motivation</h1>
 *
 * We do not want to leak {@link org.graalvm.compiler.word.WordTypes} to arbitrary places, because
 * those should be handled at the very beginning of the pipeline (i.e., during graph building). On
 * the other hand, we would like to detect cases where this failed to issue proper error messages
 * instead of doing the wrong thing and maybe or maybe not failing arbitrarily. Thus, we only give
 * access to assertions instead of the full features.
 */
public interface WordVerification {

    /**
     * Verifies that a given type is a word type.
     *
     * @return {@code true}
     * @throws Error if the assertion doe not hold
     */
    boolean verifyIsWord(JavaType type);

    /**
     * Verifies that a given type is not a word type.
     *
     * @return {@code true}
     * @throws Error if the assertion doe not hold
     */
    boolean verifyIsNoWord(JavaType type);

}
