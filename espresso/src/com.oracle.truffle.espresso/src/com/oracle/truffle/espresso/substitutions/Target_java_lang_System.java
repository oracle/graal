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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNode;
import com.oracle.truffle.espresso.nodes.helper.TypeCheckNodeGen;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNodeGen;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_java_lang_System {

    private static final DebugCounter SYSTEM_ARRAYCOPY_COUNT = DebugCounter.create("System.arraycopy call count");
    private static final DebugCounter SYSTEM_IDENTITY_HASH_CODE_COUNT = DebugCounter.create("System.identityHashCode call count");

    // region Profile values

    private static final int ZERO_LENGTH_PROFILE = 0;
    private static final int FOREIGN_PROFILE = 1;
    private static final int GUEST_PROFILE = 2;
    private static final int SAME_ARRAY_PROFILE = 3;
    private static final int DIFF_ARRAY_PROFILE = 4;
    private static final int DIFF_PRIMITIVE_ARRAYS_PROFILE = 5;
    private static final int DIFF_REFERENCE_ARRAYS_PROFILE = 6;
    private static final int SUBTYPE_ARRAYS_PROFILE = 7;
    private static final int UNRELATED_TYPE_ARRAYS_PROFILE = 8;

    private static final int ARRAYSTORE_PROFILE = 13;
    private static final int INDEXOUTOFBOUNDS_PROFILE = 14;
    private static final int NULLPOINTER_PROFILE = 15;

    // endregion Profile values

    @Substitution(isTrivial = true)
    public static int identityHashCode(@JavaType(Object.class) StaticObject self, @Inject EspressoLanguage lang) {
        SYSTEM_IDENTITY_HASH_CODE_COUNT.inc();
        return VM.JVM_IHashCode(self, lang);
    }

    @ReportPolymorphism
    @Substitution
    abstract static class Arraycopy extends SubstitutionNode {
        @Child TypeCheckNode compatibleReferenceArrayTypeCheck = insert(TypeCheckNodeGen.create());

        abstract void execute(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler);

        static boolean isNull(StaticObject obj) {
            return StaticObject.isNull(obj);
        }

        static boolean nullOrNotArray(StaticObject src, StaticObject dest) {
            return StaticObject.isNull(src) || StaticObject.isNull(dest) || !src.isArray() || !dest.isArray();
        }

        static boolean foreignArgument(StaticObject src, StaticObject dest) {
            return src.isForeignObject() || dest.isForeignObject();
        }

        static boolean isPrimitiveArray(StaticObject maybeArray) {
            return ((ArrayKlass) maybeArray.getKlass()).getComponentType().isPrimitive();
        }

        static boolean hasDifferentPrimitiveArrayInput(StaticObject src, StaticObject dest) {
            return src.getKlass() != dest.getKlass() && (isPrimitiveArray(src) || isPrimitiveArray(dest));
        }

        static boolean areCompatiblePrimitiveArrays(StaticObject src, StaticObject dest, ArrayKlass arrayKlass) {
            return src.getKlass() == arrayKlass && dest.getKlass() == src.getKlass();
        }

        final boolean areCompatibleReferenceArrays(StaticObject src, StaticObject dest) {
            return compatibleReferenceArrayTypeCheck.executeTypeCheck(getComponentType(dest), getComponentType(src));
        }

        static Klass getComponentType(StaticObject array) {
            assert array.isArray();
            return ((ArrayKlass) array.getKlass()).getComponentType();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "nullOrNotArray(src, dest)")
        void doEarlyError(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            if (StaticObject.isNull(src) || StaticObject.isNull(dest)) {
                throw meta.throwNullPointerException();
            }
            if (!src.isArray() || !dest.isArray()) {
                throw meta.throwException(meta.java_lang_ArrayStoreException);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("expected null or non-array input");
        }

        @Specialization(guards = "foreignArgument(src, dest)")
        void doForeign(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            handleForeignArray(src.isForeignObject() ? src.rawForeignObject(language) : src, srcPos,
                            dest.isForeignObject() ? dest.rawForeignObject(language) : dest, destPos, length,
                            ((ArrayKlass) dest.getKlass()).getComponentType(), meta, profiler);
        }

        /*
         * Here and in other primitive array cases: it is enough to check that arrays are not
         * foreign, null check and isArray check is automatic because klass is compared to []byte
         * (!= null and isArray)
         */
        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._byte_array) || areCompatiblePrimitiveArrays(src, dest, meta._boolean_array)", "!foreignArgument(src, dest)"})
        void doByteArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<byte[]> unwrap(language), srcPos, dest.<byte[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._int_array)", "!foreignArgument(src, dest)"})
        void doIntArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<int[]> unwrap(language), srcPos, dest.<int[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._double_array)", "!foreignArgument(src, dest)"})
        void doDoubleArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<double[]> unwrap(language), srcPos, dest.<double[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._long_array)", "!foreignArgument(src, dest)"})
        void doLongArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<long[]> unwrap(language), srcPos, dest.<long[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._char_array)", "!foreignArgument(src, dest)"})
        void doCharArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<char[]> unwrap(language), srcPos, dest.<char[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._short_array)", "!foreignArgument(src, dest)"})
        void doShortArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<short[]> unwrap(language), srcPos, dest.<short[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"areCompatiblePrimitiveArrays(src, dest, meta._float_array)", "!foreignArgument(src, dest)"})
        void doFloatArray(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<float[]> unwrap(language), srcPos, dest.<float[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isNull(src)", "!isNull(dest)", "!foreignArgument(src, dest)", "hasDifferentPrimitiveArrayInput(src, dest)"})
        void doPrimitiveArrayMismatch(@JavaType(Object.class) StaticObject src, int srcPos,
                        @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            throw throwArrayStoreEx(meta, profiler);
        }

        @Specialization(guards = {"src == dest", "!isPrimitiveArray(src)", "!src.isForeignObject()"})
        void doSameArray(@JavaType(Object.class) StaticObject src, int srcPos,
                        @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<StaticObject[]> unwrap(language), srcPos, dest.<StaticObject[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"length == 0", "!nullOrNotArray(src, dest)", "!isPrimitiveArray(src)", "!isPrimitiveArray(dest)", "!foreignArgument(src, dest)"})
        void doZeroLen(@JavaType(Object.class) StaticObject src, int srcPos,
                        @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            boundsCheck(meta, src.length(language), srcPos, dest.length(language), destPos, length, profiler);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!nullOrNotArray(src, dest)",
                        "!foreignArgument(src, dest)",
                        "!isPrimitiveArray(src)",
                        "!isPrimitiveArray(dest)",
                        "areCompatibleReferenceArrays(src, dest)"})
        void doCompatibleReferenceArrays(@JavaType(Object.class) StaticObject src, int srcPos,
                        @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            try {
                System.arraycopy(src.<StaticObject[]> unwrap(language), srcPos, dest.<StaticObject[]> unwrap(language), destPos, length);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw throwOutOfBoundsEx(meta, profiler);
            }
        }

        @Specialization(guards = {"!nullOrNotArray(src, dest)",
                        "!foreignArgument(src, dest)",
                        "!isPrimitiveArray(src)",
                        "!isPrimitiveArray(dest)",
                        "!areCompatibleReferenceArrays(src, dest)"})
        void doIncompatibleReferenceArrays(@JavaType(Object.class) StaticObject src, int srcPos,
                        @JavaType(Object.class) StaticObject dest, int destPos, int length,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Inject SubstitutionProfiler profiler,
                        @Cached TypeCheckNode copyTypeCheck) {
            SYSTEM_ARRAYCOPY_COUNT.inc();
            boundsCheck(meta, src.length(language), srcPos, dest.length(language), destPos, length, profiler);
            Klass destType = ((ArrayKlass) dest.getKlass()).getComponentType();
            StaticObject[] s = src.unwrap(language);
            StaticObject[] d = dest.unwrap(language);
            for (int i = 0; i < length; i++) {
                StaticObject cpy = s[i + srcPos];
                if (!StaticObject.isNull(cpy) && !copyTypeCheck.executeTypeCheck(destType, cpy.getKlass())) {
                    throw throwArrayStoreEx(meta, profiler);
                }
                d[destPos + i] = cpy;
            }
        }
    }

    // Maintained only to be called by JVM_ArrayCopy.
    public static void arraycopy(@JavaType(Object.class) StaticObject src, int srcPos,
                    @JavaType(Object.class) StaticObject dest, int destPos, int length,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        SYSTEM_ARRAYCOPY_COUNT.inc();
        try {
            doArrayCopy(src, srcPos, dest, destPos, length, language, meta, profiler);
        } catch (NullPointerException e) {
            throw throwNullPointerEx(meta, profiler);
        } catch (ArrayStoreException e) {
            throw throwArrayStoreEx(meta, profiler);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw throwOutOfBoundsEx(meta, profiler);
        }
    }

    /*-
     * Order of throws (see JCK api/java_lang/System/index.html#Arraycopy):
     *
     *  A - NullPointerException
     *      if either src or dst is null.
     *  B - ArrayStoreException
     *      if an element in the src array could not be stored into the dest array because of:
     *          1 - The src argument refers to an object that is not an array.
     *          2 - The dst argument refers to an object that is not an array.
     *          3 - The src argument and dst argument refer to arrays whose component types are
     *          different primitive types.
     *          4 - The src argument refers to an array with a primitive component type and the
     *          dst argument refers to an array with a reference component type.
     *          5 - The src argument refers to an array with a reference component type and the
     *          dst argument refers to an array with a primitive component type.
     *  C - IndexOutOfBoundsException
     *      if copying would cause access of data outside array bounds.
     *  D - ArrayStoreException
     *      if an element in the src array could not be stored into the dest array because of a type mismatch
     */
    private static void doArrayCopy(@JavaType(Object.class) StaticObject src, int srcPos, @JavaType(Object.class) StaticObject dest, int destPos, int length,
                    EspressoLanguage language, Meta meta, SubstitutionProfiler profiler) {
        if (StaticObject.isNull(src) || StaticObject.isNull(dest)) {
            throw throwNullPointerEx(meta, profiler);
        }
        if (src.isForeignObject() || dest.isForeignObject()) {
            // TODO: handle foreign arrays efficiently.
            profiler.profile(FOREIGN_PROFILE);
            handleForeignArray(src.isForeignObject() ? src.rawForeignObject(language) : src, srcPos, dest.isForeignObject() ? dest.rawForeignObject(language) : dest, destPos, length,
                            ((ArrayKlass) dest.getKlass()).getComponentType(), meta, profiler);
            return;
        }

        // Mimics hotspot implementation.
        /*
         * First, check that both given objects are arrays. This is done before bypassing checks and
         * bounds checks (see JCK api/java_lang/System/index.html#Arraycopy: System2015)
         */
        profiler.profile(GUEST_PROFILE);
        if (!src.isArray() || !dest.isArray()) {
            throw throwArrayStoreEx(meta, profiler);
        }

        // If both arrays are the same, a lot of checks can be bypassed
        if (src == dest) {
            profiler.profile(SAME_ARRAY_PROFILE);
            /*
             * Let host VM's arrayCopy implementation handle bounds. Guest type checking is useless
             * here due to both array being the same.
             */
            System.arraycopy(src.unwrap(language), srcPos, dest.unwrap(language), destPos, length);
            return;
        }

        profiler.profile(DIFF_ARRAY_PROFILE);
        Klass destType = ((ArrayKlass) dest.getKlass()).getComponentType();
        Klass srcType = ((ArrayKlass) src.getKlass()).getComponentType();
        if (destType.isPrimitive() || srcType.isPrimitive()) {
            // One of the two arrays is a primitive array.
            profiler.profile(DIFF_PRIMITIVE_ARRAYS_PROFILE);
            if (srcType != destType) {
                throw throwArrayStoreEx(meta, profiler);
            }
            /*
             * Let host VM's arrayCopy implementation handle bounds. Guest type checking is useless
             * here due to one of the two arrays being primitives.
             */
            System.arraycopy(src.unwrap(language), srcPos, dest.unwrap(language), destPos, length);
            return;
        }

        // Both arrays are reference arrays.
        profiler.profile(DIFF_REFERENCE_ARRAYS_PROFILE);
        /*
         * Perform bounds checks BEFORE checking for length == 0. (see JCK
         * api/java_lang/System/index.html#Arraycopy: System1001)
         */
        boundsCheck(meta, src.length(language), srcPos, dest.length(language), destPos, length, profiler);
        if (length == 0) {
            profiler.profile(ZERO_LENGTH_PROFILE);
            // All checks have been done, we can take the shortcut.
            return;
        }
        if (destType.isAssignableFrom(srcType)) {
            // We have guarantee we can copy, as all elements in src conform to dest
            // type.
            profiler.profile(SUBTYPE_ARRAYS_PROFILE);
            System.arraycopy(src.unwrap(language), srcPos, dest.unwrap(language), destPos, length);
            return;
        }

        /*
         * Slow path (manual copy) (/ex: copying an Object[] to a String[]) requires individual type
         * checks. Should rarely happen ( < 1% of cases).
         *
         * Use cases:
         *
         * - System startup.
         *
         * - MethodHandle and CallSite linking.
         */
        profiler.profile(UNRELATED_TYPE_ARRAYS_PROFILE);
        StaticObject[] s = src.unwrap(language);
        StaticObject[] d = dest.unwrap(language);
        for (int i = 0; i < length; i++) {
            StaticObject cpy = s[i + srcPos];
            if (!StaticObject.isNull(cpy) && !destType.isAssignableFrom(cpy.getKlass())) {
                throw throwArrayStoreEx(meta, profiler);
            }
            d[destPos + i] = cpy;
        }
    }

    public static EspressoException throwNullPointerEx(Meta meta, SubstitutionProfiler profiler) {
        profiler.profile(NULLPOINTER_PROFILE);
        throw throwException(meta, meta.java_lang_NullPointerException);
    }

    private static EspressoException throwOutOfBoundsEx(Meta meta, SubstitutionProfiler profiler) {
        profiler.profile(INDEXOUTOFBOUNDS_PROFILE);
        throw throwException(meta, meta.java_lang_ArrayIndexOutOfBoundsException);
    }

    private static EspressoException throwArrayStoreEx(Meta meta, SubstitutionProfiler profiler) {
        profiler.profile(ARRAYSTORE_PROFILE);
        throw throwException(meta, meta.java_lang_ArrayStoreException);
    }

    @TruffleBoundary
    private static EspressoException throwException(Meta meta, ObjectKlass exceptionKlass) {
        throw meta.throwException(exceptionKlass);
    }

    @TruffleBoundary
    private static void handleForeignArray(Object src, int srcPos, Object dest, int destPos, int length, Klass destType, Meta meta, SubstitutionProfiler profiler) {
        InteropLibrary library = InteropLibrary.getUncached();
        ToEspressoNode toEspressoNode = ToEspressoNodeGen.getUncached();
        if (library.isNull(src) || library.isNull(dest)) {
            throw throwNullPointerEx(meta, profiler);
        }
        if (!library.hasArrayElements(src) || !library.hasArrayElements(dest)) {
            throw throwArrayStoreEx(meta, profiler);
        }
        try {
            int srclen = (int) library.getArraySize(src);
            int destlen = (int) library.getArraySize(dest);
            boundsCheck(meta, srclen, srcPos, destlen, destPos, length, profiler);
            for (int i = 0; i < length; i++) {
                Object cpy = toEspressoNode.execute(library.readArrayElement(src, i + srcPos), destType);
                library.writeArrayElement(dest, destPos + i, cpy);
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere();
        } catch (InvalidArrayIndexException e) {
            throw throwArrayStoreEx(meta, profiler);
        }
    }

    private static void boundsCheck(Meta meta, int srcLen, int srcPos, int dstLen, int destPos, int length, SubstitutionProfiler profiler) {
        if (srcPos < 0 || destPos < 0 || length < 0 || // Negative checks
                        srcPos > srcLen - length || destPos > dstLen - length) {
            // Other checks are caught during execution without side effects.
            throw throwOutOfBoundsEx(meta, profiler);
        }
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(isTrivial = true)
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @TruffleBoundary(allowInlining = true)
    @Substitution(isTrivial = true)
    public static long nanoTime() {
        return System.nanoTime();
    }
}
