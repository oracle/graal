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

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import com.oracle.truffle.tools.debug.shell.server.REPLServer;

/**
 * A message for communication between a Read-Eval-Print-Loop server associated with a language
 * implementation and a possibly remote client.
 *
 * @see REPLServer
 * @since 0.8 or earlier
 */
@Deprecated
public final class REPLMessage {
    /** @since 0.8 or earlier */
    // Some standard keys and values
    public static final String AST = "ast";
    /** @since 0.8 or earlier */
    public static final String AST_DEPTH = "show-max-depth";
    /** @since 0.8 or earlier */
    public static final String BACKTRACE = "backtrace";
    /** @since 0.8 or earlier */
    public static final String BREAK_AT_LINE = "break-at-line";
    /** @since 0.8 or earlier */
    public static final String BREAK_AT_LINE_ONCE = "break-at-line-once";
    /** @since 0.8 or earlier */
    @Deprecated public static final String BREAK_AT_THROW = "break-at-throw";
    /** @since 0.8 or earlier */
    @Deprecated public static final String BREAK_AT_THROW_ONCE = "break-at-throw-once";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_CONDITION = "breakpoint-condition";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_HIT_COUNT = "breakpoint-hit-count";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_ID = "breakpoint-id";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_IGNORE_COUNT = "breakpoint-ignore-count";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_INFO = "breakpoint-info";
    /** @since 0.8 or earlier */
    public static final String BREAKPOINT_STATE = "breakpoint-state";
    /** @since 0.8 or earlier */
    public static final String CALL = "call";
    /** @since 0.8 or earlier */
    public static final String ARG0 = "call-argument-0";
    /** @since 0.8 or earlier */
    public static final String ARG1 = "call-argument-1";
    /** @since 0.8 or earlier */
    public static final String ARG2 = "call-argument-2";
    /** @since 0.8 or earlier */
    public static final String ARG3 = "call-argument-3";
    /** @since 0.8 or earlier */
    public static final String ARG4 = "call-argument-4";
    /** @since 0.8 or earlier */
    public static final String ARG5 = "call-argument-5";
    /** @since 0.8 or earlier */
    public static final String ARG6 = "call-argument-6";
    /** @since 0.8 or earlier */
    public static final String ARG7 = "call-argument-7";
    /** @since 0.8 or earlier */
    public static final String ARG8 = "call-argument-8";
    /** @since 0.8 or earlier */
    public static final String ARG9 = "call-argument-9";
    /** @since 0.8 or earlier */
    public static final String CALL_NAME = "call-name";
    /** @since 0.8 or earlier */
    public static final String CLEAR_BREAK = "clear-breakpoint";
    /** @since 0.8 or earlier */
    public static final String CODE = "code";
    /** @since 0.8 or earlier */
    public static final String CONTINUE = "continue";
    /** @since 0.8 or earlier */
    public static final String DEBUG_LEVEL = "debug-level";
    /** @since 0.8 or earlier */
    public static final String DELETE_BREAK = "delete-breakpoint";
    /** @since 0.8 or earlier */
    public static final String DISABLE_BREAK = "disable-breakpoint";
    /** @since 0.8 or earlier */
    public static final String DISPLAY_MSG = "displayable-message";
    /** @since 0.8 or earlier */
    public static final String DOWN = "down";
    /** @since 0.8 or earlier */
    public static final String ENABLE_BREAK = "enable-breakpoint";
    /** @since 0.8 or earlier */
    public static final String EVAL = "eval";
    /** @since 0.8 or earlier */
    public static final String FAILED = "failed";
    /** @since 0.8 or earlier */
    public static final String FILE = "file";
    /** @since 0.8 or earlier */
    public static final String FILE_PATH = "path";
    /** @since 0.8 or earlier */
    public static final String FRAME = "frame";
    /** @since 0.8 or earlier */
    public static final String FRAME_INFO = "frame-info";
    /** @since 0.8 or earlier */
    public static final String FRAME_NUMBER = "frame-number";
    /** @since 0.8 or earlier */
    public static final String INFO = "info";
    /** @since 0.8 or earlier */
    public static final String INFO_CURRENT_LANGUAGE = "info-current-language";
    /** @since 0.8 or earlier */
    public static final String INFO_KEY = "info-key";
    /** @since 0.8 or earlier */
    public static final String INFO_SUPPORTED_LANGUAGES = "info-supported-languages";
    /** @since 0.8 or earlier */
    public static final String INFO_VALUE = "info-value";
    /** @since 0.8 or earlier */
    public static final String KILL = "kill";
    /** @since 0.8 or earlier */
    public static final String LANG_NAME = "language-name";
    /** @since 0.8 or earlier */
    public static final String LANG_VER = "language-version";
    /** @since 0.8 or earlier */
    public static final String LANG_MIME = "language-MIME type";
    /** @since 0.8 or earlier */
    public static final String LINE_NUMBER = "line-number";
    /** @since 0.8 or earlier */
    public static final String LIST = "list";
    /** @since 0.8 or earlier */
    public static final String LOAD_SOURCE = "load-source";
    /** @since 0.8 or earlier */
    public static final String METHOD_NAME = "method-name";
    /** @since 0.8 or earlier */
    public static final String OP = "op";
    /** @since 0.8 or earlier */
    public static final String OPTION = "option";
    /** @since 0.8 or earlier */
    public static final String QUIT = "quit";
    /** @since 0.8 or earlier */
    public static final String REPEAT = "repeat";
    /** @since 0.12 */
    public static final String RUN = "run";
    /** @since 0.8 or earlier */
    public static final String SET = "set";
    /** @since 0.8 or earlier */
    public static final String SET_BREAK_CONDITION = "set-breakpoint-condition";
    /** @since 0.8 or earlier */
    public static final String SET_LANGUAGE = "set-language";
    /** @since 0.8 or earlier */
    public static final String SLOT_ID = "slot-identifier";
    /** @since 0.8 or earlier */
    public static final String SLOT_INDEX = "slot-index";
    /** @since 0.8 or earlier */
    public static final String SLOT_VALUE = "slot-value";
    /** @since 0.8 or earlier */
    public static final String SOURCE_LINE_TEXT = "source-line-text";
    /** @since 0.8 or earlier */
    public static final String SOURCE_LOCATION = "source-location";
    /** @since 0.8 or earlier */
    public static final String SOURCE_NAME = "source-name";
    /** @since 0.8 or earlier */
    public static final String SOURCE_TEXT = "source-text";
    /** @since 0.8 or earlier */
    public static final String STACK_SIZE = "stack-size";
    /** @since 0.8 or earlier */
    public static final String STATUS = "status";
    /** @since 0.8 or earlier */
    public static final String STEP_INTO = "step-into";
    /** @since 0.8 or earlier */
    public static final String STEP_OUT = "step-out";
    /** @since 0.8 or earlier */
    public static final String STEP_OVER = "step-over";
    /** @since 0.8 or earlier */
    public static final String STOPPED = "stopped";
    /** @since 0.8 or earlier */
    public static final String SUB = "sub";
    /** @since 0.8 or earlier */
    public static final String SUBTREE = "subtree";
    /** @since 0.8 or earlier */
    public static final String SUCCEEDED = "succeeded";
    /** @since 0.8 or earlier */
    public static final String TOPIC = "topic";
    /** @since 0.8 or earlier */
    public static final String TRUE = "true";
    /** @since 0.8 or earlier */
    public static final String TRUFFLE = "truffle";
    /** @since 0.8 or earlier */
    public static final String TRUFFLE_AST = "truffle-ast";
    /** @since 0.8 or earlier */
    public static final String TRUFFLE_NODE = "truffle-node";
    /** @since 0.8 or earlier */
    public static final String TRUFFLE_SUBTREE = "truffle-subtree";
    /** @since 0.8 or earlier */
    public static final String UNSET_BREAK_CONDITION = "unset-breakpoint-condition";
    /** @since 0.8 or earlier */
    public static final String UP = "up";
    /** @since 0.8 or earlier */
    public static final String VALUE = "value";
    /** @since 0.8 or earlier */
    public static final String WARNINGS = "warnings";
    /** @since 0.8 or earlier */
    public static final String WELCOME_MESSAGE = "welcome-message";
    /** @since 0.8 or earlier */
    public static final String[] ARG_NAMES = new String[]{ARG0, ARG1, ARG2, ARG3, ARG4, ARG5, ARG6, ARG7, ARG8, ARG9};
    private final Map<String, String> map;

    /**
     * Creates an empty REPL message.
     *
     * @since 0.8 or earlier
     */
    public REPLMessage() {
        this.map = new TreeMap<>();
    }

    /**
     * Creates a REPL message with an initial entry.
     *
     * @since 0.8 or earlier
     */
    public REPLMessage(String key, String value) {
        this();
        map.put(key, value);
    }

    /** @since 0.8 or earlier */
    public REPLMessage(REPLMessage message) {
        this.map = new TreeMap<>(message.map);
    }

    /** @since 0.8 or earlier */
    public String get(String key) {
        return map.get(key);
    }

    /**
     * Returns the specified key value as an integer; {@code null} if missing or non-numeric.
     *
     * @since 0.8 or earlier
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

    /** @since 0.8 or earlier */
    public String put(String key, String value) {
        return map.put(key, value);
    }

    /** @since 0.8 or earlier */
    public String remove(String key) {
        return map.remove(key);
    }

    /** @since 0.8 or earlier */
    public Set<String> keys() {
        return map.keySet();
    }

    /** @since 0.8 or earlier */
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
