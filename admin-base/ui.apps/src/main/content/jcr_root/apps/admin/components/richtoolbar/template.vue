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
import { createPathbrowserCommandMethods } from "./pathbrowserCommands.js"
import { createResponsiveMethods } from './responsiveMethods.js'



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
    setEditorContent: {
      type: Function,
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

//       historyStack: [],
//       historyIndex: -1,
//       isUndoing: false,
// 
//       sharedHistoryIndex: -1,
//       sharedHistoryLength: 0,


      layoutPing: 0,
      // selection/range state
      lastInRteSelection: null,
      lastSelectedRte: null,
      currentFontSize: 14,
      lastRteSelectionInlineOnly: null,
      lastInRteSelectionRelativeToRte: null,
      toolbarDisabled: true,

      // History
      undoStack: [],
      undoStackLength: 0,
      redoStack: [],
      redoStackLength: 0,
      performingUndoRedo: false,
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
      this.$watch('editorContent', (newValue, oldValue) => {
        if (!this.performingUndoRedo) {
          this.undoStack.push(oldValue)
          this.redoStack = [];
        }
        this.updateUndoRedoStackLengths()
      })
    } else {
      window.addEventListener('richtoolbar:undoRedoStackLength', this.undoRedoStackLengthMessageHandler)
    }

    document.addEventListener('selectionchange', this.handleSelectionChange)
    const editIframe = document.querySelector('#editview');
    const attachListener = () => {
      const doc = editIframe.contentDocument;
      if (doc?.readyState === 'complete') {
        doc.addEventListener('selectionchange', this.handleSelectionChange);
        // console.log('editIframe', editIframe)
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
	    // console.log('toolbarDisabled:', newVal)
	  },
    currentFontSize(newVal) {
      // if (this.onSubNav) console.log('fontsize:', newVal)
    },
    lastInRteSelection(newVal) {
      // console.log('selection:', newVal)
    },
    lastRteSelectionInlineOnly(newVal) {
    // console.log('inline sel:', newVal)
    },
    undoStack(newVal) {
      // console.log('undoStack', newVal)
    },
    redoStack(newVal) {
      // console.log('redoStack', newVal)
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
    ...createButtonCommandMethods(),
    ...createPathbrowserCommandMethods(),
    ...createResponsiveMethods(),

    updateUndoRedoStackLengths() {
      this.undoStackLength = this.undoStack.length;
      this.redoStackLength = this.redoStack.length;
      if (!this.onSubNav) window.dispatchEvent(new CustomEvent('richtoolbar:undoRedoStackLength', { detail: {
        undoStackLength: this.undoStackLength,
        redoStackLength: this.redoStackLength,
      } }))
    },
    undoRedoStackLengthMessageHandler(event) {
      this.undoStackLength = event.detail.undoStackLength
      this.redoStackLength = event.detail.redoStackLength
    },


    rteCommandHandler(event) {
      console.log('command:', event.detail.cmd, event.detail.value, event)
      if (this.onSubNav) return
      if (typeof this[event.detail.cmd] === 'function') {
        this[event.detail.cmd](event.detail.value)
      } else if (this?.[event.detail.cmd]) {
        this[event.detail.cmd](event.detail.value)
			} else {
				const selectionOwnerDoc = this.lastInRteSelection.commonAncestorContainer.ownerDocument
				selectionOwnerDoc.execCommand(event.detail.cmd, event.detail.showUi || null, event.detail.value || null)
			}
    },

    triggerInput() {
      this.lastSelectedRte.dispatchEvent(new Event('input'))
      console.log('lastSelectedRte', this.lastSelectedRte)
    },






    pingRichToolbar(vm = this) {
      console.warn('still using pingRichToolbar', vm)
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


    execCmd(cmd, value = null, showUi = true, doc = null) {
      console.warn('still using execCmd')
      const targetDoc = doc || this.getInlineDoc()
      if (!targetDoc || !targetDoc.execCommand) return
      targetDoc.execCommand(cmd, showUi, value)
    },

    queryCmdState(cmd) {
      // TODO: maybe use rangeMatchesSelector instead to avoid depricated functionality
      return this.lastSelectedRte?.ownerDocument?.queryCommandState(cmd) || false
    },

    exec(cmd, value = null) {
			console.warn('exec sill used:', cmd, value)
			return
    },







    async getDefaultMediaText(path = '') {
      const rootData = this.$root && this.$root.$data
      const adminNodes = rootData && rootData.admin && rootData.admin.nodes
      return resolveMediaText(path, adminNodes)
    },

    setViewport(viewport) {
      set($perAdminApp.getView(), '/state/tools/workspace/view', viewport)
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
      console.warn('saveSelection still used')
      this.selection.buffer = saveSelection(this.getInlineContainer(), this.getInlineDoc())
      this.selection.doc = this.getInlineDoc()
      this.selection.container = this.getInlineContainer()
    },

    restoreSelection() {
    console.warn('restoreSelection still used')
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

  },
}
</script>
