const chokidar = require("chokidar");
const { exec } = require("child_process");


const run = (cmd) =>
  new Promise((resolve, reject) => {
    exec(cmd, (err, stdout, stderr) => {
      if (stdout) process.stdout.write(stdout);
      if (stderr) process.stderr.write(stderr);
      if (err) return reject(err);
      resolve();
    });
  });

const onChange = async (path) => {
  try {
    if (path) {
      console.log("PATH: ", path)
      if (path.includes("admin-base/materialize") || path.includes("admin-base\\materialize")) {
        await run("npm run build:css:patch");
        console.log("Materialize CSS updated");
      } else if (path.includes("admin-base")) {
        await run("npm run build:patch");
        console.log("Admin base updated");
      }
    }

    console.log("\x1b[32m%s\x1b[0m", "✅ Everything up to date! Waiting for new changes...");
  } catch (err) {
    console.error("\x1b[31m%s\x1b[0m", `❌ Build failed: ${err.message}`);
  }
};


const watcher = chokidar.watch('admin-base/', {
  ignored: (path) => path.includes("admin-base/ui.apps/target") || path.includes("admin-base/materialize/target"),
  ignoreInitial: false,
})
console.log(watcher)

watcher.on('change', onChange);
