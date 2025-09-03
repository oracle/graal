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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Represents an instantiated WebAssembly module.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public final class WasmInstance extends RuntimeState implements TruffleObject {

    private List<LinkAction> linkActions;

    public WasmInstance(WasmStore store, WasmModule module) {
        this(store, module, module.numFunctions(), module.droppedDataInstanceOffset());
    }

    private WasmInstance(WasmStore store, WasmModule module, int numberOfFunctions, int droppedDataInstanceAddress) {
        super(store, module, numberOfFunctions, droppedDataInstanceAddress);
    }

    public String name() {
        return module().name();
    }

    /**
     * Try to infer the entry function for this instance. Not part of the spec, for testing purpose
     * only.
     *
     * @return exported function named {@code _main}, exported function named {@code _start}, start
     *         function or {@code null} in this order.
     */
    public WasmFunctionInstance inferEntryPoint() {
        final WasmFunction mainFunction = symbolTable().exportedFunctions().get("_main");
        if (mainFunction != null) {
            return functionInstance(mainFunction);
        }
        final WasmFunction startFunction = symbolTable().exportedFunctions().get("_start");
        if (startFunction != null) {
            return functionInstance(startFunction);
        }
        if (symbolTable().startFunction() != null) {
            return functionInstance(symbolTable().startFunction());
        }
        return null;
    }

    private void ensureLinked() {
        store().linker().tryLink(this);
    }

    public List<LinkAction> linkActions() {
        return linkActions;
    }

    public List<LinkAction> createLinkActions() {
        return linkActions = module().getOrRecreateLinkActions();
    }

    public void addLinkAction(LinkAction action) {
        linkActions.add(action);
    }

    public void removeLinkActions() {
        this.linkActions = null;
    }

    @Override
    protected WasmInstance instance() {
        return this;
    }

    private static final String EXPORTS_MEMBER = "exports";

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        return EXPORTS_MEMBER.equals(member);
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        if (!isMemberReadable(member)) {
            throw UnknownIdentifierException.create(member);
        }
        ensureLinked();
        assert EXPORTS_MEMBER.equals(member) : member;
        return new WasmInstanceExports(this);
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new WasmNamesObject(new String[]{EXPORTS_MEMBER});
    }

    @Override
    public String toString() {
        return "wasm-module-instance(" + name() + ")";
    }
}
