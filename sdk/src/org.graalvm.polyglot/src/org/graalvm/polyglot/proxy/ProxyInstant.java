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
package org.graalvm.polyglot.proxy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

import org.graalvm.polyglot.Value;

/**
 * Interface to be implemented to mimic guest language objects that represents timestamps. This
 * interface also implements {@link ProxyDate}, {@link ProxyTime} and {@link ProxyTimeZone}.
 *
 * @see Proxy
 * @see Value
 * @since 19.2.0
 */
public interface ProxyInstant extends ProxyDate, ProxyTime, ProxyTimeZone {

    /**
     * Returns the instant information. The returned value must not be <code>null</code>.
     *
     * The following invariant must always hold for instant proxies:
     *
     * <pre>
     * ProxyInstant proxy = ...
     * ZoneId zone = proxy.getTimeZone();
     * LocalDate date = proxy.getDate();
     * LocalTime time = proxy.getTime();
     * assert ZonedDateTime.of(date, time, zone).equals(proxy.getInstant());
     * </pre>
     *
     * @since 19.2.0
     */
    Instant asInstant();

    /**
     * {@inheritDoc}
     *
     * The invariant specified in {@link #asInstant()} must always hold for proxy instant values.
     *
     * @see #asInstant()
     * @since 19.2.0
     */
    default LocalDate asDate() {
        return asInstant().atZone(ProxyInstantConstants.UTC).toLocalDate();
    }

    /**
     * {@inheritDoc}
     *
     * The invariant specified in {@link #asInstant()} must always hold for proxy instant values.
     *
     * @see #asInstant()
     * @since 19.2.0
     */
    default LocalTime asTime() {
        return asInstant().atZone(ProxyInstantConstants.UTC).toLocalTime();
    }

    /**
     * {@inheritDoc}
     *
     * The invariant specified in {@link #asInstant()} must always hold for proxy instant values.
     *
     * @see #asInstant()
     * @since 19.2.0
     */
    default ZoneId asTimeZone() {
        return ProxyInstantConstants.UTC;
    }

    /**
     * Creates a new proxy instant from a Java instant value.
     *
     * @since 19.2.0
     */
    static ProxyInstant from(Instant instant) {
        Objects.requireNonNull(instant);
        return new ProxyInstant() {
            @Override
            public Instant asInstant() {
                return instant;
            }
        };
    }
}

class ProxyInstantConstants {
    static final ZoneId UTC = ZoneId.of("UTC");
}
