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
package com.oracle.svm.hosted.thread;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaThreadsFeature;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class HostedJavaThreadsFeature extends JavaThreadsFeature {

    /**
     * All {@link Thread} objects that are reachable in the image heap. Only unstarted threads,
     * i.e., threads in state NEW, are allowed.
     */
    final Map<Thread, Boolean> reachableThreads = new ConcurrentIdentityHashMap<>();
    /**
     * All {@link ThreadGroup} objects that are reachable in the image heap. The value of the map is
     * a helper object storing information that is used by the field value recomputations.
     */
    final Map<ThreadGroup, ReachableThreadGroup> reachableThreadGroups = new ConcurrentIdentityHashMap<>();
    /** No new threads and thread groups can be discovered after the static analysis. */
    private boolean sealed;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::collectReachableObjects);

        /*
         * This currently only means that we don't support setting custom values for
         * java.lang.ScopedValue.cacheSize at runtime.
         */
        RuntimeClassInitialization.initializeAtBuildTime("java.lang.ScopedValue");
        RuntimeClassInitialization.initializeAtBuildTime("java.lang.ScopedValue$Cache");
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        a.registerFieldValueTransformer(ReflectionUtil.lookupField(ThreadGroup.class, "ngroups"), new FieldValueTransformerWithAvailability() {

            /*
             * We must wait until reachableThreadGroups stabilizes after analysis to replace this
             * value.
             */
            @Override
            public boolean isAvailable() {
                return BuildPhaseProvider.isHostedUniverseBuilt();
            }

            @Override
            public Object transform(Object receiver, Object originalValue) {
                ThreadGroup group = (ThreadGroup) receiver;
                return reachableThreadGroups.get(group).ngroups;
            }
        });

        a.registerFieldValueTransformer(ReflectionUtil.lookupField(ThreadGroup.class, "groups"), new FieldValueTransformerWithAvailability() {

            /*
             * We must wait until reachableThreadGroups stabilizes after analysis to replace this
             * value.
             */
            @Override
            public boolean isAvailable() {
                return BuildPhaseProvider.isHostedUniverseBuilt();
            }

            @Override
            public Object transform(Object receiver, Object originalValue) {
                ThreadGroup group = (ThreadGroup) receiver;
                return reachableThreadGroups.get(group).groups;
            }
        });
    }

    private Object collectReachableObjects(Object original) {
        if (original instanceof Thread) {
            Thread thread = (Thread) original;
            if (thread.getState() == Thread.State.NEW) {
                registerReachableObject(reachableThreads, thread, Boolean.TRUE);
            } else {
                /*
                 * Started Threads must not be in the image heap. The error is reported in
                 * DisallowedImageHeapObjectFeature (which is in a hosted project).
                 */
            }

        } else if (original instanceof ThreadGroup) {
            ThreadGroup group = (ThreadGroup) original;
            if (registerReachableObject(reachableThreadGroups, group, new ReachableThreadGroup())) {
                ThreadGroup parent = group.getParent();
                if (parent != null) {
                    /* Ensure ReachableThreadGroup object for parent is created. */
                    collectReachableObjects(parent);
                    /*
                     * Build the tree of thread groups that is then written out in the image heap.
                     * This tree is a subtree of all thread groups in the image generator,
                     * containing only the thread groups that were found as reachable at run time.
                     */
                    reachableThreadGroups.get(parent).add(group);
                } else {
                    assert group == PlatformThreads.singleton().systemGroup;
                }
            }
        }
        return original;
    }

    private <K, V> boolean registerReachableObject(Map<K, V> map, K object, V value) {
        boolean result = map.putIfAbsent(object, value) == null;
        if (sealed && result) {
            throw UserError.abort("%s is reachable in the image heap but was not seen during the points-to analysis: %s", ClassUtil.getUnqualifiedName(object.getClass()), object);
        }
        return result;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        /*
         * Because we do not make the reachableThreadGroups available until afterAnalysis, we must
         * ensure its contents are scanned during analysis.
         */
        var config = (FeatureImpl.DuringAnalysisAccessImpl) access;
        for (ReachableThreadGroup threadGroup : reachableThreadGroups.values()) {
            config.rescanObject(threadGroup.groups);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        /*
         * No more changes to the reachable threads and thread groups are allowed after the
         * analysis.
         */
        sealed = true;

        /*
         * Compute the maximum thread id and autonumber sequence from all reachable threads, and use
         * these numbers as the initial values at run time. This still means that numbers are not
         * dense when threads other than the main thread are reachable. However, changing the thread
         * id or autonumber of a thread that the user created during image generation would be an
         * intrusion with unpredictable side effects.
         */
        long maxThreadId = HostedJavaThreadsMetadata.singleton().maxThreadId;
        int maxAutonumber = HostedJavaThreadsMetadata.singleton().maxAutonumber;
        for (Thread thread : reachableThreads.keySet()) {
            maxThreadId = Math.max(maxThreadId, threadId(thread));
            maxAutonumber = Math.max(maxAutonumber, autonumberOf(thread));
        }

        assert maxThreadId >= 1 : "main thread with id 1 must always be found";

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            /*
             * Update max numbers seen
             */
            HostedJavaThreadsMetadata.singleton().maxThreadId = maxThreadId;
            HostedJavaThreadsMetadata.singleton().maxAutonumber = maxAutonumber;
        } else {
            /*
             * Store the values within the application layer singleton.
             */
            JavaThreads.JavaThreadNumberSingleton.singleton().setThreadNumberInfo(maxThreadId, maxAutonumber);
        }
    }

    private static final String AUTONUMBER_PREFIX = "Thread-";

    static int autonumberOf(Thread thread) {
        if (thread.getName().startsWith(AUTONUMBER_PREFIX)) {
            try {
                return Integer.parseInt(thread.getName().substring(AUTONUMBER_PREFIX.length()));
            } catch (NumberFormatException ex) {
                /*
                 * Ignore. If the suffix is not a valid integer number, then the thread name is not
                 * an autonumber name.
                 */
            }
        }
        return -1;
    }
}

class ReachableThreadGroup {
    int ngroups;
    ThreadGroup[] groups;

    /* Copy of ThreadGroup.add(). */
    synchronized void add(ThreadGroup g) {
        if (groups == null) {
            groups = new ThreadGroup[4];
        } else if (ngroups == groups.length) {
            groups = Arrays.copyOf(groups, ngroups * 2);
        }
        groups[ngroups] = g;
        ngroups++;
    }
}

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = HostedJavaThreadsMetadata.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
class HostedJavaThreadsMetadata {
    long maxThreadId;
    int maxAutonumber;

    HostedJavaThreadsMetadata() {
        this.maxThreadId = 0;
        this.maxAutonumber = -1;
    }

    static HostedJavaThreadsMetadata singleton() {
        return ImageSingletons.lookup(HostedJavaThreadsMetadata.class);
    }

    private HostedJavaThreadsMetadata(long maxThreadId, int maxAutonumber) {
        this.maxThreadId = maxThreadId;
        this.maxAutonumber = maxAutonumber;
    }

    public LayeredImageSingleton.PersistFlags preparePersist(ImageSingletonWriter writer) {
        writer.writeLong("maxThreadId", maxThreadId);
        writer.writeInt("maxAutonumber", maxAutonumber);
        return LayeredImageSingleton.PersistFlags.CREATE;
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            SingletonLayeredCallbacks action = new SingletonLayeredCallbacks() {
                @Override
                public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, Object singleton) {
                    return ((HostedJavaThreadsMetadata) singleton).preparePersist(writer);
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            };
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action);
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator {
        @Override
        public Object createFromLoader(ImageSingletonLoader loader) {
            long maxThreadId = loader.readLong("maxThreadId");
            int maxAutonumber = loader.readInt("maxAutonumber");

            return new HostedJavaThreadsMetadata(maxThreadId, maxAutonumber);
        }
    }
}
