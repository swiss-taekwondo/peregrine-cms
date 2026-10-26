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
import {get, set} from '../../../../../../js/utils'
import {IconLib, PathBrowser} from '../../../../../../js/constants'
import {fallbackMediaText, resolveMediaText} from '../../../js/mediaText'
import RichtoolbarFontSize from '../richtoolbarfontsize/template.vue'
import RichtoolbarGroup from '../richtoolbargroup/template.vue'
import Pathbrowser from '../pathbrowser/template.vue'
import {createSelectionMethods} from './selectionHandling.js'
import { createButtonCommandMethods } from './btnCommands.js'

function renderIcon(h, icon, lib) {
  if (!icon) return null
  if (lib === IconLib.FONT_AWESOME)
    return h('i', { class: `fa fa-${icon}` })
  if (lib === IconLib.PLAIN_TEXT)
    return h('span', { domProps: { innerHTML: icon } })
  return h('i', { class: 'material-icons', domProps: { textContent: icon } })
}

function resolveClass(cls) {
  if (typeof cls === 'function') return cls()
  return cls || ''
}

const isMacPlatform = (() => {
  if (typeof navigator === 'undefined') return false
  const platform = navigator.userAgentData?.platform || navigator.platform || ''
  return /Mac|iPhone|iPad|iPod/i.test(platform)
})()

function getBtnShortcut(btn) {
  if (!btn || typeof btn !== 'object') return ''
  if (btn.shortcut) return btn.shortcut

  const modifier = isMacPlatform ? 'Cmd' : 'Ctrl'
  const redoShortcut = isMacPlatform ? `${modifier}+Shift+Z` : `${modifier}+Y`
  const formatValue = (btn.value || '').toLowerCase()

  switch (btn.cmd) {
    case 'bold':
      return `${modifier}+B`
    case 'italic':
      return `${modifier}+I`
    case 'underline':
      return `${modifier}+U`
    case 'undo':
      return `${modifier}+Z`
    case 'redo':
      return redoShortcut
    case 'formatBlock':
      if (formatValue === 'p') return `${modifier}+Alt+0`
      if (/^h[1-6]$/.test(formatValue)) return `${modifier}+Alt+${formatValue.slice(1)}`
      return ''
    default:
      return ''
  }
}

function getBtnMenuLabel(btn, vm) {
  const source = btn.menuLabel != null ? btn.menuLabel : (btn.title != null ? btn.title : btn.label)
  return vm.$i18n(source)
}

function getBtnTitle(btn, vm) {
  const baseTitle = vm.$i18n(btn.tooltip != null ? btn.tooltip : (btn.title != null ? btn.title : btn.label))
  const shortcut = getBtnShortcut(btn)
  return shortcut ? `${baseTitle} (${shortcut})` : baseTitle
}


function renderBtn(h, btn, vm, keyPrefix, index, inDropdown = false, closeDropdown = null) {
  const isActive = btn.isActive ? btn.isActive() : false
  const isDisabled = btn.isDisabled ? btn.isDisabled() : false
  const extraClass = resolveClass(btn.class)
  const uniqueKey = keyPrefix != null
    ? (index != null ? `${keyPrefix}-${index}` : `${keyPrefix}-${btn.label}`)
    : btn.label
  const classes = inDropdown
    ? ['rtb-menu-item', { active: isActive }, extraClass]
    : ['rtb-btn', 'btn', { active: isActive }, extraClass]
  const label = getBtnMenuLabel(btn, vm)
  const title = getBtnTitle(btn, vm)
  const children = [renderIcon(h, btn.icon, btn.iconLib || IconLib.FONT_AWESOME)]
  if (inDropdown) {
    children.push(h('span', { class: 'rtb-menu-item-label' }, [label]))
  }
  return h('button', {
    key: uniqueKey,
    class: classes,
    attrs: { title: title, type: 'button' },
    domProps: { disabled: isDisabled },
      on: {
        mousedown: (e) => {
          e.preventDefault()
          if (inDropdown) e.stopPropagation()
        },
        click: (e) => {
          if (inDropdown) e.stopPropagation()
          if (isDisabled) return
          if (closeDropdown) closeDropdown()
          // All buttons just dispatch a custom event to command the sidebar toolbar to execute the command. To avoid any behaviour differences between subnav / sidebar toolbars.
          // Custom click handlers should follow this as well!
          if (btn.click) btn.click(); else window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: btn.cmd } }))
        },
      },
    }, children)
}

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
      openGroups: {},
      selection: {
        restore: false,
        buffer: null,
        doc: null,
        container: null,
        content: null,
      },
      activeAnchor: null,
      param: {
        cmd: null,
        value: null,
        anchor: null,
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
        mediaSourcePath: '',
        mediaSourceText: '',
        mediaSourceFallback: '',
        path: {
          current: '',
          selected: null,
        },
        rel: true,
        img: {
          width: null,
          height: null,
          objectFit: null,
        },
      },
      docEl: {
        dimension: {
          w: 0,
          scrollWidth: 0,
        },
      },
      windowWidth: window.innerWidth,
      pageTreeWidth: 0,
      alwaysActiveWidth: 0,
      fontSizeWidth: 140,
      size: {
        button: 34,
        group: 4,
      },
      hiddenGroups: {},
      responsiveMenuGroupKey: null,
      responsiveMenuFilter: '',

      historyStack: [],
      historyIndex: -1,
      isUndoing: false,

      sharedHistoryIndex: -1,
      sharedHistoryLength: 0,

      layoutPing: 0,
      lastInRteSelection: null,
      lastSelectedRte: null,
      currentFontSize: 14,
      lastRteSelectionInlineOnly: null,
      toolbarDisabled: true,
    }
  },

  computed: {
    alwaysActiveGroup() {
      return alwaysActiveGroup(this)
    },
    groups() {
      this.inlinePing
      this.layoutPing
      return [
        textFormatGroup(this),
        actionsGroup(this),
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
    computedHiddenGroups() {
      return this.getResponsiveLayout().hiddenMap
    },
    filteredGroups() {
      const {visible} = this.getResponsiveLayout()
      void this.responsiveMenuGroup
      return visible.filter((group) => !group.isFontSizeControl && this.groupAllowed(group))
    },
    responsiveMenuGroup() {
      void this.docEl.dimension.w
      void this.docEl.dimension.scrollWidth
      void this.windowWidth
      void this.pageTreeWidth
      void this.alwaysActiveWidth
      void this.computedHiddenGroups
      const groups = this.groups
      return responsiveMenuGroup(this, groups)
    },
    fontSizeHidden() {
      return !!this.getResponsiveLayout().hiddenMap['font-size']
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
    
    
  },

  created() {
    this.buttonCommands = createButtonCommandMethods(this)
  },

  mounted() {
    this.$nextTick(() => {
      window.addEventListener('resize', this.scheduleDocElDimensionsUpdate)
      this.initResizeObserver()
      this.scheduleDocElDimensionsUpdate()
    })
    // Only sidebar toolbar executes commands!
    if (!this.onSubNav) {
      window.addEventListener('richtoolbar:cmd', this.rteCommandHandler)
      // const interval = setInterval(() => {
      //   console.log('interval range:', this.lastInRteSelection?.commonAncestorContainer)
      // }, 100)
      // window.addEventListener('keyup', (e) => {
      //   if (e.key === 's') {
      //   clearInterval(interval)
      //   }
      // })
    }

    document.addEventListener('selectionchange', this.handleSelectionChange)
    const editIframe = document.querySelector('#editview');
    const attachListener = () => {
      const doc = editIframe.contentDocument;
      if (doc?.readyState === 'complete') {
        doc.addEventListener('selectionchange', this.handleSelectionChange);
        console.log('editIframe', editIframe)
      }
    };
    editIframe.addEventListener('load', attachListener);
    attachListener();

    document.addEventListener('mousedown', this._closeDropdowns)
  },

  destroyed() {
    document.removeEventListener('selectionchange', this.handleSelectionChange)
    document.querySelector('#editview').contentDocument.removeEventListener('selectionchange', this.handleSelectionChange)
  },

  watch: {
	  toolbarDisabled(newVal) {
	    console.log('toolbarDisabled:', newVal)
	  },
    currentFontSize(newVal) {
      if (this.onSubNav) console.log('fontsize:', newVal)
    },
    lastInRteSelection(newVal) {
      console.log('selection:', newVal)
    },
    lastRteSelectionInlineOnly(newVal) {
    console.log('inline sel:', newVal)
    },
  },

  beforeDestroy() {
    window.removeEventListener('resize', this.scheduleDocElDimensionsUpdate)
    if (this._resizeObserver) {
      this._resizeObserver.disconnect()
      this._resizeObserver = null
    }
    if (this._resizeFrame) {
      cancelAnimationFrame(this._resizeFrame)
      this._resizeFrame = null
    }
    if (this._resizeTimeout) {
      clearTimeout(this._resizeTimeout)
      this._resizeTimeout = null
    }
    window.removeEventListener('richtoolbar:cmd', this.rteCommandHandler)
    if (this._inlineClickStickyTimer) {
      clearTimeout(this._inlineClickStickyTimer)
      this._inlineClickStickyTimer = null
    }
    document.removeEventListener('mousedown', this._closeDropdowns)
  },

  render(h) {
    const disabled = !this.inlineRich || this.preview === 'preview'
    const children = []

    if (this.groupAllowed(this.alwaysActiveGroup)) {
      children.push(this._renderGroup(h, this.alwaysActiveGroup, true))
    }

    for (const group of this.filteredGroups) {
      children.push(this._renderGroup(h, group, false))
    }

    if (!this.fontSizeHidden) {
      children.push(this.renderFontSize(h, 'font-size'))
    }

    if (this.groupAllowed(this.responsiveMenuGroup)) {
      children.push(this._renderGroup(h, this.responsiveMenuGroup, true))
    }

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
          'update-img-objectFit': v => { this.browser.img.objectFit = v },
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
    ...createSelectionMethods(),

    initResizeObserver() {
      if (typeof ResizeObserver === 'undefined' || !this.$el) return

      const subNavEl = this.$el.closest('.sub-nav')
      const pageTreeEl = subNavEl ? subNavEl.querySelector('.page-tree') : null
      const alwaysActiveEl = this.$el.querySelector('.group-always-active')

      this._resizeObserver = new ResizeObserver(() => {
        this.scheduleDocElDimensionsUpdate()
      })

      ;[this.$el, subNavEl, pageTreeEl, alwaysActiveEl]
        .filter(Boolean)
        .forEach((element) => this._resizeObserver.observe(element))
    },

    scheduleDocElDimensionsUpdate() {
      const nextWidth = this.$el ? this.$el.clientWidth : document.documentElement.clientWidth
      const delayMs = nextWidth > this.docEl.dimension.w ? 1000 : 0

      if (this._resizeFrame) {
        cancelAnimationFrame(this._resizeFrame)
        this._resizeFrame = null
      }
      if (this._resizeTimeout) {
        clearTimeout(this._resizeTimeout)
        this._resizeTimeout = null
      }

      const runUpdate = () => {
        this._resizeFrame = requestAnimationFrame(() => {
          this._resizeFrame = null
          this.updateDocElDimensions()
        })
      }

      if (delayMs > 0) {
        this._resizeTimeout = setTimeout(() => {
          this._resizeTimeout = null
          runUpdate()
        }, delayMs)
        return
      }

      runUpdate()
    },

    getGroupKey(group) {
      if (!group) return ''
      if (group.hiddenKey) return group.hiddenKey
      return typeof group.label === 'function' ? group.label() : group.label
    },

    getGroupItems(group) {
      const items = typeof group.items === 'function' ? group.items() : group.items
      if (!Array.isArray(items)) return []

      return items.filter((item, index) => {
        if (!item || typeof item !== 'object') return false
        if (group.itemRules && group.itemRules[index] && !group.itemRules[index]()) return false
        return true
      })
    },

    groupHasResponsiveMenuEntry(group) {
      if (!group) return false
      if (group.isFontSizeControl) return true
      if (this.getGroupItems(group).length > 0) return true
      return typeof group.toggleClick === 'function' || typeof group.click === 'function'
    },

    getGroupOrder(group, fallback = 999) {
      if (!group) return fallback
      return group.order != null ? group.order : fallback
    },

    getGroupPriority(group, fallback = 999) {
      if (!group) return fallback
      return group.priority != null ? group.priority : fallback
    },

    shouldShowResponsiveMenuForHiddenGroups(hiddenGroups = []) {
      const responsiveHiddenGroups = hiddenGroups.filter((group) => {
        return this.groupHasResponsiveMenuEntry(group)
      })
      return responsiveHiddenGroups.length > 1 || responsiveHiddenGroups.some(group => group?.isFontSizeControl)
    },

    getFontSizeResponsiveGroup(width = null) {
      return {
        label: 'font-size',
        hiddenKey: 'font-size',
        isFontSizeControl: true,
        order: 120,
        priority: 20,
        width: width != null ? width : this.fontSizeWidth,
      }
    },

    itemHasChildren(item) {
      if (!item || typeof item !== 'object' || !item.items) return false
      const items = typeof item.items === 'function' ? item.items() : item.items
      return Array.isArray(items) && items.length > 0
    },

    isResponsiveMenuGroup(group) {
      return this.getGroupKey(group) === 'responsive-menu'
    },

    enterResponsiveMenuGroup(group) {
      this.responsiveMenuGroupKey = this.getGroupKey(group)
      this.responsiveMenuFilter = ''
      if (group.toggleClick) {
        group.toggleClick()
      }
    },

    exitResponsiveMenuGroup() {
      this.responsiveMenuGroupKey = null
      this.responsiveMenuFilter = ''
    },

    getResponsiveMenuActiveGroup(groups = this.groups) {
      if (!this.responsiveMenuGroupKey) return null
      const {hidden} = this.getResponsiveLayout(groups)
      const hiddenGroups = hidden.filter(group => this.groupHasResponsiveMenuEntry(group))
      return hiddenGroups.find(group => this.getGroupKey(group) === this.responsiveMenuGroupKey) || null
    },

    getResponsiveMenuFilteredItems(group) {
      const items = this.getGroupItems(group)
      if (!group || !group.searchable || !this.responsiveMenuFilter) {
        return items
      }
      const filter = this.responsiveMenuFilter.toLowerCase()
      return items.filter((item) => {
        const name = (item.name || item.label || '').toLowerCase()
        return name.indexOf(filter) > -1
      })
    },

    getResponsiveMenuItems(groups = this.groups) {
      const {hidden} = this.getResponsiveLayout(groups)
      const hiddenGroups = hidden
        .filter(group => this.groupHasResponsiveMenuEntry(group))
        .sort((a, b) => this.getGroupOrder(a) - this.getGroupOrder(b))

      if (!this.responsiveMenuGroupKey) {
        return hiddenGroups.map(group => this.getResponsiveMenuRootItem(group))
      }

      const activeGroup = hiddenGroups.find(group => this.getGroupKey(group) === this.responsiveMenuGroupKey)
      if (!activeGroup) {
        this.responsiveMenuGroupKey = null
        return hiddenGroups.map(group => this.getResponsiveMenuRootItem(group))
      }

      return [
        {
          label: 'Back',
          title: 'Back',
          icon: 'arrow_back',
          iconLib: IconLib.MATERIAL_ICONS,
          click: () => this.exitResponsiveMenuGroup(),
        },
        activeGroup,
      ]
    },

    getResponsiveMenuRootItem(group) {
      if (group.isFontSizeControl) {
        return group
      }
      const items = this.getGroupItems(group)
      if (items.length === 0 && group.toggleClick) {
        const label = typeof group.label === 'function' ? group.label() : group.label
        const icon = typeof group.icon === 'function' ? group.icon() : group.icon
        return {
          label,
          title: label,
          icon,
          iconLib: group.iconLib || IconLib.FONT_AWESOME,
          isDisabled: group.isDisabled,
          click: () => group.toggleClick(),
        }
      }
      if (items.length === 1 && !group.searchable) {
        const item = items[0]
        return {
          label: typeof group.label === 'function' ? group.label() : group.label,
          title: typeof group.label === 'function' ? group.label() : group.label,
          icon: typeof group.icon === 'function' ? group.icon() : group.icon,
          iconLib: group.iconLib || item.iconLib || IconLib.FONT_AWESOME,
          isDisabled: item.isDisabled,
          click: () => {
            if (item.click) item.click()
            else this.exec(item.cmd)
          },
        }
      }
      return group
    },

    getMeasuredWidth(selector, fallback = 0) {
      const element = this.$el ? this.$el.querySelector(selector) : null
      if (element) {
        return Math.ceil(element.offsetWidth)
      }
      return fallback
    },

    getOuterWidth(element) {
      if (!element) return 0
      const style = window.getComputedStyle(element)
      const marginLeft = parseFloat(style.marginLeft) || 0
      const marginRight = parseFloat(style.marginRight) || 0
      return Math.ceil(element.offsetWidth + marginLeft + marginRight)
    },

    getGroupWidth(group) {
      if (group?.isFontSizeControl) {
        return group.width || this.fontSizeWidth || 140
      }
      const items = this.getGroupItems(group)
      if (items.length === 0) return 0

      const buttonWidth = this.size.button || 34
      const groupWidth = this.size.group || 4
      const interGroupPadding = 4
      return group.collapse
        ? buttonWidth + groupWidth + interGroupPadding
        : items.length * buttonWidth + groupWidth + interGroupPadding
    },

    getResponsiveLayout(groups = this.groups) {
      const hiddenMap = {}
      groups.forEach((group) => {
        hiddenMap[this.getGroupKey(group)] = false
      })

      if (!this.responsive) {
        return {visible: groups, hidden: [], hiddenMap}
      }

      const subNavEl = this.$el ? this.$el.closest('.sub-nav') : null
      const subNavW = subNavEl ? subNavEl.clientWidth : this.docEl.dimension.w
      if (subNavW === 0) {
        return {visible: groups, hidden: [], hiddenMap}
      }

      const fontSizeW = this.fontSizeWidth || this.getMeasuredWidth('.font-size-wrapper', 140)
      const alwaysActiveW = this.alwaysActiveWidth || this.getMeasuredWidth('.group-always-active', 0)
      const responsiveMenuW = this.getMeasuredWidth('.group-responsive-menu', this.size.button + this.size.group)
      const pageTreeW = this.pageTreeWidth
      const toolbarMarginLeft = this.$el ? (parseFloat(window.getComputedStyle(this.$el).marginLeft) || 0) : 0
      const orderedGroups = [...groups, this.getFontSizeResponsiveGroup(fontSizeW)]
        .sort((a, b) => this.getGroupOrder(a) - this.getGroupOrder(b))

      const fitGroups = (reserveResponsiveMenu) => {
        const reservedWidth = pageTreeW + alwaysActiveW + toolbarMarginLeft + (reserveResponsiveMenu ? responsiveMenuW : 0)
        const availableWidth = Math.max(0, subNavW - reservedWidth)
        const hiddenKeys = new Set()
        let usedWidth = orderedGroups.reduce((sum, group) => sum + this.getGroupWidth(group), 0)

        if (usedWidth > availableWidth) {
          const hideCandidates = [...orderedGroups].sort((a, b) => {
            const priorityDiff = this.getGroupPriority(b) - this.getGroupPriority(a)
            if (priorityDiff !== 0) return priorityDiff
            return this.getGroupOrder(b) - this.getGroupOrder(a)
          })

          hideCandidates.forEach((group) => {
            if (usedWidth <= availableWidth) return
            hiddenKeys.add(this.getGroupKey(group))
            usedWidth -= this.getGroupWidth(group)
          })
        }

        const visible = orderedGroups.filter(group => !hiddenKeys.has(this.getGroupKey(group)))
        const hidden = orderedGroups.filter(group => hiddenKeys.has(this.getGroupKey(group)))

        return {visible, hidden}
      }

      let reserveResponsiveMenu = false
      let layout = fitGroups(reserveResponsiveMenu)

      for (let i = 0; i < 4; i++) {
        const nextReserveResponsiveMenu = this.shouldShowResponsiveMenuForHiddenGroups(layout.hidden)
        if (nextReserveResponsiveMenu === reserveResponsiveMenu) break
        reserveResponsiveMenu = nextReserveResponsiveMenu
        layout = fitGroups(reserveResponsiveMenu)
      }

      layout.hidden.forEach((group) => {
        hiddenMap[this.getGroupKey(group)] = true
      })

      return {...layout, hiddenMap}
    },

    renderFontSize(h, key = 'font-size') {
      return h(RichtoolbarFontSize, {
        key,
        props: {
          exec: this.exec,
          lastInRteSelection: this.lastInRteSelection,
          currentFontSize: this.currentFontSize,
          disabled: !this.hasEditorSelection,
        },
      })
    },

    _renderGroup(h, group, alwaysActive) {
      if (group?.isFontSizeControl) {
        return h('div', {
          key: `group-${group.hiddenKey}`,
          class: ['rtb-menu-item', 'rtb-menu-item--font-size'],
          on: {
            mousedown: e => e.stopPropagation(),
            click: e => e.stopPropagation(),
          },
        }, [this.renderFontSize(h, 'font-size-responsive')])
      }

      const items = typeof group.items === 'function' ? group.items() : group.items
      if (!items || items.length === 0) return null

      const label = typeof group.label === 'function' ? group.label() : group.label
      const icon = typeof group.icon === 'function' ? group.icon() : group.icon
      const iconLib = group.iconLib || IconLib.FONT_AWESOME
      const groupClass = resolveClass(group.class)
      const groupIsDisabled = group.isDisabled ? group.isDisabled() : false

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
            disabled: groupIsDisabled,
          },
          on: {
            'toggle-click': () => { this.openGroups = {}; if (group.toggleClick) group.toggleClick() },
            click: ({ btn }) => { if (btn.click) btn.click(); else this.exec(btn.cmd) },
          },
        })
      }

      const itemRules = group.itemRules || null
      const realItems = items.filter((it, i) => {
        if (!it || typeof it !== 'object') return false
        if (itemRules && itemRules[i] && !itemRules[i]()) return false
        return true
      })
      const forceLinkDropdown = label === 'link' && !!this.getActiveAnchor(false)
      const dropdownItems = forceLinkDropdown ? items : realItems

      if (group.collapse) {
        const groupIsActive = this.groupIsActive(group)

        if (!forceLinkDropdown && dropdownItems.length <= 1) {
          const singleItem = dropdownItems[0]
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
            domProps: { disabled: groupIsDisabled },
            on: { mousedown: (e) => {
              e.preventDefault()
            }, click: () => { if (!groupIsDisabled) clickFn() } },
          }, [renderIcon(h, icon, iconLib)])])
        }

        const isOpen = !!this.openGroups[label]
        const activeResponsiveGroup = this.isResponsiveMenuGroup(group)
          ? this.getResponsiveMenuActiveGroup()
          : null

        const closeThisDropdown = () => this.$set(this.openGroups, label, false)
        let menuItems = []
        let menuChildren = []

        if (activeResponsiveGroup && activeResponsiveGroup.searchable) {
          const activeGroupKey = this.getGroupKey(activeResponsiveGroup)
          const backItem = h('button', {
            key: `${label}-back`,
            class: ['rtb-menu-item', 'rtb-responsive-back'],
            attrs: { type: 'button', title: this.$i18n('Back') },
            on: {
              mousedown: (e) => {
                e.preventDefault()
                e.stopPropagation()
                this.exitResponsiveMenuGroup()
              },
            },
          }, [
            h('i', { class: 'material-icons' }, ['arrow_back']),
            h('span', { class: 'rtb-menu-item-label' }, ['Back']),
          ])

          const filterInput = h('div', { class: 'filter-wrapper' }, [
            h('input', {
              class: 'filter',
              attrs: { type: 'text', placeholder: 'filter...' },
              domProps: { value: this.responsiveMenuFilter },
              on: {
                click: e => e.stopPropagation(),
                input: e => { this.responsiveMenuFilter = e.target.value },
              },
            }),
          ])

          menuItems = this.getResponsiveMenuFilteredItems(activeResponsiveGroup).map((btn, i) => {
            const itemLabel = btn.label || btn.title || ''
            return h('li', {
              key: `${label}-searchable-${i}`,
              class: ['item', { disabled: btn.isDisabled ? btn.isDisabled() : false }, btn.class ? btn.class() : null],
              attrs: { title: btn.title ? this.$i18n(btn.title) : false },
              style: {
                justifyContent: 'center',
              },
              on: {
                mousedown: (e) => {
                  e.preventDefault()
                  e.stopPropagation()
                  if (btn.isDisabled && btn.isDisabled()) return
                  if (this.selection.doc && this.selection.doc.defaultView && this.selection.doc.defaultView.focus) {
                    this.selection.doc.defaultView.focus()
                  }
                  this.restoreSelection()
                  requestAnimationFrame(() => {
                    if (btn.click) btn.click()
                    else this.exec(btn.cmd)
                    this.pingRichToolbar()
                  })
                },
              },
            }, [
              btn.icon ? h('admin-components-icon', {
                props: { icon: btn.icon, lib: btn.iconLib || IconLib.FONT_AWESOME },
              }) : null,
              h('span', {
                class: ['label'],
                style: {
                  width: 'auto',
                  minWidth: '0',
                  marginLeft: '0',
                  marginRight: '0',
                  justifyContent: 'center',
                  textAlign: 'center',
                },
                domProps: { innerHTML: btn.iconLib === IconLib.PLAIN_TEXT ? itemLabel : undefined, textContent: btn.iconLib === IconLib.PLAIN_TEXT ? undefined : itemLabel },
              }),
              btn.icon ? h('div', { class: 'center-keeper' }) : null,
            ].filter(Boolean))
          })
          menuChildren = [
            h('div', {
              class: ['btn-group', `group-${activeGroupKey}`],
            }, [
              h('div', { class: 'materialize-drop-down' }, [
                h('div', {
                  class: ['dropdown-content', 'active', 'rtb-responsive-searchable-panel'],
                  style: {
                    display: 'block',
                    opacity: '1',
                    position: 'static',
                    top: 'auto',
                    left: 'auto',
                    transform: 'none',
                    overflowX: 'hidden',
                    overflowY: 'hidden',
                    minWidth: '215px',
                    width: '215px',
                  },
                  on: {
                    mousedown: e => e.stopPropagation(),
                    click: e => e.stopPropagation(),
                  },
                }, [
                  backItem,
                  h('div', {
                    class: 'rtb-responsive-searchable-body',
                  }, [
                    filterInput,
                    h('ul', {
                      class: 'items-list',
                      style: {
                        overflowX: 'hidden',
                      },
                    }, menuItems),
                  ]),
                ]),
              ]),
            ]),
          ]
        } else if (activeResponsiveGroup) {
          const backBtn = renderBtn(h, {
            label: 'Back',
            title: 'Back',
            icon: 'arrow_back',
            iconLib: IconLib.MATERIAL_ICONS,
            click: () => this.exitResponsiveMenuGroup(),
          }, this, label, 'back', true, null)

          menuItems = this.getGroupItems(activeResponsiveGroup).map((btn, i) => {
            if (this.itemHasChildren(btn)) {
              return this._renderGroup(h, btn, false)
            }
            return renderBtn(h, btn, this, label, i, true, closeThisDropdown)
          })

          menuChildren = [
            h('div', { class: 'rtb-dropdown-items-list' }, [backBtn, ...menuItems]),
          ]
        } else {
          menuItems = dropdownItems.map((btn, i) => {
            if (this.isResponsiveMenuGroup(group) && btn?.isFontSizeControl) {
              return this._renderGroup(h, btn, false)
            }
            if (this.isResponsiveMenuGroup(group) && !this.itemHasChildren(btn)) {
              return renderBtn(h, btn, this, label, i, true, closeThisDropdown)
            }
            if (this.isResponsiveMenuGroup(group) && this.itemHasChildren(btn)) {
              return renderBtn(h, {
                label: typeof btn.label === 'function' ? btn.label() : btn.label,
                title: typeof btn.label === 'function' ? btn.label() : btn.label,
                icon: typeof btn.icon === 'function' ? btn.icon() : btn.icon,
                iconLib: btn.iconLib,
                click: () => this.enterResponsiveMenuGroup(btn),
              }, this, label, i, true, null)
            }
            if (this.itemHasChildren(btn)) {
              return this._renderGroup(h, btn, false)
            }
            return renderBtn(h, btn, this, label, i, true, closeThisDropdown)
          })
          menuChildren = [h('div', { class: 'rtb-dropdown-items-list' }, menuItems)]
        }

        const menu = h('div', {
          class: ['rtb-dropdown-menu', {
            'rtb-dropdown-menu--open': isOpen,
            'rtb-dropdown-menu--centered': this.isResponsiveMenuGroup(group) || !!activeResponsiveGroup,
          }],
          style: { backgroundColor: '#fff' },
        }, menuChildren)

        const toggleBtnChildren = [renderIcon(h, icon, iconLib)]
        if (group.showLabel) {
          toggleBtnChildren.push(h('span', { class: 'rtb-dropdown-label' }, [label]))
        }
        toggleBtnChildren.push(h('span', { class: 'caret-down' }))

        const toggleBtn = h('button', {
          class: ['rtb-btn', 'btn', 'rtb-dropdown-toggle', { active: groupIsActive && !isOpen }, groupClass],
          attrs: { title: this.$i18n(label), type: 'button' },
          domProps: { disabled: groupIsDisabled },
          on: {
            mousedown: e => e.preventDefault(),
            click: () => { if (!groupIsDisabled) this._toggleGroup(label) },
          },
        }, toggleBtnChildren)

        return h('div', {
          key: `group-${label}`,
          class: ['btn-group', 'rtb-group', 'rtb-group--dropdown', `group-${label}`, { 'rtb-group--open': isOpen }],
        }, [toggleBtn, menu])
      }

      const btnNodes = realItems.map((btn, i) => {
        if (this.itemHasChildren(btn)) {
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
      const clickedOutsideToolbar = !this.$el || !this.$el.contains(e.target)
      if (clickedOutsideToolbar) {
        this.openGroups = {}
        this.exitResponsiveMenuGroup()
      }
    },

    _toggleGroup(label) {
      const opening = !this.openGroups[label]
      this.openGroups = {}
      if (label === 'responsive-menu') {
        this.exitResponsiveMenuGroup()
      }
      if (opening) {
        this.$set(this.openGroups, label, true)
        this.$nextTick(() => {
          const menu = this.$el.querySelector('.rtb-dropdown-menu--open')
          const dropdown = menu?.querySelector('.rtb-dropdown-items-list')
          if (dropdown && !menu.classList.contains('rtb-dropdown-menu--centered')) {
            const rect = dropdown.getBoundingClientRect()
            if (label === 'responsive-menu' || rect.right > window.innerWidth) {
              menu.classList.add('rtb-dropdown-menu--align-right')
            } else {
              menu.classList.remove('rtb-dropdown-menu--align-right')
            }
          }
        })
      }
    },

    rteCommandHandler(event) {
      console.log('command:', event.detail.cmd, event.detail.value, event)
      if (this.onSubNav) return
      if (typeof this[event.detail.cmd] === 'function') {
        this[event.detail.cmd](event.detail.value)
      } else if (this.buttonCommands?.[event.detail.cmd]) {
        this.buttonCommands[event.detail.cmd](event.detail.value)
			} else {
				const selectionOwnerDoc = this.lastInRteSelection.commonAncestorContainer.ownerDocument
				selectionOwnerDoc.execCommand(event.detail.cmd, event.detail.showUi || null, event.detail.value || null)
			}
    },

    triggerInput() {
      this.lastSelectedRte.dispatchEvent(new Event('input'))
    },

    saveToModel(container) {
      const content = container.innerHTML

      const dataInline = container.getAttribute('data-per-inline')

      if (dataInline) {
        const view = $perAdminApp.getView()
        if (!view || !view.pageView || !view.pageView.page) return false

        const editorPath = $perAdminApp.getNodeFromView('/state/editor/path')
        const pageNode = view.pageView.page

        let parentProp
        if (editorPath) {
          parentProp = $perAdminApp.findNodeFromPath(pageNode, editorPath)
        }

        if (!parentProp) {
          const pathParts = dataInline.split('.').slice(1)
          pathParts.reverse()
          parentProp = pageNode
          while (pathParts.length > 1) {
            parentProp = parentProp[pathParts.pop()]
          }
        }

        const keyStr = dataInline.split('.').pop()
        if (parentProp && keyStr) {
          parentProp[keyStr] = content
          return true
        }
      }

      const editorModel = $perAdminApp.getNodeFromViewOrNull('/state/inline/editorModel')
      if (editorModel) {
        editorModel.text = content
        return true
      }

      return false
    },

    writeEditorToModel(textEditor) {
      const wrapper = textEditor?.closest?.('.text-editor-wrapper')
      let comp = textEditor?.__vue__ || wrapper?.__vue__ || this.$parent

      while (comp) {
        if (typeof comp.textEditorWriteToModel === 'function') {
          comp.textEditorWriteToModel(comp)
          return true
        }
        comp = comp.$parent
      }

      if (textEditor && this.saveToModel(textEditor)) return true

      $perAdminApp.action(this, 'textEditorWriteToModel')
      return false
    },


    getDefaultFontSize() {
      const range = this.getEditorSelection()
      if (range) {
        const targetNode = typeof range.startContainer.closest === 'function'
          ? range.startContainer
          : range.startContainer.parentElement
        if (targetNode?.ownerDocument?.defaultView) {
          return targetNode.ownerDocument.defaultView.getComputedStyle(targetNode).fontSize
        }
      }

      const activeContext = this.getActiveEditorContext()
      const editor = activeContext?.container || this.getInlineContainer()
      const editorDoc = editor?.ownerDocument || activeContext?.doc || document
      const editorWindow = editorDoc?.defaultView || window
      const fallbackTarget = editor?.firstElementChild || editor
      if (!fallbackTarget || !editorWindow?.getComputedStyle) return 16
      return editorWindow.getComputedStyle(fallbackTarget).fontSize
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
      const getSelectionFromDoc = (doc, container = null) => {
        const selection = doc?.defaultView?.getSelection?.()
        if (selection?.rangeCount > 0) {
          const range = selection.getRangeAt(0)
          if (this.isRangeInEditor(range, container)) return returnRange ? range : selection
        }
        return null
      }

      const selectedContainer = this.selection.container?.classList?.contains('inline-editing')
        ? this.selection.container
        : null
      const selectedContainerSelection = selectedContainer
        ? getSelectionFromDoc(selectedContainer.ownerDocument, selectedContainer)
        : null
      if (selectedContainerSelection) return selectedContainerSelection

      const iframeDoc = document.querySelector('iframe#editview')?.contentDocument
      const iframeContainer = iframeDoc?.querySelector?.('.inline-edit.inline-editing')
      const iframeSelection = iframeContainer ? getSelectionFromDoc(iframeDoc, iframeContainer) : null
      if (iframeSelection) return iframeSelection

      const sidebarContainer = document.querySelector('.text-editor.inline-edit.inline-editing')
      const sidebarSelection = sidebarContainer ? getSelectionFromDoc(document, sidebarContainer) : null
      if (sidebarSelection) return sidebarSelection

      return null
    },

    getEditorFrom(range) {
      const getEditorFromEl = typeof range.startContainer.closest === 'function'
        ? range.startContainer
        : range.startContainer.parentElement
      return getEditorFromEl?.closest('.inline-edit[contenteditable="true"], .text-editor') || null
    },


    isRangeInEditor(range, editor = null) {
      if (!range) return false
      const textEditor = editor || this.getEditorFrom(range)
      if (!textEditor || textEditor.ownerDocument !== range.startContainer.ownerDocument) return false
      if (!textEditor.contains(range.startContainer) || !textEditor.contains(range.endContainer)) {
        return false
      }
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



    pingRichToolbar(vm = this) {
      vm.$emit('ping')
      const view = $perAdminApp.getView()
      if (view) {
        const state = view.state || (view.state = {})
        const inline = state.inline || (state.inline = {})
        inline.ping = (inline.ping || 0) + 1

        const anchor = vm.getAnchorAtSelection ? vm.getAnchorAtSelection() : null
        if (anchor) {
          inline.lastAnchor = anchor
        }
      }
      if ($perAdminApp.getNodeFromViewOrNull('/state/contentview/editor/active')) {
        $perAdminApp.action(vm, 'reWrapEditable')
      }
    },

    getActiveEditorContext() {
      const selectionRange = this.getEditorSelection()
      if (selectionRange) {
        const editor = this.getEditorFrom(selectionRange)
        if (editor) {
          return {
            doc: editor.ownerDocument,
            container: editor,
          }
        }
      }
      return null
    },

    getInlineDoc() {
      const activeContext = this.getActiveEditorContext()
      if (activeContext?.doc) return activeContext.doc
      if (!this.inline) return null
      return this.inline.doc
    },

    getInlineContainer() {
      const activeContext = this.getActiveEditorContext()
      if (activeContext?.container) return activeContext.container
      const doc = this.getInlineDoc()
      if (doc) {
        const container = doc.querySelector('.inline-edit.inline-editing')
        if (container) return container
      }
      return this.getLastContainer()
    },

    execCmd(cmd, value = null, showUi = true, doc = null) {
      const targetDoc = doc || this.getInlineDoc()
      if (!targetDoc || !targetDoc.execCommand) return
      targetDoc.execCommand(cmd, showUi, value)
    },

    queryCmdState(cmd) {
      if (!this.getInlineDoc() || !this.getInlineDoc().queryCommandState) return
      return this.getInlineDoc().queryCommandState(cmd) || false
    },

    exec(cmd, value = null) {
	    console.log('exec:', cmd, value)
	    this.restoreEditorSelection()
      if (Object.keys(this.buttonCommands).indexOf(cmd) >= 0) {
        this.buttonCommands[cmd](value)
      } else {
        this.execCmd(cmd, value)
      }
      // this.pingRichToolbar()
      
    },

    link() {
      if (this.onSubNav) {
        window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'link' } }))
        return
      }
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

    getAnchorFromRange(range) {
      if (!range) return null
      const toEl = node => node && (node.nodeType === Node.TEXT_NODE ? node.parentElement : node)
      const start = toEl(range.startContainer)
      const end = toEl(range.endContainer)
      return (
        (start && start.closest && start.closest('a')) ||
        (end && end.closest && end.closest('a')) ||
        null
      )
    },

    getActiveAnchor(useStoredAnchor = true) {
      if (this.onSubNav) {
        if (this.activeAnchor && this.activeAnchor.parentNode) {
          return this.activeAnchor
        }
        if (useStoredAnchor && this.param.anchor && this.param.anchor.parentNode) {
          return this.param.anchor
        }
        return null
      }

      if (this.hasEditorSelection) {
        if (this.activeAnchor && this.activeAnchor.parentNode) {
          return this.activeAnchor
        }
        if (!useStoredAnchor || this.selection.container?.classList?.contains('inline-editing')) {
          return null
        }
      }

      const range = this.getEditorSelection()
      const anchor = this.getAnchorFromRange(range)
      if (anchor) {
        return anchor
      }

      if (!useStoredAnchor) return null

      if (this._savedRange) {
        const savedAnchor = this.getAnchorFromRange(this._savedRange)
        if (savedAnchor && savedAnchor.parentNode) {
          return savedAnchor
        }
      }

      const lastAnchor = this.getLastAnchor()
      if (lastAnchor && lastAnchor.parentNode) {
      }
      return lastAnchor && lastAnchor.parentNode ? lastAnchor : null
    },

    getAnchorAtSelection() {
      const activeAnchor = this.getActiveAnchor()
      if (activeAnchor) return activeAnchor

      const doc = this.getInlineDoc() || this.getLastDoc()
      if (!doc) {
        const lastDoc = this.getLastDoc()
        if (lastDoc && lastDoc.defaultView) {
          return this._findAnchorInDoc(lastDoc)
        }
        return this._findAnchorInDoc(document)
      }
      if (!doc.defaultView) return null
      return this._findAnchorInDoc(doc)
    },

    _findAnchorInDoc(doc) {
      if (!doc || !doc.defaultView) return null
      const selection = doc.defaultView.getSelection()
      if (!selection || selection.rangeCount <= 0) return null
      const node = selection.anchorNode
      if (!node) return null
      const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node
      return el ? el.closest('a') : null
    },


    editLink() {
      if (this.onSubNav) {
        window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'editLink' } }))
        return
      }
      let anchor = this.getActiveAnchor()
      
      if (anchor && (!this.getInlineDoc() || !this.getLastDoc())) {
        const anchorDoc = anchor.ownerDocument
        if (anchorDoc) {
          set($perAdminApp.getView(), '/state/inline/lastDoc', anchorDoc)
          set($perAdminApp.getView(), '/state/inline/lastContainer', anchor.closest('.inline-edit'))
        }
      }
      if (!anchor) {
        return
      }

      let doc = this.getInlineDoc() || this.getLastDoc()
      let container = this.getInlineContainer() || this.getLastContainer()

      if (!doc || !container) {
        doc = anchor.ownerDocument
        container = anchor.closest('.inline-edit')
      }

      if (!doc || !container) return

      const href = anchor.getAttribute('href') || ''
      const title = anchor.getAttribute('title') || ''
      const target = anchor.getAttribute('target') || ''
      const rel = anchor.getAttribute('rel') || ''

      this.selection.content = anchor.innerHTML
      this.selection.doc = doc
      this.selection.container = container
      this.selection.buffer = saveSelection(container, doc)
        || $perAdminApp.getNodeFromViewOrNull('/state/inline/lastSelectionBuffer')
      this.selection.restore = true

      const view = $perAdminApp.getView()
      if (view) {
        const state = view.state || (view.state = {})
        const inline = state.inline || (state.inline = {})
        inline.lastAnchor = anchor
        inline.lastContainer = container
        inline.lastDoc = doc
      }

      this.param.cmd = 'editLink'
      this.param.anchor = anchor
      this.browser.header = this.$i18n('Edit Link')
      this.browser.withLinkTab = true
      this.browser.newWindow = target === '_blank'
      this.browser.rel = rel.includes('noopener')
      this.browser.linkTitle = title
      this.browser.type = PathBrowser.Type.PAGE

      let startPath
      let resolvedHref = href

      if (/^https?:\/\//.test(href)) {
        try {
          const url = new URL(href)
          if (url.hostname === window.location.hostname) {
            resolvedHref = url.pathname + url.search + url.hash
          }
        } catch (e) { /* Do nothing */ }
      }

      const isExternal = /^https?:\/\//.test(resolvedHref)
      if (isExternal) {
        this.browser.path.selected = resolvedHref
        startPath = this.roots.pages || '/'
      } else {
        // Normalize only the repository path; keep query strings and fragments verbatim.
        const suffixIndex = resolvedHref.search(/[?#]/)
        const pathname = suffixIndex < 0 ? resolvedHref : resolvedHref.slice(0, suffixIndex)
        const suffix = suffixIndex < 0 ? '' : resolvedHref.slice(suffixIndex)
        let hrefPath = pathname.replace(/\.html$/, '')
        if (!hrefPath.startsWith('/content/')) {
          hrefPath = (this.roots.pages || '') + hrefPath
        }
        const lastSlash = hrefPath.lastIndexOf('/')
        startPath = lastSlash > 0 ? hrefPath.substring(0, lastSlash) : (this.roots.pages || '/')
        this.browser.path.selected = hrefPath + suffix
      }
      this.browser.path.current = startPath
      this.startBrowsing(startPath)
    },

    removeLink() {
      if (this.onSubNav) {
        window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'removeLink' } }))
        return
      }
      let anchor = this.getActiveAnchor()

      if (!anchor) {
        const inlineDoc = this.getInlineDoc()
        if (inlineDoc && inlineDoc.defaultView) {
          const sel = inlineDoc.defaultView.getSelection()
          if (sel && sel.rangeCount > 0) {
            const node = sel.anchorNode
            if (node) {
              const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node
              anchor = el ? el.closest('a') : null
            }
          }
        }
      }

      if (!anchor || !anchor.parentNode) {
        const container = this.getLastContainer()
        if (container) {
          const allAnchors = container.getElementsByTagName('a')
          if (allAnchors.length > 0) {
            anchor = allAnchors[0]
          }
        }
      }

      if (!anchor || !anchor.parentNode) {
        return
      }

      let doc = this.getInlineDoc() || this.getLastDoc()
      let container = this.getInlineContainer() || this.getLastContainer()

      if (!doc || !container) {
        doc = anchor.ownerDocument
        container = anchor.closest('.inline-edit')
      }

      if (!doc || !container) {
        return
      }

      this.$nextTick(() => {
        const win = doc.defaultView || window
        const sel = win.getSelection ? win.getSelection() : null
        if (!sel) return

        const range = doc.createRange()
        range.selectNodeContents(anchor)
        sel.removeAllRanges()
        sel.addRange(range)
        doc.execCommand('unlink', false, null)

        const content = container.innerHTML
        const dataInline = container.getAttribute('data-per-inline')

        if (dataInline) {
          const view = $perAdminApp.getView()
          if (view && view.pageView && view.pageView.page) {
            const pathParts = dataInline.split('.').slice(1)
            pathParts.reverse()
            let parentProp = view.pageView.page
            while (pathParts.length > 1) {
              parentProp = parentProp[pathParts.pop()]
            }
            const keyStr = pathParts.pop()
            parentProp[keyStr] = content
          }
        }
      })
    },


    async getDefaultMediaText(path = '') {
      const rootData = this.$root && this.$root.$data
      const adminNodes = rootData && rootData.admin && rootData.admin.nodes
      return resolveMediaText(path, adminNodes)
    },


    insertIcon(imgPath) {
      const doc = this.selection.doc || this.getInlineDoc() || this.getLastDoc() || document
      const win = doc.defaultView || window
      const sel = win.getSelection ? win.getSelection() : window.getSelection()
      if (!sel || sel.rangeCount === 0) return

      const range = sel.getRangeAt(0)
      range.deleteContents()
      const fragment = range.createContextualFragment(`<img class="peregrine-icon" style="font-size: inherit; display: inline; width: auto; height: 1em; vertical-align: -0.125em;" src="${imgPath}"></img>`)
      const lastChild = fragment.lastChild;
      range.insertNode(fragment)
      range.setStartAfter(lastChild)
      range.collapse(true)
      this.getEditorFrom(range).dispatchEvent(new Event('input'))
    },


    setViewport(viewport) {
      set($perAdminApp.getView(), '/state/tools/workspace/view', viewport)
    },



    itemIsTag(tagName) {
      if (tagName.toUpperCase() === 'A') {
        return !!this.getActiveAnchor(false)
      }
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
      if (!this.selection.container || !this.selection.buffer || !this.selection.doc) return
      this.selection.container.focus()
      restoreSelection(this.selection.container, this.selection.buffer, this.selection.doc)
    },

    onBrowserCancel() {
      this.browser.open = false
      this.browser.withImageTab = false
      this.param.anchor = null
      this.browser.mediaSourcePath = ''
      this.browser.mediaSourceText = ''
      this.browser.mediaSourceFallback = ''
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
          this.onImageSelect().catch((err) => console.error(err))
          return
        }

        this.execCmd(this.param.cmd, this.param.value)
        this.param.cmd = null
        this.param.value = null
        this.param.anchor = null
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
      if (!href.trim()) {
        console.warn('no href specified in onLinkSelect')
        $perAdminApp.toast("Invalid link", "warn", 3000)
        return
      }
      try {
        new URL(href, window.location.origin)
      } catch (e) {
        console.warn('invalid href specified in onLinkSelect', href, e)
        $perAdminApp.toast("Invalid link", "warn", 3000)
        return
      }

      const isPageType = this.browser.type?.toLowerCase() === PathBrowser.Type.PAGE
      if (href.startsWith('/') && isPageType) {
        const url = new URL(href, "https://placeholder.invalid");
        if (!url.pathname.endsWith('.html')) {
          url.pathname += ".html";
        }
        href = url.pathname + url.search + url.hash;
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
          link.removeAttribute('download')
          link.setAttribute('target', this.browser.newWindow ? '_blank' : '_self')
          link.setAttribute('rel', this.browser.rel ? 'noopener noreferrer' : '')
        }
      }

      if (this.param.cmd === 'insertLink') {
        const link = this.selection.doc.createElement('a')
        applyLinkAttributes(link)

        const range = this._savedRange
        if (!range) return

        const editorEl = this.getEditorFrom(range)
        if (!editorEl) return
        const textEditor = editorEl.closest('.inline-edit[contenteditable="true"]')
        if (!textEditor) return

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

        link.append(range.extractContents())
        if (link.textContent.trim().length < 1 && link.children.length === 0) {
          link.textContent = href
        }
        range.insertNode(link)
        this._savedRange = null
        textEditor.dispatchEvent(new Event('input'))
      } else {
        let anchor = this.param.anchor
        if (!anchor || !anchor.parentNode) {
          anchor = this.getLastAnchor()
        }
        if (!anchor || !anchor.parentNode) {
          anchor = this.getAnchorAtSelection()
        }
        if (!anchor || !anchor.parentNode) {
          const container = this.getLastContainer()
          if (container) {
            anchor = container.querySelector('a')
          }
        }
        if (!anchor || !anchor.parentNode) {
          const container = this.getLastContainer()
          if (container) {
            const allAnchors = container.querySelectorAll('a')
            if (allAnchors.length > 0) {
              anchor = allAnchors[0]
            }
          }
        }
        if (!anchor || !anchor.parentNode) return
        applyLinkAttributes(anchor)
        const inlineContainer = anchor.closest('[data-per-inline]')
        if (inlineContainer) {
          if ($perAdminApp.getNodeFromViewOrNull('/state/contentview/editor/active')) {
            $perAdminApp.action(this, 'reWrapEditable')
          }
          $perAdminApp.action(this, 'writeElementToModel', inlineContainer)
        } else {
          $perAdminApp.action(this, 'textEditorWriteToModel')
        }
        const view = $perAdminApp.getView()
        if (view) {
          const state = view.state || (view.state = {})
          const inline = state.inline || (state.inline = {})
          inline.lastAnchor = anchor
        }
        this.param.anchor = null
      }
    },

    async onImageSelect() {
      if (this.param.cmd === 'editIcon') {
        const imgEl = this.browser.element
        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
        imgEl.setAttribute('alt', this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected) || '')
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
        const linkTitle = this.browser.linkTitle || this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected)
        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
        imgEl.setAttribute('src', this.browser.path.selected)
        imgEl.setAttribute('alt', linkTitle || '')
        imgEl.setAttribute('title', linkTitle || '')
        imgEl.setAttribute('style', styles.join(';'))
        if ($perAdminApp.getNodeFromViewOrNull('/state/contentview/editor/active')) {
          $perAdminApp.action(this, 'reWrapEditable')
        }
        const editor = imgEl.closest('[contenteditable="true"]')
        if (editor) editor.dispatchEvent(new Event('input'))
        this.browser.element = null
        this.browser.mediaSourcePath = ''
        this.browser.mediaSourceText = ''
        this.browser.mediaSourceFallback = ''
      } else {
        // insertImage
        const sel = window.getSelection()
        const range = this._savedRange

        const styles = []
        if (this.browser.img.width) styles.push(`width: ${this.browser.img.width}px`)
        if (this.browser.img.height) styles.push(`height: ${this.browser.img.height}px`)
        if (this.browser.img.objectFit) styles.push(`object-fit: ${this.browser.img.objectFit}`)
        const mediaText = this.browser.linkTitle || this.browser.altText || await this.getDefaultMediaText(this.browser.path.selected)

        const img = document.createElement('img')
        img.setAttribute('src', this.browser.path.selected)
        img.setAttribute('alt', mediaText || '')
        img.setAttribute('title', mediaText || '')
        if (styles.length) {
          img.setAttribute('style', styles.join(';'))
        }

        range.deleteContents()
        range.insertNode(img)
        range.setStartAfter(img)
        range.collapse(true)
        sel.removeAllRanges()
        sel.addRange(range)

        const editor = this.getEditorFrom(range)
        if (editor) editor.dispatchEvent(new Event('input'))

      }
      this.browser.mediaSourcePath = ''
      this.browser.mediaSourceText = ''
      this.browser.mediaSourceFallback = ''
    },

    setBrowserPathCurrent(path) {
      this.browser.path.current = path
    },

    setBrowserPathSelected(path) {
      this.browser.path.selected = path
      if (this.param.cmd === 'editImage' && path && path !== this.browser.mediaSourcePath) {
        const current = String(this.browser.linkTitle || '').trim()
        if (!current || current === this.browser.mediaSourceText || current === this.browser.mediaSourceFallback) {
          this.browser.linkTitle = ''
        }
      }
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
      const previousHiddenGroups = this.hiddenGroups || {}
      const previousWidth = this.docEl.dimension.w
      const previousScrollWidth = this.docEl.dimension.scrollWidth
      const previousWindowWidth = this.windowWidth
      const previousPageTreeWidth = this.pageTreeWidth
      const previousAlwaysActiveWidth = this.alwaysActiveWidth
      const previousFontSizeWidth = this.fontSizeWidth
      const newW = this.$el ? this.$el.clientWidth : document.documentElement.clientWidth
      const newScrollW = this.$el ? this.$el.scrollWidth : 0
      this.docEl.dimension.w = newW
      this.docEl.dimension.scrollWidth = newScrollW
      this.windowWidth = window.innerWidth

      const subNavEl = this.$el ? this.$el.closest('.sub-nav') : null
      const pageTreeEl = subNavEl ? subNavEl.querySelector('.page-tree') : null
      const newPageTreeWidth = this.getOuterWidth(pageTreeEl)
      const alwaysActiveEl = this.$el ? this.$el.querySelector('.group-always-active') : null
      const newAlwaysActiveWidth = this.getOuterWidth(alwaysActiveEl)
      const fontSizeEl = this.$el ? this.$el.querySelector('.font-size-wrapper') : null
      const newFontSizeWidth = this.getOuterWidth(fontSizeEl)
      this.pageTreeWidth = newPageTreeWidth
      this.alwaysActiveWidth = newAlwaysActiveWidth
      if (newFontSizeWidth > 0) {
        this.fontSizeWidth = newFontSizeWidth
      }
      const nextHiddenGroups = this.getResponsiveLayout().hiddenMap
      this.hiddenGroups = nextHiddenGroups

      const layoutChanged = previousWidth !== newW ||
        previousScrollWidth !== newScrollW ||
        previousWindowWidth !== this.windowWidth ||
        previousPageTreeWidth !== newPageTreeWidth ||
        previousAlwaysActiveWidth !== newAlwaysActiveWidth ||
        (newFontSizeWidth > 0 && previousFontSizeWidth !== newFontSizeWidth)

      if (layoutChanged) {
        this.openGroups = {}
        this.exitResponsiveMenuGroup()
      }

      const hiddenGroupsChanged = JSON.stringify(previousHiddenGroups) !== JSON.stringify(nextHiddenGroups)
      if (layoutChanged || hiddenGroupsChanged) {
        this.layoutPing += 1
        this.pingRichToolbar()
      }
      if (hiddenGroupsChanged) {
        this.$nextTick(() => {
          this.scheduleDocElDimensionsUpdate()
        })
      }
    },

    groupAllowed(group) {
      const groupKey = this.getGroupKey(group)
      if (groupKey === 'responsive-menu' || groupKey === 'always-active') {
        return !group.rules || group.rules()
      }
      const hidden = this.computedHiddenGroups[groupKey]
      return (!group.rules || group.rules()) && (!this.responsive || hidden === false)
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
