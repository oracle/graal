/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Provides functionality for scanning constant objects.
 *
 * The scanning is done in parallel. The set of visited elements is a special data structure whose
 * structure can be reused over multiple scanning iterations to save CPU resources. (For details
 * {@link ReusableSet}).
 */
public class ObjectScanner {

    private static final String INDENTATION_AFTER_NEWLINE = "    ";

    protected final BigBang bb;
    private final ReusableSet scannedObjects;
    private final CompletionExecutor executor;
    private final Deque<WorklistEntry> worklist;
    private final ObjectScanningObserver scanningObserver;

    public ObjectScanner(BigBang bb, CompletionExecutor executor, ReusableSet scannedObjects, ObjectScanningObserver scanningObserver) {
        this.bb = bb;
        this.scanningObserver = scanningObserver;
        if (executor != null) {
            this.executor = executor;
            this.worklist = null;
        } else {
            this.executor = null;
            this.worklist = new ConcurrentLinkedDeque<>();
        }
        this.scannedObjects = scannedObjects;
    }

    public void scanBootImageHeapRoots() {
        scanBootImageHeapRoots(null, null);
    }

    public void scanBootImageHeapRoots(Comparator<AnalysisField> fieldComparator, Comparator<BytecodePosition> embeddedRootComparator) {
        // scan the original roots
        // the original roots are all the static fields, of object type, that were accessed
        Collection<AnalysisField> fields = bb.getUniverse().getFields();
        if (fieldComparator != null) {
            ArrayList<AnalysisField> fieldsList = new ArrayList<>(fields);
            fieldsList.sort(fieldComparator);
            fields = fieldsList;
        }
        for (AnalysisField field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && field.getJavaKind() == JavaKind.Object && field.isRead()) {
                execute(() -> scanRootField(field));
            }
        }

        // scan the constant nodes
        Map<JavaConstant, BytecodePosition> embeddedRoots = bb.getUniverse().getEmbeddedRoots();
        if (embeddedRootComparator != null) {
            embeddedRoots.entrySet().stream()
                            .sorted(Map.Entry.comparingByValue(embeddedRootComparator))
                            .forEach(entry -> execute(() -> scanEmbeddedRoot(entry.getKey(), entry.getValue())));
        } else {
            embeddedRoots.forEach((key, value) -> execute(() -> scanEmbeddedRoot(key, value)));
        }

        finish();
    }

    private void execute(Runnable runnable) {
        if (executor != null) {
            executor.execute(debug -> runnable.run());
        } else {
            runnable.run();
        }
    }

    protected void scanEmbeddedRoot(JavaConstant root, BytecodePosition position) {
        try {
            EmbeddedRootScan reason = new EmbeddedRootScan(position, root);
            scanningObserver.forEmbeddedRoot(root, reason);
            scanConstant(root, reason);
        } catch (UnsupportedFeatureException ex) {
            AnalysisMethod method = (AnalysisMethod) position.getMethod();
            bb.getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getMessage(), null, ex);
        }
    }

    /**
     * Scans the value of a root field.
     *
     * @param field the scanned root field
     */
    protected final void scanRootField(AnalysisField field) {
        scanField(field, null, null);
    }

    /**
     * Scans the value of a field giving a receiver object.
     *
     * @param field the scanned field
     * @param receiver the receiver object
     */
    protected void scanField(AnalysisField field, JavaConstant receiver, ScanReason prevReason) {
        ScanReason reason = new FieldScan(field, receiver, prevReason);
        try {
            if (!bb.getUniverse().getHeapScanner().isValueAvailable(field)) {
                /* The value is not available yet. */
                return;
            }
            JavaConstant fieldValue = bb.getUniverse().getHeapScanner().readFieldValue(field, receiver);
            if (fieldValue == null) {
                StringBuilder backtrace = new StringBuilder();
                buildObjectBacktrace(bb, reason, backtrace);
                throw AnalysisError.shouldNotReachHere("Could not find field " + field.format("%H.%n") +
                                (receiver == null ? "" : " on " + constantType(bb, receiver).toJavaName()) +
                                System.lineSeparator() + backtrace);
            }

            if (fieldValue.getJavaKind() == JavaKind.Object && bb.getHostVM().isRelocatedPointer(bb.getMetaAccess(), fieldValue)) {
                scanningObserver.forRelocatedPointerFieldValue(receiver, field, fieldValue, reason);
            } else if (fieldValue.isNull()) {
                scanningObserver.forNullFieldValue(receiver, field, reason);
            } else if (fieldValue.getJavaKind() == JavaKind.Object) {
                /* First notify the observer about the field value... */
                scanningObserver.forNonNullFieldValue(receiver, field, fieldValue, reason);
                /*
                 * ... and only then scan the new value, i.e., follow its references. The order is
                 * important for observers that expect to see the receiver before any of its
                 * referenced elements are being scanned.
                 */
                scanConstant(fieldValue, reason);
            }

        } catch (UnsupportedFeatureException ex) {
            unsupportedFeatureDuringFieldScan(bb, field, receiver, ex, reason);
        } catch (AnalysisError analysisError) {
            if (analysisError.getCause() instanceof UnsupportedFeatureException ex) {
                unsupportedFeatureDuringFieldScan(bb, field, receiver, ex, reason);
            } else {
                throw analysisError;
            }
        }
    }

    /**
     * Scans constant arrays, one element at the time.
     *
     * @param array the array to be scanned
     */
    protected final void scanArray(JavaConstant array, ScanReason prevReason) {

        AnalysisType arrayType = bb.getMetaAccess().lookupJavaType(array);
        ScanReason reason = new ArrayScan(arrayType, array, prevReason);

        if (array instanceof ImageHeapConstant) {
            if (!arrayType.getComponentType().isPrimitive()) {
                ImageHeapArray heapArray = (ImageHeapArray) array;
                for (int idx = 0; idx < heapArray.getLength(); idx++) {
                    final JavaConstant element = heapArray.readElementValue(idx);
                    if (element.isNull()) {
                        scanningObserver.forNullArrayElement(array, arrayType, idx, reason);
                    } else {
                        scanArrayElement(array, arrayType, reason, idx, element);
                    }
                }
            }
        } else {
            Object[] arrayObject = (Object[]) constantAsObject(bb, array);
            for (int idx = 0; idx < arrayObject.length; idx++) {
                Object e = arrayObject[idx];
                if (e == null) {
                    scanningObserver.forNullArrayElement(array, arrayType, idx, reason);
                } else {
                    try {
                        JavaConstant element = bb.getSnippetReflectionProvider().forObject(bb.getUniverse().replaceObject(e));
                        scanArrayElement(array, arrayType, reason, idx, element);
                    } catch (UnsupportedFeatureException ex) { /* Object replacement can throw. */
                        unsupportedFeatureDuringConstantScan(bb, bb.getSnippetReflectionProvider().forObject(e), ex, reason);
                    }
                }
            }
        }
    }

    private void scanArrayElement(JavaConstant array, AnalysisType arrayType, ScanReason reason, int idx, JavaConstant elementConstant) {
        AnalysisType elementType = bb.getMetaAccess().lookupJavaType(elementConstant);
        /* First notify the observer about the array element value... */
        scanningObserver.forNonNullArrayElement(array, arrayType, elementConstant, elementType, idx, reason);
        /*
         * ... and only then scan the new value, i.e., follow its references. The order is important
         * for observers that expect to see the receiver before any of its referenced elements are
         * being scanned.
         */
        scanConstant(elementConstant, reason);
    }

    public void scanConstant(JavaConstant value, ScanReason reason) {
        if (value.isNull() || bb.getMetaAccess().isInstanceOf(value, WordBase.class)) {
            return;
        }
        if (!bb.scanningPolicy().scanConstant(bb, value)) {
            bb.registerTypeAsInHeap(bb.getMetaAccess().lookupJavaType(value), reason);
            return;
        }
        Object valueObj = (value instanceof ImageHeapConstant) ? value : constantAsObject(bb, value);
        if (scannedObjects.putAndAcquire(valueObj) == null) {
            try {
                scanningObserver.forScannedConstant(value, reason);
            } finally {
                scannedObjects.release(valueObj);
                WorklistEntry worklistEntry = new WorklistEntry(value, reason);
                if (executor != null) {
                    executor.execute(debug -> doScan(worklistEntry));
                } else {
                    worklist.push(worklistEntry);
                }
            }
        }
    }

    /**
     * Use the constant hashCode as a key for the unsupported feature to register only one error
     * message if the constant is reachable from multiple places.
     */
    public static void unsupportedFeatureDuringConstantScan(BigBang bb, JavaConstant constant, UnsupportedFeatureException e, ScanReason reason) {
        unsupportedFeature(bb, String.valueOf(receiverHashCode(constant)), e.getMessage(), reason);
    }

    /**
     * Use the field format and receiver hashCode as a key for the unsupported feature to register
     * only one error message if the value is reachable from multiple places. For example both the
     * heap scanning and the heap verification would scan a field that contains an illegal value.
     */
    public static void unsupportedFeatureDuringFieldScan(BigBang bb, AnalysisField field, JavaConstant receiver, UnsupportedFeatureException e, ScanReason reason) {
        unsupportedFeature(bb, (receiver != null ? receiverHashCode(receiver) + "_" : "") + field.format("%H.%n"), e.getMessage(), reason);
    }

    public static void unsupportedFeatureDuringFieldFolding(BigBang bb, AnalysisField field, JavaConstant receiver, UnsupportedFeatureException e, AnalysisMethod parsedMethod, int bci) {
        ScanReason reason = new FieldConstantFold(field, parsedMethod, bci, receiver, new MethodParsing(parsedMethod));
        unsupportedFeature(bb, (receiver != null ? receiverHashCode(receiver) + "_" : "") + field.format("%H.%n"), e.getMessage(), reason);
    }

    /**
     * The {@link ImageHeapScanner} may find issue when scanning the {@link ImageHeapConstant}
     * whereas the {@link HeapSnapshotVerifier} may find issues when scanning the original hosted
     * objects. Use a consistent hash code as a key to map them to the same error message.
     */
    private static int receiverHashCode(JavaConstant receiver) {
        if (receiver instanceof ImageHeapConstant) {
            JavaConstant hostedObject = ((ImageHeapConstant) receiver).getHostedObject();
            if (hostedObject != null) {
                return hostedObject.hashCode();
            }
        }
        return receiver.hashCode();
    }

    public static void unsupportedFeature(BigBang bb, String key, String message, ScanReason reason) {
        StringBuilder objectBacktrace = new StringBuilder();
        AnalysisMethod method = buildObjectBacktrace(bb, reason, objectBacktrace);
        bb.getUnsupportedFeatures().addMessage(key, method, message, objectBacktrace.toString());
    }

    static final String indent = "  ";

    public static AnalysisMethod buildObjectBacktrace(BigBang bb, ScanReason reason, StringBuilder objectBacktrace) {
        return buildObjectBacktrace(bb, reason, objectBacktrace, "Object was reached by");
    }

    public static AnalysisMethod buildObjectBacktrace(BigBang bb, ScanReason reason, StringBuilder objectBacktrace, String header) {
        ScanReason cur = reason;
        objectBacktrace.append(header);
        objectBacktrace.append(System.lineSeparator()).append(indent).append(cur.toString(bb));
        ScanReason rootReason = cur;
        cur = cur.previous;
        while (cur != null) {
            objectBacktrace.append(System.lineSeparator()).append(indent).append(cur.toString(bb));
            rootReason = cur.previous;
            cur = cur.previous;
        }
        if (rootReason instanceof EmbeddedRootScan) {
            /* The root constant was found during scanning of 'method'. */
            return ((EmbeddedRootScan) rootReason).getMethod();
        }
        /* The root constant was not found during method scanning. */
        return null;
    }

    public static String asString(BigBang bb, JavaConstant constant) {
        return asString(bb, constant, true);
    }

    public static String asString(BigBang bb, JavaConstant constant, boolean appendToString) {
        if (constant == null || constant.isNull()) {
            return "null";
        }
        AnalysisType type = bb.getMetaAccess().lookupJavaType(constant);
        if (constant instanceof ImageHeapConstant) {
            // Checkstyle: allow Class.getSimpleName
            return constant.getClass().getSimpleName() + "<" + type.toJavaName() + ">";
            // Checkstyle: disallow Class.getSimpleName
        }
        Object obj = constantAsObject(bb, constant);
        String str = type.toJavaName() + '@' + Integer.toHexString(System.identityHashCode(obj));
        if (appendToString) {
            try {
                str += ": " + limit(obj.toString(), 80).replace(System.lineSeparator(), "");
            } catch (Throwable e) {
                // ignore any error in creating the string representation
            }
        }

        return str;
    }

    public static String limit(String value, int length) {
        StringBuilder buf = new StringBuilder(value);
        if (buf.length() > length) {
            buf.setLength(length);
            buf.append("...");
        }

        return buf.toString();
    }

    /**
     * Processes one constant entry. If the constant has an instance class then it scans its fields,
     * using the constant as a receiver. If the constant has an array class then it scans the array
     * element constants.
     */
    private void doScan(WorklistEntry entry) {
        try {
            AnalysisType type = bb.getMetaAccess().lookupJavaType(entry.constant);
            type.registerAsReachable(entry.reason);

            if (type.isInstanceClass()) {
                /* Scan constant's instance fields. */
                for (ResolvedJavaField javaField : type.getInstanceFields(true)) {
                    AnalysisField field = (AnalysisField) javaField;
                    if (field.getJavaKind() == JavaKind.Object && field.isRead()) {
                        assert !Modifier.isStatic(field.getModifiers());
                        scanField(field, entry.constant, entry.reason);
                    }
                }
            } else if (type.isArray() && bb.getWordTypes().asKind(type.getComponentType()) == JavaKind.Object) {
                /* Scan the array elements. */
                scanArray(entry.constant, entry.reason);
            }
        } catch (UnsupportedFeatureException ex) {
            unsupportedFeatureDuringConstantScan(bb, entry.constant, ex, entry.reason);
        }
    }

    /**
     * Process all consequences for scanned fields. This is done in parallel. Buckets of fields are
     * emitted into the {@code exec}, to mitigate the calling overhead.
     *
     * Processing fields can issue new fields to be scanned so we always add the check for workitems
     * at the end of the worklist.
     */
    protected void finish() {
        if (executor == null) {
            while (!worklist.isEmpty()) {
                int size = worklist.size();
                for (int i = 0; i < size; i++) {
                    doScan(worklist.remove());
                }
            }
        }
    }

    public static AnalysisType constantType(BigBang bb, JavaConstant constant) {
        return bb.getMetaAccess().lookupJavaType(constant);
    }

    public static Object constantAsObject(BigBang bb, JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(Object.class, constant);
    }

    static class WorklistEntry {
        /** The constant to be scanned. */
        private final JavaConstant constant;
        /**
         * The reason this constant was scanned, i.e., either reached from a method scan, from a
         * static field, from an instance field resolved on another constant, or from a constant
         * array indexing.
         */
        private final ScanReason reason;

        WorklistEntry(JavaConstant constant, ScanReason reason) {
            this.constant = constant;
            this.reason = reason;
        }

        public ScanReason getReason() {
            return reason;
        }
    }

    public abstract static class ScanReason {
        final ScanReason previous;
        final JavaConstant constant;

        protected ScanReason(ScanReason previous, JavaConstant constant) {
            this.previous = previous;
            this.constant = constant;
        }

        public ScanReason getPrevious() {
            return previous;
        }

        @SuppressWarnings("unused")
        public String toString(BigBang bb) {
            return toString();
        }
    }

    public static class OtherReason extends ScanReason {
        public static final ScanReason RESCAN = new OtherReason("manually triggered rescan");
        public static final ScanReason HUB = new OtherReason("scanning a class constant");

        final String reason;

        public OtherReason(String reason) {
            super(null, null);
            this.reason = reason;
        }

        @Override
        public String toString() {
            return reason;
        }
    }

    public static class FieldScan extends ScanReason {
        final AnalysisField field;

        private static ScanReason previous(AnalysisField field) {
            Object readBy = field.getReadBy();
            if (readBy instanceof BytecodePosition) {
                ResolvedJavaMethod readingMethod = ((BytecodePosition) readBy).getMethod();
                return new MethodParsing((AnalysisMethod) readingMethod);
            } else if (readBy instanceof AnalysisMethod) {
                return new MethodParsing((AnalysisMethod) readBy);
            } else {
                return new OtherReason("registered as read because: " + readBy);
            }
        }

        public FieldScan(AnalysisField field) {
            this(field, null, previous(field));
        }

        public FieldScan(AnalysisField field, JavaConstant receiver, ScanReason previous) {
            super(previous, receiver);
            this.field = field;
        }

        public AnalysisField getField() {
            return field;
        }

        public String location() {
            Object readBy = field.getReadBy();
            if (readBy instanceof BytecodePosition) {
                BytecodePosition position = (BytecodePosition) readBy;
                return position.getMethod().asStackTraceElement(position.getBCI()).toString();
            } else if (readBy instanceof AnalysisMethod) {
                return ((AnalysisMethod) readBy).asStackTraceElement(0).toString();
            } else {
                return "<unknown-location>";
            }
        }

        @Override
        public String toString(BigBang bb) {
            if (field.isStatic()) {
                return "reading static field " + field.format("%H.%n") + System.lineSeparator() + "    at " + location();
            } else {
                /* Instance field scans must have a receiver, hence the 'of'. */
                return "reading field " + field.format("%H.%n") + " of constant " + System.lineSeparator() + INDENTATION_AFTER_NEWLINE + asString(bb, constant);
            }
        }

        @Override
        public String toString() {
            return field.format("%H.%n");
        }
    }

    public static class FieldConstantFold extends ScanReason {
        final AnalysisField field;
        private final AnalysisMethod parsedMethod;
        private final int bci;

        public FieldConstantFold(AnalysisField field, AnalysisMethod parsedMethod, int bci, JavaConstant receiver, ScanReason previous) {
            super(previous, receiver);
            this.field = field;
            this.parsedMethod = parsedMethod;
            this.bci = bci;
        }

        @Override
        public String toString(BigBang bb) {
            StackTraceElement location = parsedMethod.asStackTraceElement(bci);
            if (field.isStatic()) {
                return "trying to constant fold static field " + field.format("%H.%n") + System.lineSeparator() + "    at " + location;
            } else {
                /* Instance field scans must have a receiver, hence the 'of'. */
                return "trying to constant fold field " + field.format("%H.%n") + " of constant " + System.lineSeparator() +
                                INDENTATION_AFTER_NEWLINE + asString(bb, constant) + System.lineSeparator() + "    at " + location;
            }
        }

        @Override
        public String toString() {
            return field.format("%H.%n");
        }
    }

    public static class MethodParsing extends ScanReason {
        final AnalysisMethod method;

        public MethodParsing(AnalysisMethod method) {
            this(method, null);
        }

        public MethodParsing(AnalysisMethod method, ScanReason previous) {
            super(previous, null);
            this.method = method;
        }

        public AnalysisMethod getMethod() {
            return method;
        }

        @Override
        public String toString() {
            String str = String.format("parsing method %s reachable via the parsing context", method.asStackTraceElement(0));
            str += ReportUtils.parsingContext(method, indent + indent);
            return str;
        }
    }

    public static class ArrayScan extends ScanReason {
        final AnalysisType arrayType;

        public ArrayScan(AnalysisType arrayType, JavaConstant array, ScanReason previous) {
            super(previous, array);
            this.arrayType = arrayType;
        }

        @Override
        public String toString(BigBang bb) {
            return "indexing into array " + asString(bb, constant);
        }

        @Override
        public String toString() {
            return arrayType.toJavaName(true);
        }
    }

    public static class EmbeddedRootScan extends ScanReason {
        final BytecodePosition position;

        public EmbeddedRootScan(BytecodePosition nodeSourcePosition, JavaConstant root) {
            this(nodeSourcePosition, root, new MethodParsing((AnalysisMethod) nodeSourcePosition.getMethod()));
        }

        public EmbeddedRootScan(BytecodePosition nodeSourcePosition, JavaConstant root, ScanReason previous) {
            super(previous, root);
            this.position = nodeSourcePosition;
        }

        public AnalysisMethod getMethod() {
            return (AnalysisMethod) position.getMethod();
        }

        @Override
        public String toString(BigBang bb) {
            return "scanning root " + asString(bb, constant) + " embedded in " + System.lineSeparator() + INDENTATION_AFTER_NEWLINE + position.getMethod().asStackTraceElement(position.getBCI());
        }

        @Override
        public String toString() {
            return position.getMethod().asStackTraceElement(position.getBCI()).toString();
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
     * <li>Committed: counter = sequence
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
