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
import com.oracle.truffle.espresso.classfile.LineNumberTable;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.debugger.exception.ClassNotLoadedException;
import com.oracle.truffle.espresso.debugger.exception.NoSuchSourceLineException;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public class SourceLocator {

    private final EspressoContext context;

    SourceLocator(EspressoContext context) {
        this.context = context;
    }

    public Source lookupSource(Symbol<Type> type, int lineNumber) throws ClassNotLoadedException, NoSuchSourceLineException {

        // Check if class is loaded. Don't ever load classes here since
        // this will break original class initialization order.
        Klass klass = context.getRegistries().findLoadedClassAny(type);
        if (klass == null) {
            throw new ClassNotLoadedException();
        }

        // the class was already loaded, so look for the source line
        for (Method method : klass.getDeclaredMethods()) {
            // check if line number is in method
            if (checkLine(method, lineNumber)) {
                return method.getSource();
            }
        }

        throw new NoSuchSourceLineException();
    }

    private boolean checkLine(Method method, int lineNumber) {
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null) {
            for (LineNumberTable.Entry entry : lineNumberTable.getEntries()) {
                if (entry.getLineNumber() == lineNumber) {
                    return true;
                }
            }
        }
        return false;
    }
}
