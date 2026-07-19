/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

// Prints a copy of the file named by the first program argument with its
// license header removed: everything up to and including the first blank line
// is dropped. This mirrors the former CopyStripLicense.java helper but runs on
// the `js` launcher itself, so it works on a language standalone that has no
// JDK. The `read` builtin and the `arguments` global are provided by the js
// shell (js --shell, enabled by the launcher). Usage:
//   js strip-license.js -- <input-file>  > <stripped-output>
var content = read(globalThis.arguments[0]);
var lines = content.split('\n');
// A trailing newline yields an empty final element; drop it so the output has
// the same line count as the original body (matching CopyStripLicense).
if (lines.length > 0 && lines[lines.length - 1] === '') {
    lines.pop();
}
var licenseEnded = false;
for (var i = 0; i < lines.length; i++) {
    if (!licenseEnded) {
        if (lines[i].trim().length === 0) {
            licenseEnded = true;
        }
    } else {
        print(lines[i]);
    }
}
