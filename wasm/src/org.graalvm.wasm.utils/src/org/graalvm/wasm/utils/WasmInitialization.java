/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.utils;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.memory.WasmMemory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class WasmInitialization implements Consumer<WasmContext>, TruffleObject {
    private final Map<String, Long> globalValues;
    private final Map<String, String> memoryValues;

    private WasmInitialization(Map<String, Long> globalValues, Map<String, String> memoryValues) {
        this.globalValues = globalValues;
        this.memoryValues = memoryValues;
    }

    public static WasmInitialization create(String initContent) {
        if (initContent == null) {
            return null;
        }

        final String[] lines = initContent.split("\n");
        Map<String, Long> globals = new LinkedHashMap<>();
        Map<String, String> memory = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.startsWith("[")) {
                // Memory store.
                String[] parts = line.split("=");
                String address = parts[0].substring(1, parts[0].length() - 1);
                String value = parts[1];
                memory.put(address, value);
            } else {
                // Global store.
                String[] parts = line.split("=");
                String name = parts[0];
                Long value = Long.parseLong(parts[1]);
                globals.put(name, value);
            }
        }

        return new WasmInitialization(globals, memory);
    }

    public void accept(WasmContext context) {
        try {
            final WasmModule envModule = context.modules().get("env");
            for (Map.Entry<String, Long> entry : globalValues.entrySet()) {
                final String name = entry.getKey();
                final Long value = entry.getValue();
                if (!envModule.isLinked() || envModule.global(name).isMutable()) {
                    envModule.writeMember(name, value);
                }
            }
            final WasmModule memoryModule = context.modules().get("memory");
            final WasmMemory memory = (WasmMemory) memoryModule.readMember("memory");
            for (Map.Entry<String, String> entry : memoryValues.entrySet()) {
                final String addressGlobal = entry.getKey();
                final long address = getValue(addressGlobal);
                final String valueGlobal = entry.getValue();
                final long value = getValue(valueGlobal);
                // TODO: This should most likely be a 32-bit store.
                // I think that the only reason why it works right now is little-endianess.
                // This will be checked in a separate PR.
                memory.growToAddress(address);
                memory.store_i64(null, address, value);
            }
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    private long getValue(String s) {
        if (Character.isDigit(s.charAt(0))) {
            return Integer.parseInt(s);
        } else {
            return globalValues.get(s);
        }
    }
}
