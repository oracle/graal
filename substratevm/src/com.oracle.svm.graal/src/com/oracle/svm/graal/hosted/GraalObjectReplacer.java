/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted;

import java.lang.management.ThreadMXBean;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.nativeimage.Feature.CompilationAccess;
import org.graalvm.nativeimage.c.function.RelocatedPointer;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.util.Replaced;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.graal.SubstrateGraalRuntime;
import com.oracle.svm.graal.meta.SubstrateConstantFieldProvider;
import com.oracle.svm.graal.meta.SubstrateConstantReflectionProvider;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateSignature;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.graal.meta.UniqueStringTable;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Replaces graal related objects during analysis in the universe.
 *
 * It is mainly used to replace the Hosted* meta data with the Substrate* meta data.
 */
public class GraalObjectReplacer implements Function<Object, Object> {

    private final AnalysisUniverse aUniverse;
    private final AnalysisMetaAccess aMetaAccess;
    private final HashMap<AnalysisMethod, SubstrateMethod> methods = new HashMap<>();
    private final HashMap<AnalysisField, SubstrateField> fields = new HashMap<>();
    private final HashMap<FieldLocationIdentity, FieldLocationIdentity> fieldLocationIdentities = new HashMap<>();
    private final HashMap<AnalysisType, SubstrateType> types = new HashMap<>();
    private final HashMap<Signature, SubstrateSignature> signatures = new HashMap<>();
    private final SubstrateMetaAccess sMetaAccess;
    private final SubstrateConstantReflectionProvider sConstantReflectionProvider;
    private final SubstrateConstantFieldProvider sConstantFieldProvider;
    private SubstrateGraalRuntime sGraalRuntime;

    private final UniqueStringTable stringTable;

    public GraalObjectReplacer(AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess) {
        this.aUniverse = aUniverse;
        this.aMetaAccess = aMetaAccess;
        this.sMetaAccess = new SubstrateMetaAccess();
        this.stringTable = new UniqueStringTable();
        this.sConstantReflectionProvider = new SubstrateConstantReflectionProvider(sMetaAccess);
        this.sConstantFieldProvider = new SubstrateConstantFieldProvider(aMetaAccess);
    }

    public void setGraalRuntime(SubstrateGraalRuntime sGraalRuntime) {
        assert this.sGraalRuntime == null;
        this.sGraalRuntime = sGraalRuntime;
    }

    @Override
    public Object apply(Object source) {

        if (source == null) {
            return null;
        }

        Object dest = source;

        if (source instanceof RelocatedPointer) {
            return dest;
        }

        if (source instanceof MetaAccessProvider) {
            dest = sMetaAccess;
        } else if (source instanceof GraalRuntime) {
            dest = sGraalRuntime;
        } else if (source instanceof AnalysisConstantReflectionProvider) {
            dest = sConstantReflectionProvider;
        } else if (source instanceof AnalysisConstantFieldProvider) {
            dest = sConstantFieldProvider;
        } else if (source instanceof ForeignCallsProvider) {
            dest = GraalSupport.getRuntimeConfig().getProviders().getForeignCalls();
        } else if (source instanceof HostedSnippetReflectionProvider) {
            dest = GraalSupport.getRuntimeConfig().getSnippetReflection();

        } else if (shouldBeReplaced(source)) {
            /*
             * The hash maps must be synchronized, because replace() may be called from
             * BigBang.finish(), which is multi-threaded.
             */
            synchronized (this) {
                if (source instanceof ResolvedJavaMethod) {
                    dest = createMethod((ResolvedJavaMethod) source);
                } else if (source instanceof ResolvedJavaField) {
                    dest = createField((ResolvedJavaField) source);
                } else if (source instanceof ResolvedJavaType) {
                    dest = createType((ResolvedJavaType) source);
                } else if (source instanceof Signature) {
                    dest = createSignature((Signature) source);
                } else if (source instanceof FieldLocationIdentity) {
                    dest = fieldLocationIdentities.get(source);
                    if (dest == null) {
                        SubstrateField destField = (SubstrateField) apply(((FieldLocationIdentity) source).getField());
                        dest = new SubstrateFieldLocationIdentity(destField);
                        fieldLocationIdentities.put((FieldLocationIdentity) source, (FieldLocationIdentity) dest);
                    }
                }
            }
        }

        assert dest != null;
        String className = dest.getClass().getName();
        assert !className.contains(".hotspot.") || className.contains(".svm.jtt.hotspot.") : "HotSpot object in image " + className;
        assert !className.contains(".analysis.meta.") : "Analysis meta object in image " + className;
        assert !className.contains(".hosted.meta.") : "Hosted meta object in image " + className;

        return dest;
    }

    private static boolean shouldBeReplaced(Object object) {
        if (object instanceof Replaced) {
            return false;
        }

        /*
         * Must be in sync with the types checked in replace()
         */
        if (object instanceof ResolvedJavaMethod) {
            return true;
        }
        if (object instanceof ResolvedJavaField) {
            return true;
        }
        if (object instanceof ResolvedJavaType) {
            return true;
        }
        if (object instanceof ThreadMXBean) {
            return true;
        }
        if (object instanceof FieldLocationIdentity) {
            return true;
        }
        return false;
    }

    public SubstrateMethod createMethod(ResolvedJavaMethod original) {
        AnalysisMethod aMethod;
        if (original instanceof AnalysisMethod) {
            aMethod = (AnalysisMethod) original;
        } else if (original instanceof HostedMethod) {
            aMethod = ((HostedMethod) original).wrapped;
        } else {
            aMethod = aUniverse.lookup(original);
        }
        SubstrateMethod sMethod = methods.get(aMethod);
        if (sMethod == null) {
            assert !(original instanceof HostedMethod) : "too late to create new method";
            sMethod = new SubstrateMethod(aMethod, stringTable);
            methods.put(aMethod, sMethod);

            /*
             * The links to other meta objects must be set after adding to the methods to avoid
             * infinite recursion.
             */
            sMethod.setLinks(createSignature(aMethod.getSignature()), createType(aMethod.getDeclaringClass()));

            /*
             * Annotations are updated in every analysis iteration, but this is a starting point. It
             * also ensures that all types used by annotations are created eagerly.
             */
            sMethod.setAnnotationsEncoding(Inflation.encodeAnnotations(aMetaAccess, aMethod.getAnnotations(), null));
        }
        return sMethod;
    }

    public SubstrateField createField(ResolvedJavaField original) {
        AnalysisField aField;
        if (original instanceof AnalysisField) {
            aField = (AnalysisField) original;
        } else {
            aField = ((HostedField) original).wrapped;
        }
        SubstrateField sField = fields.get(aField);

        if (sField == null) {
            assert !(original instanceof HostedField) : "too late to create new field";

            int modifiers = aField.getModifiers();
            if (ReadableJavaField.injectFinalForRuntimeCompilation(aField.wrapped)) {
                modifiers = modifiers | Modifier.FINAL;
            }
            sField = new SubstrateField(aMetaAccess, aField, modifiers, stringTable);
            fields.put(aField, sField);

            sField.setLinks(createType(aField.getType()), createType(aField.getDeclaringClass()));

            /*
             * Annotations are updated in every analysis iteration, but this is a starting point. It
             * also ensures that all types used by annotations are created eagerly.
             */
            sField.setAnnotationsEncoding(Inflation.encodeAnnotations(aMetaAccess, aField.getAnnotations(), null));
        }
        return sField;
    }

    public SubstrateField getField(AnalysisField field) {
        return fields.get(field);
    }

    public boolean removeField(AnalysisField field) {
        return fields.remove(field) != null;
    }

    public boolean typeCreated(JavaType original) {
        return types.containsKey(toAnalysisType(original));
    }

    public SubstrateType createType(JavaType original) {
        if (original == null) {
            return null;
        }

        AnalysisType aType = toAnalysisType(original);
        SubstrateType sType = types.get(aType);

        if (sType == null) {
            assert !(original instanceof HostedType) : "too late to create new type";
            DynamicHub hub = ((SVMHost) aUniverse.hostVM()).dynamicHub(aType);
            sType = new SubstrateType(aType.getJavaKind(), hub);
            types.put(aType, sType);
            hub.setMetaType(sType);

            sType.setRawAllInstanceFields(createAllInstanceFields(aType));
            createType(aType.getSuperclass());
            createType(aType.getComponentType());
            for (AnalysisType aInterface : aType.getInterfaces()) {
                createType(aInterface);
            }
        }
        return sType;
    }

    private static AnalysisType toAnalysisType(JavaType original) {
        if (original instanceof HostedType) {
            return ((HostedType) original).getWrapped();
        } else {
            return (AnalysisType) original;
        }
    }

    private SubstrateField[] createAllInstanceFields(ResolvedJavaType originalType) {
        ResolvedJavaField[] originalFields = originalType.getInstanceFields(true);
        SubstrateField[] sFields = new SubstrateField[originalFields.length];
        for (int idx = 0; idx < originalFields.length; idx++) {
            sFields[idx] = createField(originalFields[idx]);
        }
        return sFields;
    }

    private SubstrateSignature createSignature(Signature original) {
        SubstrateSignature sSignature = signatures.get(original);
        if (sSignature == null) {
            sSignature = new SubstrateSignature();
            signatures.put(original, sSignature);

            SubstrateType[] parameterTypes = new SubstrateType[original.getParameterCount(false)];
            for (int index = 0; index < original.getParameterCount(false); index++) {
                parameterTypes[index] = createType(original.getParameterType(index, null));
            }
            /*
             * The links to other meta objects must be set after adding to the signatures to avoid
             * infinite recursion.
             */
            sSignature.setTypes(parameterTypes, createType(original.getReturnType(null)));
        }
        return sSignature;
    }

    /**
     * Some meta data must be updated during analysis. This is done here.
     */
    public boolean updateDataDuringAnalysis(AnalysisMetaAccess metaAccess) {
        boolean result = false;
        List<AnalysisMethod> aMethods = new ArrayList<>();
        aMethods.addAll(methods.keySet());

        int index = 0;
        while (index < aMethods.size()) {
            AnalysisMethod aMethod = aMethods.get(index++);
            SubstrateMethod sMethod = methods.get(aMethod);

            SubstrateMethod[] implementations = new SubstrateMethod[aMethod.getImplementations().length];
            int idx = 0;
            for (AnalysisMethod impl : aMethod.getImplementations()) {
                SubstrateMethod sImpl = methods.get(impl);
                if (sImpl == null) {
                    sImpl = createMethod(impl);
                    aMethods.add(impl);
                    result = true;
                }
                implementations[idx++] = sImpl;
            }
            if (sMethod.setImplementations(implementations)) {
                result = true;
            }
        }

        for (Map.Entry<AnalysisMethod, SubstrateMethod> entry : methods.entrySet()) {
            if (entry.getValue().setAnnotationsEncoding(Inflation.encodeAnnotations(metaAccess, entry.getKey().getAnnotations(), entry.getValue().getAnnotationsEncoding()))) {
                result = true;
            }
        }
        for (Map.Entry<AnalysisField, SubstrateField> entry : fields.entrySet()) {
            if (entry.getValue().setAnnotationsEncoding(Inflation.encodeAnnotations(metaAccess, entry.getKey().getAnnotations(), entry.getValue().getAnnotationsEncoding()))) {
                result = true;
            }
        }

        return result;
    }

    /**
     * Updates all relevant data from universe building. Object replacement is done during analysis.
     * Therefore all substrate VM related data has to be updated after building the substrate
     * universe.
     */
    @SuppressWarnings("try")
    public void updateSubstrateDataAfterCompilation(HostedUniverse hUniverse) {

        for (Map.Entry<AnalysisType, SubstrateType> entry : types.entrySet()) {
            AnalysisType aType = entry.getKey();
            SubstrateType sType = entry.getValue();

            if (!hUniverse.contains(aType)) {
                continue;
            }
            HostedType hType = hUniverse.lookup(aType);

            DynamicHub uniqueImplementation = null;
            if (hType.getUniqueConcreteImplementation() != null) {
                uniqueImplementation = hType.getUniqueConcreteImplementation().getHub();
            }
            sType.setTypeCheckData(hType.getInstanceOfFromTypeID(), hType.getInstanceOfNumTypeIDs(), uniqueImplementation);
            if (sType.getInstanceFieldCount() > 1) {
                /*
                 * What we do here is just a reordering of the instance fields array. The fields
                 * array already contains all the fields, but in the order of the AnalysisType. As
                 * the UniverseBuilder reorders the fields, we re-construct the fields array in the
                 * order of the HostedType. The correct order is essential for materialization
                 * during deoptimization.
                 */
                sType.setRawAllInstanceFields(createAllInstanceFields(hType));
            }
        }

        for (Map.Entry<AnalysisField, SubstrateField> entry : fields.entrySet()) {
            AnalysisField aField = entry.getKey();
            SubstrateField sField = entry.getValue();
            HostedField hField = hUniverse.lookup(aField);

            sField.setSubstrateData(hField.getLocation(), hField.isAccessed(), hField.isWritten(), hField.getConstantValue());
        }
    }

    public void updateSubstrateDataAfterHeapLayout(HostedUniverse hUniverse) {
        for (Map.Entry<AnalysisMethod, SubstrateMethod> entry : methods.entrySet()) {
            AnalysisMethod aMethod = entry.getKey();
            SubstrateMethod sMethod = entry.getValue();
            HostedMethod hMethod = hUniverse.lookup(aMethod);
            int vTableIndex = (hMethod.hasVTableIndex() ? hMethod.getVTableIndex() : -1);

            /*
             * We access the offset of methods in the image code section here. Therefore, this code
             * can only run after the heap and code cache layout was done.
             */
            sMethod.setSubstrateData(vTableIndex, hMethod.isCodeAddressOffsetValid() ? hMethod.getCodeAddressOffset() : 0, hMethod.getDeoptOffsetInImage());
        }
    }

    public void registerImmutableObjects(CompilationAccess access) {
        for (SubstrateMethod method : methods.values()) {
            access.registerAsImmutable(method);
            access.registerAsImmutable(method.getRawImplementations());
            access.registerAsImmutable(method.getEncodedLineNumberTable());
        }
        for (SubstrateField field : fields.values()) {
            access.registerAsImmutable(field);
        }
        for (FieldLocationIdentity fieldLocationIdentity : fieldLocationIdentities.values()) {
            access.registerAsImmutable(fieldLocationIdentity);
        }
        for (SubstrateType type : types.values()) {
            access.registerAsImmutable(type);
            access.registerAsImmutable(type.getRawAllInstanceFields());
        }
        for (SubstrateSignature signature : signatures.values()) {
            access.registerAsImmutable(signature);
            access.registerAsImmutable(signature.getRawParameterTypes());
        }
    }
}
