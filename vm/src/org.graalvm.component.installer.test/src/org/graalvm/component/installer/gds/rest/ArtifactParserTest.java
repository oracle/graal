/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.component.installer.gds.rest;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.DistributionType;
import org.graalvm.component.installer.model.StabilityLevel;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Test;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 *
 * @author odouda
 */
public class ArtifactParserTest extends TestBase {
    static final String MOCK_URL = "https://mock.url/";
    static final String JSON_VAL_ID = "mockProductId";
    static final String JSON_KEY_ID = "id";
    static final String JSON_KEY_CHECKSUM = "checksum";
    static final String JSON_VAL_CHECKSUM = "mockChecksum";
    static final String JSON_KEY_METADATA = "metadata";
    static final String JSON_KEY_DISP_NAME = "displayName";
    static final String JSON_VAL_DISP_NAME = "mockDisplayName";
    static final String JSON_KEY_LIC_ID = "licenseId";
    static final String JSON_VAL_LIC_ID = "mockLicenseId";
    static final String JSON_KEY_LIC_NAME = "licenseName";
    static final String JSON_VAL_LIC_NAME = "mockLicenseName";
    static final String JSON_META_KEY = "key";
    static final String JSON_META_VAL = "value";
    static final String JSON_META_KEY_ARCH = "arch";
    static final String JSON_META_VAL_ARCH = "fakeArch";
    static final String JSON_META_KEY_OS = "os";
    static final String JSON_META_VAL_OS = "fakeOS";
    static final String JSON_VAL_DISP_NAME2 = JSON_VAL_DISP_NAME + " " + JSON_META_VAL_OS;
    static final String JSON_META_KEY_JAVA = "java";
    static final String JSON_META_VAL_JAVA = "5";
    static final String JSON_META_KEY_VERSION = "version";
    static final String JSON_META_VAL_VERSION = "18.1.3";
    static final String JSON_META_KEY_EDITION = "edition";
    static final String JSON_META_VAL_EDITION = "ee";
    static final String JSON_META_KEY_STABILITY_LEVEL = "stabilityLevel";
    static final String JSON_META_KEY_STABILITY = "stability";
    static final String JSON_META_VAL_STAB_EXPERIMENTAL = StabilityLevel.Experimental.toString();
    static final String JSON_META_VAL_STAB_SUPPORTED = StabilityLevel.Supported.toString();
    static final String JSON_META_KEY_SYMBOLIC_NAME = "symbolicName";
    static final String JSON_META_VAL_SYMBOLIC_NAME = "org.graalvm.llvm-toolchain";
    static final String JSON_META_KEY_DEPENDENCY = "requireBundle";
    static final String JSON_META_KEY_REQUIRED = "requiredCapabilities";
    static final String REQ_VER = "graalvm_version";
    static final String REQ_OS = "os_name";
    static final String REQ_ARCH = "os_arch";
    static final String REQ_JAVA = "java_version";
    static final String JSON_META_VAL_REQUIRED = "org.graalvm; filter:=\"(&(" + REQ_VER + "=" + JSON_META_VAL_VERSION + ")(" + REQ_OS + "=" + JSON_META_VAL_OS + ")(" + REQ_ARCH + "=" +
                    JSON_META_VAL_ARCH + ")(" + REQ_JAVA + "=" + JSON_META_VAL_JAVA + "))\"";
    static final String JSON_META_KEY_POLYGLOT = "polyglot";
    static final String JSON_META_KEY_WORK_DIR = "workingDirectories";
    static final String JSON_META_VAL_WORK_DIR = "languages/python";

    GDSRESTConnector conn = new GDSRESTConnector(MOCK_URL, this, JSON_VAL_ID, Version.fromString(JSON_META_VAL_VERSION));

    @Test
    public void testConstruct() {
        ArtifactParser ap = null;
        try {
            ap = new ArtifactParser(null);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            assertEquals("Parsed Artifact JSON cannot be null.", ex.getMessage());
            // expected
        }
        JSONObject jo = new JSONObject();
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_ID);
            // expected
        }
        jo.put(JSON_KEY_ID, JSON_VAL_ID);
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_CHECKSUM);
            // expected
        }
        jo.put(JSON_KEY_CHECKSUM, JSON_VAL_CHECKSUM);
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_METADATA);
            // expected
        }
        JSONArray meta = new JSONArray();
        jo.put(JSON_KEY_METADATA, meta);
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_DISP_NAME);
            // expected
        }
        jo.put(JSON_KEY_DISP_NAME, JSON_VAL_DISP_NAME);
        try {
            ap = new ArtifactParser(jo);
            fail("StringIndexOutOfBoundsException expected.");
        } catch (StringIndexOutOfBoundsException ex) {
            assertEquals("begin 0, end -1, length 15", ex.getMessage());
            // expected
        }
        jo.put(JSON_KEY_DISP_NAME, JSON_VAL_DISP_NAME + SystemUtils.OS.get().getName());
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_LIC_ID);
            // expected
        }
        jo.put(JSON_KEY_LIC_ID, JSON_VAL_LIC_ID);
        try {
            ap = new ArtifactParser(jo);
            fail("JSONException expected.");
        } catch (JSONException ex) {
            tstJsonExc(ex, JSON_KEY_LIC_NAME);
            // expected
        }
        jo.put(JSON_KEY_LIC_NAME, JSON_VAL_LIC_NAME);
        ap = new ArtifactParser(jo);
        assertEquals(null, ap.getLabel());
        assertEquals(SystemUtils.ARCH.get().getName(), ap.getArch());
        assertEquals(SystemUtils.OS.get().getName(), ap.getOs());
        assertEquals(SystemUtils.getJavaMajorVersion() + "", ap.getJava());
        assertEquals(null, ap.getVersion());
        assertEquals(null, ap.getEdition());
        setMeta(meta, JSON_META_KEY_ARCH, JSON_META_VAL_ARCH);
        assertEquals(JSON_META_VAL_ARCH, ap.getArch());
        setMeta(meta, JSON_META_KEY_OS, JSON_META_VAL_OS);
        assertEquals(JSON_META_VAL_OS, ap.getOs());
        setMeta(meta, JSON_META_KEY_JAVA, JSON_META_VAL_JAVA);
        assertEquals(JSON_META_VAL_JAVA, ap.getJava());
        setMeta(meta, JSON_META_KEY_VERSION, JSON_META_VAL_VERSION);
        assertEquals(JSON_META_VAL_VERSION, ap.getVersion());
        setMeta(meta, JSON_META_KEY_EDITION, JSON_META_VAL_EDITION);
        assertEquals(JSON_META_VAL_EDITION, ap.getEdition());
        setMeta(meta, JSON_META_KEY_SYMBOLIC_NAME, JSON_META_VAL_SYMBOLIC_NAME);
        assertEquals(JSON_META_VAL_SYMBOLIC_NAME, ap.getLabel());
    }

    @Test
    public void testAsComponentInfo() {
        JSONObject jo = prepareJO();
        ArtifactParser ap = new ArtifactParser(jo);
        ComponentInfo ci = ap.asComponentInfo(conn, this);
        assertEquals(JSON_VAL_DISP_NAME, ci.getName());
        assertEquals(JSON_META_VAL_SYMBOLIC_NAME, ci.getId());
        assertEquals(Version.fromString(JSON_META_VAL_VERSION), ci.getVersion());
        assertEquals(JSON_META_VAL_VERSION, ci.getVersionString());
        assertArrayEquals(SystemUtils.toHashBytes(JSON_VAL_CHECKSUM), ci.getShaDigest());
        assertEquals(JSON_VAL_CHECKSUM, ci.getTag());
        assertEquals(conn.makeArtifactDownloadURL(JSON_VAL_ID), ci.getRemoteURL());
        assertEquals(conn.makeArtifactsURL(JSON_META_VAL_JAVA), ci.getOrigin());
        assertEquals(conn.makeLicenseURL(JSON_VAL_LIC_ID), ci.getLicensePath());
        assertEquals(JSON_VAL_LIC_NAME, ci.getLicenseType());
        assertEquals(DistributionType.OPTIONAL, ci.getDistributionType());
        assertEquals(Collections.singleton(JSON_META_VAL_WORK_DIR), ci.getWorkingDirectories());
        assertEquals(Map.of(REQ_ARCH, JSON_META_VAL_ARCH.toLowerCase(Locale.ENGLISH),
                        REQ_OS, JSON_META_VAL_OS.toLowerCase(Locale.ENGLISH),
                        REQ_JAVA, JSON_META_VAL_JAVA,
                        REQ_VER, JSON_META_VAL_VERSION), ci.getRequiredGraalValues());
        assertEquals(Collections.singleton(JSON_META_VAL_SYMBOLIC_NAME), ci.getDependencies());
        assertEquals(StabilityLevel.fromName(JSON_META_VAL_STAB_EXPERIMENTAL), ci.getStability());
        setMeta(jo, JSON_META_KEY_STABILITY_LEVEL, JSON_META_VAL_STAB_SUPPORTED);
        ci = ap.asComponentInfo(conn, this);
        assertEquals(StabilityLevel.fromName(JSON_META_VAL_STAB_SUPPORTED), ci.getStability());
    }

    JSONObject setMeta(JSONObject jo, String key, String val) {
        JSONArray meta = jo.optJSONArray(JSON_KEY_METADATA);
        if (meta == null) {
            meta = new JSONArray();
            jo.put(JSON_KEY_METADATA, meta);
        }
        return setMeta(meta, key, val);
    }

    JSONObject setMeta(JSONArray meta, String key, String val) {
        JSONObject o;
        for (int i = 0; i < meta.length(); ++i) {
            o = meta.getJSONObject(i);
            if (key.equals(o.getString(JSON_META_KEY))) {
                o.put(JSON_META_VAL, val);
                return o;
            }
        }
        o = new JSONObject();
        o.put(JSON_META_KEY, key);
        o.put(JSON_META_VAL, val);
        meta.put(o);
        return o;
    }

    JSONObject prepareJO() {
        JSONObject jo = new JSONObject();
        jo.put(JSON_KEY_ID, JSON_VAL_ID);
        jo.put(JSON_KEY_CHECKSUM, JSON_VAL_CHECKSUM);
        jo.put(JSON_KEY_DISP_NAME, JSON_VAL_DISP_NAME2);
        jo.put(JSON_KEY_LIC_ID, JSON_VAL_LIC_ID);
        jo.put(JSON_KEY_LIC_NAME, JSON_VAL_LIC_NAME);
        JSONArray meta = new JSONArray();
        jo.put(JSON_KEY_METADATA, meta);
        setMeta(meta, JSON_META_KEY_SYMBOLIC_NAME, JSON_META_VAL_SYMBOLIC_NAME);
        setMeta(meta, JSON_META_KEY_ARCH, JSON_META_VAL_ARCH);
        setMeta(meta, JSON_META_KEY_OS, JSON_META_VAL_OS);
        setMeta(meta, JSON_META_KEY_JAVA, JSON_META_VAL_JAVA);
        setMeta(meta, JSON_META_KEY_VERSION, JSON_META_VAL_VERSION);
        setMeta(meta, JSON_META_KEY_EDITION, JSON_META_VAL_EDITION);
        setMeta(meta, JSON_META_KEY_REQUIRED, JSON_META_VAL_REQUIRED);
        setMeta(meta, JSON_META_KEY_POLYGLOT, Boolean.toString(false));
        setMeta(meta, JSON_META_KEY_WORK_DIR, JSON_META_VAL_WORK_DIR);
        setMeta(meta, JSON_META_KEY_DEPENDENCY, JSON_META_VAL_SYMBOLIC_NAME);
        setMeta(meta, JSON_META_KEY_STABILITY, JSON_META_VAL_STAB_EXPERIMENTAL);
        return jo;
    }

    void tstJsonExc(JSONException ex, String key) {
        assertEquals("JSONObject[\"" + key + "\"] not found.", ex.getMessage());
    }
}
