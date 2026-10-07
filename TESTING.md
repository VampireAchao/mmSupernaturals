# Development guidelines

- Build/test: `mvn verify`. Every feature/bugfix must add or update a test in `src/test/java`, and `mvn verify` must pass before deploy.

## IDEA dev environment (Minecraft server + debug)

Based on https://vampireachao.github.io/2020/10/05/bukkit%E5%BC%80%E5%8F%91%E7%8E%AF%E5%A2%83%E6%90%AD%E5%BB%BA/

1. Download a Paper (or Spigot) server jar of the target version and save it as `paper.jar` in the project root.
2. Open the project in IDEA (Maven import). The shared run configuration **Server** (type: JAR Application) is
   in `.idea/runConfigurations/`: path to jar = `paper.jar`, working directory = project root,
   before launch = Maven `clean package`.
3. `pom.xml` sets the jar plugin `outputDirectory` to `${session.executionRootDirectory}/plugins/`,
   so each build lands directly in the server's `plugins` folder.
4. Press the green **Run** (or the green bug **Debug**) button on **Server**: IDEA rebuilds the plugin,
   restarts the server and, in debug mode, attaches the debugger so breakpoints work.
5. First run generates `eula.txt` (the config passes `-Dcom.mojang.eula.agree=true`) and `server.properties`;
   set `online-mode=false` for local testing, then join `localhost:25565`.

Server files in the project root are git-ignored.
