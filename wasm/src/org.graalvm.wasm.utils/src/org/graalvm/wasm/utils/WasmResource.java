/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.graalvm.wasm.utils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WasmResource {
    public static String getResourceAsString(String resourceName, boolean fail) throws IOException {
        byte[] contents = getResourceAsBytes(resourceName, fail);
        if (contents != null) {
            return new String(contents);
        } else {
            assert !fail;
            return null;
        }
    }

    public static byte[] getResourceAsBytes(String resourceName, boolean fail) throws IOException {
        InputStream stream = WasmResource.class.getResourceAsStream(resourceName);
        if (stream == null) {
            if (fail) {
                Assert.fail(String.format("Could not find resource: %s", resourceName));
            } else {
                return null;
            }
        }
        byte[] contents = new byte[stream.available()];
        new DataInputStream(stream).readFully(contents);
        return contents;
    }

    public static Object getResourceAsTest(String baseName, boolean fail) throws IOException {
        final byte[] bytes = getResourceAsBytes(baseName + ".wasm", false);
        if (bytes != null) {
            return bytes;
        }
        final String text = getResourceAsString(baseName + ".wat", false);
        if (text != null) {
            return text;
        }
        if (fail) {
            Assert.fail(String.format("Could not find test (neither .wasm or .wat): %s", baseName));
        }
        return null;
    }
}
