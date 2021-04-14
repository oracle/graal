/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.runtime.EspressoContext;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class serves as a {@link ParserKlass} and {@link LinkedKlass} cache.
 *
 * In a multi-context environment, {@link ParserKlass} caching improves performance
 * and is required for context pre-initialization. {@link LinkedKlass} caching removes
 * redundant recomputation of {@link LinkedKlass} fields, such as {@link LinkedKlassFieldLayout}.
 *
 * {@link ParserKlass}es are identified by their name in order to improve performance when
 * boot classloader is used. The cache needs to be concurrent.
 *
 * {@link LinkedKlass}es are identified not only by their {@link ParserKlass}, but also
 * by superclass and implemented interfaces. Therefore, when caching {@link LinkedKlass}es,
 * a composite key needs to be used (see {@link EspressoLanguageCache.LinkedKey}).
 */
public final class EspressoLanguageCache {

    private final Map<String, ParserKlass> parserKlasses = new ConcurrentHashMap<>();
    private final Map<LinkedKey, LinkedKlass> linkedKlasses = new ConcurrentHashMap<>();

    public ParserKlass getOrCreateParserKlass(String name, byte[] bytes, EspressoContext context) {
        ParserKlass parserKlass = parserKlasses.get(name);
        if (parserKlass == null) {
            parserKlass = createParserKlass(name, bytes, context);
            parserKlasses.put(name, parserKlass);
        }
        return parserKlass;
    }

    public LinkedKlass getOrCreateLinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        LinkedKey key = new LinkedKey(parserKlass, superKlass, interfaces);
        LinkedKlass linkedKlass = linkedKlasses.get(key);
        if (linkedKlass == null) {
            linkedKlass = createLinkedKlass(parserKlass, superKlass, interfaces);
            linkedKlasses.put(key, linkedKlass);
        }
        return linkedKlass;
    }

    private ParserKlass createParserKlass(String name, byte[] bytes, EspressoContext context) {
        return ClassfileParser.parse(new ClassfileStream(bytes, null), name, null, context);
    }

    private LinkedKlass createLinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        return new LinkedKlass(parserKlass, superKlass, interfaces);
    }

    private static final class LinkedKey {
        private final ParserKlass parserKlass;
        private final LinkedKlass superKlass;
        private final LinkedKlass[] interfaces;

        public LinkedKey(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
            this.parserKlass = parserKlass;
            this.superKlass = superKlass;
            this.interfaces = interfaces;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LinkedKey linkedKey = (LinkedKey) o;
            return parserKlass.equals(linkedKey.parserKlass) && Objects.equals(superKlass, linkedKey.superKlass) && Arrays.equals(interfaces, linkedKey.interfaces);
        }

        @Override
        public int hashCode() {
            int hashCode = Objects.hash(parserKlass, superKlass);
            hashCode = 31 * hashCode + Arrays.hashCode(interfaces);
            return hashCode;
        }
    }
}
