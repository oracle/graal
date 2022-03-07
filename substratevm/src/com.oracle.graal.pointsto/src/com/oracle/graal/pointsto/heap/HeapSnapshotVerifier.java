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

import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ReusableSet;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.CompletionExecutor;

import jdk.vm.ci.meta.JavaConstant;

public class HeapSnapshotVerifier {

    static class Options {
        @Option(help = "Control heap verifier verbosity level: 0 - quiet, 1 - info, 2 - warning, 3 - all.", type = OptionType.Expert)//
        public static final OptionKey<Integer> HeapVerifierVerbosity = new OptionKey<>(0);
    }

    protected final BigBang bb;
    protected final ImageHeapScanner scanner;
    protected final ImageHeap imageHeap;

    private ReusableSet scannedObjects;
    private boolean heapPatched;
    private boolean analysisModified;

    private final int verbosity;
    private int iterations;

    public HeapSnapshotVerifier(BigBang bb, ImageHeap imageHeap, ImageHeapScanner scanner) {
        this.bb = bb;
        this.scanner = scanner;
        this.imageHeap = imageHeap;
        scannedObjects = new ReusableSet();
        verbosity = Options.HeapVerifierVerbosity.getValue(bb.getOptions());
    }

    public boolean requireAnalysisIteration(CompletionExecutor executor) throws InterruptedException {
        info("Verifying the heap snapshot...");
        analysisModified = false;
        heapPatched = false;
        int reachableTypesBefore = bb.getUniverse().getReachableTypes();
        iterations++;
        scannedObjects.reset();
        ObjectScanner objectScanner = new ObjectScanner(bb, executor, scannedObjects, new ScanningObserver());
        executor.start();
        scanTypes(objectScanner);
        objectScanner.scanBootImageHeapRoots();
        executor.complete();
        executor.shutdown();
        int verificationReachableTypes = bb.getUniverse().getReachableTypes() - reachableTypesBefore;
        if (heapPatched) {
            info("Heap verification patched the heap snapshot.");
        } else {
            info("Heap verification didn't find any heap snapshot modifications.");
        }
        if (verificationReachableTypes > 0) {
            info("Heap verification made " + verificationReachableTypes + " new types reachable.");
        } else {
            info("Heap verification didn't make any new types reachable.");
        }
        if (analysisModified) {
            info("Heap verification modified the analysis state. Executing an additional analysis iteration.");
        } else {
            info("Heap verification didn't modify the analysis state. Heap state stabilized after " + iterations + " iterations.");
            info("Exiting analysis.");
        }
        return analysisModified || verificationReachableTypes > 0;
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
                if (fieldValueTask.isDone()) {
                    JavaConstant fieldSnapshot = fieldValueTask.guardedGet();
                    if (!Objects.equals(fieldSnapshot, fieldValue)) {
                        Consumer<ScanReason> onAnalysisModified = (deepReason) -> onStaticFieldMismatch(field, fieldSnapshot, fieldValue, deepReason);
                        scanner.patchStaticField(typeData, field, fieldValue, reason, onAnalysisModified).ensureDone();
                        heapPatched = true;
                    }
                } else {
                    onStaticFieldNotComputed(field, fieldValue, reason);
                }
            } else {
                ImageHeapInstance receiverObject = (ImageHeapInstance) getReceiverObject(receiver, reason);
                AnalysisFuture<JavaConstant> fieldValueTask = receiverObject.getFieldTask(field);
                if (fieldValueTask.isDone()) {
                    JavaConstant fieldSnapshot = fieldValueTask.guardedGet();
                    if (!Objects.equals(fieldSnapshot, fieldValue)) {
                        Consumer<ScanReason> onAnalysisModified = (deepReason) -> onInstanceFieldMismatch(receiverObject, field, fieldSnapshot, fieldValue, deepReason);
                        scanner.patchInstanceField(receiverObject, field, fieldValue, reason, onAnalysisModified).ensureDone();
                        heapPatched = true;
                    }
                } else {
                    /*
                     * There may be some instance fields not yet computed because the verifier can
                     * insert new objects for annotation proxy implementations when scanning types.
                     * The annotations are set lazily, based on reachability, since we only want
                     * annotations in the heap that are otherwise marked as used.
                     */
                    Consumer<ScanReason> onAnalysisModified = (deepReason) -> onInstanceFieldNotComputed(receiverObject, field, fieldValue, deepReason);
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
                Consumer<ScanReason> onAnalysisModified = (deepReason) -> onArrayElementMismatch(elementSnapshot, elementValue, deepReason);
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
            AnalysisFuture<ImageHeapObject> rootTask = imageHeap.getTask(root);
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
            Object object = constantAsObject(bb, value);
            Class<?> objectClass = object.getClass();
            if (objectClass.equals(Class.class)) {
                /* Ensure that java.lang.Class constants are scanned. */
                AnalysisType type = bb.getMetaAccess().lookupJavaType((Class<?>) object);
                ensureTypeScanned(value, type, reason);
            } else {
                /*
                 * Ensure that the Class of any other constants are also scanned. An object replacer
                 * can introduce new types which otherwise could be missed by the verifier. For
                 * example com.oracle.svm.hosted.annotation.AnnotationObjectReplacer creates
                 * annotation proxy types on the fly for constant annotation objects.
                 */
                AnalysisType type = bb.getMetaAccess().lookupJavaType(objectClass);
                ensureTypeScanned(value, bb.getConstantReflectionProvider().asJavaClass(type), type, reason);
            }
        }

        private void ensureTypeScanned(JavaConstant typeConstant, AnalysisType type, ScanReason reason) {
            ensureTypeScanned(null, typeConstant, type, reason);
        }

        private void ensureTypeScanned(JavaConstant object, JavaConstant typeConstant, AnalysisType type, ScanReason reason) {
            AnalysisError.guarantee(type.isReachable(), "The heap snapshot verifier discovered a type not marked as reachable " + type.toJavaName());
            AnalysisFuture<ImageHeapObject> task = imageHeap.getTask(typeConstant);
            /* Make sure the DynamicHub value is scanned. */
            if (task == null) {
                onNoTaskForClassConstant(type, reason);
                scanner.toImageHeapObject(typeConstant, reason, null);
                heapPatched = true;
            } else {
                if (task.isDone()) {
                    JavaConstant snapshot = task.guardedGet().getObject();
                    if (!Objects.equals(snapshot, typeConstant)) {
                        throw error(reason, "Value mismatch for class constant snapshot: %s %n new value: %s %n", snapshot, typeConstant);
                    }
                } else {
                    onTaskForClassConstantNotDone(object, type, reason);
                    task.ensureDone();
                }
            }
        }
    }

    private void onNoTaskForClassConstant(AnalysisType type, ScanReason reason) {
        analysisModified = true;
        if (printAll()) {
            warning(reason, "No snapshot task found for class constant %s %n", type.toJavaName());
        }
    }

    private void onTaskForClassConstantNotDone(JavaConstant object, AnalysisType type, ScanReason reason) {
        analysisModified = true;
        if (printAll()) {
            if (object != null) {
                warning(reason, "Snapshot not yet computed for class %s of object %s %n", type.toJavaName(), object);
            } else {
                warning(reason, "Snapshot not yet computed for class constant %n new value: %s %n", type.toJavaName());
            }
        }
    }

    private void onArrayElementMismatch(JavaConstant elementSnapshot, JavaConstant elementValue, ScanReason reason) {
        analysisModified = true;
        if (printWarning()) {
            analysisWarning(reason, "Value mismatch for array element %n snapshot: %s %n new value: %s %n", elementSnapshot, elementValue);
        }
    }

    private void onStaticFieldMismatch(AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
        analysisModified = true;
        if (printWarning()) {
            analysisWarning(reason, "Value mismatch for static field %s %n snapshot: %s %n new value: %s %n", field, fieldSnapshot, fieldValue);
        }
    }

    private void onStaticFieldNotComputed(AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        error(reason, "Snapshot not yet computed for static field %s %n new value: %s %n", field, fieldValue);
    }

    private void onInstanceFieldMismatch(ImageHeapInstance receiver, AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
        analysisModified = true;
        if (printWarning()) {
            analysisWarning(reason, "Value mismatch for instance field %s of %s %n snapshot: %s %n new value: %s %n",
                            field, receiver.getObject(), fieldSnapshot, fieldValue);
        }
    }

    private void onInstanceFieldNotComputed(ImageHeapInstance receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        analysisModified = true;
        if (printWarning()) {
            analysisWarning(reason, "Snapshot not yet computed for instance field %s of %s %n new value: %s %n",
                            field, receiver.getObject(), fieldValue);
        }
    }

    private static final int INFO = 1;
    private static final int WARNING = 2;
    private static final int ALL = 3;

    private boolean printInfo() {
        return verbosity >= INFO;
    }

    private boolean printWarning() {
        return verbosity >= WARNING;
    }

    private boolean printAll() {
        return verbosity >= ALL;
    }

    private void info(String info) {
        if (printInfo()) {
            System.out.println("INFO: " + info);
        }
    }

    private void warning(ScanReason reason, String format, Object... args) {
        System.out.println("WARNING: " + message(reason, format, "Object was reached by", args));
    }

    private void analysisWarning(ScanReason reason, String format, Object... args) {
        System.out.println("WARNING: " + message(reason, format, "This leads to an analysis state change when", args));
    }

    private RuntimeException error(ScanReason reason, String format, Object... args) {
        throw AnalysisError.shouldNotReachHere(message(reason, format, args));
    }

    private String message(ScanReason reason, String format, Object... args) {
        return message(reason, format, "", args);
    }

    private String message(ScanReason reason, String format, String backtraceHeader, Object... args) {
        String message = format(format, args);
        StringBuilder objectBacktrace = new StringBuilder();
        ObjectScanner.buildObjectBacktrace(bb, reason, objectBacktrace, backtraceHeader);
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
