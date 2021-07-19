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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the metadata required to perform OSR compilation on Graal. An instance of this class
 * is stored in the metadata field of a {@link BytecodeOSRNode}.
 * 
 * <p>
 * Performance note: We do not require the metadata field to be {@code volatile}. As long as the
 * field is initialized under double-checked locking (as is done in
 * {@link GraalRuntimeSupport#onOSRBackEdge}, all threads will observe the same metadata instance.
 * The JMM guarantees that the instance's final fields will be safely initialized before it is
 * published; the non-final + non-volatile fields (e.g., the back edge counter) may not be, but we
 * tolerate this inaccuracy in order to avoid volatile accesses in the hot path.
 */
public final class BytecodeOSRMetadata {
    // Marker object to indicate that OSR is disabled.
    public static final BytecodeOSRMetadata DISABLED = new BytecodeOSRMetadata(null, null, Integer.MAX_VALUE);
    // Must be a power of 2 (polling uses bit masks)
    public static final int OSR_POLL_INTERVAL = 1024;

    private final BytecodeOSRNode osrNode;
    private final FrameDescriptor frameDescriptor;
    private volatile Map<Integer, OptimizedCallTarget> osrCompilations;
    private final int osrThreshold;
    private int backEdgeCount;
    private volatile boolean compilationFailed;

    // Used for OSR state transfer.
    @CompilationFinal private volatile Assumption frameVersion;
    @CompilationFinal(dimensions = 1) private volatile FrameSlot[] frameSlots;
    @CompilationFinal(dimensions = 1) private volatile byte[] frameTags;

    BytecodeOSRMetadata(BytecodeOSRNode osrNode, FrameDescriptor frameDescriptor, int osrThreshold) {
        this.osrNode = osrNode;
        if (frameDescriptor != null && frameDescriptor.canMaterialize()) {
            throw new IllegalArgumentException("Cannot perform OSR on a frame which can be materialized.");
        }
        this.frameDescriptor = frameDescriptor;
        this.osrCompilations = null;
        this.osrThreshold = osrThreshold;
        this.backEdgeCount = 0;
        this.compilationFailed = false;
    }

    Object onOSRBackEdge(VirtualFrame parentFrame, int target) {
        if (!incrementAndPoll() || compilationFailed) {
            // note: incur volatile read of compilationFailed only if poll succeeds
            return null;
        }

        OptimizedCallTarget osrTarget;
        if (osrCompilations == null) {
            ((Node) osrNode).atomic(() -> {
                if (osrCompilations == null) {
                    osrCompilations = new ConcurrentHashMap<>();
                }
            });
        }

        osrTarget = osrCompilations.get(target);
        if (osrTarget == null) {
            // Lock to request compilation.
            osrTarget = ((Node) osrNode).atomic(() -> {
                OptimizedCallTarget lockedTarget = osrCompilations.get(target);
                if (lockedTarget == null) {
                    lockedTarget = requestOSR(target);
                    if (lockedTarget != null) {
                        osrCompilations.put(target, lockedTarget);
                    }
                }
                return lockedTarget;
            });
        }

        if (osrTarget != null && !osrTarget.isCompiling()) {
            if (!osrTarget.isValid()) {
                invalidateOSRTarget(target, "OSR compilation failed or cancelled");
                return null;
            }
            return osrTarget.callOSR(parentFrame);
        }
        return null;
    }

    /**
     * Increment back edge count and return whether compilation should be polled.
     *
     * When the OSR threshold is reached, this method will return true after every OSR_POLL_INTERVAL
     * back-edges. This method is thread-safe, but could under-count.
     */
    private boolean incrementAndPoll() {
        int newBackEdgeCount = ++backEdgeCount; // Omit overflow check; OSR should trigger long
                                                // before overflow happens
        return (newBackEdgeCount >= osrThreshold && (newBackEdgeCount & (OSR_POLL_INTERVAL - 1)) == 0);
    }

    private synchronized OptimizedCallTarget requestOSR(int target) {
        assert osrCompilations != null && !osrCompilations.containsKey(target);
        TruffleLanguage<?> language = GraalRuntimeAccessor.NODES.getLanguage(((Node) osrNode).getRootNode());
        updateFrameSlots();
        OptimizedCallTarget callTarget = GraalTruffleRuntime.getRuntime().createOSRCallTarget(new BytecodeOSRRootNode(osrNode, target, language, frameDescriptor));
        callTarget.compile(false);
        if (callTarget.isCompilationFailed()) {
            markCompilationFailed();
            return null;
        }
        osrCompilations.put(target, callTarget);
        return callTarget;
    }

    private synchronized void updateFrameSlots() {
        CompilerAsserts.neverPartOfCompilation();
        if (!Assumption.isValidAssumption(this.frameVersion)) {
            // Another thread could modify the frame in the middle of this method.
            // If we get the frame slots before the assumption, the slots may be updated in between,
            // and we might obtain the new (valid) assumption, despite our slots actually being
            // stale. Get the assumption first to avoid this race.
            Assumption newFrameVersion = frameDescriptor.getVersion();
            FrameSlot[] newFrameSlots = frameDescriptor.getSlots().toArray(new FrameSlot[0]);
            byte[] newFrameTags = new byte[newFrameSlots.length];
            for (int i = 0; i < newFrameSlots.length; i++) {
                newFrameTags[i] = frameDescriptor.getFrameSlotKind(newFrameSlots[i]).tag;
            }
            this.frameVersion = newFrameVersion;
            this.frameSlots = newFrameSlots;
            this.frameTags = newFrameTags;
        }
    }

    /**
     * Transfer state from {@code source} to {@code target}. Can be used to transfer state into (or
     * out of) an OSR frame.
     */
    @ExplodeLoop
    public void executeTransfer(FrameWithoutBoxing source, FrameWithoutBoxing target) {
        // The frames should use the same descriptor.
        if (source.getFrameDescriptor() != frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Source frame descriptor is different from the descriptor used for compilation.");
        } else if (target.getFrameDescriptor() != frameDescriptor) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalArgumentException("Target frame descriptor is different from the descriptor used for compilation.");
        }

        // The frame version could have changed; if so, deoptimize and update the slots+tags.
        if (!frameVersion.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateFrameSlots();
        }

        for (int i = 0; i < frameSlots.length; i++) {
            FrameSlot slot = frameSlots[i];
            byte expectedTag = frameTags[i];
            byte actualTag = source.getTag(slot);

            // The tag for this slot may have changed; if so, deoptimize and update it.
            boolean tagsCondition = expectedTag == actualTag;
            if (!tagsCondition) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frameTags[i] = actualTag;
                expectedTag = actualTag;
            }
            switch (expectedTag) {
                case FrameWithoutBoxing.BOOLEAN_TAG:
                    target.setBoolean(slot, source.getBooleanUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.BYTE_TAG:
                    target.setByte(slot, source.getByteUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.DOUBLE_TAG:
                    target.setDouble(slot, source.getDoubleUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.FLOAT_TAG:
                    target.setFloat(slot, source.getFloatUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.INT_TAG:
                    target.setInt(slot, source.getIntUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.LONG_TAG:
                    target.setLong(slot, source.getLongUnsafe(i, slot, tagsCondition));
                    break;
                case FrameWithoutBoxing.OBJECT_TAG:
                    target.setObject(slot, source.getObjectUnsafe(i, slot, tagsCondition));
                    break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("Defined frame slot " + slot + " is illegal. Please initialize frame slot with a FrameSlotKind.");
            }
        }
    }

    private synchronized void invalidateOSRTarget(int target, CharSequence reason) {
        if (osrCompilations != null) {
            OptimizedCallTarget callTarget = osrCompilations.remove(target);
            if (callTarget != null) {
                if (callTarget.isCompilationFailed()) {
                    markCompilationFailed();
                }
                callTarget.invalidate(reason);
            }
        }
    }

    synchronized void nodeReplaced(Node oldNode, Node newNode, CharSequence reason) {
        if (osrCompilations != null) {
            for (OptimizedCallTarget callTarget : osrCompilations.values()) {
                if (callTarget != null) {
                    if (callTarget.isCompilationFailed()) {
                        markCompilationFailed();
                    }
                    callTarget.nodeReplaced(oldNode, newNode, reason);
                }
            }
            osrCompilations.clear();
        }
    }

    private void markCompilationFailed() {
        /*
         * Replace this object with the DISABLED marker object. Another thread may already have a
         * handle to this object, so also mark the failure internally so we can avoid recompilation.
         */
        compilationFailed = true;
        osrNode.setOSRMetadata(DISABLED);
    }

    // for testing
    public Map<Integer, OptimizedCallTarget> getOSRCompilations() {
        return osrCompilations;
    }

    public int getBackEdgeCount() {
        return backEdgeCount;
    }
}
