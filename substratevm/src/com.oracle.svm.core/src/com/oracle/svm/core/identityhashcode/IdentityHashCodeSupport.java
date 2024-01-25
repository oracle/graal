/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.SplittableRandom;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.IdentityHashCodeSnippets;
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
    public static int generateIdentityHashCode(Object obj) {
        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        VMError.guarantee(!ol.isIdentityHashFieldOptional(), "Optional hash is handled in snippet");

        int newHashCode = generateRandomHashCode();
        int offset = LayoutEncoding.getIdentityHashOffset(obj);
        if (!Unsafe.getUnsafe().compareAndSetInt(obj, offset, 0, newHashCode)) {
            newHashCode = ObjectAccess.readInt(obj, offset, IDENTITY_HASHCODE_LOCATION);
        }
        VMError.guarantee(newHashCode != 0, "Missing identity hash code");
        return newHashCode;
    }

    @Uninterruptible(reason = "Prevent a GC interfering with the object's identity hash state.")
    public static int computeHashCodeFromAddress(Object obj) {
        Word address = Word.objectToUntrackedPointer(obj);
        long salt = Heap.getHeap().getIdentityHashSalt(obj);
        SignedWord salted = WordFactory.signed(salt).xor(address);
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

    private static int generateRandomHashCode() {
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
}
