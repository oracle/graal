/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.WordAssertions;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.JavaType;

public final class WordAssertionsImpl implements WordAssertions {

    private final WordTypes wordTypes;

    public WordAssertionsImpl(WordTypes wordTypes) {
        this.wordTypes = wordTypes;
    }

    @Override
    public boolean assertIsWord(ValueNode node) {
        GraalError.guarantee(wordTypes.isWord(node), "Expected a Word but got %s", StampTool.typeOrNull(node));
        return true;
    }

    @Override
    public boolean assertIsWord(JavaType type) {
        GraalError.guarantee(wordTypes.isWord(type), "Expected a Word but got %s", type);
        return true;
    }

    @Override
    public boolean assertIsWord(Class<?> clazz) {
        GraalError.guarantee(wordTypes.isWord(clazz), "Expected a Word but got %s", clazz);
        return true;
    }

    @Override
    public boolean assertIsNoWord(ValueNode node) {
        GraalError.guarantee(!wordTypes.isWord(node), "Unexpected a Word type for node %s", node);
        return true;
    }

    @Override
    public boolean assertIsNoWord(JavaType type) {
        GraalError.guarantee(!wordTypes.isWord(type), "Unexpected a Word type %s", type);
        return true;
    }

    @Override
    public boolean assertIsNoWord(Class<?> clazz) {
        GraalError.guarantee(!wordTypes.isWord(clazz), "Unexpected a Word type %s", clazz);
        return true;
    }

}
