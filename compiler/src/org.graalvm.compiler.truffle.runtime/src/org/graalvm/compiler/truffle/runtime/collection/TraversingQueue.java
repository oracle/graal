/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.collection;

import java.util.LinkedList;
import java.util.List;

import org.graalvm.compiler.truffle.runtime.CompilationTask;

public class TraversingQueue<E> implements SerialQueue<E> {
    List<E> entries = new LinkedList<>();

    @Override
    public void add(E x) {
        entries.add(x);
    }

    @Override
    public E poll() {
        E peek = peek();
        entries.remove(peek);
        return peek;
    }

    @Override
    public E peek() {
        CompilationTask.ExecutorServiceWrapper firstTierMax = null;
        int firstTierMaxInc = -1;
        CompilationTask.ExecutorServiceWrapper lastTierMax = null;
        int lastTierMaxInc = -1;
        for (E entry : entries) {
            CompilationTask compileTask = ((CompilationTask.ExecutorServiceWrapper) entry).getCompileTask();
            if (compileTask == null) {
                continue;
            }
            int increase = compileTask.getIncrease();
            if (compileTask.isFirstTier()) {
                if (increase > firstTierMaxInc) {
                    firstTierMaxInc = increase;
                    firstTierMax = (CompilationTask.ExecutorServiceWrapper) entry;
                }
            } else {
                if (increase > lastTierMaxInc) {
                    lastTierMaxInc = increase;
                    lastTierMax = (CompilationTask.ExecutorServiceWrapper) entry;
                }
            }
        }
        if (firstTierMax != null) {
            return (E) firstTierMax;
        } else {
            return (E) lastTierMax;
        }
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public Object[] toArray() {
        return entries.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return entries.toArray(a);
    }

    @Override
    public int internalCapacity() {
        return 0;
    }
}
