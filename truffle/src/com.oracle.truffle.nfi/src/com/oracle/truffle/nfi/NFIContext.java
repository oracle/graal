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
package com.oracle.truffle.nfi;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.nfi.spi.NFIBackend;
import com.oracle.truffle.nfi.spi.NFIBackendFactory;
import com.oracle.truffle.nfi.spi.NFIBackendTools;

final class NFIContext {

    private static final NFIBackendTools TOOLS = new NFIBackendTools() {

        @Override
        public Object createBindableSymbol(Object symbol) {
            return NFISymbol.createBindable(symbol);
        }

        @Override
        public Object createBoundSymbol(Object symbol, Object signature) {
            return NFISymbol.createBound(symbol, signature);
        }
    };

    Env env;
    final EconomicMap<String, NFIBackend> backendCache = EconomicMap.create();

    NFIContext(Env env) {
        this.env = env;
    }

    void patch(Env newEnv) {
        this.env = newEnv;
        this.backendCache.clear();
    }

    NFIBackend getBackend(String id) {
        NFIBackend ret = backendCache.get(id);
        if (ret != null) {
            return ret;
        }

        synchronized (backendCache) {
            ret = backendCache.get(id);
            if (ret != null) {
                return ret;
            }

            for (LanguageInfo language : env.getInternalLanguages().values()) {
                if ("nfi".equals(language.getId())) {
                    continue;
                }

                NFIBackendFactory backendFactory = env.lookup(language, NFIBackendFactory.class);
                if (backendFactory != null && backendFactory.getBackendId().equals(id)) {
                    // force initialization of the backend language
                    env.initializeLanguage(language);

                    NFIBackend backend = backendFactory.createBackend(TOOLS);
                    backendCache.put(backendFactory.getBackendId(), backend);
                    return backend;
                }
            }
        }

        return null;
    }
}
