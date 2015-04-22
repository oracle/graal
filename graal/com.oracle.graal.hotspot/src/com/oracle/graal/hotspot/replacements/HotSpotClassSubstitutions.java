/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.*;

import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@link java.lang.Class} methods.
 */
public class HotSpotClassSubstitutions {

    public static int getModifiers(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            // Class for primitive type
            return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
        } else {
            return klass.readInt(klassModifierFlagsOffset(), KLASS_MODIFIER_FLAGS_LOCATION);
        }
    }

    public static boolean isInterface(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            return false;
        } else {
            int accessFlags = klass.readInt(klassAccessFlagsOffset(), KLASS_ACCESS_FLAGS_LOCATION);
            return (accessFlags & Modifier.INTERFACE) != 0;
        }
    }

    public static boolean isArray(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (klass.isNull()) {
            return false;
        } else {
            return klassIsArray(klass);
        }
    }

    public static boolean isPrimitive(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        return klass.isNull();
    }

    public static Class<?> getSuperclass(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (!klass.isNull()) {
            int accessFlags = klass.readInt(klassAccessFlagsOffset(), KLASS_ACCESS_FLAGS_LOCATION);
            if ((accessFlags & Modifier.INTERFACE) == 0) {
                if (klassIsArray(klass)) {
                    return Object.class;
                } else {
                    Word superKlass = klass.readWord(klassSuperKlassOffset(), KLASS_SUPER_KLASS_LOCATION);
                    if (superKlass.equal(0)) {
                        return null;
                    } else {
                        return readJavaMirror(superKlass);
                    }
                }
            }
        }
        return null;
    }

    public static Class<?> readJavaMirror(Word klass) {
        return PiNode.asNonNullClass(klass.readObject(classMirrorOffset(), CLASS_MIRROR_LOCATION));
    }

    public static Class<?> getComponentType(final Class<?> thisObj) {
        KlassPointer klass = ClassGetHubNode.readClass(thisObj);
        if (!klass.isNull()) {
            if (klassIsArray(klass)) {
                return PiNode.asNonNullClass(klass.readObject(arrayKlassComponentMirrorOffset(), ARRAY_KLASS_COMPONENT_MIRROR));
            }
        }
        return null;
    }
}
