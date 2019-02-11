/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.persist.test;

import org.graalvm.component.installer.ce.WebCatalog;

/**
 *
 * @author sdedic
 */
public class TestCatalog extends WebCatalog{

    @Override
    protected boolean acceptURLScheme(String scheme) {
        if ("test".equals(scheme)) {
            return true;
        }
        return super.acceptURLScheme(scheme);
    }
    
}
