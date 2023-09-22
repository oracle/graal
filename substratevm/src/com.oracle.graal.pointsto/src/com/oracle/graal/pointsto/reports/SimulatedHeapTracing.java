package com.oracle.graal.pointsto.reports;

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
import jdk.vm.ci.meta.JavaConstant;

public class SimulatedHeapTracing {
    private static class HeapConstantContext {
        public final CausalityExport.Event allocator;

        public HeapConstantContext(CausalityExport.Event allocator) {
            this.allocator = allocator;
        }

        public HeapConstantContext clone(CausalityExport.Event cloner) {
            return new HeapConstantContext(cloner);
        }
    }

    private static final class HeapInstanceContext extends HeapConstantContext {
        public final CausalityExport.Event[] fieldWriters;

        public HeapInstanceContext(CausalityExport.Event allocator, AnalysisType constantType) {
            super(allocator);
            this.fieldWriters = new CausalityExport.Event[constantType.getInstanceFields(true).length];
        }

        public HeapInstanceContext(CausalityExport.Event cloner, HeapInstanceContext original) {
            super(cloner);
            fieldWriters = new CausalityExport.Event[original.fieldWriters.length];
            Arrays.fill(fieldWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(CausalityExport.Event cloner) {
            return new HeapInstanceContext(cloner, this);
        }
    }

    private static final class HeapArrayContext extends HeapConstantContext {
        public final CausalityExport.Event[] arrayWriters;

        public HeapArrayContext(CausalityExport.Event allocator, int length) {
            super(allocator);
            this.arrayWriters = new CausalityExport.Event[length];
        }

        public HeapArrayContext(CausalityExport.Event cloner, HeapArrayContext orignal) {
            super(cloner);
            arrayWriters = new CausalityExport.Event[orignal.arrayWriters.length];
            Arrays.fill(arrayWriters, cloner);
        }

        @Override
        public HeapConstantContext clone(CausalityExport.Event cloner) {
            return new HeapArrayContext(cloner, this);
        }
    }

    private static class TypeContext {
        public final CausalityExport.Event[] fieldWriters;

        private TypeContext(AnalysisType type) {
            fieldWriters = new CausalityExport.Event[type.getStaticFields().length];
        }
    }

    protected SimulatedHeapTracing() {
    }

    public static final class Impl extends SimulatedHeapTracing {
        private final Map<ImageHeapConstant, HeapConstantContext> objects = Collections.synchronizedMap(new IdentityHashMap<>());
        private final Map<AnalysisField, CausalityExport.Event> staticFields = Collections.synchronizedMap(new HashMap<>());

        public void traceAllocation(CausalityExport.Event cause, ImageHeapInstance instance, AnalysisType type) {
            objects.put(instance, new HeapInstanceContext(cause, type));
        }

        public void traceAllocation(CausalityExport.Event cause, ImageHeapArray array) {
            objects.put(array, array instanceof ImageHeapObjectArray ? new HeapArrayContext(cause, array.getLength()) : new HeapConstantContext(cause));
        }

        public void traceWrite(CausalityExport.Event cause, ImageHeapInstance instance, AnalysisField field) {
            ((HeapInstanceContext) objects.get(instance)).fieldWriters[field.getPosition()] = cause;
        }

        public void traceWrite(CausalityExport.Event cause, ImageHeapArray array, int position) {
            if (array instanceof ImageHeapObjectArray) {
                ((HeapArrayContext) objects.get(array)).arrayWriters[position] = cause;
            }
        }

        public void traceWrite(CausalityExport.Event cause, AnalysisField field) {
            staticFields.put(field, cause);
        }

        public void traceClone(CausalityExport.Event cause, ImageHeapConstant original, ImageHeapConstant cloned) {
            objects.put(cloned, objects.get(original).clone(cause));
        }

        public CausalityExport.Event getHeapObjectCreator(ImageHeapConstant constant) {
            return objects.get(constant).allocator;
        }

        public CausalityExport.Event getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
            assert !field.isStatic();
            var context = (HeapInstanceContext) objects.get(receiver);
            return context.fieldWriters[field.getPosition()];
        }

        public CausalityExport.Event getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
            assert field.isStatic();
            return staticFields.get(field);
        }

        public CausalityExport.Event getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
            var context = (HeapArrayContext) objects.get(array);
            return context.arrayWriters[elementIndex];
        }
    }

    public static final SimulatedHeapTracing instance = CausalityExportActivation.getActivationStatus() != CausalityExportActivation.DISABLED ? new Impl() : new SimulatedHeapTracing();

    public void traceAllocation(CausalityExport.Event cause, ImageHeapInstance instance, AnalysisType type) {}

    public void traceAllocation(CausalityExport.Event cause, ImageHeapArray array) {}

    public void traceWrite(CausalityExport.Event cause, ImageHeapInstance instance, AnalysisField field) {}

    public void traceWrite(CausalityExport.Event cause, ImageHeapArray array, int position) {}

    public void traceWrite(CausalityExport.Event cause, AnalysisField field) {}

    public void traceClone(CausalityExport.Event cause, ImageHeapConstant original, ImageHeapConstant cloned) {}

    public CausalityExport.Event getHeapObjectCreator(ImageHeapConstant constant) {
        throw new UnsupportedOperationException();
    }

    public CausalityExport.Event getHeapFieldAssigner(ImageHeapInstance receiver, AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public CausalityExport.Event getHeapFieldAssigner(AnalysisField field, JavaConstant value) {
        throw new UnsupportedOperationException();
    }

    public CausalityExport.Event getHeapArrayAssigner(ImageHeapObjectArray array, int elementIndex, JavaConstant value) {
        throw new UnsupportedOperationException();
    }
}
