import specialCharacters from './specialCharacters'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'special-characters',
    icon: 'copyright',
    iconLib: IconLib.FONT_AWESOME,
    collapse: true,
    searchable: true,
    // Keep enabled while the dropdown is open (vm._specialCharRange is set after toggleClick).
    // Without this, typing in the search input fires selectionchange → hasEditorSelection=false
    // → the wrapping <button> becomes disabled (pointer-events:none) → character items unclickable.
    isDisabled: () => !vm.hasEditorSelection && !vm._specialCharRange,
    rules: () => !vm.responsive || !vm.hiddenGroups['special-characters'],
    items: [
      ...specialCharacters(vm)
    ],
    toggleClick() {
      vm.saveSelection()
      // Also capture a live Range for precise cursor restoration.
      // The character-offset approach (saveSelection/restoreSelection) cannot
      // distinguish "end of line 1" from "start of line 2" when a <br> separates
      // them — both positions have the same character count. Storing the live
      // Range avoids this ambiguity entirely.
      const doc = vm.getInlineDoc()
      if (doc && doc.defaultView) {
        const sel = doc.defaultView.getSelection()
        vm._specialCharRange = (sel && sel.rangeCount > 0)
          ? sel.getRangeAt(0).cloneRange()
          : null
      } else {
        vm._specialCharRange = null
      }
    }
  }
}