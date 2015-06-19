#
# ----------------------------------------------------------------------------------------------------
#
# Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#
# ----------------------------------------------------------------------------------------------------
#
import mx, mx_graal, os
from argparse import ArgumentParser, REMAINDER


class Makefile:
    def __init__(self):
        self.rules = []
        self.definitions = []

    def add_rule(self, s):
        self.rules.append(s)

    def add_definition(self, s):
        self.definitions.append(s)

    def generate(self):
        return "\n\n".join(self.definitions + self.rules)


def build_makefile(args):
    """Creates a Makefile which is able to build distributions without mx

    The return value indicates how many files were modified"""
    parser = ArgumentParser(prog='mx makefile')
    parser.add_argument('-o', action='store', dest='output', help='Write contents to this file.')
    parser.add_argument('selectedDists', help="Selected distribution names which are going to be built with make.", nargs=REMAINDER)
    opts = parser.parse_args(args)

    if opts.selectedDists == None or len(opts.selectedDists) == 0:
        opts.selectedDists = [d.name for d in mx_graal._jdkDeployedDists if d.partOfHotSpot]
    mf = Makefile()
    commandline = " ".join(["mx.sh", "makefile"] + args)
    if do_build_makefile(mf, opts.selectedDists, commandline):
        contents = mf.generate()
        if opts.output == None:
            print contents
        else:
            if mx.update_file(opts.output, contents, showDiff=True):
                return 1
    return 0

def short_dist_name(name):
    return name.replace("COM_ORACLE_", "")

def filter_projects(deps, t):
    def typeFilter(project): # filters
        if isinstance(project, str):
            project = mx.dependency(project, True)
        return isinstance(project, t)
    return [d for d in deps if typeFilter(d)]

def get_jdk_deployed_dists():
    return [d.name for d in mx_graal._jdkDeployedDists]

def update_list(li, elements):
    for e in elements:
        if e not in li:
            li.append(e)

def make_dist_rule(dist, mf):
    def path_dist_relative(p):
        return os.path.relpath(p, dist.suite.dir)
    shortName = short_dist_name(dist.name)
    jdkDeployedDists = get_jdk_deployed_dists()
    jarPath = path_dist_relative(dist.path)
    sourcesVariableName = shortName + "_SRC"
    depJarVariableName = shortName + "_DEP_JARS"
    sources = []
    resources = []
    sortedDeps = dist.sorted_deps(True, transitive=False, includeAnnotationProcessors=True)
    projects = filter_projects(sortedDeps, mx.Project)
    targetPathPrefix = "$(TARGET)" + os.path.sep
    libraryDeps = [path_dist_relative(l.get_path(False)) for l in filter_projects(sortedDeps, mx.Library)]

    annotationProcessorDeps = []
    distDeps = dist.get_dist_deps(includeSelf=False, transitive=True)
    distDepProjects = []
    for d in distDeps:
        update_list(distDepProjects, d.sorted_deps(includeLibs=False, transitive=True))

    classPath = [targetPathPrefix + path_dist_relative(d.path) for d in distDeps] + libraryDeps \
        + [path_dist_relative(mx.dependency(name).path) for name in dist.excludedDependencies]
    for p in projects:
        if p.definedAnnotationProcessors != None and p.definedAnnotationProcessorsDist != dist:
            update_list(annotationProcessorDeps, [p])
    for p in projects:
        projectDir = path_dist_relative(p.dir)
        if p not in distDepProjects and p not in annotationProcessorDeps:
            for src in [projectDir + os.path.sep + d for d in p.srcDirs]:
                sources.append("$(shell find {} -type f 2> /dev/null)".format(src))
                metaInf = src + os.path.sep + "META-INF"
                if os.path.exists(metaInf):
                    resources.append(metaInf)


    sourceLines = sourcesVariableName + " = " + ("\n" + sourcesVariableName + " += ").join(sources)
    apPaths = []
    apDistNames = []
    apDistVariableNames = []
    for p in annotationProcessorDeps:
        apPaths.append(path_dist_relative(p.definedAnnotationProcessorsDist.path))
        name = short_dist_name(p.definedAnnotationProcessorsDist.name)
        apDistNames.append(name)
        apDistVariableNames.append("$(" + name + "_JAR)")
    shouldExport = dist.name in jdkDeployedDists
    props = {
           "name": shortName,
           "jarPath": targetPathPrefix + jarPath,
           "depJarsVariableAccess": "$(" + depJarVariableName + ")" if len(classPath) > 0 else "",
           "depJarsVariable": depJarVariableName,
           "sourceLines": sourceLines,
           "sourcesVariableName": sourcesVariableName,
           "annotationProcessors": " ".join(apDistVariableNames),
           "cpAnnotationProcessors": ":".join(apDistVariableNames),
           "jarDeps": " ".join(classPath),
           "copyResources": " ".join(resources)
           }

    mf.add_definition(sourceLines)
    mf.add_definition("{name}_JAR = {jarPath}".format(**props))
    if len(classPath) > 0: mf.add_definition("{depJarsVariable} = {jarDeps}".format(**props))
    if shouldExport: mf.add_definition("EXPORTED_FILES += $({name}_JAR)".format(**props))
    mf.add_rule("""$({name}_JAR): $({sourcesVariableName}) {annotationProcessors} {depJarsVariableAccess}
\t$(call build_and_jar,{cpAnnotationProcessors},$(subst  $(space),:,{depJarsVariableAccess}),{copyResources},$({name}_JAR))
""".format(**props))
    return



def do_build_makefile(mf, selectedDists, commandline):
    java = mx.java()
    bootClassPath = java.bootclasspath()
    bootClassPath = bootClassPath.replace(os.path.realpath(java.jdk), "$(ABS_BOOTDIR)")
    jdkBootClassPathVariableName = "JDK_BOOTCLASSPATH"

    mf.add_definition("""# This Makefile is generated automatically, do not edit

TARGET=.
# Bootstrap JDK to be used (for javac and jar)
ABS_BOOTDIR=

JAVAC=$(ABS_BOOTDIR)/bin/javac -g -target """ + str(java.javaCompliance) + """
JAR=$(ABS_BOOTDIR)/bin/jar

HS_COMMON_SRC=.

# Directories, where the generated property-files reside within the JAR files
PROVIDERS_INF=/META-INF/jvmci.providers
SERVICES_INF=/META-INF/jvmci.services
OPTIONS_INF=/META-INF/jvmci.options

JARS = $(foreach dist,$(DISTRIBUTIONS),$($(dist)_JAR))

ifeq ($(ABS_BOOTDIR),)
    $(error Variable ABS_BOOTDIR must be set to a JDK installation.)
endif
ifeq ($(MAKE_VERBOSE),)
    QUIETLY=@
endif

# Required to construct a whitespace for use with subst
space :=
space +=

# Takes the provider files created by ServiceProviderProcessor (the processor
# for the @ServiceProvider annotation) and merges them into a single file.
# Arguments:
#  1: directory with contents of the JAR file
define process_providers
    $(eval providers := $(1)/$(PROVIDERS_INF))
    $(eval services := $(1)/$(SERVICES_INF))
    $(QUIETLY) test -d $(services) || mkdir -p $(services)
    $(QUIETLY) test ! -d $(providers) || (cd $(providers) && for i in $$(ls); do c=$$(cat $$i); echo $$i >> $(abspath $(services))/$$c; rm $$i; done)

    @# Since all projects are built together with one javac call we cannot determine
    @# which project contains HotSpotVMConfig.inline.hpp so we hardcode it.
    $(eval vmconfig := $(1)/hotspot/HotSpotVMConfig.inline.hpp)
    $(eval vmconfigDest := $(HS_COMMON_SRC)/../jvmci/com.oracle.jvmci.hotspot/src_gen/hotspot)
    $(QUIETLY) test ! -f $(vmconfig) || (mkdir -p $(vmconfigDest) && cp $(vmconfig) $(vmconfigDest))
endef

# Reads the files in jvmci.options/ created by OptionProcessor (the processor for the @Option annotation)
# and appends to services/com.oracle.jvmci.options.Options entries for the providers
# also created by the same processor.
# Arguments:
#  1: directory with contents of the JAR file
define process_options
    $(eval options := $(1)/$(OPTIONS_INF))
    $(eval services := $(1)/META-INF/services)
    $(QUIETLY) test -d $(services) || mkdir -p $(services)
    $(QUIETLY) test ! -d $(options) || (cd $(options) && for i in $$(ls); do echo $${i}_Options >> $(abspath $(services))/com.oracle.jvmci.options.Options; done)
endef

# Extracts META-INF/jvmci.services and META-INF/jvmci.options of a JAR file into a given directory
# Arguments:
#  1: JAR file to extract
#  2: target directory (which already exists)
define extract
    $(eval TMP := $(shell mktemp -d $(TARGET)/tmp_XXXXX))
    $(QUIETLY) cd $(TMP) && $(JAR) xf $(abspath $(1)) && \\
        ((test ! -d .$(SERVICES_INF) || cp -r .$(SERVICES_INF) $(abspath $(2))) && \\
         (test ! -d .$(OPTIONS_INF) || cp -r .$(OPTIONS_INF) $(abspath $(2))));
    $(QUIETLY) rm -r $(TMP);
    $(QUIETLY) cp $(1) $(2)
endef

# Calls $(JAVAC) with the boot class path $(JDK_BOOTCLASSPATH) and sources taken from the automatic variable $^
# Arguments:
#  1: processorpath
#  2: classpath
#  3: resources to copy
#  4: target JAR file
define build_and_jar
    $(info Building $(4))
    $(eval TMP := $(shell mkdir -p $(TARGET) && mktemp -d $(TARGET)/tmp_XXXXX))
    $(QUIETLY) $(JAVAC) -d $(TMP) -processorpath :$(1) -bootclasspath $(JDK_BOOTCLASSPATH) -cp :$(2) $(filter %.java,$^)
    $(QUIETLY) test "$(3)" = "" || cp -r $(3) $(TMP)
    $(QUIETLY) $(call process_options,$(TMP))
    $(QUIETLY) $(call process_providers,$(TMP))
    $(QUIETLY) mkdir -p $(shell dirname $(4))
    $(QUIETLY) $(JAR) -0cf $(4) -C $(TMP) .
    $(QUIETLY) rm -r $(TMP)
endef

# Verifies that make/defs.make contains an appropriate line for each JVMCI service or option
# and that only existing JVMCI services and options are exported.
# Arguments:
#  1: list of service or option files
#  2: variable name for directory of service or option files
define verify_defs_make
    $(eval defs := make/defs.make)
    $(eval uncondPattern := EXPORT_LIST += $$$$($(2))/)
    $(eval condPattern := CONDITIONAL_EXPORT_LIST += $$$$($(2))/)
    $(eval unconditionalExports := $(shell grep '^EXPORT_LIST += $$($2)' make/defs.make | sed 's:.*($(2))/::g'))
    $(eval conditionalExports := $(shell grep '^CONDITIONAL_EXPORT_LIST += $$($2)' make/defs.make | sed 's:.*($(2))/::g'))
    $(eval allExports := $(unconditionalExports) $(conditionalExports))
    $(foreach file,$(1),$(if $(findstring $(file),$(allExports)), ,$(error "Line matching '$(uncondPattern)$(file)' or '$(condPattern)$(file)' not found in $(defs)")))
    $(foreach export,$(unconditionalExports),$(if $(findstring $(export),$(1)), ,$(error "The line '$(uncondPattern)$(export)' should not be in $(defs)")))
endef

all: default
\t$(info Put $(EXPORTED_FILES) into SHARED_DIR $(SHARED_DIR))
\t$(shell mkdir -p $(SHARED_DIR))
\t$(foreach export,$(EXPORTED_FILES),$(call extract,$(export),$(SHARED_DIR)))

export: all
\t$(call verify_defs_make,$(notdir $(wildcard $(SHARED_DIR)/jvmci.services/*)),EXPORT_JRE_LIB_JVMCI_SERVICES_DIR)
\t$(call verify_defs_make,$(notdir $(wildcard $(SHARED_DIR)/jvmci.options/*)),EXPORT_JRE_LIB_JVMCI_OPTIONS_DIR)
.PHONY: export

clean:
\t$(QUIETLY) rm $(JARS) 2> /dev/null || true
\t$(QUIETLY) rmdir -p $(dir $(JARS)) 2> /dev/null || true
.PHONY: export clean

""")
    s = mx.suite("graal")
    dists = []
    ap = []
    projects = []
    for d in s.dists:
        if d.name in selectedDists:
            update_list(dists, d.get_dist_deps(True, True))
            update_list(projects, d.sorted_deps(includeLibs=False, transitive=True))

    for p in projects:
        deps = p.all_deps([], False, includeSelf=True, includeJreLibs=False, includeAnnotationProcessors=True)
        for d in deps:
            if d.definedAnnotationProcessorsDist != None:
                apd = d.definedAnnotationProcessorsDist
                update_list(ap, [apd])

    if len(dists) > 0:
        mf.add_definition(jdkBootClassPathVariableName + " = " + bootClassPath)
        for d in ap: make_dist_rule(d, mf)
        for d in dists: make_dist_rule(d, mf)
        mf.add_definition("DISTRIBUTIONS = " + " ".join([short_dist_name(d.name) for d in dists+ap]))
        mf.add_rule("default: $({}_JAR)\n.PHONY: default\n".format("_JAR) $(".join([short_dist_name(d.name) for d in dists])))
        return True
    else:
        for d in dists:
            selectedDists.remove(d.name)
        print "Distribution(s) '" + "', '".join(selectedDists) + "' does not exist."
