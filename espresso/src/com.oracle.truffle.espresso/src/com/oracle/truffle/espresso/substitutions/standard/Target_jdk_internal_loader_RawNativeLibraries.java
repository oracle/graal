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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
public final class Target_jdk_internal_loader_RawNativeLibraries {
    @Substitution
    abstract static class Load0 extends SubstitutionNode {
        abstract boolean execute(@JavaType(internalName = "Ljdk/internal/loader/RawNativeLibraries$RawNativeLibraryImpl;") StaticObject impl, @JavaType(String.class) StaticObject name);

        @Specialization
        boolean doLoad(@JavaType(internalName = "Ljdk/internal/loader/RawNativeLibraries$RawNativeLibraryImpl;") StaticObject impl, @JavaType(String.class) StaticObject name,
                        @CachedLibrary(limit = "2") InteropLibrary interop) {
            EspressoContext context = getContext();
            Meta meta = context.getMeta();
            TruffleObject library = context.getVM().JVM_LoadLibrary(meta.toHostString(name), false);
            long value;
            try {
                value = interop.asPointer(library);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
            meta.jdk_internal_loader_RawNativeLibraries$RawNativeLibraryImpl_handle.setLong(impl, value);
            return value != 0;
        }
    }
}
