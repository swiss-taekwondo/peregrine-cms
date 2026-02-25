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
  <!-- Empty: the actual toolbar is mounted on document.body as a portal -->
  <span style="display:none"/>
</template>

<script>
export default {
  name: 'RichtoolbarInline',
  data() {
    return {
      editorRef: null,
      iframeEl: null,       // the <iframe> DOM element (for page inline editing)
      iframeDoc: null,      // iframe.contentDocument
      visible: false,
      position: { top: 0, left: 0 },
      isInLink: false,
      isBold: false,
      isItalic: false,
      _portalEl: null,
      _portalVm: null,
    }
  },
  mounted() {
    document.addEventListener('selectionchange', this.onSelectionChange)
    document.addEventListener('mousedown', this._onDocMouseDown)
    window.addEventListener('peregrine:iframe-loaded', this._onIframeLoaded)
    window.addEventListener('peregrine:iframe-unloaded', this._onIframeUnloaded)
    this._mountPortal()
  },
  beforeDestroy() {
    document.removeEventListener('selectionchange', this.onSelectionChange)
    document.removeEventListener('mousedown', this._onDocMouseDown)
    window.removeEventListener('peregrine:iframe-loaded', this._onIframeLoaded)
    window.removeEventListener('peregrine:iframe-unloaded', this._onIframeUnloaded)
    this._detachIframeListener()
    this._destroyPortal()
  },
  methods: {
    _mountPortal() {
      const self = this

      // Create a detached div appended directly to body so position:fixed is
      // relative to the viewport, unaffected by any ancestor CSS transform.
      const portalEl = document.createElement('div')
      document.body.appendChild(portalEl)
      this._portalEl = portalEl

      const PortalComponent = Vue.extend({
        data() {
          return {
            visible: false,
            top: 0,
            left: 0,
            isInLink: false,
            isBold: false,
            isItalic: false,
            focusedIndex: -1,
            linkDropdownOpen: false,
          }
        },
        computed: {
          positionStyle() {
            return { top: this.top + 'px', left: this.left + 'px' }
          }
        },
        methods: {
          toggleLinkDropdown() {
            this.linkDropdownOpen = !this.linkDropdownOpen
          },
          closeLinkDropdown() {
            this.linkDropdownOpen = false
          },
          onContainerFocus() {
            // When the toolbar container receives focus (Tab from editor),
            // move focus to the first button using roving tabindex.
            this.$nextTick(() => {
              const btns = this.$el.querySelectorAll('button:not([disabled])')
              if (btns.length > 0) {
                this.focusedIndex = 0
                btns[0].focus()
              }
            })
          },
          onKeydown(e) {
            if (e.key === 'Escape') {
              e.preventDefault()
              if (this.linkDropdownOpen) {
                this.linkDropdownOpen = false
              } else {
                self.hide()
                self._restoreFocusToEditor()
              }
              return
            }
            if (e.key === 'Tab') {
              this.focusedIndex = -1
              this.linkDropdownOpen = false
              return
            }
            const btns = Array.from(this.$el.querySelectorAll('button:not([disabled])'))
            const count = btns.length
            if (count === 0) return
            if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
              e.preventDefault()
              this.focusedIndex = (this.focusedIndex + 1) % count
              btns[this.focusedIndex].focus()
            } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
              e.preventDefault()
              this.focusedIndex = (this.focusedIndex - 1 + count) % count
              btns[this.focusedIndex].focus()
            }
          },
        },
        render(h) {
          if (!this.visible) return h('div', { style: 'display:none' })

          // Simple flat button builder
          const mkBtn = (title, action, iconVnode, opts = {}) => {
            const idx = opts.idx
            return h('button', {
              class: { active: !!opts.active },
              attrs: {
                title,
                tabindex: idx === this.focusedIndex ? '0' : '-1',
                ...(opts.disabled ? { disabled: true } : {}),
              },
              on: {
                mousedown: e => e.preventDefault(),
                click: action,
                focus: () => { if (idx !== undefined) this.focusedIndex = idx },
              },
            }, [iconVnode])
          }

          // Toolbar-level button index (only non-dropdown, non-disabled buttons are tabbable)
          let idx = 0

          const boldBtn = mkBtn('Bold', () => self.execBold(),
            h('i', { class: 'fa fa-bold' }), { active: this.isBold, idx: idx++ })
          const italicBtn = mkBtn('Italic', () => self.execItalic(),
            h('i', { class: 'fa fa-italic' }), { active: this.isItalic, idx: idx++ })

          const sep1 = h('div', { class: 'richtoolbar-inline-separator' })

          // Link toggle button — always opens a dropdown
          const linkToggleIdx = idx++
          const linkToggle = h('button', {
            class: { active: this.isInLink, 'richtoolbar-inline-dropdown-toggle': true },
            attrs: {
              title: this.isInLink ? 'Link options' : 'Insert Link',
              tabindex: linkToggleIdx === this.focusedIndex ? '0' : '-1',
            },
            on: {
              mousedown: e => e.preventDefault(),
              click: () => this.toggleLinkDropdown(),
              focus: () => { this.focusedIndex = linkToggleIdx },
            },
          }, [
            h('i', { class: 'fa fa-link' }),
            h('span', { class: 'richtoolbar-inline-caret' }),
          ])

          // Dropdown menu: Insert Link when not in a link; Edit + Remove when in a link
          const linkDropdownItems = this.isInLink
            ? [
                h('button', {
                  attrs: { title: 'Edit Link', tabindex: '-1' },
                  on: {
                    mousedown: e => e.preventDefault(),
                    click: () => { this.closeLinkDropdown(); self.execEditLink() },
                  },
                }, [h('i', { class: 'fa fa-pencil' }), h('span', ' Edit Link')]),
                h('button', {
                  attrs: { title: 'Remove Link', tabindex: '-1' },
                  on: {
                    mousedown: e => e.preventDefault(),
                    click: () => { this.closeLinkDropdown(); self.execRemoveLink() },
                  },
                }, [h('i', { class: 'fa fa-chain-broken' }), h('span', ' Remove Link')]),
              ]
            : [
                h('button', {
                  attrs: { title: 'Insert Link', tabindex: '-1' },
                  on: {
                    mousedown: e => e.preventDefault(),
                    click: () => { this.closeLinkDropdown(); self.execInsertLink() },
                  },
                }, [h('i', { class: 'fa fa-link' }), h('span', ' Insert Link')]),
              ]

          const linkDropdown = this.linkDropdownOpen
            ? h('div', { class: 'richtoolbar-inline-dropdown' }, linkDropdownItems)
            : null

          const linkGroup = h('div', { class: 'richtoolbar-inline-link-group' }, [
            linkToggle,
            linkDropdown,
          ])

          const sep2 = h('div', { class: 'richtoolbar-inline-separator' })

          const removeFormatIdx = idx++
          const removeFormatBtn = mkBtn('Remove Format', () => self.execRemoveFormat(),
            h('i', { class: 'material-icons', domProps: { textContent: 'format_clear' } }),
            { idx: removeFormatIdx })

          return h('div', {
            class: 'richtoolbar-inline',
            style: this.positionStyle,
            attrs: {
              tabindex: this.focusedIndex === -1 ? '0' : '-1',
              role: 'toolbar',
              'aria-label': 'Text formatting',
            },
            on: {
              mousedown: e => e.stopPropagation(),
              focus: this.onContainerFocus,
              keydown: this.onKeydown,
            },
          }, [boldBtn, italicBtn, sep1, linkGroup, sep2, removeFormatBtn])
        }
      })

      this._portalVm = new PortalComponent().$mount(portalEl)
    },

    _destroyPortal() {
      if (this._portalVm) {
        this._portalVm.$destroy()
        this._portalVm = null
      }
      if (this._portalEl && this._portalEl.parentNode) {
        this._portalEl.parentNode.removeChild(this._portalEl)
        this._portalEl = null
      }
    },

    _syncPortal() {
      if (!this._portalVm) return
      this._portalVm.visible = this.visible
      this._portalVm.top = this.position.top
      this._portalVm.left = this.position.left
      this._portalVm.isInLink = this.isInLink
      this._portalVm.isBold = this.isBold
      this._portalVm.isItalic = this.isItalic
      // Reset roving focus and link dropdown when toolbar hides
      if (!this.visible) {
        this._portalVm.focusedIndex = -1
        this._portalVm.linkDropdownOpen = false
      }
    },

    _restoreFocusToEditor() {
      if (this.editorRef) {
        this.editorRef.focus()
      } else if (this.iframeDoc) {
        const active = this.iframeDoc.querySelector('.inline-edit.inline-editing')
        if (active) active.focus()
      }
    },

    setEditorRef(el) {
      this.editorRef = el
    },

    setIframeRef(iframeEl) {
      this._detachIframeListener()
      this.iframeEl = iframeEl
      this.iframeDoc = iframeEl ? iframeEl.contentDocument : null
      if (this.iframeDoc) {
        this.iframeDoc.addEventListener('selectionchange', this.onSelectionChange)
      }
    },

    _onIframeLoaded(event) {
      // Only the subnav instance (no editorRef) tracks the iframe.
      // The texteditor instance tracks only its own <p> element.
      if (this.editorRef) return
      this.setIframeRef(event.detail.iframeEl)
    },

    _onIframeUnloaded() {
      this._detachIframeListener()
      this.hide()
    },

    _detachIframeListener() {
      if (this.iframeDoc) {
        this.iframeDoc.removeEventListener('selectionchange', this.onSelectionChange)
        this.iframeDoc = null
        this.iframeEl = null
      }
    },

    onSelectionChange() {
      this.updateState()
    },

    updateState() {
      // Check selection in admin document first, then iframe document
      const adminSel = document.getSelection()
      const iframeSel = this.iframeDoc ? this.iframeDoc.defaultView.getSelection() : null

      // Determine which selection is active and non-collapsed
      let sel = null
      let iframeOffset = null

      if (adminSel && adminSel.rangeCount > 0 && !adminSel.isCollapsed && this.editorRef) {
        const range = adminSel.getRangeAt(0)
        if (this.editorRef.contains(range.commonAncestorContainer)) {
          sel = adminSel
        }
      }

      if (!sel && iframeSel && iframeSel.rangeCount > 0 && !iframeSel.isCollapsed) {
        // Selection is in the iframe — compute offset of iframe relative to admin viewport
        if (this.iframeEl) {
          iframeOffset = this.iframeEl.getBoundingClientRect()
          sel = iframeSel
        }
      }

      if (!sel) {
        this.visible = false
        this._syncPortal()
        return
      }

      const range = sel.getRangeAt(0)
      const rect = range.getBoundingClientRect()

      // If rect is zero-sized (e.g. collapsed or not rendered), hide
      if (rect.width === 0 && rect.height === 0) {
        this.visible = false
        this._syncPortal()
        return
      }

      const TOOLBAR_HEIGHT = 36
      const GAP = 8

      // For iframe selection, offset rect by iframe position in admin viewport
      const offsetTop = iframeOffset ? iframeOffset.top : 0
      const offsetLeft = iframeOffset ? iframeOffset.left : 0

      let top = (rect.top + offsetTop) - TOOLBAR_HEIGHT - GAP
      if (top < 0) top = (rect.bottom + offsetTop) + GAP

      let left = (rect.left + offsetLeft) + rect.width / 2
      left = Math.max(60, Math.min(left, window.innerWidth - 60))

      this.position = { top, left }

      try {
        const queryDoc = iframeOffset ? this.iframeDoc : document
        this.isBold = queryDoc.queryCommandState('bold')
        this.isItalic = queryDoc.queryCommandState('italic')
      } catch (e) {
        this.isBold = false
        this.isItalic = false
      }

      const anchorNode = sel.anchorNode
      const el = anchorNode
        ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode)
        : null
      this.isInLink = !!(el && el.closest('a'))

      this.visible = true
      this._syncPortal()
    },

    hide() {
      this.visible = false
      this._syncPortal()
    },

    _onDocMouseDown(e) {
      // Hide the toolbar when clicking outside of it
      if (this._portalEl && !this._portalEl.contains(e.target)) {
        this.hide()
      }
    },

    _execDoc() {
      // Use iframe document for iframe inline editing, admin document otherwise
      return this.iframeDoc || document
    },
    _writeToModel() {
      $perAdminApp.action(this, 'pingRichToolbar')
      if (this.iframeDoc) {
        // Iframe inline editing: use the contentview write action
        $perAdminApp.action(this, 'writeInlineToModel')
      } else {
        // Sidebar rich text field: use texteditor write action
        $perAdminApp.action(this, 'textEditorWriteToModel')
      }
    },
    execBold() {
      this._execDoc().execCommand('bold')
      this.updateState()
      this._writeToModel()
    },
    execItalic() {
      this._execDoc().execCommand('italic')
      this.updateState()
      this._writeToModel()
    },
    execRemoveFormat() {
      this._execDoc().execCommand('removeFormat')
      this.updateState()
      this._writeToModel()
    },
    _saveSelectionState() {
      // For iframe inline editing, save anchor/container/doc to view state so
      // the subnav richtoolbar's getLastAnchor()/getLastContainer()/getLastDoc() work.
      if (!this.iframeDoc) return
      const view = $perAdminApp.getView()
      if (!view) return
      const sel = this.iframeDoc.defaultView.getSelection()
      const anchorNode = sel && sel.rangeCount > 0 ? sel.anchorNode : null
      const el = anchorNode
        ? (anchorNode.nodeType === Node.TEXT_NODE ? anchorNode.parentElement : anchorNode)
        : null
      const container = el ? el.closest('.inline-edit') : null
      // set() from utils — available globally via import in the component tree,
      // but richtoolbarinline doesn't import it. Use direct assignment on the view object.
      const state = view.state || (view.state = {})
      const inline = state.inline || (state.inline = {})
      inline.lastAnchor = el ? el.closest('a') : null
      inline.lastContainer = container
      inline.lastDoc = this.iframeDoc
    },
    execInsertLink() {
      this._saveSelectionState()
      this.hide()
      $perAdminApp.action(this, 'insertLink')
    },
    execEditLink() {
      this._saveSelectionState()
      this.hide()
      $perAdminApp.action(this, 'editLink')
    },
    execRemoveLink() {
      this._saveSelectionState()
      this.hide()
      $perAdminApp.action(this, 'removeLink')
      this.$nextTick(() => this.updateState())
    },
  }
}
</script>
