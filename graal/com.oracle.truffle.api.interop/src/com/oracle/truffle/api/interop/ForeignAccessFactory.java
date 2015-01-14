/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.interop.messages.*;

/**
 * Interface of a factory that produces AST snippets that can access a foreign {@code TruffleObject}
 * . A Truffle language implementation accesses a {@code TruffleObject} via a {@code Message}. The
 * {@code TruffleObject} instance provides a {@code ForeignAccessFactory} instance that provides an
 * AST snippet for a given {@code Message}.
 */
public interface ForeignAccessFactory {

    /**
     * Provides a {@code InteropPredicate} that tests whether a {@code TruffleObject} can be
     * accessed using AST snippets, produced by this {@code ForeignAccessFactory}.
     *
     * @return the {@code InteropPredicate} that tests if a {@code TruffleObject} can be accessed by
     *         AST snipptes, produces by this {@code ForeignAccessFactory}.
     */
    InteropPredicate getLanguageCheck();

    /**
     * Provides an AST snippet to access a {@code TruffleObject}.
     *
     * @param tree the {@code Message} that represents the access to a {@code TruffleObject}.
     * @return the AST snippet for accessing the {@code TruffleObject}, wrapped as a
     *         {@code CallTarget}.
     */
    CallTarget getAccess(Message tree);

}
