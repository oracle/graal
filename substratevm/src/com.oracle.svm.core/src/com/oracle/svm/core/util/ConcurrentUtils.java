/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.svm.shared.collections.ConcurrentIdentityHashMap;
import com.oracle.svm.shared.util.VMError;

public class ConcurrentUtils {
    /**
     * Ensures the provided {@link Runnable} will execute only once per singleton object. Regardless
     * of which thread executes the runnable, this method will not return until the runnable has
     * been executed. Currently, the implementation ensures that at most one {@link Runnable} is
     * executed per singleton object at any time.
     * 
     * @param singleton the singleton object to be used as a key in the status map
     * @param runnable the runnable to be executed
     * @param callbackStatus the status map used to synchronize across threads
     */
    public static void synchronizeRunnableExecution(Object singleton, Runnable runnable, ConcurrentIdentityHashMap<Object, Object> callbackStatus) {
        while (true) {
            var status = callbackStatus.get(singleton);
            if (status == null) {
                // create a lock for other threads to wait on
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try {
                    status = callbackStatus.computeIfAbsent(singleton, _ -> lock);
                    if (status != lock) {
                        // Failed to install lock. Repeat loop.
                        continue;
                    }

                    // Run runnable.
                    runnable.run();

                    // The runnable has finished - update its status.
                    var prev = callbackStatus.put(singleton, Boolean.TRUE);
                    VMError.guarantee(prev == lock);
                } finally {
                    lock.unlock();
                }
            } else if (status instanceof Lock lock) {
                lock.lock();
                try {
                    // Once the lock can be acquired we know the runnable has been completed and we
                    // can proceed.
                    assert callbackStatus.get(singleton) == Boolean.TRUE;
                } finally {
                    lock.unlock();
                }
            } else {
                // The runnable has already completed.
                assert status == Boolean.TRUE;
            }
            // At this point the runnable has executed so it is safe to proceed.
            break;
        }
    }
}
