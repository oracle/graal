/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jdk.management.SubstrateThreadMXBean;
import com.oracle.svm.core.locks.Target_java_util_concurrent_locks_AbstractOwnableSynchronizer;
import com.oracle.svm.core.monitor.JavaMonitor;
import com.oracle.svm.core.thread.JavaThreads.JMXMonitoring;
import com.oracle.svm.core.thread.PlatformThreads;

import java.lang.management.ThreadInfo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.LockSupport;

/**
 * Utils to support {@link SubstrateThreadMXBean} for providing threading information for JMX
 * support. Include the {@link ThreadInfo} constructing utils, and deadlock detection utils.
 */
public class ThreadMXUtils {

    public static class ThreadInfoConstructionUtils {

        private static StackTraceElement[] getStackTrace(Thread thread, int maxDepth) {
            StackTraceElement[] stackTrace = thread.getStackTrace();
            return maxDepth == -1 || maxDepth >= stackTrace.length ? stackTrace : Arrays.copyOfRange(stackTrace, 0, maxDepth);
        }

        private record Blocker(Object blockObject, Thread ownerThread) {
        }

        private static Blocker getBlockerInfo(Thread thread) {
            Object blocker = LockSupport.getBlocker(thread);

            if (blocker instanceof JavaMonitor javaMonitor) {
                return new Blocker(
                                javaMonitor.getBlockedObject(),
                                SubstrateThreadMXBean.getThreadById(javaMonitor.getOwnerThreadId()));
            } else if (blocker instanceof AbstractOwnableSynchronizer synchronizer) {
                return new Blocker(synchronizer,
                                SubstrateUtil.cast(synchronizer, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class)
                                                .getExclusiveOwnerThread());
            }
            return new Blocker(blocker, null);
        }

        private static Object[] getLockedSynchronizers(Thread thread) {
            return JMXMonitoring.getThreadLocks(thread);
        }

        private record LockedMonitors(Object[] monitorObjects, int[] monitorDepths) {
        }

        private static LockedMonitors getLockedMonitors(Thread thread, int stacktraceLength) {
            List<JMXMonitoring.MonitorInfo> monitors = JMXMonitoring.getThreadMonitors(thread);
            Object[] monitorObjects = monitors.stream().map(JMXMonitoring.MonitorInfo::originalObject).toArray();
            int[] monitorDepths = monitors.stream().mapToInt(monitorInfo -> stacktraceLength < 0 ? -1 : stacktraceLength - monitorInfo.stacksize()).toArray();
            return new LockedMonitors(monitorObjects, monitorDepths);
        }

        private static int getThreadState(Thread thread, boolean inNative) {
            int state = PlatformThreads.getThreadStatus(thread);
            if (inNative) {
                // set the JMM thread state native flag to true:
                state |= 0x00400000;
            }
            return state;
        }

        public static ThreadInfo getThreadInfo(Thread thread, int maxDepth,
                        boolean withLockedMonitors, boolean withLockedSynchronizers) {
            Blocker blocker = getBlockerInfo(thread);
            StackTraceElement[] stackTrace = getStackTrace(thread, maxDepth);
            LockedMonitors lockedMonitors = getLockedMonitors(thread, stackTrace.length);
            boolean inNative = stackTrace.length > 0 && stackTrace[0].isNativeMethod();
            Target_java_lang_management_ThreadInfo targetThreadInfo = new Target_java_lang_management_ThreadInfo(
                            thread,
                            getThreadState(thread, inNative),
                            blocker.blockObject,
                            blocker.ownerThread,
                            JMXMonitoring.getThreadTotalBlockedCount(thread),
                            JMXMonitoring.getThreadTotalBlockedTime(thread),
                            JMXMonitoring.getThreadTotalWaitedCount(thread),
                            JMXMonitoring.getThreadTotalWaitedTime(thread),
                            stackTrace,
                            withLockedMonitors ? lockedMonitors.monitorObjects : new Object[0],
                            withLockedMonitors ? lockedMonitors.monitorDepths : new int[0],
                            withLockedSynchronizers ? getLockedSynchronizers(thread) : new Object[0]);
            return SubstrateUtil.cast(targetThreadInfo, ThreadInfo.class);
        }
    }

    public static class DeadlockDetectionUtils {
        /**
         * Returns an array of thread ids of blocked threads within some given array of ThreadInfo.
         *
         * @param threadInfos array of ThreadInfo for the threads among which the deadlocks should
         *            be detected
         * @param byMonitorOnly true if we are interested only in the deadlocks blocked exclusively
         *            on monitors
         * @return array containing thread ids of deadlocked threads
         */
        public static long[] findDeadlockedThreads(ThreadInfo[] threadInfos, boolean byMonitorOnly) {
            HashSet<Long> deadlocked = new HashSet<>();
            for (ThreadInfo threadInfo : threadInfos) {
                HashSet<Long> chain = new HashSet<>();
                ThreadInfo current = threadInfo;
                while (current != null && current.getLockInfo() != null && !deadlocked.contains(current.getThreadId())) {
                    if (!chain.add(current.getThreadId())) {
                        if (!byMonitorOnly || chain.stream().allMatch(DeadlockDetectionUtils::isBlockedByMonitor)) {
                            deadlocked.addAll(chain);
                        }
                        chain.clear();
                        break;
                    }
                    long currentLockOwnerId = current.getLockOwnerId();
                    current = Arrays.stream(threadInfos).filter(ti -> ti.getThreadId() == currentLockOwnerId).findAny().orElse(null);
                }
            }
            return deadlocked.stream().mapToLong(threadId -> threadId).toArray();
        }

        /**
         * Anything that is deadlocked can be blocked either by monitor (the object related to
         * JavaMonitor), or a lock (anything under AbstractOwnableSynchronizer).
         *
         * @return true if provided thread is blocked by a monitor
         */
        private static boolean isBlockedByMonitor(long threadId) {
            return LockSupport.getBlocker(SubstrateThreadMXBean.getThreadById(threadId)) instanceof JavaMonitor;
        }
    }
}
