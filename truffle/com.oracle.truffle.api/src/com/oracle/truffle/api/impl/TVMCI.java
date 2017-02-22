/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.Accessor.InstrumentSupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * An interface between Truffle API and hosting virtual machine. Not interesting for regular Truffle
 * API users. Acronym for Truffle Virtual Machine Compiler Interface.
 *
 * @since 0.12
 */
public abstract class TVMCI {
    /**
     * Only useful for virtual machine implementors.
     *
     * @since 0.12
     */
    protected TVMCI() {
        // export only for select packages
        assert getClass().getPackage().getName().equals("org.graalvm.compiler.truffle") ||
                        getClass().getPackage().getName().equals("com.oracle.graal.truffle") ||
                        getClass().getPackage().getName().equals("com.oracle.truffle.api.impl");
    }

    /**
     * Reports the execution count of a loop.
     *
     * @param source the Node which invoked the loop.
     * @param iterations the number iterations to report to the runtime system
     * @since 0.12
     */
    protected abstract void onLoopCount(Node source, int iterations);

    /**
     * Reports when a new root node is loaded into the system.
     *
     * @since 0.15
     */
    protected void onLoad(RootNode rootNode) {
        InstrumentSupport support = Accessor.instrumentAccess();
        if (support != null) {
            support.onLoad(rootNode);
        }
    }

    /**
     * Makes sure the <code>rootNode</code> is initialized.
     *
     * @param rootNode
     * @since 0.12
     */
    protected void onFirstExecution(RootNode rootNode) {
        final Accessor.InstrumentSupport accessor = Accessor.instrumentAccess();
        if (accessor != null) {
            accessor.onFirstExecution(rootNode);
        }
    }

    /**
     * Finds the language associated with given root node.
     *
     * @param root the node
     * @return the language of the node
     * @since 0.12
     */
    @SuppressWarnings({"rawtypes"})
    protected Class<? extends TruffleLanguage> findLanguageClass(RootNode root) {
        return Accessor.nodesAccess().findLanguage(root);
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        Accessor.framesAccess().markMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public state in {@link FrameDescriptor}.
     *
     * @since 0.14
     */
    protected boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return Accessor.framesAccess().getMaterializeCalled(descriptor);
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected boolean isCloneUninitializedSupported(RootNode root) {
        return Accessor.nodesAccess().isCloneUninitializedSupported(root);
    }

    /**
     * Accessor for non-public API in {@link RootNode}.
     *
     * @since 0.24
     */
    protected RootNode cloneUninitialized(RootNode root) {
        return Accessor.nodesAccess().cloneUninitialized(root);
    }
}
