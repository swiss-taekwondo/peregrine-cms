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
    <div class="text-editor inline-edit"
       :class="['text-editor', 'inline-edit', {'inline-editing': editing}]"
       ref="textEditor"
       v-html="value"
       :contenteditable="!(schema && schema.readonly)"
       :readonly="(schema && schema.readonly)"
       @focusin="onFocusIn"
       @focusout="onFocusOut"
       @input="onInput"
       @click="onSelectionChange"
       @dblclick="onDblClick"
       @keydown="onKeyDown"
       @mouseup="onSelectionChange"
       @keyup="onSelectionChange">
       </div>
  </div>
</template>

<script>
import {Key} from '../../../../../js/constants'
import {restoreSelection, saveSelection, set} from '../../../../../js/utils'
import Richtoolbar from '../../admin/components/richtoolbar/template.vue'
import DOMPurify from 'dompurify'

function getAnchorFromSelection(selection) {
  const anchorNode = selection && selection.rangeCount > 0 ? selection.anchorNode : null
  const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
  return el ? el.closest('a') : null
}

function resolveHeadingShortcutDigit(event) {
  const { code } = event
  if (typeof code === 'string') {
    const digitMatch = code.match(/^Digit([0-6])$/)
    if (digitMatch) return Number(digitMatch[1])
    const numpadMatch = code.match(/^Numpad([0-6])$/)
    if (numpadMatch) return Number(numpadMatch[1])
  }

  const key = event.which || event.keyCode
  if (key >= Key.DIGIT_0 && key <= Key.DIGIT_6) {
    return key - Key.DIGIT_0
  }
  if (key >= Key.NUMPAD_0 && key <= Key.NUMPAD_6) {
    return key - Key.NUMPAD_0
  }

  return null
}

const allowedClasses = [
	'peregrine-icon',
];
const allowedStyles = [
	'text-align',
	'font-size',
	'width',
	'height',
	'vertical-align',
	'display',
];
DOMPurify.addHook('uponSanitizeAttribute', (node, data) => {
 if (data.attrName === 'class') {
    const filtered = data.attrValue
      .split(/\s+/)
      .filter(cls => allowedClasses.includes(cls))
      .join(' ');

    if (filtered) {
      data.attrValue = filtered;
    } else {
      data.keepAttr = false;
    }
  }

  if (data.attrName === 'style') {
    const declarations = data.attrValue
      .split(';')
      .map(s => s.trim())
      .filter(Boolean);

    const filtered = [];

    for (const decl of declarations) {
      const idx = decl.indexOf(':');
      if (idx === -1) continue;

      const property = decl.slice(0, idx).trim().toLowerCase();
      const value = decl.slice(idx + 1).trim();

      if (allowedStyles.includes(property)) {
        filtered.push(`${property}: ${value}`);
      }
    }

    if (filtered.length) {
      data.attrValue = filtered.join('; ');
    } else {
      data.keepAttr = false;
    }
  }
})
const domPurifyConfig = {
	ALLOWED_TAGS: [
		'p', 'span', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'br',
		'strong', 'em', 'b', 'i', 'u', 'a', 'img',
		'ul', 'ol', 'li', 'sub', 'sup'
	],
	ADD_ATTR: [
		'target'
	]
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
    formatBlock(tagName) {
      const normalizedTagName = String(tagName || '').replace(/[<>]/g, '').toUpperCase()
      if (!/^(P|H[1-6])$/.test(normalizedTagName)) return false

      const selection = document.getSelection()
      if (!selection || selection.rangeCount === 0) return false

      const range = selection.getRangeAt(0)
      const selectedElement = range.startContainer.nodeType === Node.TEXT_NODE
        ? range.startContainer.parentElement
        : range.startContainer
      const block = (selectedElement ? selectedElement.closest('p, h1, h2, h3, h4, h5, h6') : null)
        || (this.$refs.textEditor && this.$refs.textEditor.matches && this.$refs.textEditor.matches('p, h1, h2, h3, h4, h5, h6') ? this.$refs.textEditor : null)
        || (this.$refs.textEditor ? this.$refs.textEditor.closest('p, h1, h2, h3, h4, h5, h6') : null)
      if (!block || !this.$refs.textEditor.contains(block)) return false
      if (block.tagName === normalizedTagName) return true

      const replacement = document.createElement(normalizedTagName)
      for (const attr of Array.from(block.attributes)) {
        replacement.setAttribute(attr.name, attr.value)
      }
      while (block.firstChild) {
        replacement.appendChild(block.firstChild)
      }
      block.replaceWith(replacement)

      const nextRange = document.createRange()
      nextRange.selectNodeContents(replacement)
      selection.removeAllRanges()
      selection.addRange(nextRange)
      this.textEditorWriteToModel()
      this.$nextTick(() => this.pingToolbar())
      return true
    },
    onFocusIn(event) {
      const view = this.view
      if (!view) return
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/doc', this.doc)
      set(view, '/state/inline/lastContainer', this.$refs.textEditor)
      set(view, '/state/inline/editorModel', this.model)
      this.editing = true
      this.syncToolbarSelection()
      this.pingToolbar()
    },
    onKeyDown(event) {
      this.pingToolbar()
      const key = event.which
      const ctrlOrCmd = event.ctrlKey || event.metaKey
      const headingDigit = ctrlOrCmd && event.altKey ? resolveHeadingShortcutDigit(event) : null

      if (headingDigit !== null) {
        event.preventDefault()
        const value = headingDigit === 0 ? 'p' : `h${headingDigit}`
        if (!this.formatBlock(value)) {
          document.execCommand('formatBlock', false, `<${value}>`)
          this.textEditorWriteToModel()
          this.$nextTick(() => this.pingToolbar())
        }
      } else if (key === Key.B && ctrlOrCmd) {
        // override and dispatchevent here for more predictable outcomes and avoid contenteditable default keybinds when we have custom functionality.
        event.preventDefault()
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'bold' } }))
      } else if (key === Key.I && ctrlOrCmd) {
        event.preventDefault()
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'italic' } }))
      } else if (key === Key.U && ctrlOrCmd) {
        event.preventDefault()
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'underline' } }))
      } else if (key === Key.Z && ctrlOrCmd) {
        event.preventDefault()
        event.stopPropagation()
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'undo' } }))
      } else if (key === Key.Y && ctrlOrCmd) {
        event.preventDefault()
        event.stopPropagation()
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'redo' } }))
    }
    },
    onFocusOut() {
      const view = this.view
      if (!view) return
      const sel = document.getSelection()
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/lastAnchor', getAnchorFromSelection(sel))
      set(view, '/state/inline/lastContainer', this.$refs.textEditor)
      set(view, '/state/inline/lastDoc', this.doc)
      set(view, '/state/inline/lastSelectionBuffer', saveSelection(this.$refs.textEditor, this.doc))
      set(view, '/state/inline/doc', null)
      set(view, '/state/inline/editorModel', null)
      this.editing = false
      this.pingToolbar()
    },
    onSelectionChange() {
      this.syncToolbarSelection()
      this.pingToolbar()
    },
    syncToolbarSelection(vm = this) {
      const toolbar = vm.$refs.richtoolbar
      const container = vm.$refs.textEditor
      const selection = document.getSelection()
      if (!toolbar || !container || !selection || selection.rangeCount <= 0) return

      const range = selection.getRangeAt(0)
      if (!container.contains(range.startContainer) || !container.contains(range.endContainer)) return

      const anchor = getAnchorFromSelection(selection)
      toolbar.selection.doc = vm.doc
      toolbar.selection.container = container
      toolbar.selection.buffer = saveSelection(container, vm.doc)
      toolbar._savedRange = range.cloneRange()
      toolbar.activeAnchor = anchor
      toolbar.hasEditorSelection = true

      const view = vm.view
      if (view) {
        set(view, '/state/inline/lastAnchor', anchor)
        set(view, '/state/inline/lastContainer', container)
        set(view, '/state/inline/lastDoc', vm.doc)
        set(view, '/state/inline/doc', vm.doc)
      }
    },
    onInput(event) {
      const content = event.target.innerHTML;
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
      const content = vm.$refs.textEditor.innerHTML;
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
      const sel = document.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode) : null
      const anchor = el ? el.closest('a') : null
      if (!anchor) return

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
      vm.syncToolbarSelection()
      set(view, '/state/inline/lastContainer', vm.$refs.textEditor)
      set(view, '/state/inline/lastDoc', vm.doc)
      set(view, '/state/inline/doc', vm.doc)
    },
  },
  watch: {
    value() {
      if (!this.value) return
      const textCheckDiv = document.createElement('div')
      textCheckDiv.innerHTML = DOMPurify.sanitize(this.value, domPurifyConfig)
      // Replace links with no href or only whitespace with their content
      textCheckDiv.querySelectorAll('a').forEach((el) => {
        if (!el.getAttribute('href') || (!el.textContent.trim() && !el.querySelector('img'))) {
          console.log("replacing invalid link with it's content:", el)
          el.replaceWith(...el.childNodes)
        }
      })
      // removing all text usually results in the left over elements like empty <p> tags or a <br> tags.
      // make sure to treat this as empty but if images and lists are present, treat it as non-empty.
      // as a side effect, this requires text to exist before being able to set Headings, super/sub-script.
      if (!textCheckDiv.textContent.trim() && !textCheckDiv.querySelector('img, ul, ol')) this.value = '';
      this.value = textCheckDiv.innerHTML
    }
  }
}
</script>
