/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.descriptors;

/**
 * Manages access to symbols encoded using the {@link ModifiedUTF8 modified UTF8} encoding, used in
 * class files.
 * 
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se24/html/jvms-4.html#jvms-4.4.7">JVM
 *      Spec - The CONSTANT_Utf8_info Structure</a>
 */
public final class Utf8Symbols {
    private final Symbols symbols;

    public Utf8Symbols(Symbols symbols) {
        this.symbols = symbols;
    }

    public Symbol<? extends ModifiedUTF8> lookupValidUtf8(ByteSequence byteSequence) {
        if (Validation.validModifiedUTF8(byteSequence)) {
            return symbols.lookup(byteSequence);
        } else {
            return null;
        }
    }

    public Symbol<? extends ModifiedUTF8> lookupValidUtf8(String string) {
        return lookupValidUtf8(ByteSequence.create(string));
    }

    public Symbol<? extends ModifiedUTF8> getOrCreateValidUtf8(String string) {
        return getOrCreateValidUtf8(ByteSequence.create(string));
    }

    public Symbol<? extends ModifiedUTF8> getOrCreateValidUtf8(ByteSequence byteSequence) {
        return getOrCreateValidUtf8(byteSequence, false);
    }

    public Symbol<? extends ModifiedUTF8> getOrCreateValidUtf8(ByteSequence byteSequence, boolean ensureStrongReference) {
        if (Validation.validModifiedUTF8(byteSequence)) {
            // Only attempt to create after validation.
            return symbols.getOrCreate(byteSequence, ensureStrongReference);
        }
        return null;
    }
}
