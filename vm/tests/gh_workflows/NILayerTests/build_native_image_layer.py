#
# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import subprocess
import sys
from pathlib import Path

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

def build_layers(native_image_path, coordinates, delimiter):
    '''
    Builds native-image layers out of the given libraries, given their GAV coordinates and native-image path.

    Firstly, the function invokes a maven command to download the library jar with all it's transitive dependencies, given its GAV coordinates.
    After that, it invokes a maven command to get the full classpath of the given library.
    Finally, it runs a native-image command to build the native-image layer and prints out the used command for local testing in case of issues.
    '''
    coordinates_list = coordinates.split(delimiter)

    for gav in coordinates_list:
        currDir = os.getcwd()
        group_id, artifact_id, version = gav.rstrip().split(':')

        subprocess.run(['mvn', '-B', 'dependency:get', f'-Dartifact={gav}', '-Dtransitive=true'])

        library_path = os.path.join(Path.home(), '.m2', 'repository', group_id.replace('.','/'), artifact_id, version)
        jar_path = os.path.join(library_path, f'{artifact_id}-{version}.jar')
        subprocess.run(['cp', f'{os.path.join(library_path, f'{artifact_id}-{version}.pom')}', f'{os.path.join(library_path, 'pom.xml')}'])

        if Path(library_path).exists():
            subprocess.run(['mkdir', gav])
            os.chdir(gav)
            image_path = os.getcwd()
            os.chdir(library_path)
            dependency_path = subprocess.check_output(['mvn', '-q', 'exec:exec', '-Dexec.executable=echo', '-Dexec.args=%classpath']).decode('utf-8').rstrip()
            os.chdir(image_path)
            command = [
                    native_image_path,
                    '-J-ea', '-J-esa',
                    '--no-fallback',
                    '-cp' ,f'{jar_path}:{dependency_path}',
                    '-H:+UnlockExperimentalVMOptions',
                    f'-H:LayerCreate=layer.nil,path={jar_path}',
                    '-H:+ReportExceptionStackTraces',
                    '-o', f'lib-{artifact_id}-{version}'
            ]
            print(f'Command: {' '.join(command)}')
            subprocess.run(command, check=True)
            os.chdir('..')

        os.chdir(currDir)

if __name__ == '__main__':
    delimiter = " && "
    libs_per_job = 2
    if len(sys.argv) == 2:
        generate_matrix(sys.argv[1], libs_per_job, delimiter)
    elif len(sys.argv) == 3:
        build_layers(sys.argv[1], sys.argv[2], delimiter)
    else:
        print("Error: Wrong number of arguments!")
        sys.exit(1)
