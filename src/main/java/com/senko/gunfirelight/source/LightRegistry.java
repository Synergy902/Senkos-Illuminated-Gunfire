package com.senko.gunfirelight.source;

/** Client-thread point sources. No game lighting, position quantization, or per-frame allocation. */
public final class LightRegistry {
    private final int capacity;
    private final int[] active, activePosition, free;
    private final long[] token;
    private final double[] x, y, z, born, expires;
    private final float[] radius, red, green, blue, intensity;
    private final int[] priority;
    private final boolean[] fading;
    private int size, freeCount;
    private long generation = 1;
    private final int[] selected;
    private final double[] scores;
    private long rejected, culled;

    public LightRegistry(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        active = new int[capacity]; activePosition = new int[capacity]; free = new int[capacity];
        token = new long[capacity]; x = new double[capacity]; y = new double[capacity]; z = new double[capacity];
        born = new double[capacity]; expires = new double[capacity]; radius = new float[capacity];
        red = new float[capacity]; green = new float[capacity]; blue = new float[capacity];
        intensity = new float[capacity]; priority = new int[capacity]; fading = new boolean[capacity];
        selected = new int[capacity]; scores = new double[capacity];
        for (int i = 0; i < capacity; i++) free[i] = capacity - 1 - i;
        freeCount = capacity;
    }

    public long create(double px, double py, double pz, float range, float r, float g, float b,
                       float strength, double now, double lifetimeSeconds, boolean fade, int importance) {
        if (!valid(px, py, pz, range, r, g, b, strength) || !Double.isFinite(now)
                || !(lifetimeSeconds > 0) || Double.isNaN(lifetimeSeconds)) return 0;
        if (freeCount == 0) { rejected++; return 0; }
        int s = free[--freeCount];
        // Slot in low 16 bits; monotonically increasing generation protects stale users.
        long id = ((generation++ & 0x7fffffffffffL) << 16) | s;
        if (id == 0) id = ((generation++ & 0x7fffffffffffL) << 16) | s;
        token[s] = id; activePosition[s] = size; active[size++] = s;
        set(s, px, py, pz, range, r, g, b, strength);
        born[s] = now; expires[s] = now + lifetimeSeconds; fading[s] = fade;
        priority[s] = Math.max(0, Math.min(16, importance));
        return id;
    }

    public boolean update(long id, double px, double py, double pz, float range,
                          float r, float g, float b, float strength) {
        int s = slot(id);
        if (s < 0 || !valid(px, py, pz, range, r, g, b, strength)) return false;
        set(s, px, py, pz, range, r, g, b, strength); return true;
    }

    public boolean remove(long id) {
        int s = slot(id); if (s < 0) return false; removeSlot(s); return true;
    }

    public boolean contains(long id) { return slot(id) >= 0; }
    public int size() { return size; }
    public long rejected() { return rejected; }
    public long culled() { return culled; }

    /** Packed: relative xyz, finite radius, radiance rgb, reserved zero. */
    public int collect(double now, double cx, double cy, double cz, double maximumDistance,
                       float[] output, int requested) {
        return collectExcluding(now, cx, cy, cz, maximumDistance, output, requested, 0);
    }

    /** Select a second view without duplicating the local muzzle's hand-space emitter. */
    public int collectExcluding(double now, double cx, double cy, double cz, double maximumDistance,
                               float[] output, int requested, long excludedHandle) {
        if (!Double.isFinite(now) || !Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)
                || !Double.isFinite(maximumDistance) || maximumDistance <= 0) return 0;
        int limit = Math.min(Math.min(requested, output.length / 8), capacity);
        if (limit <= 0) return 0;
        int count = 0;
        for (int a = 0; a < size;) {
            int s = active[a];
            if (now >= expires[s]) { removeSlot(s); continue; }
            a++;
            if (excludedHandle != 0 && token[s] == excludedHandle) continue;
            double dx = x[s] - cx, dy = y[s] - cy, dz = z[s] - cz;
            double distance2 = dx * dx + dy * dy + dz * dz;
            double reach = maximumDistance + radius[s];
            if (distance2 > reach * reach) { culled++; continue; }
            float f = envelope(s, now);
            if (f <= 0.0001f || intensity[s] <= 0) continue;
            double score = (1.0 + priority[s] * 4.0) * radius[s] * radius[s] * intensity[s] * f
                * (red[s] * .2126 + green[s] * .7152 + blue[s] * .0722) / (1.0 + distance2);
            if (count < limit) {
                int p = count++;
                while (p > 0) {
                    int parent = (p - 1) >>> 1;
                    if (scores[parent] <= score) break;
                    selected[p] = selected[parent]; scores[p] = scores[parent]; p = parent;
                }
                selected[p] = s; scores[p] = score;
            } else if (score > scores[0]) {
                int p = 0;
                while (true) {
                    int left = p * 2 + 1;
                    if (left >= count) break;
                    int child = left + 1 < count && scores[left + 1] < scores[left] ? left + 1 : left;
                    if (scores[child] >= score) break;
                    selected[p] = selected[child]; scores[p] = scores[child]; p = child;
                }
                selected[p] = s; scores[p] = score;
            }
        }
        // Tile budgets consume sources in order. Heap-sort descending so a
        // crowded tile retains the strongest/closest sources, not heap leaves.
        for (int end = count - 1; end > 0; end--) {
            int saved = selected[end]; double score = scores[end];
            selected[end] = selected[0]; scores[end] = scores[0];
            int p = 0;
            while (true) {
                int left = p * 2 + 1; if (left >= end) break;
                int child = left + 1 < end && scores[left + 1] < scores[left] ? left + 1 : left;
                if (scores[child] >= score) break;
                selected[p] = selected[child]; scores[p] = scores[child]; p = child;
            }
            selected[p] = saved; scores[p] = score;
        }
        for (int i = 0; i < count; i++) {
            int s = selected[i], o = i * 8;
            float energy = intensity[s] * envelope(s, now);
            output[o] = (float)(x[s] - cx); output[o + 1] = (float)(y[s] - cy); output[o + 2] = (float)(z[s] - cz);
            output[o + 3] = radius[s]; output[o + 4] = red[s] * energy;
            output[o + 5] = green[s] * energy; output[o + 6] = blue[s] * energy; output[o + 7] = 0;
        }
        return count;
    }

    public void clear() {
        while (size > 0) removeSlot(active[size - 1]);
    }

    private float envelope(int s, double now) {
        if (!fading[s] || !Double.isFinite(expires[s])) return 1;
        double t = Math.max(0, Math.min(1, (now - born[s]) / (expires[s] - born[s])));
        // Immediate ignition, continuous smooth decay to zero. Never rebuild terrain.
        return (float)((1 - t) * (1 - t) * (1 + 2 * t));
    }

    private int slot(long id) {
        int s = (int)(id & 65535L);
        return id != 0 && s < capacity && token[s] == id ? s : -1;
    }

    private void removeSlot(int s) {
        int p = activePosition[s], moved = active[--size];
        active[p] = moved; activePosition[moved] = p; token[s] = 0; free[freeCount++] = s;
    }

    private void set(int s, double px, double py, double pz, float range, float r, float g, float b, float strength) {
        x[s] = px; y[s] = py; z[s] = pz; radius[s] = Math.min(64, range);
        red[s] = Math.min(16, r); green[s] = Math.min(16, g); blue[s] = Math.min(16, b);
        intensity[s] = Math.min(64, strength);
    }

    private static boolean valid(double x, double y, double z, float range, float r, float g, float b, float strength) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Float.isFinite(range) && range > 0 && Float.isFinite(r) && r >= 0
            && Float.isFinite(g) && g >= 0 && Float.isFinite(b) && b >= 0
            && Float.isFinite(strength) && strength >= 0;
    }
}
