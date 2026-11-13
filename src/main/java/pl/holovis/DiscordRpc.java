package pl.holovis;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Discord Rich Presence przez lokalne IPC (bez bibliotek).
 * Tytuł "HoloVisuals" bierze się z NAZWY APLIKACJI w Discord Developer Portal - wpisz jej Application ID
 * w {@link #DEFAULT_APP_ID} przed zbudowaniem moda (wtedy działa u każdego) albo wklej je w menu.
 */
public final class DiscordRpc {
    private DiscordRpc() {}

    /** Application ID aplikacji o nazwie "HoloVisuals" z https://discord.com/developers/applications */
    public static final String DEFAULT_APP_ID = "";

    public static volatile String status = "Wyłączone";
    private static volatile String details = "W menu głównym";
    private static volatile String state = "Minecraft";
    private static volatile boolean run;
    private static Thread thread;
    private static String runningId = "";
    private static int gen;

    public static String appId() {
        String id = HoloConfig.get().discordAppId;
        if (id == null || id.isBlank()) id = DEFAULT_APP_ID;
        return id.trim();
    }

    public static synchronized void sync() {
        HoloConfig c = HoloConfig.get();
        String id = appId();
        if (!c.discordRpc) { stop(); status = "Wyłączone"; return; }
        if (!id.matches("\\d{15,25}")) { stop(); status = "Brak ID aplikacji"; return; }
        if (run && id.equals(runningId)) return;
        stop();
        runningId = id;
        run = true;
        final int my = ++gen;
        status = "Łączenie...";
        thread = new Thread(() -> loop(my, id), "holovis-discord");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void stop() {
        run = false;
        gen++;
        if (thread != null) thread.interrupt();
        thread = null;
        runningId = "";
    }

    /** Wywoływać z głównego wątku co kilka sekund. */
    public static void update(MinecraftClient mc) {
        String d;
        if (mc.world == null) d = "W menu głównym";
        else if (mc.isInSingleplayer()) d = "Gra solo";
        else {
            ServerInfo si = mc.getCurrentServerEntry();
            d = si != null ? "Gra na " + si.address : "Gra online";
        }
        details = d;
        state = "Minecraft " + mc.getGameVersion();
    }

    // ------------------------------------------------------------------
    private static void loop(int my, String appId) {
        long start = System.currentTimeMillis() / 1000L;
        Pipe p = null;
        String last = "";
        long lastSend = 0;
        while (run && my == gen) {
            try {
                if (p == null) {
                    p = open();
                    if (p == null) { status = "Nie wykryto Discorda"; if (!nap(10_000)) break; continue; }
                    send(p, 0, "{\"v\":1,\"client_id\":\"" + appId + "\"}");
                    String resp = recv(p);
                    if (!resp.contains("READY")) throw new IOException("handshake: " + resp);
                    status = "Połączono";
                    last = "";
                }
                String key = details + "|" + state;
                long now = System.currentTimeMillis();
                if (!key.equals(last) && now - lastSend > 4000) {
                    setActivity(p, details, state, start);
                    last = key;
                    lastSend = now;
                }
                if (!nap(1000)) break;
            } catch (Exception e) {
                if (p != null) { p.close(); p = null; }
                status = "Rozłączono, ponawiam...";
                if (!nap(8000)) break;
            }
        }
        if (p != null) {
            try { send(p, 1, command(null)); } catch (Exception ignored) {}
            p.close();
        }
    }

    private static boolean nap(long ms) {
        try { Thread.sleep(ms); return true; } catch (InterruptedException e) { return false; }
    }

    private static void setActivity(Pipe p, String det, String st, long start) throws IOException {
        JsonObject act = new JsonObject();
        act.addProperty("details", det);
        act.addProperty("state", st);
        JsonObject ts = new JsonObject();
        ts.addProperty("start", start);
        act.add("timestamps", ts);
        act.addProperty("instance", false);
        send(p, 1, command(act));
        recv(p);
    }

    private static String command(JsonObject activity) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        if (activity != null) args.add("activity", activity);
        JsonObject root = new JsonObject();
        root.addProperty("cmd", "SET_ACTIVITY");
        root.add("args", args);
        root.addProperty("nonce", UUID.randomUUID().toString());
        return root.toString();
    }

    // ------------------------------------------------------------------
    private interface Pipe {
        void write(byte[] b) throws IOException;
        void readFully(byte[] b) throws IOException;
        void close();
    }

    private static void send(Pipe p, int op, String json) throws IOException {
        byte[] d = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + d.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(op).putInt(d.length).put(d);
        p.write(b.array());
    }

    private static String recv(Pipe p) throws IOException {
        byte[] h = new byte[8];
        p.readFully(h);
        ByteBuffer hb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        hb.getInt();
        int len = hb.getInt();
        if (len < 0 || len > 1 << 20) throw new IOException("zła ramka");
        byte[] d = new byte[len];
        p.readFully(d);
        return new String(d, StandardCharsets.UTF_8);
    }

    private static Pipe open() {
        boolean win = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                if (win) {
                    RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Pipe() {
                        public void write(byte[] b) throws IOException { f.write(b); }
                        public void readFully(byte[] b) throws IOException { f.readFully(b); }
                        public void close() { try { f.close(); } catch (IOException ignored) {} }
                    };
                }
                String[] dirs = {System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"),
                        System.getenv("TEMP"), "/tmp"};
                for (String dir : dirs) {
                    if (dir == null || dir.isEmpty()) continue;
                    for (String sub : new String[]{"", "/app/com.discordapp.Discord", "/snap.discord"}) {
                        Path path = Path.of(dir + sub, "discord-ipc-" + i);
                        if (!Files.exists(path)) continue;
                        SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(path));
                        InputStream in = Channels.newInputStream(ch);
                        OutputStream out = Channels.newOutputStream(ch);
                        return new Pipe() {
                            public void write(byte[] b) throws IOException { out.write(b); out.flush(); }
                            public void readFully(byte[] b) throws IOException {
                                int off = 0;
                                while (off < b.length) {
                                    int n = in.read(b, off, b.length - off);
                                    if (n < 0) throw new IOException("EOF");
                                    off += n;
                                }
                            }
                            public void close() { try { ch.close(); } catch (IOException ignored) {} }
                        };
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
