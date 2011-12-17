/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.util;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.lang.*;

/**
 * A symbolizer is used to group a set of {@link Symbol symbols}. The members of the
 * group can be iterated and the symbol corresponding to a given value can be
 * retrieved from the group.
 *
 * This class is similar to the semantics of {@code enum}s in Java but adds
 * the ability to have a set of predefined symbols whose primitive values are not
 * necessarily contiguous and starting at 0.
 */
public interface Symbolizer<S extends Symbol> extends Iterable<S> {

    /**
     * @return the concrete type of the symbols in the group
     */
    Class<S> type();

    /**
     * Gets the symbol in the group whose primitive value equals {@code value}.
     *
     * @param value the search key
     * @return the found symbol or {@code null} if no symbol is found for {@code value}
     */
    S fromValue(int value);

    int numberOfValues();

    public static final class Static {

        private Static() {
        }

        public static boolean hasPackageExternalAccessibleConstructors(Class type) {
            final int publicOrProtected = Modifier.PUBLIC | Modifier.PROTECTED;
            for (Constructor constructor : type.getConstructors()) {
                if ((constructor.getModifiers() & publicOrProtected) != 0) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Gets a map from name to symbol for all the symbols represented by a given symbolizer.
         *
         * @param <S> the type of the symbol
         * @param symbolizer a set of symbols
         * @return a map from symbol name to symbol
         */
        public static <S extends Symbol> Map<String, S> toSymbolMap(Symbolizer<S> symbolizer) {
            final Map<String, S> map = new HashMap<String, S>(symbolizer.numberOfValues());
            for (S symbol : symbolizer) {
                map.put(symbol.name(), symbol);
            }
            return map;
        }

        public static <S extends Symbol> Symbolizer<S> from(Class<S> symbolType, S... symbols) {
            return new ListSymbolizer<S>(symbolType, Arrays.asList(symbols));
        }

        public static <S extends Symbol> Symbolizer<S> fromList(Class<S> symbolType, Iterable< ? extends S> symbols,
                        final S... additionalSymbols) {
            final List<S> list = new ArrayList<S>(Arrays.asList(additionalSymbols));
            for (S symbol : symbols) {
                list.add(symbol);
            }
            return new ListSymbolizer<S>(symbolType, list);
        }

        public static <S extends Symbol> Symbolizer<S> append(Symbolizer<S> symbolizer, S... symbols) {
            return fromList(symbolizer.type(), symbolizer, symbols);
        }

        public static <S extends Symbol> Symbolizer<S> append(Class<S> symbolType, Symbolizer< ? extends S> symbolizer,
                        final S... symbols) {
            return fromList(symbolType, symbolizer, symbols);
        }

        public static <S extends Symbol> Symbolizer<S> initialize(Class staticNameFieldClass, Class<S> symbolType) {
            final List<S> list = new ArrayList<S>();
            final List<StaticFieldName> staticFieldNames = StaticFieldName.Static.initialize(staticNameFieldClass);
            for (StaticFieldName staticFieldName : staticFieldNames) {
                if (symbolType.isInstance(staticFieldName)) {
                    list.add(symbolType.cast(staticFieldName));
                }
            }
            return new ListSymbolizer<S>(symbolType, list);
        }

        public static <S extends Symbol> Symbolizer<S> initialize(Class<S> symbolType) {
            return initialize(symbolType, symbolType);
        }

        @SuppressWarnings("unchecked")
        public static <S extends Symbol> Symbolizer<S> fromSymbolizer(Symbolizer<S> symbolizer, Predicate<S> predicate) {
            if (predicate == null) {
                return symbolizer;
            }
            final List<S> result = new LinkedList<S>();
            for (S element : symbolizer) {
                if (predicate.evaluate(element)) {
                    result.add(element);
                }
            }
            List<S> filtered = result;
            return fromList(symbolizer.type(), filtered);
        }

    }
}
