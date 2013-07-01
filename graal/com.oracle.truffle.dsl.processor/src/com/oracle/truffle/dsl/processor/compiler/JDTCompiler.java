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
package com.oracle.truffle.dsl.processor.compiler;

import javax.annotation.processing.*;
import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.*;

public class JDTCompiler extends AbstractCompiler {

    public static boolean isValidElement(Element currentElement) {
        try {
            Class<?> elementClass = Class.forName("org.eclipse.jdt.internal.compiler.apt.model.ElementImpl");
            return elementClass.isAssignableFrom(currentElement.getClass());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getMethodBody(ProcessingEnvironment env, ExecutableElement method) {
        try {

            char[] source = getSource(method);
            if (source == null) {
                return null;
            }

            /*
             * AbstractMethodDeclaration decl =
             * ((MethodBinding)(((ElementImpl)method)._binding)).sourceMethod(); int bodyStart =
             * decl.bodyStart; int bodyEnd = decl.bodyEnd;
             */
            Object decl = method(field(method, "_binding"), "sourceMethod");
            int bodyStart = (int) field(decl, "bodyStart");
            int bodyEnd = (int) field(decl, "bodyEnd");

            int length = bodyEnd - bodyStart;
            char[] target = new char[length];
            System.arraycopy(source, bodyStart, target, 0, length);

            return new String(target);
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

    private static char[] getSource(Element element) throws Exception {
        /*
         * Binding binding = ((ElementImpl)element)._binding; char[] source = null; if (binding
         * instanceof MethodBinding) { source = ((MethodBinding)
         * binding).sourceMethod().compilationResult.getCompilationUnit().getContents(); } else if
         * (binding instanceof SourceTypeBinding) { source =
         * ((SourceTypeBinding)binding).scope.referenceContext
         * .compilationResult.compilationUnit.getContents(); } return source;
         */

        Object binding = field(element, "_binding");
        Class<?> methodBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.MethodBinding");
        Class<?> referenceBindingClass = Class.forName("org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding");

        char[] source = null;
        if (methodBindingClass.isAssignableFrom(binding.getClass())) {
            Object sourceMethod = method(binding, "sourceMethod");
            if (sourceMethod == null) {
                return null;
            }
            source = (char[]) method(method(field(sourceMethod, "compilationResult"), "getCompilationUnit"), "getContents");
        } else if (referenceBindingClass.isAssignableFrom(binding.getClass())) {
            source = (char[]) method(field(field(field(field(binding, "scope"), "referenceContext"), "compilationResult"), "compilationUnit"), "getContents");
        }
        return source;
    }

    @Override
    public String getHeaderComment(ProcessingEnvironment env, Element type) {
        try {
            char[] source = getSource(type);
            if (source == null) {
                return null;
            }
            return parseHeader(new String(source));
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

}
