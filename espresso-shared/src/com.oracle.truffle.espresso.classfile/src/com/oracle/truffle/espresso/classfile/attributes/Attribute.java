/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public abstract class Attribute {

    public static final Attribute[] EMPTY_ARRAY = new Attribute[0];

    // Singleton instance for the Synthetic attribute.
    public static final Attribute SYNTHETIC = createRaw(ParserNames.Synthetic, null);

    public abstract Symbol<Name> getName();

    /**
     * Attribute raw data. Known attributes that parse the raw data, can drop the raw data (return
     * null).
     */
    public byte[] getData() {
        return null;
    }

    /**
     * Used as a dedicated and specialized replacement for equals().
     *
     * @param other the object to compare 'this' to
     * @return true if objects are equal
     */
    @SuppressWarnings("unused")
    public boolean isSame(Attribute other, ConstantPool thisPool, ConstantPool otherPool) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return Objects.equals(getName(), other.getName()) && Arrays.equals(getData(), other.getData());
    }

    public static Attribute createRaw(Symbol<Name> name, byte[] data) {
        return new Attribute() {
            @Override
            public Symbol<Name> getName() {
                return name;
            }

            @Override
            public byte[] getData() {
                return data;
            }
        };
    }
}
