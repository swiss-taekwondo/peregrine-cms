import superscript from './superscript'
import subscript from './subscript'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'super-sub-script',
    icon: 'A<sup>2</sup>',
    iconLib: IconLib.PLAIN_TEXT,
    order: 80,
    priority: 90,
    rules: () => !vm.responsive || !vm.hiddenGroups['super-sub-script'],
    items: [
      superscript(vm),
      subscript(vm)
    ]
  }
}
