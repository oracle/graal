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
import static com.oracle.graal.pointsto.ObjectScanner.constantAsObject;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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

import jdk.graal.compiler.core.common.type.CompressibleConstant;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

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

    public boolean checkHeapSnapshot(DebugContext debug, UniverseMetaAccess metaAccess, String stage, Map<Constant, Object> embeddedConstants) {
        CompletionExecutor executor = new CompletionExecutor(debug, bb);
        executor.init();
        return checkHeapSnapshot(metaAccess, executor, stage, false, embeddedConstants);
    }

    public boolean checkHeapSnapshot(UniverseMetaAccess metaAccess, CompletionExecutor executor, String phase, boolean forAnalysis, Map<Constant, Object> embeddedConstants) {
        return checkHeapSnapshot(metaAccess, executor, phase, forAnalysis, embeddedConstants, false);
    }

    /**
     * Heap verification does a complete scan from roots (static fields and embedded constant) and
     * compares the object graph against the shadow heap. If any new reachable objects or primitive
     * values are found then the verifier automatically patches the shadow heap. If this is during
     * analysis then the heap scanner will also notify the analysis of the new objects.
     */
    protected boolean checkHeapSnapshot(UniverseMetaAccess metaAccess, CompletionExecutor executor, String phase, boolean forAnalysis, Map<Constant, Object> embeddedConstants,
                    boolean skipReachableCheck) {
        info("Verifying the heap snapshot %s%s ...", phase, (forAnalysis ? ", iteration " + iterations : ""));
        analysisModified = false;
        heapPatched = false;
        int reachableTypesBefore = bb.getUniverse().getReachableTypes();
        iterations++;
        scannedObjects.reset();
        ObjectScanner objectScanner = installObjectScanner(metaAccess, executor, skipReachableCheck);
        executor.start();
        scanTypes(objectScanner);
        objectScanner.scanBootImageHeapRoots(embeddedConstants);
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

    protected ObjectScanner installObjectScanner(@SuppressWarnings("unused") UniverseMetaAccess metaAccess, CompletionExecutor executor, boolean skipReachableCheck) {
        return new ObjectScanner(bb, executor, scannedObjects, new ScanningObserver(skipReachableCheck));
    }

    protected void scanTypes(@SuppressWarnings("unused") ObjectScanner objectScanner) {
    }

    public void cleanupAfterAnalysis() {
    }

    protected final class ScanningObserver implements ObjectScanningObserver {

        private final boolean skipReachableCheck;

        public ScanningObserver(boolean skipReachableCheck) {
            this.skipReachableCheck = skipReachableCheck;
        }

        @Override
        public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            return verifyFieldValue(receiver, field, fieldValue, reason);
        }

        @Override
        public boolean forPrimitiveFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
            return verifyFieldValue(receiver, field, fieldValue, reason);
        }

        @Override
        public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
            return verifyFieldValue(receiver, field, JavaConstant.NULL_POINTER, reason);
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
                verifyStaticFieldValue(typeData, field, fieldSnapshot, fieldValue, reason);
            } else {
                ImageHeapInstance receiverObject = (ImageHeapInstance) getSnapshot(receiver, reason);
                if (receiverObject == null || (receiverObject.isInBaseLayer() && !bb.getUniverse().getImageLayerLoader().getRelinkedFields(receiverObject.getType()).contains(field.getPosition()))) {
                    return false;
                }
                JavaConstant fieldSnapshot = receiverObject.readFieldValue(field);
                verifyInstanceFieldValue(field, receiver, receiverObject, fieldSnapshot, fieldValue, reason);
            }
            return false;
        }

        private void verifyStaticFieldValue(TypeData typeData, AnalysisField field, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
            JavaConstant result = fieldSnapshot;
            JavaConstant unwrappedSnapshot = maybeUnwrapSnapshot(fieldSnapshot, fieldValue instanceof ImageHeapConstant);
            if (!Objects.equals(unwrappedSnapshot, fieldValue)) {
                String format = "Value mismatch for static field %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, field, unwrappedSnapshot, fieldValue);
                result = scanner.patchStaticField(typeData, field, fieldValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            } else if (patchPrimitiveArrayValue(bb, fieldSnapshot, fieldValue)) {
                heapPatched = true;
            }
            scanner.ensureReaderInstalled(result);
        }

        private void verifyInstanceFieldValue(AnalysisField field, JavaConstant receiver, ImageHeapInstance receiverObject, JavaConstant fieldSnapshot, JavaConstant fieldValue, ScanReason reason) {
            if (fieldSnapshot instanceof ImageHeapConstant ihc && ihc.isInBaseLayer() && ihc.getHostedObject() == null) {
                /*
                 * We cannot verify a base layer constant which doesn't have a backing hosted
                 * object. Since the hosted object is missing the constant would be replaced with
                 * the new hosted object reachable from the field, which would be wrong.
                 */
                throw AnalysisError.shouldNotReachHere("Trying to verify a constant from the base layer that was not relinked.");
            }
            JavaConstant result = fieldSnapshot;
            JavaConstant unwrappedSnapshot = maybeUnwrapSnapshot(fieldSnapshot, fieldValue instanceof ImageHeapConstant);
            if (!Objects.equals(unwrappedSnapshot, fieldValue)) {
                String format = "Value mismatch for instance field %s of %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, field, asString(receiver), unwrappedSnapshot, fieldValue);
                result = scanner.patchInstanceField(receiverObject, field, fieldValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            } else if (patchPrimitiveArrayValue(bb, fieldSnapshot, fieldValue)) {
                heapPatched = true;
            }
            scanner.ensureReaderInstalled(result);
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
        public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int index, ScanReason reason) {
            return verifyArrayElementValue(JavaConstant.NULL_POINTER, index, reason, array);
        }

        @Override
        public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementValue, AnalysisType elementType, int index, ScanReason reason) {
            return verifyArrayElementValue(elementValue, index, reason, array);
        }

        private boolean verifyArrayElementValue(JavaConstant elementValue, int index, ScanReason reason, JavaConstant array) {
            ImageHeapObjectArray arrayObject = (ImageHeapObjectArray) getSnapshot(array, reason);
            if (arrayObject == null) {
                return false;
            }
            /*
             * We don't care if an array element in the shadow heap was not yet read, i.e., the
             * future is not yet materialized. This can happen with values originating from lazy
             * fields that become available but may have not yet been consumed. We simply execute
             * the future, then compare the produced value.
             */
            JavaConstant elementSnapshot = arrayObject.readElementValue(index);
            if (elementSnapshot instanceof ImageHeapConstant ihc && ihc.isInBaseLayer() && ihc.getHostedObject() == null) {
                /*
                 * We cannot verify a base layer constant which doesn't have a backing hosted
                 * object. Since the hosted object is missing the constant would be replaced with
                 * the new hosted object reachable from the field, which would be wrong.
                 */
                throw AnalysisError.shouldNotReachHere("Trying to verify a constant from the base layer that was not relinked.");
            }
            JavaConstant result = elementSnapshot;
            if (!Objects.equals(maybeUnwrapSnapshot(elementSnapshot, elementValue instanceof ImageHeapConstant), elementValue)) {
                String format = "Value mismatch for array element at index %s of %s %n snapshot:  %s %n new value: %s %n";
                Consumer<ScanReason> onAnalysisModified = analysisModified(reason, format, index, asString(array), elementSnapshot, elementValue);
                result = scanner.patchArrayElement(arrayObject, index, elementValue, reason, onAnalysisModified).ensureDone();
                heapPatched = true;
            } else if (patchPrimitiveArrayValue(bb, elementSnapshot, elementValue)) {
                heapPatched = true;
            }
            scanner.ensureReaderInstalled(result);
            return false;
        }

        /**
         * {@link ImageHeapPrimitiveArray} clones the original primitive array and keeps a reference
         * to the original hosted object. The original hosted array can change value, so we use a
         * deep equals to check element equality. This method assumes and checks that the originally
         * shadowed object did not change since if that happens then the entire constant should have
         * been patched instead.
         */
        public static boolean patchPrimitiveArrayValue(BigBang bb, JavaConstant snapshot, JavaConstant newValue) {
            if (snapshot.isNull()) {
                AnalysisError.guarantee(newValue.isNull());
                return false;
            }
            if (isPrimitiveArrayConstant(bb, snapshot)) {
                AnalysisError.guarantee(isPrimitiveArrayConstant(bb, newValue));
                Object snapshotArray = ((ImageHeapPrimitiveArray) snapshot).getArray();
                Object newValueArray = constantAsObject(bb, newValue);
                if (!Objects.deepEquals(snapshotArray, newValueArray)) {
                    /* Guarantee that the shadowed constant and the hosted constant are the same. */
                    AnalysisError.guarantee(((ImageHeapPrimitiveArray) snapshot).getHostedObject().equals(newValue));
                    Integer length = bb.getUniverse().getHostedValuesProvider().readArrayLength(newValue);
                    /* Since the shadowed constant didn't change, the length should match. */
                    System.arraycopy(newValueArray, 0, snapshotArray, 0, length);
                    return true;
                }
            }
            return false;
        }

        static boolean isPrimitiveArrayConstant(BigBang bb, JavaConstant snapshot) {
            if (snapshot.getJavaKind() == JavaKind.Object) {
                AnalysisType type = bb.getMetaAccess().lookupJavaType(snapshot);
                return type.isArray() && type.getComponentType().getJavaKind() != JavaKind.Object;
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private ImageHeapConstant getSnapshot(JavaConstant constant, ScanReason reason) {
            ImageHeapConstant result;
            if (constant instanceof ImageHeapConstant) {
                /* This is a simulated constant. */
                result = (ImageHeapConstant) constant;
            } else {
                Object task = imageHeap.getSnapshot(constant);
                if (task == null && bb.getUniverse().hostVM().buildingExtensionLayer() && bb.getUniverse().getImageLayerLoader().hasValueForConstant(constant)) {
                    /* The constant might not have been accessed in the extension image yet */
                    task = bb.getUniverse().getImageLayerLoader().getValueForConstant(constant);
                }
                if (task == null && bb.getUniverse().hostVM().buildingExtensionLayer()) {
                    /*
                     * This does not distinguish between base and extension layer constants at the
                     * moment. Doing so would require some refactoring to determine earlier if the
                     * constant is from the base layer.
                     */
                    return null;
                }
                if (task == null) {
                    throw error(reason, "Task is null for constant %s.", constant);
                } else if (task instanceof ImageHeapConstant) {
                    result = (ImageHeapConstant) task;
                } else {
                    AnalysisFuture<ImageHeapConstant> future = ((AnalysisFuture<ImageHeapConstant>) task);
                    if (future.isDone()) {
                        result = future.guardedGet();
                    } else {
                        throw error(reason, "Task not yet executed for constant %s.", constant);
                    }
                }
            }
            if (!result.isReaderInstalled()) {
                /* This can be a constant discovered after compilation */
                result.ensureReaderInstalled();
            }
            return result;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void forEmbeddedRoot(JavaConstant root, ScanReason reason) {
            Object rootTask = imageHeap.getSnapshot(root);
            if (rootTask == null) {
                throw error(reason, "No snapshot task found for embedded root %s %n", root);
            } else if (rootTask instanceof ImageHeapConstant snapshot) {
                verifyEmbeddedRoot(maybeUnwrapSnapshot(snapshot, root instanceof ImageHeapConstant), CompressibleConstant.uncompress(root), reason);
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
        public static JavaConstant maybeUnwrapSnapshot(JavaConstant snapshot, boolean asImageHeapObject) {
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
            if (!skipReachableCheck && !type.isReachable()) {
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
        LogUtils.warning(formatReason(bb, reason, format, "Value was reached by", args));
    }

    private void analysisWarning(ScanReason reason, String format, Object... args) {
        LogUtils.warning(formatReason(bb, reason, format, "This leads to an analysis state change when", args));
    }

    private RuntimeException error(ScanReason reason, String format, Object... args) {
        throw AnalysisError.shouldNotReachHere(formatReason(bb, reason, format, args));
    }

    public static String formatReason(BigBang bb, ScanReason reason, String format, Object... args) {
        return formatReason(bb, reason, format, "", args);
    }

    private static String formatReason(BigBang bb, ScanReason reason, String format, String backtraceHeader, Object... args) {
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
