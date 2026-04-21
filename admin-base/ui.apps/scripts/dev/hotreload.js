const path = require('path');
const { spawn } = require('child_process');

const rootDir = path.resolve(__dirname, '../..');
const nodeBin = process.execPath;
const nodemonBin = path.join(rootDir, 'node_modules/nodemon/bin/nodemon.js');
const hotReloadServer = path.join(__dirname, 'hotreload-server.js');
const children = [];

function spawnChild(command, args, extraOptions = {}) {
  const child = spawn(command, args, {
    cwd: rootDir,
    stdio: 'inherit',
    shell: false,
    ...extraOptions,
  });
  children.push(child);
  return child;
}

function shutdown(exitCode = 0) {
  for (const child of children) {
    if (!child.killed) {
      child.kill('SIGTERM');
    }
  }
  process.exit(exitCode);
}

const hotReloadServerProcess = spawnChild(nodeBin, [hotReloadServer]);
hotReloadServerProcess.on('exit', code => {
  if (code !== 0) {
    shutdown(code || 1);
  }
});

const nodemonProcess = spawnChild(nodeBin, [nodemonBin, '--config', 'nodemon.hotreload.json']);
nodemonProcess.on('exit', code => {
  shutdown(code || 0);
});

process.on('SIGINT', () => shutdown(0));
process.on('SIGTERM', () => shutdown(0));
