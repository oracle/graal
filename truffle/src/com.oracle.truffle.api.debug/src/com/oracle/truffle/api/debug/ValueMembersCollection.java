/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * Collection of member objects.
 */
final class ValueMembersCollection extends AbstractCollection<DebugValue> {

    static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    private final DebuggerSession session;
    private final LanguageInfo language;
    private final Object object;
    private final boolean includeInherited;
    private final Object firstMembersArray;
    private final Object firstMetaObject;

    ValueMembersCollection(DebuggerSession session, LanguageInfo language, Object object, boolean includeInherited) {
        this.object = object;
        this.session = session;
        this.language = language;
        this.includeInherited = includeInherited;

        // Compute the first line of members so that we throw lang errors early.
        try {
            Object membersArray = null;
            Object metaObject = null;
            if (INTEROP.hasMetaObject(object)) {
                Object meta = INTEROP.getMetaObject(object);
                if (INTEROP.hasDeclaredMembers(meta)) {
                    membersArray = INTEROP.getDeclaredMembers(meta);
                    metaObject = meta;
                }
            }
            if (membersArray == null) {
                membersArray = INTEROP.getMemberObjects(object);
            }
            firstMembersArray = membersArray;
            firstMetaObject = metaObject;
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(session, ex, language);
        }
    }

    @Override
    public Iterator<DebugValue> iterator() {
        return new MembersIterator();
    }

    @Override
    public int size() {
        MembersIterator mit = new MembersIterator();
        int size = 0;
        try {
            Object members = mit.getMembersInit();
            while (members != null) {
                long as = INTEROP.getArraySize(members);
                if (as > Integer.MAX_VALUE || (size + as) > Integer.MAX_VALUE) {
                    // overflow
                    return Integer.MAX_VALUE;
                }
                size += (int) as;
                members = mit.getMembersNext();
            }
        } catch (InteropException ex) {
            throw DebugException.create(session, ex, language);
        }
        return size;
    }

    private final class MembersIterator implements Iterator<DebugValue> {

        private long currentIndex = 0L;
        private LinkedList<Object> metaObjectQueue;
        private Object members;
        private Object nextMember;

        @Override
        public boolean hasNext() {
            if (nextMember != null) {
                return true;
            }
            Object member = readNext();
            nextMember = member;
            return member != null;
        }

        @Override
        public DebugValue next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Object member = nextMember;
            if (member == null) {
                throw new NoSuchElementException();
            }
            nextMember = null;
            return new DebugValue.MemberObject(session, language, null, object, member);
        }

        private Object readNext() {
            try {
                if (members == null) {
                    members = getMembersInit();
                    if (members == null) {
                        return null;
                    }
                }
                if (!INTEROP.isArrayElementExisting(members, currentIndex)) {
                    if (!includeInherited) {
                        return null;
                    }
                    members = getMembersNext();
                    currentIndex = 0;
                    if (members == null) {
                        return null;
                    }
                }
                Object member = INTEROP.readArrayElement(members, currentIndex);
                this.currentIndex++;
                return member;
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable ex) {
                throw DebugException.create(session, ex, language);
            }
        }

        private Object getMembersInit() {
            if (firstMetaObject != null) {
                metaObjectQueue = new LinkedList<>();
                metaObjectQueue.add(firstMetaObject);
            }
            return firstMembersArray;
        }

        private Object getMembersNext() throws UnsupportedMessageException, InvalidArrayIndexException {
            if (metaObjectQueue != null) {
                while (!metaObjectQueue.isEmpty()) {
                    Object metaObject = metaObjectQueue.poll();
                    Object membersArray = null;
                    if (INTEROP.hasDeclaredMembers(metaObject)) {
                        membersArray = INTEROP.getDeclaredMembers(metaObject);
                    }
                    if (INTEROP.hasMetaParents(metaObject)) {
                        Object metaParents = INTEROP.getMetaParents(metaObject);
                        fillMetaObjects(metaParents);
                    }
                    if (membersArray != null && INTEROP.getArraySize(membersArray) > 0) {
                        return membersArray;
                    }
                }
            }
            return null;
        }

        private void fillMetaObjects(Object metaObjectsArray) throws UnsupportedMessageException, InvalidArrayIndexException {
            long size = INTEROP.getArraySize(metaObjectsArray);
            for (long i = 0; i < size; i++) {
                metaObjectQueue.add(INTEROP.readArrayElement(metaObjectsArray, i));
            }
        }
    }

}
