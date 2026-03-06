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
  <div class="text-editor-wrapper">
    <richtoolbar
      ref="richtoolbar"
      class="on-right-panel"
      :show-always-active="false"
      :responsive="false"
      :editorContent="value"
      @ping="key = key === 'foo'? 'bar' : 'foo'"
    />
    <p class="text-editor inline-edit"
       :class="['text-editor', 'inline-edit', {'inline-editing': editing}]"
       ref="textEditor"
       v-html="value"
       :contenteditable="!(schema && schema.readonly)"
       :readonly="(schema && schema.readonly)"
       @focusin="onFocusIn"
       @focusout="onFocusOut"
       @input="onInput"
       @click="pingToolbar"
       @dblclick="onDblClick"
       @keydown="onKeyDown"
       @keyup="pingToolbar">
    </p>
  </div>
</template>

<script>
import {restoreSelection, saveSelection, set} from '../../../../../js/utils'
import Richtoolbar from '../../admin/components/richtoolbar/template.vue'

const allowedStylesMap = {
  // bold, italic, etc handled by html tags
  "text-align": true,
  "font-size": true
};
const allowedStylesElementsMap = {
  IMG: true,
}
function removeUnwantedStyles(htmlText) {
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = htmlText

  tempDiv.querySelectorAll('[style]').forEach((span) => {
    if (allowedStylesElementsMap[span.nodeName]) return;
    const propertiesToRemove = []
    for (let i = 0; i < span.style.length; i++) {
      const property = span.style.item(i);
      if (!allowedStylesMap[property]) {
        propertiesToRemove.push(property);
      }
    }
    // must be done in later step, otherwise length changes
    for (let i = 0; i < propertiesToRemove.length; i++) {
      span.style.removeProperty(propertiesToRemove[i]);
    }
  })

  return tempDiv.innerHTML
}

export default {
  components: {Richtoolbar},
  mixins: [VueFormGenerator.abstractField],
  data() {
    return {
      doc: document,
      editing: false,
      key: 0,
    }
  },
  computed: {
    view() {
      return $perAdminApp.getView()
    },
  },

  mounted() {
    const view = this.view
    if (view) set(view, '/state/inline/rich', true)
  },

  methods: {
    onFocusIn(event) {
      const view = this.view
      if (!view) return
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/doc', this.doc)
      set(view, '/state/inline/lastContainer', this.$refs.textEditor)
      set(view, '/state/inline/editorModel', this.model)
      this.editing = true
      this.pingToolbar()
    },
    onKeyDown(event) {
      this.pingToolbar()
      const key = event.which
      const ctrlOrCmd = event.ctrlKey || event.metaKey
      
      if (ctrlOrCmd && event.altKey && ((key >= 48 && key <= 54) || (key >= 96 && key <= 102))) {
        event.preventDefault()
        const digit = key >= 96 ? key - 96 : key - 48
        const value = digit === 0 ? 'p' : `h${digit}`
        document.execCommand('formatBlock', false, value)
        this.$nextTick(() => this.pingToolbar())
      } else if (key === 66 && ctrlOrCmd) {
        event.preventDefault()
        document.execCommand('bold', false, null)
        this.$nextTick(() => this.pingToolbar())
      } else if (key === 73 && ctrlOrCmd) {
        event.preventDefault()
        document.execCommand('italic', false, null)
        this.$nextTick(() => this.pingToolbar())
      } else if (key === 85 && ctrlOrCmd) {
        event.preventDefault()
        document.execCommand('underline', false, null)
        this.$nextTick(() => this.pingToolbar())
      }
    },
    onFocusOut() {
      const view = this.view
      if (!view) return
      // Capture anchor and container BEFORE clearing doc, so toolbar can use them
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/lastAnchor', el ? el.closest('a') : null)
      set(view, '/state/inline/lastContainer', this.$refs.textEditor)
      set(view, '/state/inline/lastDoc', this.doc)
      set(view, '/state/inline/lastSelectionBuffer', saveSelection(this.$refs.textEditor, this.doc))
      set(view, '/state/inline/doc', null)
      set(view, '/state/inline/editorModel', null)
      this.editing = false
      this.pingToolbar()
    },
    onInput(event) {
      const content = event.target.innerHTML;
      // Update vnode domProps to prevent Vue from resetting cursor position on re-render
      const pVnode = this._vnode.children && this._vnode.children.find(c => c.elm === this.$refs.textEditor)
      if (pVnode && pVnode.data && pVnode.data.domProps) pVnode.data.domProps.innerHTML = content
      this.value = content
      this.textEditorWriteToModel()
      this.pingToolbar()
    },
    onDblClick(event) {
      if (event.target.tagName === 'IMG') {
        $perAdminApp.action(this, 'editImage', event.target)
      }
    },
    textEditorWriteToModel(vm = this) {
      const content = removeUnwantedStyles(vm.$refs.textEditor.innerHTML);
      // Patch the vnode domProps so Vue's next render doesn't overwrite the DOM with the old value
      const pVnode = vm._vnode && vm._vnode.children && vm._vnode.children.find(c => c.elm === vm.$refs.textEditor)
      if (pVnode && pVnode.data && pVnode.data.domProps) pVnode.data.domProps.innerHTML = content
      vm.model.text = content;
    },
    pingToolbar() {
      this.key = this.key === 'foo' ? 'bar' : 'foo'
      $perAdminApp.action(this, 'pingRichToolbar')
    },
    insertLink(vm = this) {
      vm._saveInlineSelectionState()
      if (vm.$refs.richtoolbar) vm.$refs.richtoolbar.insertLink()
    },
    editLink(vm = this) {
      vm._saveInlineSelectionState()
      if (vm.$refs.richtoolbar) vm.$refs.richtoolbar.editLink()
    },
    removeLink(vm = this) {
      // Get the anchor before any state changes
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      const anchor = el ? el.closest('a') : null
      if (!anchor) return

      // Select the anchor contents and unlink directly
      const range = document.createRange()
      range.selectNodeContents(anchor)
      sel.removeAllRanges()
      sel.addRange(range)
      document.execCommand('unlink', false, null)
      vm.textEditorWriteToModel()
    },
    _saveInlineSelectionState(vm = this) {
      const view = $perAdminApp.getView()
      if (!view) return
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      set(view, '/state/inline/lastAnchor', el ? el.closest('a') : null)
      set(view, '/state/inline/lastContainer', vm.$refs.textEditor)
      set(view, '/state/inline/lastDoc', vm.doc)
      set(view, '/state/inline/doc', vm.doc)
    },
  },
  watch: {
    value() {
      if (!this.value) return
      const textCheckDiv = document.createElement('div')
      textCheckDiv.innerHTML = this.value
      if (!textCheckDiv.textContent.trim()) this.value = '';
    }
  }
}
</script>
