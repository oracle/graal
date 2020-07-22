/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.function.Function;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.TargetMappingNodeGen.SingleMappingNodeGen;

@GenerateUncached
abstract class TargetMappingNode extends Node {

    public static final Object NO_RESULT = new Object();

    abstract Object execute(Object value, Class<?> targetType, PolyglotLanguageContext languageContext, InteropLibrary interop, boolean checkOnly);

    @SuppressWarnings("unused")
    @Specialization(guards = "targetType != null")
    @ExplodeLoop
    protected Object doCached(Object operand, Class<?> targetType, PolyglotLanguageContext context, InteropLibrary interop, boolean checkOnly,
                    @Cached(value = "getMappings(context, targetType)", dimensions = 1) PolyglotTargetMapping[] mappings,
                    @Cached(value = "createMappingNodes(mappings)") SingleMappingNode[] mappingNodes) {
        Object result = NO_RESULT;
        if (mappingNodes != null) {
            for (int i = 0; i < mappingNodes.length; i++) {
                result = mappingNodes[i].execute(operand, mappings[i], context, interop, checkOnly);
                if (result != NO_RESULT) {
                    break;
                }
            }
        }
        return result;
    }

    @Specialization(replaces = "doCached")
    @SuppressWarnings("unused")
    @TruffleBoundary
    protected Object doUncached(Object operand, Class<?> targetType, PolyglotLanguageContext context, InteropLibrary interop, boolean checkOnly) {
        Object result = NO_RESULT;
        PolyglotTargetMapping[] mappings = getMappings(context, targetType);
        if (mappings != null) {
            SingleMappingNode uncachedNode = SingleMappingNodeGen.getUncached();
            for (int i = 0; i < mappings.length; i++) {
                result = uncachedNode.execute(operand, mappings[i], context, interop, checkOnly);
                if (result != NO_RESULT) {
                    break;
                }
            }
        }
        return result;
    }

    @TruffleBoundary
    static PolyglotTargetMapping[] getMappings(PolyglotLanguageContext context, Class<?> targetType) {
        if (context == null) {
            return HostClassCache.EMPTY_MAPPINGS;
        }
        return context.getEngine().getHostClassCache().getMappings(targetType);
    }

    @TruffleBoundary
    static SingleMappingNode[] createMappingNodes(PolyglotTargetMapping[] mappings) {
        if (mappings == null) {
            return null;
        }
        SingleMappingNode[] nodes = new SingleMappingNode[mappings.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = SingleMappingNodeGen.create();
        }
        return nodes;
    }

    static TargetMappingNode create() {
        return TargetMappingNodeGen.create();
    }

    static TargetMappingNode getUncached() {
        return TargetMappingNodeGen.getUncached();
    }

    @GenerateUncached
    @SuppressWarnings("unchecked")
    abstract static class SingleMappingNode extends Node {

        abstract Object execute(Object receiver, PolyglotTargetMapping targetMapping, PolyglotLanguageContext context, InteropLibrary interop, boolean checkOnly);

        @Specialization
        protected Object doDefault(Object receiver, @SuppressWarnings("unused") PolyglotTargetMapping cachedMapping,
                        PolyglotLanguageContext context, InteropLibrary interop, boolean checkOnly,
                        @Cached ConditionProfile acceptsProfile,
                        @Cached(value = "allowsImplementation(context, cachedMapping.sourceType)", allowUncached = true) boolean allowsImplementation,
                        @Cached ToHostNode toHostRecursive) {
            CompilerAsserts.partialEvaluationConstant(checkOnly);
            Object convertedValue = NO_RESULT;
            if (acceptsProfile.profile(ToHostNode.canConvert(receiver, cachedMapping.sourceType, cachedMapping.sourceType,
                            allowsImplementation, context, ToHostNode.MAX, interop, null))) {
                if (!checkOnly || cachedMapping.accepts != null) {
                    convertedValue = toHostRecursive.execute(receiver, cachedMapping.sourceType, cachedMapping.sourceType, context, false);
                }
            } else {
                return NO_RESULT;
            }
            if (cachedMapping.accepts != null && !checkPredicate(context, convertedValue, cachedMapping.accepts)) {
                return NO_RESULT;
            }
            if (checkOnly) {
                return Boolean.TRUE;
            } else {
                return convert(context, cachedMapping.converter, convertedValue);
            }
        }

        static boolean allowsImplementation(PolyglotLanguageContext context, Class<?> type) {
            return ToHostNode.allowsImplementation(context, type);
        }

        @TruffleBoundary
        private static Object convert(PolyglotLanguageContext languageContext, Function<Object, Object> converter, Object value) {
            try {
                return converter.apply(value);
            } catch (ClassCastException t) {
                // we allow class cast exceptions
                throw PolyglotEngineException.classCast(t.getMessage());
            } catch (Throwable t) {
                throw PolyglotImpl.hostToGuestException(languageContext, t);
            }
        }

        @TruffleBoundary
        private static boolean checkPredicate(PolyglotLanguageContext languageContext, Object convertedValue, Predicate<Object> predicate) {
            try {
                return predicate.test(convertedValue);
            } catch (Throwable t) {
                throw PolyglotImpl.hostToGuestException(languageContext, t);
            }
        }
    }

}
