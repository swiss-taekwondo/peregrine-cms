import alignLeft from './alignLeft'
import alignCenter from './alignCenter'
import alignRight from './alignRight'
import alignJustify from './alignJustify'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'align',
    icon: 'align-justify',
    iconLib: IconLib.FONT_AWESOME,
    order: 40,
    priority: 50,
    rules: () => !vm.responsive || !vm.hiddenGroups.align,
    items: [
      alignLeft(vm),
      alignCenter(vm),
      alignRight(vm),
      alignJustify(vm)
    ]
  }
}
