import editLink from './editLink'
import removeLink from './removeLink'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  const isATag = () => vm.itemIsTag('A')
  return {
    label: 'link',
    icon: 'link',
    iconLib: IconLib.FONT_AWESOME,
    collapse: true,
    order: 60,
    priority: 70,
    isActive: isATag,
    isDisabled: () => !vm.hasEditorSelection && !isATag(),
    toggleClick: () => { if (!isATag()) vm.exec('link') },
    rules: () => !vm.responsive || !vm.hiddenGroups.link,
    items: [
      editLink(vm),
      removeLink(vm),
    ],
    itemRules: [
      isATag,
      isATag,
    ]
  }
}
