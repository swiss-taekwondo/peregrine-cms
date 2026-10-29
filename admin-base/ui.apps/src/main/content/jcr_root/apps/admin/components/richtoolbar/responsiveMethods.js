import {IconLib, PathBrowser} from '../../../../../../js/constants.js'
import RichtoolbarFontSize from '../richtoolbarfontsize/template.vue'
import RichtoolbarGroup from '../richtoolbargroup/template.vue'


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



export function createResponsiveMethods() {
  return {
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
      const forceLinkDropdown = label === 'link' && !!this.rangeMatchesSelector('a')
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

  }
}
