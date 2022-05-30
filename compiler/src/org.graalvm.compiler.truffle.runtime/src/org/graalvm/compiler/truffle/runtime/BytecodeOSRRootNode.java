/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

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
        return ((GraalTruffleRuntime) Truffle.getRuntime()).getFrameMaterializeCalled(frameDescriptor);
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
