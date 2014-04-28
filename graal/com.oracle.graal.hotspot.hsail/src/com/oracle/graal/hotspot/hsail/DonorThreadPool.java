/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.hsail;

import static com.oracle.graal.hotspot.hsail.HSAILHotSpotBackend.Options.*;

import java.util.concurrent.*;

import com.oracle.graal.hotspot.hsail.HSAILHotSpotBackend.Options;

/**
 * Thread pool for HSAIL allocation support.
 */
public class DonorThreadPool {

    private final Thread[] threads;

    void waitAt(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a pool of threads whose size is given by {@link Options#HsailDonorThreads}.
     */
    DonorThreadPool() {
        int size = HsailDonorThreads.getValue();
        this.threads = new Thread[size];
        CyclicBarrier barrier = new CyclicBarrier(size + 1);

        // fill in threads
        for (int i = 0; i < size; i++) {
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        waitAt(barrier);
                    }
                }
            }, "HsailDonorThread-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        // creating thread waits at barrier to make sure others have started
        waitAt(barrier);
    }

    public Thread[] getThreads() {
        return threads;
    }
}