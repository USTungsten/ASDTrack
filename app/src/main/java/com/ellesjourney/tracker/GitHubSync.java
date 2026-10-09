package com.ellesjourney.tracker;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class GitHubSync {
    private GitHubSync() {}

    static Result sync(TrackerStore store, SecureSettings.Config config) throws Exception {
        if (!config.isReady()) throw new Exception("Complete the GitHub token and 12-character family passphrase first");
        for (int attempt = 0; attempt < 2; attempt++) {
            RemoteFile remote = get(config);
            if (remote.encryptedContent != null) {
                String remoteJson;
                try {
                    remoteJson = CryptoBox.decrypt(remote.encryptedContent, config.passphrase);
                } catch (Exception exception) {
                    throw new Exception("The family passphrase does not match the existing shared data");
                }
                if (!store.importJson(remoteJson)) throw new Exception("The shared data file is not a valid app backup");
            }

            String encrypted = CryptoBox.encrypt(store.exportJson(), config.passphrase);
            int response = put(config, encrypted, remote.sha);
            if (response == 200 || response == 201) {
                return new Result(store.getEntries().size() + store.getCareUpdates().size()
                        + store.getWeeklySamples().size(), remote.sha == null);
            }
            if (response != 409) throw new Exception("GitHub rejected the update (HTTP " + response + ")");
        }
        throw new Exception("Both phones updated at once. Tap Sync now again.");
    }

    private static RemoteFile get(SecureSettings.Config config) throws Exception {
        HttpURLConnection connection = open(config, "GET");
        int response = connection.getResponseCode();
        if (response == 404) return new RemoteFile(null, null);
        String body = readBody(connection, response);
        if (response == 401 || response == 403) throw new Exception("GitHub token is invalid or lacks Contents read/write access");
        if (response != 200) throw new Exception("Could not read GitHub data (HTTP " + response + ")");
        JSONObject json = new JSONObject(body);
        String encoded = json.getString("content").replace("\n", "");
        String encrypted = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8);
        return new RemoteFile(json.getString("sha"), encrypted);
    }

    private static int put(SecureSettings.Config config, String encrypted, String sha) throws Exception {
        HttpURLConnection connection = open(config, "PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        JSONObject body = new JSONObject();
        body.put("message", "Sync encrypted Elle tracker data");
        body.put("branch", config.branch);
        body.put("content", Base64.encodeToString(encrypted.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        if (sha != null) body.put("sha", sha);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int response = connection.getResponseCode();
        readBody(connection, response);
        return response;
    }

    private static HttpURLConnection open(SecureSettings.Config config, String method) throws Exception {
        String endpoint = "https://api.github.com/repos/" + config.owner + "/" + config.repo
                + "/contents/" + config.path + ("GET".equals(method) ? "?ref=" + config.branch : "");
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("Authorization", "Bearer " + config.token);
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "Elles-Journey-Android");
        return connection;
    }

    private static String readBody(HttpURLConnection connection, int response) throws Exception {
        InputStream stream = response >= 200 && response < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class Result {
        final int records;
        final boolean created;

        Result(int records, boolean created) {
            this.records = records;
            this.created = created;
        }
    }

    private static final class RemoteFile {
        final String sha;
        final String encryptedContent;

        RemoteFile(String sha, String encryptedContent) {
            this.sha = sha;
            this.encryptedContent = encryptedContent;
        }
    }
}
