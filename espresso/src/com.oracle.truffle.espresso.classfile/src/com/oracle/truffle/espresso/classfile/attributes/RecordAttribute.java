/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;

public class RecordAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.Record;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final RecordComponentInfo[] components;

    public RecordAttribute(Symbol<Name> name, RecordComponentInfo[] components) {
        super(name, null);
        this.components = components;
    }

    public static class RecordComponentInfo {
        final char name;
        final char descriptor;
        @CompilerDirectives.CompilationFinal(dimensions = 1)//
        final Attribute[] attributes;

        public char getNameIndex() {
            return name;
        }

        public char getDescriptorIndex() {
            return descriptor;
        }

        public RecordComponentInfo(int name, int descriptor, Attribute[] attributes) {
            this.name = (char) name;
            this.descriptor = (char) descriptor;
            this.attributes = attributes;
        }

        public Attribute getAttribute(Symbol<Name> attributeName) {
            for (Attribute attr : attributes) {
                if (attr.getName().equals(attributeName)) {
                    return attr;
                }
            }
            return null;
        }
    }

    public RecordComponentInfo[] getComponents() {
        return components;
    }
}
