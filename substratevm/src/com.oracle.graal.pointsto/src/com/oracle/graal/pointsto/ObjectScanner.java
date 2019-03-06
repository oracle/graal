/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides functionality for scanning constant objects.
 *
 * The scanning is done in parallel. The set of visited elements is a special datastructure whose
 * structure can be reused over multiple scanning iterations to save CPU resources. (For details
 * {@link ReusableSet}).
 */
public abstract class ObjectScanner {

    protected final BigBang bb;
    private final ReusableSet scannedObjects;
    protected final Deque<WorklistEntry> worklist;
    /**
     * Used to track whether all work has been completed or not.
     */
    private final AtomicLong workInProgressCount = new AtomicLong(0);

    public ObjectScanner(BigBang bigbang, ReusableSet scannedObjects) {
        this.bb = bigbang;
        this.worklist = new ConcurrentLinkedDeque<>();
        this.scannedObjects = scannedObjects;
    }

    public void scanBootImageHeapRoots(CompletionExecutor executor) {
        scanBootImageHeapRoots(executor, null, null);
    }

    public void scanBootImageHeapRoots(Comparator<AnalysisField> fieldComparator, Comparator<AnalysisMethod> methodComparator) {
        scanBootImageHeapRoots(null, fieldComparator, methodComparator);
    }

    private void scanBootImageHeapRoots(CompletionExecutor exec, Comparator<AnalysisField> fieldComparator, Comparator<AnalysisMethod> methodComparator) {
        // scan the original roots
        // the original roots are all the static fields, of object type, that were accessed
        Collection<AnalysisField> fields = bb.getUniverse().getFields();
        if (fieldComparator != null) {
            ArrayList<AnalysisField> fieldsList = new ArrayList<>(fields);
            fieldsList.sort(fieldComparator);
            fields = fieldsList;
        }
        for (AnalysisField field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getJavaKind() == JavaKind.Object && field.isAccessed()) {
                if (exec != null) {
                    workInProgressCount.incrementAndGet();
                    exec.execute(new CompletionExecutor.DebugContextRunnable() {
                        @Override
                        public void run(DebugContext debug) {
                            try {
                                scanField(field, null, field);
                            } finally {
                                workInProgressCount.decrementAndGet();
                            }
                        }
                    });
                } else {
                    scanField(field, null, field);
                }
            }
        }

        // scan the constant nodes
        Collection<AnalysisMethod> methods = bb.getUniverse().getMethods();
        if (methodComparator != null) {
            ArrayList<AnalysisMethod> methodsList = new ArrayList<>(methods);
            methodsList.sort(methodComparator);
            methods = methodsList;
        }
        for (AnalysisMethod method : methods) {
            if (method.getTypeFlow().getGraph() != null) {
                if (exec != null) {
                    workInProgressCount.incrementAndGet();
                    exec.execute(new CompletionExecutor.DebugContextRunnable() {
                        @Override
                        public void run(DebugContext debug) {
                            try {
                                scanMethod(method);
                            } finally {
                                workInProgressCount.decrementAndGet();
                            }
                        }
                    });
                } else {
                    scanMethod(method);
                }
            }
        }

        finish(exec);
    }

    /*
     * When scanning a field there are three possible cases: the field value is a relocated pointer,
     * the field value is null or the field value is non-null. The method {@link
     * ObjectScanner#scanField(AnalysisField, JavaConstant, Object)} will call the appropriated
     * method during scanning.
     */

    /**
     * Hook for relocated pointer scanned field value.
     *
     * For relocated pointers the value is only known at runtime after methods are relocated, which
     * is pretty much the same as a field written at runtime: we do not have a constant value.
     */
    public abstract void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue);

    /** Hook for scanned null field value. */
    public abstract void forNullFieldValue(JavaConstant receiver, AnalysisField field);

    /** Hook for scanned non-null field value. */
    public abstract void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue);

    /**
     * Scans the value of a field giving a receiver object.
     *
     * @param field the scanned field
     * @param receiver the receiver object
     * @param reason what triggered the scanning
     */
    protected final void scanField(AnalysisField field, JavaConstant receiver, Object reason) {
        try {
            JavaConstant fieldValue = bb.getConstantReflectionProvider().readFieldValue(field, receiver);

            if (fieldValue == null) {
                StringBuilder backtrace = new StringBuilder();
                buildObjectBacktrace(reason, backtrace);
                throw AnalysisError.shouldNotReachHere("Could not find field " + field.format("%H.%n") +
                                (receiver == null ? "" : " on " + bb.getSnippetReflectionProvider().asObject(Object.class, receiver).getClass()) +
                                System.lineSeparator() + backtrace.toString());
            }

            if (fieldValue.getJavaKind() == JavaKind.Object && bb.getHostVM().isRelocatedPointer(bb.getSnippetReflectionProvider().asObject(Object.class, fieldValue))) {
                forRelocatedPointerFieldValue(receiver, field, fieldValue);
            } else if (fieldValue.isNull()) {
                forNullFieldValue(receiver, field);
            } else if (fieldValue.getJavaKind() == JavaKind.Object) {

                if (receiver == null) {
                    registerRoot(fieldValue, field);
                } else {
                    propagateRoot(receiver, fieldValue);
                }
                /* Scan the field value. */
                scanConstant(fieldValue, reason);
                /* Process the field value. */
                forNonNullFieldValue(receiver, field, fieldValue);
            }

        } catch (UnsupportedFeatureException ex) {
            unsupportedFeature(field.format("%H.%n"), ex.getMessage(), reason);
        }
    }

    /** Found a root, map the constant value to the root field. */
    private void registerRoot(JavaConstant fieldValue, AnalysisField field) {
        bb.addRoot(fieldValue, field);
    }

    /** Map the constant value to the root field of it's receiver. */
    private void propagateRoot(JavaConstant receiver, JavaConstant value) {
        Object receiverRoot = bb.getRoot(receiver);
        if (receiverRoot != null) {
            /*
             * Not all roots are accounted for. Only roots for the values coming from static final
             * fields scanning, ConstantFoldLoadFieldPlugin folding or @Fold intrinsification are
             * tracked. All other constants are from ConstantNode objects embedded in the compiled
             * code and found during method scanning. The roots of those embedded constants could be
             * tracked as well if the ConstantNode objects are intercepted at their creation
             * location.
             */
            bb.addRoot(value, receiverRoot);
        }
    }

    /*
     * When scanning array elements there are two possible cases: the element value is either null
     * or the field value is non-null. The method {@link ObjectScanner#scanArray(JavaConstant,
     * Object)} will call the appropriated method during scanning.
     */

    /** Hook for scanned null element value. */
    public abstract void forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex);

    /** Hook for scanned non-null element value. */
    public abstract void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex);

    /**
     * Scans constant arrays, one element at the time.
     *
     * @param array the array to be scanned
     * @param reason what triggered the scanning
     */
    protected final void scanArray(JavaConstant array, Object reason) {

        Object valueObj = bb.getSnippetReflectionProvider().asObject(Object.class, array);
        AnalysisType arrayType = bb.getMetaAccess().lookupJavaType(valueObj.getClass());
        assert valueObj instanceof Object[];

        try {
            Object[] arrayObject = (Object[]) valueObj;
            for (int idx = 0; idx < arrayObject.length; idx++) {
                Object e = arrayObject[idx];
                if (e == null) {
                    forNullArrayElement(array, arrayType, idx);
                } else {
                    Object element = bb.getUniverse().replaceObject(e);
                    JavaConstant elementConstant = bb.getSnippetReflectionProvider().forObject(element);
                    AnalysisType elementType = bb.getMetaAccess().lookupJavaType(element.getClass());

                    propagateRoot(array, elementConstant);
                    /* Scan the array element. */
                    scanConstant(elementConstant, reason);
                    /* Process the array element. */
                    forNonNullArrayElement(array, arrayType, elementConstant, elementType, idx);

                }
            }
        } catch (UnsupportedFeatureException ex) {
            unsupportedFeature(arrayType.toJavaName(true), ex.getMessage(), reason);
        }
    }

    /**
     * Hook for scanned constant. The subclasses can provide additional processing for the scanned
     * constants.
     */
    protected abstract void forScannedConstant(JavaConstant scannedValue, Object reason);

    public final void scanConstant(JavaConstant value, Object reason) {
        Object valueObj = bb.getSnippetReflectionProvider().asObject(Object.class, value);
        if (valueObj == null || valueObj instanceof WordBase) {
            return;
        }
        if (scannedObjects.putAndAcquire(valueObj) == null) {
            try {
                forScannedConstant(value, reason);
            } finally {
                scannedObjects.release(valueObj);
                workInProgressCount.incrementAndGet();
                worklist.push(new WorklistEntry(value, reason));
            }
        }

    }

    private void unsupportedFeature(String key, String message, Object entry) {
        StringBuilder objectBacktrace = new StringBuilder();
        AnalysisMethod method = buildObjectBacktrace(entry, objectBacktrace);
        bb.getUnsupportedFeatures().addMessage(key, method, message, objectBacktrace.toString());
    }

    private AnalysisMethod buildObjectBacktrace(Object entry, StringBuilder objectBacktrace) {
        Object cur = entry;
        AnalysisMethod method = null;

        while (cur instanceof WorklistEntry) {
            WorklistEntry curEntry = (WorklistEntry) cur;
            objectBacktrace.append("\tobject ").append(bb.getMetaAccess().lookupJavaType(curEntry.constant).toJavaName(true)).append(System.lineSeparator());
            cur = curEntry.reason;
        }

        if (cur instanceof ResolvedJavaField) {
            objectBacktrace.append("\tfield ").append(((ResolvedJavaField) cur).format("%H.%n"));
        } else if (cur instanceof ResolvedJavaMethod) {
            objectBacktrace.append("\tmethod ").append(((ResolvedJavaMethod) cur).format("%H.%n(%p)"));
            method = (AnalysisMethod) cur;
        } else {
            objectBacktrace.append("\t[unknown] ").append(cur.toString());
        }
        return method;
    }

    /**
     * Processes one constant entry. If the constant has an instance class then it scans its fields,
     * using the constant as a receiver. If the constant has an array class then it scans the array
     * element constants.
     */
    private void doScan(WorklistEntry entry) {
        Object valueObj = bb.getSnippetReflectionProvider().asObject(Object.class, entry.constant);
        assert checkCorrectClassloaders(entry, valueObj) : "Invalid classloader " + valueObj.getClass().getClassLoader() + " for " + valueObj +
                        ".\nThis error happens when objects from previous image compilations are reached in the current compilation. " +
                        "To prevent this issue reset all static state from the bootclasspath and application classpath that points to the application objects. " +
                        "For reference, see com.oracle.svm.truffle.TruffleFeature.cleanup().";
        AnalysisType type = bb.getMetaAccess().lookupJavaType(valueObj.getClass());

        if (type.isInstanceClass()) {
            /* Scan constant's instance fields. */
            for (AnalysisField field : type.getInstanceFields(true)) {
                if (field.getJavaKind() == JavaKind.Object && field.isAccessed()) {
                    assert !Modifier.isStatic(field.getModifiers());
                    scanField(field, entry.constant, entry);
                }
            }
        } else if (type.isArray() && bb.getProviders().getWordTypes().asKind(type.getComponentType()) == JavaKind.Object) {
            /* Scan the array elements. */
            scanArray(entry.constant, entry);
        }
    }

    private void scanMethod(AnalysisMethod method) {
        try {
            for (Node n : method.getTypeFlow().getGraph().getNodes()) {
                if (n instanceof ConstantNode) {
                    ConstantNode cn = (ConstantNode) n;
                    JavaConstant c = (JavaConstant) cn.getValue();
                    if (c.getJavaKind() == JavaKind.Object) {
                        scanConstant(c, method);
                    }
                }
            }
        } catch (UnsupportedFeatureException ex) {
            bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getMessage(), null, ex);
        }
    }

    private boolean checkCorrectClassloaders(WorklistEntry entry, Object valueObj) {
        boolean result = bb.isValidClassLoader(valueObj);
        if (!result) {
            System.err.println("detected an object that originates from previous compilations: " + valueObj.toString());
            Object reason = entry.getReason();
            while (reason instanceof WorklistEntry) {
                Object value = bb.getSnippetReflectionProvider().asObject(Object.class, ((WorklistEntry) reason).constant);
                System.err.println("  referenced from " + value.toString());
                reason = ((WorklistEntry) reason).getReason();
            }
            System.err.println("  referenced from " + reason);
        }
        return result;
    }

    /**
     * Process all consequences for scanned fields. This is done in parallel. Buckets of fields are
     * emitted into the {@code exec}, to mitigate the calling overhead.
     *
     * Processing fields can issue new fields to be scanned so we always add the check for workitems
     * at the end of the worklist.
     */
    protected void finish(CompletionExecutor exec) {
        if (exec != null) {
            // We add a task which checks for workitems in the worklist, we keep it adding as long
            // there are more workitems available.
            exec.execute(new CompletionExecutor.DebugContextRunnable() {
                @Override
                public void run(DebugContext ignored) {
                    if (workInProgressCount.get() > 0) {
                        int worklistLength = worklist.size();
                        while (!worklist.isEmpty()) {
                            // Put workitems into buckets to avoid overhead for scheduling
                            int bucketSize = Integer.max(1, Integer.max(worklistLength, worklist.size()) / (2 * exec.getExecutorService().getPoolSize()));
                            final ArrayList<WorklistEntry> items = new ArrayList<>();
                            while (!worklist.isEmpty() && items.size() < bucketSize) {
                                items.add(worklist.remove());
                            }
                            exec.execute(new CompletionExecutor.DebugContextRunnable() {
                                @Override
                                public void run(DebugContext ignored2) {
                                    Iterator<WorklistEntry> it = items.iterator();
                                    try {
                                        while (it.hasNext()) {
                                            try {
                                                doScan(it.next());
                                            } finally {
                                                workInProgressCount.decrementAndGet();
                                            }
                                        }
                                    } finally {
                                        // Push back leftover elements (In exception case)
                                        while (it.hasNext()) {
                                            worklist.push(it.next());
                                        }
                                    }
                                }
                            });
                        }
                        // Put ourself into the queue to re-check the worklist
                        exec.execute(this);
                    }
                }
            });
        } else {
            while (!worklist.isEmpty()) {
                int size = worklist.size();
                for (int i = 0; i < size; i++) {
                    doScan(worklist.remove());
                }
            }
        }
    }

    static class WorklistEntry {
        private final JavaConstant constant;
        private final Object reason;

        WorklistEntry(JavaConstant constant, Object reason) {
            this.constant = constant;
            this.reason = reason;
        }

        public JavaConstant getConstant() {
            return constant;
        }

        public Object getReason() {
            return reason;
        }
    }

    /**
     * This datastructure keeps track if an object was already put or not atomically. It takes
     * advantage of the fact that each typeflow iteration adds more objects to the set but never
     * removes elements. Since insertions into maps are expensive we keep the map around over
     * multiple iterations and only update the AtomicInteger sequence number after each iteration.
     *
     * Furthermore it also serializes on the object put until the method release is called with this
     * object. So each object goes through two states:
     * <li>In flight: counter = sequence - 1
     * <li>Commited: counter = sequence
     *
     * If the object is in state in flight, all other calls with this object to putAndAcquire will
     * block until release with the object is called.
     */
    public static final class ReusableSet {
        /**
         * The storage of atomic integers. During analysis the constant count for rather large
         * programs such as the JS interpreter are 90k objects. Hence we use 64k as a good start.
         */
        private final IdentityHashMap<Object, AtomicInteger> store = new IdentityHashMap<>(65536);
        private int sequence = 0;

        public Object putAndAcquire(Object object) {
            IdentityHashMap<Object, AtomicInteger> map = this.store;
            AtomicInteger i = map.get(object);
            int seq = this.sequence;
            int inflightSequence = seq - 1;
            while (true) {
                if (i != null) {
                    int current = i.get();
                    if (current == seq) {
                        return object; // Found and is already released
                    } else {
                        if (current != inflightSequence && i.compareAndSet(current, inflightSequence)) {
                            return null; // We have successfully acquired
                        } else { // Someone else has acquired
                            while (i.get() != seq) { // Wait until released
                                Thread.yield();
                            }
                            return object; // Object has been released
                        }
                    }
                } else {
                    AtomicInteger newSequence = new AtomicInteger(inflightSequence);
                    synchronized (map) {
                        i = map.putIfAbsent(object, newSequence);
                        if (i == null) {
                            return null;
                        } else {
                            continue;
                        }
                    }
                }
            }
        }

        public void release(Object o) {
            IdentityHashMap<Object, AtomicInteger> map = this.store;
            AtomicInteger i = map.get(o);
            if (i == null) {
                // We have missed a value likely someone else has updated the map at the same time.
                // Now synchronize
                synchronized (map) {
                    i = map.get(o);
                }
            }
            i.set(sequence);
        }

        public void reset() {
            sequence += 2;
        }
    }
}
