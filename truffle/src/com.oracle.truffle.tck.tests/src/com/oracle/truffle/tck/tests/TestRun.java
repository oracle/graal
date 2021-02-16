/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.tck.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public class TestRun {

    private final Entry<String, ? extends Snippet> snippet;
    private final List<Entry<String, ? extends Snippet>> arguments;
    private volatile String cachedToString;

    TestRun(
                    final Entry<String, ? extends Snippet> snippet,
                    final List<Entry<String, ? extends Snippet>> arguments) {
        Objects.requireNonNull(snippet);
        Objects.requireNonNull(arguments);
        this.snippet = snippet;
        this.arguments = arguments;
    }

    String getID() {
        return snippet.getKey();
    }

    Snippet getSnippet() {
        return snippet.getValue();
    }

    List<? extends TypeDescriptor> getActualParameterTypes() {
        final List<TypeDescriptor> res = new ArrayList<>();
        for (Entry<String, ? extends Snippet> argument : arguments) {
            res.add(argument.getValue().getReturnType());
        }
        return res;
    }

    List<? extends Value> getActualParameters() {
        final List<Value> res = new ArrayList<>(arguments.size());
        for (Entry<String, ? extends Snippet> arg : arguments) {
            final Value value = arg.getValue().getExecutableValue();
            res.add(value.execute());
        }
        return res;
    }

    List<? extends Map.Entry<String, ? extends Snippet>> getActualParameterSnippets() {
        return arguments;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        final TestRun otherRun = (TestRun) obj;
        if (!snippet.getKey().equals(otherRun.snippet.getKey()) ||
                        !snippet.getValue().getId().equals(otherRun.snippet.getValue().getId()) ||
                        snippet.getValue().getExecutableValue() != otherRun.snippet.getValue().getExecutableValue() ||
                        arguments.size() != otherRun.arguments.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            final Entry<String, ? extends Snippet> thisArg = arguments.get(i);
            final Entry<String, ? extends Snippet> otherArg = otherRun.arguments.get(i);
            if (!thisArg.getKey().equals(otherArg.getKey()) ||
                            !thisArg.getValue().getId().equals(otherArg.getValue().getId()) ||
                            !thisArg.getValue().getReturnType().equals(otherArg.getValue().getReturnType())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int res = 17;
        res = res * 31 + snippet.getKey().hashCode();
        res = res * 31 + snippet.getValue().getId().hashCode();
        for (Entry<String, ? extends Snippet> argument : arguments) {
            res = res * 31 + argument.getKey().hashCode();
            res = res * 31 + argument.getValue().getId().hashCode();
        }
        return res;
    }

    @Override
    public String toString() {
        String res = cachedToString;
        if (res == null) {
            res = arguments.stream().map(new Function<Entry<String, ? extends Snippet>, String>() {
                @Override
                public String apply(Entry<String, ? extends Snippet> e) {
                    return e.getKey() + "::" + e.getValue().getId();
                }
            }).collect(Collectors.joining(
                            ", ",
                            snippet.getKey() + "::" + snippet.getValue().getId() + "(",
                            ")"));
            cachedToString = res;
        }
        return res;
    }
}
