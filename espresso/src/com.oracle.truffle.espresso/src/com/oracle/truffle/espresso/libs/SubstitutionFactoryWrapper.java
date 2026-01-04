/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;

/**
 * A {@link TruffleObject} wrapping around a
 * {@link com.oracle.truffle.espresso.substitutions.JavaSubstitution.Factory}. This allows
 * substitutions to be used as the result of a call to
 * {@link com.oracle.truffle.espresso.ffi.NativeAccess#lookupSymbol(TruffleObject, String)}.
 */
@ExportLibrary(InteropLibrary.class)
public final class SubstitutionFactoryWrapper implements TruffleObject {
    private final JavaSubstitution.Factory subst;
    @CompilationFinal private JavaSubstitution instance;

    SubstitutionFactoryWrapper(JavaSubstitution.Factory subst) {
        this.subst = subst;
    }

    public JavaSubstitution.Factory getSubstitution() {
        return subst;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    public Object execute(Object[] args) {
        if (instance == null) {
            if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            instance = getSubstitution().create();
        }
        return instance.invoke(args);
    }
}
