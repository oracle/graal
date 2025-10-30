#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
import json
import os
import re
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
from xml.dom import minidom


def generate_matrix(path_to_data, libs_per_job, delimiter):
    '''
    Generates a matrix in the format of GAV coordinate tuples (depending on the selected number of libraries per action job) for GitHub actions.
    '''
    try:
        with open(os.path.join(path_to_data, 'popular-maven-libraries.json'), 'r') as f:
            data = json.load(f)
        with open(os.path.join(path_to_data, 'excluded-popular-maven-libraries.json'), 'r') as f:
            exclude_data = json.load(f)
    except (FileNotFoundError, json.JSONDecodeError) as e:
        print(f"Error loading files: {e}")
        sys.exit(1)

    matrix = {'coordinates': []}
    excluded_coordinates = {f'{lib['group_id']}:{lib['artifact_id']}:{lib['version']}' for lib in exclude_data}
    libs_in_job = []
    for lib in data:
        lib_coordinates = f'{lib['group_id']}:{lib['artifact_id']}:{lib['version']}'
        if lib_coordinates in excluded_coordinates:
            continue
        libs_in_job.append(lib_coordinates)
        if len(libs_in_job) == libs_per_job:
            matrix['coordinates'].append(delimiter.join(libs_in_job))
            libs_in_job = []

    if len(libs_in_job) > 0:
        matrix['coordinates'].append(delimiter.join(libs_in_job))

    try:
        github_output = os.getenv('GITHUB_OUTPUT')
        if github_output is None:
            raise EnvironmentError("GITHUB_OUTPUT environment variable not set")
        with open(github_output, 'a') as f:
            f.write(f"matrix={json.dumps(matrix)}\n")
    except (IOError, EnvironmentError) as e:
        print(f"Error writing to GITHUB_OUTPUT: {e}")
        sys.exit(1)

def preserve_all(native_image_path, coordinates, delimiter):
    '''
    Builds a native image of the given library, given its GAV coordinates,
    while running the option -H:Preserve=module=ALL-UNNAMED, preserving everything on the classpath.

    The classpath contains all compile, runtime and transitive dependencies of the given library, while
    the image entry point is an empty main.

    If the image build fails with a "--initialize-at-build-time" error, retries the build with the additional
    "--initialize-at-build-time" argument until the build completes successfully, or a different error occurs.
    '''
    coordinates_list = coordinates.split(delimiter)

    for gav in coordinates_list:
        group_id, artifact_id, version = gav.rstrip().split(':')

        _generate_effective_pom(group_id, artifact_id, version)
        _generate_image_entry_point()

        classpath = subprocess.check_output(['mvn', '-q', 'exec:exec', '-Dexec.executable=echo', '-Dexec.args=%classpath']).decode('utf-8').strip()
        
        base_command = [
            native_image_path,
            '-J-ea',
            '-J-esa',
            '-H:+UnlockExperimentalVMOptions',
            '-cp', classpath,
            '-H:Preserve=module=ALL-UNNAMED',
            '-H:+ReportExceptionStackTraces',
            '--no-fallback',
            '-o', f'{artifact_id}-{version}',
            'DummyMain'
        ]

        initialize_args = set()

        while True:
            command = base_command + list(initialize_args)
            print("Running:", " ".join(command))

            proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

            output_lines = []
            for line in proc.stdout:
                print(line, end='')
                output_lines.append(line)

            proc.wait()
            output_str = "".join(output_lines)

            if proc.returncode == 0:
                print(f'Command: {' '.join(command)}')
                break

            matches = re.findall(r"--initialize-at-build-time=([\w.$]+)", output_str)
            new_args = {f"--initialize-at-build-time={m}" for m in matches}

            if new_args - initialize_args:
                initialize_args.update(new_args)
                print("Retrying with new args:", ", ".join(new_args))
            else:
                break

def _generate_effective_pom(group_id, artifact_id, version):
    '''
    Creates a dummy POM and iteratively expands it by adding all transitive dependencies
    discovered via `mvn dependency:list`.

    Dependencies are unique by (group_id, artifact_id), first version wins.
    '''
    dependencies = {(group_id, artifact_id): version}

    while True:
        _update_dependency_scopes(dependencies)
        _generate_pom(dependencies)
        
        dependency_list = subprocess.check_output(['mvn', '-B', 'dependency:list']).decode('utf-8').rstrip()

        detected_dependencies = _parse_mvn_dependency_list(dependency_list)
        before = len(dependencies)
        for (group_id, artifact_id, version) in detected_dependencies:
            key = (group_id, artifact_id)
            if key not in dependencies:
                dependencies[key] = version
        after = len(dependencies)

        if after == before:
            break

def _update_dependency_scopes(deps):
    '''
    Replaces the scopes of all provided scope dependencies with compile scope, so that
    the maven resolver doesn't omit them and their dependencies. Also makes all
    of the optional dependencies non-optional, so we don't omit them either.
    '''
    m2_repo = Path.home() / ".m2" / "repository"
    for (group_id, artifact_id), version in deps.items():
        path_parts = group_id.split(".") + [artifact_id, version]
        pom_path = m2_repo.joinpath(*path_parts, f"{artifact_id}-{version}.pom")
        if pom_path.exists():
            try:
                tree = ET.parse(pom_path)
                root = tree.getroot()
                ns = {"m": "http://maven.apache.org/POM/4.0.0"}
                ET.register_namespace('', ns["m"])
                for dependency in root.findall(".//m:dependency", ns):
                    scope = dependency.find("m:scope", ns)
                    if scope is not None and scope.text == "provided":
                        scope.text = "compile"
                    optional = dependency.find("m:optional", ns)
                    if optional is not None and optional.text == "true":
                        optional.text = "false"
                tree.write(pom_path, encoding="utf-8", xml_declaration=True)
            except Exception as e:
                print(f"Warning: failed to patch {pom_path}: {e}")
        else:
            print(f"Warning: POM not found for {group_id}:{artifact_id}:{version}")

def _generate_pom(dependencies):
    '''
    Writes a pom.xml file with the given dependencies (map of (group_id, artifact_id) -> version).
    '''
    project = ET.Element("project", {
        "xmlns": "http://maven.apache.org/POM/4.0.0",
        "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
        "xsi:schemaLocation": "http://maven.apache.org/POM/4.0.0 "
                              "https://maven.apache.org/xsd/maven-4.0.0.xsd"
    })
    ET.SubElement(project, "modelVersion").text = "4.0.0"
    ET.SubElement(project, "groupId").text = "example"
    ET.SubElement(project, "artifactId").text = "dummy-project"
    ET.SubElement(project, "version").text = "1.0-SNAPSHOT"

    dependencies_element = ET.SubElement(project, "dependencies")
    for (group_id, artifact_id), version in sorted(dependencies.items()):
        dependency_element = ET.SubElement(dependencies_element, "dependency")
        ET.SubElement(dependency_element, "groupId").text = group_id
        ET.SubElement(dependency_element, "artifactId").text = artifact_id
        ET.SubElement(dependency_element, "version").text = version
        ET.SubElement(dependency_element, "scope").text = "compile"

    xml_str = ET.tostring(project, encoding="utf-8")
    pretty_xml = minidom.parseString(xml_str).toprettyxml(indent="  ")
    pom_path = Path.cwd() / "pom.xml"
    pom_path.write_text(pretty_xml, encoding="utf-8")

def _parse_mvn_dependency_list(dependency_list):
    '''
    Parses mvn dependency:list output and returns a list of (group_id, artifact_id, version).
    Handles lines in the format:
        [INFO]    <group_id>:<artifact_id>:jar:<version>:<scope>
    '''
    dependencies = []
    pattern = re.compile(r'^\[INFO\]\s+([\w\.\-]+):([\w\.\-]+):[\w\.\-]+:([\w\.\-]+):[\w\.\-]+')
    for line in dependency_list.splitlines():
        line = line.strip()
        dependency = pattern.match(line)
        if dependency:
            group_id, artifact_id, version = dependency.groups()
            dependencies.append((group_id, artifact_id, version))
    return dependencies

def _generate_image_entry_point():
    '''
    Generates and compiles a Java class with an empty main method,
    placing it under target/classes of the current directory.
    '''
    target_dir = Path.cwd() / "target" / "classes"
    target_dir.mkdir(parents=True, exist_ok=True)

    dummy_main = target_dir / "DummyMain.java"
    dummy_main.write_text("""
    public class DummyMain {
        public static void main(String[] args) {}
    }
    """)

    subprocess.run(["javac", "-d", str(target_dir), str(dummy_main)], check=True)

if __name__ == '__main__':
    delimiter = " && "
    libs_per_job = 2
    if len(sys.argv) == 2:
        generate_matrix(sys.argv[1], libs_per_job, delimiter)
    elif len(sys.argv) == 3:
        preserve_all(sys.argv[1], sys.argv[2], delimiter)
    else:
        print("Error: Wrong number of arguments!")
        sys.exit(1)