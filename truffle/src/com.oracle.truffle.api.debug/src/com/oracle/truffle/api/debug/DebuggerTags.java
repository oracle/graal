/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;

/**
 * Set of debugger-specific tags. Language should {@link ProvidedTags provide} an implementation of
 * these tags in order to support specific debugging features.
 *
 * @since 0.14
 */
public final class DebuggerTags {

    private DebuggerTags() {
        // No instances
    }

    /**
     * Marks program locations where debugger should always halt like if on a breakpoint.
     * <p>
     * {@link TruffleLanguage}s that support concept similar to JavaScript's <code>debugger</code>
     * statement (program locations where execution should always halt) should make sure that
     * appropriate {@link Node}s are tagged with the {@link AlwaysHalt} tag.
     *
     * {@link com.oracle.truffle.api.debug.DebuggerTagsSnippets#debuggerNode}
     *
     * All created {@link DebuggerSession debugger sessions} will suspend on these locations
     * unconditionally.
     *
     * @since 0.14
     */
    public final class AlwaysHalt extends Tag {
        private AlwaysHalt() {
            /* No instances */
        }
    }

}

class DebuggerTagsSnippets {

    @SuppressWarnings("unused")
    public static Node debuggerNode() {
        // @formatter:off
        abstract
        // BEGIN: com.oracle.truffle.api.debug.DebuggerTagsSnippets#debuggerNode
        class DebuggerNode extends Node implements InstrumentableNode {

            public boolean hasTag(Class<? extends Tag> tag) {
                if (tag == DebuggerTags.AlwaysHalt.class) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        // END: com.oracle.truffle.api.debug.DebuggerTagsSnippets#debuggerNode
        // @formatter:on
        return null;
    }
}
