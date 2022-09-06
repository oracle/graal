/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.cpufeature;

import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.InjectedNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsicFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IntegerTestNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.CPUFeatureAccess;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@AutomaticallyRegisteredFeature
class RuntimeCPUFeatureCheckFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RuntimeSupport.getRuntimeSupport().addInitializationHook(new RuntimeCPUFeatureCheckInitializer());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        access.registerAsAccessed(RuntimeCPUFeatureCheckImpl.getMaskField());
    }
}

/**
 * Hook to initialize the {@link RuntimeCPUFeatureCheckImpl#reinitialize() CPU feature mask} at run
 * time.
 *
 * This needs to be a separate class, making this a lambda call or an anonymous class would pull the
 * {@link Feature} hierarchy into the image.
 */
final class RuntimeCPUFeatureCheckInitializer implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        RuntimeCPUFeatureCheckImpl.instance().reinitialize();
    }
}

/**
 * Implementation of the {@link RuntimeCPUFeatureCheck}.
 *
 * The runtime checks are implemented by saving a bitmask of all supported CPU features during
 * native image startup in a global variable, then consulting this global value for each CPU feature
 * test.
 */
@AutomaticallyRegisteredImageSingleton
@NodeIntrinsicFactory
public final class RuntimeCPUFeatureCheckImpl {

    public static RuntimeCPUFeatureCheckImpl instance() {
        return ImageSingletons.lookup(RuntimeCPUFeatureCheckImpl.class);
    }

    /**
     * Stores the CPU features available at run time.
     *
     * The field is {@linkplain RuntimeCPUFeatureCheckInitializer initialized at run time} and
     * accessed via an {@linkplain #instance() image singleton}.
     *
     * We only emit run time checks for a limited set features. To compress the encoding, only
     * features specified by {@link RuntimeCPUFeatureCheck#getSupportedFeatures(Architecture)} are
     * considered. New features can be added on demand, but space is limited (see last paragraph).
     * Supported features are mapped to bit indices in the {@link #cpuFeatureMask} via
     * {@link RuntimeCPUFeatureCheckImpl#getEncoding(Enum)}.
     *
     * The features are stored in bit-wise negated form, i.e., a {@code 1} means that a feature is
     * <em>not</em> available. This allows testing multiple features efficiently in a single
     * {@code test} instruction, available on all supported platforms. A {@code test} is basically a
     * logical <em>and</em>. The requested features are available if the {@code test} instruction
     * sets the <em>zero flag</em>.
     *
     * <pre>
     *     features:            DCBA
     *     cpuFeatureMask:    0b1000   # feature D is not available
     *     featureCheckMask:  0x1100   # request features D and C
     *     result (&):        0x1000   # zero flag cleared -> not all features available
     * </pre>
     *
     * The field is an {@code int} because that produces the most efficient code on all supported
     * platforms. However, we cannot use the full 32 bit for encoding features, because some CPUs
     * lack instructions to encode immediate values of that size. Thus, we restrict ourselves to 8
     * bits (i.e., 8 features) for now.
     */
    @SuppressWarnings("unused") private int cpuFeatureMask;

    /**
     * Map from an enum entry (CPU feature) to a bit index in the {@link #cpuFeatureMask}.
     *
     * @implNote {@link Enum#ordinal()} is used as index. Since the number of features is highly
     *           restricted, using a {@code byte} is sufficient.
     */
    private final byte[] enumToBitIndex;

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("rawtypes")
    RuntimeCPUFeatureCheckImpl() {
        Architecture arch = ConfigurationValues.getTarget().arch;
        Set<? extends Enum<?>> supportedFeatures = RuntimeCPUFeatureCheck.getSupportedFeatures(arch);
        int size = supportedFeatures.size();
        if (size == 0) {
            // no features
            this.enumToBitIndex = null;
        } else {
            GraalError.guarantee(size <= Byte.SIZE, "Cannot encode %s features in 8 bit", size);

            ArrayList<Byte> enumToBitIndexMap = new ArrayList<>();
            int index = 0;
            Class<? extends Enum> clazz = null;
            for (Enum<?> feature : supportedFeatures) {
                // verify that we are always dealing with the same enum
                if (clazz == null) {
                    clazz = feature.getClass();
                } else {
                    GraalError.guarantee(clazz.equals(feature.getClass()), "Incompatible classes %s vs. %s", clazz, feature.getClass());
                }
                // grow array
                int ordinal = feature.ordinal();
                enumToBitIndexMap.ensureCapacity(ordinal);
                while (enumToBitIndexMap.size() < ordinal) {
                    enumToBitIndexMap.add((byte) -1);
                }
                // insert entry
                enumToBitIndexMap.add(ordinal, NumUtil.safeToByte(index));
                index++;
            }
            assert index == supportedFeatures.size();
            // copy to plain byte[]
            this.enumToBitIndex = new byte[enumToBitIndexMap.size()];
            for (int i = 0; i < enumToBitIndexMap.size(); i++) {
                this.enumToBitIndex[i] = enumToBitIndexMap.get(i);
            }
        }
        reinitialize();
    }

    void reinitialize() {
        this.cpuFeatureMask = compute();
    }

    private int compute() {
        if (!SubstrateUtil.HOSTED) {
            CPUFeatureAccess cpuFeatureAccess = ImageSingletons.lookup(CPUFeatureAccess.class);
            GraalError.guarantee(cpuFeatureAccess != null, "No %s singleton", CPUFeatureAccess.class.getSimpleName());
            EnumSet<?> features = cpuFeatureAccess.determineHostCPUFeatures();
            return ~computeFeatureMaskInternal(features);
        }
        return ~computeFeatureMask(getStaticFeatures());
    }

    /**
     * Computes the feature flag mask.
     */
    @Fold
    public <T extends Enum<T>> int computeFeatureMask(EnumSet<T> features) {
        return computeFeatureMaskInternal(features);
    }

    /**
     * Computes the feature flag mask.
     * <p>
     * This is the runtime version, i.e., no {@link Fold} annotation. It should only be used for
     * initializing the host feature mask at run time. All other usages should use
     * {@link #computeFeatureMask} to ensure it constant folds.
     *
     * @see #computeFeatureMask
     */
    private <T extends Enum<T>> int computeFeatureMaskInternal(EnumSet<T> features) {
        int mask = 0;
        for (Enum<T> feature : features) {
            if (enabledForRuntimeFeatureCheck(feature)) {
                mask |= 1 << getEncoding(feature);
            }
        }
        return mask;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void putRaw(EnumMap enumToInt, int index, Enum feature) {
        enumToInt.put(feature, index);
    }

    private boolean enabledForRuntimeFeatureCheck(Enum<?> feature) {
        return enumToBitIndex != null && getEncodingUnchecked(feature) >= 0;
    }

    private byte getEncodingUnchecked(Enum<?> feature) {
        return feature.ordinal() < enumToBitIndex.length ? enumToBitIndex[feature.ordinal()] : -1;
    }

    private int getEncoding(Enum<?> feature) {
        if (SubstrateUtil.HOSTED) {
            GraalError.guarantee(enumToBitIndex != null, "No features registered for run time feature check for platform %s", ConfigurationValues.getTarget().arch);
        }
        byte code = getEncodingUnchecked(feature);
        if (SubstrateUtil.HOSTED) {
            GraalError.guarantee(code >= 0, "Feature %s no registered for run time feature check", feature);
        }
        return code;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumSet<?> toEnumSet(Enum first, Enum... rest) {
        return EnumSet.of(first, rest);
    }

    public static boolean intrinsify(GraphBuilderContext b, @InjectedNodeParameter SnippetReflectionProvider snippetReflection, Enum<?> first, Enum<?>... rest) {
        return buildRuntimeCPUFeatureCheck(b, snippetReflection, toEnumSet(first, rest));
    }

    /**
     * Utility method for generating the graph for a {@code boolean}-valued runtime check for a CPU
     * feature, to be used by platform-specific node intrinsics. If the feature is statically known
     * to be supported, this generates a {@code true} constant. If the feature is statically known
     * to be unavailable for a runtime check, this generates a {@code false} constant.
     * </p>
     *
     * The root node built by this intrinsic includes a branch probability annotation. It must be
     * used directly as the condition of an {@code if} statement.
     */
    private static boolean buildRuntimeCPUFeatureCheck(GraphBuilderContext b, SnippetReflectionProvider snippetReflection, EnumSet<?> allFeatures) {
        // remove static features from the set
        EnumSet<?> features = removeStaticFeatures(allFeatures);
        if (features.isEmpty()) {
            /*
             * No runtime check needed, we know statically that the architecture has the required
             * feature.
             */
            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(true));
        } else if (!shouldCreateRuntimeFeatureCheck(features)) {
            /*
             * No runtime check needed, we know statically that the architecture will never have the
             * required feature.
             */
            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
        } else {
            /*
             * Generate a runtime CPU feature check, reading the feature mask from a global and
             * testing the bit of interest.
             */
            MetaAccessProvider metaAccess = b.getMetaAccess();
            ResolvedJavaField field = getMaskField(metaAccess);
            ConstantNode object = b.add(ConstantNode.forConstant(snippetReflection.forObject(instance()), metaAccess));
            ValueNode featureMask = b.add(LoadFieldNode.create(null, object, field));
            int mask = instance().computeFeatureMask(features);
            GraalError.guarantee(JavaKind.Int.equals(field.getType().getJavaKind()), "Expected field to be an int");
            LogicNode featureBitIsZero = b.add(IntegerTestNode.create(featureMask, ConstantNode.forInt(mask), NodeView.DEFAULT));
            ValueNode condition = b.add(ConditionalNode.create(featureBitIsZero, ConstantNode.forBoolean(true), ConstantNode.forBoolean(false), NodeView.DEFAULT));
            b.addPush(JavaKind.Boolean, new BranchProbabilityNode(ConstantNode.forDouble(LIKELY_PROBABILITY), condition));
        }
        return true;
    }

    public static java.lang.reflect.Field getMaskField() {
        return ReflectionUtil.lookupField(RuntimeCPUFeatureCheckImpl.class, "cpuFeatureMask");
    }

    public static ResolvedJavaField getMaskField(MetaAccessProvider metaAccess) {
        return metaAccess.lookupJavaField(getMaskField());
    }

    /**
     * Returns a set containing those of the given {@code features} that are not known to be
     * available at image build time.
     */
    @Fold
    @SuppressWarnings("unlikely-arg-type")
    public static EnumSet<?> removeStaticFeatures(EnumSet<?> features) {
        EnumSet<?> copy = EnumSet.copyOf(features);
        EnumSet<?> featuresToBeRemoved = getStaticFeatures();
        copy.removeAll(featuresToBeRemoved);
        return copy;
    }

    /**
     * Returns {@code false} if some feature cannot be available at run time, i.e., the runtime
     * feature check would always fail.
     *
     * This method returns {@code true} only if <em>all</em> of the given {@code features}
     * <ul>
     * <li>are enabled via the {@code +H:RuntimeCheckedCPUFeatures} option, and</li>
     * <li>are in the set of {@linkplain RuntimeCPUFeatureCheck#getSupportedFeatures features
     * enabled for run time feature checks} for the current architecture.</li>
     * </ul>
     *
     * This method does <em>not</em> filter out {@linkplain #removeStaticFeatures statically
     * available features}. This should be done explicitly before calling this method.
     */
    @Fold
    public static boolean shouldCreateRuntimeFeatureCheck(EnumSet<?> features) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        return containsAll(target.getRuntimeCheckedCPUFeatures(), features) && containsAll(RuntimeCPUFeatureCheck.getSupportedFeatures(target.arch), features);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean containsAll(Set features, EnumSet feature) {
        return features.containsAll(feature);
    }

    @Fold
    static EnumSet<?> getStaticFeatures() {
        Architecture arch = ConfigurationValues.getTarget().arch;
        if (arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures();
        } else if (arch instanceof AArch64) {
            return ((AArch64) arch).getFeatures();
        } else {
            throw GraalError.shouldNotReachHere("unsupported architecture");
        }
    }

}
