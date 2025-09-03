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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;

public final class SourceLocator {

    private final JDWPContext context;

    SourceLocator(JDWPContext context) {
        this.context = context;
    }

    public Source lookupSource(String slashName, int lineNumber) throws NoSuchSourceLineException {

        // Check if class is loaded. Don't ever load classes here since
        // this will break original class initialization order.
        KlassRef[] klass = context.findLoadedClass(slashName);
        if (klass == null) {
            throw new RuntimeException("not implemented yet!");
        }

        // the class was already loaded, so look for the source line
        for (MethodRef method : klass[0].getDeclaredMethods()) {
            // check if line number is in method
            if (method.hasLine(lineNumber)) {
                return method.getSource();
            }
        }

        throw new NoSuchSourceLineException();
    }
}
