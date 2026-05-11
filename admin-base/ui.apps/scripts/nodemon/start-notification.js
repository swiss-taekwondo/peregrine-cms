const path = require('path');
const notifier = require('node-notifier');

try {
	process.loadEnvFile('.env');
	if (process.env.HOTRELOAD_SILENT === 'true') {
		process.exit(0)
	}
} catch {}

notifier.notify({
  id: 1337,
  title: 'peregrine-cms (STARTED)',
  message: 'started building & deploying /ui.apps',
  icon: path.join(
    __dirname,
    '../../../../pagerenderer/server/ui.apps/src/main/content/jcr_root/content/pagerenderserver/assets/peregrine-logo.png'
  ),
  timeout: 1,
});
