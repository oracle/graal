/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.ImageHeapList;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.debug.Assertions;

/**
 * This class holds garbage collection causes that are common and therefore shared between different
 * garbage collector implementations.
 */
public class GCCause {

    @DuplicatedInNativeCode public static final GCCause JavaLangSystemGC = new GCCause("java.lang.System.gc()", 0);
    @DuplicatedInNativeCode public static final GCCause UnitTest = new GCCause("Forced GC in unit test", 1);
    @DuplicatedInNativeCode public static final GCCause TestGCInDeoptimizer = new GCCause("Test GC in deoptimizer", 2);
    @DuplicatedInNativeCode public static final GCCause HintedGC = new GCCause("Hinted GC", 3);
    @DuplicatedInNativeCode public static final GCCause JvmtiForceGC = new GCCause("JvmtiEnv ForceGarbageCollection", 4);
    @DuplicatedInNativeCode public static final GCCause HeapDump = new GCCause("Heap Dump Initiated GC", 5);
    @DuplicatedInNativeCode public static final GCCause DiagnosticCommand = new GCCause("Diagnostic Command", 6);

    private final int id;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCCause(String name, int id) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getId() {
        return id;
    }

    public static GCCause fromId(int causeId) {
        return getGCCauses().get(causeId);
    }

    public static List<GCCause> getGCCauses() {
        return ImageSingletons.lookup(GCCauseSupport.class).gcCauses;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerGCCause(GCCause cause) {
        ImageSingletons.lookup(GCCauseSupport.class).installGCCause(cause);
    }
}

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
class GCCauseSupport {
    final List<GCCause> gcCauses = ImageHeapList.create(GCCause.class, null);

    @Platforms(Platform.HOSTED_ONLY.class)
    Object collectGCCauses(Object obj) {
        if (obj instanceof GCCause gcCause) {
            synchronized (gcCauses) {
                installGCCause(gcCause);
            }
        }
        return obj;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void installGCCause(GCCause gcCause) {
        int id = gcCause.getId();
        while (gcCauses.size() <= id) {
            gcCauses.add(null);
        }
        var existing = gcCauses.set(id, gcCause);
        if (existing != null && existing != gcCause) {
            throw VMError.shouldNotReachHere("Two GCCause objects have the same id " + id + ": " + gcCause.getName() + ", " + existing.getName());
        }
    }
}

@AutomaticallyRegisteredFeature
class GCCauseFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (!ImageLayerBuildingSupport.buildingImageLayer()) {
            /*
             * In traditional builds we lazily register GCCauses as they become visible to the
             * native-image generator.
             */
            access.registerObjectReplacer(ImageSingletons.lookup(GCCauseSupport.class)::collectGCCauses);
        } else {
            /*
             * For layered builds we eagerly register all GCCauses in the initial layer. In all
             * layers, via an object replacer, we then validate all referenced GCCauses have been
             * registered.
             */
            Function<Integer, String> idToGCCauseName;
            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                GCCauseSupport support = ImageSingletons.lookup(GCCauseSupport.class);
                support.installGCCause(GCCause.JavaLangSystemGC);
                support.installGCCause(GCCause.UnitTest);
                support.installGCCause(GCCause.TestGCInDeoptimizer);
                support.installGCCause(GCCause.HintedGC);
                support.installGCCause(GCCause.JvmtiForceGC);
                support.installGCCause(GCCause.HeapDump);
                support.installGCCause(GCCause.DiagnosticCommand);

                var gcCauseList = GCCause.getGCCauses();
                idToGCCauseName = (idx) -> gcCauseList.get(idx).getName();
            } else {
                var gcCauseNames = LayeredGCCauseTracker.getRegisteredGCCauses();
                idToGCCauseName = gcCauseNames::get;
            }
            access.registerObjectReplacer(obj -> {
                if (obj instanceof GCCause gcCause) {
                    if (!idToGCCauseName.apply(gcCause.getId()).equals(gcCause.getName())) {
                        var id = gcCause.getId();
                        VMError.shouldNotReachHere("Mismatch in GCCause name for id %s: %s %s", id, idToGCCauseName.apply(id), gcCause.getName());
                    }
                }
                return obj;
            });
        }
    }
}

/**
 * In layered builds all {@link GCCause}s are registered and installed in the initial layer. Here we
 * track which {@link GCCause}s were installed in the initial layer to detect issues.
 */
@AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
class LayeredGCCauseTracker implements LayeredImageSingleton {
    List<String> registeredGCCauses;

    public static List<String> getRegisteredGCCauses() {
        assert ImageLayerBuildingSupport.buildingExtensionLayer();
        return ImageSingletons.lookup(LayeredGCCauseTracker.class).registeredGCCauses;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        List<String> gcCauses;
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            gcCauses = GCCause.getGCCauses().stream().map(gcCause -> {
                if (gcCause == null) {
                    return "";
                } else {
                    assert !gcCause.getName().isEmpty() : Assertions.errorMessage("Empty string is reserved for non-existent GCCauses", gcCause);
                    return gcCause.getName();
                }
            }).toList();
        } else {
            gcCauses = registeredGCCauses;
        }
        writer.writeStringList("registeredGCCauses", gcCauses);
        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        var causeTracker = new LayeredGCCauseTracker();
        causeTracker.registeredGCCauses = Collections.unmodifiableList(loader.readStringList("registeredGCCauses"));
        return causeTracker;
    }
}
