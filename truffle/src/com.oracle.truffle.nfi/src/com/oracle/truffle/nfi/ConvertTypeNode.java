/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.ConvertTypeNodeFactory.ConvertFromNativeNodeGen;
import com.oracle.truffle.nfi.ConvertTypeNodeFactory.ConvertToNativeNodeGen;
import com.oracle.truffle.nfi.NFIType.TypeCachedState;

@GenerateAOT
abstract class ConvertTypeNode extends Node {

    abstract Object execute(NFIType type, Object value);

    static OptimizedConvertTypeNode createOptimizedToNative(TypeCachedState state) {
        ConvertTypeImplNode impl = ConvertToNativeNodeGen.create();
        return new OptimizedConvertTypeNode(state, impl);
    }

    static OptimizedConvertTypeNode createOptimizedFromNative(TypeCachedState state) {
        ConvertTypeImplNode impl = ConvertFromNativeNodeGen.create();
        return new OptimizedConvertTypeNode(state, impl);
    }

    static class OptimizedConvertTypeNode extends ConvertTypeNode {

        final TypeCachedState typeState;
        @Child ConvertTypeImplNode convert;

        OptimizedConvertTypeNode(TypeCachedState typeState, ConvertTypeImplNode convert) {
            this.typeState = typeState;
            this.convert = convert;
        }

        @Override
        Object execute(NFIType type, Object value) {
            assert typeState == type.cachedState;
            return convert.execute(typeState, type, value);
        }
    }

    abstract static class ConvertTypeImplNode extends ConvertTypeNode {

        abstract Object execute(TypeCachedState typeState, NFIType type, Object value);

        @Override
        final Object execute(NFIType type, Object value) {
            return execute(type.cachedState, type, value);
        }
    }

    @GenerateUncached
    abstract static class ConvertToNativeNode extends ConvertTypeImplNode {

        @Specialization
        Object doConvert(TypeCachedState typeState, NFIType type, Object value,
                        @CachedLibrary(limit = "3") NFITypeLibrary library) {
            return library.convertToNative(typeState, type, value);
        }
    }

    @GenerateUncached
    abstract static class ConvertFromNativeNode extends ConvertTypeImplNode {

        @Specialization
        Object doConvert(TypeCachedState typeState, NFIType type, Object value,
                        @CachedLibrary(limit = "3") NFITypeLibrary library) {
            return library.convertFromNative(typeState, type, value);
        }
    }
}
