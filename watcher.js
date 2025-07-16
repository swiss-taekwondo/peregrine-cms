const chokidar = require("chokidar");
const { exec } = require("child_process");

const run = cmd =>
  new Promise((resolve, reject) => {
    exec(cmd, (err, stdout, stderr) => {
      if (stdout) process.stdout.write(stdout);
      if (stderr) process.stderr.write(stderr);
      if (err) return reject(err);
      resolve();
    });
  });

const runningMessage = () => {
  console.log(
    "\x1b[34m%s\x1b[0m",
    "🔭 Watcher is running. Waiting for changes..."
  );
};

const onChange = async path => {
  try {
    if (path) {
      console.log("\x1b[33m%s\x1b[0m", `Change detected in ${path}`);
      if (
        path.includes("admin-base/materialize") ||
        path.includes("admin-base\\materialize")
      ) {
        await run("npm run build:css:patch");
        console.log(
          "\x1b[36m%s\x1b[0m",
          `Materialize CSS updated ${new Date().toLocaleTimeString()}`
        );
      } else if (path.includes("admin-base")) {
        await run("npm run build:patch");
        console.log(
          "\x1b[36m%s\x1b[0m",
          `Admin base updated ${new Date().toLocaleTimeString()}`
        );
      }
    }

    console.log(
      "\x1b[32m%s\x1b[0m",
      "✅ Everything up to date! Waiting for new changes..."
    );
  } catch (err) {
    console.error("\x1b[31m%s\x1b[0m", `❌ Build failed: ${err.message}`);
  }
  runningMessage();
};

const watcher = chokidar.watch("admin-base/", {
  ignored: path =>
    path.includes("admin-base/ui.apps/target") ||
    path.includes("admin-base/materialize/target"),
  ignoreInitial: false
});
runningMessage();

watcher.on("change", onChange);
