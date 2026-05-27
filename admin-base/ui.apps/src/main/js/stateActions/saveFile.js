import { LoggerFactory } from '../logger';
import { set } from '../utils';

let log = LoggerFactory.logger('saveFile').setLevelDebug();

export default function(me, { path, content, extension }) {
  log.fine({ path, content, extension });

  return fetch(path, { method: 'PUT', body: content, headers: { 'Content-Type': 'text/plain' }}).then((response) => {
    set(me.getView(), '/state/tools/file', path);
    return response;
  });
}
