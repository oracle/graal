/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.SandboxPolicy;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

public interface TruffleOptionDescriptors extends OptionDescriptors {

    TruffleOptionDescriptors EMPTY = new TruffleOptionDescriptors() {

        public Iterator<OptionDescriptor> iterator() {
            return Collections.<OptionDescriptor> emptyList().iterator();
        }

        public OptionDescriptor get(String key) {
            return null;
        }

        @Override
        public SandboxPolicy getSandboxPolicy(String key) {
            return null;
        }
    };

    SandboxPolicy getSandboxPolicy(String key);

    static TruffleOptionDescriptors createUnion(OptionDescriptors... descriptors) {
        switch (descriptors.length) {
            case 0:
                return EMPTY;
            case 1:
                return TruffleOptionDescriptorsAdapter.wrap(descriptors[0]);
            default:
                OptionDescriptors singleNonEmpty = null;
                for (int i = 0; i < descriptors.length; i++) {
                    OptionDescriptors d = descriptors[i];
                    if (d != EMPTY && d != OptionDescriptors.EMPTY) {
                        if (singleNonEmpty == null) {
                            singleNonEmpty = d;
                        } else {
                            return new UnionTruffleOptionDescriptors(descriptors);
                        }
                    }
                }
                if (singleNonEmpty == null) {
                    return EMPTY;
                } else {
                    return TruffleOptionDescriptorsAdapter.wrap(singleNonEmpty);
                }
        }
    }
}

final class TruffleOptionDescriptorsAdapter implements TruffleOptionDescriptors {
    private final OptionDescriptors delegate;

    private TruffleOptionDescriptorsAdapter(OptionDescriptors delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public OptionDescriptor get(String optionName) {
        return delegate.get(optionName);
    }

    @Override
    public Iterator<OptionDescriptor> iterator() {
        return delegate.iterator();
    }

    @Override
    public SandboxPolicy getSandboxPolicy(String key) {
        return SandboxPolicy.TRUSTED;
    }

    static TruffleOptionDescriptors wrap(OptionDescriptors optionDescriptors) {
        return optionDescriptors instanceof TruffleOptionDescriptors ? (TruffleOptionDescriptors) optionDescriptors : new TruffleOptionDescriptorsAdapter(optionDescriptors);
    }
}

final class UnionTruffleOptionDescriptors implements TruffleOptionDescriptors {

    private final OptionDescriptors delegate;
    private final OptionDescriptors[] descriptorsList;

    UnionTruffleOptionDescriptors(OptionDescriptors[] descriptorsList) {
        this.delegate = OptionDescriptors.createUnion(descriptorsList);
        this.descriptorsList = descriptorsList;
    }

    @Override
    public Iterator<OptionDescriptor> iterator() {
        return delegate.iterator();
    }

    @Override
    public OptionDescriptor get(String optionName) {
        return delegate.get(optionName);
    }

    @Override
    public SandboxPolicy getSandboxPolicy(String key) {
        for (OptionDescriptors descriptors : descriptorsList) {
            if (descriptors.get(key) != null) {
                if (descriptors instanceof TruffleOptionDescriptors) {
                    SandboxPolicy res = ((TruffleOptionDescriptors) descriptors).getSandboxPolicy(key);
                    if (res != null) {
                        return res;
                    }
                } else {
                    return SandboxPolicy.TRUSTED;
                }
            }
        }
        return null;
    }
}
