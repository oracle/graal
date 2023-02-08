/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENTS;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.GraalEditionList;
import org.graalvm.component.installer.MemoryFeedback;
import org.graalvm.component.installer.MemoryFeedback.Memory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class AvailableTest extends CommandTestBase {

    private static final String GRAALVM_CE_202_CATALOG = "test://www.graalvm.org/graalvm-20.2-with-updates.properties";
    private static final String GRAALVM_EE_202_CATALOG = "test://www.graalvm.org/graalvmee-20.2-with-updates.properties";

    private static final String GVM_VERSION = "20.2.0";
    private static final String STABILITY = "ComponentStabilityLevel_undefined";

    @Rule public final ProxyResource proxyResource = new ProxyResource();

    private void loadReleaseFile(String name) throws IOException {
        try (InputStream stm = getClass().getResourceAsStream("data/" + name)) {
            Properties props = new Properties();
            props.load(stm);
            props.stringPropertyNames().forEach((s) -> {
                String val = props.getProperty(s);
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    val = val.substring(1, val.length() - 1);
                }
                this.storage.graalInfo.put(s.toLowerCase(Locale.ENGLISH), val);
            });
        }
    }

    private GraalEditionList editionList;
    private AvailableCommand cmd;

    void setupReleaseAndCatalog() throws Exception {
        loadReleaseFile("release21ceWithEE.properties");

        Path f = dataFile("data/graalvm-20.2-with-updates.properties");
        Handler.bind(GRAALVM_CE_202_CATALOG, f.toUri().toURL());

        Path f2 = dataFile("data/graalvmee-20.2-with-updates.properties");
        Handler.bind(GRAALVM_EE_202_CATALOG, f2.toUri().toURL());
    }

    private void initRemoteStorage() {
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        this.registry = list.createComponentCatalog(this);
        this.editionList = list;
    }

    void setupGraalVM202() throws Exception {
        setupReleaseAndCatalog();
        initRemoteStorage();

        cmd = new AvailableCommand();
        cmd.init(this, this.withBundle(AvailableCommand.class));
        cmd.execute();
    }

    @Override
    public CatalogFactory getCatalogFactory() {
        return editionList;
    }

    /**
     * By default, gu avail should list just its own release's component and NO core package so
     * users are not confused.
     *
     * @throws Exception
     */
    @Test
    public void testDefaultListWithoutCorePackage() throws Exception {
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();
        // no core is listed:
        assertFalse(infos.stream().filter(i -> CommonConstants.GRAALVM_CORE_PREFIX.equals(i.getId())).findAny().isPresent());
    }

    /**
     * Checks that by default, only compatible components are listed, EXCEPT the core, which is
     * listed in its newest version, if enabled by switch.
     *
     * @throws Exception
     */
    @Test
    public void testDefaultListOnlyCompatibleVersions() throws Exception {
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();
        // no version other than 20.2.0 is listed:
        assertFalse(infos.stream().filter(i -> !i.getVersion().displayString().contains(GVM_VERSION)).findAny().isPresent());
    }

    /**
     * Checks that by default, only compatible components are listed, EXCEPT the core, which is
     * listed in its newest version, if enabled by switch.
     *
     * @throws Exception
     */
    @Test
    public void testListCompatibleVersionsPlusCore() throws Exception {
        options.put(Commands.OPTION_SHOW_CORE, "");
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();
        // no version other than 20.2.0 is listed:
        assertFalse(infos.stream().filter(i -> !i.getVersion().displayString().contains(GVM_VERSION)).findAny().isPresent());
        // the core IS listed!
        assertTrue(infos.stream().filter(i -> CommonConstants.GRAALVM_CORE_PREFIX.equals(i.getId())).findAny().isPresent());
    }

    /**
     * Checks that updates are listed with a switch. Implies --show-core.
     */
    @Test
    public void testListUpdates() throws Exception {
        options.put(Commands.OPTION_SHOW_UPDATES, "");
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();
        // no version other than 20.2.0 is listed:
        assertTrue(infos.stream().filter(i -> !i.getVersion().displayString().contains(GVM_VERSION)).findAny().isPresent());
        // the core IS listed!
        assertTrue(infos.stream().filter(i -> CommonConstants.GRAALVM_CORE_PREFIX.equals(i.getId())).findAny().isPresent());
    }

    private static ComponentInfo findComponent(List<ComponentInfo> list, String id) {
        return list.stream().filter(i -> id.equals(i.getId())).findAny().orElse(null);
    }

    /**
     * Checks that edition component(s) are listed. Implies --show-core. Note the test data for 20.2
     * does not list R and Python components
     *
     * @throws Exception
     */
    @Test
    public void testListEECurrentVersion() throws Exception {
        options.put(Commands.OPTION_USE_EDITION, "ee");
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();

        assertNull(findComponent(infos, "org.graalvm.python"));
        assertNull(findComponent(infos, "org.graalvm.r"));

        assertNotNull(findComponent(infos, "org.graalvm.ruby"));
        assertNotNull(findComponent(infos, "org.graalvm"));
    }

    /**
     * Checks that edition component(s) are listed. Implies --show-core. Now updates for 20.3.0
     * should be visible.
     *
     * @throws Exception
     */
    @Test
    public void testListEEUpdates() throws Exception {
        options.put(Commands.OPTION_SHOW_UPDATES, "");
        options.put(Commands.OPTION_USE_EDITION, "ee");
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();

        assertNotNull(findComponent(infos, "org.graalvm.python"));
        assertNotNull(findComponent(infos, "org.graalvm.R"));

        assertNotNull(findComponent(infos, "org.graalvm.ruby"));
        assertNotNull(findComponent(infos, "org.graalvm"));
    }

    /**
     * Disables the default version filter.
     */
    @Test
    public void testListAll() throws Exception {
        options.put(Commands.OPTION_ALL, "");
        setupGraalVM202();
        List<ComponentInfo> infos = cmd.getComponents();

        assertTrue(infos.size() > 8);
    }

    @Test
    public void testJSONOutput() throws Exception {
        options.put(Commands.OPTION_JSON_OUTPUT, "");
        MemoryFeedback mf = new MemoryFeedback();
        this.delegateFeedback(mf);
        setupGraalVM202();
        for (Memory mem : mf) {
            if (!mem.silent) {
                JSONObject jo = new JSONObject(new JSONTokener(mem.key));
                JSONArray comps = jo.getJSONArray(JSON_KEY_COMPONENTS);
                assertEquals(6, comps.length());
                for (int i = 0; i < comps.length(); ++i) {
                    JSONObject comp = comps.getJSONObject(i);
                    assertEquals(comp.toString(2), GVM_VERSION, comp.getString(CommonConstants.JSON_KEY_COMPONENT_GRAALVM));
                    assertEquals(comp.toString(2), GVM_VERSION, comp.getString(CommonConstants.JSON_KEY_COMPONENT_VERSION));
                    assertEquals(comp.toString(2), STABILITY, comp.getString(CommonConstants.JSON_KEY_COMPONENT_STABILITY));
                    assertTrue(comp.toString(2), comp.getString(CommonConstants.JSON_KEY_COMPONENT_ORIGIN)
                                    .contains(comp.getString(CommonConstants.JSON_KEY_COMPONENT_ID).toLowerCase(Locale.ENGLISH) + "-installable-"));
                    assertTrue(comp.toString(2), comp.getString(CommonConstants.JSON_KEY_COMPONENT_NAME) != null);
                }
            }
        }
    }
}
