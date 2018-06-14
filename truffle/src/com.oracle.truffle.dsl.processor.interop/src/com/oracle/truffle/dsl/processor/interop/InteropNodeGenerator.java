/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.dsl.processor.interop;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

abstract class InteropNodeGenerator {
    protected final ProcessingEnvironment processingEnv;
    protected final TypeElement element;
    protected final String packageName;
    protected final String clazzName;
    protected final String userClassName;
    protected final ForeignAccessFactoryGenerator containingForeignAccessFactory;
    protected String indent = "    ";

    protected InteropNodeGenerator(ProcessingEnvironment processingEnv, TypeElement element, ForeignAccessFactoryGenerator containingForeignAccessFactory) {
        this.processingEnv = processingEnv;
        this.element = element;
        this.containingForeignAccessFactory = containingForeignAccessFactory;
        this.packageName = ElementUtils.getPackageName(element);
        this.userClassName = ElementUtils.getQualifiedName(element);
        this.clazzName = Utils.getSimpleResolveClassName(element);
    }

    public void addImports(Collection<String> imports) {
        imports.add("com.oracle.truffle.api.dsl.Specialization");
        imports.add("com.oracle.truffle.api.dsl.UnsupportedSpecializationException");
        imports.add("com.oracle.truffle.api.frame.VirtualFrame");
        imports.add("com.oracle.truffle.api.interop.ForeignAccess");
        imports.add("com.oracle.truffle.api.interop.UnsupportedTypeException");
        imports.add("com.oracle.truffle.api.nodes.RootNode");
    }

    public abstract void appendNode(Writer w) throws IOException;

    public String getRootNodeFactoryInvocation() {
        return clazzName + ".createRoot()";
    }

    private String getGeneratedDSLNodeSuffix() {
        return clazzName.endsWith("Node") ? "Gen" : "NodeGen";
    }

    protected String getGeneratedDSLNodeQualifiedName() {
        return containingForeignAccessFactory.getFullClassName() + "Factory" + '.' + clazzName + getGeneratedDSLNodeSuffix();
    }

    @Override
    public String toString() {
        return clazzName;
    }
}
