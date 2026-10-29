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
      :setEditorContent="setValue"
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
       @dblclick="onDblClick"
       @keydown="onKeyDown"
    >
    </p>
  </div>
</template>

<script>
import {Key} from '../../../../../js/constants'
import { saveSelection, set } from '../../../../../js/utils'
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
    document.execCommand('defaultParagraphSeparator', false, 'p')
  },

  methods: {
    setValue(newValue) {
      this.value = newValue;
    },
    onFocusIn() {
      const view = this.view
      if (!view) return
      set(view, '/state/inline/rich', true)
      set(view, '/state/inline/doc', this.doc)
      set(view, '/state/inline/lastContainer', this.$refs.textEditor)
      set(view, '/state/inline/editorModel', this.model)
      this.editing = true
    },

    sendRichToolbarCommand(event, cmd, value) {
      event.preventDefault()
      window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd, value } }))
    },

    onKeyDown(event) {
      const key = event.key;
      console.log('sidebarKeydown', key)
      const shift = event.shiftKey
      const alt = event.altKey
      const ctrlOrCmd = event.ctrlKey || event.metaKey

      if (ctrlOrCmd) {
        if (key === "a") this.onInlineSelectAll(event)
        if (key === "b") this.sendRichToolbarCommand(event, "bold")
        if (key === "i") this.sendRichToolbarCommand(event, "italic")
        if (key === "u") this.sendRichToolbarCommand(event, "underline")
        if (key === "z") this.sendRichToolbarCommand(event, "undo")
        if (key === "y") this.sendRichToolbarCommand(event, "redo")
      }
      if (ctrlOrCmd && alt) {
        if (key === "0") this.sendRichToolbarCommand(event, "formatBlock", "p")
        if (key === "1") this.sendRichToolbarCommand(event, "formatBlock", "h1")
        if (key === "2") this.sendRichToolbarCommand(event, "formatBlock", "h2")
        if (key === "3") this.sendRichToolbarCommand(event, "formatBlock", "h3")
        if (key === "4") this.sendRichToolbarCommand(event, "formatBlock", "h4")
        if (key === "5") this.sendRichToolbarCommand(event, "formatBlock", "h5")
        if (key === "6") this.sendRichToolbarCommand(event, "formatBlock", "h6")
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
    },

    onInput(event) {
    console.log('event', event)
      let content = event.target.innerHTML;
      const pVnode = this._vnode.children && this._vnode.children.find(c => c.elm === this.$refs.textEditor)
      if (pVnode && pVnode.data && pVnode.data.domProps) pVnode.data.domProps.innerHTML = content

      const textCheckDiv = document.createElement('div')
      textCheckDiv.innerHTML = DOMPurify.sanitize(content, domPurifyConfig)
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
      if (!textCheckDiv.textContent.trim() && !textCheckDiv.querySelector('img, ul, ol')) {
        content = '';
      } else {
        content = textCheckDiv.innerHTML
      }

      // The browser seems to keep some kind of context when deleting all text from contenteditable, inserting a span with fontsize into the element oninput.
      // Focusing another input prevents this.
      this.value = content
      if (!this.value) {
        const target = event.target
        const contentEditableContextFixInput = document.createElement('input')
        target.after(contentEditableContextFixInput)

        contentEditableContextFixInput.focus()
        target.focus()
        contentEditableContextFixInput.remove()
      }
      this.textEditorWriteToModel()
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
  },
}
</script>
