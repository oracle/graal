/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.debug.GraalError;

/**
 * Encodes a WASM type definition without an explicit type index (basically a function signature).
 * <p>
 * Ref: https://webassembly.github.io/spec/core/text/modules.html#text-typeuse
 */
public class TypeUse {
    public final List<WasmValType> params;
    public final List<WasmValType> results;

    public TypeUse(List<WasmValType> params, List<WasmValType> results) {
        this.params = Collections.unmodifiableList(params);
        this.results = Collections.unmodifiableList(results);
    }

    public static TypeUse withOptionalResult(WasmValType result, WasmValType... args) {
        if (result == null) {
            return TypeUse.withoutResult(args);
        } else {
            return TypeUse.withResult(result, args);
        }
    }

    public static TypeUse withResult(WasmValType result, WasmValType... args) {
        return new TypeUse(Arrays.asList(args), Collections.singletonList(result));
    }

    public static TypeUse withoutResult(WasmValType... args) {
        return new TypeUse(Arrays.asList(args), Collections.emptyList());
    }

    public static TypeUse forUnary(WasmValType result, WasmValType input) {
        return withResult(result, input);
    }

    public static TypeUse forBinary(WasmValType result, WasmValType left, WasmValType right) {
        return withResult(result, left, right);
    }

    public boolean hasResults() {
        return !results.isEmpty();
    }

    /**
     * Returns the single return type of this signature or {@code null} if there is no result.
     */
    public WasmValType getSingleResult() {
        if (hasResults()) {
            GraalError.guarantee(results.size() == 1, "This type use should have exactly one result, has: %d", results.size());
            return results.getFirst();
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeUse typeUse = (TypeUse) o;
        return Objects.equals(params, typeUse.params) && Objects.equals(results, typeUse.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, results);
    }

    @Override
    public String toString() {
        return "TypeUse{params=" + params + ", results=" + results + "}";
    }
}
