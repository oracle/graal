/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.test;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.jdk.jfr.JfrOptions;

public class JfrOptionsTest {

    // Taken from GlobalDefinitions.hpp
    private static final long K = 1024;
    private static final long M = K*K;
    private static final long G = M*K;

    @Test
    public void testBasicOptions() {
        JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(), "dumponexit=true");
        Assert.assertEquals(JfrOptions.getDumpOnExit(), true);
    }

    @Test
    public void testValidMemoryOptions() {
        JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(), "memorysize=256m," +
                "globalbuffersize=128m,globalbuffercount=2,threadbuffersize=8k");
        Assert.assertEquals(JfrOptions.getGlobalBufferSize(), 128 * M);
        Assert.assertEquals(JfrOptions.getMemorySize(), 256*M);
        Assert.assertEquals(JfrOptions.getGlobalBufferCount(), 2);
    }

    @Test
    public void testInvalidMemoryOptions() {
        try {
            JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(), "memorysize=1k");
        } catch (IllegalArgumentException e) {
            // Pass
            return;
        }
        Assert.fail();
    }

    @Test
    public void testBadInput() {
        try {
            JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(), "memorysize=thisShouldFail");
        } catch (Exception e) {
            // Pass
            return;
        }
        Assert.fail();
    }

    @Test
    public void testTimeOptions() {
        JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(),"delay=10s,maxage=1h,duration=2m");
        Assert.assertEquals(10000, JfrOptions.getRecordingDelay());
        Assert.assertEquals(360000, JfrOptions.getMaxAge());
        Assert.assertEquals(120000, JfrOptions.getDuration());
    }

    @Test
    public void testMaxStackDepth() {
        try {
            JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(),"stackdepth=9000");
        } catch (Exception e) {
            // Pass
            return;
        }
        Assert.fail();
    }

    @Test
    public void testNegativeStackDepth() {
        try {
            JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(),"stackdepth=-10");
        } catch (Exception e) {
            // Pass
            return;
        }
        Assert.fail();
    }

    @Test
    public void testAllArguments() {
        JfrOptions.StartFlightRecordingOption.update(EconomicMap.create(),
                "maxchunksize=1k,globalbuffersize=1m,memorysize=2m,retransform=true," +
                        "stackdepth=10,sampleprotection=true,samplethreads=true,old-object-queue-size=100," +
                        "numglobalbuffers=2,threadbuffersize=4k,repository=/path/to/repository,dumponexit=false," +
                        "name=recording,settings=/path/to/settings,delay=10s,duration=1m,disk=true,maxage=1h," +
                        "maxsize=10k,path-to-gc-roots=true");
        Assert.assertEquals(1*K, JfrOptions.getMaxChunkSize());
        Assert.assertEquals(1*M, JfrOptions.getGlobalBufferSize());
        Assert.assertEquals(2, JfrOptions.getGlobalBufferCount());
        Assert.assertEquals(2*M, JfrOptions.getMemorySize());
        Assert.assertEquals(true, JfrOptions.isRetransformEnabled());
        Assert.assertEquals(10, JfrOptions.getStackDepth());
        Assert.assertEquals(true, JfrOptions.isSampleProtectionEnabled());
        Assert.assertEquals(true, JfrOptions.isSampleThreadsEnabled());
        Assert.assertEquals(100, JfrOptions.getObjectQueueSize());
        Assert.assertEquals(2, JfrOptions.getGlobalBufferCount());
        Assert.assertEquals(4*K, JfrOptions.getThreadBufferSize());
        Assert.assertEquals("/path/to/repository", JfrOptions.getRepositoryLocation());
        Assert.assertEquals(false, JfrOptions.getDumpOnExit());
        Assert.assertEquals("recording", JfrOptions.getRecordingName());
        Assert.assertEquals("/path/to/settings", JfrOptions.getRecordingSettingsFile());
        Assert.assertEquals(10000, JfrOptions.getRecordingDelay());
        Assert.assertEquals(60000, JfrOptions.getDuration());
        Assert.assertEquals(true, JfrOptions.isPersistedToDisk());
        Assert.assertEquals(360000, JfrOptions.getMaxAge());
        Assert.assertEquals(10*K, JfrOptions.getMaxRecordingSize());
        Assert.assertEquals(true, JfrOptions.trackPathToGcRoots());
    }

}