/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.verifier;

import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Public API for calling the Bytecode Verifier.
 */
public final class Verifier {
    /**
     * Given a {@link RuntimeAccess runtime} and a {@link MethodAccess method}, performs bytecode
     * verification for the given method.
     * <p>
     * If this method returns without throwing, verification was successful.
     *
     * @throws VerificationException If the method was rejected by verification. The
     *             {@link VerificationException#kind()} method provides additional information so
     *             the caller may translate the {@link VerificationException} into a corresponding
     *             error in its runtime.
     *
     * @see RuntimeAccess
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> void verify(R runtime, M method)
                    throws VerificationException {
        MethodVerifier.verify(runtime, method);
    }

    private Verifier() {
    }
}
