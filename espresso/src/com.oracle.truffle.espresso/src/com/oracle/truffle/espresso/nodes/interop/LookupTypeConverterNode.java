/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateUncached
public abstract class LookupTypeConverterNode extends EspressoNode {
    static final int LIMIT = 3;

    public abstract PolyglotTypeMappings.TypeConverter execute(String metaName) throws ClassCastException;

    @SuppressWarnings("unused")
    @Specialization(guards = {"cachedMetaName.equals(metaName)"}, limit = "LIMIT")
    PolyglotTypeMappings.TypeConverter doCached(String metaName,
                    @Cached("metaName") String cachedMetaName,
                    @Cached("doUncached(metaName)") PolyglotTypeMappings.TypeConverter converter) throws ClassCastException {
        assert converter == doUncached(metaName);
        return converter;
    }

    @TruffleBoundary
    @Specialization(replaces = "doCached")
    PolyglotTypeMappings.TypeConverter doUncached(String metaName) throws ClassCastException {
        return getContext().getPolyglotTypeMappings().mapTypeConversion(metaName);
    }
}
