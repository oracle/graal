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
package com.oracle.truffle.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;

public class TruffleProcessBuilder {

    private final Object polylgotLanguageContext;
    private List<String> cmd;
    private TruffleFile cwd;
    private Map<String, String> env;
    private boolean redirectErrorStream;
    private Redirect[] redirects;

    TruffleProcessBuilder(Object polylgotLanguageContext, List<String> command) {
        Objects.requireNonNull(polylgotLanguageContext, "PolylgotLanguageContext must be non null.");
        Objects.requireNonNull(command, "Command must be non null.");
        this.polylgotLanguageContext = polylgotLanguageContext;
        this.cmd = command;
        this.redirects = new Redirect[]{Redirect.PIPE, Redirect.PIPE, Redirect.PIPE};
    }

    public TruffleProcessBuilder command(List<String> command) {
        Objects.requireNonNull(command, "Command must be non null.");
        this.cmd = new ArrayList<>(command);
        return this;
    }

    public TruffleProcessBuilder command(String... command) {
        Objects.requireNonNull(command, "Command must be non null.");
        this.cmd = new ArrayList<>(command.length);
        Collections.addAll(cmd, command);
        return this;
    }

    public List<String> command() {
        return cmd;
    }

    public TruffleProcessBuilder directory(TruffleFile currentWorkingDirectory) {
        Objects.requireNonNull(currentWorkingDirectory, "CurrentWorkingDirectory must be non null.");
        this.cwd = currentWorkingDirectory;
        return this;
    }

    public TruffleFile directory() {
        return this.cwd;
    }

    public TruffleProcessBuilder redirectErrorStream(boolean enabled) {
        this.redirectErrorStream = enabled;
        return this;
    }

    public boolean redirectErrorStream() {
        return redirectErrorStream;
    }

    public TruffleProcessBuilder redirectInput(Redirect destination) {
        Objects.requireNonNull(destination, "Destination must be non null.");
        redirects[0] = destination;
        return this;
    }

    public Redirect redirectInput() {
        return redirects[0];
    }

    public TruffleProcessBuilder redirectOutput(Redirect destination) {
        Objects.requireNonNull(destination, "Destination must be non null.");
        redirects[1] = destination;
        return this;
    }

    public Redirect redirectOutput() {
        return redirects[1];
    }

    public TruffleProcessBuilder redirectError(Redirect destination) {
        Objects.requireNonNull(destination, "Destination must be non null.");
        redirects[2] = destination;
        return this;
    }

    public Redirect redirectError() {
        return redirects[2];
    }

    public TruffleProcessBuilder inheritIO() {
        Arrays.fill(redirects, Redirect.INHERIT);
        return this;
    }

    public TruffleProcessBuilder addToEnvironment(Map<String, String> environment) {
        environment().putAll(environment);
        return this;
    }

    public Map<String, String> environment() {
        if (env == null) {
            env = new HashMap<>(TruffleLanguage.AccessAPI.engineAccess().getProcessEnvironment(polylgotLanguageContext));
        }
        assert env != null;
        return env;
    }

    public Process start() throws IOException {
        ProcessHandler.ProcessCommand processCommand = TruffleLanguage.AccessAPI.engineAccess().newProcessCommand(
                        polylgotLanguageContext,
                        cmd,
                        cwd == null ? null : cwd.getSPIPath().toString(),
                        environment(),
                        redirectErrorStream,
                        redirects);
        return TruffleLanguage.AccessAPI.engineAccess().startProcess(polylgotLanguageContext, processCommand);
    }
}
