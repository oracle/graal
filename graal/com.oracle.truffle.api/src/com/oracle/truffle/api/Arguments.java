/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

/**
 * Base class for arguments passed to guest language methods via the
 * {@link CallTarget#call(com.oracle.truffle.api.frame.PackedFrame, Arguments)} method. A guest
 * language create a subclass with immutable fields representing the arguments passed to a guest
 * language method. The {@link Arguments} object must be created immediately before a method call
 * and it must not be stored in a field or cast to {@link java.lang.Object}.
 */
public class Arguments {

    /**
     * Constant that can be used as an argument to
     * {@link CallTarget#call(com.oracle.truffle.api.frame.PackedFrame, Arguments)} in case no
     * arguments should be supplied.
     */
    public static final Arguments EMPTY_ARGUMENTS = new Arguments();

    /**
     * Constructs an empty {@link Arguments} instance. Guest languages should create a subclass to
     * specify their own arguments.
     */
    protected Arguments() {
    }
}
