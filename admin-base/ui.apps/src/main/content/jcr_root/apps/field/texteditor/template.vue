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
       @keydown="onKeydown"
       @keyup="pingToolbar"
       @mouseup="onMouseUp">
    </p>
    <richtoolbar-inline ref="inlineToolbar" />
  </div>
</template>

<script>
import {saveSelection, set} from '../../../../../js/utils'
import Richtoolbar from '../../admin/components/richtoolbar/template.vue'
import RichtoolbarInline from '../../admin/components/richtoolbarinline/template.vue'

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
  components: {Richtoolbar, RichtoolbarInline},
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
    // Pass the editor DOM element to the inline toolbar after both refs are available
    if (this.$refs.inlineToolbar) {
      this.$refs.inlineToolbar.setEditorRef(this.$refs.textEditor)
    }
    // Ensure Enter creates <p> instead of <div> inside the contenteditable
    document.execCommand('defaultParagraphSeparator', false, 'p')
  },

  methods: {
    onFocusIn(event) {
      const view = this.view
      if (!view) return
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/doc', this.doc)
      this.editing = true
      this.pingToolbar()
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
      this.editing = false
      this.pingToolbar()
      if (this.$refs.inlineToolbar) this.$refs.inlineToolbar.hide()
    },
    onMouseUp() {
      this.$nextTick(() => {
        if (this.$refs.inlineToolbar) this.$refs.inlineToolbar.updateState()
      })
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
    onKeydown(event) {
      if (event.ctrlKey && event.altKey) {
        const n = parseInt(event.key)
        if (!isNaN(n) && n >= 0 && n <= 6) {
          event.preventDefault()
          const tag = n === 0 ? 'p' : `h${n}`
          if (this.$refs.richtoolbar) this.$refs.richtoolbar.exec('formatBlock', tag)
          return
        }
      }
      this.pingToolbar()
    },
    pingToolbar() {
      this.key = this.key === 'foo' ? 'bar' : 'foo'
      $perAdminApp.action(this, 'pingRichToolbar')
    },
    // Proxy methods so $perAdminApp.action can find them from the inline toolbar child.
    // actionImpl calls these as unbound functions with (vm, target) where vm = this texteditor.
    // We save current selection state first (editor still has focus due to @mousedown.prevent).
    insertLink(vm = this) {
      vm._saveInlineSelectionState()
      if (vm.$refs.inlineToolbar) vm.$refs.inlineToolbar.hide()
      if (vm.$refs.richtoolbar) vm.$refs.richtoolbar.insertLink()
    },
    editLink(vm = this) {
      vm._saveInlineSelectionState()
      if (vm.$refs.inlineToolbar) vm.$refs.inlineToolbar.hide()
      if (vm.$refs.richtoolbar) vm.$refs.richtoolbar.editLink()
    },
    insertImage(vm = this) {
      vm._saveInlineSelectionState()
      if (vm.$refs.inlineToolbar) vm.$refs.inlineToolbar.hide()
      if (vm.$refs.richtoolbar) vm.$refs.richtoolbar.insertImage()
    },
    removeLink(vm = this) {
      // Get the anchor before any state changes
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      const anchor = el ? el.closest('a') : null
      if (!anchor) return

      if (vm.$refs.inlineToolbar) vm.$refs.inlineToolbar.hide()

      // Select the anchor contents and unlink directly
      const range = document.createRange()
      range.selectNodeContents(anchor)
      sel.removeAllRanges()
      sel.addRange(range)
      document.execCommand('unlink', false, null)
      vm.textEditorWriteToModel()
      vm.$nextTick(() => {
        if (vm.$refs.inlineToolbar) vm.$refs.inlineToolbar.updateState()
      })
    },
    // Capture the current selection/anchor into view state so richtoolbar methods can read them.
    // Called before opening the pathbrowser from the inline toolbar (editor still has focus).
    // Also sets inline.doc so richtoolbar's getInlineDoc() / getInlineContainer() work correctly.
    _saveInlineSelectionState(vm = this) {
      const view = $perAdminApp.getView()
      if (!view) return
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      set(view, '/state/inline/lastAnchor', el ? el.closest('a') : null)
      set(view, '/state/inline/lastContainer', vm.$refs.textEditor)
      set(view, '/state/inline/lastDoc', vm.doc)
      // Ensure inline.doc is set so richtoolbar's getInlineDoc()/getInlineContainer() work.
      // The editor still has focus (mousedown.prevent on inline toolbar buttons), so this is correct.
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
