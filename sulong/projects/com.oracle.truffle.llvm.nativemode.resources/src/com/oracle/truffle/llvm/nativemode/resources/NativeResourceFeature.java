/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode.resources;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Properties;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

import com.oracle.truffle.api.InternalResource.CPUArchitecture;
import com.oracle.truffle.api.InternalResource.OS;

public final class NativeResourceFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        String fileListPath = String.format("/META-INF/resources/llvm/native/%s/%s/files", OS.getCurrent(), CPUArchitecture.getCurrent());
        Properties props = new Properties();
        try (BufferedInputStream in = new BufferedInputStream(getClass().getResourceAsStream(fileListPath))) {
            props.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("error processing LLVM runtime resources", ex);
        }

        Module self = getClass().getModule();
        for (Object key : props.keySet()) {
            String path = (String) key;
            RuntimeResourceAccess.addResource(self, path);
        }
    }
}
