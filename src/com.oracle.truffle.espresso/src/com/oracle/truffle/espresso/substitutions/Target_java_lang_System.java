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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.object.DebugCounter;

@EspressoSubstitutions
public final class Target_java_lang_System {

    private static final DebugCounter SYSTEM_ARRAYCOPY_COUNT = DebugCounter.create("System.arraycopy call count");
    private static final DebugCounter SYSTEM_IDENTITY_HASH_CODE_COUNT = DebugCounter.create("System.identityHashCode call count");

    @Substitution
    public static int identityHashCode(@Host(Object.class) StaticObject self) {
        SYSTEM_IDENTITY_HASH_CODE_COUNT.inc();
        return System.identityHashCode(MetaUtil.maybeUnwrapNull(self));
    }

    @Substitution
    public static void arraycopy(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length,
                    @InjectMeta Meta meta, @InjectProfile SubstitutionProfiler profiler) {
        SYSTEM_ARRAYCOPY_COUNT.inc();
        try {
            doArrayCopy(src, srcPos, dest, destPos, length, meta, profiler);
        } catch (NullPointerException e) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        } catch (ArrayStoreException e) {
            profiler.profile(1);
            throw Meta.throwException(meta.java_lang_ArrayStoreException);
        } catch (ArrayIndexOutOfBoundsException e) {
            profiler.profile(2);
            // System.arraycopy javadoc states it throws IndexOutOfBoundsException, the
            // actual implementation throws ArrayIndexOutOfBoundsException (IooBE subclass).
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }

    private static void doArrayCopy(@Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length,
                    Meta meta, SubstitutionProfiler profiler) {
        if (StaticObject.isNull(src) || StaticObject.isNull(dest)) {
            profiler.profile(3);
            throw meta.throwNullPointerException();
        }
        // Mimics hotspot implementation.
        if (src.isArray() && dest.isArray()) {
            profiler.profile(14);
            // System.arraycopy does the bounds checks
            if (src == dest) {
                profiler.profile(4);
                // Same array, no need to type check
                boundsCheck(meta, src, srcPos, dest, destPos, length, profiler);
                System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
            } else {
                profiler.profile(5);
                ArrayKlass destKlass = (ArrayKlass) dest.getKlass();
                ArrayKlass srcKlass = (ArrayKlass) src.getKlass();
                Klass destType = destKlass.getComponentType();
                Klass srcType = srcKlass.getComponentType();
                if (destType.isPrimitive() && srcType.isPrimitive()) {
                    if (srcType != destType) {
                        profiler.profile(6);
                        throw Meta.throwException(meta.java_lang_ArrayStoreException);
                    }
                    profiler.profile(7);
                    boundsCheck(meta, src, srcPos, dest, destPos, length, profiler);
                    System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
                } else if (!destType.isPrimitive() && !srcType.isPrimitive()) {
                    profiler.profile(8);
                    if (destType.isAssignableFrom(srcType)) {
                        profiler.profile(9);
                        // We have guarantee we can copy, as all elements in src conform to dest.
                        boundsCheck(meta, src, srcPos, dest, destPos, length, profiler);
                        System.arraycopy(src.unwrap(), srcPos, dest.unwrap(), destPos, length);
                    } else {
                        profiler.profile(10);
                        // slow path (manual copy) (/ex: copying an Object[] to a String[]) requires
                        // individual type checks. Should rarely happen ( < 1% of cases).
                        // @formatter:off
                        // Use cases:
                        // - System startup.
                        // - MethodHandle and CallSite linking.
                        boundsCheck(meta, src, srcPos, dest, destPos, length, profiler);
                        StaticObject[] s = src.unwrap();
                        StaticObject[] d = dest.unwrap();
                        for (int i = 0; i < length; i++) {
                            StaticObject cpy = s[i + srcPos];
                            if (StaticObject.isNull(cpy) || destType.isAssignableFrom(cpy.getKlass())) {
                                d[destPos + i] = cpy;
                            } else {
                                profiler.profile(11);
                                throw Meta.throwException(meta.java_lang_ArrayStoreException);
                            }
                        }
                    }
                } else {
                    profiler.profile(12);
                    throw Meta.throwException(meta.java_lang_ArrayStoreException);
                }
            }
        } else {
            profiler.profile(13);
            throw Meta.throwException(meta.java_lang_ArrayStoreException);
        }
    }

    private static void boundsCheck(Meta meta, @Host(Object.class) StaticObject src, int srcPos, @Host(Object.class) StaticObject dest, int destPos, int length, SubstitutionProfiler profiler) {
        if (srcPos < 0 || destPos < 0 || length < 0 || srcPos > src.length() - length || destPos > dest.length() - length) {
            // Other checks are caught during execution without side effects.
            profiler.profile(15);
            throw Meta.throwException(meta.java_lang_ArrayIndexOutOfBoundsException);
        }
    }
}
