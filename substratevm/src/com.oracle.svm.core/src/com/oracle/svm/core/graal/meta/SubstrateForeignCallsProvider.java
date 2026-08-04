/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.shared.util.VMError.shouldNotReachHere;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyLookup;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public class SubstrateForeignCallsProvider implements ArrayCopyForeignCalls {

    final MetaAccessProvider metaAccess;
    final RegisterConfig registerConfig;
    final TargetDescription target;
    private final EconomicMap<ForeignCallSignature, SubstrateForeignCallLinkage> foreignCalls;
    private final EconomicMap<ForeignCallSignature, List<RuntimeCheckedForeignCall>> runtimeCheckedForeignCalls;
    protected ArrayCopyLookup arrayCopyLookup;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private final EconomicMap<EnumSet<?>, EnumSet<?>> internedEnumSets = EconomicMap.create();

    private record RuntimeCheckedForeignCall(SubstrateForeignCallLinkage linkage, EnumSet<?> requiredCPUFeatures) {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateForeignCallsProvider(MetaAccessProvider metaAccess, RegisterConfig registerConfig) {
        this.metaAccess = metaAccess;
        this.registerConfig = registerConfig;
        this.target = SubstrateTarget.singleton();
        this.foreignCalls = EconomicMap.create();
        this.runtimeCheckedForeignCalls = EconomicMap.create();
    }

    /**
     * Returns all foreign-call linkages that may need compilation, including CPU-feature variants.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public Iterable<SubstrateForeignCallLinkage> getForeignCalls() {
        return () -> Stream.concat(
                        StreamSupport.stream(foreignCalls.getValues().spliterator(), false),
                        StreamSupport.stream(runtimeCheckedForeignCalls.getValues().spliterator(), false).//
                                        flatMap(variants -> variants.stream().map(RuntimeCheckedForeignCall::linkage))).//
                        iterator();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void register(SubstrateForeignCallDescriptor... descriptors) {
        for (SubstrateForeignCallDescriptor descriptor : descriptors) {
            register(descriptor);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void register(SubstrateForeignCallDescriptor descriptor) {
        register(descriptor.getSignature(), descriptor);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void register(ForeignCallSignature signature, SubstrateForeignCallDescriptor descriptor) {
        SubstrateForeignCallLinkage linkage = new SubstrateForeignCallLinkage(this, descriptor);
        foreignCalls.put(signature, linkage);
    }

    /**
     * Deduplicate identical enum sets.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private EnumSet<?> internEnumSet(EnumSet<?> set) {
        return internedEnumSets.computeIfAbsent(set.clone(), Function.identity());
    }

    /**
     * Registers a foreign call target that is selected when its required CPU features are available
     * to the current runtime compilation. An empty feature set is registered as the ordinary
     * fallback for {@code signature}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unlikely-arg-type")
    public void registerForeignCallWithCPUFeatures(ForeignCallSignature signature, SubstrateForeignCallDescriptor variant, EnumSet<?> requiredCPUFeatures) {
        if (requiredCPUFeatures == null || requiredCPUFeatures.isEmpty()) {
            register(signature, variant);
            return;
        }
        SubstrateForeignCallLinkage linkage = new SubstrateForeignCallLinkage(this, variant);
        List<RuntimeCheckedForeignCall> existing = runtimeCheckedForeignCalls.get(signature, List.of());
        runtimeCheckedForeignCalls.put(signature,
                        Stream.concat(existing.stream(), Stream.of(new RuntimeCheckedForeignCall(linkage, internEnumSet(requiredCPUFeatures)))).collect(Collectors.toUnmodifiableList()));
    }

    /**
     * Selects the preferred CPU feature variant of {@code descriptor}, if one was registered and
     * all its required features are available to the current compilation.
     */
    @SuppressWarnings("unlikely-arg-type")
    public SubstrateForeignCallLinkage lookupForeignCallWithCPUFeatures(ForeignCallDescriptor descriptor, EnumSet<?> availableFeatures) {
        List<RuntimeCheckedForeignCall> variants = runtimeCheckedForeignCalls.get(descriptor.getSignature());
        if (variants != null) {
            for (var variant : variants) {
                if (availableFeatures.containsAll(variant.requiredCPUFeatures())) {
                    GraalError.guarantee(RuntimeCompilation.isEnabled(), "should be reached in JIT mode only");
                    return variant.linkage();
                }
            }
        }
        return lookupForeignCall(descriptor);
    }

    /**
     * Returns the ordinary foreign call registered for {@code descriptor}, or {@code null} if none
     * was registered. Runtime-checked foreign calls are intentionally excluded because selecting
     * one requires an available CPU feature set.
     */
    public SubstrateForeignCallLinkage lookupOptionalForeignCall(ForeignCallDescriptor descriptor) {
        return foreignCalls.get(descriptor.getSignature());
    }

    @Override
    public SubstrateForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        SubstrateForeignCallLinkage callTarget = lookupOptionalForeignCall(descriptor);
        if (callTarget == null) {
            throw shouldNotReachHere("missing implementation for runtime call: " + descriptor);
        }
        return callTarget;
    }

    @Override
    public ForeignCallDescriptor getDescriptor(ForeignCallSignature signature) {
        SubstrateForeignCallLinkage linkage = foreignCalls.get(signature);
        return linkage.getDescriptor();
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(target.arch, javaKind);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerArrayCopyForeignCallsDelegate(ArrayCopyLookup arraycopyForeignCalls) {
        this.arrayCopyLookup = arraycopyForeignCalls;
    }

    @Override
    public ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, LocationIdentity killedLocation) {
        if (arrayCopyLookup != null) {
            return arrayCopyLookup.lookupArraycopyDescriptor(kind, aligned, disjoint, uninit, killedLocation);
        } else {
            throw VMError.unsupportedFeature("Fast ArrayCopy not supported yet.");
        }
    }
}
