/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;

import java.lang.invoke.MethodHandle;
import java.net.URI;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getHostMethodHandleOrFail;

final class HSTruffleSourceLanguagePosition extends HSIndirectHandle implements TruffleSourceLanguagePosition {

    private static final MethodHandle getOffsetStart = getHostMethodHandleOrFail(GetOffsetStart);
    private static final MethodHandle getOffsetEnd = getHostMethodHandleOrFail(GetOffsetEnd);
    private static final MethodHandle getLineNumber = getHostMethodHandleOrFail(GetLineNumber);
    private static final MethodHandle getLanguage = getHostMethodHandleOrFail(GetLanguage);
    private static final MethodHandle getDescription = getHostMethodHandleOrFail(GetDescription);
    private static final MethodHandle getURI = getHostMethodHandleOrFail(GetURI);
    private static final MethodHandle getNodeClassName = getHostMethodHandleOrFail(GetNodeClassName);
    private static final MethodHandle getNodeId = getHostMethodHandleOrFail(GetNodeId);

    HSTruffleSourceLanguagePosition(Object hsHandle) {
        super(hsHandle);
    }

    @Override
    public String getDescription() {
        try {
            return (String) getDescription.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getOffsetEnd() {
        try {
            return (int) getOffsetEnd.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getOffsetStart() {
        try {
            return (int) getOffsetStart.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getLineNumber() {
        try {
            return (int) getLineNumber.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public URI getURI() {
        String uri;
        try {
            uri = (String) getURI.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        return uri == null ? null : URI.create(uri);
    }

    @Override
    public String getLanguage() {
        try {
            return (String) getLanguage.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public int getNodeId() {
        try {
            return (int) getNodeId.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public String getNodeClassName() {
        try {
            return (String) getNodeClassName.invoke(hsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }
}
