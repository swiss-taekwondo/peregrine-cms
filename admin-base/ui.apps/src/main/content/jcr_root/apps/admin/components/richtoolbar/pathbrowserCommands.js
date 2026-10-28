import {IconLib, PathBrowser} from '../../../../../../js/constants'

export function createPathbrowserCommandMethods(vm) {
  return {

  onBrowserSelect() {
    vm.browser.open = false

    vm.$nextTick(() => {
      if (['editLink', 'insertLink'].includes(vm.param.cmd)) {
        vm.buttonCommands.onLinkSelect()
        return
      } else if (['insertImage', 'editImage', 'editIcon'].includes(vm.param.cmd)) {
        vm.buttonCommands.onImageSelect().catch((err) => console.error(err))
        return
      }

      vm.execCmd(vm.param.cmd, vm.param.value)
      vm.param.cmd = null
      vm.param.value = null
      vm.param.anchor = null
      vm.browser.path.selected = null
      vm.browser.linkTitle = null
      vm.browser.img.width = null
      vm.browser.img.height = null
    })
  },

    // ====================
    // Links
    // ====================

  removeLink(vm) {
    vm.lastSelectedRte.ownerDocument.execCommand('unlink', false, null)
    vm.triggerInput()
  },

  insertLink() {
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
    vm.startBrowsing(startPath)
  },

  editLink() {
    const anchor = vm.rangeMatchesSelector('a')
    const href = anchor.getAttribute('href') || ''
    const title = anchor.getAttribute('title') || ''
    const target = anchor.getAttribute('target') || ''
    const rel = anchor.getAttribute('rel') || ''

    let startPath = vm.roots.pages
    vm.param.cmd = 'editLink'
    vm.param.anchor = anchor
    vm.browser.header = vm.$i18n('Edit Link')
    vm.browser.withLinkTab = true
    vm.browser.newWindow = target === '_blank'
    vm.browser.rel = rel.includes('noopener')
    vm.browser.linkTitle = title
    vm.browser.type = PathBrowser.Type.PAGE

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
      vm.browser.path.selected = resolvedHref
      startPath = vm.roots.pages || '/'
    } else {
      // Normalize only the repository path; keep query strings and fragments verbatim.
      const suffixIndex = resolvedHref.search(/[?#]/)
      const pathname = suffixIndex < 0 ? resolvedHref : resolvedHref.slice(0, suffixIndex)
      const suffix = suffixIndex < 0 ? '' : resolvedHref.slice(suffixIndex)
      let hrefPath = pathname.replace(/\.html$/, '')
      if (!hrefPath.startsWith('/content/')) {
        hrefPath = (vm.roots.pages || '') + hrefPath
      }
      const lastSlash = hrefPath.lastIndexOf('/')
      startPath = lastSlash > 0 ? hrefPath.substring(0, lastSlash) : (vm.roots.pages || '/')
      vm.browser.path.selected = hrefPath + suffix
    }
    vm.browser.path.current = startPath
    vm.startBrowsing(startPath)
    vm.startBrowsing(startPath)
  },

  onLinkSelect() {
    const typeLC = vm.browser.type?.toLowerCase()
    const isAssetOrFile = typeLC === PathBrowser.Type.ASSET || typeLC === 'file'
    let href = vm.browser.path.selected || ''
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

    const isPageType = vm.browser.type?.toLowerCase() === PathBrowser.Type.PAGE
    if (href.startsWith('/') && isPageType) {
      const url = new URL(href, "https://placeholder.invalid");
      if (!url.pathname.endsWith('.html')) {
        url.pathname += ".html";
      }
      href = url.pathname + url.search + url.hash;
    }

    const applyLinkAttributes = (link) => {
      link.setAttribute('href', href)
      if (vm.browser.linkTitle) {
        link.setAttribute('title', vm.browser.linkTitle)
      } else {
        link.removeAttribute('title')
      }
      if (isAssetOrFile) {
        link.setAttribute('download', '')
        link.removeAttribute('target')
        link.removeAttribute('rel')
      } else {
        link.removeAttribute('download')
        link.setAttribute('target', vm.browser.newWindow ? '_blank' : '_self')
        link.setAttribute('rel', vm.browser.rel ? 'noopener noreferrer' : '')
      }
    }

    if (vm.param.cmd === 'insertLink') {
      const range = vm.lastInRteSelection;
      const link = range.startContainer.ownerDocument.createElement('a')
      applyLinkAttributes(link)
      const textEditor = vm.lastSelectedRte;

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
      vm.triggerInput()
    } else { // param.cmd === editLink
      let anchor = vm.param.anchor
      applyLinkAttributes(anchor)
      vm.param.anchor = null
      vm.triggerInput()
    }
  },


  // ====================
  // Images
  // ====================

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



  }
}
