/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl;

import javax.script.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.sl.parser.*;
import com.oracle.truffle.sl.runtime.*;

public final class SLScript {

    private final SLContext context;
    private final CallTarget main;

    private SLScript(SLContext context, CallTarget mainFunction) {
        this.context = context;
        this.main = mainFunction;
    }

    public SLContext getContext() {
        return context;
    }

    public CallTarget getMain() {
        return main;
    }

    public Object run(Object... arguments) {
        return main.call(null, new SLArguments(arguments));
    }

    @Override
    public String toString() {
        return main.toString();
    }

    public static SLScript create(SLContext context, Source source) throws ScriptException {
        SLNodeFactory factory = new SLNodeFactory(context);
        Parser parser = new Parser(new Scanner(source.getInputStream()), factory);
        factory.setParser(parser);
        factory.setSource(source);
        String error = parser.ParseErrors();
        if (!error.isEmpty()) {
            throw new ScriptException(String.format("Error(s) parsing script: %s", error));
        }

        CallTarget main = context.getFunctionRegistry().lookup("main");
        if (main == null) {
            throw new ScriptException("No main function found.");
        }
        return new SLScript(context, main);
    }
}
