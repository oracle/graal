/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.types;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;

import java.util.Arrays;

public record FunctionType(@CompilerDirectives.CompilationFinal(dimensions = 1) ValueType[] paramTypes,
                @CompilerDirectives.CompilationFinal(dimensions = 1) ValueType[] resultTypes) implements CompositeType {

    @Override
    public CompositeKind compositeKind() {
        return CompositeKind.Function;
    }

    @Override
    public boolean isSubtypeOf(HeapType thatHeapType) {
        if (thatHeapType == AbstractHeapType.FUNC) {
            return true;
        }
        if (!(thatHeapType instanceof DefinedType thatDefinedType && thatDefinedType.expand() instanceof FunctionType that)) {
            return false;
        }
        if (this.paramTypes.length != that.paramTypes.length) {
            return false;
        }
        for (int i = 0; i < this.paramTypes.length; i++) {
            CompilerAsserts.partialEvaluationConstant(this.paramTypes[i]);
            if (!that.paramTypes[i].isSubtypeOf(this.paramTypes[i])) {
                return false;
            }
        }
        if (this.resultTypes.length != that.resultTypes.length) {
            return false;
        }
        for (int i = 0; i < this.resultTypes.length; i++) {
            CompilerAsserts.partialEvaluationConstant(this.resultTypes[i]);
            if (!this.resultTypes[i].isSubtypeOf(that.resultTypes[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void unroll(RecursiveTypes recursiveTypes) {
        for (ValueType paramType : paramTypes) {
            paramType.unroll(recursiveTypes);
        }
        for (ValueType resultType : resultTypes) {
            resultType.unroll(recursiveTypes);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof FunctionType that && Arrays.equals(this.paramTypes, that.paramTypes) && Arrays.equals(this.resultTypes, that.resultTypes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(paramTypes) ^ Arrays.hashCode(resultTypes);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder sb = new StringBuilder();
        sb.append("(func");
        if (paramTypes.length != 0) {
            sb.append(" (param");
            for (int i = 0; i < paramTypes.length; i++) {
                sb.append(' ');
                sb.append(paramTypes[i]);
            }
            sb.append(")");
        }
        if (resultTypes.length != 0) {
            sb.append(" (result");
            for (int i = 0; i < resultTypes.length; i++) {
                sb.append(' ');
                sb.append(resultTypes[i]);
            }
            sb.append(")");
        }
        sb.append(")");
        return sb.toString();
    }
}
