/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.MessageTransport;

public class HostPolyglotDispatch extends AbstractPolyglotImpl {

    private HostEntryPoint hostToGuest;

    public HostEntryPoint getHostToGuest() {
        if (hostToGuest == null) {
            hostToGuest = new HostEntryPoint(getNext());
        }
        return hostToGuest;
    }

    @Override
    public Engine buildEngine(String[] permittedLanguages, OutputStream out, OutputStream err, InputStream in, Map<String, String> options, boolean useSystemProperties,
                    boolean allowExperimentalOptions, boolean boundEngine,
                    MessageTransport messageInterceptor, Object logHandlerOrStream, Object hostLanguage, boolean hostLanguageOnly) {
        String option = options.get("engine.SpawnRemote");
        if (option != null && Boolean.parseBoolean(option)) {
            options.remove("engine.SpawnRemote");
            /*
             * indicates that the local engine ignores languages potentially on the class path.
             */
            boolean onlyHostLanguage = true;
            Engine localEngine = getNext().buildEngine(permittedLanguages, out, err, in, options, useSystemProperties, allowExperimentalOptions, boundEngine, messageInterceptor, logHandlerOrStream,
                            hostLanguage,
                            onlyHostLanguage);
            long remoteEngine = getHostToGuest().remoteCreateEngine();
            HostEngine engine = new HostEngine(remoteEngine, localEngine);
            return getAPIAccess().newEngine(new HostEngineDispatch(this), engine);
        } else {
            return getNext().buildEngine(permittedLanguages, out, err, in, options, useSystemProperties, allowExperimentalOptions, boundEngine, messageInterceptor, logHandlerOrStream, hostLanguage,
                            false);
        }
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

}
