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

/**
 * Evaluates the JavaScript code that represents a shared library written in JavaScript.
 *
 * This function is outside of the scope of the VM internals.
 */
function loadPrefetchedJSLibrary(content) {
    // This must evaluate to an object that contains the exported functions of the library.
    // The exported functions must have names that conform to the JNI convention, i.e.,
    // Java_<package>_<class>_<method> to be bound to the respective native method.

    // https://262.ecma-international.org/5.1/#sec-10.4.2
    //
    // Closure respect indirect call semantics of `eval` and transform the code to
    // `(0, eval)(content)`, thus no need to add `@noinline`.

    var safeEval = eval;
    return safeEval(content);
}
