package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;

public class AppLogic {

    public static final String APPS_JSON = "\\\\10.50.93.5\\g\\DebswanaAutomationProject\\apps.json";
    public static final String SERVER    = "\\\\10.50.93.5";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public List<AppModel> apps = new ArrayList<>();

    // ── Load / Save ───────────────────────────────────────────────────────

    public void loadApps() {
        try (Reader r = new FileReader(APPS_JSON)) {
            apps = gson.fromJson(r, new TypeToken<List<AppModel>>(){}.getType());
            if (apps == null) apps = new ArrayList<>();
        } catch (Exception e) {
            System.err.println("load: " + e.getMessage());
            apps = new ArrayList<>();
        }
    }

    public void saveApps() {
        try (Writer w = new FileWriter(APPS_JSON)) {
            gson.toJson(apps, w);
        } catch (Exception e) {
            System.err.println("save: " + e.getMessage());
        }
    }

    public void addApp(AppModel a) {
        apps.add(a);
        saveApps();
    }

    // ── Connection ────────────────────────────────────────────────────────

    /** Returns map with keys: connected, ssid, is_debs, server_ok */
    public Map<String,Object> checkConnection() {
        Map<String,Object> result = new HashMap<>();
        result.put("connected", false);
        result.put("ssid", "");
        result.put("is_debs", false);
        result.put("server_ok", false);

        try {
            Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "(Get-NetConnectionProfile).Name")
                    .redirectErrorStream(true).start();
            String ssid = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            if (!ssid.isEmpty()) {
                result.put("connected", true);
                result.put("ssid", ssid);
                result.put("is_debs", "debs.debswana.bw".equals(ssid));
            }
        } catch (Exception ignored) {}

        result.put("server_ok", isServerReachable());
        return result;
    }

    public boolean isServerReachable() {
        try {
            Process p = new ProcessBuilder("net", "view", SERVER)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception ignored) {}
        try {
            return Files.exists(Path.of(SERVER));
        } catch (Exception ignored) {}
        return false;
    }

    public void openServerInExplorer() {
        try { new ProcessBuilder("explorer", SERVER).start(); } catch (Exception ignored) {}
    }

    // ── Install ───────────────────────────────────────────────────────────

    /** Returns true on success. status: (message, color) where color = "orange"|"red"|"green" */
    public boolean installApp(AppModel app, BiConsumer<String,String> status) {
        String name = app.name;
        String path = app.path;
        String exePath = path;

        try {
            status.accept("Checking network...", "orange");
            if (!isServerReachable()) {
                status.accept("Server unreachable. Check your network connection.", "red");
                return false;
            }

            if ("copy-then-run".equals(app.type)) {
                status.accept("Copying files for " + name + "...", "orange");
                Files.createDirectories(Path.of(app.destDir));
                String src = path.endsWith("*") ? path.substring(0, path.length() - 1).stripTrailing() : path;
                Path srcPath = Path.of(src);
                if (path.endsWith("*")) {
                    try (var stream = Files.newDirectoryStream(srcPath)) {
                        for (Path item : stream) {
                            Path dst = Path.of(app.destDir).resolve(item.getFileName());
                            if (Files.isDirectory(item)) {
                                copyDir(item, dst);
                            } else {
                                Files.copy(item, dst, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                } else if (Files.isDirectory(srcPath)) {
                    copyDir(srcPath, Path.of(app.destDir));
                } else {
                    Files.copy(srcPath, Path.of(app.destDir, srcPath.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                exePath = app.destDir + "\\" + app.exeName;
            }

            status.accept("Launching installer: " + name + "...", "orange");
            List<String> cmd;
            if (app.runAsAdmin) {
                String ps = "-FilePath \"" + exePath + "\"";
                if (!app.args.isBlank()) ps += " -ArgumentList \"" + app.args + "\"";
                if (!app.workingDir.isBlank()) ps += " -WorkingDirectory \"" + app.workingDir + "\"";
                cmd = List.of("powershell", "-Command", "Start-Process " + ps + " -Verb RunAs -Wait");
            } else {
                cmd = new ArrayList<>(List.of(exePath));
                if (!app.args.isBlank()) {
                    // naive split on spaces (handles simple args)
                    cmd.addAll(Arrays.asList(app.args.split("\\s+")));
                }
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (!app.workingDir.isBlank()) pb.directory(new File(app.workingDir));
            pb.inheritIO();
            int code = pb.start().waitFor();
            if (code != 0) {
                status.accept(name + " - installer exited with code " + code + ".", "red");
                return false;
            }

            status.accept(name + " - Installation completed.", "green");
            return true;

        } catch (Exception e) {
            if (!"copy-then-run".equals(app.type) && app.destDir != null && !app.destDir.isBlank()) {
                try { deleteDir(Path.of(app.destDir)); } catch (Exception ignored) {}
            }
            status.accept("Error installing " + name + ": " + e.getMessage(), "red");
            return false;
        }
    }

    // ── Proxy ─────────────────────────────────────────────────────────────

    public boolean isProxyEnabled() {
        try {
            Process p = new ProcessBuilder("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    "/v", "ProxyEnable").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            return out.contains("0x1");
        } catch (Exception e) { return false; }
    }

    public void setProxy(boolean enable) throws Exception {
        if (enable) {
            reg("ProxyEnable", "REG_DWORD", "1");
            reg("ProxyServer", "REG_SZ", "10.176.40.29:80");
            reg("ProxyOverride", "REG_SZ",
                "activation-v2.sls.microsoft.com;*.microsoft.com;*.windowsupdate.com");
        } else {
            reg("ProxyEnable", "REG_DWORD", "0");
        }
    }

    private void reg(String name, String type, String value) throws Exception {
        new ProcessBuilder("reg", "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", name, "/t", type, "/d", value, "/f")
                .redirectErrorStream(true).start().waitFor();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void copyDir(Path src, Path dst) throws IOException {
        Files.walk(src).forEach(s -> {
            try {
                Path d = dst.resolve(src.relativize(s));
                if (Files.isDirectory(s)) Files.createDirectories(d);
                else Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }

    private void deleteDir(Path p) throws IOException {
        if (!Files.exists(p)) return;
        Files.walk(p).sorted(Comparator.reverseOrder()).forEach(f -> {
            try { Files.delete(f); } catch (IOException ignored) {}
        });
    }
}
