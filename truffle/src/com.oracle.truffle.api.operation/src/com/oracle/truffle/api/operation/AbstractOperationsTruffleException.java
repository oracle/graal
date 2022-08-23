/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public abstract class AbstractOperationsTruffleException extends AbstractTruffleException {

    private static final long serialVersionUID = -534184847100559365L;

    public AbstractOperationsTruffleException() {
        super();
    }

    public AbstractOperationsTruffleException(AbstractOperationsTruffleException prototype) {
        super(prototype);
    }

    public AbstractOperationsTruffleException(Node location, int bci) {
        super(getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message, Node location, int bci) {
        super(message, getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message, Throwable cause, int stackTraceElementLimit, Node location, int bci) {
        super(message, cause, stackTraceElementLimit, getLocation(location, bci));
    }

    public AbstractOperationsTruffleException(String message) {
        super(message);
    }

    private static Node getLocation(Node location, int bci) {
        if (bci >= 0) {
            return new SourceLocationNode(((OperationRootNode) location).getSourceSectionAtBci(bci));
        } else {
            return location;
        }
    }

    private static class SourceLocationNode extends Node {
        private final SourceSection location;

        SourceLocationNode(SourceSection location) {
            this.location = location;
        }

        @Override
        public SourceSection getSourceSection() {
            return location;
        }

        @Override
        public SourceSection getEncapsulatingSourceSection() {
            return location;
        }

    }
}
