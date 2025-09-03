/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.serialize;

import java.io.Serial;

/**
 * Error thrown when types are not <a href=
 * "https://www.graalvm.org/latest/reference-manual/native-image/metadata/#serialization">registered</a>
 * for serialization or deserialization.
 * <p/>
 * The purpose of this exception is to easily discover unregistered elements and to assure that all
 * serialization or deserialization operations have expected behavior.
 */
public final class MissingSerializationRegistrationError extends LinkageError {
    @Serial private static final long serialVersionUID = 2764341882856270641L;
    private final Class<?> culprit;

    public MissingSerializationRegistrationError(String message, Class<?> cl) {
        super(message);
        this.culprit = cl;
    }

    /**
     * Returns the class that was not registered for serialization or deserialization.
     */
    public Class<?> getCulprit() {
        return culprit;
    }
}
