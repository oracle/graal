/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.predefined.wasi;

import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;
import org.graalvm.wasm.predefined.wasi.fd.Fd;
import org.graalvm.wasm.predefined.wasi.types.Errno;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WasiPathFileStatGetNode extends WasmBuiltinRootNode {

    public WasiPathFileStatGetNode(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
        final Object[] args = frame.getArguments();
        return pathFilestatGet(context, (int) args[0], (int) args[1], (int) args[2], (int) args[3], (int) args[4]);
    }

    @TruffleBoundary
    private int pathFilestatGet(WasmContext context, int fd, int flags, int pathAddress, int pathLength, int resultAddress) {
        final Fd handle = context.fdManager().get(fd);
        if (handle == null) {
            return Errno.Badf.ordinal();
        }
        return handle.pathFilestatGet(this, memory(), flags, pathAddress, pathLength, resultAddress).ordinal();
    }

    @Override
    public String builtinNodeName() {
        return "__wasi_path_filestat_get";
    }
}
