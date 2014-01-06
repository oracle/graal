/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.runtime.core.array;

/**
 * Simple array utilities, not tied to any particular implementation.
 */
public abstract class ArrayUtilities {

    /**
     * Apply Ruby's wrap-around index semantics.
     */
    public static int normaliseIndex(int length, int index) {
        if (index < 0) {
            return length + index;
        } else {
            return index;
        }
    }

    /**
     * Apply Ruby's wrap-around index semantics.
     */
    public static int normaliseExclusiveIndex(int length, int exclusiveIndex) {
        if (exclusiveIndex < 0) {
            return length + exclusiveIndex + 1;
        } else {
            return exclusiveIndex;
        }
    }

    /**
     * If an exclusive index is beyond the end of the array, truncate it to be length of the array.
     */
    public static int truncateNormalisedExclusiveIndex(int length, int normalisedExclusiveEnd) {
        return Math.min(length, normalisedExclusiveEnd);
    }

    /**
     * What capacity should we allocate for a given requested length?
     */
    public static int capacityFor(int length) {
        return Math.max(16, length * 2);
    }

}
