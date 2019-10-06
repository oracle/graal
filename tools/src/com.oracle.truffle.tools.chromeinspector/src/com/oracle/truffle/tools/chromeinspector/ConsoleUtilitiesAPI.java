/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import com.oracle.truffle.api.debug.DebugValue;

/**
 * Console Utilities API. The API is described at
 * <a href="https://developers.google.com/web/tools/chrome-devtools/console/utilities">https://
 * developers.google.com/web/tools/chrome-devtools/console/utilities</a>.
 */
public final class ConsoleUtilitiesAPI {

    enum Method {
        DEBUG("debug"),
        UNDEBUG("undebug");

        private final String method;

        Method(String method) {
            this.method = method;
        }

        String getMethod() {
            return method;
        }
    }

    private final Method method;
    private final String expression;

    private ConsoleUtilitiesAPI(Method method, String expression) {
        this.method = method;
        this.expression = expression;
    }

    public static ConsoleUtilitiesAPI parse(String expression) {
        for (Method method : Method.values()) {
            if (expression.startsWith(method.getMethod())) {
                int i = method.getMethod().length();
                while (i < expression.length() && Character.isWhitespace(expression.charAt(i))) {
                    i++;
                }
                if (i < expression.length() && expression.charAt(i) == '(' && expression.endsWith(")")) {
                    return new ConsoleUtilitiesAPI(method, expression.substring(i + 1, expression.length() - 1).trim());
                }
            }
        }
        return null;
    }

    public String getExpression() {
        return expression;
    }

    public DebugValue process(DebugValue value, BreakpointsHandler breakpointsHandler) {
        switch (method) {
            case DEBUG:
                breakpointsHandler.createFunctionBreakpoint(value, null);
                return null;
            case UNDEBUG:
                breakpointsHandler.removeFunctionBreakpoint(value);
                return null;
            default:
                throw new IllegalStateException("Unknown API method " + method.name());
        }
    }
}
