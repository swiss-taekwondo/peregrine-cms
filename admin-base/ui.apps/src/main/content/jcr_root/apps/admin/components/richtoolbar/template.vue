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
<script>
import {
  actionsGroup,
  alignGroup,
  alwaysActiveGroup,
  boldItalicGroup,
  iconsGroup,
  imageGroup,
  linkGroup,
  listGroup,
  removeFormatGroup,
  responsiveMenuGroup,
  specialCharactersGroup,
  superSubScriptGroup,
  textFormatGroup,
} from './groups'
import {get, restoreSelection, saveSelection, set} from '../../../../../../js/utils'
import {IconLib, PathBrowser} from '../../../../../../js/constants'
import RichtoolbarFontSize from '../richtoolbarfontsize/template.vue'
import RichtoolbarGroup from '../richtoolbargroup/template.vue'
import Pathbrowser from '../pathbrowser/template.vue'

// ---------------------------------------------------------------------------
// Pure render helpers (no Vue dependency)
// ---------------------------------------------------------------------------

function renderIcon(h, icon, lib) {
  if (!icon) return null
  if (lib === IconLib.FONT_AWESOME)
    return h('i', { class: `fa fa-${icon}` })
  if (lib === IconLib.PLAIN_TEXT)
    return h('span', { domProps: { innerHTML: icon } })
  // default: Material Icons
  return h('i', { class: 'material-icons', domProps: { textContent: icon } })
}

function resolveClass(cls) {
  if (typeof cls === 'function') return cls()
  return cls || ''
}

function renderBtn(h, btn, vm, keyPrefix, index, inDropdown = false) {
  const isActive = btn.isActive ? btn.isActive() : false
  const extraClass = resolveClass(btn.class)
  const uniqueKey = keyPrefix != null
    ? (index != null ? `${keyPrefix}-${index}` : `${keyPrefix}-${btn.label}`)
    : btn.label
  const classes = inDropdown
    ? ['rtb-menu-item', { active: isActive }, extraClass]
    : ['rtb-btn', 'btn', { active: isActive }, extraClass]
  const label = btn.title ? vm.$i18n(btn.title) : vm.$i18n(btn.label)
  return h('button', {
    key: uniqueKey,
    class: classes,
    attrs: { title: label, type: 'button' },
    on: {
      mousedown: e => e.preventDefault(),
      click: () => btn.click ? btn.click() : vm.exec(btn.cmd),
    },
  }, [renderIcon(h, btn.icon, btn.iconLib || IconLib.FONT_AWESOME)])
}

// ---------------------------------------------------------------------------

export default {
  name: 'RichToolbar',
  components: { RichtoolbarFontSize, RichtoolbarGroup, Pathbrowser },
  props: {
    showAlwaysActive: {
      type: Boolean,
      default: true,
    },
    responsive: {
      type: Boolean,
      default: true,
    },
    editorContent: {
      type: String,
      default: '',
    },
    onSubNav: {
      type: Boolean,
      default: false,
    },
  },

  data() {
    return {
      openGroups: {},       // label → boolean, tracks open dropdowns
      selection: {
        restore: false,
        buffer: null,
        doc: null,
        container: null,
        content: null,
      },
      param: {
        cmd: null,
        value: null,
      },
      browser: {
        element: null,
        open: false,
        header: '',
        root: '',
        type: 'image',
        withLinkTab: false,
        withImageTab: false,
        newWindow: false,
        linkTitle: '',
        altText: undefined,
        path: {
          current: '',
          selected: null,
        },
        rel: true,
        img: {
          width: null,
          height: null,
        },
      },
      docEl: {
        dimension: {
          w: 0,
        },
      },
      size: {
        button: 34,
        group: 4,
      },
      hiddenGroups: {},

      historyStack: [],
      historyIndex: -1,
      isUndoing: false,
    }
  },

  computed: {
    alwaysActiveGroup() {
      return alwaysActiveGroup(this)
    },
    groups() {
      // eslint-disable-next-line no-unused-expressions
      this.inlinePing // track ping so groups re-evaluate on every editor interaction
      return [
        actionsGroup(this),
        textFormatGroup(this),
        boldItalicGroup(this),
        superSubScriptGroup(this),
        linkGroup(this),
        imageGroup(this),
        alignGroup(this),
        listGroup(this),
        iconsGroup(this),
        specialCharactersGroup(this),
        removeFormatGroup(this),
      ]
    },
    filteredGroups() {
      return this.groups.filter((group) => this.groupAllowed(group))
    },
    responsiveMenuGroup() {
      return responsiveMenuGroup(this)
    },
    inline() {
      if (!$perAdminApp.getView() || !$perAdminApp.getView().state) return null
      return $perAdminApp.getView().state.inline
    },
    inlineRich() {
      if (!this.inline) return null
      return this.inline.rich
    },
    inlinePing() {
      if (!this.inline) return 30
      return this.inline.ping || 20
    },
    viewport() {
      return $perAdminApp.getNodeFromViewOrNull('/state/tools/workspace/view')
    },
    preview() {
      return $perAdminApp.getNodeFromViewOrNull('/state/tools/workspace/preview')
    },
    roots() {
      return $perAdminApp.getNodeFromViewOrNull('/state/tenant/roots')
    },
    specialCases() {
      return {
        link: this.insertLink,
        insertImage: this.insertImage,
        editImage: this.editImage,
        editIcon: this.editIcon,
        preview: this.togglePreview,
        previewInNewTab: this.previewInNewTab,
        updateFontSize: this.updateFontSize,
        undo: this.undo,
        redo: this.redo,
      }
    },
  },

  mounted() {
    this.$nextTick(() => {
      window.addEventListener('resize', this.updateDocElDimensions)
      this.updateDocElDimensions()
    })
    this.saveSnapshot()
    this.$watch('editorContent', () => {
      if (this.isUndoing) {
        this.isUndoing = false
      }
      this.saveSnapshot()
    })
    if (!this.onSubNav) {
      window.addEventListener('inline-richtoolbar:cmd', this.inlineCmdHandler)
    }
    document.addEventListener('mousedown', this._closeDropdowns)
  },

  beforeDestroy() {
    window.removeEventListener('resize', this.updateDocElDimensions)
    window.removeEventListener('inline-richtoolbar:cmd', this.inlineCmdHandler)
    document.removeEventListener('mousedown', this._closeDropdowns)
  },

  render(h) {
    const disabled = !this.inlineRich || this.preview === 'preview'
    const children = []

    // Always-active group
    if (this.groupAllowed(this.alwaysActiveGroup)) {
      children.push(this._renderGroup(h, this.alwaysActiveGroup, true))
    }

    // Regular groups
    for (const group of this.filteredGroups) {
      children.push(this._renderGroup(h, group, false))
    }

    // Responsive overflow group
    if (this.groupAllowed(this.responsiveMenuGroup)) {
      children.push(this._renderGroup(h, this.responsiveMenuGroup, true))
    }

    // Font size selector
    children.push(h(RichtoolbarFontSize, {
      key: 'font-size',
      props: {
        exec: this.exec,
        isRangeInEditor: this.isRangeInEditor,
        isNodeInEditor: this.isNodeInEditor,
        getDefaultFontSize: this.getDefaultFontSize,
        getSelection: this.getSelection,
        getEditorSelection: this.getEditorSelection,
      },
    }))

    // Pathbrowser (mounted inline when open)
    if (this.browser.open) {
      children.push(h(Pathbrowser, {
        key: 'pathbrowser',
        props: {
          isOpen: this.browser.open,
          header: this.browser.header,
          browserRoot: this.browser.root,
          browserType: this.browser.type,
          withLinkTab: this.browser.withLinkTab,
          withImageTab: this.browser.withImageTab,
          newWindow: this.browser.newWindow,
          linkTitle: this.browser.linkTitle,
          setLinkTitle: this.setBrowserLinkTitle,
          altText: this.browser.altText,
          setAltText: this.setBrowserAltText,
          currentPath: this.browser.path.current,
          setCurrentPath: this.setBrowserPathCurrent,
          selectedPath: this.browser.path.selected,
          setSelectedPath: this.setBrowserPathSelected,
          setResourceType: this.setBrowserResourceType,
          rel: this.browser.rel,
          imgWidth: this.browser.img.width,
          imgHeight: this.browser.img.height,
          onCancel: this.onBrowserCancel,
        },
        on: {
          'toggle-newWindow': this.toggleBrowserNewWindow,
          'toggle-rel': () => { this.browser.rel = !this.browser.rel },
          'update-img-width': v => { this.browser.img.width = v },
          'update-img-height': v => { this.browser.img.height = v },
          select: this.onBrowserSelect,
        },
      }))
    }

    return h('div', {
      class: ['richtoolbar', { disabled }],
      ref: 'richToolbar',
    }, children)
  },

  methods: {
    // -----------------------------------------------------------------------
    // Render helpers
    // -----------------------------------------------------------------------

    _renderGroup(h, group, alwaysActive) {
      const items = typeof group.items === 'function' ? group.items() : group.items
      if (!items || items.length === 0) return null

      const label = typeof group.label === 'function' ? group.label() : group.label
      const icon = typeof group.icon === 'function' ? group.icon() : group.icon
      const iconLib = group.iconLib || IconLib.FONT_AWESOME
      const groupClass = resolveClass(group.class)

      // Searchable groups (icons, special-characters) use the legacy MaterializeDropDown path
      if (group.searchable) {
        return h(RichtoolbarGroup, {
          key: `group-${label}`,
          props: {
            icon,
            iconLib,
            collapse: !!group.collapse,
            label,
            active: this.groupIsActive(group),
            items,
            searchable: true,
          },
          on: {
            'toggle-click': () => { if (group.toggleClick) group.toggleClick() },
            click: ({ btn }) => { if (btn.click) btn.click(); else this.exec(btn.cmd) },
          },
        })
      }

      // Filter out dividers (string sentinels) and apply per-item rules if defined
      const itemRules = group.itemRules || null
      const realItems = items.filter((it, i) => {
        if (!it || typeof it !== 'object') return false
        if (itemRules && itemRules[i] && !itemRules[i]()) return false
        return true
      })

      if (group.collapse) {
        const groupIsActive = this.groupIsActive(group)

        if (realItems.length <= 1) {
          // Only one visible item — render as a plain button, no caret/dropdown
          const singleItem = realItems[0]
          const clickFn = group.toggleClick
            ? () => group.toggleClick()
            : singleItem
              ? () => singleItem.click ? singleItem.click() : this.exec(singleItem.cmd)
              : () => {}
          return h('div', {
            key: `group-${label}`,
            class: ['btn-group', 'rtb-group', `group-${label}`, { 'group-always-active': alwaysActive }, groupClass],
          }, [h('button', {
            class: ['rtb-btn', 'btn', { active: groupIsActive }, groupClass],
            attrs: { title: this.$i18n(label), type: 'button' },
            on: { mousedown: e => e.preventDefault(), click: clickFn },
          }, [renderIcon(h, icon, iconLib)])])
        }

        // 2+ visible items — render as dropdown
        const isOpen = !!this.openGroups[label]
        const toggleBtn = h('button', {
          class: ['rtb-btn', 'btn', 'rtb-dropdown-toggle', { active: groupIsActive }, groupClass],
          attrs: { title: this.$i18n(label), type: 'button' },
          on: {
            mousedown: e => e.preventDefault(),
            click: () => this._toggleGroup(label),
          },
        }, [
          renderIcon(h, icon, iconLib),
          h('span', { class: 'rtb-caret' }),
        ])

        const menuItems = realItems.map((btn, i) => {
          if (btn.items && btn.items.length > 0) {
            return this._renderGroup(h, btn, false)
          }
          return renderBtn(h, btn, this, label, i, true)
        })

        const menu = h('div', {
          class: ['rtb-dropdown-menu', { 'rtb-dropdown-menu--open': isOpen }],
        }, [h('div', { class: 'rtb-dropdown-items-list' }, menuItems)])

        return h('div', {
          key: `group-${label}`,
          class: ['btn-group', 'rtb-group', 'rtb-group--dropdown', `group-${label}`],
        }, [toggleBtn, menu])
      }

      // Flat group — render items inline
      const btnNodes = realItems.map((btn, i) => {
        if (btn.items && btn.items.length > 0) {
          return this._renderGroup(h, btn, false)
        }
        return renderBtn(h, btn, this, label, i)
      })

      return h('div', {
        key: `group-${label}`,
        class: ['btn-group', 'rtb-group', `group-${label}`, { 'group-always-active': alwaysActive }, groupClass],
      }, btnNodes)
    },

    _closeDropdowns(e) {
      if (!this.$el || !this.$el.contains(e.target)) {
        this.openGroups = {}
      }
    },

    _toggleGroup(label) {
      this.$set(this.openGroups, label, !this.openGroups[label])
    },

    // -----------------------------------------------------------------------
    // All original methods preserved exactly
    // -----------------------------------------------------------------------

    inlineCmdHandler(event) {
      if (this.onSubNav) return
      this[event.detail.cmd]()
    },

    saveSnapshot() {
      const content = this.editorContent
      if (this.historyIndex > -1 && this.historyStack[this.historyIndex] === content) {
        return
      }
      if (this.historyIndex < this.historyStack.length - 1) {
        this.historyStack = this.historyStack.slice(0, this.historyIndex + 1)
      }
      this.historyStack.push(content)
      this.historyIndex++
      if (this.historyStack.length > 50) {
        this.historyStack.shift()
        this.historyIndex--
      }
    },

    undo() {
      if (this.onSubNav) {
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'undo' } }))
        return
      }
      if (this.historyIndex > 0) {
        this.isUndoing = true
        this.historyIndex--
        const content = this.historyStack[this.historyIndex]
        const textEditor = this.$el.closest('.text-editor-wrapper')
          ? this.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
          : this.$el.nextElementSibling
        textEditor.innerHTML = content
        $perAdminApp.action(this, 'textEditorWriteToModel')
        this.$nextTick(() => this.isUndoing = false)
      }
    },

    redo() {
      if (this.onSubNav) {
        window.dispatchEvent(new CustomEvent('inline-richtoolbar:cmd', { detail: { cmd: 'redo' } }))
        return
      }
      if (this.historyIndex < this.historyStack.length - 1) {
        this.isUndoing = true
        this.historyIndex++
        const content = this.historyStack[this.historyIndex]
        const textEditor = this.$el.closest('.text-editor-wrapper')
          ? this.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
          : this.$el.nextElementSibling
        textEditor.innerHTML = content
        $perAdminApp.action(this, 'textEditorWriteToModel')
        this.$nextTick(() => this.isUndoing = false)
      }
    },

    getDefaultFontSize() {
      const iframeWindow = document.querySelector('iframe#editview')?.contentWindow
      if (!iframeWindow) return 16
      const currentInlineEditor = iframeWindow.document.querySelector(
        '.inline-edit[contenteditable="true"] > *'
      )
      if (!currentInlineEditor) return null
      return iframeWindow.getComputedStyle(currentInlineEditor).fontSize
    },

    wrapTextNodesInRange(range, fontSize) {
      const textNodes = []
      const walker = document.createTreeWalker(
        range.commonAncestorContainer,
        NodeFilter.SHOW_TEXT,
        {
          acceptNode: node => {
            if (!node.nodeValue.trim()) return NodeFilter.FILTER_REJECT
            const nodeRange = document.createRange()
            nodeRange.selectNodeContents(node)
            return range.intersectsNode(node) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
          },
        }
      )
      let node
      while (node = walker.nextNode()) {
        textNodes.push(node)
      }
      const fontSizeNodes = []
      for (let i = 0; i < textNodes.length; i++) {
        const textNode = textNodes[i]
        const nodeRange = document.createRange()
        nodeRange.selectNodeContents(textNode)
        if (textNode === range.startContainer || textNode.contains(range.startContainer)) {
          nodeRange.setStart(range.startContainer, range.startOffset)
        }
        if (textNode === range.endContainer || textNode.contains(range.endContainer)) {
          nodeRange.setEnd(range.endContainer, range.endOffset)
        }
        const existingSpan = textNode.parentElement.tagName === 'SPAN' && textNode.parentElement.childNodes.length === 1
          ? textNode.parentElement : null
        if (existingSpan) {
          existingSpan.style.fontSize = fontSize
          fontSizeNodes.push(existingSpan)
        } else {
          const span = document.createElement('span')
          span.style.fontSize = fontSize
          nodeRange.surroundContents(span)
          fontSizeNodes.push(span)
        }
      }
      return fontSizeNodes
    },

    getEditorSelection(returnRange = true) {
      const selection = window.getSelection()
      const iframeSelection = document.querySelector('iframe#editview')?.contentDocument.getSelection()
      if (selection?.rangeCount > 0) {
        const range = selection.getRangeAt(0)
        if (this.isRangeInEditor(range)) return returnRange ? range : selection
      }
      if (iframeSelection?.rangeCount > 0) {
        const iframeRange = iframeSelection.getRangeAt(0)
        if (this.isRangeInEditor(iframeRange)) return returnRange ? iframeRange : iframeSelection
      }
    },

    getEditorFrom(range) {
      const getEditorFromEl = typeof range.startContainer.closest === 'function'
        ? range.startContainer
        : range.startContainer.parentElement
      return getEditorFromEl.closest('.inline-edit[contenteditable="true"]')
    },

    isRangeInEditor(range) {
      if (!range) return false
      const textEditor = this.getEditorFrom(range)
      if (!textEditor) return false
      const elementRange = textEditor.ownerDocument.createRange()
      elementRange.selectNodeContents(textEditor)
      return (
        range.compareBoundaryPoints(Range.START_TO_START, elementRange) >= 0 &&
        range.compareBoundaryPoints(Range.END_TO_END, elementRange) <= 0
      )
    },

    isNodeInEditor(node) {
      if (!node) return false
      const textEditor = this.getEditorFrom({ startContainer: node })
      return textEditor.contains(node)
    },

    selectNodes(nodeArray) {
      const selection = this.getEditorSelection(false)
      selection.removeAllRanges()
      const reselectRange = nodeArray[0].ownerDocument.createRange()
      reselectRange.setStart(nodeArray[0], 0)
      reselectRange.setEnd(nodeArray[nodeArray.length - 1], nodeArray[nodeArray.length - 1].childNodes.length)
      selection.addRange(reselectRange)
    },

    updateFontSize(newSize) {
      const fontSize = `${newSize}px`
      const range = this.getEditorSelection()
      const textEditor = this.getEditorFrom(range)
      if (!this.isRangeInEditor(range, textEditor)) {
        console.warn('Selection range outside of Richtext Editor')
        return
      }

      function setFontSizeOfEl(element, fontSizeStr) {
        element.querySelectorAll('*[style*="font-size"]').forEach(element => {
          if (element.tagName === 'SPAN') {
            element.replaceWith(...element.childNodes)
          } else {
            element.style.removeProperty('font-size')
          }
        })
        element.style.fontSize = fontSizeStr
      }

      const noHighlight = range.endContainer.isEqualNode(range.startContainer) && range.endOffset === range.startOffset
      if (noHighlight) {
        const parentQuery = 'p, ul, ol, h1, h2, h3, h4, h5, h6'
        const fontSizeParent = (range.startContainer.contentEditable === 'true' && range.startContainer?.children?.length > 0)
          ? range.startContainer.children[0]
          : typeof range.startContainer.closest === 'function'
            ? range.startContainer.closest(parentQuery)
            : range.startContainer.parentElement.closest(parentQuery)
        if (!textEditor.contains(fontSizeParent)) {
          console.warn('Attempting to change fontsize of paragraph outside of richtext editor', fontSizeParent, range.startContainer)
          return
        }
        setFontSizeOfEl(fontSizeParent, fontSize)
        fontSizeParent.style.fontSize = fontSize
        textEditor.dispatchEvent(new Event('input'))
        return
      }

      if (range.startContainer.nodeType === Node.TEXT_NODE) {
        const newText = range.startContainer.splitText(range.startOffset)
        range.setStart(newText, 0)
      }
      if (range.endContainer.nodeType === Node.TEXT_NODE) {
        range.endContainer.splitText(range.endOffset)
        range.setEnd(range.endContainer, range.endContainer.length)
      }

      const onlySingleNode = range.startContainer.isEqualNode(range.endContainer)
      if (onlySingleNode) {
        if (range.startContainer.nodeType === Node.TEXT_NODE) {
          const span = document.createElement('span')
          span.style.fontSize = fontSize
          range.surroundContents(span)
          this.selectNodes([span])
          return
        } else {
          setFontSizeOfEl(range.startContainer, fontSize)
        }
      }

      const nodeRanges = this.wrapTextNodesInRange(range, fontSize)

      textEditor.querySelectorAll('span[style*="font-size"]:has(> span[style*="font-size"])').forEach(span => {
        if (span.childNodes.length === 1) {
          span.replaceWith(span.childNodes[0])
        }
      })

      this.selectNodes(nodeRanges)
      const ownerDoc = nodeRanges[0].ownerDocument
      const selectionOfNodes = ownerDoc.getSelection().getRangeAt(0)

      const startSelectionMarkId = crypto.randomUUID()
      const startOffset = selectionOfNodes.startOffset
      const endSelectionMarkId = crypto.randomUUID()
      const endOffset = selectionOfNodes.endOffset
      nodeRanges[0].dataset.startSelectionMarkId = startSelectionMarkId
      nodeRanges[nodeRanges.length - 1].dataset.endSelectionMarkId = endSelectionMarkId

      if (ownerDoc.querySelector('iframe#editview')) {
        $perAdminApp.action(this, 'textEditorWriteToModel')
      } else {
        $perAdminApp.action(this, 'writeInlineToModel')
      }

      this.$nextTick(() => {
        this.$nextTick(() => {
          const selectionAfter = ownerDoc.getSelection()
          selectionAfter.removeAllRanges()
          const newRange = ownerDoc.createRange()
          const startEl = ownerDoc.querySelector(`[data-start-selection-mark-id="${startSelectionMarkId}"]`)
          newRange.setStart(startEl, startOffset)
          delete startEl.dataset.startSelectionMarkId
          const endEl = ownerDoc.querySelector(`[data-end-selection-mark-id="${endSelectionMarkId}"]`)
          newRange.setEnd(endEl, endOffset)
          delete endEl.dataset.endSelectionMarkId
          selectionAfter.addRange(newRange)
        })
      })
    },

    pingRichToolbar(vm = this) {
      vm.$emit('ping')
      const view = $perAdminApp.getView()
      if (view) {
        const state = view.state || (view.state = {})
        const inline = state.inline || (state.inline = {})
        inline.ping = (inline.ping || 0) + 1
      }
      $perAdminApp.action(vm, 'reWrapEditable')
    },

    getInlineDoc() {
      if (!this.inline) return null
      return this.inline.doc
    },

    getInlineContainer() {
      if (!this.getInlineDoc()) return
      return this.getInlineDoc().querySelector('.inline-edit.inline-editing')
    },

    execCmd(cmd, value = null, showUi = true) {
      if (!this.getInlineDoc() || !this.getInlineDoc().execCommand) return
      this.getInlineDoc().execCommand(cmd, showUi, value)
    },

    queryCmdState(cmd) {
      if (!this.getInlineDoc() || !this.getInlineDoc().queryCommandState) return
      return this.getInlineDoc().queryCommandState(cmd) || false
    },

    exec(cmd, value = null) {
      if (Object.keys(this.specialCases).indexOf(cmd) >= 0) {
        this.specialCases[cmd](value)
      } else {
        this.execCmd(cmd, value)
      }
      this.pingRichToolbar()
    },

    link() {
      this.insertLink()
    },

    getLastAnchor() {
      return $perAdminApp.getNodeFromViewOrNull('/state/inline/lastAnchor')
    },

    getLastContainer() {
      return $perAdminApp.getNodeFromViewOrNull('/state/inline/lastContainer')
    },

    getLastDoc() {
      return $perAdminApp.getNodeFromViewOrNull('/state/inline/lastDoc')
    },

    getAnchorAtSelection() {
      // Try active inline doc first, then last doc (iframe may have lost focus)
      const doc = this.getInlineDoc() || this.getLastDoc()
      if (!doc || !doc.defaultView) return null
      const selection = doc.defaultView.getSelection()
      if (!selection || selection.rangeCount <= 0) return null
      const node = selection.anchorNode
      if (!node) return null
      const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node
      return el.closest('a')
    },

    insertLink() {
      const doc = this.getInlineDoc() || this.getLastDoc()
      const container = this.getInlineContainer() || this.getLastContainer()
      if (!doc || !container) return

      const startPath = this.roots.pages
      this.param.cmd = 'insertLink'
      this.browser.header = this.$i18n('Insert Link')
      this.browser.path.current = startPath
      this.browser.path.selected = null
      this.browser.withLinkTab = true
      this.browser.newWindow = false
      this.browser.rel = true
      this.browser.linkTitle = ''
      this.browser.type = PathBrowser.Type.PAGE
      this.selection.doc = doc
      this.selection.container = container
      this.selection.buffer = saveSelection(container, doc)
      this.selection.restore = true
      this.startBrowsing(startPath)
    },

    editLink() {
      // Prefer live anchor at current selection; fall back to saved last anchor
      const anchor = this.getAnchorAtSelection() || this.getLastAnchor()
      if (!anchor) return

      const doc = this.getInlineDoc() || this.getLastDoc()
      const container = this.getInlineContainer() || this.getLastContainer()
      if (!doc || !container) return

      const href = anchor.getAttribute('href') || ''
      const title = anchor.getAttribute('title') || ''
      const target = anchor.getAttribute('target') || ''
      const rel = anchor.getAttribute('rel') || ''

      this.selection.content = anchor.innerHTML
      this.selection.doc = doc
      this.selection.container = container
      this.selection.buffer = saveSelection(container, doc)
      this.selection.restore = true

      // Save anchor to view state so onLinkSelect can use it after pathbrowser closes
      const view = $perAdminApp.getView()
      if (view) {
        const state = view.state || (view.state = {})
        const inline = state.inline || (state.inline = {})
        inline.lastAnchor = anchor
        inline.lastContainer = container
        inline.lastDoc = doc
      }

      this.param.cmd = 'editLink'
      this.browser.header = this.$i18n('Edit Link')
      this.browser.withLinkTab = true
      this.browser.newWindow = target === '_blank'
      this.browser.rel = rel.includes('noopener')
      this.browser.linkTitle = title
      this.browser.type = PathBrowser.Type.PAGE

      let startPath
      let resolvedHref = href

      // If the href is absolute, check if it's same-origin (browser resolves relative hrefs
      // to absolute when pasting, so a pasted internal link like /events/foo.html becomes
      // https://admin-host/events/foo.html)
      if (/^https?:\/\//.test(href)) {
        try {
          const url = new URL(href)
          if (url.hostname === window.location.hostname) {
            // Same origin — treat as internal, use just the pathname
            resolvedHref = url.pathname
          }
        } catch (e) { /* Do nothing */ }
      }

      const isExternal = /^https?:\/\//.test(resolvedHref)
      if (isExternal) {
        this.browser.path.selected = resolvedHref
        startPath = this.roots.pages || '/'
      } else {
        let hrefPath = resolvedHref.replace(/\.html$/, '')
        // If the path is a publish-side relative path (not a JCR path), prepend the pages root
        if (!hrefPath.startsWith('/content/')) {
          hrefPath = (this.roots.pages || '') + hrefPath
        }
        const lastSlash = hrefPath.lastIndexOf('/')
        startPath = lastSlash > 0 ? hrefPath.substring(0, lastSlash) : (this.roots.pages || '/')
        this.browser.path.selected = hrefPath
      }
      this.browser.path.current = startPath
      this.startBrowsing(startPath)
    },

    removeLink() {
      // Prefer live anchor at current selection; fall back to saved last anchor
      const anchor = this.getAnchorAtSelection() || this.getLastAnchor()
      if (!anchor) return

      const doc = this.getInlineDoc() || this.getLastDoc()
      const container = this.getInlineContainer() || this.getLastContainer()
      if (!doc || !container) return

      container.focus()
      this.$nextTick(() => {
        const win = doc.defaultView
        const sel = win.getSelection()
        const range = doc.createRange()
        range.selectNodeContents(anchor)
        sel.removeAllRanges()
        sel.addRange(range)
        doc.execCommand('unlink', false, null)
        $perAdminApp.action(this, 'writeInlineToModel')
        $perAdminApp.action(this, 'textEditorWriteToModel')
      })
    },

    insertImage() {
      this.param.cmd = 'insertImage'
      this.browser.header = this.$i18n('Insert Image')
      this.browser.path.current = this.roots.assets
      this.browser.withLinkTab = true
      this.browser.newWindow = undefined
      this.browser.type = PathBrowser.Type.ASSET
      this.browser.path.suffix = ''
      this.saveSelection()
      this.selection.restore = true
      this.startBrowsing()
    },

    editImage(vm = this, target) {
      const title = target.getAttribute('title')
      const src = target.getAttribute('src')
      const srcArr = src.split('/')
      const img = {
        width: target.style.width ? parseInt(target.style.width) : null,
        height: target.style.height ? parseInt(target.style.height) : null,
      }
      vm.param.cmd = 'editImage'
      vm.browser.header = vm.$i18n('Edit Image')
      vm.browser.path.selected = srcArr.join('/')
      srcArr.pop()
      vm.browser.path.current = srcArr.join('/')
      vm.browser.withLinkTab = true
      vm.browser.newWindow = undefined
      vm.browser.type = PathBrowser.Type.ASSET
      vm.browser.linkTitle = title
      vm.browser.element = target
      vm.browser.img.width = img.width
      vm.browser.img.height = img.height
      vm.startBrowsing()
    },

    insertIcon(imgPath) {
      const doc = this.selection.doc
      const container = this.selection.container
      const buffer = this.selection.buffer
      if (!doc || !container || !buffer) return
      container.focus()
      this.$nextTick(() => {
        restoreSelection(container, buffer, doc)
        doc.execCommand('insertHTML', true, `<img src="${imgPath}" class="peregrine-icon" alt=""/>`)
        // execCommand may strip style attributes — find the inserted img and set size via DOM
        const inserted = container.querySelector(`img.peregrine-icon[src="${imgPath}"]:not([style])`)
        if (inserted) {
          inserted.style.width = '20px'
          inserted.style.height = '20px'
        }
        $perAdminApp.action(this, 'writeElementToModel', container)
        this.pingRichToolbar()
      })
    },

    editIcon(vm = this, target) {
      const alt = target.getAttribute('alt') || ''
      const src = target.getAttribute('src') || ''
      const img = {
        width: target.style.width ? parseInt(target.style.width) : 20,
        height: target.style.height ? parseInt(target.style.height) : 20,
      }
      vm.param.cmd = 'editIcon'
      vm.browser.header = vm.$i18n('Edit Icon')
      vm.browser.withLinkTab = false
      vm.browser.withImageTab = true
      vm.browser.path.selected = src
      vm.browser.path.current = src
      vm.browser.type = PathBrowser.Type.ASSET
      vm.browser.altText = alt
      vm.browser.linkTitle = undefined
      vm.browser.newWindow = undefined
      vm.browser.element = target
      vm.browser.img.width = img.width
      vm.browser.img.height = img.height
      vm.saveSelection()
      vm.selection.restore = true
      vm.startBrowsing(src.substring(0, src.lastIndexOf('/')))
    },

    setViewport(viewport) {
      set($perAdminApp.getView(), '/state/tools/workspace/view', viewport)
    },

    togglePreview() {
      const view = $perAdminApp.getView()
      const current = get(view, '/state/tools/workspace/preview', null)
      $perAdminApp.stateAction('editPreview', current ? null : 'preview')
    },

    previewInNewTab() {
      const view = $perAdminApp.getView()
      const page = get(view, '/pageView/path', null)
      window.open(page + '.html', 'viewer')
    },

    itemIsTag(tagName) {
      const selection = this.getSelection(0)
      if (selection) {
        const toEl = node => node && (node.nodeType === Node.TEXT_NODE ? node.parentElement : node)
        const start = toEl(selection.startContainer)
        const end = toEl(selection.endContainer)
        return !!(
          (start && start.closest(tagName)) ||
          (end && end.closest(tagName))
        )
      }
      return false
    },

    startBrowsing(path) {
      const browsePath = path || this.browser.path.current
      $perAdminApp.getApi()
        .populateNodesForBrowser(browsePath, 'pathBrowser')
        .then(() => this.browser.open = true)
        .catch(() => {
          $perAdminApp.getApi().populateNodesForBrowser('/content', 'pathBrowser')
        })
    },

    saveSelection() {
      this.selection.buffer = saveSelection(this.getInlineContainer(), this.getInlineDoc())
      this.selection.doc = this.getInlineDoc()
      this.selection.container = this.getInlineContainer()
    },

    restoreSelection() {
      this.selection.doc.body.focus()
      this.selection.container.focus()
      this.$nextTick(() => {
        restoreSelection(this.selection.container, this.selection.buffer, this.selection.doc)
      })
    },

    onBrowserCancel() {
      this.browser.open = false
      this.browser.withImageTab = false
      if (this.selection.restore) {
        this.restoreSelection()
        this.selection.restore = false
      }
    },

    onBrowserSelect() {
      this.browser.open = false

      if (this.selection.restore) {
        this.restoreSelection()
      }

      this.$nextTick(() => {
        if (['editLink', 'insertLink'].includes(this.param.cmd)) {
          this.onLinkSelect()
          return
        } else if (['insertImage', 'editImage', 'editIcon'].includes(this.param.cmd)) {
          this.onImageSelect()
          return
        }

        this.execCmd(this.param.cmd, this.param.value)
        this.param.cmd = null
        this.param.value = null
        this.browser.path.selected = null
        this.browser.linkTitle = null
        this.browser.img.width = null
        this.browser.img.height = null
        this.pingRichToolbar()

        if (this.selection.restore) {
          this.$nextTick(() => {
            this.restoreSelection()
            this.selection.restore = false
          })
        }
      })
    },

    onLinkSelect() {
      const typeLC = this.browser.type?.toLowerCase()
      const isAssetOrFile = typeLC === PathBrowser.Type.ASSET || typeLC === 'file'
      let href = this.browser.path.selected || ''

      const isPageType = this.browser.type?.toLowerCase() === PathBrowser.Type.PAGE
      if (href.startsWith('/') && isPageType && !href.endsWith('.html')) {
        href += '.html'
      }

      const applyLinkAttributes = (link) => {
        link.setAttribute('href', href)
        if (this.browser.linkTitle) {
          link.setAttribute('title', this.browser.linkTitle)
        } else {
          link.removeAttribute('title')
        }
        if (isAssetOrFile) {
          link.setAttribute('download', '')
          link.removeAttribute('target')
          link.removeAttribute('rel')
        } else {
          link.setAttribute('target', this.browser.newWindow ? '_blank' : '_self')
          link.setAttribute('rel', this.browser.rel ? 'noopener noreferrer' : '')
        }
      }

      if (this.param.cmd === 'insertLink') {
        const link = this.selection.doc.createElement('a')
        applyLinkAttributes(link)

        this.restoreSelection()
        this.$nextTick(() => {
          // Use saved selection.doc — inline.doc may be null after pathbrowser interaction
          const selectionDoc = this.selection.doc
          const selectionWin = selectionDoc.defaultView
          const range = selectionWin && selectionWin.getSelection && selectionWin.getSelection().rangeCount > 0
            ? selectionWin.getSelection().getRangeAt(0)
            : this.getSelection(0)
          if (!range) return
          const textEditor = this.getEditorFrom(range).closest('.inline-edit[contenteditable="true"]')

          let rangeIsInListItem = false
          if (!range.startContainer.isEqualNode(range.endContainer)) {
            const listItems = Array.from(textEditor.querySelectorAll('li')).reverse()
            for (const li of listItems) {
              if (range.intersectsNode(li)) {
                rangeIsInListItem = li
                break
              }
            }
          }

          if (rangeIsInListItem) {
            range.setStart(rangeIsInListItem, 0)
            if (rangeIsInListItem.isEqualNode(range.endContainer)) {
              range.setEnd(range.endContainer, range.endOffset)
            } else {
              range.setEnd(rangeIsInListItem, rangeIsInListItem.childNodes.length)
            }
          }

          link.appendChild(range.extractContents())
          if (link.textContent.trim().length < 1) {
            link.textContent = href
          }
          range.insertNode(link)
          $perAdminApp.action(this, 'reWrapEditable')
          $perAdminApp.action(this, 'writeInlineToModel')
          this.$nextTick(() => {
            $perAdminApp.action(this, 'textEditorWriteToModel')
          })
        })
      } else {
        // editLink
        const anchor = this.getLastAnchor()
        if (!anchor) return
        applyLinkAttributes(anchor)
        $perAdminApp.action(this, 'textEditorWriteToModel')
      }
    },

    onImageSelect() {
      if (this.param.cmd === 'editIcon') {
        const imgEl = this.browser.element
        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        imgEl.setAttribute('alt', this.browser.altText || '')
        imgEl.setAttribute('style', styles.join(';'))
        const container = imgEl.closest('[data-per-inline]')
        if (container) $perAdminApp.action(this, 'writeElementToModel', container)
        this.browser.element = null
        this.browser.altText = undefined
        this.browser.withImageTab = false
        return
      }
      if (this.param.cmd === 'editImage') {
        const imgEl = this.browser.element
        const linkTitle = this.browser.linkTitle
        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        imgEl.setAttribute('src', this.browser.path.selected)
        imgEl.setAttribute('alt', linkTitle ? linkTitle : '')
        imgEl.setAttribute('title', linkTitle ? linkTitle : '')
        imgEl.setAttribute('style', styles.join(';'))
        $perAdminApp.action(this, 'reWrapEditable')
        $perAdminApp.action(this, 'writeInlineToModel')
        this.$nextTick(() => {
          $perAdminApp.action(this, 'textEditorWriteToModel')
        })
        this.browser.element = null
      } else {
        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        this.param.cmd = 'insertHTML'
        this.param.value = `<img src="${this.browser.path.selected}" alt="${this.browser.linkTitle}" title="${this.browser.linkTitle}" style="${styles.join(';')}"/>`
      }
    },

    setBrowserPathCurrent(path) {
      this.browser.path.current = path
    },

    setBrowserPathSelected(path) {
      this.browser.path.selected = path
    },

    setBrowserResourceType(type) {
      if (!type) {
        this.browser.type = PathBrowser.Type.FILE
      } else {
        this.browser.type = type.split(':')[1]
      }
    },

    toggleBrowserNewWindow() {
      this.browser.newWindow = !this.browser.newWindow
    },

    setBrowserLinkTitle(event) {
      this.browser.linkTitle = event.target.value
    },

    setBrowserAltText(event) {
      this.browser.altText = event.target.value
    },

    getSelection(index = null) {
      const document = this.getInlineDoc()
      if (!document || !document.defaultView) return false
      const window = document.defaultView
      let selection = window.getSelection()
      if (!selection || selection.rangeCount <= 0) return false
      if (index !== null && index >= 0) {
        selection = selection.getRangeAt(index)
      }
      return selection
    },

    updateDocElDimensions() {
      this.docEl.dimension.w = document.documentElement.clientWidth
    },

    groupAllowed(group) {
      return !group.rules || group.rules()
    },

    groupIsActive(group) {
      if (group.isActive) {
        return group.isActive()
      }
      return group.items.filter((item) => item.isActive && item.isActive()).length > 0
    },
  },
}
</script>
