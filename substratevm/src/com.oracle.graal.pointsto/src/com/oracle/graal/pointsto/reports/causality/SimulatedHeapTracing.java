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
import com.oracle.graal.pointsto.reports.causality.events.CausalityEvent;
import jdk.vm.ci.meta.JavaConstant;

public class SimulatedHeapTracing {
    private static class HeapConstantContext {
        public final CausalityEvent allocator;

        public HeapConstantContext(CausalityEvent allocator) {
            this.allocator = allocator;
        }

        public HeapConstantContext clone(CausalityEvent cloner) {
            return new HeapConstantContext(cloner);
        }
    }

    private static final class HeapInstanceContext extends HeapConstantContext {
        public final CausalityEvent[] fieldWriters;

        public HeapInstanceContext(CausalityEvent allocator, AnalysisType constantType) {
            super(allocator);
            this.fieldWriters = new CausalityEvent[constantType.getInstanceFields(true).length];
        }

        public HeapInstanceContext(CausalityEvent cloner, HeapInstanceContext original) {
            super(cloner);
            fieldWriters = new CausalityEvent[original.fieldWriters.length];
            Arrays.fill(fieldWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(CausalityEvent cloner) {
            return new HeapInstanceContext(cloner, this);
        }
    }

    private static final class HeapArrayContext extends HeapConstantContext {
        public final CausalityEvent[] arrayWriters;

        public HeapArrayContext(CausalityEvent allocator, int length) {
            super(allocator);
            this.arrayWriters = new CausalityEvent[length];
        }

        public HeapArrayContext(CausalityEvent cloner, HeapArrayContext orignal) {
            super(cloner);
            arrayWriters = new CausalityEvent[orignal.arrayWriters.length];
            Arrays.fill(arrayWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(CausalityEvent cloner) {
            return new HeapArrayContext(cloner, this);
        }
    }

    private static class TypeContext {
        public final CausalityEvent[] fieldWriters;

        private TypeContext(AnalysisType type) {
            fieldWriters = new CausalityEvent[type.getStaticFields().length];
        }
    }

    protected SimulatedHeapTracing() {
    }

    public static final class Impl extends SimulatedHeapTracing {
        private final Map<ImageHeapConstant, HeapConstantContext> objects = Collections.synchronizedMap(new IdentityHashMap<>());
        private final Map<AnalysisField, CausalityEvent> staticFields = Collections.synchronizedMap(new HashMap<>());

        public void traceAllocation(CausalityEvent cause, ImageHeapInstance instance, AnalysisType type) {
            objects.put(instance, new HeapInstanceContext(cause, type));
        }

        public void traceAllocation(CausalityEvent cause, ImageHeapArray array) {
            objects.put(array, array instanceof ImageHeapObjectArray ? new HeapArrayContext(cause, array.getLength()) : new HeapConstantContext(cause));
        }

        public void traceWrite(CausalityEvent cause, ImageHeapInstance instance, AnalysisField field) {
            ((HeapInstanceContext) objects.get(instance)).fieldWriters[field.getPosition()] = cause;
        }

        public void traceWrite(CausalityEvent cause, ImageHeapArray array, int position) {
            if (array instanceof ImageHeapObjectArray) {
                ((HeapArrayContext) objects.get(array)).arrayWriters[position] = cause;
            }
        }

        public void traceWrite(CausalityEvent cause, AnalysisField field) {
            staticFields.put(field, cause);
        }

        public void traceClone(CausalityEvent cause, ImageHeapConstant original, ImageHeapConstant cloned) {
            objects.put(cloned, objects.get(original).clone(cause));
        }

        public CausalityEvent getHeapObjectCreator(ImageHeapConstant constant) {
            return objects.get(constant).allocator;
        }

        public CausalityEvent getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
            assert !field.isStatic();
            var context = (HeapInstanceContext) objects.get(receiver);
            return context.fieldWriters[field.getPosition()];
        }

        public CausalityEvent getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
            assert field.isStatic();
            return staticFields.get(field);
        }

        public CausalityEvent getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
            var context = (HeapArrayContext) objects.get(array);
            return context.arrayWriters[elementIndex];
        }
    }

    public static final SimulatedHeapTracing instance = CausalityExportActivation.getActivationStatus() != CausalityExportActivation.DISABLED ? new Impl() : new SimulatedHeapTracing();

    public void traceAllocation(CausalityEvent cause, ImageHeapInstance instance, AnalysisType type) {}

    public void traceAllocation(CausalityEvent cause, ImageHeapArray array) {}

    public void traceWrite(CausalityEvent cause, ImageHeapInstance instance, AnalysisField field) {}

    public void traceWrite(CausalityEvent cause, ImageHeapArray array, int position) {}

    public void traceWrite(CausalityEvent cause, AnalysisField field) {}

    public void traceClone(CausalityEvent cause, ImageHeapConstant original, ImageHeapConstant cloned) {}

    public CausalityEvent getHeapObjectCreator(ImageHeapConstant constant) {
        throw new UnsupportedOperationException();
    }

    public CausalityEvent getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public CausalityEvent getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public CausalityEvent getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
        throw new UnsupportedOperationException();
    }
}
