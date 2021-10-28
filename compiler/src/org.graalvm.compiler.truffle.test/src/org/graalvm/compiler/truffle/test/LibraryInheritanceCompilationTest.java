/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.test.LibraryInheritanceCompilationTestFactory.CompilationConstantRootNodeGen;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.RootNode;

public class LibraryInheritanceCompilationTest extends PartialEvaluationTest {

    @GenerateLibrary
    @SuppressWarnings("unused")
    abstract static class CompilationInheritanceLibrary extends Library {

        public boolean read0(Object receiver) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        public boolean read1(Object receiver) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        public boolean read2(Object receiver) {
            throw CompilerDirectives.shouldNotReachHere();
        }

    }

    @ExportLibrary(CompilationInheritanceLibrary.class)
    static class BaseObjectCIL {

        boolean baseRef = Boolean.TRUE;

        @ExportMessage
        boolean read0() {
            return baseRef;
        }

    }

    @ExportLibrary(CompilationInheritanceLibrary.class)
    static class SubObjectCIL extends BaseObjectCIL {

        boolean subRef = Boolean.TRUE;

        @ExportMessage
        boolean read1() {
            return subRef;
        }

    }

    @ExportLibrary(CompilationInheritanceLibrary.class)
    static class SubSubObjectCIL extends SubObjectCIL {

        boolean subSubRef = Boolean.TRUE;

        @ExportMessage
        boolean read2() {
            return subSubRef;
        }

    }

    @ExportLibrary(CompilationInheritanceLibrary.class)
    static final class SubSubSubObjectCIL extends SubSubObjectCIL {

        @ExportMessage(name = "read0")
        boolean read0override() {
            return baseRef;
        }

        @ExportMessage(name = "read1")
        boolean read1override() {
            return subRef;
        }

        @ExportMessage(name = "read2")
        boolean read2override() {
            return subSubRef;
        }

    }

    abstract static class CompilationConstantRootNode extends RootNode {

        private int argumentIndex;

        protected CompilationConstantRootNode(int argumentIndex) {
            super(null);
            this.argumentIndex = argumentIndex;
        }

        @Specialization(limit = "3")
        boolean doRead(Object value, @CachedLibrary("value") CompilationInheritanceLibrary lib) {
            return lib.read0(value) & lib.read1(value) & lib.read2(value);

        }

        abstract Object execute(Object arg);

        @Override
        public final Object execute(VirtualFrame frame) {
            Object[] innerArgs = (Object[]) frame.getArguments()[0];
            return execute(innerArgs[argumentIndex]);
        }
    }

    /*
     * This tests that generated libraries used with final types and exports with inherited messages
     * produce exactly the same code (besides the actual type that is checked).
     */
    @Test
    public void testInheritanceCompilation() {
        CompilationConstantRootNode expected = CompilationConstantRootNodeGen.create(0);
        CompilationConstantRootNode actual = CompilationConstantRootNodeGen.create(1);

        assertPartialEvalEquals(expected, actual, new Object[]{new Object[]{new SubSubObjectCIL(), new SubSubSubObjectCIL()}}, false);
    }

}
