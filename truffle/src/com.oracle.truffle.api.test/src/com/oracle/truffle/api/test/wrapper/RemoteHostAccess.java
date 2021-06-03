/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.EngineHostAccess;

public class RemoteHostAccess extends EngineHostAccess {

    protected RemoteHostAccess(AbstractPolyglotImpl impl) {
        super(impl);
    }

    @Override
    public Object createHostContext(HostAccess access, ClassLoader contextClassLoader, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        return null;
    }

    @Override
    public Object asHostValue(Object context, Object value) {
        return null;
    }

    @Override
    public Object asHostDynamicClass(Object context, Class<?> value) {
        return null;
    }

    @Override
    public Object asHostStaticClass(Object context, Class<?> value) {
        return null;
    }

    @Override
    public boolean isHostValue(Object value) {
        return false;
    }

    @Override
    public Object asHostValue(Object value) {
        return null;
    }

    @Override
    public boolean isHostException(Object value) {
        return false;
    }

    @Override
    public Throwable asHostException(Object value) {
        return null;
    }

    @Override
    public Object findDynamicClass(Object context, String classValue) {
        return null;
    }

    @Override
    public Object findStaticClass(Object context, String classValue) {
        return null;
    }

    @Override
    public void disposeContext(Object receiver) {

    }

    @Override
    public Object getLanguageView(Object receiver, Object value) {
        return null;
    }

    @Override
    public Object getTopScope(Object context) {
        return null;
    }

    @Override
    public void patchHostContext(Object receiver, HostAccess hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {

    }

    @Override
    public void addToHostClassPath(Object receiver, Object truffleFile) {

    }

    @Override
    public Object toGuestValue(Object receiver, Object hostValue) {
        return null;
    }

    @Override
    public AbstractValueDispatch lookupValueDispatch(Object guestValue) {
        return null;
    }

}
