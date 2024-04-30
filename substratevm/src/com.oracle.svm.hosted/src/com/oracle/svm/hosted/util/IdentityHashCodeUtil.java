/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.util;

import com.oracle.graal.pointsto.util.GraalAccess;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.internal.misc.Unsafe;

/**
 * Utility methods for directly reading and setting an object's identity hash code from an object
 * header without using traditional JDK methods.
 *
 * Note all needed JavaConstant -> (underlying Object) transformations must be done before calling
 * these utilities.
 */
public class IdentityHashCodeUtil {

    // Because the identity hashcode only uses 31 bits, we can use all negative number as tags
    public static final int UNINITIALIZED = -1;
    public static final int NOT_RETRIEVABLE = -2;
    public static final int UNKNOWN = -3;

    private static final Unsafe unsafe;
    private static final GraalHotSpotVMConfig config;
    private static final long hashCodeMask;

    static {
        unsafe = Unsafe.getUnsafe();
        config = GraalAccess.getGraalCapability(GraalHotSpotVMConfig.class);
        hashCodeMask = NumUtil.getNbitNumberLong(31) << config.identityHashCodeShift;
    }

    /**
     * Reads identity hash code from and object's header. If an identity hash code is not yet
     * present, this method will not cause it to be initialized.
     *
     * If the identity hash code is uninitialized, then {@link #UNINITIALIZED} is returned.<br/>
     * If the object passed is null, then {@link #NOT_RETRIEVABLE} is returned.<br/>
     * If the hash code cannot be return for another reason, then {@link #UNKNOWN} is returned.
     */
    public static int readIdentityHashCode(Object obj) {
        if (obj == null) {
            return NOT_RETRIEVABLE;
        }

        return readIdentityHashCode(readMarkWord(obj));
    }

    /**
     * Tries to inject the requested identity hash code into the object.
     *
     * @return the final value within the hash code, or the special flags described in
     *         {@link #readIdentityHashCode(Object)}.
     */
    public static int injectIdentityHashCode(Object obj, int requestedHashCode) {
        assert NumUtil.isUnsignedNbit(31, requestedHashCode) : Assertions.errorMessage("Injected hashcode should be a 31-bit unsigned value", requestedHashCode);

        if (obj == null) {
            return NOT_RETRIEVABLE;
        }

        do {
            long markWord = readMarkWord(obj);
            int currentHashCode = readIdentityHashCode(markWord);
            if (currentHashCode != UNINITIALIZED) {
                // Cannot set hash code; return current value
                return currentHashCode;
            }
            if (trySetIdentityHashCode(obj, markWord, requestedHashCode)) {
                // hash code was successfully set
                return requestedHashCode;
            }
        } while (true);
    }

    private static long readMarkWord(Object obj) {
        return unsafe.getLong(obj, config.markOffset);
    }

    private static int readIdentityHashCode(long markWord) {
        /*
         * See HotSpotHashCodeSnippets for explanation.
         */
        long lockBits = markWord & config.lockMaskInPlace;
        boolean containsHashCode;
        if (config.lockingMode == config.lockingModeLightweight) {
            containsHashCode = lockBits != config.monitorMask;
        } else {
            containsHashCode = lockBits == config.unlockedMask;
        }
        if (containsHashCode) {
            int hashcode = (int) (markWord >>> config.identityHashCodeShift);
            if (hashcode == config.uninitializedIdentityHashCodeValue) {
                return UNINITIALIZED;
            }
            return hashcode;
        } else {
            return UNKNOWN;
        }
    }

    /**
     * @return true if the object's identity hash code was set.
     */
    private static boolean trySetIdentityHashCode(Object obj, long originalMarkWord, int hashCode) {
        long newMarkWord;
        if (config.uninitializedIdentityHashCodeValue == 0) {
            newMarkWord = originalMarkWord | (((long) hashCode) << config.identityHashCodeShift);
        } else {
            newMarkWord = (originalMarkWord & (~hashCodeMask)) | (((long) hashCode) << config.identityHashCodeShift);
        }
        return unsafe.compareAndSetLong(obj, config.markOffset, originalMarkWord, newMarkWord);
    }
}
