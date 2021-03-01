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

import org.graalvm.compiler.truffle.runtime.CompilationTask;

public class TraversingQueue<E> implements SerialQueue<E> {
    private final boolean priority;
    private final boolean trace;
    List<E> firstTierEntries = new LinkedList<>();
    LinkedList<E> lastTierEntries = new LinkedList<>();

    public TraversingQueue(boolean priority, boolean trace) {
        this.priority = priority;
        this.trace = trace;
    }

    @Override
    public void add(E x) {
        if (!priority || task(x).isFirstTier()) {
            firstTierEntries.add(x);
        } else {
            lastTierEntries.add(x);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E poll() {
        if (firstTierEntries.isEmpty()) {
            return maxFirstTier(lastTierEntries);
        }
        return maxFirstTier(firstTierEntries);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E peek() {
        throw new UnsupportedOperationException("Peek not supported!");
    }

    private E maxFirstTier(List<E> entries) {
        if (entries.isEmpty()) {
           return null;
        }
        List<E> toRemove = new ArrayList<>();
        StringBuilder builder = new StringBuilder("Queue:[ ");
        long time = System.nanoTime();
        Iterator<E> it = entries.iterator();
        E max = it.next();
        double maxWeight = task(max).weight(time);
        if (task(max).targetPreviouslyCompiled()) {
            return remove(max, entries);
        }
        append(builder, task(max), maxWeight);
        while (it.hasNext()) {
            E entry = it.next();
            if (task(entry).targetPreviouslyCompiled()) {
                return remove(entry, entries);
            }
            CompilationTask task = task(entry);
            double weight = task.weight(time);
            if (task(entry).isCancelled() || weight < 0) {
                toRemove.add(entry);
                continue;
            }
            append(builder, task, weight);
            if (weight > maxWeight) {
                maxWeight = weight;
                max = entry;
            }
        }
        builder.append("]");
        builder.append(System.lineSeparator());
        builder.append("Picked: ");
        append(builder, task(max), maxWeight);
        builder.append(" ").append(task(max).name());
        if (trace) {
            System.out.println(builder);
        }
        remove(toRemove, entries);
        return remove(max, entries);
    }

    private void remove(List<E> toRemove, List<E> entries) {
        if (!toRemove.isEmpty()) {
            for (E e : toRemove) {
                entries.remove(e);
            }
        }
    }

    private StringBuilder append(StringBuilder builder, CompilationTask task, double maxWeight) {
        return builder.append(task.getId()).append(":").append(maxWeight).append(" ");
    }

    private E remove(E max, List<E> entries) {
        entries.remove(max);
        return max;
    }

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
}
