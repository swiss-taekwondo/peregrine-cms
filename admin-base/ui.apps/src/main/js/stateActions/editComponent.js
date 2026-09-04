/*-
 * #%L
 * admin base - UI Apps
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
import {LoggerFactory} from '../logger'
import {set} from '../utils'

let log = LoggerFactory.logger('editComponent').setLevelDebug()

function relativeComponentPath(view, target) {
    const pagePath = view && view.pageView && view.pageView.path
    const path = String(target || '')
    if (pagePath && path.indexOf(pagePath + '/jcr:content') === 0) {
        return path.substring(pagePath.length)
    }
    const jcrIndex = path.indexOf('/jcr:content')
    if (jcrIndex > -1) {
        return path.substring(jcrIndex)
    }
    return path
}

function findPageNodeFromPath(me, view, path) {
    const pageRoot = view && view.pageView && view.pageView.page
    if (!pageRoot || !path) return null
    const pagePath = view && view.pageView && view.pageView.path
    const candidates = []
    function addCandidate(value) {
        if (value && candidates.indexOf(value) === -1) {
            candidates.push(value)
        }
    }
    addCandidate(path)
    if (pagePath && path.indexOf('/jcr:content') === 0) {
        addCandidate((pagePath + path).replace(/\/\//g, '/'))
    }
    if (pagePath && path.indexOf(pagePath + '/jcr:content') === 0) {
        addCandidate(path.substring(pagePath.length))
    }
    for (let i = 0; i < candidates.length; i++) {
        const node = me.findNodeFromPath(pageRoot, candidates[i])
        if (node) return node
    }
    function scan(node) {
        if (!node || typeof node !== 'object') return null
        if (candidates.indexOf(node.path) !== -1) return node
        if (Array.isArray(node.children)) {
            for (let i = 0; i < node.children.length; i++) {
                const childResult = scan(node.children[i])
                if (childResult) return childResult
            }
        }
        return null
    }
    return scan(pageRoot)
}

function bringUpEditor(me, view, target) {
    log.fine('Bring Up Editor, ')

    const componentNode = findPageNodeFromPath(me, view, target)
    const originalData = componentNode ? JSON.parse(JSON.stringify(componentNode)) : null

    const beforeUnloadHandler = function(e) {
        if (!view.pageView || !view.pageView.page) return;
        if (!view.state.editor || !view.state.editor.originalData) return;
        const currentNode = findPageNodeFromPath(me, view, view.state.editor.path)
        if (!currentNode) return;
        const replacer = (k, v) => k === '_opDeleteProps' || k === 'children' || v === null || v === '' ? undefined : v
        const originalStr = JSON.stringify(view.state.editor.originalData, replacer)
        const currentStr = JSON.stringify(currentNode, replacer)
        if (originalStr !== currentStr) {
            e.preventDefault();
            e.returnValue = '';
            return '';
        }
    };
    $perAdminApp.setBeforeUnloadHandler(beforeUnloadHandler);

    me.beforeStateAction(function(name) {
        if (name === 'savePageEdit' || name === 'deletePageNode' || name === 'editComponent') {
            return true;
        }
        if (!view.state.editor || !view.state.editor.originalData) {
            return true;
        }
        const currentNode = findPageNodeFromPath(me, view, view.state.editor.path)
        if (!currentNode) {
            return true;
        }
        const originalStr = JSON.stringify(view.state.editor.originalData, (k, v) => k === '_opDeleteProps' || k === 'children' || v === null || v === '' ? undefined : v)
        const currentStr = JSON.stringify(currentNode, (k, v) => k === '_opDeleteProps' || k === 'children' || v === null || v === '' ? undefined : v)
        if (originalStr === currentStr) {
            return true;
        }
        return new Promise((resolve) => {
            $perAdminApp.askUser('Save Page Edit?', 'Would you like to save your page edits?', {
                defaultFocus: 'keepEditing',
                yesText: 'Save',
                noText: 'Discard Changes',
                keepEditingText: 'Keep Editing',
                yes() {
                    const path = view.state.editor.path;
                    const data = findPageNodeFromPath(me, view, path);
                    me.stateAction('savePageEdit', { pagePath: view.pageView.path, path, data}).then(() => {
                        $perAdminApp.clearBeforeStateActions();
                        $perAdminApp.clearBeforeUnloadHandler();
                        resolve(true);
                    });
                },
                no() {
                    $perAdminApp.clearBeforeStateActions();
                    $perAdminApp.clearBeforeUnloadHandler();
                    resolve(true);
                },
                keepEditing() {
                    resolve(false);
                }
            });
        });
    });

    return new Promise((resolve, reject) => {
        const componentDefinitionPath = target.indexOf('/content/') === 0
            ? target
            : view.pageView.path + target
        return me.getApi().populateComponentDefinitionFromNode(componentDefinitionPath).then((name) => {
                log.fine('component name is', name)
                set(view, '/state/editor/component', name)
                set(view, '/state/editor/path', target)
                set(view, '/state/editorVisible', true)
                set(view, '/state/rightPanelVisible', true)
                set(view, '/state/editor/originalData', originalData)
                resolve()
            }
        ).catch(error => {
            log.debug('Failed to show editor: ' + error)
            $perAdminApp.notifyUser('error', 'was not able to bring up editor for the selected component')
            reject()
        })
    })
}

export default function(me, target) {
    log.fine(target)
    let view = me.getView()

    const path = typeof target === 'object' ? target.path : target
    const relativePath = relativeComponentPath(view, path)

    if (view.state.editor && view.state.editor.path === relativePath && view.state.editorVisible && view.state.editor.component) {
        return Promise.resolve()
    }

    return new Promise((resolve, reject) => {
        bringUpEditor(me, view, relativePath).then(() => {
            resolve()
        }).catch(() => reject())
    })
}
