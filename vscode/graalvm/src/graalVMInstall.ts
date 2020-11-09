/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as https from 'https';
import * as cp from 'child_process';
import * as decompress from 'decompress';
import * as utils from './utils';
import { basename, dirname, join, normalize, delimiter } from 'path';
import { LicenseCheckPanel } from './graalVMLicenseCheck';
import { ConfigurationPickItem, getGVMHome, getConf, getGVMConfig, setGVMHome, configureGraalVMHome, getGVMInsts, setGVMInsts, setupProxy } from './graalVMConfiguration';

const GITHUB_URL: string = 'https://github.com';
const GRAALVM_RELEASES_URL: string = GITHUB_URL + '/graalvm/graalvm-ce-builds/releases';
const GRAALVM_DEV_RELEASES_URL: string = GITHUB_URL + '/graalvm/graalvm-ce-dev-builds/releases';
const GDS_URL: string = 'https://oca.opensource.oracle.com/gds/meta-data.json';
const LINUX_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-linux-amd64-\S*"/gmi;
const MAC_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-(darwin|macos)-amd64-\S*"/gmi;
const WINDOWS_LINK_REGEXP: RegExp = /<a href="\/graalvm\/graalvm-\S*-builds\/releases\/download\/\S*\/graalvm-ce-java\S*-windows-amd64-\S*"/gmi;
const INSTALL: string = 'Install ';
const OPTIONAL_COMPONENTS: string = 'Optional GraalVM Components';
const GRAALVM_EE_LICENSE: string = 'GraalVM Enterprise Edition License';

export async function installGraalVM(context: vscode.ExtensionContext): Promise<void> {
    try {
        const selected = await selectGraalVMRelease(context);
        if (selected) {
            const downloadedFile = await dowloadGraalVMRelease(selected.url, selected.location);
            const targetDir = dirname(downloadedFile);
            const name = await extractGraalVM(downloadedFile, targetDir);
            fs.unlinkSync(downloadedFile);
            if (name) {
                let graalVMHome = join(targetDir, name);
                if (process.platform === 'darwin') {
                    graalVMHome = join(graalVMHome, 'Contents', 'Home');
                }
                updateGraalVMLocations(graalVMHome);
                checkForMissingComponents(graalVMHome);
            }
        }
    } catch (err) {
        vscode.window.showErrorMessage(err.message);
    }
}

export async function removeGraalVMInstallation(homeFolder?: string): Promise<number> {
    if (!homeFolder) {
        const insts = getGVMInsts();
        homeFolder = await _selectInstalledGraalVM(vm => insts.includes(vm.path));
    }
    const graalFolder = homeFolder;
    if (!graalFolder) {
        return -1;
    }
    const index = await _removeGraalVMInstallation(graalFolder);
    return utils.askYesNo(`Do you want to delete GraalVM installation files from: ${graalFolder}`, () => deleteFolder(graalFolder)).then(() => index);
}

export async function installGraalVMComponent(component: string | Component, homeFolder?: string, context?: vscode.ExtensionContext): Promise<void> {
    _callIdGVMHome(component, homeFolder, context, _installGraalVMComponent);
}

export async function uninstallGraalVMComponent(component: string | Component, homeFolder?: string): Promise<void> {
    _callIdGVMHome(component, homeFolder, undefined, _uninstallGraalVMComponent);
}

export async function addExistingGraalVM(): Promise<void> {
    const uri = await vscode.window.showOpenDialog({
        canSelectMany: false,
        canSelectFiles: false,
        canSelectFolders: true,
        openLabel: 'Add GraalVM',
        title: 'Select GraalVM Directory'
    });
    if (uri && uri.length === 1) {
        const graalVMHome = uri[0].fsPath;
        if (graalVMHome) {
            updateGraalVMLocations(graalVMHome);
            checkForMissingComponents(graalVMHome);
        }
    } else {
        throw new Error('No GraalVM Installation selected.');
    }
}

export async function selectInstalledGraalVM(graalVMHome?: string, nonInteractive?: boolean): Promise<void> {
    graalVMHome = graalVMHome || await _selectInstalledGraalVM();
    if (graalVMHome) {
        const graalVMVersion = await getGraalVMVersion(graalVMHome);
        if (graalVMVersion) {
            configureGraalVMHome(graalVMHome, nonInteractive);
        }
    }
}

export async function findGraalVMs(): Promise<{name: string, path: string}[]> {
    const paths: string[] = [];
    addPathToJava(normalize(getGVMHome()), paths);
    const installations = getGVMInsts().map(inst => normalize(inst));
    installations.forEach(installation => addPathToJava(installation, paths, true));
    addPathsToJavaIn('/opt', paths);
    if (process.env.GRAALVM_HOME) {
        addPathToJava(normalize(process.env.GRAALVM_HOME), paths);
    }
    if (process.env.JAVA_HOME) {
        addPathToJava(normalize(process.env.JAVA_HOME), paths);
    }
    if (process.env.PATH) {
        process.env.PATH.split(delimiter)
            .filter(p => basename(p) === 'bin')
            .forEach(p => addPathToJava(dirname(p), paths));
    }
    const vms: {name: string, path: string}[] = [];
    for (let i = 0; i < paths.length; i++) {
        const version = await getGraalVMVersion(paths[i]);
        if (version) {
            vms.push({name: version, path: paths[i]});
        }
    }
    return vms;
}

export async function getGraalVMVersion(homeFolder: string): Promise<string | undefined> {
    return new Promise<string | undefined>(resolve => {
        if (homeFolder && fs.existsSync(homeFolder)) {
            const executable: string | undefined = utils.findExecutable('java', homeFolder);
            if (executable) {
                cp.execFile(executable, ['-version'], { encoding: 'utf8' }, (_error, _stdout, stderr) => {
                    if (stderr) {
                        let javaVersion: string | undefined;
                        let graalVMVersion: string | undefined;
                        stderr.split('\n').forEach((line, idx) => {
                            switch (idx) {
                                case 0:
                                    const javaInfo: string[] | null = line.match(/version\s+\"(\S+)\"/);
                                    if (javaInfo && javaInfo.length > 1) {
                                        javaVersion = javaInfo[1];
                                    }
                                    break;
                                case 2:
                                    const vmInfo = line.match(/(GraalVM.*)\s+\(/);
                                    if (vmInfo && vmInfo.length > 1) {
                                        graalVMVersion = vmInfo[1];
                                    }
                                    break;
                            }
                        });
                        if (javaVersion && graalVMVersion) {
                            if (javaVersion.startsWith('1.')) {
                                javaVersion = javaVersion.slice(2);
                            }
                            let i = javaVersion.indexOf('.');
                            javaVersion = javaVersion.slice(0, i);
                            resolve(`${graalVMVersion}, Java ${javaVersion}`);
                        } else {
                            resolve();
                        }
                    } else {
                        resolve();
                    }
                });
            } else {
                resolve();
            }
        } else {
            resolve();
        }
    });
}

export function getInstallConfigurations(): ConfigurationPickItem[] {
    const ret: ConfigurationPickItem[] = [];

    ret.push(new ConfigurationPickItem(
        'Set as default Java',
        '(java.home)',
        graalVMHome => {
            if (!vscode.extensions.getExtension('redhat.java')) {
                return false;
            }
            return getConf('java').get('home') !== graalVMHome;
        }, 
        async graalVMHome => {
            getConf('java').update('home', graalVMHome, true);
        })
    );
    
    let section: string = `${TERMINAL_INTEGRATED}.env.${dist()}`;
    ret.push(new ConfigurationPickItem(
        'Set as Java for Terminal',
        `(JAVA_HOME in ${section})`,
        graalVMHome => getTerminalEnv().JAVA_HOME !== graalVMHome, 
        async graalVMHome => {
            const env: any = getTerminalEnv();
            env.JAVA_HOME = graalVMHome;
            return setTerminalEnv(env);
        }
    ));
    
    ret.push(new ConfigurationPickItem(
        'Set as Java for Terminal',
        `(PATH in ${section})`,
        graalVMHome => {
            const env: any = getTerminalEnv();
            const path = env.PATH as string;
            return !path?.startsWith(join(graalVMHome, 'bin'));
        }, 
        async graalVMHome => {
            const env: any = getTerminalEnv();
            const path = env.PATH as string;
            const graalVMPath = join(graalVMHome, 'bin');
            if (path) {
                const paths = path.split(delimiter);
                const index = paths.indexOf(graalVMPath);
                if (index >= 0) {
                    paths.splice(index, 1);
                    paths.unshift(graalVMPath);
                    env.PATH = paths.join(delimiter);
                } else {
                    env.PATH = `${graalVMPath}${delimiter}${path}`;
                }
            } else {
                env.PATH = `${graalVMPath}${delimiter}${process.env.PATH}`;
            }
            return setTerminalEnv(env);
        }
    ));

    ret.push(new ConfigurationPickItem(
        'Set as Java for Maven',
        '(JAVA_HOME in maven.terminal.customEnv)',
        graalVMHome => {
            if (!vscode.extensions.getExtension('vscjava.vscode-maven')) {
                return false;
            }
            const envs = getConf('maven').get('terminal.customEnv') as [];
            return envs ? envs.find(env => env["environmentVariable"] === "JAVA_HOME" && env["value"] === graalVMHome) === undefined : true;
        }, 
        async graalVMHome => getConf('maven').update('terminal.customEnv', [{environmentVariable: "JAVA_HOME", value: graalVMHome}], true))
    );
    return ret;
}

export async function checkForMissingComponents(homeFolder: string): Promise<void> {
    const available = await getAvailableComponents(homeFolder);
    const components = available.filter(availableItem => !availableItem.installed);
    if (components.length > 1) {
        const itemText = INSTALL + OPTIONAL_COMPONENTS;
        return utils.ask('Optional GraalVM components are not installed in your GraalVM.', [
            {option: itemText, fnc: () => vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', undefined, homeFolder)}
        ]);
    } else if (components.length === 1) {
        const itemText = INSTALL + components[0].detail;
        return utils.ask(components[0].detail + ' is not installed in your GraalVM.', [
            {option: itemText, fnc: () => vscode.commands.executeCommand('extension.graalvm.installGraalVMComponent', components[0].label, homeFolder)}
        ]);
    }
}

function dist(): string {
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
function getTerminalEnv(): any {
    return getConf(TERMINAL_INTEGRATED).get(`env.${dist()}`) as any | {};
}

async function setTerminalEnv(env: any): Promise<any> {
    return getConf(TERMINAL_INTEGRATED).update(`env.${dist()}`, env, true);
}

async function _selectInstalledGraalVM(filter?: (vm: {name: string, path: string}) => boolean): Promise<string | undefined>{
    const vms: {label: string, detail: string}[] = (filter ? (await findGraalVMs()).filter(filter) : await findGraalVMs()).map(item => {
        return {label: item.name, detail: item.path};
    });
    const selected = await vscode.window.showQuickPick(vms, { matchOnDetail: true, placeHolder: 'Select GraalVM' });
    return selected?.detail;
}

async function selectGraalVMRelease(context: vscode.ExtensionContext): Promise<{url: string, location: string} | undefined> {

    interface State {
		graalVMDistribution: vscode.QuickPickItem;
		graalVMVersion: vscode.QuickPickItem;
		javaVersion: vscode.QuickPickItem;
	}

	async function collectInputs() {
		const state = {} as Partial<State>;
		await utils.MultiStepInput.run(input => pickGraalVMDistribution(input, state));
		return state as State;
	}

	const title = 'Download & Install GraalVM';
    let totalSteps = 3;
    let releaseInfos: any;

	async function pickGraalVMDistribution(input: utils.MultiStepInput, state: Partial<State>) {
		state.graalVMDistribution = await input.showQuickPick({
			title,
			step: 1,
			totalSteps,
			placeholder: 'Pick GraalVM distribution',
			items: [
                { label: 'Community', description: '(Free for all purposes)' },
                { label: 'Enterprise', description: '(Free for evaluation and development)' }
            ],
            activeItem: state.graalVMDistribution,
            postProcess: async item => releaseInfos = await (item.label === 'Enterprise' ? getGraalVMEEReleases() : getGraalVMCEReleases()),
			shouldResume: () => Promise.resolve(false)
        });
		return (input: utils.MultiStepInput) => pickGraalVMVersion(input, state);
	}

	async function pickGraalVMVersion(input: utils.MultiStepInput, state: Partial<State>) {
		state.graalVMVersion = await input.showQuickPick({
			title,
			step: 2,
			totalSteps,
			placeholder: 'Pick a GraalVM version',
			items: Object.keys(releaseInfos).map(label => ({ label })),
			activeItem: state.graalVMVersion,
			shouldResume: () => Promise.resolve(false)
		});
		return (input: utils.MultiStepInput) => pickJavaVersion(input, state);
	}

	async function pickJavaVersion(input: utils.MultiStepInput, state: Partial<State>) {
		state.javaVersion = await input.showQuickPick({
			title,
			step: 3,
			totalSteps,
			placeholder: 'Pick a Java version',
			items: state.graalVMVersion ? Object.keys(releaseInfos[state.graalVMVersion.label]).map(label => ({ label })) : [],
			activeItem: state.javaVersion,
			shouldResume: () => Promise.resolve(false)
		});
	}

    const state = await collectInputs();

    if (state.graalVMDistribution && state.graalVMVersion && state.javaVersion) {
        let accepted;
        if (state.graalVMDistribution.label === 'Enterprise') {
            const license = await get(releaseInfos[state.graalVMVersion.label][state.javaVersion.label].license, /^text\/plain/);
            const licenseLabel = releaseInfos[state.graalVMVersion.label][state.javaVersion.label].licenseLabel;
            accepted = await LicenseCheckPanel.show(context, licenseLabel, license.split('\n').join('<br>'));
        } else {
            accepted = true;
        }
        if (accepted) {
            const location: vscode.Uri[] | undefined = await vscode.window.showOpenDialog({
                canSelectFiles: false,
                canSelectFolders: true,
                canSelectMany: false,
                title: 'Choose Installation Directory',
                openLabel: 'Install Here'
            });
            if (location && location.length > 0) {
                return { url: releaseInfos[state.graalVMVersion.label][state.javaVersion.label].url, location: location[0].fsPath };
            }
        }
    }

    return undefined;
}

async function dowloadGraalVMRelease(releaseURL: string, storagePath: string | undefined): Promise<string> {
    const base: string = basename(releaseURL);
    return vscode.window.withProgress<string>({
        location: vscode.ProgressLocation.Notification,
        title: `Downloading ${base} ...`,
        cancellable: true
    }, (progress, token) => {
        return new Promise<string>((resolve, reject) => {
            if (storagePath) {
                fs.mkdirSync(storagePath, {recursive: true});
                const filePath: string = join(storagePath, base);
                const file: fs.WriteStream = fs.createWriteStream(filePath);
                const request = function (url: string) {
                    https.get(url, res => {
                        const { statusCode } = res;
                        if (statusCode === 302) {
                            if (res.headers.location) {
                                request(res.headers.location);
                            }
                        } else {
                            let error;
                            const contentType = res.headers['content-type'] || '';
                            const length = parseInt(res.headers['content-length'] || '0');
                            if (statusCode !== 200) {
                                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
                            } else if (!/^application\/(octet-stream|x-gtar|zip)/.test(contentType)) {
                                error = new Error(`Invalid content-type received ${contentType}`);
                            }
                            if (error) {
                                reject(error);
                                res.resume();
                            } else {
                                token.onCancellationRequested(() => {
                                    reject();
                                    res.destroy();
                                    fs.unlinkSync(filePath);
                                });
                                res.pipe(file);
                                if (length) {
                                    const percent = length / 100;
                                    let counter = 0;
                                    let progressCounter = 0;
                                    res.on('data', chunk => {
                                        counter += chunk.length;
                                        let f = Math.floor(counter / percent);
                                        if (f > progressCounter) {
                                            progress.report({ increment: f - progressCounter });
                                            progressCounter = f;
                                        }
                                    });
                                }
                                res.on('end', () => {
                                    resolve(filePath);
                                });
                            }
                        }
                    }).on('error', e => {
                        reject(e);
                    });
                };
                request(releaseURL);
            }
        });
    });
}

async function extractGraalVM(downloadedFile: string, targetDir: string): Promise<string | undefined> {
    return vscode.window.withProgress<string | undefined>({
        location: vscode.ProgressLocation.Notification,
        title: "Installing GraalVM..."
    }, async (_progress, _token) => {
        const files = await decompress(downloadedFile, targetDir).catch(_err =>{
            vscode.window.showErrorMessage(`File: "${downloadedFile}" couldn't be decompressed to: "${targetDir}". Make sure the GraalVM isn't already installed in the selected location.`);
            return [];
        });
        if (files.length === 0) {
            return undefined;
        }
        const idx = files[0].path.indexOf('/');
        return idx < 0 ? files[0].path : files[0].path.slice(0, idx);
    });
}

function _callIdGVMHome(component: string | Component, homeFolder: string | undefined, context: vscode.ExtensionContext | undefined, fnc: (id: string, graalVMHome: string, context?: vscode.ExtensionContext) => Promise<void>): Promise<void>{
    if (component instanceof Component) {
        return fnc(component.componentId, component.installation.home, context);
    } else {
        return fnc(component, homeFolder || getGVMHome(), context);
    }
}

async function _installGraalVMComponent(componentId: string | undefined, graalVMHome: string, context?: vscode.ExtensionContext): Promise<void> {
    changeGraalVMComponent(graalVMHome, componentId ? [componentId] : await selectAvailableComponents(graalVMHome), 'install', context);
}

async function _uninstallGraalVMComponent(componentId: string | undefined, graalVMHome: string): Promise<void> {
    changeGraalVMComponent(graalVMHome, componentId ? [componentId] : await selectInstalledComponents(graalVMHome), 'remove');
}

async function changeGraalVMComponent(graalVMHome: string, componentIds: string[], action: string, context?: vscode.ExtensionContext): Promise<void> {
    const executablePath = await getGU(graalVMHome);
    let accepted;
    const eeInfo: any = action === 'install' ? await getEEReleaseInfo(graalVMHome) : undefined;
    if (eeInfo && context) {
        const license = await get(eeInfo.license, /^text\/plain/);
        accepted = await LicenseCheckPanel.show(context, eeInfo.licenseLabel, license.split('\n').join('<br>'));
    } else {
        accepted = true;
    }
    if (accepted) {
        const args = eeInfo ? `--custom-catalog ${eeInfo.catalog} ` : '';
        await vscode.window.withProgress({
            location: vscode.ProgressLocation.Notification,
            title: `${action === 'install' ? 'I' : 'Uni'}nstalling GraalVM Component${componentIds.length > 1 ? 's' : ' ' + componentIds[0]}`,
            cancellable: componentIds.length > 1
        }, async (progress, token) => {
            const incr = 100/componentIds.length;
            for (const id of componentIds) {
                if (token.isCancellationRequested) {
                    return;
                }
                if (incr !== 100) {
                    progress.report({message: id, increment: incr});
                }
                try {
                    await execCancellable(`${executablePath} ${action} ${args}${id}`, token);
                } catch (error) {
                    vscode.window.showWarningMessage(error?.message);
                }
            }
            return;
        }).then(() => vscode.commands.executeCommand('extension.graalvm.refreshInstallations'));
    }
}

function execCancellable(cmd: string, token: vscode.CancellationToken): Promise<any> {
    return new Promise((resolve, reject) => {
        const child = cp.exec(cmd, (error, _stdout, _stderr) => {
            if (error) {
                reject(error);
            } else {
                resolve();
            }
        });
        token.onCancellationRequested(() => child.kill());
    });
}

async function getGU(graalVMHome?: string): Promise<string> {
    graalVMHome = graalVMHome || getGVMHome();
    if (graalVMHome) {
        if (! await getGraalVMVersion(graalVMHome)) {
            throw new Error(`Missing GraalVM Installation. ${graalVMHome}`);
        }
    }
    const executablePath = utils.findExecutable('gu', graalVMHome);
    if (executablePath) {
        return makeGUProxy(executablePath, getConf('http').get('proxy'));
    }
    throw new Error("Cannot find runtime 'gu' within your GraalVM installation.");
}

function makeGUProxy(executable:string, proxy?: string): string {
    if (!proxy || getConf('http').get('proxySupport') !== 'off') {
        return `"${executable}"`;
    }
    if (process.platform === 'win32') {
        let index = proxy.indexOf('://');
        proxy = proxy.slice(index + 3);
        index = proxy.indexOf(':');
        return `"${executable}" --vm.Dhttps.proxyHost=${proxy.slice(0, index)} --vm.Dhttps.proxyPort=${proxy.slice(index + 1)}`;
    } else {
        return `env https_proxy=${proxy} "${executable}"`;
    }
}

async function getGraalVMCEReleases(): Promise<any> {
    return Promise.all([
        getGraalVMReleaseURLs(GRAALVM_RELEASES_URL),
        getGraalVMReleaseURLs(GRAALVM_DEV_RELEASES_URL)
    ]).catch(err => {
        throw new Error('Cannot get data from server: ' + err.message);
    }).then(urls => {
        const merged: string[] = Array.prototype.concat.apply([], urls);
        if (merged.length === 0) {
            throw new Error(`No GraalVM installable found for platform ${process.platform}`);
        }
        const releases: any = {};
        merged.forEach(releaseUrl => {
            const version: string[] | null = releaseUrl.match(/\d+\.\d+\.\d+(-dev)?/);
            if (version && version.length > 0) {
                const graalvmVarsion: string = version[0];
                let releasesVersion = releases[graalvmVarsion];
                if (!Object.keys(releases).find(key => graalvmVarsion.endsWith('-dev') ? key.endsWith('-dev') : graalvmVarsion.slice(0, 2) === key.slice(0, 2))) {
                    releases[graalvmVarsion] = releasesVersion = {};
                }
                if (releasesVersion) {
                    const javaVersion: string[] | null = releaseUrl.match(/(java|jdk)(\d+)/);
                    if (javaVersion && javaVersion.length > 0) {
                        let releasesJavaVersion = releasesVersion[javaVersion[0]];
                        if (!releasesJavaVersion) {
                            releasesVersion[javaVersion[0]] = releasesJavaVersion = {};
                            releasesJavaVersion.url = releaseUrl;
                        }
                    }
                }
            }
        });
        return releases;
    });
}

async function getGraalVMEEReleases(): Promise<any> {
    return get(GDS_URL, /^application\/json/).catch(err => {
        throw new Error('Cannot get data from server: ' + err.message);
    }).then(rawData => {
        const info = JSON.parse(rawData);
        let platform: string = process.platform;
        if (platform === 'win32') {
            platform = 'windows';
        }
        const releases: any = {};
        Object.values(info.Releases)
        .filter((releaseInfo: any) => Object.keys(releaseInfo.base).find(base => releaseInfo.base[base].os === platform) !== undefined)
        .forEach((releaseInfo: any) => {
            if (releaseInfo.version && releaseInfo.java && releaseInfo.license) {
                let releaseVersion = releases[releaseInfo.version];
                if (!Object.keys(releases).find(key => releaseInfo.version.endsWith('-dev') ? key.endsWith('-dev') : releaseInfo.version.slice(0, 2) === key.slice(0, 2))) {
                    releases[releaseInfo.version] = releaseVersion = {};
                }
                if (releaseVersion) {
                    let releaseJavaVersion = releaseVersion[releaseInfo.java];
                    if (!releaseJavaVersion) {
                        const base: string | undefined = Object.keys(releaseInfo.base).find(base => releaseInfo.base[base].os === platform);
                        if (base) {
                            releaseVersion[releaseInfo.java] = releaseJavaVersion = {};
                            releaseJavaVersion.url = releaseInfo.base[base].url;
                            releaseJavaVersion.license = releaseInfo.license;
                            releaseJavaVersion.licenseLabel = releaseInfo.licenseLabel || GRAALVM_EE_LICENSE;
                        }
                    }
                }
            }
        });
        return releases;
    });
}

async function getGraalVMReleaseURLs(releasesURL: string): Promise<string[]> {
    return get(releasesURL, /^text\/html/).then(rawData => {
        let regex;
        if (process.platform === 'linux') {
            regex = LINUX_LINK_REGEXP;
        } else if (process.platform === 'darwin') {
            regex = MAC_LINK_REGEXP;
        } else if (process.platform === 'win32') {
            regex = WINDOWS_LINK_REGEXP;
        }
        const ret = [];
        if (regex) {
            let match;
            while ((match = regex.exec(rawData)) !== null) {
                ret.push(GITHUB_URL + match[0].substring(9, match[0].length - 1));
            }
        }
        return ret;
    });
}

async function get(url: string, contentTypeRegExp: RegExp, file?: fs.WriteStream): Promise<string> {
    return new Promise<string>((resolve, reject) => {
        https.get(url, res => {
            const { statusCode } = res;
            const contentType = res.headers['content-type'] || '';
            let error;
            if (statusCode !== 200) {
                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
            } else if (!contentTypeRegExp.test(contentType)) {
                error = new Error(`Invalid content-type received ${contentType}`);
            }
            if (error) {
                res.resume();
                reject(error);
            } else if (file) {
                res.pipe(file);
                res.on('end', () => {
                    resolve(undefined);
                });
            } else {
                let rawData: string = '';
                res.on('data', chunk => { rawData += chunk; });
                res.on('end', () => {
                    resolve(rawData);
                });
            }
        }).on('error', e => {
            reject(e);
        }).end();
    });
}

function deleteFolder(folder: string) {
    if (fs.existsSync(folder)) {
        fs.readdirSync(folder).forEach((file, _index) => {
            var curPath: string = join(folder, file);
            if (fs.lstatSync(curPath).isDirectory()) {
                deleteFolder(curPath);
            } else {
                fs.unlinkSync(curPath);
            }
        });
        fs.rmdirSync(folder);
    }
}

async function _removeGraalVMInstallation(homeFolder: string): Promise<number> {
    const gr = getGVMConfig();
    const installations = getGVMInsts(gr);
    const index = installations.indexOf(homeFolder);
    if (index > -1) {
        installations.splice(index, 1);
        await setGVMInsts(gr, installations);
    }
    const home = getGVMHome(gr);
    if (home === homeFolder) {
        await setGVMHome(undefined, gr);
    }
    return index;
}

function updateGraalVMLocations(homeFolder: string) {
    const gr = getGVMConfig();
    const installations = getGVMInsts(gr);
    if (!installations.find(item => item === homeFolder)) {
        getGraalVMVersion(homeFolder).then(version => {
            if (version) {
                installations.push(homeFolder);
                setGVMInsts(gr, installations);
                const graalVMHome = getGVMHome(gr);
                if (!graalVMHome) {
                    gr.update('home', homeFolder, true);

                } else if (graalVMHome !== homeFolder) {
                    utils.askYesNo(`Set ${version} as active GraalVM?`, () => selectInstalledGraalVM(homeFolder));
                }
            } else {
                vscode.window.showErrorMessage('Failed to add the selected GraalVM installation');
            }
        });
    }
}

function addPathsToJavaIn(folder: string, paths: string[]) {
    if (folder && fs.existsSync(folder) && fs.statSync(folder).isDirectory) {
        fs.readdirSync(folder).map(f => join(folder, f)).map(p => {
            if (process.platform === 'darwin') {
                let homePath: string = join(p, 'Contents', 'Home');
                return fs.existsSync(homePath) ? homePath : p;
            }
            return p;
        }).filter(p => fs.statSync(p).isDirectory()).forEach(p => addPathToJava(p, paths));
    }
}

function addPathToJava(folder: string, paths: string[], removeOnEmpty: boolean = false): void {
    const executable: string | undefined = utils.findExecutable('java', folder);
    if (!executable) {
        if (removeOnEmpty) {
            _removeGraalVMInstallation(folder);
        }
        return;
    }
    folder = normalize(join(dirname(fs.realpathSync(executable)), '..'));
    if (!paths.find(p => p === folder)) {
        paths.push(folder);
    }
}

async function selectAvailableComponents(graalVMHome: string): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
        getAvailableComponents(graalVMHome).then(available => {
            const components = available.filter(availableItem => !availableItem.installed);
            if (components.length > 0) {
                vscode.window.showQuickPick(components, { placeHolder: 'Select GraalVM components to install', canPickMany: true }).then(selected => {
                    if (selected) {
                        resolve(selected.map(component => component.label));
                    } else {
                        reject(new Error('No GraalVM component to install.'));
                    }
                });
            } else {
                reject(new Error('No GraalVM component to install.'));
            }
        });
    });
}

async function selectInstalledComponents(graalVMHome: string): Promise<string[]> {
    return new Promise<string[]>((resolve, reject) => {
        getAvailableComponents(graalVMHome).then(available => {
            const components = available.filter(availableItem => availableItem.installed);
            if (components.length > 0) {
                vscode.window.showQuickPick(components, { placeHolder: 'Select GraalVM components to remove', canPickMany: true }).then(selected => {
                    if (selected) {
                        resolve(selected.map(component => component.label));
                    } else {
                        reject(new Error('No GraalVM component to remove.'));
                    }
                });
            } else {
                reject(new Error('No GraalVM component to remove.'));
            }
        });
    });
}

async function getAvailableComponents(graalVMHome: string): Promise<{label: string, detail: string, installed?: boolean}[]> {
    return new Promise<{label: string, detail: string, installed?: boolean}[]>((resolve, reject) => {
        getGU(graalVMHome).then(executablePath => {
            cp.exec(`${executablePath} list`, (error, stdout, _stderr) => {
                if (error) {
                    reject(error);
                } else {
                    const installed: {label: string, detail: string, installed?: boolean}[] = processsGUOutput(stdout);
                    getEEReleaseInfo(graalVMHome).then(eeInfo => {
                        const args = eeInfo ? ['available', '--custom-catalog', `${eeInfo.catalog}`] : ['available'];
                        cp.exec(`${executablePath} ${args.join(' ')}`, (error: any, stdout: string, _stderr: any) => {
                            if (error) {
                                notifyConnectionProblem();
                                reject({error: error, list: installed.map(inst => {inst.installed = true; return inst; }) });
                            } else {
                                const available: {label: string, detail: string, installed?: boolean}[] = processsGUOutput(stdout);
                                available.forEach(avail => {
                                    const found = installed.find(item => item.label === avail.label);
                                    avail.installed = found ? true : false;
                                });
                                resolve(available);
                            }
                        });
                    }).catch(error => {
                        reject({error: error, list: installed.map(inst => {inst.installed = true; return inst; }) });
                    });
                }
            });
        }).catch(err => reject(err));
    });
}

async function notifyConnectionProblem(){
    const select = await vscode.window.showWarningMessage("Could not resolve GraalVM components. Check your connection and verify proxy settings.", 'Setup proxy');
    if (select === 'Setup proxy') {
        setupProxy();
    }
}

async function getEEReleaseInfo(graalVMHome: string): Promise<any> {
    const version = await getGraalVMVersion(graalVMHome);
    if (version) {
        const versionInfo: string[] | null = version.match(/GraalVM\s+(CE|EE)\s+(\S*), Java (\S*)/);
        if (versionInfo && versionInfo.length >= 4) {
            if (versionInfo[1] === 'EE') {
                const javaVersion = `jdk${versionInfo[3]}`;
                const rawData = await get(GDS_URL, /^application\/json/);
                return Object.values(JSON.parse(rawData).Releases).find((release: any) => release.version === versionInfo[2] && release.java === javaVersion);
            }
        }
    }
    return undefined;
}

function processsGUOutput(stdout: string): {label: string, detail: string}[] {
    const components: {label: string, detail: string}[] = [];
    let header: boolean = true;
    stdout.split('\n').forEach(line => {
        if (header) {
            if (line.startsWith('-----')) {
                header = false;
            }
        } else {
            const info: string[] | null = line.match(/(\S+( \S)?)+/g);
            if (info && info.length > 3) {
                components.push({ label: info[0], detail: info[2] });
            }
        }
    });
    return components;
}

export class InstallationNodeProvider implements vscode.TreeDataProvider<vscode.TreeItem> {

	private _onDidChangeTreeData: vscode.EventEmitter<vscode.TreeItem | undefined | null> = new vscode.EventEmitter<vscode.TreeItem | undefined | null>();
	readonly onDidChangeTreeData: vscode.Event<vscode.TreeItem | undefined | null> = this._onDidChangeTreeData.event;

	refresh(): void {
		this._onDidChangeTreeData.fire(undefined);
	}

	getTreeItem(element: vscode.TreeItem): vscode.TreeItem {
		return element;
	}

	getChildren(element?: vscode.TreeItem): vscode.ProviderResult<vscode.TreeItem[]> {
		if (element instanceof Installation) {
            return getAvailableComponents(element.home).then(components => {
                const ret: vscode.TreeItem[] = [new InstallationFolder(element.home)];
                components.forEach((comp: { detail: string; label: string; installed?: boolean; }) => {
                    ret.push(new Component(element, comp.detail, comp.label, comp.installed));
                });
                return ret;
            }).catch(out => {
                const ret: vscode.TreeItem[] = [new InstallationFolder(element.home)];
                if (out.list) {
                    out.list.forEach((comp: { detail: string; label: string; installed?: boolean; }) => 
                        ret.push(new Component(element, comp.detail, comp.label, comp.installed)));
                    ret.push(new ConnectionError('Could not resolve components', out?.error?.message));
                } else {
                    ret.push(new GUError('Component resolution failed', out?.message));
                }
                return ret;
            });
		} else {
            const graalVMHome = getGVMHome();
            const insts = getGVMInsts();
            return findGraalVMs().then(vms => {
                return vms.map(item => new Installation(item.name, vscode.TreeItemCollapsibleState.Collapsed, item.path, item.path === graalVMHome, insts.includes(item.path)));
            });
		}
	}
}

export class Installation extends vscode.TreeItem {

	constructor(
        public readonly label: string,
        public readonly collapsibleState: vscode.TreeItemCollapsibleState,
        public readonly home: string,
        private readonly active: boolean,
        private readonly fromConf: boolean
	) {
        super(label, collapsibleState);
        if (active) {
            this.description = '(active)';
        }
	}

    iconPath = new vscode.ThemeIcon(this.active ? "vm-active" : "vm");
    contextValue = this.fromConf ? this.active ? 'graalvmInstallationActive' : 'graalvmInstallation' : 'graalvmInstallationOut';
}

export class Component extends vscode.TreeItem {

	constructor(
        public readonly installation: Installation,
        public readonly label: string,
        public readonly componentId: string,
        private readonly installed?: boolean,
	) {
		super(label);
        if (installed) {
            this.description = '(installed)';
        }
	}

    iconPath = new vscode.ThemeIcon("extensions");
    contextValue = this.installed ? 'graalvmComponentInstalled' : 'graalvmComponent';
}

class InstallationFolder extends vscode.TreeItem {
    
    iconPath =  new vscode.ThemeIcon("folder-opened");
    contextValue = 'graalvmInstallationFolder';
}

class ConnectionError extends vscode.TreeItem {

    constructor(
        public readonly label: string,
        public readonly tooltip?: string,
	) {
        super(label);
    }
    
    iconPath = new vscode.ThemeIcon("error");
    contextValue = 'graalvmConnectionError';
}

class GUError extends vscode.TreeItem {

    constructor(
        public readonly label: string,
        public readonly tooltip?: string,
	) {
        super(label);
    }
    
    iconPath = new vscode.ThemeIcon("error");
    contextValue = 'graalvmGUError';
}
