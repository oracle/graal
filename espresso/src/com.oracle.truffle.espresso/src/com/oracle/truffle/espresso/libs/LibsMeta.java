/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.JavaVersion.VersionRange.ALL;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.DiffVersionLoadHelper;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class LibsMeta implements ContextAccess {
    private final EspressoContext context;
    private final Meta meta;
    // Checkstyle: stop field name check
    // libzip
    public final ObjectKlass java_util_zip_CRC32;
    public final Field HIDDEN_CRC32;
    public final ObjectKlass java_util_zip_Inflater;
    public final Field java_util_zip_Inflater_inputConsumed;
    public final Field java_util_zip_Inflater_outputConsumed;
    public final ObjectKlass java_util_zip_DataFormatException;
    // Checkstyle: resume field name check

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public Meta getMeta() {
        return meta;
    }

    public LibsMeta(EspressoContext ctx) {
        this.context = ctx;
        this.meta = context.getMeta();

        // libzip
        java_util_zip_CRC32 = knownKlass(EspressoSymbols.Types.java_util_zip_CRC32);
        HIDDEN_CRC32 = diff().field(ALL, EspressoSymbols.Names.HIDDEN_CRC32, EspressoSymbols.Types._int).maybeHiddenfield(java_util_zip_CRC32);
        java_util_zip_Inflater = knownKlass(EspressoSymbols.Types.java_util_zip_Inflater);
        java_util_zip_DataFormatException = knownKlass(EspressoSymbols.Types.java_util_zip_DataFormatException);
        java_util_zip_Inflater_inputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.inputConsumed, EspressoSymbols.Types._int);
        java_util_zip_Inflater_outputConsumed = java_util_zip_Inflater.requireDeclaredField(EspressoSymbols.Names.outputConsumed, EspressoSymbols.Types._int);
    }

    public ObjectKlass knownKlass(Symbol<Type> type) {
        return meta.knownKlass(type);
    }

    private DiffVersionLoadHelper diff() {
        return new DiffVersionLoadHelper(meta);
    }
}
