# Development guidelines

- Build/test: `mvn verify`. Every feature/bugfix must add or update a test in `src/test/java` and `mvn verify` must pass before deploy.
- Debug in IDEA: select **Debug (restart server + attach)** and press the green bug. It runs **Debug Server**
  (`mvn -Pdebug package exec:exec`: rebuild, copy jar to `run/plugins`, start Paper with JDWP on port 5005) and
  **Attach Debugger** (remote JVM on localhost:5005). Put a Paper jar at `run/paper.jar` first.
  If the attach starts before the server is listening, re-run **Attach Debugger** alone.
  Re-running stops the previous server (IDEA asks to stop the running Maven process).
