
if (!window.axiosUses) window.axiosUses = {}
import { LoggerFactory } from '../logger';
import { set } from '../utils';

let log = LoggerFactory.logger('saveFile').setLevelDebug();

export default function(me, { path, content, extension }) {
  log.fine({ path, content, extension });

  return fetch(path, { method: 'PUT', body: content, headers: { 'Content-Type': 'text/plain' }}).then((response) => {
  window.axiosUses['saveFile'] = true
    set(me.getView(), '/state/tools/file', path);
    return response;
  });
}
