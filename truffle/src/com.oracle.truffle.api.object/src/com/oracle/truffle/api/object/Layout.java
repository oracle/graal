/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.List;

import com.oracle.truffle.api.Assumption;

/**
 * Describes layout and behavior of a {@link DynamicObject} subclass and is used to create shapes.
 *
 * An object may change its shape but only to shapes of the same layout.
 */
abstract class Layout {

    public static final String OPTION_PREFIX = "truffle.object.";

    private static final Iterable<LayoutFactory> LAYOUT_FACTORIES = List.of(new CoreLayoutFactory(), new ExtLayoutFactory());

    private static final LayoutFactory LAYOUT_FACTORY = loadLayoutFactory();

    static final int INT_TO_DOUBLE_FLAG = 1;
    static final int INT_TO_LONG_FLAG = 2;

    /**
     * Constructor for subclasses.
     *
     * @since 0.8 or earlier
     */
    protected Layout() {
    }

    public abstract Class<? extends DynamicObject> getType();

    /**
     * Create a root shape.
     *
     * @since 20.2.0
     */
    @SuppressWarnings("unused")
    protected Shape buildShape(Object dynamicType, Object sharedData, int flags, Assumption singleContextAssumption) {
        throw new UnsupportedOperationException();
    }

    protected static LayoutFactory getFactory() {
        return LAYOUT_FACTORY;
    }

    private static LayoutFactory loadLayoutFactory() {
        LayoutFactory layoutFactory = selectLayoutFactory(LAYOUT_FACTORIES);
        if (layoutFactory == null) {
            throw shouldNotReachHere("LayoutFactory not found");
        }
        return layoutFactory;
    }

    private static LayoutFactory selectLayoutFactory(Iterable<LayoutFactory> availableLayoutFactories) {
        String layoutFactoryImplName = System.getProperty(OPTION_PREFIX + "LayoutFactory");
        LayoutFactory bestLayoutFactory = null;
        for (LayoutFactory currentLayoutFactory : availableLayoutFactories) {
            if (layoutFactoryImplName != null) {
                if (currentLayoutFactory.getClass().getName().equals(layoutFactoryImplName)) {
                    return currentLayoutFactory;
                }
            } else {
                if (bestLayoutFactory == null) {
                    bestLayoutFactory = currentLayoutFactory;
                } else if (currentLayoutFactory.getPriority() >= bestLayoutFactory.getPriority()) {
                    assert currentLayoutFactory.getPriority() != bestLayoutFactory.getPriority();
                    bestLayoutFactory = currentLayoutFactory;
                }
            }
        }
        return bestLayoutFactory;
    }
}
