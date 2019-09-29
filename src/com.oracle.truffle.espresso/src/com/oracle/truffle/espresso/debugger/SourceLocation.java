/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.debugger;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.debugger.exception.ClassNotLoadedException;
import com.oracle.truffle.espresso.debugger.exception.NoSuchSourceLineException;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class SourceLocation {

    private final int lineNumber;
    private final EspressoContext context;
    private final Symbol<Type> type;

    public SourceLocation(Symbol<Type> type, int lineNumber, EspressoContext context) {
        this.lineNumber = lineNumber;
        this.context = context;
        this.type = type;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Source getSource() throws ClassNotLoadedException, NoSuchSourceLineException {
        return new SourceLocator(context).lookupSource(type, lineNumber);
    }

    @Override
    public String toString() {
        return "Location: " + type.toString() + ":" + lineNumber;
    }

    public Symbol<Type> getType() {
        return type;
    }
}
