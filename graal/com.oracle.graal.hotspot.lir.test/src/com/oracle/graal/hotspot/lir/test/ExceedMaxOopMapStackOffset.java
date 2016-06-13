/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.lir.test;

import org.junit.Test;

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.hotspot.HotSpotReferenceMapBuilder;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.jtt.LIRTest;
import com.oracle.graal.lir.jtt.LIRTestSpecification;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ExceedMaxOopMapStackOffset extends LIRTest {

    /**
     * Allocate lots of stacks slots and initialize them with a constant.
     */
    private static class WriteStackSlotsSpec extends LIRTestSpecification {
        private final JavaConstant constant;

        WriteStackSlotsSpec(JavaConstant constant) {
            this.constant = constant;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder frameMapBuilder = gen.getResult().getFrameMapBuilder();
            LIRKind lirKind = LIRKind.reference(gen.target().arch.getPlatformKind(constant.getJavaKind()));
            // create slots
            for (int i = 0; i < slots.length; i++) {
                AllocatableValue src = gen.emitLoadConstant(lirKind, constant);
                slots[i] = frameMapBuilder.allocateSpillSlot(lirKind);
                gen.emitMove(slots[i], src);
            }
        }
    }

    /**
     * Read stacks slots and move their content into a blackhole.
     */
    private static class ReadStackSlotsSpec extends LIRTestSpecification {

        ReadStackSlotsSpec() {
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            for (int i = 0; i < slots.length; i++) {
                gen.emitBlackhole(gen.emitMove(slots[i]));
            }
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugin safepointPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new SafepointNode());
                return true;
            }
        };
        conf.getPlugins().getInvocationPlugins().register(safepointPlugin, getClass(), "safepoint");
        return super.editGraphBuilderConfiguration(conf);
    }

    /*
     * Safepoint Snippet
     */
    private static void safepoint() {
    }

    private static AllocatableValue[] slots;

    private static final LIRTestSpecification readStacks = new ReadStackSlotsSpec();

    @SuppressWarnings("unused")
    @LIRIntrinsic
    public static void instrinsic(LIRTestSpecification spec) {
    }

    private static final LIRTestSpecification writeFirstObjectStacks = new WriteStackSlotsSpec(JavaConstant.NULL_POINTER);

    public Object testFirstStackObject(Object obj) {
        instrinsic(writeFirstObjectStacks);
        safepoint();
        instrinsic(readStacks);
        return obj;
    }

    @Test
    public void runFirstStackObject() throws Throwable {
        int max = HotSpotReferenceMapBuilder.HotSpotVMConfigCompat.maxOopMapStackOffset;
        if (max == Integer.MAX_VALUE) {
            max = 16 * 1024 - 64;
        }
        try {
            int numSlots = (max / 8) + 1;
            slots = new AllocatableValue[numSlots];
            runTest("testFirstStackObject", this);
        } catch (BailoutException e) {
            return;
        }
        fail("Expected exception BailoutException wasn't thrown");
    }
}
