package dev.footstepstrail.deformation;

import dev.footstepstrail.FootstepsTrail;
import dev.footstepstrail.config.FootstepsConfig;
import dev.footstepstrail.config.SurfaceProfile;
import dev.footstepstrail.config.TrackingMode;
import dev.footstepstrail.tracking.Footstep;
import dev.footstepstrail.tracking.FootstepsTracker;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Main-thread contacts and immutable terrain-worker snapshots, shared by all five loaders. */
public final class TerrainContacts<S> {
    public interface World<S> {
        Hit floor(double x, double y, double z);
        S state(Position pos);
        boolean loaded(Position pos);
        String dimension();
        SurfaceProfile profile(S state);
        int color(S state, Position pos);
        DeformationMaterial material(S state, Position pos);
        void particle(S state, double x, double y, double z, double vx, double vy, double vz);
        void dirtySection(int x, int y, int z);
    }

    public static final class Player {
        public final double x, y, z;
        public final float yaw;
        public final boolean grounded, sprinting, sneaking;

        public Player(double x, double y, double z, float yaw, boolean grounded, boolean sprinting, boolean sneaking) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw;
            this.grounded = grounded; this.sprinting = sprinting; this.sneaking = sneaking;
        }
    }

    public static final class Position {
        public final int x, y, z;
        public Position(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Position)) return false;
            Position pos = (Position) other;
            return x == pos.x && y == pos.y && z == pos.z;
        }
    }

    public static final class Hit {
        public final Position pos;
        public final double height;
        public Hit(int x, int y, int z, double height) {
            this.pos = new Position(x, y, z); this.height = height;
        }
    }

    /** Captures one consistent snapshot, even if a client tick publishes while compiling a quad. */
    public static final class Surface {
        private final Position pos;
        private final DeformationMaterial material;
        private final List<SurfaceImprint> stamps;
        private final SurfaceHeights.Sampler heights;
        private final PixelImprint.Levels pixels;

        private Surface(Position pos, DeformationMaterial material, List<SurfaceImprint> stamps,
                SurfaceHeights.Sampler heights, PixelImprint.Levels pixels) {
            this.pos = pos; this.material = material; this.stamps = stamps;
            this.heights = heights; this.pixels = pixels;
        }

        public void emit(TerrainQuad quad, Consumer<TerrainQuad> output) {
            if (pixels != null) PixelImprint.emit(quad, pos.x, pos.y, pos.z, stamps, pixels, output);
            else QuadDeformation.emit(quad, pos.x, pos.y, pos.z, material, stamps, heights, output);
        }
    }

    /**
     * How far the player must fall before touching down counts as a landing.
     * Walking on slime makes the player hop a few hundredths of a block every
     * other tick; treating those hops as landings stamped extra prints and
     * burned through the step budget, so the trail vanished early.
     */
    static final double LANDING_DROP = 0.25;

    private final Map<Footstep, Contact<S>> contacts = new LinkedHashMap<>();
    private volatile Map<Position, Patch<S>> snapshot = Collections.emptyMap();
    private final AtomicBoolean loggedTerrain = new AtomicBoolean();
    private World<S> world;
    private double lastX = Double.NaN, lastZ, travelled;
    private double groundY = Double.NaN, airPeak = Double.NaN;
    private boolean grounded;
    private int foot;
    private long nextUpdate;

    public boolean tick(World<S> currentWorld, Player player) {
        return tick(currentWorld, player, FootstepsConfig.getInstance(), System.currentTimeMillis());
    }

    boolean tick(World<S> currentWorld, Player player, FootstepsConfig config, long now) {
        FootstepsTracker tracker = FootstepsTracker.getInstance();
        if (currentWorld != world) {
            snapshot = Collections.emptyMap();
            contacts.clear();
            tracker.clear();
            world = currentWorld;
            lastX = Double.NaN;
            travelled = 0;
            grounded = false;
            groundY = airPeak = Double.NaN;
            nextUpdate = 0;
        }
        if (!config.isEnabled() || !config.isTerrain() || world == null || player == null) {
            restore();
            lastX = Double.NaN;
            travelled = 0;
            grounded = false;
            groundY = airPeak = Double.NaN;
            return config.isTerrain();
        }
        double dx = Double.isNaN(lastX) ? 0 : player.x - lastX;
        double dz = Double.isNaN(lastX) ? 0 : player.z - lastZ;
        double distance = Math.hypot(dx, dz);
        // A hop that stays below the landing height is still walking.
        boolean onSurface = player.grounded
                || (!Double.isNaN(groundY) && player.y - groundY < LANDING_DROP);
        if (distance > 4) travelled = 0;
        else if (onSurface) travelled += distance;
        boolean allowed = !(config.isStealthSneak() && player.sneaking);
        double stride = config.getMinDistanceForStep() * (player.sprinting ? 1.5 : 1);
        // The first contact after spawning/enabling counts, so the trail starts at once.
        boolean firstContact = Double.isNaN(groundY) && Double.isNaN(airPeak);
        boolean landed = player.grounded && !grounded
                && (firstContact || airPeak - player.y >= LANDING_DROP);
        if (player.grounded) {
            groundY = player.y;
            airPeak = Double.NaN;
        } else {
            airPeak = Double.isNaN(airPeak) ? player.y : Math.max(airPeak, player.y);
        }
        if (allowed && (landed || (onSurface && travelled >= stride))) {
            float yaw = player.yaw;
            if (distance > 0.005) {
                float movement = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float turn = (movement - yaw) % 360;
                if (turn < -180) turn += 360;
                if (turn > 180) turn -= 360;
                yaw += Math.max(-25, Math.min(25, turn)) * 0.35f;
            }
            if (!config.isEnableRotation()) yaw = 0;
            double pressure = player.sprinting ? 1.2 : 1;
            if (landed && !Double.isNaN(lastX)) pressure = 1.4;
            record(player, config, yaw, pressure);
            if (landed && !Double.isNaN(lastX)) record(player, config, yaw, pressure);
            travelled = 0;
        }
        lastX = player.x; lastZ = player.z; grounded = player.grounded;
        tracker.removeExpiredFootsteps(config);
        while (contacts.size() > 500) contacts.remove(contacts.keySet().iterator().next());
        if (now >= nextUpdate) {
            publish(config, player, now);
            nextUpdate = now + 125;
        }
        return true;
    }

    private void record(Player player, FootstepsConfig config, float yaw, double pressure) {
        double offset = config.isEnableAlternatingFeet() ? config.getAlternatingFootOffset() * ((foot & 1) == 0 ? -1 : 1) : 0;
        double angle = Math.toRadians(yaw);
        double x = player.x + Math.cos(angle) * offset, z = player.z + Math.sin(angle) * offset;
        Hit hit = world.floor(x, player.y + 0.25, z);
        if (hit == null) return;
        S state = world.state(hit.pos);
        double y = hit.height;
        DeformationMaterial material = world.material(state, hit.pos);
        SurfaceImprint imprint = new SurfaceImprint(x, y, z, yaw, config.getMarkerSize(), pressure, foot++,
                material.softness);
        Map<Position, S> support = new HashMap<>();
        double radius = imprint.radius();
        for (int bx = (int) Math.floor(x - radius); bx <= (int) Math.floor(x + radius); bx++) {
            for (int bz = (int) Math.floor(z - radius); bz <= (int) Math.floor(z + radius); bz++) {
                double sx = Math.max(bx + 0.001, Math.min(bx + 0.999, x));
                double sz = Math.max(bz + 0.001, Math.min(bz + 0.999, z));
                Hit other = world.floor(sx, y + 0.20, sz);
                if (other != null && Math.abs(other.height - y) < 0.015) support.put(other.pos, world.state(other.pos));
            }
        }
        if (support.isEmpty()) return;
        Footstep step = FootstepsTracker.getInstance().recordContact(x, y, z, yaw,
                world.dimension(), world.profile(state), world.color(state, hit.pos));
        contacts.put(step, new Contact<>(imprint, support));
        if (material == DeformationMaterial.SAND || material == DeformationMaterial.GRAVEL || material == DeformationMaterial.SNOW) {
            for (int i = 0; i < 5; i++) {
                double a = i * 2.399963 + foot;
                double vx = Math.cos(a) * 0.035, vz = Math.sin(a) * 0.035;
                world.particle(state, x + vx * 4, y + 0.025, z + vz * 4, vx, 0.035, vz);
            }
        }
        nextUpdate = 0;
    }

    private void publish(FootstepsConfig config, Player viewer, long now) {
        Set<Footstep> alive = Collections.newSetFromMap(new IdentityHashMap<>());
        FootstepsTracker.getInstance().getFootsteps().forEachRemaining(alive::add);
        contacts.keySet().retainAll(alive);
        Map<Position, List<SurfaceImprint>> stamps = new HashMap<>();
        Map<Position, S> states = new HashMap<>();
        Iterator<Footstep> trail = FootstepsTracker.getInstance().getFootsteps();
        int newestIndex = trail.hasNext() ? trail.next().getStepIndex() : 0;
        for (Map.Entry<Footstep, Contact<S>> entry : contacts.entrySet()) {
            Footstep step = entry.getKey();
            Contact<S> contact = entry.getValue();
            double age = Math.max(0, (now - step.getTimestamp()) / 1000.0);
            TrackingMode mode = config.getTrackingMode();
            double ageFraction = mode.usesTime() ? age / config.getMaxDurationSeconds() : 0;
            double stepFraction = mode.usesSteps()
                    ? (newestIndex - step.getStepIndex()) / (double) config.getMaxSteps() : 0;
            double life = 1 - SurfaceImprint.smooth(0.55, 1, Math.max(ageFraction, stepFraction));
            Iterator<Map.Entry<Position, S>> surfaces = contact.support.entrySet().iterator();
            while (surfaces.hasNext()) {
                Map.Entry<Position, S> surface = surfaces.next();
                Position pos = surface.getKey();
                if (!world.loaded(pos) || world.state(pos) != surface.getValue()) {
                    surfaces.remove();
                    continue;
                }
                DeformationMaterial material = world.material(surface.getValue(), pos);
                if (!config.isHardSurfaceScuffs() && material.isHard()) continue;
                if (step.distanceSquaredTo(viewer.x, viewer.y, viewer.z)
                        > config.getMaxRenderDistance() * config.getMaxRenderDistance()) continue;
                double strength = life * contact.imprint.strength * config.getMaxOpacity() / 0.6;
                strength = Math.round(strength * 32) / 32.0;
                if (strength <= 0) continue;
                List<SurfaceImprint> list = stamps.computeIfAbsent(pos, p -> new ArrayList<>());
                if (list.size() == 24) list.remove(0);
                list.add(contact.imprint.withStrength(strength));
                states.put(pos, surface.getValue());
            }
        }
        contacts.entrySet().removeIf(entry -> entry.getValue().support.isEmpty());
        Map<Position, Patch<S>> next = new HashMap<>();
        for (Map.Entry<Position, List<SurfaceImprint>> entry : stamps.entrySet()) {
            Position pos = entry.getKey();
            next.put(pos, new Patch<>(states.get(pos), world.material(states.get(pos), pos),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue()))));
        }
        Map<Position, Patch<S>> old = snapshot;
        snapshot = Collections.unmodifiableMap(next);
        dirtyChanges(old, next);
    }

    private void dirtyChanges(Map<Position, Patch<S>> old, Map<Position, Patch<S>> next) {
        Set<Position> changed = new HashSet<>(old.keySet());
        changed.addAll(next.keySet());
        Set<Position> sections = new HashSet<>();
        for (Position pos : changed) {
            if (!same(old.get(pos), next.get(pos))) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    sections.add(new Position((pos.x + dx) >> 4, pos.y >> 4, (pos.z + dz) >> 4));
            }
        }
        for (Position section : sections) world.dirtySection(section.x, section.y, section.z);
    }

    private static boolean same(Patch<?> a, Patch<?> b) {
        if (a == null || b == null) return a == b;
        if (a.state != b.state || a.material != b.material || a.stamps.size() != b.stamps.size()) return false;
        for (int i = 0; i < a.stamps.size(); i++) if (!a.stamps.get(i).sameGeometry(b.stamps.get(i))) return false;
        return true;
    }

    private void restore() {
        Map<Position, Patch<S>> old = snapshot;
        snapshot = Collections.emptyMap();
        if (world != null && !old.isEmpty()) dirtyChanges(old, snapshot);
        contacts.clear();
    }

    public Surface surface(int x, int y, int z, S state) {
        Map<Position, Patch<S>> frame = snapshot;
        if (frame.isEmpty()) return null;
        Position pos = new Position(x, y, z);
        Patch<S> patch = frame.get(pos);
        if (patch == null || patch.state != state) return null;
        List<Patch<S>> neighbours = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            neighbours.add(frame.get(new Position(x + dx, y, z + dz)));
        if (patch.material.isPixel()) {
            // Pixel prints. A texel in a neighbouring block takes that block's level, so walls
            // close every step; a block that draws no pixel prints stays flat (null).
            return new Surface(pos, patch.material, patch.stamps, null, (wx, wy, wz) -> {
                int dx = (int) Math.floor(wx) - x, dz = (int) Math.floor(wz) - z;
                if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return null;
                Patch<S> owner = neighbours.get((dx + 1) * 3 + dz + 1);
                if (owner == null || !owner.material.isPixel()) return null;
                return PixelImprint.level(owner.stamps, owner.material, wy - y, wx, wy, wz);
            });
        }
        return new Surface(pos, patch.material, patch.stamps, (wx, wy, wz) -> {
            double height = 0;
            double fade = 1;
            for (int dx = -1; dx <= 1; dx++) {
                double weightX = SurfaceHeights.weight(wx, x + dx);
                for (int dz = -1; dz <= 1; dz++) {
                    Patch<S> neighbour = neighbours.get((dx + 1) * 3 + dz + 1);
                    if (neighbour == null) continue;
                    if (neighbour.material.isPixel()) {
                        // A pixel-print block keeps its top flat at the shared edge; settle this
                        // surface to the same height there so no seam opens between them.
                        double ox = Math.max(Math.max(x + dx - wx, wx - (x + dx + 1)), 0);
                        double oz = Math.max(Math.max(z + dz - wz, wz - (z + dz + 1)), 0);
                        fade *= SurfaceImprint.smooth(0, PixelImprint.PIXEL, Math.sqrt(ox * ox + oz * oz));
                        continue;
                    }
                    if (weightX == 0) continue;
                    double weight = weightX * SurfaceHeights.weight(wz, z + dz);
                    if (weight != 0) height += weight * SurfaceHeights.sample(wx, wy, wz, y, neighbour.material, neighbour.stamps);
                }
            }
            return height * fade;
        }, null);
    }

    public void emitted() {
        if (loggedTerrain.compareAndSet(false, true))
            FootstepsTrail.LOGGER.info("Textured footprint deformation is active in the terrain renderer");
    }

    public boolean hasSurface(int x, int y, int z, S state) {
        Map<Position, Patch<S>> frame = snapshot;
        if (frame.isEmpty()) return false;
        Patch<S> patch = frame.get(new Position(x, y, z));
        return patch != null && patch.state == state;
    }

    private static final class Contact<S> {
        final SurfaceImprint imprint;
        final Map<Position, S> support;
        Contact(SurfaceImprint imprint, Map<Position, S> support) { this.imprint = imprint; this.support = support; }
    }

    private static final class Patch<S> {
        final S state;
        final DeformationMaterial material;
        final List<SurfaceImprint> stamps;
        Patch(S state, DeformationMaterial material, List<SurfaceImprint> stamps) {
            this.state = state; this.material = material; this.stamps = stamps;
        }
    }
}
