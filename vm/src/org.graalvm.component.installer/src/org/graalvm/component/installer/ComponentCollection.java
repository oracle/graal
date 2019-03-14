/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import org.graalvm.component.installer.model.ComponentInfo;

/**
 * A facade that represents a component collection. 
 * @author sdedic
 */
public interface ComponentCollection {
    public String shortenComponentId(ComponentInfo info);
}
