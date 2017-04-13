/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

/**
 * Use {@link Source.Builder#name(java.lang.String)} to eliminate this exception. This exception
 * informs users of {@link Source.Builder} that they have to provide a name before building the
 * final {@link Source} object.
 * <p>
 * Some of the factory methods can determine the name automatically:
 * {@link Source#newBuilder(java.io.File)} or {@link Source#newBuilder(java.net.URL)}. Other (like
 * {@link Source#newBuilder(java.io.Reader)}) require one to call
 * {@link Source.Builder#name(java.lang.String)} explicitly.
 * <p>
 * Rather than catching this exception, make sure your code calls
 * {@link Source.Builder#name(java.lang.String)}.
 *
 * @since 0.15
 */
public final class MissingNameException extends Exception {
    static final long serialVersionUID = 1L;

    MissingNameException() {
        super("Use Source.Builder.name(String) before calling Source.Builder.build()!");
    }
}
