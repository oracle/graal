/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.error_tests;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class TestVariantErrorTest {

    @ExpectError("A variant with suffix \"A\" already exists. Each variant must have a unique suffix.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class))})
    @OperationProxy(ConstantOperation.class)
    public abstract static class SameName extends RootNode implements BytecodeRootNode {
        protected SameName(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must use the same language class.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "B", configuration = @GenerateBytecode(languageClass = AnotherErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentLanguage extends RootNode implements BytecodeRootNode {
        protected DifferentLanguage(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must have the same value for enableYield.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class, enableYield = true)),
                    @Variant(suffix = "B", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentYield extends RootNode implements BytecodeRootNode {
        protected DifferentYield(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    // no errors expected
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "Tier1", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "Tier0", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class, enableUncachedInterpreter = true))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentUncachedInterpreters extends RootNode implements BytecodeRootNode {
        protected DifferentUncachedInterpreters(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    public class ErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

    public class AnotherErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

}

@SuppressWarnings("truffle-inlining")
@OperationProxy.Proxyable(allowUncached = true)
abstract class ConstantOperation extends Node {
    public abstract long execute();

    @Specialization
    public static long doLong() {
        return 42L;
    }
}
