// const { glob, globSync } = require("glob");
const fs = require("fs");
const { execSync } = require("child_process");

async function npmInstall(path) {
  try {
    execSync("npm ci", {
      cwd: path,
      // stdio: "inherit",
      stdio: ["pipe", "pipe", "pipe"],
      encoding: "utf-8"
    });
    console.log("Successfully installed (npm ci) packages for:", path);
  } catch {
    try {
      execSync("npm i", {
        cwd: path,
        // stdio: "inherit",
        stdio: ["pipe", "pipe", "pipe"],
        encoding: "utf-8"
      });
      console.log("Successfully installed (npm i) packages for:", path);
    } catch {
      console.log("FAILED to install packages for:", path);
    }
  }
}

async function main() {
  // const packageJsons = await glob("./**/package.json", {
  //   ignore: ["**/node_modules/**"],
  //   platform: "linux",
  //   dot: true,
  //   dotRelative: true
  // });
  // console.log(packageJsons);

  const packageJsons = [
    "./package.json",
    "./buildscripts/package.json",
    "./admin-base/ui.apps/package.json",
    "./admin-base/materialize/package.json",
    "./admin-base/core/package.json",
    "./themes/themeclean/ui.apps/package.json",
    "./samples/experiences/ui.apps/package.json",
    "./samples/example-vue-site/ui.apps/package.json",
    "./platform/graphql/ui.apps/package.json",
    "./pagerenderer/vue/ui.apps/package.json",
    "./pagerenderer/server/ui.apps/package.json",
    "./pagerenderer/react/ui.apps/package.json",
    "./tooling/maven/archetypes/project/src/main/resources/archetype-resources/ui.apps/package.json"
  ];

  for (let i = 0; i < packageJsons.length; i++) {
    const path = packageJsons[i].replace("/package.json", "");
    npmInstall(path);
  }

  // create export paths
  const dirs = [
    "./admin-base/ui.apps/target/classes/etc/felibs",
    "./admin-base/ui.apps/target/classes/etc/felibs/admin",
    "./admin-base/ui.apps/target/classes/etc/felibs/admin/js",
    "./admin-base/ui.apps/target/classes/etc/felibs/admin/dependencies",
    "./admin-base/ui.apps/target/classes/etc/felibs/admin/css",
    "./admin-base/ui.apps/target/classes/content/docs",
    "./admin-base/ui.apps/target/classes/content/docs/pages",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/write",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/versioning",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/sitemaps",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/replication",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/renditions",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/hatch",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/docker",
    "./admin-base/ui.apps/target/classes/content/docs/pages/public/distribution"
  ];

  for (let i = 0; i < dirs.length; i++) {
    const dir = dirs[i];
    await fs.promises.mkdir(dir, { recursive: true });
  }
}

main();
