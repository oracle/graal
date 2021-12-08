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

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ReusableSet;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeap.ImageHeapObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.TypeData;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.meta.JavaConstant;

public class HeapSnapshotVerifier {
    protected final BigBang bb;
    protected final ImageHeapScanner scanner;
    protected final ImageHeap imageHeap;

    private ReusableSet scannedObjects;
    private boolean foundMismatch;

    private final boolean printOnMismatch;

    public HeapSnapshotVerifier(BigBang bb, ImageHeap imageHeap, ImageHeapScanner scanner) {
        this.bb = bb;
        this.scanner = scanner;
        this.imageHeap = imageHeap;
        scannedObjects = new ReusableSet();
        printOnMismatch = ImageHeapScanner.Options.PrintOnMismatch.getValue(bb.getOptions());
    }

    public boolean verifyHeapSnapshot(CompletionExecutor executor) throws InterruptedException {
        scanner.disable();
        foundMismatch = false;
        scannedObjects.reset();
        executor.start();
        ObjectScanner objectScanner = new ObjectScanner(bb, null, scannedObjects, new ScanningObserver());
        scanTypes(objectScanner);
        objectScanner.scanBootImageHeapRoots();
        executor.complete();
        executor.shutdown();
        scanner.enable();
        return foundMismatch;
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
                foundMismatch = true;
            }
            return result;
        }

        @Override
        public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
            boolean result = scanner.getScanningObserver().forNullFieldValue(receiver, field, reason);
            if (result) {
                foundMismatch = true;
            }
            return result;
        }

        @Override
        public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            try {
                if (field.isStatic()) {
                    TypeData typeData = field.getDeclaringClass().getOrComputeData();
                    AnalysisFuture<JavaConstant> fieldValueTask = typeData.getStaticFieldValueTask(field);
                    if (fieldValueTask.isDone()) {
                        JavaConstant fieldSnapshot = fieldValueTask.get();
                        if (!Objects.equals(fieldSnapshot, fieldValue)) {
                            warning(reason, "Value mismatch for static field %s %n snapshot: %s %n new value: %s %n",
                                            field, fieldSnapshot, fieldValue);
                            scanner.patchStaticField(typeData, field, fieldValue, reason).ensureDone();
                        }
                    } else {
                        warning(reason, "Snapshot not yet computed for field %s %n new value: %s %n", field, fieldValue);
                        fieldValueTask.ensureDone();
                    }
                } else {
                    AnalysisFuture<ImageHeapObject> receiverTask = imageHeap.getTask(receiver);
                    assert receiverTask != null && receiverTask.isDone() : message(reason, "Task %s for receiver %s.",
                                    (receiverTask == null ? "is null" : "not yet executed"), receiver);
                    ImageHeapInstance receiverObject = (ImageHeapInstance) receiverTask.get();
                    AnalysisFuture<JavaConstant> fieldValueTask = receiverObject.getFieldTask(field);
                    if (fieldValueTask.isDone()) {
                        JavaConstant fieldSnapshot = fieldValueTask.get();
                        if (!Objects.equals(fieldSnapshot, fieldValue)) {
                            warning(reason, "Value mismatch for field %s %n snapshot: %s %n new value: %s %n",
                                            field, fieldSnapshot, fieldValue);
                            scanner.patchInstanceField(receiverObject, field, fieldValue, reason).ensureDone();
                        }
                    } else {
                        warning(reason, "Snapshot not yet computed for field %s %n new value: %s %n", field, fieldValue);
                        fieldValueTask.ensureDone();
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                throw AnalysisError.shouldNotReachHere(e);
            }
        }

        @Override
        public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ScanReason reason) {
            boolean result = scanner.getScanningObserver().forNullArrayElement(array, arrayType, elementIndex, reason);
            if (result) {
                foundMismatch = true;
            }
            return result;
        }

        @Override
        public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, AnalysisType elementType, int index, ScanReason reason) {
            try {
                AnalysisFuture<ImageHeapObject> arrayTask = imageHeap.getTask(array);
                assert arrayTask != null && arrayTask.isDone() : message(reason, "Task %s for array %s.",
                                (arrayTask == null ? "is null" : "not yet executed"), array);
                ImageHeapArray receiverObject = (ImageHeapArray) arrayTask.get();
                JavaConstant elementSnapshot = receiverObject.getElement(index);
                if (!Objects.equals(elementSnapshot, elementValue)) {
                    warning(reason, "Value mismatch for array element %n snapshot: %s %n new value: %s %n", elementSnapshot, elementValue);
                    receiverObject.setElement(index, scanner.onArrayElementReachable(array, arrayType, elementValue, index, reason));
                }
            } catch (InterruptedException | ExecutionException e) {
                throw AnalysisError.shouldNotReachHere(e);
            }
        }

        @Override
        public void forEmbeddedRoot(JavaConstant root, ScanReason reason) {
            AnalysisFuture<ImageHeap.ImageHeapObject> rootTask = imageHeap.getTask(root);
            if (rootTask == null) {
                throw error(reason, "No snapshot task found for embedded root %s %n", root);
            } else if (rootTask.isDone()) {
                try {
                    JavaConstant rootSnapshot = rootTask.get().getObject();
                    if (!Objects.equals(rootSnapshot, root)) {
                        throw error(reason, "Value mismatch for embedded root %n snapshot: %s %n new value: %s %n", rootSnapshot, root);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw AnalysisError.shouldNotReachHere(e);
                }
            } else {
                throw error(reason, "Snapshot not yet computed for embedded root %n new value: %s %n", root);
            }
        }

        @Override
        public void forScannedConstant(JavaConstant value, ScanReason reason) {
            /* Make sure the value is scanned. */
            AnalysisFuture<ImageHeapObject> task = imageHeap.getTask(value);
            if (task == null) {
                scanner.toImageHeapObject(value, reason);
            }
        }
    }

    private void warning(ScanReason reason, String format, Object... args) {
        foundMismatch = true;
        if (printOnMismatch) {
            System.out.println("WARNING: " + message(reason, format, args));
        }
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
