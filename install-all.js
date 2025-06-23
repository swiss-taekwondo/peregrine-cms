import { glob } from "glob";
import { execSync } from "child_process";

async function npmInstall(path) {
  try {
    execSync("npm i", {
      cwd: path,
      stdio: "inherit",
      encoding: "utf-8"
    });
    console.log("Successfully installed packages for:", path);
  } catch (error) {
    console.log("FAILED to install packages for:", path);
  }
}

async function main() {
  const packageJsons = await glob("./**/package.json", {
    ignore: ["**/node_modules/**"],
    platform: "linux",
    dot: true,
    dotRelative: true
  });
  console.log(packageJsons);

  for (let i = 0; i < packageJsons.length; i++) {
    const path = packageJsons[i].replace("/package.json", "");
    npmInstall(path);
  }
}

main();
