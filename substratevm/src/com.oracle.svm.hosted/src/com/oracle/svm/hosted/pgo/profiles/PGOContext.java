/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.pgo.profiles;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.hosted.pgo.ProfilingUtilities;

import jdk.vm.ci.code.BytecodePosition;

/**
 * The calling context of a profile entry or of a compiler query: its frames, innermost first. The
 * branch at bci 12 of {@code String.charAt}, inlined into {@code Foo.bar} at bci 5, has the context
 * {@code Ljava/lang/String;.charAt(I)C:12 < Lcom/example/Foo;.bar()V:5}. A frame names its method
 * by descriptor ({@link ProfilingUtilities#methodDescriptor}), so the context that
 * {@link IprofProfileResolver} builds from the profile's methods table equals the one that
 * {@link #of} builds from a compiler query.
 *
 * @param frames the frames, innermost first; copied, so the context does not change
 */
record PGOContext(List<Frame> frames) {

    /**
     * One frame of a context: a method, named by its descriptor, and a bytecode index in it.
     *
     * @param methodDescriptor the method, as {@link ProfilingUtilities#methodDescriptor} names it
     * @param bci the bytecode index in that method: the call site of an outer frame, or the
     *            profiled position of the innermost frame
     */
    record Frame(String methodDescriptor, int bci) {
        @Override
        public String toString() {
            return methodDescriptor + ":" + bci;
        }
    }

    /**
     * Creates a context with an immutable copy of {@code frames}.
     *
     * @param frames the frames, innermost first
     */
    PGOContext {
        frames = List.copyOf(frames);
    }

    /**
     * The context of a compiler query, innermost frame first.
     *
     * @param position the queried position, with its callers up to the compilation root
     * @return one frame per position from {@code position} outward
     */
    static PGOContext of(BytecodePosition position) {
        List<Frame> frames = new ArrayList<>();
        for (BytecodePosition current = position; current != null; current = current.getCaller()) {
            frames.add(new Frame(ProfilingUtilities.methodDescriptor(current.getMethod()), current.getBCI()));
        }
        return new PGOContext(frames);
    }

    /**
     * The number of frames.
     *
     * @return the number of frames
     */
    int depth() {
        return frames.size();
    }

    /**
     * The innermost {@code depth} frames: {@code foo:12} of {@code foo:12 < a:5}.
     *
     * @param depth how many frames to keep, from 0 to {@link #depth()}
     * @return a context of the innermost {@code depth} frames
     */
    PGOContext prefix(int depth) {
        return new PGOContext(frames.subList(0, depth));
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Frame frame : frames) {
            result.append(result.isEmpty() ? "" : " < ").append(frame);
        }
        return result.toString();
    }
}
