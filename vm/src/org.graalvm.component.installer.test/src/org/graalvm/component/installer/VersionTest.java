/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class VersionTest {
    @Test
    public void testNoVersionInfimum() throws Exception {
        Version otherNullVersion = Version.fromString(Version.NO_VERSION.toString());
        
        assertTrue(otherNullVersion.compareTo(Version.NO_VERSION) > 0);
        assertTrue(Version.NO_VERSION.compareTo(otherNullVersion) < 0);
        
        assertFalse(otherNullVersion.equals(Version.NO_VERSION));
        assertFalse(Version.NO_VERSION.equals(otherNullVersion));
    }

    @Test
    public void testNoVersionEqualToSelf() throws Exception {
        assertTrue(Version.NO_VERSION.compareTo(Version.NO_VERSION) == 0);
        assertTrue(Version.NO_VERSION.equals(Version.NO_VERSION));
    }
}
