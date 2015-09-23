/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.shell.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.impl.DefaultVisualizer;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.tools.debug.shell.REPLMessage;

public abstract class REPLServerContext {

    private final int level;
    private final SuspendedEvent event;

    protected REPLServerContext(int level, SuspendedEvent event) {
        this.level = level;
        this.event = event;
    }

    /**
     * The nesting depth of this context in the current session.
     */
    public int getLevel() {
        return level;
    }

    /**
     * The AST node where execution is halted in this context.
     */
    public Node getNodeAtHalt() {
        return event.getNode();
    }

    /**
     * The frame where execution is halted in this context.
     */
    public MaterializedFrame getFrameAtHalt() {
        return event.getFrame();
    }

    public abstract Language getLanguage();

    public Visualizer getVisualizer() {
        return new DefaultVisualizer();
    }

    public PolyglotEngine engine() {
        return vm();
    }

    /**
     * @deprecated use {@link #engine()}.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public com.oracle.truffle.api.vm.TruffleVM vm() {
        return (com.oracle.truffle.api.vm.TruffleVM) engine();
    }

    protected abstract Debugger db();

    /**
     * Dispatches a REPL request to the appropriate handler.
     */
    public abstract REPLMessage[] receive(REPLMessage request);

    public abstract void registerBreakpoint(Breakpoint breakpoint);

    public abstract Breakpoint findBreakpoint(int id);

    public abstract int getBreakpointID(Breakpoint breakpoint);

    /**
     * Provides access to the execution stack.
     *
     * @return immutable list of stack elements
     */
    public List<FrameDebugDescription> getStack() {
        List<FrameDebugDescription> frames = new ArrayList<>();
        int frameCount = 1;
        for (FrameInstance frameInstance : event.getStack()) {
            if (frameCount == 1) {
                frames.add(new FrameDebugDescription(frameCount, event.getNode(), frameInstance));
            } else {
                frames.add(new FrameDebugDescription(frameCount, frameInstance.getCallNode(), frameInstance));
            }
            frameCount++;
        }
        return Collections.unmodifiableList(frames);
    }

    void prepareStepOut() {
        event.prepareStepOut();
    }

    void prepareStepInto(int repeat) {
        event.prepareStepInto(repeat);
    }

    void prepareStepOver(int repeat) {
        event.prepareStepOver(repeat);
    }

    void prepareContinue() {
        event.prepareContinue();
    }
}
