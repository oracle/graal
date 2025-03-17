/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * Test language that ensures that only engine overhead is tested.
 */
@TruffleLanguage.Registration(id = EngineBenchmark.TEST_LANGUAGE, name = "")
public class EngineBenchmarkTestLanguage extends TruffleLanguage<EngineBenchmarkTestLanguage.BenchmarkContext> {

    @Override
    protected BenchmarkContext createContext(Env env) {
        return new BenchmarkContext(env);
    }

    @Override
    protected void initializeContext(BenchmarkContext context) throws Exception {
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @SuppressWarnings("deprecation")
    public static Env getCurrentEnv() {
        return getCurrentContext(EngineBenchmarkTestLanguage.class).env;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Object result;
        if (request.getSource().getName().equals(EngineBenchmark.CONTEXT_LOOKUP)) {
            result = new BenchmarkObjectLookup(Integer.parseInt(request.getSource().getCharacters().toString()));
        } else {
            result = getCurrentContext(EngineBenchmarkTestLanguage.class).object;
        }
        return RootNode.createConstantNode(result).getCallTarget();
    }

    public static final class BenchmarkContext {

        final Env env;
        final BenchmarkObjectConstant object = new BenchmarkObjectConstant();
        final int index = 0;

        BenchmarkContext(Env env) {
            this.env = env;
        }

        static final ContextReference<BenchmarkContext> REFERENCE = ContextReference.create(EngineBenchmarkTestLanguage.class);

        static BenchmarkContext get(Node node) {
            return REFERENCE.get(node);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused", "hiding"})
    public static class BenchmarkObjectLookup extends BenchmarkObjectConstant {

        final int iterations;

        BenchmarkObjectLookup(int iterations) {
            this.iterations = iterations;
        }

        @SuppressWarnings("deprecation")
        @ExportMessage
        @ExplodeLoop
        final Object execute(Object[] arguments,
                        @CachedLibrary("this") InteropLibrary lib,
                        @Cached(value = "this.iterations", neverDefault = false) int cachedIterations) {
            int sum = 0;
            for (int i = 0; i < cachedIterations; i++) {
                sum += BenchmarkContext.get(lib).index;
            }
            // usage value so it is not collected.
            CompilerDirectives.blackhole(sum);
            return BenchmarkObjectConstant.constant;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings({"static-method", "unused", "hiding"})
    public static class BenchmarkObjectConstant implements TruffleObject {

        private static final Integer constant = 42;

        Object value = 42;
        long longValue = 42L;

        @ExportMessage
        protected final boolean hasMembers() {
            return true;
        }

        @ExportMessage
        protected final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        protected final boolean isExecutable() {
            return true;
        }

        @ExportMessage
        protected final Object getMembers(boolean includeInternal) {
            return null;
        }

        @ExportMessage
        protected final Object readArrayElement(long index) {
            return value;
        }

        @ExportMessage
        protected final void writeArrayElement(long index, Object value) {
            this.value = value;
        }

        @ExportMessage
        protected final boolean isArrayElementInsertable(long index) {
            return true;
        }

        @ExportMessage
        protected final long getArraySize() {
            return 0L;
        }

        @ExportMessage
        protected final boolean isArrayElementReadable(long index) {
            return true;
        }

        @ExportMessage
        protected final boolean isArrayElementModifiable(long index) {
            return true;
        }

        @ExportMessage
        protected final Object execute(Object[] arguments) {
            return constant;
        }

        @ExportMessage
        protected final boolean isMemberReadable(String member) {
            return true;
        }

        @ExportMessage
        protected final boolean isMemberModifiable(String member) {
            return true;
        }

        @ExportMessage
        protected final boolean isMemberInsertable(String member) {
            return true;
        }

        @ExportMessage
        protected final void writeMember(String member, Object value) {
            this.value = value;
        }

        @ExportMessage
        protected final Object readMember(String member) {
            return value;
        }

        @ExportMessage
        protected final boolean isPointer() {
            return true;
        }

        @ExportMessage
        protected final long asPointer() {
            return longValue;
        }

        @ExportMessage
        protected final boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        protected final Class<? extends TruffleLanguage<?>> getLanguage() {
            return EngineBenchmarkTestLanguage.class;
        }

        @ExportMessage
        protected final Object toDisplayString(boolean allowSideEffects) {
            return "displayString";
        }

    }
}
