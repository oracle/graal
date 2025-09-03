/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
const STACK_TRACE_MARKER = "NATIVE-IMAGE-MARKER";

/**
 * Create JavaScript Error object, which is used to fill in the Throwable.backtrace object.
 */
function genBacktrace() {
    return new Error(STACK_TRACE_MARKER);
}

/**
 * Extract a Java string from the given backtrace object, which is supposed to be a JavaScript Error object.
 */
function formatStackTrace(backtrace) {
    let trace;

    if (backtrace.stack) {
        let lines = backtrace.stack.split("\n");

        /*
         * Since Error.prototype.stack is non-standard, different runtimes set
         * it differently.
         * We try to remove the preamble that contains the error name and
         * message to just get the stack trace.
         */
        if (lines.length > 0 && lines[0].includes(STACK_TRACE_MARKER)) {
            lines = lines.splice(1);
        }

        trace = lines.join("\n");
    } else {
        trace = "This JavaScript runtime does not expose stack trace information.";
    }

    return toJavaString(trace);
}

function gen_call_stack() {
    return formatStackTrace(genBacktrace());
}
