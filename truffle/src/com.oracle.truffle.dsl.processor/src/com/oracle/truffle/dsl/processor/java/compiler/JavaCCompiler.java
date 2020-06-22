/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.java.compiler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

public class JavaCCompiler extends AbstractCompiler {

    public static boolean isValidElement(Element currentElement) {
        try {
            Class<?> elementClass = Class.forName("com.sun.tools.javac.code.Symbol");
            return elementClass.isAssignableFrom(currentElement.getClass());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public List<? extends Element> getAllMembersInDeclarationOrder(ProcessingEnvironment environment, TypeElement type) {
        return environment.getElementUtils().getAllMembers(type);
    }

    @Override
    public List<? extends Element> getEnclosedElementsInDeclarationOrder(TypeElement type) {
        return type.getEnclosedElements();
    }

    @Override
    protected boolean emitDeprecationWarningImpl(ProcessingEnvironment environment, Element element) {
        try {
            ReflectionSupport reflect = new ReflectionSupport();
            Object javacContext = reflect.getJavacContext(environment);
            Object trees = reflect.getTrees(environment);
            Object elementTreePath = reflect.getPath(trees, element);
            if (elementTreePath == null) {
                return false;
            }
            Object log = reflect.getLog(javacContext);
            Object check = reflect.getCheck(javacContext);
            Object prev = reflect.useSource(log, reflect.getFile(elementTreePath));
            try {
                reflect.warnDeprecated(check, elementTreePath, element);
            } finally {
                reflect.useSource(log, prev);
            }
            return true;
        } catch (ReflectiveOperationException reflectiveException) {
            return false;
        }
    }

    private static final class ReflectionSupport {

        final Class<?> check;
        final Class<?> context;
        final Class<?> diagnosticPosition;
        final Class<?> javacProcessingEnvironment;
        final Class<?> jcCompilationUnit;
        final Class<?> log;
        final Class<?> symbol;
        final Class<?> trees;
        final Class<?> treePath;

        ReflectionSupport() throws ClassNotFoundException {
            check = Class.forName("com.sun.tools.javac.comp.Check");
            context = Class.forName("com.sun.tools.javac.util.Context");
            diagnosticPosition = Class.forName("com.sun.tools.javac.util.JCDiagnostic$DiagnosticPosition");
            javacProcessingEnvironment = Class.forName("com.sun.tools.javac.processing.JavacProcessingEnvironment");
            jcCompilationUnit = Class.forName("com.sun.tools.javac.tree.JCTree$JCCompilationUnit");
            log = Class.forName("com.sun.tools.javac.util.Log");
            symbol = Class.forName("com.sun.tools.javac.code.Symbol");
            trees = Class.forName("com.sun.source.util.Trees");
            treePath = Class.forName("com.sun.source.util.TreePath");
        }

        Object getJavacContext(ProcessingEnvironment environment) throws ReflectiveOperationException {
            Method getContextMethod = javacProcessingEnvironment.getMethod("getContext");
            return getContextMethod.invoke(environment);
        }

        Object getTrees(ProcessingEnvironment environment) throws ReflectiveOperationException {
            Method treesInstanceMethod = trees.getMethod("instance", ProcessingEnvironment.class);
            return treesInstanceMethod.invoke(null, environment);
        }

        Object getPath(Object treesInstance, Element element) throws ReflectiveOperationException {
            Method getTreeMethod = trees.getMethod("getPath", Element.class);
            return getTreeMethod.invoke(treesInstance, element);
        }

        Object getLog(Object javacContext) throws ReflectiveOperationException {
            Method logInstanceMethod = log.getMethod("instance", context);
            return logInstanceMethod.invoke(null, javacContext);
        }

        Object getCheck(Object javacContext) throws ReflectiveOperationException {
            Method checkInstanceMethod = check.getMethod("instance", context);
            return checkInstanceMethod.invoke(null, javacContext);
        }

        Object getFile(Object treePathInstance) throws ReflectiveOperationException {
            Method getLeafMethod = treePath.getMethod("getCompilationUnit");
            Object compilationUnitTree = getLeafMethod.invoke(treePathInstance);
            Field sourceFileField = jcCompilationUnit.getDeclaredField("sourcefile");
            return sourceFileField.get(compilationUnitTree);
        }

        Object useSource(Object logInstance, Object currentFile) throws ReflectiveOperationException {
            Method useSourceMethod = log.getMethod("useSource", JavaFileObject.class);
            return useSourceMethod.invoke(logInstance, currentFile);
        }

        void warnDeprecated(Object checkInstance, Object treePathInstance, Element element) throws ReflectiveOperationException {
            Method getLeafMethod = treePath.getMethod("getLeaf");
            Object elementTree = getLeafMethod.invoke(treePathInstance);
            Method warnDeprecatedMethod = check.getDeclaredMethod("warnDeprecated", diagnosticPosition, symbol);
            warnDeprecatedMethod.setAccessible(true);
            warnDeprecatedMethod.invoke(checkInstance, elementTree, element);
        }
    }
}
