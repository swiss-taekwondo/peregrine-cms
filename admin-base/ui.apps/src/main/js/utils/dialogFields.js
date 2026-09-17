export function dataFields(fields = []) {
  return fields.reduce((result, field) => {
    if (field.type === 'uigroup') return result.concat(dataFields(field.fields))
    if (field.type === 'uigroupswitch') return result.concat(field, dataFields(field.fields))
    if (field.type !== 'divider') result.push(field)
    return result
  }, [])
}
