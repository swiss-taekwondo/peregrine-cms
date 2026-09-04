<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2017 headwire inc.
  %%
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  #L%
  -->
<template>
  <div class="editor-panel" ref="editorPanel" v-if="path">
    <div class="editor-panel-content">
      <template v-if="schema && dataModel">
        <span v-if="title" class="panel-title">{{ title }}</span>
        <div v-if="!hasSchema">this component does not have a dialog defined</div>
        <vue-form-generator
            ref="vfg"
            :schema="schema"
            :model="dataModel"
            :options="formOptions"
        />
      </template>
    </div>
    <div class="editor-panel-buttons">
      <button v-if="!isRootComponent" class="waves-effect waves-light btn btn-raised"
              :title="$i18n('deleteComponent')" @click.stop.prevent="onDelete">
        <i class="material-icons">delete</i>
      </button>
      <button class="waves-effect waves-light btn btn-raised" :title="$i18n('cancel')"
              @click.stop.prevent="onCancel">
        <i class="material-icons">close</i>
      </button>
      <template>
        <button v-if="hasSchema" class="waves-effect waves-light btn btn-raised"
                :title="$i18n('save')" @click.stop.prevent="onOk">
          <i class="material-icons">check</i>
        </button>
        <button v-else class="btn btn-raised disabled" style="opacity: 0">
          <i class="material-icons">check</i>
        </button>
      </template>
    </div>
  </div>
</template>

<script>
import {set} from '../../../../../../js/utils'

export default {
  props: ['model'],
  updated: function () {
    setTimeout(() => {
      const node = $perAdminApp.getNodeFromViewOrNull('/state/editor') || {}
      this.path = node.path
    }, 0)
  },
  data() {
    return {
      path: $perAdminApp.getNodeFromViewOrNull('/state/editor').path,
      isTouch: false,
      formOptions: {
        validateAfterLoad: true,
        validateAfterChanged: true,
        focusFirstField: true
      },
      initialLoadComplete: false,
      focus: {
        loop: null,
        timeout: null,
        interval: 100,
        delay: 700,
        inView: 0
      }
    }
  },
  computed: {
    view() {
      return $perAdminApp.getView()
    },
    schema: function () {
      const view = this.view;
      const component = view.state.editor.component;
      const schema = view.admin.componentDefinitions[component].model;
      return schema;
    },
    dataModel: function () {
      return this.findPageNodeFromPath(this.path)
    },
    hasSchema: function () {
      if (this.schema) return true
      return false
    },
    isRootComponent: function () {
      return $perAdminApp.getView().state.editor.path == '/jcr:content'
    },
    title: function () {
      var view = $perAdminApp.getView()
      var componentName = view.state.editor.component.split('-').join('/')
      const components = view.admin.components.data
      for (let i = 0; i < components.length; i++) {
        const component = components[i]
        if (component.path.endsWith(componentName) && component.group !== '.hidden') {
          return component.title
        }
      }
    }
  },
  watch: {
    'view.state.inline.model'(val) {
      if (!val) return
      this.focusFieldByModel(val)
    },
    dataModel: {
      handler(val) {
        if (val && this.initialLoadComplete) {
          set($perAdminApp.getView(), '/state/editor/hasChanges', true)
        }
      },
      deep: true
    }
  },
  mounted() {
    let stateTools = $perAdminApp.getNodeFromViewWithDefault('/state/tools', {})
    stateTools._deleted = {}
    this.isTouch = 'ontouchstart' in window || navigator.maxTouchPoints
    if (this.schema && this.schema.hasOwnProperty('groups')) {
      this.hideGroups()
    }
    setTimeout(() => {
      this.initialLoadComplete = true
    }, 100)
  },
  methods: {
    findPageNodeFromPath(path) {
      const pageView = $perAdminApp.getNodeFromViewOrNull('/pageView') || {}
      const pageRoot = pageView.page
      const pagePath = pageView.path
      if (!pageRoot || !path) return null
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
        const node = $perAdminApp.findNodeFromPath(pageRoot, candidates[i])
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
    },
    onOk(e) {
      if (this.$refs.vfg && this.$refs.vfg.validate) {
        const isValid = this.$refs.vfg.validate()
        if (!isValid) {
          console.log('[VALIDATION] Form has validation errors, preventing save')
          $perAdminApp.notifyUser('error', 'Please fix validation errors before saving')
          return
        }
      }
      let data = JSON.parse(JSON.stringify(this.dataModel))
      let _deleted = $perAdminApp.getNodeFromViewWithDefault('/state/tools/_deleted', {})

      // Merge _deleted child items back into the object that we need to save recursively.
      const mergeDeleted = (currentNode, nodePath) => {
        if (!currentNode || typeof currentNode !== 'object') return;

        for (const key in currentNode) {
          const hasDeletedItems = _deleted && Array.isArray(_deleted[key]) && _deleted[key].length > 0;
          const isArray = Array.isArray(currentNode[key]);

          if (isArray || hasDeletedItems) {
            // 1. Prevent empty arrays from polluting the JCR
            if (isArray && currentNode[key].length === 0 && !hasDeletedItems) {
              delete currentNode[key];
              continue;
            }

            let nodeArray = currentNode[key] || [];
            let targetMap = {};

            if (hasDeletedItems) {
              for (const deleted of _deleted[key]) {
                const expectedPath = nodePath + '/' + key + '/' + deleted.name;
                if (deleted.path === expectedPath) {
                  targetMap[deleted.name] = deleted;
                }
              }
            }

            for (const i in nodeArray) {
              const child = nodeArray[i];
              targetMap[child.name] = child;
              const childPath = nodePath + '/' + key + '/' + child.name;
              mergeDeleted(child, childPath);
            }

            // 4. Send EVERYTHING natively as an Array!
            currentNode[key] = Object.values(targetMap);

          } else if (typeof currentNode[key] === 'object' && currentNode[key] !== null) {
            mergeDeleted(currentNode[key], nodePath + '/' + key);
          }
        }
      };

      mergeDeleted(data, this.path);

      var view = $perAdminApp.getView()
      $perAdminApp.action(this, 'onEditorExitFullscreen')
      $perAdminApp.stateAction('savePageEdit', {data: data, path: view.state.editor.path}).then(() => {
        set(view, '/state/editor/hasChanges', false)
        $perAdminApp.action(this, 'unselect')
        $perAdminApp.getNodeFromView('/state/tools')._deleted = {}
      })
    },

    onCancel(e) {
      var view = $perAdminApp.getView()
      $perAdminApp.action(this, 'onEditorExitFullscreen')
      $perAdminApp.action(this, 'unselect')
      $perAdminApp.stateAction('cancelPageEdit', {
        pagePath: view.pageView.path,
        path: view.state.editor.path
      }).then(() => {
        $perAdminApp.getNodeFromView('/state/tools')._deleted = {}
      })
    },

    onDelete(e) {
      const vm = this
      const view = $perAdminApp.getView()
      const pagePath = view.pageView.path
      const componentPath = view.state.editor.path

      let undoEntry = null
      const captureUndoData = componentPath !== '/jcr:content'
        ? new Promise((resolve) => {
          const jcrPath = pagePath + componentPath
          fetch(jcrPath + '.infinity.json')
            .then(response => response.json())
            .then(jcrData => {
          const nodeData = vm.jcrToInsertData(jcrData, componentPath)

          const DROPTARGET = 'data-per-droptarget'
          const PATH = 'data-per-path'
          const editview = document.getElementById('editview')
          const iframeDoc = editview && (editview.contentDocument || editview.contentWindow.document)
          let dropPath = null
          let drop = 'into'
          const el = iframeDoc && iframeDoc.querySelector(`[data-per-path="${componentPath}"]`)
          if (el) {
            let sibling = el.nextElementSibling
            while (sibling) {
              if (sibling.hasAttribute(PATH) && !sibling.hasAttribute(DROPTARGET)) {
                dropPath = sibling.getAttribute(PATH)
                drop = 'before'
                break
              }
              sibling = sibling.nextElementSibling
            }
            if (!dropPath) {
              sibling = el.previousElementSibling
              while (sibling) {
                if (sibling.hasAttribute(PATH) && !sibling.hasAttribute(DROPTARGET)) {
                  dropPath = sibling.getAttribute(PATH)
                  drop = 'after'
                  break
                }
                sibling = sibling.previousElementSibling
              }
            }
            if (!dropPath) {
              const parentEl = el.parentElement ? el.parentElement.closest(`[${DROPTARGET}]`) : null
              if (parentEl) {
                dropPath = parentEl.getAttribute(PATH) || parentEl.getAttribute(DROPTARGET)
              }
              if (!dropPath) {
                dropPath = componentPath.substring(0, componentPath.lastIndexOf('/'))
              }
              drop = 'into'
            }
          } else {
            dropPath = componentPath.substring(0, componentPath.lastIndexOf('/'))
            drop = 'into'
          }
          undoEntry = { pagePath, dropPath, drop, data: nodeData }
              resolve()
            })
            .catch(err => {
          console.warn('Failed to capture undo data for deletion', err)
              resolve()
            })
        })
        : Promise.resolve()

      let blockDelete = false
      let deleteMessage = 'Are you sure you want to delete the component?'
      captureUndoData.then(() => {
        const isTemplateOrSkeleton = pagePath.includes('/skeleton-pages/') || pagePath.includes('/templates/')
        if (isTemplateOrSkeleton && componentPath !== '/jcr:content') {
          const fullJcrPath = pagePath + componentPath
          return fetch(
            '/perapi/admin/isComponentUsedInSkeleton.json?path='
            + encodeURIComponent(fullJcrPath)
          )
            .then(skeletonResponse => skeletonResponse.json())
            .then(skeletonData => {
          if (skeletonData && skeletonData.isTopLevelInSkeleton) {
            blockDelete = true
            const pageList = (skeletonData.skeletonPages || []).map(p => p.title || p.path).join(', ')
            deleteMessage = 'This component cannot be deleted because it is used in a skeleton page'
              + (pageList ? ': ' + pageList : '')
              + '. Removing it could break every page created from that skeleton.'
          }
            })
            .catch(err => {
          console.warn('Failed to check skeleton usage', err)
            })
        }
        return Promise.resolve()
      }).then(() => {

      $perAdminApp.askUser(
        blockDelete ? 'Cannot Delete Component' : 'Delete Component?',
        deleteMessage,
        {
        yesText: 'Yes',
        noText: blockDelete ? 'Close' : 'No',
        warning: blockDelete,
        blockDelete,
        yes() {
          $perAdminApp.action(vm, 'onEditorExitFullscreen')
          $perAdminApp.stateAction('deletePageNode', {
            pagePath,
            path: componentPath
          }).then(() => {
            $perAdminApp.action(vm, 'unselect')
            $perAdminApp.getNodeFromView('/state/tools')._deleted = {}
            if (undoEntry) {
              window.dispatchEvent(new CustomEvent('per:component-deleted', { detail: undoEntry }))
            }
          })
        },
        no() {}
      })
      })
    },

    jcrToInsertData(jcrNode, path) {
      const IGNORED_KEYS = new Set([
        'jcr:primaryType', 'jcr:uuid', 'jcr:created', 'jcr:createdBy',
        'jcr:baseVersion', 'jcr:isCheckedOut', 'jcr:predecessors',
        'jcr:versionHistory', 'per:Replicated', 'per:ReplicatedBy',
        'per:ReplicationLastAction', 'per:ReplicationRef', 'per:ReplicationStatus',
      ])
      const result = { path }
      const children = []
      const entries = Object.entries(jcrNode)
      for (let entryIndex = 0; entryIndex < entries.length; entryIndex++) {
        const key = entries[entryIndex][0]
        const value = entries[entryIndex][1]
        if (IGNORED_KEYS.has(key)) continue
        if (key === 'sling:resourceType') {
          result.component = value
        } else if (value !== null && typeof value === 'object' && !Array.isArray(value) && value['jcr:primaryType']) {
          children.push(this.jcrToInsertData(value, path + '/' + key))
        } else {
          result[key] = value
        }
      }
      if (children.length > 0) result.children = children
      return result
    },

    hideGroups() {
        var $vueFormGenerators = $('.vue-form-generator');
        $vueFormGenerators.each(function() {
            var $groups = $(this).children('fieldset');
            $groups.each(function (i) {
                var $group = $(this);
                var $title = $group.find('legend');
                $title.click(function () {
                    var isActive = $group.hasClass('active');
                    $groups.filter('.active').removeClass('active');
                    if (!isActive) {
                        $group.addClass('active');
                    }
                });
                if (i !== 0) {
                    $group.removeClass('active');
                }
                if (i === 0) {
                    $group.addClass('active');
                }
                $group.addClass('vfg-group');
            });
        });
    },

    getFieldAndIndexByModel(schema, model) {
      const formGenerator = this.$refs.formGenerator
      const fields = schema.fields
      let field
      let index = -1
      fields.some((f) => {
        if (f.visible) {
          if (typeof f.visible === 'string') {
            if (exprEval.Parser.evaluate(f.visible, formGenerator) === true) {
              index++
            }
          } else if (formGenerator.fieldVisible(f) === true) {
            index++
          }
        } else {
          index++
        }
        if (f.model === model) {
          return field = f
        }
      })
      return {field, index}
    },

    getFieldComponent($vfg, model) {
      if (!$vfg || !$vfg.$children) return null
      return $vfg.$children.find(($c) => $c.field && $c.field.model === model)
    },

    focusFieldByModel(model) {
      if (!model) return

      setTimeout(() => {
        model = model.split('.')
        model.reverse()
        if (model[model.length - 1] === 'model') {
          model.pop()
        }
        const $field = this.getFieldComponent(this.$refs.vfg, model.pop())
        if ($field && $field.field && $field.field.type) {
          const fieldType = $field.field.type
          this.openFieldGroup($field.$el)
          this.$nextTick(() => {
            $field.$el.scrollIntoView()
            if (['input', 'texteditor', 'material-textarea'].indexOf(fieldType) >= 0) {
              this.$nextTick(() => {
                set(this.view, '/state/inline/rich', this.isRichEditor($field.field))
              })
            } else if (fieldType === 'collection') {
              this.focusCollectionField(model, $field)
            } else {
<<<<<<< HEAD
              console.warn('Unsupported field type: ', fieldType)
=======
              console.warn('Unsupported field type: ', $field.field.type)
>>>>>>> d4b83753ff08324a21699f76a51a0447871becd9
            }
          })

          set(this.view, '/state/inline/model', null)
        }
      }, 0)
    },

    focusCollectionField(model, $field) {
      this.$nextTick(() => {
        const $collection = $field.$children[0]
        if (!$collection) return
        const activeItemIndex = parseInt(model.pop())
        if ($collection.activeItem !== activeItemIndex) {
          $collection.activeItem = activeItemIndex
        }
        setTimeout(() => {
          const $collectionVfg = $collection.$children[0]
          const $collectionField = this.getFieldComponent($collectionVfg, model.pop())
          if (!$collectionField || !$collectionField.field) return
          if ($collectionField.field.type === 'collection') {
            this.focusCollectionField(model, $collectionField);
            return;
          }
          set(this.view, '/state/inline/rich', this.isRichEditor($collectionField.field))
          this.clearFocusStuff()
          this.focus.loop = setInterval(() => {
            $collectionField.$el.scrollIntoView()
          }, this.focus.interval)
          this.focus.timeout = setTimeout(() => {
            this.clearFocusStuff()
          }, this.focus.delay)
        }, 0)
      })
    },

    clearFocusStuff() {
      this.focus.inView = 0
      clearInterval(this.focus.loop)
      clearTimeout(this.focus.timeout)
    },

    isRichEditor(field) {
      return ['texteditor'].indexOf(field.type) >= 0
    },

    openFieldGroup(el) {
      let group = el.parentNode

      while (group.tagName !== 'FIELDSET') {
        group = group.parentNode
      }

      if (group.classList.contains('vfg-group') && !group.classList.contains('active')) {
        group.querySelector('legend').click()
      }
    },
  }
//      ,
//      beforeMount: function() {
//        if(!perAdminView.state.editor) this.$set(perAdminView.state, 'editor', { })
//      }
}
</script>
