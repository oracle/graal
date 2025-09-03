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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.io.Checks;
import com.oracle.truffle.espresso.io.FDAccess;
import com.oracle.truffle.espresso.io.Throw;
import com.oracle.truffle.espresso.io.TruffleIO;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.OS;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNamesProvider;
import com.oracle.truffle.espresso.substitutions.Throws;

@EspressoSubstitutions(value = RandomAccessFile.class, group = LibJava.class)
public final class Target_java_io_RandomAccessFile {

    @Substitution
    public static void initIDs() {
        // Do nothing.
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    static long length0(@JavaType(RandomAccessFile.class) StaticObject self,
                    @Inject TruffleIO io) {
        return io.length(self, FDAccess.forRandomAccessFile());
    }

    @Substitution(hasReceiver = true)
    @Throws({IOException.class, FileNotFoundException.class})
    public static void open0(@JavaType(RandomAccessFile.class) StaticObject self, @JavaType(String.class) StaticObject name, int mode,
                    @Inject EspressoContext context, @Inject TruffleIO io) {
        Checks.nullCheck(name, context);
        String hostName = context.getMeta().toHostString(name);
        Set<OpenOption> openOptions = getOpenOptions(mode, context, io);
        io.open(self, FDAccess.forRandomAccessFile(), hostName, openOptions);
    }

    @Substitution(hasReceiver = true, nameProvider = Append0.class)
    @Throws({IOException.class, IndexOutOfBoundsException.class})
    static int readBytes(@JavaType(RandomAccessFile.class) StaticObject self, @JavaType(byte[].class) StaticObject b, int off, int len,
                    @Inject EspressoContext context, @Inject TruffleIO io) {
        Checks.nullCheck(b, context);
        Checks.requireNonForeign(b, context);
        return io.readBytes(self, FDAccess.forRandomAccessFile(), b.unwrap(context.getLanguage()), off, len);
    }

    @Substitution(hasReceiver = true)
    @Throws(IOException.class)
    static void seek0(@JavaType(RandomAccessFile.class) StaticObject self, long pos,
                    @Inject EspressoContext context, @Inject TruffleIO io) {
        assert pos >= 0;
        Checks.nullCheck(self, context);
        io.seek(self, FDAccess.forRandomAccessFile(), pos);
    }

    @TruffleBoundary
    private static Set<OpenOption> getOpenOptions(int mode, EspressoContext context, TruffleIO io) {
        TruffleIO.RAF_Sync rafSync = io.rafSync;

        Set<OpenOption> openOptions = new HashSet<>();
        if ((mode & rafSync.O_RDONLY) != 0) {
            // flags = O_RDONLY;
            openOptions.add(StandardOpenOption.READ);
        } else if ((mode & rafSync.O_RDWR) != 0) {
            // flags = O_RDWR | O_CREAT;
            openOptions.add(StandardOpenOption.READ);
            openOptions.add(StandardOpenOption.WRITE);
            openOptions.add(StandardOpenOption.CREATE);
            if ((mode & rafSync.O_SYNC) != 0) {
                // flags |= O_SYNC;
                throw Throw.throwUnsupported("O_SYNC", context);
            } else if ((mode & rafSync.O_DSYNC) != 0) {
                // flags |= O_DSYNC;
                throw Throw.throwUnsupported("O_DSYNC", context);
            }
        }

        // On Java >= 9, RandomAccessFile supports O_TEMPORARY only Windows, use DELETE_ON_CLOSE as
        // a best-effort alternative.
        if (context.getJavaVersion().java9OrLater() && OS.getCurrent() == OS.Windows) {
            if ((mode & rafSync.O_TEMPORARY) != 0) {
                // flags |= O_TEMPORARY;
                openOptions.add(StandardOpenOption.DELETE_ON_CLOSE);
            }
        }
        return openOptions;
    }

    static final class Append0 extends SubstitutionNamesProvider {
        private static String RAF = "Ljava/io/RandomAccessFile;";
        private static String[] NAMES = {
                        RAF,
                        RAF,
        };

        public static SubstitutionNamesProvider INSTANCE = new Append0();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }

        @Override
        public String[] getMethodNames(String name) {
            return append0(this, name);
        }
    }

}
