/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.MetricKey;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.SnippetResolvedJavaMethod;
import org.graalvm.compiler.hotspot.SnippetResolvedJavaType;
import org.graalvm.compiler.nodes.FieldLocationIdentity;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.nativeimage.hosted.Feature.CompilationAccess;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.HostedStringDeduplication;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.SubstrateGraalRuntime;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateSignature;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.graal.meta.SubstrateUniverseFactory;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedConstantFieldProvider;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Replaces Graal related objects during analysis in the universe.
 *
 * It is mainly used to replace the Hosted* meta data with the Substrate* meta data.
 */
public class GraalGraphObjectReplacer implements Function<Object, Object> {

    private final AnalysisUniverse aUniverse;
    private final ConcurrentMap<AnalysisMethod, SubstrateMethod> methods = ObservableImageHeapMapProvider.create();
    private final ConcurrentMap<AnalysisField, SubstrateField> fields = new ConcurrentHashMap<>();
    private final ConcurrentMap<FieldLocationIdentity, SubstrateFieldLocationIdentity> fieldLocationIdentities = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisType, SubstrateType> types = new ConcurrentHashMap<>();
    private final ConcurrentMap<Signature, SubstrateSignature> signatures = new ConcurrentHashMap<>();
    private final SubstrateProviders sProviders;
    private final SubstrateUniverseFactory universeFactory;
    private SubstrateGraalRuntime sGraalRuntime;

    private final HostedStringDeduplication stringTable;

    private final Field substrateFieldTypeField;
    private final Field substrateFieldDeclaringClassField;
    private final Field dynamicHubMetaTypeField;
    private final Field substrateTypeRawAllInstanceFieldsField;

    private final Class<?> jvmciCleanerClass = ReflectionUtil.lookupClass(false, "jdk.vm.ci.hotspot.Cleaner");

    /**
     * Tracks whether it is legal to create new types.
     */
    private boolean forbidNewTypes = false;
    private BeforeAnalysisAccessImpl beforeAnalysisAccess;

    public GraalGraphObjectReplacer(AnalysisUniverse aUniverse, SubstrateProviders sProviders, SubstrateUniverseFactory universeFactory) {
        this.aUniverse = aUniverse;
        this.sProviders = sProviders;
        this.universeFactory = universeFactory;
        this.stringTable = HostedStringDeduplication.singleton();
        substrateFieldTypeField = ReflectionUtil.lookupField(SubstrateField.class, "type");
        substrateFieldDeclaringClassField = ReflectionUtil.lookupField(SubstrateField.class, "declaringClass");
        dynamicHubMetaTypeField = ReflectionUtil.lookupField(DynamicHub.class, "metaType");
        substrateTypeRawAllInstanceFieldsField = ReflectionUtil.lookupField(SubstrateType.class, "rawAllInstanceFields");
    }

    public void setGraalRuntime(SubstrateGraalRuntime sGraalRuntime) {
        assert this.sGraalRuntime == null;
        this.sGraalRuntime = sGraalRuntime;
    }

    public void setAnalysisAccess(BeforeAnalysisAccessImpl beforeAnalysisAccess) {
        this.beforeAnalysisAccess = beforeAnalysisAccess;
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

        if (source instanceof SnippetResolvedJavaMethod || source instanceof SnippetResolvedJavaType) {
            return source;
        }
        if (source instanceof MetaAccessProvider) {
            dest = sProviders.getMetaAccessProvider();
        } else if (source instanceof HotSpotJVMCIRuntime) {
            throw new UnsupportedFeatureException("HotSpotJVMCIRuntime should not appear in the image: " + source);
        } else if (source instanceof GraalHotSpotVMConfig) {
            throw new UnsupportedFeatureException("GraalHotSpotVMConfig should not appear in the image: " + source);
        } else if (source instanceof HotSpotBackendFactory) {
            HotSpotBackendFactory factory = (HotSpotBackendFactory) source;
            Architecture hostArch = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch;
            if (!factory.getArchitecture().equals(hostArch.getClass())) {
                throw new UnsupportedFeatureException("Non-host architecture HotSpotBackendFactory should not appear in the image: " + source);
            }
        } else if (source instanceof GraalRuntime) {
            dest = sGraalRuntime;
        } else if (source instanceof AnalysisConstantReflectionProvider) {
            dest = sProviders.getConstantReflectionProvider();
        } else if (source instanceof AnalysisConstantFieldProvider) {
            dest = sProviders.getConstantFieldProvider();
        } else if (source instanceof ForeignCallsProvider) {
            dest = sProviders.getForeignCallsProvider();
        } else if (source instanceof SnippetReflectionProvider) {
            dest = sProviders.getSnippetReflectionProvider();

        } else if (source instanceof MetricKey) {
            /* Ensure lazily initialized name fields are computed. */
            ((MetricKey) source).getName();
        } else if (source instanceof NodeClass) {
            /* Ensure lazily initialized shortName field is computed. */
            ((NodeClass<?>) source).shortName();

        } else if (source instanceof HotSpotResolvedJavaMethod) {
            throw new UnsupportedFeatureException(source.toString());
        } else if (source instanceof HotSpotResolvedJavaField) {
            throw new UnsupportedFeatureException(source.toString());
        } else if (source instanceof HotSpotResolvedJavaType) {
            throw new UnsupportedFeatureException(source.toString());
        } else if (source instanceof HotSpotSignature) {
            throw new UnsupportedFeatureException(source.toString());
        } else if (source instanceof HotSpotObjectConstant) {
            throw new UnsupportedFeatureException(source.toString());
        } else if (source instanceof ResolvedJavaMethod && !(source instanceof SubstrateMethod)) {
            dest = createMethod((ResolvedJavaMethod) source);
        } else if (source instanceof ResolvedJavaField && !(source instanceof SubstrateField)) {
            dest = createField((ResolvedJavaField) source);
        } else if (source instanceof ResolvedJavaType && !(source instanceof SubstrateType)) {
            dest = createType((ResolvedJavaType) source);
        } else if (source instanceof FieldLocationIdentity && !(source instanceof SubstrateFieldLocationIdentity)) {
            dest = createFieldLocationIdentity((FieldLocationIdentity) source);
        }

        assert dest != null;
        Class<?> destClass = dest.getClass();
        if (jvmciCleanerClass.isAssignableFrom(destClass)) {
            throw new UnsupportedFeatureException(jvmciCleanerClass.getName() + " objects should not appear in the image: " + source);
        }
        String className = destClass.getName();
        assert SubstrateUtil.isBuildingLibgraal() || !className.contains(".hotspot.") || className.contains(".svm.jtt.hotspot.") : "HotSpot object in image " + className;
        assert !className.contains(".graal.reachability") : "Analysis meta object in image " + className;
        assert !className.contains(".pointsto.meta.") : "Analysis meta object in image " + className;
        assert !className.contains(".hosted.meta.") : "Hosted meta object in image " + className;
        assert !SubstrateUtil.isBuildingLibgraal() || !className.contains(".svm.hosted.snippets.") : "Hosted snippet object in image " + className;

        return dest;
    }

    public synchronized SubstrateMethod createMethod(ResolvedJavaMethod original) {
        assert !(original instanceof SubstrateMethod) : original;

        AnalysisMethod aMethod;
        if (original instanceof AnalysisMethod) {
            aMethod = (AnalysisMethod) original;
        } else if (original instanceof HostedMethod) {
            aMethod = ((HostedMethod) original).wrapped;
        } else {
            aMethod = aUniverse.lookup(original);
        }
        aMethod = aMethod.getMultiMethod(MultiMethod.ORIGINAL_METHOD);
        assert aMethod != null;

        SubstrateMethod sMethod = methods.get(aMethod);
        if (sMethod == null) {
            assert !(original instanceof HostedMethod) : "too late to create new method";
            SubstrateMethod newMethod = universeFactory.createMethod(aMethod, stringTable);
            sMethod = methods.putIfAbsent(aMethod, newMethod);
            if (sMethod == null) {
                sMethod = newMethod;
                AnalysisType baseType = aMethod.getDeclaringClass();
                AnalysisMethod baseMethod = aMethod;
                /*
                 * Cannot use a method override reachability handler here. The original method can
                 * be the target of an invokeinterface, which doesn't necessarily correspond to an
                 * actual declared method, so normal resolution will not work.
                 */
                beforeAnalysisAccess.registerSubtypeReachabilityHandler((a, reachableSubtype) -> {
                    AnalysisType subtype = beforeAnalysisAccess.getMetaAccess().lookupJavaType(reachableSubtype);
                    if (!subtype.equals(baseType)) {
                        AnalysisMethod resolvedOverride = subtype.resolveConcreteMethod(baseMethod, null);
                        if (resolvedOverride != null) {
                            resolvedOverride.registerImplementationInvokedCallback((analysisAccess) -> createMethod(resolvedOverride));
                        }
                    }
                }, baseType.getJavaClass());

                /*
                 * The links to other meta objects must be set after adding to the methods to avoid
                 * infinite recursion.
                 */
                sMethod.setLinks(createSignature(aMethod.getSignature()), createType(aMethod.getDeclaringClass()));
            }
        }
        return sMethod;
    }

    public synchronized SubstrateField createField(ResolvedJavaField original) {
        assert !(original instanceof SubstrateField) : original;

        AnalysisField aField;
        if (original instanceof AnalysisField) {
            aField = (AnalysisField) original;
        } else if (original instanceof HostedField) {
            aField = ((HostedField) original).wrapped;
        } else {
            throw new InternalError(original.toString());
        }
        SubstrateField sField = fields.get(aField);
        if (sField == null) {
            assert !(original instanceof HostedField) : "too late to create new field";
            SubstrateField newField = universeFactory.createField(aField, stringTable);
            sField = fields.putIfAbsent(aField, newField);
            if (sField == null) {
                sField = newField;

                sField.setLinks(createType(aField.getType()), createType(aField.getDeclaringClass()));
                aUniverse.getHeapScanner().rescanField(sField, substrateFieldTypeField);
                aUniverse.getHeapScanner().rescanField(sField, substrateFieldDeclaringClassField);
            }
        }
        return sField;
    }

    private synchronized SubstrateFieldLocationIdentity createFieldLocationIdentity(FieldLocationIdentity original) {
        assert !(original instanceof SubstrateFieldLocationIdentity) : original;
        return fieldLocationIdentities.computeIfAbsent(original, (id) -> new SubstrateFieldLocationIdentity(createField(id.getField()), id.isImmutable()));
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

    /**
     * After this is called no new types can be created.
     */
    public void forbidNewTypes() {
        forbidNewTypes = true;
    }

    public synchronized SubstrateType createType(JavaType original) {
        assert !(original instanceof SubstrateType) : original;
        if (original == null) {
            return null;
        }

        AnalysisType aType = toAnalysisType(original);
        VMError.guarantee(aType.isLinked(), "Types reachable for JIT compilation must not have linkage errors");
        SubstrateType sType = types.get(aType);
        if (sType == null) {
            VMError.guarantee(!(forbidNewTypes || (original instanceof HostedType)), "Too late to create a new type: %s", aType);
            aType.registerAsReachable("type reachable from Graal graphs");
            DynamicHub hub = ((SVMHost) aUniverse.hostVM()).dynamicHub(aType);
            SubstrateType newType = new SubstrateType(aType.getJavaKind(), hub);
            sType = types.putIfAbsent(aType, newType);
            if (sType == null) {
                sType = newType;
                hub.setMetaType(sType);
                aUniverse.getHeapScanner().rescanField(hub, dynamicHubMetaTypeField);

                sType.setRawAllInstanceFields(createAllInstanceFields(aType));
                aUniverse.getHeapScanner().rescanField(sType, substrateTypeRawAllInstanceFieldsField);
                createType(aType.getSuperclass());
                createType(aType.getComponentType());
                for (AnalysisType aInterface : aType.getInterfaces()) {
                    createType(aInterface);
                }
            }
        }
        return sType;
    }

    private static AnalysisType toAnalysisType(JavaType original) {
        if (original instanceof HostedType) {
            return ((HostedType) original).getWrapped();
        } else if (original instanceof AnalysisType) {
            return (AnalysisType) original;
        } else {
            throw new InternalError("Unexpected type " + original);
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

    private synchronized SubstrateSignature createSignature(Signature original) {
        assert !(original instanceof SubstrateSignature) : original;

        SubstrateSignature sSignature = signatures.get(original);
        if (sSignature == null) {
            SubstrateSignature newSignature = new SubstrateSignature();
            sSignature = signatures.putIfAbsent(original, newSignature);
            if (sSignature == null) {
                sSignature = newSignature;

                SubstrateType[] parameterTypes = new SubstrateType[original.getParameterCount(false)];
                for (int index = 0; index < original.getParameterCount(false); index++) {
                    parameterTypes[index] = createType(original.getParameterType(index, null));
                }
                /*
                 * The links to other meta objects must be set after adding to the signatures to
                 * avoid infinite recursion.
                 */
                sSignature.setTypes(parameterTypes, createType(original.getReturnType(null)));
            }
        }
        return sSignature;
    }

    /**
     * Collect {@link SubstrateMethod} implementations.
     */
    public void setMethodsImplementations() {
        for (Map.Entry<AnalysisMethod, SubstrateMethod> entry : methods.entrySet()) {
            AnalysisMethod aMethod = entry.getKey();
            SubstrateMethod sMethod = entry.getValue();
            AnalysisMethod[] aMethodImplementations = aMethod.getImplementations();
            SubstrateMethod[] implementations = new SubstrateMethod[aMethodImplementations.length];
            int idx = 0;
            for (AnalysisMethod impl : aMethodImplementations) {
                SubstrateMethod sImpl = methods.get(impl);
                VMError.guarantee(sImpl != null, "SubstrateMethod for %s missing.", impl);
                implementations[idx++] = sImpl;
            }
            sMethod.setImplementations(implementations);
        }
    }

    /**
     * Updates all relevant data from universe building. Object replacement is done during analysis.
     * Therefore all substrate VM related data has to be updated after building the substrate
     * universe.
     */
    @SuppressWarnings("try")
    public void updateSubstrateDataAfterCompilation(HostedUniverse hUniverse, Providers providers) {

        for (Map.Entry<AnalysisType, SubstrateType> entry : types.entrySet()) {
            AnalysisType aType = entry.getKey();
            SubstrateType sType = entry.getValue();

            if (!hUniverse.contains(aType)) {
                continue;
            }
            HostedType hType = hUniverse.lookup(aType);

            if (hType.getUniqueConcreteImplementation() != null) {
                sType.setTypeCheckData(hType.getUniqueConcreteImplementation().getHub());
            }

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

            JavaConstant constantValue = hField.isStatic() && ((HostedConstantFieldProvider) providers.getConstantFieldProvider()).isFinalField(hField, null)
                            ? providers.getConstantReflection().readFieldValue(hField, null)
                            : null;
            sField.setSubstrateData(hField.getLocation(), hField.isAccessed(), hField.isWritten() || !hField.isValueAvailable(), constantValue);
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
