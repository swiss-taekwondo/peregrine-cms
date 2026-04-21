import insertImage from './insertImage'
import {IconLib} from '../../../../../../../../js/constants'

export default (vm) => {
  return {
    label: 'image',
    icon: 'picture-o',
    iconLib: IconLib.FONT_AWESOME,
    order: 70,
    priority: 100,
    rules: () => !vm.responsive || !vm.hiddenGroups.image,
    items: [
      insertImage(vm)
    ]
  }
}
