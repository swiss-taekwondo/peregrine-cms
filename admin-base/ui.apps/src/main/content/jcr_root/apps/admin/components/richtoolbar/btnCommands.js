
export function createButtonCommandMethods(vm) {
  return {

  formatBlock(tagName) {
    const normalizedTagName = String(tagName || '').replace(/[<>]/g, '').toUpperCase()
    if (!/^(P|H[1-6])$/.test(normalizedTagName)) return false

    const savedRange = vm._savedRange
    let range = savedRange || vm.getEditorSelection()
    let textEditor = range ? vm.getEditorFrom(range) : null

    if (!range && vm.selection?.container && vm.selection?.buffer) {
      const doc = vm.selection.doc || vm.selection.container.ownerDocument
      restoreSelection(vm.selection.container, vm.selection.buffer, doc)
      range = vm.getEditorSelection()
      textEditor = range ? vm.getEditorFrom(range) : null
    }

    if (!range || !textEditor) return false
    if (!vm.isRangeInEditor(range, textEditor)) {
      if (vm.selection?.container && vm.selection?.buffer) {
        const doc = vm.selection.doc || vm.selection.container.ownerDocument
        restoreSelection(vm.selection.container, vm.selection.buffer, doc)
        range = vm.getEditorSelection()
        textEditor = range ? vm.getEditorFrom(range) : null
      }
      if (!range || !textEditor || !vm.isRangeInEditor(range, textEditor)) return false
    }
    if (!range.collapsed) {
      if (!wrapSelectedRangeInBlock(range, textEditor, normalizedTagName)) return false
      vm.writeEditorToModel(textEditor)
      textEditor.dispatchEvent(new Event('input'))
      vm.pingRichToolbar()
      return true
    }

    const blocks = getSelectedBlocksFromRange(range, textEditor)
    if (!blocks.length) return false
    if (blocks.every((block) => block.tagName === normalizedTagName)) return true

    for (const block of blocks) {
      const replacement = textEditor.ownerDocument.createElement(normalizedTagName)

      for (const attr of Array.from(block.attributes)) {
        replacement.setAttribute(attr.name, attr.value)
      }

      while (block.firstChild) {
        replacement.appendChild(block.firstChild)
      }
    
      block.replaceWith(replacement)
    }

    vm.writeEditorToModel(textEditor)
    textEditor.dispatchEvent(new Event('input'))
    vm.pingRichToolbar()
    return true
  },

  insertLink() {
    if (vm.onSubNav) {
      window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'insertLink' } }))
      return
    }
    const doc = vm.getInlineDoc() || vm.getLastDoc()
    const container = vm.getInlineContainer() || vm.getLastContainer()
    if (!doc || !container) {
      return
    }

    const startPath = vm.roots.pages
    vm.param.cmd = 'insertLink'
    vm.param.anchor = null
    vm.browser.header = vm.$i18n('Insert Link')
    vm.browser.path.current = startPath
    vm.browser.path.selected = null
    vm.browser.withLinkTab = true
    vm.browser.newWindow = false
    vm.browser.rel = true
    vm.browser.linkTitle = ''
    vm.browser.type = PathBrowser.Type.PAGE
    vm.selection.doc = doc
    vm.selection.container = container

    const win = doc.defaultView || window
    const liveSel = win.getSelection ? win.getSelection() : document.getSelection()
    const liveRange = liveSel && liveSel.rangeCount > 0 ? liveSel.getRangeAt(0).cloneRange() : null
    if (!liveRange) {
      return
    }
    vm._savedRange = liveRange

    const savedSel = saveSelection(container, doc)
      || $perAdminApp.getNodeFromViewOrNull('/state/inline/lastSelectionBuffer')
    vm.selection.buffer = savedSel
    vm.selection.restore = false
    vm.startBrowsing(startPath)
  },


  insertImage() {
    let container = null
    let doc = null

    const inlineContainer = vm.getInlineContainer()
    const inlineDoc = vm.getInlineDoc()
    if (inlineContainer && inlineDoc) {
      container = inlineContainer
      doc = inlineDoc
    }

    if (!container) {
      const lastContainer = vm.getLastContainer()
      const lastDoc = vm.getLastDoc()
      if (lastContainer && lastDoc) {
        container = lastContainer
        doc = lastDoc
      }
    }

    if (!container) {
      const wrapper = vm.$el?.closest('.text-editor-wrapper')
      if (wrapper) {
        container = wrapper.querySelector('.text-editor')
        if (!doc) doc = document
      }
    }

    if (!container) {
      container = document.querySelector('.text-editor')
      if (!doc) doc = document
    }

    if (!container) {
      return
    }

    if (!doc) doc = container.ownerDocument

    vm.param.cmd = 'insertImage'
    vm.browser.header = vm.$i18n('Insert Image')
    vm.browser.path.current = vm.roots.assets
    vm.browser.withLinkTab = true
    vm.browser.newWindow = undefined
    vm.browser.type = PathBrowser.Type.ASSET
    vm.browser.path.suffix = ''
    vm.browser.img.width = null
    vm.browser.img.height = null
    vm.browser.img.objectFit = null
    vm.browser.path.selected = '';
    vm.selection.doc = doc
    vm.selection.container = container

    const win = doc.defaultView || window
    const liveSel = win.getSelection ? win.getSelection() : document.getSelection()
    const liveRange = liveSel?.rangeCount > 0 ? liveSel.getRangeAt(0).cloneRange() : null
    vm._savedRange = liveRange

    const savedSel = saveSelection(container, doc) || $perAdminApp.getNodeFromViewOrNull('/state/inline/lastSelectionBuffer')
    vm.selection.buffer = savedSel
    vm.selection.restore = false
    vm.startBrowsing()
  },


  async editImage(target) {
    const title = target.getAttribute('title')
    const src = target.getAttribute('src')
    const defaultText = title || target.getAttribute('alt') || await vm.getDefaultMediaText(src)
    const fallbackText = fallbackMediaText(src)
    const srcArr = src.split('/')
    const img = {
      width: target.style.width ? parseInt(target.style.width) : null,
      height: target.style.height ? parseInt(target.style.height) : null,
      objectFit: target.style.objectFit || null,
    }
    vm.param.cmd = 'editImage'
    vm.selection.restore = false
    vm.browser.header = vm.$i18n('Edit Image')
    vm.browser.path.selected = srcArr.join('/')
    srcArr.pop()
    vm.browser.path.current = srcArr.join('/')
    vm.browser.withLinkTab = true
    vm.browser.newWindow = undefined
    vm.browser.type = PathBrowser.Type.ASSET
    vm.browser.linkTitle = defaultText
    vm.browser.mediaSourcePath = src
    vm.browser.mediaSourceText = defaultText || ''
    vm.browser.mediaSourceFallback = fallbackText
    vm.browser.element = target
    vm.browser.img.width = img.width
    vm.browser.img.height = img.height
    vm.browser.img.objectFit = img.objectFit
    vm.startBrowsing()
  },


  async editIcon(target) {
    const alt = target.getAttribute('alt') || await vm.getDefaultMediaText(target.getAttribute('src') || '')
    const src = target.getAttribute('src') || ''
    const img = {
      width: target.style.width ? parseInt(target.style.width) : 20,
      height: target.style.height ? parseInt(target.style.height) : 20,
      objectFit: target.style.objectFit || null,
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
    vm.browser.img.objectFit = img.objectFit
    vm.saveSelection()
    vm.selection.restore = true
    vm.startBrowsing(src.substring(0, src.lastIndexOf('/')))
  },


  // is this used?
  togglePreview() {
    const view = $perAdminApp.getView()
    const current = get(view, '/state/tools/workspace/preview', null)
    $perAdminApp.stateAction('editPreview', current ? null : 'preview')
  },

  // is this used?
  previewInNewTab() {
    const view = $perAdminApp.getView()
    const page = get(view, '/pageView/path', null)
    window.open(page + '.html', 'viewer')
  },


  updateFontSize(newSize) {
    const fontSize = `${newSize}px`
    const range = vm.lastInRteSelection;
    console.log('updatefontsize range:', range.commonAncestorContainer, range.startOffset, range.endOffset, range)
    const textEditor = vm.lastSelectedRte;

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
    console.log('noHighlight', noHighlight)
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
      vm.triggerInput()
      vm.$nextTick(() => {
	        vm.$nextTick(() => {
	           vm.reconstructSelectionRelativeToRte()
	        })
      })
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
      const isOnlyChildTextNodeOfExistingFontSizeSpan = false;
      if (range.startContainer.nodeType === Node.TEXT_NODE && !isOnlyChildTextNodeOfExistingFontSizeSpan) {
        const span = document.createElement('span')
        span.style.fontSize = fontSize
        range.surroundContents(span)
        vm.triggerInput()
        vm.selectNodes([span])
        vm.$nextTick(() => {
	        vm.$nextTick(() => {
	           vm.reconstructSelectionRelativeToRte()
	        })
        })
      } else {
        setFontSizeOfEl(range.startContainer, fontSize)
      }
      return
    }

    const nodeRanges = vm.wrapTextNodesInRange(range, fontSize)

    textEditor.querySelectorAll('span[style*="font-size"]:has(> span[style*="font-size"])').forEach(span => {
      if (span.childNodes.length === 1) {
        span.replaceWith(span.childNodes[0])
      }
    })

    vm.selectNodes(nodeRanges)
    vm.$nextTick(() => {
      vm.$nextTick(() => {
          vm.reconstructSelectionRelativeToRte()
      })
    })
  },

      undo() {
        if (vm.onSubNav) {
          window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'undo' } }))
          return
        }
        if (vm.historyIndex > 0) {
          vm.isUndoing = true
          vm.historyIndex--
          window.dispatchEvent(new CustomEvent('richtoolbar:history', {
            detail: { historyIndex: vm.historyIndex, historyLength: vm.historyStack.length },
          }))
          const content = vm.historyStack[vm.historyIndex]
          const container = vm.getInlineContainer()
          const doc = vm.getInlineDoc()
          const savedSel = container && doc ? saveSelection(container, doc) : null
          const textEditor = vm.$el.closest('.text-editor-wrapper')
            ? vm.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
            : vm.$el.nextElementSibling
          textEditor.innerHTML = content
          $perAdminApp.action(this, 'textEditorWriteToModel')
          vm.$nextTick(() => {
            vm.isUndoing = false
            if (savedSel && container && doc) {
              // restoreSelection(container, savedSel, doc)
            }
            vm.pingRichToolbar()
          })
        }
      },

    redo() {
      if (vm.onSubNav) {
        window.dispatchEvent(new CustomEvent('richtoolbar:cmd', { detail: { cmd: 'redo' } }))
        return
      }
      if (vm.historyIndex < vm.historyStack.length - 1) {
        vm.isUndoing = true
        vm.historyIndex++
        window.dispatchEvent(new CustomEvent('richtoolbar:history', {
          detail: { historyIndex: vm.historyIndex, historyLength: vm.historyStack.length },
        }))
        const content = vm.historyStack[vm.historyIndex]
        const container = vm.getInlineContainer()
        const doc = vm.getInlineDoc()
        const savedSel = container && doc ? saveSelection(container, doc) : null
        const textEditor = vm.$el.closest('.text-editor-wrapper')
          ? vm.$el.closest('.text-editor-wrapper').querySelector('.text-editor')
          : vm.$el.nextElementSibling
        textEditor.innerHTML = content
        $perAdminApp.action(this, 'textEditorWriteToModel')
        vm.$nextTick(() => {
          vm.isUndoing = false
          if (savedSel && container && doc) {
            // restoreSelection(container, savedSel, doc)
          }
          vm.pingRichToolbar()
        })
      }
    },












  
  }
}
