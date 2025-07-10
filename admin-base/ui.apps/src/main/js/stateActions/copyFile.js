import { LoggerFactory } from '../logger';
import { set, parentPath as getParentPath } from '../utils';
import { SUFFIX_PARAM_SEPARATOR } from '../constants';

let log = LoggerFactory.logger('copyFile').setLevelDebug();

export default function(me, { from, to, resourceType = 'nt:file' }) {
  log.fine({ from, to });

  console.log('TESING: ', me, from, to, resourceType)
  debugger
  const view = me.getView();
  const page = to.split('/')[3];
  const file = from.split('/').pop();
  let filename = file;
  let extension = "";
  const fileSplit = file.split('.');
  if (fileSplit.length > 0) {
    extension = "." + fileSplit.pop();
  }
  filename = `${fileSplit.join('.')}-copy${extension}`;

  const options = {
    headers: {
      'Content-Type': 'text/plain',
    },
  };
  let existingNode = me.findNodeFromPath(
    me.getView().admin.nodes,
    `${to}/${filename}`
  );

  if (existingNode) {
    let counter = 2;

    while (existingNode) {
      filename = `${fileSplit.join('.')}-copy-${counter}${extension}`;
      existingNode = me.findNodeFromPath(
        me.getView().admin.nodes,
        `${to}/${filename}`
      );
      counter++;
    }
  }

  return axios
    .get(from, options)
    .then(({ data: content }) => {
      return axios.put(`${to}/${filename}`, content, options);
    })
    .then((response) => {
      set(view, '/state/tools/file', to);
      set(view, '/state/tools/explorerpreview/resourceType', resourceType);

      return response;
    })
    .then(() =>
      $perAdminApp.loadContent(
        `/content/admin/pages/${page}.html/path${SUFFIX_PARAM_SEPARATOR}${to}`
      )
    )
    .then(() => ({
      path: `${to}/${filename}`,
      filename: filename,
      resourceType: resourceType,
    }));
}
