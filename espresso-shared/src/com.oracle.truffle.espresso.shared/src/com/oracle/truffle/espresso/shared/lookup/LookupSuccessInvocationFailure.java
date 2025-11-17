/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.lookup;

import java.io.Serial;

import com.oracle.truffle.espresso.shared.meta.MethodAccess;

/**
 * Checked exception thrown by the {@link MethodLookup} helper class when method lookup succeeds,
 * but direct invocation of the resulting method should trigger
 * {@link IncompatibleClassChangeError}.
 * <p>
 * Use of a checked exceptions should ensure that the result is not accidentally passed along or
 * invoked without consideration.
 */
public final class LookupSuccessInvocationFailure extends Exception {
    @Serial private static final long serialVersionUID = 1794882806666028197L;

    LookupSuccessInvocationFailure(MethodAccess<?, ?, ?> m) {
        super(null, null);
        this.m = m;
    }

    private final transient MethodAccess<?, ?, ?> m;

    /**
     * Obtain the result of method lookup.
     */
    @SuppressWarnings("unchecked")
    public <M extends MethodAccess<?, ?, ?>> M getResult() {
        return (M) m;
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
