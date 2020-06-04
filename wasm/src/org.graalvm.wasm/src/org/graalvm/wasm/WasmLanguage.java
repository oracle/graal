/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import org.graalvm.wasm.exception.WasmValidationException;
import org.graalvm.wasm.nodes.WasmEmptyRootNode;
import org.graalvm.options.OptionDescriptors;

@TruffleLanguage.Registration(id = "wasm", name = "WebAssembly", defaultMimeType = "application/wasm", byteMimeTypes = "application/wasm", contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE, fileTypeDetectors = WasmFileDetector.class, //
                interactive = false)
public final class WasmLanguage extends TruffleLanguage<WasmContext> {
    private static final int MIN_DEFAULT_STACK_SIZE = 1_000_000;
    private static final int MAX_DEFAULT_ASYNC_STACK_SIZE = 10_000_000;
    private boolean isFirst = true;

    @Override
    protected WasmContext createContext(Env env) {
        return new WasmContext(env, this);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        final WasmContext context = getCurrentContext();
        final String moduleName = isFirst ? "main" : request.getSource().getName();
        isFirst = false;
        final byte[] data = request.getSource().getBytes().toByteArray();
        final WasmOptions.StoreConstantsPolicyEnum storeConstantsPolicy = WasmOptions.StoreConstantsPolicy.getValue(context.environment().getOptions());
        final WasmModule module = new WasmModule(moduleName, data, storeConstantsPolicy);
        readModule(context, module, data);
        context.registerModule(module);
        return Truffle.getRuntime().createCallTarget(new WasmEmptyRootNode(this));
    }

    private void readModule(WasmContext context, WasmModule module, byte[] data) {
        int binarySize = data.length;
        final int asyncParsingBinarySize = WasmOptions.AsyncParsingBinarySize.getValue(context.environment().getOptions());
        if (binarySize < asyncParsingBinarySize) {
            readModuleSynchronously(context, module, data);
        } else {
            final Runnable parsing = new Runnable() {
                @Override
                public void run() {
                    readModuleSynchronously(context, module, data);
                }
            };
            final String name = "wasm-parsing-thread(" + module.name() + ")";
            final int requestedSize = WasmOptions.AsyncParsingStackSize.getValue(context.environment().getOptions()) * 1000;
            final int defaultSize = Math.max(MIN_DEFAULT_STACK_SIZE, Math.min(2 * binarySize, MAX_DEFAULT_ASYNC_STACK_SIZE));
            final int stackSize = requestedSize != 0 ? requestedSize : defaultSize;
            final Thread parsingThread = new Thread(null, parsing, name, stackSize);
            final ParsingExceptionHandler handler = new ParsingExceptionHandler();
            parsingThread.setUncaughtExceptionHandler(handler);
            parsingThread.start();
            try {
                parsingThread.join();
                if (handler.parsingException() != null) {
                    throw new WasmValidationException("Asynchronous parsing failed.", handler.parsingException());
                }
            } catch (InterruptedException e) {
                throw new WasmValidationException("Asynchronous parsing interrupted.", e);
            }
        }
    }

    private void readModuleSynchronously(WasmContext context, WasmModule module, byte[] data) {
        final BinaryParser reader = new BinaryParser(this, module, context, data);
        reader.readModule();
    }

    @Override
    protected Iterable<Scope> findTopScopes(WasmContext context) {
        return context.getTopScopes();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WasmOptionsOptionDescriptors();
    }

    static WasmContext getCurrentContext() {
        return getCurrentContext(WasmLanguage.class);
    }

    private class ParsingExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Throwable parsingException = null;

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            this.parsingException = e;
        }

        public Throwable parsingException() {
            return parsingException;
        }
    }
}
