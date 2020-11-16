/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';

import { checkForMissingComponents, getInstallConfigurations } from './graalVMInstall';
import { getPythonConfigurations } from './graalVMPython';
import { getRConfigurations } from './graalVMR';
import { getRubyConfigurations } from './graalVMRuby';

let configurations: ConfigurationPickItem[];

export function getConf(key: string): vscode.WorkspaceConfiguration {
	return vscode.workspace.getConfiguration(key);
}

export function getGVMConfig(gvmConfig?: vscode.WorkspaceConfiguration): vscode.WorkspaceConfiguration {
	if (!gvmConfig) {
		gvmConfig = getConf('graalvm');
	}
	return gvmConfig;
}

export function getGVMHome(gvmConfig?: vscode.WorkspaceConfiguration): string {
	return getGVMConfig(gvmConfig).get('home') as string;
}

export async function setGVMHome(graalVMHome: string | undefined, gvmConfig?: vscode.WorkspaceConfiguration): Promise<void> {
	return getGVMConfig(gvmConfig).update('home', graalVMHome, true);
}

const CONFIG_INSTALLATIONS = 'installations';
export function getGVMInsts(gvmConfig?: vscode.WorkspaceConfiguration): string[] {
	return getGVMConfig(gvmConfig).get(CONFIG_INSTALLATIONS) as string[] || [];
}

export function setGVMInsts(gvmConfig: vscode.WorkspaceConfiguration, installations: string[]): Thenable<void> {
	return gvmConfig.update(CONFIG_INSTALLATIONS, installations, true);
}

export function dist(): string {
    if (process.platform === 'linux') {
        return 'linux';
    } else if (process.platform === 'darwin') {
        return 'mac';
    } else if (process.platform === 'win32') {
        return 'windows';
    }
    return 'undefined';
}

const TERMINAL_INTEGRATED: string = 'terminal.integrated';

export function getTerminalEnvName(): string {
    return `${TERMINAL_INTEGRATED}.env.${dist()}`;
}

export function getTerminalEnv(): any {
    return getConf(TERMINAL_INTEGRATED).get(`env.${dist()}`) as any | {};
}

export async function setTerminalEnv(env: any): Promise<any> {
    return getConf(TERMINAL_INTEGRATED).update(`env.${dist()}`, env, true);
}

export function setupProxy() {
    const http = getConf('http');
    const proxy = http.get('proxy') as string;
    vscode.window.showInputBox(
        {
            prompt: 'Input proxy settings.',
            placeHolder: '<http(s)>://<host>:<port>',
            value: proxy
        }
    ).then(async out => {
        if (proxy !== out) {
            await http.update('proxy', out, true);
            await http.update('proxySupport', out ? 'off' : 'on', true);
            await vscode.commands.executeCommand('extension.graalvm.refreshInstallations');
        }
    });
}

export async function checkGraalVMconfiguration(graalVMHome: string) {
    gatherConfigurations();
    for (const conf of configurations) {
        if (!conf.show(graalVMHome) && conf.setted(graalVMHome)) {
            try {
                await conf.unset(graalVMHome);
            } catch (_err) {}
        }
    }
}

export async function configureGraalVMHome(graalVMHome: string, nonInteractive?: boolean) {
    const gr = getGVMConfig();
    const oldGVM = getGVMHome(gr);
    if (graalVMHome !== oldGVM) {
        await removeConfigurations(oldGVM);
        await defaultConfig(graalVMHome, gr);
    }
    if (!nonInteractive) {
        await configureInteractive(graalVMHome);
    }
}

export async function removeGraalVMconfiguration(graalVMHome: string) {
    await removeDefaultConfigurations(graalVMHome);
    await removeConfigurations(graalVMHome);
}

async function removeDefaultConfigurations(graalVMHome: string) {
    const gr = getGVMConfig();
    const installations = getGVMInsts(gr);
    const index = installations.indexOf(graalVMHome);
    if (index > -1) {
        installations.splice(index, 1);
        await setGVMInsts(gr, installations);
    }
    const home = getGVMHome(gr);
    if (home === graalVMHome) {
        await setGVMHome(undefined, gr);
    }
    const env = getTerminalEnv();
    if (env) {
        if (env.GRAALVM_HOME === graalVMHome) {
            env.GRAALVM_HOME = undefined;
        }
        await setTerminalEnv(env);
    }
    try {
        const nbConf = getConf('netbeans');
        const nbHome = nbConf.get('jdkhome') as string;
        if (nbHome === graalVMHome) {
            await nbConf.update('jdkhome', undefined, true);
        }
    } catch(_err) {}
}

async function removeConfigurations(graalVMHome: string) {
    gatherConfigurations();
    for (const conf of configurations) {
        if (conf.setted(graalVMHome)) {
            try {
                await conf.unset(graalVMHome);
            } catch (_err) {}
        }
    }
}

async function configureInteractive(graalVMHome: string) {
    checkForMissingComponents(graalVMHome);
    gatherConfigurations();
    const toShow: ConfigurationPickItem[] = configurations.filter(conf => {
        const show = conf.show(graalVMHome);
        if (show) {
            conf.picked = conf.setted(graalVMHome);
        }
        return show;
    });
    if (toShow.length > 0) {
        const selected: ConfigurationPickItem[] | undefined = await vscode.window.showQuickPick(
            toShow, {
                canPickMany: true,
                placeHolder: 'Configure active GraalVM'
            });
        if (selected) {
            for (const shown of toShow) {
                try {
                    if (selected.includes(shown)) {
                        await shown.set(graalVMHome);
                    } else {
                        await shown.unset(graalVMHome);
                    }
                } catch (error) {
                    vscode.window.showErrorMessage(error?.message);
                }
            }
        }
    }
}

function gatherConfigurations() {
    if (configurations) {
        return;
    }
    configurations = getInstallConfigurations().concat(
        getPythonConfigurations(), 
        getRubyConfigurations(),
        getRConfigurations());
}

async function defaultConfig(graalVMHome: string, gr: vscode.WorkspaceConfiguration) {
    await setGVMHome(graalVMHome, gr);
    const insts = getGVMInsts(gr);
    if (!insts.includes(graalVMHome)) {
        insts.push(graalVMHome);
        await setGVMInsts(gr, insts);
    }

    try {
        await getConf('netbeans').update('jdkhome', graalVMHome, true);
    } catch (error) {}

    let env: any = getTerminalEnv();
    env.GRAALVM_HOME = graalVMHome;
    await setTerminalEnv(env);
}

export class ConfigurationPickItem implements vscode.QuickPickItem {
    public picked?: boolean;
    public detail?: string;
	constructor (
        public readonly label: string,
        public readonly description: string,
        public readonly show: (graalVMHome: string) => boolean,
        public readonly setted: (graalVMHome: string) => boolean,
        public readonly set: ((graalVMHome: string) => Promise<any>),
        public readonly unset: ((graalVMHome: string) => Promise<any>)
	) {}
}