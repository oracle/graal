/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.hotspot.HotSpotBackend.Options.PreferGraalStubs;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.config;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.readPendingDeoptimization;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.writePendingDeoptimization;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.writeRegisterAsWord;

import com.oracle.graal.api.replacements.Fold;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.SaveAllRegistersNode;
import com.oracle.graal.hotspot.nodes.StubForeignCallNode;
import com.oracle.graal.hotspot.nodes.UncommonTrapCallNode;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.word.Word;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.LocationIdentity;

/**
 * Uncommon trap stub.
 *
 * This is the entry point for code which is returning to a de-optimized frame.
 *
 * The steps taken by this frame are as follows:
 *
 * <li>push a dummy "register_save" and save the return values (O0, O1, F0/F1, G1) and all
 * potentially live registers (at a pollpoint many registers can be live).
 *
 * <li>call the C routine: Deoptimization::fetch_unroll_info (this function returns information
 * about the number and size of interpreter frames which are equivalent to the frame which is being
 * deoptimized)
 *
 * <li>deallocate the unpack frame, restoring only results values. Other volatile registers will now
 * be captured in the vframeArray as needed.
 *
 * <li>deallocate the deoptimization frame
 *
 * <li>in a loop using the information returned in the previous step push new interpreter frames
 * (take care to propagate the return values through each new frame pushed)
 *
 * <li>create a dummy "unpack_frame" and save the return values (O0, O1, F0)
 *
 * <li>call the C routine: Deoptimization::unpack_frames (this function lays out values on the
 * interpreter frame which was just created)
 *
 * <li>deallocate the dummy unpack_frame
 *
 * <li>ensure that all the return values are correctly set and then do a return to the interpreter
 * entry point
 *
 * <p>
 * <b>ATTENTION: We cannot do any complicated operations e.g. logging via printf in this snippet
 * because we change the current stack layout and so the code is very sensitive to register
 * allocation.</b>
 */
public class UncommonTrapStub extends SnippetStub {

    public static final LocationIdentity STACK_BANG_LOCATION = NamedLocationIdentity.mutable("stack bang");

    private final TargetDescription target;

    public UncommonTrapStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(UncommonTrapStub.class, "uncommonTrapHandler", providers, linkage);
        this.target = target;
        assert PreferGraalStubs.getValue();
    }

    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        switch (index) {
            case 0:
                return providers.getRegisters().getThreadRegister();
            case 1:
                return providers.getRegisters().getStackPointerRegister();
            default:
                throw JVMCIError.shouldNotReachHere("unknown parameter " + name + " at index " + index);
        }
    }

    /**
     * Uncommon trap handler.
     *
     * We save the argument return registers. We call the first C routine, fetch_unroll_info(). This
     * routine captures the return values and returns a structure which describes the current frame
     * size and the sizes of all replacement frames. The current frame is compiled code and may
     * contain many inlined functions, each with their own JVM state. We pop the current frame, then
     * push all the new frames. Then we call the C routine unpack_frames() to populate these frames.
     * Finally unpack_frames() returns us the new target address. Notice that callee-save registers
     * are BLOWN here; they have already been captured in the vframeArray at the time the return PC
     * was patched.
     */
    @Snippet
    private static void uncommonTrapHandler(@ConstantParameter Register threadRegister, @ConstantParameter Register stackPointerRegister) {
        final Word thread = registerAsWord(threadRegister);
        final long registerSaver = SaveAllRegistersNode.saveAllRegisters();

        final int actionAndReason = readPendingDeoptimization(thread);
        writePendingDeoptimization(thread, -1);

        final Word unrollBlock = UncommonTrapCallNode.uncommonTrap(registerSaver, actionAndReason);

        DeoptimizationStub.deoptimizationCommon(stackPointerRegister, thread, registerSaver, unrollBlock, deoptimizationUnpackUncommonTrap());
    }

    /**
     * Reads the value of the passed register as a Word.
     */
    private static Word readRegister(Register register) {
        return registerAsWord(register, false, false);
    }

    /**
     * Writes the value of the passed register.
     *
     * @param value value the register should be set to
     */
    private static void writeRegister(Register register, Word value) {
        writeRegisterAsWord(register, value);
    }

    @Fold
    private static int stackShadowPages() {
        return config().useStackBanging ? config().stackShadowPages : 0;
    }

    /**
     * Returns the stack bias for the host architecture.
     *
     * @deprecated This method should go away as soon as JDK-8032410 hits the Graal repository.
     *
     * @return stack bias
     */
    @Deprecated
    @Fold
    private static int stackBias() {
        return config().stackBias;
    }

    @Fold
    private static int deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset() {
        return config().deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockCallerAdjustmentOffset() {
        return config().deoptimizationUnrollBlockCallerAdjustmentOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockNumberOfFramesOffset() {
        return config().deoptimizationUnrollBlockNumberOfFramesOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockTotalFrameSizesOffset() {
        return config().deoptimizationUnrollBlockTotalFrameSizesOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockFrameSizesOffset() {
        return config().deoptimizationUnrollBlockFrameSizesOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockFramePcsOffset() {
        return config().deoptimizationUnrollBlockFramePcsOffset;
    }

    @Fold
    private static int deoptimizationUnrollBlockInitialInfoOffset() {
        return config().deoptimizationUnrollBlockInitialInfoOffset;
    }

    @Fold
    private static int deoptimizationUnpackDeopt() {
        return config().deoptimizationUnpackDeopt;
    }

    @Fold
    private static int deoptimizationUnpackUncommonTrap() {
        return config().deoptimizationUnpackUncommonTrap;
    }

    @NodeIntrinsic(value = StubForeignCallNode.class, setStampFromReturnType = true)
    public static native int unpackFrames(@ConstantNodeParameter ForeignCallDescriptor unpackFrames, Word thread, int mode);
}
