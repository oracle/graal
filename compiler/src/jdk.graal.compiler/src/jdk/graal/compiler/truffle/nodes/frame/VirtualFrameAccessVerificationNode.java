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

package jdk.graal.compiler.truffle.nodes.frame;

import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.truffle.phases.FrameAccessVerificationPhase;

/**
 * Interface used to update the frame verification state in {@link FrameAccessVerificationPhase}.
 * <p>
 * Since the verification phase only verify coherent frames at merges, this interface does not
 * perform tag or access checks, it only provides the state to the phase.
 */
public interface VirtualFrameAccessVerificationNode extends ValueNodeInterface {
    VirtualFrameAccessType getType();

    int getFrameSlotIndex();

    NewFrameNode getFrame();

    /**
     * Frame accesses that modify the frame should implement this method to update the given
     * {@code state} using the given {@code updater}.
     * <p>
     * Due to how the {@link FrameAccessVerificationPhase verification phase} works, accesses that
     * do not modify the frame can provide a void implementation.
     *
     * @see VirtualFrameCopyNode#updateVerificationState(VirtualFrameVerificationStateUpdater,
     *      Object)
     * @see VirtualFrameGetNode#updateVerificationState(VirtualFrameVerificationStateUpdater,
     *      Object)
     */
    <State> void updateVerificationState(VirtualFrameVerificationStateUpdater<State> updater, State state);

    /**
     * Provides control over a given state representation of a virtual frame during
     * {@link FrameAccessVerificationPhase frame verification}.
     */
    interface VirtualFrameVerificationStateUpdater<State> {
        void set(State state, int slot, byte tag);

        void clear(State state, int slot);

        void copy(State state, int src, int dst);

        void swap(State state, int src, int dst);
    }
}
