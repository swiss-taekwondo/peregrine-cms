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
  <transition name="modal">
    <div class="modal-mask pathbrowser-modal-mask" v-on:click.stop.prevent="onCancel">
      <div class="modal-wrapper" ref="modalWrapper">
        <div class="pathbrowser modal-container"
             ref="pathbrowser"
             @click.stop>
          <!-- @mousedown.prevent="() => {}" -->
          <div class="modal-header">
            {{ header }}
          </div>
          <ul class="pathbrowser-tabs">
            <li class="tab">
              <a href="#" :class="tab === 'browse' ? 'active' : ''" v-on:click="select('browse')">
                <i class="material-icons">list</i>
              </a>
            </li>
            <li class="tab">
              <a href="#" :class="tab === 'cards' ? 'active' : ''" v-on:click="select('cards')">
                <i class="material-icons">view_module</i>
              </a>
            </li>
            <li v-if="withLinkTab" class="tab">
              <a href="#" :class="tab === 'link' ? 'active' : ''" v-on:click="select('link')">
                <i class="material-icons">link</i>
              </a>
            </li>
            <li v-if="withImageTab" class="tab">
              <a href="#" :class="tab === 'image' ? 'active' : ''" v-on:click="select('image')">
                <i class="material-icons">image</i>
              </a>
            </li>
            <li
                class="indicator"
                :style="`transform: translateX(${tabIndicatorPosition}px)`">
            </li>
          </ul>
          <div v-if="tab === 'browse' || tab === 'cards'" class="pathbrowser-filter" :style="`width: calc(100% - ${searchTabOffset}px)`">
            <input placeholder="search" type="text" v-model="search">
            <button v-if="search" type="button" class="clear-search" aria-label="Clear search" @click="search = ''">
              <i class="material-icons">close</i>
            </button>
          </div>
          <div v-if="tab === 'browse' || tab === 'cards'" class="asset-browser-header">
            <div class="current-folder-nav">
              <div class="current-folder">
                <template v-if="!isRoot">
                  <a href="#!" v-on:click.stop.prevent="selectParent"><i class="material-icons">keyboard_arrow_left</i></a>
                  {{ currentPath }} ({{ itemCountLabel }})
                </template>
                <template v-else>
                  <input v-if="allowFolderSelection && nodes && nodes.path" :id="`${instanceId}-root-selection`" type="radio" class="with-gap" :checked="isSelected(currentPath)" @click.stop @change="selectItem(nodes)"/>
                  <label v-if="allowFolderSelection && nodes && nodes.path" :for="`${instanceId}-root-selection`" @click.stop>{{ currentPath }} ({{ itemCountLabel }})</label>
                  <span v-else>{{ currentPath }} ({{ itemCountLabel }})</span>
                </template>
              </div>
            </div>
            <div class="asset-browser-controls">
              <ul class="cards-toolbar sort-nav">
              <li><span class="cards-toolbar-title">Sort</span></li>
              <li><input :name="`${instanceId}-sort`" type="radio" class="with-gap" :id="`${instanceId}-sort-name`" :checked="sortBy === 'name'" @change="onSort('name')"/><label :for="`${instanceId}-sort-name`">name</label></li>
              <li><input :name="`${instanceId}-sort`" type="radio" class="with-gap" :id="`${instanceId}-sort-type`" :checked="sortBy === 'resourceType'" @change="onSort('resourceType')"/><label :for="`${instanceId}-sort-type`">type</label></li>
              <li><input :name="`${instanceId}-sort`" type="radio" class="with-gap" :id="`${instanceId}-sort-date`" :checked="sortBy === 'created'" @change="onSort('created')"/><label :for="`${instanceId}-sort-date`">date</label></li>
              </ul>
              <ul class="cards-toolbar filter-nav">
              <li><span class="cards-toolbar-title">filter</span></li>
              <li><input :name="`${instanceId}-filter`" type="radio" class="with-gap" :id="`${instanceId}-filter-all`" :checked="filterBy === '*'" @change="onFilter('*')"/><label :for="`${instanceId}-filter-all`">all</label></li>
              <li><input :name="`${instanceId}-filter`" type="radio" class="with-gap" :id="`${instanceId}-filter-files`" :checked="filterBy === 'files'" @change="onFilter('files')"/><label :for="`${instanceId}-filter-files`">files</label></li>
              <li><input :name="`${instanceId}-filter`" type="radio" class="with-gap" :id="`${instanceId}-filter-folders`" :checked="filterBy === 'folders'" @change="onFilter('folders')"/><label :for="`${instanceId}-filter-folders`">folders</label></li>
              </ul>
            </div>
            <div v-if="tab === 'cards'" class="range-field browser-card-size">
              <input type="range" min="120" max="400" v-model.number="cardSize"
                     aria-label="Asset preview size"
                     @change="onCardSizeChangeComplete"
                     @mouseenter="showCardSize = true" @mouseleave="showCardSize = false"
                     @focus="showCardSize = true" @blur="showCardSize = false"/>
              <output v-if="showCardSize" class="card-size-hint"
                      :style="{ left: `calc(7px + (100% - 14px) * ${(Number(cardSize) - 120) / 280})` }">{{ cardSize }}</output>
            </div>
          </div>
          <div class="modal-content" :class="{ 'with-browser-header': tab === 'browse' || tab === 'cards', 'with-card-size': tab === 'cards' }">
            <div class="col-browse">
              <template v-if="tab === 'browse'">
                <ul class="browse-list" v-if="visibleList.length > 0">
                  <template v-for="(item, index) in visibleList">
                    <li v-if="isFolder(item)"
                        :key="item.path"
                        :data-browser-path="item.path"
                        v-on:click.stop.prevent="isBrowserTypePage && isSelectable(item) ? selectItem(item) : navigateFolder(item)"
                        :class="isSelected(item.path) ? 'selected' : ''">
                      <template v-if="isSelectable(item)">
                        <input :name="`${instanceId}-selected`"
                               :id="`${instanceId}-browse-${index}`"
                               @click.stop
                               @change="selectItem(item)"
                               type="radio"
                               class="with-gap"
                               :checked="isSelected(item.path)"/>
                        <label :for="`${instanceId}-browse-${index}`" @click.stop></label>
                      </template>
                      <i class="material-icons"
                         v-on:click.stop.prevent="navigateFolder(item)"
                         :title="isBrowserTypePage ? 'Open' : ''">{{ getFolderIcon(item) }}</i>
                      <span>{{ item.name }}</span>
                    </li>
                    <li v-if="isFile(item)"
                        :key="item.path"
                        :data-browser-path="item.path"
                        v-on:click.stop.prevent="selectItem(item)"
                        :class="isSelected(item.path) ? 'selected' : ''">
                      <template v-if="isSelectable(item)">
                        <input :name="`${instanceId}-selected`"
                               :id="`${instanceId}-browse-${index}`"
                               @click.stop
                               @change="selectItem(item)"
                               type="radio"
                               class="with-gap"
                               :checked="isSelected(item.path)"/>
                        <label :for="`${instanceId}-browse-${index}`" @click.stop></label>
                      </template>
                      <img
                          v-if="isImagePath(item.path)"
                          class="browse-thumbnail"
                          :src="assetPreviewPath(item)"
                          @error="fallbackAssetPreview($event, item)"
                          loading="lazy"
                          :alt="item.alt || item.name"/>
                      <icon v-else v-bind="getFileIcon(item)"/>
                      <span>{{ item.name }}</span>
                    </li>
                  </template>
                </ul>
                <p v-else class="flow-text">{{ getEmptyText() }}</p>
              </template>
              <template v-if="tab === 'cards'">
                  <asset-grid
                      v-show="visibleList.length"
                      ref="grid"
                      class="isotopes"
                      :items="visibleList"
                      :search-query="searchQuery"
                      :size="cardSize"
                      :selected-path="selectedNodePath"
                      :instance-id="instanceId"
                      :is-folder="isFolder"
                      :is-file="isFile"
                      :is-browser-type-page="isBrowserTypePage"
                      :is-selectable="isSelectable"
                      :get-folder-icon="getFolderIcon"
                      :get-file-icon="getFileIcon"
                      @navigate="navigateFolder"
                      @select="selectItem"/>
                <p v-if="!visibleList.length" class="flow-text">{{ getEmptyText() }}</p>
              </template>
              <template v-if="withLinkTab && tab === 'link'">
                <div class="form-group">
                  <label for="pathBrowserLink">URL</label>
                  <input
                      id="pathBrowserLink"
                      ref="pathBrowserLink"
                      type="url"
                      placeholder="URL"
                      :value="selectedPath"
                      @mousedown=""
                      @input="selectLink"/>
                </div>
                <div class="form-group" v-if="linkTitle !== undefined">
                  <label for="linkTitle">Link Title</label>
                  <input
                      id="linkTitle"
                      type="text"
                      placeholder="Link Title"
                      :value="linkTitle"
                      @input="setLinkTitle"/>
                </div>
                <div v-if="isImagePath(selectedPath) && isAsset" class="img-group">
                  <div class="form-group">
                    <label for="linkTitle">Image Width (px)</label>
                    <input
                        id="imgWidth"
                        type="number"
                        placeholder="leave blank to keep original size"
                        :value="imgWidth"
                        @input="onUpdateImgDimension('width', $event)"/>
                  </div>
                  <div class="form-group">
                    <label for="linkTitle">Image Height (px)</label>
                    <input
                        id="imgHeight"
                        type="number"
                        placeholder="leave blank to keep original size"
                        :value="imgHeight"
                        @input="onUpdateImgDimension('height', $event)"/>
                  </div>
                </div>
                <div class="checkboxes-group">
                  <div class="pathbrowser-newwindow" v-if="newWindow !== undefined"
                       @click="$emit('toggle-newWindow')"
                       @keyup.space="$emit('toggle-newWindow')">
                    <input type="checkbox" id="newWindow" v-model="newWindow"/>
                    <label for="newWindow">Open in new window?</label>
                  </div>
                  <div class="pathbrowser-rel"
                       v-if="showRel"
                       :title="$i18n('pathbrowserRelTitle')"
                       @click="$emit('toggle-rel')"
                       @keyup.space="$emit('toggle-rel')">
                    <input type="checkbox" id="rel" v-model="rel"/>
                    <label for="rel"> Add rel="noopener noreferrer" to link? </label>
                  </div>
                </div>
              </template>
              <template v-if="withImageTab && tab === 'image'">
                <div class="img-group">
                  <div class="form-group">
                    <label for="imageTabWidth">Width (px)</label>
                    <input
                        id="imageTabWidth"
                        type="number"
                        placeholder="Width"
                        :value="imgWidth"
                        @input="onUpdateImgDimension('width', $event)"/>
                  </div>
                  <div class="form-group">
                    <label for="imageTabHeight">Height (px)</label>
                    <input
                        id="imageTabHeight"
                        type="number"
                        placeholder="Height"
                        :value="imgHeight"
                        @input="onUpdateImgDimension('height', $event)"/>
                  </div>
                </div>
              </template>
            </div>
            <div class="col-preview">
              <template v-if="preview">
                <div v-if="isFolder(preview)" class="preview-folder">
                  <admin-components-iconopenfolder></admin-components-iconopenfolder>
                </div>
                <template v-else>
                  <img v-if="isImagePath(preview.path)" class="preview-image" :src="assetPreviewPath(preview)" @error="fallbackAssetPreview($event, preview)" :alt="preview.alt || preview.name"/>
                  <icon v-else v-bind="getFileIcon(preview)"/>
                </template>
                <dl class="preview-data">
                  <dt>Name</dt>
                  <dd>{{ preview.name }}</dd>
                  <template v-if="preview.alt">
                    <dt>Alt text</dt>
                    <dd>{{ preview.alt }}</dd>
                  </template>
                  <dt>Type</dt>
                  <dd>{{ preview.resourceType }}</dd>
                  <dt>Path</dt>
                  <dd>{{ preview.path }}</dd>
                  <dt>Created</dt>
                  <dd>{{ preview.created }}</dd>
                </dl>

              </template>
              <div v-else class="no-asset-selected">
                <span>{{ $i18n('noAssetSelected') }}</span>
                <i class="material-icons">info</i>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <div class="pathbrowser-footer-details">
              <span class="pathbrowser-selected-path">{{ selectedPath && selectedPath.split ? selectedPath.split('#')[0] : '' }}</span>

            <div class="" v-if="preview && preview.resourceType === 'per:Page' && preview.anchors && preview.anchors.length > 0">
              <select id="pageAnchor" v-model="selectedAnchor" class="browser-default" aria-label="Select anchor">
                <option value="">Anchor: none</option>
                <option v-for="anchor in preview.anchors" :key="anchor" :value="anchor">
                  #{{ anchor }}
                </option>
              </select>
            </div>
            </div>
            <div class="pathbrowser-buttons">
              <button v-on:click="onCancel"
                      class="modal-action waves-effect waves-light btn-flat">
                cancel
              </button>
              <button v-on:click="$emit('select', selectedPath)"
                      class="modal-action waves-effect waves-light btn-flat">
                select
              </button>
            </div>
          </div>
        </div>
        <file-dropper
            v-if="isBrowserTypeAsset"
            :drop-context="$refs.pathbrowser"
            :path="currentPath"
            @upload-done="onFileDropperUploadDone"/>
      </div>
    </div>
  </transition>
</template>

<script>
import { IconLib, PathBrowser } from '../../../../../../js/constants'

import {isImagePath, encodeAssetPath, normalizeBrowserPath, assetPreviewPath, fallbackAssetPreview} from '../../../../../../js/utils'
import FileDropper from '../filedropper/template.vue'
import Icon from '../icon/template.vue'
import AssetGrid from './AssetGrid.vue'

export default {
  name: 'PathBrowser',
  components: {
    AssetGrid,
    Icon,
    FileDropper
  },
  props: {
    isOpen: Boolean,
    header: String,
    browserRoot: String,
    browserType: String,
    currentPath: String,
    selectedPath: String,
    withLinkTab: Boolean,
    withImageTab: Boolean,
    newWindow: {
      type: Boolean,
      default: false
    },
    toggleNewWindow: Function,
    setCurrentPath: Function,
    setSelectedPath: Function,
    setResourceType: Function,
    linkTitle: String,
    setLinkTitle: Function,
    rel: {
      type: Boolean,
      default: true
    },
    imgWidth: {
      type: Number
    },
    imgHeight: {
      type: Number
    },
    onCancel: Function,
  },
  watch: {
    withLinkTab(enabled) {
      if (!enabled && this.tab === 'link') this.select('browse')
    },
    withImageTab(enabled) {
      if (!enabled && this.tab === 'image') this.select('browse')
    },
    search: function (newSearch, oldSearch) {
      if (!newSearch && oldSearch) this.scrollToSelectedItem()
    },
    list: function () {
      this.scrollToSelectedItem()
    },
    selectedPath: function () {
      this.scrollToSelectedItem()
    }
  },
  mounted() {
    // set initial tab
    if (this.withImageTab) {
      this.tab = 'image'
    } else if (this.withLinkTab && this.selectedPath && this.selectedPath.match(/^(https?:)?\/\//)) {
      this.tab = 'link'
    } else {
      this.tab = 'browse'
    }
    this.scrollToSelectedItem()
  },
  beforeDestroy() {
    this.isDestroying = true
    if (this.scrollFrame) window.cancelAnimationFrame(this.scrollFrame)
  },
  data: function () {
    return {
      IconLib,
      instanceId: `pathbrowser-${this._uid}`,
      tab: null,
      cardSize: 120,
      showCardSize: false,
      search: '',
      previewType: this.selectedPath ? 'selected' : 'current',
      sortBy: '',
      filterBy: '*',
      scrollFrame: null,
      isDestroying: false,
    }
  },
  computed: {
    selectedNodePath() {
      return normalizeBrowserPath(this.selectedPath)
    },
    searchQuery() {
      return this.search.trim().toLocaleLowerCase()
    },
    isRoot() {
      if (!this.currentPath) return true
      const root = (this.browserRoot || '/').replace(/\/+$/, '') || '/'
      const current = (this.currentPath || '').replace(/\/+$/, '') || '/'
      return current === root || !current.startsWith(root === '/' ? '/' : root + '/')
    },
    nodes() {
      let view = $perAdminApp.getView()
      let nodes = view.admin.pathBrowser
      if (nodes && this.currentPath) {
        return $perAdminApp.findNodeFromPath(nodes, this.currentPath)
      }
      return {}
    },
    list() {
      if (this.nodes && this.nodes.children) {
        return this.nodes.children
      }
      return []
    },
    visibleList() {
      let items = this.filteredList.slice()
      if (this.sortBy) {
        items.sort((a, b) => {
          const left = this.sortBy === 'resourceType' ? (a.mimeType || a.resourceType || '') : (a[this.sortBy] || '')
          const right = this.sortBy === 'resourceType' ? (b.mimeType || b.resourceType || '') : (b[this.sortBy] || '')
          if (this.sortBy === 'created') {
            const leftDate = typeof left === 'number' ? left : Date.parse(left)
            const rightDate = typeof right === 'number' ? right : Date.parse(right)
            const leftInvalid = Number.isNaN(leftDate)
            const rightInvalid = Number.isNaN(rightDate)
            if (leftInvalid !== rightInvalid) return leftInvalid ? 1 : -1
            return leftDate - rightDate
          }
          return String(left).localeCompare(String(right), undefined, { sensitivity: 'base' })
        })
      }
      return items
    },
    filteredList() {
      let items = this.list.slice()
      if (this.searchQuery) items = items.filter(item => this.searchFilter(item))
      if (this.filterBy === 'files') items = items.filter(item => this.isFile(item))
      if (this.filterBy === 'folders') items = items.filter(item => this.isFolder(item))
      return items
    },
    tabIndicatorPosition() {
      const tabs = ['browse', 'cards']
      if (this.withLinkTab) tabs.push('link')
      if (this.withImageTab) tabs.push('image')
      return Math.max(0, tabs.indexOf(this.tab)) * 72
    },
    searchTabOffset() {
      return 72 * (2 + Number(this.withLinkTab) + Number(this.withImageTab))
    },
    itemCountLabel() {
      return this.searchQuery || this.filterBy !== '*'
          ? `${this.visibleList.length} of ${this.list.length}`
          : String(this.list.length)
    },
    allowFolderSelection() {
      return !this.isBrowserTypeImage && !this.isBrowserTypeObjectDefinition
    },
    isAsset() {
      return this.isType(PathBrowser.Type.ASSET)
    },
    isBrowserTypePage() {
      return this.isType(PathBrowser.Type.PAGE)
    },
    isBrowserTypeImage() {
      return this.isType(PathBrowser.Type.IMAGE)
    },
    isBrowserTypeAsset() {
      return this.isAsset || this.isType(PathBrowser.Type.IMAGE)
    },
    isBrowserTypeObjectDefinition() {
      return this.isType(PathBrowser.Type.OBJECT_DEFINITION)
    },
    showRel() {
      return !this.selectedPath || (
          this.selectedPath.startsWith('http://')
          || this.selectedPath.startsWith('https://')
      )
    },
    selectedAnchor: {
      get() {
        if (!this.selectedPath) return ''
        const parts = this.selectedPath.split('#')
        return parts.length > 1 ? parts[1] : ''
      },
      set(val) {
        if (!this.selectedPath) return
        const basePath = this.selectedPath.split('#')[0]
        const newPath = val ? `${basePath}#${val}` : basePath
        this.setSelectedPath(newPath)
      }
    },
    preview() {
      if (this.previewType === 'selected') {
        const view = $perAdminApp.getView()
        return $perAdminApp.findNodeFromPath(view.admin.pathBrowser, this.selectedNodePath)
      } else {
        return this.nodes
      }
    },
  },
  methods: {
    isImagePath,
    encodeAssetPath,
    assetPreviewPath,
    fallbackAssetPreview,
    getFileExt(item) {
      return item.name.split('.').pop()
    },
    getFileIcon(item) {
      const ext = this.getFileExt(item)
      if (this.isImagePath(item && item.path)) {
        return {icon: 'image', lib: IconLib.MATERIAL_ICONS}
      } else if (ext === 'xml') {
        return {icon: 'file-code', lib: IconLib.FONT_AWESOME}
      } else if (ext === 'json') {
        return {icon: '{&#8230;}', lib: IconLib.PLAIN_TEXT}
      } else {
        return {icon: 'insert_drive_file', lib: IconLib.MATERIAL_ICONS}
      }
    },
    getFolderIcon(item) {
      if (item) {
          return item.hasChildren ? 'folder_open' : 'description'
      }
      return this.isAsset ? 'folder_open' : 'description'
    },
    getEmptyText() {
      return this.isAsset ? 'Folder is empty' : 'No child pages'
    },
    onCardSizeChangeComplete() {
      this.scrollToSelectedItem()
    },
    onSort(sortType) {
      this.sortBy = sortType
    },
    onFilter(filterType) {
      this.filterBy = filterType
    },
    select(name) {
      if (name === 'link' || name === 'image') this.search = ''
      this.tab = name
      this.scrollToSelectedItem()
    },
    scrollToSelectedItem() {
      if (!this.selectedPath || this.isDestroying) return
      this.$nextTick(() => {
        if (this.isDestroying) return
        if (this.scrollFrame) window.cancelAnimationFrame(this.scrollFrame)
        if (this.$refs.grid) {
          this.$refs.grid.layout(this.revealSelectedItem, false)
        } else {
          this.scrollFrame = window.requestAnimationFrame(this.revealSelectedItem)
        }
      })
    },
    revealSelectedItem() {
      this.scrollFrame = null
      if (!this.selectedPath || this.isDestroying || !this.$el || !this.$el.isConnected) return
      const content = this.$el.querySelector('.col-browse')
      if (!content) return
      const target = Array.from(content.querySelectorAll('[data-browser-path]'))
          .find(element => this.isSelected(element.dataset.browserPath))
      if (!target) return

      const container = window.getComputedStyle(content).overflowY === 'auto'
          ? content : content.closest('.modal-content')
      if (!container || !container.getClientRects().length) return
      const containerBounds = container.getBoundingClientRect()
      const targetBounds = target.getBoundingClientRect()
      if (targetBounds.top < containerBounds.top || targetBounds.bottom > containerBounds.bottom) {
        container.scrollTop += targetBounds.top - containerBounds.top
            - (containerBounds.height - targetBounds.height) / 2
      }
    },
    searchFilter(item) {
      const query = this.searchQuery
      if (!query) return true
      return [item.name, item.alt]
          .some(value => value && String(value).toLocaleLowerCase().includes(query))
    },
    selectParent() {
      if (this.isRoot) return
      let parentFolder = (this.currentPath || '').replace(/\/+$/, '').split('/')
      parentFolder.pop()
      const root = (this.browserRoot || '/').replace(/\/+$/, '') || '/'
      let newPath = parentFolder.join('/') || root
      if (root !== '/' && newPath !== root && !newPath.startsWith(root + '/')) newPath = root
      /* TODO: pass in obj with name and type, not just path */
      this.navigateFolder({path: newPath})
    },
    isFile(item) {
      if (!item) return false
      return ['per:Asset', 'per:Object', 'nt:file'].indexOf(item.resourceType) >= 0
    },
    isFolder(item) {
      if (!item) return false
      return [
        'per:Page',
        'nt:folder',
        'sling:Folder',
        'sling:OrderedFolder',
        'per:ObjectDefinition'
      ].indexOf(item.resourceType) >= 0
    },
    isSelected(path) {
      return this.selectedNodePath === normalizeBrowserPath(path)
    },
    navigateFolder(item) {
      if (!item || !item.path) return
      const request = {}
      this._navigationRequest = request
      const isCurrent = () => !this.isDestroying && this._navigationRequest === request
      return $perAdminApp.getApi()
          .populateNodesForBrowser(item.path, 'pathBrowser', false, isCurrent)
          .then(() => {
            if (!isCurrent()) return
            this.search = ''
            this.previewType = 'current'
            this.setCurrentPath(item.path)
          })
          .catch(error => {
            if (isCurrent()) console.error('Unable to open folder', item.path, error)
          })
    },
    selectItem(item) {
      if (this.isSelectable(item)) {
        this.previewType = 'selected'
        this.setSelectedPath(item.path)
        if (item.resourceType && typeof this.setResourceType === 'function') {
          this.setResourceType(item.resourceType)
        }
      }
    },
    selectLink(ev) {
      // TODO: add link preview
      this.previewType = 'link'
      // TODO: allow target="_blank" or target="_self"
      this.setSelectedPath(ev.target.value)
    },
    isType(browserType) {
      return (this.browserType || '').toLowerCase() === (browserType || '').toLowerCase()
    },
    isSelectable(item) {
      if (!item || !item.path) return false
      if (this.isBrowserTypeObjectDefinition) return item.resourceType === 'per:ObjectDefinition'
      if (this.isFolder(item)) return this.allowFolderSelection
      if (!this.isBrowserTypeImage) {
        return true
      } else if (this.isImagePath(item && item.path)) {
        return true
      }
      return false
    },
    onUpdateImgDimension(name, event) {
      const payload = event.target.value ? parseInt(event.target.value) : null
      this.$emit(`update-img-${name}`, payload)
    },
    onFileDropperUploadDone(files) {
      files.reverse().some((file) => {
        file.mimeType = file.type
        if (this.isImagePath(file.name)) {
          this.setSelectedPath(`${this.currentPath}/${file.name}`)
          return true
        }
      })
    }
  }
}
</script>

<style scoped>
.browse-list li i.material-icons {
  cursor: pointer;
}

.browse-list .browse-thumbnail {
  width: 36px;
  height: 36px;
  margin-right: 0.375rem;
  object-fit: cover;
  flex: 0 0 36px;
  background: #eceff1;
}

.form-group label,
.form-group input {
  color: #222;
}

.img-group {
  display: flex;
  gap: 8px;
}

.img-group .form-group {
  flex: 1;
}

.browse-list .icon {
  height:36px;
  width: 36px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: bolder;
  color: #000000;
}

.col-preview .icon {
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: bolder;
  color: #000000;
  width: 100%;
  font-size: 4rem;
  padding: .5rem;
  height: 6rem;
}
</style>
