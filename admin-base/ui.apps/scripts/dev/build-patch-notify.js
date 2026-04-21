const http = require('http');
const { spawn } = require('child_process');

const port = parseInt(process.env.HOT_RELOAD_PORT || '35729', 10);
const host = process.env.HOT_RELOAD_HOST || '127.0.0.1';
const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';

function notifyReload() {
  return new Promise((resolve, reject) => {
    const request = http.request({
      host,
      port,
      path: '/reload',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    }, response => {
      response.resume();
      response.on('end', resolve);
    });

    request.on('error', reject);
    request.write(JSON.stringify({
      reason: 'build:patch',
      triggeredAt: Date.now(),
    }));
    request.end();
  });
}

const buildProcess = spawn(npmCommand, ['run', 'build:patch'], {
  stdio: 'inherit',
  shell: false,
});

buildProcess.on('exit', async code => {
  if (code !== 0) {
    process.exit(code || 1);
    return;
  }

  try {
    await notifyReload();
    console.log('[hotreload] notified connected browsers');
  } catch (error) {
    console.warn(`[hotreload] reload notification failed: ${error.message}`);
  }

  process.exit(0);
});
