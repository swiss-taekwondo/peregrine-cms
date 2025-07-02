const { exec } = require("child_process");
const path = process.argv[2];

const run = (cmd) =>
  new Promise((resolve, reject) => {
    exec(cmd, (err, stdout, stderr) => {
      if (stdout) process.stdout.write(stdout);
      if (stderr) process.stderr.write(stderr);
      if (err) return reject(err);
      resolve();
    });
  });

(async () => {
  try {
    // Run both scripts on startup
    await run("npm run build:patch");
    await run("npm run build:css:patch");

    // Handle changes after startup
    if (path) {
      if (path.includes("admin-base/materialize")) {
        await run("npm run build:css:patch");
      } else if (path.includes("admin-base")) {
        await run("npm run build:patch");
      }
    }

    console.log("\x1b[32m%s\x1b[0m", "✅ Everything up to date! Waiting for new changes...");
  } catch (err) {
    console.error("\x1b[31m%s\x1b[0m", `❌ Build failed: ${err.message}`);
  }
})();
