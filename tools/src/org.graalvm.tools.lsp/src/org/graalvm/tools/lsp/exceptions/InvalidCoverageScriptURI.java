/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.exceptions;

import java.nio.file.InvalidPathException;

/**
 * This exception is thrown when an invalid script path is provided. The diagnostics is sent to the
 * client, when caught.
 */
public final class InvalidCoverageScriptURI extends Exception {

    private static final long serialVersionUID = 2253144060104500867L;
    private final int index;
    private final String reason;
    private int length = 0;

    public InvalidCoverageScriptURI(InvalidPathException cause, int offset, int length) {
        super(cause);
        this.index = offset + ((cause.getIndex() >= 0) ? cause.getIndex() : 0);
        this.reason = null;
        this.length = length;
    }

    public InvalidCoverageScriptURI(int offset, String reason, int length) {
        this.index = offset;
        this.reason = reason;
        this.length = length;
    }

    public int getIndex() {
        return index;
    }

    public String getReason() {
        return reason != null ? reason : ((InvalidPathException) getCause()).getReason();
    }

    public int getLength() {
        return length;
    }
}
