/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to help run multiple virtual threads executing some task. Reflection is used for JDK 19+
 * virtual thread APIs for compatibility with JDK 17. Once JDK 17 support ends, the reflection can
 * be replaced.
 */
public class VirtualStressor {
    @SuppressWarnings("preview")
    public static void execute(int numberOfThreads, Runnable task) throws Exception {
        join(executeAsync(numberOfThreads, task));
    }

    @SuppressWarnings("preview")
    public static List<Thread> executeAsync(int numberOfThreads, Runnable task) throws Exception {
        List<Thread> threads = new ArrayList<>();
        for (int n = 0; n < numberOfThreads; ++n) {
            Method startVirtualThread = Thread.class.getMethod("startVirtualThread", Runnable.class);
            Thread t = (Thread) startVirtualThread.invoke(null, task);
            threads.add(t);
        }
        return threads;
    }

    public static void join(List<Thread> threads) throws Exception {
        for (Thread t : threads) {
            t.join();
        }
    }
}
