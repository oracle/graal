/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/*
 @ApiInfo(
 group="Truffle"
 )
 */

/**
 * This package contains classes that specify the contract for inter-operability between Truffle
 * languages.
 * <p>
 * Languages can exchange primitive Java type wrapper objects (e.g., {@link java.lang.Byte},
 * {@link java.lang.Short}, {@link java.lang.Integer}, {@link java.lang.Long},
 * {@link java.lang.Float}, {@link java.lang.Double}, {@link java.lang.Character},
 * {@link java.lang.Boolean}, and {@link java.lang.String}) as well as any type implementing
 * {@link com.oracle.truffle.api.interop.TruffleObject}. Foreign objects are precisely those
 * implementing {@link com.oracle.truffle.api.interop.TruffleObject}.
 * <p>
 * To use a {@link com.oracle.truffle.api.interop.TruffleObject} from a different language, you need
 * to ask the language to build appropriate AST for a given
 * {@link com.oracle.truffle.api.interop.Message} with
 * {@link com.oracle.truffle.api.interop.Message#createNode}. The message can then be executed with
 * {@link com.oracle.truffle.api.interop.ForeignAccess} methods.
 *
 * @since 0.8 or older
 */
package com.oracle.truffle.api.interop;
