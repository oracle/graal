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
function llog(p) {
    if (p instanceof Error) {
        console.log(p);
    } else if (p instanceof Object) {
        console.log(p.toString());
    } else {
        console.log(p);
    }
}

/**
 * A writer emulating stdout and stderr using console.log and console.error
 *
 * Since those functions cannot print without newline, lines are buffered but
 * without a max buffer size.
 */
class ConsoleWriter {
    constructor(logger) {
        this.line = "";
        this.newline = "\n".charCodeAt(0);
        this.closed = false;
        this.logger = logger;
    }

    printChars(chars) {
        let index = chars.lastIndexOf(this.newline);

        if (index >= 0) {
            this.line += charArrayToString(chars.slice(0, index));
            this.writeLine();
            chars = chars.slice(index + 1);
        }

        this.line += charArrayToString(chars);
    }

    writeLine() {
        this.logger(this.line);
        this.line = "";
    }

    flush() {
        if (this.line.length > 0) {
            // In JS we cannot print without newline, so flushing will always produce one
            this.writeLine();
        }
    }

    close() {
        if (this.closed) {
            return;
        }
        this.closed = true;

        this.flush();
    }
}

var stdoutWriter = new ConsoleWriter(console.log);
var stderrWriter = new ConsoleWriter(console.error);
