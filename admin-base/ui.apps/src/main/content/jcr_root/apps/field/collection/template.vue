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
  <div class="wrap">
    <label>{{ schema.title }} </label>
    <ul v-if="!schema.preview"
        class="collapsible"
        :style="schema.allowAdd === false ? { marginBottom: '0' } : {}"
        :class="schema.multifield ? 'multifield' : 'singlefield'"
        ref="collapsible">
      <li v-for="(item, index) in value"
          class="collection-item"
          :class="getItemClass(item, index)"
          :key="`collection-item-${index}`"
          ref="item"
          draggable="false"> {{ item._opDelete }}
        <div
            ref="header"
            class="collapsible-header"
            draggable="true"
            @dragstart.stop="onDragStart(item, index, $event)"
            @dragover.stop.prevent="onDragOver($event, index)"
            @dragenter.stop.prevent="onDragEnter"
            @dragleave.stop.prevent="onDragLeave($event, index)"
            @drop.stop.prevent="onDrop($event, index, item)"
            @click.stop.prevent="onSetActiveItem(index)">
          <i class="material-icons">drag_handle</i>
          <span v-if="schema.multifield" :title="itemName(item, index)">{{ itemName(item, index) }}</span>
          <input
              v-else
              ref="input"
              v-model="value[index]">
          <i class="material-icons delete-icon" @click.stop="onRemoveItem(item, index)">delete</i>
        </div>
        <transition
            @enter="enter"
            @leave="leave"
            :css="false">
          <div v-if="schema.multifield && activeItem === index" class="collapsible-body">
            <vue-form-generator
                :schema="schema"
                :model="prepModel(item, schema)"></vue-form-generator>
          </div>
        </transition>
      </li>
      <button v-if="schema.allowAdd !== false || (schema.assetSections && !model.assets)" type="button" class="btn-flat btn-add-item" @click="onAddItem">
        <i class="material-icons">add</i>
      </button>
    </ul>
    <ul v-else class="collection">
      <template v-for="(item,i) in value">

        <ul v-if="typeof item === 'object'" class="collection z-depth-1" :key="item.name">
          <vue-form-generator
              v-if="schema.multifield"
              class="collection-item"
              :schema="schema"
              :model="prepModel(item, schema)"></vue-form-generator>
        </ul>

        <li v-else class="collection-item" :key="item+i">{{ item }}</li>

      </template>
    </ul>
  </div>
</template>

<script>
import {dataFields} from '../../../../../js/utils/dialogFields'

export default {
  mixins: [VueFormGenerator.abstractField],
  beforeMount() {
    if (!this.value) this.value = []
  },
  beforeDestroy() {
    this._assetSectionsRequest = null
    if (this._finishAssetSectionsRefresh) this._finishAssetSectionsRefresh()
  },
  data() {
    return {
      activeItem: null
    }
  },
  watch: {
    // DownloadList opts in through assetSections: refresh sections and preview downloads
    // from the asset folder, while keeping the existing entries until loading succeeds.
    'model.assets': async function (path, oldPath) {
      if (!this.schema.assetSections || path === oldPath) return
      if (this._finishAssetSectionsRefresh) this._finishAssetSectionsRefresh()
      const request = this._assetSectionsRequest = {}
      delete this.model._assetSectionsError
      // DownloadList keeps the loaded downloads and sections as editable manual entries when the asset folder is cleared.
      // Invalidate the previous request so a late response cannot replace those entries after the switch to manual mode.
      if (!path || !path.trim()) {
        delete this.model._assetSectionsPending
        return
      }
      let finishRefresh
      const pending = new Promise(resolve => { finishRefresh = resolve })
      this._finishAssetSectionsRefresh = () => {
        finishRefresh()
        if (this.model._assetSectionsPending === pending) delete this.model._assetSectionsPending
      }
      Object.defineProperty(this.model, '_assetSectionsPending', { value: pending, writable: true, configurable: true })
      try {
        const downloads = []
        const timestamp = value => {
          if (value == null || value === '') return ''
          const date = new Date(value)
          return Number.isFinite(date.getTime()) ? date.toISOString() : ''
        }
        const load = async (folderPath, section) => {
          if (this._assetSectionsRequest !== request) return new Set()
          const response = await axios.get('/perapi/admin/nodes.json' + folderPath.split('/').map(encodeURIComponent).join('/'))
          if (this._assetSectionsRequest !== request) return new Set()
          const tree = response.data
          const folder = tree.path === folderPath ? tree : $perAdminApp.findNodeFromPath(tree, folderPath)
          if (!folder) throw new Error("Asset folder could not be loaded")
          const names = new Set()
          for (const child of (folder && folder.children || [])) {
            if (child.resourceType === 'per:Asset') {
              names.add(section || this.model.title || 'Downloads')
              downloads.push({ name: child.name, title: child.title || child.name, link: child.path, section,
                defaultgroup: String(!section), text: '',
                date: timestamp(child.created), modified: timestamp(child.lastModified) })
            }
            else if (['sling:Folder', 'sling:OrderedFolder', 'nt:folder'].includes(child.resourceType)) {
              const language = ['de', 'en', 'fr', 'it'].includes(child.name.toLowerCase())
              const nested = await load(child.path, language ? section : (section ? section + ' / ' : '') + child.name)
              nested.forEach(name => names.add(name))
            }
          }
          return names
        }
        const names = await load(path, '')
        if (this._assetSectionsRequest !== request) return
        // The update API merges children, explicitly delete persisted entries being replaced.
        const persisted = (this.value || []).filter(item => item.path)
        if (persisted.length) {
          if (!this.model._deletedCollections) Vue.set(this.model, '_deletedCollections', {})
          const deleted = this.model._deletedCollections[this.schema.model] || []
          const paths = new Set(deleted.map(item => item.path))
          persisted.forEach(item => {
            if (!paths.has(item.path)) deleted.push({ ...item, _opDelete: true })
          })
          Vue.set(this.model._deletedCollections, this.schema.model, deleted)
        }
        const grouped = new Map()
        downloads.forEach(item => {
          const match = item.link.match(/^(.*)\/(de|en|fr|it)\/([^/]+)$/i)
          const filename = item.link.slice(item.link.lastIndexOf('/') + 1)
          const suffix = filename.match(/_(de|en|fr|it)(?=\.[^.]+$)/i)
          const language = match ? match[2].toLowerCase() : suffix && suffix[1].toLowerCase()
          const base = filename.replace(/_(de|en|fr|it)(?=\.[^.]+$)/i, '')
          const key = language ? (match ? match[1] : item.link.slice(0, item.link.lastIndexOf('/'))) + '/' + base : item.link
          const mapKey = (language ? 'languages:' : 'asset:') + key
          if (!grouped.has(mapKey)) grouped.set(mapKey, { ...item, groupkey: key })
          const group = grouped.get(mapKey)
          if (item.date && (!group.date || Date.parse(item.date) < Date.parse(group.date))) group.date = item.date
          if (item.modified && (!group.modified || Date.parse(item.modified) > Date.parse(group.modified))) group.modified = item.modified
          if (language) {
            group['language_' + language] = item.link
            group['title_' + language] = item.title === filename ? base : item.title
          }
        })
        Vue.set(this.model, 'downloads', Array.from(grouped.values()))
        this.activeItem = null
        this.value = Array.from(names, (section, index) => ({name: 'folder_' + Date.now() + '_' + index, section, sort: 'natural', 'sling:resourceType': this.schema.resourceType}))
      } catch (error) {
        if (this._assetSectionsRequest !== request) return
        Object.defineProperty(this.model, '_assetSectionsError', { value: true, configurable: true })
        $perAdminApp.toast('Unable to load asset sections. Existing sections have been kept.', 'error')
        console.error('Unable to load asset sections', error)
      } finally {
        finishRefresh()
        if (this.model._assetSectionsPending === pending) delete this.model._assetSectionsPending
      }
    }
  },
  computed: {
    itemModel() {
      const model = {}
      dataFields(this.schema.fields).forEach((item, index) => {
        model[item.model] = ''
      })
      model.name = 'n' + Date.now()
      return model
    }
  },
  methods: {
    getSchemaForIndex(schema, index) {
      const newSchema = JSON.parse(JSON.stringify(schema))
      newSchema.fields[0].model = '' + index
      return newSchema
    },
    getItemClass(item, index) {
      if (!this.schema.multifield) return
      if (this.activeItem === index) {
        return 'active'
      }
      if (item._opDelete) {
        return 'deleted'
      }
      return false
    },
    itemName(item, index) {
      if (this.schema.fieldLabel) {
        const len = this.schema.fieldLabel.length
        for (let i = 0; i < len; i++) {
          let label = this.schema.fieldLabel[i]
          let childItem = this.value[index]
          if (childItem[label]) {
            return childItem[label]
          }
        }
      }
      return parseInt(index) + 1
    },
    prepModel(model, schema) {
      const fields = dataFields(schema.fields);
      for (let i = 0; i < fields.length; i++) {
        const field = fields[i].model;
        const isCollection = fields[i].multifield || fields[i].type === 'collection';

        if (!model[field]) {
          // 1. Initialize missing fields. Collections become arrays to trigger Vue reactivity.
          const fallbackDefault = isCollection ? [] : '';
          Vue.set(model, field, fields[i].default ?? fallbackDefault);

        } else if (isCollection && !Array.isArray(model[field])) {
          // 2. If Sling returns an empty JCR node {}, convert it back into an array for Vue
          // This prevents the "this.value.push is not a function" error when adding items.
          Vue.set(model, field, []);
        }
      }
      return model;
    },

    onAddItem(e) {
      if (!this.schema.multifield) {
        var newChild = ''
        if (!this.value || this.value === '') {
          Vue.set(this, 'value', new Array())
        }
      } else {
        var newChild = {name: 'n' + Date.now()}
        this.prepModel(newChild, this.schema)
        newChild['sling:resourceType'] = this.schema.resourceType
      }
      if (!this.value) {
        this.value = []
      }
      this.value.push(newChild)
      this.onSetActiveItem(this.value.length - 1)
      this.$forceUpdate()
    },
    onRemoveItem(item, index) {
      if (this.schema.multifield) {
        if (item.hasOwnProperty('path')) {
          let copy = JSON.parse(JSON.stringify(item))
          copy._opDelete = true
          if (!this.model._deletedCollections) {
            Vue.set(this.model, '_deletedCollections', {})
          }
          if (!this.model._deletedCollections[this.schema.model]) {
            Vue.set(this.model._deletedCollections, this.schema.model, [])
          }
          this.model._deletedCollections[this.schema.model].push(copy)
        }
        this.value = this.value.filter((item, i) => i !== index)
        this.activeItem = null
      } else {
        this.value = this.value.filter((item, i) => i !== index)
      }
    },
    onSetActiveItem(index) {
      if (!this.schema.multifield) {
        this.$nextTick(() => {
          this.$nextTick(() => {
            if (this.$refs.input[index]) {
              this.$refs.input[index].focus()
            }
          })
        })
      } else {
        if (index === this.activeItem) {
          $(this.$refs.collapsible).collapsible('close', this.activeItem)
          this.activeItem = null
        } else {
          this.$nextTick(function () {
            if (this.activeItem !== null) {
              $(this.$refs.collapsible).collapsible('close', this.activeItem)
            }
            $(this.$refs.collapsible).collapsible('open', index)
            this.activeItem = index
            // focus first field of expanded item
            this.$nextTick(() => {
              let firstField = this.$refs.collapsible.querySelector('li.active input')
              if (firstField) firstField.focus()
            })
          })
        }
      }
    },
    onDragStart(item, index, ev) {
      const $item = this.$refs.item[index]
      $item.classList.add('dragging')
      ev.dataTransfer.setData('text', index)
    },
    onDragOver(ev, index) {
      const $item = this.$refs.item[index]
      const center = $item.offsetHeight / 2
      $item.classList.toggle('drop-after', ev.offsetY >= center)
      $item.classList.toggle('drop-before', ev.offsetY < center)
    },
    onDragEnter(ev) {
    },
    onDragLeave(ev, index) {
      const $item = this.$refs.item[index]
      $item.classList.remove('drop-after', 'drop-before')
    },
    onDrop(ev, index, item) {
      const oldIndex = parseInt(ev.dataTransfer.getData('text'))
      const $item = this.$refs.item[index]
      if ($item.classList.contains('drop-after')) {
        index += 1
      }
      $item.classList.remove('drop-after', 'drop-before')
      this.$refs.item.forEach((item) => item.classList.remove('dragging'))
      this.onReorder(oldIndex, index)
      this.$nextTick(() => {
        this.onSetActiveItem(index)
      })
    },
    /**
     * https://stackoverflow.com/a/5306832/4622620
     */
    onReorder(oldIndex, newIndex) {
      if (newIndex >= this.length) {
        var k = newIndex - this.length
        while ((k--) + 1) {
          this.value.push(undefined)
        }
      }

      this.value.splice(newIndex, 0, this.value.splice(oldIndex, 1)[0])
      this.$forceUpdate()
    },
    // animations with Velocity.js
    enter: function (el, done) {
      window.Materialize.Vel(el, 'slideDown', {duration: 250})
    },
    leave: function (el, done) {
      window.Materialize.Vel(el, 'slideUp', {duration: 250}, {complete: done})
    }
  }
}
</script>

<style scoped>
/* Open selects must be above following collection rows and add buttons. */
.wrap:has(.multiselect--active),
.wrap .collapsible .collection-item:has(.multiselect--active) {
  position: relative;
  z-index: 2;
  overflow: visible;
}

.wrap .collapsible:has(.multiselect--active),
.wrap .collapsible-body:has(.multiselect--active) {
  overflow: visible !important;
}

.wrap .collapsible .collection-item.dragging {
  transition: opacity .25s linear;
  opacity: .3;
}

.wrap .collapsible .collection-item.drop-before,
.wrap .collapsible .collection-item.drop-after {
  transition: border .1s linear;
}

.wrap .collapsible .collection-item.drop-before {
  border-top: 3px solid var(--pcms-orange);
}

.wrap .collapsible .collection-item.drop-after {
  border-bottom: 3px solid var(--pcms-orange);
}
</style>
