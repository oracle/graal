/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

public final class PolyglotCompilerOptionsScope implements Closeable {

    private static ThreadLocal<PolyglotCompilerOptionsScope> currentScope = new ThreadLocal<>();

    private final OptionValues optionValues;
    private PolyglotCompilerOptionsScope parent;

    private PolyglotCompilerOptionsScope(OptionValues optionValues, PolyglotCompilerOptionsScope parent) {
        Objects.requireNonNull(optionValues, "OptionValues must be non null.");
        this.optionValues = optionValues;
        this.parent = parent;
    }

    @Override
    public void close() {
        PolyglotCompilerOptionsScope current = currentScope.get();
        if (current != this) {
            throw new IllegalStateException("Unpaired close.");
        }
        currentScope.set(current.parent);
    }

    public static OptionValues getOptionValues() {
        PolyglotCompilerOptionsScope current = currentScope.get();
        if (current == null) {
            throw new IllegalStateException("Not entered in scope.");
        }
        return current.optionValues;
    }

    public static <T> T getValue(OptionKey<T> optionKey) {
        return PolyglotCompilerOptions.getValue(getOptionValues(), optionKey);
    }

    static PolyglotCompilerOptionsScope open(Map<String, Object> options) {
        PolyglotCompilerOptionsScope parent = currentScope.get();
        OptionValues values = convertToOptionValues(options);
        PolyglotCompilerOptionsScope newScope = new PolyglotCompilerOptionsScope(values, parent);
        currentScope.set(newScope);
        return newScope;
    }

    private static OptionValues convertToOptionValues(Map<String, Object> options) {
        EconomicMap<OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        OptionDescriptors descriptors = PolyglotCompilerOptions.getDescriptors();
        for (Map.Entry<String, Object> e : options.entrySet()) {
            final OptionDescriptor descriptor = descriptors.get(e.getKey());
            final OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
            if (k != null) {
                Object value = e.getValue();
                if (value.getClass() == String.class) {
                    value = descriptor.getKey().getType().convert((String) e.getValue());
                }
                parsedOptions.put(k, value);
            }
        }
        return new OptionValuesImpl(descriptors, parsedOptions);
    }
}
