<!--
  #%L
  admin base - UI Apps
  %%
  Copyright (C) 2026 headwire inc.
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
  <div :class="{ 'asset-grid-fallback': !hasIsotope }" @load.capture="onImageLoad" @error.capture="onImageLoad">
    <div
        v-for="(item, index) in items"
        :data-browser-path="item.path"
        :title="item.path"
        :class="['asset-grid-item', { selected: isSelected(item.path) }]"
        :style="`width: ${size}px`"
        :key="item.path">
      <div
          v-if="isFolder(item)"
          class="item-folder"
          v-bind:style="`width: ${size}px; height: ${size}px`"
          v-on:click.stop.prevent="onFolderClick(item)">
        <div v-if="isSelectable(item)" class="item-select">
          <input :name="`${instanceId}-selected`"
                 :id="`${instanceId}-card-${index}`"
                 @click.stop
                 @change="selectFile(item)"
                 type="radio"
                 class="with-gap"
                 :checked="isSelected(item.path)"/>
          <label :for="`${instanceId}-card-${index}`" @click.stop></label>
        </div>
        <div class="item-content">
          <i
              class="material-icons"
              v-on:click.stop.prevent="$emit('navigate', item)"
              :style="`font-size: ${Math.floor(size / 3)}px`">{{
              getFolderIcon(item)
            }}</i>
          <br/>
          <span class="truncate">{{ item.name }}</span>
        </div>
      </div>
      <template v-if="isFile(item)">
        <img
            v-if="isImagePath(item.path)"
            class="item-image"
            v-bind:style="`width: ${size}px`"
            v-bind:src="assetPreviewPath(item)"
                          @error="fallbackAssetPreview($event, item)"
            :alt="item.alt || item.name"
            v-on:click.stop.prevent="selectFile(item)"/>
        <div
            v-if="isImagePath(item.path)"
            class="asset-card-name"
            :style="`width: ${size}px`"
            :title="item.path"
            v-on:click.stop.prevent="selectFile(item)">{{ item.name }}</div>
        <div
            v-else
            class="item-file"
            :title="item.path"
            v-bind:style="`width: ${size}px; height: ${size}px`"
            v-on:click.stop.prevent="selectFile(item)">
          <div class="item-content">
            <icon v-bind="getFileIcon(item)"/>
            <br/>
            <span class="truncate">{{ item.name }}</span>
          </div>
        </div>

      </template>
    </div>
  </div>
</template>

<script>
import { isImagePath, encodeAssetPath, normalizeBrowserPath, assetPreviewPath, fallbackAssetPreview } from '../../../../../../js/utils'
import Icon from '../icon/template.vue'
import Isotope from 'isotope-layout'

export default {
  name: 'AssetGrid',
  components: { Icon },
  props: {
    items: { type: Array, required: true },
    size: { type: Number, required: true },
    selectedPath: String,
    searchQuery: { type: String, default: '' },
    instanceId: String,
    isFolder: { type: Function, required: true },
    isFile: { type: Function, required: true },
    getFolderIcon: { type: Function, required: true },
    getFileIcon: { type: Function, required: true },
    isSelectable: { type: Function, required: true },
    isBrowserTypePage: Boolean
  },
  data() {
    return { hasIsotope: false }
  },
  watch: {
    items() { this.$nextTick(() => this.layout()) },
    size() { this.$nextTick(() => this.layout(null, false)) }
  },
  mounted() {
    const IsotopeConstructor = window.Isotope || Isotope
    this.hasIsotope = Boolean(IsotopeConstructor)
    if (IsotopeConstructor && this.$el) this._isotope = new IsotopeConstructor(this.$el, {
      itemSelector: '.asset-grid-item',
      layoutMode: 'masonry',
      transitionDuration: 0,
      masonry: { gutter: 15 }
    })
    this.layout()
  },
  beforeDestroy() {
    window.cancelAnimationFrame(this._layoutFrame)
    this._afterLayout = null
    if (this._isotope) this._isotope.destroy()
  },
  methods: {
    isImagePath,
    encodeAssetPath,
    assetPreviewPath,
    fallbackAssetPreview,
    onImageLoad(event) {
      if (event.target.tagName === 'IMG') {
        this.layout(() => this.$emit('layout'), false)
      }
    },
    isSelected(path) {
      return normalizeBrowserPath(path) === normalizeBrowserPath(this.selectedPath)
    },
    onFolderClick(item) {
      if (this.isBrowserTypePage && this.isSelectable(item)) this.selectFile(item)
      else this.$emit('navigate', item)
    },
    selectFile(item) {
      if (this.isSelectable(item)) this.$emit('select', item)
    },
    layout(onComplete, reloadItems = true) {
      if (typeof onComplete === 'function') this._afterLayout = onComplete
      if (reloadItems) this._needsReload = true
      window.cancelAnimationFrame(this._layoutFrame)
      this._layoutFrame = window.requestAnimationFrame(() => {
        if (this._isDestroyed) return
        if (!this._isotope) {
          const complete = this._afterLayout
          this._afterLayout = null
          if (complete) complete()
          return
        }
        if (this._needsReload) {
          this._isotope.reloadItems()
          this._isotope.arrange({ sortBy: 'original-order' })
          this._needsReload = false
        } else {
          this._isotope.layout()
        }
        const complete = this._afterLayout
        this._afterLayout = null
        if (complete) complete()
      })
    }
  }
}
</script>

<style scoped>
.asset-grid-fallback {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  column-gap: 15px;
}
.asset-grid-fallback > .asset-grid-item {
  position: relative;
  flex-shrink: 0;
}
</style>
