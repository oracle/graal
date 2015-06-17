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
package com.oracle.truffle.tools.debug.shell;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * A message for communication between a Read-Eval-Print-Loop server associated with a language
 * implementation and a possibly remote client.
 *
 * @see REPLClient
 * @see REPLServer
 */
public final class REPLMessage {

    // Some standard keys and values
    public static final String AST = "ast";
    public static final String AST_DEPTH = "show-max-depth";
    public static final String BACKTRACE = "backtrace";
    public static final String BREAK_AT_LINE = "break-at-line";
    public static final String BREAK_AT_LINE_ONCE = "break-at-line-once";
    public static final String BREAK_AT_THROW = "break-at-throw";
    public static final String BREAK_AT_THROW_ONCE = "break-at-throw-once";
    public static final String BREAKPOINT_CONDITION = "breakpoint-condition";
    public static final String BREAKPOINT_GROUP_ID = "breakpoint-group-id";
    public static final String BREAKPOINT_HIT_COUNT = "breakpoint-hit-count";
    public static final String BREAKPOINT_ID = "breakpoint-id";
    public static final String BREAKPOINT_IGNORE_COUNT = "breakpoint-ignore-count";
    public static final String BREAKPOINT_INFO = "breakpoint-info";
    public static final String BREAKPOINT_STATE = "breakpoint-state";
    public static final String CLEAR_BREAK = "clear-breakpoint";
    public static final String CODE = "code";
    public static final String CONTINUE = "continue";
    public static final String DEBUG_LEVEL = "debug-level";
    public static final String DELETE_BREAK = "delete-breakpoint";
    public static final String DISABLE_BREAK = "disable-breakpoint";
    public static final String DISPLAY_MSG = "displayable-message";
    public static final String DOWN = "down";
    public static final String ENABLE_BREAK = "enable-breakpoint";
    public static final String EVAL = "eval";
    public static final String FAILED = "failed";
    public static final String FILE = "file";
    public static final String FILE_PATH = "path";
    public static final String FRAME = "frame";
    public static final String FRAME_INFO = "frame-info";
    public static final String FRAME_NUMBER = "frame-number";
    public static final String INFO = "info";
    public static final String INFO_KEY = "info-key";
    public static final String INFO_VALUE = "info-value";
    public static final String KILL = "kill";
    public static final String LANGUAGE = "language";
    public static final String LINE_NUMBER = "line-number";
    public static final String LIST = "list";
    public static final String LOAD_RUN = "load-run-source";
    public static final String LOAD_STEP = "load-step-into-source";
    public static final String METHOD_NAME = "method-name";
    public static final String OP = "op";
    public static final String OPTION = "option";
    public static final String QUIT = "quit";
    public static final String REPEAT = "repeat";
    public static final String SET = "set";
    public static final String SET_BREAK_CONDITION = "set-breakpoint-condition";
    public static final String SOURCE_LINE_TEXT = "source-line-text";
    public static final String SOURCE_LOCATION = "source-location";
    public static final String SOURCE_NAME = "source-name";
    public static final String SOURCE_TEXT = "source-text";
    public static final String STACK_SIZE = "stack-size";
    public static final String STATUS = "status";
    public static final String STEP_INTO = "step-into";
    public static final String STEP_OUT = "step-out";
    public static final String STEP_OVER = "step-over";
    public static final String STOPPED = "stopped";
    public static final String SUB = "sub";
    public static final String SUBTREE = "subtree";
    public static final String SUCCEEDED = "succeeded";
    public static final String TOPIC = "topic";
    public static final String TRUFFLE = "truffle";
    public static final String TRUFFLE_AST = "truffle-ast";
    public static final String TRUFFLE_NODE = "truffle-node";
    public static final String TRUFFLE_SUBTREE = "truffle-subtree";
    public static final String UNSET_BREAK_CONDITION = "unset-breakpoint-condition";
    public static final String UP = "up";
    public static final String VALUE = "value";
    public static final String WARNINGS = "warnings";
    private final Map<String, String> map;

    /**
     * Creates an empty REPL message.
     */
    public REPLMessage() {
        this.map = new TreeMap<>();
    }

    /**
     * Creates a REPL message with an initial entry.
     */
    public REPLMessage(String key, String value) {
        this();
        map.put(key, value);
    }

    public REPLMessage(REPLMessage message) {
        this.map = new TreeMap<>(message.map);
    }

    public String get(String key) {
        return map.get(key);
    }

    /**
     * Returns the specified key value as an integer; {@code null} if missing or non-numeric.
     */
    public Integer getIntValue(String key) {
        final String value = map.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {

            }
        }
        return null;
    }

    public String put(String key, String value) {
        return map.put(key, value);
    }

    public String remove(String key) {
        return map.remove(key);
    }

    public Set<String> keys() {
        return map.keySet();
    }

    public void print(PrintStream out, String linePrefix) {
        map.keySet();

        for (Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > 50) {
                value = value.substring(0, 50) + " ...";
            }
            out.println(linePrefix + entry.getKey() + " = \"" + value + "\"");
        }
    }
}
