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
// var axios = require('axios')

import {LoggerFactory} from './logger'
import {objectToFormData, stripNulls, pagePathToDataPath} from './utils'
import {Field, Toast} from './constants'
import Notifier from './utils/notifier'

let logger = LoggerFactory.logger('apiImpl').setLevelDebug()

const API_BASE = '/perapi'
const postConfig = {
  withCredentials: true
}

let callbacks
let translationModel = null

function blob(content) {
  return new Blob([ content ], {type: 'application/json; charset=utf-8'})
}

function json(data) {
  const content = JSON.stringify(data)
  return blob(content)
}

// Shared Diffing Helper: Finds exact paths of nodes with string changes AND their changed properties
function getModifiedPaths({ oldObj = {}, newObj = {}, pagePath, currentObjPath, modifiedPathsMap = {} }) {
  const keys = Object.keys(newObj);
  let nodeChangedProps = [];

  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];

    // Ignore structural properties, fields starting with an underscore, & internal state tracking keys
    if (key === 'path' || key === 'name' || key === 'component' || key === 'jcr:primaryType' || key === 'sling:resourceType' || key === 'per:TranslateRef' || key.indexOf('_') === 0 || key.indexOf('per:') === 0) {
      continue;
    }

    const newVal = newObj[key];
    const oldVal = oldObj[key];

    if (typeof newVal === 'string') {
      const effectiveOldVal = oldVal === undefined ? "" : String(oldVal);

      // Ignore typical non-translatable form-injected defaults if they weren't explicitly set before
      const isNewEmptyOrDefault = (newVal === "" || newVal === "[]" || newVal === "{}" || newVal === "false" || newVal === "true");

      if (effectiveOldVal === "" && isNewEmptyOrDefault) {
        // Ignore form serializer initialization artifacts
      } else if (newVal !== effectiveOldVal) {
        nodeChangedProps.push(key);
      }
    } else if (typeof newVal === 'object' && newVal !== null) {
      let childOldVal = oldVal;

      // Handle cases where child was previously serialized as a string
      if (typeof oldVal === 'string') {
        try { childOldVal = JSON.parse(oldVal); } catch(e) { childOldVal = {}; }
      } else if (typeof oldVal !== 'object' || oldVal === null) {
        childOldVal = Array.isArray(newVal) ? [] : {};
      }

      if (Array.isArray(newVal)) {
        for (let j = 0; j < newVal.length; j++) {
          let itemPath = currentObjPath;
          // If nested array items contain their own JCR path, reconstruct it
          if (newVal[j] && typeof newVal[j] === 'object' && newVal[j].path) {
            itemPath = newVal[j].path.indexOf('/jcr:content') === 0
              ? (pagePath + newVal[j].path).replace(/\/\//g, '/')
              : newVal[j].path;
          } else {
            itemPath = currentObjPath + '/' + key + '/' + j;
          }
          getModifiedPaths({
            oldObj: childOldVal[j],
            newObj: newVal[j],
            pagePath,
            currentObjPath: itemPath,
            modifiedPathsMap
          });
        }
      } else {
        let childPath = currentObjPath;
        if (newVal.path) {
          childPath = newVal.path.indexOf('/jcr:content') === 0
            ? (pagePath + newVal.path).replace(/\/\//g, '/')
            : newVal.path;
        } else {
          childPath = currentObjPath + '/' + key;
        }
        getModifiedPaths({
          oldObj: childOldVal,
          newObj: newVal,
          pagePath,
          currentObjPath: childPath,
          modifiedPathsMap
        });
      }
    }
  }

  if (nodeChangedProps.length > 0) {
    if (!modifiedPathsMap[currentObjPath]) {
      modifiedPathsMap[currentObjPath] = [];
    }
    // Merge to avoid duplicates
    nodeChangedProps.forEach(prop => {
      if (modifiedPathsMap[currentObjPath].indexOf(prop) === -1) {
        modifiedPathsMap[currentObjPath].push(prop);
      }
    })
  }

  return modifiedPathsMap;
}

function getTranslationModel() {
  if (translationModel) {
    return Promise.resolve(translationModel)
  }

  const tenantName = $perAdminApp.getView().state.tenant.name

  if (!tenantName) {
    return Promise.reject(new Error("Could not determine tenant name."))
  }

  return axios.get('/content/' + tenantName + '.json')
    .then(tenantRes => {
      const tenantConfig = tenantRes.data
      const sourceSite = tenantConfig.sourceSite ? tenantConfig.sourceSite : tenantName
      return axios.get('/apps/' + sourceSite + '/i18n/model.json')
    })
    .then(modelRes => {
      translationModel = modelRes.data
      return translationModel
    })
}

function listTranslations(path, model) {
  const content = JSON.stringify(model)
  const modelBlob = new Blob([content], { type: 'application/json; charset=utf-8' })

  const formData = new FormData()
  formData.append('model', modelBlob)

  return axios.post('/perapi/admin/listTranslations.json' + path, formData)
    .then(response => {
      return response.data
    })
}

function autoTranslate(path, translations, changedProperties = null) {
  if (!translations || !translations.nodes || translations.nodes.length === 0) {
    return Promise.resolve("No nodes found to translate.")
  }

  // Check if there's at least one node with translations anywhere in the tree
  // To avoid auto translations, one can simply delete all translations
  let hasAnyTranslation = false
  for (let i = 0; i < translations.nodes.length; i++) {
    if (translations.nodes[i].translations) {
      hasAnyTranslation = true
      break
    }
  }

  if (!hasAnyTranslation) {
    return Promise.resolve("No existing translations found. Skipping auto-translation.")
  }

  // Find the exact node matching the requested path
  const targetNode = translations.nodes.find(n => n.path === path)

  if (!targetNode) {
    return Promise.resolve("Target path not found in translations payload.")
  }

  function hasTranslatableText(htmlString) {
    if (!htmlString || !htmlString.trim()) return false
    const parser = new DOMParser()
    const doc = parser.parseFromString(htmlString, 'text/html')

    function walk(node) {
      if (node.nodeType === Node.TEXT_NODE) {
        return node.textContent.trim().length > 0
      }
      if (node.nodeType === Node.ELEMENT_NODE) {
        const tagName = node.tagName.toLowerCase()
        const ignoredTags = ['script', 'style', 'noscript', 'iframe', 'object']
        if (ignoredTags.indexOf(tagName) !== -1) return false
        for (let j = 0; j < node.childNodes.length; j++) {
          if (walk(node.childNodes[j])) return true
        }
      }
      return false
    }
    return walk(doc.body)
  }

  const propertiesToTranslate = []

  if (targetNode.original) {
    Object.keys(targetNode.original).forEach(key => {
      const isChanged = !changedProperties || changedProperties.includes(key);
      if (isChanged && hasTranslatableText(targetNode.original[key])) {
        propertiesToTranslate.push(key)
      }
    })
  }

  if (propertiesToTranslate.length === 0) {
    return Promise.resolve("No translatable properties found for this node.")
  }

  // Fetch the configured languages dynamically
  return axios.get('/perapi/admin/translateNode.json')
    .then(response => {
      const languageMap = (response.data && response.data.languageMap) ? response.data.languageMap : {};
      const languages = Object.keys(languageMap);

      if (languages.length === 0) {
        return Promise.resolve("No languages configured for translation.");
      }

      const translationPromises = []

      languages.forEach(lang => {
        const formData = new FormData()
        formData.append('_charset_', 'UTF-8')
        formData.append('lang', lang)
        formData.append('override', 'true')

        propertiesToTranslate.forEach(prop => {
          formData.append('properties[]', prop)
        })

        const requestPromise = axios.post('/perapi/admin/translateNode.json' + targetNode.path, formData)
          .then(response => {
            return response.data
          })
          .catch(error => {
            // Catching individual translation errors so a single failed language doesn't kill the Promise.all
            console.error(`Failed to auto-translate to ${lang}:`, error);
            return `Failed to translate to ${lang}`;
          });

        translationPromises.push(requestPromise)
      })

      return Promise.all(translationPromises)
    })
    .catch(error => {
      console.error("Failed to fetch configured languages:", error);
      throw new Error("Could not load translation language map.");
    });
}

function fetch(path) {
  logger.fine('Fetch ', path)
  return axios.get(API_BASE + path).then((response) => {
    return new Promise((resolve, reject) => {

      // Fix for IE11
      if ((typeof response.data === 'string' && response.data.startsWith(
          '<!DOCTYPE')) || (response.request && response.request.responseURL
          && response.request.responseURL.indexOf('/system/sling/form/login')
          >= 0)) {
        window.location = '/system/sling/form/login'
        reject('need to authenticate')
      }
      resolve(response.data)
    })
  }).catch((error) => {
    logger.error('Fetch request to', path, 'failed')
    if (path.startsWith('/admin/access.json?')) {
      window.location = '/system/sling/form/login'
    }
    throw error
  })

}

function update(path) {
  logger.fine('Update, path: ', path)
  return axios.post(API_BASE + path, null, postConfig)
      .then((response) => {
        logger.fine('Update, response data: ' + response.data)
        return response.data
      })
      .catch((error) => {
        logger.error('Update request to', path, 'failed')
        throw error
      })
}

function updateWithForm(path, data) {
  logger.fine('Update with Form, path: ' + path + ', data: ' + data)
  return axios.post(API_BASE + path, data, postConfig)
      .then((response) => response.data)
      .catch((error) => {
        logger.error('Update with Form request to', path, 'failed')
        throw error
      })
}

function updateWithFormAndConfig(path, data, config) {
  //AS TODO: How to merge config into postConfig or the other way around?
  // config.withCredentials: true
  logger.fine('Update with Form and Config, path: ' + path + ', data: ' + data);

  return postFormData(API_BASE + path, data, config);
}

/**
 *
 * @param {string} url
 * @param {FormData} data
 * @param {AxiosRequestOptions} config
 * @returns
 */
function postFormData(url, data, config = null) {

  if (!url) {
    return logger.error('missing url!', url, data, config);
  }

  let formData;

  if (!data) {
    logger.warn('sending empty form-data request!?');
  } else if (!(data instanceof FormData)) {
    logger.fine('data is not FormData instance. fixing that for you', data);
    formData = objectToFormData(data);
  } else {
    formData = data;
  }

  logger.fine('postFormData: ', url, data, config);

  return axios
     .post(url, formData, config)
     .then(({data}) => {
      logger.fine('postFormData, response data: ' + data);

      return data;
    })
    .catch((error) => {
      logger.error('postFormData ', error.response.request.path, 'failed');
      throw error;
    });
}

function getOrCreate(obj, path) {

  if (path === '/') {
    // do nothing, requesting root
  } else {
    var segments = path.split('/').slice(1).reverse()

    while (segments.length > 0) {
      var segment = segments.pop()
      if (!obj[segment]) {
        Vue.set(obj, segment, {})
//                obj[segment] = {}
      }
      obj = obj[segment]
    }
  }

  return obj
}

function populateView(path, name, data) {

  return new Promise((resolve) => {
    var obj = getOrCreate(callbacks.getView(), path)
    let vue = callbacks.getApp()
    if (vue && path !== '/') {
      Vue.set(obj, name, data)
    } else {
      obj[name] = data
    }
    resolve(path, name, data)
  })

}

// function updateExplorerDialog() {
//   const view = callbacks.getView()
//   const page = get(view, '/state/tools/page', '')
//   const template = get(view, '/state/tools/template', '')
//   if (page) {
//     $perAdminApp.stateAction('showPageInfo', {selected: page})
//   }
//   if (template) {
//     $perAdminApp.stateAction('showPageInfo', {selected: template})
//   }
// }

function translateFields(fields) {
  const $i18n = Vue.prototype.$i18n
  if (!$i18n) return fields
  if (!fields || fields.length <= 0) {
    return
  }
  for (let i = 0; i < fields.length; i++) {
    const field = fields[i]
    if (field) {
      if (field.label) {
        const label = field.label.split(':').join('..')
        fields[i].label = $i18n(label)
      }
      if (field.placeholder) {
        const placeholder = field.placeholder.split(':').join('..')
        fields[i].placeholder = $i18n(placeholder)
      }
      if (field.hint) {
        let split = field.hint.split('. ')
        if (split.length <= 1) {
          fields[i].hint = $i18n(field.hint)
        } else {
          for (let j = 0; j < split.length; j++) {
            let item = split[j]
            if (item.length > 0) {
              split[j] = $i18n(item)
            }
          }
          fields[i].hint = split.join('. ')
        }
      }
      if (field.type === Field.SWITCH) {
        fields[i].textOn = $i18n(field.textOn)
        fields[i].textOff = $i18n(field.textOff)
      } else if (field.type === Field.SELECT) {
        const values = fields[i].values
        for (let j = 0; j < values.length; j++) {
          const name = values[j].name
          const t = $i18n(name)
          fields[i].values[j].name = t.startsWith('T[') ? name : t
        }
      } else if (field.type === Field.MULTI_SELECT) {
        if (field.selectOptions.placeholder) {
          const placeholder = field.selectOptions.placeholder
          field.selectOptions.placeholder = $i18n(placeholder)
        }
      }
    }
  }
}

function fetchRef(service, path, sameTenant = false) {
  return fetch(`/admin/${service}.json${path}?${new URLSearchParams({ sameTenant })}`)
}

class PerAdminImpl {

  constructor(cb) {
    callbacks = cb
    this.$notifier = new Notifier($perAdminApp)
  }

  populateTools() {
    return fetch('/admin/list.json/tools')
        .then((data) => populateView('/admin', 'tools', data.children))
        .catch((error) => {
          logger.error('call populateTools() failed')
          return error
        })
  }

  populateToolsConfig() {
    return fetch('/admin/list.json/tools/config')
        .then((data) => populateView('/admin', 'toolsConfig', data.children))
  }

  populateUser() {
    return fetch('/admin/access.json?' + (new Date()).getTime())
        .then((data) => {
          return populateView('/state', 'user', data.userID).then(() => {
            if (data.userID === 'anonymous') {
              // alert('please login to continue')
              window.location = '/'
            }
            return populateView('/state', 'userPreferences', data.preferences)
                .then(() => {
                      if (data.profile) {
                        return populateView('/state/userPreferences', 'profile',
                            data.profile)
                      }
                    }
                )
          })
        })
  }

  populateContent(path) {
    return fetch('/admin/content.json' + path)
        .then((data) => populateView('/', 'adminPageStaged', data))
  }

  populateComponents() {
    return fetch('/admin/components.json')
        .then((data) => populateView('/admin', 'components', data))
  }

  populateObjects() {
    return fetch('/admin/objects.json')
        .then((data) => populateView('/admin', 'objects', data))
  }

  populateTemplates() {
    return fetch('/admin/templates.json')
        .then((data) => populateView('/admin', 'templates', data))
  }

  populateSkeletonPages(path, target = 'skeletonNodes',
      includeParents = false) {
    const skeletonPagePath = path.split('/').slice(0, 4).join('/')
        + '/skeleton-pages'

    // try {
    //   if (get(skeletonPagePath, null)) {
    //     this.populateContent(skeletonPagePath)
    //   }
    // } catch(err) {}

    return this.populateNodesForBrowser(skeletonPagePath, target,
        includeParents)
  }

  populateNodesForBrowser(path, target = 'nodes', includeParents = false) {
    return fetch('/admin/nodes.json' + path + '?includeParents=' + includeParents)
        .then((data) => populateView('/admin', target, data))
  }

  populateComponentDefinitionFor(component) {
    return fetch('/admin/components/' + component)
        .then((data) => populateView('/admin/componentDefinitions', component,
            data))
  }

  populateComponentDefinitionFromNode(path) {
    return new Promise((resolve, reject) => {
      var name
      fetch('/admin/componentDefinition.json' + path)
          .then((data) => {
            name = data.name
            let component = callbacks.getComponentByName(name)
            if (component && component.methods
                && component.methods.augmentEditorSchema) {
              data.model = component.methods.augmentEditorSchema(data.model)
              data.ogTags = component.methods.augmentEditorSchema(data.ogTags)
            }

            let promises = []
            if (data && data.model) {
              const processField = (field) => {
                let from = field.valuesFrom
                if (from) {
                  field.values = []
                  let promise = axios.get(from).then((response) => {
                    for (var key in response.data) {
                      if (response.data[key]['jcr:title']) {
                        const nodeName = key
                        const val = from.replace('.infinity.json',
                          '/' + nodeName)
                        let name = response.data[key].name
                        if (!name) {
                          name = response.data[key]['jcr:title']
                        }
                        field.values.push(
                          {value: val, name: name})
                      }
                    }
                  }).catch((error) => {
                    logger.error('missing node',
                      field.valuesFrom,
                      'for list population in dialog', error)
                  })
                  promises.push(promise)
                }
                let visible = field.visible
                if (visible) {
                  field.visible = function () {
                    return exprEval.Parser.evaluate(visible, this)
                  }
                }

                if (field.type === 'collection') {
                  if (Array.isArray(field.fields)) {
                    for (let i = 0; i < field.fields.length; i++) {
                      processField(field.fields[i])
                    }
                  }
                  if (field.serialized) {
                    try {
                      field.values = json.parse(field.values)
                    } catch (error) {
                      console.error('error parsing list values', error)
                    }
                  }
                }
              };

              if (data.model.groups) {
                for (let j = 0; j < data.model.groups.length; j++) {
                  for (let i = 0; i < data.model.groups[j].fields.length; i++) {
                    processField(data.model.groups[j].fields[i])
                  }
                }
              } else {
                for (let i = 0; i < data.model.fields.length; i++) {
                  processField(data.model.fields[i])
                }
                if (data.ogTags) {
                  for (let i = 0; i < data.ogTags.fields.length; i++) {
                    processField(data.ogTags.fields[i]);
                  }
                  translateFields(data.ogTags.fields)
                }
                translateFields(data.model.fields)
              }
            } else {
              logger.warn(
                  `no dialogger.json file given for component "${name}"`)
            }

            Promise.all(promises).then(() => {
              populateView('/admin/componentDefinitions', data.name, data)
              resolve(name)
            })
          })
    }).catch(error => {
      reject(error)
    })
  }

  populateExplorerDialog(path) {
    return this.populateComponentDefinitionFromNode(path)
  }

  populateTenants() {
    return new Promise((resolve, reject) => {
      fetch('/admin/listTenants.json')
          .then((data) => {
            // const state = callbacks.getView().state
            // if (!state.tenant && data.tenants.length > 0) {
            //   $perAdminApp.stateAction('setTenant',
            //       data.tenants[data.tenants.length - 1])
            //       .then(() => populateView('/admin', 'tenants', data.tenants))
            //       .then(() => resolve())
            // } else {
            populateView('/admin', 'tenants', data.tenants)
                .then(() => resolve())
            // }
          })
    })
  }

  populateBackupInfo(backup) {
    let tenantName = backup ? backup.tenant : ''
    if (tenantName === '' || tenantName === 'undefined') {
      const tenant = $perAdminApp.getNodeFromViewWithDefault('/state/tenant',
          {})
      tenantName = tenant ? tenant.name : ''
    }
    fetch('/admin/backupTenant.json/content/' + tenantName)
        .then((data) => populateView('/state/tools', 'backup', data))
  }

  populatePageView(path) {
    return fetch('/admin/readNode.json' + path)
        .then((data) => populateView('/pageView', 'page', data))
  }

  populateObject(path, target, name, schema) {
    return this.populateComponentDefinitionFromNode(path)
      .then(() => {
        return fetch('/admin/getObject.json' + path)
          .then(async (data) => {
            if (!schema) {
              schema = await fetch('/admin/componentDefinition.json' + path).then((data) => data.model);
            }
            if (schema && schema.fields && schema.fields.forEach) schema.fields.forEach((field) => {
              if (data[field.model] && field.multifield && field.serialized) {
                try {
                  data[field.model] = JSON.parse(data[field.model])
                } catch(e) {
                  data[field.model] = []
                }
              }
            });
            if (data.tags) {
              data.tags = JSON.parse(data.tags)
            }
            return populateView(target, name, data)
          })
      })
  }

  populateReferencedBy(path, sameTenant = false) {
    return fetchRef('refBy', path, sameTenant)
        .then((data) => {
          if (sameTenant && data.referencedBy && Array.isArray(data.referencedBy)) {
            data.referencedBy = data.referencedBy.filter(reference => reference.path !== path && !reference.path.includes('/experiences/lang_') && !reference.activated || reference.activated && reference.is_stale)
          }

          populateView('/state', 'referencedBy', data)
        })
  }

  populateReferences(path, sameTenant = false) {
    return new Promise((resolve, reject) => {
      fetchRef('ref', path, sameTenant)
          .then(function (data) {
            if (sameTenant && data.references && Array.isArray(data.references)) {
              data.references = data.references.filter(reference => reference.path !== path && !reference.path.includes('/experiences/lang_') && !reference.activated || reference.activated && reference.is_stale)

              const tenant = $perAdminApp.getView().state.tenant.name;
              if (path.startsWith(`/content/${tenant}/pages/`) || path.startsWith(`/content/${tenant}/templates/`) || path.startsWith(`/content/${tenant}/objects/`)) {
                const ignoredTypes = ['per:Page', 'sling:OrderedFolder', 'per:Object', 'per:ObjectDefinition', 'admin/objects/tag'];
                data.references = data.references.filter(reference => !ignoredTypes.includes(reference.type));
              }
              else if (path.startsWith(`/content/${tenant}/assets/`) || path.startsWith(`/content/${tenant}/object-definitions/`)) {
                data.references = [];
              }
            }
            populateView('/state', 'references', data)
                .then(() => resolve())
          })
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
          })
    })
  }

  populateI18N(language) {
    return new Promise((resolve, reject) => {
      axios.get('/i18n/admin/' + language + '.infinity.json')
          .then((response) => {
            populateView('/admin/i18n', language, response.data)
                .then(() => resolve())
          })
    })
  }

  populateRecyclebin(page = 0) {
    let tenant = getOrCreate(callbacks.getView(), '/state/tenant').name
    if (tenant == undefined) {
      tenant = callbacks.getView().state.tenant.name
    }
    if (page instanceof Object) {
      page = 0
    }
    return new Promise((resolve, reject) => {
      fetch(`/admin/listRecyclables.json/content/${tenant}?page=${page}`)
          .then(function (result) {
            populateView('/admin', 'recyclebin', result)
                .then(() => resolve())
          })
          .catch(error => {
            $perAdminApp.notifyUser('error',
                `${error}. Unable to load Recycle Bin`)
          })
    })
  }

  populateVersions(page) {
    if (page) {
      return new Promise((resolve, reject) => {
        fetch(`/admin/listVersions.json${page}`)
            .then(function (result) {
              populateView('/state', 'versions', result)
                  .then(() => resolve())
            })
            .catch(error => {
              if (error.response && error.response.data
                  && error.response.data.message) {
                reject(error.response.data.message)
              }
            })
      })
    }
  }

  recycleItem(item) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      updateWithForm('/admin/restoreRecyclable.json' + item.recyclebinItemPath,
          data)
          .then((data) => callbacks.getApi().populateRecyclebin(0))
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  deleteRecyclable(path) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      updateWithForm('/admin/deleteNode.json' + path, data)
          .then((data) => callbacks.getApi().populateRecyclebin(0))
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  deleteVersion(info) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('action', 'deleteVersion')
      data.append('version', info.version)
      updateWithForm('/admin/manageVersions.json' + info.path, data)
          .then((data) => callbacks.getApi().populateVersions(info.path))
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  createVersion(path) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('action', 'createVersion')
      updateWithForm('/admin/manageVersions.json' + path, data)
          .then((data) => callbacks.getApi().populateVersions(path))
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  restoreVersion(path, versionName) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('action', 'restoreVersion')
      data.append('version', versionName)
      updateWithForm('/admin/manageVersions.json' + path, data)
          .then((data) => callbacks.getApi().populateVersions(path))
          .then(function () {
            if (path.includes('/assets/')) {
              $perAdminApp.loadContent('/content/admin/pages/assets')
            } else {
              callbacks.getApi().populatePageView(path)
                  .then(function () {
                    const editView = document.getElementById('editview')
                    if (editView) {
                      editView.contentWindow.$peregrineApp.loadContent(
                          path + '.html')
                    }
                  })
                  .then(function () {
                    let nodes = ''
                    const pagesRgx = new RegExp('^\/content\/[^\/]+\/pages\/')
                    const templatesRgx = new RegExp(
                        '^\/content\/[^\/]+\/templates\/')
                    if (pagesRgx.test(path)) {
                      nodes = $perAdminApp.getView().state.tools.pages
                    } else if (templatesRgx.test(path)) {
                      nodes = $perAdminApp.getView().state.tools.templates
                    }
                    if (nodes != '') {
                      $perAdminApp.getApi().populateNodesForBrowser(nodes)
                    }
                  })
            }
          })
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            } else {
              reject(error)
            }
          })
    })
  }

  createTenant(fromName, toName, tenantTitle, tenantUserPwd, colorPalette) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('fromTenant', fromName)
      data.append('toTenant', toName)
      data.append('tenantTitle', blob(tenantTitle))
      data.append('tenantUserPwd', tenantUserPwd)
      if (colorPalette) {
        data.append('colorPalette', colorPalette)
      }
      updateWithForm('/admin/createTenant.json', data)
          .then((data) => this.populateNodesForBrowser(
              callbacks.getView().state.tools.pages))
          .then(() => resolve())
          .catch(error => {
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  createPage(parentPath, name, templatePath, title) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('name', name)
      data.append('templatePath', templatePath)
      data.append('title', title)
      updateWithForm('/admin/createPage.json' + parentPath, data)
          .then((data) => {
            if (parentPath.indexOf('skeleton-pages') > -1) {
              this.populateSkeletonPages(parentPath)
            }
            this.populateNodesForBrowser(parentPath)
          })
          .then(() => resolve())
    })
  }

  createPageFromSkeletonPage(parentPath, name, skeletonPagePath) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('path', skeletonPagePath)
      data.append('to', parentPath)
      data.append('deep', 'true')
      data.append('newName', name)
      data.append('newTitle', name)
      data.append('type', 'child')
      updateWithForm('/admin/createPageFromSkeletonPage.json', data)
          .then((data) => this.populateNodesForBrowser(parentPath))
          .then(() => resolve())
    })
  }

  createObject(parentPath, name, templatePath) {
    let data = new FormData()
    data.append('name', name)
    data.append('templatePath', templatePath)
    return updateWithForm('/admin/createObject.json' + parentPath, data)
        .then(() => this.populateNodesForBrowser(parentPath))
  }

  deleteObject(path) {
    let data = new FormData()
    return updateWithForm('/admin/deleteNode.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  deleteAsset(path) {
    let data = new FormData()
    return updateWithForm('/admin/deleteNode.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  renameAsset(path, newName) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('to', newName)
      updateWithForm('/admin/asset/rename.json' + path, data)
          .then((data) => this.populateNodesForBrowser(path))
          .then(() => resolve())
          .catch(error => {
            logger.error('Failed to change name: ' + error)
            reject('Unable to change name. ' + error)
          })
    })
  }

  moveAsset(path, to, type) {
    let data = new FormData()
    data.append('to', to)
    data.append('type', type)
    return updateWithForm('/admin/move.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  moveObject(path, to, type) {
    let data = new FormData()
    data.append('to', to)
    data.append('type', type)
    return updateWithForm('/admin/move.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  renameObject(path, newName) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('to', newName)
      updateWithForm('/admin/object/rename.json' + path, data)
          .then((data) => this.populateNodesForBrowser(path))
          .then(() => resolve())
    })
  }

  deletePage(path) {
    const data = new FormData()
    return updateWithForm('/admin/deletePage.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  deleteTenant(target) {
    const name = target.name
    const root = '/content'
    const data = new FormData()
    data.append('name', name)
    return updateWithForm('/admin/deleteTenant.json', data)
        .then(() => this.populateNodesForBrowser(root))
  }

  renamePage(path, newName, newTitle) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('to', newName)
      if (newTitle) {
        data.append('title', newTitle)
      }
      updateWithForm('/admin/page/rename.json' + path, data)
          .then((data) => this.populateNodesForBrowser(path))
          .then(() => resolve())
          .catch(error => {
            logger.error('Failed to change name: ' + error)
            reject('Unable to change name. ' + error)
          })
    })
  }

  copyPage(srcPath, targetPath, name = null, otherProperties) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('path', srcPath)
      data.append('to', targetPath)
      data.append('deep', 'true')
      data.append('newName', name)
      data.append('newTitle', name)
      data.append('type', 'child')

      if (otherProperties) {
        const keys = Object.keys(otherProperties)
        for (let i = 0; i < keys.length; i++) {
          const key = keys[i];
          const value = otherProperties[key]
          data.append(key, value)
        }
      }

      updateWithForm('/admin/createPageFromSkeletonPage.json', data)
          .then((data) => this.populateNodesForBrowser(srcPath))
          .then(() => resolve())
    })
  }

  movePage(path, to, type) {
    let data = new FormData()
    data.append('to', to)
    data.append('type', type)
    return updateWithForm('/admin/move.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  deletePageNode(path, nodePath) {
    let data = new FormData()
    return updateWithForm('/admin/deleteNode.json' + nodePath, data)
        .then(() => this.populatePageView(path))
  }

  createTemplate(parentPath, name, component, title) {
    let data = new FormData()
    data.append('name', name)
    data.append('component', component)
    data.append('title', title)
    return updateWithForm('/admin/createTemplate.json' + parentPath, data)
        .then(() => this.populateNodesForBrowser(parentPath))
  }

  createObjectDefinition(parentPath, name) {
    let data = new FormData()
    data.append('name', name)
    return updateWithForm('/admin/createObjectDefinition.json' + parentPath,
        data)
        .then(() => this.populateNodesForBrowser(parentPath))
  }

  moveTemplate(path, to, type) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      data.append('to', to)
      data.append('type', type)
      updateWithForm('/admin/move.json' + path, data)
          .then((data) => this.populateNodesForBrowser(path))
          .then(() => resolve())
    })
  }

  deleteTemplate(path) {
    return new Promise((resolve, reject) => {
      let data = new FormData()
      updateWithForm('/admin/deleteNode.json' + path, data)
          .then((data) => this.populateNodesForBrowser(path))
          .then(() => resolve())
    })
  }

  createFolder(parentPath, name) {
    let data = new FormData()
    data.append('name', name)
    return updateWithForm('/admin/createFolder.json' + parentPath, data)
        .then(() => this.populateNodesForBrowser(parentPath))
  }

  deleteFolder(path) {
    let data = new FormData()
    return updateWithForm('/admin/deleteNode.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  deleteFile(path) {
    let data = new FormData()
    return updateWithForm('/admin/deleteNode.json' + path, data)
        .then(() => this.populateNodesForBrowser(path))
  }

  uploadFiles(path, files, callback) {
    const me = this

    function onUploadProgress(progressEvent) {
      callback(Math.floor((progressEvent.loaded * 100) / progressEvent.total))
    }

    function addFile(file) {
      return new Promise((resolve, reject) => {
        if (me.nameAvailable(file.name, path)) {
          resolve(file)
        } else {
          me.onFileExists(file, path).then((file) => {
            resolve(file)
          })
        }
      })
    }

    function fileListToFormData(fileList) {
      const formData = new FormData()
      fileList.forEach((file) => formData.append(file.name, file, file.name))
      return formData
    }

    return this.populateNodesForBrowser(path)
        .then(() => {
          const promises = []
          files = Array.from(files)
          let chain = Promise.resolve()
          Array.from(files).forEach((file) => {
            chain = chain.then((newFiles) => {
              const promise = addFile(file)
              promises.push(promise)
              return promise
            })
          })
          return chain.then((data) => Promise.all(promises))
        })
        .then((addedFiles) => {
          if (addedFiles.length > 0) {
            const uri = `/admin/uploadFiles.json${path}`
            const formData = fileListToFormData(addedFiles)
            const config = {onUploadProgress}
            return updateWithFormAndConfig(uri, formData, config)
                .then((data) => ({addedFiles, data}))
          }
        })
        .then(({addedFiles, data}) => {
          if (data && data.assets) {
            return this.populateNodesForBrowser(path).then(() => addedFiles)
          } else {
            throw 'updateWithFormAndConfig has been rejected'
          }
        })
        .catch(error => {
          logger.error(`Failed to upload: ${error}`)
          return {errors: {msg: 'Unable to upload', error}}
        })
  }

  nameAvailable(value, path) {
    if (!value || value.length === 0) {
      return false
    } else {
      const nodes = $perAdminApp.getView().admin.nodes
      const folder = $perAdminApp.findNodeFromPath(nodes, path)
      for (let i = 0; i < folder.children.length; i++) {
        if (folder.children[i].name === value) {
          return false
        }
      }
    }
    return true
  }

  onFileExists(file, path) {
    const me = this
    return new Promise((resolve, reject) => {
      $perAdminApp.askUser(
          `File "${file.name}" already exists`,
          'Do you want to replace the original or keep both?',
          {
            yesText: 'Replace',
            noText: 'Keep both',
            yes() {
              logger.info(`onFileExists: user selected 'replace'`)
              resolve(file)
            },
            no() {
              logger.info('onFileExists: user selected \'keep both\'')
              resolve(me.createFileCopy(file, path))
            }
          }
      )
    })
  }

  createFileCopy(file, path) {
    const split = file.name.split('.')
    const extension = split.pop()
    const rawName = split.join('.')
    let name = `${rawName}-copy.${extension}`
    let counter = 2
    while (!this.nameAvailable(name, path)) {
      name = `${rawName}-copy-${counter}.${extension}`
      counter++
    }
    return new File([file], name, {type: file.type})
  }

  fetchExternalImage(path, url, name, config) {
    return axios.get(url, {responseType: 'blob'})
        .then((response) => {
          var data = new FormData()
          data.append(name, response.data, name)

          return updateWithFormAndConfig('/admin/uploadFiles.json' + path, data,
              config)
              .then(() => this.populateNodesForBrowser(path))
        })
  }

  setInitialPageEditorState(path) {
    return new Promise((resolve, reject) => {
      populateView('/state', 'editorVisible', false)
      populateView('/state', 'rightPanelVisible', true)
      populateView('/state', 'rightPanelFullscreen', false)
      populateView('/state', 'editor', {})

      try {
        const page = path
        const pagePath = page.split('/')
        const type = pagePath[3]
        pagePath.pop()
        if (type === 'pages') {
          callbacks.getView().state.tools.pages = pagePath.join('/')
        } else if (type === 'templates') {
          callbacks.getView().state.tools.templates = pagePath.join('/')
        }
        return $perAdminApp.stateAction('showPageInfo', {selected: page}).then(
            () => {
              resolve()
            })
      } catch (error) {
        logger.error('setting of path in initial page editor state failed')
        logger.error(error)
        reject()
      }
    })
  }

  savePageEdit(path, node) {
    return new Promise((resolve, reject) => {
      // convert to a new object
      let nodeData = JSON.parse(JSON.stringify(node))
      if (nodeData.component) {
        let component = callbacks.getComponentByName(nodeData.component)
        if (component && component.methods && component.methods.beforeSave) {
          nodeData = component.methods.beforeSave(nodeData)
        }
      }

      let isPage = false
      if (nodeData.path === '/jcr:content') {
        nodeData['jcr:primaryType'] = 'per:PageContent'
        isPage = true
      } else {
        nodeData['jcr:primaryType'] = 'nt:unstructured'
      }

      delete nodeData['children']
      delete nodeData['path']
      delete nodeData['component']
      if (node.component) {
        nodeData['sling:resourceType'] = node.component.split('-').join('/')
      }
      stripNulls(nodeData)

      // Sanitize the target path for backend calls
      const targetNodePath = (path + node.path).replace(/\/\//g, '/');

      axios.get(pagePathToDataPath(path))
        .then(res => res.data)
        .catch(e => {
          console.warn("Could not fetch current content for diffing", e);
          return {};
        })
        .then((pageViewPage) => {
          let currentData = {};
          try {
            currentData = $perAdminApp.findNodeFromPath(pageViewPage, node.path) || {};
          } catch (e) {
            console.warn("Could not find current content for diffing", e);
          }

          // 3. Determine which specific nested paths need translation
          const pathsToTranslateMap = getModifiedPaths({
            oldObj: currentData,
            newObj: nodeData,
            pagePath: path,
            currentObjPath: targetNodePath
          });
          const pathsToTranslate = Object.keys(pathsToTranslateMap);
          console.log("Paths marked for translation diff:", pathsToTranslateMap);

          // 4. Processing logic run asynchronously after the form updates are completed
          function processTranslations() {
            if (pathsToTranslate.length === 0) {
              console.log("No translatable string changes detected. Skipping translation.");
              return;
            }

            console.log("Starting auto-translation for modified paths...");
            // $perAdminApp.toast('Auto-translation in progress...', Toast.Level.INFO);

            // 1. Get Model -> 2. List Translations ONCE -> 3. Loop and Auto-Translate
            getTranslationModel()
              .then(model => listTranslations(path, model))
              .then(translations => {
                const translationTasks = [];

                pathsToTranslate.forEach(translatePath => {
                  const changedProps = pathsToTranslateMap[translatePath];

                  const task = autoTranslate(translatePath, translations, changedProps)
                    .then(results => {
                      if (typeof results === 'string') {
                        console.log(results);
                        return 'skipped';
                      }

                      if (Array.isArray(results) && results.length > 0) {
                        // Check if any specific language failed (autoTranslate returns "Failed..." strings on error)
                        const hasErrors = results.some(r => typeof r === 'string' && r.startsWith('Failed'));
                        const hasSuccess = results.some(r => typeof r !== 'string' || !r.startsWith('Failed'));

                        if (hasErrors && !hasSuccess) return 'error';
                        if (hasErrors && hasSuccess) return 'partial';
                        return 'success';
                      }
                      return 'skipped';
                    })
                    .catch(() => 'error'); // Catch any unhandled rejection

                  translationTasks.push(task);
                });

                return Promise.all(translationTasks);
              })
              .then(statuses => {
                const hasSuccess = statuses.includes('success') || statuses.includes('partial');
                const hasError = statuses.includes('error') || statuses.includes('partial');

                if (hasError && hasSuccess) {
                  $perAdminApp.toast('Auto-translation completed with some errors.', Toast.Level.WARNING);
                } else if (hasError && !hasSuccess) {
                  $perAdminApp.toast('Auto-translation failed.', Toast.Level.WARNING);
                } else if (hasSuccess) {
                  console.log('Translations generated successfully.');
                  $perAdminApp.toast('Auto-translation completed successfully.', Toast.Level.SUCCESS);
                }
              })
              .catch(error => {
                $perAdminApp.toast('Auto-translation failed: ' + error.message, Toast.Level.WARNING);
                console.error('Translation error:', error);
              });
          }

          // Execute Form Updates
          if (isPage) {
            // Delete tags
            const formDataTags = new FormData();
            formDataTags.append('content', json({tags: {_opDelete: true}}));

            updateWithForm('/admin/updateResource.json' + targetNodePath, formDataTags)
              .then(() => {
                const formData = new FormData()
                formData.append('content', json(nodeData))

                updateWithForm('/admin/updateResource.json' + targetNodePath, formData)
                  .then(() => {
                    resolve(); // Unblock the UI immediately
                    processTranslations(); // Run translations in the background
                  })
                  .catch(error => {
                    logger.error('Failed to save page: ' + error)
                    reject('Unable to save change. ' + error)
                  })
              })
              .catch((error) => {
                logger.error('Failed to save page: ' + error)
                reject('Unable to save change. ' + error)
              });
          }
          else {
            const formData = new FormData()
            formData.append('content', json(nodeData))

            updateWithForm('/admin/updateResource.json' + targetNodePath, formData)
              .then(() => {
                resolve(); // Unblock the UI immediately
                processTranslations(); // Run translations in the background
              })
              .catch(function (error) {
                logger.error('Failed to save page: ' + error)
                reject('Unable to save change. ' + error)
              });
          }
        });
    });
  }

  saveObjectEdit(path, node, schema) {
    return new Promise((resolve, reject) => {
      const formData = new FormData()
      // convert to a new object
      const nodeData = JSON.parse(JSON.stringify(node))
      stripNulls(nodeData)
      delete nodeData['jcr:created']
      delete nodeData['jcr:createdBy']
      delete nodeData['jcr:lastModified']
      delete nodeData['jcr:lastModifiedBy']

      if (schema && schema.fields && schema.fields.forEach) schema.fields.forEach((field) => {
        if (nodeData[field.model] && field.multifield && field.serialized) {
          const list = [];
          Object.values(nodeData[field.model]).forEach((item) => {
            list.push(item)
          });
          nodeData[field.model] = JSON.stringify(list)
        }
      })

      if (nodeData.tags) {
        const tags = []
        Object.keys(nodeData.tags).forEach((tag) => {
          tags.push(nodeData.tags[tag])
        });
        nodeData.tags = JSON.stringify(tags)
      } else {
        nodeData.tags = JSON.stringify([])
      }

      formData.append('content', json(nodeData))

      // Fetch the current object state for diffing before saving
      fetch('/admin/getObject.json' + path)
        .catch(e => {
          console.warn("Could not fetch current object content for diffing", e);
          return {};
        })
        .then(currentData => {
          // Check if the object has a valid translation reference in the data
          const hasTranslateRef = currentData['per:TranslateRef']
            && currentData['per:TranslateRef'].trim() !== ''
            && nodeData['per:TranslateRef']
            && nodeData['per:TranslateRef'].trim() !== '';

          // Compare old and new object states
          const pathsToTranslateMap = getModifiedPaths({
            oldObj: currentData,
            newObj: nodeData,
            pagePath: path,
            currentObjPath: path
          });
          const pathsToTranslate = Object.keys(pathsToTranslateMap);

          // Update the backend with the new data
          updateWithForm('/admin/updateResource.json' + path, formData)
            .then(() => {
              resolve(); // Unblock the UI immediately

              // Bail early if there's no translation reference
              if (!hasTranslateRef) {
                console.log("No per:TranslateRef found on object. Skipping auto-translation.");
                return;
              }

              // Bail early if nothing actually changed
              if (pathsToTranslate.length === 0) {
                console.log("No translatable string changes detected for object. Skipping auto-translation.");
                return;
              }

              console.log("Starting auto-translation for modified object properties...");
              $perAdminApp.toast('Auto-translation in progress...', Toast.Level.INFO);

              // 1. Get Model -> 2. List Translations ONCE -> 3. Loop and Auto-Translate
              getTranslationModel()
                .then(model => listTranslations(path, model))
                .then(translations => {
                  const translationTasks = [];

                  pathsToTranslate.forEach(translatePath => {
                    const changedProps = pathsToTranslateMap[translatePath];

                    const task = autoTranslate(translatePath, translations, changedProps)
                      .then(results => {
                        if (typeof results === 'string') {
                          console.log(results);
                          return 'skipped';
                        }

                        if (Array.isArray(results) && results.length > 0) {
                          const hasErrors = results.some(r => typeof r === 'string' && r.startsWith('Failed'));
                          const hasSuccess = results.some(r => typeof r !== 'string' || !r.startsWith('Failed'));

                          if (hasErrors && !hasSuccess) return 'error';
                          if (hasErrors && hasSuccess) return 'partial';
                          return 'success';
                        }
                        return 'skipped';
                      })
                      .catch(() => 'error');

                    translationTasks.push(task);
                  });

                  return Promise.all(translationTasks);
                })
                .then(statuses => {
                  const hasSuccess = statuses.includes('success') || statuses.includes('partial');
                  const hasError = statuses.includes('error') || statuses.includes('partial');

                  if (hasError && hasSuccess) {
                    $perAdminApp.toast('Auto-translation completed with some errors.', Toast.Level.WARNING);
                  } else if (hasError && !hasSuccess) {
                    $perAdminApp.toast('Auto-translation failed.', Toast.Level.WARNING);
                  } else if (hasSuccess) {
                    console.log('Translations generated successfully.');
                    $perAdminApp.toast('Auto-translation completed successfully.', Toast.Level.SUCCESS);
                  }
                })
                .catch(error => {
                  $perAdminApp.toast('Auto-translation failed: ' + error.message, Toast.Level.WARNING);
                  console.error('Translation error:', error);
                });
            })
            .catch(reject);
        });
    });
  }

  saveAssetProperties(node) {
    return new Promise((resolve, reject) => {
      let formData = new FormData()
      // convert to a new object
      let nodeData = JSON.parse(JSON.stringify(node))
      stripNulls(nodeData)
      delete nodeData['name']
      delete nodeData['path']
      delete nodeData['created']
      delete nodeData['createdBy']
      delete nodeData['lastModified']
      delete nodeData['lastModifiedBy']
      formData.append('content', json(nodeData))
      updateWithForm('/admin/updateResource.json' + node.path + '/jcr:content',
          formData)
          .then(() => resolve())
    })
  }

  insertNodeAt(path, component, drop, variation) {
    logger.fine(arguments)
    let formData = new FormData()
    formData.append('component', component)
    formData.append('drop', drop)
    if (variation) {
      formData.append('variation', variation)
    }
    return new Promise((resolve) => {
      updateWithForm('/admin/insertNodeAt.json' + path, formData)
          .then(function (data) {
            resolve(data)
          }).catch(() => {
            resolve({})
          }
      )
    })
  }

  insertNodeWithDataAt(path, data, drop) {
    logger.fine(arguments)
    let formData = new FormData()
    formData.append('content', json(data))
    formData.append('drop', drop)
    return new Promise((resolve) => {
      updateWithForm('/admin/insertNodeAt.json' + path, formData)
          .then((data) => resolve(data))
          .catch(() => resolve({}))
    })
  }

  moveNodeTo(path, component, drop) {
    logger.fine(
        'Move Node To: path: ' + path + ', component: ' + component + ', drop: '
        + drop)
    let formData = new FormData()
    formData.append('component', component)
    formData.append('drop', drop)
    return updateWithForm('/admin/moveNodeTo.json' + path, formData)
  }

  replicate(path, deep = false, deactivate = false, resources = [], draft = true, callback = true) {
    const timeNow = Date.now() - 1000
    let noticeFunction = undefined
    let count = 0
    console.log(`time now = ${timeNow}`)
    return new Promise((resolve, reject) => {
      let formData = new FormData()
      formData.append('deep', deep)
      formData.append('deactivate', deactivate)
      formData.append('draft', draft)
      formData.append('callback', callback)
      resources.forEach((ref) => formData.append('resources', ref))
      updateWithForm('/admin/repl.json' + path, formData)
          .then(respData => {
            count = 0
            noticeFunction = setInterval(function () {
              if (!document.hasFocus()) {
                return;
              }

              function stopPolling(data) {
                const lastAction = data['per:ReplicationLastAction']
                const activated = data['activated']
                const ref = data['per:ReplicationRef']
                const replicated = data['per:Replicated']
                if (lastAction === 'deactivated' && activated === false
                    && !ref) {
                  return true
                }

                if (lastAction === 'activated' && activated === true
                    && replicated && timeNow < Date.parse(replicated)
                    && ref !== 'distribution pending') {
                  return true
                }

                return false
              }

              // Use lastResource if any to check replication status
              const lastResource = resources[resources.length - 1];
              return fetch(
                  `/admin/listReplicationStatus.json${respData.sourcePath}${lastResource ? `?lastResource=${lastResource}` : ''}`)
                  .then(data => {
                    if (count++ >= 25) {
                      clearInterval(noticeFunction)
                      $perAdminApp.notifyUser('Error',
                          `Action timed out when ${deactivate ? 'un'
                              : ''}publishing ${data.sourcePath}.`)
                      reject()
                    } else if (stopPolling(data)) {
                      clearInterval(noticeFunction)
                      const parentPath = path.substring(0,
                          path.lastIndexOf('/'))
                      $perAdminApp.getApi().populateNodesForBrowser(parentPath)
                      $perAdminApp.notifyUser('Success',
                          `${respData.sourcePath} was successfully ${deactivate
                              ? 'un' : ''}published.`)
                    }
                  })
            }, 1000)
          })
          .then(() => resolve())
          .catch(error => {
            clearInterval(noticeFunction)
            $perAdminApp.notifyUser('Errors',
                `were encountered when ${deactivate ? 'un'
                    : ''}publishing ${path}. Please check with your admin.`)
            if (error.response && error.response.data
                && error.response.data.message) {
              reject(error.response.data.message)
            }
            reject(error)
          })
    })
  }

  getPalettes(templateName) {
    return fetch(`/admin/nodes.json/content/${templateName}/pages/css/palettes`)
        .then((data) => {
          return $perAdminApp.findNodeFromPath(data,
              `/content/${templateName}/pages/css/palettes`)
        }).catch((err) => {
          logger.warn(`template ${templateName} does not support palettes`)
        })
  }

  populateIcons(tenant) {
    return fetch(`/admin/nodes.json/content/${tenant.name}/assets/icons`)
        .then((data) => {
          const iconsNode = $perAdminApp.findNodeFromPath(data,
              `/content/${tenant.name}/assets/icons`)
          const icons = iconsNode.children
          Vue.set($perAdminApp.getView().admin, 'icons', icons)
          logger.debug(`populated icons for tenant ${tenant.name}:`, icons)
        }).catch((err) => {
          logger.warn(`tenant ${tenant.name} does not have any icons`)
        })
  }

  tenantSetupReplication(path, withSite) {
    let formData = new FormData()
    formData.append('withSite', withSite)
    return updateWithForm('/admin/tenantSetupReplication.json' + path, formData)
  }

  backupTenant(path) {
    let formData = new FormData()
    return updateWithForm('/admin/backupTenant.json' + path, formData)
  }

  downloadBackupTenant(path) {
    return fetch('/admin/downloadBackupTenant.zip' + path + '.zip')
  }

  uploadBackupTenant(path, files, cb) {
    const config = {
      onUploadProgress: progressEvent => {
        const percentCompleted = Math.floor(
            (progressEvent.loaded * 100) / progressEvent.total)
        cb(percentCompleted)
      }
    }
    const data = new FormData()
    if (files.length > 0) {
      const file = files[0]
      data.append('file', file, file.name)
      data.append('force', 'true')
    }
    if (!data.entries().next().done) {
      return updateWithFormAndConfig('/admin/uploadBackupTenant.json' + path,
          data,
          config)
          .then(() => this.populateNodesForBrowser(path))
          .catch(error => {
//            logger.error('Failed to upload: ' + error)
            reject('Unable to upload due to an error. ' + error)
          })
    }
    return
  }

  restoreTenant(path) {
    let formData = new FormData()
    return updateWithForm('/admin/restoreTenant.json' + path, formData)
  }

  acceptTermsAndConditions() {
    let formData = new FormData()
    return updateWithForm('/admin/acceptTermsAndConditions.json',
        formData).then(() => {
      return this.populateUser()
    })
  }

  checkTenantNameAvailability(name) {
    return fetch('/admin/tenants/name/available.json?name=' + name)
  }

  isReferencedInPublish(path) {
    return fetch(`/admin/isReferencedInPublish.json${path}`)
  }

  _postFormDataImpl(url, data, config) {
    return postFormData(url, data, config);
  }
}

export default PerAdminImpl
