/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

import java.io.IOException;
import java.util.Map;

import org.graalvm.graphio.GraphOutput;

/**
 * Functionality related to generating output useful for debugging compilation of Truffle ASTs.
 */
public interface TruffleDebugContext extends AutoCloseable {

    /**
     * Gets a map from components in the current runtime to the id of the source code control commit
     * from which they were built.
     *
     * A typical map when running on GraalVM is shown below:
     *
     * <pre>
     * {version.substratevm=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.tools=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.truffle=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.compiler=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.sulong=d453fd9d5e17a5fd1739cc4c77f7995852ace14f,
     *  version.sdk=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.vm=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.graal-js=c32bfc97c8435f285055e289d193e3b06e6b40e5,
     *  version.regex=f5875dc5cd664df832f3ef6546fe3ab3610152a9,
     *  version.graal-nodejs=c32bfc97c8435f285055e289d193e3b06e6b40e5}
     * </pre>
     */
    Map<Object, Object> getVersionProperties();

    /**
     * Opens a channel for dumping the graph based structure represented by {@code builder}.
     */
    <G, N, M> GraphOutput<G, M> buildOutput(GraphOutput.Builder<G, N, M> builder) throws IOException;

    /**
     * Determines if dumping is enabled in the current scope.
     */
    boolean isDumpEnabled();

    /**
     * Opens a named scope. The implementation will typically rely on command line options based on
     * scope names to decide in which scopes dumping is {@linkplain #isDumpEnabled() enabled}.
     */
    AutoCloseable scope(String name);

    /**
     * Opens a named scope. The implementation will typically rely on command line options based on
     * scope names to decide in which scopes dumping is {@linkplain #isDumpEnabled() enabled}.
     *
     * @param context some context to be associated with the scope
     */
    AutoCloseable scope(String name, Object context);

    /**
     * Closes this debug context.
     */
    @Override
    void close();

    /**
     * Closes any debugging output channels open in this scope. This is useful when debug output is
     * structured and closing a channel sends output denoting the end of a nested data structure.
     */
    void closeDebugChannels();
}
