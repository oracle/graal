/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Represents public information about an instrument.
 *
 * @since 0.26
 */
public final class InstrumentInfo {

    private final Object polyglotInstrument;
    private final String id;
    private final String name;
    private final String version;

    InstrumentInfo(Object vmObject, String id, String name, String version) {
        this.polyglotInstrument = vmObject;
        this.id = id;
        this.name = name;
        this.version = version;
    }

    /**
     * Gets the id clients can use to acquire this instrument.
     *
     * @return this instrument's unique id
     * @since 0.26
     */
    public String getId() {
        return id;
    }

    /**
     * Gets a human readable name of this instrument.
     *
     * @return this instrument's user-friendly name
     * @since 0.26
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the version of this instrument.
     *
     * @return this instrument's version
     * @since 0.26
     */
    public String getVersion() {
        return version;
    }

    Object getPolyglotInstrument() {
        return polyglotInstrument;
    }

    /**
     * @since 0.26
     */
    @Override
    public String toString() {
        return "InstrumentInfo [id=" + id + ", name=" + name + ", version=" + version + "]";
    }

}
