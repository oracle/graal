/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.predefined.wasi.fd.FdManager;

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;

public final class WasmContext {
    private final Env env;
    private final WasmLanguage language;
    private final WasmContextOptions contextOptions;
    private final WasmStore contextStore;
    private final FdManager fdManager;
    private final MemoryContext memoryContext;

    /**
     * Optional grow callback to notify the embedder.
     */
    private Object memGrowCallback;
    /**
     * JS callback to implement part of memory.atomic.notify.
     */
    private Object memNotifyCallback;
    /**
     * JS callback to implement part of memory.atomic.waitN.
     */
    private Object memWaitCallback;

    @SuppressWarnings("this-escape")
    public WasmContext(Env env, WasmLanguage language) {
        this.env = env;
        this.language = language;
        this.contextOptions = WasmContextOptions.fromOptionValues(env.getOptions());
        this.fdManager = new FdManager(env);
        this.memoryContext = new MemoryContext();
        this.contextStore = new WasmStore(this, language);
    }

    public Env environment() {
        return env;
    }

    public WasmLanguage language() {
        return language;
    }

    public WasmStore contextStore() {
        return contextStore;
    }

    @SuppressWarnings("unused")
    public Object getScope() {
        return new WasmScope(contextStore);
    }

    public WasmModule readModule(byte[] data, ModuleLimits moduleLimits) {
        return readModule("Unnamed", data, moduleLimits);
    }

    public WasmModule readModule(String moduleName, byte[] data, ModuleLimits moduleLimits) {
        final WasmModule module = WasmModule.create(moduleName, moduleLimits);
        final BinaryParser reader = new BinaryParser(module, this, data);
        reader.readModule();
        return module;
    }

    public WasmContextOptions getContextOptions() {
        return this.contextOptions;
    }

    private static final ContextReference<WasmContext> REFERENCE = ContextReference.create(WasmLanguage.class);

    public static WasmContext get(Node node) {
        return REFERENCE.get(node);
    }

    public void setMemGrowCallback(Object callback) {
        this.memGrowCallback = callback;
    }

    public Object getMemGrowCallback() {
        return memGrowCallback;
    }

    public void setMemNotifyCallback(Object callback) {
        this.memNotifyCallback = callback;
    }

    public Object getMemNotifyCallback() {
        return memNotifyCallback;
    }

    public void setMemWaitCallback(Object callback) {
        this.memWaitCallback = callback;
    }

    public Object getMemWaitCallback() {
        return memWaitCallback;
    }

    public MemoryContext memoryContext() {
        return memoryContext;
    }

    public FdManager fdManager() {
        return fdManager;
    }
}
