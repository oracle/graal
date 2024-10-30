/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.reports.causality;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapObjectArray;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.causality.facts.Fact;
import jdk.vm.ci.meta.JavaConstant;

public class SimulatedHeapTracing {
    private static class HeapConstantContext {
        public final Fact allocator;

        HeapConstantContext(Fact allocator) {
            this.allocator = allocator;
        }

        public HeapConstantContext clone(Fact cloner) {
            return new HeapConstantContext(cloner);
        }
    }

    private static final class HeapInstanceContext extends HeapConstantContext {
        public final Fact[] fieldWriters;

        HeapInstanceContext(Fact allocator, AnalysisType constantType) {
            super(allocator);
            this.fieldWriters = new Fact[constantType.getInstanceFields(true).length];
        }

        HeapInstanceContext(Fact cloner, HeapInstanceContext original) {
            super(cloner);
            fieldWriters = new Fact[original.fieldWriters.length];
            Arrays.fill(fieldWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(Fact cloner) {
            return new HeapInstanceContext(cloner, this);
        }
    }

    private static final class HeapArrayContext extends HeapConstantContext {
        public final Fact[] arrayWriters;

        HeapArrayContext(Fact allocator, int length) {
            super(allocator);
            this.arrayWriters = new Fact[length];
        }

        HeapArrayContext(Fact cloner, HeapArrayContext orignal) {
            super(cloner);
            arrayWriters = new Fact[orignal.arrayWriters.length];
            Arrays.fill(arrayWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(Fact cloner) {
            return new HeapArrayContext(cloner, this);
        }
    }

    protected SimulatedHeapTracing() {
    }

    public static final class Impl extends SimulatedHeapTracing {
        private final Map<ImageHeapConstant, HeapConstantContext> objects = Collections.synchronizedMap(new IdentityHashMap<>());
        private final Map<AnalysisField, Fact> staticFields = Collections.synchronizedMap(new HashMap<>());

        @Override
        public void traceAllocation(Fact cause, ImageHeapInstance instance, AnalysisType type) {
            objects.put(instance, new HeapInstanceContext(cause, type));
        }

        @Override
        public void traceAllocation(Fact cause, ImageHeapArray array) {
            objects.put(array, array instanceof ImageHeapObjectArray ? new HeapArrayContext(cause, array.getLength()) : new HeapConstantContext(cause));
        }

        @Override
        public void traceWrite(Fact cause, ImageHeapInstance instance, AnalysisField field) {
            ((HeapInstanceContext) objects.get(instance)).fieldWriters[field.getPosition()] = cause;
        }

        @Override
        public void traceWrite(Fact cause, ImageHeapArray array, int position) {
            if (array instanceof ImageHeapObjectArray) {
                ((HeapArrayContext) objects.get(array)).arrayWriters[position] = cause;
            }
        }

        @Override
        public void traceWrite(Fact cause, AnalysisField field) {
            staticFields.put(field, cause);
        }

        @Override
        public void traceClone(Fact cause, ImageHeapConstant original, ImageHeapConstant cloned) {
            objects.put(cloned, objects.get(original).clone(cause));
        }

        @Override
        public Fact getHeapObjectCreator(ImageHeapConstant constant) {
            return objects.get(constant).allocator;
        }

        @Override
        public Fact getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
            assert !field.isStatic();
            var context = (HeapInstanceContext) objects.get(receiver);
            return context.fieldWriters[field.getPosition()];
        }

        @Override
        public Fact getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
            assert field.isStatic();
            return staticFields.get(field);
        }

        @Override
        public Fact getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
            var context = (HeapArrayContext) objects.get(array);
            return context.arrayWriters[elementIndex];
        }
    }

    public static final SimulatedHeapTracing instance = Causality.isEnabled() ? new Impl() : new SimulatedHeapTracing();

    public void traceAllocation(Fact cause, ImageHeapInstance instance, AnalysisType type) {
    }

    public void traceAllocation(Fact cause, ImageHeapArray array) {
    }

    public void traceWrite(Fact cause, ImageHeapInstance instance, AnalysisField field) {
    }

    public void traceWrite(Fact cause, ImageHeapArray array, int position) {
    }

    public void traceWrite(Fact cause, AnalysisField field) {
    }

    public void traceClone(Fact cause, ImageHeapConstant original, ImageHeapConstant cloned) {
    }

    public Fact getHeapObjectCreator(ImageHeapConstant constant) {
        throw new UnsupportedOperationException();
    }

    public Fact getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public Fact getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public Fact getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
        throw new UnsupportedOperationException();
    }
}
