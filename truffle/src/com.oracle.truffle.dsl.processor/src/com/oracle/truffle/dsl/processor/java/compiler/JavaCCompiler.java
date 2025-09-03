/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class JavaCCompiler extends AbstractCompiler {

    private static volatile Reflection reflection;

    private static class Reflection {
        final Class<?> clsTrees;
        final Method metTreesGetPath;
        final Method metTreePathGetCompilationUnit;
        final Method metCompilationUnitTreeGetSourceFile;

        Reflection(ClassLoader cl) throws ReflectiveOperationException {
            clsTrees = Class.forName("com.sun.source.util.Trees", false, cl);
            metTreesGetPath = clsTrees.getMethod("getPath", new Class<?>[]{Element.class});

            Class<?> clsTreePath = Class.forName("com.sun.source.util.TreePath", false, cl);
            metTreePathGetCompilationUnit = clsTreePath.getMethod("getCompilationUnit", new Class<?>[0]);

            Class<?> clsCompilationUnitTree = Class.forName("com.sun.source.tree.CompilationUnitTree", false, cl);
            metCompilationUnitTreeGetSourceFile = clsCompilationUnitTree.getDeclaredMethod("getSourceFile", new Class<?>[0]);
        }
    }

    private static void initializeReflectionClasses(Object classLoaderSupplier) throws ReflectiveOperationException {
        if (reflection == null) {
            synchronized (JavaCCompiler.class) {
                if (reflection == null) {
                    reflection = new Reflection(classLoaderSupplier.getClass().getClassLoader());
                }
            }
        }
    }

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
            Object javacContext = method(environment, "getContext");
            Object elementTreePath = getTreePathForElement(environment, element);
            if (elementTreePath == null) {
                return false;
            }
            Object log = getLog(javacContext);
            Object check = getCheck(javacContext);
            Object file = field(method(elementTreePath, "getCompilationUnit"), "sourcefile");
            Object prev = useSource(log, file);
            try {
                reportProblem(check, elementTreePath, element);
            } finally {
                useSource(log, prev);
            }
            return true;
        } catch (ReflectiveOperationException reflectiveException) {
            return false;
        }
    }

    private static Object getTrees(ProcessingEnvironment environment, Element element) throws ReflectiveOperationException {
        initializeReflectionClasses(element);
        return staticMethod(reflection.clsTrees, "instance", new Class<?>[]{ProcessingEnvironment.class}, environment);
    }

    private static Object getTreePathForElement(ProcessingEnvironment environment, Element element) throws ReflectiveOperationException {
        Object trees = getTrees(environment, element);
        return reflection.metTreesGetPath.invoke(trees, element);
    }

    private static Object getLog(Object javacContext) throws ReflectiveOperationException {
        ClassLoader cl = javacContext.getClass().getClassLoader();
        Class<?> logClass = Class.forName("com.sun.tools.javac.util.Log", false, cl);
        Class<?> contextClass = Class.forName("com.sun.tools.javac.util.Context", false, cl);
        return staticMethod(logClass, "instance", new Class<?>[]{contextClass}, javacContext);
    }

    private static Object getCheck(Object javacContext) throws ReflectiveOperationException {
        ClassLoader cl = javacContext.getClass().getClassLoader();
        Class<?> checkClass = Class.forName("com.sun.tools.javac.comp.Check");
        Class<?> contextClass = Class.forName("com.sun.tools.javac.util.Context", false, cl);
        return staticMethod(checkClass, "instance", new Class<?>[]{contextClass}, javacContext);
    }

    private static Object useSource(Object log, Object currentFile) throws ReflectiveOperationException {
        return method(log, "useSource", new Class<?>[]{JavaFileObject.class}, currentFile);
    }

    private static void reportProblem(Object check, Object treePath, Element element) throws ReflectiveOperationException {
        ClassLoader cl = check.getClass().getClassLoader();
        Class<?> diagnosticPositionClass = Class.forName("com.sun.tools.javac.util.JCDiagnostic$DiagnosticPosition", false, cl);
        Class<?> symbolClass = Class.forName("com.sun.tools.javac.code.Symbol", false, cl);
        Object elementTree = method(treePath, "getLeaf");
        method(check, "warnDeprecated", new Class<?>[]{diagnosticPositionClass, symbolClass}, elementTree, element);
    }

    @Override
    public File getEnclosingSourceFile(ProcessingEnvironment processingEnv, Element element) {
        try {
            Object treePath = getTreePathForElement(processingEnv, element);
            Object compilationUnit = reflection.metTreePathGetCompilationUnit.invoke(treePath);
            JavaFileObject obj = (JavaFileObject) reflection.metCompilationUnitTreeGetSourceFile.invoke(compilationUnit);
            return new File(obj.toUri());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("should not happen", e);
        }
    }
}
