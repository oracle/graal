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

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.*;

public class JavaCCompiler extends AbstractCompiler {

    public static boolean isValidElement(Element currentElement) {
        try {
            Class<?> elementClass = Class.forName("com.sun.tools.javac.code.Symbol");
            return elementClass.isAssignableFrom(currentElement.getClass());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public List<? extends Element> getEnclosedElementsDeclarationOrder(TypeElement type) {
        return type.getEnclosedElements();
    }

    private static final Class[] getTreeAndTopLevelSignature = new Class[]{Element.class, AnnotationMirror.class, AnnotationValue.class};
    private static final Class[] getCharContentSignature = new Class[]{boolean.class};

    @Override
    public String getMethodBody(ProcessingEnvironment env, ExecutableElement method) {
        try {
            /*
             * if (false) { Pair<JCTree, JCCompilationUnit> treeAndTopLevel = ((JavacElements)
             * env.getElementUtils()).getTreeAndTopLevel(method, null, null); JCBlock block =
             * ((JCMethodDecl) treeAndTopLevel.fst).getBody(); int startPos = block.pos; int endPos
             * = block.endpos; String methodBody =
             * treeAndTopLevel.snd.getSourceFile().getCharContent(true).subSequence(startPos + 1,
             * endPos).toString(); return methodBody; }
             */

            Object treeAndTopLevel = getTreeAndTopLevel(env, method);
            Object block = method(field(treeAndTopLevel, "fst"), "getBody");
            int startPos = (int) field(block, "pos");
            int endPos = (int) field(block, "endpos");
            return getContent(treeAndTopLevel).subSequence(startPos + 1, endPos).toString();
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

    private static CharSequence getContent(Object treeAndTopLevel) throws Exception {
        /*
         * CharSequence content = treeAndTopLevel.snd.getSourceFile().getCharContent(true);
         */
        return (CharSequence) method(method(field(treeAndTopLevel, "snd"), "getSourceFile"), "getCharContent", getCharContentSignature, true);
    }

    private static Object getTreeAndTopLevel(ProcessingEnvironment env, Element element) throws Exception {
        /*
         * Pair<JCTree, JCCompilationUnit> treeAndTopLevel = ((JavacElements)
         * env.getElementUtils()).getTreeAndTopLevel(method, null, null);
         */
        return method(method(env, "getElementUtils"), "getTreeAndTopLevel", getTreeAndTopLevelSignature, element, null, null);
    }

    @Override
    public String getHeaderComment(ProcessingEnvironment env, Element type) {
        try {
            String content = getContent(getTreeAndTopLevel(env, type)).toString();
            return parseHeader(content);
        } catch (Exception e) {
            return Utils.printException(e);
        }
    }

}
