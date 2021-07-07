/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.classfile.attributes;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class InnerClassesAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.InnerClasses;

    public static class Entry {
        public final int innerClassIndex;
        public final int outerClassIndex;
        public final int innerNameIndex;
        public final int innerClassAccessFlags;

        public Entry(int innerClassIndex, int outerClassIndex, int innerNameIndex, int innerClassAccessFlags) {
            this.innerClassIndex = innerClassIndex;
            this.outerClassIndex = outerClassIndex;
            this.innerNameIndex = innerNameIndex;
            this.innerClassAccessFlags = innerClassAccessFlags;
        }
    }

    @CompilationFinal(dimensions = 1) private final Entry[] entries;

    /**
     * Trust the caller to not modify it.
     */
    public Entry[] entries() {
        return entries;
    }

    public InnerClassesAttribute(Symbol<Name> name, Entry[] entries) {
        super(name, null);
        assert NAME.equals(name);
        this.entries = entries;
    }
}
