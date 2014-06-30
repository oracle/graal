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
package com.oracle.graal.compiler.hsail.test.lambda;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.runtime;
import com.oracle.graal.hotspot.HotSpotVMConfig;
import static org.junit.Assert.*;
import org.junit.*;

import java.util.*;
import java.util.stream.IntStream;

public class ReduceMaxTest {
    // The length of the input array
    static int jobSize = 1027 * 1023 * 13;
    static int loops = 1;

    // The source array
    int bigArray[] = null;

    // result for baseline single threaded stream
    int resultStream = 0;
    // result for parallel CPU and offloaded streams
    int resultOffload = 0;

    int evaluate(boolean doParallelStream) {
        int result = 0;
        for (int i = 0; i < loops; i++) {
            IntStream s = Arrays.stream(bigArray);
            if (doParallelStream == true) {
                OptionalInt resultParallel = s.parallel().reduce(Integer::max);
                result = resultParallel.getAsInt();
            } else {
                result = s.reduce(Integer::max).getAsInt();
            }
        }
        return result;
    }

    int evaluateWithIdentity(boolean doParallelStream) {
        int result = 0;
        for (int i = 0; i < loops; i++) {
            IntStream s = Arrays.stream(bigArray);
            if (doParallelStream == true) {
                result = s.parallel().reduce(0, Integer::max);
            } else {
                result = s.reduce(0, Integer::max);
            }
        }
        return result;
    }

    @Test
    public void testReduce() {
        // Handmade reduce does not support +UseCompressedOops
        HotSpotVMConfig config = runtime().getConfig();
        if (config.useCompressedOops == true || config.useHSAILDeoptimization == true) {
            return;
        }

        bigArray = new int[jobSize];
        for (int i = 0; i < jobSize; i++) {
            // bigArray[i] = i + 1;
            bigArray[i] = -1024 + i + 1;
        }

        // Get non parallel baseline
        resultStream = evaluate(false);

        // Get OptionalInt version kernel
        resultOffload = evaluate(true);
        assertTrue(resultStream == resultOffload);

        // Do identity version kernel
        // Get non parallel baseline
        resultStream = evaluateWithIdentity(false);

        resultOffload = evaluateWithIdentity(true);
        assertTrue(resultStream == resultOffload);
    }
}
