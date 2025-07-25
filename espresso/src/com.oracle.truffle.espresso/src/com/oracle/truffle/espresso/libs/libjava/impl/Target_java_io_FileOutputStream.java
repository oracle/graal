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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.io.Checks;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(value = FileOutputStream.class, group = LibJava.class)
public final class Target_java_io_FileOutputStream {

    @Substitution
    public static void initIDs() {
        // Do nothing.
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static void open0(@JavaType(FileOutputStream.class) StaticObject self, @JavaType(String.class) StaticObject path, boolean append, @Inject TruffleIO io, @Inject Meta meta) {
        EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        if (append) {
            options.add(StandardOpenOption.APPEND);
        }
        io.open(self, FDAccess.forFileOutputStream(), meta.toHostString(path), options);
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    @TruffleBoundary
    static void writeBytes(@JavaType(FileOutputStream.class) StaticObject self, @JavaType(byte[].class) StaticObject b, int off, int len, @SuppressWarnings("unused") boolean append,
                    @Inject EspressoLanguage lang, @Inject TruffleIO io, @Inject EspressoContext ctx) {
        Checks.nullCheck(b, ctx);
        Checks.requireNonForeign(b, ctx);
        io.writeBytes(self, FDAccess.forFileOutputStream(), b.unwrap(lang), off, len);
    }
}
