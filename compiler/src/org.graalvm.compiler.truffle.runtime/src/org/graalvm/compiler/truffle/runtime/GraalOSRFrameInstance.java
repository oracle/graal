package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.frame.Frame;
import jdk.vm.ci.code.stack.InspectedFrame;

/**
 * Represents a Truffle {@link com.oracle.truffle.api.frame.FrameInstance} where OSR occurred.
 * 
 * Contains a separate field for the {@link InspectedFrame} containing the most up-to-date Frame.
 */
public final class GraalOSRFrameInstance extends GraalFrameInstance {
    InspectedFrame osrFrame;

    public GraalOSRFrameInstance(InspectedFrame callTargetFrame, InspectedFrame callNodeFrame, InspectedFrame osrFrame) {
        super(callTargetFrame, callNodeFrame);
        this.osrFrame = osrFrame;
    }

    @Override
    public Frame getFrame(FrameAccess access) {
        return getFrameFrom(osrFrame, access);
    }

    @Override
    public boolean isVirtualFrame() {
        return osrFrame.isVirtual(FRAME_INDEX);
    }
}
