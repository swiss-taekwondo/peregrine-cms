// import { glob } from "glob";
import { execSync } from "child_process";

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

  // TODO: additionally fix build by creating /admin-base/ui.apps/target/etc/admin/dependencies/ dir
}

main();
