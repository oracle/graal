/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.nodes.instrument;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * SLStatmentWrapper is a Truffle AST node that gets inserted as the parent to the node that it is
 * wrapping. Any debugging instruments are attached to this wrapper through {@link Probe}s (which
 * themselves contain the instruments). It is through this mechanism that tools can interact
 * directly with the AST. <br/>
 * SLStatmentWrapper specifically wraps {@link SLStatementWrapper}s and overrides the executeVoid
 * function of {@link SLStatementNode} to operate on the child of the wrapper instead of the wrapper
 * itself.
 *
 */
public final class SLStatementWrapper extends SLStatementNode implements Wrapper {

    @Child private SLStatementNode child;

    private final Probe probe;

    public SLStatementWrapper(SLContext context, SLStatementNode child) {
        super(child.getSourceSection());
        assert !(child instanceof SLStatementWrapper);
        this.probe = context.createProbe(child.getSourceSection());
        this.child = child;
        // The child should only be inserted after a replace, so we defer inserting the child to the
        // creator of the wrapper.
    }

    @Override
    public SLStatementNode getNonWrapperNode() {
        return child;
    }

    public Node getChild() {
        return child;
    }

    public Probe getProbe() {
        return probe;
    }

    @SlowPath
    public boolean isTaggedAs(SyntaxTag tag) {
        return probe.isTaggedAs(tag);
    }

    @SlowPath
    public Iterable<SyntaxTag> getSyntaxTags() {
        return probe.getSyntaxTags();
    }

    @SlowPath
    public void tagAs(SyntaxTag tag) {
        probe.tagAs(tag);
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        this.tagAs(StandardSyntaxTag.STATEMENT);
        probe.enter(child, frame);

        try {
            child.executeVoid(frame);
            probe.leave(child, frame);
        } catch (KillException e) {
            throw (e);
        } catch (Exception e) {
            probe.leaveExceptional(child, frame, e);
            throw (e);
        }
    }

    /**
     * Sets the parent pointer of this wrapper's child.
     */
    public void insertChild() {
        insert(this.child);
    }
}
