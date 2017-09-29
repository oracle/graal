/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.tck;

import java.util.List;
import java.util.Objects;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

public final class SnippetRun {
    private final List<? extends Value> parameters;
    private final Value result;
    private final PolyglotException exception;

    private SnippetRun(final List<? extends Value> parameters, Value result, PolyglotException exception) {
        this.parameters = parameters;
        this.result = result;
        this.exception = exception;
    }

    public List<? extends Value> getParameters() {
        return parameters;
    }

    public Value getResult() {
        return result;
    }

    public PolyglotException getException() {
        return exception;
    }

    public static SnippetRun create(final List<? extends Value> parameters, final Value result) {
        Objects.requireNonNull(parameters, "Parameters has to be given.");
        Objects.requireNonNull(result, "Result has to be given.");
        return new SnippetRun(parameters, result, null);
    }

    public static SnippetRun create(final List<? extends Value> parameters, final PolyglotException exception) {
        return new SnippetRun(
                        Objects.requireNonNull(parameters, "Parameters has to be given."),
                        null,
                        Objects.requireNonNull(exception, "Exception has to be given."));
    }
}
