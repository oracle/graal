/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

final class MergedHostGuestIterator<T, G> implements Iterator<T> {

    private static final boolean TRACE_STACK_TRACE_WALKING = false;

    private static final String POLYGLOT_PACKAGE = "org.graalvm.polyglot.";
    private static final String HOST_INTEROP_PACKAGE = "com.oracle.truffle.polyglot.";
    private static final String[] JAVA_INTEROP_HOST_TO_GUEST = {
                    HOST_INTEROP_PACKAGE + "PolyglotMap",
                    HOST_INTEROP_PACKAGE + "PolyglotList",
                    HOST_INTEROP_PACKAGE + "PolyglotFunction",
                    HOST_INTEROP_PACKAGE + "PolyglotMapAndFunction",
                    HOST_INTEROP_PACKAGE + "PolyglotFunctionProxyHandler",
                    HOST_INTEROP_PACKAGE + "PolyglotObjectProxyHandler"
    };

    private final Object polyglotEngineImpl;
    private final Iterator<G> guestFrames;
    private final StackTraceElement[] hostStack;
    private final ListIterator<StackTraceElement> hostFrames;
    private final Function<StackTraceElement, T> hostFrameConvertor;
    private final Function<G, T> guestFrameConvertor;
    private final boolean includeHostFrames;
    private boolean inHostLanguage;
    private T fetchedNext;
    private boolean firstFrame;

    @TruffleBoundary
    static Object getExceptionStackTrace(Object receiver, Object vmObject, boolean forceInHost, boolean includeHostStack) {
        Throwable throwable = (Throwable) receiver;
        List<TruffleStackTraceElement> stack = TruffleStackTrace.getStackTrace(throwable);
        if (stack == null) {
            stack = Collections.emptyList();
        }
        Object polyglotEngine = vmObject == null
                        ? ExceptionAccessor.ENGINE.getCurrentPolyglotEngine()
                        : ExceptionAccessor.ENGINE.getEngineFromPolyglotObject(vmObject);
        boolean hasGuestToHostCalls = false;
        boolean inHost = true;
        if (!forceInHost) {
            for (TruffleStackTraceElement element : stack) {
                if (ExceptionAccessor.ENGINE.isGuestToHostRootNode(polyglotEngine, element.getTarget().getRootNode())) {
                    hasGuestToHostCalls = true;
                    break;
                } else {
                    inHost = false;
                }
            }
            /*
             * A host exception always originates in the host. The exception may be created in the
             * host, passed into a guest language as a HostException, and then thrown within the
             * guest language. In this case, the above detection does not work as there is no
             * GuestToHostRootNode on top of the stack.
             */
            inHost |= ExceptionAccessor.ENGINE.isHostException(throwable);
        }

        Object[] items;
        if (includeHostStack || hasGuestToHostCalls) {
            // If we have guest to host calls, we need to merge in (or filter) the host frames.
            items = mergeHostGuestFrames(throwable, stack, inHost, polyglotEngine, includeHostStack);
        } else {
            // If there are no guest to host calls, there is no need for any extra processing.
            items = new Object[stack.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = stack.get(i).getGuestObject();
            }
        }
        return new InteropList(items);
    }

    MergedHostGuestIterator(Object polyglotEngineImpl, StackTraceElement[] hostStack, Iterator<G> guestFrames, boolean inHostLanguage,
                    boolean includeHostFrames, Function<StackTraceElement, T> hostFrameConvertor, Function<G, T> guestFrameConvertor) {
        this.polyglotEngineImpl = polyglotEngineImpl;
        this.hostStack = hostStack;
        this.includeHostFrames = includeHostFrames;
        this.hostFrames = Arrays.asList(hostStack).listIterator();
        this.guestFrames = guestFrames;
        this.inHostLanguage = inHostLanguage;
        this.hostFrameConvertor = hostFrameConvertor;
        this.guestFrameConvertor = guestFrameConvertor;
        this.firstFrame = true;
    }

    @Override
    public boolean hasNext() {
        return fetchNext() != null;
    }

    @Override
    public T next() {
        if (firstFrame) {
            firstFrame = false;
            if (TRACE_STACK_TRACE_WALKING) {
                // To mark the beginning of the stack trace and separate from the previous one
                PrintStream out = System.out;
                out.println();
            }
        }
        T next = fetchNext();
        if (next == null) {
            throw new NoSuchElementException();
        }
        fetchedNext = null;
        return next;
    }

    private T fetchNext() {
        if (fetchedNext != null) {
            return fetchedNext;
        }

        while (hostFrames.hasNext()) {
            StackTraceElement element = hostFrames.next();
            traceStackTraceElement(element);
            // we need to flip inHostLanguage state in opposite order as the stack is top to
            // bottom.
            if (inHostLanguage) {
                int guestToHost = findGuestToHostFrame(polyglotEngineImpl, element, hostStack, hostFrames.nextIndex());
                if (guestToHost >= 0) {
                    assert !isHostToGuest(element);
                    inHostLanguage = false;

                    for (int i = 0; i < guestToHost; i++) {
                        element = hostFrames.next();
                        traceStackTraceElement(element);
                    }
                }
            } else {
                if (isHostToGuest(element)) {
                    inHostLanguage = true;

                    // skip extra host-to-guest frames
                    while (hostFrames.hasNext()) {
                        StackTraceElement next = hostFrames.next();
                        traceStackTraceElement(next);
                        if (isHostToGuest(next)) {
                            element = next;
                        } else {
                            hostFrames.previous();
                            break;
                        }
                    }
                }
            }

            if (isGuestCall(element)) {
                inHostLanguage = false;
                // construct guest frame
                if (guestFrames.hasNext()) {
                    G guestFrame = guestFrames.next();
                    T frame = guestFrameConvertor.apply(guestFrame);
                    if (frame != null) {
                        fetchedNext = frame;
                        return fetchedNext;
                    }
                }
            } else if (inHostLanguage) {
                if (includeHostFrames) {
                    // construct host frame
                    T frame = hostFrameConvertor.apply(element);
                    if (frame != null) {
                        fetchedNext = frame;
                        return fetchedNext;
                    }
                }
            } else {
                // skip stack frame that is part of guest language stack
            }
        }

        // consume guest frames
        while (guestFrames.hasNext()) {
            G guestFrame = guestFrames.next();
            T frame = guestFrameConvertor.apply(guestFrame);
            if (frame != null) {
                fetchedNext = frame;
                return fetchedNext;
            }
        }

        return null;
    }

    private static boolean isLazyStackTraceElement(StackTraceElement element) {
        return element == null;
    }

    private static boolean isGuestCall(StackTraceElement element) {
        return isLazyStackTraceElement(element) || ExceptionAccessor.RUNTIME.isGuestCallStackFrame(element);
    }

    private static boolean isHostToGuest(StackTraceElement element) {
        if (isLazyStackTraceElement(element)) {
            return false;
        }
        if (element.getClassName().startsWith(POLYGLOT_PACKAGE) && element.getClassName().indexOf('.', POLYGLOT_PACKAGE.length()) < 0) {
            return !element.getClassName().equals("org.graalvm.polyglot.Engine$APIAccessImpl");
        } else if (element.getClassName().startsWith(HOST_INTEROP_PACKAGE)) {
            for (String hostToGuestClassName : JAVA_INTEROP_HOST_TO_GUEST) {
                if (element.getClassName().equals(hostToGuestClassName)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Return the number of frames with reflective calls to skip
    private static int findGuestToHostFrame(Object polyglotEngineImpl, StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
        if (isLazyStackTraceElement(firstElement) || polyglotEngineImpl == null) {
            return -1;
        }
        return ExceptionAccessor.ENGINE.findGuestToHostFrame(polyglotEngineImpl, firstElement, hostStack, nextElementIndex);
    }

    private void traceStackTraceElement(StackTraceElement element) {
        if (TRACE_STACK_TRACE_WALKING) {
            PrintStream out = System.out;
            out.printf("host: %5s, guestToHost: %2s, hostToGuest: %5s, guestCall: %5s, -- %s %n", inHostLanguage,
                            findGuestToHostFrame(polyglotEngineImpl, element, hostStack, hostFrames.nextIndex()), isHostToGuest(element),
                            isGuestCall(element), element);
        }
    }

    static Object[] mergeHostGuestFrames(Throwable throwable, List<TruffleStackTraceElement> guestStack, boolean inHost, Object polyglotEngine, boolean includeHostStack) {
        StackTraceElement[] hostStack = null;
        InteropLibrary exceptions = InteropLibrary.getUncached(throwable);
        if (exceptions.isHostObject(throwable) && exceptions.isException(throwable)) {
            Throwable original = null;
            try {
                original = (Throwable) exceptions.asHostObject(throwable);
            } catch (HeapIsolationException e) {
                // continue with original == null
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            hostStack = original != null ? original.getStackTrace() : throwable.getStackTrace();
        } else if (throwable instanceof AbstractTruffleException) {
            Throwable lazyStackTrace = ((AbstractTruffleException) throwable).getLazyStackTrace();
            if (lazyStackTrace != null) {
                hostStack = ExceptionAccessor.LANGUAGE.getInternalStackTraceElements(lazyStackTrace);
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
        boolean includeHostFrames = includeHostStack || (polyglotEngine != null && ExceptionAccessor.ENGINE.getHostService(polyglotEngine).isHostStackTraceVisibleToGuest());
        int lastGuestToHostIndex = includeHostFrames && polyglotEngine != null ? indexOfLastGuestToHostFrame(polyglotEngine, guestStack) : -1;

        Iterator<Object> mergedElements = new MergedHostGuestIterator<>(polyglotEngine, hostStack, guestStackIterator, inHost, includeHostFrames,
                        new Function<StackTraceElement, Object>() {
                            @Override
                            public Object apply(StackTraceElement element) {
                                assert includeHostFrames;
                                if (!includeHostStack) {
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
                                }
                                return new HostStackTraceElementObject(element);
                            }
                        },
                        new Function<TruffleStackTraceElement, Object>() {
                            @Override
                            public Object apply(TruffleStackTraceElement element) {
                                RootNode rootNode = element.getTarget().getRootNode();
                                if (ExceptionAccessor.ENGINE.isGuestToHostRootNode(polyglotEngine, rootNode)) {
                                    // omit guest to host frame (e.g. HostObject.doInvoke)
                                    return null;
                                } else if (ExceptionAccessor.ENGINE.isHostToGuestRootNode(rootNode)) {
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

    private static int indexOfLastGuestToHostFrame(Object polyglotEngine, List<TruffleStackTraceElement> guestStack) {
        for (var iterator = guestStack.listIterator(guestStack.size()); iterator.hasPrevious();) {
            int index = iterator.previousIndex();
            TruffleStackTraceElement element = iterator.previous();
            if (ExceptionAccessor.ENGINE.isGuestToHostRootNode(polyglotEngine, element.getTarget().getRootNode())) {
                return index;
            }
        }
        return -1;
    }
}
