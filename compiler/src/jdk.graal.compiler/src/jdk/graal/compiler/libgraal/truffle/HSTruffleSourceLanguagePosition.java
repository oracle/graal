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
package jdk.graal.compiler.libgraal.truffle;

import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;
import com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;

import java.net.URI;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetDescription;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLanguage;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetLineNumber;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeClassName;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetNodeId;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetEnd;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetOffsetStart;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetURI;

final class HSTruffleSourceLanguagePosition extends HSObject implements TruffleSourceLanguagePosition {

    private final TruffleFromLibGraalCalls calls;

    HSTruffleSourceLanguagePosition(JNIMethodScope scope, JObject handle, TruffleFromLibGraalCalls calls) {
        super(scope, handle);
        this.calls = calls;
    }

    @TruffleFromLibGraal(GetDescription)
    @Override
    public String getDescription() {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, HSTruffleSourceLanguagePositionGen.callGetDescription(calls, env, getHandle()));
    }

    @TruffleFromLibGraal(GetOffsetEnd)
    @Override
    public int getOffsetEnd() {
        return HSTruffleSourceLanguagePositionGen.callGetOffsetEnd(calls, JNIMethodScope.env(), getHandle());
    }

    @TruffleFromLibGraal(GetOffsetStart)
    @Override
    public int getOffsetStart() {
        return HSTruffleSourceLanguagePositionGen.callGetOffsetStart(calls, JNIMethodScope.env(), getHandle());
    }

    @TruffleFromLibGraal(GetLineNumber)
    @Override
    public int getLineNumber() {
        return HSTruffleSourceLanguagePositionGen.callGetLineNumber(calls, JNIMethodScope.env(), getHandle());
    }

    @TruffleFromLibGraal(GetURI)
    @Override
    public URI getURI() {
        JNIEnv env = JNIMethodScope.env();
        String stringifiedURI = JNIUtil.createString(env, HSTruffleSourceLanguagePositionGen.callGetURI(calls, env, getHandle()));
        return stringifiedURI == null ? null : URI.create(stringifiedURI);
    }

    @TruffleFromLibGraal(GetLanguage)
    @Override
    public String getLanguage() {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, HSTruffleSourceLanguagePositionGen.callGetLanguage(calls, env, getHandle()));
    }

    @TruffleFromLibGraal(GetNodeId)
    @Override
    public int getNodeId() {
        return HSTruffleSourceLanguagePositionGen.callGetNodeId(calls, JNIMethodScope.env(), getHandle());
    }

    @TruffleFromLibGraal(GetNodeClassName)
    @Override
    public String getNodeClassName() {
        JNIEnv env = JNIMethodScope.env();
        return JNIUtil.createString(env, HSTruffleSourceLanguagePositionGen.callGetNodeClassName(calls, env, getHandle()));
    }
}
