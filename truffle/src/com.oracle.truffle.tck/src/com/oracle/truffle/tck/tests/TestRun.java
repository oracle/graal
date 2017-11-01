/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.tck.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.Snippet;
import org.graalvm.polyglot.tck.TypeDescriptor;

public final class TestRun {

    private final Entry<String, ? extends Snippet> snippet;
    private final List<Entry<String, ? extends Snippet>> arguments;

    TestRun(
                    final Entry<String, ? extends Snippet> snippet,
                    final List<Entry<String, ? extends Snippet>> arguments) {
        Objects.requireNonNull(snippet);
        Objects.requireNonNull(arguments);
        this.snippet = snippet;
        this.arguments = arguments;
    }

    Snippet getSnippet() {
        return snippet.getValue();
    }

    List<? extends TypeDescriptor> gatActualParameterTypes() {
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

    @Override
    public String toString() {
        return arguments.stream().map(new Function<Entry<String, ? extends Snippet>, String>() {
            @Override
            public String apply(Entry<String, ? extends Snippet> e) {
                return e.getKey() + "::" + e.getValue().getId();
            }
        }).collect(Collectors.joining(
                        ", ",
                        snippet.getKey() + "::" + snippet.getValue().getId() + "(",
                        ")"));
    }
}
