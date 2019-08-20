/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.UserError;

@AutomaticFeature
@Platforms(Platform.HOSTED_ONLY.class)
class JavaThreadsFeature implements Feature {

    static JavaThreadsFeature singleton() {
        return ImageSingletons.lookup(JavaThreadsFeature.class);
    }

    /**
     * All {@link Thread} objects that are reachable in the image heap. Only unstarted threads,
     * i.e., threads in state NEW, are allowed.
     */
    final Map<Thread, Boolean> reachableThreads = Collections.synchronizedMap(new IdentityHashMap<>());
    /**
     * All {@link ThreadGroup} objects that are reachable in the image heap. The value of the map is
     * a helper object storing information that is used by the field value recomputations.
     */
    final Map<ThreadGroup, ReachableThreadGroup> reachableThreadGroups = Collections.synchronizedMap(new IdentityHashMap<>());
    /** No new threads and thread groups can be discovered after the static analysis. */
    private boolean sealed;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::collectReachableObjects);
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
                    assert group == JavaThreads.singleton().systemGroup;
                }
            }
        }
        return original;
    }

    private <K, V> boolean registerReachableObject(Map<K, V> map, K object, V value) {
        boolean result = map.putIfAbsent(object, value) == null;
        if (sealed && result) {
            throw UserError.abort(object.getClass().getSimpleName() + " is reachable in the image heap but was not seen during the points-to analysis: " + object);
        }
        return result;
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
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
        long maxThreadId = 0;
        int maxAutonumber = -1;
        for (Thread thread : reachableThreads.keySet()) {
            maxThreadId = Math.max(maxThreadId, threadId(thread));
            maxAutonumber = Math.max(maxAutonumber, autonumberOf(thread));
        }
        assert maxThreadId >= 1 : "main thread with id 1 must always be found";
        JavaThreads.singleton().threadSeqNumber.set(maxThreadId);
        JavaThreads.singleton().threadInitNumber.set(maxAutonumber);
    }

    static long threadId(Thread thread) {
        return thread == JavaThreads.singleton().mainThread ? 1 : thread.getId();
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

@Platforms(Platform.HOSTED_ONLY.class)
class ReachableThreadGroup {
    int ngroups;
    ThreadGroup[] groups;

    /* Copy of ThreadGroup.add(). */
    // Checkstyle: allow synchronization
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
