/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.classfile;

import java.util.function.Supplier;

import com.oracle.truffle.espresso.shared.JavaVersion;
import com.oracle.truffle.espresso.shared.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.shared.descriptors.ByteSequence;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.shared.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.shared.perf.TimerCollection;

public interface ParsingContext {

    JavaVersion getJavaVersion();

    boolean isStrictJavaCompliance();

    TimerCollection getTimers();

    boolean isPreviewEnabled();

    Logger getLogger();

    Symbol<Name> getOrCreateName(ByteSequence byteSequence);

    // symbolify(Types.nameToType(byteSequence))
    Symbol<Type> getOrCreateTypeFromName(ByteSequence byteSequence);

    Utf8Constant getOrCreateUtf8Constant(ByteSequence bytes);

    long getNewKlassId();

    interface Logger {
        void log(String message);

        void log(Supplier<String> messageSupplier);

        void log(String message, Throwable throwable);

        Logger NOP = new Logger() {
            @Override
            public void log(String message) {
                // nop
            }

            @Override
            public void log(Supplier<String> messageSupplier) {
                // nop
            }

            @Override
            public void log(String message, Throwable throwable) {
                // nop
            }
        };
    }
}
