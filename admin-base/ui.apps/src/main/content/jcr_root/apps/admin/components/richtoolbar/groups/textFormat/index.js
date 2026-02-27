import headlines from './headlines'
import paragraph from './paragraph'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  const paragraphItem = paragraph(vm)
  const headlineItems = headlines(vm)

  const getActiveItem = () => {
    for (let i = 1; i <= 6; i++) {
      if (vm.itemIsTag(`H${i}`)) return headlineItems[i - 1]
    }
    return paragraphItem
  }

  return {
    label: () => {
      const active = getActiveItem()
      return active ? `${vm.$i18n('text-format')} (${active.label})` : vm.$i18n('text-format')
    },
    icon: () => getActiveItem().icon,
    iconLib: IconLib.FONT_AWESOME,
    collapse: true,
    rules: () => !vm.responsive || !vm.hiddenGroups['text-format'],
    items: [paragraphItem, ...headlineItems]
  }
}
