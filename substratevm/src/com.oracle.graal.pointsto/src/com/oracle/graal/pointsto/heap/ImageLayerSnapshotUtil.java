/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.graal.compiler.util.ObjectCopierInputStream;
import jdk.graal.compiler.util.ObjectCopierOutputStream;

public class ImageLayerSnapshotUtil {
    public static final String FILE_NAME_PREFIX = "layer-snapshot-";
    public static final String FILE_EXTENSION = ".lsb";
    public static final String GRAPHS_FILE_NAME_PREFIX = "layer-snapshot-graphs-";
    public static final String GRAPHS_FILE_EXTENSION = ".big";

    public static final String CONSTRUCTOR_NAME = "<init>";
    public static final String CLASS_INIT_NAME = "<clinit>";

    public static final String PERSISTED = "persisted";

    public static final int UNDEFINED_CONSTANT_ID = -1;
    public static final int UNDEFINED_FIELD_INDEX = -1;

    private static final String TRACKED_REASON = "reachable from a graph";

    protected final List<Field> externalValueFields;
    /** This needs to be initialized after analysis, as some fields are not available before. */
    protected Map<Object, Field> externalValues;

    public ImageLayerSnapshotUtil(boolean computeExternalValues) {
        if (computeExternalValues) {
            try {
                this.externalValueFields = ObjectCopier.getExternalValueFields();
            } catch (IOException e) {
                throw AnalysisError.shouldNotReachHere("Unexpected exception when creating external value fields list", e);
            }
        } else {
            this.externalValueFields = List.of();
        }
    }

    /**
     * Compute and cache the final {@code externalValues} map in
     * {@link ImageLayerSnapshotUtil#externalValues} to avoid computing it for each graph.
     * <p>
     * A single {@code ObjectCopier.Encoder} instance could alternatively be used for all graphs,
     * but it would then be impossible to process multiple graphs concurrently.
     */
    public void initializeExternalValues() {
        assert externalValues == null : "The external values should be computed only once.";
        externalValues = ObjectCopier.Encoder.gatherExternalValues(externalValueFields);
    }

    public static String snapshotFileName(String imageName) {
        return FILE_NAME_PREFIX + imageName + FILE_EXTENSION;
    }

    public static String snapshotGraphsFileName(String imageName) {
        return GRAPHS_FILE_NAME_PREFIX + imageName + GRAPHS_FILE_EXTENSION;
    }

    public String getTypeDescriptor(AnalysisType type) {
        String javaName = type.toJavaName(true);
        return addModuleName(javaName, type.getJavaClass().getModule().getName());
    }

    public String getMethodDescriptor(AnalysisMethod method) {
        AnalysisType declaringClass = method.getDeclaringClass();
        Executable originalMethod = OriginalMethodProvider.getJavaMethod(method);
        String moduleName = declaringClass.getJavaClass().getModule().getName();
        if (originalMethod != null) {
            return addModuleName(originalMethod.toString(), moduleName);
        }
        return addModuleName(getQualifiedName(method), moduleName);
    }

    protected static String addModuleName(String elementName, String moduleName) {
        return moduleName + ":" + elementName;
    }

    protected static String getQualifiedName(AnalysisMethod method) {
        return method.getSignature().getReturnType().toJavaName(true) + " " + method.getQualifiedName();
    }

    /**
     * Get all the field indexes that should be relinked using the hosted value of a constant from
     * the given type.
     */
    @SuppressWarnings("unused")
    public Set<Integer> getRelinkedFields(AnalysisType type, AnalysisMetaAccess metaAccess) {
        return Set.of();
    }

    public GraphEncoder getGraphEncoder(ImageLayerWriter imageLayerWriter) {
        return new GraphEncoder(externalValues, imageLayerWriter);
    }

    @SuppressWarnings("unused")
    public GraphDecoder getGraphAnalysisElementsDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new GraphDecoder(EncodedGraph.class.getClassLoader(), imageLayerLoader, analysisMethod);
    }

    @SuppressWarnings("unused")
    public GraphDecoder getGraphDecoder(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod, SnippetReflectionProvider snippetReflectionProvider) {
        return new GraphDecoder(EncodedGraph.class.getClassLoader(), imageLayerLoader, analysisMethod);
    }

    public static class GraphEncoder extends ObjectCopier.Encoder {
        @SuppressWarnings("this-escape")
        public GraphEncoder(Map<Object, Field> externalValues, ImageLayerWriter imageLayerWriter) {
            super(externalValues);
            addBuiltin(new ImageHeapConstantBuiltIn(imageLayerWriter, null));
            addBuiltin(new AnalysisTypeBuiltIn(null));
            addBuiltin(new AnalysisMethodBuiltIn(null, null));
            addBuiltin(new AnalysisFieldBuiltIn(null));
            addBuiltin(new FieldLocationIdentityBuiltIn(null));
        }
    }

    public static class GraphDecoder extends ObjectCopier.Decoder {
        private final ImageLayerLoader imageLayerLoader;

        @SuppressWarnings("this-escape")
        public GraphDecoder(ClassLoader classLoader, ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod) {
            super(classLoader);
            this.imageLayerLoader = imageLayerLoader;
            addBuiltin(new ImageHeapConstantBuiltIn(null, imageLayerLoader));
            addBuiltin(new AnalysisTypeBuiltIn(imageLayerLoader));
            addBuiltin(new AnalysisMethodBuiltIn(imageLayerLoader, analysisMethod));
            addBuiltin(new AnalysisFieldBuiltIn(imageLayerLoader));
            addBuiltin(new FieldLocationIdentityBuiltIn(imageLayerLoader));
        }

        @Override
        public Class<?> loadClass(String className) {
            return imageLayerLoader.lookupClass(false, className);
        }
    }

    public static class ImageHeapConstantBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerWriter imageLayerWriter;
        private final ImageLayerLoader imageLayerLoader;

        protected ImageHeapConstantBuiltIn(ImageLayerWriter imageLayerWriter, ImageLayerLoader imageLayerLoader) {
            super(ImageHeapConstant.class, ImageHeapInstance.class, ImageHeapObjectArray.class, ImageHeapPrimitiveArray.class);
            this.imageLayerWriter = imageLayerWriter;
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            ImageHeapConstant imageHeapConstant = (ImageHeapConstant) obj;
            imageLayerWriter.ensureConstantPersisted(imageHeapConstant);
            stream.writePackedUnsignedInt(imageHeapConstant.getConstantData().id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return imageLayerLoader.getOrCreateConstant(id);
        }
    }

    public static class AnalysisTypeBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerLoader imageLayerLoader;

        protected AnalysisTypeBuiltIn(ImageLayerLoader imageLayerLoader) {
            super(AnalysisType.class, PointsToAnalysisType.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisType type = (AnalysisType) obj;
            type.registerAsTrackedAcrossLayers(TRACKED_REASON);
            stream.writePackedUnsignedInt(type.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return imageLayerLoader.getAnalysisTypeForBaseLayerId(id);
        }
    }

    public static class AnalysisMethodBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerLoader imageLayerLoader;
        private final AnalysisMethod analysisMethod;

        protected AnalysisMethodBuiltIn(ImageLayerLoader imageLayerLoader, AnalysisMethod analysisMethod) {
            super(AnalysisMethod.class, PointsToAnalysisMethod.class);
            this.imageLayerLoader = imageLayerLoader;
            this.analysisMethod = analysisMethod;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisMethod method = (AnalysisMethod) obj;
            method.registerAsTrackedAcrossLayers(TRACKED_REASON);
            stream.writePackedUnsignedInt(method.getId());
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            if (id == analysisMethod.getId()) {
                return analysisMethod;
            }
            return imageLayerLoader.getAnalysisMethodForBaseLayerId(id);
        }
    }

    public static class AnalysisFieldBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerLoader imageLayerLoader;

        protected AnalysisFieldBuiltIn(ImageLayerLoader imageLayerLoader) {
            super(AnalysisField.class, PointsToAnalysisField.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            AnalysisField field = (AnalysisField) obj;
            int id = encodeField(field);
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return decodeField(imageLayerLoader, id);
        }
    }

    public static class FieldLocationIdentityBuiltIn extends ObjectCopier.Builtin {
        private final ImageLayerLoader imageLayerLoader;

        protected FieldLocationIdentityBuiltIn(ImageLayerLoader imageLayerLoader) {
            super(FieldLocationIdentity.class);
            this.imageLayerLoader = imageLayerLoader;
        }

        @Override
        public void encode(ObjectCopier.Encoder encoder, ObjectCopierOutputStream stream, Object obj) throws IOException {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) obj;
            AnalysisField field = (AnalysisField) fieldLocationIdentity.getField();
            int id = encodeField(field);
            stream.writePackedUnsignedInt(id);
        }

        @Override
        protected Object decode(ObjectCopier.Decoder decoder, Class<?> concreteType, ObjectCopierInputStream stream) throws IOException {
            int id = stream.readPackedUnsignedInt();
            return new FieldLocationIdentity(decodeField(imageLayerLoader, id));
        }
    }

    private static int encodeField(AnalysisField field) {
        field.registerAsTrackedAcrossLayers(TRACKED_REASON);
        return field.getId();
    }

    private static AnalysisField decodeField(ImageLayerLoader imageLayerLoader, int id) {
        return imageLayerLoader.getAnalysisFieldForBaseLayerId(id);
    }
}
