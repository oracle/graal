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
package com.oracle.truffle.tools.debug.shell.client;

import java.util.*;

import com.oracle.truffle.api.source.*;
import com.oracle.truffle.tools.debug.shell.*;

/**
 * Client context for interaction with a program halted by the {@link REPLServer}.
 */
public interface REPLClientContext {

    /**
     * The source code halted in this context.
     */
    Source source();

    /**
     * The 1-based line at which execution is halted in this context; 0 means unknown.
     */
    int lineNumber();

    /**
     * The Truffle stack where execution is halted in this context.
     */
    List<REPLFrame> frames();

    /**
     * The nesting level of the execution context: 0 means evaluating shell commands outside any
     * executing program.
     */
    int level();

    /**
     * The source currently selected by the user; defaults to where halted.
     */
    Source getSelectedSource();

    /**
     * The frame number in this execution context currently selected by the user; defaults to 0.
     */
    int getSelectedFrameNumber();

    /**
     * Issue a command to the REPLServer that
     * <ul>
     * <li>can be specified by a single "op",</li>
     * <li>produces information in the form of a single string, and</li>
     * <li>has no effect on the execution state.</li>
     * </ul>
     */
    String stringQuery(String op);

    /**
     * Sets a new "default" frame number for frame-related commands.
     */
    void selectFrameNumber(int frameNumber);

    /**
     * Sends an information message.
     */
    void displayInfo(String message);

    void displayStack();

    /**
     * Send a message related to handling a command in this context.
     */
    void displayReply(String message);

    /**
     * Send a message related to failure while handling a command in this context.
     */
    void displayFailReply(String message);

}
