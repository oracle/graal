/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.persist;

import org.graalvm.component.installer.MetadataException;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.Version;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class HeaderParserTest extends TestBase {
    private HeaderParser v(String content) {
        return new HeaderParser(BundleConstants.BUNDLE_VERSION, content, withBundle(HeaderParser.class));
    }

    private HeaderParser r(String content) {
        return new HeaderParser(BundleConstants.BUNDLE_REQUIRED, content, withBundle(HeaderParser.class));
    }

    private HeaderParser c(String content) {
        return new HeaderParser(BundleConstants.BUNDLE_PROVIDED, content, withBundle(HeaderParser.class));
    }

    private HeaderParser d(String content) {
        return new HeaderParser(BundleConstants.BUNDLE_DEPENDENCY, content, withBundle(HeaderParser.class));
    }

    private static void assertHeader(MetadataException ex) {
        assertEquals(BundleConstants.BUNDLE_VERSION, ex.getOffendingHeader());
    }

    @Test
    public void testVersionComponents() {
        assertEquals("1", v("1").version());
        assertEquals("1.1", v("1.1").version());
        assertEquals("1.2.1", v("1.2.1").version());
        assertEquals("1.2.1.whatever", v("1.2.1.whatever").version());
    }

    @Test
    public void testNonDigitsInVersion() {
        try {
            v("1a").version();
            fail("Must fail on incorrect major");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("aa").version();
            fail("Must fail on incorrect major");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("1.1a").version();
            fail("Must fail on incorrect minor");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("1.2.1a").version();
            fail("Must fail on incorrect micro");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        // relaxed check, this should succeed
        v("0.33-dev").version();
    }

    @Test
    public void testEmptyVersionComponent() {
        try {
            v("").version();
            fail("Must fail on empty version");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v(".1").version();
            fail("Must fail on empty major");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("1..1").version();
            fail("Must fail on empty micro");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("1.1..").version();
            fail("Must fail on empty micro");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
        try {
            v("1.1.2.").version();
            fail("Must fail on empty qualifier");
        } catch (MetadataException ex) {
            assertHeader(ex);
        }
    }

    @Test(expected = MetadataException.class)
    public void testGarbageAfterVersion() {
        v("aa 1").version();
    }

    @Test(expected = MetadataException.class)
    public void testGarbageBeforeVersion() {
        v("1 aa").version();
    }

    @Test
    public void testFoo() {
        try {
            r("org.graalvm; filter := \"()\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
    }

    @Test
    public void testBadCapabilitySyntax() {
        try {
            r("foobar").parseRequiredCapabilities();
            fail("Should fail on unkown capability");
        } catch (MetadataException ex) {
            assertEquals(BundleConstants.BUNDLE_REQUIRED, ex.getOffendingHeader());
        }
        try {
            r("foobar = aaa").parseRequiredCapabilities();
            fail("Should fail on invalid syntax");
        } catch (MetadataException ex) {
            // expected
        }

        try {
            r("org.graalvm;").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_MissingVersionFilter", ex.getMessage());
        }
        try {
            r("org.graalvm; unknown = aaa").parseRequiredCapabilities();
            fail("Should fail on unknown parameter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedParameters", ex.getMessage());
        }
        try {
            r("org.graalvm; unknown := aaa").parseRequiredCapabilities();
            fail("Should fail on unknown directive");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedDirectives", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := aaa").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := ()").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidParameterSyntax", ex.getMessage());
        }
    }

    @Test
    public void testBadProvideCapabilitySyntax() {
        try {
            r("org.graalvm; = \"CE\"; native_version:Version=\"19.3").parseProvidedCapabilities();
            fail("Should fail on invalid capability");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidCapabilityName", ex.getMessage());
        }
        try {
            r("org.graalvm; edition := \"CE\"").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidCapabilitySyntax", ex.getMessage());
        }
        try {
            r("org.graalvm; edition :;").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidCapabilitySyntax", ex.getMessage());
        }
        try {
            r("org.graalvm; edition : #=\"2\";").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidCapabilitySyntax", ex.getMessage());
        }
        try {
            r("org.graalvm; edition:Unknown=\"2\";").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedCapabilityType", ex.getMessage());
        }
        try {
            r("org.graalvm; edition").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidCapabilitySyntax", ex.getMessage());
        }
    }

    @Test
    public void testReadCapabilities() {
        Map<String, Object> caps = r("org.graalvm; edition = \"CE\"; native_version:Version=\"19.3\"").parseProvidedCapabilities();
        assertEquals(2, caps.size());
        assertEquals("CE", caps.get("edition"));
        assertNotNull(caps.get("native_version"));
        Version v = (Version) caps.get("native_version");
        assertEquals(Version.fromString("19.3"), v);
    }

    @Test
    public void testBadCapabilityUnsupportedType() {
        try {
            r("org.graalvm; edition:long=\"2\";").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedCapabilityType", ex.getMessage());
        }
        try {
            r("org.graalvm; edition:double=\"2\";").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedCapabilityType", ex.getMessage());
        }
        try {
            r("org.graalvm; edition:list=\"2\";").parseProvidedCapabilities();
            fail("Should fail on invalid capability syntax");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedCapabilityType", ex.getMessage());
        }
    }

    @Test
    public void testBadFilterSyntax() {
        try {
            r("org.graalvm; filter := \"()\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
    }

    @Test
    public void testBadFilterParenthesis() {
        try {
            r("org.graalvm; filter := \"(graalvm_version=0.32\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(graalvm_version=0.32)(whatever=111)\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"&(graalvm_version=0.32)(whatever=111)\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
    }

    @Test
    public void testInvalidFilterAttributeValues() {
        try {
            r("org.graalvm; filter := \"(&(graalvm_version=0.~32)(whatever=111))\"").parseRequiredCapabilities();
            fail("Should fail on invalid filter attribute value");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(&(graalvm_version=0.>32)(whatever=111))\"").parseRequiredCapabilities();
            fail("Should fail on invalid filter attribute value");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(&(graalvm_version=0.<32)(whatever=111))\"").parseRequiredCapabilities();
            fail("Should fail on invalid filter attribute value");
        } catch (MetadataException ex) {
            assertEquals("ERROR_InvalidFilterSpecification", ex.getMessage());
        }
    }

    @Test
    public void testInvalidFilterOperations() {
        try {
            r("org.graalvm; filter := \"(graalvm_version>0.32\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedFilterOperation", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(graalvm_version<0.32\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedFilterOperation", ex.getMessage());
        }
        try {
            r("org.graalvm; filter := \"(graalvm_version=0.32*eee\"").parseRequiredCapabilities();
            fail("Should fail on missing filter");
        } catch (MetadataException ex) {
            assertEquals("ERROR_UnsupportedFilterOperation", ex.getMessage());
        }
    }

    @Rule public ExpectedException exc = ExpectedException.none();

    @Test
    public void testFilterValueEscaping() {
        Map<String, String> attrs = r("org.graalvm; filter := \"(graalvm_version=0\\32)\"").parseRequiredCapabilities();
        assertEquals("0\\32", attrs.get("graalvm_version"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testFilterMutlipleValues() {
        Map m = r("org.graalvm; filter := \"(&(graalvm_version=0.32)(whatever=111))\"").parseRequiredCapabilities();
        assertEquals("0.32", m.get("graalvm_version"));
        assertEquals("111", m.get("whatever"));
    }

    @Test
    public void testFilterDuplicateValues() {
        exc.expect(MetadataException.class);
        exc.expectMessage("ERROR_DuplicateFilterAttribute");
        r("org.graalvm; filter := \"(&(graalvm_version=0.32)(graalvm_version=111))\"").parseRequiredCapabilities();
    }

    @Test
    public void testSimpleProvidedCapability() {
        Map<String, Object> v = c("org.graalvm; edition=ee; graalvm_version=ff").parseProvidedCapabilities();
        assertEquals("ee", v.get("edition"));
        assertEquals("ff", v.get("graalvm_version"));
    }

    @Test
    public void testSimpleProvidedCapabilityBad1() {
        exc.expect(MetadataException.class);
        exc.expectMessage("ERROR_InvalidCapabilitySyntax");
        c("org.graalvm; edition; graalvm_version=ff").parseProvidedCapabilities();
    }

    @Test
    public void testSimpleDependency() {
        assertTrue(d("").parseDependencies().isEmpty());
        Set<String> s = d("org.graalvm.llvm-toolchain").parseDependencies();
        assertEquals(1, s.size());
        assertEquals("org.graalvm.llvm-toolchain", s.iterator().next());
    }

    @Test
    public void testMultipleDependencies() {
        Set<String> s = d("org.graalvm.llvm-toolchain, org.graalvm.native-image").parseDependencies();
        assertEquals(2, s.size());
        assertTrue(s.contains("org.graalvm.llvm-toolchain"));
        assertTrue(s.contains("org.graalvm.native-image"));
    }

    @Test
    public void testRejectVersionedDependency() {
        exc.expect(MetadataException.class);
        exc.expectMessage("ERROR_DependencyParametersNotSupported");
        d("org.graalvm.llvm-toolchain; bundle-version=19.3").parseDependencies();
    }
}
