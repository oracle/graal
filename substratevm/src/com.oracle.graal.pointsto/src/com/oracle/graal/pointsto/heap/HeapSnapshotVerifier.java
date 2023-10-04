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

import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.options.OptionKey;
import jdk.compiler.graal.options.OptionType;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ReusableSet;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.util.LogUtils;

import jdk.vm.ci.meta.JavaConstant;

public class HeapSnapshotVerifier {

    static class Options {
        @Option(help = "Control heap verifier verbosity level: 0 - quiet, 1 - info, 2 - warning, 3 - all.", type = OptionType.Expert)//
        public static final OptionKey<Integer> HeapVerifierVerbosity = new OptionKey<>(0);
    }

    protected final BigBang bb;
    protected final ImageHeapScanner scanner;
    protected final ImageHeap imageHeap;

    protected ReusableSet scannedObjects;
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

    public boolean checkHeapSnapshot(UniverseMetaAccess metaAccess, ForkJoinPool threadPool, String stage) {
        CompletionExecutor executor = new CompletionExecutor(bb, threadPool);
        executor.init();
        return checkHeapSnapshot(metaAccess, executor, stage, false);
    }

    /**
     * Heap verification does a complete scan from roots (static fields and embedded constant) and
     * compares the object graph against the shadow heap. If any new reachable objects or primitive
     * values are found then the verifier automatically patches the shadow heap. If this is during
     * analysis then the heap scanner will also notify the analysis of the new objects.
     */
    public boolean checkHeapSnapshot(UniverseMetaAccess metaAccess, CompletionExecutor executor, String phase, boolean forAnalysis) {
        info("Verifying the heap snapshot %s%s ...", phase, (forAnalysis ? ", iteration " + iterations : ""));
        analysisModified = false;
        heapPatched = false;
        int reachableTypesBefore = bb.getUniverse().getReachableTypes();
        iterations++;
        scannedObjects.reset();
        ObjectScanner objectScanner = installObjectScanner(metaAccess, executor);
        executor.start();
        scanTypes(objectScanner);
        objectScanner.scanBootImageHeapRoots();
        try {
            executor.complete();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();
        if (heapPatched) {
            info("Heap verification patched the heap snapshot.");
        } else {
            info("Heap verification didn't find any heap snapshot modifications.");
        }
        int verificationReachableTypes = bb.getUniverse().getReachableTypes() - reachableTypesBefore;
        if (forAnalysis) {
            if (verificationReachableTypes > 0) {
                info("Heap verification made %s new types reachable.", verificationReachableTypes);
            } else {
                info("Heap verification didn't make any new types reachable.");
            }
            if (analysisModified) {
                info("Heap verification modified the analysis state. Executing an additional analysis iteration.");
            } else {
                info("Heap verification didn't modify the analysis state. Heap state stabilized after %s iterations.", iterations);
            }
        } else if (analysisModified || verificationReachableTypes > 0) {
            String error = analysisModified ? "modified the analysis state" : "";
            error += verificationReachableTypes > 0 ? (analysisModified ? " and " : "") + "made " + verificationReachableTypes + " new types reachable" : "";
            throw AnalysisError.shouldNotReachHere("Heap verification " + error + ". This is illegal at this stage.");
        }
        return analysisModified || verificationReachableTypes > 0;
    }

    protected ObjectScanner installObjectScanner(@SuppressWarnings("unused") UniverseMetaAccess metaAccess, CompletionExecutor executor) {
        return new ObjectScanner(bb, executor, scannedObjects, new ScanningObserver());
    }

    protected void scanTypes(@SuppressWarnings("unused") ObjectScanner objectScanner) {
    }

    public void cleanupAfterAnalysis() {
    }

    protected final class ScanningObserver implements ObjectScanningObserver {

        public ScanningObserver() {
        }

        @Override
        public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            boolean result = false;
            ObjectScanningObserver scanningObserver = scanner.getScanningObserver();
            if (scanningObserver != null) {
                result = scanningObserver.forRelocatedPointerFieldValue(receiver, field, fieldValue, reason);
                if (result) {
                    analysisModified = true;
                }
            }
            return result;
        }

        @Override
        public boolean forPrimitiveFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            return verifyFieldValue(receiver, field, fieldValue, reason);
        }

        @Override
        public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
            boolean result = false;
            ObjectScanningObserver scanningObserver = scanner.getScanningObserver();
            if (scanningObserver != null) {
                result = scanningObserver.forNullFieldValue(receiver, field, reason);
                if (result) {
                    analysisModified = true;
                }
            }
            return result;
        }

        @Override
        public boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            return verifyFieldValue(receiver, field, fieldValue, reason);
        }

        private boolean verifyFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            /*
             * We don't care if a field in the shadow heap was not yet read, i.e., the future is not
             * yet materialized. This can happen with lazy fields that become available but may have
             * not yet been consumed. We simply execute the future, then compare the produced value.
             */
            if (field.isStatic()) {
                TypeData typeData = field.getDeclaringClass().getOrComputeData();
                JavaConstant fieldSnapshot = typeData.readFieldValue(field);
                verifyStaticFieldValue(typeData, field, maybeUnwrapSnapshot(fieldSnapshot, fieldValue instanceof ImageHeapConstant), fieldValue, reason);
            } else {
                ImageHeapInstance receiverObject = (ImageHeapInstance) getReceiverObject(receiver, reason);
                JavaConstant fieldSnapshot = receiverObject.readFieldValue(field);
                verifyInstanceFieldValue(field, receiver, receiverObject, maybeUnwrapSnapshot(fieldSnapshot, fieldValue instanceof ImageHeapConstant), fieldValue, reason);
            }
            return false;
        }

        private void verifyStaticFieldValue(TypeData typeData, AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
            if (!Objects.equals(fieldSnapshot, fieldValue)) {
                String format = "Value mismatch for static field %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, field, fieldSnapshot, fieldValue);
                scanner.patchStaticField(typeData, field, fieldValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            }
        }

        private void verifyInstanceFieldValue(AnalysisField field, JavaConstant receiver, ImageHeapInstance receiverObject, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
            if (!Objects.equals(fieldSnapshot, fieldValue)) {
                String format = "Value mismatch for instance field %s of %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, field, asString(receiver), fieldSnapshot, fieldValue);
                scanner.patchInstanceField(receiverObject, field, fieldValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            }
        }

        private Consumer<ScanReason> analysisModified(ScanReason reason, String format, Object... args) {
            if (printAll()) {
                warning(reason, format, args);
                return (deepReason) -> analysisModified = true;
            } else {
                return (deepReason) -> {
                    analysisModified = true;
                    if (printWarning()) {
                        analysisWarning(deepReason, format, args);
                    }
                };
            }
        }

        @Override
        public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ScanReason reason) {
            boolean result = false;
            ObjectScanningObserver scanningObserver = scanner.getScanningObserver();
            if (scanningObserver != null) {
                result = scanningObserver.forNullArrayElement(array, arrayType, elementIndex, reason);
                if (result) {
                    analysisModified = true;
                }
            }
            return result;
        }

        @Override
        public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, AnalysisType elementType, int index, ScanReason reason) {
            /*
             * We don't care if an array element in the shadow heap was not yet read, i.e., the
             * future is not yet materialized. This can happen with values originating from lazy
             * fields that become available but may have not yet been consumed. We simply execute
             * the future, then compare the produced value.
             */
            ImageHeapObjectArray arrayObject = (ImageHeapObjectArray) getReceiverObject(array, reason);
            JavaConstant elementSnapshot = arrayObject.readElementValue(index);
            verifyArrayElementValue(elementValue, index, reason, array, arrayObject, elementSnapshot);
            return false;
        }

        private void verifyArrayElementValue(JavaConstant elementValue, int index, ScanReason reason, JavaConstant array, ImageHeapObjectArray arrayObject, JavaConstant elementSnapshot) {
            if (!Objects.equals(maybeUnwrapSnapshot(elementSnapshot, elementValue instanceof ImageHeapConstant), elementValue)) {
                String format = "Value mismatch for array element at index %s of %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, index, asString(array), elementSnapshot, elementValue);
                scanner.patchArrayElement(arrayObject, index, elementValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private ImageHeapConstant getReceiverObject(JavaConstant constant, ScanReason reason) {
            if (constant instanceof ImageHeapConstant) {
                /* This is a simulated constant. */
                return (ImageHeapConstant) constant;
            }
            Object task = imageHeap.getSnapshot(constant);
            if (task == null) {
                throw error(reason, "Task is null for constant %s.", constant);
            } else if (task instanceof ImageHeapConstant) {
                return (ImageHeapConstant) task;
            } else {
                AnalysisFuture<ImageHeapConstant> future = ((AnalysisFuture<ImageHeapConstant>) task);
                if (future.isDone()) {
                    return future.guardedGet();
                } else {
                    throw error(reason, "Task not yet executed for constant %s.", constant);
                }
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void forEmbeddedRoot(JavaConstant root, ScanReason reason) {
            Object rootTask = imageHeap.getSnapshot(root);
            if (rootTask == null) {
                throw error(reason, "No snapshot task found for embedded root %s %n", root);
            } else if (rootTask instanceof ImageHeapConstant snapshot) {
                verifyEmbeddedRoot(maybeUnwrapSnapshot(snapshot, root instanceof ImageHeapConstant), root, reason);
            } else {
                AnalysisFuture<ImageHeapConstant> future = (AnalysisFuture<ImageHeapConstant>) rootTask;
                if (future.isDone()) {
                    ImageHeapConstant snapshot = future.guardedGet();
                    verifyEmbeddedRoot(maybeUnwrapSnapshot(snapshot, root instanceof ImageHeapConstant), root, reason);
                } else {
                    throw error(reason, "Snapshot not yet computed for embedded root %n new value: %s %n", root);
                }
            }
        }

        /**
         * Since embedded constants or constants reachable when scanning from roots can also be
         * ImageHeapObject that are not backed by a hosted object, we need to make sure that we
         * compare it with the correct representation of the snapshot, i.e., without unwrapping it.
         */
        private JavaConstant maybeUnwrapSnapshot(JavaConstant snapshot, boolean asImageHeapObject) {
            if (snapshot instanceof ImageHeapConstant) {
                return asImageHeapObject ? snapshot : ((ImageHeapConstant) snapshot).getHostedObject();
            }
            return snapshot;
        }

        private void verifyEmbeddedRoot(JavaConstant rootSnapshot, JavaConstant root, ScanReason reason) {
            if (!Objects.equals(rootSnapshot, root)) {
                throw error(reason, "Value mismatch for embedded root %n snapshot: %s %n new value: %s %n", rootSnapshot, root);
            }
        }

        @Override
        public void forScannedConstant(JavaConstant value, ScanReason reason) {
            if (bb.getMetaAccess().isInstanceOf(value, Class.class)) {
                /* Ensure that java.lang.Class constants are scanned. */
                AnalysisType type = (AnalysisType) bb.getConstantReflectionProvider().asJavaType(value);
                ensureTypeScanned(value, type, reason);
            } else {
                /*
                 * Ensure that the Class of any other constants are also scanned. An object replacer
                 * can introduce new types which otherwise could be missed by the verifier. For
                 * example com.oracle.svm.hosted.annotation.AnnotationObjectReplacer creates
                 * annotation proxy types on the fly for constant annotation objects.
                 */
                AnalysisType type = bb.getMetaAccess().lookupJavaType(value);
                ensureTypeScanned(value, bb.getConstantReflectionProvider().asJavaClass(type), type, reason);
            }
        }

        private void ensureTypeScanned(JavaConstant typeConstant, AnalysisType type, ScanReason reason) {
            ensureTypeScanned(null, typeConstant, type, reason);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void ensureTypeScanned(JavaConstant value, JavaConstant typeConstant, AnalysisType type, ScanReason reason) {
            if (!type.isReachable()) {
                error(reason, "The heap snapshot verifier discovered a type not marked as reachable: %s", type);
            }
            Object task = imageHeap.getSnapshot(typeConstant);
            /* Make sure the DynamicHub value is scanned. */
            if (task == null) {
                onNoTaskForClassConstant(type, reason);
                scanner.toImageHeapObject(typeConstant, reason);
                heapPatched = true;
            } else if (task instanceof ImageHeapConstant snapshot) {
                verifyTypeConstant(maybeUnwrapSnapshot(snapshot, typeConstant instanceof ImageHeapConstant), typeConstant, reason);
            } else {
                AnalysisFuture<ImageHeapConstant> future = ((AnalysisFuture<ImageHeapConstant>) task);
                if (future.isDone()) {
                    JavaConstant snapshot = maybeUnwrapSnapshot(future.guardedGet(), typeConstant instanceof ImageHeapConstant);
                    verifyTypeConstant(snapshot, typeConstant, reason);
                } else {
                    onTaskForClassConstantNotDone(value, type, reason);
                    future.ensureDone();
                }
            }
        }

        private void verifyTypeConstant(JavaConstant snapshot, JavaConstant typeConstant, ScanReason reason) {
            if (!Objects.equals(snapshot, typeConstant)) {
                throw error(reason, "Value mismatch for class constant%n snapshot:  %s %n new value: %s %n", snapshot, typeConstant);
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

    private void info(String format, Object... args) {
        if (printInfo()) {
            LogUtils.info(String.format(format, args));
        }
    }

    private void warning(ScanReason reason, String format, Object... args) {
        LogUtils.warning(message(reason, format, "Value was reached by", args));
    }

    private void analysisWarning(ScanReason reason, String format, Object... args) {
        LogUtils.warning(message(reason, format, "This leads to an analysis state change when", args));
    }

    private RuntimeException error(ScanReason reason, String format, Object... args) {
        throw AnalysisError.shouldNotReachHere(message(reason, format, args));
    }

    private String message(ScanReason reason, String format, Object... args) {
        return message(reason, format, "", args);
    }

    private String message(ScanReason reason, String format, String backtraceHeader, Object... args) {
        String message = format(bb, format, args);
        StringBuilder objectBacktrace = new StringBuilder();
        ObjectScanner.buildObjectBacktrace(bb, reason, objectBacktrace, backtraceHeader);
        message += objectBacktrace;
        return message;
    }

    private String asString(JavaConstant array) {
        return ObjectScanner.asString(bb, array, false);
    }

    public static String format(BigBang bb, String msg, Object... args) {
        if (args != null) {
            for (int i = 0; i < args.length; ++i) {
                if (args[i] instanceof JavaConstant) {
                    args[i] = ObjectScanner.asString(bb, (JavaConstant) args[i]);
                } else if (args[i] instanceof AnalysisField) {
                    args[i] = ((AnalysisField) args[i]).format("%H.%n");
                }
            }
        }
        return String.format(msg, args);
    }

}
