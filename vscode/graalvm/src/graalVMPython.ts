/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as utils from './utils';
import { ConfigurationPickItem, getConf } from './graalVMConfiguration';

export function getPythonConfigurations(): ConfigurationPickItem[] {
    const ret: ConfigurationPickItem[] = [];
    ret.push(new ConfigurationPickItem(
        'Set as Python runtime',
        '(python.pythonPath)',
        graalVMHome => utils.findExecutable('graalpython', graalVMHome) !== undefined && utils.checkRecommendedExtension('ms-python.python', 'Python Language'),
        graalVMHome => utils.findExecutable('graalpython', graalVMHome) === getConf('python').get('pythonPath'),
        async graalVMHome => {
            const executable = utils.findExecutable('graalpython', graalVMHome);
            if (executable) {
                return setConfig('pythonPath', executable);
            }
        },
        async _graalVMHome => setConfig('pythonPath', undefined))
    );
    return ret;
}

function setConfig(section: string, path?: string) {
	const config = getConf('python');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
}
