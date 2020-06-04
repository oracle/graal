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
package org.graalvm.wasm.predefined.wasi;

import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.exception.WasmTrap;
import org.graalvm.wasm.memory.WasmMemory;
import org.graalvm.wasm.predefined.WasmBuiltinRootNode;

import static org.graalvm.wasm.WasmTracing.trace;

public class WasiFdWrite extends WasmBuiltinRootNode {

    public WasiFdWrite(WasmLanguage language, WasmModule module) {
        super(language, module);
    }

    @Override
    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
        Object[] args = frame.getArguments();
        assert args.length == 4;
        for (Object arg : args) {
            trace("argument: %s", arg);
        }

        int stream = (int) args[0];
        int iov = (int) args[1];
        int iovcnt = (int) args[2];
        int pnum = (int) args[3];

        return fdWrite(stream, iov, iovcnt, pnum);
    }

    @CompilerDirectives.TruffleBoundary
    private Object fdWrite(int stream, int iov, int iovcnt, int pnum) {
        Consumer<Character> charPrinter;
        switch (stream) {
            case 1:
                charPrinter = System.out::print;
                break;
            case 2:
                charPrinter = System.err::print;
                break;
            default:
                throw new WasmTrap(this, "WasiFdWrite: invalid file stream");
        }

        trace("WasiFdWrite EXECUTE");

        WasmMemory memory = module.symbolTable().memory();
        int num = 0;
        for (int i = 0; i < iovcnt; i++) {
            int ptr = memory.load_i32(this, iov + (i * 8 + 0));
            int len = memory.load_i32(this, iov + (i * 8 + 4));
            for (int j = 0; j < len; j++) {
                final char c = (char) memory.load_i32_8u(this, ptr + j);
                charPrinter.accept(c);
            }
            num += len;
            memory.store_i32(this, pnum, num);
        }

        return 0;
    }

    @Override
    public String builtinNodeName() {
        return "___wasi_fd_write";
    }
}
