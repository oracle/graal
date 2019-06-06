/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.c;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.ComparableWord;

import com.oracle.svm.core.util.VMError;

/**
 * Wrapper for arrays that will be part of the image heap and be unmovable as such, but are accessed
 * as {@link NonmovableArray} during the image build.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class HostedNonmovableArray<T> implements NonmovableArray<T> {
    private final Object array;

    HostedNonmovableArray(Object array) {
        this.array = array;
    }

    Object getArray() {
        return array;
    }

    @Override
    public boolean equal(ComparableWord val) {
        if (val != this && val instanceof HostedNonmovableArray<?>) {
            return ((HostedNonmovableArray<?>) val).array == array;
        }
        return (val == this);
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        return !equal(val);
    }

    @Override
    public boolean isNull() {
        return array == null;
    }

    @Override
    public boolean isNonNull() {
        return !isNull();
    }

    @Override
    public long rawValue() {
        /*
         * This method is called when trying to write a word value for this instance to the image.
         *
         * Supporting direct references to NonmovableArray during the image build is tricky. (1) We
         * would need to ensure that the actual array object is seen as reachable, despite being
         * referenced through a word type. (2) The word value that is read has to be the address of
         * the actual array object. With compressed references, we cannot use relocations and would
         * have to compute the address via the heap base at the location where this supposed word
         * value is accessed.
         *
         * We therefore go the route described below. We could also use a boxing type to achieve the
         * same result, at the potential cost of some indirection.
         */
        throw VMError.shouldNotReachHere("The image heap contains an instance of NonmovableArray, which is currently " +
                        "not supported. Unwrap the actual array object using NonmovableArrays.getHostedArray() and reference " +
                        "it directly, then use NonmovableArrays.fromImageHeap() at runtime.");
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public boolean equals(Object obj) {
        throw VMError.shouldNotReachHere("equals() not supported on words");
    }

    @Override
    public int hashCode() {
        throw VMError.shouldNotReachHere("hashCode() not supported on words");
    }
}

/**
 * Wrapper for object arrays that are immutable objects within the image heap and will be pinned,
 * but are accessed as {@link NonmovableObjectArray} during the image build.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class HostedNonmovableObjectArray<T> extends HostedNonmovableArray<Void> implements NonmovableObjectArray<T> {

    HostedNonmovableObjectArray(Object array) {
        super(array);
    }
}
