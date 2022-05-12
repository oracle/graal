package org.graalvm.compiler.truffle.compiler.hotspot.amd64;

import org.graalvm.compiler.hotspot.meta.HotSpotInvocationPluginProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.truffle.compiler.amd64.substitutions.AMD64TruffleInvocationPlugins;

import jdk.vm.ci.code.Architecture;

@ServiceProvider(HotSpotInvocationPluginProvider.class)
public class AMD64HotSpotTruffleInvocationPluginProvider implements HotSpotInvocationPluginProvider {

    @Override
    public void registerInvocationPlugins(Architecture architecture, InvocationPlugins plugins, Replacements replacements) {
        AMD64TruffleInvocationPlugins.register(architecture, plugins, replacements);
    }
}
