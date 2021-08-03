/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Abstract base class for all uninterruptible hashtables.
 */
public interface UninterruptibleAbstractHashtable<T extends UninterruptibleEntry<T>> {

    static <T extends UninterruptibleEntry<T>> UninterruptibleThreadSafeHashtable<T> makeHashtableThreadSafe(String name, UninterruptibleAbstractHashtable<T> table) {
        return new UninterruptibleThreadSafeHashtable<>(name, table);
    }

    /**
     * Sets hashtable size.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setSize(int size);

    /**
     * Gets hashtable size.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int getSize();

    /**
     * Gets hashtable.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    T[] getTable();

    /**
     * Check if {@code valueOnStack} exists in hashtable, if it's exists, return that value.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    T contains(T valueOnStack);

    /**
     * Put {@code valueOnStack} in hashtable.
     * 
     * @return new entry id or existing entry id, if old one is equals to the {@code valueOnStack}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    long put(T valueOnStack);

    /**
     * Put {@code valueOnStack} in hashtable.
     *
     * @return true if new entry is created, false, if it's already there.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean putIfAbsent(T valueOnStack);

    /**
     * Deallocate memory occupied by {@code t}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void free(T t);

    /**
     * Clear all entries from map.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void clear();

    /**
     * Teardown hashtable.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void teardown();
}
