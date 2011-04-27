/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This enum represents all of the intrinsics, i.e. a library methods that
 * are treated specially by the compiler. Note that the list includes more intrinsics
 * than are currently handled by C1X.
 *
 * @author Ben L. Titzer
 */
public enum C1XIntrinsic {

    // java.lang.Object
    java_lang_Object$init     ("java.lang.Object", "<init>", "()V"),
    java_lang_Object$hashCode ("java.lang.Object", "hashCode", "()I"),
    java_lang_Object$getClass ("java.lang.Object", "getClass", "()Ljava/lang/Class;"),
    java_lang_Object$clone    ("java.lang.Object", "clone", "()Ljava/lang/Object;"),

    // java.lang.Class
    java_lang_Class$isAssignableFrom ("java.lang.Class", "isAssignableFrom", "(Ljava/lang/Class;)Z"),
    java_lang_Class$isInstance       ("java.lang.Class", "isInstance", "(Ljava/lang/Object;)Z"),
    java_lang_Class$getModifiers     ("java.lang.Class", "getModifiers", "()I"),
    java_lang_Class$isInterface      ("java.lang.Class", "isInterface", "()Z"),
    java_lang_Class$isArray          ("java.lang.Class", "isArray", "()Z"),
    java_lang_Class$isPrimitive      ("java.lang.Class", "isPrimitive", "()Z"),
    java_lang_Class$getSuperclass    ("java.lang.Class", "getSuperclass", "()Ljava/lang/Class;"),
    java_lang_Class$getComponentType ("java.lang.Class", "getComponentType", "()Ljava/lang/Class;"),

    // java.util.Arrays
    java_util_Arrays$copyOf ("java.util.Arrays", "copyOf", "([Ljava/lang/Object;I)[Ljava/lang/Object;"),

    // java.lang.String
    java_lang_String$compareTo ("java.lang.String", "compareTo", "(Ljava/lang/String;)I"),
    java_lang_String$indexOf   ("java.lang.String", "indexOf", "(Ljava/lang/String;)I"),
    java_lang_String$equals    ("java.lang.String", "equals", "(Ljava/lang/Object;)Z"),

    // java.lang.Math
    java_lang_Math$abs   ("java.lang.Math", "abs", "(D)D"),
    java_lang_Math$sin   ("java.lang.Math", "sin", "(D)D"),
    java_lang_Math$cos   ("java.lang.Math", "cos", "(D)D"),
    java_lang_Math$tan   ("java.lang.Math", "tan", "(D)D"),
    java_lang_Math$atan2 ("java.lang.Math", "atan2", "(DD)D"),
    java_lang_Math$sqrt  ("java.lang.Math", "sqrt", "(D)D"),
    java_lang_Math$log   ("java.lang.Math", "log", "(D)D"),
    java_lang_Math$log10 ("java.lang.Math", "log10", "(D)D"),
    java_lang_Math$pow   ("java.lang.Math", "pow", "(DD)D"),
    java_lang_Math$exp   ("java.lang.Math", "exp", "(D)D"),
    java_lang_Math$min   ("java.lang.Math", "min", "(II)I"),
    java_lang_Math$max   ("java.lang.Math", "max", "(II)I"),

    // java.lang.Float
    java_lang_Float$floatToRawIntBits ("java.lang.Float", "floatToRawIntBits", "(F)I"),
    java_lang_Float$floatToIntBits    ("java.lang.Float", "floatToIntBits", "(F)I"),
    java_lang_Float$intBitsToFloat    ("java.lang.Float", "intBitsToFloat", "(I)F"),

    // java.lang.Double
    java_lang_Double$doubleToRawLongBits ("java.lang.Double", "doubleToRawLongBits", "(D)J"),
    java_lang_Double$doubleToLongBits    ("java.lang.Double", "doubleToLongBits", "(D)J"),
    java_lang_Double$longBitsToDouble    ("java.lang.Double", "longBitsToDouble", "(J)D"),

    // java.lang.Integer
    java_lang_Integer$bitCount     ("java.lang.Integer", "bitCount", "(I)I"),
    java_lang_Integer$reverseBytes ("java.lang.Integer", "reverseBytes", "(I)I"),

    // java.lang.Long
    java_lang_Long$bitCount     ("java.lang.Long", "bitCount", "(J)I"),
    java_lang_Long$reverseBytes ("java.lang.Long", "reverseBytes", "(J)J"),

    // java.lang.System
    java_lang_System$identityHashCode  ("java.lang.System", "identityHashCode", "(Ljava/lang/Object;)I"),
    java_lang_System$currentTimeMillis ("java.lang.System", "currentTimeMillis", "()J"),
    java_lang_System$nanoTime          ("java.lang.System", "nanoTime", "()J"),
    java_lang_System$arraycopy         ("java.lang.System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),

    // java.lang.Thread
    java_lang_Thread$currentThread ("java.lang.Thread", "currentThread", "()Ljava/lang/Thread;"),

    // java.lang.reflect.Array
    java_lang_reflect_Array$getLength ("java.lang.reflect.Array", "getLength", "(Ljava/lang/Object;)I"),
    java_lang_reflect_Array$newArray  ("java.lang.reflect.Array", "newArray", "(Ljava/lang/Class;I)Ljava/lang/Object;"),

    // java.nio.Buffer
    java_nio_Buffer$checkIndex ("java.nio.Buffer", "checkIndex", "(I)I"),

    // sun.misc.Unsafe
    sun_misc_Unsafe$compareAndSwapObject ("sun.misc.Unsafe", "compareAndSwapObject", "(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z"),
    sun_misc_Unsafe$compareAndSwapLong   ("sun.misc.Unsafe", "compareAndSwapLong", "(Ljava/lang/Object;JJJ)Z"),
    sun_misc_Unsafe$compareAndSwapInt    ("sun.misc.Unsafe", "compareAndSwapInt", "(Ljava/lang/Object;JII)Z");

    private static final HashMap<String, HashMap<String, C1XIntrinsic>> intrinsicMap = new HashMap<String, HashMap<String, C1XIntrinsic>>(100);

    /**
     * The name of the class in which this method is declared.
     */
    public final String className;

    /**
     * The name of this intrinsic method.
     */
    public final String methodName;

    /**
     * The signature of this intrinsic method as a string.
     */
    public final String signature;

    C1XIntrinsic(String className, String methodName, String signature) {
        // Check that enum names are according to convention.
        assert className.equals(name().substring(0, name().indexOf('$')).replace('_', '.'));
        assert methodName.equals("<init>") || methodName.equals(name().substring(name().indexOf('$') + 1));
        this.methodName = methodName;
        this.className = className;
        this.signature = signature;
    }

    static {
        // iterate through all the intrinsics and add them to the map
        for (C1XIntrinsic i : C1XIntrinsic.values()) {
            // note that the map uses internal names to map lookup faster
            String className = CiUtil.toInternalName(i.className);
            HashMap<String, C1XIntrinsic> map = intrinsicMap.get(className);
            if (map == null) {
                map = new HashMap<String, C1XIntrinsic>();
                intrinsicMap.put(className, map);
            }
            map.put(i.methodName + i.signature, i);
        }
    }

    /**
     * Looks up an intrinsic for the specified method.
     * @param method the compiler interface method
     * @return a reference to the intrinsic for the method, if the method is an intrinsic
     * (and is loaded); {@code null} otherwise
     */
    public static C1XIntrinsic getIntrinsic(RiMethod method) {
        RiType holder = method.holder();
        if (method.isResolved() && holder.isResolved() && holder.isInitialized()) {
            // note that the map uses internal names to make lookup faster
            HashMap<String, C1XIntrinsic> map = intrinsicMap.get(holder.name());
            if (map != null) {
                C1XIntrinsic result = map.get(method.name() + method.signature().asString());
                return result;
            }
        }
        return null;
    }
}
