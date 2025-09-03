/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.NFIRootNodeFactory.LoadLibraryNodeGen;
import com.oracle.truffle.nfi.NFIRootNodeFactory.LookupAndBindNodeGen;
import com.oracle.truffle.nfi.NativeSource.ParsedLibrary;
import com.oracle.truffle.nfi.SignatureRootNode.BuildSignatureNode;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackend;
import com.oracle.truffle.nfi.backend.spi.types.NativeLibraryDescriptor;

class NFIRootNode extends RootNode {

    abstract static class LookupAndBindNode extends Node {

        private final String name;
        @Child BuildSignatureNode signature;

        LookupAndBindNode(String name, BuildSignatureNode signature) {
            this.name = name;
            this.signature = signature;
        }

        abstract Object execute(API api, Object library);

        @Specialization(limit = "1")
        Object doLookupAndBind(API api, Object library,
                        @CachedLibrary("library") InteropLibrary libInterop,
                        @CachedLibrary(limit = "1") SignatureLibrary signatures) {
            try {
                Object symbol = libInterop.readMember(library, name);
                Object sig = signature.execute(api);
                return signatures.bind(sig, symbol);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new NFIPreBindException(ex.getMessage(), this);
            }
        }
    }

    abstract static class LoadLibraryNode extends Node {

        private final NativeLibraryDescriptor descriptor;

        LoadLibraryNode(NativeLibraryDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        protected abstract Object execute(NFIBackend backend);

        @TruffleBoundary
        CallTarget parseLibrary(NFIBackend backend) {
            return backend.parse(descriptor);
        }

        @Specialization(limit = "5", guards = "backend == cachedBackend")
        Object doCached(NFIBackend backend,
                        @Cached(value = "backend", weak = true) NFIBackend cachedBackend,
                        @Cached("create(parseLibrary(cachedBackend))") DirectCallNode callNode) {
            assert backend == cachedBackend;
            return callNode.call();
        }

        @Specialization(replaces = "doCached")
        Object doGeneric(NFIBackend backend,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(parseLibrary(backend));
        }
    }

    private abstract class GetBackendNode extends Node {

        abstract API execute();
    }

    private final class GetSelectedBackendNode extends GetBackendNode {

        private final String backendId;

        GetSelectedBackendNode(String backendId) {
            this.backendId = backendId;
        }

        @Override
        API execute() {
            return NFIContext.get(this).getAPI(backendId, this);
        }
    }

    private final class GetDefaultBackendNode extends GetBackendNode {

        @TruffleBoundary
        String getDefaultBackendOption(OptionValues values) {
            return values.get(NFIOptions.DEFAULT_BACKEND);
        }

        @Override
        API execute() {
            NFIContext ctx = NFIContext.get(this);
            String defaultBackendId = getDefaultBackendOption(ctx.env.getOptions());
            return ctx.getAPI(defaultBackendId, this);
        }
    }

    @Child LoadLibraryNode loadLibrary;
    @Children LookupAndBindNode[] lookupAndBind;

    @Child GetBackendNode getBackend;

    NFIRootNode(NFILanguage language, ParsedLibrary source, String backendId) {
        super(language);
        this.loadLibrary = LoadLibraryNodeGen.create(source.getLibraryDescriptor());
        this.lookupAndBind = new LookupAndBindNode[source.preBoundSymbolsLength()];

        if (backendId == null) {
            getBackend = new GetDefaultBackendNode();
        } else {
            getBackend = new GetSelectedBackendNode(backendId);
        }

        for (int i = 0; i < lookupAndBind.length; i++) {
            lookupAndBind[i] = LookupAndBindNodeGen.create(source.getPreBoundSymbol(i), source.getPreBoundSignature(i));
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        API api = getBackend.execute();

        Object library = loadLibrary.execute(api.backend);
        if (lookupAndBind.length == 0) {
            return library;
        } else {
            NFILibrary ret = new NFILibrary(library);
            for (LookupAndBindNode l : lookupAndBind) {
                ret.preBindSymbol(l.name, l.execute(api, library));
            }
            return ret;
        }
    }
}
