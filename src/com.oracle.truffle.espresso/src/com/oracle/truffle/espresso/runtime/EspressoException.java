/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.Host;

public final class EspressoException extends RuntimeException implements TruffleException {
    private static final long serialVersionUID = -7667957575377419520L;
    private final StaticObject exception;

    public EspressoException(@Host(Throwable.class) StaticObject exception) {
        assert StaticObject.notNull(exception);
        // TODO(peterssen): Check that exception is a real exception object (e.g. exception
        // instanceof Exception)
        this.exception = exception;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String getMessage() {
        return getMessage(exception);
    }

    public static String getMessage(StaticObject e) {
        return Meta.toHostString((StaticObject) e.getKlass().lookupMethod(Name.getMessage, Signature.String).invokeDirect(e));
    }

    public StaticObject getException() {
        return exception;
    }

    @Override
    public Node getLocation() {
        return null;
    }

    @Override
    public Object getExceptionObject() {
        return null;
    }

    @Override
    public boolean isSyntaxError() {
        return false;
    }

    @Override
    public boolean isIncompleteSource() {
        return false;
    }

    @Override
    public boolean isInternalError() {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isExit() {
        return false;
    }

    @Override
    public int getExitStatus() {
        return 0;
    }

    @Override
    public int getStackTraceElementLimit() {
        return -1;
    }

    @Override
    public SourceSection getSourceLocation() {
        return null;
    }
}
