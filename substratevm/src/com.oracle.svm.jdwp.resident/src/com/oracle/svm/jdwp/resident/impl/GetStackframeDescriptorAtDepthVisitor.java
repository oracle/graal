/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident.impl;

import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.jdwp.resident.api.StackframeDescriptor;
import org.graalvm.word.Pointer;

public final class GetStackframeDescriptorAtDepthVisitor extends AbstractJDWPJavaFrameInfoVisitor implements StackframeDescriptor {
    private final int frameDepth;
    private FrameSourceInfo frameSourceInfo;
    private Pointer stackPointer;

    @Override
    public FrameSourceInfo getFrameSourceInfo() {
        return frameSourceInfo;
    }

    @Override
    public Pointer getStackPointer() {
        return stackPointer;
    }

    GetStackframeDescriptorAtDepthVisitor(boolean filterExceptions, int frameDepth) {
        super(filterExceptions);
        this.frameDepth = frameDepth;
    }

    @Override
    protected boolean processFrame(FrameSourceInfo frameInfo, Pointer sp) {
        super.processFrame(frameInfo, sp);
        // currentFrameDepth was incremented in super.processFrame(frameInfo, sp).

        if (currentFrameDepth - 1 == frameDepth) {
            this.frameSourceInfo = frameInfo;
            this.stackPointer = sp;
            return false;
        }

        return true;
    }
}
