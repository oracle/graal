/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor.test.process;

import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.GenerateProcessToProcessBridge;
import org.graalvm.nativebridge.GenerateProcessToProcessFactory;
import org.graalvm.nativebridge.processor.test.ExpectError;
import org.graalvm.nativebridge.processor.test.LocalService;
import org.graalvm.nativebridge.processor.test.Service;
import org.graalvm.nativebridge.processor.test.TestMarshallerConfig;

@GenerateProcessToProcessFactory(marshallers = TestMarshallerConfig.class, initialService = InvalidServices2Service1.class, services = {InvalidServices2Service1.class})
final class InvalidServices2 {
}

@GenerateProcessToProcessBridge(factory = InvalidServices2.class, implementation = LocalService.class)
abstract class InvalidServices2Service1 implements ForeignObject, Service {
}

@ExpectError("Invalid service registration: The service definition `InvalidServices2Service2` is not listed in `@GenerateProcessToProcessFactory.services` of `InvalidServices2`.%n" +
                "To resolve this, include `InvalidServices2Service2` in the `services` attribute of `@GenerateProcessToProcessFactory` in `InvalidServices2`.")
@GenerateProcessToProcessBridge(factory = InvalidServices2.class)
abstract class InvalidServices2Service2 implements ForeignObject, Service {
}
