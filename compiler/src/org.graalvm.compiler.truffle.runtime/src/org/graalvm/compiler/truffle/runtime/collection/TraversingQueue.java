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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.graalvm.compiler.truffle.runtime.CompilationTask;

public class TraversingQueue<E> implements SerialQueue<E> {
    private final boolean firstTierPriority;
    List<E> firstTierEntries = new LinkedList<>();
    Queue<E> lastTierEntries = new LinkedList<>();

    public TraversingQueue(boolean priority) {
        this.firstTierPriority = priority;
    }

    @Override
    public void add(E x) {
        if (task(x).isFirstTier() || !firstTierPriority) {
            firstTierEntries.add(x);
        } else {
            lastTierEntries.add(x);
        }
    }

    @Override
    public E poll() {
        if (firstTierEntries.isEmpty()) {
            return lastTierEntries.poll();
        }
        return poll(firstTierEntries);
    }

    private E poll(List<E> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        List<E> toRemove = new ArrayList<>();
        long time = System.nanoTime();
        Iterator<E> it = entries.iterator();
        E max = it.next();
        double maxWeight = task(max).weight(time);
        boolean maxCompiled = task(max).targetPreviouslyCompiled();
        while (it.hasNext()) {
            E entry = it.next();
            CompilationTask task = task(entry);
            if (task.targetPreviouslyCompiled()) {
                return remove(entry, entries);
            }
            double weight = task.weight(time);
            if (task.isCancelled() || weight < 0) {
                toRemove.add(entry);
                continue;
            }
            boolean compiled = task.targetPreviouslyCompiled();
            if (greater(maxWeight, maxCompiled, weight, compiled)) {
                maxWeight = weight;
                maxCompiled = compiled;
                max = entry;
            }
        }
        remove(toRemove, entries);
        return remove(max, entries);
    }

    private static boolean greater(double maxWeight, boolean maxCompiled, double weight, boolean compiled) {
        if (compiled && !maxCompiled) {
            return true;
        }
        if (maxCompiled && !compiled) {
            return false;
        }
        return weight > maxWeight;
    }

    private void remove(List<E> toRemove, List<E> entries) {
        if (!toRemove.isEmpty()) {
            for (E e : toRemove) {
                entries.remove(e);
            }
        }
    }

    private E remove(E max, List<E> entries) {
        entries.remove(max);
        return max;
    }

    @SuppressWarnings("unchecked")
    private CompilationTask task(E entry) {
        return ((CompilationTask.ExecutorServiceWrapper) entry).getCompileTask();
    }

    @Override
    public void clear() {
        firstTierEntries.clear();
        lastTierEntries.clear();
    }

    @Override
    public int size() {
        return firstTierEntries.size();
    }

    @Override
    public Object[] toArray() {
        return firstTierEntries.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return firstTierEntries.toArray(a);
    }

    @Override
    public int internalCapacity() {
        return 0;
    }

    @Override
    public E peek() {
        throw new UnsupportedOperationException("Peek not supported!");
    }
}
