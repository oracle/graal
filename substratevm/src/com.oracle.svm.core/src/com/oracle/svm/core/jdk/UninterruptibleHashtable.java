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
 * Common interface for all uninterruptible hashtable implementations. Please note that we don't use
 * generics as this sometimes breaks the {@link Uninterruptible} annotation when ECJ is used for
 * compiling the Java sources.
 */
public interface UninterruptibleHashtable {
    /**
     * Gets the number of entries that are in the hashtable.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int getSize();

    /**
     * Returns the internal array of the hashtable.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UninterruptibleEntry[] getTable();

    /**
     * Returns the matching value for {@code valueOnStack} from the hashtable. If there is no
     * matching value in the hashtable, a null pointer is returned.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UninterruptibleEntry get(UninterruptibleEntry valueOnStack);

    /**
     * Tries to insert {@code valueOnStack} into the hashtable. Returns false if there was already a
     * matching entry in the hashtable or if an error occurred while insert the entry. Returns true
     * if the entry was inserted successfully.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean putIfAbsent(UninterruptibleEntry valueOnStack);

    /**
     * If the hashtable contains an existing entry that matches {@code valueOnStack}, then this
     * existing entry will be returned and no value will be inserted.
     * 
     * If there wasn't already a matching entry, this method tries to create and insert a new entry
     * hashtable. If an error occurred while inserting the entry, a null pointer is returned
     * instead.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UninterruptibleEntry getOrPut(UninterruptibleEntry valueOnStack);

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
