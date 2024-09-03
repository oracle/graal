/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.wasm.api.JsConstants;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.BuiltinModule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@Registration(id = WasmLanguage.ID, //
                name = WasmLanguage.NAME, //
                defaultMimeType = WasmLanguage.WASM_MIME_TYPE, //
                byteMimeTypes = WasmLanguage.WASM_MIME_TYPE, //
                contextPolicy = TruffleLanguage.ContextPolicy.SHARED, //
                fileTypeDetectors = WasmFileDetector.class, //
                interactive = false, //
                website = "https://www.graalvm.org/")
@ProvidedTags({StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.StatementTag.class})
public final class WasmLanguage extends TruffleLanguage<WasmContext> {
    public static final String ID = "wasm";
    public static final String NAME = "WebAssembly";
    public static final String WASM_MIME_TYPE = "application/wasm";
    public static final String WASM_SOURCE_NAME_SUFFIX = ".wasm";
    public static final String PARSE_JS_MODULE_MARKER = "js_module_decode";
    public static final String[] PARSE_JS_MODULE_ARGS = {PARSE_JS_MODULE_MARKER};

    private static final LanguageReference<WasmLanguage> REFERENCE = LanguageReference.create(WasmLanguage.class);

    @CompilationFinal private volatile boolean isMultiContext;

    private final ContextThreadLocal<MultiValueStack> multiValueStackThreadLocal = locals.createContextThreadLocal(((context, thread) -> new MultiValueStack()));

    private final Map<BuiltinModule, WasmModule> builtinModules = new ConcurrentHashMap<>();

    private final Map<SymbolTable.FunctionType, Integer> equivalenceClasses = new ConcurrentHashMap<>();
    private int nextEquivalenceClass = SymbolTable.FIRST_EQUIVALENCE_CLASS;

    public int equivalenceClassFor(SymbolTable.FunctionType type) {
        Integer equivalenceClass = equivalenceClasses.get(type);
        if (equivalenceClass == null) {
            synchronized (this) {
                equivalenceClass = equivalenceClasses.get(type);
                if (equivalenceClass == null) {
                    equivalenceClass = nextEquivalenceClass++;
                    Integer prev = equivalenceClasses.put(type, equivalenceClass);
                    assert prev == null;
                }
            }
        }
        return equivalenceClass;
    }

    @Override
    protected WasmContext createContext(Env env) {
        WasmContext context = new WasmContext(env, this);
        if (env.isPolyglotBindingsAccessAllowed()) {
            env.exportSymbol("WebAssembly", new WebAssembly(context));
        }
        return context;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        final WasmContext context = WasmContext.get(null);
        final Source source = request.getSource();
        final String moduleName = source.getName();
        final byte[] data = source.getBytes().toByteArray();
        ModuleLimits moduleLimits = null;
        if (!request.getArgumentNames().isEmpty() && PARSE_JS_MODULE_MARKER.equals(request.getArgumentNames().get(0))) {
            moduleLimits = JsConstants.JS_LIMITS;
        }
        final WasmModule module = context.readModule(moduleName, data, moduleLimits);
        return new ParsedWasmModuleRootNode(this, module, source).getCallTarget();
    }

    private static final class ParsedWasmModuleRootNode extends RootNode {
        private final WasmModule module;
        private final Source source;

        private ParsedWasmModuleRootNode(WasmLanguage language, WasmModule module, Source source) {
            super(language);
            this.module = module;
            this.source = source;
        }

        @Override
        public WasmInstance execute(VirtualFrame frame) {
            final WasmContext context = WasmContext.get(this);
            WasmInstance instance = context.lookupModuleInstance(module);
            if (instance == null) {
                instance = context.readInstance(module);
            }
            return instance;
        }

        @Override
        public SourceSection getSourceSection() {
            return source.createUnavailableSection();
        }

        WasmModule getModule() {
            return module;
        }
    }

    public static WasmModule getParsedModule(CallTarget parseResult) {
        if (parseResult instanceof RootCallTarget rct && rct.getRootNode() instanceof ParsedWasmModuleRootNode moduleRoot) {
            return moduleRoot.getModule();
        } else {
            return null;
        }
    }

    @Override
    protected Object getScope(WasmContext context) {
        return context.getScope();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WasmOptionsOptionDescriptors();
    }

    @Override
    protected void finalizeContext(WasmContext context) {
        super.finalizeContext(context);
        for (int i = 0; i < context.memories().count(); ++i) {
            final WasmMemory memory = context.memories().memory(i);
            memory.close();
        }
        try {
            context.fdManager().close();
        } catch (IOException e) {
            throw new RuntimeException("Error while closing WasmFilesManager.");
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected void initializeMultipleContexts() {
        isMultiContext = true;
    }

    public boolean isMultiContext() {
        return isMultiContext;
    }

    public static WasmLanguage get(Node node) {
        return REFERENCE.get(node);
    }

    public WasmModule getOrCreateBuiltinModule(BuiltinModule builtinModule, Function<? super BuiltinModule, ? extends WasmModule> factory) {
        return builtinModules.computeIfAbsent(builtinModule, factory);
    }

    @Override
    protected boolean areOptionsCompatible(OptionValues firstOptions, OptionValues newOptions) {
        if (!firstOptions.hasSetOptions() && !newOptions.hasSetOptions()) {
            return true;
        } else if (firstOptions.equals(newOptions)) {
            return true;
        } else {
            return WasmContextOptions.fromOptionValues(firstOptions).equals(WasmContextOptions.fromOptionValues(newOptions));
        }
    }

    public MultiValueStack multiValueStack() {
        return multiValueStackThreadLocal.get();
    }

    public static final class MultiValueStack {
        private long[] primitiveStack;
        private Object[] objectStack;
        // Initialize size to 1, so we only create the stack for more than 1 result value.
        private int size = 1;

        /**
         * @return The current primitive multi-value stack or null if it has never been resized.
         */
        public long[] primitiveStack() {
            return primitiveStack;
        }

        /**
         * @return the current object multi-value stack or null if it has never been resized.
         */
        public Object[] objectStack() {
            return objectStack;
        }

        /**
         * Updates the size of the multi-value stack if needed. In case of a resize, the values are
         * not copied. Therefore, resizing should occur before any call to a function that uses the
         * multi-value stack.
         *
         * @param expectedSize The minimum expected size.
         */
        public void resize(int expectedSize) {
            if (expectedSize > size) {
                primitiveStack = new long[expectedSize];
                objectStack = new Object[expectedSize];
                size = expectedSize;
            }
        }
    }
}
