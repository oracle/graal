/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.json;

import jdk.vm.ci.meta.TriState;

/**
 * Thrown by {@link JsonParser} if an error is encountered during parsing.
 */
@SuppressWarnings("serial")
public final class JsonParserException extends RuntimeException {
    /**
     * Whether the parser was at the end of the input when the exception occurred. This may be
     * {@link TriState#UNKNOWN} for exceptions that were not thrown by {@link JsonParser}.
     */
    private final TriState isAtEOF;

    /**
     * Constructs a new JSON parser exception with the specified detail message. The state of
     * whether the parser was at the end of the input when the exception occurred is unknown.
     *
     * @param msg the detail message
     */
    public JsonParserException(final String msg) {
        super(msg);
        this.isAtEOF = TriState.UNKNOWN;
    }

    /**
     * Constructs a new JSON parser exception with the specified detail message and information
     * about whether the parser was at the end of the input when the exception occurred.
     *
     * @param msg the detail message
     * @param isAtEOF whether the parser was at the end of the input when the exception occurred
     */
    public JsonParserException(String msg, boolean isAtEOF) {
        super(msg);
        this.isAtEOF = TriState.get(isAtEOF);
    }

    /**
     * Returns whether the parser was at the end of the input when the exception occurred. This may
     * be {@link TriState#UNKNOWN} for exceptions that were not thrown by {@link JsonParser}.
     */
    public TriState isAtEOF() {
        return isAtEOF;
    }
}
