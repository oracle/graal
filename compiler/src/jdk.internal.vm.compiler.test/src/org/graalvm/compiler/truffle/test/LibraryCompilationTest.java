/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.DynamicDispatchLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.BailoutException;

public class LibraryCompilationTest extends PartialEvaluationTest {

    @GenerateLibrary
    abstract static class CompilationLibrary extends Library {

        public abstract Object foldAsConstant(Object receiver);

    }

    private static class CompilationConstantRoot extends RootNode {

        @Child private CompilationLibrary library = LibraryFactory.resolve(CompilationLibrary.class).createDispatched(5);

        protected CompilationConstantRoot() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object value = library.foldAsConstant(frame.getArguments()[0]);
            CompilerAsserts.partialEvaluationConstant(value);
            return value;
        }
    }

    @ExportLibrary(CompilationLibrary.class)
    static final class FinalReceiver {
        @SuppressWarnings("static-method")
        @ExportMessage
        Object foldAsConstant() {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            return "FinalReceiver";
        }
    }

    @ExportLibrary(CompilationLibrary.class)
    static class NonFinalReceiver {
        @SuppressWarnings("static-method")
        @ExportMessage
        Object foldAsConstant() {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            return "NonFinalReceiver";
        }
    }

    @ExportLibrary(CompilationLibrary.class)
    static class SubClassReceiver extends NonFinalReceiver {
        @Override
        @SuppressWarnings("static-method")
        @ExportMessage
        Object foldAsConstant() {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            return "SubClassReceiver";
        }
    }

    @ExportLibrary(DynamicDispatchLibrary.class)
    static final class DynamicDispatchReceiver1 {

        private final Class<?> export;

        DynamicDispatchReceiver1(Class<?> export) {
            this.export = export;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Class<?> dispatch() {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            return export;
        }
    }

    @ExportLibrary(DynamicDispatchLibrary.class)
    static class DynamicDispatchReceiver2 {

        private final Class<?> export;

        DynamicDispatchReceiver2(Class<?> export) {
            this.export = export;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        Class<?> dispatch() {
            CompilerAsserts.partialEvaluationConstant(this.getClass());
            return export;
        }
    }

    static class DynamicDispatchReceiver2Sub extends DynamicDispatchReceiver2 {

        DynamicDispatchReceiver2Sub(Class<?> export) {
            super(export);
        }

    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver1.class)
    static class DynamicDispatchExports1 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver1 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports1";
        }
    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver1.class)
    static class DynamicDispatchExports2 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver1 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports2";
        }
    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver2.class)
    static class DynamicDispatchExports3 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver2 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports3";
        }
    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver2.class)
    static class DynamicDispatchExports4 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver2 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports4";
        }
    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver2.class)
    static class DynamicDispatchExports5 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver2 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports5";
        }
    }

    @ExportLibrary(value = CompilationLibrary.class, receiverType = DynamicDispatchReceiver2.class)
    static class DynamicDispatchExports6 {
        @SuppressWarnings("static-method")
        @ExportMessage
        static Object foldAsConstant(@SuppressWarnings("unused") DynamicDispatchReceiver2 receiver) {
            CompilerAsserts.partialEvaluationConstant(receiver.getClass());
            return "DynamicDispatchExports6";
        }
    }

    @Test
    public void testCompilation() {
        // instantiate first to make sure all classes are loaded
        Object finalReceiver = new FinalReceiver();
        Object subReceiver = new SubClassReceiver();
        Object nonFinalReceiver = new NonFinalReceiver();
        Object dynamicDispatchReceiver1 = new DynamicDispatchReceiver1(DynamicDispatchExports1.class);
        Object dynamicDispatchReceiver2 = new DynamicDispatchReceiver1(DynamicDispatchExports2.class);
        Object dynamicDispatchReceiver3 = new DynamicDispatchReceiver2(DynamicDispatchExports3.class);
        Object dynamicDispatchReceiver4 = new DynamicDispatchReceiver2(DynamicDispatchExports4.class);
        Object dynamicDispatchReceiver5 = new DynamicDispatchReceiver2Sub(DynamicDispatchExports5.class);
        Object dynamicDispatchReceiver6 = new DynamicDispatchReceiver2Sub(DynamicDispatchExports6.class);

        assertCompiling(new CompilationConstantRoot(), finalReceiver);
        assertCompiling(new CompilationConstantRoot(), subReceiver);
        assertCompiling(new CompilationConstantRoot(), nonFinalReceiver);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver1);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver2);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver3);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver4);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver5);
        assertCompiling(new CompilationConstantRoot(), dynamicDispatchReceiver6);
    }

    private OptimizedCallTarget assertCompiling(RootNode node, Object... arguments) {
        try {
            return compileHelper("assertCompiling", node, arguments);
        } catch (BailoutException e) {
            throw new AssertionError("bailout not expected", e);
        }
    }

}
