/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import static com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import static com.oracle.graal.pointsto.ObjectScanner.asString;
import static com.oracle.graal.pointsto.ObjectScanner.constantAsObject;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ReusableSet;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.meta.JavaConstant;

public class HeapSnapshotVerifier {
    private static final int QUIET = 0;
    private static final int MOST = 1;
    private static final int ALL = 2;

    static class Options {
        @Option(help = "Control heap verifier verbosity level: 0 - quiet, 1 - print most, 2 - print all.", type = OptionType.Expert)//
        public static final OptionKey<Integer> HeapVerifierVerbosity = new OptionKey<>(2);
    }

    protected final BigBang bb;
    protected final ImageHeapScanner scanner;
    protected final ImageHeap imageHeap;

    private ReusableSet scannedObjects;
    private boolean heapPatched;
    private boolean analysisModified;

    private final int verbosity;

    private final Set<AnalysisType> skipArrayTypes = new HashSet<>();
    /**
     * Internal data structure fields that can always be skipped from reporting, for example
     * `java.util.HashMap$Node.next`. These fields can change for example when a map is balanced.
     */
    private final Set<AnalysisField> internalFields = new HashSet<>();
    /**
     * External data structure fields that should be reported when their value is not in the heap
     * (even if they don't link to the value), but should be reported when the value is completely
     * missing.
     */
    private final Set<AnalysisField> externalFields = new HashSet<>();

    public HeapSnapshotVerifier(BigBang bb, ImageHeap imageHeap, ImageHeapScanner scanner) {
        this.bb = bb;
        this.scanner = scanner;
        this.imageHeap = imageHeap;
        scannedObjects = new ReusableSet();
        verbosity = Options.HeapVerifierVerbosity.getValue(bb.getOptions());

        skipArrayTypes.add(scanner.lookupJavaType("java.util.concurrent.ConcurrentHashMap$Node").getArrayClass());
        skipArrayTypes.add(scanner.lookupJavaType("java.util.HashMap$Node").getArrayClass());
        skipArrayTypes.add(scanner.lookupJavaType("java.util.WeakHashMap$Entry").getArrayClass());

        internalFields.add(scanner.lookupJavaField("java.util.concurrent.ConcurrentHashMap", "table"));
        internalFields.add(scanner.lookupJavaField("java.util.concurrent.ConcurrentHashMap$Node", "next"));
        internalFields.add(scanner.lookupJavaField("java.util.HashMap", "table"));
        internalFields.add(scanner.lookupJavaField("java.util.HashMap$Node", "next"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedList", "last"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedList", "first"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedHashMap", "head"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedHashMap", "tail"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedHashMap$Entry", "before"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedHashMap$Entry", "after"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedList$Node", "prev"));
        internalFields.add(scanner.lookupJavaField("java.util.LinkedList$Node", "next"));
        internalFields.add(scanner.lookupJavaField("java.util.ArrayList", "elementData"));

        externalFields.add(scanner.lookupJavaField("java.util.concurrent.ConcurrentHashMap$Node", "key"));
        externalFields.add(scanner.lookupJavaField("java.util.concurrent.ConcurrentHashMap$Node", "val"));
        externalFields.add(scanner.lookupJavaField("java.util.HashMap$Node", "key"));
        externalFields.add(scanner.lookupJavaField("java.util.HashMap$Node", "value"));
        // externalFields.add(scanner.getAnalysisField("java.lang.ref.Reference", "referent")); ?
    }

    public boolean verifyHeapSnapshot(CompletionExecutor executor) throws InterruptedException {
        heapPatched = false;
        analysisModified = false;
        scannedObjects.reset();
        ObjectScanner objectScanner = new ObjectScanner(bb, executor, scannedObjects, new ScanningObserver());
        executor.start();
        scanTypes(objectScanner);
        objectScanner.scanBootImageHeapRoots();
        executor.complete();
        executor.shutdown();
        return analysisModified || heapPatched;
    }

    protected void scanTypes(@SuppressWarnings("unused") ObjectScanner objectScanner) {
    }

    public void cleanupAfterAnalysis() {
        scannedObjects = null;
    }

    private final class ScanningObserver implements ObjectScanningObserver {

        @Override
        public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            boolean result = scanner.getScanningObserver().forRelocatedPointerFieldValue(receiver, field, fieldValue, reason);
            if (result) {
                analysisModified = true;
            }
            return result;
        }

        @Override
        public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
            boolean result = scanner.getScanningObserver().forNullFieldValue(receiver, field, reason);
            if (result) {
                analysisModified = true;
            }
            return result;
        }

        @Override
        public boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            if (field.isStatic()) {
                TypeData typeData = field.getDeclaringClass().getOrComputeData();
                AnalysisFuture<JavaConstant> fieldValueTask = typeData.getFieldTask(field);
                if (!fieldValueTask.isDone()) {
                    // warnStaticFieldNotComputed(field, fieldValue, reason);
                    fieldValueTask.ensureDone();
                }
                JavaConstant fieldSnapshot = fieldValueTask.guardedGet();
                if (!Objects.equals(fieldSnapshot, fieldValue)) {
                    Runnable onAnalysisModified = () -> warnStaticFieldMismatch(field, fieldSnapshot, fieldValue, reason);
                    scanner.patchStaticField(typeData, field, fieldValue, reason, onAnalysisModified).ensureDone();
                    heapPatched = true;
                }
            } else {
                ImageHeapInstance receiverObject = (ImageHeapInstance) getReceiverObject(receiver, reason);
                AnalysisFuture<JavaConstant> fieldValueTask = receiverObject.getFieldTask(field);
                if (!fieldValueTask.isDone()) {
                    // warnInstanceFieldNotComputed(field, fieldValue, reason);
                    fieldValueTask.ensureDone();
                }
                JavaConstant fieldSnapshot = fieldValueTask.guardedGet();
                if (!Objects.equals(fieldSnapshot, fieldValue)) {
                    Runnable onAnalysisModified = () -> warnInstanceFieldMismatch(field, fieldSnapshot, fieldValue, reason);
                    scanner.patchInstanceField(receiverObject, field, fieldValue, reason, onAnalysisModified).ensureDone();
                    heapPatched = true;
                }
            }
            return false;
        }

        @Override
        public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ScanReason reason) {
            boolean result = scanner.getScanningObserver().forNullArrayElement(array, arrayType, elementIndex, reason);
            if (result) {
                analysisModified = true;
            }
            return result;
        }

        @Override
        public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, AnalysisType elementType, int index, ScanReason reason) {
            ImageHeapArray arrayObject = (ImageHeapArray) getReceiverObject(array, reason);
            JavaConstant elementSnapshot = arrayObject.getElement(index);
            if (!Objects.equals(elementSnapshot, elementValue)) {
                Runnable onAnalysisModified = () -> warnArrayElementMismatch(arrayType, elementSnapshot, elementValue, reason);
                arrayObject.setElement(index, scanner.onArrayElementReachable(array, arrayType, elementValue, index, reason, onAnalysisModified));
                heapPatched = true;
            }
            return false;
        }

        private ImageHeapObject getReceiverObject(JavaConstant constant, ScanReason reason) {
            AnalysisFuture<ImageHeapObject> task = imageHeap.getTask(constant);
            if (task == null || !task.isDone()) {
                throw error(reason, "Task %s for constant %s.", (task == null ? "is null" : "not yet executed"), constant);
            }
            return task.guardedGet();
        }

        @Override
        public void forEmbeddedRoot(JavaConstant root, ScanReason reason) {
            AnalysisFuture<ImageHeap.ImageHeapObject> rootTask = imageHeap.getTask(root);
            if (rootTask == null) {
                throw error(reason, "No snapshot task found for embedded root %s %n", root);
            } else if (rootTask.isDone()) {
                JavaConstant rootSnapshot = rootTask.guardedGet().getObject();
                if (!Objects.equals(rootSnapshot, root)) {
                    throw error(reason, "Value mismatch for embedded root %n snapshot: %s %n new value: %s %n", rootSnapshot, root);
                }
            } else {
                throw error(reason, "Snapshot not yet computed for embedded root %n new value: %s %n", root);
            }
        }

        @Override
        public void forScannedConstant(JavaConstant value, ScanReason reason) {
            AnalysisFuture<ImageHeapObject> task = imageHeap.getTask(value);
            if (constantAsObject(bb, value).getClass().equals(Class.class)) {
                /* Make sure the DynamicHub value is scanned. */
                if (task == null) {
                    warnNoTaskForHub(value, reason);
                    scanner.toImageHeapObject(value, reason);
                    heapPatched = true;
                } else {
                    if (!task.isDone()) {
                        /* If there is a task for the hub it should have been triggered. */
                        warnNoTaskComputedForHub(value, reason);
                        task.ensureDone();
                        heapPatched = true;
                    }
                    JavaConstant snapshot = task.guardedGet().getObject();
                    if (!Objects.equals(snapshot, value)) {
                        throw error(reason, "Value mismatch for hub snapshot: %s %n new value: %s %n", snapshot, value);
                    }
                }
            }
        }
    }

    private void warnNoTaskForHub(JavaConstant value, ScanReason reason) {
        analysisModified = true;
        if (!skipWarning()) {
            warning(reason, "No snapshot task found for hub %s %n", value);
        }
    }

    private void warnNoTaskComputedForHub(JavaConstant value, ScanReason reason) {
        analysisModified = true;
        if (!skipWarning()) {
            warning(reason, "Snapshot not yet computed for hub %n new value: %s %n", value);
        }
    }

    private void warnArrayElementMismatch(AnalysisType arrayType, JavaConstant elementSnapshot, JavaConstant elementValue, ScanReason reason) {
        analysisModified = true;
        if (skipWarning(() -> skipArrayTypes.contains(arrayType))) {
            return;
        }
        warning(reason, "Value mismatch for array element %n snapshot: %s %n new value: %s %n", elementSnapshot, elementValue);
    }

    private void warnStaticFieldMismatch(AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
        analysisModified = true;
        if (!skipWarning(() -> internalFields.contains(field))) {
            warning(reason, "Value mismatch for static field %s %n snapshot: %s %n new value: %s %n", field, fieldSnapshot, fieldValue);
        }
    }

    @SuppressWarnings("unused")
    private void warnStaticFieldNotComputed(AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        if (!skipWarning(() -> internalFields.contains(field))) {
            warning(reason, "Snapshot not yet computed for static field %s %n new value: %s %n", field, fieldValue);
        }
    }

    private void warnInstanceFieldMismatch(AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
        analysisModified = true;
        if (!skipWarning(() -> internalFields.contains(field))) {
            warning(reason, "Value mismatch for instance field %s %n snapshot: %s %n new value: %s %n", field, fieldSnapshot, fieldValue);
        }
    }

    @SuppressWarnings("unused")
    private void warnInstanceFieldNotComputed(AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        if (!skipWarning(() -> internalFields.contains(field) || externalFields.contains(field))) {
            warning(reason, "Snapshot not yet computed for instance field %s %n new value: %s %n", field, fieldValue);
        }
    }

    private boolean skipWarning() {
        return skipWarning(() -> false);
    }

    private boolean skipWarning(BooleanSupplier skip) {
        if (verbosity == QUIET) {
            return true;
        } else if (verbosity == MOST) {
            return skip.getAsBoolean();
        }
        assert verbosity == ALL;
        return false;
    }

    private void warning(ScanReason reason, String format, Object... args) {
        System.out.println("WARNING: " + message(reason, format, args));
    }

    private RuntimeException error(ScanReason reason, String format, Object... args) {
        throw AnalysisError.shouldNotReachHere(message(reason, format, args));
    }

    private String message(ScanReason reason, String format, Object... args) {
        String message = format(format, args);
        StringBuilder objectBacktrace = new StringBuilder();
        ObjectScanner.buildObjectBacktrace(bb, reason, objectBacktrace);
        message += objectBacktrace;
        return message;
    }

    private String format(String msg, Object... args) {
        if (args != null) {
            for (int i = 0; i < args.length; ++i) {
                if (args[i] instanceof JavaConstant) {
                    args[i] = asString(bb, (JavaConstant) args[i]);
                } else if (args[i] instanceof AnalysisField) {
                    args[i] = ((AnalysisField) args[i]).format("%H.%n");
                }
            }
        }
        return String.format(msg, args);
    }

}
