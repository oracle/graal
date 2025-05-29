/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.identityhashcode;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.SplittableRandom;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;
import jdk.internal.misc.Unsafe;

public final class IdentityHashCodeSupport {
    public static final LocationIdentity IDENTITY_HASHCODE_LOCATION = NamedLocationIdentity.mutable("identityHashCode");

    /**
     * Location representing the {@linkplain Heap#getIdentityHashSalt salt values used for the
     * identity hash code of objects}. These values change between collections, so this location
     * must be killed at safepoint checks and allocation slow-paths.
     */
    public static final LocationIdentity IDENTITY_HASHCODE_SALT_LOCATION = NamedLocationIdentity.mutable("identityHashCodeSalt");

    private static final FastThreadLocalObject<SplittableRandom> hashCodeGeneratorTL = FastThreadLocalFactory.createObject(SplittableRandom.class, "IdentityHashCodeSupport.hashCodeGeneratorTL");

    /**
     * Initialization can require synchronization which is not allowed during safepoints, so this
     * method can be called before using identity hash codes during a safepoint operation.
     */
    public static void ensureInitialized() {
        new SplittableRandom().nextInt();
    }

    public static IdentityHashCodeSnippets.Templates createSnippetTemplates(OptionValues options, Providers providers) {
        return SubstrateIdentityHashCodeSnippets.createTemplates(options, providers);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.")
    public static int computeAbsentIdentityHashCode(Object obj) {
        assert ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional();

        /*
         * This code must not be inlined into the snippet because it could be used in an
         * interruptible method: the individual reads and writes of the object header and hash salt
         * could be independently spread across a safepoint check where a GC could happen, during
         * which addresses, header or salt can change. This could cause inconsistent hash values,
         * corrupted object headers (when modified by GC), or memory corruption and crashes (when
         * writing the object header to the object's previous location after is has been moved).
         */
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        Word objPtr = Word.objectToUntrackedPointer(obj);
        Word header = oh.readHeaderFromPointer(objPtr);
        if (oh.hasOptionalIdentityHashField(header)) {
            /*
             * Between the snippet and execution of this method, another thread could have set the
             * header bit and a GC could have triggered and added the field.
             */
            return readIdentityHashCodeFromField(obj);
        }
        if (!oh.hasIdentityHashFromAddress(header)) {
            oh.setIdentityHashFromAddress(objPtr, header);
        }
        return computeHashCodeFromAddress(obj);
    }

    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.")
    public static int computeHashCodeFromAddress(Object obj) {
        Word address = Word.objectToUntrackedPointer(obj);
        long salt = Heap.getHeap().getIdentityHashSalt(obj);
        SignedWord salted = Word.signed(salt).xor(address);
        int hash = mix32(salted.rawValue()) >>> 1; // shift: ensure positive, same as on HotSpot
        return (hash == 0) ? 1 : hash; // ensure nonzero
    }

    /** Avalanching bit mixer, from {@link SplittableRandom}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int mix32(long a) {
        long z = a;
        z = (z ^ (z >>> 33)) * 0x62a9d9ed799705f5L;
        return (int) (((z ^ (z >>> 28)) * 0xcb24d0a5c88c35b3L) >>> 32);
    }

    public static int generateRandomHashCode() {
        SplittableRandom hashCodeGenerator = hashCodeGeneratorTL.get();
        if (hashCodeGenerator == null) {
            /*
             * Create a new thread-local random number generator. SplittableRandom ensures that
             * values created by different random number generator instances are random as a whole.
             */
            hashCodeGenerator = new SplittableRandom();
            hashCodeGeneratorTL.set(hashCodeGenerator);
        }

        /*
         * The range of nextInt(MAX_INT) includes 0 and excludes MAX_INT, so adding 1 gives us the
         * range [1, MAX_INT] that we want.
         */
        int hashCode = hashCodeGenerator.nextInt(Integer.MAX_VALUE) + 1;

        assert hashCode != 0 : "Must not return 0 because it means 'hash code not computed yet' in the field that stores the hash code";
        assert hashCode > 0 : "The Java HotSpot VM only returns positive numbers for the identity hash code, so we want to have the same restriction on Substrate VM in order to not surprise users";

        return hashCode;
    }

    /**
     * Reads the identity hashcode from the identity hashcode field. Returns zero if the object
     * doesn't have an identity hashcode yet. Note that this method must not be called for objects
     * that do not have an identity hashcode field (e.g., objects where the identity hashcode is
     * computed from the address).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static int readIdentityHashCodeFromField(Object obj) {
        assertHasIdentityHashField(obj);

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        int numBits = ol.getIdentityHashCodeNumBits();
        int shift = ol.getIdentityHashCodeShift();
        int offset = LayoutEncoding.getIdentityHashOffset(obj);

        int totalBits = numBits + shift;
        if (totalBits <= Integer.SIZE) {
            int value = ObjectAccess.readInt(obj, offset, IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION);
            return extractIdentityHashCode(value, numBits, shift);
        }

        long value = ObjectAccess.readLong(obj, offset, IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION);
        return extractIdentityHashCode(value, numBits, shift);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static int generateIdentityHashCode(Object obj) {
        /* The guarantee makes the code a bit smaller as obj is non-null afterward. */
        VMError.guarantee(obj != null);

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        assert !ol.isIdentityHashFieldOptional();

        int numBits = ol.getIdentityHashCodeNumBits();
        int shift = ol.getIdentityHashCodeShift();
        int offset = LayoutEncoding.getIdentityHashOffset(obj);

        int newHash = generateRandomHashCode();
        int totalBits = numBits + shift;
        if (numBits == Integer.SIZE && shift == 0) {
            /* There is a dedicated int field for the identity hashcode. */
            if (!Unsafe.getUnsafe().compareAndSetInt(obj, offset, 0, newHash)) {
                return ObjectAccess.readInt(obj, offset, IDENTITY_HASHCODE_LOCATION);
            }
        } else if (totalBits <= Integer.SIZE) {
            int existingValue;
            int newValue;
            do {
                existingValue = Unsafe.getUnsafe().getIntOpaque(obj, offset);
                int existingHash = extractIdentityHashCode(existingValue, numBits, shift);
                if (existingHash != 0) {
                    return existingHash;
                }
                newValue = encodeIdentityHashCode(existingValue, newHash, shift);
            } while (!Unsafe.getUnsafe().compareAndSetInt(obj, offset, existingValue, newValue));
        } else {
            assert totalBits <= Long.SIZE;
            long existingValue;
            long newValue;
            do {
                existingValue = Unsafe.getUnsafe().getLongOpaque(obj, offset);
                int existingHash = extractIdentityHashCode(existingValue, numBits, shift);
                if (existingHash != 0) {
                    return existingHash;
                }
                newValue = encodeIdentityHashCode(existingValue, newHash, shift);
            } while (!Unsafe.getUnsafe().compareAndSetLong(obj, offset, existingValue, newValue));
        }

        assert readIdentityHashCodeFromField(obj) == newHash;
        return newHash;
    }

    /** This method may only be called after the hub pointer was already written. */
    public static void writeIdentityHashCodeToImageHeap(Pointer hashCodePtr, int value) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        int numBits = ol.getIdentityHashCodeNumBits();
        int shift = ol.getIdentityHashCodeShift();
        long mask = ol.getIdentityHashCodeMask();

        int totalBits = numBits + shift;
        if (totalBits <= Integer.SIZE) {
            int oldValue = hashCodePtr.readInt(0);
            assertIdentityHashCodeZero(oldValue, mask);
            hashCodePtr.writeInt(0, encodeIdentityHashCode(oldValue, value, shift));
        } else {
            assert totalBits <= Long.SIZE;
            long oldValue = hashCodePtr.readLong(0);
            assertIdentityHashCodeZero(oldValue, mask);
            hashCodePtr.writeLong(0, encodeIdentityHashCode(oldValue, value, shift));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int extractIdentityHashCode(int value, int numBits, int shift) {
        int left = Integer.SIZE - numBits - shift;
        int right = Integer.SIZE - numBits;
        return (value << left) >>> right;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int extractIdentityHashCode(long value, int numBits, int shift) {
        int left = Long.SIZE - numBits - shift;
        int right = Long.SIZE - numBits;
        return (int) ((value << left) >>> right);
    }

    private static int encodeIdentityHashCode(int existingValue, int newHash, int shift) {
        return existingValue | (newHash << shift);
    }

    private static long encodeIdentityHashCode(long existingValue, long newHash, int shift) {
        return existingValue | (newHash << shift);
    }

    private static void assertIdentityHashCodeZero(int oldValue, long mask) {
        assert (oldValue & mask) == 0 : "hashcode bits must be 0";
    }

    private static void assertIdentityHashCodeZero(long oldValue, long mask) {
        assert (oldValue & mask) == 0L : "hashcode bits must be 0";
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void assertHasIdentityHashField(Object obj) {
        if (GraalDirectives.inIntrinsic()) {
            ReplacementsUtil.dynamicAssert(hasIdentityHashField(obj), "must have an identity hashcode field");
        } else {
            assert hasIdentityHashField(obj) : "must have an identity hashcode field";
        }
    }

    /**
     * Note that the result of this method is prone to race conditions. All races that can happen
     * don't matter for the current callers though (we only want to know if the given object
     * definitely has an identity hash code field - once it has one, it won't be taken away).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean hasIdentityHashField(Object obj) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        return !ol.isIdentityHashFieldOptional() || oh.hasOptionalIdentityHashField(oh.readHeaderFromObject(obj));
    }
}
