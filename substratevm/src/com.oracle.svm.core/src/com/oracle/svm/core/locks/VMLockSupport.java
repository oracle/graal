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
package com.oracle.svm.core.locks;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.util.ImageHeapList;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Registry and lifecycle owner for VM-level locking primitives that are reachable in an image.
 * <p>
 * During image build, {@link VMMutex}, {@link VMCondition}, and {@link VMSemaphore} objects are
 * created as hosted placeholders. Those placeholders are then replaced with runtime implementations
 * that are backed by platform-specific state.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class VMLockSupport {
    /** All mutexes, so that we can initialize them at run time when the VM starts. */
    private final List<VMMutex> mutexes = ImageHeapList.create(VMMutex.class, Comparator.comparing(VMMutex::getName));
    /** All conditions, so that we can initialize them at run time when the VM starts. */
    private final List<VMCondition> conditions = ImageHeapList.create(VMCondition.class, Comparator.comparing(VMCondition::getName));
    /** All semaphores, so that we can initialize them at run time when the VM starts. */
    private final List<VMSemaphore> semaphores = ImageHeapList.create(VMSemaphore.class, Comparator.comparing(VMSemaphore::getName));

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ClassInstanceReplacer<VMMutex, VMMutex> mutexReplacer;

    @Fold
    public static VMLockSupport singleton() {
        return ImageSingletons.lookup(VMLockSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void registerObjectReplacers(InternalFeature.DuringSetupAccess access) {
        mutexReplacer = new ClassInstanceReplacer<>(VMMutex.class, mutexes, this::replaceVMMutex);

        access.registerObjectReplacer(mutexReplacer);
        access.registerObjectReplacer(new ClassInstanceReplacer<>(VMCondition.class, conditions, this::replaceVMCondition));
        access.registerObjectReplacer(new ClassInstanceReplacer<>(VMSemaphore.class, semaphores, this::replaceSemaphore));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private VMMutex replaceVMMutex(VMMutex source) {
        return new RuntimeVMMutex(source.getName());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private VMCondition replaceVMCondition(VMCondition source) {
        return new RuntimeVMCondition((RuntimeVMMutex) mutexReplacer.apply(source.getMutex()), source.getName());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private VMSemaphore replaceSemaphore(VMSemaphore source) {
        return new RuntimeVMSemaphore(source.getName());
    }

    /**
     * Initializes all {@link VMMutex}, {@link VMCondition}, and {@link VMSemaphore} objects.
     *
     * @return {@code true} if the initialization was successful, {@code false} if an error
     *         occurred.
     */
    @Uninterruptible(reason = "Too early for safepoints.")
    public final boolean initialize() {
        for (int i = 0; i < mutexes.size(); i++) {
            if (mutexes.get(i).initialize() != 0) {
                return false;
            }
        }
        for (int i = 0; i < conditions.size(); i++) {
            if (conditions.get(i).initialize() != 0) {
                return false;
            }
        }
        for (int i = 0; i < semaphores.size(); i++) {
            if (semaphores.get(i).initialize() != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Destroys all {@link VMMutex}, {@link VMCondition}, and {@link VMSemaphore} objects.
     */
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public final void destroy() {
        for (int i = 0; i < semaphores.size(); i++) {
            int code = semaphores.get(i).destroy();
            VMError.guarantee(code == 0, "VMSemaphore.destroy() failed.");
        }
        for (int i = 0; i < conditions.size(); i++) {
            int code = conditions.get(i).destroy();
            VMError.guarantee(code == 0, "VMCondition.destroy() failed.");
        }
        for (int i = 0; i < mutexes.size(); i++) {
            int code = mutexes.get(i).destroy();
            VMError.guarantee(code == 0, "VMMutex.destroy() failed.");
        }
    }

    public static class DumpVMMutexes extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("VM mutexes:").indent(true);

            VMLockSupport support = VMLockSupport.singleton();
            for (int i = 0; i < support.mutexes.size(); i++) {
                VMMutex mutex = support.mutexes.get(i);
                IsolateThread owner = mutex.owner;
                log.string("mutex \"").string(mutex.getName()).string("\" ");
                if (owner.isNull()) {
                    log.string("is unlocked.");
                } else {
                    log.string("is locked by ");
                    if (owner.equal(VMMutex.UNSPECIFIED_OWNER)) {
                        log.string("an unspecified thread.");
                    } else {
                        log.string("thread ").zhex(owner);
                    }
                }
                log.newline();
            }

            log.indent(false);
        }
    }
}

@AutomaticallyRegisteredFeature
final class VMLockFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            ImageSingletons.add(VMLockSupport.class, new VMLockSupport());
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            /* Replace build-time with run-time objects. */
            VMLockSupport support = ImageSingletons.lookup(VMLockSupport.class);
            support.registerObjectReplacers(access);
        } else {
            /* Extension layers must not add any extra VMLockingPrimitives. */
            Consumer<VMLockingPrimitive> disallowLockingPrimitives = (obj) -> {
                Class<?> clazz = obj.getClass();
                VMError.guarantee(clazz == RuntimeVMMutex.class || clazz == RuntimeVMCondition.class || clazz == RuntimeVMSemaphore.class,
                                "A VMLockingPrimitive is added in an extension layer, which is unsupported: %s", obj);
            };
            access.registerObjectReachabilityHandler(disallowLockingPrimitives, VMLockingPrimitive.class);
        }
    }
}
