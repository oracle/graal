#
# Copyright (c) 2016, 2024, Oracle and/or its affiliates.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors may be used to
# endorse or promote products derived from this software without specific prior written
# permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.
#
import argparse
import os

import mx
import mx_unittest
import mx_sulong
import mx_truffle

_suite = mx.suite('sulong')

class SulongTestConfig:
    def __init__(self, name, sulongHome, configRoot, runtimeDeps, resourceDeps, testConfigDep, nativeTestDistFrom):
        self.name = name
        self.sulongHome = sulongHome
        self.configRoot = configRoot
        self.runtimeDeps = runtimeDeps
        self.resourceDeps = resourceDeps
        self.testConfigDep = testConfigDep
        self.nativeTestDistFrom = nativeTestDistFrom

    def extra_vm_args(self):
        return []

_sulong_test_configs = {}

def register_sulong_test_config(cfg):
    name = cfg.name
    assert not name in _sulong_test_configs, 'duplicate sulong test config'
    _sulong_test_configs[name] = cfg


class NativeSulongTestConfig(SulongTestConfig):
    def __init__(self, name):
        super(NativeSulongTestConfig, self).__init__(name=name,
                                                     sulongHome="SULONG_HOME",
                                                     configRoot=os.path.join(_suite.dir, "tests", "configs"),
                                                     runtimeDeps=["SULONG_NATIVE"],
                                                     resourceDeps=["SULONG_NATIVE_RESOURCES"],
                                                     testConfigDep="SULONG_TEST",
                                                     nativeTestDistFrom=[_suite])

register_sulong_test_config(NativeSulongTestConfig("Native"))
register_sulong_test_config(NativeSulongTestConfig("AOTCacheLoadNative"))
register_sulong_test_config(NativeSulongTestConfig("AOTCacheStoreNative"))
register_sulong_test_config(NativeSulongTestConfig("AOTDebugCache"))
register_sulong_test_config(NativeSulongTestConfig("DryRun"))


def _get_test_distributions(suites):
    for s in suites:
        for d in s.dists:
            if d.is_test_distribution() and not d.isClasspathDependency():
                yield d

class SulongUnittestConfigBase(mx_unittest.MxUnittestConfig):
    sulongConfig = None
    useResources = None

    def apply(self, config, overrideSulongConfig=None):
        (vmArgs, mainClass, mainClassArgs) = config
        cfg = overrideSulongConfig or SulongUnittestConfigBase.sulongConfig
        # remove args from javaProperties
        vmArgs = [arg for arg in vmArgs if not arg.startswith('-Dorg.graalvm.language.llvm.home')]
        vmArgs += [f'-Dsulongtest.path.{d.name}={d.get_output()}' for d in _get_test_distributions(cfg.nativeTestDistFrom)]
        vmArgs += [f'-Dsulongtest.configRoot={cfg.configRoot}']
        vmArgs += [f'-Dsulongtest.config={cfg.name}']
        if '-p' in vmArgs or '--module-path' in vmArgs:
            # ALL-UNNAMED for native methods in
            # com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput
            native_access_target_module = 'org.graalvm.truffle,ALL-UNNAMED'
        else:
            native_access_target_module = 'ALL-UNNAMED'
        vmArgs += [f'--enable-native-access={native_access_target_module}']
        # GR-59703: Migrate sun.misc.* usages.
        mx_truffle.enable_sun_misc_unsafe(vmArgs)
        if mx.get_opts().use_llvm_standalone is not None:
            vmArgs += [f'-Dsulongtest.testAOTImage={mx_sulong.get_lli_path()}']
        else:
            if mx.suite('compiler', fatalIfMissing=False) is None:
                mx.warn("compiler suite not available, running Sulong unittests with -Dpolyglot.engine.WarnInterpreterOnly=false")
                vmArgs += ['-Dpolyglot.engine.WarnInterpreterOnly=false']
            if not SulongUnittestConfigBase.useResources:
                vmArgs += [f'-Dorg.graalvm.language.llvm.home={mx.distribution(cfg.sulongHome).get_output()}']
        vmArgs += cfg.extra_vm_args()
        return (vmArgs, mainClass, mainClassArgs)

    def processDeps(self, deps):
        cfg = SulongUnittestConfigBase.sulongConfig
        deps.add(mx.distribution(cfg.testConfigDep))
        if mx.get_opts().use_llvm_standalone is None:
            for d in cfg.runtimeDeps:
                deps.add(mx.distribution(d))
            if SulongUnittestConfigBase.useResources:
                for d in cfg.resourceDeps:
                    deps.add(mx.distribution(d))

# unittest config for Sulong tests that depend only on the embedders API
class SulongUnittestConfig(SulongUnittestConfigBase):
    def __init__(self):
        super(SulongUnittestConfig, self).__init__(name="sulong")

    def apply(self, config, **kwArgs):
        (vmArgs, mainClass, mainClassArgs) = super(SulongUnittestConfig, self).apply(config, **kwArgs)
        newVmArgs = []
        i = 0
        while i < len(vmArgs):
            # remove all --add-modules args
            if vmArgs[i] == "--add-modules":
                i += 2
            elif vmArgs[i].startswith("--add-modules="):
                i += 1
            else:
                newVmArgs.append(vmArgs[i])
                i += 1

        if mx.get_opts().use_llvm_standalone is None:
            # add back the embedders API if we're not testing a standalone
            newVmArgs.append("--add-modules=org.graalvm.polyglot")

        return (newVmArgs, mainClass, mainClassArgs)

# unittest config for Sulong tests that depend on sulong internals
class SulongInternalUnittestConfig(SulongUnittestConfigBase):
    def __init__(self):
        super(SulongInternalUnittestConfig, self).__init__(name="sulong-internal")

    def apply(self, config, **kwArgs):
        (vmArgs, mainClass, mainClassArgs) = super(SulongInternalUnittestConfig, self).apply(config, **kwArgs)
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED'])  # for TruffleRunner
        mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.llvm_community/*=ALL-UNNAMED'])  # for Sulong internals
        return (vmArgs, mainClass, mainClassArgs)


mx_unittest.register_unittest_config(SulongUnittestConfig())
mx_unittest.register_unittest_config(SulongInternalUnittestConfig())


class SelectSulongConfigAction(argparse.Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        SulongUnittestConfigBase.sulongConfig = _sulong_test_configs["Native"]
        super(SelectSulongConfigAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        if values in _sulong_test_configs:
            SulongUnittestConfigBase.sulongConfig = _sulong_test_configs[values]
        else:
            mx.abort(f"Sulong test config {values} unknown!")

class SulongResourceConfigAction(argparse.Action):
    def __init__(self, **kwargs):
        kwargs['required'] = False
        kwargs['nargs'] = 0
        SulongUnittestConfigBase.useResources = False
        super(SulongResourceConfigAction, self).__init__(**kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        SulongUnittestConfigBase.useResources = True

mx_unittest.add_unittest_argument('--sulong-config', default=None, help='Select test engine configuration for the sulong unittests.', metavar='<config>', action=SelectSulongConfigAction)
mx_unittest.add_unittest_argument('--sulong-test-resources', default=False, help='Run Sulong tests with resource jars instead of language home.', metavar='<config>', action=SulongResourceConfigAction)

# helper for `mx native-unittest`
def get_vm_args_for_native():
    cfg = SulongInternalUnittestConfig()
    (extraVmArgs, _, _) = cfg.apply(([], None, []), overrideSulongConfig=_sulong_test_configs["Native"])
    return extraVmArgs


class _LLVMNFITestConfig(mx_truffle.NFITestConfig):

    def __init__(self):
        super(_LLVMNFITestConfig, self).__init__('llvm', ['SULONG_NFI', 'SULONG_NATIVE'])

    def vm_args(self):
        testPath = mx.distribution('SULONG_NFI_TESTS').output
        sulongHome = mx.distribution('SULONG_HOME').output
        args = [
            '-Dnative.test.backend=llvm',
            '-Dnative.test.path.llvm=' + testPath,
            '-Dorg.graalvm.language.llvm.home=' + sulongHome
        ]
        return args


mx_truffle.register_nfi_test_config(_LLVMNFITestConfig())
