/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer;

import java.util.function.Predicate;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public final class ComponentSpec implements Predicate<ComponentInfo> {

    public enum Match {
        EXACT,
        MIN
    }
    
    private final String  id;
    private final Version version;
    private final Match match;
    
    public ComponentSpec(ComponentInfo info) {
        this(info, Match.EXACT);
    }

    public ComponentSpec(ComponentInfo info, Match m) {
        this.id = info.getId();
        this.version = info.getVersion();
        this.match = m;
    }
    
    public ComponentSpec(String i, String vS) {
        this(i, Version.fromString(vS), Match.MIN);
    }
    
    public ComponentSpec(String i, Version vers, Match m) {
        this.id = i;
        this.version = vers;
        this.match = m;
    }

    public String getId() {
        return id;
    }

    public Version getVersion() {
        return version;
    }

    public Match getMatch() {
        return match;
    }
    
    @Override
    public boolean test(ComponentInfo t) {
        if (!id.equals(t.getId())) {
            return false;
        }
        Version v = t.getVersion();
        switch (match) {
            case EXACT:
                return v.equals(this.version);
            case MIN:
                return version.compareTo(v) <= 0;
        }
        return false;
    }
}
