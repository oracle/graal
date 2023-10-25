/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

final class BytecodeOSRRootNode extends BaseOSRRootNode {
    private final int target;
    private final Object interpreterState;
    @CompilationFinal private boolean seenMaterializedFrame;

    private final Object entryTagsCache;

    BytecodeOSRRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, BytecodeOSRNode bytecodeOSRNode, int target, Object interpreterState, Object entryTagsCache) {
        super(language, frameDescriptor, bytecodeOSRNode);
        this.target = target;
        this.interpreterState = interpreterState;
        this.seenMaterializedFrame = materializeCalled(frameDescriptor);
        this.entryTagsCache = entryTagsCache;

        // Support for deprecated frame transfer: GR-38296
        this.usesDeprecatedFrameTransfer = checkUsesDeprecatedFrameTransfer(bytecodeOSRNode.getClass());
    }

    private static boolean materializeCalled(FrameDescriptor frameDescriptor) {
        return ((OptimizedTruffleRuntime) Truffle.getRuntime()).getFrameMaterializeCalled(frameDescriptor);
    }

    Object getEntryTagsCache() {
        return entryTagsCache;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Object executeOSR(VirtualFrame frame) {
        BytecodeOSRNode osrNode = (BytecodeOSRNode) loopNode;
        VirtualFrame parentFrame = (VirtualFrame) osrNode.restoreParentFrameFromArguments(frame.getArguments());

        if (!seenMaterializedFrame) {
            // We aren't expecting a materialized frame. If we get one, deoptimize.
            if (materializeCalled(parentFrame.getFrameDescriptor())) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenMaterializedFrame = true;
            }
        }

        if (seenMaterializedFrame) {
            // If materialize has ever happened, just use the parent frame.
            // This will be slower, since we cannot do scalar replacement on the frame, but it is
            // required to prevent the materialized frame from getting out of sync during OSR.
            return osrNode.executeOSR(parentFrame, target, interpreterState);
        } else {
            if (usesDeprecatedFrameTransfer) { // Support for deprecated frame transfer: GR-38296
                osrNode.copyIntoOSRFrame(frame, parentFrame, target);
            } else {
                osrNode.copyIntoOSRFrame(frame, parentFrame, target, entryTagsCache);
            }
            try {
                return osrNode.executeOSR(frame, target, interpreterState);
            } finally {
                osrNode.restoreParentFrame(frame, parentFrame);
            }
        }
    }

    @Override
    public String getName() {
        return toString();
    }

    @Override
    public String toString() {
        return loopNode.toString() + "<OSR@" + target + ">";
    }

    // GR-38296
    /* Deprecated frame transfer handling below */

    private static final Map<Class<?>, Boolean> usesDeprecatedTransferClasses = new ConcurrentHashMap<>();

    private final boolean usesDeprecatedFrameTransfer;

    /**
     * Detects usage of deprecated frame transfer, and directs the frame transfer path accordingly
     * later. When removing the support for this deprecation, constructs used and paths related are
     * marked with the comment "Support for deprecated frame transfer" and a reference to GR-38296.
     */
    private static boolean usesDeprecatedFrameTransfer(Class<?> osrNodeClass) {
        try {
            Method m = osrNodeClass.getMethod("copyIntoOSRFrame", VirtualFrame.class, VirtualFrame.class, int.class);
            return m.getDeclaringClass() != BytecodeOSRNode.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean checkUsesDeprecatedFrameTransfer(Class<?> osrNodeClass) {
        if (ImageInfo.inImageRuntimeCode()) {
            // this must have been pre-computed
            return usesDeprecatedTransferClasses.get(osrNodeClass);
        } else {
            return usesDeprecatedTransferClasses.computeIfAbsent(osrNodeClass, BytecodeOSRRootNode::usesDeprecatedFrameTransfer);
        }
    }

    // Called by truffle feature to initialize the map at build time.
    @SuppressWarnings("unused")
    private static boolean initializeClassUsingDeprecatedFrameTransfer(Class<?> subType) {
        if (subType.isInterface()) {
            return false;
        }
        if (usesDeprecatedTransferClasses.containsKey(subType)) {
            return false;
        }
        // Eagerly initialize result.
        usesDeprecatedTransferClasses.put(subType, usesDeprecatedFrameTransfer(subType));
        return true;
    }

}
