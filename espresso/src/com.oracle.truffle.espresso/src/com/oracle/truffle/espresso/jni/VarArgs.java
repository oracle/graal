/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

/**
 * Varargs... methods for Java. Helper shim to call Java methods from the native world with
 * varargs... signature. This is a "pop" implementation, e.g. parameters are popped by the callee.
 * Arguments must be popped in the right order with the proper type. The caller must guarantee to
 * pass arguments with the right type and the callee must be able to resolve proper types.
 * <p>
 * It's allowed to pop less arguments than actually passed by the caller, but never more. A typical
 * consumer for varargs...
 *
 * <pre>
 * public static void jprintf(PrintStream out, String fmt, long varargsPtr) {
 *     // VarArgs is a helper object that uses/calls the native pop* implementation.
 *     VarArgs varargs = VarArgs.init(varargsPtr);
 *     for (int i = 0; i < fmt.length(); ++i) {
 *         char c = fmt.charAt(i);
 *         if (c == '%') {
 *             ++i;
 *             if (i < fmt.length) {
 *                 char id = fmt.charAt(i);
 *                 switch (id) {
 *                     case 'z':
 *                         out.print(varargs.popBoolean());
 *                         break;
 *                     case 'b':
 *                         out.print(varargs.popByte());
 *                         break;
 *                     case 'c':
 *                         out.print(varargs.popChar());
 *                         break;
 *                     case 's':
 *                         out.print(varargs.popShort());
 *                         break;
 *                     case 'i':
 *                         out.print(varargs.popInt());
 *                         break;
 *                     case 'f':
 *                         out.print(varargs.popFloat());
 *                         break;
 *                     case 'd':
 *                         out.print(varargs.popDouble());
 *                         break;
 *                     case 'j':
 *                         out.print(varargs.popLong());
 *                         break;
 *                     case 'l':
 *                         out.print(varargs.popObject());
 *                         break;
 *                     default:
 *                         out.print(id);
 *                 }
 *             }
 *         } else {
 *             out.print(c);
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * The `varargsPtr` can only be used/traversed once, it cannot be reused, reinitialized or copied.
 */
public interface VarArgs {

    boolean popBoolean();

    byte popByte();

    char popChar();

    short popShort();

    int popInt();

    float popFloat();

    double popDouble();

    long popLong();

    Object popObject();
}
