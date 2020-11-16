/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as cp from 'child_process';
import * as utils from './utils';
import { registerLanguageServer } from './graalVMLanguageServer';
import { ConfigurationPickItem, getConf, getGVMConfig } from './graalVMConfiguration';

export const RUBY_LANGUAGE_SERVER_GEM_NAME: string = 'solargraph';
const INSTALL_RUBY_LANGUAGE_SERVER: string = 'Install Ruby Language Server';

export function getRubyConfigurations(): ConfigurationPickItem[] {
    const ret: ConfigurationPickItem[] = [];
    ret.push(new ConfigurationPickItem(
        'Set as Ruby runtime',
        '(ruby.interpreter.commandPath)',
        graalVMHome => {
            const executable = utils.findExecutable('ruby', graalVMHome);
            if (executable) {
                return utils.checkRecommendedExtension('rebornix.Ruby', 'Ruby Language') && executable !== getConf('ruby').get('interpreter.commandPath');
            }
            return false;
        }, 
        async graalVMHome => {
            const executable = utils.findExecutable('ruby', graalVMHome);
            if (executable) {
                return setConfig('interpreter.commandPath', executable);
            }
        })
    );
    return ret;
}

function setConfig(section: string, path: string) {
	const config = getConf('ruby');
	const term = config.inspect(section);
	if (term) {
		config.update(section, path, true);
	}
	const startRLS = getGVMConfig().get('languageServer.startRubyLanguageServer') as boolean;
	if (startRLS) {
		if (!isRubyGemInstalled(RUBY_LANGUAGE_SERVER_GEM_NAME)) {
			vscode.window.showInformationMessage('Solargraph gem is not installed in your GraalVM Ruby.', INSTALL_RUBY_LANGUAGE_SERVER).then(value => {
				switch (value) {
					case INSTALL_RUBY_LANGUAGE_SERVER:
						installRubyGem(RUBY_LANGUAGE_SERVER_GEM_NAME);
						break;
				}
			});
		} else {
            registerLanguageServer(() => startRubyLanguageServer());
		}
	}
}

function isRubyGemInstalled(name: string): boolean {
	const executable = utils.findExecutable('gem');
	if (executable) {
		try {
			const out = cp.execFileSync(executable, ['list', '-i', name], { encoding: 'utf8' });
			if (out.includes('true')) {
				return true;
			}
		} catch (err) {
			return false;
		}
	}
	return false;
}

export function installRubyGem(name: string): boolean {
	const executable = utils.findExecutable('gem');
	if (executable) {
		utils.runInTerminal(`${executable.replace(/(\s+)/g, '\\$1')} install ${name}`);
		return true;
	}
	return false;
}

function startRubyLanguageServer(): Thenable<string> {
	return new Promise<string>((resolve, reject) => {
		const executable = utils.findExecutable('solargraph');
		if (executable) {
			const child = cp.spawn(executable, ['socket', '--port', '0']);
			child.stderr.on('data', data => {
				var match = data.toString().match(/PORT=([0-9]*)[\s]+PID=([0-9]*)/);
				if (match) {
					const port = parseInt(match[1]);
					resolve(`ruby@${port}`);
				}
			});
			child.on('error', (err) => {
				reject(err);
			});
			child.on('exit', () => {
				reject(new Error("Solargraph exited"));
			});
		} else {
			reject(new Error("Cannot find 'solagraph' within your GraalVM installation."));
		}
	});
}
