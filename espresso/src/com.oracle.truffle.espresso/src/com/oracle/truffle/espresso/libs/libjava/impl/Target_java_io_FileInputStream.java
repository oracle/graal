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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import com.oracle.truffle.espresso.io.Checks;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(value = FileInputStream.class, group = LibJava.class)
public final class Target_java_io_FileInputStream {

    private static final EnumSet<StandardOpenOption> READ_ONLY_OPTION_SET = EnumSet.of(StandardOpenOption.READ);

    @Substitution
    public static void initIDs() {
        // Do nothing.
    }

    @Substitution(hasReceiver = true)
    @Throws(FileNotFoundException.class)
    public static void open0(@JavaType(FileInputStream.class) StaticObject self,
                    @JavaType(String.class) StaticObject name,
                    @Inject Meta meta, @Inject TruffleIO io) {
        Checks.nullCheck(name, meta);
        io.open(self, FDAccess.forFileInputStream(), meta.toHostString(name), READ_ONLY_OPTION_SET);
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static int read0(@JavaType(FileInputStream.class) StaticObject self,
                    @Inject TruffleIO io) {
        return io.readSingle(self, FDAccess.forFileInputStream());
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static int readBytes(@JavaType(FileInputStream.class) StaticObject self, @JavaType(byte[].class) StaticObject b, int off, int len,
                    @Inject TruffleIO io) {
        Checks.nullCheck(b, io);
        return io.readBytes(self, FDAccess.forFileInputStream(), b.unwrap(io.getLanguage()), off, len);
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static long length0(@JavaType(FileInputStream.class) StaticObject self, @Inject TruffleIO io) {
        return io.length(self, FDAccess.forFileInputStream());
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static long position0(@JavaType(FileInputStream.class) StaticObject self, @Inject TruffleIO io) {
        return io.position(self, FDAccess.forFileInputStream());
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    @SuppressWarnings("unused")
    public static long skip0(@JavaType(FileInputStream.class) StaticObject self, long n) {
        throw JavaSubstitution.unimplemented();
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    public static int available0(@JavaType(FileInputStream.class) StaticObject self,
                    @Inject TruffleIO io) {
        long size = io.length(self, FDAccess.forFileInputStream());
        long pos = io.position(self, FDAccess.forFileInputStream());
        if (size <= pos) {
            return 0;
        }
        return Math.toIntExact(size - pos);
    }
}
