/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.gc.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature.BeforeCompilationAccess;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfoOffsets;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.thread.VMThreadFeature;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.graal.compiler.word.Word;

/**
 * For performance reasons, the GC-related C++ code accesses certain static/thread-local/instance
 * fields directly. This class computes the offsets of those fields and writes them to a byte array
 * that is then passed to the C++ code upon initialization. It also ensures that the static analysis
 * considers all those fields as reachable, even if they are only accessed on the C++ side.
 */
public class NativeGCAccessedFields {
    public static final BooleanSupplier ALWAYS = new Always();
    public static final BooleanSupplier USE_PERF_DATA = new UsePerfData();
    public static final BooleanSupplier CLOSED_TYPE_WORLD_HUB_LAYOUT = SubstrateOptions::useClosedTypeWorldHubLayout;
    public static final BooleanSupplier OPEN_TYPE_WORLD_HUB_LAYOUT = new OpenTypeWorldHubLayout();

    /** Marks the given fields as accessed and their classes as used. */
    public static void markAsAccessed(BeforeAnalysisAccessImpl access, AccessedClass[] accessedClasses) {
        for (AccessedClass accessedClass : accessedClasses) {
            markAsAccessed(access, accessedClass.clazz, accessedClass.fields);
        }
    }

    /** Writes all offsets as integer values (4 bytes per value) into a byte array. */
    public static byte[] writeOffsets(BeforeCompilationAccess access, int markWordOffset, FastThreadLocalBytes<Word> nativeJavaThreadTL, FastThreadLocalObject<Object> podReferenceMapTL,
                    AccessedClass[] accessedClasses) {
        UnsafeArrayTypeWriter buffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        writeObjectLayoutOffsets(buffer, markWordOffset);
        writeThreadLocalOffsets(buffer, nativeJavaThreadTL, podReferenceMapTL);
        writeCodeInfoOffsets(buffer);
        for (AccessedClass accessedClass : accessedClasses) {
            writeFieldOffsets(access, buffer, accessedClass.clazz, accessedClass.fields);
        }

        return buffer.toArray();
    }

    private static void markAsAccessed(BeforeAnalysisAccessImpl access, Class<?> clazz, AccessedField[] accessedFields) {
        access.registerAsUsed(clazz);

        Field[] fields = clazz.getDeclaredFields();
        for (AccessedField accessedField : accessedFields) {
            if (accessedField.shouldInclude()) {
                boolean found = false;
                for (Field field : fields) {
                    if (accessedField.matches(field)) {
                        AnalysisField analysisField = access.getMetaAccess().lookupJavaField(field);
                        accessedField.registerAsAccessed(access, analysisField);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw VMError.shouldNotReachHere("Could not find field " + ClassUtil.getUnqualifiedName(clazz) + "." + accessedField.name);
                }
            }
        }
    }

    private static void writeObjectLayoutOffsets(UnsafeArrayTypeWriter buffer, int markWordOffset) {
        ObjectLayout objectLayout = ImageSingletons.lookup(ObjectLayout.class);
        assert objectLayout.getFirstFieldOffset() <= objectLayout.getArrayLengthOffset();
        int objBase = objectLayout.getFirstFieldOffset();
        int minObjectSize = objectLayout.alignUp(objBase);

        // object layout
        buffer.putS4(markWordOffset);
        buffer.putS4(objectLayout.getHubOffset());
        buffer.putS4(objectLayout.getAlignment());
        buffer.putS4(minObjectSize);
        buffer.putS4(objBase);
        buffer.putS4(objectLayout.getArrayLengthOffset());

        // dynamic hub
        buffer.putS4(SubstrateOptions.useClosedTypeWorldHubLayout() ? DynamicHubLayout.singleton().getClosedTypeWorldTypeCheckSlotsOffset() : -1);
    }

    private static void writeThreadLocalOffsets(UnsafeArrayTypeWriter buffer, FastThreadLocalBytes<Word> nativeJavaThreadTL, FastThreadLocalObject<Object> podReferenceMapTL) {
        VMThreadFeature vmThreadMtFeature = ImageSingletons.lookup(VMThreadFeature.class);

        buffer.putS4(vmThreadMtFeature.offsetOf(VMThreads.nextTL));
        buffer.putS4(vmThreadMtFeature.offsetOf(nativeJavaThreadTL));
        buffer.putS4(vmThreadMtFeature.offsetOf(StatusSupport.statusTL));
        buffer.putS4(vmThreadMtFeature.offsetOf(podReferenceMapTL));
    }

    private static void writeCodeInfoOffsets(UnsafeArrayTypeWriter buffer) {
        buffer.putS4(CodeInfoOffsets.objectFields());
        buffer.putS4(CodeInfoOffsets.state());
        buffer.putS4(CodeInfoOffsets.gcData());
        buffer.putS4(CodeInfoOffsets.objectConstants());
        buffer.putS4(CodeInfoOffsets.deoptimizationObjectConstants());
        buffer.putS4(CodeInfoOffsets.stackReferenceMapEncoding());
        buffer.putS4(CodeInfoOffsets.codeStart());
        buffer.putS4(CodeInfoOffsets.codeConstantsReferenceMapEncoding());
        buffer.putS4(CodeInfoOffsets.codeConstantsReferenceMapIndex());
        buffer.putS4(CodeInfoOffsets.areAllObjectsInImageHeap());
    }

    private static void writeFieldOffsets(BeforeCompilationAccess access, UnsafeArrayTypeWriter buffer, Class<?> clazz, AccessedField[] accessedFields) {
        Field[] declaredFields = clazz.getDeclaredFields();
        for (AccessedField accessedField : accessedFields) {
            if (accessedField.shouldInclude()) {
                boolean found = false;
                for (Field field : declaredFields) {
                    if (accessedField.matches(field)) {
                        buffer.putS4(access.objectFieldOffset(field));
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw VMError.shouldNotReachHere("Could not find field " + ClassUtil.getUnqualifiedName(clazz) + "." + accessedField.name);
                }
            } else {
                // Add a dummy value so that the size of the structure is always the same.
                buffer.putS4(-1);
            }
        }
    }

    public enum FieldAccessKind {
        READ,
        READ_WRITE
    }

    public static final class AccessedClass {
        Class<?> clazz;
        AccessedField[] fields;

        public AccessedClass(Class<?> clazz, AccessedField... fields) {
            this.clazz = clazz;
            this.fields = fields;
        }
    }

    public abstract static class AccessedField {
        final String name;
        final FieldAccessKind accessKind;
        final BooleanSupplier include;

        AccessedField(String name, FieldAccessKind accessKind, BooleanSupplier include) {
            this.name = name;
            this.accessKind = accessKind;
            this.include = include;
        }

        public abstract boolean matches(Field field);

        public boolean shouldInclude() {
            return include.getAsBoolean();
        }

        void registerAsAccessed(BeforeAnalysisAccessImpl access, AnalysisField field) {
            if (accessKind == FieldAccessKind.READ) {
                access.registerAsRead(field, "it is read by the GC");
            } else {
                assert accessKind == FieldAccessKind.READ_WRITE;
                access.registerAsAccessed(field, "it is accessed by the GC");
            }
        }
    }

    public static final class InstanceField extends AccessedField {
        public InstanceField(String name, FieldAccessKind accessKind) {
            this(name, accessKind, ALWAYS);
        }

        public InstanceField(String name, FieldAccessKind accessKind, BooleanSupplier include) {
            super(name, accessKind, include);
        }

        @Override
        public boolean matches(Field field) {
            return !Modifier.isStatic(field.getModifiers()) && name.equals(field.getName());
        }
    }

    public static final class StaticField extends AccessedField {
        public StaticField(String name, FieldAccessKind accessKind) {
            this(name, accessKind, ALWAYS);
        }

        StaticField(String name, FieldAccessKind accessKind, BooleanSupplier include) {
            super(name, accessKind, include);
        }

        @Override
        public boolean matches(Field field) {
            return Modifier.isStatic(field.getModifiers()) && name.equals(field.getName());
        }

        @Override
        public void registerAsAccessed(BeforeAnalysisAccessImpl access, AnalysisField field) {
            /*
             * Regardless of the field access kind, we always mark the field as accessed. This is
             * necessary to ensure that memory gets reserved for the static field as other SVM code
             * assumes that read-only static fields can always be constant-folded.
             */
            access.registerAsAccessed(field, "it is accessed by the GC");
        }
    }

    public static final class Always implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return true;
        }
    }

    public static final class UsePerfData implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(PerfManager.class);
        }
    }

    public static final class ContinuationsSupported implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ContinuationSupport.isSupported();
        }
    }

    public static final class OpenTypeWorldHubLayout implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !SubstrateOptions.useClosedTypeWorldHubLayout();
        }
    }
}
