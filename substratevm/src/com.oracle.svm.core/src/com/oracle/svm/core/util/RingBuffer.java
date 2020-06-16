/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.function.Supplier;

import com.oracle.svm.core.annotate.Uninterruptible;

/**
 * Keeps the last-n entries and allows to read the out on demand..
 */
public final class RingBuffer<T> {
    private static int defaultBufferSize = 30;

    private final T[] entries;
    private int pos;
    private boolean wrapped;

    public interface Consumer<T> {
        void accept(Object context, T t);
    }

    public RingBuffer() {
        this(defaultBufferSize);
    }

    @SuppressWarnings("unchecked")
    public RingBuffer(int numEntries) {
        this.entries = (T[]) new Object[numEntries];
    }

    public RingBuffer(int numEntries, Supplier<T> supplier) {
        this(numEntries);
        for (int i = 0; i < entries.length; i++) {
            entries[i] = supplier.get();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private int nextIndex(int p) {
        return (p + 1) % entries.length;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void append(T entry) {
        entries[pos] = entry;
        advance();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void advance() {
        int posNext = nextIndex(pos);
        if (posNext <= pos) {
            wrapped = true;
        }
        pos = posNext;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public T next() {
        T result = entries[pos];
        advance();
        return result;
    }

    public void foreach(Consumer<T> consumer) {
        foreach(null, consumer);
    }

    public void foreach(Object context, Consumer<T> consumer) {
        if (wrapped) {
            int i = pos;
            do {
                consumer.accept(context, entries[i]);
                i = nextIndex(i);
            } while (i != pos);
        } else {
            for (int i = 0; i < pos; i += 1) {
                consumer.accept(context, entries[i]);
            }
        }
    }
}
