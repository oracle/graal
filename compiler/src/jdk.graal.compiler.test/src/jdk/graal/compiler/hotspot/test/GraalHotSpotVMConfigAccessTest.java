/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.test;

import java.util.function.Supplier;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.runtime.RuntimeProvider;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.common.JVMCIError;

public class GraalHotSpotVMConfigAccessTest {
    @Test
    public void test() {
        Assume.assumeTrue("Only expect error in JDK with explicit JVMCI version (e.g. labsjdk)", GraalHotSpotVMConfig.JVMCI);
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        GraalHotSpotVMConfig config = rt.getVMConfig();

        testErrorInternal(() -> config.getFieldAddress("unknownFieldAddress", "unknown"), "unknownFieldAddress");
        testErrorInternal(() -> config.getFieldOffset("unknownFieldOffset", Long.class), "unknownFieldOffset");
        testErrorInternal(() -> config.getFieldValue("unknownFieldValue", Long.class), "unknownFieldValue");
        testErrorInternal(() -> config.getFieldValue("unknownFieldValueCpp", Long.class, "type"), "unknownFieldValueCpp");

        testErrorInternal(() -> config.getFlag("unknownFlagBoolean", Boolean.class), "unknownFlagBoolean");
        testErrorInternal(() -> config.getFlag("unknownFlagByte", Byte.class), "unknownFlagByte");
        testErrorInternal(() -> config.getFlag("unknownFlagInteger", Integer.class), "unknownFlagInteger");
        testErrorInternal(() -> config.getFlag("unknownFlagLong", Long.class), "unknownFlagLong");
        testErrorInternal(() -> config.getFlag("unknownFlagString", String.class), "unknownFlagString");
        testErrorInternal(() -> config.getFlag("unknownFlagObject", Object.class), "unknownFlagObject");
    }

    private static void testErrorInternal(Supplier<Object> fn, String expectedName) {
        try {
            Object obj = fn.get();
            Assert.fail("JVMCIError expected, got " + obj);
        } catch (JVMCIError err) {
            Assert.assertTrue(err.getMessage().contains("VM config values missing"));
            Assert.assertTrue(err.getMessage().contains(expectedName));
        }
    }
}
