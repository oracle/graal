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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMMutex;

/**
 * An uninterruptible thread-safe hashtable with a fixed size that uses chaining in case of a
 * collision.
 */
final class UninterruptibleThreadSafeHashtable<T extends UninterruptibleEntry<T>> implements UninterruptibleAbstractHashtable<T> {

    private final VMMutex mutex;
    private final UninterruptibleAbstractHashtable<T> hashtable;

    @Platforms(Platform.HOSTED_ONLY.class)
    UninterruptibleThreadSafeHashtable(String name, UninterruptibleAbstractHashtable<T> hashtable) {
        this.hashtable = hashtable;
        this.mutex = new VMMutex(name);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void setSize(int size) {
        hashtable.setSize(size);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getSize() {
        return hashtable.getSize();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public T[] getTable() {
        return hashtable.getTable();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public T contains(T valueOnStack) {
        return hashtable.contains(valueOnStack);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long put(T valueOnStack) {
        mutex.lockNoTransition();
        try {
            return hashtable.put(valueOnStack);
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean putIfAbsent(T valueOnStack) {
        mutex.lockNoTransition();
        try {
            return hashtable.putIfAbsent(valueOnStack);
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void free(T t) {
        hashtable.free(t);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clear() {
        hashtable.clear();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void teardown() {
        hashtable.teardown();
    }
}
