/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as https from 'https';
import * as path from 'path';
import * as decompress from 'decompress';
import * as utils from './utils';

const MICRONAUT_LAUNCH_URL: string = 'https://launch.micronaut.io';
const MICRONAUT_SNAPSHOT_URL: string = 'https://snapshot.micronaut.io';
const APPLICATION_TYPES: string = '/application-types';
const FEATURES: string = '/features';
const VERSIONS: string = '/versions';
const CREATE: string = '/create';
const OPEN_IN_NEW_WINDOW = 'Open in new window';
const OPEN_IN_CURRENT_WINDOW: string = 'Open in current window';
const ADD_TO_CURRENT_WORKSPACE = 'Add to current workspace';

export async function createProject() {
    const options = await selectCreateOptions();
    if (options) {
        const downloadedFile = await downloadProject(options);
        const files = await decompress(downloadedFile, options.target);
        fs.unlinkSync(downloadedFile);
        if (files.length > 0) {
            const uri = vscode.Uri.file(options.target);
            if (vscode.workspace.workspaceFolders) {
                vscode.window.showInformationMessage('New Micronaut project created', OPEN_IN_NEW_WINDOW, ADD_TO_CURRENT_WORKSPACE).then(value => {
                    if (value === OPEN_IN_NEW_WINDOW) {
                        vscode.commands.executeCommand('vscode.openFolder', uri, true);
                    } else if (value === ADD_TO_CURRENT_WORKSPACE) {
                        vscode.workspace.updateWorkspaceFolders(vscode.workspace.workspaceFolders ? vscode.workspace.workspaceFolders.length : 0, undefined, { uri });
                    }
                });
            } else if (vscode.window.visibleTextEditors.length > 0) {
                vscode.window.showInformationMessage('New Micronaut project created', OPEN_IN_NEW_WINDOW, OPEN_IN_CURRENT_WINDOW).then(value => {
                    if (value) {
                        vscode.commands.executeCommand('vscode.openFolder', uri, OPEN_IN_NEW_WINDOW === value);
                    }
                });
            } else {
                vscode.commands.executeCommand('vscode.openFolder', uri, false);
            }
        }
    }
}

async function selectCreateOptions(): Promise<{url: string, name: string, target: string} | undefined> {

    interface State {
		micronautVersion: {label: string, serviceUrl: string};
		applicationType: {label: string, name: string};
        javaVersion: {label: string, value: string};
        projectName: string;
        basePackage: string;
        language: {label: string, value: string};
        features: {label: string, detail: string, name: string}[];
        buildTool: {label: string, value: string};
        testFramework: {label: string, value: string};
	}

	async function collectInputs(): Promise<State> {
		const state = {} as Partial<State>;
        await utils.MultiStepInput.run(input => pickMicronautVersion(input, state));
		return state as State;
	}

    const title = 'Create Micronaut Project';
    const totalSteps = 9;

	async function pickMicronautVersion(input: utils.MultiStepInput, state: Partial<State>) {
        const selected: any = await input.showQuickPick({
			title,
			step: 1,
			totalSteps,
			placeholder: 'Pick Micronaut version',
			items: await getMicronautVersions(),
			activeItems: state.micronautVersion,
			shouldResume: () => Promise.resolve(false)
        });
        state.micronautVersion = selected;
		return (input: utils.MultiStepInput) => pickApplicationType(input, state);
	}

	async function pickApplicationType(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 2,
			totalSteps,
			placeholder: 'Pick application type',
			items: state.micronautVersion ? await getApplicationTypes(state.micronautVersion) : [],
			activeItems: state.applicationType,
			shouldResume: () => Promise.resolve(false)
        });
        state.applicationType = selected;
		return (input: utils.MultiStepInput) => pickJavaVersion(input, state);
	}

	async function pickJavaVersion(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 3,
			totalSteps,
			placeholder: 'Pick Java version',
			items: getJavaVersions(),
			activeItems: state.javaVersion,
			shouldResume: () => Promise.resolve(false)
        });
        state.javaVersion = selected;
		return (input: utils.MultiStepInput) => projectName(input, state);
	}

	async function projectName(input: utils.MultiStepInput, state: Partial<State>) {
		state.projectName = await input.showInputBox({
			title,
			step: 4,
			totalSteps,
			value: state.projectName || 'demo',
			prompt: 'Provide project name',
			validate: () => Promise.resolve(undefined),
			shouldResume: () => Promise.resolve(false)
		});
		return (input: utils.MultiStepInput) => basePackage(input, state);
	}

	async function basePackage(input: utils.MultiStepInput, state: Partial<State>) {
		state.basePackage = await input.showInputBox({
			title,
			step: 5,
			totalSteps,
			value: state.basePackage || 'com.example',
			prompt: 'Provide base package',
			validate: () => Promise.resolve(undefined),
			shouldResume: () => Promise.resolve(false)
		});
		return (input: utils.MultiStepInput) => pickLanguage(input, state);
	}

	async function pickLanguage(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 6,
			totalSteps,
            placeholder: 'Pick project language',
            items: getLanguages(),
            activeItems: state.language,
			shouldResume: () => Promise.resolve(false)
        });
        state.language = selected;
		return (input: utils.MultiStepInput) => pickFeatures(input, state);
	}

	async function pickFeatures(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 7,
			totalSteps,
            placeholder: 'Pick project features',
            items: state.micronautVersion && state.applicationType ? await getFeatures(state.micronautVersion, state.applicationType) : [],
            activeItems: state.features,
            canSelectMany: true,
			shouldResume: () => Promise.resolve(false)
        });
        state.features = selected;
		return (input: utils.MultiStepInput) => pickBuildTool(input, state);
	}

	async function pickBuildTool(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 8,
			totalSteps,
            placeholder: 'Pick build tool',
            items: getBuildTools(),
            activeItems: state.buildTool,
			shouldResume: () => Promise.resolve(false)
        });
        state.buildTool = selected;
		return (input: utils.MultiStepInput) => pickTestFramework(input, state);
	}

	async function pickTestFramework(input: utils.MultiStepInput, state: Partial<State>) {
		const selected: any = await input.showQuickPick({
			title,
			step: 9,
			totalSteps,
            placeholder: 'Pick test framework',
            items: getTestFrameworks(),
            activeItems: state.testFramework,
			shouldResume: () => Promise.resolve(false)
        });
        state.testFramework = selected;
	}

    const state = await collectInputs();

    if (state.micronautVersion && state.applicationType && state.projectName) {
        const location: vscode.Uri[] | undefined = await vscode.window.showOpenDialog({
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            title: 'Select destination folder',
            openLabel: 'Select'
        });
        if (location && location.length > 0) {
            let query = '';
            if (state.javaVersion) {
                if (query) {
                    query += '&javaVersion=' + state.javaVersion.value;
                } else {
                    query = '?javaVersion=' + state.javaVersion.value;
                }
            }
            if (state.language) {
                if (query) {
                    query += '&lang=' + state.language.value;
                } else {
                    query = '?lang=' + state.language.value;
                }
            }
            if (state.buildTool) {
                if (query) {
                    query += '&build=' + state.buildTool.value;
                } else {
                    query = '?build=' + state.buildTool.value;
                }
            }
            if (state.testFramework) {
                if (query) {
                    query += '&test=' + state.testFramework.value;
                } else {
                    query = '?test=' + state.testFramework.value;
                }
            }
            if (state.features) {
                state.features.forEach((feature: {label: string, detail: string, name: string}) => {
                    if (query) {
                        query += '&feature=' + feature.name;
                    } else {
                        query = '?feature=' + feature.name;
                    }
                });
            }
            let appName = state.basePackage;
            if (appName) {
                appName += '.' + state.projectName;
            } else {
                appName = state.projectName;
            }
            return {
                url: state.micronautVersion.serviceUrl + CREATE + '/' + state.applicationType.name + '/' + appName + query,
                name: state.projectName,
                target: path.join(location[0].fsPath, state.projectName)
            };
        }
    }
    return undefined;
}

async function getMicronautVersions(): Promise<{label: string, serviceUrl: string}[]> {
    return Promise.all([
        get(MICRONAUT_LAUNCH_URL + VERSIONS),
        get(MICRONAUT_SNAPSHOT_URL + VERSIONS)
    ]).then(data => {
        return [
            { label: JSON.parse(data[0]).versions["micronaut.version"], serviceUrl: MICRONAUT_LAUNCH_URL},
            { label: JSON.parse(data[1]).versions["micronaut.version"], serviceUrl: MICRONAUT_SNAPSHOT_URL}
        ];
    });
}

async function getApplicationTypes(micronautVersion: {label: string, serviceUrl: string}): Promise<{label: string, name: string}[]> {
    return get(micronautVersion.serviceUrl + APPLICATION_TYPES).then(data => {
        return JSON.parse(data).types.map((type: any) => ({ label: type.title, name: type.name }));
    });
}

function getJavaVersions(): {label: string, value: string}[] {
    return [
        { label: '8', value: 'JDK_8'},
        { label: '9', value: 'JDK_9'},
        { label: '11', value: 'JDK_11'},
        { label: '12', value: 'JDK_12'},
        { label: '13', value: 'JDK_13'},
        { label: '14', value: 'JDK_14'},
    ];
}

function getLanguages(): {label: string, value: string}[] {
    return [
        { label: 'Java', value: 'JAVA'},
        { label: 'Kotlin', value: 'KOTLIN'},
        { label: 'Groovy', value: 'GROOVY'}
    ];
}

function getBuildTools() {
    return [
        { label: 'Gradle', value: 'GRADLE'},
        { label: 'Maven', value: 'MAVEN'}
    ];
}

function getTestFrameworks() {
    return [
        { label: 'JUnit', value: 'JUNIT'},
        { label: 'Spock', value: 'SPOCK'},
        { label: 'Kotlintest', value: 'KOTLINTEST'}
    ];
}

async function getFeatures(micronautVersion: {label: string, serviceUrl: string}, applicationType: {label: string, name: string}): Promise<{label: string, detail: string, name: string}[]> {
    return get(micronautVersion.serviceUrl + APPLICATION_TYPES + '/' + applicationType.name + FEATURES).then(data => {
        return JSON.parse(data).features.map((feature: any) => ({label: `${feature.category}: ${feature.title}`, detail: feature.description, name: feature.name})).sort((f1: any, f2: any) => f1.label < f2.label ? -1 : 1);
    });
}

async function get(url: string): Promise<string> {
    return new Promise<string>((resolve, reject) => {
        https.get(url, res => {
            const { statusCode } = res;
            const contentType = res.headers['content-type'] || '';
            let error;
            if (statusCode !== 200) {
                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
            } else if (!/^application\/json/.test(contentType)) {
                error = new Error(`Invalid content-type.\nExpected application/json but received ${contentType}`);
            }
            if (error) {
                res.resume();
                reject(error);
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

async function downloadProject(options: {url: string, name: string, target: string}): Promise<string> {
    return new Promise<string>((resolve, reject) => {
        fs.mkdirSync(options.target, {recursive: true});
        const filePath: string = path.join(options.target, options.name + '.zip');
        const file: fs.WriteStream = fs.createWriteStream(filePath);
        https.get(options.url, res => {
            const { statusCode } = res;
            const contentType = res.headers['content-type'] || '';
            let error;
            if (statusCode !== 201) {
                error = new Error(`Request Failed.\nStatus Code: ${statusCode}`);
            } else if (!/^application\/zip/.test(contentType)) {
                error = new Error(`Invalid content-type.\nExpected application/zip but received ${contentType}`);
            }
            if (error) {
                res.resume();
                reject(error);
            } else {
                res.pipe(file);
                res.on('end', () => {
                    resolve(filePath);
                });
            }
        }).on('error', e => {
            reject(e);
        }).end();
    });
}
