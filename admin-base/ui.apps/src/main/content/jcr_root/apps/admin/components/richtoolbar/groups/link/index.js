import insertLink from './insertLink'
import editLink from './editLink'
import removeLink from './removeLink'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'link',
    icon: 'link',
    iconLib: IconLib.FONT_AWESOME,
    collapse: true,
    isActive: () => vm.itemIsTag('A'),
    toggleClick: () => { if (!vm.itemIsTag('A')) vm.exec('link') },
    rules: () => !vm.responsive || !vm.hiddenGroups.link,
    items: [
      insertLink(vm),
      editLink(vm),
      removeLink(vm),
    ],
    itemRules: [
      () => true,
      () => vm.itemIsTag('A'),
      () => vm.itemIsTag('A'),
    ]
  }
}
