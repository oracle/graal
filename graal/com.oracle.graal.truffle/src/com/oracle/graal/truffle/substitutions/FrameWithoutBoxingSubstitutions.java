/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.substitutions;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.truffle.api.frame.*;

@ClassSubstitution(FrameWithoutBoxing.class)
public class FrameWithoutBoxingSubstitutions {

    private static final ResolvedJavaField LOCALS_FIELD;
    private static final ResolvedJavaField PRIMITIVELOCALS_FIELD;
    private static final ResolvedJavaField TAGS_FIELD;

    static {
        try {
            MetaAccessProvider runtime = Graal.getRequiredCapability(MetaAccessProvider.class);
            LOCALS_FIELD = runtime.lookupJavaField(FrameWithoutBoxing.class.getDeclaredField("locals"));
            PRIMITIVELOCALS_FIELD = runtime.lookupJavaField(FrameWithoutBoxing.class.getDeclaredField("primitiveLocals"));
            TAGS_FIELD = runtime.lookupJavaField(FrameWithoutBoxing.class.getDeclaredField("tags"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    @MethodSubstitution(isStatic = false, forced = true)
    public static Object pack(FrameWithoutBoxing frame) {
        return null;
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static Object getObject(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Object);
        return getObjectUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setObject(FrameWithoutBoxing frame, FrameSlot slot, Object value) {
        verifySet(frame, slot, FrameSlotKind.Object);
        setObjectUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static boolean getBoolean(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Boolean);
        return getBooleanUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setBoolean(FrameWithoutBoxing frame, FrameSlot slot, boolean value) {
        verifySet(frame, slot, FrameSlotKind.Boolean);
        setBooleanUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static float getFloat(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Float);
        return getFloatUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setFloat(FrameWithoutBoxing frame, FrameSlot slot, float value) {
        verifySet(frame, slot, FrameSlotKind.Float);
        setFloatUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static long getLong(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Long);
        return getLongUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setLong(FrameWithoutBoxing frame, FrameSlot slot, long value) {
        verifySet(frame, slot, FrameSlotKind.Long);
        setLongUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static int getInt(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Int);
        return getIntUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setInt(FrameWithoutBoxing frame, FrameSlot slot, int value) {
        verifySet(frame, slot, FrameSlotKind.Int);
        setIntUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static double getDouble(FrameWithoutBoxing frame, FrameSlot slot) {
        verifyGet(frame, slot, FrameSlotKind.Double);
        return getDoubleUnsafe(frame, slot);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static void setDouble(FrameWithoutBoxing frame, FrameSlot slot, double value) {
        verifySet(frame, slot, FrameSlotKind.Double);
        setDoubleUnsafe(frame, slot, value);
    }

    @MethodSubstitution(isStatic = false)
    public static Object getObjectUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Object, frame, slot, LOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setObjectUnsafe(FrameWithoutBoxing frame, FrameSlot slot, Object value) {
        FrameSetNode.set(Kind.Object, frame, slot, value, LOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static boolean getBooleanUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Boolean, frame, slot, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setBooleanUnsafe(FrameWithoutBoxing frame, FrameSlot slot, boolean value) {
        FrameSetNode.set(Kind.Boolean, frame, slot, value, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static int getIntUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Int, frame, slot, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setIntUnsafe(FrameWithoutBoxing frame, FrameSlot slot, int value) {
        FrameSetNode.set(Kind.Int, frame, slot, value, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static long getLongUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Long, frame, slot, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setLongUnsafe(FrameWithoutBoxing frame, FrameSlot slot, long value) {
        FrameSetNode.set(Kind.Long, frame, slot, value, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static double getDoubleUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Double, frame, slot, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setDoubleUnsafe(FrameWithoutBoxing frame, FrameSlot slot, double value) {
        FrameSetNode.set(Kind.Double, frame, slot, value, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static float getFloatUnsafe(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Float, frame, slot, PRIMITIVELOCALS_FIELD);
    }

    @MethodSubstitution(isStatic = false)
    public static void setFloatUnsafe(FrameWithoutBoxing frame, FrameSlot slot, float value) {
        FrameSetNode.set(Kind.Float, frame, slot, value, PRIMITIVELOCALS_FIELD);
    }

    private static void verifySet(FrameWithoutBoxing frame, FrameSlot slot, FrameSlotKind accessType) {
        setTag(frame, slot, (byte) accessType.ordinal());
    }

    private static void verifyGet(FrameWithoutBoxing frame, FrameSlot slot, FrameSlotKind accessType) {
        if (getTag(frame, slot) != (byte) accessType.ordinal()) {
            DeoptimizeNode.deopt(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.UnreachedCode);
        }
    }

    private static byte getTag(FrameWithoutBoxing frame, FrameSlot slot) {
        return FrameGetNode.get(Kind.Byte, frame, slot, TAGS_FIELD);
    }

    private static void setTag(FrameWithoutBoxing frame, FrameSlot slot, byte tag) {
        FrameSetNode.set(Kind.Byte, frame, slot, tag, TAGS_FIELD);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static MaterializedFrame materialize(FrameWithoutBoxing frame) {
        return MaterializeFrameNode.materialize(frame);
    }

    @MethodSubstitution(isStatic = false, forced = true)
    public static boolean isInitialized(FrameWithoutBoxing frame, FrameSlot slot) {
        return getTag(frame, slot) != 0;
    }
}
