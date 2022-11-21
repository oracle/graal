/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.wasm.parser.ir.CodeEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Represents a parsed and validated WebAssembly module, which has not yet been instantiated.
 */
@SuppressWarnings("static-method")
public final class WasmModule extends SymbolTable implements TruffleObject {
    private final String name;
    private final ArrayList<BiConsumer<WasmContext, WasmInstance>> linkActions;
    private final ModuleLimits limits;
    private Source source;

    @CompilationFinal(dimensions = 1) private CodeEntry[] codeEntries;

    @CompilationFinal private boolean hasCodeSection;
    @CompilationFinal(dimensions = 1) private byte[] data;
    @CompilationFinal private boolean isParsed;

    private WasmModule(String name, byte[] data, ModuleLimits limits) {
        super();
        this.name = name;
        this.limits = limits == null ? ModuleLimits.DEFAULTS : limits;
        this.linkActions = new ArrayList<>();
        this.data = data;
        this.isParsed = false;
    }

    public static WasmModule create(String name, byte[] data, ModuleLimits limits) {
        return new WasmModule(name, data, limits);
    }

    public static WasmModule createBuiltin(String name) {
        return new WasmModule(name, null, null);
    }

    public ModuleLimits limits() {
        return limits;
    }

    @Override
    protected WasmModule module() {
        return this;
    }

    public void setParsed() {
        isParsed = true;
    }

    public boolean isParsed() {
        return isParsed;
    }

    public SymbolTable symbolTable() {
        return this;
    }

    public String name() {
        return name;
    }

    public byte[] data() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public boolean isBuiltin() {
        return data == null;
    }

    public Source source() {
        if (source == null) {
            if (isBuiltin()) {
                source = Source.newBuilder(WasmLanguage.ID, "", name).internal(true).build();
            } else {
                source = Source.newBuilder(WasmLanguage.ID, ByteSequence.create(data), name).build();
            }
        }
        return source;
    }

    public List<BiConsumer<WasmContext, WasmInstance>> linkActions() {
        return Collections.unmodifiableList(linkActions);
    }

    public void addLinkAction(BiConsumer<WasmContext, WasmInstance> action) {
        linkActions.add(action);
    }

    @Override
    public String toString() {
        return "wasm-module(" + name + ")";
    }

    public void setCodeEntries(CodeEntry[] codeEntries) {
        this.codeEntries = codeEntries;
        this.hasCodeSection = true;
    }

    public CodeEntry[] getCodeEntries() {
        return codeEntries;
    }

    public void removeCodeEntries() {
        codeEntries = null;
    }

    public boolean hasCodeEntries() {
        return codeEntries != null;
    }

    public boolean hasCodeSection() {
        return hasCodeSection;
    }
}
