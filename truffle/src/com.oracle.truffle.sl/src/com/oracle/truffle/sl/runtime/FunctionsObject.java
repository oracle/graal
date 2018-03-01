/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class FunctionsObject implements TruffleObject {

    final Map<String, SLFunction> functions = new HashMap<>();

    FunctionsObject() {
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return FunctionsObjectMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof FunctionsObject;
    }

    @MessageResolution(receiverType = FunctionsObject.class)
    static final class FunctionsObjectMessageResolution {

        @Resolve(message = "HAS_KEYS")
        abstract static class FunctionsObjectHasKeysNode extends Node {

            @SuppressWarnings("unused")
            public Object access(FunctionsObject fo) {
                return true;
            }
        }

        @Resolve(message = "KEYS")
        abstract static class FunctionsObjectKeysNode extends Node {

            @CompilerDirectives.TruffleBoundary
            public Object access(FunctionsObject fo) {
                return new FunctionsObjectMessageResolution.FunctionNamesObject(fo.functions.keySet());
            }
        }

        @Resolve(message = "KEY_INFO")
        abstract static class FunctionsObjectKeyInfoNode extends Node {

            @CompilerDirectives.TruffleBoundary
            public Object access(FunctionsObject fo, String name) {
                if (fo.functions.containsKey(name)) {
                    return 3;
                } else {
                    return 0;
                }
            }
        }

        @Resolve(message = "READ")
        abstract static class FunctionsObjectReadNode extends Node {

            @CompilerDirectives.TruffleBoundary
            public Object access(FunctionsObject fo, String name) {
                try {
                    return fo.functions.get(name);
                } catch (IndexOutOfBoundsException ioob) {
                    return null;
                }
            }
        }

        static final class FunctionNamesObject implements TruffleObject {

            private final Set<String> names;

            private FunctionNamesObject(Set<String> names) {
                this.names = names;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return FunctionNamesMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof FunctionsObjectMessageResolution.FunctionNamesObject;
            }

            @MessageResolution(receiverType = FunctionsObjectMessageResolution.FunctionNamesObject.class)
            static final class FunctionNamesMessageResolution {

                @Resolve(message = "HAS_SIZE")
                abstract static class FunctionNamesHasSizeNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(FunctionsObjectMessageResolution.FunctionNamesObject namesObject) {
                        return true;
                    }
                }

                @Resolve(message = "GET_SIZE")
                abstract static class FunctionNamesGetSizeNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(FunctionsObjectMessageResolution.FunctionNamesObject namesObject) {
                        return namesObject.names.size();
                    }
                }

                @Resolve(message = "READ")
                abstract static class FunctionNamesReadNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(FunctionsObjectMessageResolution.FunctionNamesObject namesObject, int index) {
                        if (index >= namesObject.names.size()) {
                            throw UnknownIdentifierException.raise(Integer.toString(index));
                        }
                        Iterator<String> iterator = namesObject.names.iterator();
                        int i = index;
                        while (i-- > 0) {
                            iterator.next();
                        }
                        return iterator.next();
                    }
                }

            }
        }
    }
}
