/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.word.*;

import sun.reflect.*;

/**
 * Substitutions for {@link sun.reflect.ConstantPool} methods.
 */
@ClassSubstitution(sun.reflect.ConstantPool.class)
public class ConstantPoolSubstitutions {

    /**
     * Get the metaspace {@code ConstantPool} pointer for the given holder class.
     *
     * @param constantPoolOop the holder class as {@link Object}
     * @return a metaspace {@code ConstantPool} pointer
     */
    private static Word metaspaceConstantPool(Object constantPoolOop) {
        // ConstantPool.constantPoolOop is in fact the holder class.
        Class<?> constantPoolHolder = (Class<?>) constantPoolOop;
        KlassPointer klass = ClassGetHubNode.readClass(constantPoolHolder);
        return klass.readWord(instanceKlassConstantsOffset(), INSTANCE_KLASS_CONSTANTS);
    }

    @MethodSubstitution(isStatic = false)
    private static int getSize0(@SuppressWarnings("unused") final ConstantPool thisObj, Object constantPoolOop) {
        return metaspaceConstantPool(constantPoolOop).readInt(constantPoolLengthOffset());
    }

    @MethodSubstitution(isStatic = false)
    private static int getIntAt0(@SuppressWarnings("unused") final ConstantPool thisObj, Object constantPoolOop, int index) {
        return metaspaceConstantPool(constantPoolOop).readInt(constantPoolSize() + index * wordSize());
    }

    @MethodSubstitution(isStatic = false)
    private static long getLongAt0(@SuppressWarnings("unused") final ConstantPool thisObj, Object constantPoolOop, int index) {
        return metaspaceConstantPool(constantPoolOop).readLong(constantPoolSize() + index * wordSize());
    }

    @MethodSubstitution(isStatic = false)
    private static float getFloatAt0(@SuppressWarnings("unused") final ConstantPool thisObj, Object constantPoolOop, int index) {
        return metaspaceConstantPool(constantPoolOop).readFloat(constantPoolSize() + index * wordSize());
    }

    @MethodSubstitution(isStatic = false)
    private static double getDoubleAt0(@SuppressWarnings("unused") final ConstantPool thisObj, Object constantPoolOop, int index) {
        return metaspaceConstantPool(constantPoolOop).readDouble(constantPoolSize() + index * wordSize());
    }

}
