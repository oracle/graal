/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

final class ExceptionAccessor extends Accessor {

    static final ExceptionAccessor ACCESSOR = new ExceptionAccessor();

    private ExceptionAccessor() {
    }

    static final class ExceptionSupportImpl extends ExceptionSupport {

        @Override
        public Throwable getLazyStackTrace(Throwable exception) {
            return ((AbstractTruffleException) exception).getLazyStackTrace();
        }

        @Override
        public void setLazyStackTrace(Throwable exception, Throwable stackTrace) {
            ((AbstractTruffleException) exception).setLazyStackTrace(stackTrace);
        }

        @Override
        public Object createDefaultStackTraceElementObject(RootNode rootNode, SourceSection sourceSection) {
            return new DefaultStackTraceElementObject(rootNode, sourceSection);
        }

        @Override
        public boolean isException(Object receiver) {
            return receiver instanceof AbstractTruffleException;
        }

        @Override
        public RuntimeException throwException(Object receiver) {
            throw (AbstractTruffleException) receiver;
        }

        @Override
        public Object getExceptionType(Object receiver) {
            return ExceptionType.RUNTIME_ERROR;
        }

        @Override
        public boolean isExceptionIncompleteSource(Object receiver) {
            return false;
        }

        @Override
        public int getExceptionExitStatus(Object receiver) {
            throw throwUnsupportedMessageException();
        }

        @Override
        public boolean hasExceptionCause(Object receiver) {
            return isException(((AbstractTruffleException) receiver).getCause());
        }

        @Override
        public Object getExceptionCause(Object receiver) {
            Throwable throwable = ((AbstractTruffleException) receiver).getCause();
            if (isException(throwable)) {
                return throwable;
            } else {
                throw throwUnsupportedMessageException();
            }
        }

        @Override
        @TruffleBoundary
        public boolean hasExceptionMessage(Object receiver) {
            return ((AbstractTruffleException) receiver).getMessage() != null;
        }

        @Override
        @TruffleBoundary
        public Object getExceptionMessage(Object receiver) {
            String message = ((AbstractTruffleException) receiver).getMessage();
            if (message == null) {
                throw throwUnsupportedMessageException();
            } else {
                return message;
            }
        }

        @Override
        public boolean hasExceptionStackTrace(Object receiver) {
            return true;
        }

        @Override
        @TruffleBoundary
        public Object getExceptionStackTrace(Object receiver, Object polyglotContext) {
            Throwable throwable = (Throwable) receiver;
            List<TruffleStackTraceElement> stack = TruffleStackTrace.getStackTrace(throwable);
            if (stack == null) {
                stack = Collections.emptyList();
            }
            boolean hasGuestToHostCalls = false;
            boolean inHost = true;
            for (TruffleStackTraceElement element : stack) {
                if (ACCESSOR.hostSupport().isGuestToHostRootNode(element.getTarget().getRootNode())) {
                    hasGuestToHostCalls = true;
                    break;
                } else {
                    inHost = false;
                }
            }
            Object[] items;
            if (hasGuestToHostCalls) {
                // If we have guest to host calls, we need to merge in (or filter) the host frames.
                Object polyglotEngine = polyglotContext == null
                                ? ACCESSOR.engineSupport().getCurrentPolyglotEngine()
                                : ACCESSOR.engineSupport().getEngineFromPolyglotObject(polyglotContext);
                items = mergeHostGuestFrames(throwable, stack, inHost, polyglotEngine);
            } else {
                // If there are no guest to host calls, there is no need for any extra processing.
                items = new Object[stack.size()];
                for (int i = 0; i < items.length; i++) {
                    items[i] = stack.get(i).getGuestObject();
                }
            }
            return new InteropList(items);
        }

        private static Object[] mergeHostGuestFrames(Throwable throwable, List<TruffleStackTraceElement> guestStack, boolean inHost, Object polyglotEngine) {
            StackTraceElement[] hostStack = null;
            AbstractHostLanguageService hostService = ACCESSOR.engineSupport().getHostService(polyglotEngine);
            if (hostService.isHostException(throwable)) {
                Throwable original = hostService.unboxHostException(throwable);
                hostStack = original.getStackTrace();
            } else if (throwable instanceof AbstractTruffleException) {
                Throwable lazyStackTrace = ((AbstractTruffleException) throwable).getLazyStackTrace();
                if (lazyStackTrace != null) {
                    hostStack = ACCESSOR.languageSupport().getInternalStackTraceElements(lazyStackTrace);
                }
            } else {
                // Internal error.
                hostStack = throwable.getStackTrace();
            }
            if (hostStack == null) {
                // protect against getStackTrace() returning null
                hostStack = new StackTraceElement[0];
            }

            ListIterator<TruffleStackTraceElement> guestStackIterator = guestStack.listIterator();
            boolean includeHostFrames = ACCESSOR.engineSupport().getHostService(polyglotEngine).isHostStackTraceVisibleToGuest();
            int lastGuestToHostIndex = includeHostFrames ? indexOfLastGuestToHostFrame(guestStack) : -1;
            Iterator<Object> mergedElements = ACCESSOR.engineSupport().mergeHostGuestFrames(polyglotEngine,
                            hostStack, guestStackIterator, inHost, includeHostFrames,
                            new Function<StackTraceElement, Object>() {
                                @Override
                                public Object apply(StackTraceElement element) {
                                    assert includeHostFrames;
                                    if (!guestStackIterator.hasNext()) {
                                        // omit trailing host frames
                                        return null;
                                    } else if (guestStackIterator.nextIndex() > lastGuestToHostIndex) {
                                        /*
                                         * omit host frames not followed by a guest-to-host frame;
                                         * just in case we have extra guest frames due to the guest
                                         * trace getting out of sync with the host trace.
                                         */
                                        return null;
                                    }
                                    return new HostStackTraceElementObject(element);
                                }
                            },
                            new Function<TruffleStackTraceElement, Object>() {
                                @Override
                                public Object apply(TruffleStackTraceElement element) {
                                    RootNode rootNode = element.getTarget().getRootNode();
                                    if (ACCESSOR.hostSupport().isGuestToHostRootNode(rootNode)) {
                                        // omit guest to host frame (e.g. HostObject.doInvoke)
                                        return null;
                                    } else if (ACCESSOR.engineSupport().isHostToGuestRootNode(rootNode)) {
                                        // omit host to guest frame (e.g. Value.execute)
                                        return null;
                                    }
                                    return element.getGuestObject();
                                }
                            });

            List<Object> elementsList = new ArrayList<>();
            while (mergedElements.hasNext()) {
                elementsList.add(mergedElements.next());
            }
            return elementsList.toArray();
        }

        private static int indexOfLastGuestToHostFrame(List<TruffleStackTraceElement> guestStack) {
            for (var iterator = guestStack.listIterator(guestStack.size()); iterator.hasPrevious();) {
                int index = iterator.previousIndex();
                TruffleStackTraceElement element = iterator.previous();
                if (ACCESSOR.hostSupport().isGuestToHostRootNode(element.getTarget().getRootNode())) {
                    return index;
                }
            }
            return -1;
        }

        @Override
        public boolean hasSourceLocation(Object receiver) {
            return ((AbstractTruffleException) receiver).getEncapsulatingSourceSection() != null;
        }

        @Override
        public SourceSection getSourceLocation(Object receiver) {
            SourceSection sourceSection = ((AbstractTruffleException) receiver).getEncapsulatingSourceSection();
            if (sourceSection == null) {
                throw throwUnsupportedMessageException();
            }
            return sourceSection;
        }

        @Override
        public int getStackTraceElementLimit(Object receiver) {
            return ((AbstractTruffleException) receiver).getStackTraceElementLimit();
        }

        @Override
        public Node getLocation(Object receiver) {
            return ((AbstractTruffleException) receiver).getLocation();
        }

        @Override
        public boolean assertGuestObject(Object guestObject) {
            if (guestObject == null) {
                throw new AssertionError("Guest object must not be null.");
            }
            InteropLibrary interop = InteropLibrary.getUncached();
            if (interop.hasExecutableName(guestObject)) {
                Object executableName;
                try {
                    executableName = interop.getExecutableName(guestObject);
                } catch (UnsupportedMessageException um) {
                    throw new AssertionError("Failed to get the executable name.", um);
                }
                if (!interop.isString(executableName)) {
                    throw new AssertionError("Executable name must be an interop string.");
                }
            }
            if (interop.hasDeclaringMetaObject(guestObject)) {
                Object metaObject;
                try {
                    metaObject = interop.getDeclaringMetaObject(guestObject);
                } catch (UnsupportedMessageException um) {
                    throw new AssertionError("Failed to get the declaring meta object.", um);
                }
                if (!interop.isMetaObject(metaObject)) {
                    throw new AssertionError("Declaring meta object must be an interop meta object");
                }
            }
            return true;
        }

        private static RuntimeException throwUnsupportedMessageException() {
            throw silenceException(RuntimeException.class, UnsupportedMessageException.create());
        }

        @SuppressWarnings({"unchecked", "unused"})
        private static <E extends Throwable> E silenceException(Class<E> type, Throwable ex) throws E {
            throw (E) ex;
        }
    }
}
