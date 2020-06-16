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
package org.graalvm.polyglot;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractInstrumentImpl;

/**
 * A handle for an <em>instrument</em> installed in an {@link Engine engine}. The instrument is
 * usable from other threads. The handle provides access to the metadata of the instrument and
 * allows to {@link #lookup(Class) lookup} instrument specific services.
 * <p>
 * All methods here, as well as instrumentation services in general, can be used safely from any
 * thread.
 *
 * @see Engine#getInstruments()
 * @since 19.0
 */
public final class Instrument {

    final AbstractInstrumentImpl impl;

    Instrument(AbstractInstrumentImpl impl) {
        this.impl = impl;
    }

    /**
     * Gets the ID clients can use to acquire this instrument.
     *
     * @return the unique ID for this instrument.
     * @since 19.0
     */
    public String getId() {
        return impl.getId();
    }

    /**
     * Gets a human-readable name for this instrument.
     *
     * @return the user-friendly name for this instrument.
     * @since 19.0
     */
    public String getName() {
        return impl.getName();
    }

    /**
     * Gets the options available for this instrument.
     *
     * @return the options as {@link OptionDescriptors}.
     * @since 19.0
     */
    public OptionDescriptors getOptions() {
        return impl.getOptions();
    }

    /**
     * Gets the version of this instrument.
     *
     * @return the version of this instrument.
     * @since 19.0
     */
    public String getVersion() {
        return impl.getVersion();
    }

    /**
     * Looks up an additional internal service that is provided by this instrument using a Java
     * type. Note that the services returned by this method are implementation specific and subject
     * to change without notice.
     *
     * @param <T> the type of the internal service.
     * @param type class of the service that is being requested.
     * @return instance of requested type, <code>null</code> if no such service is available.
     * @since 19.0
     */
    public <T> T lookup(Class<T> type) {
        return impl.lookup(type);
    }

}
