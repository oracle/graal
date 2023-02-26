/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

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
import com.oracle.truffle.host.HostTargetMappingNodeGen.SingleMappingNodeGen;

@GenerateUncached
abstract class HostTargetMappingNode extends Node {

    public static final Object NO_RESULT = new Object();

    abstract Object execute(Object value, Class<?> targetType, HostContext hostContext, InteropLibrary interop, boolean checkOnly, int startPriority, int endPriority);

    @SuppressWarnings("unused")
    @Specialization(guards = "targetType != null")
    @ExplodeLoop
    protected Object doCached(Object operand, Class<?> targetType, HostContext context, InteropLibrary interop, boolean checkOnly, int startPriority, int endPriority,
                    @Cached(value = "getMappings(context, targetType)", dimensions = 1) HostTargetMapping[] mappings,
                    @Cached(value = "createMappingNodes(mappings)") SingleMappingNode[] mappingNodes) {
        assert startPriority <= endPriority;
        Object result = NO_RESULT;
        if (mappingNodes != null) {
            for (int i = 0; i < mappingNodes.length; i++) {
                HostTargetMapping mapping = mappings[i];
                if (mapping.hostPriority < startPriority) {
                    continue; // skip
                } else if (mapping.hostPriority > endPriority) {
                    break; // break because mappings are ordered by hostPriority
                }
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
    protected Object doUncached(Object operand, Class<?> targetType, HostContext hostContext, InteropLibrary interop, boolean checkOnly, int startPriority, int endPriority) {
        assert startPriority <= endPriority;
        Object result = NO_RESULT;
        HostTargetMapping[] mappings = getMappings(hostContext, targetType);
        if (mappings != null) {
            SingleMappingNode uncachedNode = SingleMappingNodeGen.getUncached();
            for (int i = 0; i < mappings.length; i++) {
                HostTargetMapping mapping = mappings[i];
                if (mapping.hostPriority < startPriority) {
                    continue; // skip
                } else if (mapping.hostPriority > endPriority) {
                    break; // break because mappings are ordered by hostPriority
                }
                result = uncachedNode.execute(operand, mappings[i], hostContext, interop, checkOnly);
                if (result != NO_RESULT) {
                    break;
                }
            }
        }
        return result;
    }

    @TruffleBoundary
    static HostTargetMapping[] getMappings(HostContext hostContext, Class<?> targetType) {
        if (hostContext == null) {
            return HostClassCache.EMPTY_MAPPINGS;
        }
        return hostContext.getHostClassCache().getMappings(targetType);
    }

    @TruffleBoundary
    static SingleMappingNode[] createMappingNodes(HostTargetMapping[] mappings) {
        if (mappings == null) {
            return null;
        }
        SingleMappingNode[] nodes = new SingleMappingNode[mappings.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = SingleMappingNodeGen.create();
        }
        return nodes;
    }

    static HostTargetMappingNode create() {
        return HostTargetMappingNodeGen.create();
    }

    static HostTargetMappingNode getUncached() {
        return HostTargetMappingNodeGen.getUncached();
    }

    @GenerateUncached
    @SuppressWarnings("unchecked")
    abstract static class SingleMappingNode extends Node {

        abstract Object execute(Object receiver, HostTargetMapping targetMapping, HostContext context, InteropLibrary interop, boolean checkOnly);

        @Specialization
        protected Object doDefault(Object receiver, @SuppressWarnings("unused") HostTargetMapping cachedMapping,
                        HostContext context, InteropLibrary interop, boolean checkOnly,
                        @Cached ConditionProfile acceptsProfile,
                        @Cached(value = "allowsImplementation(context, cachedMapping.sourceType)", allowUncached = true) boolean allowsImplementation,
                        @Cached HostToTypeNode toHostRecursive) {
            CompilerAsserts.partialEvaluationConstant(checkOnly);
            Object convertedValue = NO_RESULT;
            if (acceptsProfile.profile(HostToTypeNode.canConvert(receiver, cachedMapping.sourceType, cachedMapping.sourceType,
                            allowsImplementation, context, HostToTypeNode.LOWEST, interop, null))) {
                if (!checkOnly || cachedMapping.accepts != null) {
                    convertedValue = toHostRecursive.execute(context, receiver, cachedMapping.sourceType, cachedMapping.sourceType, false);
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

        static boolean allowsImplementation(HostContext context, Class<?> type) {
            return HostToTypeNode.allowsImplementation(context, type);
        }

        @TruffleBoundary
        private static Object convert(HostContext context, Function<Object, Object> converter, Object value) {
            try {
                return converter.apply(value);
            } catch (ClassCastException t) {
                // we allow class cast exceptions
                throw HostEngineException.classCast(context.access, t.getMessage());
            } catch (Throwable t) {
                throw context.hostToGuestException(t);
            }
        }

        @TruffleBoundary
        private static boolean checkPredicate(HostContext context, Object convertedValue, Predicate<Object> predicate) {
            try {
                return predicate.test(convertedValue);
            } catch (Throwable t) {
                throw context.hostToGuestException(t);
            }
        }
    }

}
