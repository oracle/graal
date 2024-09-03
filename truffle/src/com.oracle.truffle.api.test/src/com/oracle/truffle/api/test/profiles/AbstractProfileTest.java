/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.profiles;

import static com.oracle.truffle.api.test.ReflectionUtils.invoke;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;

import com.oracle.truffle.api.dsl.InlineSupport.ByteField;
import com.oracle.truffle.api.dsl.InlineSupport.InlinableField;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.IntField;
import com.oracle.truffle.api.dsl.InlineSupport.LongField;
import com.oracle.truffle.api.dsl.InlineSupport.ReferenceField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedProfile;
import com.oracle.truffle.api.profiles.Profile;
import com.oracle.truffle.api.test.ReflectionUtils;

public abstract class AbstractProfileTest {

    protected <T> T createEnabled(Class<T> p) {
        if (InlinedProfile.class.isAssignableFrom(p)) {
            return inline(p);
        } else {
            return ReflectionUtils.newInstance(p);
        }
    }

    static final class InlinedStateNode extends Node {

        int state0;
        int state1;
        int state2;
        int state3;

        int int0;
        int int1;
        int int2;
        int int3;

        byte byte0;
        byte byte1;
        byte byte2;
        byte byte3;

        long long0;
        long long1;
        long long2;
        long long3;

        Object ref0;
        Object ref1;
        Object ref2;
        Object ref3;

        static Lookup lookup() {
            return MethodHandles.lookup();
        }

    }

    protected abstract InlinableField[] getInlinedFields();

    protected static final InlinableField[] createInlinedFields(int stateFields, int byteFields, int intFields, int longFields, int refFields) {
        List<InlinableField> fields = new ArrayList<>();
        addInlinedFields(fields, stateFields, byteFields, intFields, longFields, refFields);
        return fields.toArray(new InlinableField[fields.size()]);
    }

    protected static void addInlinedFields(List<InlinableField> fields, int stateFields, int byteFields, int intFields, int longFields, int refFields) {
        for (int i = 0; i < stateFields; i++) {
            fields.add(StateField.create(InlinedStateNode.lookup(), "state" + i));
        }

        for (int i = 0; i < byteFields; i++) {
            fields.add(ByteField.create(InlinedStateNode.lookup(), "byte" + i));
        }

        for (int i = 0; i < intFields; i++) {
            fields.add(IntField.create(InlinedStateNode.lookup(), "int" + i));
        }

        for (int i = 0; i < longFields; i++) {
            fields.add(LongField.create(InlinedStateNode.lookup(), "long" + i));
        }

        for (int i = 0; i < refFields; i++) {
            fields.add(ReferenceField.create(InlinedStateNode.lookup(), "ref" + i, Object.class));
        }
    }

    protected InlinedStateNode state;

    @Before
    public final void setup() {
    }

    @SuppressWarnings("unchecked")
    private <T> T inline(Class<T> inlined) {
        state = new InlinedStateNode();
        return ReflectionUtils.newInstance(inlined, InlineTarget.create(Profile.class, getInlinedFields()));

    }

    protected static boolean isInlined(Object p) {
        return p instanceof InlinedProfile;
    }

    protected String toString(Object profile) {
        if (isInlined(profile)) {
            return ((InlinedProfile) profile).toString(state);
        } else {
            return profile.toString();
        }
    }

    protected boolean isGeneric(Object profile) {
        return (boolean) invokeProfileMethod(profile, "isGeneric");
    }

    @SuppressWarnings("rawtypes")
    protected final Object invokeProfileMethod(Object profile, String name) {
        if (isInlined(profile)) {
            return invoke(profile, name, new Class[]{Node.class}, state);
        } else {
            return invoke(profile, name);
        }
    }

    protected boolean isUninitialized(Object profile) {
        return (boolean) invokeProfileMethod(profile, "isUninitialized");
    }

    @SuppressWarnings("unchecked")
    protected <V> V getCachedValue(Object profile) {
        return (V) invokeProfileMethod(profile, "getCachedValue");
    }

}
