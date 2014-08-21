/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import com.oracle.truffle.api.nodes.*;

/**
 * An assumption is a global boolean flag that starts with the value true (i.e., the assumption is
 * valid) and can subsequently be invalidated (using {@link Assumption#invalidate()}). Once
 * invalidated, an assumption can never get valid again. Assumptions can be created using the
 * {@link TruffleRuntime#createAssumption()} or the {@link TruffleRuntime#createAssumption(String)}
 * method. The Truffle compiler has special knowledge of this class in order to produce efficient
 * machine code for checking an assumption in case the assumption object is a compile time constant.
 * Therefore, assumptions should be stored in final fields in Truffle nodes.
 *
 * All instances of classes implementing {@code Assumption} must be held in {@code final} fields for
 * compiler optimizations to take effect.
 */
public interface Assumption {

    /**
     * Checks that this assumption is still valid. The method throws an exception, if this is no
     * longer the case. This method is preferred over the {@link #isValid()} method when writing
     * guest language interpreter code. The catch block should perform a node rewrite (see
     * {@link Node#replace(Node)}) with a node that no longer relies on the assumption.
     *
     * @throws InvalidAssumptionException If the assumption is no longer valid.
     */
    void check() throws InvalidAssumptionException;

    /**
     * Checks whether the assumption is still valid.
     *
     * @return a boolean value indicating the validity of the assumption
     */
    boolean isValid();

    /**
     * Invalidates this assumption. Performs no operation, if the assumption is already invalid.
     */
    void invalidate();

    /**
     * A name for the assumption that is used for debug output.
     *
     * @return the name of the assumption
     */
    String getName();
}
