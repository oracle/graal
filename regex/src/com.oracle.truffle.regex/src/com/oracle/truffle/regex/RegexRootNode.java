/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;

public final class RegexRootNode extends RootNode {

    private static final FrameDescriptor SHARED_EMPTY_FRAMEDESCRIPTOR = new FrameDescriptor();

    @Child private RegexBodyNode body;

    public RegexRootNode(RegexLanguage language, RegexBodyNode body) {
        super(language, SHARED_EMPTY_FRAMEDESCRIPTOR);
        this.body = body;
    }

    public RegexSource getSource() {
        return body.getSource();
    }

    @Override
    public SourceSection getSourceSection() {
        return body.getSourceSection();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (body instanceof InstrumentableNode.WrapperNode) {
            return ((InstrumentableNode.WrapperNode) body).getDelegateNode().toString();
        }
        return body.toString();
    }

    /**
     * Throws a {@link RegexInterruptedException} if the current thread is marked as interrupted.
     * This method should be called in interpreter mode only, since all cancel requests will cause a
     * deopt on the entire AST held by this root node.
     */
    public static void checkThreadInterrupted() {
        CompilerAsserts.neverPartOfCompilation("do not check thread interruption from compiled code");
        if (Thread.interrupted()) {
            throw new RegexInterruptedException();
        }
    }
}
