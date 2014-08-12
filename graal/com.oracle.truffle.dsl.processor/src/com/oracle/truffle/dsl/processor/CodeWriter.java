/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.io.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;

import com.oracle.truffle.dsl.processor.java.compiler.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.java.transform.*;

public final class CodeWriter extends AbstractCodeWriter {

    private final Element originalElement;
    private final ProcessingEnvironment env;

    public CodeWriter(ProcessingEnvironment env, Element originalElement) {
        this.env = env;
        this.originalElement = originalElement;
    }

    @Override
    protected Writer createWriter(CodeTypeElement clazz) throws IOException {
        JavaFileObject jfo = env.getFiler().createSourceFile(clazz.getQualifiedName(), originalElement);
        return new BufferedWriter(jfo.openWriter());
    }

    @Override
    protected void writeHeader() {
        if (env == null) {
            return;
        }
        String comment = CompilerFactory.getCompiler(originalElement).getHeaderComment(env, originalElement);
        if (comment != null) {
            writeLn(comment);
        }
    }

}