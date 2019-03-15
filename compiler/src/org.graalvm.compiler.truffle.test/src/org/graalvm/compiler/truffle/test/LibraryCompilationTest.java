/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.truffle.runtime.TruffleRuntimeOptions;
import org.junit.Test;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
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
            return "FinalReceiver";
        }
    }

    @ExportLibrary(CompilationLibrary.class)
    static class NonFinalReceiver {
        @SuppressWarnings("static-method")
        @ExportMessage
        Object foldAsConstant() {
            return "NonFinalReceiver";
        }
    }

    @ExportLibrary(CompilationLibrary.class)
    static class SubClassReceiver extends NonFinalReceiver {
        @Override
        @SuppressWarnings("static-method")
        @ExportMessage
        Object foldAsConstant() {
            return "SubClassReceiver";
        }
    }

    @Test
    public void testCompilation() {
        // instantiate first to make sure all classes are loaded
        Object finalReceiver = new FinalReceiver();
        Object subReceiver = new SubClassReceiver();
        Object nonFinalReceiver = new NonFinalReceiver();

        assertCompiling(new CompilationConstantRoot(), finalReceiver);
        assertCompiling(new CompilationConstantRoot(), subReceiver);
        assertCompiling(new CompilationConstantRoot(), nonFinalReceiver);
    }

    private OptimizedCallTarget assertCompiling(RootNode node, Object... arguments) {
        try {
            return compileHelper("assertCompiling", node, arguments);
        } catch (BailoutException e) {
            throw new AssertionError("bailout not expected", e);
        }
    }

}
