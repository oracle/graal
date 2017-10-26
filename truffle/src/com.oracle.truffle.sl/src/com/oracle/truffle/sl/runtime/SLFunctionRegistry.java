/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.SLRootNode;
import com.oracle.truffle.sl.parser.Parser;

/**
 * Manages the mapping from function names to {@link SLFunction function objects}.
 */
public final class SLFunctionRegistry {

    private final SLLanguage language;
    private final FunctionsObject functionsObject = new FunctionsObject();

    public SLFunctionRegistry(SLLanguage language) {
        this.language = language;
    }

    /**
     * Returns the canonical {@link SLFunction} object for the given name. If it does not exist yet,
     * it is created.
     */
    public SLFunction lookup(String name, boolean createIfNotPresent) {
        SLFunction result = functionsObject.functions.get(name);
        if (result == null && createIfNotPresent) {
            result = new SLFunction(language, name);
            functionsObject.functions.put(name, result);
        }
        return result;
    }

    /**
     * Associates the {@link SLFunction} with the given name with the given implementation root
     * node. If the function did not exist before, it defines the function. If the function existed
     * before, it redefines the function and the old implementation is discarded.
     */
    public SLFunction register(String name, SLRootNode rootNode) {
        SLFunction function = lookup(name, true);
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        function.setCallTarget(callTarget);
        return function;
    }

    public void register(Map<String, SLRootNode> newFunctions) {
        for (Map.Entry<String, SLRootNode> entry : newFunctions.entrySet()) {
            register(entry.getKey(), entry.getValue());
        }
    }

    public void register(Source newFunctions) {
        register(Parser.parseSL(language, newFunctions));
    }

    /**
     * Returns the sorted list of all functions, for printing purposes only.
     */
    public List<SLFunction> getFunctions() {
        List<SLFunction> result = new ArrayList<>(functionsObject.functions.values());
        Collections.sort(result, new Comparator<SLFunction>() {
            public int compare(SLFunction f1, SLFunction f2) {
                return f1.toString().compareTo(f2.toString());
            }
        });
        return result;
    }

    public TruffleObject getFunctionsObject() {
        return functionsObject;
    }

    static class FunctionsObject implements TruffleObject {

        private final Map<String, SLFunction> functions = new HashMap<>();

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

            @Resolve(message = "KEYS")
            abstract static class FunctionsObjectKeysNode extends Node {

                @TruffleBoundary
                public Object access(FunctionsObject fo) {
                    return new FunctionNamesObject(fo.functions.keySet());
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class FunctionsObjectKeyInfoNode extends Node {

                @TruffleBoundary
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

                @TruffleBoundary
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
                    return obj instanceof FunctionNamesObject;
                }

                @MessageResolution(receiverType = FunctionNamesObject.class)
                static final class FunctionNamesMessageResolution {

                    @Resolve(message = "HAS_SIZE")
                    abstract static class FunctionNamesHasSizeNode extends Node {

                        @SuppressWarnings("unused")
                        public Object access(FunctionNamesObject namesObject) {
                            return true;
                        }
                    }

                    @Resolve(message = "GET_SIZE")
                    abstract static class FunctionNamesGetSizeNode extends Node {

                        public Object access(FunctionNamesObject namesObject) {
                            return namesObject.names.size();
                        }
                    }

                    @Resolve(message = "READ")
                    abstract static class FunctionNamesReadNode extends Node {

                        @TruffleBoundary
                        public Object access(FunctionNamesObject namesObject, int index) {
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
}
