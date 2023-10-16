/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import java.util.Iterator;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_collections_EspressoForeignIterator {

    @Substitution
    abstract static class Create extends SubstitutionNode {
        static final int LIMIT = 4;

        abstract @JavaType(Iterator.class) StaticObject execute(@JavaType(Object.class) StaticObject receiver, @Inject Meta meta);

        @Specialization
        @JavaType(Iterator.class)
        StaticObject doCached(
                        @JavaType(Object.class) StaticObject foreignIterator,
                        @Inject Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            assert foreignIterator != null;
            assert foreignIterator.isForeignObject();
            EspressoLanguage language = meta.getLanguage();
            Object rawForeign = foreignIterator.rawForeignObject(language);
            return StaticObject.createForeign(language, meta.polyglot.EspressoForeignIterator, rawForeign, interop);
        }
    }
}
