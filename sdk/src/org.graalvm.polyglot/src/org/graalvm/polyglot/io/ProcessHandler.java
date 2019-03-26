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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

public interface ProcessHandler {

    Process start(ProcessCommand command) throws IOException;

    final class ProcessCommand {
        private List<String> cmd;
        private String cwd;
        private Map<String, String> environment;
        private boolean redirectErrorStream;
        private Redirect[] redirects;

        private ProcessCommand(List<String> command, String cwd, Map<String, String> environment, boolean redirectErrorStream, Redirect[] redirects) {
            Objects.requireNonNull(command, "Command must be non null.");
            Objects.requireNonNull(environment, "Environment must be non null.");
            Objects.requireNonNull(redirects, "Redirects must be non null.");
            this.cmd = Collections.unmodifiableList(new ArrayList<>(command));
            this.cwd = cwd;
            this.environment = Collections.unmodifiableMap(new HashMap<>(environment));
            this.redirectErrorStream = redirectErrorStream;
            this.redirects = Arrays.copyOf(redirects, redirects.length);
        }

        public List<String> getCommand() {
            return cmd;
        }

        public String getDirectory() {
            return cwd;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public boolean isRedirectErrorStream() {
            return redirectErrorStream;
        }

        public Redirect getInputRedirect() {
            return redirects[0];
        }

        public Redirect getOutputRedirect() {
            return redirects[1];
        }

        public Redirect getErrorRedirect() {
            return redirects[2];
        }

        static {
            try {
                Method method = Engine.class.getDeclaredMethod("getImpl");
                method.setAccessible(true);
                AbstractPolyglotImpl impl = (AbstractPolyglotImpl) method.invoke(null);
                impl.setIO(new IOAccessImpl());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize execution listener class.", e);
            }
        }

        private static final class IOAccessImpl extends AbstractPolyglotImpl.IOAccess {
            @Override
            public ProcessCommand newProcessCommand(List<String> cmd, String cwd, Map<String, String> environment, boolean redirectErrorStream, Redirect[] redirects) {
                return new ProcessCommand(cmd, cwd, environment, redirectErrorStream, redirects);
            }
        }
    }

    final class Redirect {

        public static final Redirect PIPE = new Redirect(Type.PIPE);
        public static final Redirect INHERIT = new Redirect(Type.INHERIT);

        private final Type type;

        private Redirect(Type type) {
            Objects.requireNonNull(type, "Type must be non null.");
            this.type = type;
        }

        public Type type() {
            return type;
        }

        @Override
        public String toString() {
            return type.toString();
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != Redirect.class) {
                return false;
            }
            return type.equals(((Redirect) obj).type);
        }

        public enum Type {
            PIPE,
            INHERIT
        }
    }
}
