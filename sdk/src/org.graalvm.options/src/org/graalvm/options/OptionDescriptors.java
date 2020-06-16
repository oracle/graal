/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.options;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * An interface to a set of {@link OptionDescriptor}s.
 *
 * @since 19.0
 */
public interface OptionDescriptors extends Iterable<OptionDescriptor> {

    /**
     * An empty set of option descriptors.
     *
     * @since 19.0
     */
    OptionDescriptors EMPTY = new OptionDescriptors() {

        public Iterator<OptionDescriptor> iterator() {
            return Collections.<OptionDescriptor> emptyList().iterator();
        }

        public OptionDescriptor get(String key) {
            return null;
        }
    };

    /**
     * Gets the {@link OptionDescriptor} matching a given option name or {@code null} if this option
     * descriptor set does not contain a matching option name.
     *
     * @since 19.0
     */
    OptionDescriptor get(String optionName);

    /**
     * Creates a union options descriptor out of multiple given descriptors. The operation
     * descriptors are not checked for duplicate keys. The option descriptors are iterated in
     * declaration order.
     *
     * @since 19.0
     */
    static OptionDescriptors createUnion(OptionDescriptors... descriptors) {
        if (descriptors.length == 0) {
            return EMPTY;
        } else if (descriptors.length == 1) {
            return descriptors[0];
        } else {
            return new UnionOptionDescriptors(descriptors);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    Iterator<OptionDescriptor> iterator();

    /**
     * Creates an {@link OptionDescriptors} instance from a list. The option descriptors
     * implementation is backed by a {@link LinkedHashMap} that preserves ordering.
     *
     * @since 19.0
     */
    static OptionDescriptors create(List<OptionDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return EMPTY;
        }
        return new OptionDescriptorsMap(descriptors);
    }
}

class OptionDescriptorsMap implements OptionDescriptors {

    final Map<String, OptionDescriptor> descriptors = new LinkedHashMap<>();
    final List<String> prefixes = new ArrayList<>();

    OptionDescriptorsMap(List<OptionDescriptor> descriptorList) {
        for (OptionDescriptor descriptor : descriptorList) {
            if (descriptor.isOptionMap()) {
                prefixes.add(descriptor.getName());
            }
            descriptors.put(descriptor.getName(), descriptor);
        }
    }

    @Override
    public OptionDescriptor get(String optionName) {
        if (!prefixes.isEmpty()) {
            for (String prefix : prefixes) {
                if (optionName.startsWith(prefix + ".") || optionName.equals(prefix)) {
                    return descriptors.get(prefix);
                }
            }
        }
        return descriptors.get(optionName);
    }

    @Override
    public Iterator<OptionDescriptor> iterator() {
        return descriptors.values().iterator();
    }

}

final class UnionOptionDescriptors implements OptionDescriptors {

    final OptionDescriptors[] descriptorsList;

    UnionOptionDescriptors(OptionDescriptors[] descriptors) {
        // defensive copy
        this.descriptorsList = Arrays.copyOf(descriptors, descriptors.length);
    }

    public Iterator<OptionDescriptor> iterator() {
        return new Iterator<OptionDescriptor>() {

            Iterator<OptionDescriptor> descriptors = descriptorsList[0].iterator();
            int descriptorsIndex = 0;
            OptionDescriptor next = null;

            public boolean hasNext() {
                return fetchNext() != null;
            }

            private OptionDescriptor fetchNext() {
                if (next != null) {
                    return next;
                }
                if (descriptors.hasNext()) {
                    next = descriptors.next();
                    return next;
                } else if (descriptorsIndex < descriptorsList.length - 1) {
                    descriptorsIndex++;
                    descriptors = descriptorsList[descriptorsIndex].iterator();
                    return fetchNext();
                } else {
                    return null;
                }
            }

            public OptionDescriptor next() {
                OptionDescriptor fetchedNext = fetchNext();
                if (fetchedNext != null) {
                    // consume next
                    this.next = null;
                    return fetchedNext;
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }

    public OptionDescriptor get(String value) {
        for (OptionDescriptors descriptors : descriptorsList) {
            OptionDescriptor descriptor = descriptors.get(value);
            if (descriptor != null) {
                return descriptor;
            }
        }
        return null;
    }

}
